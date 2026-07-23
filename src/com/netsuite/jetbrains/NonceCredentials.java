package com.netsuite.jetbrains;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses and validates the JSON password field for nonce generation.
 *
 * Expected format (flat JSON object, no nesting):
 * {
 *   "accountId": "YOUR_ACCOUNT_ID",
 *   "consumerKey": "...",
 *   "consumerSecret": "...",
 *   "tokenId": "...",
 *   "tokenSecret": "..."
 * }
 *
 * Design decision: Hand-rolled JSON parsing to avoid external dependencies.
 * The password is always a simple flat string-to-string map, so a full JSON
 * library would be overkill and would bloat the JAR.
 */
public class NonceCredentials {

    private static final String[] REQUIRED_FIELDS = {
        "accountId", "consumerKey", "consumerSecret", "tokenId", "tokenSecret"
    };

    public final String accountId;
    public final String consumerKey;
    public final String consumerSecret;
    public final String tokenId;
    public final String tokenSecret;

    private NonceCredentials(String accountId, String consumerKey, String consumerSecret,
                             String tokenId, String tokenSecret) {
        this.accountId = accountId;
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.tokenId = tokenId;
        this.tokenSecret = tokenSecret;
    }

    /**
     * Parse the password string as JSON and extract nonce credentials.
     *
     * @throws SQLException if the JSON is malformed or missing required fields
     */
    public static NonceCredentials fromJson(String json) throws SQLException {
        if (json == null || json.trim().isEmpty()) {
            throw new SQLException(
                "GenerateNonce=true but password is empty. Expected JSON: " + expectedFormat());
        }

        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new SQLException(
                "GenerateNonce=true but password is not valid JSON object. Expected: " + expectedFormat());
        }

        Map<String, String> fields = parseSimpleJson(json);

        for (String field : REQUIRED_FIELDS) {
            String value = fields.get(field);
            if (value == null || value.trim().isEmpty()) {
                throw new SQLException(
                    "GenerateNonce=true but password JSON is missing required field: \"" + field + "\". " +
                    "Expected: " + expectedFormat());
            }
        }

        return new NonceCredentials(
            fields.get("accountId").trim(),
            fields.get("consumerKey").trim(),
            fields.get("consumerSecret").trim(),
            fields.get("tokenId").trim(),
            fields.get("tokenSecret").trim()
        );
    }

    /**
     * Minimal JSON object parser for flat string-to-string maps.
     * Handles quoted keys/values with basic escape support (\", \\).
     */
    private static Map<String, String> parseSimpleJson(String json) throws SQLException {
        Map<String, String> map = new HashMap<String, String>();

        // Strip outer braces
        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) {
            return map;
        }

        int i = 0;
        while (i < content.length()) {
            // Skip whitespace and commas
            while (i < content.length() && (content.charAt(i) == ',' || Character.isWhitespace(content.charAt(i)))) {
                i++;
            }
            if (i >= content.length()) break;

            // Parse key
            if (content.charAt(i) != '"') {
                throw new SQLException("GenerateNonce JSON parse error: expected '\"' at position " + i);
            }
            i++;
            StringBuilder key = new StringBuilder();
            while (i < content.length() && content.charAt(i) != '"') {
                if (content.charAt(i) == '\\' && i + 1 < content.length()) {
                    i++;
                }
                key.append(content.charAt(i));
                i++;
            }
            if (i >= content.length()) {
                throw new SQLException("GenerateNonce JSON parse error: unterminated key string");
            }
            i++; // skip closing quote

            // Skip colon and whitespace
            while (i < content.length() && (content.charAt(i) == ':' || Character.isWhitespace(content.charAt(i)))) {
                i++;
            }

            // Parse value
            if (i >= content.length() || content.charAt(i) != '"') {
                throw new SQLException("GenerateNonce JSON parse error: expected '\"' for value of \"" + key + "\"");
            }
            i++;
            StringBuilder value = new StringBuilder();
            while (i < content.length() && content.charAt(i) != '"') {
                if (content.charAt(i) == '\\' && i + 1 < content.length()) {
                    i++;
                }
                value.append(content.charAt(i));
                i++;
            }
            if (i >= content.length()) {
                throw new SQLException("GenerateNonce JSON parse error: unterminated value string for \"" + key + "\"");
            }
            i++; // skip closing quote

            map.put(key.toString(), value.toString());
        }

        return map;
    }

    private static String expectedFormat() {
        return "{\"accountId\":\"...\", \"consumerKey\":\"...\", \"consumerSecret\":\"...\", " +
               "\"tokenId\":\"...\", \"tokenSecret\":\"...\"}";
    }
}
