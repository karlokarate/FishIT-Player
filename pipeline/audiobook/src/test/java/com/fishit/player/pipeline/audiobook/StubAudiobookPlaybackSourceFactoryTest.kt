package com.fishit.player.pipeline.audiobook

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for StubAudiobookPlaybackSourceFactory.
 *
 * Verifies that stub implementation returns null for all playback requests
 * as specified in Phase 2 requirements.
 */
class StubAudiobookPlaybackSourceFactoryTest {
    private lateinit var factory: AudiobookPlaybackSourceFactory

    @Before
    fun setup() {
        factory = StubAudiobookPlaybackSourceFactory()
    }

    @Test
    fun `createPlaybackContext returns null`() =
        runTest {
            val audiobook = createTestAudiobook()

            val result = factory.createPlaybackContext(audiobook)

            assertNull(result)
        }

    @Test
    fun `createPlaybackContext with start chapter returns null`() =
        runTest {
            val audiobook = createTestAudiobook()

            val result = factory.createPlaybackContext(audiobook, startChapterNumber = 3)

            assertNull(result)
        }

    @Test
    fun `createPlaybackContextForChapter returns null`() =
        runTest {
            val audiobook = createTestAudiobook()
            val chapter = createTestChapter(audiobook.id)

            val result = factory.createPlaybackContextForChapter(audiobook, chapter)

            assertNull(result)
        }

    private fun createTestAudiobook(id: String = "test-audiobook-1"): AudiobookItem =
        AudiobookItem(
            id = id,
            title = "Test Audiobook",
            author = "Test Author",
            narrator = "Test Narrator",
            totalDurationMs = 3600000L,
        )

    private fun createTestChapter(
        audiobookId: String,
        chapterNumber: Int = 1,
    ): AudiobookChapter =
        AudiobookChapter(
            id = "chapter-$chapterNumber",
            audiobookId = audiobookId,
            title = "Chapter $chapterNumber",
            chapterNumber = chapterNumber,
            startPositionMs = 0L,
            endPositionMs = 600000L,
        )
}
