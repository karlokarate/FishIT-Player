package com.fishit.player.pipeline.xtream.playback.stub

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.core.model.PlaybackType
import com.fishit.player.pipeline.xtream.playback.XtreamPlaybackSourceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [StubXtreamPlaybackSourceFactory].
 *
 * These tests verify that the stub implementation returns basic playback sources
 * as expected for Phase 2.
 */
class StubXtreamPlaybackSourceFactoryTest {
    private lateinit var factory: XtreamPlaybackSourceFactory

    @Before
    fun setup() {
        factory = StubXtreamPlaybackSourceFactory()
    }

    @Test
    fun `createSource for VOD returns correct source`() {
        val context =
            PlaybackContext(
                type = PlaybackType.VOD,
                uri = "http://example.com/vod/123.mp4",
                title = "Test VOD",
            )

        val source = factory.createSource(context)

        assertEquals("URI should match context", context.uri, source.uri)
        assertEquals("Content type should be video/mp4", "video/mp4", source.contentType)
        assertTrue("Headers should be empty", source.headers.isEmpty())
    }

    @Test
    fun `createSource for SERIES returns correct source`() {
        val context =
            PlaybackContext(
                type = PlaybackType.SERIES,
                uri = "http://example.com/series/episode.mp4",
                title = "Test Episode",
            )

        val source = factory.createSource(context)

        assertEquals("URI should match context", context.uri, source.uri)
        assertEquals("Content type should be video/mp4", "video/mp4", source.contentType)
        assertTrue("Headers should be empty", source.headers.isEmpty())
    }

    @Test
    fun `createSource for LIVE returns correct source`() {
        val context =
            PlaybackContext(
                type = PlaybackType.LIVE,
                uri = "http://example.com/live/channel.m3u8",
                title = "Test Channel",
            )

        val source = factory.createSource(context)

        assertEquals("URI should match context", context.uri, source.uri)
        assertEquals(
            "Content type should be HLS",
            "application/x-mpegURL",
            source.contentType,
        )
        assertTrue("Headers should be empty", source.headers.isEmpty())
    }

    @Test
    fun `supportsContext returns true for VOD`() {
        val context =
            PlaybackContext(
                type = PlaybackType.VOD,
                uri = "test://vod",
                title = "Test",
            )
        assertTrue("Should support VOD", factory.supportsContext(context))
    }

    @Test
    fun `supportsContext returns true for SERIES`() {
        val context =
            PlaybackContext(
                type = PlaybackType.SERIES,
                uri = "test://series",
                title = "Test",
            )
        assertTrue("Should support SERIES", factory.supportsContext(context))
    }

    @Test
    fun `supportsContext returns true for LIVE`() {
        val context =
            PlaybackContext(
                type = PlaybackType.LIVE,
                uri = "test://live",
                title = "Test",
            )
        assertTrue("Should support LIVE", factory.supportsContext(context))
    }

    @Test
    fun `supportsContext returns false for TELEGRAM`() {
        val context =
            PlaybackContext(
                type = PlaybackType.TELEGRAM,
                uri = "test://telegram",
                title = "Test",
            )
        assertFalse("Should not support TELEGRAM", factory.supportsContext(context))
    }

    @Test
    fun `supportsContext returns false for AUDIOBOOK`() {
        val context =
            PlaybackContext(
                type = PlaybackType.AUDIOBOOK,
                uri = "test://audiobook",
                title = "Test",
            )
        assertFalse("Should not support AUDIOBOOK", factory.supportsContext(context))
    }

    @Test
    fun `supportsContext returns false for IO`() {
        val context =
            PlaybackContext(
                type = PlaybackType.IO,
                uri = "test://io",
                title = "Test",
            )
        assertFalse("Should not support IO", factory.supportsContext(context))
    }
}
