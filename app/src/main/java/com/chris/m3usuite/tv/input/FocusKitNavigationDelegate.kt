package com.chris.m3usuite.tv.input

import com.chris.m3usuite.player.miniplayer.DefaultMiniPlayerManager
import com.chris.m3usuite.player.miniplayer.MiniPlayerManager
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.focus.FocusZoneId

/**
 * FocusKit-backed implementation of [TvNavigationDelegate].
 *
 * This delegate bridges the global TV input system with FocusKit's zone management.
 * It handles:
 * - NAVIGATE_* actions by delegating to FocusKit's moveDpad* methods
 * - FOCUS_* actions by delegating to FocusKit's requestZoneFocus method
 * - TOGGLE_MINI_PLAYER_FOCUS action for Phase 7 MiniPlayer focus toggle
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 6.2
 * Contract Reference: INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 6
 *
 * Phase 6 Task 5: Full FocusKit integration
 * Phase 7 Task 2: TOGGLE_MINI_PLAYER_FOCUS support
 *
 * @param miniPlayerManager The MiniPlayer state manager for visibility checks
 * @see TvNavigationDelegate
 * @see FocusKit
 * @see FocusZoneId
 */
class FocusKitNavigationDelegate(
    private val miniPlayerManager: MiniPlayerManager = DefaultMiniPlayerManager,
) : TvNavigationDelegate {
    /**
     * Move focus in the specified direction within the current focus zone.
     *
     * Maps NAVIGATE_* actions to FocusKit's moveDpad* methods:
     * - NAVIGATE_UP → FocusKit.moveDpadUp()
     * - NAVIGATE_DOWN → FocusKit.moveDpadDown()
     * - NAVIGATE_LEFT → FocusKit.moveDpadLeft()
     * - NAVIGATE_RIGHT → FocusKit.moveDpadRight()
     *
     * @param action The navigation action
     * @return True if focus was moved, false otherwise
     */
    override fun moveFocus(action: TvAction): Boolean =
        when (action) {
            TvAction.NAVIGATE_UP -> FocusKit.moveDpadUp()
            TvAction.NAVIGATE_DOWN -> FocusKit.moveDpadDown()
            TvAction.NAVIGATE_LEFT -> FocusKit.moveDpadLeft()
            TvAction.NAVIGATE_RIGHT -> FocusKit.moveDpadRight()
            else -> false
        }

    /**
     * Request focus on a specific zone based on the focus action.
     *
     * Maps FOCUS_* actions to FocusZoneId and calls FocusKit.requestZoneFocus():
     * - FOCUS_QUICK_ACTIONS → FocusZoneId.QUICK_ACTIONS
     * - FOCUS_TIMELINE → FocusZoneId.TIMELINE
     * - TOGGLE_MINI_PLAYER_FOCUS → Toggle between MINI_PLAYER and PRIMARY_UI
     *
     * **Phase 7 TOGGLE_MINI_PLAYER_FOCUS Behavior:**
     * - If MiniPlayerState.visible == false → no-op (ignore action)
     * - If MiniPlayerState.visible == true:
     *   - If current focus zone == PRIMARY_UI → focus MINI_PLAYER
     *   - If current focus zone == MINI_PLAYER → focus PRIMARY_UI
     *
     * @param action The focus action
     * @return True if focus was moved to the zone, false if zone not found
     */
    override fun focusZone(action: TvAction): Boolean {
        // Handle TOGGLE_MINI_PLAYER_FOCUS specially
        if (action == TvAction.TOGGLE_MINI_PLAYER_FOCUS) {
            return handleToggleMiniPlayerFocus()
        }

        val zoneId = zoneForAction(action) ?: return false
        return FocusKit.requestZoneFocus(zoneId)
    }

    /**
     * Handle TOGGLE_MINI_PLAYER_FOCUS action.
     *
     * ════════════════════════════════════════════════════════════════════════════════
     * PHASE 7 – MiniPlayer Focus Toggle (Long-press PLAY)
     * ════════════════════════════════════════════════════════════════════════════════
     *
     * **Behavior:**
     * - If MiniPlayer is NOT visible → no-op (return false)
     * - If MiniPlayer IS visible:
     *   - If current focus zone is PRIMARY_UI → move focus to MINI_PLAYER
     *   - If current focus zone is MINI_PLAYER → move focus to PRIMARY_UI
     *   - If current focus zone is neither → default to focusing MINI_PLAYER
     *
     * **Contract Reference:**
     * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Section 6
     *
     * @return True if focus toggle was performed, false if MiniPlayer not visible
     */
    private fun handleToggleMiniPlayerFocus(): Boolean {
        // Check if MiniPlayer is visible
        val miniPlayerVisible = miniPlayerManager.state.value.visible
        if (!miniPlayerVisible) {
            // MiniPlayer not visible - ignore action
            return false
        }

        // Get current focus zone
        val currentZone = FocusKit.getCurrentZone()

        // Toggle focus between MINI_PLAYER and PRIMARY_UI
        return when (currentZone) {
            FocusZoneId.PRIMARY_UI -> {
                // Currently in PRIMARY_UI → move focus to MINI_PLAYER
                FocusKit.requestZoneFocus(FocusZoneId.MINI_PLAYER)
            }
            FocusZoneId.MINI_PLAYER -> {
                // Currently in MINI_PLAYER → move focus to PRIMARY_UI
                FocusKit.requestZoneFocus(FocusZoneId.PRIMARY_UI)
            }
            else -> {
                // Neither zone currently focused → default to MINI_PLAYER
                // This happens when:
                // - Focus is on a screen element not marked with focusZone()
                // - Focus is in a dialog or overlay
                // - No focusable element has focus yet
                //
                // We default to MINI_PLAYER because the user explicitly triggered
                // TOGGLE_MINI_PLAYER_FOCUS (long-press PLAY), indicating intent
                // to interact with the MiniPlayer.
                com.chris.m3usuite.core.debug.GlobalDebug.logDpad(
                    "TOGGLE_MINI_PLAYER_FOCUS fallback",
                    mapOf("currentZone" to currentZone?.name),
                )
                FocusKit.requestZoneFocus(FocusZoneId.MINI_PLAYER)
            }
        }
    }

    companion object {
        /**
         * Get the FocusZoneId corresponding to a focus TvAction.
         *
         * Public utility for tests and diagnostics.
         *
         * Note: TOGGLE_MINI_PLAYER_FOCUS is not included here because it
         * toggles between zones rather than targeting a specific zone.
         *
         * @param action The focus action
         * @return The corresponding FocusZoneId, or null if not a focus action
         */
        fun zoneForAction(action: TvAction): FocusZoneId? =
            when (action) {
                TvAction.FOCUS_QUICK_ACTIONS -> FocusZoneId.QUICK_ACTIONS
                TvAction.FOCUS_TIMELINE -> FocusZoneId.TIMELINE
                else -> null
            }
    }
}
