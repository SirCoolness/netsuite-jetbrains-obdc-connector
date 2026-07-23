package com.netsuite.jetbrains;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * Base class that delegates all DatabaseMetaData calls to an underlying instance.
 * Uses reflection-based delegation to avoid implementing 150+ methods manually.
 * Subclasses override specific methods (getCatalogs, getSchemas, getTables, etc.).
 */
public class DatabaseMetaDataDelegate {

    private final DatabaseMetaData delegate;

    public DatabaseMetaDataDelegate(DatabaseMetaData delegate) {
        this.delegate = delegate;
    }

    public DatabaseMetaData getDelegate() {
        return delegate;
    }

    // Methods that subclasses override
    public String getDatabaseProductName() throws SQLException { return delegate.getDatabaseProductName(); }
    public ResultSet getCatalogs() throws SQLException { return delegate.getCatalogs(); }
    public ResultSet getSchemas() throws SQLException { return delegate.getSchemas(); }
    public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
        return delegate.getSchemas(catalog, schemaPattern);
    }
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
        return delegate.getTables(catalog, schemaPattern, tableNamePattern, types);
    }
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        return delegate.getColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern);
    }
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        return delegate.getPrimaryKeys(catalog, schema, table);
    }
    public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        return delegate.getImportedKeys(catalog, schema, table);
    }
    public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        return delegate.getExportedKeys(catalog, schema, table);
    }
    public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) throws SQLException {
        return delegate.getIndexInfo(catalog, schema, table, unique, approximate);
    }

    /**
     * Create a DatabaseMetaData proxy that delegates all calls to the wrapper,
     * using the overridden methods where available, and reflection for everything else.
     */
    public DatabaseMetaData asProxy() {
        DatabaseMetaDataDelegate wrapper = this;
        return (DatabaseMetaData) Proxy.newProxyInstance(
            DatabaseMetaData.class.getClassLoader(),
            new Class<?>[]{ DatabaseMetaData.class },
            new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String name = method.getName();
                    JdbcLogger.log("MetaData", name, args);

                    Object result = null;
                    // Route overridden methods to this wrapper
                    try {
                        switch (name) {
                            case "getDatabaseProductName":
                                result = wrapper.getDatabaseProductName();
                                JdbcLogger.log("MetaData", name, args, result);
                                return result;
                            case "getCatalogs":
                                result = wrapper.getCatalogs();
                                JdbcLogger.logResultSetMeta("MetaData.getCatalogs()", (ResultSet) result);
                                return result;
                            case "getSchemas":
                                if (args == null || args.length == 0) {
                                    result = wrapper.getSchemas();
                                } else if (args.length == 2) {
                                    result = wrapper.getSchemas((String) args[0], (String) args[1]);
                                } else {
                                    break;
                                }
                                JdbcLogger.logResultSetMeta("MetaData.getSchemas()", (ResultSet) result);
                                return result;
                            case "getTables":
                                result = wrapper.getTables(
                                    (String) args[0], (String) args[1], (String) args[2], (String[]) args[3]);
                                JdbcLogger.logResultSetMeta("MetaData.getTables()", (ResultSet) result);
                                return result;
                            case "getColumns":
                                result = wrapper.getColumns(
                                    (String) args[0], (String) args[1], (String) args[2], (String) args[3]);
                                JdbcLogger.logResultSetMeta("MetaData.getColumns()", (ResultSet) result);
                                return result;
                            case "getPrimaryKeys":
                                result = wrapper.getPrimaryKeys((String) args[0], (String) args[1], (String) args[2]);
                                return result;
                            case "getImportedKeys":
                                result = wrapper.getImportedKeys((String) args[0], (String) args[1], (String) args[2]);
                                return result;
                            case "getExportedKeys":
                                result = wrapper.getExportedKeys((String) args[0], (String) args[1], (String) args[2]);
                                return result;
                            case "getIndexInfo":
                                result = wrapper.getIndexInfo(
                                    (String) args[0], (String) args[1], (String) args[2],
                                    (Boolean) args[3], (Boolean) args[4]);
                                return result;
                        }
                    } catch (SQLException e) {
                        JdbcLogger.logException("MetaData", name, e);
                        throw e;
                    }

                    // Everything else delegates to the real metadata
                    try {
                        result = method.invoke(delegate, args);
                        JdbcLogger.log("MetaData", name, args, result);
                        return result;
                    } catch (InvocationTargetException e) {
                        JdbcLogger.logException("MetaData", name, e.getCause());
                        throw e.getCause();
                    }
                }
            }
        );
    }
}
