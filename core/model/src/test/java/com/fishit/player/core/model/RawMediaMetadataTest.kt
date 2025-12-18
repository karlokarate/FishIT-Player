package com.fishit.player.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [RawMediaMetadata] model extensions.
 *
 * Phase 1.4 of TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md:
 * - Unit tests for RawMediaMetadata extensions (ageRating, rating)
 */
class RawMediaMetadataTest {

    @Test
    fun `ageRating field accepts valid FSK values`() {
        val metadata = createTestMetadata(ageRating = 12)

        assertEquals(12, metadata.ageRating)
    }

    @Test
    fun `ageRating field accepts FSK 0`() {
        val metadata = createTestMetadata(ageRating = 0)

        assertEquals(0, metadata.ageRating)
    }

    @Test
    fun `ageRating field accepts maximum value 21`() {
        val metadata = createTestMetadata(ageRating = 21)

        assertEquals(21, metadata.ageRating)
    }

    @Test
    fun `ageRating field defaults to null`() {
        val metadata = createTestMetadata()

        assertNull(metadata.ageRating)
    }

    @Test
    fun `rating field accepts valid TMDB rating`() {
        val metadata = createTestMetadata(rating = 7.5)

        assertEquals(7.5, metadata.rating)
    }

    @Test
    fun `rating field accepts boundary values`() {
        val metadataLow = createTestMetadata(rating = 0.0)
        val metadataHigh = createTestMetadata(rating = 10.0)

        assertEquals(0.0, metadataLow.rating)
        assertEquals(10.0, metadataHigh.rating)
    }

    @Test
    fun `rating field defaults to null`() {
        val metadata = createTestMetadata()

        assertNull(metadata.rating)
    }

    @Test
    fun `externalIds with typed tmdb reference`() {
        val tmdbRef = TmdbRef(TmdbMediaType.MOVIE, 12345)
        val externalIds = ExternalIds(tmdb = tmdbRef)
        val metadata = createTestMetadata(externalIds = externalIds)

        assertEquals(12345, metadata.externalIds.tmdb?.id)
        assertEquals(TmdbMediaType.MOVIE, metadata.externalIds.tmdb?.type)
        assertEquals(12345, metadata.externalIds.effectiveTmdbId)
    }

    @Test
    fun `structured bundle metadata with all fields`() {
        val metadata = RawMediaMetadata(
            originalTitle = "The Movie",
            mediaType = MediaType.MOVIE,
            year = 2020,
            durationMs = 120 * 60_000L,
            externalIds = ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, 12345)),
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Mel Brooks 🥳",
            sourceId = "-1001434421634_388021760",
            rating = 7.5,
            ageRating = 12,
        )

        assertEquals("The Movie", metadata.originalTitle)
        assertEquals(MediaType.MOVIE, metadata.mediaType)
        assertEquals(2020, metadata.year)
        assertEquals(120 * 60_000L, metadata.durationMs)
        assertEquals(12345, metadata.externalIds.tmdb?.id)
        assertEquals(TmdbMediaType.MOVIE, metadata.externalIds.tmdb?.type)
        assertEquals(SourceType.TELEGRAM, metadata.sourceType)
        assertEquals("Mel Brooks 🥳", metadata.sourceLabel)
        assertEquals("-1001434421634_388021760", metadata.sourceId)
        assertEquals(7.5, metadata.rating)
        assertEquals(12, metadata.ageRating)
    }

    @Test
    fun `copy with updated fields`() {
        val original = createTestMetadata()
        val updated = original.copy(
            ageRating = 16,
            rating = 8.5,
        )

        assertNull(original.ageRating)
        assertNull(original.rating)
        assertEquals(16, updated.ageRating)
        assertEquals(8.5, updated.rating)
    }

    // === Helper Functions ===

    private fun createTestMetadata(
        ageRating: Int? = null,
        rating: Double? = null,
        externalIds: ExternalIds = ExternalIds(),
    ): RawMediaMetadata = RawMediaMetadata(
        originalTitle = "Test Movie",
        mediaType = MediaType.MOVIE,
        sourceType = SourceType.TELEGRAM,
        sourceLabel = "Test Chat",
        sourceId = "test_123",
        ageRating = ageRating,
        rating = rating,
        externalIds = externalIds,
    )
}
