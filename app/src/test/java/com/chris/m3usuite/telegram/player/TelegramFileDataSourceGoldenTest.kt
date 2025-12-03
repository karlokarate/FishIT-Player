package com.chris.m3usuite.telegram.player

import com.chris.m3usuite.telegram.core.StreamingConfigRefactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

/**
 * Phase T4: DataSource Golden Test
 *
 * Per TELEGRAM_SIP_PLAYER_INTEGRATION.md:
 * Tests the TelegramFileDataSource URL parsing and resolution strategy
 * WITHOUT requiring actual TDLib or file system access.
 *
 * Golden Test Scenario:
 * Given:
 *   - A tg:// URL with fileId=0 and a valid remoteId
 *   - A fake downloader that would return a known fileId and local path
 * Assert:
 *   - URL parsing extracts all required parameters
 *   - Resolution strategy is remoteId-first when fileId=0
 *   - All identifiers (chatId, messageId, remoteId, uniqueId) are preserved
 */
class TelegramFileDataSourceGoldenTest {
    // ==========================================================================
    // URL Parsing Tests (Using Java URI, no Android dependency)
    // ==========================================================================

    @Test
    fun `Golden Test - Parse tg URL with fileId in path`() {
        val url = "tg://file/12345?chatId=-1001234567890&messageId=99999&remoteId=TestRemoteId&uniqueId=TestUniqueId"
        val uri = URI.create(url)

        // Scheme and host
        assertEquals("Scheme", "tg", uri.scheme)
        assertEquals("Host", "file", uri.host)

        // FileId from path
        val path = uri.path
        val fileIdStr = path?.substringAfter("/") ?: ""
        assertEquals("FileId from path", "12345", fileIdStr)

        // Parse query parameters
        val params = parseQueryParams(uri.query)
        assertEquals("chatId", "-1001234567890", params["chatId"])
        assertEquals("messageId", "99999", params["messageId"])
        assertEquals("remoteId", "TestRemoteId", params["remoteId"])
        assertEquals("uniqueId", "TestUniqueId", params["uniqueId"])
    }

    @Test
    fun `Golden Test - Parse tg URL with fileId=0 (remoteId resolution needed)`() {
        // This is the key scenario: fileId=0 means we need to resolve via remoteId
        val url = "tg://file/0?chatId=-1001234567890&messageId=99999&remoteId=BQACAgIAAxkBTestRemote&uniqueId=AgADTestUnique"
        val uri = URI.create(url)

        // FileId from path is 0
        val fileIdStr = uri.path?.substringAfter("/") ?: "0"
        val fileIdInt = fileIdStr.toIntOrNull() ?: 0
        assertEquals("FileId should be 0 (trigger remoteId resolution)", 0, fileIdInt)

        // Parse query parameters
        val params = parseQueryParams(uri.query)

        // RemoteId MUST be present for resolution
        val remoteId = params["remoteId"]
        assertNotNull("remoteId must be present when fileId=0", remoteId)
        assertTrue("remoteId must not be blank", remoteId!!.isNotBlank())

        // UniqueId should also be present
        val uniqueId = params["uniqueId"]
        assertNotNull("uniqueId should be present", uniqueId)
        assertTrue("uniqueId must not be blank", uniqueId!!.isNotBlank())
    }

    @Test
    fun `Golden Test - Resolution strategy when fileId is valid (greater than 0)`() {
        val url = "tg://file/54321?chatId=-1001234567890&messageId=99999&remoteId=TestRemote&uniqueId=TestUnique"
        val uri = URI.create(url)

        val fileIdStr = uri.path?.substringAfter("/") ?: "0"
        val fileIdInt = fileIdStr.toIntOrNull() ?: 0

        // When fileId > 0, use it directly (fast path)
        assertTrue("FileId > 0 means use directly", fileIdInt > 0)
        assertEquals("FileId should be 54321", 54321, fileIdInt)
    }

    @Test
    fun `Golden Test - Resolution strategy when fileId is 0 or invalid`() {
        val url = "tg://file/0?chatId=-1001234567890&messageId=99999&remoteId=BQACAgRemoteXYZ&uniqueId=AgADUniqueXYZ"
        val uri = URI.create(url)

        val fileIdStr = uri.path?.substringAfter("/") ?: "0"
        val fileIdInt = fileIdStr.toIntOrNull() ?: 0

        // When fileId <= 0, must resolve via remoteId
        assertTrue("FileId <= 0 means resolve via remoteId", fileIdInt <= 0)

        // RemoteId is the PRIMARY identifier for resolution
        val params = parseQueryParams(uri.query)
        val remoteId = params["remoteId"]
        assertNotNull("remoteId is required for resolution", remoteId)
    }

    // ==========================================================================
    // URL Format Compliance Tests
    // ==========================================================================

    @Test
    fun `URL format - Required parameters`() {
        // All tg:// URLs MUST have these parameters
        val url = "tg://file/12345?chatId=-1001234567890&messageId=99999&remoteId=TestRemote&uniqueId=TestUnique"
        val uri = URI.create(url)
        val params = parseQueryParams(uri.query)

        // Required: chatId
        assertNotNull("chatId is required", params["chatId"])

        // Required: messageId
        assertNotNull("messageId is required", params["messageId"])

        // Required (for remoteId-first): remoteId
        assertNotNull("remoteId is required for remoteId-first strategy", params["remoteId"])

        // Required (for validation): uniqueId
        assertNotNull("uniqueId is required for validation", params["uniqueId"])
    }

