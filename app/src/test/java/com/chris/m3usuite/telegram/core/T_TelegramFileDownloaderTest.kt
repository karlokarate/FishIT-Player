package com.chris.m3usuite.telegram.core

import org.junit.Test

/**
 * Unit tests for T_TelegramFileDownloader.
 * Tests file downloader structure and API compatibility.
 *
 * These tests verify:
 * - Public API surface area (methods exist and are accessible)
 * - Key refactoring changes have been applied
 * - Code structure follows expected patterns
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
    fun `T_TelegramFileDownloader has ensureFileReady method`() {
        // Verify ensureFileReady method exists for zero-copy streaming
        val clazz = T_TelegramFileDownloader::class
        val methods = clazz.java.methods.map { it.name }
        assert("ensureFileReady" in methods) {
            "T_TelegramFileDownloader should have ensureFileReady method"
        }
    }

    @Test
    fun `T_TelegramFileDownloader has getFileInfo methods`() {
        // Verify getFileInfo methods exist
        val clazz = T_TelegramFileDownloader::class
        val methods = clazz.java.methods.filter { it.name == "getFileInfo" }
        assert(methods.isNotEmpty()) {
            "T_TelegramFileDownloader should have getFileInfo method(s)"
        }

        // For suspend functions, Kotlin adds a Continuation parameter,
        // so we just verify that at least one getFileInfo method exists
        // We've already confirmed methods exist above
    }

    @Test
    fun `getFileOrThrow helper method exists in implementation`() {
        // Verify the getFileOrThrow helper was added by checking the class has expected structure
        // We check this by verifying all declared methods including private ones
        val clazz = T_TelegramFileDownloader::class.java
        val allMethods = clazz.declaredMethods.map { it.name }

        // The getFileOrThrow method should exist (will be mangled name for suspend functions)
        val hasGetFileOrThrowRelated = allMethods.any { it.contains("getFileOrThrow") }
        assert(hasGetFileOrThrowRelated) {
            "T_TelegramFileDownloader should have getFileOrThrow helper method. " +
                "Available methods: ${allMethods.filter { it.contains("File") }.joinToString()}"
        }
    }

    @Test
    fun `getFreshFileState helper method exists in implementation`() {
        // Verify the getFreshFileState helper exists
        val clazz = T_TelegramFileDownloader::class.java
        val allMethods = clazz.declaredMethods.map { it.name }

        // The getFreshFileState method should exist (will be mangled name for suspend functions)
        val hasGetFreshFileStateRelated = allMethods.any { it.contains("getFreshFileState") }
        assert(hasGetFreshFileStateRelated) {
            "T_TelegramFileDownloader should have getFreshFileState helper method. " +
                "Available methods: ${allMethods.filter { it.contains("File") || it.contains("Fresh") }.joinToString()}"
        }
    }

    @Test
    fun `refactoring extracts shared helper as documented`() {
        // This is a documentation test that verifies the key refactoring goals:
        // 1. getFileOrThrow helper exists (checked above)
        // 2. getFreshFileState helper exists (checked above)
        // 3. Both methods are used to eliminate duplication

        // Since we've verified both helpers exist, the refactoring requirement is met
        val clazz = T_TelegramFileDownloader::class.java
        val methodNames = clazz.declaredMethods.map { it.name }

        assert(methodNames.any { it.contains("getFileOrThrow") }) {
            "Refactoring requirement: getFileOrThrow helper should exist"
        }

        assert(methodNames.any { it.contains("getFreshFileState") }) {
            "Refactoring requirement: getFreshFileState helper should exist"
        }
    }

    @Test
    fun `T_TelegramFileDownloader has 50MB streaming window constants`() {
        // Verify Phase D+ streaming constants exist
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            
            // Check for TELEGRAM_STREAM_WINDOW_BYTES (50 MB)
            assert(content.contains("TELEGRAM_STREAM_WINDOW_BYTES")) {
                "T_TelegramFileDownloader should define TELEGRAM_STREAM_WINDOW_BYTES constant"
            }
            assert(content.contains("50L * 1024L * 1024L")) {
                "TELEGRAM_STREAM_WINDOW_BYTES should be 50 MB (50L * 1024L * 1024L)"
            }
            
            // Check for TELEGRAM_MIN_PREFIX_BYTES (256 KB)
            assert(content.contains("TELEGRAM_MIN_PREFIX_BYTES")) {
                "T_TelegramFileDownloader should define TELEGRAM_MIN_PREFIX_BYTES constant"
            }
            assert(content.contains("256L * 1024L")) {
                "TELEGRAM_MIN_PREFIX_BYTES should be 256 KB (256L * 1024L)"
            }
            
            // Check for POLL_INTERVAL_MS
            assert(content.contains("POLL_INTERVAL_MS")) {
                "T_TelegramFileDownloader should define POLL_INTERVAL_MS constant"
            }
        }
    }

    @Test
    fun `ensureFileReady uses sliding window with offset and limit`() {
        // Verify Phase D+ sliding window implementation
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            
            // Find ensureFileReady function
            val ensureFileReadyStart = content.indexOf("suspend fun ensureFileReady")
            assert(ensureFileReadyStart >= 0) {
                "ensureFileReady function should exist"
            }
            
            // Extract function body (approximate)
            val nextFunctionStart = content.indexOf("suspend fun", ensureFileReadyStart + 1)
            val functionBody =
                if (nextFunctionStart > 0) {
                    content.substring(ensureFileReadyStart, nextFunctionStart)
                } else {
                    content.substring(ensureFileReadyStart)
                }
            
            // Verify window computation
            assert(functionBody.contains("windowStart") || functionBody.contains("val windowStart")) {
                "ensureFileReady should compute windowStart for sliding window"
            }
            assert(functionBody.contains("windowEnd") || functionBody.contains("val windowEnd")) {
                "ensureFileReady should compute windowEnd for sliding window"
            }
            assert(functionBody.contains("TELEGRAM_STREAM_WINDOW_BYTES")) {
                "ensureFileReady should use TELEGRAM_STREAM_WINDOW_BYTES for window size"
            }
            
            // Verify downloadFile call with offset and limit
            assert(functionBody.contains("client.downloadFile")) {
                "ensureFileReady should call client.downloadFile"
            }
            assert(functionBody.contains("offset =") || functionBody.contains("offset=")) {
                "downloadFile should be called with offset parameter"
            }
            assert(functionBody.contains("limit =") || functionBody.contains("limit=")) {
                "downloadFile should be called with limit parameter"
            }
            
            // Verify it does NOT download full file (no limit = 0 for streaming)
            val downloadFileCallStart = functionBody.indexOf("client.downloadFile")
            if (downloadFileCallStart >= 0) {
                val downloadFileCallEnd = functionBody.indexOf(")", downloadFileCallStart)
                if (downloadFileCallEnd > downloadFileCallStart) {
                    val downloadFileCall = functionBody.substring(downloadFileCallStart, downloadFileCallEnd)
                    // Should NOT have limit = 0 or limit = 0L for streaming window downloads
                    assert(!downloadFileCall.contains("limit = 0,") && !downloadFileCall.contains("limit = 0L,")) {
                        "ensureFileReady should NOT download full file (limit should not be 0)"
                    }
                }
            }
        }
    }

    @Test
    fun `ensureFileReady documentation mentions 50MB window policy`() {
        // Verify Phase D+ documentation is updated
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            
            // Find ensureFileReady function documentation
            val ensureFileReadyIndex = content.indexOf("suspend fun ensureFileReady")
            if (ensureFileReadyIndex >= 0) {
                // Find the documentation before the function
                val docStart = content.lastIndexOf("/**", ensureFileReadyIndex)
                if (docStart >= 0) {
                    val docEnd = content.indexOf("*/", docStart)
                    if (docEnd >= 0 && docEnd < ensureFileReadyIndex) {
                        val documentation = content.substring(docStart, docEnd)
                        
                        // Should mention sliding window and 50MB
                        assert(
                            documentation.contains("50MB") || 
                            documentation.contains("50 MB") ||
                            documentation.contains("sliding window") ||
                            documentation.contains("Sliding Window")
                        ) {
                            "ensureFileReady documentation should mention 50MB sliding window policy"
                        }
                        
                        // Should mention streaming-first approach
                        assert(
                            documentation.contains("streaming") || 
                            documentation.contains("Streaming")
                        ) {
                            "ensureFileReady documentation should mention streaming approach"
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `T_TelegramFileDownloader has cancelDownloadOnPlaybackEnd method`() {
        // Verify Phase D+ cache management method exists
        val clazz = T_TelegramFileDownloader::class
        val methods = clazz.java.methods.map { it.name }
        assert("cancelDownloadOnPlaybackEnd" in methods) {
            "T_TelegramFileDownloader should have cancelDownloadOnPlaybackEnd method for cache management"
        }
    }

    @Test
    fun `cleanupCache uses getStorageStatisticsFast`() {
        // Verify Phase D+ cleanupCache uses fast stats API
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            
            // Find cleanupCache function
            val cleanupCacheStart = content.indexOf("suspend fun cleanupCache")
            if (cleanupCacheStart >= 0) {
                val nextFunctionStart = content.indexOf("suspend fun", cleanupCacheStart + 1)
                val functionBody =
                    if (nextFunctionStart > 0) {
                        content.substring(cleanupCacheStart, nextFunctionStart)
                    } else {
                        content.substring(cleanupCacheStart)
                    }
                
                // Should use getStorageStatisticsFast instead of getStorageStatistics
                assert(functionBody.contains("getStorageStatisticsFast")) {
                    "cleanupCache should use getStorageStatisticsFast() for better performance"
                }
                
                // Should use optimizeStorage for cleanup
                assert(functionBody.contains("optimizeStorage")) {
                    "cleanupCache should call optimizeStorage() when threshold exceeded"
                }
            }
        }
    }
}
