package com.fishit.player.pipeline.telegram

import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.source.TelegramPlaybackSourceFactoryStub
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for TelegramPlaybackSourceFactoryStub.
 */
class TelegramPlaybackSourceFactoryStubTest {
    private val factory = TelegramPlaybackSourceFactoryStub()

    @Test
    fun `createPlaybackUrl generates valid tg URL`() {
        val item =
            TelegramMediaItem(
                chatId = 123456789L,
                messageId = 42L,
                fileId = 1001,
            )

        val url = factory.createPlaybackUrl(item)
        assertNotNull(url)
        assertTrue(url.startsWith("tg://file/"))
        assertTrue(url.contains("chatId=123456789"))
        assertTrue(url.contains("messageId=42"))
    }

    @Test
    fun `createPlaybackUrl returns null when fileId is null`() {
        val item =
            TelegramMediaItem(
                chatId = 123456789L,
                messageId = 42L,
                fileId = null,
            )

        val url = factory.createPlaybackUrl(item)
        assertNull(url)
    }

    @Test
    fun `isTelegramUrl recognizes tg scheme`() {
        assertTrue(factory.isTelegramUrl("tg://file/1001?chatId=123&messageId=42"))
        assertFalse(factory.isTelegramUrl("https://example.com/video.mp4"))
        assertFalse(factory.isTelegramUrl("http://example.com"))
    }

    @Test
    fun `parseUrl extracts components correctly`() {
        val url = "tg://file/1001?chatId=123456789&messageId=42"
        val info = factory.parseUrl(url)

        assertNotNull(info)
        assertEquals("1001", info.fileId)
        assertEquals(123456789L, info.chatId)
        assertEquals(42L, info.messageId)
    }

    @Test
    fun `parseUrl handles missing query params`() {
        val url = "tg://file/1001"
        val info = factory.parseUrl(url)

        assertNotNull(info)
        assertEquals("1001", info.fileId)
        assertNull(info.chatId)
        assertNull(info.messageId)
    }

    @Test
    fun `parseUrl returns null for invalid URL`() {
        val info1 = factory.parseUrl("https://example.com")
        assertNull(info1)

        val info2 = factory.parseUrl("tg://")
        assertNull(info2)
    }

    @Test
    fun `round-trip URL creation and parsing`() {
        val originalItem =
            TelegramMediaItem(
                chatId = 123456789L,
                messageId = 42L,
                fileId = 1001,
            )

        val url = factory.createPlaybackUrl(originalItem)
        assertNotNull(url)

        val parsed = factory.parseUrl(url)
        assertNotNull(parsed)

        assertEquals("1001", parsed.fileId)
        assertEquals(originalItem.chatId, parsed.chatId)
        assertEquals(originalItem.messageId, parsed.messageId)
    }
}
