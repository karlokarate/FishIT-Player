package com.fishit.player.feature.telegram

import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PipelineIdTag
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.internal.session.InternalPlayerSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TelegramTapToPlayUseCase].
 *
 * Tests that the use case:
 * - Converts RawMediaMetadata to PlaybackContext correctly
 * - Sets SourceType.TELEGRAM
 * - Calls InternalPlayerSession.play()
 * - Validates input
 * - Handles errors appropriately
 */
class TelegramTapToPlayUseCaseTest {

    private lateinit var testPlayerSession: TestInternalPlayerSession
    private lateinit var useCase: TelegramTapToPlayUseCase

    @Before
    fun setup() {
        testPlayerSession = TestInternalPlayerSession()
        useCase = TelegramTapToPlayUseCase(testPlayerSession)
    }

    @Test
    fun `play converts Telegram item to PlaybackContext with correct sourceType`() = runTest {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        useCase.play(telegramItem)

        // Then
        val capturedContext = testPlayerSession.lastPlaybackContext
        assertEquals(
            com.fishit.player.core.playermodel.SourceType.TELEGRAM,
            capturedContext?.sourceType
        )
        assertEquals("msg:123:456", capturedContext?.canonicalId)
        assertEquals("Test Video", capturedContext?.title)
    }

    @Test
    fun `play builds PlaybackContext with non-secret extras`() = runTest {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        useCase.play(telegramItem)

        // Then
        val capturedContext = testPlayerSession.lastPlaybackContext
        val extras = capturedContext?.extras ?: emptyMap()
        
        // Should contain chatId, messageId
        assertEquals("123", extras["chatId"])
        assertEquals("456", extras["messageId"])
        
        // Should NOT contain secrets like auth tokens
        assertEquals(false, extras.containsKey("authToken"))
        assertEquals(false, extras.containsKey("apiHash"))
    }

    @Test
    fun `play sets isLive to false for Telegram media`() = runTest {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        useCase.play(telegramItem)

        // Then
        val capturedContext = testPlayerSession.lastPlaybackContext
        assertEquals(false, capturedContext?.isLive)
        assertEquals(true, capturedContext?.isSeekable)
    }

    @Test
    fun `play throws IllegalArgumentException for non-Telegram source`() = runTest {
        // Given
        val xtreamItem = createTestTelegramItem().copy(
            sourceType = SourceType.XTREAM
        )

        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            runTest { useCase.play(xtreamItem) }
        }
    }

    @Test
    fun `play calls playerSession play with correct context`() = runTest {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        useCase.play(telegramItem)

        // Then
        assertEquals(1, testPlayerSession.playCallCount)
        assertEquals("msg:123:456", testPlayerSession.lastPlaybackContext?.canonicalId)
    }

    @Test
    fun `play sets sourceKey for factory resolution`() = runTest {
        // Given
        val telegramItem = createTestTelegramItem()

        // When
        useCase.play(telegramItem)

        // Then
        val capturedContext = testPlayerSession.lastPlaybackContext
        assertEquals("msg:123:456", capturedContext?.sourceKey)
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
     * Test implementation of InternalPlayerSession.
     *
     * Per repository testing practices, we use simple test doubles
     * instead of mocking frameworks.
     */
    private class TestInternalPlayerSession : InternalPlayerSession(
        context = throw UnsupportedOperationException("Not used in tests"),
        sourceResolver = throw UnsupportedOperationException("Not used in tests"),
        resumeManager = throw UnsupportedOperationException("Not used in tests"),
        kidsPlaybackGate = throw UnsupportedOperationException("Not used in tests"),
        codecConfigurator = throw UnsupportedOperationException("Not used in tests"),
    ) {
        var playCallCount = 0
        var lastPlaybackContext: PlaybackContext? = null

        override suspend fun play(context: PlaybackContext) {
            playCallCount++
            lastPlaybackContext = context
        }
    }
}
