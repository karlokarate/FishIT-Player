package com.chris.m3usuite.player.miniplayer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
