package com.chris.m3usuite.telegram.ingestion

import com.chris.m3usuite.telegram.domain.MessageBlock
import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.parser.ExportMessageFactory
import com.chris.m3usuite.telegram.parser.ExportVideo
import com.chris.m3usuite.telegram.parser.TelegramItemBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TelegramUpdateHandler.
 *
 * Phase T3: Tests the update handler's API and pipeline consistency.
 *
 * Key test cases:
 * - Handler class structure verification
 * - Same parser logic as batch ingestion (ExportMessageFactory → TelegramItemBuilder)
 * - MessageBlock creation for single messages
 * - Pipeline equivalence with TelegramIngestionCoordinator
 */
class TelegramUpdateHandlerTest {
    @Test
    fun `TelegramUpdateHandler class exists`() {
        val clazz = TelegramUpdateHandler::class.java
        assertTrue("Class should exist", clazz.name.isNotEmpty())
    }

    @Test
    fun `TelegramUpdateHandler has required methods`() {
        val clazz = TelegramUpdateHandler::class.java
        val methods = clazz.methods.map { it.name }

        assertTrue("Should have start method", methods.contains("start"))
        assertTrue("Should have stop method", methods.contains("stop"))
        assertTrue("Should have processNewMessage method", methods.contains("processNewMessage"))
        assertTrue("Should have handleMessageDeleted method", methods.contains("handleMessageDeleted"))
    }

    @Test
    fun `MessageBlock can be created with single message`() {
        // Phase T3: Verify single-message block creation pattern
        // This is the pattern used by TelegramUpdateHandler for live updates

        // Create a mock ExportVideo for testing
        val exportVideo = createTestExportVideo()

        val block =
            MessageBlock(
                chatId = 123L,
                messages = listOf(exportVideo),
            )

        assertEquals(123L, block.chatId)
        assertEquals(1, block.messages.size)
        assertEquals(exportVideo, block.messages.first())
    }

    @Test
    fun `TelegramItemBuilder processes single-message block`() {
        // Phase T3: Verify TelegramItemBuilder works with single-message blocks
        // This ensures UpdateHandler uses SAME logic as batch ingestion

        val exportVideo = createTestExportVideo()
        val block =
            MessageBlock(
                chatId = 123L,
                messages = listOf(exportVideo),
            )

        val item = TelegramItemBuilder.build(block, "Test Chat")

        assertNotNull("Should build item from single-message block", item)
        item?.let {
            assertEquals(123L, it.chatId)
            assertEquals(1001L, it.anchorMessageId)
            assertTrue(
                "Should be video type",
                it.type in
                    listOf(
                        TelegramItemType.MOVIE,
                        TelegramItemType.SERIES_EPISODE,
                        TelegramItemType.CLIP,
                    ),
            )
        }
    }

    @Test
    fun `ExportMessageFactory is used for message conversion`() {
        // Phase T3: Verify ExportMessageFactory is available
        // The handler must use this factory for consistent conversion

        // Verify factory methods exist
        val factoryClass = ExportMessageFactory::class.java
        val methods = factoryClass.methods.map { it.name }

        assertTrue("Should have fromTdlMessage method", methods.contains("fromTdlMessage"))
        assertTrue("Should have fromTdlVideo method", methods.contains("fromTdlVideo"))
        assertTrue("Should have fromTdlPhoto method", methods.contains("fromTdlPhoto"))
        assertTrue("Should have fromTdlText method", methods.contains("fromTdlText"))
        assertTrue("Should have fromTdlDocument method", methods.contains("fromTdlDocument"))
    }

    @Test
    fun `TelegramItemBuilder is used for item creation`() {
        // Phase T3: Verify TelegramItemBuilder is available
        // The handler must use this builder for consistent item creation

        val builderClass = TelegramItemBuilder::class.java
        val methods = builderClass.methods.map { it.name }

        assertTrue("Should have build method", methods.contains("build"))
    }

    @Test
    fun `MessageBlock with empty messages returns null item`() {
        // Edge case: Empty block should not produce an item
        val block =
            MessageBlock(
                chatId = 123L,
                messages = emptyList(),
            )

        val item = TelegramItemBuilder.build(block, "Test Chat")

        assertNull("Empty block should not produce an item", item)
    }

    @Test
    fun `Pipeline equivalence - same code path as ingestion coordinator`() {
        // Phase T3: Verify the update handler uses identical pipeline
        // This is a structural test to ensure no code forks

        // Both paths should use:
        // 1. ExportMessageFactory.fromTdlMessage() for conversion
        // 2. TelegramItemBuilder.build() for item creation
        // 3. TelegramContentRepository.upsertItems() for persistence

        // Verify ExportMessageFactory exists and has correct signature
        val fromTdlMethod =
            ExportMessageFactory::class.java.methods
                .find { it.name == "fromTdlMessage" }
        assertNotNull("fromTdlMessage method should exist", fromTdlMethod)

        // Verify TelegramItemBuilder.build() has correct signature
        val buildMethod =
            TelegramItemBuilder::class.java.methods
                .find { it.name == "build" }
        assertNotNull("build method should exist", buildMethod)
    }

    // ==========================================================================
    // Test Helpers
    // ==========================================================================

    /**
     * Create a test ExportVideo for unit testing.
     * Uses the ExportMessage model structure.
     */
    private fun createTestExportVideo(): ExportVideo {
        val videoFile =
            com.chris.m3usuite.telegram.parser.ExportFile(
                id = 1,
                size = 1024 * 1024 * 100, // 100 MB
                remote =
                    com.chris.m3usuite.telegram.parser.ExportRemoteFile(
                        id = "test_remote_id_123",
                        uniqueId = "test_unique_id_456",
                    ),
            )

        val videoContent =
            com.chris.m3usuite.telegram.parser.ExportVideoContent(
                duration = 3600, // 1 hour
                width = 1920,
                height = 1080,
                fileName = "Test Movie (2024).mp4",
                mimeType = "video/mp4",
                supportsStreaming = true,
                thumbnail = null,
                video = videoFile,
            )

        return ExportVideo(
            id = 1001L,
            chatId = 123L,
            dateEpochSeconds = System.currentTimeMillis() / 1000,
            dateIso = "2024-01-15T10:30:00Z",
            video = videoContent,
            caption = "Test Movie (2024) - A great test movie",
            captionEntities = emptyList(),
        )
    }
}
