package com.chris.m3usuite.telegram.util

import com.chris.m3usuite.telegram.player.TelegramPlaybackRequest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TelegramPlayUrl utility.
 *
 * Phase D+: Tests remoteId-first URL building for playback wiring.
 */
class TelegramPlayUrlTest {
    // ==========================================================================
    // Phase D+ RemoteId-First URL Tests
    // ==========================================================================

    @Test
    fun `build with TelegramPlaybackRequest includes remoteId and uniqueId`() {
        val request = TelegramPlaybackRequest(
            chatId = -1001234567890L,
            messageId = 98765L,
            remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
            uniqueId = "AQADCAAH1234",
            fileId = 12345,
        )

        val url = TelegramPlayUrl.build(request)

        // Verify all parameters are present
        assertTrue("URL should start with tg://file/", url.startsWith("tg://file/"))
        assertTrue("URL should contain fileId in path", url.contains("/12345?"))
        assertTrue("URL should contain chatId", url.contains("chatId=-1001234567890"))
        assertTrue("URL should contain messageId", url.contains("messageId=98765"))
        assertTrue("URL should contain remoteId", url.contains("remoteId=AgACAgIAAxkBAAIBNmF1Y2xxxxx"))
        assertTrue("URL should contain uniqueId", url.contains("uniqueId=AQADCAAH1234"))
    }

    @Test
    fun `build with null fileId uses 0 in path`() {
        val request = TelegramPlaybackRequest(
            chatId = 123L,
            messageId = 456L,
            remoteId = "test-remote-id",
            uniqueId = "test-unique-id",
            fileId = null,
        )

        val url = TelegramPlayUrl.build(request)

        // When fileId is null, path should contain 0
        assertTrue("URL should use 0 for null fileId", url.startsWith("tg://file/0?"))
        assertTrue("URL should still contain remoteId", url.contains("remoteId=test-remote-id"))
    }

    @Test
    fun `buildFileUrl with remoteId includes all identifiers`() {
        val url = TelegramPlayUrl.buildFileUrl(
            fileId = 12345,
            chatId = -1001234567890L,
            messageId = 98765L,
            remoteId = "AgACAgIAAxkBAAIBNmF1Y2xxxxx",
            uniqueId = "AQADCAAH1234",
        )

        assertTrue("URL should contain all parameters", url.contains("remoteId="))
        assertTrue("URL should contain uniqueId", url.contains("uniqueId="))
        assertTrue("URL should start with tg://file/12345", url.startsWith("tg://file/12345?"))
    }

    @Test
    fun `buildFileUrl with null fileId and remoteId still builds valid URL`() {
        val url = TelegramPlayUrl.buildFileUrl(
            fileId = null,
            chatId = 123L,
            messageId = 456L,
            remoteId = "test-remote-id",
            uniqueId = "test-unique-id",
        )

        // Should use 0 as fileId path segment
        assertTrue("URL should use 0 for null fileId", url.startsWith("tg://file/0?"))
        assertTrue("URL should contain remoteId for resolution", url.contains("remoteId=test-remote-id"))
    }

    // ==========================================================================
    // Legacy API Tests (deprecated but still supported)
    // ==========================================================================

    @Test
    fun `legacy buildFileUrl constructs proper tg URL format`() {
        @Suppress("DEPRECATION")
        val url =
            TelegramPlayUrl.buildFileUrl(
                fileId = 12345,
                chatId = -1001234567890L,
                messageId = 98765L,
            )

        assertEquals(
            "tg://file/12345?chatId=-1001234567890&messageId=98765",
            url,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `legacy buildFileUrl throws exception for null fileId`() {
        @Suppress("DEPRECATION")
        TelegramPlayUrl.buildFileUrl(
            fileId = null,
            chatId = 123L,
            messageId = 456L,
        )
    }

    @Test
    fun `legacy buildFileUrl handles various fileId values`() {
        // Test with different fileId values
        @Suppress("DEPRECATION")
        val url1 = TelegramPlayUrl.buildFileUrl(1, 100L, 200L)
        assertTrue(url1.startsWith("tg://file/1?"))

        @Suppress("DEPRECATION")
        val url2 = TelegramPlayUrl.buildFileUrl(999999, 100L, 200L)
        assertTrue(url2.startsWith("tg://file/999999?"))
    }
}
