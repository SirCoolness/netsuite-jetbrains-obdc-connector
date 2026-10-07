package com.netsuite.jetbrains;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Connection proxy that:
 * - returns a {@link MetaDataWrapper} from getMetaData() and wraps every statement in a
 *   {@link StatementWrapper}, so nothing hands JetBrains the raw OpenAccess objects;
 * - strips sandbox suffixes from getCatalog();
 * - transparently reconnects (with a fresh nonce) once when NetSuite reports
 *   "Connection expired", for read-only calls, unless the open transaction has written.
 *
 * Proxies ALL interfaces the real connection implements (not just java.sql.Connection)
 * to avoid ClassCastException when JetBrains checks for vendor-specific interfaces.
 */
final class ConnectionWrapper implements InvocationHandler {

    /** Opens a new real connection; for nonce auth each call signs a fresh nonce. */
    interface Connector {
        Connection connect() throws SQLException;
    }

    interface Call {
        Object call() throws Throwable;
    }

    /** Connection methods that only read state and are safe to repeat after a reconnect. */
    private static final Set<String> RETRYABLE = new HashSet<String>(Arrays.asList(
        "getCatalog", "getMetaData", "nativeSQL", "getWarnings"));

    private static final Set<String> STATEMENT_FACTORIES = new HashSet<String>(Arrays.asList(
        "createStatement", "prepareStatement", "prepareCall"));

    private final Connector connector;
    /** Session settings (setReadOnly, setCatalog, ...) replayed onto a reconnected session. */
    private final Map<String, Object[]> settings = new LinkedHashMap<String, Object[]>();
    private final Map<String, Method> settingMethods = new LinkedHashMap<String, Method>();
    private final MetaDataWrapper metaData = new MetaDataWrapper(this);
    private volatile Connection real;
    private volatile int generation;
    private volatile boolean autoCommit = true;
    /** A non-query ran since the last commit/rollback; such a transaction is never replayed. */
    private volatile boolean wroteInTransaction;
    private volatile boolean closed;
    private Connection proxy;
    private DatabaseMetaData realMetaData;
    private int realMetaDataGeneration = -1;
    private String schema;
    private int schemaGeneration = -1;

    private ConnectionWrapper(Connector connector, Connection real) {
        this.connector = connector;
        this.real = real;
    }

    static Connection wrap(Connector connector) throws SQLException {
        Connection real = open(connector);
        ConnectionWrapper handler = new ConnectionWrapper(connector, real);
        handler.proxy = Proxies.create(real, Connection.class, handler);
        return handler.proxy;
    }

    private static Connection open(Connector connector) throws SQLException {
        Connection conn = connector.connect();
        if (conn == null) {
            throw new SQLException("The NetSuite OpenAccess driver did not accept the connection URL.");
        }
        return conn;
    }

    Connection proxy() {
        return proxy;
    }

    Connection real() {
        return real;
    }

    int generation() {
        return generation;
    }

    synchronized DatabaseMetaData realMetaData() throws SQLException {
        if (realMetaDataGeneration != generation) {
            realMetaData = real.getMetaData();
            realMetaDataGeneration = generation;
        }
        return realMetaData;
    }

    public Object invoke(Object proxyRef, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        JdbcLogger.log("Connection", name, args);
        try {
            Object common = Proxies.common(proxyRef, method, args, () -> real);
            if (common != Proxies.UNHANDLED) {
                return common;
            }
            if ("getMetaData".equals(name) && Proxies.isNoArg(args)) {
                return metaData.proxy();
            }
            if ("getCatalog".equals(name) && Proxies.isNoArg(args)) {
                return CatalogStripper.strip((String) withReconnect(() -> real.getCatalog()));
            }
            if (STATEMENT_FACTORIES.contains(name)) {
                return StatementWrapper.wrap(this, method, args);
            }
            if ("getSchema".equals(name) && Proxies.isNoArg(args)) {
                return schema();
            }
            if ("close".equals(name)) {
                closed = true;
            }
            if (("commit".equals(name) || "rollback".equals(name)) && Proxies.isNoArg(args)) {
                wroteInTransaction = false;
            }
            if (name.startsWith("set") && !"setSavepoint".equals(name) && !Proxies.isNoArg(args)) {
                Object result = withReconnect(() -> Proxies.invoke(real, method, args));
                rememberSetting(method, args);
                return result;
            }
            if (RETRYABLE.contains(name)) {
                return withReconnect(() -> Proxies.invoke(real, method, args));
            }
            return Proxies.invoke(real, method, args);
        } catch (Throwable t) {
            JdbcLogger.logException("Connection", name, t);
            throw t;
        }
    }

