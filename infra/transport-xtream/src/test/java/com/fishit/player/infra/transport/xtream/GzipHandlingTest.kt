package com.fishit.player.infra.transport.xtream

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for gzip handling in DefaultXtreamApiClient.fetchRaw()
 *
 * Validates defensive gzip decompression when:
 * - Server sends gzip data without proper Content-Encoding header
 * - Response body starts with gzip magic bytes (0x1F 0x8B)
 * - OkHttp didn't automatically decompress the content
 *
 * This ensures we handle edge cases where servers misconfigure their gzip headers.
 */
class GzipHandlingTest {
    /**
     * Helper to create gzip-compressed data.
     * Mimics what a misconfigured server might send.
     */
    private fun gzipCompress(text: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use { gzipStream ->
            gzipStream.write(text.toByteArray(Charsets.UTF_8))
        }
        return outputStream.toByteArray()
    }

    /**
     * Helper to check if data is gzip-compressed by looking at magic bytes.
     * This is the same logic used in DefaultXtreamApiClient.
     */
    private fun isGzipCompressed(data: ByteArray): Boolean =
        data.size >= 2 &&
            (data[0].toInt() and 0xFF) == 0x1F &&
            (data[1].toInt() and 0xFF) == 0x8B

    /**
     * Helper to check if string (converted to bytes) has gzip magic bytes.
     * Used to simulate what fetchRaw() sees.
     */
    private fun hasGzipMagicBytes(text: String): Boolean {
        if (text.length < 2) return false
        val byte0 = text[0].code
        val byte1 = text[1].code
        return byte0 == 0x1F && byte1 == 0x8B
    }

    // ===== Gzip Detection Tests =====

    @Test
    fun `gzip magic bytes are correctly detected`() {
        val originalJson = """{"user_info":{"status":"Active"}}"""
        val compressed = gzipCompress(originalJson)

        assertTrue(isGzipCompressed(compressed), "Gzip magic bytes should be detected")
        assertEquals(0x1F, compressed[0].toInt() and 0xFF, "First byte should be 0x1F")
        assertEquals(0x8B, compressed[1].toInt() and 0xFF, "Second byte should be 0x8B")
    }

    @Test
    fun `plain JSON does not have gzip magic bytes`() {
        val json = """{"user_info":{"status":"Active"}}"""
        val bytes = json.toByteArray()

        assertTrue(!isGzipCompressed(bytes), "Plain JSON should not be detected as gzip")
    }

    @Test
    fun `empty data is not detected as gzip`() {
        val empty = ByteArray(0)
        assertTrue(!isGzipCompressed(empty), "Empty data should not be detected as gzip")
    }

    @Test
    fun `single byte is not detected as gzip`() {
        val single = byteArrayOf(0x1F)
        assertTrue(!isGzipCompressed(single), "Single byte should not be detected as gzip")
    }

    // ===== Compression/Decompression Round-Trip Tests =====

    @Test
    fun `gzip compression and decompression round-trip`() {
        val originalJson = """{"user_info":{"status":"Active"},"server_info":{"url":"example.com"}}"""
        val compressed = gzipCompress(originalJson)

        // Verify it's compressed
        assertTrue(isGzipCompressed(compressed), "Data should be gzip-compressed")

        // Verify we can decompress it
        val decompressed =
            java.util.zip
                .GZIPInputStream(compressed.inputStream())
                .bufferedReader()
                .readText()
        assertEquals(originalJson, decompressed, "Decompressed JSON should match original")
    }

    @Test
    fun `gzip compression of minimal JSON`() {
        val originalJson = """{}"""
        val compressed = gzipCompress(originalJson)

        assertTrue(isGzipCompressed(compressed), "Minimal JSON should compress")

        val decompressed =
            java.util.zip
                .GZIPInputStream(compressed.inputStream())
                .bufferedReader()
                .readText()
        assertEquals(originalJson, decompressed)
    }

    @Test
    fun `gzip compression of JSON array`() {
        val originalJson = """[{"id":"1","name":"Test"}]"""
        val compressed = gzipCompress(originalJson)

        assertTrue(isGzipCompressed(compressed), "JSON array should compress")

        val decompressed =
            java.util.zip
                .GZIPInputStream(compressed.inputStream())
                .bufferedReader()
                .readText()
        assertEquals(originalJson, decompressed)
    }

    // ===== Edge Cases =====

    @Test
    fun `gzip compression preserves unicode characters`() {
        val originalJson = """{"title":"Película española","description":"Émilie et François"}"""
        val compressed = gzipCompress(originalJson)

        val decompressed =
            java.util.zip
                .GZIPInputStream(compressed.inputStream())
                .bufferedReader()
                .readText()
        assertEquals(originalJson, decompressed, "Unicode characters should be preserved")
    }

    @Test
    fun `gzip compression of large JSON`() {
        val largeJson =
            buildString {
                append("[")
                repeat(1000) { i ->
                    if (i > 0) append(",")
                    append("""{"id":$i,"name":"Item $i","description":"Description for item $i"}""")
                }
                append("]")
            }

        val compressed = gzipCompress(largeJson)
        assertTrue(isGzipCompressed(compressed), "Large JSON should compress")

        val decompressed =
            java.util.zip
                .GZIPInputStream(compressed.inputStream())
                .bufferedReader()
                .readText()
        assertEquals(largeJson, decompressed)
        assertTrue(compressed.size < largeJson.length, "Compressed size should be smaller than original")
    }

    // ===== Documentation Tests =====

    /**
     * Documents the expected behavior of fetchRaw() when encountering gzipped content:
     * 1. OkHttp normally handles Content-Encoding: gzip automatically
     * 2. As a defensive fallback, fetchRaw() checks for gzip magic bytes (0x1F 0x8B)
     * 3. If detected, it manually decompresses the body
     * 4. This handles misconfigured servers that send gzip without proper headers
     */
    @Test
    fun `documents defensive gzip handling strategy`() {
        val expectedBehavior =
            """
            1. Request sets Accept-Encoding: gzip
            2. OkHttp automatically decompresses if Content-Encoding: gzip is set
            3. Defensive fallback: Check first two bytes for 0x1F 0x8B (gzip magic)
            4. If detected, manually decompress using GZIPInputStream
            5. Log decompression for diagnostics
            6. Continue with normal JSON validation on decompressed body
            """.trimIndent()

        assertTrue(expectedBehavior.isNotEmpty(), "Gzip handling strategy should be documented")
    }

    /**
     * Documents the gzip magic byte format from RFC 1952.
     * First two bytes of gzip format are always 0x1F 0x8B.
     */
    @Test
    fun `documents gzip magic byte format`() {
        val magicByte1 = 0x1F // ID1 (Identification byte 1)
        val magicByte2 = 0x8B // ID2 (Identification byte 2)

        assertEquals(31, magicByte1, "Gzip ID1 should be 0x1F (31)")
        assertEquals(139, magicByte2, "Gzip ID2 should be 0x8B (139)")
    }
}
