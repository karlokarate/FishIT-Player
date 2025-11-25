package com.chris.m3usuite.player.internal.bridge

import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.shadow.BehaviorContractEnforcer
import com.chris.m3usuite.player.internal.shadow.InternalPlayerControlsShadow
import com.chris.m3usuite.player.internal.shadow.ShadowComparisonService
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState

/**
 * Phase 3 Shadow Mode:
 * Runs the modular InternalPlayerSession in parallel without controlling playback.
 * Output is used purely for future verification and diagnostics.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 STEP 4: SPEC-DRIVEN SHADOW-MODE ENTRY POINT (NON-RUNTIME)
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This entry point allows shadow-mode invocation of the modular SIP session from
 * Phase 3+ verification tasks. It does NOT control playback and does NOT interact
 * with the real player or modify runtime behavior.
 *
 * **KEY PRINCIPLE (Step 4):**
 * Shadow diagnostics now operate strictly via the Behavior Contract.
 * Legacy behavior is NOT the source of truth. The Behavior Contract
 * (docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md) defines correctness.
 *
 * **CURRENT STATUS:**
 * - NOT called by runtime code paths.
 * - Runtime flow remains: InternalPlayerEntry → legacy InternalPlayerScreen
 * - Future Phase 3–4 verification workflows may invoke this entry point to:
 *   1. Observe parallel modular session behavior
 *   2. Compare modular vs legacy vs SPEC state emissions
 *   3. Debug diagnostics without affecting playback
 *
 * **SHADOW MODE PRINCIPLES:**
 * - Never controls real ExoPlayer instance
 * - Never modifies legacy screen state
 * - Never interrupts or affects user playback experience
 * - State is captured for diagnostics only
 *
 * **INTEGRATION READINESS:**
 * When Phase 3 verification is complete, this bridge can be evolved to:
 * - Toggle between shadow-mode and runtime-mode
 * - Provide A/B comparison of modular vs legacy behavior
 * - Eventually replace legacy InternalPlayerScreen entirely (Phase 4+)
 *
 * **PHASE 3 STEP 3: CONTROLS SHADOW MODE**
 * Shadow pipeline now evaluates Session + Controls without affecting runtime:
 * - InternalPlayerControlsShadow evaluates control state diagnostically
 * - Diagnostics callback for control events via onShadowControlsDiagnostic
 * - No player control event influences the legacy player
 * - No UI calls are made
 * - No gestures, trickplay, seek, or visibility toggles propagate to runtime
 *
 * **PHASE 3 STEP 4: SPEC-DRIVEN DIAGNOSTICS**
 * Shadow pipeline now evaluates SIP and Legacy behavior strictly against the Behavior Contract:
 * - SIP is allowed to intentionally improve on legacy when the spec says so
 * - Legacy no longer defines correctness; the Behavior Contract does
 * - New SpecComparisonResult opens the door for future CI validation
 * - Every diagnostic output is classified using ParityKind
 *
 * **PHASE 3B: BEHAVIOR CONTRACT ENFORCEMENT**
 * Shadow pipeline now includes enforcement layer that:
 * - Evaluates SIP state against Behavior Contract
 * - Produces corrective EnforcementActions for violations
 * - Invokes onEnforcementActions callback in shadow-only mode
 * - Does NOT modify actual playback behavior
 */
object InternalPlayerShadow {

