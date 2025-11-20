package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.models.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TgContentHeuristics (season/episode detection, language, quality).
 *
 * Note: These tests verify the heuristics layer that works with parsed data.
 * MediaParser.parseMessage() requires mocking TDLib Message objects which is complex,
 * so we test the heuristics functions that MediaParser uses internally.
 *
 * Tests focus on:
 * - Episode detection (SxxEyy format) via guessSeasonEpisode
 * - Episode detection (alternative formats like "Episode 4")
 * - Language tag detection via detectLanguages
 * - Quality extraction via detectQuality
 * - Content classification via classify
 */
class MediaParserTest {

    @Test
    fun `guessSeasonEpisode detects S01E01 format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Breaking Bad S01E01 Pilot")
        assertNotNull(result)
        assertEquals(1, result?.season)
        assertEquals(1, result?.episode)
        assertEquals("SxxEyy", result?.pattern)
    }

    @Test
    fun `guessSeasonEpisode detects S1E1 format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Show S1E1")
        assertNotNull(result)
        assertEquals(1, result?.season)
        assertEquals(1, result?.episode)
    }

    @Test
    fun `guessSeasonEpisode detects 1x01 format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Series 1x01")
        assertNotNull(result)
        assertEquals(1, result?.season)
        assertEquals(1, result?.episode)
        assertEquals("XxY", result?.pattern)
    }

    @Test
    fun `guessSeasonEpisode detects Episode 4 format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Episode 4")
        assertNotNull(result)
        assertNull(result?.season) // No season specified
        assertEquals(4, result?.episode)
        assertEquals("Episode X", result?.pattern)
    }

    @Test
    fun `guessSeasonEpisode detects Ep 4 format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Ep 4 The Beginning")
        assertNotNull(result)
        assertNull(result?.season)
        assertEquals(4, result?.episode)
        assertEquals("Ep X", result?.pattern)
    }

    @Test
    fun `guessSeasonEpisode detects Folge 4 format German`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Folge 4 Der Anfang")
        assertNotNull(result)
        assertNull(result?.season)
        assertEquals(4, result?.episode)
        assertEquals("Folge X", result?.pattern)
    }

    @Test
    fun `guessSeasonEpisode returns null for non-episode content`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Random Movie Title")
        assertNull(result)
    }

    @Test
    fun `guessSeasonEpisode detects high season and episode numbers`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Show S12E23")
        assertNotNull(result)
        assertEquals(12, result?.season)
        assertEquals(23, result?.episode)
    }

    @Test
    fun `detectLanguages finds German tags`() {
        val result = TgContentHeuristics.detectLanguages("Movie.GERMAN.1080p.mkv")
        assertTrue("Expected GERMAN tag", result.any { it.contains("GERMAN", ignoreCase = true) })
    }

    @Test
    fun `detectLanguages finds English tags`() {
        val result = TgContentHeuristics.detectLanguages("Show.ENGLISH.720p")
        assertTrue("Expected ENGLISH tag", result.any { it.contains("ENGLISH", ignoreCase = true) || it.contains("ENG", ignoreCase = true) })
    }

    @Test
    fun `detectLanguages finds MULTI tags`() {
        val result = TgContentHeuristics.detectLanguages("Film.MULTI.BDRip")
        assertTrue("Expected MULTI tag", result.any { it.contains("MULTI", ignoreCase = true) })
    }

    @Test
    fun `detectLanguages finds multiple tags`() {
        val result = TgContentHeuristics.detectLanguages("Movie.GERMAN.ENGLISH.1080p")
        assertTrue("Expected at least 2 language tags", result.size >= 2)
    }

    @Test
    fun `detectLanguages returns empty for no tags`() {
        val result = TgContentHeuristics.detectLanguages("Simple Movie Title")
        assertTrue("Expected empty result", result.isEmpty())
    }

    @Test
    fun `detectLanguages is case insensitive`() {
        val result = TgContentHeuristics.detectLanguages("movie.german.1080p")
        assertTrue("Expected to find german tag despite lowercase", result.isNotEmpty())
    }

    @Test
    fun `detectQuality detects 4K`() {
        val result = TgContentHeuristics.detectQuality("Movie.4K.HDR.mkv")
        assertTrue("Expected 4K or UHD", result?.contains("4K", ignoreCase = true) == true || result?.contains("UHD", ignoreCase = true) == true)
    }

    @Test
    fun `detectQuality detects 1080p`() {
        val result = TgContentHeuristics.detectQuality("Show.1080p.BluRay")
        assertTrue("Expected 1080p or FHD", result?.contains("1080", ignoreCase = true) == true)
    }

    @Test
    fun `detectQuality detects 720p`() {
        val result = TgContentHeuristics.detectQuality("Film.720p.WEB-DL")
        assertTrue("Expected 720p", result?.contains("720", ignoreCase = true) == true)
    }

    @Test
    fun `detectQuality detects 480p`() {
        val result = TgContentHeuristics.detectQuality("Video.480p.DVDRip")
        assertTrue("Expected 480p", result?.contains("480", ignoreCase = true) == true)
    }

    @Test
    fun `detectQuality returns null for no quality info`() {
        val result = TgContentHeuristics.detectQuality("Simple Movie")
        assertNull("Expected null for no quality info", result)
    }

    @Test
    fun `hasSeriesIndicators detects series in chat title`() {
        assertTrue(TgContentHeuristics.hasSeriesIndicators("TV Series Collection"))
        assertTrue(TgContentHeuristics.hasSeriesIndicators("Best Shows"))
        assertTrue(TgContentHeuristics.hasSeriesIndicators("Serien HD"))
    }

    @Test
    fun `hasMovieIndicators detects movies in chat title`() {
        assertTrue(TgContentHeuristics.hasMovieIndicators("Movies HD"))
        assertTrue(TgContentHeuristics.hasMovieIndicators("Film Collection"))
        assertTrue(TgContentHeuristics.hasMovieIndicators("Cinema Releases"))
    }

    @Test
    fun `classify detects series from episode pattern`() {
        val parsed = MediaInfo(
            chatId = 123,
            messageId = 456,
            kind = MediaKind.MOVIE,
            fileName = "Breaking.Bad.S01E01.mkv",
            title = "Breaking Bad",
            seasonNumber = 1,
            episodeNumber = 1
        )
        val result = TgContentHeuristics.classify(parsed, "TV Series")
        assertEquals("Expected EPISODE classification", MediaKind.EPISODE, result.suggestedKind)
        assertTrue("Expected high confidence > 0.7", result.confidence > 0.7)
    }

    @Test
    fun `classify respects movie classification from year and context`() {
        val parsed = MediaInfo(
            chatId = 123,
            messageId = 456,
            kind = MediaKind.MOVIE,
            fileName = "Inception.2010.1080p.mkv",
            title = "Inception",
            year = 2010
        )
        val result = TgContentHeuristics.classify(parsed, "Movies HD")
        // Should stay as MOVIE or have reasonable confidence
        assertTrue("Result should be meaningful", result.confidence > 0.3)
    }
}
