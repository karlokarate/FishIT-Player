package com.fishit.player.core.metadata

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.ids.TmdbId
import com.fishit.player.infra.transport.tmdb.api.TmdbError
import com.fishit.player.infra.transport.tmdb.api.TmdbRequestParams
import com.fishit.player.infra.transport.tmdb.api.TmdbResult
import com.fishit.player.infra.transport.tmdb.testing.FakeTmdbGateway
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for DefaultTmdbMetadataResolver using FakeTmdbGateway.
 *
 * Tests verify:
 * - ID-first contract: only enriches when tmdbId exists
 * - No search: does not call gateway when tmdbId is missing
 * - Proper enrichment when gateway returns success
 * - Graceful handling when gateway returns errors
 */
class DefaultTmdbMetadataResolverTest {
    private lateinit var fakeGateway: FakeTmdbGateway
    private lateinit var resolver: DefaultTmdbMetadataResolver
    private val defaultParams = TmdbRequestParams(language = "en-US", region = null)

    @Before
    fun setup() {
        fakeGateway = FakeTmdbGateway()
        resolver = DefaultTmdbMetadataResolver(fakeGateway, defaultParams)
    }

    @Test
    fun `enrich returns input unmodified when no tmdbId`() =
        runTest {
            // Given: normalized metadata without TMDB ID
            val normalized =
                NormalizedMediaMetadata(
                    canonicalTitle = "X-Men",
                    year = 2000,
                    season = null,
                    episode = null,
                    tmdbId = null,
                    externalIds = ExternalIds(),
                )

            // When: enriching
            val enriched = resolver.enrich(normalized)

            // Then: output is unchanged
            assertEquals(normalized, enriched)
            // And: gateway was not called
            assertEquals(0, fakeGateway.getMovieDetailsCalls.size)
            assertEquals(0, fakeGateway.getTvDetailsCalls.size)
        }

    @Test
    fun `enrich fetches movie details when tmdbId exists`() =
        runTest {
            // Given: normalized metadata with TMDB ID
            val tmdbId = TmdbId(603)
            val normalized =
                NormalizedMediaMetadata(
                    canonicalTitle = "Raw Title",
                    year = null,
                    season = null,
                    episode = null,
                    tmdbId = tmdbId,
                    externalIds = ExternalIds(tmdbId = tmdbId),
                )

            // And: gateway returns movie details
            val movieDetails = FakeTmdbGateway.createFakeMovieDetails(id = 603, title = "The Matrix")
            fakeGateway.movieDetailsToReturn = TmdbResult.Ok(movieDetails)

            // When: enriching
            val enriched = resolver.enrich(normalized)

            // Then: canonical title is updated from TMDB
            assertEquals("The Matrix", enriched.canonicalTitle)
            // And: year is extracted from release date
            assertEquals(2024, enriched.year)
            // And: gateway was called
            assertEquals(1, fakeGateway.getMovieDetailsCalls.size)
            assertEquals(603, fakeGateway.getMovieDetailsCalls[0].first)
        }

    @Test
    fun `enrich fetches TV details when tmdbId exists and media is TV show`() =
        runTest {
            // Given: normalized TV show metadata with TMDB ID
            val tmdbId = TmdbId(1399)
            val normalized =
                NormalizedMediaMetadata(
                    canonicalTitle = "Raw Title",
                    year = null,
                    season = 1,
                    episode = 1,
                    tmdbId = tmdbId,
                    externalIds = ExternalIds(tmdbId = tmdbId),
                )

            // And: gateway returns TV details
            val tvDetails = FakeTmdbGateway.createFakeTvDetails(id = 1399, name = "Game of Thrones")
            fakeGateway.tvDetailsToReturn = TmdbResult.Ok(tvDetails)

            // When: enriching
            val enriched = resolver.enrich(normalized)

            // Then: canonical title is updated from TMDB
            assertEquals("Game of Thrones", enriched.canonicalTitle)
            // And: year is extracted from first air date
            assertEquals(2024, enriched.year)
            // And: gateway was called for TV, not movie
            assertEquals(0, fakeGateway.getMovieDetailsCalls.size)
            assertEquals(1, fakeGateway.getTvDetailsCalls.size)
        }

    @Test
    fun `enrich returns original when gateway returns error`() =
        runTest {
            // Given: normalized metadata with TMDB ID
            val tmdbId = TmdbId(999999)
            val normalized =
                NormalizedMediaMetadata(
                    canonicalTitle = "Original Title",
                    year = 2020,
                    season = null,
                    episode = null,
                    tmdbId = tmdbId,
                    externalIds = ExternalIds(tmdbId = tmdbId),
                )

            // And: gateway returns NotFound error
            fakeGateway.movieDetailsToReturn = TmdbResult.Err(TmdbError.NotFound)

            // When: enriching
            val enriched = resolver.enrich(normalized)

            // Then: original data is returned (graceful degradation)
            assertEquals(normalized, enriched)
            // And: gateway was called
            assertEquals(1, fakeGateway.getMovieDetailsCalls.size)
        }

    @Test
    fun `enrich handles network errors gracefully`() =
        runTest {
            // Given: metadata with TMDB ID
            val normalized =
                NormalizedMediaMetadata(
                    canonicalTitle = "Test",
                    year = 2020,
                    tmdbId = TmdbId(123),
                    externalIds = ExternalIds(tmdbId = TmdbId(123)),
                )

            // And: gateway returns network error
            fakeGateway.movieDetailsToReturn = TmdbResult.Err(TmdbError.Network)

            // When: enriching
            val enriched = resolver.enrich(normalized)

            // Then: original data is preserved
            assertEquals(normalized, enriched)
        }
}
