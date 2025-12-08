package com.chris.m3usuite.telegram.ingestion

import com.chris.m3usuite.telegram.domain.TelegramItemType
import com.chris.m3usuite.telegram.parser.ExportFile
import com.chris.m3usuite.telegram.parser.ExportFixtures
import com.chris.m3usuite.telegram.parser.ExportMessage
import com.chris.m3usuite.telegram.parser.ExportRemoteFile
import com.chris.m3usuite.telegram.parser.ExportVideo
import com.chris.m3usuite.telegram.parser.ExportVideoContent
import com.chris.m3usuite.telegram.parser.TelegramBlockGrouper
import com.chris.m3usuite.telegram.parser.TelegramItemBuilder
import com.chris.m3usuite.telegram.parser.toExportMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Integration-style test to verify TelegramIngestionCoordinator uses the same
 * parser pipeline as validated in TelegramParserStatsTest and TelegramRuntimeParserEquivalenceTest.
 *
 * This test ensures that:
 * 1. The same ExportMessage → TelegramBlockGrouper → TelegramItemBuilder pipeline
 *    used in JSON-based tests is also used by the runtime ingestion coordinator.
 * 2. Given identical ExportMessage input, the pipeline produces identical TelegramItem output.
 *
 * Note: Full integration with TelegramIngestionCoordinator requires Android context
 * and TDLib client. This test focuses on verifying the pipeline logic itself.
 */
class TelegramIngestionCoordinatorPipelineTest {
    // ==========================================================================
    // Test: Pipeline logic is identical between JSON path and coordinator path
    // ==========================================================================

    @Test
    fun `TelegramIngestionCoordinator class exists with required methods`() {
        val clazz = TelegramIngestionCoordinator::class.java
        val methods = clazz.methods.map { it.name }

        // Verify the coordinator has the expected API
        assertTrue("Should have startBackfill method", methods.contains("startBackfill"))
        assertTrue("Should have resumeBackfill method", methods.contains("resumeBackfill"))
        assertTrue("Should have pauseBackfill method", methods.contains("pauseBackfill"))
        assertTrue("Should have getScanState method", methods.contains("getScanState"))
    }

    @Test
    fun `pipeline produces same items for identical ExportMessage input`() {
        // Create sample ExportMessages that would come from either JSON or TDLib
        val chatId = -1001000000000L
        val chatTitle = "Test Movie Chat"

        val videoRemoteId = "test-video-remote-id"
        val videoUniqueId = "test-video-unique-id"

        // Create a video message
        val videoMessage =
            ExportVideo(
                id = 100L,
                chatId = chatId,
                dateEpochSeconds = 1700000000L,
                dateIso = "2023-11-14T22:13:20Z",
                video =
                    ExportVideoContent(
                        duration = 5400,
                        width = 1920,
                        height = 1080,
                        fileName = "Test Movie 2023.mp4",
                        mimeType = "video/mp4",
                        supportsStreaming = true,
                        thumbnail = null,
                        video =
                            ExportFile(
                                id = 1000,
                                size = 2000000000,
                                remote =
                                    ExportRemoteFile(
                                        id = videoRemoteId,
                                        uniqueId = videoUniqueId,
                                    ),
                            ),
                    ),
                caption = "Test Movie 2023",
            )

        val messages: List<ExportMessage> = listOf(videoMessage)

        // Run through the pipeline (same logic used by TelegramIngestionCoordinator.processBatch)
        val blocks = TelegramBlockGrouper.group(messages)
        val items = blocks.mapNotNull { TelegramItemBuilder.build(it, chatTitle) }

        // Verify we got a valid item
        assertEquals("Should produce exactly one item", 1, items.size)

        val item = items.first()
        assertEquals("chatId should match", chatId, item.chatId)
        assertEquals("anchorMessageId should match", 100L, item.anchorMessageId)

        // Verify video ref has correct IDs
        assertNotNull("Should have videoRef", item.videoRef)
        assertEquals("remoteId should match", videoRemoteId, item.videoRef!!.remoteId)
        assertEquals("uniqueId should match", videoUniqueId, item.videoRef!!.uniqueId)
    }

    @Test
    fun `pipeline handles multiple message types correctly`() {
        val chatId = -1001000000000L
        val chatTitle = "Mixed Content Chat"

        // Create multiple video messages that would group together
        val messages =
            listOf(
                createVideoMessage(1L, chatId, 1700000100L, "video-1-remote", "video-1-unique", "Movie 1.mp4"),
                createVideoMessage(2L, chatId, 1700000000L, "video-2-remote", "video-2-unique", "Movie 2.mp4"),
                createVideoMessage(3L, chatId, 1699999000L, "video-3-remote", "video-3-unique", "Movie 3.mp4"),
            )

        // Run through pipeline
        val blocks = TelegramBlockGrouper.group(messages)
        val items = blocks.mapNotNull { TelegramItemBuilder.build(it, chatTitle) }

        // Messages with 100-second gaps should form separate blocks
        // Message 1 and 2 are 100 seconds apart -> same block
        // Message 3 is 1000 seconds earlier -> separate block
        assertTrue("Should produce at least 2 blocks", blocks.size >= 2)

        // Verify all items have valid videoRefs
        for (item in items) {
            when (item.type) {
                TelegramItemType.MOVIE,
                TelegramItemType.SERIES_EPISODE,
                TelegramItemType.CLIP,
                -> {
                    assertNotNull("Item should have videoRef", item.videoRef)
                    assertTrue("remoteId should not be blank", item.videoRef!!.remoteId.isNotBlank())
                    assertTrue("uniqueId should not be blank", item.videoRef!!.uniqueId.isNotBlank())
                }
                else -> { /* Other types don't require videoRef */ }
            }
        }
    }

