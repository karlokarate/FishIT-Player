package com.fishit.player.v2.work

import androidx.work.Data
import com.fishit.player.core.persistence.config.ObxWriteConfig
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Unit tests for [WorkerInputData] runtime budget handling.
 *
 * Specifically tests the failsafe for the runtime=0ms bug.
 */
class WorkerInputDataTest {
    @Test
    fun `missing max_runtime_ms key uses default`() {
        val data =
            Data
                .Builder()
                .putString(WorkerConstants.KEY_SYNC_RUN_ID, "test-run")
                .putString(WorkerConstants.KEY_SYNC_MODE, WorkerConstants.SYNC_MODE_AUTO)
                // Intentionally NOT setting KEY_MAX_RUNTIME_MS
                .build()

        val input = WorkerInputData.from(data)

        assertEquals(WorkerConstants.DEFAULT_MAX_RUNTIME_MS, input.maxRuntimeMs)
    }

    @Test
    fun `max_runtime_ms set to 0 uses default`() {
        val data =
            Data
                .Builder()
                .putString(WorkerConstants.KEY_SYNC_RUN_ID, "test-run")
                .putString(WorkerConstants.KEY_SYNC_MODE, WorkerConstants.SYNC_MODE_AUTO)
                .putLong(WorkerConstants.KEY_MAX_RUNTIME_MS, 0L)
                .build()

        val input = WorkerInputData.from(data)

        assertEquals(WorkerConstants.DEFAULT_MAX_RUNTIME_MS, input.maxRuntimeMs)
    }

    @Test
    fun `max_runtime_ms set to negative uses default`() {
        val data =
            Data
                .Builder()
                .putString(WorkerConstants.KEY_SYNC_RUN_ID, "test-run")
                .putString(WorkerConstants.KEY_SYNC_MODE, WorkerConstants.SYNC_MODE_AUTO)
                .putLong(WorkerConstants.KEY_MAX_RUNTIME_MS, -100L)
                .build()

        val input = WorkerInputData.from(data)

        assertEquals(WorkerConstants.DEFAULT_MAX_RUNTIME_MS, input.maxRuntimeMs)
    }

    @Test
    fun `valid max_runtime_ms is preserved`() {
        val customRuntime = 5L * 60 * 1000 // 5 minutes

        val data =
            Data
                .Builder()
                .putString(WorkerConstants.KEY_SYNC_RUN_ID, "test-run")
                .putString(WorkerConstants.KEY_SYNC_MODE, WorkerConstants.SYNC_MODE_AUTO)
                .putLong(WorkerConstants.KEY_MAX_RUNTIME_MS, customRuntime)
                .build()

        val input = WorkerInputData.from(data)

        assertEquals(customRuntime, input.maxRuntimeMs)
    }

    @Test
    fun `default max_runtime_ms is reasonable`() {
        // DEFAULT should be at least 1 minute (60_000ms)
        assertTrue("DEFAULT should be at least 1 minute", WorkerConstants.DEFAULT_MAX_RUNTIME_MS >= 60_000L)

        // DEFAULT should be at most 30 minutes (1_800_000ms)
        assertTrue("DEFAULT should be at most 30 minutes", WorkerConstants.DEFAULT_MAX_RUNTIME_MS <= 1_800_000L)
    }

    @Test
    fun `runtime budget greater than 0ms after failsafe`() {
        // This is the core test for the runtime=0ms bug fix
        // Even with empty input, maxRuntimeMs should be > 0

        val emptyData = Data.Builder().build()
        val input = WorkerInputData.from(emptyData)

        assertTrue("Runtime budget must be > 0ms", input.maxRuntimeMs > 0L)
        assertTrue("Runtime budget should be at least 1 minute", input.maxRuntimeMs >= 60_000L)
    }

    @Test
    fun `batch size based on device class`() {
        val fireTvInput =
            WorkerInputData(
                syncRunId = "test",
                syncMode = WorkerConstants.SYNC_MODE_AUTO,
                activeSources = emptySet(),
                wifiOnly = false,
                maxRuntimeMs = WorkerConstants.DEFAULT_MAX_RUNTIME_MS,
                deviceClass = WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM,
                xtreamSyncScope = null,
                xtreamInfoBackfillConcurrency = 4,
                telegramSyncKind = null,
                ioSyncScope = null,
            )

        val normalInput =
            WorkerInputData(
                syncRunId = "test",
                syncMode = WorkerConstants.SYNC_MODE_AUTO,
                activeSources = emptySet(),
                wifiOnly = false,
                maxRuntimeMs = WorkerConstants.DEFAULT_MAX_RUNTIME_MS,
                deviceClass = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
                xtreamSyncScope = null,
                xtreamInfoBackfillConcurrency = 6,
                telegramSyncKind = null,
                ioSyncScope = null,
            )

        assertEquals(ObxWriteConfig.FIRETV_BATCH_CAP, fireTvInput.batchSize)
        assertEquals(ObxWriteConfig.NORMAL_BATCH_SIZE, normalInput.batchSize)
    }
}
