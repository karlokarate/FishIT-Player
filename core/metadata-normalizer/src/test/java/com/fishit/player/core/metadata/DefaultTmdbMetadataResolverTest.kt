package com.fishit.player.core.metadata

import com.fishit.player.core.metadata.tmdb.TmdbConfig
import com.fishit.player.core.metadata.tmdb.TmdbResolutionResult
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Tests for DefaultTmdbMetadataResolver.
 *
 * Note: Full API integration tests are deferred pending library access resolution.
 * See TMDB_LIBRARY_INTEGRATION_STATUS.md for details.
 */
class DefaultTmdbMetadataResolverTest {

    @Test
    fun `resolver returns Disabled when apiKey is blank`() = runTest {
        val config = TmdbConfig(apiKey = "")
        val resolver = DefaultTmdbMetadataResolver(config)

        val raw = RawMediaMetadata(
            originalTitle = "The Matrix",
            mediaType = MediaType.MOVIE,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Test",
            sourceId = "test-123"
        )

        val result = resolver.resolve(raw)
        assertTrue(result is TmdbResolutionResult.Disabled)
    }

    @Test
    fun `resolver returns Disabled when apiKey is present but library is not accessible`() = runTest {
        val config = TmdbConfig(apiKey = "test-api-key-123")
        val resolver = DefaultTmdbMetadataResolver(config)

        val raw = RawMediaMetadata(
            originalTitle = "The Matrix",
            mediaType = MediaType.MOVIE,
            year = 1999,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Test",
            sourceId = "test-123"
        )

        // Should return Disabled due to library access limitation
        val result = resolver.resolve(raw)
        assertTrue(result is TmdbResolutionResult.Disabled)
    }
}
