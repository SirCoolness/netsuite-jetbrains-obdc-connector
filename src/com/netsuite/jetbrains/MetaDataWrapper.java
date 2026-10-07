package com.netsuite.jetbrains;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * DatabaseMetaData proxy that makes NetSuite metadata usable by JetBrains' generic
 * JdbcIntrospector:
 * - getDatabaseProductName() returns "GenericSQL". PhpStorm maps a product name matching
 *   "openaccess" to its NETSUITE DBMS, whose dialect extends Oracle and whose introspector
 *   rejects any connection that is not ORACLE (DbmsMismatchException). An unknown product
 *   name selects the generic introspector instead.
 * - Catalog columns of every metadata result have the sandbox suffix (_SB1) stripped, and
 *   catalog arguments are passed to the server as null (it only knows the suffixed name).
 * - "SYSTEM TABLE" is hidden from getTableTypes (a slow scan of the server's SYSTEM schema)
 *   and from getTables when no types are given.
 * - getPrimaryKeys returns one key per table. The server returns one candidate per foreign key
 *   that references the table (each with its own PK_NAME), and candidates disagree (transactionLine:
 *   [id], [transaction], [uniquekey], [transaction, id]); see {@link #bestCandidate}.
 * - getImportedKeys for all tables (which the server refuses) is answered with getExportedKeys
 *   for all tables, the same set of foreign keys.
 * - Catalogs are reported as unusable in SQL: the server rejects "catalog"."schema".table, so the
 *   IDE must not catalog-qualify names.
 * - Metadata calls the server does not support return an empty result with the standard
 *   JDBC columns instead of throwing, so introspection completes.
 * - getConnection() returns the connection proxy, never the raw OpenAccess connection.
 */
final class MetaDataWrapper implements InvocationHandler {

    static final String PRODUCT_NAME = "GenericSQL";

    /** Answers that replace the server's. */
    private static final Map<String, Object> CONSTANTS = new HashMap<String, Object>();
    static {
        CONSTANTS.put("getDatabaseProductName", PRODUCT_NAME);
        CONSTANTS.put("supportsCatalogsInDataManipulation", false);
        CONSTANTS.put("supportsCatalogsInTableDefinitions", false);
        CONSTANTS.put("supportsCatalogsInProcedureCalls", false);
        CONSTANTS.put("supportsCatalogsInIndexDefinitions", false);
        CONSTANTS.put("supportsCatalogsInPrivilegeDefinitions", false);
    }
    private static final String SYSTEM_TABLE = "SYSTEM TABLE";

    /** Methods whose first argument is a catalog (getCrossReference also has one at index 3). */
    private static final Set<String> CATALOG_FIRST = new HashSet<String>(Arrays.asList(
        "getSchemas", "getTables", "getColumns", "getPrimaryKeys", "getImportedKeys", "getExportedKeys",
        "getCrossReference", "getIndexInfo", "getProcedures", "getProcedureColumns", "getFunctions",
        "getFunctionColumns", "getUDTs", "getAttributes", "getVersionColumns", "getBestRowIdentifier",
        "getColumnPrivileges", "getTablePrivileges", "getSuperTypes", "getSuperTables", "getPseudoColumns"));

    private static final String[] KEY_COLUMNS = {
        "PKTABLE_CAT", "PKTABLE_SCHEM", "PKTABLE_NAME", "PKCOLUMN_NAME", "FKTABLE_CAT", "FKTABLE_SCHEM",
        "FKTABLE_NAME", "FKCOLUMN_NAME", "KEY_SEQ", "UPDATE_RULE", "DELETE_RULE", "FK_NAME", "PK_NAME",
        "DEFERRABILITY"};

    /** Standard JDBC result columns, used to answer unsupported calls with an empty result. */
    private static final Map<String, String[]> EMPTY_RESULTS = new HashMap<String, String[]>();
    static {
        EMPTY_RESULTS.put("getFunctions", new String[]{
            "FUNCTION_CAT", "FUNCTION_SCHEM", "FUNCTION_NAME", "REMARKS", "FUNCTION_TYPE", "SPECIFIC_NAME"});
        EMPTY_RESULTS.put("getFunctionColumns", new String[]{
            "FUNCTION_CAT", "FUNCTION_SCHEM", "FUNCTION_NAME", "COLUMN_NAME", "COLUMN_TYPE", "DATA_TYPE",
            "TYPE_NAME", "PRECISION", "LENGTH", "SCALE", "RADIX", "NULLABLE", "REMARKS", "CHAR_OCTET_LENGTH",
            "ORDINAL_POSITION", "IS_NULLABLE", "SPECIFIC_NAME"});
        EMPTY_RESULTS.put("getProcedures", new String[]{
            "PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME", "RESERVED1", "RESERVED2", "RESERVED3",
            "REMARKS", "PROCEDURE_TYPE", "SPECIFIC_NAME"});
        EMPTY_RESULTS.put("getProcedureColumns", new String[]{
            "PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME", "COLUMN_NAME", "COLUMN_TYPE", "DATA_TYPE",
            "TYPE_NAME", "PRECISION", "LENGTH", "SCALE", "RADIX", "NULLABLE", "REMARKS", "COLUMN_DEF",
            "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "CHAR_OCTET_LENGTH", "ORDINAL_POSITION", "IS_NULLABLE",
            "SPECIFIC_NAME"});
        EMPTY_RESULTS.put("getUDTs", new String[]{
            "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "CLASS_NAME", "DATA_TYPE", "REMARKS", "BASE_TYPE"});
        EMPTY_RESULTS.put("getAttributes", new String[]{
            "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "ATTR_NAME", "DATA_TYPE", "ATTR_TYPE_NAME", "ATTR_SIZE",
            "DECIMAL_DIGITS", "NUM_PREC_RADIX", "NULLABLE", "REMARKS", "ATTR_DEF", "SQL_DATA_TYPE",
            "SQL_DATETIME_SUB", "CHAR_OCTET_LENGTH", "ORDINAL_POSITION", "IS_NULLABLE", "SCOPE_CATALOG",
            "SCOPE_SCHEMA", "SCOPE_TABLE", "SOURCE_DATA_TYPE"});
        EMPTY_RESULTS.put("getVersionColumns", new String[]{
            "SCOPE", "COLUMN_NAME", "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH", "DECIMAL_DIGITS",
            "PSEUDO_COLUMN"});
        EMPTY_RESULTS.put("getPrimaryKeys", new String[]{
            "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "KEY_SEQ", "PK_NAME"});
        EMPTY_RESULTS.put("getImportedKeys", KEY_COLUMNS);
        EMPTY_RESULTS.put("getExportedKeys", KEY_COLUMNS);
        EMPTY_RESULTS.put("getCrossReference", KEY_COLUMNS);
        EMPTY_RESULTS.put("getIndexInfo", new String[]{
            "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "NON_UNIQUE", "INDEX_QUALIFIER", "INDEX_NAME", "TYPE",
            "ORDINAL_POSITION", "COLUMN_NAME", "ASC_OR_DESC", "CARDINALITY", "PAGES", "FILTER_CONDITION"});
        EMPTY_RESULTS.put("getSuperTypes", new String[]{
            "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "SUPERTYPE_CAT", "SUPERTYPE_SCHEM", "SUPERTYPE_NAME"});
        EMPTY_RESULTS.put("getSuperTables", new String[]{
            "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "SUPERTABLE_NAME"});
        EMPTY_RESULTS.put("getPseudoColumns", new String[]{
            "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "DATA_TYPE", "COLUMN_SIZE",
            "DECIMAL_DIGITS", "NUM_PREC_RADIX", "COLUMN_USAGE", "REMARKS", "CHAR_OCTET_LENGTH", "IS_NULLABLE"});
        EMPTY_RESULTS.put("getClientInfoProperties", new String[]{
            "NAME", "MAX_LEN", "DEFAULT_VALUE", "DESCRIPTION"});
    }

    /** How the OpenAccess server words "this call/argument combination is not supported". */
    private static final Pattern UNSUPPORTED = Pattern.compile(
        "unsupported method|not supported|must be supplied", Pattern.CASE_INSENSITIVE);

    /** Results that identify the account or user; never written to the debug log. */
    private static final Set<String> SENSITIVE_RESULTS = new HashSet<String>(Arrays.asList("getURL", "getUserName"));

    private final ConnectionWrapper owner;
    private DatabaseMetaData proxy;
    private volatile String[] tableTypes;

    MetaDataWrapper(ConnectionWrapper owner) {
        this.owner = owner;
    }

    synchronized DatabaseMetaData proxy() {
        if (proxy == null) {
            proxy = (DatabaseMetaData) Proxy.newProxyInstance(
                MetaDataWrapper.class.getClassLoader(), new Class<?>[]{DatabaseMetaData.class}, this);
        }
        return proxy;
    }

    public Object invoke(Object proxyRef, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        JdbcLogger.log("MetaData", name, args);
        try {
            Object result = dispatch(method, args);
            JdbcLogger.log("MetaData", name, args, SENSITIVE_RESULTS.contains(name) ? "<redacted>" : result);
            return result;
        } catch (Throwable t) {
            JdbcLogger.logException("MetaData", name, t);
            throw t;
        }
    }

    private Object dispatch(Method method, Object[] args) throws Throwable {
        String name = method.getName();
        Object common = Proxies.common(proxy, method, args, owner::realMetaData);
        if (common != Proxies.UNHANDLED) {
            return common;
        }
        if (CONSTANTS.containsKey(name) && Proxies.isNoArg(args)) {
            return CONSTANTS.get(name);
        }
        if ("getConnection".equals(name)) {
            return owner.proxy();
        }
        if ("getTableTypes".equals(name)) {
            List<Object[]> rows = new ArrayList<Object[]>();
            for (String type : tableTypes()) {
                rows.add(new Object[]{type});
            }
            return new SimpleResultSet(new String[]{"TABLE_TYPE"}, rows);
        }

        Object[] serverArgs = serverArgs(name, args);
        if ("getTables".equals(name) && serverArgs[3] == null) {
            serverArgs[3] = tableTypes();
        }
        if ("getImportedKeys".equals(name) && (args[2] == null || "%".equals(args[2]))) {
            ResultSet all = allForeignKeys((String) args[1]);
            if (all != null) {
                return all;
            }
        }
        if ("getPrimaryKeys".equals(name)) {
            return primaryKeys((ResultSet) fetch(method, serverArgs));
        }
        if ("getCatalogs".equals(name)) {
            return distinctCatalogs((ResultSet) fetch(method, serverArgs));
        }
        if ("getSchemas".equals(name)) {
            String catalog = args != null && args.length > 0 ? (String) args[0] : null;
            return schemasIn((ResultSet) fetch(method, serverArgs), catalog);
        }
        Object result = fetch(method, serverArgs);
        return result instanceof ResultSet ? new CatalogRewriteResultSet((ResultSet) result) : result;
    }

    /** Catalog arguments are stripped names the server does not know; ask for all catalogs. */
    private static Object[] serverArgs(String name, Object[] args) {
        if (args == null) {
            return null;
        }
        Object[] copy = args.clone();
        if (CATALOG_FIRST.contains(name) && copy.length > 0) {
            copy[0] = null;
        }
        if ("getCrossReference".equals(name) && copy.length > 3) {
            copy[3] = null;
        }
        return copy;
    }

    private Object fetch(Method method, Object[] args) throws Throwable {
        try {
            return owner.withReconnect(() -> Proxies.invoke(owner.realMetaData(), method, args));
        } catch (SQLException e) {
            String[] columns = EMPTY_RESULTS.get(method.getName());
            if (columns == null || !(e instanceof SQLFeatureNotSupportedException
                    || (e.getMessage() != null && UNSUPPORTED.matcher(e.getMessage()).find()))) {
                throw e;
            }
            JdbcLogger.write("MetaData." + method.getName() + " unsupported by server, returning empty: " + e.getMessage());
            return new SimpleResultSet(columns, new ArrayList<Object[]>());
        }
    }

    /** Every foreign key in the schema, via getExportedKeys; null if the server refuses that too. */
    private ResultSet allForeignKeys(String schema) throws Throwable {
        try {
            return new CatalogRewriteResultSet((ResultSet) owner.withReconnect(
                () -> owner.realMetaData().getExportedKeys(null, schema, null)));
        } catch (SQLException e) {
            JdbcLogger.write("MetaData.getExportedKeys for all tables failed: " + e.getMessage());
            return null;
        }
    }

    /** The server's table types without SYSTEM TABLE; fetched once per connection. */
    private String[] tableTypes() throws Throwable {
        String[] types = tableTypes;
        if (types == null) {
            List<String> kept = new ArrayList<String>();
            ResultSet rs = (ResultSet) owner.withReconnect(() -> owner.realMetaData().getTableTypes());
            try {
                while (rs.next()) {
                    String type = rs.getString(1);
                    if (type != null && !SYSTEM_TABLE.equalsIgnoreCase(type.trim())) {
                        kept.add(type.trim());
                    }
                }
            } finally {
                rs.close();
            }
            types = kept.toArray(new String[0]);
            tableTypes = types;
        }
        return types;
    }

    /**
     * One primary key per table. Rows are grouped by table and by the server's PK_NAME (one
     * candidate per referencing foreign key, with its own KEY_SEQ order), then
     * {@link #bestCandidate} picks one, renumbered 1..n and named pk_<table>.
     */
    private static ResultSet primaryKeys(ResultSet raw) throws SQLException {
        final Map<String, Map<String, Candidate>> tables = new LinkedHashMap<String, Map<String, Candidate>>();
        SimpleResultSet keys = SimpleResultSet.copy(raw, (row, target) -> {
            CatalogStripper.stripRow(row, target.columnNames());
            String table = value(row, target.indexOf("TABLE_SCHEM")) + "\u0000" + value(row, target.indexOf("TABLE_NAME"));
            String pkName = String.valueOf(value(row, target.indexOf("PK_NAME")));
            Map<String, Candidate> candidates = tables.get(table);
            if (candidates == null) {
                candidates = new LinkedHashMap<String, Candidate>();
                tables.put(table, candidates);
            }
            Candidate candidate = candidates.get(pkName);
            if (candidate == null) {
                candidate = new Candidate();
                candidates.put(pkName, candidate);
            }
            candidate.rows.add(row);
            return false;
        });
        int table = keys.indexOf("TABLE_NAME"), column = keys.indexOf("COLUMN_NAME");
        int keySeq = keys.indexOf("KEY_SEQ"), pkName = keys.indexOf("PK_NAME");
        List<Object[]> rows = new ArrayList<Object[]>();
        for (Map<String, Candidate> candidates : tables.values()) {
            Candidate best = bestCandidate(candidates.values(), column, keySeq);
            for (int i = 0; i < best.rows.size(); i++) {
                Object[] row = best.rows.get(i);
                if (keySeq >= 0) row[keySeq] = i + 1;
                if (pkName >= 0 && table >= 0) row[pkName] = "pk_" + row[table];
                rows.add(row);
            }
        }
        return keys.withRows(rows);
    }

    /** The rows of one server PK_NAME: a candidate key. */
    private static final class Candidate {
        final List<Object[]> rows = new ArrayList<Object[]>();
        int references;

        List<Object> columns(int column) {
            List<Object> columns = new ArrayList<Object>();
            for (Object[] row : rows) {
                columns.add(value(row, column));
            }
            return columns;
        }
    }

    /**
     * Pick the key among candidates with distinct column lists: the most columns (a superset of
     * a unique key is still unique, and NetSuite's composite keys such as transactionLine
     * [transaction, id] are the real ones, while single-column candidates there are not unique),
     * then one containing "id", then the one most foreign keys reference, then the first seen.
     */
    private static Candidate bestCandidate(Collection<Candidate> candidates, int column, final int keySeq) {
        Map<List<Object>, Candidate> distinct = new LinkedHashMap<List<Object>, Candidate>();
        for (Candidate candidate : candidates) {
            candidate.rows.sort((x, y) -> Long.compare(keySeqOf(x, keySeq), keySeqOf(y, keySeq)));
            // a column listed twice in one candidate (CardholderAuthBillAddress) counts once
            Set<Object> seen = new HashSet<Object>();
            candidate.rows.removeIf(row -> !seen.add(value(row, column)));
            List<Object> columns = candidate.columns(column);
            Candidate same = distinct.get(columns);
            if (same == null) {
                distinct.put(columns, candidate);
                same = candidate;
            }
            same.references++;
        }
        Candidate best = null;
        for (Map.Entry<List<Object>, Candidate> entry : distinct.entrySet()) {
            Candidate candidate = entry.getValue();
            if (best == null || compare(candidate, entry.getKey(), best, best.columns(column)) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static int compare(Candidate a, List<Object> aColumns, Candidate b, List<Object> bColumns) {
        if (aColumns.size() != bColumns.size()) {
            return Integer.compare(aColumns.size(), bColumns.size());
        }
        boolean aId = containsId(aColumns), bId = containsId(bColumns);
        if (aId != bId) {
            return aId ? 1 : -1;
        }
        return Integer.compare(a.references, b.references);
    }

    private static boolean containsId(List<Object> columns) {
        for (Object c : columns) {
            if (c != null && "id".equalsIgnoreCase(c.toString())) return true;
        }
        return false;
    }

    private static long keySeqOf(Object[] row, int keySeq) {
        Object v = value(row, keySeq);
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }

    private static ResultSet distinctCatalogs(ResultSet raw) throws SQLException {
        final Set<Object> seen = new HashSet<Object>();
        return SimpleResultSet.copy(raw, (row, target) -> {
            CatalogStripper.stripRow(row, target.columnNames());
            return seen.add(value(row, target.indexOf("TABLE_CAT")));
        });
    }

    private static ResultSet schemasIn(ResultSet raw, final String catalog) throws SQLException {
        return SimpleResultSet.copy(raw, (row, target) -> {
            CatalogStripper.stripRow(row, target.columnNames());
            Object rowCatalog = value(row, target.indexOf("TABLE_CATALOG"));
            return catalog == null || rowCatalog == null || catalog.equals(rowCatalog);
        });
    }

    private static Object value(Object[] row, int index) {
        return index >= 0 ? row[index] : null;
    }
}
