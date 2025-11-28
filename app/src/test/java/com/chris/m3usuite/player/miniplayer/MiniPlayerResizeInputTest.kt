package com.chris.m3usuite.player.miniplayer

import androidx.compose.ui.geometry.Offset
import com.chris.m3usuite.tv.input.TvAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MiniPlayer Resize Mode input handling.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – MiniPlayer Resize Mode Input Tests
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * Tests verify:
 * - In RESIZE mode:
 *   - TvAction.PIP_ENTER_RESIZE_MODE → mode changes to RESIZE
 *   - TvAction.PIP_MOVE_LEFT/RIGHT/UP/DOWN → position changes appropriately
 *   - TvAction.PIP_SEEK_* → affects size, not playback position
 *   - TvAction.PIP_CONFIRM_RESIZE → exits RESIZE, retains changed size
 *   - BACK → calls cancelResize() and reverts
 * - In NORMAL mode:
 *   - PIP_SEEK_* still controls playback (not size)
 *   - PIP_ENTER_RESIZE_MODE enters resize
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 4.2
 * - GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md: Mini-Context: Player + PIP Enabled
 */
class MiniPlayerResizeInputTest {
    private lateinit var handler: MiniPlayerResizeActionHandler

    @Before
    fun setUp() {
        DefaultMiniPlayerManager.resetForTesting()
        handler =
            MiniPlayerResizeActionHandler(
                miniPlayerManager = DefaultMiniPlayerManager,
            )
    }

    @After
    fun tearDown() {
        DefaultMiniPlayerManager.resetForTesting()
    }

    // ══════════════════════════════════════════════════════════════════
    // Action Handling - MiniPlayer Not Visible
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `actions not handled when MiniPlayer not visible`() {
        // MiniPlayer is not visible (default state)
        assertFalse(DefaultMiniPlayerManager.state.value.visible)

        assertFalse(handler.onAction(TvAction.PIP_ENTER_RESIZE_MODE))
        assertFalse(handler.onAction(TvAction.PIP_SEEK_FORWARD))
        assertFalse(handler.onAction(TvAction.PIP_TOGGLE_PLAY_PAUSE))
    }

