package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.models.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TgContentHeuristics.
 *
 * Tests focus on:
 * - Content classification (Movie vs Series vs Episode)
 * - Confidence scoring via classify()
 * - Season/Episode detection via guessSeasonEpisode()
 * - Language detection via detectLanguages()
 * - Quality detection via detectQuality()
 * - Chat title analysis via hasSeriesIndicators/hasMovieIndicators
 */
class TgContentHeuristicsTest {

    @Test
    fun `classify detects EPISODE from season and episode in parsed data`() {
        val parsed = MediaInfo(
            chatId = 123L,
            messageId = 456L,
            kind = MediaKind.MOVIE, // Will be overridden by heuristics
            fileName = "Breaking.Bad.S01E01.mkv",
            title = "Breaking Bad",
            seasonNumber = 1,
            episodeNumber = 1
        )
        val result = TgContentHeuristics.classify(parsed, "TV Series")
        assertEquals(MediaKind.EPISODE, result.suggestedKind)
        assertTrue("Confidence should be high (> 0.7)", result.confidence > 0.7)
        assertNotNull(result.seasonNumber)
        assertNotNull(result.episodeNumber)
    }

    @Test
    fun `classify detects SERIES from episode metadata`() {
        val parsed = MediaInfo(
            chatId = 123L,
            messageId = 456L,
            kind = MediaKind.SERIES,
            fileName = "Show.Complete.mkv",
            title = "Complete Show",
            totalEpisodes = 24,
            totalSeasons = 2
        )
        val result = TgContentHeuristics.classify(parsed, "Series Collection")
        assertEquals(MediaKind.SERIES, result.suggestedKind)
        assertTrue("Confidence should be reasonable (> 0.5)", result.confidence > 0.5)
    }

    @Test
    fun `classify respects MOVIE with year in filename`() {
        val parsed = MediaInfo(
            chatId = 123L,
            messageId = 456L,
            kind = MediaKind.MOVIE,
            fileName = "Inception.2010.1080p.mkv",
            title = "Inception",
            year = 2010
        )
        val result = TgContentHeuristics.classify(parsed, "Movies HD")
        // Should remain MOVIE or have movie-friendly classification
        assertTrue("Should have reasonable confidence", result.confidence > 0.3)
    }

    @Test
    fun `classify uses chat title series indicators`() {
        val parsed = MediaInfo(
            chatId = 123L,
            messageId = 456L,
            kind = MediaKind.MOVIE,
            fileName = "episode_01.mkv",
            title = "Episode 1"
        )
        val result = TgContentHeuristics.classify(parsed, "TV Series Collection")
        // Chat title should influence classification
        assertTrue("Series indicator in chat should affect classification", 
            result.suggestedKind == MediaKind.SERIES || result.suggestedKind == MediaKind.EPISODE)
    }

    @Test
    fun `classify uses chat title movie indicators`() {
        val parsed = MediaInfo(
            chatId = 123L,
            messageId = 456L,
            kind = MediaKind.MOVIE,
            fileName = "feature.mkv",
            title = "Feature Film"
        )
        val result = TgContentHeuristics.classify(parsed, "Movies HD")
        // Chat title should support movie classification
        assertTrue("Confidence should be reasonable", result.confidence > 0.3)
    }