    /**
     * Start a shadow-mode session that runs in parallel without controlling playback.
     *
     * This method instantiates the modular InternalPlayerSession in a passive observation
     * mode. The session will:
     * - Process the same inputs as the legacy player
     * - Produce state updates via [onShadowStateChanged] callback
     * - Never interact with real ExoPlayer or UI
     *
     * **SHADOW MODE CONSTRAINTS:**
     * - Must NOT wire to actual ExoPlayer instance
     * - Must NOT navigate or modify UI state
     * - Must NOT call ObjectBox directly (uses abstracted repositories)
     * - Must NOT throw exceptions to caller (fail-safe)
     *
     * **PHASE 3 STEP 4 SPEC-DRIVEN USAGE:**
     * ```kotlin
     * // Example spec-driven verification workflow
     * InternalPlayerShadow.startShadowSession(
     *     url = currentUrl,
     *     startMs = providedStartMs,
     *     mimeType = providedMimeType,
     *     mediaItem = preparedMediaItem,
     *     playbackContext = playbackContext,
     *     onShadowStateChanged = { shadowState ->
     *         // Compare shadowState with legacy state
     *         diagnosticsLogger.logShadowComparison(legacyState, shadowState)
     *     },
     *     onShadowComparison = { comparisonResult ->
     *         // Log parity comparison results (legacy format)
     *         if (!comparisonResult.resumeParityOk) {
     *             diagnosticsLogger.logMismatch("resume", comparisonResult)
     *         }
     *     },
     *     onSpecComparison = { specResult ->
     *         // Log spec-driven comparison results (new format)
     *         when (specResult.parityKind) {
     *             ParityKind.SpecPreferredSIP -> log("SIP correct, legacy bug: ${specResult.dimension}")
     *             ParityKind.SpecPreferredLegacy -> log("SIP violation: ${specResult.dimension}")
     *             else -> {}
     *         }
     *     },
     *     onShadowControlsDiagnostic = { diagnostic ->
     *         // Log control diagnostics
     *         diagnosticsLogger.logControlEvent(diagnostic)
     *     },
     *     onEnforcementActions = { actions ->
     *         // Log enforcement actions (Phase 3B)
     *         actions.forEach { action ->
     *             log("Enforcement: ${action.dimension} -> ${action.action.describe()}")
     *         }
     *     }
     * )
     * ```
     *
     * @param url The playback URL (same as passed to legacy player)
     * @param startMs Explicit start position in milliseconds, or null for resume behavior
     * @param mimeType Explicit MIME type, or null for auto-detection
     * @param mediaItem Prepared MediaItem with metadata, or null
     * @param playbackContext Domain context with type, IDs, and hints
     * @param onShadowStateChanged Callback for shadow state updates (diagnostics only)
     * @param onShadowComparison Callback for legacy↔shadow state comparison results (legacy format, diagnostics only)
     * @param onSpecComparison Callback for spec-driven comparison results (Phase 3 Step 4)
     * @param onShadowControlsDiagnostic Callback for controls diagnostic strings (diagnostics only)
     * @param onEnforcementActions Callback for enforcement actions (Phase 3B, shadow-only mode)
     */
    @Suppress("UNUSED_PARAMETER")
    fun startShadowSession(
        url: String,
        startMs: Long?,
        mimeType: String?,
        mediaItem: MediaItem?,
        playbackContext: PlaybackContext,
        onShadowStateChanged: ((ShadowSessionState) -> Unit)? = null,
        onShadowComparison: ((ShadowComparisonService.ComparisonResult) -> Unit)? = null,
        onSpecComparison: ((ShadowComparisonService.SpecComparisonResult) -> Unit)? = null,
        onShadowControlsDiagnostic: ((String) -> Unit)? = null,
        onEnforcementActions: ((List<BehaviorContractEnforcer.EnforcementResult>) -> Unit)? = null,
    ) {
        // TODO(Phase 3): Instantiate modular InternalPlayerSession without wiring to UI or ExoPlayer.
        // This session must not interact with the real player or modify runtime behavior.
        //
        // Implementation steps for future Phase 3 work:
        // 1. Create a "headless" version of rememberInternalPlayerSession that doesn't
        //    require @Composable context or real ExoPlayer
        // 2. Wire ResumeManager and KidsPlaybackGate in passive mode
        // 3. Emit state updates through onShadowStateChanged callback
        // 4. Inside shadow state update, call comparison:
        //    onShadowComparison?.invoke(
        //        ShadowComparisonService.compare(legacyStateSnapshot, shadowState)
        //    )
        // 5. Log all state transitions for comparison with legacy behavior
        // 6. Call enforcement evaluation (Phase 3B):
        //    val specResults = ShadowComparisonService.compareAgainstSpec(legacy, sip, type, duration)
        //    val enforcements = BehaviorContractEnforcer.evaluate(specResults, sipState, type, duration)
        //    onEnforcementActions?.invoke(BehaviorContractEnforcer.filterActionableEnforcements(enforcements))
        //
        // For now, this is a no-op placeholder that defines the shadow-mode contract.
    }

