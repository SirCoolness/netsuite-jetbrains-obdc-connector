package com.netsuite.jetbrains;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strips NetSuite sandbox suffixes (_SB1, _SB2, etc.) from catalog names.
 *
 * NetSuite appends environment identifiers to the company name in catalog metadata
 * (e.g., "Harmony Equities LLC_SB1" for sandbox 1). This causes JetBrains schema
 * patterns configured for production ("Harmony Equities LLC:*") to fail on sandbox
 * environments. By normalizing the catalog name, patterns work across all environments.
 */
public class CatalogStripper {

    // Matches _SB followed by one or more digits at the end of the string
    private static final Pattern SANDBOX_SUFFIX = Pattern.compile("_SB\\d+$");

    /** Metadata result columns that hold a catalog name (and only those are rewritten). */
    private static final Set<String> CATALOG_LABELS = new HashSet<String>(Arrays.asList(
        "TABLE_CAT", "TABLE_CATALOG", "PKTABLE_CAT", "FKTABLE_CAT", "PROCEDURE_CAT",
        "FUNCTION_CAT", "TYPE_CAT", "SCOPE_CATALOG", "SCOPE_CAT", "SUPERTYPE_CAT"));

    /**
     * Remove the sandbox suffix from a catalog name if present.
     * "Harmony Equities LLC_SB1" -> "Harmony Equities LLC"
     * "Harmony Equities LLC" -> "Harmony Equities LLC" (no change)
     */
    public static String strip(String catalogName) {
        if (catalogName == null) return null;
        return SANDBOX_SUFFIX.matcher(catalogName).replaceFirst("");
    }

    public static boolean isCatalogLabel(String label) {
        return label != null && CATALOG_LABELS.contains(label.toUpperCase());
    }

    /** Strip every catalog column of a materialized metadata row in place. */
    public static void stripRow(Object[] row, String[] labels) {
        for (int i = 0; i < labels.length; i++) {
            if (isCatalogLabel(labels[i]) && row[i] instanceof String) {
                row[i] = strip((String) row[i]);
            }
        }
    }
}
