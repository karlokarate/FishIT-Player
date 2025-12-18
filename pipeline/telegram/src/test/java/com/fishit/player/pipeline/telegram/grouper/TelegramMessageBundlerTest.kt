package com.fishit.player.pipeline.telegram.grouper

import com.fishit.player.infra.transport.telegram.api.TgContent
import com.fishit.player.infra.transport.telegram.api.TgMessage
import com.fishit.player.infra.transport.telegram.api.TgPhotoSize
import com.fishit.player.pipeline.telegram.model.TelegramBundleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TelegramMessageBundler].
 *
 * Per TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md Section 7.1:
 * - Test: Messages with same timestamp are grouped as BundleCandidate
 * - Test: Messages with different timestamps remain separate
 * - Test: Bundle classification (3-cluster, 2-cluster, Single)
 * - Test: Sorting within bundle by messageId
 * - Test: Cohesion Gate accepts valid candidate (messageId span ≤ 3×2²⁰)
 * - Test: Cohesion Gate rejects invalid candidate (too large span)
 * - Test: Cohesion Gate with albumId (primary discriminator)
 */
class TelegramMessageBundlerTest {

    private lateinit var bundler: TelegramMessageBundler

    @Before
    fun setUp() {
        bundler = TelegramMessageBundler()
    }

    // ========== Timestamp Grouping Tests ==========

    @Test
    fun `messages with same timestamp are grouped as BundleCandidate`() {
        val timestamp = 1731704712L
        val messages = listOf(
            createPhotoMessage(chatId = 123L, messageId = 100L, timestamp = timestamp),
            createTextMessage(chatId = 123L, messageId = 101L, timestamp = timestamp),
            createVideoMessage(chatId = 123L, messageId = 102L, timestamp = timestamp),
        )

        val bundles = bundler.groupByTimestamp(messages)

        assertEquals(1, bundles.size)
        assertEquals(3, bundles[0].messages.size)
        assertEquals(TelegramBundleType.FULL_3ER, bundles[0].bundleType)
    }

    @Test
    fun `messages with different timestamps remain separate`() {
        val messages = listOf(
            createVideoMessage(chatId = 123L, messageId = 100L, timestamp = 1731704712L),
            createVideoMessage(chatId = 123L, messageId = 200L, timestamp = 1731704800L),
            createVideoMessage(chatId = 123L, messageId = 300L, timestamp = 1731704900L),
        )

        val bundles = bundler.groupByTimestamp(messages)

        assertEquals(3, bundles.size)
        bundles.forEach { bundle ->
            assertEquals(TelegramBundleType.SINGLE, bundle.bundleType)
            assertEquals(1, bundle.videoCount)
        }
    }

    // ========== Bundle Classification Tests ==========

    @Test
    fun `classifies FULL_3ER bundle with PHOTO plus TEXT plus VIDEO`() {
        val messages = listOf(
            createPhotoMessage(chatId = 123L, messageId = 100L, timestamp = 1L),
            createTextMessage(chatId = 123L, messageId = 101L, timestamp = 1L),
            createVideoMessage(chatId = 123L, messageId = 102L, timestamp = 1L),
        )

        val bundleType = bundler.classifyBundle(messages)

        assertEquals(TelegramBundleType.FULL_3ER, bundleType)
    }

    @Test
    fun `classifies COMPACT_2ER bundle with TEXT plus VIDEO`() {
        val messages = listOf(
            createTextMessage(chatId = 123L, messageId = 100L, timestamp = 1L),
            createVideoMessage(chatId = 123L, messageId = 101L, timestamp = 1L),
        )

        val bundleType = bundler.classifyBundle(messages)

        assertEquals(TelegramBundleType.COMPACT_2ER, bundleType)
    }

    @Test
    fun `classifies COMPACT_2ER bundle with PHOTO plus VIDEO`() {
        val messages = listOf(
            createPhotoMessage(chatId = 123L, messageId = 100L, timestamp = 1L),
            createVideoMessage(chatId = 123L, messageId = 101L, timestamp = 1L),
        )

        val bundleType = bundler.classifyBundle(messages)

        assertEquals(TelegramBundleType.COMPACT_2ER, bundleType)
    }

