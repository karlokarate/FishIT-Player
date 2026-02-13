package com.fishit.player.pipeline.telegram.golden

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.SourceType
import com.fishit.player.pipeline.telegram.model.TelegramBundleType
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.model.TelegramMediaType
import com.fishit.player.pipeline.telegram.model.TelegramTmdbType
import com.fishit.player.pipeline.telegram.model.toRawMediaMetadata
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Golden file tests for Telegram → RawMediaMetadata mapping.
 *
 * Verifies that [TelegramMediaItem.toRawMediaMetadata] produces deterministic output
 * matching expected golden JSON files under `test-data/golden/telegram/`.
 *
 * ## Running modes
 *
 * **Comparison mode** (default): Asserts current output matches golden files.
 * ```
 * ./gradlew :pipeline:telegram:testDebugUnitTest --tests "*TelegramGoldenFileTest*"
 * ```
 *
 * **Update mode**: Regenerates golden files from current mapper output.
 * ```
 * ./gradlew :pipeline:telegram:testDebugUnitTest --tests "*TelegramGoldenFileTest*" -Dgolden.update=true
 * ```
 *
 * ## Test coverage
 *
 * 1. Simple video (movie-like, no structured data)
 * 2. Series episode (isSeries + S/E markers)
 * 3. Structured Bundle (TMDB ID, rating, FSK, genres, director)
 * 4. Audio item (mediaType=AUDIO → MUSIC)
 * 5. Minimal item (fallback title "Untitled Media $messageId")
 * 6. Item with thumbnails (thumbRemoteId, minithumbnail)
 *
 * @see TelegramMediaItem.toRawMediaMetadata
 * @see RawMetadataJsonSerializer
 */
class TelegramGoldenFileTest {

    private val shouldUpdate: Boolean
        get() = System.getProperty("golden.update")?.toBoolean() == true

    private val goldenDir = File("test-data/golden/telegram")

    // =========================================================================
    // Test Fixtures
    // =========================================================================

    /**
     * Simple movie-like video with basic fields.
     * No structured bundle data, no thumbnails.
     * VIDEO → UNKNOWN (normalizer classifies).
     */
    private fun createSimpleVideoItem() = TelegramMediaItem(
        chatId = -1001234567890L,
        messageId = 42L,
        mediaType = TelegramMediaType.VIDEO,
        title = "Inception",
        remoteId = "BQACAgIAAxkDAAI_inception_remote",
        mimeType = "video/mp4",
        sizeBytes = 2_147_483_648L,
        durationSecs = 8880,
        width = 1920,
        height = 1080,
        supportsStreaming = true,
        fileName = "Inception.2010.1080p.BluRay.x264-GROUP.mkv",
        year = 2010,
        date = 1700000000L, // 2023-11-14T22:13:20Z
    )

    /**
     * Series episode with isSeries flag, season/episode numbers, series name.
     * Should produce mediaType=SERIES_EPISODE, sourceLabel="Telegram: Breaking Bad".
     */
    private fun createSeriesEpisodeItem() = TelegramMediaItem(
        chatId = -1009876543210L,
        messageId = 100L,
        mediaType = TelegramMediaType.VIDEO,
        title = "Breaking Bad S01E05 - Gray Matter",
        isSeries = true,
        seriesName = "Breaking Bad",
        seasonNumber = 1,
        episodeNumber = 5,
        episodeTitle = "Gray Matter",
        remoteId = "BQACAgIAAxkBBBI_bb_s01e05_remote",
        mimeType = "video/x-matroska",
        durationSecs = 2820,
        fileName = "Breaking.Bad.S01E05.720p.BluRay.mkv",
        date = 1695000000L, // 2023-09-18T06:40:00Z
    )

    /**
     * Structured Bundle item (FULL_3ER) with TMDB movie data.
     * Tests: externalIds with typed TmdbRef, structuredYear overrides year,
     * structuredLengthMinutes overrides durationSecs, rating, ageRating, genres, director.
     */
    private fun createStructuredBundleItem() = TelegramMediaItem(
        chatId = -1001111222333L,
        messageId = 200L,
        mediaType = TelegramMediaType.VIDEO,
        title = "Oppenheimer",
        year = 2022, // intentionally wrong — structuredYear overrides
        structuredTmdbId = 872585,
        structuredTmdbType = TelegramTmdbType.MOVIE,
        structuredRating = 8.1,
        structuredYear = 2023,
        structuredFsk = 12,
        structuredGenres = listOf("Drama", "History"),
        structuredDirector = "Christopher Nolan",
        structuredOriginalTitle = "Oppenheimer",
        structuredLengthMinutes = 181,
        bundleType = TelegramBundleType.FULL_3ER,
        textMessageId = 199L,
        photoMessageId = 198L,
        description = "The story of J. Robert Oppenheimer and the Manhattan Project.",
        remoteId = "BQACAgIAAxkCCCI_oppenheimer_remote",
        mimeType = "video/mp4",
        sizeBytes = 5_368_709_120L,
        durationSecs = 10800, // 3h in seconds — should be OVERRIDDEN by structuredLengthMinutes
        date = 1710000000L, // 2024-03-09T16:00:00Z
    )

