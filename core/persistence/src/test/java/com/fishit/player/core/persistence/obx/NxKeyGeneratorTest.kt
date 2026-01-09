package com.fishit.player.core.persistence.obx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for NxKeyGenerator.
 *
 * Verifies key format specifications from NX_SSOT_CONTRACT.md.
 */
class NxKeyGeneratorTest {
    // =========================================================================
    // Work Key Tests
    // =========================================================================

    @Test
    fun `workKey - movie generates correct format`() {
        val key = NxKeyGenerator.workKey(WorkType.MOVIE, "The Matrix", 1999)
        assertEquals("MOVIE:the-matrix:1999", key)
    }

    @Test
    fun `workKey - series generates correct format`() {
        val key = NxKeyGenerator.workKey(WorkType.SERIES, "Breaking Bad", 2008)
        assertEquals("SERIES:breaking-bad:2008", key)
    }

    @Test
    fun `workKey - episode includes season and episode`() {
        val key =
            NxKeyGenerator.workKey(
                workType = WorkType.EPISODE,
                title = "Breaking Bad",
                year = 2008,
                season = 1,
                episode = 5,
            )
        assertEquals("EPISODE:breaking-bad:2008:S01E05", key)
    }

    @Test
    fun `workKey - live uses LIVE instead of year`() {
        val key = NxKeyGenerator.workKey(WorkType.LIVE, "CNN Live")
        assertEquals("LIVE:cnn-live:LIVE", key)
    }

    @Test
    fun `workKey - unknown type with no year uses 0000`() {
        val key = NxKeyGenerator.workKey(WorkType.UNKNOWN, "Some Content")
        assertEquals("UNKNOWN:some-content:0000", key)
    }

    @Test
    fun `seriesKey - convenience method works`() {
        val key = NxKeyGenerator.seriesKey("Game of Thrones", 2011)
        assertEquals("SERIES:game-of-thrones:2011", key)
    }

    @Test
    fun `episodeKey - convenience method works`() {
        val key = NxKeyGenerator.episodeKey("Game of Thrones", 2011, 3, 9)
        assertEquals("EPISODE:game-of-thrones:2011:S03E09", key)
    }

    // =========================================================================
    // Authority Key Tests
    // =========================================================================

    @Test
    fun `authorityKey - generates correct format`() {
        val key = NxKeyGenerator.authorityKey("TMDB", "movie", "603")
        assertEquals("tmdb:movie:603", key)
    }

    @Test
    fun `tmdbKey - generates correct format`() {
        val key = NxKeyGenerator.tmdbKey("movie", 603)
        assertEquals("tmdb:movie:603", key)
    }

    @Test
    fun `imdbKey - generates correct format`() {
        val key = NxKeyGenerator.imdbKey("tt0133093")
        assertEquals("imdb:title:tt0133093", key)
    }

    @Test
    fun `tvdbKey - generates correct format`() {
        val key = NxKeyGenerator.tvdbKey(81189)
        assertEquals("tvdb:series:81189", key)
    }

    // =========================================================================
    // Source Key Tests
    // =========================================================================

