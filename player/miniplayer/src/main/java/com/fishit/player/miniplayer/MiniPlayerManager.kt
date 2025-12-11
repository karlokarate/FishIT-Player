package com.fishit.player.miniplayer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing MiniPlayer state and transitions.
 *
 * ════════════════════════════════════════════════════════════════════════════════ PHASE 5 –
 * MiniPlayer Domain Manager
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This interface defines the contract for MiniPlayer state management:
 * - Enter/exit transitions between full player and MiniPlayer
 * - Mode changes (normal ↔ resize)
 * - Anchor/size/position updates
 * - Focus management
 *
 * **Key Principles:**
 * - No UI code: Pure domain logic only
 * - No PiP/system integration: In-app MiniPlayer only
 * - No direct ExoPlayer usage: Interacts with player:internal through state
 */
interface MiniPlayerManager {
    /** Observable state of the MiniPlayer. */
    val state: StateFlow<MiniPlayerState>

    /**
     * Enter MiniPlayer mode from full player.
     *
     * This method should be called when the user triggers the PIP button. It stores the return
     * context for navigation back to the originating screen.
     *
     * **Behavior:**
     * - Sets visible = true
     * - Stores returnRoute for back navigation
     * - Stores list/item indices for scroll restoration
     * - Does NOT create a new player instance (uses player:internal session)
     *
     * @param fromRoute The navigation route to return to (e.g., "library", "detail/123")
     * @param mediaId The media ID of the currently playing content
     * @param rowIndex Optional row index for scroll position restoration
     * @param itemIndex Optional item index within row for focus restoration
     */
    fun enterMiniPlayer(
            fromRoute: String,
            mediaId: Long? = null,
            rowIndex: Int? = null,
            itemIndex: Int? = null,
    )

    /**
     * Exit MiniPlayer mode.
     *
     * **Behavior when returnToFullPlayer = true:**
     * - Hides the MiniPlayer overlay
     * - Navigates to the full player route
     * - Playback continues seamlessly
     *
     * **Behavior when returnToFullPlayer = false:**
     * - Hides the MiniPlayer overlay
     * - Does NOT navigate (stays on current screen)
     * - Playback may continue in background or stop based on context
     *
     * @param returnToFullPlayer If true, navigate back to full player
     */
    fun exitMiniPlayer(returnToFullPlayer: Boolean)

    /**
     * Update the display mode.
     *
     * @param mode New display mode (NORMAL or RESIZE)
     */
    fun updateMode(mode: MiniPlayerMode)

    /**
     * Update the anchor position.
     *
     * @param anchor New anchor position
     */
    fun updateAnchor(anchor: MiniPlayerAnchor)

    /**
     * Update the size.
     *
     * @param size New size
     */
    fun updateSize(size: DpSize)

    /**
     * Update the precise position offset.
     *
     * @param offset New position offset (used for drag/move)
     */
    fun updatePosition(offset: Offset)

    /**
     * Clear all state and return to initial state. Does NOT stop playback - that's controlled by
     * player:internal.
     */
    fun reset()

    // ══════════════════════════════════════════════════════════════════
    // RESIZE MODE METHODS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Enter resize mode.
     *
     * **Behavior:**
     * - Sets mode = RESIZE
     * - Stores current size as previousSize (if not already set)
     * - Stores current position as previousPosition (if not already set)
     *
     * After entering resize mode:
     * - FF/RW change size (coarse)
     * - DPAD moves the MiniPlayer (fine)
     * - CENTER confirms new size/position
     * - BACK cancels and restores previous size/position
     */
    fun enterResizeMode()

    /**
     * Apply a size delta to the MiniPlayer.
     *
     * Only effective in RESIZE mode. Size is clamped to MIN_MINI_SIZE and MAX_MINI_SIZE.
     *
     * @param deltaSize Size change to apply (positive = larger, negative = smaller)
     */
    fun applyResize(deltaSize: DpSize)

    /**
     * Move the MiniPlayer by a position delta.
     *
     * Only effective in RESIZE mode. Position is clamped inside layout bounds (screen edges).
     *
     * @param delta Position offset to add (positive X = right, positive Y = down)
     */
    fun moveBy(delta: Offset)

    /**
     * Confirm the resize operation.
     *
     * **Behavior:**
     * - Sets mode = NORMAL
     * - Clears previousSize and previousPosition
     * - Keeps current size and position as the new baseline
     * - Snaps to nearest anchor if enabled
     */
    fun confirmResize()

    /**
     * Cancel the resize operation.
     *
     * **Behavior:**
     * - Restores previousSize/previousPosition if present
     * - Sets mode = NORMAL
     * - Clears previousSize and previousPosition
     */
    fun cancelResize()

    // ══════════════════════════════════════════════════════════════════
    // SNAPPING & BOUNDS METHODS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Snap the MiniPlayer to the nearest anchor.
     *
     * Called automatically when confirming resize or on drag end. Uses the current position to
     * determine the nearest anchor point.
     *
     * @param screenWidthPx Screen width in pixels
     * @param screenHeightPx Screen height in pixels
     * @param density Density for dp to px conversion
     */
    fun snapToNearestAnchor(
            screenWidthPx: Float,
            screenHeightPx: Float,
            density: Density,
    )

    /**
     * Clamp the MiniPlayer position to safe margins within screen bounds.
     *
     * Ensures the MiniPlayer is fully visible and doesn't overlap screen edges.
     *
     * @param screenWidthPx Screen width in pixels
     * @param screenHeightPx Screen height in pixels
     * @param density Density for dp to px conversion
     */
    fun clampToSafeArea(
            screenWidthPx: Float,
            screenHeightPx: Float,
            density: Density,
    )

    /** Mark the first-time hint as shown. */
    fun markFirstTimeHintShown()
}
