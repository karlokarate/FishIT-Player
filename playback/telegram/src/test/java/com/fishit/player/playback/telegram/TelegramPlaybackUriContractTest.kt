package com.fishit.player.playback.telegram

import com.fishit.player.core.model.PlaybackHintKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TelegramPlaybackUriContract] - the SSOT for Telegram URI building/validation.
 *
 * Tests cover:
 * - URI building with all fields
 * - URI building from extras (playbackHints)
 * - URI parsing
 * - Validation (valid and invalid cases)
 */
class TelegramPlaybackUriContractTest {

    // ==================== buildUri Tests ====================

    @Test
    fun `buildUri with all fields creates valid URI`() {
        val uri = TelegramPlaybackUriContract.buildUri(
            fileId = 123,
            chatId = -1001234567890,
            messageId = 42,
            remoteId = "AgACAgIAAxkBAAI...",
            mimeType = "video/mp4",
        )

        assertEquals("tg://file/123?chatId=-1001234567890&messageId=42&remoteId=AgACAgIAAxkBAAI...&mimeType=video%2Fmp4", uri)
    }

    @Test
    fun `buildUri with zero fileId but valid remoteId creates valid URI`() {
        val uri = TelegramPlaybackUriContract.buildUri(
            fileId = 0,
            chatId = -1001234567890,
            messageId = 42,
            remoteId = "AgACAgIAAxkBAAI...",
            mimeType = null,
        )

        assertEquals("tg://file/0?chatId=-1001234567890&messageId=42&remoteId=AgACAgIAAxkBAAI...", uri)
    }

