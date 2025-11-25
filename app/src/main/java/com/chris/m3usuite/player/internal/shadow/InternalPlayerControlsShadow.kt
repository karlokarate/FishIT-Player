package com.chris.m3usuite.player.internal.shadow

import com.chris.m3usuite.player.internal.state.AspectRatioMode
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState

/**
 * Shadow-mode wrapper for modular player controls.
 * Never interacts with UI or runtime player.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 STATUS: CONTROLS SHADOW MODE (DIAGNOSTICS ONLY)
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This shadow wrapper mirrors the behaviors of [InternalPlayerControls] for diagnostic purposes.
 * It evaluates control state and emits diagnostic strings without affecting runtime behavior.
 *
 * **SHADOWED CONTROLS BEHAVIORS:**
 * - Gesture logic (play/pause, seek)
 * - Trickplay (fast-forward, rewind speeds)
 * - UI visibility (controls visible/hidden)
 * - Seek preview (scrubbing position)
 * - CC/subtitle triggers (menu toggles)
 * - Menu toggles (settings, tracks, speed dialogs)
 * - Aspect ratio mode changes
 * - Future DPAD mappings (Phase 6)
 *
 * **SAFETY GUARANTEES:**
 * - Never modifies InternalPlayerUiState
 * - Never interacts with any UI component
 * - Never affects ExoPlayer or legacy player
 * - Safe to call with null/empty/invalid state fields
 * - Never throws exceptions
 *
 * **USAGE:**
 * This object is called from [InternalPlayerShadow] after shadow state updates
 * to evaluate what control actions would have been triggered.
 */
object InternalPlayerControlsShadow {

    /**
     * Evaluate controls state and emit diagnostic strings.
     *
     * This function inspects the provided [InternalPlayerUiState] and determines
     * what control events would have been triggered. Diagnostics are emitted via
     * the optional callback without modifying state or affecting runtime.
     *
     * **DIAGNOSTIC EXAMPLES:**
     * - "controlsVisible toggled via state change"
     * - "seekPreview requested at 12000ms"
     * - "aspectRatioMode changed to FIT"
     * - "trickplay started: speed=+16x"
     * - "subtitle menu opened"
     * - "playback state: playing=true, buffering=false"
     *
     * @param state The current shadow UI state to evaluate
     * @param onControlsDiagnostic Optional callback to receive diagnostic strings
     */
    fun evaluateControls(
        state: InternalPlayerUiState,
        onControlsDiagnostic: ((String) -> Unit)? = null,
    ) {
        // Guard: Early return if no callback provided
        if (onControlsDiagnostic == null) return

        try {
            // Emit initial evaluation marker
            onControlsDiagnostic("shadow-controls-evaluated")

            // ────────────────────────────────────────────────────────────────
            // Playback State Diagnostics
            // ────────────────────────────────────────────────────────────────
            evaluatePlaybackState(state, onControlsDiagnostic)

            // ────────────────────────────────────────────────────────────────
            // UI Visibility Diagnostics
            // ────────────────────────────────────────────────────────────────
            evaluateUiVisibility(state, onControlsDiagnostic)

            // ────────────────────────────────────────────────────────────────
            // Trickplay / Speed Diagnostics
            // ────────────────────────────────────────────────────────────────
            evaluateTrickplay(state, onControlsDiagnostic)

            // ────────────────────────────────────────────────────────────────
            // Aspect Ratio Diagnostics
            // ────────────────────────────────────────────────────────────────
            evaluateAspectRatio(state, onControlsDiagnostic)

            // ────────────────────────────────────────────────────────────────
            // Seek / Position Diagnostics
            // ────────────────────────────────────────────────────────────────
            evaluateSeekState(state, onControlsDiagnostic)

            // ────────────────────────────────────────────────────────────────
            // Loop Mode Diagnostics
            // ────────────────────────────────────────────────────────────────
            evaluateLoopMode(state, onControlsDiagnostic)

            // ────────────────────────────────────────────────────────────────
            // Menu / Dialog Diagnostics
            // ────────────────────────────────────────────────────────────────
            evaluateMenus(state, onControlsDiagnostic)

            // ────────────────────────────────────────────────────────────────
            // Kids Gate Diagnostics
            // ────────────────────────────────────────────────────────────────
            evaluateKidsGate(state, onControlsDiagnostic)

            // ────────────────────────────────────────────────────────────────
            // Error State Diagnostics
            // ────────────────────────────────────────────────────────────────
            evaluateError(state, onControlsDiagnostic)
        } catch (_: Throwable) {
            // Fail-safe: Never throw, silently absorb any unexpected errors
            onControlsDiagnostic("shadow-controls-error: evaluation failed safely")
        }
    }

