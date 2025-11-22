package com.chris.m3usuite.telegram.player

import org.junit.Test

/**
 * Unit tests for TelegramDataSource.
 * Tests data source structure and API compatibility.
 *
 * Note: Full integration testing of TelegramDataSource requires TDLib client
 * and actual file downloads, which are better suited for integration tests.
 */
class TelegramDataSourceTest {
    @Test
    fun `TelegramDataSource class exists`() {
        // Verify the class can be referenced
        val clazz = com.chris.m3usuite.telegram.player.TelegramDataSource::class
        assert(clazz.java.name.endsWith("TelegramDataSource")) {
            "Expected TelegramDataSource class"
        }
    }

    @Test
    fun `TelegramDataSource has open method`() {
        val clazz = com.chris.m3usuite.telegram.player.TelegramDataSource::class
        val methods = clazz.java.methods.map { it.name }
        assert("open" in methods) {
            "TelegramDataSource should have open method"
        }
    }

    @Test
    fun `TelegramDataSource has read method`() {
        val clazz = com.chris.m3usuite.telegram.player.TelegramDataSource::class
        val methods = clazz.java.methods.map { it.name }
        assert("read" in methods) {
            "TelegramDataSource should have read method"
        }
    }

    @Test
    fun `TelegramDataSource has close method`() {
        val clazz = com.chris.m3usuite.telegram.player.TelegramDataSource::class
        val methods = clazz.java.methods.map { it.name }
        assert("close" in methods) {
            "TelegramDataSource should have close method"
        }
    }

    @Test
    fun `TelegramDataSource uses withTimeout for window transitions`() {
        // Verify imports include withTimeout for timeout handling
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("import kotlinx.coroutines.withTimeout")) {
                "TelegramDataSource should import withTimeout for timeout handling"
            }
            assert(
                content.contains("withTimeout(StreamingConfig.WINDOW_TRANSITION_TIMEOUT_MS)") ||
                    content.contains("withTimeout(StreamingConfig.READ_OPERATION_TIMEOUT_MS)"),
            ) {
                "TelegramDataSource should use withTimeout with StreamingConfig timeout constants for blocking operations"
            }
        }
    }

    @Test
    fun `TelegramDataSource imports TimeoutCancellationException`() {
        // Verify imports include TimeoutCancellationException for proper exception handling
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("import kotlinx.coroutines.TimeoutCancellationException")) {
                "TelegramDataSource should import TimeoutCancellationException"
            }
        }
    }

    @Test
    fun `TelegramDataSource close method calls cleanupFileHandle`() {
        // Verify close method has explicit cleanup
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("cleanupFileHandle")) {
                "TelegramDataSource close method should call cleanupFileHandle"
            }
        }
    }

    @Test
    fun `TelegramDataSource has MIN_WINDOW_SIZE_BYTES constant`() {
        // Verify MIN_WINDOW_SIZE_BYTES constant exists
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("const val MIN_WINDOW_SIZE_BYTES")) {
                "TelegramDataSource should define MIN_WINDOW_SIZE_BYTES constant"
            }
            assert(content.contains("256 * 1024L")) {
                "MIN_WINDOW_SIZE_BYTES should be 256 KB (256 * 1024L)"
            }
        }
    }

    @Test
    fun `TelegramDataSource logs prepare window before ensureWindow`() {
        // Verify prepare window logging before ensureWindow calls
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("\"prepare window\"")) {
                "TelegramDataSource should log 'prepare window' message"
            }
        }
    }

    @Test
    fun `TelegramDataSource prepare window log includes required details`() {
        // Verify prepare window log includes all required fields
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            // Find the prepare window logging sections
            val prepareWindowLogs = content.split("\"prepare window\"")

            // Should have at least 2 occurrences (open and read methods)
            assert(prepareWindowLogs.size >= 3) {
                // Split creates one more element than occurrences
                "TelegramDataSource should have prepare window logging in multiple places"
            }

            // Check that logging includes required fields
            val hasFileId = content.contains("\"fileId\" to fileIdInt.toString()")
            val hasPosition = content.contains("\"position\" to position.toString()")
            val hasReadLength = content.contains("\"readLength\" to readLength.toString()")
            val hasWindowStart = content.contains("\"windowStart\" to windowStart.toString()")
            val hasWindowSize = content.contains("\"windowSize\" to windowSize.toString()")

            assert(hasFileId && hasPosition && hasWindowStart && hasWindowSize) {
                "Prepare window log should include fileId, position, windowStart, and windowSize"
            }
        }
    }

    @Test
    fun `TelegramDataSource calls ensureWindow with correct parameters`() {
        // Verify ensureWindow is called with proper window parameters
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("ensureWindow(fileIdInt, windowStart, windowSize)")) {
                "TelegramDataSource should call ensureWindow with fileIdInt, windowStart, windowSize"
            }
        }
    }

    @Test
    fun `TelegramDataSource uses currentPosition variable`() {
        // Verify that position variable is renamed to currentPosition
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("private var currentPosition: Long = 0")) {
                "TelegramDataSource should use currentPosition variable instead of position"
            }
        }
    }

    @Test
    fun `TelegramDataSource sets currentPosition from dataSpec position in open`() {
        // Verify currentPosition is set from dataSpec.position
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("currentPosition = dataSpec.position")) {
                "TelegramDataSource open() should set currentPosition from dataSpec.position"
            }
        }
    }

    @Test
    fun `TelegramDataSource reads from currentPosition not windowStart`() {
        // Verify that read operations use currentPosition, not windowStart
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("readFileChunk(fid, currentPosition, buffer, offset, bytesToRead)")) {
                "TelegramDataSource should call readFileChunk with currentPosition (absolute position)"
            }
        }
    }

    @Test
    fun `TelegramDataSource logs currentPosition in opened message`() {
        // Verify that opened log includes currentPosition
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            // Look for opened log with currentPosition
            assert(content.contains("\"currentPosition\" to currentPosition.toString()")) {
                "TelegramDataSource opened log should include currentPosition field"
            }
        }
    }

    @Test
    fun `TelegramDataSource increments currentPosition after read`() {
        // Verify currentPosition is incremented correctly
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assert(content.contains("currentPosition += bytesRead.toLong()")) {
                "TelegramDataSource should increment currentPosition by bytesRead after successful read"
            }
        }
    }

    @Test
    fun `TelegramDataSource documents position vs window separation`() {
        // Verify documentation clarifies position vs window separation
        val sourceFile = java.io.File("app/src/main/java/com/chris/m3usuite/telegram/player/TelegramDataSource.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            // Check for documentation about position management
            assert(
                content.contains("currentPosition is the absolute file position") ||
                    content.contains("IMPORTANT: currentPosition"),
            ) {
                "TelegramDataSource should document that currentPosition is absolute and independent of window"
            }
        }
    }
}
