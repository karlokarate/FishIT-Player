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
        assertEquals("X-Men.2000.1080p.BluRay.x264-GROUP.mkv", raw["originalTitle"])
        // IO does NOT extract year/season/episode
        assertEquals(null, raw["year"])
        assertEquals(null, raw["season"])
        assertEquals(null, raw["episode"])
    }

    @Test
    fun `toRawMediaMetadata forwards duration in minutes`() {
        val item =
            IoMediaItem(
                id = "test-id",
                source = IoSource.LocalFile("/path/video.mp4"),
                title = "Test Video",
                fileName = "video.mp4",
                durationMs = 7_260_000L, // 121 minutes
            )

        val raw = item.toRawMediaMetadata()

        // Duration should be converted from ms to minutes
        assertEquals(121, raw["durationMinutes"])
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

        assertEquals(null, raw["durationMinutes"])
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

        assertEquals("IO", raw["sourceType"])
        assertEquals("Local File: video.mp4", raw["sourceLabel"])
        assertEquals("io:file:file:///movies/video.mp4", raw["sourceId"])
    }

    @Test
    fun `toRawMediaMetadata does not include external IDs`() {
        // IO pipeline cannot provide TMDB/IMDB IDs from raw filesystem
        val item = IoMediaItem.fake()

        val raw = item.toRawMediaMetadata()

        @Suppress("UNCHECKED_CAST")
        val externalIds = raw["externalIds"] as Map<String, String>
        assertTrue("IO should not provide external IDs", externalIds.isEmpty())
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
        assertEquals("[Movie] Title (2023) {Edition}.mkv", raw["originalTitle"])
    }
}