    @Test
    fun `buildUri with fileId but no remoteId creates valid URI`() {
        val uri = TelegramPlaybackUriContract.buildUri(
            fileId = 999,
            chatId = -1001234567890,
            messageId = 42,
            remoteId = null,
            mimeType = "video/mp4",
        )

        assertEquals("tg://file/999?chatId=-1001234567890&messageId=42&mimeType=video%2Fmp4", uri)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildUri without chatId throws`() {
        TelegramPlaybackUriContract.buildUri(
            fileId = 123,
            chatId = null,
            messageId = 42,
            remoteId = "AgACAgIAAxkBAAI...",
            mimeType = null,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildUri without messageId throws`() {
        TelegramPlaybackUriContract.buildUri(
            fileId = 123,
            chatId = -1001234567890,
            messageId = null,
            remoteId = "AgACAgIAAxkBAAI...",
            mimeType = null,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildUri without fileId and remoteId throws`() {
        TelegramPlaybackUriContract.buildUri(
            fileId = 0,
            chatId = -1001234567890,
            messageId = 42,
            remoteId = null,
            mimeType = null,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildUri with blank remoteId and zero fileId throws`() {
        TelegramPlaybackUriContract.buildUri(
            fileId = 0,
            chatId = -1001234567890,
            messageId = 42,
            remoteId = "   ",
            mimeType = null,
        )
    }

    // ==================== buildUriFromExtras Tests ====================

    @Test
    fun `buildUriFromExtras with standard keys creates valid URI`() {
        val extras = mapOf(
            PlaybackHintKeys.Telegram.CHAT_ID to "-1001234567890",
            PlaybackHintKeys.Telegram.MESSAGE_ID to "42",
            PlaybackHintKeys.Telegram.REMOTE_ID to "AgACAgIAAxkBAAI...",
            PlaybackHintKeys.Telegram.MIME_TYPE to "video/mp4",
        )

        val uri = TelegramPlaybackUriContract.buildUriFromExtras(extras, fileId = 123)

        assertTrue(uri.contains("chatId=-1001234567890"))
        assertTrue(uri.contains("messageId=42"))
        assertTrue(uri.contains("remoteId=AgACAgIAAxkBAAI..."))
    }

    @Test
    fun `buildUriFromExtras with legacy keys creates valid URI`() {
        val extras = mapOf(
            "chatId" to "-1001234567890",
            "messageId" to "42",
            "remoteId" to "AgACAgIAAxkBAAI...",
        )

        val uri = TelegramPlaybackUriContract.buildUriFromExtras(extras, fileId = 0)

        assertTrue(uri.contains("chatId=-1001234567890"))
        assertTrue(uri.contains("messageId=42"))
        assertTrue(uri.contains("remoteId=AgACAgIAAxkBAAI..."))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildUriFromExtras without required fields throws`() {
        val extras = mapOf(
            PlaybackHintKeys.Telegram.MIME_TYPE to "video/mp4",
        )

        TelegramPlaybackUriContract.buildUriFromExtras(extras, fileId = 0)
    }

    // ==================== parseUri Tests ====================

    @Test
    fun `parseUri extracts all fields correctly`() {
        val uri = "tg://file/123?chatId=-1001234567890&messageId=42&remoteId=AgACAgIAAxkBAAI...&mimeType=video%2Fmp4"

        val parsed = TelegramPlaybackUriContract.parseUri(uri)

        assertNotNull(parsed)
        assertEquals(123, parsed!!.fileId)
        assertEquals(-1001234567890, parsed.chatId)
        assertEquals(42, parsed.messageId)
        assertEquals("AgACAgIAAxkBAAI...", parsed.remoteId)
        assertEquals("video/mp4", parsed.mimeType)
    }

    @Test
    fun `parseUri with missing optional fields returns null for them`() {
        val uri = "tg://file/123?chatId=-1001234567890&messageId=42"

        val parsed = TelegramPlaybackUriContract.parseUri(uri)

        assertNotNull(parsed)
        assertEquals(123, parsed!!.fileId)
        assertEquals(-1001234567890, parsed.chatId)
        assertEquals(42, parsed.messageId)
        assertNull(parsed.remoteId)
        assertNull(parsed.mimeType)
    }

    @Test
    fun `parseUri returns null for non-telegram URI`() {
        val uri = "https://example.com/file.mp4"

        val parsed = TelegramPlaybackUriContract.parseUri(uri)

        assertNull(parsed)
    }

    @Test
    fun `parseUri returns null for malformed URI`() {
        val uri = "tg://file/"

        val parsed = TelegramPlaybackUriContract.parseUri(uri)

        assertNull(parsed)
    }

    // ==================== ParsedUri Helper Tests ====================

    @Test
    fun `ParsedUri isResolvable returns true when fileId is valid`() {
        val parsed = TelegramPlaybackUriContract.ParsedUri(
            fileId = 123,
            chatId = -1001234567890,
            messageId = 42,
            remoteId = null,
            mimeType = null,
        )

        assertTrue(parsed.isResolvable)
    }

    @Test
    fun `ParsedUri isResolvable returns true when remoteId is present`() {
        val parsed = TelegramPlaybackUriContract.ParsedUri(
            fileId = 0,
            chatId = -1001234567890,
            messageId = 42,
            remoteId = "AgACAgIAAxkBAAI...",
            mimeType = null,
        )

        assertTrue(parsed.isResolvable)
    }

    @Test
    fun `ParsedUri isResolvable returns false when no file locator`() {
        val parsed = TelegramPlaybackUriContract.ParsedUri(
            fileId = 0,
            chatId = -1001234567890,
            messageId = 42,
            remoteId = null,
            mimeType = null,
        )

        assertFalse(parsed.isResolvable)
    }

    @Test
    fun `ParsedUri hasMessageLocator returns true when chatId and messageId present`() {
        val parsed = TelegramPlaybackUriContract.ParsedUri(
            fileId = 0,
            chatId = -1001234567890,
            messageId = 42,
            remoteId = "AgACAgIAAxkBAAI...",
            mimeType = null,
        )

        assertTrue(parsed.hasMessageLocator)
    }

    @Test
    fun `ParsedUri hasMessageLocator returns false when chatId missing`() {
        val parsed = TelegramPlaybackUriContract.ParsedUri(
            fileId = 123,
            chatId = null,
            messageId = 42,
            remoteId = "AgACAgIAAxkBAAI...",
            mimeType = null,
        )

        assertFalse(parsed.hasMessageLocator)
    }

    // ==================== validate Tests ====================

    @Test
    fun `validate returns Valid for complete URI`() {
        val uri = "tg://file/123?chatId=-1001234567890&messageId=42&remoteId=AgACAgIAAxkBAAI..."

        val result = TelegramPlaybackUriContract.validate(uri)

        assertTrue(result is TelegramPlaybackUriContract.ValidationResult.Valid)
    }

    @Test
    fun `validate returns Invalid for missing chatId`() {
        val uri = "tg://file/123?messageId=42&remoteId=AgACAgIAAxkBAAI..."

        val result = TelegramPlaybackUriContract.validate(uri)

        assertTrue(result is TelegramPlaybackUriContract.ValidationResult.Invalid)
        assertTrue((result as TelegramPlaybackUriContract.ValidationResult.Invalid).reason.contains("chatId"))
    }

    @Test
    fun `validate returns Invalid for missing messageId`() {
        val uri = "tg://file/123?chatId=-1001234567890&remoteId=AgACAgIAAxkBAAI..."

        val result = TelegramPlaybackUriContract.validate(uri)

        assertTrue(result is TelegramPlaybackUriContract.ValidationResult.Invalid)
        assertTrue((result as TelegramPlaybackUriContract.ValidationResult.Invalid).reason.contains("messageId"))
    }

    @Test
    fun `validate returns Invalid for unresolvable URI (no fileId or remoteId)`() {
        val uri = "tg://file/0?chatId=-1001234567890&messageId=42"

        val result = TelegramPlaybackUriContract.validate(uri)

        assertTrue(result is TelegramPlaybackUriContract.ValidationResult.Invalid)
        assertTrue((result as TelegramPlaybackUriContract.ValidationResult.Invalid).reason.contains("fileId"))
    }

    @Test
    fun `validate returns Invalid for non-telegram URI`() {
        val uri = "https://example.com/file.mp4"

        val result = TelegramPlaybackUriContract.validate(uri)

        assertTrue(result is TelegramPlaybackUriContract.ValidationResult.Invalid)
    }

    // ==================== isTelegramUri Tests ====================

    @Test
    fun `isTelegramUri returns true for tg scheme`() {
        assertTrue(TelegramPlaybackUriContract.isTelegramUri("tg://file/123"))
    }

    @Test
    fun `isTelegramUri returns false for http scheme`() {
        assertFalse(TelegramPlaybackUriContract.isTelegramUri("https://example.com/file.mp4"))
    }

    @Test
    fun `isTelegramUri handles null gracefully`() {
        assertFalse(TelegramPlaybackUriContract.isTelegramUri(null))
    }
}
