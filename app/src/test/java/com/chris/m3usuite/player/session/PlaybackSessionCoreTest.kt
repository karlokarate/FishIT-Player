package com.chris.m3usuite.player.session

import com.chris.m3usuite.playback.PlaybackSession
import com.chris.m3usuite.playback.PlaybackSessionController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PlaybackSession core functionality.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – PlaybackSession Core Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * - PlaybackSession implements PlaybackSessionController interface
 * - StateFlow initial values are correct
 * - State flows exist and are accessible
 * - Command methods are accessible (actual ExoPlayer integration requires instrumented tests)
 */
class PlaybackSessionCoreTest {
    @Before
    fun setUp() {
        // Reset session state before each test
        PlaybackSession.resetForTesting()
    }

    @After
    fun tearDown() {
        // Clean up after each test
        PlaybackSession.resetForTesting()
    }

    // ══════════════════════════════════════════════════════════════════
    // Interface Implementation Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PlaybackSession implements PlaybackSessionController`() {
        // Verify that PlaybackSession is assignable to the interface
        val controller: PlaybackSessionController = PlaybackSession
        assertNotNull(controller)
    }

    // ══════════════════════════════════════════════════════════════════
    // StateFlow Initial Value Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `positionMs initial value is zero`() {
        assertEquals(0L, PlaybackSession.positionMs.value)
    }

    @Test
    fun `durationMs initial value is zero`() {
        assertEquals(0L, PlaybackSession.durationMs.value)
    }

    @Test
    fun `isPlaying initial value is false`() {
        assertFalse(PlaybackSession.isPlaying.value)
    }

    @Test
    fun `buffering initial value is false`() {
        assertFalse(PlaybackSession.buffering.value)
    }

    @Test
    fun `error initial value is null`() {
        assertNull(PlaybackSession.error.value)
    }

    @Test
    fun `videoSize initial value is null`() {
        assertNull(PlaybackSession.videoSize.value)
    }

    @Test
    fun `playbackState initial value is STATE_IDLE`() {
        // Player.STATE_IDLE = 1
        assertEquals(1, PlaybackSession.playbackState.value)
    }

    @Test
    fun `isSessionActive initial value is false`() {
        assertFalse(PlaybackSession.isSessionActive.value)
    }

    // ══════════════════════════════════════════════════════════════════
    // StateFlow Accessibility Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `all state flows are accessible`() {
        assertNotNull(PlaybackSession.positionMs)
        assertNotNull(PlaybackSession.durationMs)
        assertNotNull(PlaybackSession.isPlaying)
        assertNotNull(PlaybackSession.buffering)
        assertNotNull(PlaybackSession.error)
        assertNotNull(PlaybackSession.videoSize)
        assertNotNull(PlaybackSession.playbackState)
        assertNotNull(PlaybackSession.isSessionActive)
    }

    // ══════════════════════════════════════════════════════════════════
    // Source Management Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `currentSource returns null initially`() {
        assertNull(PlaybackSession.currentSource())
    }

    @Test
    fun `setSource and currentSource work correctly`() {
        val testUrl = "https://example.com/video.mp4"
        PlaybackSession.setSource(testUrl)
        assertEquals(testUrl, PlaybackSession.currentSource())
    }

    @Test
    fun `setSource with null clears source`() {
        PlaybackSession.setSource("https://example.com/video.mp4")
        PlaybackSession.setSource(null)
        assertNull(PlaybackSession.currentSource())
    }

    // ══════════════════════════════════════════════════════════════════
    // Player Acquisition Tests (without actual ExoPlayer)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `current returns null when no player acquired`() {
        assertNull(PlaybackSession.current())
    }

    // ══════════════════════════════════════════════════════════════════
    // Release Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `release resets all state flows`() {
        // Set some source (simulating session usage)
        PlaybackSession.setSource("https://example.com/video.mp4")

        // Release
        PlaybackSession.release()

        // Verify all state flows are reset
        assertEquals(0L, PlaybackSession.positionMs.value)
        assertEquals(0L, PlaybackSession.durationMs.value)
        assertFalse(PlaybackSession.isPlaying.value)
        assertFalse(PlaybackSession.buffering.value)
        assertNull(PlaybackSession.error.value)
        assertNull(PlaybackSession.videoSize.value)
        assertEquals(1, PlaybackSession.playbackState.value) // STATE_IDLE
        assertFalse(PlaybackSession.isSessionActive.value)
        assertNull(PlaybackSession.currentSource())
    }

    @Test
    fun `release can be called multiple times safely`() {
        PlaybackSession.release()
        PlaybackSession.release()
        PlaybackSession.release()
        // Should not throw
        assertNull(PlaybackSession.current())
    }

    // ══════════════════════════════════════════════════════════════════
    // Command Method Accessibility Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `play does not throw when no player`() {
        // Should not throw
        PlaybackSession.play()
    }

    @Test
    fun `pause does not throw when no player`() {
        // Should not throw
        PlaybackSession.pause()
    }

    @Test
    fun `togglePlayPause does not throw when no player`() {
        // Should not throw
        PlaybackSession.togglePlayPause()
    }

    @Test
    fun `seekTo does not throw when no player`() {
        // Should not throw
        PlaybackSession.seekTo(1000L)
    }

    @Test
    fun `seekBy does not throw when no player`() {
        // Should not throw
        PlaybackSession.seekBy(10_000L)
    }

    @Test
    fun `setSpeed does not throw when no player`() {
        // Should not throw
        PlaybackSession.setSpeed(2.0f)
    }

    @Test
    fun `enableTrickplay does not throw when no player`() {
        // Should not throw
        PlaybackSession.enableTrickplay(2.0f)
    }

    @Test
    fun `stop does not throw when no player`() {
        // Should not throw
        PlaybackSession.stop()
    }

    // ══════════════════════════════════════════════════════════════════
    // Stop Behavior Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `stop sets isSessionActive to false`() {
        // Note: Without actual ExoPlayer, we can only verify that stop
        // sets isSessionActive to false (the state change we control)
        PlaybackSession.stop()
        assertFalse(PlaybackSession.isSessionActive.value)
    }

    // ══════════════════════════════════════════════════════════════════
    // Single Instance Semantics Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PlaybackSession is a singleton object`() {
        // Verify that accessing PlaybackSession returns the same instance
        val ref1 = PlaybackSession
        val ref2 = PlaybackSession
        assertTrue(ref1 === ref2)
    }

    @Test
    fun `resetForTesting clears state`() {
        PlaybackSession.setSource("https://example.com/test.mp4")
        PlaybackSession.resetForTesting()
        assertNull(PlaybackSession.currentSource())
        assertNull(PlaybackSession.current())
    }
}