    @Test
    fun `classifies SINGLE for video-only message`() {
        val messages = listOf(
            createVideoMessage(chatId = 123L, messageId = 100L, timestamp = 1L),
        )

        val bundleType = bundler.classifyBundle(messages)

        assertEquals(TelegramBundleType.SINGLE, bundleType)
    }

    // ========== Sorting Tests ==========

    @Test
    fun `messages within bundle are sorted by messageId`() {
        val timestamp = 1731704712L
        val messages = listOf(
            createVideoMessage(chatId = 123L, messageId = 388021760L, timestamp = timestamp),
            createPhotoMessage(chatId = 123L, messageId = 387924480L, timestamp = timestamp),
            createTextMessage(chatId = 123L, messageId = 387973120L, timestamp = timestamp),
        )

        val bundles = bundler.groupByTimestamp(messages)

        assertEquals(1, bundles.size)
        val sortedIds = bundles[0].messages.map { it.messageId }
        assertEquals(listOf(387924480L, 387973120L, 388021760L), sortedIds)
    }

    // ========== Cohesion Gate Tests ==========

    @Test
    fun `Cohesion Gate accepts valid candidate within MAX_MESSAGE_ID_SPAN`() {
        val messages = listOf(
            createVideoMessage(chatId = 123L, messageId = 1000000L, timestamp = 1L),
            createVideoMessage(chatId = 123L, messageId = 2048576L, timestamp = 1L), // Span = 1,048,576
        )

        val isCoherent = bundler.checkCohesion(messages)

        assertTrue("Span within threshold should be cohesive", isCoherent)
    }

    @Test
    fun `Cohesion Gate accepts candidate with exact EXPECTED_MESSAGE_ID_STEP`() {
        // Real Telegram pattern: messages differ by exactly 2^20 = 1,048,576
        val baseId = 387924480L
        val messages = listOf(
            createPhotoMessage(chatId = 123L, messageId = baseId, timestamp = 1L),
            createTextMessage(chatId = 123L, messageId = baseId + 1_048_576L, timestamp = 1L),
            createVideoMessage(chatId = 123L, messageId = baseId + 2_097_152L, timestamp = 1L),
        )

        val isCoherent = bundler.checkCohesion(messages)

        assertTrue("Step-pattern 2^20 should be cohesive", isCoherent)
    }

    @Test
    fun `Cohesion Gate rejects invalid candidate with too large span`() {
        val messages = listOf(
            createVideoMessage(chatId = 123L, messageId = 1000000L, timestamp = 1L),
            createVideoMessage(chatId = 123L, messageId = 100000000L, timestamp = 1L), // Span >> 3×2^20
        )

        val isCoherent = bundler.checkCohesion(messages)

        assertFalse("Large span without step-pattern should be rejected", isCoherent)
    }

    @Test
    fun `rejected candidate is split into SINGLE bundles`() {
        val timestamp = 1731704712L
        val messages = listOf(
            createVideoMessage(chatId = 123L, messageId = 1000000L, timestamp = timestamp),
            createVideoMessage(chatId = 123L, messageId = 999000000L, timestamp = timestamp), // Too far apart
        )

        val bundles = bundler.groupByTimestamp(messages)

        // Should be split into 2 SINGLE bundles
        assertEquals(2, bundles.size)
        bundles.forEach { bundle ->
            assertEquals(TelegramBundleType.SINGLE, bundle.bundleType)
        }
    }

    // ========== Multi-Video Bundle Tests ==========

    @Test
    fun `bundle with multiple VIDEOs has correct videoCount`() {
        val timestamp = 1731704712L
        val baseId = 100000000L
        val messages = listOf(
            createPhotoMessage(chatId = 123L, messageId = baseId, timestamp = timestamp),
            createTextMessage(chatId = 123L, messageId = baseId + 1_048_576L, timestamp = timestamp),
            createVideoMessage(chatId = 123L, messageId = baseId + 2_097_152L, timestamp = timestamp),
            createVideoMessage(chatId = 123L, messageId = baseId + 3_145_728L, timestamp = timestamp),
        )

        val bundles = bundler.groupByTimestamp(messages)

        assertEquals(1, bundles.size)
        assertEquals(2, bundles[0].videoCount)
        assertEquals(TelegramBundleType.FULL_3ER, bundles[0].bundleType)
    }

    // ========== Bundle Properties Tests ==========

