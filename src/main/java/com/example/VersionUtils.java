package com.example;

/**
 * Pure-Java utility for version comparison and version.json manifest parsing.
 * No Minecraft or Fabric dependencies — fully unit-testable.
 */
class VersionUtils {

    private VersionUtils() {}

    /**
     * Returns true if {@code remote} is strictly newer than {@code local}.
     * Leading 'v' prefixes are stripped; segments are compared as integers.
     * Shorter version strings are zero-padded on the right (e.g. "1.1" == "1.1.0").
     */
    static boolean isNewerVersion(String remote, String local) {
        String[] r = remote.replace("v", "").split("\\.");
        String[] l = local.replace("v",  "").split("\\.");
        int max = Math.max(r.length, l.length);
        for (int i = 0; i < max; i++) {
            int rv = i < r.length ? parseVersionPart(r[i]) : 0;
            int lv = i < l.length ? parseVersionPart(l[i]) : 0;
            if (rv > lv) return true;
            if (rv < lv) return false;
        }
        return false;
    }

    /**
     * Strips non-digit characters and parses the numeric value.
     * Returns 0 for empty or all-letter strings.
     */
    static int parseVersionPart(String part) {
        try {
            String clean = part.replaceAll("[^0-9]", "");
            return clean.isEmpty() ? 0 : Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Extracts a string value from a hand-rolled JSON object.
     * Requires the pattern {@code "fieldName": "value"} (space after colon).
     * Returns {@code null} if the field is absent or the input is null/empty.
     */
    static String extractJsonField(String json, String fieldName) {
        if (json == null || json.isEmpty()) return null;
        try {
            String search = "\"" + fieldName + "\": \"";
            int start = json.indexOf(search);
            if (start == -1) return null;
            start += search.length();
            int end = json.indexOf("\"", start);
            return (end == -1) ? null : json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
}
