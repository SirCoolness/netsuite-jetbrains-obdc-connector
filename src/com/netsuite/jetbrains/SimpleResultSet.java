package com.netsuite.jetbrains;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Minimal in-memory ResultSet for returning small metadata result sets
 * (catalogs, schemas) after rewriting values.
 */
public class SimpleResultSet implements ResultSet {

    private final String[] columnNames;
    private final List<String[]> rows;
    private int cursor = -1;
    private boolean closed = false;

    public SimpleResultSet(String[] columnNames, List<String[]> rows) {
        this.columnNames = columnNames;
        this.rows = rows;
    }

    public boolean next() throws SQLException { return ++cursor < rows.size(); }
    public void close() throws SQLException { closed = true; }
    public boolean isClosed() throws SQLException { return closed; }

    public String getString(int columnIndex) throws SQLException {
        return rows.get(cursor)[columnIndex - 1];
    }

    public String getString(String columnLabel) throws SQLException {
        for (int i = 0; i < columnNames.length; i++) {
            if (columnNames[i].equalsIgnoreCase(columnLabel)) {
                return rows.get(cursor)[i];
            }
        }
        throw new SQLException("Column not found: " + columnLabel);
    }

    public ResultSetMetaData getMetaData() throws SQLException {
        return new ResultSetMetaData() {
            public int getColumnCount() { return columnNames.length; }
            public String getColumnName(int column) { return columnNames[column - 1]; }
            public String getColumnLabel(int column) { return columnNames[column - 1]; }
            public int getColumnType(int column) { return Types.VARCHAR; }
            public String getColumnTypeName(int column) { return "VARCHAR"; }
            public String getColumnClassName(int column) { return String.class.getName(); }
            public int isNullable(int column) { return columnNullable; }
            public boolean isAutoIncrement(int column) { return false; }
            public boolean isCaseSensitive(int column) { return true; }
            public boolean isSearchable(int column) { return true; }
            public boolean isCurrency(int column) { return false; }
            public int getColumnDisplaySize(int column) { return 256; }
            public int getPrecision(int column) { return 0; }
            public int getScale(int column) { return 0; }
            public String getSchemaName(int column) { return ""; }
            public String getTableName(int column) { return ""; }
            public String getCatalogName(int column) { return ""; }
            public boolean isSigned(int column) { return false; }
            public boolean isReadOnly(int column) { return true; }
            public boolean isWritable(int column) { return false; }
            public boolean isDefinitelyWritable(int column) { return false; }
            public <T> T unwrap(Class<T> t) throws SQLException { throw new SQLException("Not supported"); }
            public boolean isWrapperFor(Class<?> t) { return false; }
        };
    }

