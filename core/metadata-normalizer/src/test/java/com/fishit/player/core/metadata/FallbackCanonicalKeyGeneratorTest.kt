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
    fun `generates episode fallback key with season and episode`() {
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
    fun `returns null for movie without year`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "Unknown Movie",
                        year = null,
                        season = null,
                        episode = null,
                        mediaType = MediaType.MOVIE,
                )

        assertNull(key)
    }

    @Test
    fun `returns null for LIVE content`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "CNN Live",
                        year = 2024,
                        season = null,
                        episode = null,
                        mediaType = MediaType.LIVE,
                )

        assertNull(key)
    }

    @Test
    fun `returns null for episode without season and episode numbers`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "Some Episode",
                        year = 2020,
                        season = null,
                        episode = null,
                        mediaType = MediaType.SERIES_EPISODE,
                )

        // Per contract: episodes without S/E numbers and movies without year cannot be
        // canonically identified, but we now have year, so this becomes a movie-style key
        assertEquals("movie:some-episode:2020", key?.value)
    }

    @Test
    fun `strips scene tags from title`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "The Matrix 1999 1080p BluRay x264 DTS-HD",
                        year = 1999,
                        season = null,
                        episode = null,
                        mediaType = MediaType.MOVIE,
                )

        assertEquals("movie:the-matrix-1999:1999", key?.value)
    }

    @Test
    fun `normalizes title with dots and underscores`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "The.Walking.Dead.S01E01.720p",
                        year = 2010,
                        season = 1,
                        episode = 1,
                        mediaType = MediaType.SERIES_EPISODE,
                )

        // S01E01 pattern in title should be stripped as a scene tag
        assertEquals("episode:the-walking-dead:S01E01", key?.value)
    }

    @Test
    fun `handles single-digit season and episode with zero padding`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "Game of Thrones",
                        year = 2011,
                        season = 1,
                        episode = 1,
                        mediaType = MediaType.SERIES_EPISODE,
                )

        assertEquals("episode:game-of-thrones:S01E01", key?.value)
    }

    @Test
    fun `handles double-digit season and episode`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "The Simpsons",
                        year = 1989,
                        season = 25,
                        episode = 12,
                        mediaType = MediaType.SERIES_EPISODE,
                )

        assertEquals("episode:the-simpsons:S25E12", key?.value)
    }

    @Test
    fun `returns null for UNKNOWN type without year`() {
        val key =
                FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                        originalTitle = "Random Content",
                        year = null,
                        season = null,
                        episode = null,
                        mediaType = MediaType.UNKNOWN,
                )

        assertNull(key)
    }
}
