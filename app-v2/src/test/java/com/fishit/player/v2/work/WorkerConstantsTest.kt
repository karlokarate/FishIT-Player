package com.fishit.player.v2.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for TMDB-specific worker constants.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-17: FireTV Safety - batch size ranges
 * - W-22: TMDB Scope definitions
 */
class WorkerConstantsTest {
    // ========== Work Names ==========

    @Test
    fun `TMDB work name is defined`() {
        assertEquals("tmdb_enrichment_global", WorkerConstants.WORK_NAME_TMDB_ENRICHMENT)
    }

    // ========== TMDB Scopes (W-22) ==========

    @Test
    fun `TMDB scopes are defined`() {
        assertEquals("DETAILS_BY_ID", WorkerConstants.TMDB_SCOPE_DETAILS_BY_ID)
        assertEquals("RESOLVE_MISSING_IDS", WorkerConstants.TMDB_SCOPE_RESOLVE_MISSING_IDS)
        assertEquals("BOTH", WorkerConstants.TMDB_SCOPE_BOTH)
        assertEquals("REFRESH_SSOT", WorkerConstants.TMDB_SCOPE_REFRESH_SSOT)
    }

    // ========== Batch Size Ranges (W-17) ==========

    @Test
    fun `FireTV batch size range is valid`() {
        assertTrue(WorkerConstants.TMDB_FIRETV_BATCH_SIZE_MIN > 0)
        assertTrue(WorkerConstants.TMDB_FIRETV_BATCH_SIZE_MAX >= WorkerConstants.TMDB_FIRETV_BATCH_SIZE_MIN)
        assertTrue(
            WorkerConstants.TMDB_FIRETV_BATCH_SIZE_DEFAULT in
                WorkerConstants.TMDB_FIRETV_BATCH_SIZE_MIN..WorkerConstants.TMDB_FIRETV_BATCH_SIZE_MAX,
        )
    }

    @Test
    fun `FireTV batch size range is 10-25`() {
        assertEquals(10, WorkerConstants.TMDB_FIRETV_BATCH_SIZE_MIN)
        assertEquals(25, WorkerConstants.TMDB_FIRETV_BATCH_SIZE_MAX)
    }

    @Test
    fun `Normal device batch size range is valid`() {
        assertTrue(WorkerConstants.TMDB_NORMAL_BATCH_SIZE_MIN > 0)
        assertTrue(WorkerConstants.TMDB_NORMAL_BATCH_SIZE_MAX >= WorkerConstants.TMDB_NORMAL_BATCH_SIZE_MIN)
        assertTrue(
            WorkerConstants.TMDB_NORMAL_BATCH_SIZE_DEFAULT in
                WorkerConstants.TMDB_NORMAL_BATCH_SIZE_MIN..WorkerConstants.TMDB_NORMAL_BATCH_SIZE_MAX,
        )
    }

    @Test
    fun `Normal device batch size range is 50-150`() {
        assertEquals(50, WorkerConstants.TMDB_NORMAL_BATCH_SIZE_MIN)
        assertEquals(150, WorkerConstants.TMDB_NORMAL_BATCH_SIZE_MAX)
    }

    @Test
    fun `FireTV max is less than normal min for clear separation`() {
        assertTrue(WorkerConstants.TMDB_FIRETV_BATCH_SIZE_MAX < WorkerConstants.TMDB_NORMAL_BATCH_SIZE_MIN)
    }

    // ========== Cooldown and Attempts ==========

    @Test
    fun `TMDB cooldown is 24 hours`() {
        val expectedMs = 24L * 60 * 60 * 1000 // 24 hours in ms
        assertEquals(expectedMs, WorkerConstants.TMDB_COOLDOWN_MS)
    }

    @Test
    fun `TMDB max attempts is 3`() {
        assertEquals(3, WorkerConstants.TMDB_MAX_ATTEMPTS)
    }

    // ========== Worker Tags ==========

    @Test
    fun `TMDB worker tags are defined`() {
        assertEquals("worker/TmdbEnrichmentOrchestratorWorker", WorkerConstants.TAG_WORKER_TMDB_ORCHESTRATOR)
        assertEquals("worker/TmdbEnrichmentBatchWorker", WorkerConstants.TAG_WORKER_TMDB_BATCH)
        assertEquals("worker/TmdbEnrichmentContinuationWorker", WorkerConstants.TAG_WORKER_TMDB_CONTINUATION)
    }

    // ========== Input Data Keys ==========

    @Test
    fun `TMDB input data keys are defined`() {
        assertEquals("tmdb_scope", WorkerConstants.KEY_TMDB_SCOPE)
        assertEquals("tmdb_force_refresh", WorkerConstants.KEY_TMDB_FORCE_REFRESH)
        assertEquals("tmdb_batch_size_hint", WorkerConstants.KEY_TMDB_BATCH_SIZE_HINT)
        assertEquals("tmdb_batch_cursor", WorkerConstants.KEY_TMDB_BATCH_CURSOR)
    }
}
