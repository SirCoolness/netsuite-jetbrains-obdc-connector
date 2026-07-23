package com.netsuite.jetbrains;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a JDBC Connection to intercept getMetaData() and return a patched
 * DatabaseMetaData that strips sandbox suffixes from catalog names.
 *
 * Proxies ALL interfaces the real connection implements (not just java.sql.Connection)
 * to avoid ClassCastException when JetBrains checks for vendor-specific interfaces.
 */
public class ConnectionWrapper {

    /**
     * Wrap the given connection so that getMetaData() returns normalized catalog names.
     */
    public static Connection wrap(final Connection real) {
        // Collect all interfaces from the real connection's class hierarchy
        // so instanceof checks and casts in JetBrains still work
        Class<?>[] interfaces = getAllInterfaces(real.getClass());

        return (Connection) Proxy.newProxyInstance(
            real.getClass().getClassLoader(),
            interfaces,
            new InvocationHandler() {
                // Lazy-init: interceptor needs a reference to the proxied connection
                // but we're inside the proxy factory, so we capture it after creation
                private StatementInterceptor interceptor = null;

                private StatementInterceptor getInterceptor(Object proxyRef) {
                    if (interceptor == null) {
                        interceptor = new StatementInterceptor((Connection) proxyRef);
                    }
                    return interceptor;
                }

                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String methodName = method.getName();
                    JdbcLogger.log("Connection", methodName, args);

                    if ("getMetaData".equals(methodName) && (args == null || args.length == 0)) {
                        DatabaseMetaData realMeta = real.getMetaData();
                        return new MetaDataWrapper(realMeta).asProxy();
                    }
                    // Strip sandbox suffix from getCatalog() too
                    if ("getCatalog".equals(methodName) && (args == null || args.length == 0)) {
                        String catalog = real.getCatalog();
                        String result = CatalogStripper.strip(catalog);
                        JdbcLogger.log("Connection", methodName, args, result);
                        return result;
                    }
                    // Wrap statements to intercept Oracle-specific queries
                    if ("createStatement".equals(methodName)) {
                        Statement stmt = (Statement) method.invoke(real, args);
                        return getInterceptor(proxy).wrapStatement(stmt);
                    }
                    if ("prepareStatement".equals(methodName)) {
                        // PreparedStatement queries are set at creation time — pass through
                        // (JetBrains' OraDialect uses createStatement + executeQuery, not prepared)
                        return method.invoke(real, args);
                    }
                    // unwrap should return the real connection
                    if ("unwrap".equals(methodName) && args != null && args.length == 1) {
                        Class<?> iface = (Class<?>) args[0];
                        if (iface.isInstance(real)) {
                            return real;
                        }
                    }
                    if ("isWrapperFor".equals(methodName) && args != null && args.length == 1) {
                        Class<?> iface = (Class<?>) args[0];
                        return iface.isInstance(real);
                    }
                    try {
                        Object result = method.invoke(real, args);
                        // Wrap any Statement returned from other methods too
                        if (result instanceof Statement && !(result instanceof java.sql.PreparedStatement)) {
                            return getInterceptor(proxy).wrapStatement((Statement) result);
                        }
                        return result;
                    } catch (InvocationTargetException e) {
                        JdbcLogger.logException("Connection", methodName, e.getCause());
                        throw e.getCause();
                    }
                }
            }
        );
    }

    private static Class<?>[] getAllInterfaces(Class<?> clazz) {
        List<Class<?>> interfaces = new ArrayList<Class<?>>();
        while (clazz != null) {
            for (Class<?> iface : clazz.getInterfaces()) {
                if (!interfaces.contains(iface)) {
                    interfaces.add(iface);
                }
            }
            clazz = clazz.getSuperclass();
        }
        // Ensure Connection is in there
        if (!interfaces.contains(Connection.class)) {
            interfaces.add(Connection.class);
        }
        return interfaces.toArray(new Class<?>[0]);
    }
}
