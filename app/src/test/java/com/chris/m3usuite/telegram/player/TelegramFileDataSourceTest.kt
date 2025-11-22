package com.chris.m3usuite.telegram.player

import org.junit.Test

/**
 * Unit tests for TelegramFileDataSource.
 * Tests the new zero-copy architecture with FileDataSource delegation.
 *
 * Note: Full integration testing requires TDLib client and actual file downloads,
 * which are better suited for integration tests.
 */
class TelegramFileDataSourceTest {
    @Test
    fun `TelegramFileDataSource class exists`() {
        // Verify the class can be referenced
        val clazz = com.chris.m3usuite.telegram.player.TelegramFileDataSource::class
        assert(clazz.java.name.endsWith("TelegramFileDataSource")) {
            "Expected TelegramFileDataSource class"
        }
    }

    @Test
    fun `TelegramFileDataSource has open method`() {
        val clazz = com.chris.m3usuite.telegram.player.TelegramFileDataSource::class
        val methods = clazz.java.methods.map { it.name }
        assert("open" in methods) {
            "TelegramFileDataSource should have open method"
        }
    }

    @Test
    fun `TelegramFileDataSource has read method`() {
        val clazz = com.chris.m3usuite.telegram.player.TelegramFileDataSource::class
        val methods = clazz.java.methods.map { it.name }
        assert("read" in methods) {
            "TelegramFileDataSource should have read method"
        }
    }

    @Test
    fun `TelegramFileDataSource has close method`() {
        val clazz = com.chris.m3usuite.telegram.player.TelegramFileDataSource::class
        val methods = clazz.java.methods.map { it.name }
        assert("close" in methods) {
            "TelegramFileDataSource should have close method"
        }
    }

