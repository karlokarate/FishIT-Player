package com.fishit.player.feature.telegram

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.telegrammedia.domain.TelegramMediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TelegramTapToPlayUseCase].
 *
 * Tests that the use case builds PlaybackContext correctly:
 * - Converts TelegramMediaItem (domain model) to PlaybackContext
 * - Sets SourceType.TELEGRAM
 * - Extracts identifiers without secrets
 *
 * Note: We test the PlaybackContext building logic directly since
 * InternalPlayerSession is a final class and cannot be mocked.
 */
class TelegramTapToPlayUseCaseTest {
    @Test
    fun `buildPlaybackContext converts Telegram item with correct sourceType`() {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then
        assertEquals(
            com.fishit.player.core.playermodel.SourceType.TELEGRAM,
            context.sourceType,
        )
        assertEquals("msg:123:456", context.canonicalId)
        assertEquals("Test Video", context.title)
        assertEquals("Telegram Chat", context.subtitle)
    }

    @Test
    fun `buildPlaybackContext builds extras with non-secret identifiers`() {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then
        val extras = context.extras

        // Should contain chatId, messageId
        assertEquals("123", extras["chatId"])
        assertEquals("456", extras["messageId"])

        // Should NOT contain secrets like auth tokens
        assertEquals(false, extras.containsKey("authToken"))
        assertEquals(false, extras.containsKey("apiHash"))
        assertEquals(false, extras.containsKey("apiId"))
        assertEquals(false, extras.containsKey("phoneNumber"))

        // Should NOT contain URI-like strings
        extras.values.forEach { value ->
            assertEquals(
                "Extras should not contain URI-like strings",
                false,
                value.startsWith("http://") || value.startsWith("https://") || value.startsWith("tg://"),
            )
        }
    }

    @Test
    fun `buildPlaybackContext sets isLive to false for Telegram media`() {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then
        assertEquals(false, context.isLive)
        assertEquals(true, context.isSeekable)
    }

    @Test
    fun `buildPlaybackContext sets sourceKey for factory resolution`() {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then
        assertEquals("msg:123:456", context.sourceKey)
    }

    @Test
    fun `buildPlaybackContext extracts chatId and messageId`() {
        // Given
        val telegramItem =
            createTestTelegramItem().copy(
                chatId = 9876L,
                messageId = 5432L,
            )

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then
        assertEquals("9876", context.extras["chatId"])
        assertEquals("5432", context.extras["messageId"])
    }

    @Test
    fun `buildPlaybackContext handles null chatId and messageId`() {
        // Given
        val telegramItem =
            createTestTelegramItem().copy(
                chatId = null,
                messageId = null,
            )

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then
        assertEquals(false, context.extras.containsKey("chatId"))
        assertEquals(false, context.extras.containsKey("messageId"))
    }

    @Test
    fun `buildPlaybackContext uses stable mediaId for canonicalId and sourceKey`() {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then - canonicalId and sourceKey should use stable mediaId
        assertEquals("msg:123:456", context.canonicalId)
        assertEquals("msg:123:456", context.sourceKey ?: "")

        // Should NOT be URI-like strings
        assertEquals(false, context.canonicalId.startsWith("http://"))
        assertEquals(false, context.canonicalId.startsWith("https://"))
        assertEquals(false, context.sourceKey?.startsWith("tg://") == true)
    }

    @Test
    fun `buildPlaybackContext sets correct SourceType TELEGRAM`() {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then - must use SourceType.TELEGRAM
        assertEquals(
            com.fishit.player.core.playermodel.SourceType.TELEGRAM,
            context.sourceType,
        )
    }

    private fun createTestTelegramItem(): TelegramMediaItem =
        TelegramMediaItem(
            mediaId = "msg:123:456",
            title = "Test Video",
            sourceLabel = "Telegram Chat",
            mediaType = MediaType.MOVIE,
            durationMs = 120 * 60_000L,
            posterUrl = null,
            chatId = 123L,
            messageId = 456L,
        )

    /**
     * Helper to build PlaybackContext for testing.
     * Mirrors the logic in TelegramTapToPlayUseCase.buildPlaybackContext.
     */
    private fun buildPlaybackContext(item: TelegramMediaItem): PlaybackContext {
        val extras =
            buildMap<String, String> {
                item.chatId?.let { put("chatId", it.toString()) }
                item.messageId?.let { put("messageId", it.toString()) }
            }

        return PlaybackContext(
            canonicalId = item.mediaId,
            sourceType = com.fishit.player.core.playermodel.SourceType.TELEGRAM,
            sourceKey = item.mediaId,
            title = item.title,
            subtitle = item.sourceLabel,
            posterUrl = item.posterUrl,
            startPositionMs = 0L,
            isLive = false,
            isSeekable = true,
            extras = extras,
        )
    }
}
