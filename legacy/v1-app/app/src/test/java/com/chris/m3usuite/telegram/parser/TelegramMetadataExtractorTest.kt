package com.chris.m3usuite.telegram.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for TelegramMetadataExtractor.
 */
class TelegramMetadataExtractorTest {
    @Test
    fun `extractFromText preserves all metadata fields`() {
        val text =
            ExportText(
                id = 1,
                chatId = -1001L,
                dateEpochSeconds = 1000L,
                dateIso = "2023-01-01T00:00:00Z",
                text = "Movie info text",
                title = "Test Movie",
                originalTitle = "Original Test Movie",
                year = 2023,
                lengthMinutes = 120,
                fsk = 12,
                productionCountry = "USA",
                collection = "Test Collection",
                director = "Test Director",
                tmdbRating = 7.5,
                genres = listOf("Action", "Drama"),
                tmdbUrl = "https://themoviedb.org/movie/12345",
            )

        val metadata = TelegramMetadataExtractor.extractFromText(text, chatTitle = "Movies")

        assertEquals("Title should be preserved", "Test Movie", metadata.title)
        assertEquals("Original title should be preserved", "Original Test Movie", metadata.originalTitle)
        assertEquals("Year should be preserved", 2023, metadata.year)
        assertEquals("Length should be preserved", 120, metadata.lengthMinutes)
        assertEquals("FSK should be preserved", 12, metadata.fsk)
        assertEquals("Country should be preserved", "USA", metadata.productionCountry)
        assertEquals("Collection should be preserved", "Test Collection", metadata.collection)
        assertEquals("Director should be preserved", "Test Director", metadata.director)
        assertEquals("TMDb rating should be preserved", 7.5, metadata.tmdbRating)
        assertEquals("Genres should be preserved", listOf("Action", "Drama"), metadata.genres)
        assertEquals("TMDb URL should be preserved", "https://themoviedb.org/movie/12345", metadata.tmdbUrl)
    }

    @Test
    fun `extractFromText determines isAdult from chat title`() {
        val text =
            ExportText(
                id = 1,
                chatId = -1001L,
                dateEpochSeconds = 1000L,
                dateIso = "2023-01-01T00:00:00Z",
                text = "Movie info",
            )

        val adultMetadata = TelegramMetadataExtractor.extractFromText(text, chatTitle = "Adult Content 18+")
        val normalMetadata = TelegramMetadataExtractor.extractFromText(text, chatTitle = "HD Movies")

        assertTrue("Adult chat should set isAdult", adultMetadata.isAdult)
        assertFalse("Normal chat should not set isAdult", normalMetadata.isAdult)
    }

    @Test
    fun `extractFromText extracts TMDb URL from text`() {
        val text =
            ExportText(
                id = 1,
                chatId = -1001L,
                dateEpochSeconds = 1000L,
                dateIso = "2023-01-01T00:00:00Z",
                text = "Check this movie https://www.themoviedb.org/movie/550-fight-club great!",
            )

        val metadata = TelegramMetadataExtractor.extractFromText(text, chatTitle = "Movies")

        assertTrue("Should extract TMDb URL", metadata.tmdbUrl?.contains("themoviedb.org/movie/550") == true)
    }

    @Test
    fun `extractFromFilename extracts year from filename`() {
        val metadata =
            TelegramMetadataExtractor.extractFromFilename(
                fileName = "Movie.Name.2023.1080p.mp4",
                caption = null,
                chatTitle = "Movies",
            )

        assertEquals("Should extract year", 2023, metadata.year)
    }

    @Test
    fun `extractFromFilename extracts title from filename`() {
        val metadata =
            TelegramMetadataExtractor.extractFromFilename(
                fileName = "The.Great.Movie.2023.1080p.BluRay.mp4",
                caption = null,
                chatTitle = "Movies",
            )

        // Title should be extracted (contains "The Great Movie" after cleaning dots)
        assertNotNull("Should extract title", metadata.title)
        assertTrue("Title should not be empty", metadata.title!!.isNotBlank())
    }

    @Test
    fun `extractFromFilename handles year in parentheses`() {
        val metadata =
            TelegramMetadataExtractor.extractFromFilename(
                fileName = "Movie Name (2023).mp4",
                caption = null,
                chatTitle = "Movies",
            )

        assertEquals("Should extract year from parentheses", 2023, metadata.year)
    }

