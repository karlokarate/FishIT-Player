package com.fishit.player.pipeline.io

import com.fishit.player.core.model.PlaybackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for IoMediaItem extension functions.
 *
 * These tests verify the PlaybackContext conversion helper.
 */
class IoMediaItemExtensionsTest {
    @Test
    fun `toPlaybackContext creates valid context`() {
        val item =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/path/to/video.mp4"),
                title = "Test Video",
                fileName = "video.mp4",
                mimeType = "video/mp4",
                sizeBytes = 1024L,
                durationMs = 60000L,
            )

        val context = item.toPlaybackContext()

        assertEquals(PlaybackType.IO, context.type)
        assertEquals("Test Video", context.title)
        assertEquals("video.mp4", context.subtitle)
        assertTrue(context.uri.contains("video.mp4"))
        assertNotNull(context.contentId)
        assertTrue(context.contentId!!.startsWith("io:file:"))
    }

    @Test
    fun `toPlaybackContext includes optional parameters`() {
        val item = IoMediaItem.fake()

        val context =
            item.toPlaybackContext(
                profileId = 123L,
                startPositionMs = 5000L,
                isKidsContent = true,
            )

        assertEquals(123L, context.profileId)
        assertEquals(5000L, context.startPositionMs)
        assertTrue(context.isKidsContent)
    }

    @Test
    fun `toPlaybackContext includes metadata in extras`() {
        val item =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/path/video.mp4"),
                title = "Test",
                fileName = "video.mp4",
                mimeType = "video/mp4",
                sizeBytes = 2048L,
                durationMs = 120000L,
                metadata =
                    mapOf(
                        "codec" to "h264",
                        "resolution" to "1920x1080",
                    ),
            )

        val context = item.toPlaybackContext()

        assertEquals("video/mp4", context.extras["mimeType"])
        assertEquals("2048", context.extras["sizeBytes"])
        assertEquals("120000", context.extras["durationMs"])
        assertEquals("h264", context.extras["codec"])
        assertEquals("1920x1080", context.extras["resolution"])
    }

    @Test
    fun `toPlaybackContext handles thumbnail path`() {
        val item =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/path/video.mp4"),
                title = "Test",
                fileName = "video.mp4",
                thumbnailPath = "/path/thumbnail.jpg",
            )

        val context = item.toPlaybackContext()

        assertEquals("/path/thumbnail.jpg", context.posterUrl)
    }
}
