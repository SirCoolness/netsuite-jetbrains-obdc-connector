package com.netsuite.jetbrains;

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

    /**
     * Remove the sandbox suffix from a catalog name if present.
     * "Harmony Equities LLC_SB1" -> "Harmony Equities LLC"
     * "Harmony Equities LLC" -> "Harmony Equities LLC" (no change)
     */
    public static String strip(String catalogName) {
        if (catalogName == null) return null;
        return SANDBOX_SUFFIX.matcher(catalogName).replaceFirst("");
    }
}
