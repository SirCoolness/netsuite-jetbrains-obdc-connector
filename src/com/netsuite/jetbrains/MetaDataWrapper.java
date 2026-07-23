package com.netsuite.jetbrains;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps DatabaseMetaData to normalize catalog names by stripping sandbox suffixes
 * (_SB1, _SB2, etc.) so JetBrains schema patterns work across environments.
 *
 * getDatabaseProductName() returns "GenericSQL" to prevent JetBrains from mapping
 * "OpenAccess" → NETSUITE → Oracle introspector, which causes a DbmsMismatchException.
 * With an unrecognized product name, JetBrains falls through to generic JDBC introspection.
 */
public class MetaDataWrapper extends DatabaseMetaDataDelegate {

    public MetaDataWrapper(DatabaseMetaData delegate) {
        super(delegate);
    }

    @Override
    public String getDatabaseProductName() throws SQLException {
        return "GenericSQL";
    }


    @Override
    public ResultSet getCatalogs() throws SQLException {
        ResultSet original = super.getCatalogs();
        List<String[]> rows = new ArrayList<String[]>();
        while (original.next()) {
            String catalog = original.getString(1);
            rows.add(new String[]{ CatalogStripper.strip(catalog) });
        }
        original.close();
        return new SimpleResultSet(new String[]{"TABLE_CAT"}, rows);
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        ResultSet original = super.getSchemas();
        List<String[]> rows = new ArrayList<String[]>();
        while (original.next()) {
            String schema = original.getString(1);
            String catalog = original.getString(2);
            rows.add(new String[]{ schema, CatalogStripper.strip(catalog) });
        }
        original.close();
        return new SimpleResultSet(new String[]{"TABLE_SCHEM", "TABLE_CATALOG"}, rows);
    }

    @Override
    public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
        // Pass null catalog to the underlying driver — it only knows the real _SB1 name.
        // We filter post-hoc against the stripped catalog the caller expects.
        ResultSet original = getDelegate().getSchemas(null, schemaPattern);
        List<String[]> rows = new ArrayList<String[]>();
        while (original.next()) {
            String schema = original.getString(1);
            String rawCatalog = original.getString(2);
            String stripped = CatalogStripper.strip(rawCatalog);
            // If caller specified a catalog, only include matching rows
            if (catalog == null || catalog.equals(stripped)) {
                rows.add(new String[]{ schema, stripped });
            }
        }
        original.close();
        return new SimpleResultSet(new String[]{"TABLE_SCHEM", "TABLE_CATALOG"}, rows);
    }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
        // Pass null catalog to get all tables, let the underlying driver handle filtering
        ResultSet original = super.getTables(null, schemaPattern, tableNamePattern, types);
        return new CatalogRewriteResultSet(original, 1);
    }

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        ResultSet original = super.getColumns(null, schemaPattern, tableNamePattern, columnNamePattern);
        return new CatalogRewriteResultSet(original, 1);
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        return new CatalogRewriteResultSet(super.getPrimaryKeys(null, schema, table), 1);
    }

    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        return new CatalogRewriteResultSet(super.getImportedKeys(null, schema, table), 3);
    }

    @Override
    public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        return new CatalogRewriteResultSet(super.getExportedKeys(null, schema, table), 5);
    }

    @Override
    public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) throws SQLException {
        return new CatalogRewriteResultSet(super.getIndexInfo(null, schema, table, unique, approximate), 1);
    }
}
