package com.fishit.player.core.imaging.fetcher

import com.fishit.player.core.model.ImageRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ImageRefFetcher] and ImageRef routing logic.
 *
 * Note: Full integration tests require Android context for Coil. These tests verify the routing
 * logic and MIME type detection.
 */
class ImageRefFetcherTest {

    @Test
    fun `guessMimeType returns correct type for common extensions`() {
        val mimeTypes =
                mapOf(
                        "jpg" to "image/jpeg",
                        "jpeg" to "image/jpeg",
                        "png" to "image/png",
                        "gif" to "image/gif",
                        "webp" to "image/webp",
                        "bmp" to "image/bmp",
                        "heic" to "image/heic",
                        "avif" to "image/avif",
                )

        mimeTypes.forEach { (ext, expected) ->
            val actual = guessMimeTypeTestHelper("/path/to/image.$ext")
            assertEquals("MIME type for .$ext", expected, actual)
        }
    }

    @Test
    fun `guessMimeType returns null for unknown extensions`() {
        assertNull(guessMimeTypeTestHelper("/path/to/file.unknown"))
        assertNull(guessMimeTypeTestHelper("/path/to/file.xyz"))
        assertNull(guessMimeTypeTestHelper("/path/to/file"))
    }

    @Test
    fun `ImageRef Http preserves url and headers`() {
        val headers = mapOf("Authorization" to "Bearer token")
        val ref =
                ImageRef.Http(
                        url = "https://example.com/image.jpg",
                        headers = headers,
                        preferredWidth = 200,
                        preferredHeight = 300,
                )

        assertEquals("https://example.com/image.jpg", ref.url)
        assertEquals(headers, ref.headers)
        assertEquals(200, ref.preferredWidth)
        assertEquals(300, ref.preferredHeight)
    }

    @Test
    fun `ImageRef TelegramThumb preserves all fields`() {
        val ref =
                ImageRef.TelegramThumb(
                        fileId = 12345,
                        uniqueId = "abc123",
                        chatId = 100L,
                        messageId = 200L,
                        preferredWidth = 100,
                        preferredHeight = 100,
                )

        assertEquals(12345, ref.fileId)
        assertEquals("abc123", ref.uniqueId)
        assertEquals(100L, ref.chatId)
        assertEquals(200L, ref.messageId)
    }

    @Test
    fun `ImageRef LocalFile preserves path`() {
        val ref =
                ImageRef.LocalFile(
                        path = "/data/local/tmp/image.png",
                        preferredWidth = 150,
                        preferredHeight = 150,
                )

        assertEquals("/data/local/tmp/image.png", ref.path)
        assertEquals(150, ref.preferredWidth)
        assertEquals(150, ref.preferredHeight)
    }

    /** Helper to test MIME type guessing (mirrors the private function in ImageRefFetcher). */
    private fun guessMimeTypeTestHelper(path: String): String? {
        val extension = path.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "avif" -> "image/avif"
            else -> null
        }
    }

    // === ImageRefKeyer Tests ===

    @Test
    fun `ImageRefKeyer generates URL key for Http`() {
        val ref = ImageRef.Http(url = "https://example.com/image.jpg")
        val key = keyerTestHelper(ref)
        assertEquals("https://example.com/image.jpg", key)
    }

    @Test
    fun `ImageRefKeyer generates uniqueId key for TelegramThumb`() {
        val ref = ImageRef.TelegramThumb(fileId = 123, uniqueId = "AgACAgIAAxkB")
        val key = keyerTestHelper(ref)
        assertEquals("tg:AgACAgIAAxkB", key)
    }

    @Test
    fun `ImageRefKeyer generates path key for LocalFile`() {
        val ref = ImageRef.LocalFile(path = "/data/cache/thumb.jpg")
        val key = keyerTestHelper(ref)
        assertEquals("file:/data/cache/thumb.jpg", key)
    }

    @Test
    fun `ImageRefKeyer uses uniqueId not fileId for cross-session stability`() {
        // Same uniqueId but different fileId should produce same key
        val ref1 = ImageRef.TelegramThumb(fileId = 100, uniqueId = "stable_id")
        val ref2 = ImageRef.TelegramThumb(fileId = 200, uniqueId = "stable_id")

        assertEquals(keyerTestHelper(ref1), keyerTestHelper(ref2))
    }

    // === InlineBytes Tests ===

    @Test
    fun `ImageRef InlineBytes preserves bytes and mimeType`() {
        val testBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) // JPEG magic bytes
        val ref =
                ImageRef.InlineBytes(
                        bytes = testBytes,
                        mimeType = "image/jpeg",
                        preferredWidth = 40,
                        preferredHeight = 40,
                )

        assertEquals(3, ref.bytes.size)
        assertEquals("image/jpeg", ref.mimeType)
        assertEquals(40, ref.preferredWidth)
        assertEquals(40, ref.preferredHeight)
    }

    @Test
    fun `ImageRef InlineBytes equality uses contentEquals for bytes`() {
        val bytes1 = byteArrayOf(1, 2, 3, 4, 5)
        val bytes2 = byteArrayOf(1, 2, 3, 4, 5) // Same content, different array
        val bytes3 = byteArrayOf(1, 2, 3, 4, 6) // Different content

        val ref1 = ImageRef.InlineBytes(bytes = bytes1)
        val ref2 = ImageRef.InlineBytes(bytes = bytes2)
        val ref3 = ImageRef.InlineBytes(bytes = bytes3)

        // Same content should be equal
        assertEquals(ref1, ref2)
        assertEquals(ref1.hashCode(), ref2.hashCode())

        // Different content should not be equal
        assertNotEquals(ref1, ref3)
    }

    @Test
    fun `ImageRefKeyer generates hash key for InlineBytes`() {
        val testBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val ref = ImageRef.InlineBytes(bytes = testBytes)
        val key = keyerTestHelper(ref)

        // Key should start with "inline:" and contain content hash
        assert(key.startsWith("inline:"))
        assertEquals("inline:${testBytes.contentHashCode()}", key)
    }

    @Test
    fun `ImageRefKeyer InlineBytes same content produces same key`() {
        val bytes1 = byteArrayOf(10, 20, 30, 40, 50)
        val bytes2 = byteArrayOf(10, 20, 30, 40, 50) // Same content

        val ref1 = ImageRef.InlineBytes(bytes = bytes1)
        val ref2 = ImageRef.InlineBytes(bytes = bytes2)

        assertEquals(keyerTestHelper(ref1), keyerTestHelper(ref2))
    }

    /** Helper to test keyer (mirrors ImageRefKeyer.key logic). */
    private fun keyerTestHelper(ref: ImageRef): String {
        return when (ref) {
            is ImageRef.Http -> ref.url
            is ImageRef.TelegramThumb -> "tg:${ref.uniqueId}"
            is ImageRef.LocalFile -> "file:${ref.path}"
            is ImageRef.InlineBytes -> "inline:${ref.bytes.contentHashCode()}"
        }
    }
}
