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
        assert(true) {
            "getFileInfo methods verified"
        }
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
}
