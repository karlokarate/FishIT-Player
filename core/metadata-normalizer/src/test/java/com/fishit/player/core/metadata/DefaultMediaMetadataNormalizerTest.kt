package com.fishit.player.core.metadata

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for DefaultMediaMetadataNormalizer.
 *
 * Phase 3 skeleton tests verify that the default implementation
 * is a no-op pass-through (no title cleaning, no parsing).
 */
class DefaultMediaMetadataNormalizerTest {
    private val normalizer = DefaultMediaMetadataNormalizer()

    @Test
    fun `normalize returns input as-is without cleaning`() =
        runTest {
            // Given: raw metadata with technical tags and messy formatting
            val raw =
                RawMediaMetadata(
                    originalTitle = "X-Men.2000.1080p.BluRay.x264-GROUP",
                    mediaType = MediaType.MOVIE,
                    year = 2000,
                    season = null,
                    episode = null,
                    durationMs = 104 * 60_000L,
                    externalIds = ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, 12345)),
                    sourceType = SourceType.IO,
                    sourceLabel = "Local Files",
                    sourceId = "file:///storage/movies/xmen.mkv",
                )

            // When: normalizing
            val normalized = normalizer.normalize(raw)

            // Then: output matches input (no cleaning)
            assertEquals(raw.originalTitle, normalized.canonicalTitle)
            assertEquals(raw.mediaType, normalized.mediaType)
            assertEquals(raw.year, normalized.year)
            assertEquals(raw.season, normalized.season)
            assertEquals(raw.episode, normalized.episode)
            assertEquals(raw.externalIds.tmdb, normalized.tmdb)
            assertEquals(raw.externalIds, normalized.externalIds)
        }

    @Test
    fun `normalize handles episode metadata`() =
        runTest {
            // Given: raw metadata for an episode
            val raw =
                RawMediaMetadata(
                    originalTitle = "Breaking.Bad.S01E01.Pilot.720p.HDTV.x264",
                    mediaType = MediaType.SERIES_EPISODE,
                    year = 2008,
                    season = 1,
                    episode = 1,
                    durationMs = 58 * 60_000L,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.TELEGRAM,
                    sourceLabel = "Telegram: TV Shows",
                    sourceId = "tg://message/12345",
                )

            // When: normalizing
            val normalized = normalizer.normalize(raw)

            // Then: output matches input
            assertEquals(raw.originalTitle, normalized.canonicalTitle)
            assertEquals(raw.mediaType, normalized.mediaType)
            assertEquals(raw.year, normalized.year)
            assertEquals(raw.season, normalized.season)
            assertEquals(raw.episode, normalized.episode)
        }

    @Test
    fun `normalize preserves TMDB ID from source`() =
        runTest {
            // Given: raw metadata with TMDB ID from Xtream
            val raw =
                RawMediaMetadata(
                    originalTitle = "The Matrix",
                    mediaType = MediaType.MOVIE,
                    year = 1999,
                    season = null,
                    episode = null,
                    durationMs = 136 * 60_000L,
                    externalIds = ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, 603), imdbId = "tt0133093"),
                    sourceType = SourceType.XTREAM,
                    sourceLabel = "Xtream: Premium IPTV",
                    sourceId = "xtream://vod/12345",
                )

            // When: normalizing
            val normalized = normalizer.normalize(raw)

            // Then: TMDB reference is preserved
            assertEquals(TmdbRef(TmdbMediaType.MOVIE, 603), normalized.tmdb)
            assertEquals("tt0133093", normalized.externalIds.imdbId)
        }

    @Test
    fun `normalize preserves mediaType from source`() =
        runTest {
            // Given: raw metadata with various media types
            val liveRaw =
                RawMediaMetadata(
                    originalTitle = "ESPN HD",
                    mediaType = MediaType.LIVE,
                    year = null,
                    season = null,
                    episode = null,
                    durationMs = null,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.XTREAM,
                    sourceLabel = "Xtream: Live TV",
                    sourceId = "xtream://live/999",
                )

            val clipRaw =
                RawMediaMetadata(
                    originalTitle = "Movie Trailer HD",
                    mediaType = MediaType.CLIP,
                    year = 2024,
                    season = null,
                    episode = null,
                    durationMs = 3 * 60_000L,
                    externalIds = ExternalIds(),
                    sourceType = SourceType.TELEGRAM,
                    sourceLabel = "Telegram: Clips",
                    sourceId = "tg://message/456",
                )

            // When: normalizing
            val liveNormalized = normalizer.normalize(liveRaw)
            val clipNormalized = normalizer.normalize(clipRaw)

            // Then: media types are preserved
            assertEquals(MediaType.LIVE, liveNormalized.mediaType)
            assertEquals(MediaType.CLIP, clipNormalized.mediaType)
        }
}
