package com.fishit.player.core.persistence.obx

import com.fishit.player.core.model.ids.TmdbId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for CanonicalKeyGenerator. */
class CanonicalKeyGeneratorTest {

    // ========== TMDB Key Generation ==========

    @Test
    fun `generates TMDB key correctly`() {
        val key = CanonicalKeyGenerator.fromTmdbId(TmdbId(550))
        assertEquals("tmdb:550", key)
    }

    @Test
    fun `generates TMDB key for TV show`() {
        val key = CanonicalKeyGenerator.fromTmdbId(TmdbId(1399))
        assertEquals("tmdb:1399", key)
    }

    // ========== Movie Key Generation ==========

    @Test
    fun `generates movie key with year`() {
        val key = CanonicalKeyGenerator.forMovie("Fight Club", 1999)
        assertEquals("movie:fight-club:1999", key)
    }

    @Test
    fun `generates movie key without year`() {
        val key = CanonicalKeyGenerator.forMovie("Fight Club", null)
        assertEquals("movie:fight-club", key)
    }

    @Test
    fun `normalizes title with special characters`() {
        val key = CanonicalKeyGenerator.forMovie("Spider-Man: No Way Home", 2021)
        assertEquals("movie:spider-man-no-way-home:2021", key)
    }

    @Test
    fun `normalizes title with German umlauts`() {
        val key = CanonicalKeyGenerator.forMovie("Mörderische Jagd", 2020)
        // Umlauts are stripped (not ASCII)
        assertEquals("movie:mrderische-jagd:2020", key)
    }

    @Test
    fun `handles multiple spaces`() {
        val key = CanonicalKeyGenerator.forMovie("The    Matrix", 1999)
        assertEquals("movie:the-matrix:1999", key)
    }

    @Test
    fun `handles leading and trailing spaces`() {
        val key = CanonicalKeyGenerator.forMovie("  The Matrix  ", 1999)
        assertEquals("movie:the-matrix:1999", key)
    }

    @Test
    fun `handles all uppercase`() {
        val key = CanonicalKeyGenerator.forMovie("THE MATRIX", 1999)
        assertEquals("movie:the-matrix:1999", key)
    }

    // ========== Episode Key Generation ==========

    @Test
    fun `generates episode key with single digit season and episode`() {
        val key = CanonicalKeyGenerator.forEpisode("Game of Thrones", 1, 1)
        assertEquals("episode:game-of-thrones:S01E01", key)
    }

    @Test
    fun `generates episode key with double digit season and episode`() {
        val key = CanonicalKeyGenerator.forEpisode("Breaking Bad", 5, 16)
        assertEquals("episode:breaking-bad:S05E16", key)
    }

    @Test
    fun `generates episode key with colon in title`() {
        val key = CanonicalKeyGenerator.forEpisode("Star Trek: Discovery", 2, 10)
        assertEquals("episode:star-trek-discovery:S02E10", key)
    }

    // ========== Kind Detection ==========

    @Test
    fun `detects movie kind from TMDB key`() {
        val kind = CanonicalKeyGenerator.kindFromKey("tmdb:550")
        assertEquals("movie", kind)
    }

    @Test
    fun `detects movie kind from movie key`() {
        val kind = CanonicalKeyGenerator.kindFromKey("movie:fight-club:1999")
        assertEquals("movie", kind)
    }

    @Test
    fun `detects episode kind from episode key`() {
        val kind = CanonicalKeyGenerator.kindFromKey("episode:breaking-bad:S05E16")
        assertEquals("episode", kind)
    }

    // ========== TMDB Detection ==========

    @Test
    fun `detects TMDB-based key`() {
        assertTrue(CanonicalKeyGenerator.isTmdbBased("tmdb:550"))
    }

    @Test
    fun `detects non-TMDB key`() {
        assertFalse(CanonicalKeyGenerator.isTmdbBased("movie:fight-club:1999"))
    }

