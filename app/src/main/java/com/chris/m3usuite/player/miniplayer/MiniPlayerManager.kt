package com.chris.m3usuite.player.miniplayer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/**
 * Interface for managing MiniPlayer state and transitions.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – MiniPlayer Domain Manager
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
 * - No direct ExoPlayer usage: Interacts with PlaybackSession through state
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Sections 4.1, 4.2
 */
interface MiniPlayerManager {
    /**
     * Observable state of the MiniPlayer.
     */
    val state: StateFlow<MiniPlayerState>

    /**
     * Enter MiniPlayer mode from full player.
     *
     * This method should be called when the user triggers the PIP button.
     * It stores the return context for navigation back to the originating screen.
     *
     * **Behavior:**
     * - Sets visible = true
     * - Stores returnRoute for back navigation
     * - Stores list/item indices for scroll restoration
     * - Does NOT create a new player instance (uses PlaybackSession)
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
     * Clear all state and return to initial state.
     * Does NOT stop playback - that's controlled by PlaybackSession.
     */
    fun reset()

    // ══════════════════════════════════════════════════════════════════
    // PHASE 7 – RESIZE MODE METHODS
    // Contract Reference: INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md
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
     * Only effective in RESIZE mode.
     * Size is clamped to MIN_MINI_SIZE and MAX_MINI_SIZE.
     *
     * @param deltaSize Size change to apply (positive = larger, negative = smaller)
     */
    fun applyResize(deltaSize: DpSize)

    /**
     * Move the MiniPlayer by a position delta.
     *
     * Only effective in RESIZE mode.
     * Position is clamped inside layout bounds (screen edges).
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
    // PHASE 7 – SNAPPING & BOUNDS METHODS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Snap the MiniPlayer to the nearest anchor.
     *
     * Called automatically when confirming resize or on drag end.
     * Uses the current position to determine the nearest anchor point.
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

    /**
     * Mark the first-time hint as shown.
     */
    fun markFirstTimeHintShown()
}

/**
 * Default implementation of MiniPlayerManager.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – MiniPlayer Manager Implementation
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This singleton implementation manages MiniPlayer state across the app.
 * It is designed to be independent of:
 * - ViewModels (no ViewModel dependencies)
 * - Navigation (no NavController - caller handles navigation)
 * - UI (no Compose dependencies except unit types)
 *
 * **Thread Safety:**
 * - Uses StateFlow for thread-safe state updates
 * - update() operations are atomic
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Sections 4.1, 4.2
 */
object DefaultMiniPlayerManager : MiniPlayerManager {
    private val _state = MutableStateFlow(MiniPlayerState.INITIAL)
    override val state: StateFlow<MiniPlayerState> = _state.asStateFlow()

    override fun enterMiniPlayer(
        fromRoute: String,
        mediaId: Long?,
        rowIndex: Int?,
        itemIndex: Int?,
    ) {
        _state.update { current ->
            current.copy(
                visible = true,
                mode = MiniPlayerMode.NORMAL,
                returnRoute = fromRoute,
                returnMediaId = mediaId,
                returnRowIndex = rowIndex,
                returnItemIndex = itemIndex,
            )
        }
    }

    override fun exitMiniPlayer(returnToFullPlayer: Boolean) {
        _state.update { current ->
            current.copy(
                visible = false,
                // Keep return context if returning to full player (for potential back navigation)
                // Clear it otherwise
                returnRoute = if (returnToFullPlayer) current.returnRoute else null,
                returnMediaId = if (returnToFullPlayer) current.returnMediaId else null,
                returnRowIndex = if (returnToFullPlayer) current.returnRowIndex else null,
                returnItemIndex = if (returnToFullPlayer) current.returnItemIndex else null,
            )
        }
    }

    override fun updateMode(mode: MiniPlayerMode) {
        _state.update { it.copy(mode = mode) }
    }

    override fun updateAnchor(anchor: MiniPlayerAnchor) {
        _state.update { it.copy(anchor = anchor) }
    }

    override fun updateSize(size: DpSize) {
        _state.update { it.copy(size = size) }
    }

    override fun updatePosition(offset: Offset) {
        _state.update { it.copy(position = offset) }
    }

    override fun reset() {
        _state.value = MiniPlayerState.INITIAL
    }

    // ══════════════════════════════════════════════════════════════════
    // PHASE 7 – RESIZE MODE IMPLEMENTATION
    // ══════════════════════════════════════════════════════════════════

    override fun enterResizeMode() {
        _state.update { current ->
            // Only enter resize mode if MiniPlayer is visible
            if (!current.visible) return@update current

            current.copy(
                mode = MiniPlayerMode.RESIZE,
                // Store current size/position for cancel restoration (only if not already in resize)
                previousSize = current.previousSize ?: current.size,
                previousPosition = current.previousPosition ?: current.position,
            )
        }
    }

    override fun applyResize(deltaSize: DpSize) {
        _state.update { current ->
            // Only apply resize in RESIZE mode
            if (current.mode != MiniPlayerMode.RESIZE) return@update current

            // Calculate new size with clamping
            val newWidth =
                (current.size.width + deltaSize.width).coerceIn(
                    MIN_MINI_SIZE.width,
                    MAX_MINI_SIZE.width,
                )
            val newHeight =
                (current.size.height + deltaSize.height).coerceIn(
                    MIN_MINI_SIZE.height,
                    MAX_MINI_SIZE.height,
                )

            current.copy(size = DpSize(newWidth, newHeight))
        }
    }

