package com.fishit.player.core.model.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [EpochConverter] — SSOT for Unix epoch timestamp conversion.
 *
 * Validates both String? and Long overloads.
 */
class EpochConverterTest {
    // ========== secondsToMs(String?) ==========

    @Test
    fun `secondsToMs string converts valid epoch`() {
        // 2024-01-31 12:00:00 UTC = 1706702400
        assertEquals(1_706_702_400_000L, EpochConverter.secondsToMs("1706702400"))
    }

    @Test
    fun `secondsToMs string returns null for null`() {
        assertNull(EpochConverter.secondsToMs(null as String?))
    }

    @Test
    fun `secondsToMs string returns null for empty`() {
        assertNull(EpochConverter.secondsToMs(""))
    }

    @Test
    fun `secondsToMs string returns null for non-numeric`() {
        assertNull(EpochConverter.secondsToMs("abc"))
    }

    @Test
    fun `secondsToMs string handles zero`() {
        assertEquals(0L, EpochConverter.secondsToMs("0"))
    }

    @Test
    fun `secondsToMs string handles whitespace-padded input`() {
        // toLongOrNull handles surrounding whitespace? No - it doesn't.
        // This verifies correct behavior for whitespace inputs.
        assertNull(EpochConverter.secondsToMs(" 1706702400 "))
    }

    // ========== secondsToMs(Long) ==========

    @Test
    fun `secondsToMs long converts correctly`() {
        assertEquals(1_706_702_400_000L, EpochConverter.secondsToMs(1706702400L))
    }

    @Test
    fun `secondsToMs long handles zero`() {
        assertEquals(0L, EpochConverter.secondsToMs(0L))
    }

    @Test
    fun `secondsToMs long handles large timestamps`() {
        // Year 2100 approx: 4102444800
        assertEquals(4_102_444_800_000L, EpochConverter.secondsToMs(4102444800L))
    }
}