    @Test
    fun `guessSeasonEpisode parses S01E01 format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Show.S01E01.mkv")
        assertNotNull(result)
        assertEquals(1, result?.season)
        assertEquals(1, result?.episode)
        assertEquals("SxxEyy", result?.pattern)
    }

    @Test
    fun `guessSeasonEpisode parses 1x02 format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Series.1x02.mkv")
        assertNotNull(result)
        assertEquals(1, result?.season)
        assertEquals(2, result?.episode)
        assertEquals("XxY", result?.pattern)
    }

    @Test
    fun `guessSeasonEpisode parses Episode 4 format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Episode 4 Title")
        assertNotNull(result)
        assertNull(result?.season)
        assertEquals(4, result?.episode)
        assertEquals("Episode X", result?.pattern)
    }

    @Test
    fun `guessSeasonEpisode parses Ep 5 format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Ep 5 The Beginning")
        assertNotNull(result)
        assertNull(result?.season)
        assertEquals(5, result?.episode)
        assertEquals("Ep X", result?.pattern)
    }

    @Test
    fun `guessSeasonEpisode parses German Folge format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Serie Folge 3")
        assertNotNull(result)
        assertNull(result?.season)
        assertEquals(3, result?.episode)
        assertEquals("Folge X", result?.pattern)
    }

    @Test
    fun `guessSeasonEpisode parses German Staffel format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Staffel 2 Komplett")
        assertNotNull(result)
        assertEquals(2, result?.season)
        assertNull(result?.episode)
        assertEquals("Staffel X", result?.pattern)
    }

    @Test
    fun `guessSeasonEpisode returns null for non-episode`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Random.Movie.2023.mkv")
        assertNull(result)
    }

    @Test
    fun `guessSeasonEpisode handles high episode numbers`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Show.S05E123.mkv")
        assertNotNull(result)
        assertEquals(5, result?.season)
        assertEquals(123, result?.episode)
    }

    @Test
    fun `detectLanguages finds German tag`() {
        val result = TgContentHeuristics.detectLanguages("Movie.GERMAN.1080p.mkv")
        assertTrue("Should find GERMAN", result.any { it.contains("GERMAN", ignoreCase = true) })
    }

    @Test
    fun `detectLanguages finds English tag`() {
        val result = TgContentHeuristics.detectLanguages("Show.ENGLISH.720p")
        assertTrue("Should find ENGLISH or ENG", 
            result.any { it.contains("ENGLISH", ignoreCase = true) || it.contains("ENG", ignoreCase = true) })
    }

    @Test
    fun `detectLanguages finds MULTI tag`() {
        val result = TgContentHeuristics.detectLanguages("Film.MULTI.BDRip")
        assertTrue("Should find MULTI", result.any { it.contains("MULTI", ignoreCase = true) })
    }

    @Test
    fun `detectLanguages finds multiple tags`() {
        val result = TgContentHeuristics.detectLanguages("Movie.GERMAN.ENGLISH.DUBBED.1080p")
        assertTrue("Should find at least 2 tags", result.size >= 2)
    }

    @Test
    fun `detectLanguages is case insensitive`() {
        val result = TgContentHeuristics.detectLanguages("movie.german.1080p")
        assertTrue("Should find lowercase german", result.isNotEmpty())
    }

    @Test
    fun `detectLanguages returns empty for no tags`() {
        val result = TgContentHeuristics.detectLanguages("Simple Movie Title")
        assertTrue("Should return empty list", result.isEmpty())
    }

    @Test
    fun `detectQuality finds 4K`() {
        val result = TgContentHeuristics.detectQuality("Movie.4K.HDR.mkv")
        assertNotNull("Should find 4K", result)
        assertTrue("Should contain 4K or UHD", 
            result!!.contains("4K", ignoreCase = true) || result.contains("UHD", ignoreCase = true))
    }

    @Test
    fun `detectQuality finds 2160p`() {
        val result = TgContentHeuristics.detectQuality("Film.2160p.UHD.mkv")
        assertNotNull("Should find 2160p", result)
        assertTrue("Should contain 2160 or UHD", result!!.contains("2160", ignoreCase = true) || result.contains("UHD", ignoreCase = true))
    }

    @Test
    fun `detectQuality finds 1080p`() {
        val result = TgContentHeuristics.detectQuality("Show.1080p.BluRay")
        assertNotNull("Should find 1080p", result)
        assertTrue("Should contain 1080", result!!.contains("1080", ignoreCase = true))
    }

    @Test
    fun `detectQuality finds 720p`() {
        val result = TgContentHeuristics.detectQuality("Film.720p.WEB-DL")
        assertNotNull("Should find 720p", result)
        assertTrue("Should contain 720", result!!.contains("720", ignoreCase = true))
    }

    @Test
    fun `detectQuality finds 480p`() {
        val result = TgContentHeuristics.detectQuality("Video.480p.DVDRip")
        assertNotNull("Should find 480p", result)
        assertTrue("Should contain 480", result!!.contains("480", ignoreCase = true))
    }

    @Test
    fun `detectQuality returns null for no quality`() {
        val result = TgContentHeuristics.detectQuality("Simple Movie")
        assertNull("Should return null for no quality info", result)
    }

    @Test
    fun `hasSeriesIndicators detects series keyword`() {
        assertTrue(TgContentHeuristics.hasSeriesIndicators("TV Series Collection"))
    }

    @Test
    fun `hasSeriesIndicators detects show keyword`() {
        assertTrue(TgContentHeuristics.hasSeriesIndicators("Best Shows"))
    }

    @Test
    fun `hasSeriesIndicators detects German Serien`() {
        assertTrue(TgContentHeuristics.hasSeriesIndicators("Serien HD"))
    }

    @Test
    fun `hasSeriesIndicators detects Staffel`() {
        assertTrue(TgContentHeuristics.hasSeriesIndicators("Staffel Sammlung"))
    }

    @Test
    fun `hasSeriesIndicators returns false for no indicators`() {
        assertFalse(TgContentHeuristics.hasSeriesIndicators("Random Channel"))
    }

    @Test
    fun `hasMovieIndicators detects movie keyword`() {
        assertTrue(TgContentHeuristics.hasMovieIndicators("Movies HD"))
    }

    @Test
    fun `hasMovieIndicators detects film keyword`() {
        assertTrue(TgContentHeuristics.hasMovieIndicators("Film Collection"))
    }

    @Test
    fun `hasMovieIndicators detects cinema keyword`() {
        assertTrue(TgContentHeuristics.hasMovieIndicators("Cinema Releases"))
    }

    @Test
    fun `hasMovieIndicators detects German Kino`() {
        assertTrue(TgContentHeuristics.hasMovieIndicators("Kino Filme"))
    }

    @Test
    fun `hasMovieIndicators returns false for no indicators`() {
        assertFalse(TgContentHeuristics.hasMovieIndicators("Random Channel"))
    }

    @Test
    fun `scoreContent provides quality score`() {
        val parsed = MediaInfo(
            chatId = 123L,
            messageId = 456L,
            kind = MediaKind.MOVIE,
            fileName = "Inception.2010.1080p.BluRay.mkv",
            title = "Inception",
            year = 2010,
            tmdbRating = 8.5
        )
        val score = TgContentHeuristics.scoreContent(parsed)
        assertTrue("Score should be positive", score > 0.0)
        assertTrue("Score should be reasonable (< 1.5)", score <= 1.5)
    }

    @Test
    fun `scoreContent handles minimal data`() {
        val parsed = MediaInfo(
            chatId = 123L,
            messageId = 456L,
            kind = MediaKind.MOVIE,
            fileName = "movie.mkv",
            title = null
        )
        val score = TgContentHeuristics.scoreContent(parsed)
        assertTrue("Score should still be valid", score >= 0.0)
    }
}