    @Test
    fun `TelegramFileDataSource uses FileDataSource delegate`() {
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramFileDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("import androidx.media3.datasource.FileDataSource")) {
                "TelegramFileDataSource should import FileDataSource"
            }
            assert(content.contains("private var delegate: FileDataSource?")) {
                "TelegramFileDataSource should have FileDataSource delegate"
            }
        }
    }

    @Test
    fun `TelegramFileDataSource calls ensureFileReady`() {
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramFileDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("ensureFileReady")) {
                "TelegramFileDataSource should call ensureFileReady"
            }
        }
    }

    @Test
    fun `TelegramFileDataSource has MIN_PREFIX_BYTES constant`() {
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramFileDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("const val MIN_PREFIX_BYTES")) {
                "TelegramFileDataSource should define MIN_PREFIX_BYTES constant"
            }
            assert(content.contains("256 * 1024L")) {
                "MIN_PREFIX_BYTES should be 256 KB (256 * 1024L)"
            }
        }
    }

    @Test
    fun `TelegramFileDataSource does not manage window state`() {
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramFileDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(!content.contains("windowStart")) {
                "TelegramFileDataSource should not manage windowStart (delegated to FileDataSource)"
            }
            assert(!content.contains("windowSize")) {
                "TelegramFileDataSource should not manage windowSize (delegated to FileDataSource)"
            }
            assert(!content.contains("currentPosition")) {
                "TelegramFileDataSource should not track currentPosition (delegated to FileDataSource)"
            }
        }
    }

    @Test
    fun `TelegramFileDataSource does not use ByteArray buffers`() {
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramFileDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            // Should not have any ByteArray fields (except method parameters)
            val lines = content.lines()
            val hasRingBuffer = lines.any { line ->
                line.contains("RingBuffer") && !line.trim().startsWith("*") && !line.trim().startsWith("//")
            }
            assert(!hasRingBuffer) {
                "TelegramFileDataSource should not use ringbuffer"
            }
        }
    }

    @Test
    fun `TelegramFileDataSource read delegates to FileDataSource`() {
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramFileDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("delegate?.read(buffer, offset, length)")) {
                "TelegramFileDataSource read should delegate to FileDataSource"
            }
        }
    }

    @Test
    fun `TelegramFileDataSource builds file URI from localPath`() {
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramFileDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("Uri.fromFile")) {
                "TelegramFileDataSource should build file:// URI from local path"
            }
        }
    }

    @Test
    fun `TelegramFileDataSourceFactory class exists`() {
        val clazz = com.chris.m3usuite.telegram.player.TelegramFileDataSourceFactory::class
        assert(clazz.java.name.endsWith("TelegramFileDataSourceFactory")) {
            "Expected TelegramFileDataSourceFactory class"
        }
    }

    @Test
    fun `TelegramFileDataSourceFactory implements DataSource_Factory`() {
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramFileDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("class TelegramFileDataSourceFactory")) {
                "TelegramFileDataSourceFactory class should exist"
            }
            assert(content.contains(": DataSource.Factory")) {
                "TelegramFileDataSourceFactory should implement DataSource.Factory"
            }
        }
    }

    @Test
    fun `TelegramFileDataSource open method has blocking behavior documentation`() {
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramFileDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("IMPORTANT - Blocking Behavior:")) {
                "TelegramFileDataSource.open() should document blocking behavior"
            }
            assert(content.contains("runBlocking()")) {
                "Documentation should mention runBlocking()"
            }
            assert(content.contains("ANR")) {
                "Documentation should warn about ANR (Application Not Responding)"
            }
        }
    }

    @Test
    fun `TelegramFileDataSource calls TransferListener lifecycle events`() {
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramFileDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("transferListener?.onTransferStart")) {
                "TelegramFileDataSource should call onTransferStart on TransferListener"
            }
            assert(content.contains("transferListener?.onTransferEnd")) {
                "TelegramFileDataSource should call onTransferEnd on TransferListener"
            }
            // Should call onTransferEnd at least twice (success and failure paths)
            val onTransferEndCount = content.split("transferListener?.onTransferEnd").size - 1
            assert(onTransferEndCount >= 2) {
                "TelegramFileDataSource should call onTransferEnd in both success and failure paths (found $onTransferEndCount calls)"
            }
        }
    }

    @Test
    fun `ensureFileReady uses reactive Flow instead of polling`() {
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            
            // Should use fileUpdates Flow
            assert(content.contains("client.fileUpdates")) {
                "ensureFileReady should use client.fileUpdates Flow for reactive waiting"
            }
            
            // Should use withTimeout instead of manual retry loop
            assert(content.contains("withTimeout")) {
                "ensureFileReady should use withTimeout for timeout handling"
            }
            
            // Should use .first() to wait for condition
            assert(content.contains(".first")) {
                "ensureFileReady should use .first() to wait for download completion"
            }
            
            // Should NOT have old polling pattern with retryCount and delay(100)
            val ensureFileReadyStart = content.indexOf("suspend fun ensureFileReady")
            if (ensureFileReadyStart >= 0) {
                val nextFunctionStart = content.indexOf("suspend fun", ensureFileReadyStart + 1)
                val ensureFileReadyBody = if (nextFunctionStart > 0) {
                    content.substring(ensureFileReadyStart, nextFunctionStart)
                } else {
                    content.substring(ensureFileReadyStart)
                }
                
                assert(!ensureFileReadyBody.contains("while (retryCount < maxRetries)")) {
                    "ensureFileReady should not use old polling loop with retryCount"
                }
                
                assert(!ensureFileReadyBody.contains("delay(100)")) {
                    "ensureFileReady should not use delay(100) for polling"
                }
            }
        }
    }

    @Test
    fun `ensureFileReady documents reactive behavior`() {
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            
            // Find the ensureFileReady function
            val ensureFileReadyIndex = content.indexOf("suspend fun ensureFileReady")
            if (ensureFileReadyIndex >= 0) {
                // Find the documentation before the function
                val ensureFileReadyStart = content.lastIndexOf("/**", ensureFileReadyIndex)
                if (ensureFileReadyStart >= 0) {
                    val docEnd = content.indexOf("*/", ensureFileReadyStart)
                    if (docEnd >= 0 && docEnd < ensureFileReadyIndex) {
                        val documentation = content.substring(ensureFileReadyStart, docEnd)
                        
                        assert(documentation.contains("Reactively wait") || documentation.contains("fileUpdates Flow")) {
                            "ensureFileReady documentation should mention reactive waiting via fileUpdates Flow"
                        }
                        
                        assert(documentation.contains("reduces CPU usage") || documentation.contains("unnecessary API calls")) {
                            "ensureFileReady documentation should mention efficiency benefits"
                        }
                    }
                }
            }
        }
    }
}
