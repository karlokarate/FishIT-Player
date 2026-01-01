package com.fishit.player.playback.xtream

import com.fishit.player.playback.domain.PlaybackSourceException
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for HLS capability detection and automatic TS fallback.
 *
 * Validates:
 * 1. HLS-aware format selection (with/without HLS module)
 * 2. Automatic fallback to TS when HLS unavailable
 * 3. Fail-fast behavior for HLS-only scenarios
 */
class HlsCapabilityFallbackTest {
    // =========================================================================
    // HLS Capability-Aware Format Selection
    // =========================================================================

    @Test
    fun `format selection with HLS available selects m3u8 when present`() {
        // Given formats with m3u8, ts, and HLS module present
        val formats = setOf("m3u8", "ts", "mp4")

        // When selecting with HLS available
        val selected =
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                allowedFormats = formats,
                hlsAvailable = true,
            )

        // Then m3u8 is selected (highest priority)
        assertEquals("m3u8", selected)
    }

    @Test
    fun `format selection without HLS falls back to ts when m3u8 and ts available`() {
        // Given formats with both m3u8 and ts, but HLS module NOT present
        val formats = setOf("m3u8", "ts")

        // When selecting without HLS available
        val selected =
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                allowedFormats = formats,
                hlsAvailable = false,
            )

        // Then ts is selected (automatic fallback from m3u8)
        assertEquals("ts", selected)
    }

    @Test
    fun `format selection without HLS falls back to ts with m3u8 ts and mp4 available`() {
        // Given all formats including m3u8, ts, mp4, but HLS module NOT present
        val formats = setOf("m3u8", "ts", "mp4")

        // When selecting without HLS available
        val selected =
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                allowedFormats = formats,
                hlsAvailable = false,
            )

        // Then ts is selected (fallback from m3u8, preferred over mp4)
        assertEquals("ts", selected)
    }

    @Test
    fun `format selection without HLS fails when only m3u8 allowed`() {
        // Given only m3u8 format allowed, but HLS module NOT present
        val formats = setOf("m3u8")

        // When selecting without HLS available
        val exception =
            assertThrows(PlaybackSourceException::class.java) {
                XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                    allowedFormats = formats,
                    hlsAvailable = false,
                )
            }

        // Then fail fast with actionable error message
        assertTrue(exception.message!!.contains("HLS (m3u8) required"))
        assertTrue(exception.message!!.contains("media3-exoplayer-hls module is not available"))
    }

    @Test
    fun `format selection without HLS and without m3u8 selects ts normally`() {
        // Given ts and mp4 formats (no m3u8), HLS module NOT present
        val formats = setOf("ts", "mp4")

        // When selecting without HLS available
        val selected =
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                allowedFormats = formats,
                hlsAvailable = false,
            )

        // Then ts is selected normally (no fallback needed, m3u8 not present)
        assertEquals("ts", selected)
    }

    @Test
    fun `format selection with HLS selects ts when only ts and mp4 available`() {
        // Given only ts and mp4 (no m3u8), HLS module present
        val formats = setOf("ts", "mp4")

        // When selecting with HLS available
        val selected =
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                allowedFormats = formats,
                hlsAvailable = true,
            )

        // Then ts is selected (normal priority, no m3u8 present)
        assertEquals("ts", selected)
    }

    @Test
    fun `format selection with HLS selects mp4 when only mp4 available`() {
        // Given only mp4 format, HLS module present
        val formats = setOf("mp4")

        // When selecting with HLS available
        val selected =
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                allowedFormats = formats,
                hlsAvailable = true,
            )

        // Then mp4 is selected (only option)
        assertEquals("mp4", selected)
    }

    @Test
    fun `format selection without HLS selects mp4 when only mp4 available`() {
        // Given only mp4 format, HLS module NOT present
        val formats = setOf("mp4")

        // When selecting without HLS available
        val selected =
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                allowedFormats = formats,
                hlsAvailable = false,
            )

        // Then mp4 is selected (only option, no m3u8 to trigger fallback)
        assertEquals("mp4", selected)
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    fun `format selection with empty formats fails regardless of HLS`() {
        // Given empty formats
        val formats = emptySet<String>()

        // When selecting (both with and without HLS)
        val exceptionWithHls =
            assertThrows(PlaybackSourceException::class.java) {
                XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                    allowedFormats = formats,
                    hlsAvailable = true,
                )
            }
        val exceptionWithoutHls =
            assertThrows(PlaybackSourceException::class.java) {
                XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                    allowedFormats = formats,
                    hlsAvailable = false,
                )
            }

        // Then both fail with empty formats error
        assertTrue(exceptionWithHls.message!!.contains("allowed_output_formats is empty"))
        assertTrue(exceptionWithoutHls.message!!.contains("allowed_output_formats is empty"))
    }

    @Test
    fun `format selection handles case-insensitive formats`() {
        // Given formats with mixed case
        val formats = setOf("M3U8", "TS", "MP4")

        // When selecting with HLS available
        val selected =
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                allowedFormats = formats,
                hlsAvailable = true,
            )

        // Then m3u8 is selected (case-insensitive)
        assertEquals("m3u8", selected)
    }

    @Test
    fun `format selection without HLS handles case-insensitive fallback`() {
        // Given formats with mixed case
        val formats = setOf("M3U8", "TS")

        // When selecting without HLS available
        val selected =
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                allowedFormats = formats,
                hlsAvailable = false,
            )

        // Then ts is selected (case-insensitive fallback)
        assertEquals("ts", selected)
    }

    // =========================================================================
    // Real-World Scenarios
    // =========================================================================

    @Test
    fun `Cloudflare panel with m3u8 and ts works with HLS`() {
        // Given typical Cloudflare panel formats
        val formats = setOf("m3u8", "ts")

        // When selecting with HLS available
        val selected =
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                allowedFormats = formats,
                hlsAvailable = true,
            )

        // Then m3u8 is selected for best quality
        assertEquals("m3u8", selected)
    }

    @Test
    fun `Cloudflare panel with m3u8 and ts falls back to ts without HLS`() {
        // Given typical Cloudflare panel formats
        val formats = setOf("m3u8", "ts")

        // When selecting without HLS available
        val selected =
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                allowedFormats = formats,
                hlsAvailable = false,
            )

        // Then ts is selected (automatic fallback ensures playback works)
        assertEquals("ts", selected)
    }

    @Test
    fun `Provider with only m3u8 requires HLS module`() {
        // Given provider that only allows m3u8
        val formats = setOf("m3u8")

        // When HLS module is present
        val selectedWithHls =
            XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                allowedFormats = formats,
                hlsAvailable = true,
            )

        // Then m3u8 works fine
        assertEquals("m3u8", selectedWithHls)

        // When HLS module is NOT present
        val exception =
            assertThrows(PlaybackSourceException::class.java) {
                XtreamPlaybackSourceFactoryImpl.selectXtreamOutputExt(
                    allowedFormats = formats,
                    hlsAvailable = false,
                )
            }

        // Then explicit error about missing HLS module
        assertTrue(exception.message!!.contains("HLS (m3u8) required"))
        assertTrue(exception.message!!.contains("module is not available"))
    }
}
