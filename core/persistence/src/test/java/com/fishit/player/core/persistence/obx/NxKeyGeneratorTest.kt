package com.fishit.player.core.persistence.obx

import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
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
    fun `workKey - heuristic movie generates correct format`() {
        val key = NxKeyGenerator.workKey(WorkType.MOVIE, "The Matrix", 1999)
        assertEquals("movie:heuristic:matrix-1999", key)
    }

    @Test
    fun `workKey - tmdb movie uses tmdb authority`() {
        val key = NxKeyGenerator.workKey(WorkType.MOVIE, "The Matrix", 1999, tmdbId = 603)
        assertEquals("movie:tmdb:603", key)
    }

    @Test
    fun `workKey - heuristic series generates correct format`() {
        val key = NxKeyGenerator.workKey(WorkType.SERIES, "Breaking Bad", 2008)
        assertEquals("series:heuristic:breaking-bad-2008", key)
    }

    @Test
    fun `workKey - heuristic episode includes season and episode`() {
        val key =
            NxKeyGenerator.workKey(
                workType = WorkType.EPISODE,
                title = "Breaking Bad",
                year = 2008,
                season = 1,
                episode = 5,
            )
        assertEquals("episode:heuristic:breaking-bad-2008-s01e05", key)
    }

    @Test
    fun `workKey - tmdb episode uses tmdb id only`() {
        val key =
            NxKeyGenerator.workKey(
                workType = WorkType.EPISODE,
                title = "Breaking Bad",
                year = 2008,
                tmdbId = 67890,
                season = 1,
                episode = 5,
            )
        assertEquals("episode:tmdb:67890", key)
    }

    @Test
    fun `workKey - live omits year`() {
        val key = NxKeyGenerator.workKey(WorkType.LIVE_CHANNEL, "CNN Live")
        assertEquals("live_channel:heuristic:cnn-live", key)
    }

    @Test
    fun `workKey - unknown type with no year uses unknown`() {
        val key = NxKeyGenerator.workKey(WorkType.UNKNOWN, "Some Content")
        assertEquals("unknown:heuristic:some-content-unknown", key)
    }

    @Test
    fun `seriesKey - convenience method works`() {
        val key = NxKeyGenerator.seriesKey("Game of Thrones", 2011)
        assertEquals("series:heuristic:game-of-thrones-2011", key)
    }

    @Test
    fun `seriesKey - tmdb convenience method works`() {
        val key = NxKeyGenerator.seriesKey("Game of Thrones", 2011, tmdbId = 1399)
        assertEquals("series:tmdb:1399", key)
    }

    @Test
    fun `episodeKey - convenience method works`() {
        val key = NxKeyGenerator.episodeKey("Game of Thrones", 2011, season = 3, episode = 9)
        assertEquals("episode:heuristic:game-of-thrones-2011-s03e09", key)
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

    @Test
    fun `workTypeToTmdbNamespace - SERIES maps to tv`() {
        assertEquals("tv", NxKeyGenerator.workTypeToTmdbNamespace("SERIES"))
        assertEquals("tv", NxKeyGenerator.workTypeToTmdbNamespace("series"))
        assertEquals("tv", NxKeyGenerator.workTypeToTmdbNamespace("Series"))
    }

    @Test
    fun `workTypeToTmdbNamespace - MOVIE maps to movie`() {
        assertEquals("movie", NxKeyGenerator.workTypeToTmdbNamespace("MOVIE"))
    }

    @Test
    fun `workTypeToTmdbNamespace - EPISODE maps to episode`() {
        assertEquals("episode", NxKeyGenerator.workTypeToTmdbNamespace("EPISODE"))
    }

    // =========================================================================
    // Source Key Tests — REMOVED
    // =========================================================================
    // sourceKey building/parsing is SSOT of SourceKeyParser (infra/data-nx).
    // NxKeyGenerator no longer contains sourceKey generation methods.
    // See SourceKeyParserTest for source key tests.

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
        assertEquals("matrix", NxKeyGenerator.toSlug("The Matrix"))
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
        assertEquals("matrix", NxKeyGenerator.toSlug("The   Matrix"))
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
    fun `parseWorkKey - parses heuristic movie key`() {
        val result = NxKeyGenerator.parseWorkKey("movie:heuristic:the-matrix-1999")
        assertNotNull(result)
        assertEquals(WorkType.MOVIE, result?.workType)
        assertEquals("heuristic", result?.authority)
        assertNull(result?.tmdbId)
        assertEquals("the-matrix-1999", result?.id)
        assertEquals(1999, result?.year)
        assertNull(result?.season)
        assertNull(result?.episode)
    }

    @Test
    fun `parseWorkKey - parses tmdb movie key`() {
        val result = NxKeyGenerator.parseWorkKey("movie:tmdb:603")
        assertNotNull(result)
        assertEquals(WorkType.MOVIE, result?.workType)
        assertEquals("tmdb", result?.authority)
        assertEquals(603, result?.tmdbId)
        assertEquals("603", result?.id)
        assertNull(result?.year)
    }

    @Test
    fun `parseWorkKey - parses heuristic episode key with season`() {
        val result = NxKeyGenerator.parseWorkKey("episode:heuristic:breaking-bad-2008-s01e05")
        assertNotNull(result)
        assertEquals(WorkType.EPISODE, result?.workType)
        assertEquals("heuristic", result?.authority)
        assertEquals(2008, result?.year)
        assertEquals(1, result?.season)
        assertEquals(5, result?.episode)
    }

    @Test
    fun `parseWorkKey - parses live channel key`() {
        val result = NxKeyGenerator.parseWorkKey("live_channel:heuristic:cnn-live")
        assertNotNull(result)
        assertEquals(WorkType.LIVE_CHANNEL, result?.workType)
        assertEquals("heuristic", result?.authority)
        assertEquals("cnn-live", result?.id)
        assertNull(result?.year)
    }

    @Test
    fun `parseWorkKey - invalid key returns null`() {
        val result = NxKeyGenerator.parseWorkKey("invalid")
        assertNull(result)
    }

    // parseSourceKey tests REMOVED — sourceKey parsing is SSOT of SourceKeyParser (infra/data-nx)
}
