package com.chris.m3usuite.player.miniplayer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MiniPlayerState data class.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – MiniPlayerState Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * These tests verify:
 * - Default values match contract specifications
 * - Data class copy operations work correctly
 * - Enum completeness
 * - State transitions
 */
class MiniPlayerStateTest {
    // ══════════════════════════════════════════════════════════════════
    // Default Value Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `default visible is false`() {
        val state = MiniPlayerState()
        assertFalse(state.visible)
    }

    @Test
    fun `default mode is NORMAL`() {
        val state = MiniPlayerState()
        assertEquals(MiniPlayerMode.NORMAL, state.mode)
    }

    @Test
    fun `default anchor is BOTTOM_RIGHT`() {
        val state = MiniPlayerState()
        assertEquals(MiniPlayerAnchor.BOTTOM_RIGHT, state.anchor)
    }

    @Test
    fun `default size is DEFAULT_MINI_SIZE`() {
        val state = MiniPlayerState()
        assertEquals(DEFAULT_MINI_SIZE, state.size)
    }

    @Test
    fun `default position is null`() {
        val state = MiniPlayerState()
        assertNull(state.position)
    }

    @Test
    fun `default returnRoute is null`() {
        val state = MiniPlayerState()
        assertNull(state.returnRoute)
    }

    @Test
    fun `default returnMediaId is null`() {
        val state = MiniPlayerState()
        assertNull(state.returnMediaId)
    }

    @Test
    fun `default returnRowIndex is null`() {
        val state = MiniPlayerState()
        assertNull(state.returnRowIndex)
    }

    @Test
    fun `default returnItemIndex is null`() {
        val state = MiniPlayerState()
        assertNull(state.returnItemIndex)
    }

    @Test
    fun `INITIAL companion matches default constructor`() {
        val state = MiniPlayerState()
        assertEquals(state, MiniPlayerState.INITIAL)
    }

    // ══════════════════════════════════════════════════════════════════
    // Copy Operations Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `copy preserves unchanged fields`() {
        val original =
            MiniPlayerState(
                visible = true,
                mode = MiniPlayerMode.RESIZE,
                anchor = MiniPlayerAnchor.TOP_LEFT,
                returnRoute = "library",
                returnMediaId = 123L,
            )

        val copied = original.copy(visible = false)

        assertFalse(copied.visible)
        assertEquals(MiniPlayerMode.RESIZE, copied.mode)
        assertEquals(MiniPlayerAnchor.TOP_LEFT, copied.anchor)
        assertEquals("library", copied.returnRoute)
        assertEquals(123L, copied.returnMediaId)
    }

    @Test
    fun `copy can change multiple fields`() {
        val original = MiniPlayerState.INITIAL

        val copied =
            original.copy(
                visible = true,
                mode = MiniPlayerMode.RESIZE,
                anchor = MiniPlayerAnchor.TOP_RIGHT,
                returnRoute = "detail/456",
                returnMediaId = 456L,
                returnRowIndex = 2,
                returnItemIndex = 5,
            )

        assertTrue(copied.visible)
        assertEquals(MiniPlayerMode.RESIZE, copied.mode)
        assertEquals(MiniPlayerAnchor.TOP_RIGHT, copied.anchor)
        assertEquals("detail/456", copied.returnRoute)
        assertEquals(456L, copied.returnMediaId)
        assertEquals(2, copied.returnRowIndex)
        assertEquals(5, copied.returnItemIndex)
    }

    @Test
    fun `copy with position offset`() {
        val state = MiniPlayerState().copy(position = Offset(100f, 200f))
        assertEquals(Offset(100f, 200f), state.position)
    }

    // ══════════════════════════════════════════════════════════════════
    // Enum Completeness Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `MiniPlayerMode has NORMAL and RESIZE`() {
        val modes = MiniPlayerMode.entries.toList()
        assertEquals(2, modes.size)
        assertTrue(modes.contains(MiniPlayerMode.NORMAL))
        assertTrue(modes.contains(MiniPlayerMode.RESIZE))
    }

    @Test
    fun `MiniPlayerAnchor has all four corners`() {
        val anchors = MiniPlayerAnchor.entries.toList()
        assertEquals(4, anchors.size)
        assertTrue(anchors.contains(MiniPlayerAnchor.TOP_LEFT))
        assertTrue(anchors.contains(MiniPlayerAnchor.TOP_RIGHT))
        assertTrue(anchors.contains(MiniPlayerAnchor.BOTTOM_LEFT))
        assertTrue(anchors.contains(MiniPlayerAnchor.BOTTOM_RIGHT))
    }

    // ══════════════════════════════════════════════════════════════════
    // State Transition Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `transition from hidden to visible`() {
        val hidden = MiniPlayerState(visible = false)
        val visible = hidden.copy(visible = true)

        assertFalse(hidden.visible)
        assertTrue(visible.visible)
    }

    @Test
    fun `transition from NORMAL to RESIZE mode`() {
        val normal = MiniPlayerState(mode = MiniPlayerMode.NORMAL)
        val resize = normal.copy(mode = MiniPlayerMode.RESIZE)

        assertEquals(MiniPlayerMode.NORMAL, normal.mode)
        assertEquals(MiniPlayerMode.RESIZE, resize.mode)
    }

    @Test
    fun `transition through all anchors`() {
        var state = MiniPlayerState(anchor = MiniPlayerAnchor.BOTTOM_RIGHT)

        state = state.copy(anchor = MiniPlayerAnchor.BOTTOM_LEFT)
        assertEquals(MiniPlayerAnchor.BOTTOM_LEFT, state.anchor)

        state = state.copy(anchor = MiniPlayerAnchor.TOP_LEFT)
        assertEquals(MiniPlayerAnchor.TOP_LEFT, state.anchor)

        state = state.copy(anchor = MiniPlayerAnchor.TOP_RIGHT)
        assertEquals(MiniPlayerAnchor.TOP_RIGHT, state.anchor)
    }

    // ══════════════════════════════════════════════════════════════════
    // Size Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `DEFAULT_MINI_SIZE has correct dimensions`() {
        assertEquals(320.dp, DEFAULT_MINI_SIZE.width)
        assertEquals(180.dp, DEFAULT_MINI_SIZE.height)
    }

    @Test
    fun `custom size can be set`() {
        val customSize =
            androidx.compose.ui.unit.DpSize(
                width = 400.dp,
                height = 225.dp,
            )
        val state = MiniPlayerState(size = customSize)
        assertEquals(customSize, state.size)
    }

    // ══════════════════════════════════════════════════════════════════
    // Data Class Equality Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `equals works for identical states`() {
        val state1 =
            MiniPlayerState(
                visible = true,
                mode = MiniPlayerMode.NORMAL,
                anchor = MiniPlayerAnchor.TOP_RIGHT,
                returnRoute = "library",
            )
        val state2 =
            MiniPlayerState(
                visible = true,
                mode = MiniPlayerMode.NORMAL,
                anchor = MiniPlayerAnchor.TOP_RIGHT,
                returnRoute = "library",
            )
        assertEquals(state1, state2)
    }

    @Test
    fun `equals is false for different states`() {
        val state1 = MiniPlayerState(visible = true)
        val state2 = MiniPlayerState(visible = false)
        assertFalse(state1 == state2)
    }
}
