package com.netsuite.jetbrains;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
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
 * When GenerateNonce is absent or false, all calls pass through transparently to the
 * original driver with zero overhead.
 *
 * Usage in JetBrains:
 *   Driver class: com.netsuite.jetbrains.NetsuiteJetbrainsDriver
 *   URL: jdbc:ns://host:port;...;CustomProperties=(AccountID=...;RoleID=...;GenerateNonce=true)
 *   Password: {"accountId":"...","consumerKey":"...","consumerSecret":"...","tokenId":"...","tokenSecret":"..."}
 */
public class NetsuiteJetbrainsDriver implements Driver {

    private static final String URL_PREFIX = "jdbc:ns:";

    // Pattern to detect GenerateNonce=true inside CustomProperties(...)
    // Case-insensitive match within the parenthesized custom properties block
    private static final Pattern GENERATE_NONCE_PATTERN = Pattern.compile(
        "CustomProperties\\s*=\\s*\\(([^)]*?)\\)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NONCE_FLAG_PATTERN = Pattern.compile(
        "GenerateNonce\\s*=\\s*true",
        Pattern.CASE_INSENSITIVE
    );

    private final Driver delegate;

    static {
        try {
            DriverManager.registerDriver(new NetsuiteJetbrainsDriver());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register NetsuiteJetbrainsDriver", e);
        }
    }

    public NetsuiteJetbrainsDriver() throws SQLException {
        // Instantiate the real NetSuite OpenAccess driver
        try {
            Class<?> driverClass = Class.forName("com.netsuite.jdbc.openaccess.OpenAccessDriver");
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

        Connection conn;
        if (shouldGenerateNonce(url)) {
            String cleanUrl = stripGenerateNonce(url);
            Properties modifiedProps = generateNoncePassword(info);
            if (modifiedProps == null) {
                return null;
            }
            conn = delegate.connect(cleanUrl, modifiedProps);
        } else {
            conn = delegate.connect(url, info);
        }

        // Wrap the connection to normalize catalog names (strip _SB1 sandbox suffixes)
        // so JetBrains schema patterns work across environments
        if (conn != null) {
            conn = ConnectionWrapper.wrap(conn);
        }
        return conn;
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.toLowerCase().startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return delegate.getPropertyInfo(url, info);
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

    /**
     * Check if the URL contains GenerateNonce=true in its CustomProperties block.
     */
    private boolean shouldGenerateNonce(String url) {
        Matcher cpMatcher = GENERATE_NONCE_PATTERN.matcher(url);
        if (!cpMatcher.find()) {
            return false;
        }
        String customProps = cpMatcher.group(1);
        return NONCE_FLAG_PATTERN.matcher(customProps).find();
    }

    /**
     * Remove GenerateNonce=true (and its trailing/leading semicolons) from CustomProperties
     * so the real driver doesn't choke on an unknown property.
     */
    private String stripGenerateNonce(String url) {
        return url.replaceAll("(?i);?\\s*GenerateNonce\\s*=\\s*true\\s*;?", "")
                  .replaceAll(";;", ";")
                  .replaceAll(";\\)", ")");
    }

    /**
     * Parse the password as JSON credentials and generate a fresh nonce password.
     * Returns a new Properties object with the computed password substituted.
     */
    private Properties generateNoncePassword(Properties original) throws SQLException {
        String password = null;
        if (original != null) {
            password = original.getProperty("password");
            if (password == null) {
                password = original.getProperty("PASSWORD");
            }
            if (password == null) {
                password = original.getProperty("Password");
            }
        }

        if (password == null || password.trim().isEmpty()) {
            // JetBrains may open secondary connections without credentials (e.g. for
            // introspection pooling). Return null to signal caller to abort gracefully.
            return null;
        }

        NonceCredentials credentials = NonceCredentials.fromJson(password);
        String noncePassword = NonceGenerator.generatePassword(credentials);

        // Build new properties with the generated password
        Properties modified = new Properties();
        if (original != null) {
            modified.putAll(original);
        }
        // Replace all case variants of password with the generated value
        modified.remove("password");
        modified.remove("PASSWORD");
        modified.remove("Password");
        modified.setProperty("password", noncePassword);

        return modified;
    }
}
