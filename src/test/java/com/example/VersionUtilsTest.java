package com.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionUtilsTest {

    // -------------------------------------------------------------------------
    // isNewerVersion
    // -------------------------------------------------------------------------

    @Test
    void patchBump_isNewer() {
        assertTrue(VersionUtils.isNewerVersion("1.1.8", "1.1.7"));
    }

    @Test
    void minorBump_isNewer() {
        assertTrue(VersionUtils.isNewerVersion("1.2.0", "1.1.9"));
    }

    @Test
    void majorBump_isNewer() {
        assertTrue(VersionUtils.isNewerVersion("2.0.0", "1.9.9"));
    }

    @Test
    void sameVersion_isNotNewer() {
        assertFalse(VersionUtils.isNewerVersion("1.1.7", "1.1.7"));
    }

    @Test
    void olderRemote_isNotNewer() {
        assertFalse(VersionUtils.isNewerVersion("1.1.6", "1.1.7"));
    }

    @Test
    void vPrefixStripped_newer() {
        assertTrue(VersionUtils.isNewerVersion("v1.2.0", "v1.1.9"));
    }

    @Test
    void vPrefixStripped_same() {
        assertFalse(VersionUtils.isNewerVersion("v1.1.7", "v1.1.7"));
    }

    @Test
    void numericComparison_notLexicographic() {
        // "10" > "9" must be compared as integers (lexicographic comparison would return false)
        assertTrue(VersionUtils.isNewerVersion("1.1.10", "1.1.9"));
    }

    @Test
    void differentSegmentCounts_shorterPaddedWithZero_equal() {
        // "1.0" vs "1.0.0" should be equal
        assertFalse(VersionUtils.isNewerVersion("1.0", "1.0.0"));
        assertFalse(VersionUtils.isNewerVersion("1.0.0", "1.0"));
    }

    @Test
    void differentSegmentCounts_shorterPaddedWithZero_newer() {
        // "1.1" vs "1.0.9" → 1.1.0 > 1.0.9
        assertTrue(VersionUtils.isNewerVersion("1.1", "1.0.9"));
    }

    @Test
    void zeroMinorAndPatch_isNotNewer() {
        assertFalse(VersionUtils.isNewerVersion("1.0.0", "1.0.0"));
    }

    // -------------------------------------------------------------------------
    // parseVersionPart
    // -------------------------------------------------------------------------

    @Test
    void parsePlainNumber() {
        assertEquals(5, VersionUtils.parseVersionPart("5"));
        assertEquals(0, VersionUtils.parseVersionPart("0"));
        assertEquals(42, VersionUtils.parseVersionPart("42"));
    }

    @Test
    void parseAlphaNumeric_stripsLetters() {
        assertEquals(1, VersionUtils.parseVersionPart("1alpha"));
        assertEquals(2, VersionUtils.parseVersionPart("2beta"));
        assertEquals(3, VersionUtils.parseVersionPart("rc3"));
    }

    @Test
    void parseEmpty_returnsZero() {
        assertEquals(0, VersionUtils.parseVersionPart(""));
    }

    @Test
    void parseLettersOnly_returnsZero() {
        assertEquals(0, VersionUtils.parseVersionPart("abc"));
    }

    // -------------------------------------------------------------------------
    // extractJsonField
    // -------------------------------------------------------------------------

    @Test
    void extractsExistingStringField() {
        String json = "{ \"version\": \"1.1.7\", \"downloadUrl\": \"https://example.com/mod.jar\" }";
        assertEquals("1.1.7", VersionUtils.extractJsonField(json, "version"));
        assertEquals("https://example.com/mod.jar", VersionUtils.extractJsonField(json, "downloadUrl"));
    }

    @Test
    void extractsSha256Field() {
        String json = "{ \"version\": \"1.1.7\", \"sha256\": \"abc123def456\" }";
        assertEquals("abc123def456", VersionUtils.extractJsonField(json, "sha256"));
    }

    @Test
    void missingField_returnsNull() {
        String json = "{ \"version\": \"1.1.7\" }";
        assertNull(VersionUtils.extractJsonField(json, "sha256"));
    }

    @Test
    void nullJson_returnsNull() {
        assertNull(VersionUtils.extractJsonField(null, "version"));
    }

    @Test
    void emptyJson_returnsNull() {
        assertNull(VersionUtils.extractJsonField("", "version"));
    }

    @Test
    void noSpaceAfterColon_returnsNull() {
        // The parser requires ": \"" (space after colon) — this documents the known limitation
        String json = "{ \"version\":\"1.1.7\" }";
        assertNull(VersionUtils.extractJsonField(json, "version"));
    }

    @Test
    void fieldAtEndOfObject_extracted() {
        // Last field with no trailing comma
        String json = "{ \"downloadUrl\": \"https://example.com/mod.jar\" }";
        assertEquals("https://example.com/mod.jar", VersionUtils.extractJsonField(json, "downloadUrl"));
    }
}
