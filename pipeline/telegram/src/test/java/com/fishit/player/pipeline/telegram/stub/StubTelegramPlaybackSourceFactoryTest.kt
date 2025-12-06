package com.fishit.player.pipeline.telegram.stub

import com.fishit.player.core.model.PlaybackType
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for StubTelegramPlaybackSourceFactory.
 *
 * Phase 2 Task 3 (P2-T3) - validates playback source creation and structure.
 */
class StubTelegramPlaybackSourceFactoryTest {
    private val factory = StubTelegramPlaybackSourceFactory()

    @Test
    fun `creates playback context with correct type`() {
        val mediaItem = createTestMediaItem()

        val context = factory.createPlaybackContext(mediaItem)

        assertEquals(PlaybackType.TELEGRAM, context.type)
    }

    @Test
    fun `creates playback context with telegram uri`() {
        val mediaItem = createTestMediaItem(
            fileId = 12345,
            chatId = 67890,
            messageId = 99999,
        )

        val context = factory.createPlaybackContext(mediaItem)

        assertEquals("tg://file/12345?chatId=67890&messageId=99999", context.uri)
    }

    @Test
    fun `creates playback context with title and subtitle`() {
        val mediaItem = createTestMediaItem(
            title = "Test Movie",
            fileName = "test.mp4",
        )

        val context = factory.createPlaybackContext(mediaItem)

        assertEquals("Test Movie", context.title)
        assertEquals("test.mp4", context.subtitle)
    }

    @Test
    fun `creates playback context with series subtitle`() {
        val mediaItem = createTestMediaItem(
            title = "Test Series",
            isSeries = true,
            seasonNumber = 1,
            episodeNumber = 5,
            episodeTitle = "The Big Episode",
        )

        val context = factory.createPlaybackContext(mediaItem)

        assertEquals("Test Series", context.title)
        assertEquals("S1E5: The Big Episode", context.subtitle)
    }

    @Test
    fun `creates playback context with series subtitle without episode title`() {
        val mediaItem = createTestMediaItem(
            title = "Test Series",
            isSeries = true,
            seasonNumber = 2,
            episodeNumber = 3,
        )

        val context = factory.createPlaybackContext(mediaItem)

        assertEquals("S2E3", context.subtitle)
    }

    @Test
    fun `creates playback context with content id`() {
        val mediaItem = createTestMediaItem(
            chatId = 12345,
            messageId = 67890,
        )

        val context = factory.createPlaybackContext(mediaItem)

        assertEquals("telegram:12345:67890", context.contentId)
    }

    @Test
    fun `creates playback context with series metadata`() {
        val mediaItem = createTestMediaItem(
            isSeries = true,
            seriesName = "My Series",
            seasonNumber = 1,
            episodeNumber = 2,
        )

        val context = factory.createPlaybackContext(mediaItem)

        assertEquals("My Series", context.seriesId)
        assertEquals(1, context.seasonNumber)
        assertEquals(2, context.episodeNumber)
    }

    @Test
    fun `creates playback context with profile and start position`() {
        val mediaItem = createTestMediaItem()

        val context = factory.createPlaybackContext(
            mediaItem = mediaItem,
            profileId = 42,
            startPositionMs = 120000,
        )

        assertEquals(42L, context.profileId)
        assertEquals(120000L, context.startPositionMs)
    }

    @Test
    fun `creates playback context with extras`() {
        val mediaItem = createTestMediaItem(
            fileId = 123,
            remoteId = "remote123",
            mimeType = "video/mp4",
            sizeBytes = 1024000,
            durationSecs = 300,
            supportsStreaming = true,
        )

        val context = factory.createPlaybackContext(mediaItem)

        assertEquals("123", context.extras["fileId"])
        assertEquals("remote123", context.extras["remoteId"])
        assertEquals("video/mp4", context.extras["mimeType"])
        assertEquals("1024000", context.extras["sizeBytes"])
        assertEquals("300", context.extras["durationSecs"])
        assertEquals("true", context.extras["supportsStreaming"])
    }

    @Test
    fun `canPlay returns true for playable media`() {
        val mediaItem = createTestMediaItem(
            fileId = 123,
            mimeType = "video/mp4",
        )

        val result = factory.canPlay(mediaItem)

        assertTrue(result)
    }

    @Test
    fun `canPlay returns false for media without file id`() {
        val mediaItem = createTestMediaItem(
            fileId = null,
            mimeType = "video/mp4",
        )

        val result = factory.canPlay(mediaItem)

        assertFalse(result)
    }

    @Test
    fun `canPlay returns false for media without mime type`() {
        val mediaItem = createTestMediaItem(
            fileId = 123,
            mimeType = null,
        )

        val result = factory.canPlay(mediaItem)

        assertFalse(result)
    }

    @Test
    fun `creates playback context with poster url`() {
        val mediaItem = createTestMediaItem(
            thumbnailPath = "/path/to/thumb.jpg",
        )

        val context = factory.createPlaybackContext(mediaItem)

        assertEquals("/path/to/thumb.jpg", context.posterUrl)
    }

    @Test
    fun `handles media item without optional fields`() {
        val mediaItem = TelegramMediaItem(
            id = 1,
            chatId = 123,
            messageId = 456,
            fileId = 789,
            title = "Minimal",
            mimeType = "video/mp4",
        )

        val context = factory.createPlaybackContext(mediaItem)

        assertNotNull(context)
        assertEquals(PlaybackType.TELEGRAM, context.type)
        assertEquals("Minimal", context.title)
        assertNull(context.seriesId)
    }

    private fun createTestMediaItem(
        id: Long = 1,
        chatId: Long = 12345,
        messageId: Long = 67890,
        fileId: Int? = 123,
        remoteId: String? = "remote_id",
        title: String = "Test Media",
        fileName: String? = null,
        mimeType: String? = "video/mp4",
        sizeBytes: Long? = null,
        durationSecs: Int? = null,
        supportsStreaming: Boolean? = null,
        thumbnailPath: String? = null,
        isSeries: Boolean = false,
        seriesName: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        episodeTitle: String? = null,
    ): TelegramMediaItem {
        return TelegramMediaItem(
            id = id,
            chatId = chatId,
            messageId = messageId,
            fileId = fileId,
            remoteId = remoteId,
            title = title,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            durationSecs = durationSecs,
            supportsStreaming = supportsStreaming,
            thumbnailPath = thumbnailPath,
            isSeries = isSeries,
            seriesName = seriesName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
        )
    }
}
