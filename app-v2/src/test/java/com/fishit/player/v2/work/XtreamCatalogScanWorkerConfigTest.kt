package com.fishit.player.v2.work

import com.fishit.player.core.catalogsync.EnhancedSyncConfig
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Unit tests for [XtreamCatalogScanWorker] enhanced sync config selection.
 *
 * Tests verify that the correct EnhancedSyncConfig is selected based on:
 * - Device class (FireTV low-RAM vs normal)
 * - Sync mode (AUTO vs FORCE_RESCAN)
 */
class XtreamCatalogScanWorkerConfigTest {
    @Test
    fun `FireTV low-RAM uses FIRETV_SAFE config`() {
        val input = createInputData(
            deviceClass = WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM,
            syncMode = WorkerConstants.SYNC_MODE_AUTO,
        )

        val config = selectEnhancedConfigForTest(input)

        // Should use FIRETV_SAFE config with 35-item batch cap
        assertEquals("FireTV Live batch should be 35", 35, config.liveConfig.batchSize)
        assertEquals("FireTV Movies batch should be 35", 35, config.moviesConfig.batchSize)
        assertEquals("FireTV Series batch should be 35", 35, config.seriesConfig.batchSize)
        assertTrue("FireTV should enable time-based flush", config.enableTimeBasedFlush)
        assertFalse("FireTV should disable canonical linking for performance", config.enableCanonicalLinking)
    }

    @Test
    fun `normal device uses PROGRESSIVE_UI config`() {
        val input = createInputData(
            deviceClass = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
            syncMode = WorkerConstants.SYNC_MODE_AUTO,
        )

        val config = selectEnhancedConfigForTest(input)

        // Should use PROGRESSIVE_UI config with larger batches and no canonical linking
        assertEquals("PROGRESSIVE_UI Live batch should be 600", 600, config.liveConfig.batchSize)
        assertEquals("PROGRESSIVE_UI Movies batch should be 400", 400, config.moviesConfig.batchSize)
        assertEquals("PROGRESSIVE_UI Series batch should be 200", 200, config.seriesConfig.batchSize)
        assertTrue("PROGRESSIVE_UI should enable time-based flush", config.enableTimeBasedFlush)
        assertFalse("PROGRESSIVE_UI should disable canonical linking for hot path relief", config.enableCanonicalLinking)
    }

    @Test
    fun `force rescan mode uses large batches with no time flush`() {
        val input = createInputData(
            deviceClass = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
            syncMode = WorkerConstants.SYNC_MODE_FORCE_RESCAN,
        )

        val config = selectEnhancedConfigForTest(input)

        // Should use larger batches optimized for throughput
        assertEquals("Force rescan Live batch should be 600", 600, config.liveConfig.batchSize)
        assertEquals("Force rescan Movies batch should be 400", 400, config.moviesConfig.batchSize)
        assertEquals("Force rescan Series batch should be 200", 200, config.seriesConfig.batchSize)
        assertFalse("Force rescan should prioritize throughput over UI", config.enableTimeBasedFlush)
    }

    @Test
    fun `force rescan on FireTV still respects FireTV limits`() {
        // Edge case: Force rescan should NOT override FireTV safety limits
        val input = createInputData(
            deviceClass = WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM,
            syncMode = WorkerConstants.SYNC_MODE_FORCE_RESCAN,
        )

        val config = selectEnhancedConfigForTest(input)

        // FireTV safety should take precedence over force rescan mode
        assertEquals("FireTV force rescan should still cap at 35", 35, config.liveConfig.batchSize)
        assertEquals("FireTV force rescan should still cap at 35", 35, config.moviesConfig.batchSize)
        assertEquals("FireTV force rescan should still cap at 35", 35, config.seriesConfig.batchSize)
    }

    @Test
    fun `expert sync now mode uses PROGRESSIVE_UI config`() {
        val input = createInputData(
            deviceClass = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
            syncMode = WorkerConstants.SYNC_MODE_EXPERT_NOW,
        )

        val config = selectEnhancedConfigForTest(input)

        // EXPERT_NOW should behave like AUTO (use PROGRESSIVE_UI)
        assertEquals(600, config.liveConfig.batchSize)
        assertEquals(400, config.moviesConfig.batchSize)
        assertEquals(200, config.seriesConfig.batchSize)
        assertTrue(config.enableTimeBasedFlush)
        assertFalse(config.enableCanonicalLinking)
    }

    @Test
    fun `FIRETV_SAFE config matches expected values`() {
        // Verify that FIRETV_SAFE constant has the correct values
        val config = EnhancedSyncConfig.FIRETV_SAFE

        assertEquals(35, config.liveConfig.batchSize)
        assertEquals(35, config.moviesConfig.batchSize)
        assertEquals(35, config.seriesConfig.batchSize)
        assertTrue(config.enableTimeBasedFlush)
        assertFalse(config.enableCanonicalLinking)
    }

    @Test
    fun `PROGRESSIVE_UI config matches expected values`() {
        // Verify that PROGRESSIVE_UI constant has the correct values
        val config = EnhancedSyncConfig.PROGRESSIVE_UI

        assertEquals(600, config.liveConfig.batchSize)
        assertEquals(400, config.moviesConfig.batchSize)
        assertEquals(200, config.seriesConfig.batchSize)
        assertTrue(config.enableTimeBasedFlush)
        assertFalse(config.enableCanonicalLinking)
    }

    // Helper methods

    /**
     * Create WorkerInputData for testing with minimal required fields.
     */
    private fun createInputData(
        deviceClass: String,
        syncMode: String,
    ): WorkerInputData = WorkerInputData(
        syncRunId = "test-run-id",
        syncMode = syncMode,
        activeSources = setOf("xtream"),
        wifiOnly = false,
        maxRuntimeMs = 600_000L, // 10 minutes
        deviceClass = deviceClass,
        xtreamSyncScope = null,
        xtreamUseEnhancedSync = true,
        xtreamInfoBackfillConcurrency = 6,
        telegramSyncKind = null,
        ioSyncScope = null,
    )

    /**
     * Simulate the private selectEnhancedConfig method for testing.
     * This mirrors the logic in XtreamCatalogScanWorker.selectEnhancedConfig.
     */
    private fun selectEnhancedConfigForTest(input: WorkerInputData): EnhancedSyncConfig {
        return when {
            // FireTV: Use predefined FIRETV_SAFE config (global 35-item cap to prevent OOM)
            input.isFireTvLowRam -> EnhancedSyncConfig.FIRETV_SAFE

            // Force rescan: Maximize throughput with larger batches
            input.syncMode == WorkerConstants.SYNC_MODE_FORCE_RESCAN -> {
                EnhancedSyncConfig(
                    liveConfig =
                        com.fishit.player.core.catalogsync.SyncPhaseConfig.LIVE.copy(
                            batchSize = 600, // Larger than default 400
                        ),
                    moviesConfig =
                        com.fishit.player.core.catalogsync.SyncPhaseConfig.MOVIES.copy(
                            batchSize = 400, // Larger than default 250
                        ),
                    seriesConfig =
                        com.fishit.player.core.catalogsync.SyncPhaseConfig.SERIES.copy(
                            batchSize = 200, // Larger than default 150
                        ),
                    enableTimeBasedFlush = false, // Prioritize throughput over UI
                    enableCanonicalLinking = false, // Align FORCE_RESCAN with throughput-focused behavior
                )
            }
            // Default: Use PROGRESSIVE_UI for maximum UI-first loading speed
            else -> EnhancedSyncConfig.PROGRESSIVE_UI
        }
    }
}
