package com.chris.m3usuite.player.miniplayer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MiniPlayer Resize Mode state management.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – MiniPlayer Resize Mode Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * Tests verify:
 * - enterResizeMode() sets previousSize/position and mode = RESIZE
 * - confirmResize() commits new size/position and clears previous fields
 * - cancelResize() reverts to previousSize/position and sets mode = NORMAL
 * - Size clamping to MIN/MAX bounds
 * - Position updates via moveBy()
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 4.1, 4.2
 */
class MiniPlayerResizeStateTest {
    @Before
    fun setUp() {
        DefaultMiniPlayerManager.resetForTesting()
    }

    @After
    fun tearDown() {
        DefaultMiniPlayerManager.resetForTesting()
    }

    // ══════════════════════════════════════════════════════════════════
    // enterResizeMode() Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `enterResizeMode sets mode to RESIZE`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        assertEquals(MiniPlayerMode.RESIZE, DefaultMiniPlayerManager.state.value.mode)
    }

    @Test
    fun `enterResizeMode stores previousSize`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size

        DefaultMiniPlayerManager.enterResizeMode()

        assertEquals(originalSize, DefaultMiniPlayerManager.state.value.previousSize)
    }

    @Test
    fun `enterResizeMode stores previousPosition`() {
        val position = Offset(50f, 100f)
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.updatePosition(position)

        DefaultMiniPlayerManager.enterResizeMode()

        assertEquals(position, DefaultMiniPlayerManager.state.value.previousPosition)
    }

    @Test
    fun `enterResizeMode stores null previousPosition when position is null`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        // Don't set a position - should be null

        DefaultMiniPlayerManager.enterResizeMode()

        assertNull(DefaultMiniPlayerManager.state.value.previousPosition)
    }

    @Test
    fun `enterResizeMode does nothing when MiniPlayer not visible`() {
        // MiniPlayer is not visible (default state)
        assertFalse(DefaultMiniPlayerManager.state.value.visible)

        DefaultMiniPlayerManager.enterResizeMode()

        // Should remain in NORMAL mode
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
    }

    @Test
    fun `enterResizeMode does not overwrite existing previousSize`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size

        // Enter resize mode first time
        DefaultMiniPlayerManager.enterResizeMode()
        assertEquals(originalSize, DefaultMiniPlayerManager.state.value.previousSize)

        // Change size
        val newSize = DpSize(400.dp, 225.dp)
        DefaultMiniPlayerManager.updateSize(newSize)

        // Re-enter resize mode (should not update previousSize)
        DefaultMiniPlayerManager.enterResizeMode()
        assertEquals(originalSize, DefaultMiniPlayerManager.state.value.previousSize)
    }

    // ══════════════════════════════════════════════════════════════════
    // confirmResize() Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `confirmResize sets mode to NORMAL`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        DefaultMiniPlayerManager.confirmResize()

        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
    }

    @Test
    fun `confirmResize clears previousSize`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()
        assertNotNull(DefaultMiniPlayerManager.state.value.previousSize)

        DefaultMiniPlayerManager.confirmResize()

        assertNull(DefaultMiniPlayerManager.state.value.previousSize)
    }

    @Test
    fun `confirmResize clears previousPosition`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.updatePosition(Offset(100f, 200f))
        DefaultMiniPlayerManager.enterResizeMode()

        DefaultMiniPlayerManager.confirmResize()

        assertNull(DefaultMiniPlayerManager.state.value.previousPosition)
    }

    @Test
    fun `confirmResize keeps current size`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        val newSize = DpSize(400.dp, 225.dp)
        DefaultMiniPlayerManager.updateSize(newSize)
        DefaultMiniPlayerManager.confirmResize()

        assertEquals(newSize, DefaultMiniPlayerManager.state.value.size)
    }

    @Test
    fun `confirmResize keeps current position`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        val newPosition = Offset(200f, 300f)
        DefaultMiniPlayerManager.updatePosition(newPosition)
        DefaultMiniPlayerManager.confirmResize()

        assertEquals(newPosition, DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `confirmResize does nothing when not in RESIZE mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        // Not in resize mode

        DefaultMiniPlayerManager.confirmResize()

        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
    }

    // ══════════════════════════════════════════════════════════════════
    // cancelResize() Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `cancelResize sets mode to NORMAL`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        DefaultMiniPlayerManager.cancelResize()

        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
    }

    @Test
    fun `cancelResize restores previousSize`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size
        DefaultMiniPlayerManager.enterResizeMode()

        // Change size
        DefaultMiniPlayerManager.updateSize(DpSize(500.dp, 281.dp))

        DefaultMiniPlayerManager.cancelResize()

        assertEquals(originalSize, DefaultMiniPlayerManager.state.value.size)
    }

    @Test
    fun `cancelResize restores previousPosition`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalPosition = Offset(50f, 50f)
        DefaultMiniPlayerManager.updatePosition(originalPosition)
        DefaultMiniPlayerManager.enterResizeMode()

        // Change position
        DefaultMiniPlayerManager.updatePosition(Offset(200f, 300f))

        DefaultMiniPlayerManager.cancelResize()

        assertEquals(originalPosition, DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `cancelResize clears previousSize`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()
        assertNotNull(DefaultMiniPlayerManager.state.value.previousSize)

        DefaultMiniPlayerManager.cancelResize()

        assertNull(DefaultMiniPlayerManager.state.value.previousSize)
    }

    @Test
    fun `cancelResize clears previousPosition`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.updatePosition(Offset(100f, 200f))
        DefaultMiniPlayerManager.enterResizeMode()

        DefaultMiniPlayerManager.cancelResize()

        assertNull(DefaultMiniPlayerManager.state.value.previousPosition)
    }

    @Test
    fun `cancelResize does nothing when not in RESIZE mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size

        DefaultMiniPlayerManager.cancelResize()

        assertEquals(originalSize, DefaultMiniPlayerManager.state.value.size)
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
    }

    // ══════════════════════════════════════════════════════════════════
    // applyResize() Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `applyResize increases size by delta`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()
        val originalSize = DefaultMiniPlayerManager.state.value.size

        DefaultMiniPlayerManager.applyResize(DpSize(40.dp, 22.5.dp))

        val expectedSize =
            DpSize(
                originalSize.width + 40.dp,
                originalSize.height + 22.5.dp,
            )
        assertEquals(expectedSize, DefaultMiniPlayerManager.state.value.size)
    }

    @Test
    fun `applyResize decreases size by negative delta`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()
        val originalSize = DefaultMiniPlayerManager.state.value.size

        DefaultMiniPlayerManager.applyResize(DpSize((-40).dp, (-22.5).dp))

        val expectedSize =
            DpSize(
                originalSize.width - 40.dp,
                originalSize.height - 22.5.dp,
            )
        assertEquals(expectedSize, DefaultMiniPlayerManager.state.value.size)
    }

    @Test
    fun `applyResize clamps size to MIN_MINI_SIZE`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        // Try to shrink below minimum
        DefaultMiniPlayerManager.applyResize(DpSize((-500).dp, (-500).dp))

        assertEquals(MIN_MINI_SIZE, DefaultMiniPlayerManager.state.value.size)
    }

    @Test
    fun `applyResize clamps size to MAX_MINI_SIZE`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        // Try to grow above maximum
        DefaultMiniPlayerManager.applyResize(DpSize(1000.dp, 1000.dp))

        assertEquals(MAX_MINI_SIZE, DefaultMiniPlayerManager.state.value.size)
    }

    @Test
    fun `applyResize does nothing when not in RESIZE mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size
        // Not in resize mode

        DefaultMiniPlayerManager.applyResize(DpSize(100.dp, 100.dp))

        assertEquals(originalSize, DefaultMiniPlayerManager.state.value.size)
    }

    // ══════════════════════════════════════════════════════════════════
    // moveBy() Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `moveBy updates position`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        DefaultMiniPlayerManager.moveBy(Offset(20f, 30f))

        assertEquals(Offset(20f, 30f), DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `moveBy accumulates position`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        DefaultMiniPlayerManager.moveBy(Offset(20f, 30f))
        DefaultMiniPlayerManager.moveBy(Offset(10f, 15f))

        assertEquals(Offset(30f, 45f), DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `moveBy allows negative deltas`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        DefaultMiniPlayerManager.moveBy(Offset(-50f, -75f))

        assertEquals(Offset(-50f, -75f), DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `moveBy does nothing when not in RESIZE mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        // Not in resize mode

        DefaultMiniPlayerManager.moveBy(Offset(100f, 100f))

        // Position should still be null (never set)
        assertNull(DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `moveBy starts from existing position`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.updatePosition(Offset(100f, 200f))
        DefaultMiniPlayerManager.enterResizeMode()

        DefaultMiniPlayerManager.moveBy(Offset(50f, 25f))

        assertEquals(Offset(150f, 225f), DefaultMiniPlayerManager.state.value.position)
    }

    // ══════════════════════════════════════════════════════════════════
    // Full Resize Flow Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `full resize flow - enter, resize, move, confirm`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size

        // Enter resize mode
        DefaultMiniPlayerManager.enterResizeMode()
        assertEquals(MiniPlayerMode.RESIZE, DefaultMiniPlayerManager.state.value.mode)

        // Resize
        DefaultMiniPlayerManager.applyResize(DpSize(40.dp, 22.5.dp))

        // Move
        DefaultMiniPlayerManager.moveBy(Offset(100f, 50f))

        // Confirm
        DefaultMiniPlayerManager.confirmResize()

        // Verify
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
        assertEquals(
            DpSize(originalSize.width + 40.dp, originalSize.height + 22.5.dp),
            DefaultMiniPlayerManager.state.value.size,
        )
        assertEquals(Offset(100f, 50f), DefaultMiniPlayerManager.state.value.position)
        assertNull(DefaultMiniPlayerManager.state.value.previousSize)
        assertNull(DefaultMiniPlayerManager.state.value.previousPosition)
    }

    @Test
    fun `full resize flow - enter, resize, move, cancel`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size
        val originalPosition = Offset(10f, 20f)
        DefaultMiniPlayerManager.updatePosition(originalPosition)

        // Enter resize mode
        DefaultMiniPlayerManager.enterResizeMode()

        // Resize
        DefaultMiniPlayerManager.applyResize(DpSize(100.dp, 56.25.dp))

        // Move
        DefaultMiniPlayerManager.moveBy(Offset(200f, 150f))

        // Cancel
        DefaultMiniPlayerManager.cancelResize()

        // Verify original values restored
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
        assertEquals(originalSize, DefaultMiniPlayerManager.state.value.size)
        assertEquals(originalPosition, DefaultMiniPlayerManager.state.value.position)
        assertNull(DefaultMiniPlayerManager.state.value.previousSize)
        assertNull(DefaultMiniPlayerManager.state.value.previousPosition)
    }
}
