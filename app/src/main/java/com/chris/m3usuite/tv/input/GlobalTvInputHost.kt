package com.chris.m3usuite.tv.input

import android.view.KeyEvent
import com.chris.m3usuite.player.TvKeyDebouncer
import kotlinx.coroutines.CoroutineScope

/**
 * Global host for TV input handling.
 *
 * GlobalTvInputHost is the entry point for all TV remote key events. It:
 * 1. Owns a [TvKeyDebouncer] instance to prevent rapid duplicate events
 * 2. Maps debounced KeyEvents to [TvKeyRole] via [TvKeyMapper]
 * 3. Resolves [TvAction] via [TvScreenInputConfig] (Kids Mode + overlay filtering)
 * 4. Passes the resolved action to [TvInputController] for dispatch
 * 5. Logs events via [TvInputDebugSink] for diagnostics
 *
 * Contract Reference:
 * - INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 9.1
 * - GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md (double BACK → Exit to Home)
 *
 * Pipeline:
 * ```
 * KeyEvent → TvKeyDebouncer → TvKeyRole → TvInputController → TvAction dispatch
 * ```
 *
 * ## Double BACK → Exit to Home
 *
 * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md:
 * - Single BACK → normal BACK action (close overlay, go back)
 * - Double BACK within threshold → TvAction.EXIT_TO_HOME
 *
 * The double-BACK detection is implemented in this host layer:
 * 1. Track the timestamp of the last BACK key press
 * 2. If a second BACK arrives within [DOUBLE_BACK_THRESHOLD_MS], dispatch EXIT_TO_HOME
 * 3. Otherwise, dispatch normal BACK action
 *
 * Note: The actual navigation to home screen is NOT implemented in this task.
 * This is a contract-level hook. The navigation layer will handle EXIT_TO_HOME
 * in a future task.
 *
 * Usage (from Composable root or Activity):
 * ```kotlin
 * val host = remember {
 *     GlobalTvInputHost(
 *         controller = controller,
 *         configs = DefaultTvScreenConfigs.all,
 *         scope = coroutineScope,
 *     )
 * }
 *
 * Modifier.onPreviewKeyEvent { event ->
 *     host.handleKeyEvent(event, currentContext)
 * }
 * ```
 *
 * Phase 6 Task 3:
 * - GlobalTvInputHost implementation
 * - TvKeyDebouncer integration
 * - Full pipeline from KeyEvent to controller dispatch
 * - Debug logging via TvInputDebugSink
 *
 * Phase 6 Task 4:
 * - Double BACK → EXIT_TO_HOME hook (contract-level, no navigation yet)
 *
 * @param controller The [TvInputController] to receive processed events
 * @param configs Map of screen configurations for action resolution
 * @param scope CoroutineScope for TvKeyDebouncer job management
 * @param debug Debug sink for event logging (default: no-op)
 * @param debounceMs Debounce threshold in milliseconds (default: 300ms for Fire TV)
 * @param enableDebouncing Whether to enable debouncing (default: true)
 * @param doubleBackThresholdMs Threshold for double-BACK detection (default: 500ms)
 */
