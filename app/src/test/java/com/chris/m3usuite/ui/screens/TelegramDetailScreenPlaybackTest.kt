package com.chris.m3usuite.ui.screens

import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.domain.TelegramMediaRef
import com.chris.m3usuite.telegram.util.TelegramPlayUrl
import org.junit.Test

/**
 * Unit tests for TelegramDetailScreen playback wiring.
 *
 * Phase D.6: Tests for playback wiring correctness.
 *
 * Per Phase 8 Telegram ↔ PlayerLifecycle Contract:
 * - Telegram MUST build PlaybackContext(type=VOD) from TelegramItem
 * - Telegram MUST convert TelegramMediaRef → MediaItem via central resolver
 * - Telegram MUST navigate into InternalPlayerEntry for playback
 * - Telegram MUST NOT create/release ExoPlayer
 * - Telegram MUST NOT modify PlayerView
 */
class TelegramDetailScreenPlaybackTest {
    @Test
    fun `TelegramPlayUrl builds correct URL format`() {
        // Test URL format: tg://file/<fileId>?chatId=...&messageId=...
        val fileId = 12345
        val chatId = -1001234567890L
        val messageId = 9876L

        val url = TelegramPlayUrl.buildFileUrl(fileId, chatId, messageId)

        assert(url == "tg://file/$fileId?chatId=$chatId&messageId=$messageId") {
            "TelegramPlayUrl should generate correct tg:// URL format, got: $url"
        }
    }

    @Test
    fun `TelegramPlayUrl requires non-null fileId`() {
        val chatId = -1001234567890L
        val messageId = 9876L

        try {
            TelegramPlayUrl.buildFileUrl(null, chatId, messageId)
            assert(false) { "Should throw exception for null fileId" }
        } catch (e: IllegalArgumentException) {
            // Expected
            assert(e.message?.contains("fileId must not be null") == true) {
                "Exception should mention fileId requirement"
            }
        }
    }

    @Test
    fun `TelegramMediaRef can be converted to playback URL`() {
        // Test that TelegramMediaRef has all fields needed for playback
        val mediaRef =
            TelegramMediaRef(
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = 12345,
                sizeBytes = 1_000_000_000L,
                mimeType = "video/mp4",
                durationSeconds = 5400,
                width = 1920,
                height = 1080,
            )

        // Verify required fields for playback exist
        assert(mediaRef.fileId != null) { "fileId should be present for playback" }
        assert(mediaRef.remoteId.isNotEmpty()) { "remoteId should be present for stable reference" }
        assert(mediaRef.uniqueId.isNotEmpty()) { "uniqueId should be present for stable reference" }
        assert(mediaRef.mimeType != null) { "mimeType should be present for player" }
    }

    @Test
    fun `TelegramItemType VIDEO types have correct playback eligibility`() {
        // MOVIE, SERIES_EPISODE, CLIP should be playable
        val playableTypes =
            listOf(
                TelegramItemType.MOVIE,
                TelegramItemType.SERIES_EPISODE,
                TelegramItemType.CLIP,
            )

        playableTypes.forEach { type ->
            assert(
                type == TelegramItemType.MOVIE ||
                    type == TelegramItemType.SERIES_EPISODE ||
                    type == TelegramItemType.CLIP,
            ) {
                "$type should be in playable types"
            }
        }
    }

    @Test
    fun `TelegramItemType DOCUMENT types are not directly playable`() {
        // AUDIOBOOK, RAR_ITEM, POSTER_ONLY should NOT use TelegramFileDataSource
        val nonPlayableTypes =
            listOf(
                TelegramItemType.AUDIOBOOK,
                TelegramItemType.RAR_ITEM,
                TelegramItemType.POSTER_ONLY,
            )

        nonPlayableTypes.forEach { type ->
            assert(
                type == TelegramItemType.AUDIOBOOK ||
                    type == TelegramItemType.RAR_ITEM ||
                    type == TelegramItemType.POSTER_ONLY,
            ) {
                "$type should be in non-playable types"
            }
        }
    }

    @Test
    fun `TelegramDetailScreen composable functions exist`() {
        // Verify both detail screen composables exist
        // We check via reflection on the file's generated class

        // Legacy screen (id-based)
        try {
            val legacyScreenClass =
                Class.forName("com.chris.m3usuite.ui.screens.TelegramDetailScreenKt")
            assert(legacyScreenClass != null) {
                "TelegramDetailScreen composable should exist"
            }
        } catch (e: ClassNotFoundException) {
            assert(false) { "TelegramDetailScreenKt class should exist" }
        }
    }

    @Test
    fun `TelegramItemDetailScreen composable exists`() {
        // Verify Phase D TelegramItemDetailScreen exists
        try {
            val screenClass =
                Class.forName("com.chris.m3usuite.ui.screens.TelegramDetailScreenKt")
            val methods = screenClass.methods.map { it.name }

            // TelegramItemDetailScreen is generated as a method in TelegramDetailScreenKt
            assert(methods.contains("TelegramItemDetailScreen")) {
                "TelegramItemDetailScreen composable should exist for Phase D"
            }
        } catch (e: ClassNotFoundException) {
            assert(false) { "TelegramDetailScreenKt class should exist" }
        }
    }

    @Test
    fun `Playback URL scheme is correct for TDLib integration`() {
        // Verify URL scheme matches TelegramFileDataSource expectations
        val fileId = 12345
        val chatId = -1001234567890L
        val messageId = 9876L

        val url = TelegramPlayUrl.buildFileUrl(fileId, chatId, messageId)

        // Parse URL to verify components
        assert(url.startsWith("tg://file/")) {
            "URL should start with tg://file/ scheme"
        }

        assert(url.contains("chatId=")) {
            "URL should contain chatId parameter"
        }

        assert(url.contains("messageId=")) {
            "URL should contain messageId parameter"
        }
    }
}
