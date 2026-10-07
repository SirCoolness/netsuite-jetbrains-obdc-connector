package com.netsuite.jetbrains;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lightweight file logger for diagnosing JetBrains JDBC introspection calls.
 * Writes to <java.io.tmpdir>/netsuite-jdbc.log (override with -Dnetsuite.jdbc.log=/path),
 * created readable by the owner only.
 *
 * Disabled by default. Enable with: -Dnetsuite.jdbc.debug=true
 *
 * Every line is redacted: account ids, user names, passwords, credential JSON fields and
 * generated nonce passwords never reach the file.
 */
public class JdbcLogger {

    private static final boolean ENABLED = Boolean.getBoolean("netsuite.jdbc.debug");
    private static final String LOG_FILE = System.getProperty("netsuite.jdbc.log",
        new File(System.getProperty("java.io.tmpdir"), "netsuite-jdbc.log").getPath());
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("HH:mm:ss.SSS");
    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");

    /** pattern, replacement pairs applied to every log line */
    private static final Object[][] REDACTIONS = {
        // URL / connection properties: AccountID=..., User=..., Password=...
        {Pattern.compile("(?i)\\b(AccountID|UID|User|Password|PWD)(\\s*=\\s*)[^;)\\s,]*"), "$1$2***"},
        // the account id is also the host name: <account>.connect.api.netsuite.com
        {Pattern.compile("(?i)//[^/:;\\s]+(\\.connect\\.api\\.netsuite\\.com)"), "//***$1"},
        // credential JSON
        {Pattern.compile("(?i)\"(accountId|consumerKey|consumerSecret|tokenId|tokenSecret)\"\\s*:\\s*\"[^\"]*\""), "\"$1\":\"***\""},
        // a generated nonce password: account&key&token&nonce&timestamp&signature&HMAC-SHA256
        {Pattern.compile("\\S*&HMAC-SHA256"), "***"},
    };

    private static boolean prepared;

    public static void log(String component, String method, Object[] args) {
        if (!ENABLED) return;
        write(component + "." + method + "(" + formatArgs(args) + ")");
    }

    public static void log(String component, String method, Object[] args, Object result) {
        if (!ENABLED) return;
        String resultStr = formatResult(result);
        write(component + "." + method + "(" + formatArgs(args) + ") -> " + resultStr);
    }

    public static void logException(String component, String method, Throwable t) {
        if (!ENABLED) return;
        write(component + "." + method + " THREW: " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }

    static String redact(String message) {
        String out = message;
        for (Object[] redaction : REDACTIONS) {
            out = ((Pattern) redaction[0]).matcher(out).replaceAll((String) redaction[1]);
        }
        return out;
    }

    private static String formatArgs(Object[] args) {
        if (args == null || args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            if (args[i] == null) {
                sb.append("null");
            } else if (args[i] instanceof String) {
                String s = (String) args[i];
                if (s.length() > 300) s = s.substring(0, 300) + "...";
                sb.append("\"").append(s).append("\"");
            } else if (args[i] instanceof String[]) {
                String[] arr = (String[]) args[i];
                sb.append("[");
                for (int j = 0; j < arr.length && j < 5; j++) {
                    if (j > 0) sb.append(", ");
                    sb.append("\"").append(arr[j]).append("\"");
                }
                if (arr.length > 5) sb.append(", ...");
                sb.append("]");
            } else if (args[i] instanceof java.util.Properties) {
                sb.append("<properties>"); // may hold the password
            } else {
                sb.append(formatResult(args[i]));
            }
        }
        return sb.toString();
    }

    private static String formatResult(Object result) {
        if (result == null) return "null";
        if (result instanceof String) return "\"" + result + "\"";
        if (result instanceof ResultSet) return "<ResultSet>";
        if (result instanceof Boolean) return result.toString();
        if (result instanceof Number) return result.toString();
        if (result instanceof Class) return ((Class<?>) result).getSimpleName() + ".class";
        return result.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(result));
    }

    static synchronized void write(String msg) {
        if (!ENABLED) return;
        try {
            prepareFile();
            try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE, true))) {
                pw.println(DATE_FMT.format(new Date()) + " " + redact(msg));
            }
        } catch (Exception ignored) {
        }
    }

    /** Create the log owner-only (and tighten a file left by an older version). */
    private static void prepareFile() {
        if (prepared) return;
        prepared = true;
        Path path = new File(LOG_FILE).toPath();
        try {
            if (!Files.exists(path)) {
                Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
            } else {
                Files.setPosixFilePermissions(path, OWNER_ONLY);
            }
        } catch (Exception ignored) {
            // non-POSIX file system or a file owned by someone else: FileWriter creates/appends
        }
    }
}
