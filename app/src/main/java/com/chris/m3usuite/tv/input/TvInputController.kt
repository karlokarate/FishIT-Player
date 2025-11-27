package com.chris.m3usuite.tv.input

import androidx.compose.runtime.State

/**
 * Interface for the global TV input controller.
 *
 * TvInputController is the central orchestrator for all TV remote input handling.
 * It processes key events, resolves semantic actions via TvScreenInputConfig,
 * and dispatches them to appropriate handlers (FocusKit, player, overlays).
 *
 * This interface is intentionally minimal for Phase 6 Task 3:
 * - Key event handling with role-based dispatch
 * - Observable state for quick actions and focused action
 * - Stub hooks for future FocusKit integration (TvNavigationDelegate)
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 5.1
 *
 * Pipeline Position:
 * ```
 * KeyEvent → TvKeyDebouncer → TvKeyMapper → TvInputController → TvAction dispatch
 *                                              ↓
 *                                    - FocusKit (navigation actions)
 *                                    - Screen callbacks (playback/menu actions)
 *                                    - Overlay managers (overlay actions)
 * ```
 *
 * Phase 6 Task 3:
 * - Interface definition
 * - DefaultTvInputController implementation with stub FocusKit hooks
 * - Integration with SIP player as consumer
 *
 * @see DefaultTvInputController for the default implementation
 * @see GlobalTvInputHost for the KeyEvent entry point
 */
interface TvInputController {
    /**
     * Process a key event and dispatch the resolved action.
     *
     * This method is called by GlobalTvInputHost after debouncing.
     * It should:
     * 1. Use TvScreenInputConfig to resolve the TvAction from (screenId, role, ctx)
     * 2. Apply Kids Mode and overlay blocking filters (already in config's resolve)
     * 3. Dispatch the action to the appropriate handler
     * 4. Update internal state (quickActionsVisible, focusedAction)
     *
     * @param role The semantic key role (already mapped from KeyEvent)
     * @param ctx The current screen context
     * @return True if the event was handled/consumed, false otherwise
     */
    fun onKeyEvent(
        role: TvKeyRole,
        ctx: TvScreenContext,
    ): Boolean

    /**
     * Observable state for quick actions panel visibility.
     *
     * This state is toggled when:
     * - OPEN_QUICK_ACTIONS action is dispatched → true
     * - BACK action when quick actions are visible → false
     * - Screen navigation occurs → false
     *
     * Screens can observe this to show/hide the quick actions panel.
     */
    val quickActionsVisible: State<Boolean>

    /**
     * Observable state for the currently focused action.
     *
     * This represents the action that would be executed if the user
     * presses DPAD_CENTER. It's used by UI to highlight the focused
     * control and for accessibility announcements.
     *
     * Value is null when no focusable action is highlighted.
     */
    val focusedAction: State<TvAction?>
}

/**
 * Delegate interface for navigation actions.
 *
 * This interface abstracts FocusKit integration, allowing TvInputController
 * to dispatch navigation actions without directly depending on FocusKit.
 * The actual FocusKit wiring will be done in Phase 6 Task 4.
 *
 * Phase 6 Task 3: Stub interface only; implementations provided in Task 4.
 *
 * @see TvInputController
 */
interface TvNavigationDelegate {
    /**
     * Move focus in the specified direction within the current focus zone.
     *
     * @param action The navigation action (NAVIGATE_UP, NAVIGATE_DOWN, etc.)
     * @return True if focus was moved, false if no valid target was found
     */
    fun moveFocus(action: TvAction): Boolean

    /**
     * Request focus on a specific zone.
     *
     * @param action The focus action (FOCUS_QUICK_ACTIONS, FOCUS_TIMELINE)
     * @return True if focus was moved to the zone, false if zone not found
     */
    fun focusZone(action: TvAction): Boolean
}

/**
 * No-op implementation of TvNavigationDelegate.
 *
 * Used as default until FocusKit integration is completed in Task 4.
 * All navigation requests return false (not handled).
 */
object NoOpTvNavigationDelegate : TvNavigationDelegate {
    override fun moveFocus(action: TvAction): Boolean = false

    override fun focusZone(action: TvAction): Boolean = false
}

/**
 * Listener interface for TvAction dispatch.
 *
 * This interface allows screens to receive TvAction callbacks from the controller.
 * Each screen can implement this to handle screen-specific actions.
 *
 * @see TvInputController
 */
fun interface TvActionListener {
    /**
     * Called when a TvAction should be executed.
     *
     * @param action The action to execute
     * @return True if the action was handled, false otherwise
     */
    fun onAction(action: TvAction): Boolean
}