    /**
     * Invoke comparison between legacy and shadow states.
     *
     * This utility function should be called from within shadow state update logic
     * to perform parity comparison and invoke the callback.
     *
     * **USAGE:**
     * ```kotlin
     * onShadowStateUpdate { shadowState ->
     *     InternalPlayerShadow.invokeComparison(
     *         legacyState = legacyStateSnapshot,
     *         shadowState = shadowState,
     *         callback = onShadowComparison
     *     )
     * }
     * ```
     *
     * **SAFETY GUARANTEES:**
     * - Safe to call with null callback (no-op)
     * - Never throws exceptions
     * - Never affects runtime behavior
     *
     * @param legacyState The current state from legacy InternalPlayerScreen
     * @param shadowState The current state from modular InternalPlayerSession
     * @param callback Optional callback to receive comparison results
     */
    fun invokeComparison(
        legacyState: InternalPlayerUiState,
        shadowState: InternalPlayerUiState,
        callback: ((ShadowComparisonService.ComparisonResult) -> Unit)?,
    ) {
        callback?.invoke(
            ShadowComparisonService.compare(legacyState, shadowState),
        )
    }

    /**
     * Invoke spec-driven comparison between legacy, SIP, and the Behavior Contract.
     *
     * This utility function performs three-way comparison according to Phase 3 Step 4:
     * 1. SIP state vs Spec
     * 2. Legacy state vs Spec
     * 3. SIP vs Legacy (for context only)
     *
     * **USAGE:**
     * ```kotlin
     * onShadowStateUpdate { shadowState ->
     *     InternalPlayerShadow.invokeSpecComparison(
     *         legacyState = legacyStateSnapshot,
     *         sipState = shadowState,
     *         playbackContext = playbackContext,
     *         durationMs = knownDuration,
     *         callback = onSpecComparison
     *     )
     * }
     * ```
     *
     * **KEY PRINCIPLE:**
     * Legacy behavior is NOT the source of truth. The Behavior Contract
     * (docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md) defines correctness.
     *
     * **SAFETY GUARANTEES:**
     * - Safe to call with null callback (no-op)
     * - Never throws exceptions
     * - Never affects runtime behavior
     *
     * @param legacyState The current state from legacy InternalPlayerScreen
     * @param sipState The current state from modular InternalPlayerSession
     * @param playbackContext The playback context with type information
     * @param durationMs Optional duration for near-end calculations
     * @param callback Optional callback to receive each spec comparison result
     */
    fun invokeSpecComparison(
        legacyState: InternalPlayerUiState,
        sipState: InternalPlayerUiState,
        playbackContext: PlaybackContext,
        durationMs: Long? = null,
        callback: ((ShadowComparisonService.SpecComparisonResult) -> Unit)?,
    ) {
        if (callback == null) return

        try {
            val results = ShadowComparisonService.compareAgainstSpec(
                legacy = legacyState,
                sip = sipState,
                playbackType = playbackContext.type,
                durationMs = durationMs,
            )
            results.forEach { result -> callback(result) }
        } catch (_: Throwable) {
            // Fail-safe: Never throw, silently absorb errors
        }
    }

