package com.fishit.player.playback.telegram

import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.domain.PlaybackSourceException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TelegramPlaybackSourceFactoryImpl].
 *
 * Tests cover:
 * - Happy path: valid context produces valid PlaybackSource
 * - Fail-early: missing required fields throw PlaybackSourceException
 * - URI format compliance with TelegramPlaybackUriContract
 */
class TelegramPlaybackSourceFactoryImplTest {

    private val factory = TelegramPlaybackSourceFactoryImpl()

    // ==================== supports() Tests ====================

    @Test
    fun `supports returns true for TELEGRAM source type`() {
        assertTrue(factory.supports(SourceType.TELEGRAM))
    }

    @Test
    fun `supports returns false for XTREAM source type`() {
        assert(!factory.supports(SourceType.XTREAM))
    }

    @Test
    fun `supports returns false for OTHER source type`() {
        assert(!factory.supports(SourceType.OTHER))
    }

    // ==================== createSource() Happy Path Tests ====================

    @Test
    fun `createSource with all extras returns valid PlaybackSource`() = runTest {
        val context = PlaybackContext(
            canonicalId = "test-canonical-id",
            sourceType = SourceType.TELEGRAM,
            sourceKey = null,
            uri = null,
            extras = mapOf(
                PlaybackHintKeys.Telegram.CHAT_ID to "-1001234567890",
                PlaybackHintKeys.Telegram.MESSAGE_ID to "42",
                PlaybackHintKeys.Telegram.REMOTE_ID to "AgACAgIAAxkBAAI...",
                PlaybackHintKeys.Telegram.MIME_TYPE to "video/mp4",
            ),
        )

        val source = factory.createSource(context)

        assertNotNull(source)
        assertEquals(DataSourceType.TELEGRAM_FILE, source.dataSourceType)
        assertEquals("video/mp4", source.mimeType)
        assertTrue(source.uri?.startsWith("tg://file/") == true)
        assertTrue(source.uri?.contains("chatId=-1001234567890") == true)
        assertTrue(source.uri?.contains("messageId=42") == true)
        assertTrue(source.uri?.contains("remoteId=AgACAgIAAxkBAAI...") == true)
    }

    @Test
    fun `createSource with existing valid tg URI passes through`() = runTest {
        val existingUri = "tg://file/123?chatId=-1001234567890&messageId=42&remoteId=AgACAgIAAxkBAAI..."
        val context = PlaybackContext(
            canonicalId = "test-canonical-id",
            sourceType = SourceType.TELEGRAM,
            sourceKey = null,
            uri = existingUri,
            extras = emptyMap(),
        )

        val source = factory.createSource(context)

        assertNotNull(source)
        assertEquals(existingUri, source.uri)
    }

    @Test
    fun `createSource with msg sourceKey and extras returns valid source`() = runTest {
        val context = PlaybackContext(
            canonicalId = "test-canonical-id",
            sourceType = SourceType.TELEGRAM,
            sourceKey = "msg:-1001234567890:42",
            uri = null,
            extras = mapOf(
                PlaybackHintKeys.Telegram.REMOTE_ID to "AgACAgIAAxkBAAI...",
                PlaybackHintKeys.Telegram.MIME_TYPE to "video/mp4",
            ),
        )

        val source = factory.createSource(context)

        assertNotNull(source)
        assertEquals(DataSourceType.TELEGRAM_FILE, source.dataSourceType)
        assertTrue(source.uri?.contains("chatId=-1001234567890") == true)
        assertTrue(source.uri?.contains("messageId=42") == true)
        assertTrue(source.uri?.contains("remoteId=AgACAgIAAxkBAAI...") == true)
    }

    @Test
    fun `createSource with fileId but no remoteId succeeds`() = runTest {
        val context = PlaybackContext(
            canonicalId = "test-canonical-id",
            sourceType = SourceType.TELEGRAM,
            sourceKey = null,
            uri = null,
            extras = mapOf(
                PlaybackHintKeys.Telegram.CHAT_ID to "-1001234567890",
                PlaybackHintKeys.Telegram.MESSAGE_ID to "42",
                PlaybackHintKeys.Telegram.FILE_ID to "999",
            ),
        )

        val source = factory.createSource(context)

        assertNotNull(source)
        assertTrue(source.uri?.startsWith("tg://file/999") == true)
    }

    // ==================== createSource() Fail-Early Tests ====================

    @Test(expected = PlaybackSourceException::class)
    fun `createSource without chatId throws PlaybackSourceException`() = runTest {
        val context = PlaybackContext(
            canonicalId = "test-canonical-id",
            sourceType = SourceType.TELEGRAM,
            sourceKey = null,
            uri = null,
            extras = mapOf(
                PlaybackHintKeys.Telegram.MESSAGE_ID to "42",
                PlaybackHintKeys.Telegram.REMOTE_ID to "AgACAgIAAxkBAAI...",
            ),
        )

        factory.createSource(context)
    }

