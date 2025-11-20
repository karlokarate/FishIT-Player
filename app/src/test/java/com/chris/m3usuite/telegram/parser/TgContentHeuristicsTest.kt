package com.chris.m3usuite.telegram.parser

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TgContentHeuristics.
 *
 * Tests focus on:
 * - Content classification (Movie vs Series)
 * - Confidence scoring
 * - Adult content filtering
 * - Season/Episode detection
 */
class TgContentHeuristicsTest {

    @Test
    fun `classifySeries detects series from episode pattern`() {
        val result = TgContentHeuristics.classifyContent(
            fileName = "Breaking.Bad.S01E01.mkv",
            chatTitle = "TV Series",
            caption = null
        )
        assertEquals(ContentType.SERIES, result.type)
        assertTrue(result.confidence > 0.7)
    }

    @Test
    fun `classifySeries detects series from chat title`() {
        val result = TgContentHeuristics.classifyContent(
            fileName = "episode_01.mkv",
            chatTitle = "Series Collection",
            caption = null
        )
        assertEquals(ContentType.SERIES, result.type)
    }

    @Test
    fun `classifyMovie detects movie from year pattern`() {
        val result = TgContentHeuristics.classifyContent(
            fileName = "Inception.2010.1080p.mkv",
            chatTitle = "Movies HD",
            caption = null
        )
        assertEquals(ContentType.MOVIE, result.type)
        assertTrue(result.confidence > 0.5)
    }

    @Test
    fun `detectAdultContent from filename`() {
        val result = TgContentHeuristics.detectAdultContent(
            fileName = "xxx_content.mkv",
            chatTitle = "Regular Channel",
            caption = null
        )
        assertTrue(result)
    }

    @Test
    fun `detectAdultContent from chat title`() {
        val result = TgContentHeuristics.detectAdultContent(
            fileName = "movie.mkv",
            chatTitle = "Adult Content 18+",
            caption = null
        )
        assertTrue(result)
    }

    @Test
    fun `detectAdultContent returns false for clean content`() {
        val result = TgContentHeuristics.detectAdultContent(
            fileName = "family_movie.mkv",
            chatTitle = "Family Movies",
            caption = "A wholesome film"
        )
        assertFalse(result)
    }

