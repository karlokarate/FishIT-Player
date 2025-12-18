package com.fishit.player.pipeline.telegram.grouper

import com.fishit.player.infra.transport.telegram.api.TgContent
import com.fishit.player.infra.transport.telegram.api.TgMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TelegramStructuredMetadataExtractor].
 *
 * Per TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md Section 7.1:
 * - Test: TMDB URL parsing (various formats)
 * - Test: FSK extraction (numeric)
 * - Test: Genre list parsing
 * - Test: Missing fields → null
 * - Test: Schema Guards (year, rating, fsk, length ranges)
 */
class TelegramStructuredMetadataExtractorTest {

    private lateinit var extractor: TelegramStructuredMetadataExtractor

    @Before
    fun setUp() {
        extractor = TelegramStructuredMetadataExtractor()
    }

    // ========== TMDB URL Parsing Tests ==========

    @Test
    fun `extractTmdbIdFromUrl parses movie URL`() {
        val url = "https://www.themoviedb.org/movie/12345-movie-name"
        
        val tmdbId = extractor.extractTmdbIdFromUrl(url)
        
        assertEquals(12345, tmdbId)
    }

    @Test
    fun `extractTmdbIdFromUrl parses TV URL`() {
        val url = "https://www.themoviedb.org/tv/98765-show-name"
        
        val tmdbId = extractor.extractTmdbIdFromUrl(url)
        
        assertEquals(98765, tmdbId)
    }

    @Test
    fun `extractTmdbIdFromUrl parses URL without protocol`() {
        val url = "themoviedb.org/movie/54321"
        
        val tmdbId = extractor.extractTmdbIdFromUrl(url)
        
        assertEquals(54321, tmdbId)
    }

    @Test
    fun `extractTmdbIdFromUrl returns null for invalid format`() {
        val invalidUrls = listOf(
            "https://imdb.com/title/tt1234567",
            "https://example.com/movie/12345",
            "not-a-url",
            "",
            null,
        )
        
        invalidUrls.forEach { url ->
            val tmdbId = extractor.extractTmdbIdFromUrl(url)
            assertNull("Expected null for: $url", tmdbId)
        }
    }

    // ========== Schema Guards Tests ==========

    @Test
    fun `Schema Guard accepts valid year`() {
        val message = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", year: 2020"""
        )
        
        val metadata = extractor.extractStructuredMetadata(message)
        
