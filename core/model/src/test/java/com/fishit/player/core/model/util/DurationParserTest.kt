package com.fishit.player.core.model.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [DurationParser] — SSOT for duration string parsing.
 *
 * Validates all Xtream API duration formats and edge cases.
 */
class DurationParserTest {

    // ========== parseToMs: HH:MM:SS format ==========

    @Test
    fun `parseToMs handles standard HH_MM_SS`() {
        assertEquals(5_400_000L, DurationParser.parseToMs("01:30:00"))
    }

    @Test
    fun `parseToMs handles HH_MM_SS with large hours`() {
        // 2h 15m 30s = (2*3600 + 15*60 + 30) * 1000
        assertEquals(8_130_000L, DurationParser.parseToMs("02:15:30"))
    }

    @Test
    fun `parseToMs handles zero duration HH_MM_SS`() {
        assertEquals(0L, DurationParser.parseToMs("00:00:00"))
    }

    // ========== parseToMs: MM:SS format ==========

    @Test
    fun `parseToMs handles MM_SS format`() {
        // 90:00 = 90 min 0 sec = 5400 sec
        assertEquals(5_400_000L, DurationParser.parseToMs("90:00"))
    }

    @Test
    fun `parseToMs handles short MM_SS`() {
        // 1:30 = 1 min 30 sec = 90 sec
        assertEquals(90_000L, DurationParser.parseToMs("1:30"))
    }

    @Test
    fun `parseToMs handles MM_SS with only seconds`() {
        // 0:45 = 45 seconds
        assertEquals(45_000L, DurationParser.parseToMs("0:45"))
    }

    // ========== parseToMs: plain minutes ==========

    @Test
    fun `parseToMs handles plain minutes number`() {
        assertEquals(5_400_000L, DurationParser.parseToMs("90"))
    }

    @Test
    fun `parseToMs handles single digit minutes`() {
        assertEquals(300_000L, DurationParser.parseToMs("5"))
    }

    @Test
    fun `parseToMs handles zero minutes`() {
        assertEquals(0L, DurationParser.parseToMs("0"))
    }

    // ========== parseToMs: "min" suffix ==========

    @Test
    fun `parseToMs handles minutes with min suffix`() {
        assertEquals(5_400_000L, DurationParser.parseToMs("90 min"))
    }

    @Test
    fun `parseToMs handles minutes with min suffix case insensitive`() {
        assertEquals(5_400_000L, DurationParser.parseToMs("90 MIN"))
    }

    @Test
    fun `parseToMs handles minutes with min suffix no space`() {
        assertEquals(5_400_000L, DurationParser.parseToMs("90min"))
    }

    // ========== parseToMs: null/empty/invalid ==========

    @Test
    fun `parseToMs returns null for null`() {
        assertNull(DurationParser.parseToMs(null))
    }

    @Test
    fun `parseToMs returns null for empty string`() {
        assertNull(DurationParser.parseToMs(""))
    }

    @Test
    fun `parseToMs returns null for blank string`() {
        assertNull(DurationParser.parseToMs("   "))
    }

    @Test
    fun `parseToMs returns null for non-numeric string`() {
        assertNull(DurationParser.parseToMs("abc"))
    }

    @Test
    fun `parseToMs returns null for invalid colon format`() {
        assertNull(DurationParser.parseToMs("abc:def"))
    }

    @Test
    fun `parseToMs handles whitespace around input`() {
        assertEquals(5_400_000L, DurationParser.parseToMs("  90  "))
    }

    // ========== secondsToMs ==========

    @Test
    fun `secondsToMs converts correctly`() {
        assertEquals(5_400_000L, DurationParser.secondsToMs(5400L))
    }

    @Test
    fun `secondsToMs with zero`() {
        assertEquals(0L, DurationParser.secondsToMs(0L))
    }

    // ========== minutesToMs ==========

    @Test
    fun `minutesToMs converts correctly`() {
        assertEquals(5_400_000L, DurationParser.minutesToMs(90))
    }

    @Test
    fun `minutesToMs with zero`() {
        assertEquals(0L, DurationParser.minutesToMs(0))
    }

    // ========== resolve ==========

    @Test
    fun `resolve prefers durationSecs over string`() {
        // durationSecs=3600 (1h), string="90" (1.5h) → should prefer 3600*1000
        assertEquals(3_600_000L, DurationParser.resolve(3600L, "90"))
    }

    @Test
    fun `resolve falls back to string when durationSecs is null`() {
        assertEquals(5_400_000L, DurationParser.resolve(null, "90"))
    }

    @Test
    fun `resolve falls back to string when durationSecs is zero`() {
        // Zero duration from API should be treated as "no data"
        assertEquals(5_400_000L, DurationParser.resolve(0L, "90"))
    }

    @Test
    fun `resolve falls back to string when durationSecs is negative`() {
        assertEquals(5_400_000L, DurationParser.resolve(-1L, "90"))
    }

    @Test
    fun `resolve returns null when both inputs are null`() {
        assertNull(DurationParser.resolve(null, null))
    }

    @Test
    fun `resolve returns null when durationSecs is zero and string is unparseable`() {
        assertNull(DurationParser.resolve(0L, "abc"))
    }
}
