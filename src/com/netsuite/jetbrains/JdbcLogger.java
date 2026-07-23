package com.netsuite.jetbrains;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Lightweight file logger for diagnosing JetBrains JDBC introspection calls.
 * Writes to /tmp/netsuite-jdbc.log in append mode.
 *
 * Disabled by default. Enable with: -Dnetsuite.jdbc.debug=true
 */
public class JdbcLogger {

    private static final boolean ENABLED = Boolean.getBoolean("netsuite.jdbc.debug");
    private static final String LOG_FILE = "/tmp/netsuite-jdbc.log";
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("HH:mm:ss.SSS");

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

    public static void logResultSetMeta(String label, ResultSet rs) {
        if (!ENABLED) return;
        try {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            StringBuilder sb = new StringBuilder();
            sb.append(label).append(" columns=[");
            for (int i = 1; i <= cols; i++) {
                if (i > 1) sb.append(", ");
                sb.append(meta.getColumnName(i));
            }
            sb.append("]");
            write(sb.toString());
        } catch (Exception e) {
            write(label + " (failed to read metadata: " + e.getMessage() + ")");
        }
    }

    public static void logResultSetRow(String label, ResultSet rs) {
        if (!ENABLED) return;
        try {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            StringBuilder sb = new StringBuilder();
            sb.append("  ROW: {");
            for (int i = 1; i <= cols; i++) {
                if (i > 1) sb.append(", ");
                sb.append(meta.getColumnName(i)).append("=").append(rs.getString(i));
            }
            sb.append("}");
            write(sb.toString());
        } catch (Exception e) {
            write("  ROW: (failed to read: " + e.getMessage() + ")");
        }
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
                if (s.length() > 100) s = s.substring(0, 100) + "...";
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
            } else {
                sb.append(args[i].toString());
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
        return result.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(result));
    }

    private static synchronized void write(String msg) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            pw.println(DATE_FMT.format(new Date()) + " " + msg);
        } catch (Exception ignored) {
        }
    }
}
