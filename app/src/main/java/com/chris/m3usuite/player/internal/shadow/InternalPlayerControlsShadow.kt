package com.chris.m3usuite.player.internal.shadow

import com.chris.m3usuite.player.internal.state.AspectRatioMode
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState

/**
 * Shadow-mode wrapper for modular player controls.
 * Never interacts with UI or runtime player.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 STEP 4: SPEC-DRIVEN CONTROLS SHADOW MODE (DIAGNOSTICS ONLY)
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This shadow wrapper mirrors the behaviors of [InternalPlayerControls] for diagnostic purposes.
 * It evaluates control state and emits diagnostic strings without affecting runtime behavior.
 *
 * **KEY PRINCIPLE (Step 4):**
 * Diagnostics are now spec-oriented. The shadow never assumes the legacy screen is correct.
 * All behavior is validated against the Behavior Contract where applicable.
 *
 * **SHADOWED CONTROLS BEHAVIORS:**
 * - Gesture logic (play/pause, seek)
 * - Trickplay (fast-forward, rewind speeds) - with spec validation
 * - UI visibility (controls visible/hidden)
 * - Seek preview (scrubbing position)
 * - CC/subtitle triggers (menu toggles)
 * - Menu toggles (settings, tracks, speed dialogs)
 * - Aspect ratio mode changes - with spec-preferred scenarios
 * - Future DPAD mappings (Phase 6)
 *
 * **SPEC-ORIENTED DIAGNOSTICS:**
 * - "trickplay start violates spec (speed < 0 not allowed)"
 * - "aspect ratio FIT is spec-preferred in this scenario"
 * - "subtitle menu open matches spec"
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
     * **SPEC-ORIENTED DIAGNOSTIC EXAMPLES (Phase 3 Step 4):**
     * - "trickplay start violates spec (speed < 0 not allowed)"
     * - "aspect ratio FIT is spec-preferred for VOD content"
     * - "subtitle/tracks menu: opened (matches spec)"
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
            // Trickplay / Speed Diagnostics (Spec-Validated)
            // ────────────────────────────────────────────────────────────────
            evaluateTrickplay(state, onControlsDiagnostic)

            // ────────────────────────────────────────────────────────────────
            // Aspect Ratio Diagnostics (Spec-Oriented)
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
            // Menu / Dialog Diagnostics (Spec-Oriented)
            // ────────────────────────────────────────────────────────────────
            evaluateMenus(state, onControlsDiagnostic)

            // ────────────────────────────────────────────────────────────────
            // Kids Gate Diagnostics (Spec-Validated)
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
     * Evaluate trickplay / playback speed state with spec validation.
     *
     * Trickplay is considered "active" when:
     * - Speed is not 1.0f (normal speed)
     * - Speed is greater than 0 (valid speed)
     *
     * **SPEC RULES:**
     * - Speed must be > 0 (negative/zero violates spec)
     * - Speed < 1.0 is allowed for slow-motion
     * - Speed > 1.0 is allowed for fast-forward
     *
     * Edge cases handled with spec validation:
     * - Zero speed (invalid): Violates spec
     * - Negative speed (invalid): Violates spec
     * - Speed = 1.0f: Normal playback, matches spec
     */
    private fun evaluateTrickplay(
        state: InternalPlayerUiState,
        callback: (String) -> Unit,
    ) {
        val speed = state.playbackSpeed

        // Guard: Invalid speeds (zero, negative) - SPEC VIOLATION
        if (speed <= 0f) {
            callback("trickplay: invalid speed ($speed)")
            callback("trickplay violates spec (speed <= 0 not allowed)")
            return
        }

        // Normal speed - matches spec
        if (speed == 1f) {
            callback("trickplay: inactive (normal speed)")
            callback("trickplay matches spec (normal playback)")
            return
        }

        // Format speed with sign for clarity
        val speedFormatted = if (speed > 1f) "+${speed}x" else "${speed}x"
        callback("trickplay active: speed=$speedFormatted")

        // Spec validation for active trickplay
        if (speed < 1f) {
            callback("slow-motion playback is spec-compliant")
        } else {
            callback("fast-forward playback is spec-compliant")
        }
    }

    /**
     * Evaluate aspect ratio mode with spec-orientation.
     *
     * **SPEC GUIDANCE:**
     * - FIT is typically preferred for most content
     * - FILL may crop content but fills screen
     * - ZOOM expands content
     * - STRETCH may distort aspect ratio
     */
    private fun evaluateAspectRatio(
        state: InternalPlayerUiState,
        callback: (String) -> Unit,
    ) {
        val mode = state.aspectRatioMode
        callback("aspectRatioMode: ${mode.name}")

        // Spec-oriented diagnostic
        when (mode) {
            AspectRatioMode.FIT -> callback("aspect ratio FIT is spec-preferred for most scenarios")
            AspectRatioMode.FILL -> callback("aspect ratio FILL may crop content (user preference)")
            AspectRatioMode.ZOOM -> callback("aspect ratio ZOOM expands content (user preference)")
            AspectRatioMode.STRETCH -> callback("aspect ratio STRETCH may distort (user preference)")
        }
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

            // Spec validation: Resume should be > 10s (Section 3.3)
            if (resumeMs > 0 && resumeMs <= 10_000L) {
                callback("resume position violates spec (should be >10s)")
            } else if (resumeMs > 10_000L) {
                callback("resume position is spec-compliant (>10s)")
            }
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
     * Evaluate menu/dialog visibility with spec-orientation.
     *
     * Handles subtitle/track menu, speed dialog, settings dialog, sleep timer dialog.
     */
    private fun evaluateMenus(
        state: InternalPlayerUiState,
        callback: (String) -> Unit,
    ) {
        if (state.showTracksDialog) {
            callback("subtitle/tracks menu: opened")
            callback("subtitle menu open matches spec")
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
     * Evaluate kids gate state with spec validation.
     *
     * **SPEC RULES (Section 4):**
     * - Block when quota <= 0
     * - 60-second accumulation for quota decrement
     * - Fail-open on errors
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

            // Spec validation: Block when quota <= 0 (Section 4.3)
            if (remaining != null && remaining <= 0 && !blocked) {
                callback("kids gate violates spec (should be blocked when quota <= 0)")
            } else if (remaining != null && remaining <= 0 && blocked) {
                callback("kids gate matches spec (blocked due to exhausted quota)")
            } else if (remaining != null && remaining > 0 && blocked) {
                callback("kids gate may violate spec (blocked but quota > 0)")
            } else if (remaining != null && remaining > 0 && !blocked) {
                callback("kids gate matches spec (not blocked, quota available)")
            }
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
