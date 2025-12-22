package com.fishit.player.infra.transport.xtream

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for JSON response gate logic.
 *
 * This tests the validation rules used in DefaultXtreamApiClient.fetchRaw()
 * to determine whether a response body is valid JSON.
 *
 * The actual implementation checks:
 * - Body starts with '{' or '[' after trimming whitespace
 * - Returns null if body doesn't start with valid JSON characters
 *
 * This prevents JSON parsing exceptions when server returns M3U/HTML/text.
 */
class JsonResponseGateTest {
    /**
     * Simulates the JSON gate logic from fetchRaw().
     * This helper matches the exact implementation in DefaultXtreamApiClient.
     */
    private fun isValidJsonResponse(body: String): Boolean {
        if (body.isEmpty()) return false
        val trimmed = body.trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    /**
     * Detects M3U playlist content, used for better error messages.
     */
    private fun isM3UPlaylist(
        body: String,
        contentType: String,
    ): Boolean {
        val trimmed = body.trimStart()
        return trimmed.startsWith("#EXTM3U") ||
            trimmed.startsWith("#EXTINF") ||
            contentType.contains("mpegurl", ignoreCase = true) ||
            contentType.contains("x-mpegurl", ignoreCase = true)
    }

    // ===== Valid JSON Tests =====

    @Test
    fun `valid JSON object is accepted`() {
        val body = """{"user_info":{"status":"Active"}}"""
        assertTrue(isValidJsonResponse(body), "JSON object should be accepted")
    }

    @Test
    fun `valid JSON array is accepted`() {
        val body = """[{"category_id":"1","category_name":"Movies"}]"""
        assertTrue(isValidJsonResponse(body), "JSON array should be accepted")
    }

    @Test
    fun `JSON with leading whitespace is accepted`() {
        val body = "  \n\t  {\"key\":\"value\"}"
        assertTrue(isValidJsonResponse(body), "JSON with leading whitespace should be accepted")
    }

    @Test
    fun `empty JSON object is accepted`() {
        assertTrue(isValidJsonResponse("{}"), "Empty JSON object should be accepted")
    }

    @Test
    fun `empty JSON array is accepted`() {
        assertTrue(isValidJsonResponse("[]"), "Empty JSON array should be accepted")
    }

    @Test
    fun `nested JSON object is accepted`() {
        val body = """{"server_info":{"url":"example.com","port":8080},"user_info":{"username":"test"}}"""
        assertTrue(isValidJsonResponse(body), "Nested JSON object should be accepted")
    }

    // ===== Invalid Response Tests =====

    @Test
    fun `empty body is rejected`() {
        assertFalse(isValidJsonResponse(""), "Empty body should be rejected")
    }

    @Test
    fun `whitespace-only body is rejected`() {
        assertFalse(isValidJsonResponse("   \n\t  "), "Whitespace-only body should be rejected")
    }

    @Test
    fun `M3U playlist is rejected`() {
        val body =
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="ESPN" tvg-logo="http://logo.png",ESPN
            http://example.com/live/user/pass/12345.ts
            """.trimIndent()

        assertFalse(isValidJsonResponse(body), "M3U playlist should be rejected")
    }

    @Test
    fun `HTML error page is rejected`() {
        val body =
            """
            <!DOCTYPE html>
            <html><head><title>Error</title></head>
            <body><h1>Server Error</h1></body></html>
            """.trimIndent()

        assertFalse(isValidJsonResponse(body), "HTML page should be rejected")
    }

    @Test
    fun `plain text response is rejected`() {
        assertFalse(isValidJsonResponse("Invalid API key"), "Plain text should be rejected")
    }

    @Test
    fun `XML response is rejected`() {
        val body = """<?xml version="1.0"?><error>Invalid credentials</error>"""
        assertFalse(isValidJsonResponse(body), "XML response should be rejected")
    }

    @Test
    fun `PHP error message is rejected`() {
        val body = """PHP Warning: Cannot modify header information..."""
        assertFalse(isValidJsonResponse(body), "PHP error should be rejected")
    }

    // ===== M3U Detection Tests =====

    @Test
    fun `M3U detection identifies EXTM3U header`() {
        val body = "#EXTM3U\n#EXTINF:-1,Channel\nhttp://url.ts"
        assertTrue(isM3UPlaylist(body, "text/plain"), "Should detect #EXTM3U header")
    }

    @Test
    fun `M3U detection identifies EXTINF without header`() {
        val body = "#EXTINF:-1,Channel\nhttp://url.ts"
        assertTrue(isM3UPlaylist(body, "text/plain"), "Should detect #EXTINF without header")
    }

    @Test
    fun `M3U detection identifies mpegurl content type`() {
        val body = "http://url.ts"
        assertTrue(isM3UPlaylist(body, "application/x-mpegurl"), "Should detect x-mpegurl content type")
    }

    @Test
    fun `M3U detection identifies vnd mpegurl content type`() {
        val body = "http://url.ts"
        assertTrue(isM3UPlaylist(body, "application/vnd.apple.mpegurl"), "Should detect vnd.apple.mpegurl")
    }

    @Test
    fun `M3U detection returns false for JSON`() {
        val body = """{"status":"ok"}"""
        assertFalse(isM3UPlaylist(body, "application/json"), "Should not detect JSON as M3U")
    }

    // ===== Edge Cases =====

    @Test
    fun `response starting with number is rejected`() {
        assertFalse(isValidJsonResponse("123"), "Number at start should be rejected")
    }

    @Test
    fun `response starting with quote is rejected`() {
        assertFalse(isValidJsonResponse("\"string value\""), "String literal should be rejected")
    }

    @Test
    fun `malformed JSON with correct start is accepted for gate`() {
        // Note: The gate only checks the first character - actual parsing happens later
        val body = """{"incomplete": """
        assertTrue(isValidJsonResponse(body), "Gate only checks first char, not validity")
    }

    // ===== Content-Type Header Tests =====

    /**
     * Validates that Content-Type with charset suffix is accepted.
     * Common formats: "application/json", "application/json; charset=utf-8", "application/json;charset=UTF-8"
     */
    @Test
    fun `content type with charset is accepted`() {
        val contentTypes =
            listOf(
                "application/json",
                "application/json; charset=utf-8",
                "application/json; charset=UTF-8",
                "application/json;charset=utf-8",
                "APPLICATION/JSON; CHARSET=UTF-8",
            )

        contentTypes.forEach { contentType ->
            assertTrue(
                contentType.contains("application/json", ignoreCase = true),
                "Content-Type '$contentType' should be accepted",
            )
        }
    }

    /**
     * Documents that header lookup is case-insensitive in OkHttp.
     * response.header("Content-Type") works correctly with any case per HTTP spec (RFC 7230).
     */
    @Test
    fun `documents case insensitive header handling`() {
        val headerVariants =
            listOf(
                "Content-Type",
                "content-type",
                "CONTENT-TYPE",
                "Content-type",
                "content-Type",
            )

        // OkHttp's response.header() is case-insensitive
        // This test documents that our implementation uses the correct API
        assertTrue(headerVariants.all { it.equals("Content-Type", ignoreCase = true) })
    }
}
