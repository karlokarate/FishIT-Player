package com.chris.m3usuite.telegram.util

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for TelegramPlayUrl utility.
 * Tests URL building functionality (Section 4 of cleanup task).
 */
class TelegramPlayUrlTest {
    @Test
    fun `buildFileUrl constructs proper tg URL format`() {
        val url = TelegramPlayUrl.buildFileUrl(
            fileId = 12345,
            chatId = -1001234567890L,
            messageId = 98765L
        )
        
        assertEquals(
            "tg://file/12345?chatId=-1001234567890&messageId=98765",
            url
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `buildFileUrl throws exception for null fileId`() {
        TelegramPlayUrl.buildFileUrl(
            fileId = null,
            chatId = 123L,
            messageId = 456L
        )
    }

    @Test
    fun `buildFileUrl handles various fileId values`() {
        // Test with different fileId values
        val url1 = TelegramPlayUrl.buildFileUrl(1, 100L, 200L)
        assertTrue(url1.startsWith("tg://file/1?"))
        
        val url2 = TelegramPlayUrl.buildFileUrl(999999, 100L, 200L)
        assertTrue(url2.startsWith("tg://file/999999?"))
    }
}
