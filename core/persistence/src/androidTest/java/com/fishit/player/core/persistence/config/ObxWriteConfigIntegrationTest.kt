package com.fishit.player.core.persistence.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fishit.player.core.device.DeviceClass
import com.fishit.player.core.device.DeviceClassProvider
import com.fishit.player.infra.device.AndroidDeviceClassProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for ObxWriteConfig with real Android context.
 *
 * Tests the complete flow:
 * 1. Real AndroidDeviceClassProvider detecting actual device characteristics
 * 2. ObxWriteConfig using device detection for batch sizing
 * 3. Proper batch size selection based on device class and phase hints
 *
 * Related Issues:
 * - #606: ObjectBox Performance Optimization
 * - #609: Device Detection Architecture
 *
 * Note: These tests validate the device detection and batch sizing logic.
 * ObjectBox Box operations with putChunked are covered in unit tests with mocks.
 */
@RunWith(AndroidJUnit4::class)
class ObxWriteConfigIntegrationTest {

    private lateinit var context: Context
    private lateinit var deviceClassProvider: DeviceClassProvider

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        deviceClassProvider = AndroidDeviceClassProvider(context)
    }

    @Test
    fun testRealDeviceDetection() {
        // Test that device detection works on real device
        val deviceClass = deviceClassProvider.getDeviceClass(context)
        assertTrue(
            "Device class should be valid",
            deviceClass in listOf(DeviceClass.TV_LOW_RAM, DeviceClass.TV, DeviceClass.PHONE_TABLET)
        )
    }

    @Test
    fun testBatchSizeBasedOnRealDevice() {
        // Test that batch sizes adapt to real device
        val batchSize = ObxWriteConfig.getBatchSize(deviceClassProvider, context)
        val liveBatch = ObxWriteConfig.getSyncLiveBatchSize(deviceClassProvider, context)
        val moviesBatch = ObxWriteConfig.getSyncMoviesBatchSize(deviceClassProvider, context)
        val seriesBatch = ObxWriteConfig.getSyncSeriesBatchSize(deviceClassProvider, context)

        // Verify phase-specific sizing is applied
        when (deviceClassProvider.getDeviceClass(context)) {
            DeviceClass.TV_LOW_RAM -> {
                // FireTV/Low-RAM: All capped at 35
                assertEquals(35, batchSize)
                assertEquals(35, liveBatch)
                assertEquals(35, moviesBatch)
                assertEquals(35, seriesBatch)
            }
            DeviceClass.TV, DeviceClass.PHONE_TABLET -> {
                // Phone/Tablet/TV: Phase-specific
                assertTrue("Live batch should be 600", liveBatch == 600)
                assertTrue("Movies batch should be 400", moviesBatch == 400)
                assertTrue("Series batch should be 200", seriesBatch == 200)
            }
        }
    }

    @Test
    fun testBackfillChunkSizeBasedOnRealDevice() {
        val chunkSize = ObxWriteConfig.getBackfillChunkSize(deviceClassProvider, context)

        when (deviceClassProvider.getDeviceClass(context)) {
            DeviceClass.TV_LOW_RAM -> {
                // FireTV: Conservative chunk size
                assertEquals(500, chunkSize)
            }
            DeviceClass.TV, DeviceClass.PHONE_TABLET -> {
                // Phone/Tablet: Optimized chunk size
                assertEquals(2000, chunkSize)
            }
        }
    }

    @Test
    fun testPageSizeForBackfill() {
        // Test page size calculations for backfill operations
        val livePageSize = ObxWriteConfig.getBackfillPageSize(deviceClassProvider, context, "live")
        val vodPageSize = ObxWriteConfig.getBackfillPageSize(deviceClassProvider, context, "vod")
        val seriesPageSize = ObxWriteConfig.getBackfillPageSize(deviceClassProvider, context, "series")

        when (deviceClassProvider.getDeviceClass(context)) {
            DeviceClass.TV_LOW_RAM -> {
                // FireTV: Smaller page sizes
                assertEquals(500, livePageSize)
                assertEquals(400, vodPageSize)
                assertEquals(300, seriesPageSize)
            }
            DeviceClass.TV, DeviceClass.PHONE_TABLET -> {
                // Phone/Tablet: Larger page sizes
                assertEquals(5000, livePageSize)
                assertEquals(4000, vodPageSize)
                assertEquals(4000, seriesPageSize)
            }
        }
    }

    @Test
    fun testExportBatchSize() {
        // Test export streaming batch size
        val exportBatch = ObxWriteConfig.getExportBatchSize(deviceClassProvider, context)

        when (deviceClassProvider.getDeviceClass(context)) {
            DeviceClass.TV_LOW_RAM -> {
                // FireTV: Capped for memory pressure
                assertEquals(500, exportBatch)
            }
            DeviceClass.TV, DeviceClass.PHONE_TABLET -> {
                // Phone/Tablet: Optimized for streaming
                assertEquals(5000, exportBatch)
            }
        }
    }

    @Test
    fun testTmdbEnrichmentBatchSize() {
        // Test TMDB enrichment batch sizing
        val tmdbBatch = ObxWriteConfig.getTmdbEnrichmentBatchSize(deviceClassProvider, context)

        when (deviceClassProvider.getDeviceClass(context)) {
            DeviceClass.TV_LOW_RAM -> {
                // FireTV: Small batches (15)
                assertEquals(15, tmdbBatch)
            }
            DeviceClass.TV, DeviceClass.PHONE_TABLET -> {
                // Phone/Tablet: Larger batches (75)
                assertEquals(75, tmdbBatch)
            }
        }
    }

    @Test
    fun testDeviceClassConsistency() {
        // Test that device class detection is consistent across multiple calls
        val deviceClass1 = deviceClassProvider.getDeviceClass(context)
        val deviceClass2 = deviceClassProvider.getDeviceClass(context)
        val deviceClass3 = deviceClassProvider.getDeviceClass(context)

        assertEquals("Device class should be consistent", deviceClass1, deviceClass2)
        assertEquals("Device class should be consistent", deviceClass2, deviceClass3)
    }

    @Test
    fun testPhaseSpecificBatchSizes() {
        // Test that different phases get appropriate batch sizes
        val deviceClass = deviceClassProvider.getDeviceClass(context)

        val liveBatch = ObxWriteConfig.getSyncLiveBatchSize(deviceClassProvider, context)
        val moviesBatch = ObxWriteConfig.getSyncMoviesBatchSize(deviceClassProvider, context)
        val seriesBatch = ObxWriteConfig.getSyncSeriesBatchSize(deviceClassProvider, context)
        val episodesBatch = ObxWriteConfig.getSyncEpisodesBatchSize(deviceClassProvider, context)

        if (deviceClass == DeviceClass.TV_LOW_RAM) {
            // All capped on FireTV
            assertEquals(35, liveBatch)
            assertEquals(35, moviesBatch)
            assertEquals(35, seriesBatch)
            assertEquals(35, episodesBatch)
        } else {
            // Phase-specific on normal devices
            assertTrue("Live should have highest batch", liveBatch >= moviesBatch)
            assertTrue("Movies should have higher batch than series", moviesBatch >= seriesBatch)
            assertEquals("Series and episodes same batch", seriesBatch, episodesBatch)
        }
    }

    @Test
    fun testBackfillPageSizeContentTypeVariations() {
        // Test different content types return appropriate page sizes
        val livePageSize = ObxWriteConfig.getBackfillPageSize(deviceClassProvider, context, "live")
        val vodPageSize = ObxWriteConfig.getBackfillPageSize(deviceClassProvider, context, "vod")
        val moviesPageSize = ObxWriteConfig.getBackfillPageSize(deviceClassProvider, context, "movies")
        val seriesPageSize = ObxWriteConfig.getBackfillPageSize(deviceClassProvider, context, "series")
        val unknownPageSize = ObxWriteConfig.getBackfillPageSize(deviceClassProvider, context, "unknown")

        // VOD and movies should be same
        assertEquals("VOD and movies should have same page size", vodPageSize, moviesPageSize)

        // Unknown should use default (same as VOD)
        assertEquals("Unknown should use VOD default", vodPageSize, unknownPageSize)

        val deviceClass = deviceClassProvider.getDeviceClass(context)
        if (deviceClass == DeviceClass.TV_LOW_RAM) {
            // Verify FireTV values
            assertTrue("Live page should be reasonable", livePageSize in 300..600)
            assertTrue("Series page should be smallest", seriesPageSize <= vodPageSize)
        } else {
            // Verify normal device values
            assertTrue("Live page should be largest", livePageSize >= vodPageSize)
            assertTrue("All normal pages should be substantial", vodPageSize >= 4000)
        }
    }
}
