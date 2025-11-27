package com.chris.m3usuite.tv.input

import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.focus.FocusZoneId

/**
 * FocusKit-backed implementation of [TvNavigationDelegate].
 *
 * This delegate bridges the global TV input system with FocusKit's zone management.
 * It handles:
 * - NAVIGATE_* actions by delegating to FocusKit's moveDpad* methods
 * - FOCUS_* actions by delegating to FocusKit's requestZoneFocus method
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 6.2
 *
 * Phase 6 Task 5: Full FocusKit integration
 *
 * @see TvNavigationDelegate
 * @see FocusKit
 * @see FocusZoneId
 */
class FocusKitNavigationDelegate : TvNavigationDelegate {
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
     *
     * @param action The focus action
     * @return True if focus was moved to the zone, false if zone not found
     */
    override fun focusZone(action: TvAction): Boolean {
        val zoneId = mapActionToZone(action) ?: return false
        return FocusKit.requestZoneFocus(zoneId)
    }

    /**
     * Map a focus TvAction to a FocusZoneId.
     *
     * @param action The focus action
     * @return The corresponding FocusZoneId, or null if action is not a focus action
     */
    private fun mapActionToZone(action: TvAction): FocusZoneId? =
        when (action) {
            TvAction.FOCUS_QUICK_ACTIONS -> FocusZoneId.QUICK_ACTIONS
            TvAction.FOCUS_TIMELINE -> FocusZoneId.TIMELINE
            else -> null
        }

    companion object {
        /**
         * Get the FocusZoneId corresponding to a focus TvAction.
         *
         * Public utility for tests and diagnostics.
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
