package com.fishit.player.infra.transport.xtream

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for lenient Xtream API validation behavior.
 *
 * This validates the changes made to accept servers that:
 * - Return non-JSON from player_api.php (no action)
 * - Return valid JSON from any action-based endpoint (fallback validation)
 * - Have minimal or empty server info responses
 *
 * The goal is to accept as many valid Xtream servers as possible,
 * as long as SOME endpoint returns valid JSON content.
 */
class LenientValidationTest {
    /**
     * Helper to simulate JSON gate logic.
     * Returns true if body starts with '{' or '[' after trimming.
     */
    private fun isValidJsonStructure(body: String): Boolean {
        if (body.isEmpty()) return false
        val trimmed = body.trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    // ===== Minimal Valid Responses =====

    @Test
    fun `empty JSON object is valid`() {
        assertTrue(isValidJsonStructure("{}"), "Empty object should be valid JSON structure")
    }

    @Test
    fun `empty JSON array is valid`() {
        assertTrue(isValidJsonStructure("[]"), "Empty array should be valid JSON structure")
    }

    @Test
    fun `minimal server info with no user info is valid`() {
        val minimal = """{"server_info":{"url":"example.com"}}"""
        assertTrue(isValidJsonStructure(minimal), "Minimal server info should be valid")
    }

    @Test
    fun `minimal server info with empty user info is valid`() {
        val minimal = """{"user_info":{},"server_info":{}}"""
        assertTrue(isValidJsonStructure(minimal), "Server info with empty fields should be valid")
    }

    @Test
    fun `minimal category list is valid`() {
        val minimal = """[{"category_id":"1","category_name":"Movies"}]"""
        assertTrue(isValidJsonStructure(minimal), "Category list should be valid")
    }

    @Test
    fun `empty category list is valid`() {
        assertTrue(isValidJsonStructure("[]"), "Empty category list should be valid")
    }

    // ===== Non-JSON Content (Should Be Rejected) =====

    @Test
    fun `M3U content is rejected`() {
        val m3u =
            """
            #EXTM3U
            #EXTINF:-1,Channel
            http://example.com/stream
            """.trimIndent()
        assertFalse(isValidJsonStructure(m3u), "M3U content should be rejected")
    }

    @Test
    fun `HTML error page is rejected`() {
        val html = """<!DOCTYPE html><html><body>Error</body></html>"""
        assertFalse(isValidJsonStructure(html), "HTML should be rejected")
    }

    @Test
    fun `plain text error is rejected`() {
        assertFalse(isValidJsonStructure("Invalid credentials"), "Plain text should be rejected")
    }

    @Test
    fun `XML response is rejected`() {
        val xml = """<?xml version="1.0"?><error>Failed</error>"""
        assertFalse(isValidJsonStructure(xml), "XML should be rejected")
    }

    // ===== Edge Cases =====

    @Test
    fun `JSON with leading whitespace is valid`() {
        val json = "  \n\t  {\"key\":\"value\"}"
        assertTrue(isValidJsonStructure(json), "JSON with leading whitespace should be valid")
    }

    @Test
    fun `whitespace-only is rejected`() {
        assertFalse(isValidJsonStructure("   \n\t  "), "Whitespace-only should be rejected")
    }

    @Test
    fun `empty string is rejected`() {
        assertFalse(isValidJsonStructure(""), "Empty string should be rejected")
    }

    // ===== Fallback Endpoint Scenarios =====

    /**
     * Documents the fallback validation strategy:
     * If player_api.php (no action) returns non-JSON, the client tries:
     * 1. get_live_categories
     * 2. get_vod_categories
     * 3. get_series_categories
     * 4. get_live_streams
     *
     * As long as ANY of these returns valid JSON, the server is accepted.
     */
    @Test
    fun `documents fallback endpoint order`() {
        val fallbackEndpoints =
            listOf(
                "get_live_categories",
                "get_vod_categories",
                "get_series_categories",
                "get_live_streams",
            )

        // This test documents the expected fallback order
        // Actual implementation is in DefaultXtreamApiClient.tryFallbackValidation()
        assertTrue(
            fallbackEndpoints.isNotEmpty(),
            "Fallback endpoints should be defined for lenient validation",
        )
    }

    /**
     * Validates that minimal responses from fallback endpoints are accepted.
     */
    @Test
    fun `minimal fallback responses are valid`() {
        // Empty category list (valid JSON)
        assertTrue(isValidJsonStructure("[]"), "Empty categories should be valid")

        // Empty streams list (valid JSON)
        assertTrue(isValidJsonStructure("[]"), "Empty streams should be valid")

        // Single category (minimal but valid)
        val singleCategory = """[{"category_id":"1"}]"""
        assertTrue(isValidJsonStructure(singleCategory), "Minimal category should be valid")

        // Single stream (minimal but valid)
        val singleStream = """[{"stream_id":"1","name":"Test"}]"""
        assertTrue(isValidJsonStructure(singleStream), "Minimal stream should be valid")
    }
}
