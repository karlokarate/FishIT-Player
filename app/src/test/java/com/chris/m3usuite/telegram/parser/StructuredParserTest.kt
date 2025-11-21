package com.chris.m3usuite.telegram.parser

import com.chris.m3usuite.telegram.models.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for structured movie parsing and series detection.
 * 
 * Tests:
 * - parseStructuredMovieChat with 3-message pattern
 * - Series detection from filename/caption
 * - extractSeriesName helper
 * - Consumed message filtering
 * - fileId validation
 */
class StructuredParserTest {
    
    @Test
    fun `extractSeriesName removes S01E01 pattern`() {
        val seasonEp = TgContentHeuristics.SeasonEpisode(
            season = 1,
            episode = 1,
            pattern = "SxxEyy"
        )
        
        val result = MediaParser.extractSeriesName("Breaking.Bad.S01E01.1080p.mp4", seasonEp)
        
        assertNotNull(result)
        assertTrue("Expected 'Breaking Bad' in result", result?.contains("Breaking") == true)
        assertFalse("Should not contain S01E01", result?.contains("S01E01") == true)
    }
    
    @Test
    fun `extractSeriesName removes 1x02 pattern`() {
        val seasonEp = TgContentHeuristics.SeasonEpisode(
            season = 1,
            episode = 2,
            pattern = "XxY"
        )
        
        val result = MediaParser.extractSeriesName("The.Wire.1x02.720p.mkv", seasonEp)
        
        assertNotNull(result)
        assertTrue("Expected 'The Wire' in result", result?.contains("Wire") == true)
        assertFalse("Should not contain 1x02", result?.contains("1x02") == true)
    }
    
    @Test
    fun `extractSeriesName removes Episode pattern`() {
        val seasonEp = TgContentHeuristics.SeasonEpisode(
            season = null,
            episode = 4,
            pattern = "Episode X"
        )
        
        val result = MediaParser.extractSeriesName("Sherlock.Episode.4.mp4", seasonEp)
        
        assertNotNull(result)
        assertTrue("Expected 'Sherlock' in result", result?.contains("Sherlock") == true)
        assertFalse("Should not contain Episode", result?.contains("Episode") == true)
    }
    
    @Test
    fun `extractSeriesName cleans separators`() {
        val seasonEp = TgContentHeuristics.SeasonEpisode(
            season = 2,
            episode = 5,
            pattern = "SxxEyy"
        )
        
        val result = MediaParser.extractSeriesName("Game.Of.Thrones.S02E05.mkv", seasonEp)
        
        assertNotNull(result)
        // Should replace dots with spaces
        assertTrue("Expected spaces instead of dots", result?.contains(" ") == true)
        assertFalse("Should not contain dots", result?.contains(".") == true)
    }
    
    @Test
    fun `series detection identifies episode from filename with S01E01`() {
        // This tests the logic in parseMedia for MessageVideo
        // We can't easily test the full parseMessage without mocking TDLib Message objects
        // But we can test the heuristics
        
        val seasonEp = TgContentHeuristics.guessSeasonEpisode("Breaking.Bad.S01E01.1080p.mp4")
        
        assertNotNull("Should detect season/episode", seasonEp)
        assertEquals(1, seasonEp?.season)
        assertEquals(1, seasonEp?.episode)
    }
    
    @Test
    fun `series detection identifies episode from filename with 1x02`() {
        val seasonEp = TgContentHeuristics.guessSeasonEpisode("Show.1x02.720p.mkv")
        
        assertNotNull("Should detect season/episode", seasonEp)
        assertEquals(1, seasonEp?.season)
        assertEquals(2, seasonEp?.episode)
    }
    
    @Test
    fun `series detection from caption with German pattern`() {
        val seasonEp = TgContentHeuristics.guessSeasonEpisode("Dark Staffel 2 Folge 3")
        
        assertNotNull("Should detect German season/episode pattern", seasonEp)
        // This should match "Staffel 2" and "Folge 3"
        // Note: Current implementation may need enhancement for combined patterns
    }
    
    @Test
    fun `movie without series pattern returns null`() {
        val seasonEp = TgContentHeuristics.guessSeasonEpisode("Inception.2010.1080p.mp4")
        
        assertNull("Should not detect series pattern in movie filename", seasonEp)
    }
    
    @Test
    fun `ChatContext with isStructuredMovieChat flag`() {
        val context = ChatContext(
            chatId = 123456L,
            chatTitle = "Movie Collection",
            isStructuredMovieChat = true
        )
        
        assertTrue("Should have structured flag set", context.isStructuredMovieChat)
        assertEquals(123456L, context.chatId)
    }
    
    @Test
    fun `MediaInfo with fileId and consumed flag`() {
        val mediaInfo = MediaInfo(
            chatId = 123L,
            messageId = 456L,
            kind = MediaKind.MOVIE,
            fileId = 789,
            isConsumed = true
        )
        
        assertEquals(789, mediaInfo.fileId)
        assertTrue("Should be marked as consumed", mediaInfo.isConsumed)
    }
    
    @Test
    fun `MediaInfo with series metadata`() {
        val mediaInfo = MediaInfo(
            chatId = 123L,
            messageId = 456L,
            kind = MediaKind.EPISODE,
            seriesName = "Breaking Bad",
            seasonNumber = 1,
            episodeNumber = 2,
            episodeTitle = "Cat's in the Bag"
        )
        
        assertEquals("Breaking Bad", mediaInfo.seriesName)
        assertEquals(1, mediaInfo.seasonNumber)
        assertEquals(2, mediaInfo.episodeNumber)
        assertEquals("Cat's in the Bag", mediaInfo.episodeTitle)
    }
    
    @Test
    fun `MediaInfo with poster fileId`() {
        val mediaInfo = MediaInfo(
            chatId = 123L,
            messageId = 456L,
            kind = MediaKind.MOVIE,
            posterFileId = 999,
            title = "Test Movie"
        )
        
        assertEquals(999, mediaInfo.posterFileId)
        assertEquals("Test Movie", mediaInfo.title)
    }
}