        assertNotNull(metadata)
        assertEquals(2020, metadata?.year)
    }

    @Test
    fun `Schema Guard rejects year outside 1800-2100`() {
        val message1 = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", year: 1700"""
        )
        val message2 = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", year: 2200"""
        )
        
        val metadata1 = extractor.extractStructuredMetadata(message1)
        val metadata2 = extractor.extractStructuredMetadata(message2)
        
        assertNull(metadata1?.year)
        assertNull(metadata2?.year)
    }

    @Test
    fun `Schema Guard accepts valid rating`() {
        val message = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", tmdbRating: 7.5"""
        )
        
        val metadata = extractor.extractStructuredMetadata(message)
        
        assertNotNull(metadata)
        assertEquals(7.5, metadata?.tmdbRating)
    }

    @Test
    fun `Schema Guard rejects rating outside 0-10`() {
        val message = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", tmdbRating: 15.0"""
        )
        
        val metadata = extractor.extractStructuredMetadata(message)
        
        assertNull(metadata?.tmdbRating)
    }

    @Test
    fun `Schema Guard accepts valid FSK values`() {
        val fskValues = listOf(0, 6, 12, 16, 18, 21)
        
        fskValues.forEach { fsk ->
            val message = createTextMessageWithCaption(
                """tmdbUrl: "themoviedb.org/movie/123", fsk: $fsk"""
            )
            
            val metadata = extractor.extractStructuredMetadata(message)
            
            assertEquals("FSK $fsk should be valid", fsk, metadata?.fsk)
        }
    }

    @Test
    fun `Schema Guard rejects FSK outside 0-21`() {
        val message = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", fsk: 25"""
        )
        
        val metadata = extractor.extractStructuredMetadata(message)
        
        assertNull(metadata?.fsk)
    }

    @Test
    fun `Schema Guard accepts valid length`() {
        val message = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", lengthMinutes: 120"""
        )
        
        val metadata = extractor.extractStructuredMetadata(message)
        
        assertEquals(120, metadata?.lengthMinutes)
    }

    @Test
    fun `Schema Guard rejects length outside 1-600`() {
        val message1 = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", lengthMinutes: 0"""
        )
        val message2 = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", lengthMinutes: 700"""
        )
        
        val metadata1 = extractor.extractStructuredMetadata(message1)
        val metadata2 = extractor.extractStructuredMetadata(message2)
        
        assertNull(metadata1?.lengthMinutes)
        assertNull(metadata2?.lengthMinutes)
    }

    // ========== Genre Parsing Tests ==========

    @Test
    fun `extracts genre list`() {
        val message = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", genres: ["Action", "Comedy", "Drama"]"""
        )
        
        val metadata = extractor.extractStructuredMetadata(message)
        
        assertEquals(listOf("Action", "Comedy", "Drama"), metadata?.genres)
    }

    @Test
    fun `extracts genres with single quotes`() {
        val message = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", genres: ['Action', 'Sci-Fi']"""
        )
        
        val metadata = extractor.extractStructuredMetadata(message)
        
        assertEquals(listOf("Action", "Sci-Fi"), metadata?.genres)
    }

    @Test
    fun `empty genres returns empty list`() {
        val message = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", genres: []"""
        )
        
        val metadata = extractor.extractStructuredMetadata(message)
        
        assertEquals(emptyList<String>(), metadata?.genres)
    }

    // ========== Complete Extraction Tests ==========

    @Test
    fun `extracts complete structured metadata`() {
        val message = createTextMessageWithCaption(
            """
            tmdbUrl: "https://www.themoviedb.org/movie/957-spaceballs"
            tmdbRating: 6.9
            year: 1987
            fsk: 12
            director: "Mel Brooks"
            originalTitle: "Spaceballs"
            lengthMinutes: 96
            productionCountry: "US"
            genres: ["Comedy", "Science Fiction"]
            """.trimIndent()
        )
        
        val metadata = extractor.extractStructuredMetadata(message)
        
        assertNotNull(metadata)
        assertEquals(957, metadata?.tmdbId)
        assertEquals(6.9, metadata?.tmdbRating)
        assertEquals(1987, metadata?.year)
        assertEquals(12, metadata?.fsk)
        assertEquals("Mel Brooks", metadata?.director)
        assertEquals("Spaceballs", metadata?.originalTitle)
        assertEquals(96, metadata?.lengthMinutes)
        assertEquals("US", metadata?.productionCountry)
        assertEquals(listOf("Comedy", "Science Fiction"), metadata?.genres)
    }

    @Test
    fun `handles missing optional fields`() {
        val message = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", year: 2020"""
        )
        
        val metadata = extractor.extractStructuredMetadata(message)
        
        assertNotNull(metadata)
        assertEquals(123, metadata?.tmdbId)
        assertEquals(2020, metadata?.year)
        assertNull(metadata?.tmdbRating)
        assertNull(metadata?.fsk)
        assertNull(metadata?.director)
        assertNull(metadata?.lengthMinutes)
        assertEquals(emptyList<String>(), metadata?.genres)
    }

    // ========== Detection Tests ==========

    @Test
    fun `hasStructuredFields returns true for structured message`() {
        val message = createTextMessageWithCaption(
            """tmdbUrl: "themoviedb.org/movie/123", year: 2020"""
        )
        
        val hasFields = extractor.hasStructuredFields(message)
        
        assertTrue(hasFields)
    }

    @Test
    fun `hasStructuredFields returns false for non-structured message`() {
        val message = createTextMessageWithCaption(
            "Just a regular caption with no structured data"
        )
        
        val hasFields = extractor.hasStructuredFields(message)
        
        assertFalse(hasFields)
    }

    @Test
    fun `returns null for non-structured message`() {
        val message = createTextMessageWithCaption(
            "Movie title 2020 - just a regular caption"
        )
        
        val metadata = extractor.extractStructuredMetadata(message)
        
        assertNull(metadata)
    }

    // ========== StructuredMetadata Properties Tests ==========

    @Test
    fun `hasTmdbId returns true when tmdbId is set`() {
        val metadata = StructuredMetadata(
            tmdbId = 123,
            tmdbRating = null,
            year = null,
            fsk = null,
            genres = emptyList(),
            director = null,
            originalTitle = null,
            lengthMinutes = null,
            productionCountry = null,
        )
        
        assertTrue(metadata.hasTmdbId)
    }

    @Test
    fun `hasAnyField returns true when any field is set`() {
        val metadata = StructuredMetadata(
            tmdbId = null,
            tmdbRating = null,
            year = 2020,
            fsk = null,
            genres = emptyList(),
            director = null,
            originalTitle = null,
            lengthMinutes = null,
            productionCountry = null,
        )
        
        assertTrue(metadata.hasAnyField)
    }

    @Test
    fun `EMPTY has no fields`() {
        val empty = StructuredMetadata.EMPTY
        
        assertFalse(empty.hasTmdbId)
        assertFalse(empty.hasAnyField)
    }

    // ========== Helper Methods ==========

    private fun createTextMessageWithCaption(caption: String): TgMessage =
        TgMessage(
            messageId = 100L,
            chatId = 123L,
            date = 1731704712L,
            content = TgContent.Video(
                fileId = 1,
                remoteId = "test",
                fileName = "test.mkv",
                mimeType = "video/mp4",
                duration = 100,
                width = 1920,
                height = 1080,
                fileSize = 1000L,
                caption = caption,
            ),
        )
}
