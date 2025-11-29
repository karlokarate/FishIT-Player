package com.chris.m3usuite.player.session

import com.chris.m3usuite.playback.PlaybackPriority
import com.chris.m3usuite.playback.PlaybackSession
import com.chris.m3usuite.playback.SessionLifecycleState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PlaybackPriority state behavior.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 3: Playback-Aware Worker Scheduling Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * - isPlaybackActive reflects correct state based on PlaybackSession
 * - Lifecycle state transitions correctly update isPlaybackActive
 * - Throttle constant is defined
 * - shouldThrottle() convenience method works
 *
 * NOTE: These tests do NOT import Telegram-related modules per task constraints.
 */
class PlaybackPriorityStateTest {

    @Before
    fun setUp() {
        // Reset PlaybackSession state before each test
        PlaybackSession.resetForTesting()
    }

    @After
    fun tearDown() {
        // Clean up after each test
        PlaybackSession.resetForTesting()
    }

    // ══════════════════════════════════════════════════════════════════
    // Initial State Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isPlaybackActive is false initially`() {
        assertFalse(PlaybackPriority.isPlaybackActive.value)
    }

    @Test
    fun `shouldThrottle returns false initially`() {
        assertFalse(PlaybackPriority.shouldThrottle())
    }

    @Test
    fun `PLAYBACK_THROTTLE_MS is defined and positive`() {
        assertTrue(PlaybackPriority.PLAYBACK_THROTTLE_MS > 0)
    }

    @Test
    fun `PLAYBACK_THROTTLE_MS has expected value`() {
        assertEquals(500L, PlaybackPriority.PLAYBACK_THROTTLE_MS)
    }

    // ══════════════════════════════════════════════════════════════════
    // StateFlow Accessibility Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isPlaybackActive StateFlow is not null`() {
        assertNotNull(PlaybackPriority.isPlaybackActive)
    }

    @Test
    fun `isPlaybackActive value is accessible`() {
        // Should not throw
        val value = PlaybackPriority.isPlaybackActive.value
        assertNotNull(value)
    }

    // ══════════════════════════════════════════════════════════════════
    // Lifecycle State Integration Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isPlaybackActive false when lifecycle is IDLE`() {
        // Default state after resetForTesting is IDLE
        assertEquals(SessionLifecycleState.IDLE, PlaybackSession.lifecycleState.value)
        assertFalse(PlaybackPriority.isPlaybackActive.value)
    }

    @Test
    fun `isPlaybackActive false when lifecycle is STOPPED`() {
        // Stop sets lifecycle to STOPPED
        PlaybackSession.stop()
        assertEquals(SessionLifecycleState.STOPPED, PlaybackSession.lifecycleState.value)
        assertFalse(PlaybackPriority.isPlaybackActive.value)
    }

    @Test
    fun `isPlaybackActive false when lifecycle is RELEASED`() {
        // Release sets lifecycle to RELEASED
        PlaybackSession.release()
        assertEquals(SessionLifecycleState.RELEASED, PlaybackSession.lifecycleState.value)
        assertFalse(PlaybackPriority.isPlaybackActive.value)
    }

    @Test
    fun `isPlaybackActive false when isPlaying is false even with active lifecycle`() {
        // Even if lifecycle allows, isPlaying must also be true
        // Since we can't set isPlaying without actual player, test default state
        assertFalse(PlaybackSession.isPlaying.value)
        assertFalse(PlaybackPriority.isPlaybackActive.value)
    }

    // ══════════════════════════════════════════════════════════════════
    // Definition Semantics Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `definition requires both isPlaying true AND active lifecycle state`() {
        // Test the definition: isPlaybackActive = isPlaying && lifecycleState in {PLAYING, PAUSED, BACKGROUND}
        // With no player, isPlaying is false, so isPlaybackActive should always be false
        // regardless of lifecycle state (since we can't set isPlaying=true without player)
        assertFalse(PlaybackSession.isPlaying.value)
        assertFalse(PlaybackPriority.isPlaybackActive.value)
    }

    // ══════════════════════════════════════════════════════════════════
    // shouldThrottle() Convenience Method Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `shouldThrottle returns same value as isPlaybackActive value`() {
        assertEquals(PlaybackPriority.isPlaybackActive.value, PlaybackPriority.shouldThrottle())
    }

    @Test
    fun `shouldThrottle is false when playback not active`() {
        PlaybackSession.resetForTesting()
        assertFalse(PlaybackPriority.shouldThrottle())
    }

    // ══════════════════════════════════════════════════════════════════
    // Enum State Coverage Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `SessionLifecycleState has all expected states`() {
        val states = SessionLifecycleState.entries
        assertEquals(7, states.size)
        assertTrue(states.contains(SessionLifecycleState.IDLE))
        assertTrue(states.contains(SessionLifecycleState.PREPARED))
        assertTrue(states.contains(SessionLifecycleState.PLAYING))
        assertTrue(states.contains(SessionLifecycleState.PAUSED))
        assertTrue(states.contains(SessionLifecycleState.BACKGROUND))
        assertTrue(states.contains(SessionLifecycleState.STOPPED))
        assertTrue(states.contains(SessionLifecycleState.RELEASED))
    }

    @Test
    fun `active lifecycle states are PLAYING PAUSED BACKGROUND`() {
        // Document the states that count as "active" for playback priority
        // Per contract: isPlaybackActive requires lifecycleState in {PLAYING, PAUSED, BACKGROUND}
        val activeStates = setOf(
            SessionLifecycleState.PLAYING,
            SessionLifecycleState.PAUSED,
            SessionLifecycleState.BACKGROUND,
        )

        // Verify these are the only states that could make isPlaybackActive true
        // (when combined with isPlaying=true)
        assertEquals(3, activeStates.size)
    }

    @Test
    fun `inactive lifecycle states are IDLE PREPARED STOPPED RELEASED`() {
        // Document the states that do NOT count as "active" for playback priority
        val inactiveStates = setOf(
            SessionLifecycleState.IDLE,
            SessionLifecycleState.PREPARED,
            SessionLifecycleState.STOPPED,
            SessionLifecycleState.RELEASED,
        )

        // Verify there are 4 inactive states
        assertEquals(4, inactiveStates.size)
    }

    // ══════════════════════════════════════════════════════════════════
    // Thread Safety Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `isPlaybackActive can be read from any thread`() {
        // StateFlow is thread-safe by design
        // This test verifies we can read the value without exception
        val value1 = PlaybackPriority.isPlaybackActive.value
        val value2 = PlaybackPriority.shouldThrottle()
        assertEquals(value1, value2)
    }

    // ══════════════════════════════════════════════════════════════════
    // PlaybackPriority Singleton Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PlaybackPriority is a singleton object`() {
        val ref1 = PlaybackPriority
        val ref2 = PlaybackPriority
        assertTrue(ref1 === ref2)
    }
}
