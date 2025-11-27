package com.chris.m3usuite.tv.input

import android.view.KeyEvent

/**
 * Debug sink interface for TV input event logging.
 *
 * This interface allows GlobalTvInputHost to report input events for diagnostics
 * without depending on any specific logging implementation. The actual implementation
 * (e.g., integrating with GlobalDebug/DiagnosticsLogger) will be done in Phase 6 Task 5.
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 7
 *
 * Phase 6 Task 3:
 * - Define interface for debug event logging
 * - Implementation deferred to Task 5 (TV Input Inspector)
 *
 * @see GlobalTvInputHost for usage
 */
interface TvInputDebugSink {
    /**
     * Called when a TV input event is processed by the global input pipeline.
     *
     * @param event The raw Android KeyEvent
     * @param role The resolved TvKeyRole (null if keycode not supported)
     * @param action The resolved TvAction (null if blocked or unmapped)
     * @param ctx The screen context at the time of the event
     * @param handled True if the event was consumed by the controller
     */
    fun onTvInputEvent(
        event: KeyEvent,
        role: TvKeyRole?,
        action: TvAction?,
        ctx: TvScreenContext,
        handled: Boolean,
    )
}

/**
 * No-op implementation of TvInputDebugSink.
 *
 * Used as default when no debug logging is needed.
 */
object NoOpTvInputDebugSink : TvInputDebugSink {
    override fun onTvInputEvent(
        event: KeyEvent,
        role: TvKeyRole?,
        action: TvAction?,
        ctx: TvScreenContext,
        handled: Boolean,
    ) {
        // No-op: Silent sink for production builds or when debugging is disabled
    }
}