    // ========== Edge Cases ==========

    @Test
    fun `handles empty title`() {
        val key = CanonicalKeyGenerator.forMovie("", 2020)
        assertEquals("movie::2020", key)
    }

    @Test
    fun `handles numeric title`() {
        val key = CanonicalKeyGenerator.forMovie("1917", 2019)
        assertEquals("movie:1917:2019", key)
    }

    @Test
    fun `handles title with only special characters`() {
        val key = CanonicalKeyGenerator.forMovie("!!!", 2020)
        assertEquals("movie::2020", key)
    }

    @Test
    fun `handles very long title`() {
        val longTitle = "A" + "a".repeat(200)
        val key = CanonicalKeyGenerator.forMovie(longTitle, 2020)
        assertTrue(key.startsWith("movie:"))
        assertTrue(key.endsWith(":2020"))
    }

    // ========== Determinism Tests ==========

    @Test
    fun `generates same key for same input`() {
        val key1 = CanonicalKeyGenerator.forMovie("Fight Club", 1999)
        val key2 = CanonicalKeyGenerator.forMovie("Fight Club", 1999)
        assertEquals(key1, key2)
    }

    @Test
    fun `generates same key regardless of case`() {
        val key1 = CanonicalKeyGenerator.forMovie("Fight Club", 1999)
        val key2 = CanonicalKeyGenerator.forMovie("FIGHT CLUB", 1999)
        val key3 = CanonicalKeyGenerator.forMovie("fight club", 1999)
        assertEquals(key1, key2)
        assertEquals(key2, key3)
    }

    @Test
    fun `generates different keys for different titles`() {
        val key1 = CanonicalKeyGenerator.forMovie("Fight Club", 1999)
        val key2 = CanonicalKeyGenerator.forMovie("The Matrix", 1999)
        assertTrue(key1 != key2)
    }

    @Test
    fun `generates different keys for different years`() {
        val key1 = CanonicalKeyGenerator.forMovie("Dune", 1984)
        val key2 = CanonicalKeyGenerator.forMovie("Dune", 2021)
        assertTrue(key1 != key2)
    }

    // ========== Real World Examples ==========

    @Test
    fun `handles real movie titles`() {
        val testCases =
                mapOf(
                        Pair("Inception", 2010) to "movie:inception:2010",
                        Pair("The Dark Knight", 2008) to "movie:the-dark-knight:2008",
                        Pair("Pulp Fiction", 1994) to "movie:pulp-fiction:1994",
                        Pair("The Lord of the Rings: The Fellowship of the Ring", 2001) to
                                "movie:the-lord-of-the-rings-the-fellowship-of-the-ring:2001",
                        Pair("WALL·E", 2008) to "movie:walle:2008",
                        Pair("Amélie", 2001) to "movie:amlie:2001",
                        Pair("Léon: The Professional", 1994) to "movie:lon-the-professional:1994",
                )

        testCases.forEach { (input, expected) ->
            val key = CanonicalKeyGenerator.forMovie(input.first, input.second)
            assertEquals("Failed for: ${input.first}", expected, key)
        }
    }

    @Test
    fun `handles real series titles`() {
        val testCases =
                mapOf(
                        Triple("Breaking Bad", 1, 1) to "episode:breaking-bad:S01E01",
                        Triple("Game of Thrones", 8, 6) to "episode:game-of-thrones:S08E06",
                        Triple("The Office (US)", 9, 23) to "episode:the-office-us:S09E23",
                        Triple("Stranger Things", 4, 9) to "episode:stranger-things:S04E09",
                        Triple("The Mandalorian", 3, 8) to "episode:the-mandalorian:S03E08",
                )

        testCases.forEach { (input, expected) ->
            val key = CanonicalKeyGenerator.forEpisode(input.first, input.second, input.third)
            assertEquals("Failed for: ${input.first}", expected, key)
        }
    }
}