    /**
     * Audio item — mediaType should map to MUSIC.
     */
    private fun createAudioItem() = TelegramMediaItem(
        chatId = -1005555666777L,
        messageId = 300L,
        mediaType = TelegramMediaType.AUDIO,
        title = "Bohemian Rhapsody",
        fileName = "Queen - Bohemian Rhapsody.mp3",
        mimeType = "audio/mpeg",
        sizeBytes = 8_500_000L,
        durationSecs = 355,
        date = 1680000000L, // 2023-03-28T16:00:00Z
    )

    /**
     * Minimal item with all defaults — tests fallback title "Untitled Media 777"
     * and null/default values for all optional fields.
     */
    private fun createMinimalItem() = TelegramMediaItem(
        chatId = 999L,
        messageId = 777L,
        mediaType = TelegramMediaType.VIDEO,
    )

    /**
     * Item with video thumbnail (thumbRemoteId) and minithumbnail (inline bytes).
     * Tests ImageRef.TelegramThumb and ImageRef.InlineBytes serialization.
     */
    private fun createItemWithThumbnails() = TelegramMediaItem(
        chatId = -1001234567890L,
        messageId = 500L,
        mediaType = TelegramMediaType.VIDEO,
        title = "Avatar",
        thumbRemoteId = "AAMCAgADGQEAAj_avatar_thumb_remote",
        thumbnailWidth = 320,
        thumbnailHeight = 180,
        minithumbnailBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        ),
        minithumbnailWidth = 40,
        minithumbnailHeight = 22,
        durationSecs = 9720,
        year = 2009,
        date = 1690000000L, // 2023-07-22T10:06:40Z
    )

    // =========================================================================
    // Golden File Tests
    // =========================================================================

    @Test
    fun `simple video maps to RawMediaMetadata correctly`() {
        val item = createSimpleVideoItem()
        val raw = item.toRawMediaMetadata()

        assertGolden("telegram_simple_video.json", raw)

        // Spot-check critical fields
        assertEquals("Inception", raw.originalTitle)
        assertEquals(MediaType.UNKNOWN, raw.mediaType) // VIDEO → UNKNOWN
        assertEquals("msg:-1001234567890:42", raw.sourceId)
        assertEquals(SourceType.TELEGRAM, raw.sourceType)
        assertEquals(PipelineIdTag.TELEGRAM, raw.pipelineIdTag)
        assertEquals(2010, raw.year)
        assertEquals(8_880_000L, raw.durationMs)
        assertEquals(1_700_000_000_000L, raw.addedTimestamp) // date * 1000
        assertNull(raw.poster)
        assertNull(raw.thumbnail)

        // Playback hints
        assertEquals("-1001234567890", raw.playbackHints[PlaybackHintKeys.Telegram.CHAT_ID])
        assertEquals("42", raw.playbackHints[PlaybackHintKeys.Telegram.MESSAGE_ID])
        assertEquals("BQACAgIAAxkDAAI_inception_remote", raw.playbackHints[PlaybackHintKeys.Telegram.REMOTE_ID])
        assertEquals("video/mp4", raw.playbackHints[PlaybackHintKeys.Telegram.MIME_TYPE])
    }

    @Test
    fun `series episode maps with correct type and season-episode`() {
        val item = createSeriesEpisodeItem()
        val raw = item.toRawMediaMetadata()

        assertGolden("telegram_series_episode.json", raw)

        // Spot checks
        assertEquals("Breaking Bad S01E05 - Gray Matter", raw.originalTitle)
        assertEquals(MediaType.SERIES_EPISODE, raw.mediaType)
        assertEquals(1, raw.season)
        assertEquals(5, raw.episode)
        assertEquals("Telegram: Breaking Bad", raw.sourceLabel)
        assertEquals("msg:-1009876543210:100", raw.sourceId)
        assertEquals(2_820_000L, raw.durationMs)
    }

    @Test
    fun `structured bundle maps TMDB and overrides correctly`() {
        val item = createStructuredBundleItem()
        val raw = item.toRawMediaMetadata()

        assertGolden("telegram_structured_bundle.json", raw)

        // structuredYear overrides year
        assertEquals(2023, raw.year)
        // structuredLengthMinutes overrides durationSecs
        assertEquals(181L * 60_000L, raw.durationMs) // 10,860,000 ms
        // ExternalIds
        assertEquals(872585, raw.externalIds.effectiveTmdbId)
        assertNotNull(raw.externalIds.tmdb)
        assertEquals(872585, raw.externalIds.tmdb!!.id)
        // Rating & age rating
        assertEquals(8.1, raw.rating)
        assertEquals(12, raw.ageRating)
        // Rich metadata
        assertEquals("Drama, History", raw.genres)
        assertEquals("Christopher Nolan", raw.director)
        assertEquals("The story of J. Robert Oppenheimer and the Manhattan Project.", raw.plot)
    }

    @Test
    fun `audio item maps to MUSIC media type`() {
        val item = createAudioItem()
        val raw = item.toRawMediaMetadata()

        assertGolden("telegram_audio.json", raw)

        assertEquals("Bohemian Rhapsody", raw.originalTitle)
        assertEquals(MediaType.MUSIC, raw.mediaType)
        assertEquals(355_000L, raw.durationMs)
        assertEquals("msg:-1005555666777:300", raw.sourceId)
    }

    @Test
    fun `minimal item uses fallback title and null fields`() {
        val item = createMinimalItem()
        val raw = item.toRawMediaMetadata()

        assertGolden("telegram_minimal.json", raw)

        assertEquals("Untitled Media 777", raw.originalTitle)
        assertEquals(MediaType.UNKNOWN, raw.mediaType)
        assertEquals("msg:999:777", raw.sourceId)
        assertNull(raw.year)
        assertNull(raw.durationMs)
        assertNull(raw.poster)
        assertNull(raw.thumbnail)
        assertNull(raw.addedTimestamp)
        assertNull(raw.rating)
        assertTrue(raw.playbackHints.containsKey(PlaybackHintKeys.Telegram.CHAT_ID))
        assertEquals("999", raw.playbackHints[PlaybackHintKeys.Telegram.CHAT_ID])
    }

    @Test
    fun `item with thumbnails produces correct ImageRef URIs`() {
        val item = createItemWithThumbnails()
        val raw = item.toRawMediaMetadata()

        assertGolden("telegram_with_thumbnails.json", raw)

        // Thumbnail: TelegramThumb from thumbRemoteId
        assertNotNull(raw.thumbnail, "Should have thumbnail from thumbRemoteId")
        assertTrue(
            raw.thumbnail!!.let { it is com.fishit.player.core.model.ImageRef.TelegramThumb },
            "Thumbnail should be TelegramThumb",
        )

        // Placeholder: InlineBytes from minithumbnailBytes
        assertNotNull(raw.placeholderThumbnail, "Should have placeholder from minithumbnail")
        assertTrue(
            raw.placeholderThumbnail!!.let { it is com.fishit.player.core.model.ImageRef.InlineBytes },
            "Placeholder should be InlineBytes",
        )

        // No poster (video items without photoSizes don't have poster)
        assertNull(raw.poster)
    }

    // =========================================================================
    // Golden file assertion helper
    // =========================================================================

    /**
     * Compare current mapper output against a golden file.
     *
     * In update mode (`-Dgolden.update=true`), writes the golden file instead of comparing.
     */
    private fun assertGolden(
        fileName: String,
        raw: com.fishit.player.core.model.RawMediaMetadata,
    ) {
        val actualJson = RawMetadataJsonSerializer.toJsonString(raw)
        val goldenFile = File(goldenDir, fileName)

        if (shouldUpdate) {
            goldenDir.mkdirs()
            goldenFile.writeText(actualJson + "\n")
            println("Updated golden file: ${goldenFile.absolutePath}")
            return
        }

        assertTrue(goldenFile.exists(), "Golden file not found: ${goldenFile.absolutePath}. Run with -Dgolden.update=true to generate.")
        val expectedJson = goldenFile.readText().trim()
        val expected = RawMetadataJsonSerializer.parseGoldenFile(expectedJson)
        val actual = RawMetadataJsonSerializer.parseGoldenFile(actualJson)

        assertEquals(
            expected,
            actual,
            buildString {
                appendLine("Golden file mismatch: $fileName")
                appendLine("--- Expected ---")
                appendLine(expectedJson)
                appendLine("--- Actual ---")
                appendLine(actualJson)
            },
        )
    }
}