    private synchronized void rememberSetting(Method method, Object[] args) {
        String name = method.getName();
        if ("setAutoCommit".equals(name)) {
            autoCommit = (Boolean) args[0];
            wroteInTransaction = false;
        }
        // setClientInfo(name, value) is keyed per property, everything else per method
        String key = args.length == 2 && args[0] instanceof String ? name + ":" + args[0] : name;
        settings.remove(key);
        settings.put(key, args.clone());
        settingMethods.put(key, method);
    }

    void markWrite() {
        if (!autoCommit) {
            wroteInTransaction = true;
        }
    }

    /** The session (and its transaction) is gone, so nothing written in it can be lost by a retry. */
    void sessionExpired() {
        wroteInTransaction = false;
    }

    /**
     * NetSuite reports the user name ("TBA") as the current schema and ignores setSchema, so
     * the IDE's "current schema" would point at a schema that does not exist. Answer with the
     * schema last set, else the only schema the role can see, else what the server says.
     */
    private synchronized String schema() throws Throwable {
        Object[] set = settings.get("setSchema");
        if (set != null) {
            return (String) set[0];
        }
        if (schemaGeneration != generation) {
            schema = (String) withReconnect(() -> {
                List<String> schemas = new ArrayList<String>();
                try (ResultSet rs = realMetaData().getSchemas()) {
                    while (rs.next()) {
                        schemas.add(rs.getString("TABLE_SCHEM"));
                    }
                }
                return schemas.size() == 1 ? schemas.get(0) : real.getSchema();
            });
            schemaGeneration = generation;
        }
        return schema;
    }

    /**
     * Run a read-only call; if NetSuite says the session expired, reconnect with a fresh
     * nonce and run it once more. Never retries after close() or once the open transaction
     * has run a non-query (JetBrains drivers often default to manual commit, and a
     * transaction that has only read is safe to restart on a new session).
     * The call must look up {@link #real()} itself so the retry reaches the new session.
     */
    Object withReconnect(Call call) throws Throwable {
        int seen = generation;
        try {
            return call.call();
        } catch (SQLException e) {
            if (closed || wroteInTransaction || !isExpired(e)) {
                throw e;
            }
            JdbcLogger.write("Connection expired; reconnecting with a fresh nonce and retrying once");
            reconnect(seen);
            return call.call();
        }
    }

    private synchronized void reconnect(int seenGeneration) throws SQLException {
        if (generation != seenGeneration) {
            return; // another thread already reconnected
        }
        Connection fresh = open(connector);
        for (Map.Entry<String, Object[]> setting : settings.entrySet()) {
            try {
                settingMethods.get(setting.getKey()).invoke(fresh, setting.getValue());
            } catch (Exception e) {
                JdbcLogger.write("Could not restore " + setting.getKey() + " after reconnect: " + e);
            }
        }
        Connection old = real;
        real = fresh;
        generation++;
        try {
            old.close();
        } catch (SQLException ignored) {
        }
    }

    static boolean isExpired(SQLException e) {
        List<Throwable> seen = new ArrayList<Throwable>();
        for (Throwable t = e; t != null && !seen.contains(t); t = t.getCause()) {
            seen.add(t);
            String message = t.getMessage();
            if (message != null && message.toLowerCase().contains("connection expired")) {
                return true;
            }
        }
        SQLException next = e.getNextException();
        return next != null && next != e && isExpired(next);
    }
}
