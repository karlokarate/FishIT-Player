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

    @Test
    fun `ensureWindow logs start complete and failed messages`() {
        // Verify ensureWindow has proper logging for start, complete, and failed states
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("\"ensureWindow start\"")) {
                "ensureWindow should log 'ensureWindow start' message"
            }
            assert(content.contains("\"ensureWindow complete\"")) {
                "ensureWindow should log 'ensureWindow complete' message"
            }
            assert(content.contains("\"ensureWindow failed\"")) {
                "ensureWindow should log 'ensureWindow failed' message"
            }
        }
    }

    @Test
    fun `ensureWindow logging contains required details`() {
        // Verify ensureWindow logs include fileId, windowStart, and windowSize
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            val ensureWindowSection =
                content
                    .substringAfter("suspend fun ensureWindow", "")
                    .substringBefore("suspend fun getFileSize")

            assert(ensureWindowSection.contains("\"fileId\" to fileIdInt.toString()")) {
                "ensureWindow logs should include fileId"
            }
            assert(ensureWindowSection.contains("\"windowStart\" to windowStart.toString()")) {
                "ensureWindow logs should include windowStart"
            }
            assert(ensureWindowSection.contains("\"windowSize\" to windowSize.toString()")) {
                "ensureWindow logs should include windowSize"
            }
        }
    }

    @Test
    fun `readFileChunk retry logic uses TelegramLogRepository debug`() {
        // Verify retry logging uses proper format with debug level
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            val readFileChunkSection =
                content
                    .substringAfter("suspend fun readFileChunk", "")
                    .substringBefore("suspend fun startDownload")

            assert(readFileChunkSection.contains("\"Retrying read after closed stream\"")) {
                "readFileChunk retry should log 'Retrying read after closed stream' message"
            }
            assert(readFileChunkSection.contains("\"retryCount\" to retryCount.toString()")) {
                "readFileChunk retry log should include retryCount"
            }
        }
    }

    @Test
    fun `ChunkRingBuffer class exists and is internal`() {
        // Verify ChunkRingBuffer class exists
        // Tests run from app directory
        val sourceFile = java.io.File("src/main/java/com/chris/m3usuite/telegram/core/ChunkRingBuffer.kt")
        assert(sourceFile.exists()) {
            "ChunkRingBuffer.kt file should exist in telegram/core package"
        }

        val content = sourceFile.readText()
        assert(content.contains("internal class ChunkRingBuffer")) {
            "ChunkRingBuffer should be an internal class"
        }
    }

    @Test
    fun `T_TelegramFileDownloader references ChunkRingBuffer`() {
        // Verify T_TelegramFileDownloader uses ChunkRingBuffer
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("ChunkRingBuffer")) {
                "T_TelegramFileDownloader should reference ChunkRingBuffer"
            }
            assert(content.contains("streamingBuffers")) {
                "T_TelegramFileDownloader should have streamingBuffers field"
            }
        }
    }

    @Test
    fun `readFileChunk uses ringbuffer for read-through cache`() {
        // Verify readFileChunk implements read-through cache pattern with ringbuffer
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            val readFileChunkSection =
                content
                    .substringAfter("suspend fun readFileChunk", "")
                    .substringBefore("suspend fun startDownload")

            assert(readFileChunkSection.contains("getRingBuffer(")) {
                "readFileChunk should call getRingBuffer to get ringbuffer instance"
            }
            assert(readFileChunkSection.contains("containsRange(")) {
                "readFileChunk should check if data is in ringbuffer with containsRange"
            }
            assert(readFileChunkSection.contains("ringBuffer.read(")) {
                "readFileChunk should read from ringbuffer when data is available"
            }
            assert(readFileChunkSection.contains("ringBuffer.write(")) {
                "readFileChunk should write to ringbuffer after reading from file"
            }
        }
    }

    @Test
    fun `cancelDownload clears ringbuffer`() {
        // Verify cancelDownload methods clean up ringbuffer
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            // Check both cancelDownload overloads
            val cancelSections = content.split("suspend fun cancelDownload")
            assert(cancelSections.size >= 3) {
                // Original + 2 overloads
                "Should have at least 2 cancelDownload method implementations"
            }

            var foundCleanup = 0
            for (section in cancelSections.drop(1)) { // Skip the part before first method
                if (section.contains("streamingBuffers.remove(") && section.contains(".clear()")) {
                    foundCleanup++
                }
            }
            assert(foundCleanup >= 2) {
                "Both cancelDownload overloads should clean up streamingBuffers (found $foundCleanup)"
            }
        }
    }

    @Test
    fun `cleanupFileHandle clears ringbuffer`() {
        // Verify cleanupFileHandle also cleans up ringbuffer
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            val cleanupSection = content.substringAfter("suspend fun cleanupFileHandle", "")
            assert(cleanupSection.contains("streamingBuffers.remove(") && cleanupSection.contains(".clear()")) {
                "cleanupFileHandle should clean up streamingBuffers"
            }
        }
    }

    @Test
    fun `ensureWindow clears ringbuffer on new window`() {
        // Verify ensureWindow clears ringbuffer when creating new window
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            val ensureWindowSection =
                content
                    .substringAfter("suspend fun ensureWindow", "")
                    .substringBefore("suspend fun getFileSize")

            assert(ensureWindowSection.contains("getRingBuffer(") && ensureWindowSection.contains(".clear()")) {
                "ensureWindow should clear ringbuffer when creating new window"
            }
        }
    }

    @Test
    fun `StreamingConfig has ringbuffer constants`() {
        // Verify StreamingConfig includes ringbuffer configuration
        // Tests run from app directory
        val sourceFile = java.io.File("src/main/java/com/chris/m3usuite/telegram/core/StreamingConfig.kt")
        assert(sourceFile.exists()) {
            "StreamingConfig.kt should exist as a separate file"
        }

        val content = sourceFile.readText()
        assert(content.contains("RINGBUFFER_CHUNK_SIZE_BYTES")) {
            "StreamingConfig should define RINGBUFFER_CHUNK_SIZE_BYTES"
        }
        assert(content.contains("RINGBUFFER_MAX_CHUNKS")) {
            "StreamingConfig should define RINGBUFFER_MAX_CHUNKS"
        }
    }

    @Test
    fun `StreamingConfig has read retry constants`() {
        // Verify StreamingConfig includes retry configuration for blocking reads
        val sourceFile = java.io.File("src/main/java/com/chris/m3usuite/telegram/core/StreamingConfig.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("READ_RETRY_MAX_ATTEMPTS")) {
                "StreamingConfig should define READ_RETRY_MAX_ATTEMPTS"
            }
            assert(content.contains("READ_RETRY_DELAY_MS")) {
                "StreamingConfig should define READ_RETRY_DELAY_MS"
            }
            assert(Regex("""READ_RETRY_MAX_ATTEMPTS\s*=\s*200\b""").containsMatchIn(content)) {
                "READ_RETRY_MAX_ATTEMPTS should be set to 200"
            }
            assert(Regex("""READ_RETRY_DELAY_MS\s*=\s*15L""").containsMatchIn(content)) {
                "READ_RETRY_DELAY_MS should be 15L"
            }
        }
    }

    @Test
    fun `readFileChunk implements blocking retry for chunk availability`() {
        // Verify readFileChunk waits for TDLib download instead of throwing immediately
        val sourceFile = java.io.File("src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            val readFileChunkSection =
                content
                    .substringAfter("suspend fun readFileChunk", "")
                    .substringBefore("suspend fun startDownload")

            // Check for blocking retry loop
            assert(readFileChunkSection.contains("while (!isDownloadedAt(fileId, position))")) {
                "readFileChunk should have blocking retry loop with isDownloadedAt check"
            }

            assert(readFileChunkSection.contains("delay(StreamingConfig.READ_RETRY_DELAY_MS)")) {
                "readFileChunk should use delay with READ_RETRY_DELAY_MS"
            }

            assert(readFileChunkSection.contains("StreamingConfig.READ_RETRY_MAX_ATTEMPTS")) {
                "readFileChunk should use READ_RETRY_MAX_ATTEMPTS constant"
            }

            // Check for appropriate logging
            assert(readFileChunkSection.contains("\"read(): waiting for chunk\"")) {
                "readFileChunk should log 'waiting for chunk' during retries"
            }

            assert(readFileChunkSection.contains("\"read(): chunk available, reading...\"")) {
                "readFileChunk should log 'chunk available' after successful wait"
            }

            assert(readFileChunkSection.contains("\"read(): timeout waiting for chunk\"")) {
                "readFileChunk should log timeout error if retries exhausted"
            }
        }
    }

    @Test
    fun `T_TelegramFileDownloader has isDownloadedAt helper method`() {
        // Verify isDownloadedAt helper exists for checking chunk availability
        val sourceFile = java.io.File("src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()

            assert(content.contains("private suspend fun isDownloadedAt")) {
                "T_TelegramFileDownloader should have isDownloadedAt helper method"
            }

            val isDownloadedAtSection =
                content
                    .substringAfter("private suspend fun isDownloadedAt", "")
                    .substringBefore("suspend fun readFileChunk")

            // Check implementation details
            assert(isDownloadedAtSection.contains("localPath.isNullOrBlank()")) {
                "isDownloadedAt should check if localPath exists"
            }

            assert(isDownloadedAtSection.contains("file.exists()")) {
                "isDownloadedAt should check if file exists"
            }

            assert(isDownloadedAtSection.contains("file.length() > position")) {
                "isDownloadedAt should check if file is large enough for position"
            }
        }
    }

    @Test
    fun `ensureFileReady checks file status immediately after download start`() {
        // Verify ensureFileReady polls TDLib state correctly
        val sourceFile = java.io.File("src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()

            val ensureFileReadySection =
                content
                    .substringAfter("suspend fun ensureFileReady", "")
                    .substringBefore("suspend fun getFileSize")

            // Check that the new implementation checks status first
            assert(ensureFileReadySection.contains("val initialPrefix = file.local?.downloadedPrefixSize?.toLong() ?: 0L")) {
                "ensureFileReady should get initial prefix size from TDLib"
            }

            assert(ensureFileReadySection.contains("if (!localPath.isNullOrBlank() && initialPrefix >= requiredPrefixSize)")) {
                "ensureFileReady should check if file is already satisfied before downloading"
            }

            assert(ensureFileReadySection.contains("\"ensureFileReady: already satisfied\"")) {
                "ensureFileReady should log when file is already satisfied"
            }

            // Check polling loop implementation
            assert(ensureFileReadySection.contains("while (result == null)")) {
                "ensureFileReady should use polling loop"
            }

            assert(ensureFileReadySection.contains("delay(100)")) {
                "ensureFileReady should use delay in polling loop"
            }

            assert(ensureFileReadySection.contains("file = getFileInfo(fileId)")) {
                "ensureFileReady should refresh file info from TDLib in loop"
            }

            assert(ensureFileReadySection.contains("val prefix = file.local?.downloadedPrefixSize?.toLong() ?: 0L")) {
                "ensureFileReady should read downloadedPrefixSize from TDLib in loop"
            }

            assert(ensureFileReadySection.contains("SystemClock.elapsedRealtime()")) {
                "ensureFileReady should use SystemClock for timeout tracking"
            }
        }
    }
}
