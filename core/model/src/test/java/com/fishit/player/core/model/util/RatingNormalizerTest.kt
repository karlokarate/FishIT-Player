package com.fishit.player.core.model.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [RatingNormalizer] — SSOT for rating scale normalization.
 *
 * Validates 5→10 normalization and resolve() priority logic.
 */
class RatingNormalizerTest {
    // ========== normalize5to10 ==========

    @Test
    fun `normalize5to10 doubles the value`() {
        assertEquals(9.0, RatingNormalizer.normalize5to10(4.5)!!, 0.001)
    }

    @Test
    fun `normalize5to10 with zero returns null (no rating)`() {
        // Xtream API uses rating_5based=0 for unrated content
        assertNull(RatingNormalizer.normalize5to10(0.0))
    }

    @Test
    fun `normalize5to10 with very small positive value`() {
        assertEquals(0.2, RatingNormalizer.normalize5to10(0.1)!!, 0.001)
    }

    @Test
    fun `normalize5to10 with max 5`() {
        assertEquals(10.0, RatingNormalizer.normalize5to10(5.0)!!, 0.001)
    }

    @Test
    fun `normalize5to10 returns null for null`() {
        assertNull(RatingNormalizer.normalize5to10(null))
    }

    // ========== resolve ==========

    @Test
    fun `resolve prefers raw rating string over rating5Based`() {
        // raw="7.5" (already 0-10), rating5Based=4.0 (would be 8.0)
        assertEquals(7.5, RatingNormalizer.resolve("7.5", 4.0)!!, 0.001)
    }

    @Test
    fun `resolve falls back to normalized rating5Based when raw is null`() {
        assertEquals(8.0, RatingNormalizer.resolve(null, 4.0)!!, 0.001)
    }

    @Test
    fun `resolve falls back to normalized rating5Based when raw is invalid`() {
        assertEquals(8.0, RatingNormalizer.resolve("abc", 4.0)!!, 0.001)
    }

    @Test
    fun `resolve falls back to normalized rating5Based when raw is empty`() {
        assertEquals(8.0, RatingNormalizer.resolve("", 4.0)!!, 0.001)
    }

    @Test
    fun `resolve returns null when both inputs are null`() {
        assertNull(RatingNormalizer.resolve(null, null))
    }

    @Test
    fun `resolve treats zero raw rating as no rating`() {
        // Xtream API uses rating="0" for unrated content, not genuine zero-star
        // Falls back to rating5Based normalization
        assertEquals(8.0, RatingNormalizer.resolve("0", 4.0)!!, 0.001)
    }

    @Test
    fun `resolve returns null when raw is zero and rating5Based is also zero`() {
        assertNull(RatingNormalizer.resolve("0", 0.0))
    }

    @Test
    fun `resolve returns null when raw is zero and rating5Based is null`() {
        assertNull(RatingNormalizer.resolve("0", null))
    }

    @Test
    fun `resolve handles decimal raw rating`() {
        assertEquals(6.543, RatingNormalizer.resolve("6.543", null)!!, 0.001)
    }

    @Test
    fun `resolve handles integer raw rating`() {
        assertEquals(8.0, RatingNormalizer.resolve("8", null)!!, 0.001)
    }
}
