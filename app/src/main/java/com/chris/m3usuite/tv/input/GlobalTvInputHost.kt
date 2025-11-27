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
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 9.1
 *
 * Pipeline:
 * ```
 * KeyEvent → TvKeyDebouncer → TvKeyRole → TvInputController → TvAction dispatch
 * ```
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
 * @param controller The [TvInputController] to receive processed events
 * @param configs Map of screen configurations for action resolution
 * @param scope CoroutineScope for TvKeyDebouncer job management
 * @param debug Debug sink for event logging (default: no-op)
 * @param debounceMs Debounce threshold in milliseconds (default: 300ms for Fire TV)
 * @param enableDebouncing Whether to enable debouncing (default: true)
 */
class GlobalTvInputHost(
    private val controller: TvInputController,
    private val configs: Map<TvScreenId, TvScreenInputConfig> = DefaultTvScreenConfigs.all,
    scope: CoroutineScope,
    private val debug: TvInputDebugSink = NoOpTvInputDebugSink,
    debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    enableDebouncing: Boolean = true,
) {
    companion object {
        /** Default debounce threshold (300ms) optimized for Fire TV remotes */
        const val DEFAULT_DEBOUNCE_MS = 300L
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
     * Handle a raw KeyEvent from the Compose/View input system.
     *
     * This is the main entry point for TV remote key events. It:
     * 1. Passes the event through TvKeyDebouncer
     * 2. Maps debounced events to TvKeyRole
     * 3. Resolves TvAction via TvScreenInputConfig
     * 4. Dispatches to TvInputController
     * 5. Logs the event via TvInputDebugSink
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

        // Step 2: Resolve TvAction via config (with Kids Mode + overlay filtering)
        val action = configs.resolve(ctx.screenId, role, ctx)

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
     * Reset the debouncer state.
     *
     * Called when navigating away from a screen or when the host is being disposed.
     */
    fun reset() {
        debouncer.resetAll()
    }

    /**
     * Reset debouncer state for a specific key.
     *
     * @param keyCode The Android KeyEvent.KEYCODE_* value to reset
     */
    fun resetKey(keyCode: Int) {
        debouncer.resetKey(keyCode)
    }
}
