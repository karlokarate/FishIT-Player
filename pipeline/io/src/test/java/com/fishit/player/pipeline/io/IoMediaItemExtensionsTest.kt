package com.fishit.player.pipeline.io

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.fishit.player.core.playermodel.SourceType as PlayerSourceType

/**
 * Tests for IoMediaItem extension functions.
 *
 * These tests verify the PlaybackContext and RawMediaMetadata conversion helpers.
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

        assertEquals(PlayerSourceType.FILE, context.sourceType)
        assertEquals("Test Video", context.title)
        assertEquals("video.mp4", context.subtitle)
        assertNotNull(context.uri)
        assertTrue(context.uri!!.contains("video.mp4"))
        assertTrue(context.canonicalId.startsWith("io:"))
    }

    @Test
    fun `toPlaybackContext includes start position`() {
        val item = IoMediaItem.fake()

        val context = item.toPlaybackContext(startPositionMs = 5000L)

        assertEquals(5000L, context.startPositionMs)
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

    // Tests for toRawMediaMetadata() - Phase 3 contract compliance

    @Test
    fun `toRawMediaMetadata forwards raw filename without cleaning`() {
        // Test that scene-style filenames are NOT cleaned by IO pipeline
        val item =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/movies/X-Men.2000.1080p.BluRay.x264-GROUP.mkv"),
                title = "X-Men",
                fileName = "X-Men.2000.1080p.BluRay.x264-GROUP.mkv",
            )

        val raw = item.toRawMediaMetadata()

        // Raw filename must be forwarded as-is, NO cleaning
        assertEquals("X-Men.2000.1080p.BluRay.x264-GROUP.mkv", raw.originalTitle)
        // IO does NOT extract year/season/episode
        assertNull(raw.year)
        assertNull(raw.season)
        assertNull(raw.episode)
    }

    @Test
    fun `toRawMediaMetadata forwards duration in milliseconds`() {
        val item =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/path/video.mp4"),
                title = "Test Video",
                fileName = "video.mp4",
                durationMs = 7_260_000L, // 121 minutes in ms
            )

        val raw = item.toRawMediaMetadata()

        // Duration should be forwarded directly in milliseconds
        assertEquals(7_260_000L, raw.durationMs)
    }

    @Test
    fun `toRawMediaMetadata handles missing duration`() {
        val item =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/path/video.mp4"),
                title = "Test Video",
                fileName = "video.mp4",
                durationMs = null,
            )

        val raw = item.toRawMediaMetadata()

        assertNull(raw.durationMs)
    }

    @Test
    fun `toRawMediaMetadata includes source identification`() {
        val item =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/movies/video.mp4"),
                title = "Test Video",
                fileName = "video.mp4",
            )

        val raw = item.toRawMediaMetadata()

        assertEquals(SourceType.IO, raw.sourceType)
        assertEquals("Local File: video.mp4", raw.sourceLabel)
        assertEquals("io:file:file:///movies/video.mp4", raw.sourceId)
        assertEquals(PipelineIdTag.IO, raw.pipelineIdTag)
    }

    @Test
    fun `toRawMediaMetadata infers media type from mime`() {
        val videoItem =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/path/video.mp4"),
                title = "Test Video",
                fileName = "video.mp4",
                mimeType = "video/mp4",
            )

        val raw = videoItem.toRawMediaMetadata()

        assertEquals(MediaType.CLIP, raw.mediaType)
    }

    @Test
    fun `toRawMediaMetadata preserves special characters in filename`() {
        // Test that special characters are preserved without cleaning
        val item =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/path/[Movie] Title (2023) {Edition}.mkv"),
                title = "Title",
                fileName = "[Movie] Title (2023) {Edition}.mkv",
            )

        val raw = item.toRawMediaMetadata()

        // Special characters must be preserved
        assertEquals("[Movie] Title (2023) {Edition}.mkv", raw.originalTitle)
    }
}