    // Minimal stubs for unused methods
    public boolean wasNull() throws SQLException { return false; }
    public boolean getBoolean(int i) throws SQLException { return false; }
    public byte getByte(int i) throws SQLException { return 0; }
    public short getShort(int i) throws SQLException { return 0; }
    public int getInt(int i) throws SQLException { return 0; }
    public long getLong(int i) throws SQLException { return 0; }
    public float getFloat(int i) throws SQLException { return 0; }
    public double getDouble(int i) throws SQLException { return 0; }
    @Deprecated public BigDecimal getBigDecimal(int i, int s) throws SQLException { return null; }
    public byte[] getBytes(int i) throws SQLException { return null; }
    public Date getDate(int i) throws SQLException { return null; }
    public Time getTime(int i) throws SQLException { return null; }
    public Timestamp getTimestamp(int i) throws SQLException { return null; }
    public InputStream getAsciiStream(int i) throws SQLException { return null; }
    @Deprecated public InputStream getUnicodeStream(int i) throws SQLException { return null; }
    public InputStream getBinaryStream(int i) throws SQLException { return null; }
    public boolean getBoolean(String s) throws SQLException { return false; }
    public byte getByte(String s) throws SQLException { return 0; }
    public short getShort(String s) throws SQLException { return 0; }
    public int getInt(String s) throws SQLException { return 0; }
    public long getLong(String s) throws SQLException { return 0; }
    public float getFloat(String s) throws SQLException { return 0; }
    public double getDouble(String s) throws SQLException { return 0; }
    @Deprecated public BigDecimal getBigDecimal(String s, int i) throws SQLException { return null; }
    public byte[] getBytes(String s) throws SQLException { return null; }
    public Date getDate(String s) throws SQLException { return null; }
    public Time getTime(String s) throws SQLException { return null; }
    public Timestamp getTimestamp(String s) throws SQLException { return null; }
    public InputStream getAsciiStream(String s) throws SQLException { return null; }
    @Deprecated public InputStream getUnicodeStream(String s) throws SQLException { return null; }
    public InputStream getBinaryStream(String s) throws SQLException { return null; }
    public SQLWarning getWarnings() throws SQLException { return null; }
    public void clearWarnings() throws SQLException {}
    public String getCursorName() throws SQLException { return null; }
    public Object getObject(int i) throws SQLException { return getString(i); }
    public Object getObject(String s) throws SQLException { return getString(s); }
    public int findColumn(String s) throws SQLException {
        for (int i = 0; i < columnNames.length; i++) {
            if (columnNames[i].equalsIgnoreCase(s)) return i + 1;
        }
        throw new SQLException("Column not found: " + s);
    }
    public Reader getCharacterStream(int i) throws SQLException { return null; }
    public Reader getCharacterStream(String s) throws SQLException { return null; }
    public BigDecimal getBigDecimal(int i) throws SQLException { return null; }
    public BigDecimal getBigDecimal(String s) throws SQLException { return null; }
    public boolean isBeforeFirst() throws SQLException { return cursor < 0; }
    public boolean isAfterLast() throws SQLException { return cursor >= rows.size(); }
    public boolean isFirst() throws SQLException { return cursor == 0; }
    public boolean isLast() throws SQLException { return cursor == rows.size() - 1; }
    public void beforeFirst() throws SQLException { cursor = -1; }
    public void afterLast() throws SQLException { cursor = rows.size(); }
    public boolean first() throws SQLException { cursor = 0; return !rows.isEmpty(); }
    public boolean last() throws SQLException { cursor = rows.size() - 1; return !rows.isEmpty(); }
    public int getRow() throws SQLException { return cursor + 1; }
    public boolean absolute(int i) throws SQLException { cursor = i - 1; return cursor >= 0 && cursor < rows.size(); }
    public boolean relative(int i) throws SQLException { cursor += i; return cursor >= 0 && cursor < rows.size(); }
    public boolean previous() throws SQLException { cursor--; return cursor >= 0; }
    public void setFetchDirection(int i) throws SQLException {}
    public int getFetchDirection() throws SQLException { return FETCH_FORWARD; }
    public void setFetchSize(int i) throws SQLException {}
    public int getFetchSize() throws SQLException { return 0; }
    public int getType() throws SQLException { return TYPE_FORWARD_ONLY; }
    public int getConcurrency() throws SQLException { return CONCUR_READ_ONLY; }
    public boolean rowUpdated() throws SQLException { return false; }
    public boolean rowInserted() throws SQLException { return false; }
    public boolean rowDeleted() throws SQLException { return false; }
    public void updateNull(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBoolean(int i, boolean v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateByte(int i, byte v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateShort(int i, short v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateInt(int i, int v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateLong(int i, long v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateFloat(int i, float v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateDouble(int i, double v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBigDecimal(int i, BigDecimal v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateString(int i, String v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBytes(int i, byte[] v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateDate(int i, Date v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateTime(int i, Time v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateTimestamp(int i, Timestamp v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateAsciiStream(int i, InputStream v, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBinaryStream(int i, InputStream v, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateCharacterStream(int i, Reader v, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateObject(int i, Object v, int s) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateObject(int i, Object v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNull(String s) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBoolean(String s, boolean v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateByte(String s, byte v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateShort(String s, short v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateInt(String s, int v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateLong(String s, long v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateFloat(String s, float v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateDouble(String s, double v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBigDecimal(String s, BigDecimal v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateString(String s, String v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBytes(String s, byte[] v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateDate(String s, Date v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateTime(String s, Time v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateTimestamp(String s, Timestamp v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateAsciiStream(String s, InputStream v, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBinaryStream(String s, InputStream v, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateCharacterStream(String s, Reader v, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateObject(String s, Object v, int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateObject(String s, Object v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void insertRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void deleteRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void refreshRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void cancelRowUpdates() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void moveToInsertRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void moveToCurrentRow() throws SQLException {}
    public Statement getStatement() throws SQLException { return null; }
    public Object getObject(int i, Map<String,Class<?>> m) throws SQLException { return getString(i); }
    public Ref getRef(int i) throws SQLException { return null; }
    public Blob getBlob(int i) throws SQLException { return null; }
    public Clob getClob(int i) throws SQLException { return null; }
    public Array getArray(int i) throws SQLException { return null; }
    public Object getObject(String s, Map<String,Class<?>> m) throws SQLException { return getString(s); }
    public Ref getRef(String s) throws SQLException { return null; }
    public Blob getBlob(String s) throws SQLException { return null; }
    public Clob getClob(String s) throws SQLException { return null; }
    public Array getArray(String s) throws SQLException { return null; }
    public Date getDate(int i, Calendar c) throws SQLException { return null; }
    public Date getDate(String s, Calendar c) throws SQLException { return null; }
    public Time getTime(int i, Calendar c) throws SQLException { return null; }
    public Time getTime(String s, Calendar c) throws SQLException { return null; }
    public Timestamp getTimestamp(int i, Calendar c) throws SQLException { return null; }
    public Timestamp getTimestamp(String s, Calendar c) throws SQLException { return null; }
    public URL getURL(int i) throws SQLException { return null; }
    public URL getURL(String s) throws SQLException { return null; }
    public void updateRef(int i, Ref v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateRef(String s, Ref v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBlob(int i, Blob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBlob(String s, Blob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateClob(int i, Clob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateClob(String s, Clob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateArray(int i, Array v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateArray(String s, Array v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public RowId getRowId(int i) throws SQLException { return null; }
    public RowId getRowId(String s) throws SQLException { return null; }
    public void updateRowId(int i, RowId v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateRowId(String s, RowId v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public int getHoldability() throws SQLException { return HOLD_CURSORS_OVER_COMMIT; }
    public void updateNString(int i, String v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNString(String s, String v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNClob(int i, NClob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNClob(String s, NClob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public NClob getNClob(int i) throws SQLException { return null; }
    public NClob getNClob(String s) throws SQLException { return null; }
    public SQLXML getSQLXML(int i) throws SQLException { return null; }
    public SQLXML getSQLXML(String s) throws SQLException { return null; }
    public void updateSQLXML(int i, SQLXML v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateSQLXML(String s, SQLXML v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public String getNString(int i) throws SQLException { return getString(i); }
    public String getNString(String s) throws SQLException { return getString(s); }
    public Reader getNCharacterStream(int i) throws SQLException { return null; }
    public Reader getNCharacterStream(String s) throws SQLException { return null; }
    public void updateNCharacterStream(int i, Reader v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNCharacterStream(String s, Reader v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateAsciiStream(int i, InputStream v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBinaryStream(int i, InputStream v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateCharacterStream(int i, Reader v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateAsciiStream(String s, InputStream v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBinaryStream(String s, InputStream v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateCharacterStream(String s, Reader v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBlob(int i, InputStream v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBlob(String s, InputStream v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateClob(int i, Reader v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateClob(String s, Reader v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNClob(int i, Reader v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNClob(String s, Reader v, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNCharacterStream(int i, Reader v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNCharacterStream(String s, Reader v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateAsciiStream(int i, InputStream v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBinaryStream(int i, InputStream v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateCharacterStream(int i, Reader v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateAsciiStream(String s, InputStream v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBinaryStream(String s, InputStream v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateCharacterStream(String s, Reader v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBlob(int i, InputStream v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBlob(String s, InputStream v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateClob(int i, Reader v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateClob(String s, Reader v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNClob(int i, Reader v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNClob(String s, Reader v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public <T> T getObject(int i, Class<T> t) throws SQLException { return null; }
    public <T> T getObject(String s, Class<T> t) throws SQLException { return null; }
    public <T> T unwrap(Class<T> t) throws SQLException { throw new SQLException("Not supported"); }
    public boolean isWrapperFor(Class<?> t) { return false; }
}
