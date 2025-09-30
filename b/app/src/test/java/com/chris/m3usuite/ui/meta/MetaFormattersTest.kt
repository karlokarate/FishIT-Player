package com.chris.m3usuite.ui.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetaFormattersTest {

    @Test
    fun quality_formats_from_height_and_hdr() {
        assertEquals("4K", MetaFormatters.quality(VideoInfo(height = 2160)))
        assertEquals("1080p HDR", MetaFormatters.quality(VideoInfo(height = 1080, hdr = true)))
        assertEquals("HD", MetaFormatters.quality(VideoInfo(height = 720)))
        assertEquals("1440p", MetaFormatters.quality(VideoInfo(height = 1440)))
        assertNull(MetaFormatters.quality(null))
    }

    @Test
    fun duration_formats_minutes() {
        assertEquals("90 min", MetaFormatters.duration(90))
        assertNull(MetaFormatters.duration(null))
        assertNull(MetaFormatters.duration(0))
    }

    @Test
    fun audio_formats_languages_and_channels() {
        assertEquals("DE/EN 5.1", MetaFormatters.audio(AudioInfo(channels = "5.1", languages = listOf("de", "en"))))
        assertEquals("EN", MetaFormatters.audio(AudioInfo(channels = null, languages = listOf("english"))))
        assertEquals("2.0", MetaFormatters.audio(AudioInfo(channels = "2.0", languages = emptyList())))
        assertNull(MetaFormatters.audio(null))
    }

    @Test
    fun year_formats_as_string() {
        assertEquals("2024", MetaFormatters.year(2024))
        assertNull(MetaFormatters.year(null))
        assertNull(MetaFormatters.year(0))
    }

    @Test
    fun parse_genres_splits_and_dedups() {
        val g = parseGenres("Action, Drama | Action / Sci‑Fi")
        assertEquals(listOf("Action", "Drama", "Sci‑Fi"), g)
    }
}
