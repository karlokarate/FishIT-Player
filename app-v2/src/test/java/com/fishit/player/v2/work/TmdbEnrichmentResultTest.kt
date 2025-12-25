package com.fishit.player.v2.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for TMDB enrichment result types.
 */
class TmdbEnrichmentResultTest {
    @Test
    fun `Success result captures all batch statistics`() {
        val result =
            TmdbEnrichmentResult.Success(
                itemsProcessed = 50,
                itemsResolved = 45,
                itemsFailed = 5,
                hasMore = true,
                nextCursor = "cursor-page2",
            )

        assertEquals(50, result.itemsProcessed)
        assertEquals(45, result.itemsResolved)
        assertEquals(5, result.itemsFailed)
        assertTrue(result.hasMore)
        assertEquals("cursor-page2", result.nextCursor)
    }

    @Test
    fun `Success result without cursor`() {
        val result =
            TmdbEnrichmentResult.Success(
                itemsProcessed = 25,
                itemsResolved = 25,
                itemsFailed = 0,
                hasMore = false,
                nextCursor = null,
            )

        assertFalse(result.hasMore)
        assertNull(result.nextCursor)
    }

    @Test
    fun `RetryableFailure captures failure reason and progress`() {
        val result =
            TmdbEnrichmentResult.RetryableFailure(
                reason = "Network timeout",
                itemsProcessedBeforeFailure = 10,
            )

        assertEquals("Network timeout", result.reason)
        assertEquals(10, result.itemsProcessedBeforeFailure)
    }

    @Test
    fun `PermanentFailure captures failure reason`() {
        val result =
            TmdbEnrichmentResult.PermanentFailure(
                reason = "TMDB_API_KEY_MISSING",
            )

        assertEquals("TMDB_API_KEY_MISSING", result.reason)
    }

    @Test
    fun `NoCandidates is singleton`() {
        val result1 = TmdbEnrichmentResult.NoCandidates
        val result2 = TmdbEnrichmentResult.NoCandidates

        assertEquals(result1, result2)
    }

    @Test
    fun `Disabled is singleton`() {
        val result1 = TmdbEnrichmentResult.Disabled
        val result2 = TmdbEnrichmentResult.Disabled

        assertEquals(result1, result2)
    }
}