class GlobalTvInputHost(
    private val controller: TvInputController,
    private val configs: Map<TvScreenId, TvScreenInputConfig> = DefaultTvScreenConfigs.all,
    scope: CoroutineScope,
    private val debug: TvInputDebugSink = NoOpTvInputDebugSink,
    debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    enableDebouncing: Boolean = true,
    private val doubleBackThresholdMs: Long = DOUBLE_BACK_THRESHOLD_MS,
) {
    companion object {
        /** Default debounce threshold (300ms) optimized for Fire TV remotes */
        const val DEFAULT_DEBOUNCE_MS = 300L

        /**
         * Threshold for double-BACK detection.
         *
         * Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md: "Global double BACK = Exit to Home"
         * If two BACK presses occur within this threshold, dispatch EXIT_TO_HOME.
         *
         * Default: 500ms (half a second)
         */
        const val DOUBLE_BACK_THRESHOLD_MS = 500L
    }

    /**
     * Internal TvKeyDebouncer instance.
     *
     * Prevents rapid key events on Fire TV/Android TV remotes from causing
     * endless seek/scrubbing states. Positioned at the start of the pipeline
     * per contract Section 9.2.
     */
    private val debouncer =
        TvKeyDebouncer(
            scope = scope,
            debounceMs = debounceMs,
            enableDebouncing = enableDebouncing,
        )

    /**
     * Timestamp of the last BACK key press for double-BACK detection.
     *
     * Used to determine if a second BACK press should trigger EXIT_TO_HOME.
     */
    private var lastBackPressTimeMs: Long = 0L

    /**
     * Handle a raw KeyEvent from the Compose/View input system.
     *
     * This is the main entry point for TV remote key events. It:
     * 1. Passes the event through TvKeyDebouncer
     * 2. Maps debounced events to TvKeyRole
     * 3. Checks for double-BACK → EXIT_TO_HOME
     * 4. Resolves TvAction via TvScreenInputConfig
     * 5. Dispatches to TvInputController
     * 6. Logs the event via TvInputDebugSink
     *
     * @param event The raw Android KeyEvent
     * @param ctx The current screen context
     * @return True if the event was handled/consumed, false otherwise
     */
    fun handleKeyEvent(
        event: KeyEvent,
        ctx: TvScreenContext,
    ): Boolean {
        // Only process ACTION_DOWN to avoid duplicate handling on key release
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        // Use debouncer to prevent rapid duplicate events
        return debouncer.handleKeyEvent(event.keyCode, event) { keyCode, _ ->
            processKeyEvent(event, ctx)
        }
    }

    /**
     * Internal method to process a debounced key event.
     *
     * Called by the debouncer when an event should be processed.
     */
    private fun processKeyEvent(
        event: KeyEvent,
        ctx: TvScreenContext,
    ): Boolean {
        // Step 1: Map KeyEvent to TvKeyRole
        val role = TvKeyMapper.mapDebounced(event)

        // Unsupported keycode - log and return unhandled
        if (role == null) {
            debug.onTvInputEvent(
                event = event,
                role = null,
                action = null,
                ctx = ctx,
                handled = false,
            )
            return false
        }

        // Step 2: Check for double-BACK → EXIT_TO_HOME
        // Per GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md: "Global double BACK = Exit to Home"
        val action = resolveActionWithDoubleBackCheck(role, ctx)

        // Step 3: Dispatch to controller
        val handled = controller.onKeyEvent(role, ctx)

        // Step 4: Log the event
        debug.onTvInputEvent(
            event = event,
            role = role,
            action = action,
            ctx = ctx,
            handled = handled,
        )

        return handled
    }

    /**
     * Resolve the action for a key role, with special handling for double-BACK.
     *
     * Double-BACK Detection Logic:
     * 1. If the role is BACK, check if a previous BACK occurred within threshold
     * 2. If yes, return EXIT_TO_HOME instead of BACK
     * 3. Update the last BACK press timestamp
     *
     * TODO: The navigation layer should handle TvAction.EXIT_TO_HOME by navigating
     * to the home/start screen. This host only dispatches the action.
     *
     * @param role The key role to resolve
     * @param ctx The screen context
     * @return The resolved action, potentially EXIT_TO_HOME for double-BACK
     */
    private fun resolveActionWithDoubleBackCheck(
        role: TvKeyRole,
        ctx: TvScreenContext,
    ): TvAction? {
        // Get the base action from config
        val baseAction = configs.resolve(ctx.screenId, role, ctx)

        // Special handling for BACK key → double-BACK detection
        if (role == TvKeyRole.BACK && baseAction == TvAction.BACK) {
            val currentTimeMs = System.currentTimeMillis()
            val timeSinceLastBack = currentTimeMs - lastBackPressTimeMs

            // Update timestamp for next BACK detection
            lastBackPressTimeMs = currentTimeMs

            // Check if this is a double-BACK within threshold
            if (timeSinceLastBack in 1 until doubleBackThresholdMs) {
                // Double-BACK detected! Return EXIT_TO_HOME
                // Note: Reset timestamp to prevent triple-BACK from continuing to trigger
                lastBackPressTimeMs = 0L

                // TODO: Navigation layer should handle EXIT_TO_HOME action.
                // This host only dispatches the action; actual navigation is not
                // implemented in this task per Phase 6 Task 4 constraints.
                return TvAction.EXIT_TO_HOME
            }
        }

        return baseAction
    }

    /**
     * Reset the debouncer state.
     *
     * Called when navigating away from a screen or when the host is being disposed.
     */
    fun reset() {
        debouncer.resetAll()
        lastBackPressTimeMs = 0L
    }

    /**
     * Reset debouncer state for a specific key.
     *
     * @param keyCode The Android KeyEvent.KEYCODE_* value to reset
     */
    fun resetKey(keyCode: Int) {
        debouncer.resetKey(keyCode)
    }

    /**
     * Reset the double-BACK detection state.
     *
     * Called when navigating to a new screen to prevent accidental EXIT_TO_HOME.
     */
    fun resetDoubleBackState() {
        lastBackPressTimeMs = 0L
    }
}
