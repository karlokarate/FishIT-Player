package com.chris.m3usuite.tv.input

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.chris.m3usuite.tv.input.TvAction.Companion.isFocusAction
import com.chris.m3usuite.tv.input.TvAction.Companion.isNavigationAction

/**
 * Default implementation of [TvInputController].
 *
 * This controller:
 * - Uses [TvScreenInputConfig] to resolve [TvAction] from (screenId, role, context)
 * - Manages internal state for [quickActionsVisible] and [focusedAction]
 * - Delegates navigation actions to [TvNavigationDelegate] (stub in Task 3)
 * - Forwards screen-specific actions to the registered [TvActionListener]
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 5.2
 *
 * Phase 6 Task 3 Scope:
 * - Full action resolution via TvScreenInputConfig (Kids Mode + overlay filtering baked in)
 * - Quick actions visibility management
 * - Focused action tracking for UI highlighting
 * - Stub hooks for FocusKit integration (TvNavigationDelegate)
 * - Listener-based action dispatch to screens
 *
 * @param configs Map of screen configurations (typically [DefaultTvScreenConfigs.all])
 * @param navigationDelegate Delegate for focus navigation (default: no-op)
 */
class DefaultTvInputController(
    private val configs: Map<TvScreenId, TvScreenInputConfig> = DefaultTvScreenConfigs.all,
    private val navigationDelegate: TvNavigationDelegate = NoOpTvNavigationDelegate,
) : TvInputController {
    // ════════════════════════════════════════════════════════════════════════════
    // INTERNAL STATE
    // ════════════════════════════════════════════════════════════════════════════

    private val _quickActionsVisible: MutableState<Boolean> = mutableStateOf(false)
    private val _focusedAction: MutableState<TvAction?> = mutableStateOf(null)

    /**
     * Registered action listener for screen-specific action dispatch.
     * Set this to receive action callbacks from the controller.
     */
    var actionListener: TvActionListener? = null

    // ════════════════════════════════════════════════════════════════════════════
    // PUBLIC STATE (from interface)
    // ════════════════════════════════════════════════════════════════════════════

    override val quickActionsVisible: State<Boolean>
        get() = _quickActionsVisible

    override val focusedAction: State<TvAction?>
        get() = _focusedAction

    // ════════════════════════════════════════════════════════════════════════════
    // KEY EVENT PROCESSING
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Process a key event and dispatch the resolved action.
     *
     * Pipeline:
     * 1. Resolve TvAction via TvScreenInputConfig (with Kids Mode + overlay filtering)
     * 2. Handle quick actions visibility toggle
     * 3. Dispatch navigation actions to TvNavigationDelegate
     * 4. Dispatch focus actions to TvNavigationDelegate
     * 5. Forward remaining actions to actionListener
     * 6. Update focusedAction for UI highlighting
     *
     * @param role The semantic key role
     * @param ctx The current screen context
     * @return True if handled, false otherwise
     */
    override fun onKeyEvent(
        role: TvKeyRole,
        ctx: TvScreenContext,
    ): Boolean {
        // Step 1: Resolve action via config (Kids Mode + overlay filtering applied)
        val action = configs.resolve(ctx.screenId, role, ctx)

        // No action = not handled (key may fall through to other handlers)
        if (action == null) {
            return false
        }

        // Step 2: Handle BACK specially when quick actions are visible
        if (action == TvAction.BACK && _quickActionsVisible.value) {
            _quickActionsVisible.value = false
            return true
        }

        // Step 3: Handle OPEN_QUICK_ACTIONS
        if (action == TvAction.OPEN_QUICK_ACTIONS) {
            _quickActionsVisible.value = true
            return true
        }

        // Step 4: Handle navigation actions via delegate
        if (action.isNavigationAction()) {
            val handled = navigationDelegate.moveFocus(action)
            // Update focused action for UI feedback even if delegate didn't handle it
            // This allows screens to track which direction was last pressed
            _focusedAction.value = action
            return handled
        }

        // Step 5: Handle focus zone actions via delegate
        if (action.isFocusAction()) {
            val handled = navigationDelegate.focusZone(action)
            _focusedAction.value = action
            return handled
        }

        // Step 6: Forward to action listener
        val handled = actionListener?.onAction(action) ?: false

        // Update focused action for playback/menu actions too
        // This allows UI to highlight the active control
        if (handled) {
            _focusedAction.value = action
        }

        return handled
    }

    // ════════════════════════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Reset controller state.
     *
     * Called when navigating away from a screen or when the player is closed.
     * Resets quick actions visibility and focused action.
     */
    fun resetState() {
        _quickActionsVisible.value = false
        _focusedAction.value = null
    }

    /**
     * Explicitly set quick actions visibility.
     *
     * Primarily for testing or programmatic control.
     */
    fun setQuickActionsVisible(visible: Boolean) {
        _quickActionsVisible.value = visible
    }

    /**
     * Explicitly set focused action.
     *
     * Primarily for testing or programmatic control.
     */
    fun setFocusedAction(action: TvAction?) {
        _focusedAction.value = action
    }
}
