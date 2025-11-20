package com.chris.m3usuite.telegram.parser

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MediaParser.
 *
 * Tests focus on:
 * - Episode detection (SxxEyy format)
 * - Episode detection (alternative formats like "Episode 4")
 * - Language tag detection
 * - Content type classification
 * - Title extraction and parsing
 */
class MediaParserTest {

    @Test
    fun `parseEpisodeInfo detects S01E01 format`() {
        val result = MediaParser.parseEpisodeInfo("Breaking Bad S01E01 Pilot")
        assertNotNull(result)
        assertEquals(1, result?.season)
        assertEquals(1, result?.episode)
    }

    @Test
    fun `parseEpisodeInfo detects S1E1 format`() {
        val result = MediaParser.parseEpisodeInfo("Show S1E1")
        assertNotNull(result)
        assertEquals(1, result?.season)
        assertEquals(1, result?.episode)
    }

    @Test
    fun `parseEpisodeInfo detects 1x01 format`() {
        val result = MediaParser.parseEpisodeInfo("Series 1x01")
        assertNotNull(result)
        assertEquals(1, result?.season)
        assertEquals(1, result?.episode)
    }

    @Test
    fun `parseEpisodeInfo detects Episode 4 format`() {
        val result = MediaParser.parseEpisodeInfo("Episode 4")
        assertNotNull(result)
        assertEquals(null, result?.season) // No season specified
        assertEquals(4, result?.episode)
    }

    @Test
    fun `parseEpisodeInfo detects Ep 4 format`() {
        val result = MediaParser.parseEpisodeInfo("Ep 4 The Beginning")
        assertNotNull(result)
        assertEquals(null, result?.season)
        assertEquals(4, result?.episode)
    }

    @Test
    fun `parseEpisodeInfo detects Part 3 format`() {
        val result = MediaParser.parseEpisodeInfo("Documentary Part 3")
        assertNotNull(result)
        assertEquals(null, result?.season)
        assertEquals(3, result?.episode)
    }

    @Test
    fun `parseEpisodeInfo returns null for non-episode content`() {
        val result = MediaParser.parseEpisodeInfo("Random Movie Title")
        assertNull(result)
    }

    @Test
    fun `parseEpisodeInfo detects high season and episode numbers`() {
        val result = MediaParser.parseEpisodeInfo("Show S12E23")
        assertNotNull(result)
        assertEquals(12, result?.season)
        assertEquals(23, result?.episode)
    }

    @Test
    fun `detectLanguage detects German from filename`() {
        val result = MediaParser.detectLanguage("Movie.German.1080p.BluRay.mkv")
        assertEquals("de", result)
    }

    @Test
    fun `detectLanguage detects English from filename`() {
        val result = MediaParser.detectLanguage("Movie.English.720p.WEB.mp4")
        assertEquals("en", result)
    }

    @Test
    fun `detectLanguage detects French from filename`() {
        val result = MediaParser.detectLanguage("Film.French.2160p.mkv")
        assertEquals("fr", result)
    }

    @Test
    fun `detectLanguage detects Spanish from filename`() {
        val result = MediaParser.detectLanguage("Pelicula.Spanish.1080p.mkv")
        assertEquals("es", result)
    }

    @Test
    fun `detectLanguage detects Multi audio`() {
        val result = MediaParser.detectLanguage("Movie.MULTI.1080p.BluRay.mkv")
        assertEquals("multi", result)
    }

    @Test
    fun `detectLanguage returns null for unknown language`() {
        val result = MediaParser.detectLanguage("Movie.1080p.BluRay.mkv")
        assertNull(result)
    }

    @Test
    fun `extractQuality detects 2160p`() {
        val result = MediaParser.extractQuality("Movie.2160p.UHD.mkv")
        assertEquals("2160p", result)
    }

    @Test
    fun `extractQuality detects 1080p`() {
        val result = MediaParser.extractQuality("Movie.1080p.BluRay.mkv")
        assertEquals("1080p", result)
    }

    @Test
    fun `extractQuality detects 720p`() {
        val result = MediaParser.extractQuality("Movie.720p.WEB.mp4")
        assertEquals("720p", result)
    }

    @Test
    fun `extractQuality detects 480p`() {
        val result = MediaParser.extractQuality("Movie.480p.DVDRip.avi")
        assertEquals("480p", result)
    }

    @Test
    fun `extractQuality returns null when not specified`() {
        val result = MediaParser.extractQuality("Movie.BluRay.mkv")
        assertNull(result)
    }

    @Test
    fun `isRarArchive detects RAR extension`() {
        assertTrue(MediaParser.isRarArchive("archive.rar"))
        assertTrue(MediaParser.isRarArchive("archive.RAR"))
        assertTrue(MediaParser.isRarArchive("movie.part1.rar"))
    }

    @Test
    fun `isRarArchive rejects non-RAR files`() {
        assertFalse(MediaParser.isRarArchive("movie.mkv"))
        assertFalse(MediaParser.isRarArchive("archive.zip"))
        assertFalse(MediaParser.isRarArchive("document.pdf"))
    }

    @Test
    fun `cleanTitle removes common tags`() {
        val result = MediaParser.cleanTitle("Movie.2023.1080p.BluRay.x264-GROUP")
        // Should remove resolution, year, quality tags
        assertTrue(result.contains("Movie"))
        assertFalse(result.contains("1080p"))
        assertFalse(result.contains("BluRay"))
    }

    @Test
    fun `cleanTitle handles dots and underscores`() {
        val result = MediaParser.cleanTitle("Movie_Name.2023.mkv")
        assertTrue(result.contains("Movie"))
        assertTrue(result.contains("Name") || result.contains("name"))
    }

    @Test
    fun `parseYear extracts 4-digit year`() {
        assertEquals(2023, MediaParser.parseYear("Movie.2023.1080p.mkv"))
        assertEquals(1999, MediaParser.parseYear("Classic.1999.DVDRip.avi"))
        assertEquals(2001, MediaParser.parseYear("Film.2001.A.Space.Odyssey.mkv"))
    }

    @Test
    fun `parseYear returns null when year not found`() {
        assertNull(MediaParser.parseYear("Movie.Without.Year.mkv"))
        assertNull(MediaParser.parseYear("123.mkv"))
    }

    @Test
    fun `parseYear ignores invalid years`() {
        assertNull(MediaParser.parseYear("Movie.1899.mkv")) // Too old
        assertNull(MediaParser.parseYear("Movie.2100.mkv")) // Too far in future
    }
}