    override fun moveBy(delta: Offset) {
        _state.update { current ->
            // Only move in RESIZE mode
            if (current.mode != MiniPlayerMode.RESIZE) return@update current

            // Calculate new position (or start from Offset.Zero if null)
            val currentPos = current.position ?: Offset.Zero
            val newPosition =
                Offset(
                    x = currentPos.x + delta.x,
                    y = currentPos.y + delta.y,
                )

            current.copy(position = newPosition)
        }
    }

    override fun confirmResize() {
        _state.update { current ->
            // Only confirm if in RESIZE mode
            if (current.mode != MiniPlayerMode.RESIZE) return@update current

            current.copy(
                mode = MiniPlayerMode.NORMAL,
                // Clear previous values - current size/position is now the baseline
                previousSize = null,
                previousPosition = null,
            )
        }
    }

    override fun cancelResize() {
        _state.update { current ->
            // Only cancel if in RESIZE mode
            if (current.mode != MiniPlayerMode.RESIZE) return@update current

            current.copy(
                mode = MiniPlayerMode.NORMAL,
                // Restore previous size/position if available
                size = current.previousSize ?: current.size,
                position = current.previousPosition,
                // Clear previous values
                previousSize = null,
                previousPosition = null,
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // PHASE 7 – SNAPPING & BOUNDS IMPLEMENTATION
    // ══════════════════════════════════════════════════════════════════

    override fun snapToNearestAnchor(
        screenWidthPx: Float,
        screenHeightPx: Float,
        density: Density,
    ) {
        _state.update { current ->
            if (!current.visible) return@update current

            val nearestAnchor =
                calculateNearestAnchor(
                    currentPosition = current.position ?: Offset.Zero,
                    currentSize = current.size,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    density = density,
                )

            // Update anchor and reset position offset (anchor defines the base position)
            current.copy(
                anchor = nearestAnchor,
                position = null, // Reset offset since anchor defines position
            )
        }
    }

    override fun clampToSafeArea(
        screenWidthPx: Float,
        screenHeightPx: Float,
        density: Density,
    ) {
        _state.update { current ->
            if (!current.visible || current.position == null) return@update current

            val safeMarginPx = with(density) { SAFE_MARGIN_DP.toPx() }
            val miniWidthPx = with(density) { current.size.width.toPx() }
            val miniHeightPx = with(density) { current.size.height.toPx() }

            // Calculate clamped position based on anchor
            val clampedPosition =
                calculateClampedPosition(
                    position = current.position!!,
                    anchor = current.anchor,
                    miniWidthPx = miniWidthPx,
                    miniHeightPx = miniHeightPx,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    safeMarginPx = safeMarginPx,
                )

            current.copy(position = clampedPosition)
        }
    }

    override fun markFirstTimeHintShown() {
        _state.update { current ->
            current.copy(hasShownFirstTimeHint = true)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════

    private fun calculateNearestAnchor(
        currentPosition: Offset,
        currentSize: DpSize,
        screenWidthPx: Float,
        screenHeightPx: Float,
        density: Density,
    ): MiniPlayerAnchor {
        val miniWidthPx = with(density) { currentSize.width.toPx() }
        val miniHeightPx = with(density) { currentSize.height.toPx() }
        val centerSnapThresholdPx = with(density) { CENTER_SNAP_THRESHOLD_DP.toPx() }

        // Calculate mini player center based on position offset
        // Position offset is relative to the anchor position
        val screenCenterX = screenWidthPx / 2
        val screenCenterY = screenHeightPx / 2

        // Get effective center of the MiniPlayer based on current position
        val effectiveCenterX = currentPosition.x + miniWidthPx / 2
        val effectiveCenterY = currentPosition.y + miniHeightPx / 2

        // Check if near horizontal center (for CENTER_TOP or CENTER_BOTTOM)
        val isNearHorizontalCenter = abs(effectiveCenterX - screenCenterX) < centerSnapThresholdPx

        // Determine vertical position (top or bottom half)
        val isInTopHalf = effectiveCenterY < screenCenterY

        // Determine horizontal position (left or right half)
        val isInLeftHalf = effectiveCenterX < screenCenterX

        return when {
            isNearHorizontalCenter && isInTopHalf -> MiniPlayerAnchor.CENTER_TOP
            isNearHorizontalCenter && !isInTopHalf -> MiniPlayerAnchor.CENTER_BOTTOM
            isInTopHalf && isInLeftHalf -> MiniPlayerAnchor.TOP_LEFT
            isInTopHalf && !isInLeftHalf -> MiniPlayerAnchor.TOP_RIGHT
            !isInTopHalf && isInLeftHalf -> MiniPlayerAnchor.BOTTOM_LEFT
            else -> MiniPlayerAnchor.BOTTOM_RIGHT
        }
    }

    private fun calculateClampedPosition(
        position: Offset,
        anchor: MiniPlayerAnchor,
        miniWidthPx: Float,
        miniHeightPx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        safeMarginPx: Float,
    ): Offset {
        // Calculate valid range based on anchor
        // The offset is relative to the anchor position
        val maxOffsetX = screenWidthPx - miniWidthPx - safeMarginPx * 2
        val maxOffsetY = screenHeightPx - miniHeightPx - safeMarginPx * 2

        // Clamp offset to valid range
        val clampedX = position.x.coerceIn(-maxOffsetX / 2, maxOffsetX / 2)
        val clampedY = position.y.coerceIn(-maxOffsetY / 2, maxOffsetY / 2)

        return Offset(clampedX, clampedY)
    }

    // ══════════════════════════════════════════════════════════════════
    // TEST SUPPORT (for unit tests only)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Reset state for testing purposes.
     * This should only be called from tests.
     */
    internal fun resetForTesting() {
        reset()
    }
}
