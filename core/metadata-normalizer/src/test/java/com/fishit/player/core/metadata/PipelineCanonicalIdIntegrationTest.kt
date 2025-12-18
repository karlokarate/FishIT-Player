package com.fishit.player.core.metadata

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests verifying that both Telegram and Xtream pipelines produce correct 
 * CanonicalIds through the Normalizer for movies and series.
 *
 * This test suite validates the end-to-end flow:
 * Pipeline → RawMediaMetadata → Normalizer → NormalizedMedia with CanonicalId
 */
class PipelineCanonicalIdIntegrationTest {

    // ===================================================================================
    // TELEGRAM PIPELINE: Movies
    // ===================================================================================

    @Test
    fun `telegram movie with year produces correct canonicalId`() {
        // Simulates TelegramMediaItem.toRawMediaMetadata() output
        // Note: Scene tags in title produce slugs including the year,
        // so "Inception.2010..." becomes "inception-2010" + ":2010" suffix
        val raw = RawMediaMetadata(
            originalTitle = "Inception.2010.1080p.BluRay.x264",
            mediaType = MediaType.MOVIE,
            year = 2010,
            season = null,
            episode = null,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram: Movies HD",
            sourceId = "msg:123456:789",
            pipelineIdTag = PipelineIdTag.TELEGRAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertEquals(1, normalized.size)
        val entry = normalized.first()
        assertNotNull(entry.canonicalId, "Movie should have canonicalId")
        // Year from title becomes part of slug, plus year suffix
        assertEquals("movie:inception-2010:2010", entry.canonicalId?.value)
        assertEquals(MediaType.MOVIE, entry.mediaType)
    }

    @Test
    fun `telegram movie without year produces canonicalId without year suffix`() {
        val raw = RawMediaMetadata(
            originalTitle = "Fight Club 1080p BluRay",
            mediaType = MediaType.MOVIE,
            year = null,
            season = null,
            episode = null,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram: Movies",
            sourceId = "msg:111:222",
            pipelineIdTag = PipelineIdTag.TELEGRAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertEquals(1, normalized.size)
        assertNotNull(normalized.first().canonicalId)
        assertEquals("movie:fight-club", normalized.first().canonicalId?.value)
    }

    // ===================================================================================
    // TELEGRAM PIPELINE: Series Episodes
    // ===================================================================================

    @Test
    fun `telegram series episode produces correct canonicalId`() {
        // Simulates TelegramMediaItem with isSeries=true, seasonNumber, episodeNumber
        val raw = RawMediaMetadata(
            originalTitle = "Breaking Bad S05E16",
            mediaType = MediaType.SERIES_EPISODE,
            year = null,
            season = 5,
            episode = 16,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram: Series",
            sourceId = "msg:555:666",
            pipelineIdTag = PipelineIdTag.TELEGRAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertEquals(1, normalized.size)
        val entry = normalized.first()
        assertNotNull(entry.canonicalId, "Episode should have canonicalId")
        assertEquals("episode:breaking-bad-s05e16:S05E16", entry.canonicalId?.value)
        assertEquals(MediaType.SERIES_EPISODE, entry.mediaType)
    }

    @Test
    fun `telegram series episode with scene tags produces clean canonicalId`() {
        val raw = RawMediaMetadata(
            originalTitle = "Game.of.Thrones.S03E09.1080p.BluRay.x264-GROUP",
            mediaType = MediaType.SERIES_EPISODE,
            year = null,
            season = 3,
            episode = 9,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram: Series HD",
            sourceId = "msg:777:888",
            pipelineIdTag = PipelineIdTag.TELEGRAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertEquals(1, normalized.size)
        val entry = normalized.first()
        assertNotNull(entry.canonicalId)
        // Scene tags should be stripped during slug generation
        assertTrue(entry.canonicalId!!.value.startsWith("episode:"))
        assertTrue(entry.canonicalId!!.value.endsWith(":S03E09"))
    }

    @Test
    fun `telegram series episode without season and episode numbers produces null canonicalId`() {
        // Edge case: marked as SERIES_EPISODE but missing numbers
        val raw = RawMediaMetadata(
            originalTitle = "Some Series Episode",
            mediaType = MediaType.SERIES_EPISODE,
            year = null,
            season = null,
            episode = null,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram",
            sourceId = "msg:999:000",
            pipelineIdTag = PipelineIdTag.TELEGRAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertEquals(1, normalized.size)
        assertNull(normalized.first().canonicalId, "Episode without S/E numbers should have null canonicalId")
    }

    // ===================================================================================
    // XTREAM PIPELINE: Movies (VOD)
    // ===================================================================================

    @Test
    fun `xtream vod movie produces correct canonicalId`() {
        // Simulates XtreamVodItem.toRawMediaMetadata() output
        val raw = RawMediaMetadata(
            originalTitle = "The Matrix",
            mediaType = MediaType.MOVIE,
            year = 1999,
            season = null,
            episode = null,
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream VOD",
            sourceId = "xtream:vod:12345:mkv",
            pipelineIdTag = PipelineIdTag.XTREAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertEquals(1, normalized.size)
        val entry = normalized.first()
        assertNotNull(entry.canonicalId)
        assertEquals("movie:the-matrix:1999", entry.canonicalId?.value)
    }

    @Test
    fun `xtream vod movie without year produces canonicalId without year`() {
        val raw = RawMediaMetadata(
            originalTitle = "Pulp Fiction",
            mediaType = MediaType.MOVIE,
            year = null,
            season = null,
            episode = null,
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream VOD",
            sourceId = "xtream:vod:67890",
            pipelineIdTag = PipelineIdTag.XTREAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertEquals(1, normalized.size)
        assertEquals("movie:pulp-fiction", normalized.first().canonicalId?.value)
    }

    // ===================================================================================
    // XTREAM PIPELINE: Series Container
    // ===================================================================================

    @Test
    fun `xtream series container produces null canonicalId`() {
        // Series container (not episode) should not have canonicalId
        // Only episodes get linked
        val raw = RawMediaMetadata(
            originalTitle = "Stranger Things",
            mediaType = MediaType.SERIES, // Container, not episode
            year = 2016,
            season = null,
            episode = null,
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream Series",
            sourceId = "xtream:series:111",
            pipelineIdTag = PipelineIdTag.XTREAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertEquals(1, normalized.size)
        assertNull(normalized.first().canonicalId, "Series container should have null canonicalId")
    }

    // ===================================================================================
    // XTREAM PIPELINE: Series Episodes
    // ===================================================================================

    @Test
    fun `xtream episode produces correct canonicalId`() {
        // Simulates XtreamEpisode.toRawMediaMetadata() output
        val raw = RawMediaMetadata(
            originalTitle = "The One Where Everybody Finds Out",
            mediaType = MediaType.SERIES_EPISODE,
            year = null,
            season = 5,
            episode = 14,
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream: Friends",
            sourceId = "xtream:episode:222:mp4",
            pipelineIdTag = PipelineIdTag.XTREAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertEquals(1, normalized.size)
        val entry = normalized.first()
        assertNotNull(entry.canonicalId)
        assertEquals("episode:the-one-where-everybody-finds-out:S05E14", entry.canonicalId?.value)
    }

    @Test
    fun `xtream episode with single digit season and episode pads correctly`() {
        val raw = RawMediaMetadata(
            originalTitle = "Pilot",
            mediaType = MediaType.SERIES_EPISODE,
            year = null,
            season = 1,
            episode = 1,
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream: Lost",
            sourceId = "xtream:episode:333",
            pipelineIdTag = PipelineIdTag.XTREAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertEquals(1, normalized.size)
        // Season and episode should be zero-padded: S01E01
        assertTrue(normalized.first().canonicalId!!.value.endsWith(":S01E01"))
    }

    // ===================================================================================
    // CROSS-PIPELINE: Same Content from Different Sources
    // ===================================================================================

    @Test
    fun `same movie from telegram and xtream produces matching canonicalIds`() {
        val telegramRaw = RawMediaMetadata(
            originalTitle = "Interstellar.2014.1080p.BluRay",
            mediaType = MediaType.MOVIE,
            year = 2014,
            season = null,
            episode = null,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram: Movies",
            sourceId = "msg:aaa:bbb",
            pipelineIdTag = PipelineIdTag.TELEGRAM,
        )

        val xtreamRaw = RawMediaMetadata(
            originalTitle = "Interstellar",
            mediaType = MediaType.MOVIE,
            year = 2014,
            season = null,
            episode = null,
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream VOD",
            sourceId = "xtream:vod:444",
            pipelineIdTag = PipelineIdTag.XTREAM,
        )

        val telegramNormalized = Normalizer.normalize(listOf(telegramRaw))
        val xtreamNormalized = Normalizer.normalize(listOf(xtreamRaw))

        // Both should produce movie:interstellar:2014
        assertEquals(
            "movie:interstellar:2014",
            telegramNormalized.first().canonicalId?.value,
            "Telegram movie canonicalId mismatch"
        )
        assertEquals(
            "movie:interstellar:2014",
            xtreamNormalized.first().canonicalId?.value,
            "Xtream movie canonicalId mismatch"
        )
        assertEquals(
            telegramNormalized.first().canonicalId,
            xtreamNormalized.first().canonicalId,
            "Same movie from different sources should have matching canonicalIds"
        )
    }

    @Test
    fun `same episode from telegram and xtream produces matching canonicalIds`() {
        val telegramRaw = RawMediaMetadata(
            originalTitle = "The Rains of Castamere",
            mediaType = MediaType.SERIES_EPISODE,
            year = null,
            season = 3,
            episode = 9,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram: GoT",
            sourceId = "msg:ccc:ddd",
            pipelineIdTag = PipelineIdTag.TELEGRAM,
        )

        val xtreamRaw = RawMediaMetadata(
            originalTitle = "The Rains of Castamere",
            mediaType = MediaType.SERIES_EPISODE,
            year = null,
            season = 3,
            episode = 9,
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream: Game of Thrones",
            sourceId = "xtream:episode:555",
            pipelineIdTag = PipelineIdTag.XTREAM,
        )

        val telegramNormalized = Normalizer.normalize(listOf(telegramRaw))
        val xtreamNormalized = Normalizer.normalize(listOf(xtreamRaw))

        assertEquals(
            telegramNormalized.first().canonicalId,
            xtreamNormalized.first().canonicalId,
            "Same episode from different sources should have matching canonicalIds"
        )
        assertTrue(
            telegramNormalized.first().canonicalId!!.value.endsWith(":S03E09"),
            "CanonicalId should end with S03E09"
        )
    }

    // ===================================================================================
    // LIVE: Should Never Get CanonicalId
    // ===================================================================================

    @Test
    fun `telegram live channel produces null canonicalId`() {
        val raw = RawMediaMetadata(
            originalTitle = "RTL HD Live",
            mediaType = MediaType.LIVE,
            year = null,
            season = null,
            episode = null,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram Live",
            sourceId = "msg:live:123",
            pipelineIdTag = PipelineIdTag.TELEGRAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertNull(normalized.first().canonicalId, "Live content should never have canonicalId")
    }

    @Test
    fun `xtream live channel produces null canonicalId`() {
        val raw = RawMediaMetadata(
            originalTitle = "DE: ProSieben HD",
            mediaType = MediaType.LIVE,
            year = null,
            season = null,
            episode = null,
            sourceType = SourceType.XTREAM,
            sourceLabel = "Xtream Live",
            sourceId = "xtream:live:999",
            pipelineIdTag = PipelineIdTag.XTREAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertNull(normalized.first().canonicalId, "Live content should never have canonicalId")
    }

    // ===================================================================================
    // EDGE CASES: Title Cleaning
    // ===================================================================================

    @Test
    fun `movie with special characters produces valid slug`() {
        val raw = RawMediaMetadata(
            originalTitle = "Amélie (2001) [French]",
            mediaType = MediaType.MOVIE,
            year = 2001,
            season = null,
            episode = null,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram",
            sourceId = "msg:french:001",
            pipelineIdTag = PipelineIdTag.TELEGRAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertNotNull(normalized.first().canonicalId)
        // Special characters should be removed, spaces converted to hyphens
        val canonicalValue = normalized.first().canonicalId!!.value
        assertTrue(canonicalValue.startsWith("movie:"), "Should start with movie:")
        assertTrue(canonicalValue.endsWith(":2001"), "Should end with year")
    }

    @Test
    fun `episode with scene release format strips technical tags`() {
        val raw = RawMediaMetadata(
            originalTitle = "The.Office.US.S02E04.The.Fire.720p.WEB-DL.DD5.1.H.264-CtrlHD",
            mediaType = MediaType.SERIES_EPISODE,
            year = null,
            season = 2,
            episode = 4,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram: The Office",
            sourceId = "msg:office:004",
            pipelineIdTag = PipelineIdTag.TELEGRAM,
        )

        val normalized = Normalizer.normalize(listOf(raw))

        assertNotNull(normalized.first().canonicalId)
        val canonicalValue = normalized.first().canonicalId!!.value
        // Should strip 720p, WEB-DL, etc.
        assertTrue(!canonicalValue.contains("720p", ignoreCase = true))
        assertTrue(!canonicalValue.contains("web-dl", ignoreCase = true))
        assertTrue(canonicalValue.endsWith(":S02E04"))
    }
}