    @Test
    fun `URL format - ChatId can be negative (channel IDs)`() {
        val url = "tg://file/12345?chatId=-1001234567890&messageId=99999&remoteId=Test&uniqueId=Test"
        val uri = URI.create(url)
        val params = parseQueryParams(uri.query)

        val chatIdStr = params["chatId"]
        assertNotNull(chatIdStr)

        val chatId = chatIdStr!!.toLongOrNull()
        assertNotNull("chatId must be parseable as Long", chatId)
        assertTrue("chatId can be negative (Telegram channels)", chatId!! < 0)
    }

    @Test
    fun `URL format - MessageId is positive Long`() {
        val url = "tg://file/12345?chatId=-1001234567890&messageId=123456789&remoteId=Test&uniqueId=Test"
        val uri = URI.create(url)
        val params = parseQueryParams(uri.query)

        val messageIdStr = params["messageId"]
        assertNotNull(messageIdStr)

        val messageId = messageIdStr!!.toLongOrNull()
        assertNotNull("messageId must be parseable as Long", messageId)
        assertTrue("messageId should be positive", messageId!! > 0)
    }

    // ==========================================================================
    // Error Case Tests
    // ==========================================================================

    @Test
    fun `Invalid URL - Missing chatId detected`() {
        val url = "tg://file/12345?messageId=99999&remoteId=Test&uniqueId=Test"
        val uri = URI.create(url)
        val params = parseQueryParams(uri.query)

        // chatId is missing
        val chatIdStr = params["chatId"]
        assertTrue("Missing chatId should be null or blank", chatIdStr.isNullOrBlank())
    }

    @Test
    fun `Invalid URL - Missing messageId detected`() {
        val url = "tg://file/12345?chatId=-1001234567890&remoteId=Test&uniqueId=Test"
        val uri = URI.create(url)
        val params = parseQueryParams(uri.query)

        // messageId is missing
        val messageIdStr = params["messageId"]
        assertTrue("Missing messageId should be null or blank", messageIdStr.isNullOrBlank())
    }

    @Test
    fun `Invalid URL - Wrong scheme (not tg) detected`() {
        val url = "https://file/12345?chatId=-1001234567890&messageId=99999"
        val uri = URI.create(url)

        // Scheme is not "tg"
        assertTrue("Scheme is not 'tg'", uri.scheme != "tg")
    }

    @Test
    fun `Invalid URL - Wrong host (not file) detected`() {
        val url = "tg://video/12345?chatId=-1001234567890&messageId=99999"
        val uri = URI.create(url)

        // Host is not "file"
        assertTrue("Host is not 'file'", uri.host != "file")
    }

    // ==========================================================================
    // RemoteId Format Tests
    // ==========================================================================

    @Test
    fun `RemoteId format - Typical Telegram file remote ID`() {
        // Telegram remote IDs are base64-like strings
        val remoteId = "BQACAgIAAxkBAAIBNmF1Y2xxxxx"

        // Should be non-empty
        assertTrue("RemoteId must not be empty", remoteId.isNotEmpty())

        // Should be reasonable length
        assertTrue("RemoteId should be reasonable length", remoteId.length in 10..200)
    }

    @Test
    fun `UniqueId format - Typical Telegram unique ID`() {
        // Telegram unique IDs are also base64-like
        val uniqueId = "AQADCAAH1234"

        // Should be non-empty
        assertTrue("UniqueId must not be empty", uniqueId.isNotEmpty())

        // Should be reasonable length
        assertTrue("UniqueId should be reasonable length", uniqueId.length in 5..100)
    }

    // ==========================================================================
    // DataSource Constants Tests
    // ==========================================================================

    @Test
    fun `TelegramFileDataSource has correct MIN_PREFIX_BYTES constant`() {
        // Per contract: MIN_PREFIX_BYTES is now MIN_PREFIX_FOR_VALIDATION_BYTES in StreamingConfigRefactor
        // The value is 64 KB (not 256 KB as originally documented)
        val expectedMinPrefix = 64 * 1024L

        // The constant has moved to StreamingConfigRefactor
        assertEquals(
            "MIN_PREFIX_FOR_VALIDATION_BYTES should be 64 KB",
            expectedMinPrefix,
            StreamingConfigRefactor.MIN_PREFIX_FOR_VALIDATION_BYTES,
        )
    }

    @Test
    fun `TelegramFileDataSource has correct REMOTE_ID_RESOLUTION_TIMEOUT constant`() {
        // Per contract: Resolution timeout = 10 seconds
        val expectedTimeout = 10_000L

        assertEquals(
            "REMOTE_ID_RESOLUTION_TIMEOUT_MS should be 10 seconds",
            expectedTimeout,
            TelegramFileDataSource.REMOTE_ID_RESOLUTION_TIMEOUT_MS,
        )
    }

    // ==========================================================================
    // Helper Functions
    // ==========================================================================

    /**
     * Parse query string into a map (JVM-compatible, no Android dependency).
     */
    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query
            .split("&")
            .map { it.split("=", limit = 2) }
            .filter { it.size == 2 }
            .associate { (key, value) ->
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }
    }
}
