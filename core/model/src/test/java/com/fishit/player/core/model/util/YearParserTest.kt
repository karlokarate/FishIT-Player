package com.fishit.player.core.model.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [YearParser] — SSOT for year validation and extraction.
 *
 * Validates API year validation, date extraction, and title extraction patterns.
 */
class YearParserTest {
    // ========== validate ==========

    @Test
    fun `validate accepts valid year`() {
        assertEquals(2024, YearParser.validate("2024"))
    }

    @Test
    fun `validate accepts boundary year 1900`() {
        assertEquals(1900, YearParser.validate("1900"))
    }

    @Test
    fun `validate accepts boundary year 2100`() {
        assertEquals(2100, YearParser.validate("2100"))
    }

    @Test
    fun `validate rejects zero`() {
        assertNull(YearParser.validate("0"))
    }

    @Test
    fun `validate rejects N-A`() {
        assertNull(YearParser.validate("N/A"))
    }

    @Test
    fun `validate rejects empty string`() {
        assertNull(YearParser.validate(""))
    }

    @Test
    fun `validate rejects blank string`() {
        assertNull(YearParser.validate("   "))
    }

    @Test
    fun `validate rejects null`() {
        assertNull(YearParser.validate(null))
    }

    @Test
    fun `validate rejects year below range`() {
        assertNull(YearParser.validate("1899"))
    }

    @Test
    fun `validate rejects year above range`() {
        assertNull(YearParser.validate("2101"))
    }

    @Test
    fun `validate rejects non-numeric`() {
        assertNull(YearParser.validate("abc"))
    }

    @Test
    fun `validate rejects float string`() {
        // "2024.5" → toIntOrNull() returns null
        assertNull(YearParser.validate("2024.5"))
    }

    // ========== fromDate ==========

    @Test
    fun `fromDate extracts year from ISO date`() {
        assertEquals(2014, YearParser.fromDate("2014-09-21"))
    }

    @Test
    fun `fromDate extracts year from plain year string`() {
        assertEquals(2023, YearParser.fromDate("2023"))
    }

    @Test
    fun `fromDate returns null for non-numeric`() {
        assertNull(YearParser.fromDate("abc"))
    }

    @Test
    fun `fromDate returns null for null`() {
        assertNull(YearParser.fromDate(null))
    }

    @Test
    fun `fromDate returns null for too-short string`() {
        // "20" → take(4) = "20" → toIntOrNull() = 20 → NOT in 1900..2100 → null
        assertNull(YearParser.fromDate("20"))
    }

    @Test
    fun `fromDate rejects date with invalid year prefix`() {
        // "0000-01-01" → take(4) = "0000" → 0 → out of range
        assertNull(YearParser.fromDate("0000-01-01"))
    }

    // ========== extractFromVodTitle ==========

    @Test
    fun `extractFromVodTitle finds year in pipe-delimited title`() {
        assertEquals(2025, YearParser.extractFromVodTitle("Ella McCay | 2025 | 5.2"))
    }

    @Test
    fun `extractFromVodTitle finds year with trailing pipe`() {
        assertEquals(2024, YearParser.extractFromVodTitle("The Killer | 2024 | 6.4 |"))
    }

    @Test
    fun `extractFromVodTitle falls back to parenthesized year`() {
        assertEquals(2023, YearParser.extractFromVodTitle("Movie Name (2023)"))
    }

    @Test
    fun `extractFromVodTitle returns null for no year`() {
        assertNull(YearParser.extractFromVodTitle("Random Movie Title"))
    }

    @Test
    fun `extractFromVodTitle rejects non-year number in pipe`() {
        // "Title | 500 | ..." → 500 not in 1900..2100
        assertNull(YearParser.extractFromVodTitle("Title | 500 | 6.0"))
    }

    // ========== extractFromSeriesTitle ==========

    @Test
    fun `extractFromSeriesTitle finds year in parentheses`() {
        assertEquals(2023, YearParser.extractFromSeriesTitle("Show Name (2023)"))
    }

    @Test
    fun `extractFromSeriesTitle finds year in brackets`() {
        assertEquals(2023, YearParser.extractFromSeriesTitle("Show Name [2023]"))
    }

    @Test
    fun `extractFromSeriesTitle finds standalone year at end`() {
        assertEquals(2023, YearParser.extractFromSeriesTitle("Show Name 2023"))
    }

    @Test
    fun `extractFromSeriesTitle picks last parenthesized year`() {
        // "Show (2020) Season 2 (2023)" → last match is 2023
        assertEquals(2023, YearParser.extractFromSeriesTitle("Show (2020) Season 2 (2023)"))
    }

    @Test
    fun `extractFromSeriesTitle returns null for no year`() {
        assertNull(YearParser.extractFromSeriesTitle("Some Random Show"))
    }

    @Test
    fun `extractFromSeriesTitle rejects mid-title year not at end`() {
        // "2023 Show Name" → standalone pattern requires \b\d{4}$ (end of string)
        // But parenthesized pattern would match if present
        assertNull(YearParser.extractFromSeriesTitle("2023 Show Name"))
    }

    @Test
    fun `extractFromSeriesTitle handles S01 prefix with year`() {
        assertEquals(2023, YearParser.extractFromSeriesTitle("Show S01 (2023)"))
    }
}