    @Test
    fun `bundle properties are correctly populated`() {
        val timestamp = 1731704712L
        val chatId = -1001434421634L
        val baseId = 387924480L
        
        val messages = listOf(
            createPhotoMessage(chatId = chatId, messageId = baseId, timestamp = timestamp),
            createTextMessage(chatId = chatId, messageId = baseId + 1_048_576L, timestamp = timestamp),
            createVideoMessage(chatId = chatId, messageId = baseId + 2_097_152L, timestamp = timestamp),
        )

        val bundles = bundler.groupByTimestamp(messages)

        assertEquals(1, bundles.size)
        val bundle = bundles[0]
        
        assertEquals(timestamp, bundle.timestamp)
        assertEquals(chatId, bundle.chatId)
        assertEquals(TelegramBundleType.FULL_3ER, bundle.bundleType)
        assertTrue(bundle.hasStructuredMetadata)
        assertTrue(bundle.hasPoster)
        assertTrue(bundle.isComplete)
        assertNotNull(bundle.textMessage)
        assertNotNull(bundle.photoMessage)
        assertEquals(1, bundle.videoCount)
    }

    @Test
    fun `SINGLE bundle has no structured metadata or poster`() {
        val message = createVideoMessage(chatId = 123L, messageId = 100L, timestamp = 1L)
        
        val bundle = TelegramMessageBundle.single(message)

        assertEquals(TelegramBundleType.SINGLE, bundle.bundleType)
        assertFalse(bundle.hasStructuredMetadata)
        assertFalse(bundle.hasPoster)
        assertFalse(bundle.isComplete)
        assertNull(bundle.textMessage)
        assertNull(bundle.photoMessage)
    }

    // ========== Empty Input Tests ==========

    @Test
    fun `empty input returns empty list`() {
        val bundles = bundler.groupByTimestamp(emptyList())

        assertTrue(bundles.isEmpty())
    }

    // ========== Real-World Pattern Tests ==========

    @Test
    fun `Mel Brooks chat pattern - 3-cluster detection`() {
        // Real message IDs from JSON export analysis (Section 1.3)
        val timestamp = 1731704712L
        val chatId = -1001434421634L
        
        val messages = listOf(
            // PHOTO (lowest ID)
            createPhotoMessage(chatId = chatId, messageId = 387924480L, timestamp = timestamp),
            // TEXT
            createTextMessage(chatId = chatId, messageId = 387973120L, timestamp = timestamp),
            // VIDEO (highest ID)
            createVideoMessage(chatId = chatId, messageId = 388021760L, timestamp = timestamp),
        )

        val bundles = bundler.groupByTimestamp(messages)

        assertEquals(1, bundles.size)
        assertEquals(TelegramBundleType.FULL_3ER, bundles[0].bundleType)
        assertEquals(chatId, bundles[0].chatId)
        
        // Verify order: PHOTO → TEXT → VIDEO
        val bundle = bundles[0]
        assertEquals(387924480L, bundle.photoMessage?.messageId)
        assertEquals(387973120L, bundle.textMessage?.messageId)
        assertEquals(388021760L, bundle.videoMessages[0].messageId)
    }

    // ========== Helper Methods ==========

    private fun createVideoMessage(chatId: Long, messageId: Long, timestamp: Long): TgMessage =
        TgMessage(
            messageId = messageId,
            chatId = chatId,
            date = timestamp,
            content = TgContent.Video(
                fileId = 1,
                remoteId = "video_remote_$messageId",
                fileName = "movie.mkv",
                mimeType = "video/x-matroska",
                duration = 7200,
                width = 1920,
                height = 1080,
                fileSize = 5_000_000_000L,
                supportsStreaming = true,
            ),
        )

    private fun createPhotoMessage(chatId: Long, messageId: Long, timestamp: Long): TgMessage =
        TgMessage(
            messageId = messageId,
            chatId = chatId,
            date = timestamp,
            content = TgContent.Photo(
                sizes = listOf(
                    TgPhotoSize(
                        fileId = 1,
                        remoteId = "photo_remote_$messageId",
                        width = 1000,
                        height = 1500,
                        fileSize = 100_000L,
                    ),
                ),
            ),
        )

    private fun createTextMessage(chatId: Long, messageId: Long, timestamp: Long): TgMessage =
        TgMessage(
            messageId = messageId,
            chatId = chatId,
            date = timestamp,
            content = null, // TEXT messages have no media content
        )
}
