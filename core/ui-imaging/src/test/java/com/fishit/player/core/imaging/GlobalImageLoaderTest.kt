package com.fishit.player.core.imaging

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [GlobalImageLoader]. */
class GlobalImageLoaderTest {

    @Test
    fun `cache config has expected defaults`() {
        assertEquals(0.25, GlobalImageLoader.CacheConfig.MEMORY_CACHE_PERCENT, 0.001)
        assertEquals(512L * 1024 * 1024, GlobalImageLoader.CacheConfig.DISK_CACHE_SIZE_BYTES)
        assertEquals("image_cache", GlobalImageLoader.CacheConfig.DISK_CACHE_DIR)
    }

    @Test
    fun `http config has expected defaults`() {
        assertEquals(15L, GlobalImageLoader.HttpConfig.CONNECT_TIMEOUT_SECONDS)
        assertEquals(30L, GlobalImageLoader.HttpConfig.READ_TIMEOUT_SECONDS)
        assertEquals(30L, GlobalImageLoader.HttpConfig.WRITE_TIMEOUT_SECONDS)
        assertEquals(16, GlobalImageLoader.HttpConfig.MAX_REQUESTS)
        assertEquals(4, GlobalImageLoader.HttpConfig.MAX_REQUESTS_PER_HOST)
    }

    @Test
    fun `computeDynamicDiskCacheSize returns value within bounds`() {
        val tempDir =
                File(
                        System.getProperty("java.io.tmpdir"),
                        "test_cache_${System.currentTimeMillis()}"
                )
        tempDir.mkdirs()

        try {
            val size = GlobalImageLoader.computeDynamicDiskCacheSize(tempDir)

            // Should be at least 256 MB (minimum for 32-bit)
            val minSize = 256L * 1024 * 1024
            assertTrue("Size should be at least $minSize, was $size", size >= minSize)

            // Should be at most 768 MB (maximum for 64-bit)
            val maxSize = 768L * 1024 * 1024
            assertTrue("Size should be at most $maxSize, was $size", size <= maxSize)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `createDefaultOkHttpClient applies dispatcher limits`() {
        val client = GlobalImageLoader.createDefaultOkHttpClient()

        assertEquals(16, client.dispatcher.maxRequests)
        assertEquals(4, client.dispatcher.maxRequestsPerHost)
    }
}
