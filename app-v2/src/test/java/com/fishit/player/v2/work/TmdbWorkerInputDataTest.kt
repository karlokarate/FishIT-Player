package com.fishit.player.v2.work

import androidx.work.Data
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for TMDB worker input data parsing and batch size clamping.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-14: TMDB InputData keys
 * - W-17: FireTV Safety - batch size clamping
 * - W-22: TMDB Scope Priority
 */
class TmdbWorkerInputDataTest {
    // ========== Parsing Tests ==========

    @Test
    fun `from parses all fields correctly`() {
        val data =
            Data
                .Builder()
                .putString(WorkerConstants.KEY_SYNC_RUN_ID, "test-run-123")
                .putString(WorkerConstants.KEY_TMDB_SCOPE, WorkerConstants.TMDB_SCOPE_DETAILS_BY_ID)
                .putBoolean(WorkerConstants.KEY_TMDB_FORCE_REFRESH, true)
                .putInt(WorkerConstants.KEY_TMDB_BATCH_SIZE_HINT, 100)
                .putString(WorkerConstants.KEY_TMDB_BATCH_CURSOR, "cursor-abc")
                .putString(WorkerConstants.KEY_DEVICE_CLASS, WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET)
                .putLong(WorkerConstants.KEY_MAX_RUNTIME_MS, 300_000L)
                .build()

        val input = TmdbWorkerInputData.from(data)

        assertEquals("test-run-123", input.runId)
        assertEquals(WorkerConstants.TMDB_SCOPE_DETAILS_BY_ID, input.tmdbScope)
        assertTrue(input.forceRefresh)
        assertEquals(100, input.batchSizeHint)
        assertEquals("cursor-abc", input.batchCursor)
        assertEquals(WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET, input.deviceClass)
        assertEquals(300_000L, input.maxRuntimeMs)
    }

    @Test
    fun `from uses defaults for missing fields`() {
        val data = Data.Builder().build()

        val input = TmdbWorkerInputData.from(data)

        // runId is auto-generated UUID
        assertTrue(input.runId.isNotBlank())
        assertEquals(WorkerConstants.TMDB_SCOPE_BOTH, input.tmdbScope)
        assertFalse(input.forceRefresh)
        assertNull(input.batchCursor)
        assertEquals(WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET, input.deviceClass)
        assertEquals(WorkerConstants.DEFAULT_MAX_RUNTIME_MS, input.maxRuntimeMs)
    }

    // ========== FireTV Batch Size Clamping Tests (W-17) ==========

    @Test
    fun `effectiveBatchSize clamps to FireTV range on low-RAM devices`() {
        val input =
            TmdbWorkerInputData(
                runId = "test",
                tmdbScope = WorkerConstants.TMDB_SCOPE_BOTH,
                forceRefresh = false,
                batchSizeHint = 5, // Below minimum
                batchCursor = null,
                deviceClass = WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM,
                maxRuntimeMs = 300_000L,
            )

        // Should clamp to minimum (10)
        assertEquals(WorkerConstants.TMDB_FIRETV_BATCH_SIZE_MIN, input.effectiveBatchSize)
    }

    @Test
    fun `effectiveBatchSize clamps high value to FireTV max`() {
        val input =
            TmdbWorkerInputData(
                runId = "test",
                tmdbScope = WorkerConstants.TMDB_SCOPE_BOTH,
                forceRefresh = false,
                batchSizeHint = 100, // Above maximum (25)
                batchCursor = null,
                deviceClass = WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM,
                maxRuntimeMs = 300_000L,
            )

        // Should clamp to maximum (25)
        assertEquals(WorkerConstants.TMDB_FIRETV_BATCH_SIZE_MAX, input.effectiveBatchSize)
    }

    @Test
    fun `effectiveBatchSize allows valid FireTV values`() {
        val input =
            TmdbWorkerInputData(
                runId = "test",
                tmdbScope = WorkerConstants.TMDB_SCOPE_BOTH,
                forceRefresh = false,
                batchSizeHint = 20, // Within range (10-25)
                batchCursor = null,
                deviceClass = WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM,
                maxRuntimeMs = 300_000L,
            )

        assertEquals(20, input.effectiveBatchSize)
    }

    @Test
    fun `effectiveBatchSize clamps to normal range on regular devices`() {
        val input =
            TmdbWorkerInputData(
                runId = "test",
                tmdbScope = WorkerConstants.TMDB_SCOPE_BOTH,
                forceRefresh = false,
                batchSizeHint = 10, // Below minimum (50)
                batchCursor = null,
                deviceClass = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
                maxRuntimeMs = 300_000L,
            )

        // Should clamp to minimum (50)
        assertEquals(WorkerConstants.TMDB_NORMAL_BATCH_SIZE_MIN, input.effectiveBatchSize)
    }