    @Test(expected = PlaybackSourceException::class)
    fun `createSource without messageId throws PlaybackSourceException`() = runTest {
        val context = PlaybackContext(
            canonicalId = "test-canonical-id",
            sourceType = SourceType.TELEGRAM,
            sourceKey = null,
            uri = null,
            extras = mapOf(
                PlaybackHintKeys.Telegram.CHAT_ID to "-1001234567890",
                PlaybackHintKeys.Telegram.REMOTE_ID to "AgACAgIAAxkBAAI...",
            ),
        )

        factory.createSource(context)
    }

    @Test(expected = PlaybackSourceException::class)
    fun `createSource without file locator throws PlaybackSourceException`() = runTest {
        val context = PlaybackContext(
            canonicalId = "test-canonical-id",
            sourceType = SourceType.TELEGRAM,
            sourceKey = null,
            uri = null,
            extras = mapOf(
                PlaybackHintKeys.Telegram.CHAT_ID to "-1001234567890",
                PlaybackHintKeys.Telegram.MESSAGE_ID to "42",
                // Neither fileId nor remoteId provided
            ),
        )

        factory.createSource(context)
    }

    @Test(expected = PlaybackSourceException::class)
    fun `createSource with empty context throws PlaybackSourceException`() = runTest {
        val context = PlaybackContext(
            canonicalId = "test-canonical-id",
            sourceType = SourceType.TELEGRAM,
            sourceKey = null,
            uri = null,
            extras = emptyMap(),
        )

        factory.createSource(context)
    }

    @Test(expected = PlaybackSourceException::class)
    fun `createSource with invalid msg sourceKey and no extras throws`() = runTest {
        val context = PlaybackContext(
            canonicalId = "test-canonical-id",
            sourceType = SourceType.TELEGRAM,
            sourceKey = "msg:invalid",
            uri = null,
            extras = emptyMap(),
        )

        factory.createSource(context)
    }

    @Test(expected = PlaybackSourceException::class)
    fun `createSource with msg sourceKey but no remoteId in extras throws`() = runTest {
        // This tests the fail-early case: we have chatId/messageId from sourceKey
        // but no remoteId or fileId in extras - cannot resolve file
        val context = PlaybackContext(
            canonicalId = "test-canonical-id",
            sourceType = SourceType.TELEGRAM,
            sourceKey = "msg:-1001234567890:42",
            uri = null,
            extras = mapOf(
                PlaybackHintKeys.Telegram.MIME_TYPE to "video/mp4",
                // No remoteId or fileId!
            ),
        )

        factory.createSource(context)
    }

    // ==================== URI Validation Tests ====================

    @Test
    fun `createSource produces URI that passes validation`() = runTest {
        val context = PlaybackContext(
            canonicalId = "test-canonical-id",
            sourceType = SourceType.TELEGRAM,
            sourceKey = null,
            uri = null,
            extras = mapOf(
                PlaybackHintKeys.Telegram.CHAT_ID to "-1001234567890",
                PlaybackHintKeys.Telegram.MESSAGE_ID to "42",
                PlaybackHintKeys.Telegram.REMOTE_ID to "AgACAgIAAxkBAAI...",
            ),
        )

        val source = factory.createSource(context)
        val validation = TelegramPlaybackUriContract.validate(source.uri!!)

        assertTrue(validation is TelegramPlaybackUriContract.ValidationResult.Valid)
    }

    @Test
    fun `createSource URI can be parsed back correctly`() = runTest {
        val context = PlaybackContext(
            canonicalId = "test-canonical-id",
            sourceType = SourceType.TELEGRAM,
            sourceKey = null,
            uri = null,
            extras = mapOf(
                PlaybackHintKeys.Telegram.CHAT_ID to "-1001234567890",
                PlaybackHintKeys.Telegram.MESSAGE_ID to "42",
                PlaybackHintKeys.Telegram.REMOTE_ID to "AgACAgIAAxkBAAI...",
                PlaybackHintKeys.Telegram.FILE_ID to "123",
            ),
        )

        val source = factory.createSource(context)
        val parsed = TelegramPlaybackUriContract.parseUri(source.uri!!)

        assertNotNull(parsed)
        assertEquals(-1001234567890L, parsed!!.chatId)
        assertEquals(42L, parsed.messageId)
        assertEquals("AgACAgIAAxkBAAI...", parsed.remoteId)
        assertEquals(123, parsed.fileId)
    }
}