    /**
     * Invoke enforcement evaluation in shadow mode (Phase 3B).
     *
     * This utility function evaluates the SIP state against the Behavior Contract
     * and returns recommended enforcement actions.
     *
     * **USAGE:**
     * ```kotlin
     * onShadowStateUpdate { shadowState ->
     *     InternalPlayerShadow.invokeEnforcement(
     *         legacyState = legacyStateSnapshot,
     *         sipState = shadowState,
     *         playbackContext = playbackContext,
     *         durationMs = knownDuration,
     *         callback = onEnforcementActions
     *     )
     * }
     * ```
     *
     * **KEY PRINCIPLE:**
     * This method produces corrective actions but does NOT execute them.
     * The callback receives the actions for diagnostics logging only.
     * Phase 4 will wire these actions to actual playback behavior.
     *
     * **SAFETY GUARANTEES:**
     * - Safe to call with null callback (no-op)
     * - Never throws exceptions
     * - Never affects runtime behavior
     *
     * @param legacyState The current state from legacy InternalPlayerScreen
     * @param sipState The current state from modular InternalPlayerSession
     * @param playbackContext The playback context with type information
     * @param durationMs Optional duration for calculations
     * @param callback Optional callback to receive enforcement results
     */
    fun invokeEnforcement(
        legacyState: InternalPlayerUiState,
        sipState: InternalPlayerUiState,
        playbackContext: PlaybackContext,
        durationMs: Long? = null,
        callback: ((List<BehaviorContractEnforcer.EnforcementResult>) -> Unit)?,
    ) {
        if (callback == null) return

        try {
            val results = BehaviorContractEnforcer.evaluateFromStates(
                legacyState = legacyState,
                sipState = sipState,
                playbackType = playbackContext.type,
                durationMs = durationMs,
            )
            // Only invoke callback with actionable enforcements
            val actionable = BehaviorContractEnforcer.filterActionableEnforcements(results)
            if (actionable.isNotEmpty()) {
                callback(actionable)
            }
        } catch (_: Throwable) {
            // Fail-safe: Never throw, silently absorb errors
        }
    }

    /**
     * Evaluate controls state in shadow mode.
     *
     * This utility function evaluates the provided shadow state through
     * [InternalPlayerControlsShadow] and invokes the diagnostic callback.
     *
     * **USAGE:**
     * Called after shadow state updates to evaluate what control actions
     * would have been triggered without affecting runtime.
     *
     * ```kotlin
     * // Inside shadow state update handler
     * InternalPlayerShadow.evaluateControlsInShadowMode(
     *     shadowState = updatedShadowState,
     *     callback = onShadowControlsDiagnostic
     * )
     * ```
     *
     * **SAFETY GUARANTEES:**
     * - Safe to call with null callback (no-op)
     * - Never throws exceptions
     * - Never modifies state
     * - Never affects runtime behavior
     *
     * @param shadowState The current shadow UI state to evaluate
     * @param callback Optional callback to receive diagnostic strings
     */
    fun evaluateControlsInShadowMode(
        shadowState: InternalPlayerUiState,
        callback: ((String) -> Unit)?,
    ) {
        InternalPlayerControlsShadow.evaluateControls(shadowState, callback)
    }

    /**
     * Stop the current shadow session and clean up resources.
     *
     * Shadow sessions should be stopped when:
     * - Legacy playback ends
     * - User exits the player screen
     * - Diagnostics collection is complete
     *
     * **SAFETY GUARANTEES:**
     * - Safe to call multiple times
     * - Safe to call even if no session is active
     * - Never affects legacy player state
     */
    fun stopShadowSession() {
        // TODO(Phase 3): Clean up shadow session resources.
        // No-op placeholder for Phase 3 initialization.
    }
}

/**
 * Shadow session state for diagnostics and verification.
 *
 * This state mirrors [InternalPlayerUiState] but is used purely for shadow-mode
 * observation and comparison with legacy behavior.
 *
 * **DIAGNOSTIC FIELDS:**
 * - All fields from InternalPlayerUiState
 * - Additional shadow-specific metadata
 *
 * **NOT FOR RUNTIME USE:**
 * This state class is not consumed by runtime UI. It exists only for:
 * - Phase 3–4 verification workflows
 * - A/B comparison between modular and legacy behavior
 * - Debugging and diagnostics overlay
 */
data class ShadowSessionState(
    /**
     * Indicates whether the shadow session is actively observing.
     */
    val shadowActive: Boolean = false,

    /**
     * Debug string representing the current shadow state.
     * Format: "position_ms|duration_ms|is_playing|is_buffering|kid_active|kid_blocked"
     */
    val shadowStateDebug: String? = null,

    /**
     * Timestamp when this state was emitted (for diagnostics).
     */
    val timestampMs: Long = System.currentTimeMillis(),

    /**
     * Source of this state ("shadow" vs "legacy" for comparison).
     */
    val source: String = "shadow",
)
