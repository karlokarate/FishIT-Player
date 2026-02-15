package com.fishit.player.infra.transport.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for resolved* computed properties in Xtream API models.
 *
 * These tests verify that the transport layer correctly handles
 * field name variations across different Xtream panels.
 *
 * Per AGENTS.md Section 12 - Generic Xtream Panel Support:
 * The resolved* properties are the SSOT for field normalization.
 *
 * Priority Order (from XtreamApiModels.kt):
 * - XtreamVodStream.resolvedId: streamId → vodId → movieId → id → 0
 * - XtreamVodStream.resolvedPoster: streamIcon → posterPath → cover → logo
 * - XtreamLiveStream.resolvedId: streamId → id → 0
 * - XtreamLiveStream.resolvedIcon: streamIcon → logo
 * - XtreamSeriesStream.resolvedId: seriesId → id → 0
 * - XtreamSeriesStream.resolvedCover: cover → posterPath → logo
 */
class XtreamResolvedFieldsTest {
    // =========================================================================
    // XtreamVodStream.resolvedId
    // Priority: streamId → vodId → movieId → id → 0
    // =========================================================================

    @Test
    fun `vodStream resolvedId prefers streamId`() {
        val stream = XtreamVodStream(streamId = 789, vodId = 123, movieId = 456, id = 999)
        assertEquals(789, stream.resolvedId)
    }

    @Test
    fun `vodStream resolvedId falls back to vodId`() {
        val stream = XtreamVodStream(streamId = null, vodId = 123, movieId = 456, id = 999)
        assertEquals(123, stream.resolvedId)
    }

    @Test
    fun `vodStream resolvedId falls back to movieId`() {
        val stream = XtreamVodStream(streamId = null, vodId = null, movieId = 456, id = 999)
        assertEquals(456, stream.resolvedId)
    }

    @Test
    fun `vodStream resolvedId falls back to id`() {
        val stream = XtreamVodStream(streamId = null, vodId = null, movieId = null, id = 999)
        assertEquals(999, stream.resolvedId)
    }

    @Test
    fun `vodStream resolvedId handles all nulls`() {
        val stream = XtreamVodStream()
        assertEquals(0, stream.resolvedId)
    }

    // =========================================================================
    // XtreamVodStream.resolvedPoster
    // Priority: streamIcon → posterPath → cover → logo
    // =========================================================================

    @Test
    fun `vodStream resolvedPoster prefers streamIcon`() {
        val stream =
            XtreamVodStream(
                streamIcon = "https://icon.jpg",
                posterPath = "https://tmdb.org/poster.jpg",
                cover = "https://cover.jpg",
                logo = "https://logo.jpg",
            )
        assertEquals("https://icon.jpg", stream.resolvedPoster)
    }

    @Test
    fun `vodStream resolvedPoster falls back to posterPath`() {
        val stream =
            XtreamVodStream(
                streamIcon = null,
                posterPath = "https://tmdb.org/poster.jpg",
                cover = "https://cover.jpg",
                logo = "https://logo.jpg",
            )
        assertEquals("https://tmdb.org/poster.jpg", stream.resolvedPoster)
    }

    @Test
    fun `vodStream resolvedPoster falls back to cover`() {
        val stream =
            XtreamVodStream(
                streamIcon = null,
                posterPath = null,
                cover = "https://cover.jpg",
                logo = "https://logo.jpg",
            )
        assertEquals("https://cover.jpg", stream.resolvedPoster)
    }

    @Test
    fun `vodStream resolvedPoster falls back to logo`() {
        val stream =
            XtreamVodStream(
                streamIcon = null,
                posterPath = null,
                cover = null,
                logo = "https://logo.jpg",
            )
        assertEquals("https://logo.jpg", stream.resolvedPoster)
    }

    @Test
    fun `vodStream resolvedPoster handles all nulls`() {
        val stream =
            XtreamVodStream(
                streamIcon = null,
                posterPath = null,
                cover = null,
                logo = null,
            )
        assertNull(stream.resolvedPoster)
    }

    // =========================================================================
    // XtreamLiveStream.resolvedId
    // Priority: streamId → id → 0
    // =========================================================================

    @Test
    fun `liveStream resolvedId prefers streamId`() {
        val stream = XtreamLiveStream(streamId = 100, id = 200)
        assertEquals(100, stream.resolvedId)
    }

    @Test
    fun `liveStream resolvedId falls back to id`() {
        val stream = XtreamLiveStream(streamId = null, id = 200)
        assertEquals(200, stream.resolvedId)
    }

    // =========================================================================
    // XtreamLiveStream.resolvedIcon
    // =========================================================================

