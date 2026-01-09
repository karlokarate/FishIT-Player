package com.fishit.player.core.persistence.config

import android.content.Context
import com.fishit.player.core.device.DeviceClass
import com.fishit.player.core.device.DeviceClassProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for ObxWriteConfig - SSOT for ObjectBox batch sizes.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2 W-17 (FireTV Safety)
 * Uses DeviceClassProvider architecture from core:device-api.
 */
class ObxWriteConfigTest {
    // ========== Constants ==========

    @Test
    fun `FireTV batch cap is 35`() {
        assertEquals(35, ObxWriteConfig.FIRETV_BATCH_CAP)
    }

    @Test
    fun `FireTV backfill chunk size is 500`() {
        assertEquals(500, ObxWriteConfig.FIRETV_BACKFILL_CHUNK_SIZE)
    }

    @Test
    fun `Normal batch size is 100`() {
        assertEquals(100, ObxWriteConfig.NORMAL_BATCH_SIZE)
    }

    @Test
    fun `Normal backfill chunk size is 2000`() {
        assertEquals(2000, ObxWriteConfig.NORMAL_BACKFILL_CHUNK_SIZE)
    }

    @Test
    fun `Normal page size is 4000`() {
        assertEquals(4000, ObxWriteConfig.NORMAL_PAGE_SIZE)
    }

    // ========== Sync Phase Constants ==========

    @Test
    fun `Live batch size is 600`() {
        assertEquals(600, ObxWriteConfig.SYNC_LIVE_BATCH_PHONE)
    }

    @Test
    fun `Movies batch size is 400`() {
        assertEquals(400, ObxWriteConfig.SYNC_MOVIES_BATCH_PHONE)
    }

    @Test
    fun `Series batch size is 200`() {
        assertEquals(200, ObxWriteConfig.SYNC_SERIES_BATCH_PHONE)
    }

    @Test
    fun `Episodes batch size is 200`() {
        assertEquals(200, ObxWriteConfig.SYNC_EPISODES_BATCH_PHONE)
    }

    // ========== Device-Aware Accessors (TV_LOW_RAM) ==========

    @Test
    fun `getBatchSize returns FireTV cap for TV_LOW_RAM devices`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.TV_LOW_RAM

        assertEquals(35, ObxWriteConfig.getBatchSize(deviceClassProvider, context))
    }

    @Test
    fun `getSyncLiveBatchSize returns FireTV cap for TV_LOW_RAM devices`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.TV_LOW_RAM

        assertEquals(35, ObxWriteConfig.getSyncLiveBatchSize(deviceClassProvider, context))
    }

    @Test
    fun `getSyncMoviesBatchSize returns FireTV cap for TV_LOW_RAM devices`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.TV_LOW_RAM

        assertEquals(35, ObxWriteConfig.getSyncMoviesBatchSize(deviceClassProvider, context))
    }

    @Test
    fun `getSyncSeriesBatchSize returns FireTV cap for TV_LOW_RAM devices`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.TV_LOW_RAM

        assertEquals(35, ObxWriteConfig.getSyncSeriesBatchSize(deviceClassProvider, context))
    }

    @Test
    fun `getBackfillChunkSize returns 500 for TV_LOW_RAM devices`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.TV_LOW_RAM

        assertEquals(500, ObxWriteConfig.getBackfillChunkSize(deviceClassProvider, context))
    }

    @Test
    fun `getPageSize returns 500 for TV_LOW_RAM devices`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.TV_LOW_RAM

        assertEquals(500, ObxWriteConfig.getPageSize(deviceClassProvider, context))
    }

    // ========== Device-Aware Accessors (PHONE_TABLET / TV) ==========

    @Test
    fun `getBatchSize returns 100 for PHONE_TABLET devices`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.PHONE_TABLET

        assertEquals(100, ObxWriteConfig.getBatchSize(deviceClassProvider, context))
    }

    @Test
    fun `getBatchSize returns 100 for TV devices (not low-RAM)`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.TV

        assertEquals(100, ObxWriteConfig.getBatchSize(deviceClassProvider, context))
    }

    @Test
    fun `getSyncLiveBatchSize returns 600 for PHONE_TABLET devices`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.PHONE_TABLET

        assertEquals(600, ObxWriteConfig.getSyncLiveBatchSize(deviceClassProvider, context))
    }

    @Test
    fun `getSyncMoviesBatchSize returns 400 for PHONE_TABLET devices`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.PHONE_TABLET

        assertEquals(400, ObxWriteConfig.getSyncMoviesBatchSize(deviceClassProvider, context))
    }

    @Test
    fun `getSyncSeriesBatchSize returns 200 for PHONE_TABLET devices`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.PHONE_TABLET

        assertEquals(200, ObxWriteConfig.getSyncSeriesBatchSize(deviceClassProvider, context))
    }

    @Test
    fun `getBackfillChunkSize returns 2000 for PHONE_TABLET devices`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.PHONE_TABLET

        assertEquals(2000, ObxWriteConfig.getBackfillChunkSize(deviceClassProvider, context))
    }

    @Test
    fun `getPageSize returns 4000 for PHONE_TABLET devices`() {
        val context = mockk<Context>()
        val deviceClassProvider = mockk<DeviceClassProvider>()
        every { deviceClassProvider.getDeviceClass(context) } returns DeviceClass.PHONE_TABLET

        assertEquals(4000, ObxWriteConfig.getPageSize(deviceClassProvider, context))
    }

    // ========== FireTV Safety Contract (W-17) ==========

    @Test
    fun `FireTV batch cap is significantly smaller than normal batch size`() {
        assertTrue(
            "FireTV cap (${ObxWriteConfig.FIRETV_BATCH_CAP}) should be < 50% of normal " +
                "batch (${ObxWriteConfig.NORMAL_BATCH_SIZE})",
            ObxWriteConfig.FIRETV_BATCH_CAP < ObxWriteConfig.NORMAL_BATCH_SIZE / 2,
        )
    }

    @Test
    fun `FireTV backfill is smaller than normal backfill`() {
        assertTrue(
            ObxWriteConfig.FIRETV_BACKFILL_CHUNK_SIZE < ObxWriteConfig.NORMAL_BACKFILL_CHUNK_SIZE,
        )
    }

    @Test
    fun `Phase-specific batches are larger than base batch for normal devices`() {
        // Live channels can use larger batches (smaller entities)
        assertTrue(ObxWriteConfig.SYNC_LIVE_BATCH_PHONE > ObxWriteConfig.NORMAL_BATCH_SIZE)

        // Movies use larger batches (medium entities)
        assertTrue(ObxWriteConfig.SYNC_MOVIES_BATCH_PHONE > ObxWriteConfig.NORMAL_BATCH_SIZE)

        // Series use larger batches (but smaller than Live/Movies)
        assertTrue(ObxWriteConfig.SYNC_SERIES_BATCH_PHONE > ObxWriteConfig.NORMAL_BATCH_SIZE)
    }

    @Test
    fun `Batch size ordering is correct - Live largest, Series smallest`() {
        // Live channels have smallest payload → largest batch
        // Series have largest payload → smallest batch
        assertTrue(ObxWriteConfig.SYNC_LIVE_BATCH_PHONE > ObxWriteConfig.SYNC_MOVIES_BATCH_PHONE)
        assertTrue(ObxWriteConfig.SYNC_MOVIES_BATCH_PHONE > ObxWriteConfig.SYNC_SERIES_BATCH_PHONE)
    }
}