    @Test
    fun `extractFromFilename uses caption as fallback title`() {
        val metadata =
            TelegramMetadataExtractor.extractFromFilename(
                fileName = null,
                caption = "Amazing Film Description",
                chatTitle = "Movies",
            )

        assertEquals("Should use caption as title", "Amazing Film Description", metadata.title)
    }

    @Test
    fun `extractFromFilename extracts TMDb URL from caption`() {
        val metadata =
            TelegramMetadataExtractor.extractFromFilename(
                fileName = "video.mp4",
                caption = "Movie - https://themoviedb.org/movie/12345",
                chatTitle = "Movies",
            )

        assertTrue("Should extract TMDb URL from caption", metadata.tmdbUrl?.contains("12345") == true)
    }

    @Test
    fun `merge prefers primary metadata`() {
        val primary =
            TelegramMetadataExtractor.emptyMetadata(null).copy(
                title = "Primary Title",
                year = 2023,
                genres = listOf("Action"),
            )
        val secondary =
            TelegramMetadataExtractor.emptyMetadata(null).copy(
                title = "Secondary Title",
                year = 2022,
                director = "Some Director",
                genres = listOf("Drama"),
            )

        val merged = TelegramMetadataExtractor.merge(primary, secondary)

        assertEquals("Should use primary title", "Primary Title", merged.title)
        assertEquals("Should use primary year", 2023, merged.year)
        assertEquals("Should use secondary director (primary is null)", "Some Director", merged.director)
        assertEquals("Should use primary genres (not empty)", listOf("Action"), merged.genres)
    }

    @Test
    fun `merge falls back to secondary when primary is null`() {
        val primary = TelegramMetadataExtractor.emptyMetadata(null)
        val secondary =
            TelegramMetadataExtractor.emptyMetadata(null).copy(
                title = "Secondary Title",
                year = 2022,
            )

        val merged = TelegramMetadataExtractor.merge(primary, secondary)

        assertEquals("Should use secondary title", "Secondary Title", merged.title)
        assertEquals("Should use secondary year", 2022, merged.year)
    }

    @Test
    fun `merge combines isAdult with OR`() {
        val adultPrimary =
            TelegramMetadataExtractor.emptyMetadata(null).copy(isAdult = true)
        val nonAdultSecondary =
            TelegramMetadataExtractor.emptyMetadata(null).copy(isAdult = false)

        val merged1 = TelegramMetadataExtractor.merge(adultPrimary, nonAdultSecondary)
        val merged2 = TelegramMetadataExtractor.merge(nonAdultSecondary, adultPrimary)

        assertTrue("Should be adult if primary is adult", merged1.isAdult)
        assertTrue("Should be adult if secondary is adult", merged2.isAdult)
    }

    @Test
    fun `emptyMetadata only has isAdult from chat title`() {
        val adultMetadata = TelegramMetadataExtractor.emptyMetadata("Porn Channel")
        val normalMetadata = TelegramMetadataExtractor.emptyMetadata("Movies")

        assertTrue("Adult chat should set isAdult", adultMetadata.isAdult)
        assertFalse("Normal chat should not set isAdult", normalMetadata.isAdult)
        assertNull("Other fields should be null", adultMetadata.title)
        assertNull("Year should be null", adultMetadata.year)
    }

    @Test
    fun `extractFromFilename validates year range`() {
        val futureYear =
            TelegramMetadataExtractor.extractFromFilename(
                fileName = "Movie.2050.mp4",
                caption = null,
                chatTitle = "Movies",
            )

        val oldYear =
            TelegramMetadataExtractor.extractFromFilename(
                fileName = "Movie.1850.mp4",
                caption = null,
                chatTitle = "Movies",
            )

        val validYear =
            TelegramMetadataExtractor.extractFromFilename(
                fileName = "Movie.2023.mp4",
                caption = null,
                chatTitle = "Movies",
            )

        assertNull("Future year should be rejected", futureYear.year)
        assertNull("Too old year should be rejected", oldYear.year)
        assertEquals("Valid year should be extracted", 2023, validYear.year)
    }

    @Test
    fun `extractFromText handles null chatTitle`() {
        val text =
            ExportText(
                id = 1,
                chatId = -1001L,
                dateEpochSeconds = 1000L,
                dateIso = "2023-01-01T00:00:00Z",
                text = "Movie info",
                title = "Test",
            )

        val metadata = TelegramMetadataExtractor.extractFromText(text, chatTitle = null)

        assertFalse("Null chat title should not set isAdult", metadata.isAdult)
        assertEquals("Title should still be extracted", "Test", metadata.title)
    }
}
