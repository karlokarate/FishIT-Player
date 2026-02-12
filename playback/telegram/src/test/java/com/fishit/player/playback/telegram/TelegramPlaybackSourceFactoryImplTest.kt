package com.fishit.player.playback.telegram

import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.domain.PlaybackSourceException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TelegramPlaybackSourceFactoryImpl] — Telethon proxy architecture.
 *
 * Tests cover:
 * - Happy path: valid context produces HTTP proxy PlaybackSource
 * - Fail-early: missing required fields throw PlaybackSourceException
 * - URI format: http://127.0.0.1:PORT/file?chat=X&id=Y
 * - DataSourceType is DEFAULT (standard HTTP, NOT TELEGRAM_FILE)
 */
class TelegramPlaybackSourceFactoryImplTest {
    private val proxyBaseUrl = "http://127.0.0.1:8089"
    private val factory = TelegramPlaybackSourceFactoryImpl(proxyBaseUrl)

    // ==================== supports() Tests ====================

    @Test
    fun `supports returns true for TELEGRAM source type`() {
        assertTrue(factory.supports(SourceType.TELEGRAM))
    }

    @Test
    fun `supports returns false for XTREAM source type`() {
        assertFalse(factory.supports(SourceType.XTREAM))
    }

    @Test
    fun `supports returns false for OTHER source type`() {
        assertFalse(factory.supports(SourceType.OTHER))
    }

    // ==================== createSource() Happy Path Tests ====================

    @Test
    fun `createSource with chatId and messageId returns HTTP proxy PlaybackSource`() =
        runTest {
            val context = PlaybackContext(
                canonicalId = "test-canonical-id",
                sourceType = SourceType.TELEGRAM,
                sourceKey = null,
                uri = null,
                extras = mapOf(
                    PlaybackHintKeys.Telegram.CHAT_ID to "-1001234567890",
                    PlaybackHintKeys.Telegram.MESSAGE_ID to "42",
                    PlaybackHintKeys.Telegram.MIME_TYPE to "video/mp4",
                ),
            )

            val source = factory.createSource(context)

            assertNotNull(source)
            assertEquals(DataSourceType.DEFAULT, source.dataSourceType)
            assertEquals("video/mp4", source.mimeType)
            assertTrue(source.uri?.startsWith("http://127.0.0.1:8089/file?") == true)
            assertTrue(source.uri?.contains("chat=-1001234567890") == true)
            assertTrue(source.uri?.contains("id=42") == true)
        }

    @Test
    fun `createSource with msg sourceKey extracts chatId and messageId`() =
        runTest {
            val context = PlaybackContext(
                canonicalId = "test-canonical-id",
                sourceType = SourceType.TELEGRAM,
                sourceKey = "msg:-1001234567890:42",
                uri = null,
                extras = mapOf(
                    PlaybackHintKeys.Telegram.MIME_TYPE to "video/mp4",
                ),
            )

            val source = factory.createSource(context)

            assertNotNull(source)
            assertEquals(DataSourceType.DEFAULT, source.dataSourceType)
            assertTrue(source.uri?.contains("chat=-1001234567890") == true)
            assertTrue(source.uri?.contains("id=42") == true)
        }

    @Test
    fun `createSource without mimeType still succeeds`() =
        runTest {
            val context = PlaybackContext(
                canonicalId = "test-canonical-id",
                sourceType = SourceType.TELEGRAM,
                sourceKey = null,
                uri = null,
                extras = mapOf(
                    PlaybackHintKeys.Telegram.CHAT_ID to "-1001234567890",
                    PlaybackHintKeys.Telegram.MESSAGE_ID to "42",
                ),
            )

            val source = factory.createSource(context)

            assertNotNull(source)
            assertEquals(DataSourceType.DEFAULT, source.dataSourceType)
            assertTrue(source.uri?.contains("chat=-1001234567890") == true)
        }

    // ==================== createSource() Fail-Early Tests ====================

    @Test(expected = PlaybackSourceException::class)
    fun `createSource without chatId throws PlaybackSourceException`() =
        runTest {
            val context = PlaybackContext(
                canonicalId = "test-canonical-id",
                sourceType = SourceType.TELEGRAM,
                sourceKey = null,
                uri = null,
                extras = mapOf(
                    PlaybackHintKeys.Telegram.MESSAGE_ID to "42",
                ),
            )
            factory.createSource(context)
        }

    @Test(expected = PlaybackSourceException::class)
    fun `createSource without messageId throws PlaybackSourceException`() =
        runTest {
            val context = PlaybackContext(
                canonicalId = "test-canonical-id",
                sourceType = SourceType.TELEGRAM,
                sourceKey = null,
                uri = null,
                extras = mapOf(
                    PlaybackHintKeys.Telegram.CHAT_ID to "-1001234567890",
                ),
            )
            factory.createSource(context)
        }

    @Test(expected = PlaybackSourceException::class)
    fun `createSource with empty context throws PlaybackSourceException`() =
        runTest {
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
    fun `createSource with invalid msg sourceKey and no extras throws`() =
        runTest {
            val context = PlaybackContext(
                canonicalId = "test-canonical-id",
                sourceType = SourceType.TELEGRAM,
                sourceKey = "msg:invalid",
                uri = null,
                extras = emptyMap(),
            )
            factory.createSource(context)
        }
}
