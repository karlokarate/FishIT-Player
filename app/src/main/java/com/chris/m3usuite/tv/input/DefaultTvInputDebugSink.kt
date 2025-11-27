package com.chris.m3usuite.tv.input

import android.view.KeyEvent
import com.chris.m3usuite.core.debug.GlobalDebug
import com.chris.m3usuite.diagnostics.DiagnosticsLogger
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.focus.FocusZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A TV input event snapshot for the debug inspector overlay.
 *
 * This data class captures all relevant information about a processed TV input event
 * for display in the TvInputInspectorOverlay.
 *
 * @property timestamp When the event occurred (epoch millis)
 * @property keyCodeName The Android keycode name (e.g., "KEYCODE_DPAD_LEFT")
 * @property actionType The KeyEvent action ("DOWN", "UP", etc.)
 * @property role The resolved TvKeyRole (e.g., DPAD_LEFT)
 * @property action The resolved TvAction (e.g., NAVIGATE_LEFT)
 * @property screenId The current screen ID
 * @property focusZone The current focus zone (if available)
 * @property handled Whether the event was consumed
 */
data class TvInputEventSnapshot(
    val timestamp: Long,
    val keyCodeName: String,
    val actionType: String,
    val role: TvKeyRole?,
    val action: TvAction?,
    val screenId: TvScreenId,
    val focusZone: FocusZoneId?,
    val handled: Boolean,
)

/**
 * Default implementation of [TvInputDebugSink] that integrates with existing debug infrastructure.
 *
 * This sink:
 * 1. Logs events via [GlobalDebug.logDpad] when GlobalDebug is enabled
 * 2. Logs structured events via [DiagnosticsLogger.ComposeTV.logKeyEvent]
 * 3. Emits events to [events] SharedFlow for the inspector overlay to consume
 * 4. Maintains a rolling [history] of the last [MAX_HISTORY_SIZE] events
 *
 * ## Usage
 *
 * ```kotlin
 * val sink = DefaultTvInputDebugSink
 *
 * // Wire into GlobalTvInputHost
 * val host = GlobalTvInputHost(
 *     controller = controller,
 *     configs = configs,
 *     scope = scope,
 *     debug = sink,
 * )
 *
 * // Observe events in inspector overlay
 * val events by sink.events.collectAsState()
 * ```
 *
 * Contract Reference: INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md Section 7
 *
 * Phase 6 Task 6:
 * - Implement TvInputDebugSink using GlobalDebug and DiagnosticsLogger
 * - Provide StateFlow for inspector overlay consumption
 */
object DefaultTvInputDebugSink : TvInputDebugSink {
    private const val MAX_HISTORY_SIZE = 10

    private val historyBuffer = java.util.ArrayDeque<TvInputEventSnapshot>(MAX_HISTORY_SIZE)
    private val _history = MutableStateFlow<List<TvInputEventSnapshot>>(emptyList())
    private val _events = MutableSharedFlow<TvInputEventSnapshot>(extraBufferCapacity = 16)

    /** Read-only history of recent TV input events. */
    val history: StateFlow<List<TvInputEventSnapshot>> = _history.asStateFlow()

    /** Live stream of TV input events for the inspector overlay. */
    val events: SharedFlow<TvInputEventSnapshot> = _events.asSharedFlow()

    /**
     * Check if capture is enabled.
     * Returns true when GlobalDebug.isTvInputInspectorEnabled() is true.
     */
    val captureEnabled: Boolean
        get() = GlobalDebug.isTvInputInspectorEnabled()

    override fun onTvInputEvent(
        event: KeyEvent,
        role: TvKeyRole?,
        action: TvAction?,
        ctx: TvScreenContext,
        handled: Boolean,
    ) {
        // Step 1: Log via GlobalDebug (respects GlobalDebug.isEnabled())
        GlobalDebug.logDpad(
            action = "TvInput",
            extras = mapOf(
                "keyCode" to KeyEvent.keyCodeToString(event.keyCode),
                "role" to (role?.name ?: "null"),
                "action" to (action?.name ?: "null"),
                "screen" to ctx.screenId.name,
                "handled" to handled.toString(),
            ),
        )

        // Step 2: Log via DiagnosticsLogger for structured logging
        DiagnosticsLogger.ComposeTV.logKeyEvent(
            screen = ctx.screenId.name,
            keyCode = KeyEvent.keyCodeToString(event.keyCode),
            action = action?.name ?: "none",
        )

        // Step 3: Capture event for inspector overlay (if enabled via GlobalDebug)
        if (GlobalDebug.isTvInputInspectorEnabled()) {
            val currentZone = FocusKit.getCurrentZone()
            val snapshot = TvInputEventSnapshot(
                timestamp = System.currentTimeMillis(),
                keyCodeName = KeyEvent.keyCodeToString(event.keyCode),
                actionType = when (event.action) {
                    KeyEvent.ACTION_DOWN -> "DOWN"
                    KeyEvent.ACTION_UP -> "UP"
                    else -> "OTHER"
                },
                role = role,
                action = action,
                screenId = ctx.screenId,
                focusZone = currentZone,
                handled = handled,
            )

            // Update history using efficient ArrayDeque
            synchronized(historyBuffer) {
                if (historyBuffer.size >= MAX_HISTORY_SIZE) {
                    historyBuffer.removeFirst()
                }
                historyBuffer.addLast(snapshot)
                _history.value = historyBuffer.toList()
            }

            // Emit live event
            _events.tryEmit(snapshot)
        }
    }

    /**
     * Clear the event history.
     * Called when inspector is closed or reset.
     */
    fun clearHistory() {
        synchronized(historyBuffer) {
            historyBuffer.clear()
            _history.value = emptyList()
        }
    }
}
