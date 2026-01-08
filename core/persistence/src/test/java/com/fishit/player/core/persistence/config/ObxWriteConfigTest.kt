package com.fishit.player.core.persistence.config

import android.content.Context
import com.fishit.player.infra.transport.xtream.XtreamTransportConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for ObxWriteConfig - SSOT for ObjectBox batch sizes.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2 W-17 (FireTV Safety)
 */
class ObxWriteConfigTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mockkObject(XtreamTransportConfig)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

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

    // ========== Phase-Specific Batch Sizes ==========

    @Test
    fun `Sync live batch size is 600`() {
        assertEquals(600, ObxWriteConfig.SYNC_LIVE_BATCH_PHONE)
    }

    @Test
    fun `Sync movies batch size is 400`() {
        assertEquals(400, ObxWriteConfig.SYNC_MOVIES_BATCH_PHONE)
    }

    @Test
    fun `Sync series batch size is 200`() {
        assertEquals(200, ObxWriteConfig.SYNC_SERIES_BATCH_PHONE)
    }

    @Test
    fun `Sync episodes batch size is 200`() {
        assertEquals(200, ObxWriteConfig.SYNC_EPISODES_BATCH_PHONE)
    }

    // ========== Device-Aware Accessors (FireTV) ==========

    @Test
    fun `getBatchSize returns FireTV cap for TV devices`() {
        every { XtreamTransportConfig.detectDeviceClass(context) } returns
            XtreamTransportConfig.DeviceClass.TV_LOW_RAM

        assertEquals(35, ObxWriteConfig.getBatchSize(context))
    }

    @Test
    fun `getSyncLiveBatchSize returns FireTV cap for TV devices`() {
        every { XtreamTransportConfig.detectDeviceClass(context) } returns
            XtreamTransportConfig.DeviceClass.TV_LOW_RAM

        assertEquals(35, ObxWriteConfig.getSyncLiveBatchSize(context))
    }

    @Test
    fun `getSyncMoviesBatchSize returns FireTV cap for TV devices`() {
        every { XtreamTransportConfig.detectDeviceClass(context) } returns
            XtreamTransportConfig.DeviceClass.TV_LOW_RAM

        assertEquals(35, ObxWriteConfig.getSyncMoviesBatchSize(context))
    }

    @Test
    fun `getSyncSeriesBatchSize returns FireTV cap for TV devices`() {
        every { XtreamTransportConfig.detectDeviceClass(context) } returns
            XtreamTransportConfig.DeviceClass.TV_LOW_RAM

        assertEquals(35, ObxWriteConfig.getSyncSeriesBatchSize(context))
    }

    @Test
    fun `getBackfillChunkSize returns 500 for TV devices`() {
        every { XtreamTransportConfig.detectDeviceClass(context) } returns
            XtreamTransportConfig.DeviceClass.TV_LOW_RAM

        assertEquals(500, ObxWriteConfig.getBackfillChunkSize(context))
    }

    @Test
    fun `getPageSize returns 500 for TV devices`() {
        every { XtreamTransportConfig.detectDeviceClass(context) } returns
            XtreamTransportConfig.DeviceClass.TV_LOW_RAM

        assertEquals(500, ObxWriteConfig.getPageSize(context))
    }

    // ========== Device-Aware Accessors (Phone/Tablet) ==========

    @Test
    fun `getBatchSize returns 100 for normal devices`() {
        every { XtreamTransportConfig.detectDeviceClass(context) } returns
            XtreamTransportConfig.DeviceClass.PHONE_TABLET

        assertEquals(100, ObxWriteConfig.getBatchSize(context))
    }

    @Test
    fun `getSyncLiveBatchSize returns 600 for normal devices`() {
        every { XtreamTransportConfig.detectDeviceClass(context) } returns
            XtreamTransportConfig.DeviceClass.PHONE_TABLET

        assertEquals(600, ObxWriteConfig.getSyncLiveBatchSize(context))
    }

    @Test
    fun `getSyncMoviesBatchSize returns 400 for normal devices`() {
        every { XtreamTransportConfig.detectDeviceClass(context) } returns
            XtreamTransportConfig.DeviceClass.PHONE_TABLET

        assertEquals(400, ObxWriteConfig.getSyncMoviesBatchSize(context))
    }

    @Test
    fun `getSyncSeriesBatchSize returns 200 for normal devices`() {
        every { XtreamTransportConfig.detectDeviceClass(context) } returns
            XtreamTransportConfig.DeviceClass.PHONE_TABLET

        assertEquals(200, ObxWriteConfig.getSyncSeriesBatchSize(context))
    }

    @Test
    fun `getBackfillChunkSize returns 2000 for normal devices`() {
        every { XtreamTransportConfig.detectDeviceClass(context) } returns
            XtreamTransportConfig.DeviceClass.PHONE_TABLET

        assertEquals(2000, ObxWriteConfig.getBackfillChunkSize(context))
    }

    @Test
    fun `getPageSize returns 4000 for normal devices`() {
        every { XtreamTransportConfig.detectDeviceClass(context) } returns
            XtreamTransportConfig.DeviceClass.PHONE_TABLET

        assertEquals(4000, ObxWriteConfig.getPageSize(context))
    }

    // ========== FireTV Safety Contract (W-17) ==========

    @Test
    fun `FireTV batch cap is significantly smaller than normal batch size`() {
        assertTrue(
            "FireTV cap (${ObxWriteConfig.FIRETV_BATCH_CAP}) should be < 50% of normal " +
                "batch (${ObxWriteConfig.NORMAL_BATCH_SIZE})",
            ObxWriteConfig.FIRETV_BATCH_CAP < ObxWriteConfig.NORMAL_BATCH_SIZE / 2
        )
    }

    @Test
    fun `FireTV backfill is smaller than normal backfill`() {
        assertTrue(
            ObxWriteConfig.FIRETV_BACKFILL_CHUNK_SIZE < ObxWriteConfig.NORMAL_BACKFILL_CHUNK_SIZE
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
        assertTrue(
            "Live (${ObxWriteConfig.SYNC_LIVE_BATCH_PHONE}) > Movies (${ObxWriteConfig.SYNC_MOVIES_BATCH_PHONE})",
            ObxWriteConfig.SYNC_LIVE_BATCH_PHONE > ObxWriteConfig.SYNC_MOVIES_BATCH_PHONE
        )
        assertTrue(
            "Movies (${ObxWriteConfig.SYNC_MOVIES_BATCH_PHONE}) > Series (${ObxWriteConfig.SYNC_SERIES_BATCH_PHONE})",
            ObxWriteConfig.SYNC_MOVIES_BATCH_PHONE > ObxWriteConfig.SYNC_SERIES_BATCH_PHONE
        )
    }

    // ========== PR #604 Values Verification ==========

    @Test
    fun `Live batch size matches PR #604 optimization (600)`() {
        assertEquals(
            "Live batch size should be 600 per PR #604 speed optimization",
            600,
            ObxWriteConfig.SYNC_LIVE_BATCH_PHONE
        )
    }

    @Test
    fun `Movies batch size matches PR #604 optimization (400)`() {
        assertEquals(
            "Movies batch size should be 400 per PR #604 speed optimization",
            400,
            ObxWriteConfig.SYNC_MOVIES_BATCH_PHONE
        )
    }

    @Test
    fun `Series batch size matches PR #604 optimization (200)`() {
        assertEquals(
            "Series batch size should be 200 per PR #604 speed optimization",
            200,
            ObxWriteConfig.SYNC_SERIES_BATCH_PHONE
        )
    }
}
