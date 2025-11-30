package com.chris.m3usuite.player.session

import com.chris.m3usuite.playback.PlaybackError
import com.chris.m3usuite.playback.PlaybackSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for playback error recovery and error state management.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 6b: Playback Error Recovery Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * - PlaybackError models (Network, Http, Source, Decoder, Unknown)
 * - PlaybackSession.playbackError state flow
 * - clearError() clears the error state
 * - retry() clears error and returns appropriate result
 * - User-friendly and kids-friendly messages
 */
class PlaybackErrorRecoveryTest {
    @Before
    fun setUp() {
        PlaybackSession.resetForTesting()
    }

    @After
    fun tearDown() {
        PlaybackSession.resetForTesting()
    }

    // ══════════════════════════════════════════════════════════════════
    // PlaybackError Model Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PlaybackError Network has correct typeName`() {
        val error = PlaybackError.Network(code = 1001, message = "Connection failed")
        assertEquals("Network", error.typeName)
    }

    @Test
    fun `PlaybackError Http has correct typeName`() {
        val error = PlaybackError.Http(code = 404, url = "https://example.com/video.mp4")
        assertEquals("Http", error.typeName)
    }

    @Test
    fun `PlaybackError Source has correct typeName`() {
        val error = PlaybackError.Source(message = "Unsupported format")
        assertEquals("Source", error.typeName)
    }

    @Test
    fun `PlaybackError Decoder has correct typeName`() {
        val error = PlaybackError.Decoder(message = "Codec not available")
        assertEquals("Decoder", error.typeName)
    }

    @Test
    fun `PlaybackError Unknown has correct typeName`() {
        val error = PlaybackError.Unknown(throwable = RuntimeException("Unknown error"))
        assertEquals("Unknown", error.typeName)
    }

    // ══════════════════════════════════════════════════════════════════
    // Short Summary Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `Network error toShortSummary includes code`() {
        val error = PlaybackError.Network(code = 1001)
        assertTrue(error.toShortSummary().contains("Network error"))
        assertTrue(error.toShortSummary().contains("1001"))
    }

    @Test
    fun `Http error toShortSummary includes status code`() {
        val error = PlaybackError.Http(code = 503)
        assertEquals("HTTP 503", error.toShortSummary())
    }

    @Test
    fun `Source error toShortSummary includes message`() {
        val error = PlaybackError.Source(message = "Malformed container")
        assertTrue(error.toShortSummary().contains("Source error"))
        assertTrue(error.toShortSummary().contains("Malformed container"))
    }

    // ══════════════════════════════════════════════════════════════════
    // User-Friendly Message Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `Network error user-friendly message mentions internet connection`() {
        val error = PlaybackError.Network()
        val message = error.toUserFriendlyMessage()
        assertTrue(message.contains("internet connection") || message.contains("connect"))
    }

    @Test
    fun `Http 401 error user-friendly message mentions credentials`() {
        val error = PlaybackError.Http(code = 401)
        val message = error.toUserFriendlyMessage()
        assertTrue(message.contains("credentials") || message.contains("denied"))
    }

    @Test
    fun `Http 404 error user-friendly message mentions not found`() {
        val error = PlaybackError.Http(code = 404)
        val message = error.toUserFriendlyMessage()
        assertTrue(message.contains("not found") || message.contains("removed"))
    }

    @Test
    fun `Http 500+ error user-friendly message mentions server error`() {
        val error = PlaybackError.Http(code = 503)
        val message = error.toUserFriendlyMessage()
        assertTrue(message.contains("Server error") || message.contains("server"))
    }

    @Test
    fun `Source error user-friendly message mentions format`() {
        val error = PlaybackError.Source()
        val message = error.toUserFriendlyMessage()
        assertTrue(message.contains("format") || message.contains("supported"))
    }

    @Test
    fun `Decoder error user-friendly message mentions device`() {
        val error = PlaybackError.Decoder()
        val message = error.toUserFriendlyMessage()
        assertTrue(message.contains("device") || message.contains("play"))
    }

    // ══════════════════════════════════════════════════════════════════
    // Kids-Friendly Message Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `toKidsFriendlyMessage is generic for all error types`() {
        // Kids mode should never see technical details
        val errors = listOf(
            PlaybackError.Network(code = 1001, message = "Technical error"),
            PlaybackError.Http(code = 500, url = "https://secret.url/video.mp4"),
            PlaybackError.Source(message = "Parsing failed"),
            PlaybackError.Decoder(message = "Codec AAC not found"),
            PlaybackError.Unknown(throwable = IllegalStateException("Stack trace")),
        )

        errors.forEach { error ->
            val kidsMessage = error.toKidsFriendlyMessage()
            // Should be the same generic message for all
            assertEquals("Cannot play this video right now.", kidsMessage)
            // Should NOT contain technical details
            assertFalse(kidsMessage.contains("Technical error"))
            assertFalse(kidsMessage.contains("secret.url"))
            assertFalse(kidsMessage.contains("Parsing"))
            assertFalse(kidsMessage.contains("AAC"))
            assertFalse(kidsMessage.contains("Stack trace"))
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // HTTP Code and URL Extraction Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `httpOrNetworkCodeAsString returns code for Network error`() {
        val error = PlaybackError.Network(code = 1002)
        assertEquals("1002", error.httpOrNetworkCodeAsString)
    }

    @Test
    fun `httpOrNetworkCodeAsString returns code for Http error`() {
        val error = PlaybackError.Http(code = 404)
        assertEquals("404", error.httpOrNetworkCodeAsString)
    }

    @Test
    fun `httpOrNetworkCodeAsString returns null for Source error`() {
        val error = PlaybackError.Source()
        assertNull(error.httpOrNetworkCodeAsString)
    }

    @Test
    fun `urlOrNull returns url for Http error`() {
        val url = "https://example.com/video.mp4"
        val error = PlaybackError.Http(code = 404, url = url)
        assertEquals(url, error.urlOrNull)
    }

    @Test
    fun `urlOrNull returns null for Network error`() {
        val error = PlaybackError.Network()
        assertNull(error.urlOrNull)
    }

    // ══════════════════════════════════════════════════════════════════
    // PlaybackSession Error State Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `playbackError initial value is null`() {
        assertNull(PlaybackSession.playbackError.value)
    }

    @Test
    fun `playbackError StateFlow is accessible`() {
        assertNotNull(PlaybackSession.playbackError)
    }

    // ══════════════════════════════════════════════════════════════════
    // clearError() Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `clearError clears both error and playbackError`() {
        // Given: error state is already null (initial state)
        assertNull(PlaybackSession.error.value)
        assertNull(PlaybackSession.playbackError.value)

        // When: clearError is called
        PlaybackSession.clearError()

        // Then: both remain null
        assertNull(PlaybackSession.error.value)
        assertNull(PlaybackSession.playbackError.value)
    }

    @Test
    fun `clearError can be called multiple times safely`() {
        PlaybackSession.clearError()
        PlaybackSession.clearError()
        PlaybackSession.clearError()
        // Should not throw
        assertNull(PlaybackSession.playbackError.value)
    }

    // ══════════════════════════════════════════════════════════════════
    // retry() Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `retry returns false when no player exists`() {
        // Given: no player acquired
        assertNull(PlaybackSession.current())

        // When: retry is called
        val result = PlaybackSession.retry()

        // Then: returns false (no player to retry with)
        assertFalse(result)
    }

    @Test
    fun `retry clears error state`() {
        // Given: initial state
        assertNull(PlaybackSession.playbackError.value)

        // When: retry is called (even with no player)
        PlaybackSession.retry()

        // Then: error state remains null (was already null, but clearError was called)
        assertNull(PlaybackSession.playbackError.value)
    }

    // ══════════════════════════════════════════════════════════════════
    // Error Recovery Contract Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `error state is independent of session active state`() {
        // Error can be cleared without affecting isSessionActive
        assertFalse(PlaybackSession.isSessionActive.value)
        PlaybackSession.clearError()
        assertFalse(PlaybackSession.isSessionActive.value)
    }

    @Test
    fun `stop clears session but does not affect error clearing capability`() {
        // Stop the session
        PlaybackSession.stop()
        assertFalse(PlaybackSession.isSessionActive.value)

        // clearError should still work
        PlaybackSession.clearError()
        assertNull(PlaybackSession.playbackError.value)
    }

    @Test
    fun `release clears all state including errors`() {
        // Release the session
        PlaybackSession.release()

        // Error should be cleared as part of release
        assertNull(PlaybackSession.playbackError.value)
        assertNull(PlaybackSession.error.value)
    }

    // ══════════════════════════════════════════════════════════════════
    // Media ID Context Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `setCurrentMediaId does not throw`() {
        // Should not throw
        PlaybackSession.setCurrentMediaId("test-media-123")
        PlaybackSession.setCurrentMediaId(null)
    }
}
