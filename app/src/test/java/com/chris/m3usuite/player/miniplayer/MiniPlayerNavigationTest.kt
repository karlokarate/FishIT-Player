package com.chris.m3usuite.player.miniplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for MiniPlayer navigation behavior.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – MiniPlayer Navigation Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * **Requirements:**
 * - When MiniPlayerManager.enterMiniPlayer() is called:
 *   - Save returnRoute, returnMediaId, returnRowIndex, returnItemIndex
 *   - Set MiniPlayerState.visible = true
 *   - Pop the SIP player screen from backstack (handled by navigation layer)
 *
 * - When MiniPlayerManager.exitMiniPlayer(returnToFullPlayer = true):
 *   - Navigate to full player route
 *   - Set MiniPlayerState.visible = false
 *
 * - When exitMiniPlayer(returnToFullPlayer = false):
 *   - Just set visible = false, do NOT navigate
 *
 * - PlaybackSession is NOT recreated in any of these flows
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 4.2
 */
class MiniPlayerNavigationTest {
    @Before
    fun setup() {
        // Reset manager to initial state before each test
        DefaultMiniPlayerManager.resetForTesting()
    }

    // ══════════════════════════════════════════════════════════════════
    // enterMiniPlayer Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `enterMiniPlayer sets visible to true`() {
        // Given: Initial state
        assertFalse("Initially should not be visible", DefaultMiniPlayerManager.state.value.visible)

        // When: Enter mini player
        DefaultMiniPlayerManager.enterMiniPlayer("library")

        // Then: Should be visible
        assertTrue("Should be visible after enter", DefaultMiniPlayerManager.state.value.visible)
    }

    @Test
    fun `enterMiniPlayer stores return route`() {
        // When: Enter with route
        DefaultMiniPlayerManager.enterMiniPlayer("vod/123")

        // Then: Route should be stored
        assertEquals("vod/123", DefaultMiniPlayerManager.state.value.returnRoute)
    }

    @Test
    fun `enterMiniPlayer stores media id`() {
        // When: Enter with media ID
        DefaultMiniPlayerManager.enterMiniPlayer("library", mediaId = 456L)

        // Then: Media ID should be stored
        assertEquals(456L, DefaultMiniPlayerManager.state.value.returnMediaId)
    }

    @Test
    fun `enterMiniPlayer stores row and item indices`() {
        // When: Enter with indices
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "library",
            mediaId = 123L,
            rowIndex = 2,
            itemIndex = 5,
        )

        // Then: Indices should be stored
        assertEquals(2, DefaultMiniPlayerManager.state.value.returnRowIndex)
        assertEquals(5, DefaultMiniPlayerManager.state.value.returnItemIndex)
    }

    @Test
    fun `enterMiniPlayer sets mode to NORMAL`() {
        // When: Enter mini player
        DefaultMiniPlayerManager.enterMiniPlayer("library")

        // Then: Mode should be NORMAL
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
    }

    // ══════════════════════════════════════════════════════════════════
    // exitMiniPlayer Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `exitMiniPlayer sets visible to false`() {
        // Given: MiniPlayer is visible
        DefaultMiniPlayerManager.enterMiniPlayer("library")
        assertTrue(DefaultMiniPlayerManager.state.value.visible)

        // When: Exit
        DefaultMiniPlayerManager.exitMiniPlayer(returnToFullPlayer = false)

        // Then: Should not be visible
        assertFalse("Should not be visible after exit", DefaultMiniPlayerManager.state.value.visible)
    }

    @Test
    fun `exitMiniPlayer with returnToFullPlayer=true preserves return context`() {
        // Given: MiniPlayer with return context
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "library",
            mediaId = 123L,
            rowIndex = 2,
            itemIndex = 5,
        )

        // When: Exit with returnToFullPlayer = true
        DefaultMiniPlayerManager.exitMiniPlayer(returnToFullPlayer = true)

        // Then: Return context should be preserved
        assertEquals("library", DefaultMiniPlayerManager.state.value.returnRoute)
        assertEquals(123L, DefaultMiniPlayerManager.state.value.returnMediaId)
    }

    @Test
    fun `exitMiniPlayer with returnToFullPlayer=false clears return context`() {
        // Given: MiniPlayer with return context
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "library",
            mediaId = 123L,
            rowIndex = 2,
            itemIndex = 5,
        )

        // When: Exit with returnToFullPlayer = false
        DefaultMiniPlayerManager.exitMiniPlayer(returnToFullPlayer = false)

        // Then: Return context should be cleared
        assertEquals(null, DefaultMiniPlayerManager.state.value.returnRoute)
        assertEquals(null, DefaultMiniPlayerManager.state.value.returnMediaId)
        assertEquals(null, DefaultMiniPlayerManager.state.value.returnRowIndex)
        assertEquals(null, DefaultMiniPlayerManager.state.value.returnItemIndex)
    }

    // ══════════════════════════════════════════════════════════════════
    // Full ↔ Mini ↔ Full Transition Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `full to mini to full transition preserves state`() {
        // Given: Start from full player
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "vod/123",
            mediaId = 123L,
        )

        // Verify: Mini player visible with context
        assertTrue(DefaultMiniPlayerManager.state.value.visible)
        assertEquals("vod/123", DefaultMiniPlayerManager.state.value.returnRoute)

        // When: Return to full player
        DefaultMiniPlayerManager.exitMiniPlayer(returnToFullPlayer = true)

        // Then: Not visible but context preserved for navigation
        assertFalse(DefaultMiniPlayerManager.state.value.visible)
        assertEquals("vod/123", DefaultMiniPlayerManager.state.value.returnRoute)
    }

    @Test
    fun `multiple enter-exit cycles work correctly`() {
        // First cycle
        DefaultMiniPlayerManager.enterMiniPlayer("library", mediaId = 1L)
        assertTrue(DefaultMiniPlayerManager.state.value.visible)
        DefaultMiniPlayerManager.exitMiniPlayer(returnToFullPlayer = false)
        assertFalse(DefaultMiniPlayerManager.state.value.visible)

        // Second cycle
        DefaultMiniPlayerManager.enterMiniPlayer("vod/2", mediaId = 2L)
        assertTrue(DefaultMiniPlayerManager.state.value.visible)
        assertEquals(2L, DefaultMiniPlayerManager.state.value.returnMediaId)
        DefaultMiniPlayerManager.exitMiniPlayer(returnToFullPlayer = true)
        assertFalse(DefaultMiniPlayerManager.state.value.visible)

        // Third cycle
        DefaultMiniPlayerManager.enterMiniPlayer("series/3", mediaId = 3L)
        assertTrue(DefaultMiniPlayerManager.state.value.visible)
        assertEquals(3L, DefaultMiniPlayerManager.state.value.returnMediaId)
    }

    // ══════════════════════════════════════════════════════════════════
    // Reset Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `reset clears all state`() {
        // Given: MiniPlayer with various state
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "library",
            mediaId = 123L,
            rowIndex = 2,
            itemIndex = 5,
        )
        DefaultMiniPlayerManager.updateMode(MiniPlayerMode.RESIZE)

        // When: Reset
        DefaultMiniPlayerManager.reset()

        // Then: All state should be initial
        assertEquals(MiniPlayerState.INITIAL, DefaultMiniPlayerManager.state.value)
    }
}
