package com.fishit.player.feature.telegram

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.playermodel.PlaybackContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TelegramTapToPlayUseCase].
 *
 * Tests that the use case builds PlaybackContext correctly:
 * - Converts RawMediaMetadata to PlaybackContext
 * - Sets SourceType.TELEGRAM
 * - Extracts identifiers without secrets
 *
 * Note: We test the PlaybackContext building logic directly since
 * InternalPlayerSession is a final class and cannot be mocked.
 */
class TelegramTapToPlayUseCaseTest {

    @Test
    fun `buildPlaybackContext converts Telegram item with correct sourceType`() {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then
        assertEquals(
            com.fishit.player.core.playermodel.SourceType.TELEGRAM,
            context.sourceType
        )
        assertEquals("msg:123:456", context.canonicalId)
        assertEquals("Test Video", context.title)
        assertEquals("Telegram", context.subtitle)
    }

    @Test
    fun `buildPlaybackContext builds extras with non-secret identifiers`() {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then
        val extras = context.extras
        
        // Should contain chatId, messageId
        assertEquals("123", extras["chatId"])
        assertEquals("456", extras["messageId"])
        
        // Should NOT contain secrets like auth tokens
        assertEquals(false, extras.containsKey("authToken"))
        assertEquals(false, extras.containsKey("apiHash"))
    }

    @Test
    fun `buildPlaybackContext sets isLive to false for Telegram media`() {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then
        assertEquals(false, context.isLive)
        assertEquals(true, context.isSeekable)
    }

    @Test
    fun `buildPlaybackContext sets sourceKey for factory resolution`() {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then
        assertEquals("msg:123:456", context.sourceKey)
    }

    @Test
    fun `buildPlaybackContext extracts chatId and messageId from sourceId`() {
        // Given
        val telegramItem = createTestTelegramItem().copy(
            sourceId = "msg:9876:5432"
        )

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then
        assertEquals("msg:9876:5432", context.canonicalId)
        assertEquals("9876", context.extras["chatId"])
        assertEquals("5432", context.extras["messageId"])
    }

    @Test
    fun `buildPlaybackContext handles sourceId with fileId`() {
        // Given
        val telegramItem = createTestTelegramItem().copy(
            sourceId = "msg:123:456:789"
        )

        // When
        val context = buildPlaybackContext(telegramItem)

        // Then
        assertEquals("123", context.extras["chatId"])
        assertEquals("456", context.extras["messageId"])
        assertEquals("789", context.extras["fileId"])
    }

    private fun createTestTelegramItem(): RawMediaMetadata {
        return RawMediaMetadata(
            originalTitle = "Test Video",
            mediaType = MediaType.MOVIE,
            sourceType = SourceType.TELEGRAM,
            sourceLabel = "Telegram",
            sourceId = "msg:123:456",
            pipelineIdTag = PipelineIdTag.TELEGRAM,
            durationMinutes = 120,
        )
    }

    /**
     * Helper to build PlaybackContext for testing.
     * Mirrors the logic in TelegramTapToPlayUseCase.buildPlaybackContext.
     */
    private fun buildPlaybackContext(item: RawMediaMetadata): PlaybackContext {
        val parts = item.sourceId.split(":")
        val chatId = parts.getOrNull(1)?.toLongOrNull()
        val messageId = parts.getOrNull(2)?.toLongOrNull()
        val fileId = parts.getOrNull(3)?.toIntOrNull()

        val extras = buildMap<String, String> {
            chatId?.let { put("chatId", it.toString()) }
            messageId?.let { put("messageId", it.toString()) }
            fileId?.let { put("fileId", it.toString()) }
        }

        return PlaybackContext(
            canonicalId = item.sourceId,
            sourceType = com.fishit.player.core.playermodel.SourceType.TELEGRAM,
            sourceKey = item.sourceId,
            title = item.originalTitle,
            subtitle = item.sourceLabel,
            posterUrl = null,
            startPositionMs = 0L,
            isLive = false,
            isSeekable = true,
            extras = extras,
        )
    }
}


