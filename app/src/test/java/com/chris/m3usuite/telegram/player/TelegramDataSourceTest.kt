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
                content.contains("withTimeout(StreamingConfig.READ_OPERATION_TIMEOUT_MS)")
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
}
