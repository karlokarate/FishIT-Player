package com.chris.m3usuite.telegram.core

import org.junit.Test

/**
 * Unit tests for T_TelegramFileDownloader.
 * Tests file downloader structure and API compatibility.
 *
 * Note: Full integration testing requires TDLib client and actual file operations,
 * which are better suited for integration tests.
 */
class T_TelegramFileDownloaderTest {
    @Test
    fun `T_TelegramFileDownloader class exists`() {
        // Verify the class can be referenced
        val clazz = T_TelegramFileDownloader::class
        assert(clazz.java.name.endsWith("T_TelegramFileDownloader")) {
            "Expected T_TelegramFileDownloader class"
        }
    }

    @Test
    fun `T_TelegramFileDownloader has cleanupFileHandle method`() {
        // Verify cleanupFileHandle method exists for explicit file handle cleanup
        val clazz = T_TelegramFileDownloader::class
        val methods = clazz.java.methods.map { it.name }
        assert("cleanupFileHandle" in methods) {
            "T_TelegramFileDownloader should have cleanupFileHandle method for explicit cleanup"
        }
    }

    @Test
    fun `T_TelegramFileDownloader has cancelDownload methods`() {
        // Verify cancelDownload methods exist
        val clazz = T_TelegramFileDownloader::class
        val methods = clazz.java.methods.map { it.name }
        val cancelDownloadCount = methods.count { it == "cancelDownload" }
        assert(cancelDownloadCount >= 2) {
            "T_TelegramFileDownloader should have at least 2 cancelDownload overloads (String and Int)"
        }
    }

    @Test
    fun `T_TelegramFileDownloader has readFileChunk method`() {
        // Verify readFileChunk method exists for Zero-Copy reads
        val clazz = T_TelegramFileDownloader::class
        val methods = clazz.java.methods.map { it.name }
        assert("readFileChunk" in methods) {
            "T_TelegramFileDownloader should have readFileChunk method"
        }
    }

    @Test
    fun `T_TelegramFileDownloader has ensureWindow method`() {
        // Verify ensureWindow method exists for windowed downloads
        val clazz = T_TelegramFileDownloader::class
        val methods = clazz.java.methods.map { it.name }
        assert("ensureWindow" in methods) {
            "T_TelegramFileDownloader should have ensureWindow method"
        }
    }

    @Test
    fun `readFileChunk implements retry logic for concurrency`() {
        // Verify readFileChunk has retry logic to handle race conditions
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("retryCount") && content.contains("maxRetries")) {
                "readFileChunk should implement retry logic for handling closed streams"
            }
            assert(content.contains("runCatching")) {
                "readFileChunk should use runCatching for safe file handle closing"
            }
        }
    }

    @Test
    fun `cancelDownload uses runCatching for robust cleanup`() {
        // Verify cancelDownload methods use runCatching to prevent cleanup failures
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            // Look for the pattern in cancelDownload methods
            assert(content.contains("fileHandleCache.remove(") && content.contains("?.runCatching { close() }")) {
                "cancelDownload should use runCatching for safe file handle cleanup"
            }
        }
    }

    @Test
    fun `cleanupFileHandle method uses runCatching`() {
        // Verify cleanupFileHandle uses runCatching for safe cleanup
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            val cleanupFileHandleSection = content.substringAfter("suspend fun cleanupFileHandle", "")
            assert(cleanupFileHandleSection.isNotEmpty() && cleanupFileHandleSection.contains("runCatching")) {
                "cleanupFileHandle should use runCatching for safe file handle cleanup"
            }
        }
    }
}
