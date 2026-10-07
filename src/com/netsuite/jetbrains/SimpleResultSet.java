package com.netsuite.jetbrains;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * In-memory ResultSet for metadata results the wrapper rewrites (catalogs, schemas,
 * table types, deduplicated primary keys) or synthesizes (empty results for metadata
 * calls the server does not support). Values keep their Java types; typed getters
 * convert them the way a driver would, and wasNull() reflects the last value read.
 */
public class SimpleResultSet implements ResultSet {

    /** Decides whether a copied row is kept; may rewrite the row in place. */
    public interface RowFilter {
        boolean keep(Object[] row, SimpleResultSet target) throws SQLException;
    }

    private final String[] columnNames;
    private final int[] columnTypes;
    private final List<Object[]> rows;
    private int cursor = -1;
    private boolean closed = false;
    private boolean lastWasNull = false;

    public SimpleResultSet(String[] columnNames, int[] columnTypes, List<Object[]> rows) {
        this.columnNames = columnNames;
        this.columnTypes = columnTypes;
        this.rows = rows;
    }

    /** All-VARCHAR result, e.g. an empty result for an unsupported metadata call. */
    public SimpleResultSet(String[] columnNames, List<Object[]> rows) {
        this(columnNames, varchar(columnNames.length), rows);
    }

    private static int[] varchar(int count) {
        int[] types = new int[count];
        java.util.Arrays.fill(types, Types.VARCHAR);
        return types;
    }

    /**
     * Copy a result set into memory (closing it), keeping its column labels and types.
     */
    public static SimpleResultSet copy(ResultSet source, RowFilter filter) throws SQLException {
        try {
            ResultSetMetaData meta = source.getMetaData();
            int count = meta.getColumnCount();
            String[] names = new String[count];
            int[] types = new int[count];
            for (int i = 0; i < count; i++) {
                names[i] = meta.getColumnLabel(i + 1);
                types[i] = meta.getColumnType(i + 1);
            }
            SimpleResultSet target = new SimpleResultSet(names, types, new ArrayList<Object[]>());
            while (source.next()) {
                Object[] row = new Object[count];
                for (int i = 0; i < count; i++) {
                    row[i] = source.getObject(i + 1);
                }
                if (filter.keep(row, target)) {
                    target.rows.add(row);
                }
            }
            return target;
        } finally {
            source.close();
        }
    }

    /** Same columns, different rows. */
    public SimpleResultSet withRows(List<Object[]> newRows) {
        return new SimpleResultSet(columnNames, columnTypes, newRows);
    }

    public String[] columnNames() {
        return columnNames;
    }

    /** Zero-based index of the column, or -1 if absent. */
    public int indexOf(String label) {
        for (int i = 0; i < columnNames.length; i++) {
            if (columnNames[i].equalsIgnoreCase(label)) return i;
        }
        return -1;
    }

    private Object value(int columnIndex) throws SQLException {
        if (cursor < 0 || cursor >= rows.size()) {
            throw new SQLException("No current row");
        }
        if (columnIndex < 1 || columnIndex > columnNames.length) {
            throw new SQLException("Column index out of range: " + columnIndex);
        }
        Object v = rows.get(cursor)[columnIndex - 1];
        lastWasNull = v == null;
        return v;
    }

    private Number number(int columnIndex) throws SQLException {
        Object v = value(columnIndex);
        if (v == null) return 0;
        if (v instanceof Number) return (Number) v;
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        try {
            return new BigDecimal(v.toString().trim());
        } catch (NumberFormatException e) {
            throw new SQLException("Value is not numeric: " + v);
        }
    }

    public boolean next() throws SQLException { return ++cursor < rows.size(); }
    public void close() throws SQLException { closed = true; }
    public boolean isClosed() throws SQLException { return closed; }
    public boolean wasNull() throws SQLException { return lastWasNull; }

