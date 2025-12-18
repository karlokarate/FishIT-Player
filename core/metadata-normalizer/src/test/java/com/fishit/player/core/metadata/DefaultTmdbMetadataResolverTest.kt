package com.fishit.player.core.metadata

import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
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
                    tmdb = null,
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
                    tmdb = null, // No TMDB ref
                    externalIds = ExternalIds(),
                )

            // When: enriching
            val enriched = resolver.enrich(normalized)

            // Then: TMDB ref remains null (no lookup performed)
            assertEquals(null, enriched.tmdb)
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
                    tmdb = TmdbRef(TmdbMediaType.MOVIE, 603),
                    externalIds = ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, 603)),
                )

            // When: enriching
            val enriched = resolver.enrich(normalized)

            // Then: TMDB ref is preserved
            assertEquals(TmdbRef(TmdbMediaType.MOVIE, 603), enriched.tmdb)
            assertEquals(normalized, enriched)
        }
}
