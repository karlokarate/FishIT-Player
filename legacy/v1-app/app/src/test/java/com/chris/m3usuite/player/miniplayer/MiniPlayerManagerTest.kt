package com.chris.m3usuite.player.miniplayer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MiniPlayerManager.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – MiniPlayerManager Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * - enterMiniPlayer / exitMiniPlayer behavior
 * - State is updated correctly
 * - Mode, anchor, size, position updates
 * - Return context preservation
 */
class MiniPlayerManagerTest {
    @Before
    fun setUp() {
        DefaultMiniPlayerManager.resetForTesting()
    }

    @After
    fun tearDown() {
        DefaultMiniPlayerManager.resetForTesting()
    }

    // ══════════════════════════════════════════════════════════════════
    // Initial State Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `initial state is not visible`() {
        assertFalse(DefaultMiniPlayerManager.state.value.visible)
    }

    @Test
    fun `initial state has NORMAL mode`() {
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
    }

    @Test
    fun `initial state has BOTTOM_RIGHT anchor`() {
        assertEquals(MiniPlayerAnchor.BOTTOM_RIGHT, DefaultMiniPlayerManager.state.value.anchor)
    }

    @Test
    fun `initial state has default size`() {
        assertEquals(DEFAULT_MINI_SIZE, DefaultMiniPlayerManager.state.value.size)
    }

    @Test
    fun `initial state has no return context`() {
        val state = DefaultMiniPlayerManager.state.value
        assertNull(state.returnRoute)
        assertNull(state.returnMediaId)
        assertNull(state.returnRowIndex)
        assertNull(state.returnItemIndex)
    }

    // ══════════════════════════════════════════════════════════════════
    // Enter MiniPlayer Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `enterMiniPlayer sets visible to true`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        assertTrue(DefaultMiniPlayerManager.state.value.visible)
    }

    @Test
    fun `enterMiniPlayer sets mode to NORMAL`() {
        // First set to RESIZE to verify it's reset
        DefaultMiniPlayerManager.updateMode(MiniPlayerMode.RESIZE)
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
    }

    @Test
    fun `enterMiniPlayer stores returnRoute`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        assertEquals("library", DefaultMiniPlayerManager.state.value.returnRoute)
    }

    @Test
    fun `enterMiniPlayer stores mediaId`() {
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "detail/123",
            mediaId = 123L,
        )
        assertEquals(123L, DefaultMiniPlayerManager.state.value.returnMediaId)
    }

    @Test
    fun `enterMiniPlayer stores rowIndex`() {
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "library",
            rowIndex = 5,
        )
        assertEquals(5, DefaultMiniPlayerManager.state.value.returnRowIndex)
    }

    @Test
    fun `enterMiniPlayer stores itemIndex`() {
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "library",
            itemIndex = 3,
        )
        assertEquals(3, DefaultMiniPlayerManager.state.value.returnItemIndex)
    }

    @Test
    fun `enterMiniPlayer stores all context`() {
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "library/vod",
            mediaId = 456L,
            rowIndex = 2,
            itemIndex = 7,
        )

        val state = DefaultMiniPlayerManager.state.value
        assertTrue(state.visible)
        assertEquals("library/vod", state.returnRoute)
        assertEquals(456L, state.returnMediaId)
        assertEquals(2, state.returnRowIndex)
        assertEquals(7, state.returnItemIndex)
    }

    // ══════════════════════════════════════════════════════════════════
    // Exit MiniPlayer Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `exitMiniPlayer with returnToFullPlayer false hides MiniPlayer`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.exitMiniPlayer(returnToFullPlayer = false)
        assertFalse(DefaultMiniPlayerManager.state.value.visible)
    }

    @Test
    fun `exitMiniPlayer with returnToFullPlayer false clears return context`() {
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "library",
            mediaId = 123L,
            rowIndex = 2,
            itemIndex = 5,
        )
        DefaultMiniPlayerManager.exitMiniPlayer(returnToFullPlayer = false)

        val state = DefaultMiniPlayerManager.state.value
        assertNull(state.returnRoute)
        assertNull(state.returnMediaId)
        assertNull(state.returnRowIndex)
        assertNull(state.returnItemIndex)
    }

    @Test
    fun `exitMiniPlayer with returnToFullPlayer true hides MiniPlayer`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.exitMiniPlayer(returnToFullPlayer = true)
        assertFalse(DefaultMiniPlayerManager.state.value.visible)
    }

    @Test
    fun `exitMiniPlayer with returnToFullPlayer true preserves return context`() {
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "library",
            mediaId = 123L,
            rowIndex = 2,
            itemIndex = 5,
        )
        DefaultMiniPlayerManager.exitMiniPlayer(returnToFullPlayer = true)

        val state = DefaultMiniPlayerManager.state.value
        assertEquals("library", state.returnRoute)
        assertEquals(123L, state.returnMediaId)
        assertEquals(2, state.returnRowIndex)
        assertEquals(5, state.returnItemIndex)
    }

    // ══════════════════════════════════════════════════════════════════
    // Mode Update Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `updateMode changes mode to RESIZE`() {
        DefaultMiniPlayerManager.updateMode(MiniPlayerMode.RESIZE)
        assertEquals(MiniPlayerMode.RESIZE, DefaultMiniPlayerManager.state.value.mode)
    }

    @Test
    fun `updateMode changes mode back to NORMAL`() {
        DefaultMiniPlayerManager.updateMode(MiniPlayerMode.RESIZE)
        DefaultMiniPlayerManager.updateMode(MiniPlayerMode.NORMAL)
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
    }

    // ══════════════════════════════════════════════════════════════════
    // Anchor Update Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `updateAnchor changes to TOP_LEFT`() {
        DefaultMiniPlayerManager.updateAnchor(MiniPlayerAnchor.TOP_LEFT)
        assertEquals(MiniPlayerAnchor.TOP_LEFT, DefaultMiniPlayerManager.state.value.anchor)
    }

    @Test
    fun `updateAnchor changes to TOP_RIGHT`() {
        DefaultMiniPlayerManager.updateAnchor(MiniPlayerAnchor.TOP_RIGHT)
        assertEquals(MiniPlayerAnchor.TOP_RIGHT, DefaultMiniPlayerManager.state.value.anchor)
    }

    @Test
    fun `updateAnchor changes to BOTTOM_LEFT`() {
        DefaultMiniPlayerManager.updateAnchor(MiniPlayerAnchor.BOTTOM_LEFT)
        assertEquals(MiniPlayerAnchor.BOTTOM_LEFT, DefaultMiniPlayerManager.state.value.anchor)
    }

    @Test
    fun `updateAnchor cycles through all anchors`() {
        val anchors = MiniPlayerAnchor.entries.toList()
        for (anchor in anchors) {
            DefaultMiniPlayerManager.updateAnchor(anchor)
            assertEquals(anchor, DefaultMiniPlayerManager.state.value.anchor)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Size Update Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `updateSize changes size`() {
        val newSize = DpSize(400.dp, 225.dp)
        DefaultMiniPlayerManager.updateSize(newSize)
        assertEquals(newSize, DefaultMiniPlayerManager.state.value.size)
    }

    @Test
    fun `updateSize can be called multiple times`() {
        DefaultMiniPlayerManager.updateSize(DpSize(200.dp, 112.dp))
        DefaultMiniPlayerManager.updateSize(DpSize(480.dp, 270.dp))
        assertEquals(DpSize(480.dp, 270.dp), DefaultMiniPlayerManager.state.value.size)
    }

    // ══════════════════════════════════════════════════════════════════
    // Position Update Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `updatePosition sets position`() {
        val offset = Offset(100f, 200f)
        DefaultMiniPlayerManager.updatePosition(offset)
        assertEquals(offset, DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `updatePosition can be called multiple times`() {
        DefaultMiniPlayerManager.updatePosition(Offset(50f, 50f))
        DefaultMiniPlayerManager.updatePosition(Offset(300f, 400f))
        assertEquals(Offset(300f, 400f), DefaultMiniPlayerManager.state.value.position)
    }

    // ══════════════════════════════════════════════════════════════════
    // Reset Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `reset returns to INITIAL state`() {
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "library",
            mediaId = 123L,
            rowIndex = 2,
            itemIndex = 5,
        )
        DefaultMiniPlayerManager.updateMode(MiniPlayerMode.RESIZE)
        DefaultMiniPlayerManager.updateAnchor(MiniPlayerAnchor.TOP_LEFT)
        DefaultMiniPlayerManager.updateSize(DpSize(400.dp, 225.dp))
        DefaultMiniPlayerManager.updatePosition(Offset(100f, 200f))

        DefaultMiniPlayerManager.reset()

        assertEquals(MiniPlayerState.INITIAL, DefaultMiniPlayerManager.state.value)
    }

    // ══════════════════════════════════════════════════════════════════
    // StateFlow Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `state is a StateFlow`() {
        assertNotNull(DefaultMiniPlayerManager.state)
        // Verify it's accessible as a StateFlow
        val flow = DefaultMiniPlayerManager.state
        assertNotNull(flow.value)
    }

    // ══════════════════════════════════════════════════════════════════
    // Interface Implementation Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `DefaultMiniPlayerManager implements MiniPlayerManager`() {
        val manager: MiniPlayerManager = DefaultMiniPlayerManager
        assertNotNull(manager)
    }

    // ══════════════════════════════════════════════════════════════════
    // Full Transition Flow Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `full player to mini player to full player flow`() {
        // Start: Player is full screen, mini player hidden
        assertFalse(DefaultMiniPlayerManager.state.value.visible)

        // User taps PIP button
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "detail/movie/123",
            mediaId = 123L,
        )

        // Verify mini player is shown
        assertTrue(DefaultMiniPlayerManager.state.value.visible)
        assertEquals("detail/movie/123", DefaultMiniPlayerManager.state.value.returnRoute)

        // User taps expand to return to full player
        DefaultMiniPlayerManager.exitMiniPlayer(returnToFullPlayer = true)

        // Verify mini player is hidden and context preserved
        assertFalse(DefaultMiniPlayerManager.state.value.visible)
        assertEquals("detail/movie/123", DefaultMiniPlayerManager.state.value.returnRoute)
    }

    @Test
    fun `mini player close without returning preserves nothing`() {
        // Enter mini player
        DefaultMiniPlayerManager.enterMiniPlayer(
            fromRoute = "library",
            mediaId = 456L,
        )

        // Close without returning to full player
        DefaultMiniPlayerManager.exitMiniPlayer(returnToFullPlayer = false)

        // Verify all context is cleared
        val state = DefaultMiniPlayerManager.state.value
        assertFalse(state.visible)
        assertNull(state.returnRoute)
        assertNull(state.returnMediaId)
    }
}