    /**
     * Evaluate playback state (playing, buffering).
     */
    private fun evaluatePlaybackState(
        state: InternalPlayerUiState,
        callback: (String) -> Unit,
    ) {
        val playbackDiag = buildString {
            append("playback state: playing=")
            append(state.isPlaying)
            append(", buffering=")
            append(state.isBuffering)
        }
        callback(playbackDiag)
    }

    /**
     * Evaluate UI visibility state.
     */
    private fun evaluateUiVisibility(
        state: InternalPlayerUiState,
        callback: (String) -> Unit,
    ) {
        if (state.showDebugInfo) {
            callback("debug overlay: visible")
        }
    }

    /**
     * Evaluate trickplay / playback speed state.
     *
     * Trickplay is considered "active" when:
     * - Speed is not 1.0f (normal speed)
     * - Speed is greater than 0 (valid speed)
     *
     * Edge cases handled:
     * - Zero speed (invalid): Treated as paused/invalid, no trickplay diagnostic
     * - Negative speed (invalid): Treated as invalid, no trickplay diagnostic
     * - Speed = 1.0f: Normal playback, no trickplay
     */
    private fun evaluateTrickplay(
        state: InternalPlayerUiState,
        callback: (String) -> Unit,
    ) {
        val speed = state.playbackSpeed

        // Guard: Invalid speeds (zero, negative)
        if (speed <= 0f) {
            callback("trickplay: invalid speed ($speed)")
            return
        }

        // Normal speed - no trickplay
        if (speed == 1f) {
            callback("trickplay: inactive (normal speed)")
            return
        }

        // Format speed with sign for clarity
        val speedFormatted = if (speed > 1f) "+${speed}x" else "${speed}x"
        callback("trickplay active: speed=$speedFormatted")
    }

    /**
     * Evaluate aspect ratio mode.
     */
    private fun evaluateAspectRatio(
        state: InternalPlayerUiState,
        callback: (String) -> Unit,
    ) {
        val mode = state.aspectRatioMode
        callback("aspectRatioMode: ${mode.name}")
    }

    /**
     * Evaluate seek/position state.
     */
    private fun evaluateSeekState(
        state: InternalPlayerUiState,
        callback: (String) -> Unit,
    ) {
        val pos = state.positionMs
        val dur = state.durationMs

        // Position diagnostic
        callback("position: ${pos}ms / ${dur}ms")

        // Resume state diagnostic
        if (state.isResumingFromLegacy) {
            val resumeMs = state.resumeStartMs ?: 0L
            callback("resuming from: ${resumeMs}ms")
        }

        // Seek preview (if currentPositionMs differs from positionMs, could indicate scrubbing)
        val comparisonPos = state.currentPositionMs
        if (comparisonPos != null && comparisonPos != pos) {
            callback("seekPreview requested at ${comparisonPos}ms")
        }
    }

    /**
     * Evaluate loop mode.
     */
    private fun evaluateLoopMode(
        state: InternalPlayerUiState,
        callback: (String) -> Unit,
    ) {
        if (state.isLooping) {
            callback("loop mode: enabled")
        }
    }

    /**
     * Evaluate menu/dialog visibility.
     *
     * Handles subtitle/track menu, speed dialog, settings dialog, sleep timer dialog.
     */
    private fun evaluateMenus(
        state: InternalPlayerUiState,
        callback: (String) -> Unit,
    ) {
        if (state.showTracksDialog) {
            callback("subtitle/tracks menu: opened")
        }
        if (state.showSpeedDialog) {
            callback("speed dialog: opened")
        }
        if (state.showSettingsDialog) {
            callback("settings dialog: opened")
        }
        if (state.showSleepTimerDialog) {
            callback("sleep timer dialog: opened")
        }

        // Sleep timer active diagnostic
        val sleepRemaining = state.sleepTimerRemainingMs
        if (sleepRemaining != null && sleepRemaining > 0) {
            callback("sleep timer active: ${sleepRemaining}ms remaining")
        }
    }

    /**
     * Evaluate kids gate state.
     */
    private fun evaluateKidsGate(
        state: InternalPlayerUiState,
        callback: (String) -> Unit,
    ) {
        if (state.kidActive) {
            val profileId = state.kidProfileId
            val blocked = state.kidBlocked
            val remaining = state.remainingKidsMinutes
            callback(
                buildString {
                    append("kids gate: active, profileId=")
                    append(profileId ?: "null")
                    append(", blocked=")
                    append(blocked)
                    if (remaining != null) {
                        append(", remaining=")
                        append(remaining)
                        append("min")
                    }
                },
            )
        }
    }

    /**
     * Evaluate error state.
     */
    private fun evaluateError(
        state: InternalPlayerUiState,
        callback: (String) -> Unit,
    ) {
        val error = state.playbackError
        if (error != null) {
            callback("playback error: ${error.message ?: "unknown"}")
        }
    }
}