    @Test
    fun `sourceKey - generates correct format`() {
        val key = NxKeyGenerator.sourceKey(SourceType.TELEGRAM, "acc123", "chat_msg_456")
        assertEquals("telegram:acc123:chat_msg_456", key)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sourceKey - throws when accountKey is blank (INV-13)`() {
        NxKeyGenerator.sourceKey(SourceType.TELEGRAM, "", "123")
    }

    @Test
    fun `telegramSourceKey - generates correct format`() {
        val key = NxKeyGenerator.telegramSourceKey("acc123", -1001234567890L, 42)
        assertEquals("telegram:acc123:-1001234567890_42", key)
    }

    @Test
    fun `xtreamSourceKey - generates correct format`() {
        val key = NxKeyGenerator.xtreamSourceKey("xtream_abc", "movie", 12345)
        assertEquals("xtream:xtream_abc:movie_12345", key)
    }

    @Test
    fun `localSourceKey - generates hash-based key`() {
        val key = NxKeyGenerator.localSourceKey("/storage/movies/movie.mp4")
        assertNotNull(key)
        assert(key.startsWith("local:local:"))
    }

    // =========================================================================
    // Variant Key Tests
    // =========================================================================

    @Test
    fun `variantKey - default quality and language`() {
        val sourceKey = "telegram:acc:123"
        val key = NxKeyGenerator.variantKey(sourceKey)
        assertEquals("telegram:acc:123#source:original", key)
    }

    @Test
    fun `variantKey - custom quality and language`() {
        val sourceKey = "telegram:acc:123"
        val key = NxKeyGenerator.variantKey(sourceKey, "1080p", "en")
        assertEquals("telegram:acc:123#1080p:en", key)
    }

    // =========================================================================
    // Category Key Tests
    // =========================================================================

    @Test
    fun `categoryKey - generates correct format`() {
        val key = NxKeyGenerator.categoryKey(SourceType.XTREAM, "acc123", "vod_movies")
        assertEquals("xtream:acc123:vod_movies", key)
    }

    // =========================================================================
    // Account Key Tests
    // =========================================================================

    @Test
    fun `accountKey - generates correct format`() {
        val key = NxKeyGenerator.accountKey(SourceType.TELEGRAM, "phonehash123")
        assertEquals("telegram:phonehash123", key)
    }

    @Test
    fun `telegramAccountKey - hashes phone number`() {
        val key = NxKeyGenerator.telegramAccountKey("+49123456789")
        assertNotNull(key)
        assert(key.startsWith("telegram:"))
    }

    @Test
    fun `xtreamAccountKey - hashes server and username`() {
        val key = NxKeyGenerator.xtreamAccountKey("http://server.com", "user123")
        assertNotNull(key)
        assert(key.startsWith("xtream:"))
    }

    // =========================================================================
    // Profile Key Tests
    // =========================================================================

    @Test
    fun `profileKey - generates correct format`() {
        val key = NxKeyGenerator.profileKey(ProfileType.MAIN, 0)
        assertEquals("profile:main:0", key)
    }

    @Test
    fun `profileKey - kids profile with index`() {
        val key = NxKeyGenerator.profileKey(ProfileType.KIDS, 1)
        assertEquals("profile:kids:1", key)
    }

    // =========================================================================
    // Slug Generation Tests
    // =========================================================================

    @Test
    fun `toSlug - basic title`() {
        assertEquals("the-matrix", NxKeyGenerator.toSlug("The Matrix"))
    }

    @Test
    fun `toSlug - special characters removed`() {
        assertEquals("star-wars-a-new-hope", NxKeyGenerator.toSlug("Star Wars: A New Hope!"))
    }

    @Test
    fun `toSlug - unicode normalized`() {
        assertEquals("cafe", NxKeyGenerator.toSlug("Café"))
    }

    @Test
    fun `toSlug - german umlauts`() {
        assertEquals("munchen", NxKeyGenerator.toSlug("München"))
    }

    @Test
    fun `toSlug - multiple spaces collapsed`() {
        assertEquals("the-matrix", NxKeyGenerator.toSlug("The   Matrix"))
    }

    @Test
    fun `toSlug - empty string returns untitled`() {
        assertEquals("untitled", NxKeyGenerator.toSlug(""))
    }

    @Test
    fun `toSlug - only special chars returns untitled`() {
        assertEquals("untitled", NxKeyGenerator.toSlug("!!!"))
    }

    // =========================================================================
    // Key Parsing Tests
    // =========================================================================

    @Test
    fun `parseWorkKey - parses movie key`() {
        val result = NxKeyGenerator.parseWorkKey("MOVIE:the-matrix:1999")
        assertNotNull(result)
        assertEquals(WorkType.MOVIE, result?.workType)
        assertEquals("the-matrix", result?.slug)
        assertEquals(1999, result?.year)
        assertNull(result?.season)
        assertNull(result?.episode)
    }

    @Test
    fun `parseWorkKey - parses episode key`() {
        val result = NxKeyGenerator.parseWorkKey("EPISODE:breaking-bad:2008:S01E05")
        assertNotNull(result)
        assertEquals(WorkType.EPISODE, result?.workType)
        assertEquals("breaking-bad", result?.slug)
        assertEquals(2008, result?.year)
        assertEquals(1, result?.season)
        assertEquals(5, result?.episode)
    }

    @Test
    fun `parseWorkKey - parses live key`() {
        val result = NxKeyGenerator.parseWorkKey("LIVE:cnn-live:LIVE")
        assertNotNull(result)
        assertEquals(WorkType.LIVE, result?.workType)
        assertEquals("cnn-live", result?.slug)
        assertNull(result?.year)
    }

    @Test
    fun `parseWorkKey - invalid key returns null`() {
        val result = NxKeyGenerator.parseWorkKey("invalid")
        assertNull(result)
    }

    @Test
    fun `parseSourceKey - parses telegram key`() {
        val result = NxKeyGenerator.parseSourceKey("telegram:acc123:-1001234567890_42")
        assertNotNull(result)
        assertEquals(SourceType.TELEGRAM, result?.sourceType)
        assertEquals("acc123", result?.accountKey)
        assertEquals("-1001234567890_42", result?.sourceId)
    }

    @Test
    fun `parseSourceKey - invalid key returns null`() {
        val result = NxKeyGenerator.parseSourceKey("invalid")
        assertNull(result)
    }
}
