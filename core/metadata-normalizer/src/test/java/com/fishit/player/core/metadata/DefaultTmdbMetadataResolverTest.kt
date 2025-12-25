package com.fishit.player.core.metadata

import com.fishit.player.core.metadata.tmdb.TmdbConfig
import com.fishit.player.core.metadata.tmdb.TmdbConfigProvider
import com.fishit.player.core.model.ExternalIds
import com.fishit.player.core.model.NormalizedMediaMetadata
import com.fishit.player.core.model.TmdbMediaType
import com.fishit.player.core.model.TmdbRef
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for DefaultTmdbMetadataResolver.
 *
 * Per TMDB_ENRICHMENT_CONTRACT.md:
 * - Resolver is enrichment-only, never mutates SourceType/canonicalTitle
 * - Uses typed TmdbRef (MOVIE or TV)
 * - Details-by-ID when tmdbRef exists, search+score otherwise
 *
 * These tests verify behavior with TMDB API disabled (no API key).
 * Full integration tests would require a real API key.
 */
class DefaultTmdbMetadataResolverTest {
    /** Config provider that returns DISABLED config (no API key) */
    private val disabledConfigProvider =
        object : TmdbConfigProvider {
            override fun getConfig(): TmdbConfig = TmdbConfig.DISABLED
        }

    private val resolver = DefaultTmdbMetadataResolver(disabledConfigProvider)

    @Test
    fun `enrich returns input unmodified when API key is blank`() =
        runTest {
            // Given: normalized metadata (API key is blank)
            val normalized =
                NormalizedMediaMetadata(
                    canonicalTitle = "X-Men",
                    year = 2000,
                    season = null,
                    episode = null,
                    tmdb = null,
                    externalIds = ExternalIds(),
                )

            // When: enriching with disabled config
            val enriched = resolver.enrich(normalized)

            // Then: output is unchanged (no API calls made)
            assertEquals(normalized.canonicalTitle, enriched.canonicalTitle)
            assertEquals(normalized.year, enriched.year)
            assertEquals(null, enriched.tmdb)
        }

    @Test
    fun `enrich preserves existing TmdbRef when API disabled`() =
        runTest {
            // Given: normalized metadata WITH existing TMDB ref
            val existingRef = TmdbRef(TmdbMediaType.MOVIE, 603)
            val normalized =
                NormalizedMediaMetadata(
                    canonicalTitle = "The Matrix",
                    year = 1999,
                    season = null,
                    episode = null,
                    tmdb = existingRef,
                    externalIds = ExternalIds(),
                )

            // When: enriching
            val enriched = resolver.enrich(normalized)

            // Then: existing TmdbRef is preserved
            assertEquals(existingRef, enriched.tmdb)
        }

    @Test
    fun `enrich does not perform TMDB lookup when API key is blank`() =
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

            // Then: TMDB ref remains null (no lookup performed, API disabled)
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