    public Object getObject(int i) throws SQLException { return value(i); }
    public String getString(int i) throws SQLException { Object v = value(i); return v == null ? null : v.toString(); }
    public boolean getBoolean(int i) throws SQLException {
        Object v = value(i);
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        String s = v.toString().trim();
        return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("y") || s.equalsIgnoreCase("yes") || s.equals("1");
    }
    public byte getByte(int i) throws SQLException { return number(i).byteValue(); }
    public short getShort(int i) throws SQLException { return number(i).shortValue(); }
    public int getInt(int i) throws SQLException { return number(i).intValue(); }
    public long getLong(int i) throws SQLException { return number(i).longValue(); }
    public float getFloat(int i) throws SQLException { return number(i).floatValue(); }
    public double getDouble(int i) throws SQLException { return number(i).doubleValue(); }
    public BigDecimal getBigDecimal(int i) throws SQLException {
        Object v = value(i);
        return v == null ? null : v instanceof BigDecimal ? (BigDecimal) v : new BigDecimal(number(i).toString());
    }
    @Deprecated public BigDecimal getBigDecimal(int i, int scale) throws SQLException {
        BigDecimal v = getBigDecimal(i);
        return v == null ? null : v.setScale(scale, BigDecimal.ROUND_HALF_UP);
    }
    public byte[] getBytes(int i) throws SQLException {
        Object v = value(i);
        return v == null ? null : v instanceof byte[] ? (byte[]) v : v.toString().getBytes(StandardCharsets.UTF_8);
    }
    public Date getDate(int i) throws SQLException { Object v = value(i); return v instanceof Date ? (Date) v : null; }
    public Time getTime(int i) throws SQLException { Object v = value(i); return v instanceof Time ? (Time) v : null; }
    public Timestamp getTimestamp(int i) throws SQLException { Object v = value(i); return v instanceof Timestamp ? (Timestamp) v : null; }
    public Date getDate(int i, Calendar c) throws SQLException { return getDate(i); }
    public Time getTime(int i, Calendar c) throws SQLException { return getTime(i); }
    public Timestamp getTimestamp(int i, Calendar c) throws SQLException { return getTimestamp(i); }
    public InputStream getAsciiStream(int i) throws SQLException { byte[] b = getBytes(i); return b == null ? null : new ByteArrayInputStream(b); }
    @Deprecated public InputStream getUnicodeStream(int i) throws SQLException { return getAsciiStream(i); }
    public InputStream getBinaryStream(int i) throws SQLException { return getAsciiStream(i); }
    public Reader getCharacterStream(int i) throws SQLException { String s = getString(i); return s == null ? null : new StringReader(s); }
    public String getNString(int i) throws SQLException { return getString(i); }
    public Reader getNCharacterStream(int i) throws SQLException { return getCharacterStream(i); }
    public Object getObject(int i, Map<String, Class<?>> m) throws SQLException { return getObject(i); }
    public <T> T getObject(int i, Class<T> type) throws SQLException {
        Object v = value(i);
        if (v == null || type.isInstance(v)) return type.cast(v);
        if (type == String.class) return type.cast(v.toString());
        if (type == Integer.class) return type.cast(getInt(i));
        if (type == Long.class) return type.cast(getLong(i));
        if (type == Short.class) return type.cast(getShort(i));
        if (type == Boolean.class) return type.cast(getBoolean(i));
        if (type == BigDecimal.class) return type.cast(getBigDecimal(i));
        throw new SQLException("Cannot convert " + v.getClass().getName() + " to " + type.getName());
    }
    public Ref getRef(int i) throws SQLException { value(i); return null; }
    public Blob getBlob(int i) throws SQLException { value(i); return null; }
    public Clob getClob(int i) throws SQLException { value(i); return null; }
    public Array getArray(int i) throws SQLException { value(i); return null; }
    public NClob getNClob(int i) throws SQLException { value(i); return null; }
    public SQLXML getSQLXML(int i) throws SQLException { value(i); return null; }
    public RowId getRowId(int i) throws SQLException { value(i); return null; }
    public URL getURL(int i) throws SQLException { value(i); return null; }

    public int findColumn(String label) throws SQLException {
        int i = indexOf(label);
        if (i < 0) throw new SQLException("Column not found: " + label);
        return i + 1;
    }

    public Object getObject(String s) throws SQLException { return getObject(findColumn(s)); }
    public String getString(String s) throws SQLException { return getString(findColumn(s)); }
    public boolean getBoolean(String s) throws SQLException { return getBoolean(findColumn(s)); }
    public byte getByte(String s) throws SQLException { return getByte(findColumn(s)); }
    public short getShort(String s) throws SQLException { return getShort(findColumn(s)); }
    public int getInt(String s) throws SQLException { return getInt(findColumn(s)); }
    public long getLong(String s) throws SQLException { return getLong(findColumn(s)); }
    public float getFloat(String s) throws SQLException { return getFloat(findColumn(s)); }
    public double getDouble(String s) throws SQLException { return getDouble(findColumn(s)); }
    public BigDecimal getBigDecimal(String s) throws SQLException { return getBigDecimal(findColumn(s)); }
    @Deprecated public BigDecimal getBigDecimal(String s, int scale) throws SQLException { return getBigDecimal(findColumn(s), scale); }
    public byte[] getBytes(String s) throws SQLException { return getBytes(findColumn(s)); }
    public Date getDate(String s) throws SQLException { return getDate(findColumn(s)); }
    public Time getTime(String s) throws SQLException { return getTime(findColumn(s)); }
    public Timestamp getTimestamp(String s) throws SQLException { return getTimestamp(findColumn(s)); }
    public Date getDate(String s, Calendar c) throws SQLException { return getDate(findColumn(s)); }
    public Time getTime(String s, Calendar c) throws SQLException { return getTime(findColumn(s)); }
    public Timestamp getTimestamp(String s, Calendar c) throws SQLException { return getTimestamp(findColumn(s)); }
    public InputStream getAsciiStream(String s) throws SQLException { return getAsciiStream(findColumn(s)); }
    @Deprecated public InputStream getUnicodeStream(String s) throws SQLException { return getAsciiStream(findColumn(s)); }
    public InputStream getBinaryStream(String s) throws SQLException { return getBinaryStream(findColumn(s)); }
    public Reader getCharacterStream(String s) throws SQLException { return getCharacterStream(findColumn(s)); }
    public String getNString(String s) throws SQLException { return getString(findColumn(s)); }
    public Reader getNCharacterStream(String s) throws SQLException { return getCharacterStream(findColumn(s)); }
    public Object getObject(String s, Map<String, Class<?>> m) throws SQLException { return getObject(findColumn(s)); }
    public <T> T getObject(String s, Class<T> type) throws SQLException { return getObject(findColumn(s), type); }
    public Ref getRef(String s) throws SQLException { return getRef(findColumn(s)); }
    public Blob getBlob(String s) throws SQLException { return getBlob(findColumn(s)); }
    public Clob getClob(String s) throws SQLException { return getClob(findColumn(s)); }
    public Array getArray(String s) throws SQLException { return getArray(findColumn(s)); }
    public NClob getNClob(String s) throws SQLException { return getNClob(findColumn(s)); }
    public SQLXML getSQLXML(String s) throws SQLException { return getSQLXML(findColumn(s)); }
    public RowId getRowId(String s) throws SQLException { return getRowId(findColumn(s)); }
    public URL getURL(String s) throws SQLException { return getURL(findColumn(s)); }

