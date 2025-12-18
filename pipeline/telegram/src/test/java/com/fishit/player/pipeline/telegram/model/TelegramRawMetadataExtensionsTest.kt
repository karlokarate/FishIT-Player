package com.fishit.player.pipeline.telegram.model

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.TmdbMediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for TelegramRawMetadataExtensions.
 *
 * Verifies that TelegramMediaItem correctly converts to RawMediaMetadata per
 * MEDIA_NORMALIZATION_CONTRACT.md requirements.
 */
class TelegramRawMetadataExtensionsTest {

        @Test
        fun `toRawMediaMetadata uses title as first priority`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "The Movie Title",
                                episodeTitle = "Episode Name",
                                caption = "Some caption",
                                fileName = "movie.mkv"
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals("The Movie Title", raw.originalTitle)
                assertEquals("", raw.globalId)
        }

        @Test
        fun `toRawMediaMetadata falls back to episodeTitle when title is blank`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "",
                                episodeTitle = "Episode Name",
                                caption = "Some caption",
                                fileName = "movie.mkv"
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals("Episode Name", raw.originalTitle)
        }

        @Test
        fun `toRawMediaMetadata falls back to caption when title and episodeTitle are blank`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "",
                                episodeTitle = null,
                                caption = "Some caption",
                                fileName = "movie.mkv"
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals("Some caption", raw.originalTitle)
        }

        @Test
        fun `toRawMediaMetadata falls back to fileName when others are blank`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "",
                                caption = "",
                                fileName = "Movie.2020.1080p.BluRay.x264-GROUP.mkv"
                        )

                val raw = item.toRawMediaMetadata()

                // Critical: fileName passed through AS-IS, no cleaning
                assertEquals("Movie.2020.1080p.BluRay.x264-GROUP.mkv", raw.originalTitle)
        }

        @Test
        fun `toRawMediaMetadata uses fallback for empty item`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 789L,
                                mediaType = TelegramMediaType.VIDEO
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals("Untitled Media 789", raw.originalTitle)
        }

        @Test
        fun `toRawMediaMetadata sets correct mediaType for series`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "Breaking Bad S01E05",
                                isSeries = true,
                                seasonNumber = 1,
                                episodeNumber = 5
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals(MediaType.SERIES_EPISODE, raw.mediaType)
                assertEquals(1, raw.season)
                assertEquals(5, raw.episode)
        }

        @Test
        fun `toRawMediaMetadata sets MOVIE for regular video`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "Some Movie"
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals(MediaType.MOVIE, raw.mediaType)
        }

        @Test
        fun `toRawMediaMetadata sets MUSIC for audio`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.AUDIO,
                                title = "Song Title"
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals(MediaType.MUSIC, raw.mediaType)
        }

        @Test
        fun `toRawMediaMetadata calculates durationMinutes from seconds`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "Movie",
                                durationSecs = 7200 // 2 hours
                        )

                val raw = item.toRawMediaMetadata()

                // 7200 secs = 7,200,000 ms
                assertEquals(7_200_000L, raw.durationMs)
        }

        @Test
        fun `toRawMediaMetadata uses remoteId as sourceId when available`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "Movie",
                                remoteId = "stable-remote-id-xyz"
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals("stable-remote-id-xyz", raw.sourceId)
        }

        @Test
        fun `toRawMediaMetadata builds sourceId from chat and message when no remoteId`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "Movie",
                                remoteId = null
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals("msg:123:456", raw.sourceId)
        }

        @Test
        fun `toRawMediaMetadata always has TELEGRAM sourceType`() {
                val item = TelegramMediaItem(chatId = 123L, messageId = 456L, title = "Test")

                val raw = item.toRawMediaMetadata()

                assertEquals(SourceType.TELEGRAM, raw.sourceType)
        }

        @Test
        fun `toRawMediaMetadata leaves externalIds empty`() {
                val item = TelegramMediaItem(chatId = 123L, messageId = 456L, title = "Test")

                val raw = item.toRawMediaMetadata()

                // Per contract: Telegram doesn't provide TMDB/IMDB/TVDB
                assertNull(raw.externalIds.tmdb)
                assertNull(raw.externalIds.imdbId)
                assertNull(raw.externalIds.tvdbId)
        }

        @Test
        fun `toRawMediaMetadata uses seriesName in sourceLabel when available`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                title = "Episode",
                                seriesName = "Breaking Bad"
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals("Telegram: Breaking Bad", raw.sourceLabel)
        }

        @Test
        fun `toRawMediaMetadata falls back to chatId in sourceLabel`() {
                val item = TelegramMediaItem(chatId = 123456789L, messageId = 456L, title = "Movie")

                val raw = item.toRawMediaMetadata()

                assertEquals("Telegram Chat: 123456789", raw.sourceLabel)
        }

        @Test
        fun `toRawMediaMetadata preserves year from source`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                title = "Movie",
                                year = 2020
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals(2020, raw.year)
        }

        // =======================================================================
        // CONTRACT TEST: GlobalId Isolation (MEDIA_NORMALIZATION_CONTRACT Section 2.1.1)
        // =======================================================================

        @Test
        fun `CONTRACT - all Telegram conversions leave globalId empty`() {
                // Per MEDIA_NORMALIZATION_CONTRACT.md Section 2.1.1:
                // Pipelines MUST leave globalId empty (""). Canonical identity is computed
                // centrally by :core:metadata-normalizer.

                val videoItem =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                title = "Video Title",
                                mediaType = TelegramMediaType.VIDEO,
                                year = 2020
                        )

                val photoItem =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 789L,
                                title = "Photo Title",
                                mediaType = TelegramMediaType.PHOTO
                        )

                val episodeItem =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 101L,
                                title = "Episode",
                                mediaType = TelegramMediaType.VIDEO,
                                seriesName = "Series",
                                seasonNumber = 1,
                                episodeNumber = 5
                        )

                // ALL conversions must leave globalId empty - normalizer owns this field
                assertEquals(
                        "",
                        videoItem.toRawMediaMetadata().globalId,
                        "Video globalId must be empty"
                )
                assertEquals(
                        "",
                        photoItem.toRawMediaMetadata().globalId,
                        "Photo globalId must be empty"
                )
                assertEquals(
                        "",
                        episodeItem.toRawMediaMetadata().globalId,
                        "Episode globalId must be empty"
                )
        }

        // ========== Structured Bundle Tests (Phase 2) ==========

        @Test
        fun `toRawMediaMetadata passes through typed structuredTmdb to externalIds`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "The Movie",
                                structuredTmdbId = 12345,
                                structuredTmdbType = TelegramTmdbType.MOVIE,
                                bundleType = TelegramBundleType.FULL_3ER,
                        )

                val raw = item.toRawMediaMetadata()

                // Per Gold Decision: Typed TmdbRef with MOVIE type
                assertEquals(12345, raw.externalIds.tmdb?.id)
                assertEquals(TmdbMediaType.MOVIE, raw.externalIds.tmdb?.type)
        }
        
        @Test
        fun `toRawMediaMetadata uses legacyTmdbId when type missing`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "The Movie",
                                structuredTmdbId = 12345,
                                // NO structuredTmdbType - legacy behavior
                                bundleType = TelegramBundleType.FULL_3ER,
                        )

                val raw = item.toRawMediaMetadata()

                // Typed TmdbRef is null when type is missing
                assertNull(raw.externalIds.tmdb)
                // Legacy ID is set for migration compatibility
                @Suppress("DEPRECATION")
                assertEquals(12345, raw.externalIds.legacyTmdbId)
        }

        @Test
        fun `toRawMediaMetadata passes through structuredRating to rating`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "The Movie",
                                structuredRating = 7.5,
                                bundleType = TelegramBundleType.FULL_3ER,
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals(7.5, raw.rating)
        }

        @Test
        fun `toRawMediaMetadata passes through structuredFsk to ageRating`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "The Movie",
                                structuredFsk = 16,
                                bundleType = TelegramBundleType.FULL_3ER,
                        )

                val raw = item.toRawMediaMetadata()

                assertEquals(16, raw.ageRating)
        }

        @Test
        fun `toRawMediaMetadata structuredYear overrides filename-parsed year`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "The Movie",
                                year = 2019, // From filename parsing
                                structuredYear = 2020, // From structured TEXT
                                bundleType = TelegramBundleType.FULL_3ER,
                        )

                val raw = item.toRawMediaMetadata()

                // structuredYear takes precedence
                assertEquals(2020, raw.year)
        }

        @Test
        fun `toRawMediaMetadata structuredLengthMinutes overrides durationSecs`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "The Movie",
                                durationSecs = 7200, // 120 minutes from video metadata
                                structuredLengthMinutes = 118, // Precise runtime from TEXT
                                bundleType = TelegramBundleType.FULL_3ER,
                        )

                val raw = item.toRawMediaMetadata()

                // structuredLengthMinutes (118 min) takes precedence → 118 * 60 * 1000 = 7,080,000 ms
                assertEquals(7_080_000L, raw.durationMs)
        }

        @Test
        fun `toRawMediaMetadata complete structured bundle has all fields`() {
                val item =
                        TelegramMediaItem(
                                chatId = -1001434421634L,
                                messageId = 388021760L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "Spaceballs (1987)",
                                fileName = "Spaceballs.1987.German.DL.1080p.BluRay.x264.mkv",
                                mimeType = "video/x-matroska",
                                durationSecs = 5880,
                                // Structured Bundle Fields
                                structuredTmdbId = 957,
                                structuredTmdbType = TelegramTmdbType.MOVIE, // Gold Decision: typed TMDB
                                structuredRating = 6.9,
                                structuredYear = 1987,
                                structuredFsk = 12,
                                structuredGenres = listOf("Comedy", "Science Fiction"),
                                structuredDirector = "Mel Brooks",
                                structuredOriginalTitle = "Spaceballs",
                                structuredProductionCountry = "US",
                                structuredLengthMinutes = 96,
                                bundleType = TelegramBundleType.FULL_3ER,
                                textMessageId = 387973120L,
                                photoMessageId = 387924480L,
                        )

                val raw = item.toRawMediaMetadata()

                // Verify all structured fields are passed through
                assertEquals(957, raw.externalIds.tmdb?.id)
                assertEquals(TmdbMediaType.MOVIE, raw.externalIds.tmdb?.type)
                assertEquals(6.9, raw.rating)
                assertEquals(1987, raw.year)
                assertEquals(12, raw.ageRating)
                // structuredLengthMinutes (96 min) → 96 * 60 * 1000 = 5,760,000 ms
                assertEquals(5_760_000L, raw.durationMs)
                assertEquals(SourceType.TELEGRAM, raw.sourceType)
        }

        @Test
        fun `toRawMediaMetadata SINGLE bundle has no externalIds`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                mediaType = TelegramMediaType.VIDEO,
                                title = "Some Video",
                                bundleType = TelegramBundleType.SINGLE,
                        )

                val raw = item.toRawMediaMetadata()

                assertNull(raw.externalIds.tmdb)
                assertNull(raw.rating)
                assertNull(raw.ageRating)
        }

        @Test
        fun `hasStructuredMetadata returns true when structured fields present`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                structuredTmdbId = 12345,
                        )

                assertEquals(true, item.hasStructuredMetadata())
        }

        @Test
        fun `hasStructuredMetadata returns false for SINGLE bundle`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                bundleType = TelegramBundleType.SINGLE,
                        )

                assertEquals(false, item.hasStructuredMetadata())
        }

        @Test
        fun `isFromCompleteBundle returns true for FULL_3ER`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                bundleType = TelegramBundleType.FULL_3ER,
                        )

                assertEquals(true, item.isFromCompleteBunde())
        }

        @Test
        fun `isFromCompleteBundle returns false for COMPACT_2ER`() {
                val item =
                        TelegramMediaItem(
                                chatId = 123L,
                                messageId = 456L,
                                bundleType = TelegramBundleType.COMPACT_2ER,
                        )

                assertEquals(false, item.isFromCompleteBunde())
        }
}