    @Test
    fun `liveStream resolvedIcon prefers streamIcon`() {
        val stream = XtreamLiveStream(streamIcon = "https://icon.jpg", logo = "https://logo.jpg")
        assertEquals("https://icon.jpg", stream.resolvedIcon)
    }

    @Test
    fun `liveStream resolvedIcon falls back to logo`() {
        val stream = XtreamLiveStream(streamIcon = null, logo = "https://logo.jpg")
        assertEquals("https://logo.jpg", stream.resolvedIcon)
    }

    // =========================================================================
    // XtreamSeriesStream.resolvedId
    // =========================================================================

    @Test
    fun `seriesStream resolvedId prefers seriesId`() {
        val stream = XtreamSeriesStream(seriesId = 500, id = 600)
        assertEquals(500, stream.resolvedId)
    }

    @Test
    fun `seriesStream resolvedId falls back to id`() {
        val stream = XtreamSeriesStream(seriesId = null, id = 600)
        assertEquals(600, stream.resolvedId)
    }

    // =========================================================================
    // XtreamSeriesStream.resolvedCover
    // =========================================================================

    @Test
    fun `seriesStream resolvedCover prefers cover`() {
        val stream =
            XtreamSeriesStream(
                cover = "https://cover.jpg",
                posterPath = "https://poster.jpg",
                logo = "https://logo.jpg",
            )
        assertEquals("https://cover.jpg", stream.resolvedCover)
    }

    @Test
    fun `seriesStream resolvedCover falls back to posterPath`() {
        val stream =
            XtreamSeriesStream(
                cover = null,
                posterPath = "https://poster.jpg",
                logo = "https://logo.jpg",
            )
        assertEquals("https://poster.jpg", stream.resolvedCover)
    }

    @Test
    fun `seriesStream resolvedCover falls back to logo`() {
        val stream =
            XtreamSeriesStream(
                cover = null,
                posterPath = null,
                logo = "https://logo.jpg",
            )
        assertEquals("https://logo.jpg", stream.resolvedCover)
    }

    // =========================================================================
    // XtreamSeriesStream.resolvedYear
    // =========================================================================

    @Test
    fun `seriesStream resolvedYear prefers year field`() {
        val stream = XtreamSeriesStream(year = "2024", releaseDate = "2023-05-15")
        assertEquals("2024", stream.resolvedYear)
    }

    @Test
    fun `seriesStream resolvedYear extracts from releaseDate`() {
        val stream = XtreamSeriesStream(year = null, releaseDate = "2023-05-15")
        assertEquals("2023", stream.resolvedYear)
    }

    @Test
    fun `seriesStream resolvedYear handles invalid releaseDate`() {
        val stream = XtreamSeriesStream(year = null, releaseDate = "invalid")
        assertEquals(null, stream.resolvedYear)
    }

    // =========================================================================
    // XtreamSeriesInfoBlock.resolvedReleaseDate
    // Priority: releasedate → releaseDate (camelCase)
    // =========================================================================

    @Test
    fun `seriesInfoBlock resolvedReleaseDate prefers lowercase releasedate`() {
        val block =
            XtreamSeriesInfoBlock(
                releaseDate = "2008-01-20",
                releaseDateCamel = "2010-06-15",
            )
        assertEquals("2008-01-20", block.resolvedReleaseDate)
    }

    @Test
    fun `seriesInfoBlock resolvedReleaseDate falls back to camelCase`() {
        val block =
            XtreamSeriesInfoBlock(
                releaseDate = null,
                releaseDateCamel = "2008-01-20",
            )
        assertEquals("2008-01-20", block.resolvedReleaseDate)
    }

    @Test
    fun `seriesInfoBlock resolvedReleaseDate ignores blank lowercase`() {
        val block =
            XtreamSeriesInfoBlock(
                releaseDate = "",
                releaseDateCamel = "2008-01-20",
            )
        assertEquals("2008-01-20", block.resolvedReleaseDate)
    }

    @Test
    fun `seriesInfoBlock resolvedReleaseDate handles all null`() {
        val block = XtreamSeriesInfoBlock()
        assertNull(block.resolvedReleaseDate)
    }

    // =========================================================================
    // XtreamSeriesInfoBlock new fields (lastModified, categoryId)
    // =========================================================================

    @Test
    fun `seriesInfoBlock stores lastModified`() {
        val block = XtreamSeriesInfoBlock(lastModified = "1706025493")
        assertEquals("1706025493", block.lastModified)
    }

    @Test
    fun `seriesInfoBlock stores categoryId`() {
        val block = XtreamSeriesInfoBlock(categoryId = "42")
        assertEquals("42", block.categoryId)
    }
}
