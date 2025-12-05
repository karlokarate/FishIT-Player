package com.fishit.player.core.persistence.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fishit.player.core.model.PlaybackType
import com.fishit.player.core.persistence.obx.ObxStore
import io.objectbox.BoxStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [ObxResumeRepository] verifying resume behavior contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ObxResumeRepositoryTest {
    private lateinit var boxStore: BoxStore
    private lateinit var repository: ObxResumeRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        boxStore = ObxStore.get(context)
        repository = ObxResumeRepository(boxStore)
    }

    @After
    fun tearDown() {
        boxStore.close()
        ObxStore.close()
    }

    @Test
    fun `test save resume position greater than 10 seconds`() =
        runTest {
            val contentId = "vod:123"
            val position = 15

            repository.saveResumePosition(contentId, PlaybackType.VOD, position, durationSecs = 3600)

            val saved = repository.getResumePosition(contentId, PlaybackType.VOD)
            assertEquals(position, saved)
        }

    @Test
    fun `test do not save resume position less than or equal to 10 seconds`() =
        runTest {
            val contentId = "vod:124"
            val position = 10

            repository.saveResumePosition(contentId, PlaybackType.VOD, position, durationSecs = 3600)

            val saved = repository.getResumePosition(contentId, PlaybackType.VOD)
            assertNull(saved, "Position <= 10 seconds should not be saved")
        }

    @Test
    fun `test clear resume when remaining time less than 10 seconds`() =
        runTest {
            val contentId = "vod:125"
            val duration = 100

            // First save a valid position
            repository.saveResumePosition(contentId, PlaybackType.VOD, 50, durationSecs = duration)
            assertEquals(50, repository.getResumePosition(contentId, PlaybackType.VOD))

            // Now save a position near the end (remaining < 10s)
            repository.saveResumePosition(contentId, PlaybackType.VOD, 92, durationSecs = duration)

            // Should be cleared
            val saved = repository.getResumePosition(contentId, PlaybackType.VOD)
            assertNull(saved, "Position should be cleared when remaining < 10 seconds")
        }

    @Test
    fun `test never save resume for LIVE content`() =
        runTest {
            val contentId = "live:456"

            repository.saveResumePosition(contentId, PlaybackType.LIVE, 100, durationSecs = null)

            val saved = repository.getResumePosition(contentId, PlaybackType.LIVE)
            assertNull(saved, "LIVE content should never have resume position")
        }

    @Test
    fun `test update existing resume position`() =
        runTest {
            val contentId = "vod:126"

            repository.saveResumePosition(contentId, PlaybackType.VOD, 20, durationSecs = 3600)
            assertEquals(20, repository.getResumePosition(contentId, PlaybackType.VOD))

            repository.saveResumePosition(contentId, PlaybackType.VOD, 40, durationSecs = 3600)
            assertEquals(40, repository.getResumePosition(contentId, PlaybackType.VOD))
        }

    @Test
    fun `test clear resume position`() =
        runTest {
            val contentId = "vod:127"

            repository.saveResumePosition(contentId, PlaybackType.VOD, 50, durationSecs = 3600)
            assertEquals(50, repository.getResumePosition(contentId, PlaybackType.VOD))

            repository.clearResumePosition(contentId, PlaybackType.VOD)
            assertNull(repository.getResumePosition(contentId, PlaybackType.VOD))
        }

    @Test
    fun `test get all resume positions`() =
        runTest {
            repository.saveResumePosition("vod:201", PlaybackType.VOD, 30, durationSecs = 3600)
            Thread.sleep(10) // Ensure different timestamps
            repository.saveResumePosition("vod:202", PlaybackType.VOD, 40, durationSecs = 3600)
            Thread.sleep(10)
            repository.saveResumePosition("series:301:1:1", PlaybackType.SERIES, 50, durationSecs = 3600)

            val allResumes = repository.getAllResumePositions(limit = 10)

            assertEquals(3, allResumes.size)
            // Should be ordered by most recent
            assertEquals("series:301:1:1", allResumes[0].contentId)
        }
}
