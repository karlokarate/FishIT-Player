package com.chris.m3usuite.player.miniplayer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import com.chris.m3usuite.playback.PlaybackSession
import com.chris.m3usuite.tv.input.TvAction
import com.chris.m3usuite.tv.input.TvActionListener

/**
 * Action handler for MiniPlayer resize and seek operations.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – MiniPlayer Resize Mode Input Handling
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This handler manages TV input actions when the MiniPlayer is visible.
 * It routes actions differently based on the current MiniPlayer mode:
 *
 * **NORMAL mode:**
 * - PIP_SEEK_FORWARD/BACKWARD → Seek playback
 * - PIP_TOGGLE_PLAY_PAUSE → Toggle playback
 * - PIP_ENTER_RESIZE_MODE → Enter resize mode
 *
 * **RESIZE mode:**
 * - PIP_SEEK_FORWARD/BACKWARD → Resize (FF larger, RW smaller)
 * - PIP_MOVE_LEFT/RIGHT/UP/DOWN → Move position
 * - PIP_CONFIRM_RESIZE → Confirm and exit resize mode
 * - BACK → Cancel and restore previous size/position
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 4.2
 * - GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md: Mini-Context: Player + PIP Enabled
 */
class MiniPlayerResizeActionHandler(
    private val miniPlayerManager: MiniPlayerManager,
    private val playbackSession: PlaybackSession = PlaybackSession,
) : TvActionListener {
    /**
     * Handle a TV action when MiniPlayer is visible.
     *
     * @param action The action to handle
     * @return True if handled, false otherwise
     */
    override fun onAction(action: TvAction): Boolean {
        val state = miniPlayerManager.state.value

        // Only handle actions when MiniPlayer is visible
        if (!state.visible) return false

        return when (state.mode) {
            MiniPlayerMode.NORMAL -> handleNormalModeAction(action)
            MiniPlayerMode.RESIZE -> handleResizeModeAction(action)
        }
    }

    /**
     * Handle actions in NORMAL mode.
     * FF/RW seek, PLAY toggles, MENU enters resize mode.
     */
    private fun handleNormalModeAction(action: TvAction): Boolean =
        when (action) {
            TvAction.PIP_SEEK_FORWARD -> {
                playbackSession.seekBy(SEEK_DELTA_MS)
                true
            }

            TvAction.PIP_SEEK_BACKWARD -> {
                playbackSession.seekBy(-SEEK_DELTA_MS)
                true
            }

            TvAction.PIP_TOGGLE_PLAY_PAUSE -> {
                playbackSession.togglePlayPause()
                true
            }

            TvAction.PIP_ENTER_RESIZE_MODE -> {
                miniPlayerManager.enterResizeMode()
                true
            }

            // Navigation actions pass through in normal mode
            TvAction.PIP_MOVE_LEFT,
            TvAction.PIP_MOVE_RIGHT,
            TvAction.PIP_MOVE_UP,
            TvAction.PIP_MOVE_DOWN,
            -> false // Let background UI navigation handle these

            TvAction.PIP_CONFIRM_RESIZE -> false // Not in resize mode, ignore

            else -> false
        }

    /**
     * Handle actions in RESIZE mode.
     * FF/RW change size, DPAD moves, CENTER confirms, BACK cancels.
     */
    private fun handleResizeModeAction(action: TvAction): Boolean =
        when (action) {
            // FF/RW change size (coarse adjustment)
            TvAction.PIP_SEEK_FORWARD -> {
                miniPlayerManager.applyResize(RESIZE_SIZE_DELTA)
                true
            }

            TvAction.PIP_SEEK_BACKWARD -> {
                // Negative delta = smaller
                miniPlayerManager.applyResize(negativeSizeDelta())
                true
            }

            // DPAD moves position (fine adjustment)
            TvAction.PIP_MOVE_LEFT -> {
                miniPlayerManager.moveBy(Offset(-MOVE_POSITION_DELTA, 0f))
                true
            }

            TvAction.PIP_MOVE_RIGHT -> {
                miniPlayerManager.moveBy(Offset(MOVE_POSITION_DELTA, 0f))
                true
            }

            TvAction.PIP_MOVE_UP -> {
                miniPlayerManager.moveBy(Offset(0f, -MOVE_POSITION_DELTA))
                true
            }

            TvAction.PIP_MOVE_DOWN -> {
                miniPlayerManager.moveBy(Offset(0f, MOVE_POSITION_DELTA))
                true
            }

            // CENTER confirms resize
            TvAction.PIP_CONFIRM_RESIZE -> {
                miniPlayerManager.confirmResize()
                true
            }

            // BACK cancels resize
            TvAction.BACK -> {
                miniPlayerManager.cancelResize()
                true
            }

            // Play/pause still works in resize mode
            TvAction.PIP_TOGGLE_PLAY_PAUSE -> {
                playbackSession.togglePlayPause()
                true
            }

            // Re-entering resize mode is a no-op (already in resize mode)
            TvAction.PIP_ENTER_RESIZE_MODE -> true

            else -> false
        }

    /**
     * Create a negative size delta for shrinking the MiniPlayer.
     */
    private fun negativeSizeDelta(): DpSize = DpSize(-RESIZE_SIZE_DELTA.width, -RESIZE_SIZE_DELTA.height)

    companion object {
        /** Seek delta in milliseconds for PIP seek operations */
        private const val SEEK_DELTA_MS = 10_000L
    }
}
