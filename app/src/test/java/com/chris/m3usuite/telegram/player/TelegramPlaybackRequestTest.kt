package com.chris.m3usuite.telegram.player

import com.chris.m3usuite.telegram.domain.TelegramMediaRef
import org.junit.Test

/**
 * Unit tests for TelegramPlaybackRequest and remoteId-first playback wiring.
 *
 * Per TELEGRAM_SIP_PLAYER_INTEGRATION.md Section 4.2:
 * - remoteId and uniqueId are the PRIMARY identifiers (REQUIRED)
 * - fileId is an OPTIONAL volatile cache that may become stale
 * - All playback URLs must include remoteId for DataSource resolution
 */
class TelegramPlaybackRequestTest {
    // ==========================================================================
    // TelegramPlaybackRequest Construction Tests
    // ==========================================================================

    @Test
    fun `TelegramPlaybackRequest requires non-empty remoteId`() {
        val request =
            TelegramPlaybackRequest(
                chatId = -1001234567890L,
                messageId = 98765L,
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = 12345,
            )

        assert(request.remoteId.isNotEmpty()) {
            "remoteId must be non-empty (REQUIRED per contract)"
        }
    }

    @Test
    fun `TelegramPlaybackRequest requires non-empty uniqueId`() {
        val request =
            TelegramPlaybackRequest(
                chatId = -1001234567890L,
                messageId = 98765L,
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = 12345,
            )

        assert(request.uniqueId.isNotEmpty()) {
            "uniqueId must be non-empty (REQUIRED per contract)"
        }
    }

    @Test
    fun `TelegramPlaybackRequest allows null fileId`() {
        val request =
            TelegramPlaybackRequest(
                chatId = -1001234567890L,
                messageId = 98765L,
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = null,
            )

        assert(request.fileId == null) {
            "fileId is OPTIONAL and may be null"
        }
    }

    @Test
    fun `TelegramPlaybackRequest preserves all fields`() {
        val request =
            TelegramPlaybackRequest(
                chatId = -1001234567890L,
                messageId = 98765L,
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = 12345,
            )

        assert(request.chatId == -1001234567890L) { "chatId should be preserved" }
        assert(request.messageId == 98765L) { "messageId should be preserved" }
        assert(request.remoteId == "AgACAgIAAxkBAAIBNmF1Y2xxxxx") { "remoteId should be preserved" }
        assert(request.uniqueId == "AQADCAAH1234") { "uniqueId should be preserved" }
        assert(request.fileId == 12345) { "fileId should be preserved" }
    }

    // ==========================================================================
    // TelegramMediaRef.toPlaybackRequest() Extension Tests
    // ==========================================================================

    @Test
    fun `toPlaybackRequest maps TelegramMediaRef correctly`() {
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

        // Verify remoteId and uniqueId are correctly mapped (PRIMARY identifiers)
        assert(request.remoteId == mediaRef.remoteId) {
            "remoteId must be mapped from TelegramMediaRef"
        }
        assert(request.uniqueId == mediaRef.uniqueId) {
            "uniqueId must be mapped from TelegramMediaRef"
        }

        // Verify fileId is correctly mapped (OPTIONAL cache)
        assert(request.fileId == mediaRef.fileId) {
            "fileId must be mapped from TelegramMediaRef"
        }

        // Verify context parameters are set
        assert(request.chatId == -1001234567890L) {
            "chatId must be set from parameter"
        }
        assert(request.messageId == 98765L) {
            "messageId must be set from anchorMessageId parameter"
        }
    }

    @Test
    fun `toPlaybackRequest handles null fileId`() {
        val mediaRef =
            TelegramMediaRef(
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = null, // Optional cache - may be null
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

        assert(request.fileId == null) {
            "Null fileId should be preserved - DataSource will resolve via remoteId"
        }
        assert(request.remoteId.isNotEmpty()) {
            "remoteId must still be present for resolution"
        }
    }

    @Test
    fun `toPlaybackRequest preserves video metadata independently`() {
        // Note: TelegramPlaybackRequest does not include video metadata (size, mime, etc.)
        // Those are handled by the DataSource via TDLib file info
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

        // The request only needs identifiers for playback - metadata is separate
        assert(request.remoteId.isNotEmpty()) { "remoteId is the key for TDLib resolution" }
        assert(request.uniqueId.isNotEmpty()) { "uniqueId is the key for cache validation" }

        // Video metadata (size, mime, duration) is NOT part of the playback request
        // It's obtained via TDLib getFile() during DataSource.open()
    }

    // ==========================================================================
    // Edge Cases
    // ==========================================================================

    @Test
    fun `toPlaybackRequest with negative chatId`() {
        // Telegram group/channel chat IDs are typically negative
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

        assert(request.chatId < 0) {
            "Negative chatId (group/channel) should be preserved"
        }
    }

    @Test
    fun `toPlaybackRequest with positive chatId`() {
        // Telegram private chat IDs are positive
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
                chatId = 123456789L,
                anchorMessageId = 98765L,
            )

        assert(request.chatId > 0) {
            "Positive chatId (private chat) should be preserved"
        }
    }

    @Test
    fun `TelegramPlaybackRequest data class equals and hashCode`() {
        val request1 =
            TelegramPlaybackRequest(
                chatId = -1001234567890L,
                messageId = 98765L,
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = 12345,
            )

        val request2 =
            TelegramPlaybackRequest(
                chatId = -1001234567890L,
                messageId = 98765L,
                remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
                uniqueId = "AQADCAAH1234",
                fileId = 12345,
            )

        val request3 =
            TelegramPlaybackRequest(
                chatId = -1001234567890L,
                messageId = 98765L,
                remoteId = "DIFFERENT_REMOTE_ID",
                uniqueId = "AQADCAAH1234",
                fileId = 12345,
            )

        assert(request1 == request2) { "Identical requests should be equal" }
        assert(request1.hashCode() == request2.hashCode()) { "Identical requests should have same hashCode" }
        assert(request1 != request3) { "Requests with different remoteId should not be equal" }
    }
}