    @Test
    fun `effectiveBatchSize clamps high value to normal max`() {
        val input =
            TmdbWorkerInputData(
                runId = "test",
                tmdbScope = WorkerConstants.TMDB_SCOPE_BOTH,
                forceRefresh = false,
                batchSizeHint = 500, // Above maximum (150)
                batchCursor = null,
                deviceClass = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
                maxRuntimeMs = 300_000L,
            )

        // Should clamp to maximum (150)
        assertEquals(WorkerConstants.TMDB_NORMAL_BATCH_SIZE_MAX, input.effectiveBatchSize)
    }

    @Test
    fun `effectiveBatchSize allows valid normal values`() {
        val input =
            TmdbWorkerInputData(
                runId = "test",
                tmdbScope = WorkerConstants.TMDB_SCOPE_BOTH,
                forceRefresh = false,
                batchSizeHint = 100, // Within range (50-150)
                batchCursor = null,
                deviceClass = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
                maxRuntimeMs = 300_000L,
            )

        assertEquals(100, input.effectiveBatchSize)
    }

    // ========== Retry Limit Tests ==========

    @Test
    fun `retryLimit returns AUTO limit for normal mode`() {
        val input =
            TmdbWorkerInputData(
                runId = "test",
                tmdbScope = WorkerConstants.TMDB_SCOPE_BOTH,
                forceRefresh = false,
                batchSizeHint = 50,
                batchCursor = null,
                deviceClass = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
                maxRuntimeMs = 300_000L,
            )

        assertEquals(WorkerConstants.RETRY_LIMIT_AUTO, input.retryLimit)
    }

    @Test
    fun `retryLimit returns EXPERT limit for force refresh`() {
        val input =
            TmdbWorkerInputData(
                runId = "test",
                tmdbScope = WorkerConstants.TMDB_SCOPE_BOTH,
                forceRefresh = true,
                batchSizeHint = 50,
                batchCursor = null,
                deviceClass = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
                maxRuntimeMs = 300_000L,
            )

        assertEquals(WorkerConstants.RETRY_LIMIT_EXPERT, input.retryLimit)
    }

    // ========== Device Class Detection Tests ==========

    @Test
    fun `isFireTvLowRam returns true for FireTV device class`() {
        val input =
            TmdbWorkerInputData(
                runId = "test",
                tmdbScope = WorkerConstants.TMDB_SCOPE_BOTH,
                forceRefresh = false,
                batchSizeHint = 15,
                batchCursor = null,
                deviceClass = WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM,
                maxRuntimeMs = 300_000L,
            )

        assertTrue(input.isFireTvLowRam)
    }

    @Test
    fun `isFireTvLowRam returns false for normal device class`() {
        val input =
            TmdbWorkerInputData(
                runId = "test",
                tmdbScope = WorkerConstants.TMDB_SCOPE_BOTH,
                forceRefresh = false,
                batchSizeHint = 75,
                batchCursor = null,
                deviceClass = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
                maxRuntimeMs = 300_000L,
            )

        assertFalse(input.isFireTvLowRam)
    }

    // ========== Input Data Building Tests ==========

    @Test
    fun `buildInputData creates correct Data with all fields`() {
        val data =
            TmdbWorkerInputData.buildInputData(
                runId = "build-test-123",
                tmdbScope = WorkerConstants.TMDB_SCOPE_RESOLVE_MISSING_IDS,
                forceRefresh = true,
                batchSizeHint = 80,
                batchCursor = "cursor-xyz",
                deviceClass = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
                maxRuntimeMs = 600_000L,
            )

        assertEquals("build-test-123", data.getString(WorkerConstants.KEY_SYNC_RUN_ID))
        assertEquals(WorkerConstants.TMDB_SCOPE_RESOLVE_MISSING_IDS, data.getString(WorkerConstants.KEY_TMDB_SCOPE))
        assertTrue(data.getBoolean(WorkerConstants.KEY_TMDB_FORCE_REFRESH, false))
        assertEquals(80, data.getInt(WorkerConstants.KEY_TMDB_BATCH_SIZE_HINT, 0))
        assertEquals("cursor-xyz", data.getString(WorkerConstants.KEY_TMDB_BATCH_CURSOR))
        assertEquals(WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET, data.getString(WorkerConstants.KEY_DEVICE_CLASS))
        assertEquals(600_000L, data.getLong(WorkerConstants.KEY_MAX_RUNTIME_MS, 0))
    }

    @Test
    fun `buildInputData uses FireTV default batch size`() {
        val data =
            TmdbWorkerInputData.buildInputData(
                deviceClass = WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM,
            )

        assertEquals(
            WorkerConstants.TMDB_FIRETV_BATCH_SIZE_DEFAULT,
            data.getInt(WorkerConstants.KEY_TMDB_BATCH_SIZE_HINT, 0),
        )
    }

    @Test
    fun `buildInputData uses normal default batch size`() {
        val data =
            TmdbWorkerInputData.buildInputData(
                deviceClass = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
            )

        assertEquals(
            WorkerConstants.TMDB_NORMAL_BATCH_SIZE_DEFAULT,
            data.getInt(WorkerConstants.KEY_TMDB_BATCH_SIZE_HINT, 0),
        )
    }
}
