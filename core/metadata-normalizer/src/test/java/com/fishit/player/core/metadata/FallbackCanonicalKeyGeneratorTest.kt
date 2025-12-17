package com.fishit.player.core.metadata

import com.fishit.player.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FallbackCanonicalKeyGeneratorTest {
    @Test
    fun `generates movie fallback key with year`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "Fight Club 1080p",
                        year = 1999,
                        season = null,
                        episode = null,
                        mediaType = MediaType.MOVIE,
                )

        assertEquals("movie:fight-club:1999", key?.value)
    }

    @Test
    fun `generates movie fallback key without year`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "Inception",
                        year = null,
                        season = null,
                        episode = null,
                        mediaType = MediaType.MOVIE,
                )

        assertEquals("movie:inception", key?.value)
    }

    @Test
    fun `generates episode fallback key`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "Breaking Bad",
                        year = 2008,
                        season = 5,
                        episode = 16,
                        mediaType = MediaType.SERIES_EPISODE,
                )

        assertEquals("episode:breaking-bad:S05E16", key?.value)
    }

    @Test
    fun `returns null when episode numbers missing`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "Breaking Bad",
                        year = 2008,
                        season = null,
                        episode = null,
                        mediaType = MediaType.SERIES_EPISODE,
                )

        assertNull(key)
    }

    @Test
    fun `returns null for live media`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "Live News",
                        year = null,
                        season = null,
                        episode = null,
                        mediaType = MediaType.LIVE,
                )

        assertNull(key)
    }
}
