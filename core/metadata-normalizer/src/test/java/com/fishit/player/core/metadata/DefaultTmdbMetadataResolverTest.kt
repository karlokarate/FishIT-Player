package com.fishit.player.core.metadata

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.ids.TmdbId
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Tests for DefaultTmdbMetadataResolver.
 *
 * Phase 3 skeleton tests verify that the default implementation
 * is a no-op pass-through (no TMDB calls, no enrichment).
 */
class DefaultTmdbMetadataResolverTest {
    private val resolver = DefaultTmdbMetadataResolver()

    @Test
    fun `enrich returns input unmodified`() =
        runTest {
            // Given: normalized metadata
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

            // Then: output is the same instance (no modifications)
            assertSame(normalized, enriched)
        }

    @Test
    fun `enrich does not perform TMDB lookup`() =
        runTest {
            // Given: normalized metadata without TMDB ID
            val normalized =
                NormalizedMediaMetadata(
                    canonicalTitle = "The Matrix",
                    year = 1999,
                    season = null,
                    episode = null,
                    tmdbId = null, // No TMDB ID
                    externalIds = ExternalIds(),
                )

            // When: enriching
            val enriched = resolver.enrich(normalized)

            // Then: TMDB ID remains null (no lookup performed)
            assertEquals(null, enriched.tmdbId)
            assertEquals(normalized, enriched)
        }

    @Test
    fun `enrich preserves existing TMDB ID`() =
        runTest {
            // Given: normalized metadata with existing TMDB ID
            val normalized =
                NormalizedMediaMetadata(
                    canonicalTitle = "The Matrix",
                    year = 1999,
                    season = null,
                    episode = null,
                    tmdbId = TmdbId(603),
                    externalIds = ExternalIds(tmdbId = TmdbId(603)),
                )

            // When: enriching
            val enriched = resolver.enrich(normalized)

            // Then: TMDB ID is preserved
            assertEquals(TmdbId(603), enriched.tmdbId)
            assertEquals(normalized, enriched)
        }
}
