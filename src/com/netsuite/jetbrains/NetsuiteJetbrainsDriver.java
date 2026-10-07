package com.netsuite.jetbrains;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JDBC Driver wrapper that auto-generates nonce passwords for NetSuite SuiteAnalytics Connect.
 *
 * When GenerateNonce=true is present in the CustomProperties of the JDBC URL, this driver:
 * 1. Parses the password field as JSON containing HMAC nonce credentials
 * 2. Generates a fresh nonce + timestamp + HMAC-SHA256 signature
 * 3. Delegates to the real OpenAccessDriver with the computed password
 *
 * Without GenerateNonce (or with GenerateNonce=false) the password is passed to the original
 * driver as is. Either way the connection is wrapped (see ConnectionWrapper) so JetBrains'
 * generic introspector works and expired sessions are reopened transparently.
 *
 * Usage in JetBrains:
 *   Driver class: com.netsuite.jetbrains.NetsuiteJetbrainsDriver
 *   URL: jdbc:ns://host:port;...;CustomProperties=(AccountID=...;RoleID=...;GenerateNonce=true)
 *   Password: {"accountId":"...","consumerKey":"...","consumerSecret":"...","tokenId":"...","tokenSecret":"..."}
 */
public class NetsuiteJetbrainsDriver implements Driver {

    private static final String URL_PREFIX = "jdbc:ns:";
    private static final String STOCK_DRIVER = "com.netsuite.jdbc.openaccess.OpenAccessDriver";

    /** The parenthesized CustomProperties block; group 2 is its content. */
    private static final Pattern CUSTOM_PROPERTIES = Pattern.compile(
        "(CustomProperties\\s*=\\s*\\()([^)]*)(\\))", Pattern.CASE_INSENSITIVE);

    /** One GenerateNonce entry of the CustomProperties block; group 1 is its value. */
    private static final Pattern GENERATE_NONCE_ENTRY = Pattern.compile(
        "\\s*GenerateNonce\\s*=\\s*(.*?)\\s*", Pattern.CASE_INSENSITIVE);

    private static final String[] PASSWORD_KEYS = {"password", "PASSWORD", "Password"};

    private final Driver delegate;

    static {
        try {
            NetsuiteJetbrainsDriver driver = new NetsuiteJetbrainsDriver();
            // Loading the stock driver registers it too. Unregister it so DriverManager does not
            // also offer it the JSON password (a guaranteed failed login per connection).
            for (Driver registered : Collections.list(DriverManager.getDrivers())) {
                if (STOCK_DRIVER.equals(registered.getClass().getName())) {
                    DriverManager.deregisterDriver(registered);
                }
            }
            DriverManager.registerDriver(driver);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register NetsuiteJetbrainsDriver", e);
        }
    }

    public NetsuiteJetbrainsDriver() throws SQLException {
        // Instantiate the real NetSuite OpenAccess driver
        try {
            Class<?> driverClass = Class.forName(STOCK_DRIVER);
            this.delegate = (Driver) driverClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new SQLException(
                "Failed to load the NetSuite OpenAccess JDBC driver. " +
                "Ensure netsuite-jbdc.jar (NQjc.jar) is on the classpath.", e);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        JdbcLogger.log("Driver", "connect", new Object[]{url});
        final String serverUrl = stripGenerateNonce(url);
        final Properties props = new Properties();
        if (info != null) {
            props.putAll(info);
        }
        if (!shouldGenerateNonce(url)) {
            return ConnectionWrapper.wrap(() -> delegate.connect(serverUrl, props));
        }

        // Validate the credential JSON up front; each (re)connect then signs a fresh nonce
        final NonceCredentials credentials = NonceCredentials.fromJson(password(props));
        for (String key : PASSWORD_KEYS) {
            props.remove(key);
        }
        return ConnectionWrapper.wrap(() -> {
            Properties signed = new Properties();
            signed.putAll(props);
            signed.setProperty("password", NonceGenerator.generatePassword(credentials));
            return delegate.connect(serverUrl, signed);
        });
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.toLowerCase().startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return delegate.getPropertyInfo(url == null ? null : stripGenerateNonce(url), info);
    }

    @Override
    public int getMajorVersion() {
        return delegate.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return delegate.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return delegate.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    private static String password(Properties props) {
        for (String key : PASSWORD_KEYS) {
            String value = props.getProperty(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * True if CustomProperties contains GenerateNonce=true (or 1/yes/on), in any position
     * and any letter case.
     */
    static boolean shouldGenerateNonce(String url) {
        Matcher block = CUSTOM_PROPERTIES.matcher(url);
        if (!block.find()) {
            return false;
        }
        for (String entry : block.group(2).split(";")) {
            Matcher m = GENERATE_NONCE_ENTRY.matcher(entry);
            if (m.matches()) {
                String value = m.group(1).toLowerCase();
                return value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("on");
            }
        }
        return false;
    }

    /**
     * Remove every GenerateNonce entry from CustomProperties (the server does not know it),
     * keeping the other entries and their separators intact wherever the flag appeared, and
     * the whole block if the flag was its only entry.
     */
    static String stripGenerateNonce(String url) {
        Matcher block = CUSTOM_PROPERTIES.matcher(url);
        if (!block.find()) {
            return url;
        }
        List<String> kept = new ArrayList<String>();
        for (String entry : block.group(2).split(";")) {
            if (!entry.trim().isEmpty() && !GENERATE_NONCE_ENTRY.matcher(entry).matches()) {
                kept.add(entry.trim());
            }
        }
        if (!kept.isEmpty()) {
            return url.substring(0, block.start(2)) + String.join(";", kept) + url.substring(block.end(2));
        }
        // nothing left: drop the whole CustomProperties=() block and its leading separator
        int start = block.start(1);
        while (start > 0 && Character.isWhitespace(url.charAt(start - 1))) start--;
        if (start > 0 && url.charAt(start - 1) == ';') start--;
        return url.substring(0, start) + url.substring(block.end(3));
    }
}
