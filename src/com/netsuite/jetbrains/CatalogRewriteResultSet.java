package com.netsuite.jetbrains;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Wraps a ResultSet to rewrite a specific column's value through CatalogStripper.
 * Used for getTables(), getColumns(), etc. where catalog is in a known column position.
 */
public class CatalogRewriteResultSet extends ResultSetDelegate {

    private final int catalogColumnIndex;

    public CatalogRewriteResultSet(ResultSet delegate, int catalogColumnIndex) {
        super(delegate);
        this.catalogColumnIndex = catalogColumnIndex;
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        String value = super.getString(columnIndex);
        if (columnIndex == catalogColumnIndex && value != null) {
            return CatalogStripper.strip(value);
        }
        return value;
    }

    @Override
    public String getString(String columnLabel) throws SQLException {
        String value = super.getString(columnLabel);
        if (columnLabel != null && columnLabel.equalsIgnoreCase("TABLE_CAT") && value != null) {
            return CatalogStripper.strip(value);
        }
        if (columnLabel != null && columnLabel.equalsIgnoreCase("TABLE_CATALOG") && value != null) {
            return CatalogStripper.strip(value);
        }
        return value;
    }
}