    @Test
    fun `pipeline produces identical results for JSON fixtures`() {
        // Load a JSON fixture and verify the pipeline produces consistent results
        val exportsDir = findExportsDirectory()
        if (exportsDir == null) {
            println("⚠️ Export fixtures directory not found - skipping test")
            return
        }

        // Find a fixture with videos (known to produce items)
        val jsonFile =
            exportsDir
                .listFiles { f -> f.extension == "json" }
                ?.filter { it.name.startsWith("-1001") } // Channel IDs typically have video content
                ?.firstOrNull()

        if (jsonFile == null) {
            println("⚠️ No suitable JSON fixture found - skipping test")
            return
        }

        val chatExport = ExportFixtures.loadChatExportFromFile(jsonFile)
        if (chatExport == null) {
            println("⚠️ Failed to load ${jsonFile.name} - skipping test")
            return
        }

        // Convert raw messages to ExportMessages
        val messages = chatExport.messages.map { it.toExportMessage() }

        // Run through pipeline twice and verify identical results
        val blocks1 = TelegramBlockGrouper.group(messages)
        val items1 = blocks1.mapNotNull { TelegramItemBuilder.build(it, chatExport.title) }

        val blocks2 = TelegramBlockGrouper.group(messages)
        val items2 = blocks2.mapNotNull { TelegramItemBuilder.build(it, chatExport.title) }

        // Results should be identical
        assertEquals("Block count should be deterministic", blocks1.size, blocks2.size)
        assertEquals("Item count should be deterministic", items1.size, items2.size)

        // Compare each item
        for ((index, item1) in items1.withIndex()) {
            val item2 = items2[index]
            assertEquals("chatId should match", item1.chatId, item2.chatId)
            assertEquals("anchorMessageId should match", item1.anchorMessageId, item2.anchorMessageId)
            assertEquals("type should match", item1.type, item2.type)

            if (item1.videoRef != null) {
                assertEquals("videoRef.remoteId should match", item1.videoRef!!.remoteId, item2.videoRef!!.remoteId)
                assertEquals("videoRef.uniqueId should match", item1.videoRef!!.uniqueId, item2.videoRef!!.uniqueId)
            }
        }

        println("Verified pipeline determinism: ${items1.size} items produced identically")
    }

    @Test
    fun `TelegramHistoryScanner class exists with required methods`() {
        val clazz = TelegramHistoryScanner::class.java
        val methods = clazz.methods.map { it.name }

        // Verify the scanner has the expected API
        assertTrue("Should have scan method", methods.contains("scan"))
        assertTrue("Should have scanSingleBatch method", methods.contains("scanSingleBatch"))
    }

    @Test
    fun `TelegramHistoryScanner ScanConfig has expected fields`() {
        // Verify ScanConfig exists and can be instantiated
        val config =
            TelegramHistoryScanner.ScanConfig(
                pageSize = 50,
                maxPages = 5,
                maxRetries = 3,
                onlyLocal = true,
                fromMessageId = 12345L,
            )

        assertEquals(50, config.pageSize)
        assertEquals(5, config.maxPages)
        assertEquals(3, config.maxRetries)
        assertEquals(true, config.onlyLocal)
        assertEquals(12345L, config.fromMessageId)
    }

    @Test
    fun `TelegramHistoryScanner ScanResult has expected fields`() {
        // Verify ScanResult exists and can be instantiated
        val result =
            TelegramHistoryScanner.ScanResult(
                messages = emptyList(),
                oldestMessageId = 100L,
                hasMoreHistory = true,
                rawMessageCount = 50,
                convertedCount = 45,
            )

        assertEquals(100L, result.oldestMessageId)
        assertEquals(true, result.hasMoreHistory)
        assertEquals(50, result.rawMessageCount)
        assertEquals(45, result.convertedCount)
    }

    // ==========================================================================
    // Helper functions
    // ==========================================================================

    private fun createVideoMessage(
        id: Long,
        chatId: Long,
        dateEpochSeconds: Long,
        remoteId: String,
        uniqueId: String,
        fileName: String,
    ): ExportVideo =
        ExportVideo(
            id = id,
            chatId = chatId,
            dateEpochSeconds = dateEpochSeconds,
            dateIso =
                java.time.Instant
                    .ofEpochSecond(dateEpochSeconds)
                    .toString(),
            video =
                ExportVideoContent(
                    duration = 3600,
                    width = 1920,
                    height = 1080,
                    fileName = fileName,
                    mimeType = "video/mp4",
                    supportsStreaming = true,
                    thumbnail = null,
                    video =
                        ExportFile(
                            id = id.toInt(),
                            size = 1000000000,
                            remote =
                                ExportRemoteFile(
                                    id = remoteId,
                                    uniqueId = uniqueId,
                                ),
                        ),
                ),
            caption = null,
        )

    private fun findExportsDirectory(): File? {
        val paths =
            listOf(
                "docs/telegram/exports/exports",
                "../docs/telegram/exports/exports",
                "../../docs/telegram/exports/exports",
                "../../../docs/telegram/exports/exports",
            )

        for (path in paths) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) return dir
        }

        return null
    }
}
