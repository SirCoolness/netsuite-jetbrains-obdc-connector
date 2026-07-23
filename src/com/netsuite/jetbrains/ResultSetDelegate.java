package com.netsuite.jetbrains;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.Map;

/**
 * Delegates all ResultSet methods to an underlying ResultSet.
 * Subclasses override specific methods to modify behavior.
 */
public class ResultSetDelegate implements ResultSet {

    protected final ResultSet delegate;

    public ResultSetDelegate(ResultSet delegate) {
        this.delegate = delegate;
    }

    public boolean next() throws SQLException { return delegate.next(); }
    public void close() throws SQLException { delegate.close(); }
    public boolean wasNull() throws SQLException { return delegate.wasNull(); }
    public String getString(int i) throws SQLException { return delegate.getString(i); }
    public boolean getBoolean(int i) throws SQLException { return delegate.getBoolean(i); }
    public byte getByte(int i) throws SQLException { return delegate.getByte(i); }
    public short getShort(int i) throws SQLException { return delegate.getShort(i); }
    public int getInt(int i) throws SQLException { return delegate.getInt(i); }
    public long getLong(int i) throws SQLException { return delegate.getLong(i); }
    public float getFloat(int i) throws SQLException { return delegate.getFloat(i); }
    public double getDouble(int i) throws SQLException { return delegate.getDouble(i); }
    @Deprecated public BigDecimal getBigDecimal(int i, int s) throws SQLException { return delegate.getBigDecimal(i, s); }
    public byte[] getBytes(int i) throws SQLException { return delegate.getBytes(i); }
    public Date getDate(int i) throws SQLException { return delegate.getDate(i); }
    public Time getTime(int i) throws SQLException { return delegate.getTime(i); }
    public Timestamp getTimestamp(int i) throws SQLException { return delegate.getTimestamp(i); }
    public InputStream getAsciiStream(int i) throws SQLException { return delegate.getAsciiStream(i); }
    @Deprecated public InputStream getUnicodeStream(int i) throws SQLException { return delegate.getUnicodeStream(i); }
    public InputStream getBinaryStream(int i) throws SQLException { return delegate.getBinaryStream(i); }
    public String getString(String s) throws SQLException { return delegate.getString(s); }
    public boolean getBoolean(String s) throws SQLException { return delegate.getBoolean(s); }
    public byte getByte(String s) throws SQLException { return delegate.getByte(s); }
    public short getShort(String s) throws SQLException { return delegate.getShort(s); }
    public int getInt(String s) throws SQLException { return delegate.getInt(s); }
    public long getLong(String s) throws SQLException { return delegate.getLong(s); }
    public float getFloat(String s) throws SQLException { return delegate.getFloat(s); }
    public double getDouble(String s) throws SQLException { return delegate.getDouble(s); }
    @Deprecated public BigDecimal getBigDecimal(String s, int i) throws SQLException { return delegate.getBigDecimal(s, i); }
    public byte[] getBytes(String s) throws SQLException { return delegate.getBytes(s); }
    public Date getDate(String s) throws SQLException { return delegate.getDate(s); }
    public Time getTime(String s) throws SQLException { return delegate.getTime(s); }
    public Timestamp getTimestamp(String s) throws SQLException { return delegate.getTimestamp(s); }
    public InputStream getAsciiStream(String s) throws SQLException { return delegate.getAsciiStream(s); }
    @Deprecated public InputStream getUnicodeStream(String s) throws SQLException { return delegate.getUnicodeStream(s); }
    public InputStream getBinaryStream(String s) throws SQLException { return delegate.getBinaryStream(s); }
    public SQLWarning getWarnings() throws SQLException { return delegate.getWarnings(); }
    public void clearWarnings() throws SQLException { delegate.clearWarnings(); }
    public String getCursorName() throws SQLException { return delegate.getCursorName(); }
    public ResultSetMetaData getMetaData() throws SQLException { return delegate.getMetaData(); }
    public Object getObject(int i) throws SQLException { return delegate.getObject(i); }
    public Object getObject(String s) throws SQLException { return delegate.getObject(s); }
    public int findColumn(String s) throws SQLException { return delegate.findColumn(s); }
    public Reader getCharacterStream(int i) throws SQLException { return delegate.getCharacterStream(i); }
    public Reader getCharacterStream(String s) throws SQLException { return delegate.getCharacterStream(s); }
    public BigDecimal getBigDecimal(int i) throws SQLException { return delegate.getBigDecimal(i); }
    public BigDecimal getBigDecimal(String s) throws SQLException { return delegate.getBigDecimal(s); }
    public boolean isBeforeFirst() throws SQLException { return delegate.isBeforeFirst(); }
    public boolean isAfterLast() throws SQLException { return delegate.isAfterLast(); }
    public boolean isFirst() throws SQLException { return delegate.isFirst(); }
    public boolean isLast() throws SQLException { return delegate.isLast(); }
    public void beforeFirst() throws SQLException { delegate.beforeFirst(); }
    public void afterLast() throws SQLException { delegate.afterLast(); }
    public boolean first() throws SQLException { return delegate.first(); }
    public boolean last() throws SQLException { return delegate.last(); }
    public int getRow() throws SQLException { return delegate.getRow(); }
    public boolean absolute(int i) throws SQLException { return delegate.absolute(i); }
    public boolean relative(int i) throws SQLException { return delegate.relative(i); }
    public boolean previous() throws SQLException { return delegate.previous(); }
    public void setFetchDirection(int i) throws SQLException { delegate.setFetchDirection(i); }
    public int getFetchDirection() throws SQLException { return delegate.getFetchDirection(); }
    public void setFetchSize(int i) throws SQLException { delegate.setFetchSize(i); }
    public int getFetchSize() throws SQLException { return delegate.getFetchSize(); }
    public int getType() throws SQLException { return delegate.getType(); }
    public int getConcurrency() throws SQLException { return delegate.getConcurrency(); }
    public boolean rowUpdated() throws SQLException { return delegate.rowUpdated(); }
    public boolean rowInserted() throws SQLException { return delegate.rowInserted(); }
    public boolean rowDeleted() throws SQLException { return delegate.rowDeleted(); }
    public void updateNull(int i) throws SQLException { delegate.updateNull(i); }
    public void updateBoolean(int i, boolean v) throws SQLException { delegate.updateBoolean(i, v); }
    public void updateByte(int i, byte v) throws SQLException { delegate.updateByte(i, v); }
    public void updateShort(int i, short v) throws SQLException { delegate.updateShort(i, v); }
    public void updateInt(int i, int v) throws SQLException { delegate.updateInt(i, v); }
    public void updateLong(int i, long v) throws SQLException { delegate.updateLong(i, v); }
    public void updateFloat(int i, float v) throws SQLException { delegate.updateFloat(i, v); }
    public void updateDouble(int i, double v) throws SQLException { delegate.updateDouble(i, v); }
    public void updateBigDecimal(int i, BigDecimal v) throws SQLException { delegate.updateBigDecimal(i, v); }
    public void updateString(int i, String v) throws SQLException { delegate.updateString(i, v); }
    public void updateBytes(int i, byte[] v) throws SQLException { delegate.updateBytes(i, v); }
    public void updateDate(int i, Date v) throws SQLException { delegate.updateDate(i, v); }
    public void updateTime(int i, Time v) throws SQLException { delegate.updateTime(i, v); }
    public void updateTimestamp(int i, Timestamp v) throws SQLException { delegate.updateTimestamp(i, v); }
    public void updateAsciiStream(int i, InputStream v, int l) throws SQLException { delegate.updateAsciiStream(i, v, l); }
    public void updateBinaryStream(int i, InputStream v, int l) throws SQLException { delegate.updateBinaryStream(i, v, l); }
    public void updateCharacterStream(int i, Reader v, int l) throws SQLException { delegate.updateCharacterStream(i, v, l); }
    public void updateObject(int i, Object v, int s) throws SQLException { delegate.updateObject(i, v, s); }
    public void updateObject(int i, Object v) throws SQLException { delegate.updateObject(i, v); }
    public void updateNull(String s) throws SQLException { delegate.updateNull(s); }
    public void updateBoolean(String s, boolean v) throws SQLException { delegate.updateBoolean(s, v); }
    public void updateByte(String s, byte v) throws SQLException { delegate.updateByte(s, v); }
    public void updateShort(String s, short v) throws SQLException { delegate.updateShort(s, v); }
    public void updateInt(String s, int v) throws SQLException { delegate.updateInt(s, v); }
    public void updateLong(String s, long v) throws SQLException { delegate.updateLong(s, v); }
    public void updateFloat(String s, float v) throws SQLException { delegate.updateFloat(s, v); }
    public void updateDouble(String s, double v) throws SQLException { delegate.updateDouble(s, v); }
    public void updateBigDecimal(String s, BigDecimal v) throws SQLException { delegate.updateBigDecimal(s, v); }
    public void updateString(String s, String v) throws SQLException { delegate.updateString(s, v); }
    public void updateBytes(String s, byte[] v) throws SQLException { delegate.updateBytes(s, v); }
    public void updateDate(String s, Date v) throws SQLException { delegate.updateDate(s, v); }
    public void updateTime(String s, Time v) throws SQLException { delegate.updateTime(s, v); }
    public void updateTimestamp(String s, Timestamp v) throws SQLException { delegate.updateTimestamp(s, v); }
    public void updateAsciiStream(String s, InputStream v, int l) throws SQLException { delegate.updateAsciiStream(s, v, l); }
    public void updateBinaryStream(String s, InputStream v, int l) throws SQLException { delegate.updateBinaryStream(s, v, l); }
    public void updateCharacterStream(String s, Reader v, int l) throws SQLException { delegate.updateCharacterStream(s, v, l); }
    public void updateObject(String s, Object v, int i) throws SQLException { delegate.updateObject(s, v, i); }
    public void updateObject(String s, Object v) throws SQLException { delegate.updateObject(s, v); }
    public void insertRow() throws SQLException { delegate.insertRow(); }
    public void updateRow() throws SQLException { delegate.updateRow(); }
    public void deleteRow() throws SQLException { delegate.deleteRow(); }
    public void refreshRow() throws SQLException { delegate.refreshRow(); }
    public void cancelRowUpdates() throws SQLException { delegate.cancelRowUpdates(); }
    public void moveToInsertRow() throws SQLException { delegate.moveToInsertRow(); }
    public void moveToCurrentRow() throws SQLException { delegate.moveToCurrentRow(); }
    public Statement getStatement() throws SQLException { return delegate.getStatement(); }
    public Object getObject(int i, Map<String,Class<?>> m) throws SQLException { return delegate.getObject(i, m); }
    public Ref getRef(int i) throws SQLException { return delegate.getRef(i); }
    public Blob getBlob(int i) throws SQLException { return delegate.getBlob(i); }
    public Clob getClob(int i) throws SQLException { return delegate.getClob(i); }
    public Array getArray(int i) throws SQLException { return delegate.getArray(i); }
    public Object getObject(String s, Map<String,Class<?>> m) throws SQLException { return delegate.getObject(s, m); }
    public Ref getRef(String s) throws SQLException { return delegate.getRef(s); }
    public Blob getBlob(String s) throws SQLException { return delegate.getBlob(s); }
    public Clob getClob(String s) throws SQLException { return delegate.getClob(s); }
    public Array getArray(String s) throws SQLException { return delegate.getArray(s); }
    public Date getDate(int i, Calendar c) throws SQLException { return delegate.getDate(i, c); }
    public Date getDate(String s, Calendar c) throws SQLException { return delegate.getDate(s, c); }
    public Time getTime(int i, Calendar c) throws SQLException { return delegate.getTime(i, c); }
    public Time getTime(String s, Calendar c) throws SQLException { return delegate.getTime(s, c); }
    public Timestamp getTimestamp(int i, Calendar c) throws SQLException { return delegate.getTimestamp(i, c); }
    public Timestamp getTimestamp(String s, Calendar c) throws SQLException { return delegate.getTimestamp(s, c); }
    public URL getURL(int i) throws SQLException { return delegate.getURL(i); }
    public URL getURL(String s) throws SQLException { return delegate.getURL(s); }
    public void updateRef(int i, Ref v) throws SQLException { delegate.updateRef(i, v); }
    public void updateRef(String s, Ref v) throws SQLException { delegate.updateRef(s, v); }
    public void updateBlob(int i, Blob v) throws SQLException { delegate.updateBlob(i, v); }
    public void updateBlob(String s, Blob v) throws SQLException { delegate.updateBlob(s, v); }
    public void updateClob(int i, Clob v) throws SQLException { delegate.updateClob(i, v); }
    public void updateClob(String s, Clob v) throws SQLException { delegate.updateClob(s, v); }
    public void updateArray(int i, Array v) throws SQLException { delegate.updateArray(i, v); }
    public void updateArray(String s, Array v) throws SQLException { delegate.updateArray(s, v); }
    public RowId getRowId(int i) throws SQLException { return delegate.getRowId(i); }
    public RowId getRowId(String s) throws SQLException { return delegate.getRowId(s); }
    public void updateRowId(int i, RowId v) throws SQLException { delegate.updateRowId(i, v); }
    public void updateRowId(String s, RowId v) throws SQLException { delegate.updateRowId(s, v); }
    public int getHoldability() throws SQLException { return delegate.getHoldability(); }
    public boolean isClosed() throws SQLException { return delegate.isClosed(); }
    public void updateNString(int i, String v) throws SQLException { delegate.updateNString(i, v); }
    public void updateNString(String s, String v) throws SQLException { delegate.updateNString(s, v); }
    public void updateNClob(int i, NClob v) throws SQLException { delegate.updateNClob(i, v); }
    public void updateNClob(String s, NClob v) throws SQLException { delegate.updateNClob(s, v); }
    public NClob getNClob(int i) throws SQLException { return delegate.getNClob(i); }
    public NClob getNClob(String s) throws SQLException { return delegate.getNClob(s); }
    public SQLXML getSQLXML(int i) throws SQLException { return delegate.getSQLXML(i); }
    public SQLXML getSQLXML(String s) throws SQLException { return delegate.getSQLXML(s); }
    public void updateSQLXML(int i, SQLXML v) throws SQLException { delegate.updateSQLXML(i, v); }
    public void updateSQLXML(String s, SQLXML v) throws SQLException { delegate.updateSQLXML(s, v); }
    public String getNString(int i) throws SQLException { return delegate.getNString(i); }
    public String getNString(String s) throws SQLException { return delegate.getNString(s); }
    public Reader getNCharacterStream(int i) throws SQLException { return delegate.getNCharacterStream(i); }
    public Reader getNCharacterStream(String s) throws SQLException { return delegate.getNCharacterStream(s); }
    public void updateNCharacterStream(int i, Reader v, long l) throws SQLException { delegate.updateNCharacterStream(i, v, l); }
    public void updateNCharacterStream(String s, Reader v, long l) throws SQLException { delegate.updateNCharacterStream(s, v, l); }
    public void updateAsciiStream(int i, InputStream v, long l) throws SQLException { delegate.updateAsciiStream(i, v, l); }
    public void updateBinaryStream(int i, InputStream v, long l) throws SQLException { delegate.updateBinaryStream(i, v, l); }
    public void updateCharacterStream(int i, Reader v, long l) throws SQLException { delegate.updateCharacterStream(i, v, l); }
    public void updateAsciiStream(String s, InputStream v, long l) throws SQLException { delegate.updateAsciiStream(s, v, l); }
    public void updateBinaryStream(String s, InputStream v, long l) throws SQLException { delegate.updateBinaryStream(s, v, l); }
    public void updateCharacterStream(String s, Reader v, long l) throws SQLException { delegate.updateCharacterStream(s, v, l); }
    public void updateBlob(int i, InputStream v, long l) throws SQLException { delegate.updateBlob(i, v, l); }
    public void updateBlob(String s, InputStream v, long l) throws SQLException { delegate.updateBlob(s, v, l); }
    public void updateClob(int i, Reader v, long l) throws SQLException { delegate.updateClob(i, v, l); }
    public void updateClob(String s, Reader v, long l) throws SQLException { delegate.updateClob(s, v, l); }
    public void updateNClob(int i, Reader v, long l) throws SQLException { delegate.updateNClob(i, v, l); }
    public void updateNClob(String s, Reader v, long l) throws SQLException { delegate.updateNClob(s, v, l); }
    public void updateNCharacterStream(int i, Reader v) throws SQLException { delegate.updateNCharacterStream(i, v); }
    public void updateNCharacterStream(String s, Reader v) throws SQLException { delegate.updateNCharacterStream(s, v); }
    public void updateAsciiStream(int i, InputStream v) throws SQLException { delegate.updateAsciiStream(i, v); }
    public void updateBinaryStream(int i, InputStream v) throws SQLException { delegate.updateBinaryStream(i, v); }
    public void updateCharacterStream(int i, Reader v) throws SQLException { delegate.updateCharacterStream(i, v); }
    public void updateAsciiStream(String s, InputStream v) throws SQLException { delegate.updateAsciiStream(s, v); }
    public void updateBinaryStream(String s, InputStream v) throws SQLException { delegate.updateBinaryStream(s, v); }
    public void updateCharacterStream(String s, Reader v) throws SQLException { delegate.updateCharacterStream(s, v); }
    public void updateBlob(int i, InputStream v) throws SQLException { delegate.updateBlob(i, v); }
    public void updateBlob(String s, InputStream v) throws SQLException { delegate.updateBlob(s, v); }
    public void updateClob(int i, Reader v) throws SQLException { delegate.updateClob(i, v); }
    public void updateClob(String s, Reader v) throws SQLException { delegate.updateClob(s, v); }
    public void updateNClob(int i, Reader v) throws SQLException { delegate.updateNClob(i, v); }
    public void updateNClob(String s, Reader v) throws SQLException { delegate.updateNClob(s, v); }
    public <T> T getObject(int i, Class<T> t) throws SQLException { return delegate.getObject(i, t); }
    public <T> T getObject(String s, Class<T> t) throws SQLException { return delegate.getObject(s, t); }
    public <T> T unwrap(Class<T> t) throws SQLException { return delegate.unwrap(t); }
    public boolean isWrapperFor(Class<?> t) throws SQLException { return delegate.isWrapperFor(t); }
}