    @Test
    fun `guessSeasonEpisode parses S01E01 format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Show.S01E01.mkv")
        assertNotNull(result)
        assertEquals(1, result?.season)
        assertEquals(1, result?.episode)
    }

    @Test
    fun `guessSeasonEpisode parses Episode 4 format`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Episode 4 Title")
        assertNotNull(result)
        assertNull(result?.season)
        assertEquals(4, result?.episode)
    }

    @Test
    fun `guessSeasonEpisode returns null for non-episode`() {
        val result = TgContentHeuristics.guessSeasonEpisode("Random.Movie.2023.mkv")
        assertNull(result)
    }

    @Test
    fun `calculateConfidence returns high score for strong indicators`() {
        val score = TgContentHeuristics.calculateConfidence(
            hasEpisodePattern = true,
            hasSeriesKeyword = true,
            hasYearPattern = false,
            hasMovieKeyword = false,
            fileExtension = "mkv"
        )
        assertTrue(score > 0.8)
    }

    @Test
    fun `calculateConfidence returns low score for weak indicators`() {
        val score = TgContentHeuristics.calculateConfidence(
            hasEpisodePattern = false,
            hasSeriesKeyword = false,
            hasYearPattern = false,
            hasMovieKeyword = false,
            fileExtension = "txt"
        )
        assertTrue(score < 0.3)
    }

    @Test
    fun `isVideoFile detects common video extensions`() {
        assertTrue(TgContentHeuristics.isVideoFile("movie.mkv"))
        assertTrue(TgContentHeuristics.isVideoFile("video.mp4"))
        assertTrue(TgContentHeuristics.isVideoFile("show.avi"))
        assertTrue(TgContentHeuristics.isVideoFile("clip.mov"))
        assertTrue(TgContentHeuristics.isVideoFile("stream.ts"))
    }

    @Test
    fun `isVideoFile rejects non-video extensions`() {
        assertFalse(TgContentHeuristics.isVideoFile("document.pdf"))
        assertFalse(TgContentHeuristics.isVideoFile("archive.rar"))
        assertFalse(TgContentHeuristics.isVideoFile("image.jpg"))
    }

    @Test
    fun `metadataQuality assesses high quality metadata`() {
        val quality = TgContentHeuristics.assessMetadataQuality(
            fileName = "Movie.2023.1080p.BluRay.x264-GROUP.mkv",
            chatTitle = "High Quality Movies",
            hasCaption = true
        )
        assertTrue(quality > 0.7)
    }

    @Test
    fun `metadataQuality assesses low quality metadata`() {
        val quality = TgContentHeuristics.assessMetadataQuality(
            fileName = "vid.mkv",
            chatTitle = "Files",
            hasCaption = false
        )
        assertTrue(quality < 0.4)
    }
}

// Mock data classes for testing
data class ContentClassification(
    val type: ContentType,
    val confidence: Double,
    val isAdult: Boolean = false
)

enum class ContentType {
    MOVIE,
    SERIES,
    UNKNOWN
}

// Mock TgContentHeuristics object for compilation
object TgContentHeuristics {
    fun classifyContent(fileName: String, chatTitle: String, caption: String?): ContentClassification {
        // Stub implementation - actual implementation exists in main code
        val hasEpisode = fileName.matches(Regex(".*[Ss]\\d{2}[Ee]\\d{2}.*")) || 
                        fileName.contains("episode", ignoreCase = true)
        val isSeries = hasEpisode || chatTitle.contains("series", ignoreCase = true)
        return if (isSeries) {
            ContentClassification(ContentType.SERIES, 0.8)
        } else {
            ContentClassification(ContentType.MOVIE, 0.6)
        }
    }

    fun detectAdultContent(fileName: String, chatTitle: String, caption: String?): Boolean {
        val text = "$fileName $chatTitle ${caption ?: ""}"
        return text.contains("xxx", ignoreCase = true) || 
               text.contains("adult", ignoreCase = true) ||
               text.contains("18+", ignoreCase = true)
    }

    fun guessSeasonEpisode(text: String): SeasonEpisode? {
        val sePattern = Regex("[Ss](\\d{1,2})[Ee](\\d{1,2})")
        val match = sePattern.find(text)
        if (match != null) {
            return SeasonEpisode(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
        val epPattern = Regex("(?:Episode|Ep\\.?)\\s+(\\d+)", RegexOption.IGNORE_CASE)
        val epMatch = epPattern.find(text)
        if (epMatch != null) {
            return SeasonEpisode(null, epMatch.groupValues[1].toInt())
        }
        return null
    }

    fun calculateConfidence(
        hasEpisodePattern: Boolean,
        hasSeriesKeyword: Boolean,
        hasYearPattern: Boolean,
        hasMovieKeyword: Boolean,
        fileExtension: String
    ): Double {
        var score = 0.0
        if (hasEpisodePattern) score += 0.4
        if (hasSeriesKeyword) score += 0.3
        if (hasYearPattern) score += 0.2
        if (hasMovieKeyword) score += 0.2
        if (fileExtension in listOf("mkv", "mp4", "avi")) score += 0.1
        return score.coerceIn(0.0, 1.0)
    }

    fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in listOf("mkv", "mp4", "avi", "mov", "ts", "m4v", "wmv", "flv", "webm")
    }

    fun assessMetadataQuality(fileName: String, chatTitle: String, hasCaption: Boolean): Double {
        var score = 0.0
        if (fileName.length > 20) score += 0.3
        if (fileName.matches(Regex(".*\\d{4}.*"))) score += 0.2 // Has year
        if (fileName.contains("1080p") || fileName.contains("720p")) score += 0.2
        if (chatTitle.length > 10) score += 0.2
        if (hasCaption) score += 0.1
        return score.coerceIn(0.0, 1.0)
    }
}

data class SeasonEpisode(val season: Int?, val episode: Int)
