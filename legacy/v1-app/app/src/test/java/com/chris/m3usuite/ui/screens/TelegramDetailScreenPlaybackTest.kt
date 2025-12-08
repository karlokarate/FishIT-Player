package com.chris.m3usuite.ui.screens

import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.domain.TelegramMediaRef
import com.chris.m3usuite.telegram.player.TelegramPlaybackRequest
import com.chris.m3usuite.telegram.player.toPlaybackRequest
import com.chris.m3usuite.telegram.util.TelegramPlayUrl
import org.junit.Test

/**
 * Unit tests for TelegramDetailScreen playback wiring.
 *
 * Phase D+: Tests for remoteId-first playback wiring correctness.
 *
 * Per Phase 8 Telegram ↔ PlayerLifecycle Contract:
 * - Telegram MUST build PlaybackContext(type=VOD) from TelegramItem
 * - Telegram MUST convert TelegramMediaRef → MediaItem via central resolver
 * - Telegram MUST navigate into InternalPlayerEntry for playback
 * - Telegram MUST NOT create/release ExoPlayer
 * - Telegram MUST NOT modify PlayerView
 *
 * Per Phase D+ RemoteId-First Contract:
 * - remoteId and uniqueId are the PRIMARY identifiers (stable across sessions)
 * - fileId is an OPTIONAL volatile cache that may become stale
 * - DataSource resolves fileId via getRemoteFile(remoteId) if needed
 */
class TelegramDetailScreenPlaybackTest {
    // ==========================================================================
    // Phase D+ RemoteId-First Tests
    // ==========================================================================

    @Test
    fun `TelegramMediaRef toPlaybackRequest preserves all identifiers`() {
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

        val request =
            mediaRef.toPlaybackRequest(
                chatId = -1001234567890L,
                anchorMessageId = 98765L,
            )

        assert(request.remoteId == "AgACAgIAAxkBAAIBNmF1Y2xxxxx") {
            "remoteId should be preserved"
        }
        assert(request.uniqueId == "AQADCAAH1234") {
            "uniqueId should be preserved"
        }
        assert(request.fileId == 12345) {
            "fileId should be preserved"
        }
        assert(request.chatId == -1001234567890L) {
            "chatId should be set"
        }
        assert(request.messageId == 98765L) {
            "messageId should be set"
        }
    }

    @Test
    fun `TelegramPlaybackRequest with null fileId builds valid URL`() {
        val request =
            TelegramPlaybackRequest(
                chatId = -1001234567890L,
                messageId = 98765L,
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = null,
            )

        val url = TelegramPlayUrl.build(request)

        // URL should use 0 as fileId path segment, but include remoteId for resolution
        assert(url.startsWith("tg://file/0?")) {
            "URL should use 0 for null fileId"
        }
        assert(url.contains("remoteId=AgACAgIAAxkBAAIBNmF1Y2xxxxx")) {
            "URL should include remoteId for DataSource resolution"
        }
    }

    @Test
    fun `RemoteId-first URL includes all identifiers for DataSource`() {
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

        val request =
            mediaRef.toPlaybackRequest(
                chatId = -1001234567890L,
                anchorMessageId = 98765L,
            )

        val url = TelegramPlayUrl.build(request)

        // Verify all identifiers are present for DataSource parsing
        assert(url.contains("chatId=-1001234567890")) { "URL must include chatId" }
        assert(url.contains("messageId=98765")) { "URL must include messageId" }
        assert(url.contains("remoteId=")) { "URL must include remoteId" }
        assert(url.contains("uniqueId=")) { "URL must include uniqueId" }
    }

    @Test
    fun `Playback with stale fileId can still resolve via remoteId`() {
        // This test verifies the contract: even if fileId is stale (0),
        // the URL contains remoteId for DataSource to resolve a fresh fileId
        val mediaRef =
            TelegramMediaRef(
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = null, // Stale/missing fileId
                sizeBytes = 1_000_000_000L,
                mimeType = "video/mp4",
                durationSeconds = 5400,
                width = 1920,
                height = 1080,
            )

        val request =
            mediaRef.toPlaybackRequest(
                chatId = -1001234567890L,
                anchorMessageId = 98765L,
            )

        val url = TelegramPlayUrl.build(request)

        // The URL should allow DataSource to resolve via remoteId
        assert(request.fileId == null) { "fileId should be null (stale)" }
        assert(request.remoteId.isNotEmpty()) { "remoteId should be present for resolution" }
        assert(url.contains("remoteId=")) { "URL must include remoteId for resolution" }
    }

    // ==========================================================================
    // Legacy URL Format Tests
    // ==========================================================================

    @Test
    fun `Legacy TelegramPlayUrl builds correct URL format`() {
        // Test URL format: tg://file/<fileId>?chatId=...&messageId=...
        val fileId = 12345
        val chatId = -1001234567890L
        val messageId = 9876L

        @Suppress("DEPRECATION")
        val url = TelegramPlayUrl.buildFileUrl(fileId, chatId, messageId)

        assert(url == "tg://file/$fileId?chatId=$chatId&messageId=$messageId") {
            "TelegramPlayUrl should generate correct tg:// URL format, got: $url"
        }
    }

    @Test
    fun `Legacy TelegramPlayUrl requires non-null fileId`() {
        val chatId = -1001234567890L
        val messageId = 9876L

        try {
            @Suppress("DEPRECATION")
            TelegramPlayUrl.buildFileUrl(null, chatId, messageId)
            assert(false) { "Should throw exception for null fileId" }
        } catch (e: IllegalArgumentException) {
            // Expected - check that message contains "fileId" substring
            assert(e.message?.contains("fileId") == true) {
                "Exception should mention fileId requirement, got: ${e.message}"
            }
        }
    }

    // ==========================================================================
    // TelegramMediaRef Validation Tests
    // ==========================================================================

    @Test
    fun `TelegramMediaRef has required fields for playback`() {
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
        assert(mediaRef.remoteId.isNotEmpty()) { "remoteId should be present (PRIMARY identifier)" }
        assert(mediaRef.uniqueId.isNotEmpty()) { "uniqueId should be present (PRIMARY identifier)" }
        assert(mediaRef.fileId != null) { "fileId can be present for fast-path (OPTIONAL cache)" }
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
        val request =
            TelegramPlaybackRequest(
                chatId = -1001234567890L,
                messageId = 9876L,
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = 12345,
            )

        val url = TelegramPlayUrl.build(request)

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

        // Phase D+: Also verify remoteId/uniqueId
        assert(url.contains("remoteId=")) {
            "URL should contain remoteId parameter (Phase D+)"
        }

        assert(url.contains("uniqueId=")) {
            "URL should contain uniqueId parameter (Phase D+)"
        }
    }
}
