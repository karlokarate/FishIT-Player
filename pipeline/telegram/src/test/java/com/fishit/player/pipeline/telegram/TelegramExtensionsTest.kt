package com.fishit.player.pipeline.telegram

import com.fishit.player.pipeline.telegram.ext.toPlaybackContext
import com.fishit.player.pipeline.telegram.ext.toPlaybackContexts
import com.fishit.player.pipeline.telegram.model.PlaybackType
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for Telegram extension functions.
 */
class TelegramExtensionsTest {
    @Test
    fun `toPlaybackContext creates TELEGRAM type for non-series media`() {
        val item =
            TelegramMediaItem(
                chatId = 123456789L,
                messageId = 42L,
                fileId = 1001,
                isSeries = false,
            )

        val context = item.toPlaybackContext()

        assertEquals(PlaybackType.TELEGRAM, context.type)
        assertEquals(42L, context.mediaId)
        assertEquals(123456789L, context.telegramChatId)
        assertEquals(42L, context.telegramMessageId)
        assertEquals("1001", context.telegramFileId)
    }

    @Test
    fun `toPlaybackContext creates SERIES type for series media`() {
        val item =
            TelegramMediaItem(
                chatId = 123456789L,
                messageId = 42L,
                fileId = 1001,
                isSeries = true,
                seriesName = "Test Series",
                seriesNameNormalized = "test-series",
                seasonNumber = 1,
                episodeNumber = 5,
            )

        val context = item.toPlaybackContext()

        assertEquals(PlaybackType.SERIES, context.type)
        assertEquals(42L, context.mediaId)
        assertEquals(123456789L, context.telegramChatId)
        assertEquals(42L, context.telegramMessageId)
        assertEquals("1001", context.telegramFileId)
        assertNotNull(context.seriesId)
        assertEquals(1, context.season)
        assertEquals(5, context.episodeNumber)
    }

    @Test
    fun `toPlaybackContext handles null fileId`() {
        val item =
            TelegramMediaItem(
                chatId = 123456789L,
                messageId = 42L,
                fileId = null,
            )

        val context = item.toPlaybackContext()

        assertEquals(PlaybackType.TELEGRAM, context.type)
        assertEquals(42L, context.mediaId)
        assertEquals(123456789L, context.telegramChatId)
        assertEquals(42L, context.telegramMessageId)
        assertEquals(null, context.telegramFileId)
    }

    @Test
    fun `toPlaybackContexts converts list correctly`() {
        val items =
            listOf(
                TelegramMediaItem(chatId = 1L, messageId = 10L, fileId = 100),
                TelegramMediaItem(chatId = 2L, messageId = 20L, fileId = 200),
                TelegramMediaItem(chatId = 3L, messageId = 30L, fileId = 300, isSeries = true),
            )

        val contexts = items.toPlaybackContexts()

        assertEquals(3, contexts.size)
        assertEquals(PlaybackType.TELEGRAM, contexts[0].type)
        assertEquals(PlaybackType.TELEGRAM, contexts[1].type)
        assertEquals(PlaybackType.SERIES, contexts[2].type)
    }

    @Test
    fun `toPlaybackContexts handles empty list`() {
        val items = emptyList<TelegramMediaItem>()
        val contexts = items.toPlaybackContexts()
        assertEquals(0, contexts.size)
    }
}
