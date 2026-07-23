package com.netsuite.jetbrains;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;

/**
 * Generates HMAC-SHA256 signed nonce passwords for NetSuite SuiteAnalytics Connect.
 *
 * The nonce password is time-sensitive — it contains a nonce and timestamp that are only valid
 * for a short window. This class generates a fresh password on every call, mirroring the
 * algorithm in netsuite-tba-password.sh:
 *
 *   BASE_STRING = accountId & consumerKey & tokenId & nonce & timestamp
 *   SIGNATURE_KEY = consumerSecret & tokenSecret
 *   SIGNATURE = HMAC-SHA256(SIGNATURE_KEY, BASE_STRING) → base64
 *   PASSWORD = BASE_STRING & SIGNATURE & "HMAC-SHA256"
 *
 * Design decision: Uses only JDK standard library (javax.crypto.Mac, java.security.SecureRandom,
 * java.util.Base64) to avoid any external dependencies in the fat JAR.
 */
public class NonceGenerator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generate a complete nonce password string from the given credentials.
     * Each call produces a unique password (fresh nonce + current timestamp).
     */
    public static String generatePassword(NonceCredentials credentials) throws SQLException {
        String nonce = generateNonce();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);

        String baseString = credentials.accountId + "&" +
                            credentials.consumerKey + "&" +
                            credentials.tokenId + "&" +
                            nonce + "&" +
                            timestamp;

        String signatureKey = credentials.consumerSecret + "&" + credentials.tokenSecret;

        String signature = computeHmacSha256(signatureKey, baseString);

        // Final password format: baseString & signature & HMAC-SHA256
        // The "&HMAC-SHA256" suffix is appended to the signature (matching the shell script behavior)
        return baseString + "&" + signature + "&HMAC-SHA256";
    }

    /**
     * Generate a random 20-character hex nonce (10 random bytes → 20 hex chars).
     * Matches: openssl rand -hex 10
     */
    private static String generateNonce() {
        byte[] bytes = new byte[10];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(20);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }

    /**
     * Compute HMAC-SHA256 of the data using the given key, returning base64-encoded result.
     */
    private static String computeHmacSha256(String key, String data) throws SQLException {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                key.getBytes(StandardCharsets.US_ASCII), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new SQLException("Failed to compute HMAC-SHA256 for nonce: " + e.getMessage(), e);
        }
    }
}
