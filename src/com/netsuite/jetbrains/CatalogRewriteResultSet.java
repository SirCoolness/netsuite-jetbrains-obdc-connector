package com.netsuite.jetbrains;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Wraps a metadata ResultSet and strips sandbox suffixes from every catalog column
 * (TABLE_CAT, PKTABLE_CAT, FKTABLE_CAT, ...), found by label, so reads by index and by
 * label, through getString and getObject alike, agree with getTables/getColumns.
 * Other columns (table names included) are never touched.
 */
public class CatalogRewriteResultSet extends ResultSetDelegate {

    private final Set<Integer> catalogColumns = new HashSet<Integer>();

    public CatalogRewriteResultSet(ResultSet delegate) throws SQLException {
        super(delegate);
        ResultSetMetaData meta = delegate.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (CatalogStripper.isCatalogLabel(meta.getColumnLabel(i))) {
                catalogColumns.add(i);
            }
        }
    }

    private Object rewrite(boolean catalog, Object value) {
        return catalog && value instanceof String ? CatalogStripper.strip((String) value) : value;
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        return (String) rewrite(catalogColumns.contains(columnIndex), super.getString(columnIndex));
    }

    @Override
    public String getString(String columnLabel) throws SQLException {
        return (String) rewrite(CatalogStripper.isCatalogLabel(columnLabel), super.getString(columnLabel));
    }

    @Override
    public String getNString(int columnIndex) throws SQLException {
        return (String) rewrite(catalogColumns.contains(columnIndex), super.getNString(columnIndex));
    }

    @Override
    public String getNString(String columnLabel) throws SQLException {
        return (String) rewrite(CatalogStripper.isCatalogLabel(columnLabel), super.getNString(columnLabel));
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        return rewrite(catalogColumns.contains(columnIndex), super.getObject(columnIndex));
    }

    @Override
    public Object getObject(String columnLabel) throws SQLException {
        return rewrite(CatalogStripper.isCatalogLabel(columnLabel), super.getObject(columnLabel));
    }

    // The OpenAccess driver (JDBC 4.0) does not implement getObject(..., Class), so catalog
    // columns read as text are answered here; other reads go to the driver unchanged.
    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        if (catalogColumns.contains(columnIndex) && (type == String.class || type == Object.class)) {
            return type.cast(getString(columnIndex));
        }
        return super.getObject(columnIndex, type);
    }

    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        if (CatalogStripper.isCatalogLabel(columnLabel) && (type == String.class || type == Object.class)) {
            return type.cast(getString(columnLabel));
        }
        return super.getObject(columnLabel, type);
    }

    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        return rewrite(catalogColumns.contains(columnIndex), super.getObject(columnIndex, map));
    }

    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        return rewrite(CatalogStripper.isCatalogLabel(columnLabel), super.getObject(columnLabel, map));
    }
}
