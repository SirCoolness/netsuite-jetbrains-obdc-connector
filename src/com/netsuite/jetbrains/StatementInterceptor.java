package com.netsuite.jetbrains;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Intercepts Statement.executeQuery() to stub Oracle-specific queries that
 * JetBrains' OraDialect fires during introspection. NetSuite's SuiteAnalytics
 * Connect doesn't support SYS_CONTEXT, V$ views, or DBA_/ALL_ dictionary tables,
 * so we return sensible single-row results instead of letting them 400.
 *
 * Strategy: pattern-match the SQL and return a SimpleResultSet with appropriate
 * values derived from the connection metadata (catalog = company, user = username).
 */
public class StatementInterceptor {

    // Oracle introspection patterns that NetSuite will never support
    private static final Pattern SYS_CONTEXT_PATTERN =
        Pattern.compile("SYS_CONTEXT\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_USER_DUAL =
        Pattern.compile("SELECT\\s+USER\\s+FROM\\s+DUAL", Pattern.CASE_INSENSITIVE);
    private static final Pattern DBA_USERS =
        Pattern.compile("\\bDBA_USERS\\b|\\bALL_USERS\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern V_DOLLAR =
        Pattern.compile("\\bV\\$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NLS_PARAMS =
        Pattern.compile("\\bNLS_SESSION_PARAMETERS\\b|\\bNLS_DATABASE_PARAMETERS\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALL_SYNONYMS =
        Pattern.compile("\\bALL_SYNONYMS\\b|\\bDBA_SYNONYMS\\b", Pattern.CASE_INSENSITIVE);

    private final Connection wrappedConnection;

    public StatementInterceptor(Connection wrappedConnection) {
        this.wrappedConnection = wrappedConnection;
    }

    /**
     * Wrap a Statement so executeQuery is intercepted for Oracle stubs.
     */
    public Statement wrapStatement(final Statement real) {
        Class<?>[] interfaces = getAllInterfaces(real.getClass());

        return (Statement) Proxy.newProxyInstance(
            real.getClass().getClassLoader(),
            interfaces,
            new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if ("executeQuery".equals(method.getName()) && args != null && args.length == 1) {
                        String sql = (String) args[0];
                        JdbcLogger.log("Statement", "executeQuery", args);
                        ResultSet stubbed = tryStub(sql);
                        if (stubbed != null) {
                            JdbcLogger.log("Statement", "executeQuery [STUBBED]", args, stubbed);
                            return stubbed;
                        }
                        JdbcLogger.log("Statement", "executeQuery [PASSTHROUGH]", args);
                    }
                    // execute(String) can also be used for queries; intercept if it
                    // would throw on Oracle-specific SQL
                    if ("execute".equals(method.getName()) && args != null && args.length >= 1
                            && args[0] instanceof String) {
                        String sql = (String) args[0];
                        JdbcLogger.log("Statement", "execute", args);
                        if (isOracleSpecific(sql)) {
                            JdbcLogger.log("Statement", "execute [STUBBED false]", args);
                            return Boolean.FALSE;
                        }
                    }
                    try {
                        Object result = method.invoke(real, args);
                        return result;
                    } catch (InvocationTargetException e) {
                        JdbcLogger.logException("Statement", method.getName(), e.getCause());
                        throw e.getCause();
                    }
                }
            }
        );
    }

    private boolean isOracleSpecific(String sql) {
        return SYS_CONTEXT_PATTERN.matcher(sql).find()
            || SELECT_USER_DUAL.matcher(sql).find()
            || DBA_USERS.matcher(sql).find()
            || V_DOLLAR.matcher(sql).find()
            || NLS_PARAMS.matcher(sql).find()
            || ALL_SYNONYMS.matcher(sql).find();
    }

    /**
     * If the SQL matches a known Oracle introspection query, return a stub ResultSet.
     * Returns null if the query should pass through to the real driver.
     */
    private ResultSet tryStub(String sql) {
        if (sql == null) return null;

        // SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') → return catalog (company name)
        if (SYS_CONTEXT_PATTERN.matcher(sql).find()) {
            String schema = getCatalogSafe();
            return singleValueResult("CURRENT_SCHEMA", schema);
        }

        // SELECT USER FROM DUAL → return the JDBC username
        if (SELECT_USER_DUAL.matcher(sql).find()) {
            String user = getUserSafe();
            return singleValueResult("USER", user);
        }

        // DBA_USERS / ALL_USERS → return real schemas as "users" (Oracle equates schemas with users)
        // The Oracle introspector uses this to discover available schemas for introspection.
        if (DBA_USERS.matcher(sql).find()) {
            return buildUsersFromSchemas();
        }

        // V$VERSION, V$PARAMETER, etc → empty
        if (V_DOLLAR.matcher(sql).find()) {
            return emptyResult("BANNER");
        }

        // NLS_SESSION_PARAMETERS / NLS_DATABASE_PARAMETERS → empty
        if (NLS_PARAMS.matcher(sql).find()) {
            return emptyResult("PARAMETER", "VALUE");
        }

        // ALL_SYNONYMS → empty (NetSuite has no synonym concept)
        if (ALL_SYNONYMS.matcher(sql).find()) {
            return emptyResult("OWNER", "SYNONYM_NAME", "TABLE_OWNER", "TABLE_NAME");
        }

        return null;
    }

    /**
     * Build a fake ALL_USERS result from the real schemas reported by DatabaseMetaData.
     * Oracle treats schemas as users, so the introspector expects users = schemas.
     */
    private ResultSet buildUsersFromSchemas() {
        List<String[]> rows = new ArrayList<String[]>();
        try {
            java.sql.DatabaseMetaData meta = wrappedConnection.getMetaData();
            java.sql.ResultSet schemas = meta.getSchemas();
            while (schemas.next()) {
                String schemaName = schemas.getString(1);
                rows.add(new String[]{ schemaName, "1", "2025-01-01" });
            }
            schemas.close();
        } catch (Exception e) {
            // Fallback: return catalog as the sole user
            String catalog = getCatalogSafe();
            if (catalog != null && !catalog.isEmpty()) {
                rows.add(new String[]{ catalog, "1", "2025-01-01" });
            }
        }
        // Also add the catalog itself as a "user" in case the introspector looks for it
        String catalog = getCatalogSafe();
        if (catalog != null && !catalog.isEmpty()) {
            boolean alreadyPresent = false;
            for (String[] row : rows) {
                if (catalog.equals(row[0])) { alreadyPresent = true; break; }
            }
            if (!alreadyPresent) {
                rows.add(new String[]{ catalog, "2", "2025-01-01" });
            }
        }
        return new SimpleResultSet(new String[]{"USERNAME", "USER_ID", "CREATED"}, rows);
    }

    private ResultSet singleValueResult(String columnName, String value) {
        List<String[]> rows = new ArrayList<String[]>();
        rows.add(new String[]{ value });
        return new SimpleResultSet(new String[]{ columnName }, rows);
    }

    private ResultSet emptyResult(String... columns) {
        List<String[]> rows = new ArrayList<String[]>();
        return new SimpleResultSet(columns, rows);
    }

    private String getCatalogSafe() {
        try {
            return wrappedConnection.getCatalog();
        } catch (Exception e) {
            return "";
        }
    }

    private String getUserSafe() {
        try {
            return wrappedConnection.getMetaData().getUserName();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    private static Class<?>[] getAllInterfaces(Class<?> clazz) {
        List<Class<?>> ifaces = new ArrayList<Class<?>>();
        while (clazz != null) {
            for (Class<?> iface : clazz.getInterfaces()) {
                if (!ifaces.contains(iface)) ifaces.add(iface);
            }
            clazz = clazz.getSuperclass();
        }
        if (!ifaces.contains(Statement.class)) ifaces.add(Statement.class);
        return ifaces.toArray(new Class<?>[0]);
    }
}