    // ══════════════════════════════════════════════════════════════════
    // NORMAL Mode - Enter Resize Mode
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PIP_ENTER_RESIZE_MODE enters resize mode in NORMAL mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)

        val handled = handler.onAction(TvAction.PIP_ENTER_RESIZE_MODE)

        assertTrue(handled)
        assertEquals(MiniPlayerMode.RESIZE, DefaultMiniPlayerManager.state.value.mode)
    }

    @Test
    fun `PIP_ENTER_RESIZE_MODE is handled (true) in RESIZE mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        val handled = handler.onAction(TvAction.PIP_ENTER_RESIZE_MODE)

        assertTrue(handled) // Action is consumed, no-op
        assertEquals(MiniPlayerMode.RESIZE, DefaultMiniPlayerManager.state.value.mode)
    }

    // ══════════════════════════════════════════════════════════════════
    // NORMAL Mode - Move Actions (Pass Through)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PIP_MOVE actions not handled in NORMAL mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")

        assertFalse(handler.onAction(TvAction.PIP_MOVE_LEFT))
        assertFalse(handler.onAction(TvAction.PIP_MOVE_RIGHT))
        assertFalse(handler.onAction(TvAction.PIP_MOVE_UP))
        assertFalse(handler.onAction(TvAction.PIP_MOVE_DOWN))
    }

    @Test
    fun `PIP_CONFIRM_RESIZE not handled in NORMAL mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")

        val handled = handler.onAction(TvAction.PIP_CONFIRM_RESIZE)

        assertFalse(handled)
    }

    // ══════════════════════════════════════════════════════════════════
    // RESIZE Mode - Move Actions
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PIP_MOVE_LEFT moves position left in RESIZE mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        val handled = handler.onAction(TvAction.PIP_MOVE_LEFT)

        assertTrue(handled)
        assertEquals(Offset(-MOVE_POSITION_DELTA, 0f), DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `PIP_MOVE_RIGHT moves position right in RESIZE mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        val handled = handler.onAction(TvAction.PIP_MOVE_RIGHT)

        assertTrue(handled)
        assertEquals(Offset(MOVE_POSITION_DELTA, 0f), DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `PIP_MOVE_UP moves position up in RESIZE mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        val handled = handler.onAction(TvAction.PIP_MOVE_UP)

        assertTrue(handled)
        assertEquals(Offset(0f, -MOVE_POSITION_DELTA), DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `PIP_MOVE_DOWN moves position down in RESIZE mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        val handled = handler.onAction(TvAction.PIP_MOVE_DOWN)

        assertTrue(handled)
        assertEquals(Offset(0f, MOVE_POSITION_DELTA), DefaultMiniPlayerManager.state.value.position)
    }

    // ══════════════════════════════════════════════════════════════════
    // RESIZE Mode - Size Actions (FF/RW)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PIP_SEEK_FORWARD increases size in RESIZE mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size
        DefaultMiniPlayerManager.enterResizeMode()

        val handled = handler.onAction(TvAction.PIP_SEEK_FORWARD)

        assertTrue(handled)
        assertTrue(DefaultMiniPlayerManager.state.value.size.width > originalSize.width)
        assertTrue(DefaultMiniPlayerManager.state.value.size.height > originalSize.height)
    }

    @Test
    fun `PIP_SEEK_BACKWARD decreases size in RESIZE mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size
        DefaultMiniPlayerManager.enterResizeMode()

        val handled = handler.onAction(TvAction.PIP_SEEK_BACKWARD)

        assertTrue(handled)
        assertTrue(DefaultMiniPlayerManager.state.value.size.width < originalSize.width)
        assertTrue(DefaultMiniPlayerManager.state.value.size.height < originalSize.height)
    }

    // ══════════════════════════════════════════════════════════════════
    // RESIZE Mode - Confirm/Cancel
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PIP_CONFIRM_RESIZE exits RESIZE mode and keeps changes`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        // Make some changes
        handler.onAction(TvAction.PIP_SEEK_FORWARD)
        handler.onAction(TvAction.PIP_MOVE_RIGHT)
        val newSize = DefaultMiniPlayerManager.state.value.size
        val newPosition = DefaultMiniPlayerManager.state.value.position

        val handled = handler.onAction(TvAction.PIP_CONFIRM_RESIZE)

        assertTrue(handled)
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
        assertEquals(newSize, DefaultMiniPlayerManager.state.value.size)
        assertEquals(newPosition, DefaultMiniPlayerManager.state.value.position)
    }

    @Test
    fun `BACK cancels RESIZE mode and reverts changes`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size
        val originalPosition = Offset(50f, 50f)
        DefaultMiniPlayerManager.updatePosition(originalPosition)
        DefaultMiniPlayerManager.enterResizeMode()

        // Make some changes
        handler.onAction(TvAction.PIP_SEEK_FORWARD)
        handler.onAction(TvAction.PIP_MOVE_DOWN)

        val handled = handler.onAction(TvAction.BACK)

        assertTrue(handled)
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
        assertEquals(originalSize, DefaultMiniPlayerManager.state.value.size)
        assertEquals(originalPosition, DefaultMiniPlayerManager.state.value.position)
    }

    // ══════════════════════════════════════════════════════════════════
    // RESIZE Mode - Play/Pause Still Works
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `PIP_TOGGLE_PLAY_PAUSE is handled in RESIZE mode`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        DefaultMiniPlayerManager.enterResizeMode()

        val handled = handler.onAction(TvAction.PIP_TOGGLE_PLAY_PAUSE)

        assertTrue(handled)
        // Note: Actual playback state change requires PlaybackSession integration
    }

    // ══════════════════════════════════════════════════════════════════
    // Full Input Flow Tests
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `full input flow - enter resize, move, resize, confirm`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size

        // Enter resize mode
        assertTrue(handler.onAction(TvAction.PIP_ENTER_RESIZE_MODE))
        assertEquals(MiniPlayerMode.RESIZE, DefaultMiniPlayerManager.state.value.mode)

        // Move
        assertTrue(handler.onAction(TvAction.PIP_MOVE_RIGHT))
        assertTrue(handler.onAction(TvAction.PIP_MOVE_DOWN))

        // Resize
        assertTrue(handler.onAction(TvAction.PIP_SEEK_FORWARD))

        // Confirm
        assertTrue(handler.onAction(TvAction.PIP_CONFIRM_RESIZE))

        // Verify final state
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
        assertTrue(DefaultMiniPlayerManager.state.value.size.width > originalSize.width)
        assertEquals(
            Offset(MOVE_POSITION_DELTA, MOVE_POSITION_DELTA),
            DefaultMiniPlayerManager.state.value.position,
        )
    }

    @Test
    fun `full input flow - enter resize, move, resize, cancel`() {
        DefaultMiniPlayerManager.enterMiniPlayer(fromRoute = "library")
        val originalSize = DefaultMiniPlayerManager.state.value.size
        val originalPosition = Offset(10f, 10f)
        DefaultMiniPlayerManager.updatePosition(originalPosition)

        // Enter resize mode
        assertTrue(handler.onAction(TvAction.PIP_ENTER_RESIZE_MODE))

        // Move and resize
        assertTrue(handler.onAction(TvAction.PIP_MOVE_LEFT))
        assertTrue(handler.onAction(TvAction.PIP_SEEK_BACKWARD))

        // Cancel
        assertTrue(handler.onAction(TvAction.BACK))

        // Verify original state restored
        assertEquals(MiniPlayerMode.NORMAL, DefaultMiniPlayerManager.state.value.mode)
        assertEquals(originalSize, DefaultMiniPlayerManager.state.value.size)
        assertEquals(originalPosition, DefaultMiniPlayerManager.state.value.position)
    }
}