    public ResultSetMetaData getMetaData() throws SQLException {
        return new ResultSetMetaData() {
            public int getColumnCount() { return columnNames.length; }
            public String getColumnName(int column) { return columnNames[column - 1]; }
            public String getColumnLabel(int column) { return columnNames[column - 1]; }
            public int getColumnType(int column) { return columnTypes[column - 1]; }
            public String getColumnTypeName(int column) { return JDBCType.valueOf(columnTypes[column - 1]).getName(); }
            public String getColumnClassName(int column) { return Object.class.getName(); }
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

    public SQLWarning getWarnings() throws SQLException { return null; }
    public void clearWarnings() throws SQLException {}
    public String getCursorName() throws SQLException { return null; }
    public Statement getStatement() throws SQLException { return null; }
    public boolean isBeforeFirst() throws SQLException { return cursor < 0 && !rows.isEmpty(); }
    public boolean isAfterLast() throws SQLException { return cursor >= rows.size() && !rows.isEmpty(); }
    public boolean isFirst() throws SQLException { return cursor == 0 && !rows.isEmpty(); }
    public boolean isLast() throws SQLException { return cursor == rows.size() - 1 && !rows.isEmpty(); }
    public void beforeFirst() throws SQLException { cursor = -1; }
    public void afterLast() throws SQLException { cursor = rows.size(); }
    public boolean first() throws SQLException { cursor = 0; return !rows.isEmpty(); }
    public boolean last() throws SQLException { cursor = rows.size() - 1; return !rows.isEmpty(); }
    public int getRow() throws SQLException { return cursor >= 0 && cursor < rows.size() ? cursor + 1 : 0; }
    public boolean absolute(int i) throws SQLException { cursor = i >= 0 ? i - 1 : rows.size() + i; return cursor >= 0 && cursor < rows.size(); }
    public boolean relative(int i) throws SQLException { cursor += i; return cursor >= 0 && cursor < rows.size(); }
    public boolean previous() throws SQLException { cursor--; return cursor >= 0; }
    public void setFetchDirection(int i) throws SQLException {}
    public int getFetchDirection() throws SQLException { return FETCH_FORWARD; }
    public void setFetchSize(int i) throws SQLException {}
    public int getFetchSize() throws SQLException { return 0; }
    public int getType() throws SQLException { return TYPE_SCROLL_INSENSITIVE; }
    public int getConcurrency() throws SQLException { return CONCUR_READ_ONLY; }
    public int getHoldability() throws SQLException { return HOLD_CURSORS_OVER_COMMIT; }
    public boolean rowUpdated() throws SQLException { return false; }
    public boolean rowInserted() throws SQLException { return false; }
    public boolean rowDeleted() throws SQLException { return false; }
    public <T> T unwrap(Class<T> t) throws SQLException { throw new SQLException("Not a wrapper for " + t.getName()); }
    public boolean isWrapperFor(Class<?> t) { return false; }

    // Read-only: every update method is unsupported
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
    public void updateRef(int i, Ref v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateRef(String s, Ref v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBlob(int i, Blob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateBlob(String s, Blob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateClob(int i, Clob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateClob(String s, Clob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateArray(int i, Array v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateArray(String s, Array v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateRowId(int i, RowId v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateRowId(String s, RowId v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNString(int i, String v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNString(String s, String v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNClob(int i, NClob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateNClob(String s, NClob v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateSQLXML(int i, SQLXML v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    public void updateSQLXML(String s, SQLXML v) throws SQLException { throw new SQLFeatureNotSupportedException(); }
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
}
