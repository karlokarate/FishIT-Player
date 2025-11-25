package com.chris.m3usuite.player.internal.shadow

import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState

/**
 * Phase 3B: SIP Behavior Enforcement Layer.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * BEHAVIOR CONTRACT ENFORCER - SHADOW-PHASE ENFORCEMENT SIMULATION
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This component interprets ShadowComparisonService results and determines corrective actions
 * that SIP should take to comply with the Behavior Contract. It provides a deterministic
 * "contract guard" that prepares SIP for Phase 4 activation.
 *
 * **KEY PRINCIPLE:**
 * This enforcer does NOT modify runtime behavior. It produces enforcement actions that
 * are logged for diagnostics only. Phase 4 will wire these actions to actual playback.
 *
 * **ENFORCEMENT LOGIC:**
 * - If SIP violates spec → produce corrective actions
 * - If SIP matches spec → NoAction
 * - If SIP improves on legacy but still violates spec → correction required
 * - If SIP matches spec and legacy violates → NoAction (SIP is preferred)
 * - DontCare → NoAction
 *
 * **INPUTS:**
 * - SpecComparisonResult (per dimension)
 * - Current SIP session state
 * - PlaybackType (VOD/SERIES/LIVE)
 * - durationMs (for position calculations)
 *
 * **OUTPUTS:**
 * - List of EnforcementActions to correct SIP behavior
 *
 * **PURE KOTLIN:**
 * - No Android dependencies
 * - Deterministic, side-effect-free logic
 * - Thread-safe (stateless)
 */
object BehaviorContractEnforcer {

    // ════════════════════════════════════════════════════════════════════════════
    // Enforcement Actions
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Actions that the enforcement layer recommends to correct SIP behavior.
     *
     * These actions are not executed automatically - they are returned for
     * diagnostics logging in shadow mode, or for actual execution in Phase 4.
     */
    sealed class EnforcementAction {
        /**
         * No enforcement needed - SIP behavior is correct.
         */
        data object NoAction : EnforcementAction()

        /**
         * Fix resume position to the specified value.
         * Used when SIP has an invalid resume position.
         *
         * @property positionMs The correct resume position in milliseconds
         */
        data class FixResumePosition(val positionMs: Long) : EnforcementAction()

        /**
         * Clear resume position entirely.
         * Used when SIP incorrectly stores a resume position
         * (e.g., for LIVE content or position <= 10s or near-end).
         */
        data object ClearResume : EnforcementAction()

        /**
         * Block kids playback.
         * Used when SIP fails to block playback when quota is exhausted.
         */
        data object BlockKidsPlayback : EnforcementAction()

        /**
         * Unblock kids playback.
         * Used when SIP incorrectly blocks playback when quota is available.
         */
        data object UnblockKidsPlayback : EnforcementAction()

        /**
         * Normalize position to bring it within acceptable drift.
         * Used when position drift exceeds the tolerance.
         *
         * @property positionMs The normalized position in milliseconds
         */
        data class NormalizePosition(val positionMs: Long) : EnforcementAction()

        /**
         * Get human-readable description of the action.
         */
        fun describe(): String = when (this) {
            is NoAction -> "No action needed"
            is FixResumePosition -> "Fix resume position to ${positionMs}ms"
            is ClearResume -> "Clear resume position"
            is BlockKidsPlayback -> "Block kids playback (quota exhausted)"
            is UnblockKidsPlayback -> "Unblock kids playback (quota available)"
            is NormalizePosition -> "Normalize position to ${positionMs}ms"
        }
    }

    /**
     * Result of enforcement evaluation for a single dimension.
     *
     * @property dimension The aspect being evaluated ("resume", "kids", "position")
     * @property action The recommended enforcement action
     * @property sipValue The current SIP value for this dimension
     * @property specValue The expected value according to the Behavior Contract
     * @property reason Human-readable explanation of why this action is needed
     */
    data class EnforcementResult(
        val dimension: String,
        val action: EnforcementAction,
        val sipValue: Any?,
        val specValue: Any?,
        val reason: String,
    )

    // ════════════════════════════════════════════════════════════════════════════
    // Main Enforcement API
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Evaluate enforcement actions based on spec comparison results.
     *
     * This method takes the results from ShadowComparisonService.compareAgainstSpec()
     * and determines what corrective actions SIP should take.
     *
     * @param specResults List of spec comparison results from ShadowComparisonService
     * @param sipState Current SIP session state
     * @param playbackType Type of playback (VOD, SERIES, LIVE)
     * @param durationMs Optional duration for position calculations
     * @return List of enforcement results with recommended actions
     */
    fun evaluate(
        specResults: List<ShadowComparisonService.SpecComparisonResult>,
        sipState: InternalPlayerUiState,
        playbackType: PlaybackType,
        durationMs: Long?,
    ): List<EnforcementResult> {
        return specResults.mapNotNull { result ->
            evaluateSingleDimension(result, sipState, playbackType, durationMs)
        }
    }

    /**
     * Convenience method that performs spec comparison and enforcement in one call.
     *
     * @param legacyState Legacy player state (for comparison context)
     * @param sipState SIP session state
     * @param playbackType Type of playback
     * @param durationMs Optional duration
     * @return List of enforcement results
     */
    fun evaluateFromStates(
        legacyState: InternalPlayerUiState,
        sipState: InternalPlayerUiState,
        playbackType: PlaybackType,
        durationMs: Long?,
    ): List<EnforcementResult> {
        val specResults = ShadowComparisonService.compareAgainstSpec(
            legacy = legacyState,
            sip = sipState,
            playbackType = playbackType,
            durationMs = durationMs,
        )
        return evaluate(specResults, sipState, playbackType, durationMs)
    }

    /**
     * Check if any enforcement action is needed.
     *
     * @param specResults Spec comparison results
     * @param sipState SIP state
     * @param playbackType Playback type
     * @param durationMs Duration
     * @return True if at least one action other than NoAction is needed
     */
    fun needsEnforcement(
        specResults: List<ShadowComparisonService.SpecComparisonResult>,
        sipState: InternalPlayerUiState,
        playbackType: PlaybackType,
        durationMs: Long?,
    ): Boolean {
        return evaluate(specResults, sipState, playbackType, durationMs)
            .any { it.action !is EnforcementAction.NoAction }
    }

    /**
     * Get only the actions that require enforcement (filter out NoAction).
     *
     * @param results Enforcement results
     * @return List of results with actual enforcement actions
     */
    fun filterActionableEnforcements(
        results: List<EnforcementResult>,
    ): List<EnforcementResult> {
        return results.filter { it.action !is EnforcementAction.NoAction }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Per-Dimension Enforcement Logic
    // ════════════════════════════════════════════════════════════════════════════

    private fun evaluateSingleDimension(
        result: ShadowComparisonService.SpecComparisonResult,
        sipState: InternalPlayerUiState,
        playbackType: PlaybackType,
        durationMs: Long?,
    ): EnforcementResult? {
        return when (result.dimension) {
            "resume" -> evaluateResumeEnforcement(result, sipState, playbackType, durationMs)
            "kids" -> evaluateKidsEnforcement(result, sipState)
            "position" -> evaluatePositionEnforcement(result, sipState, durationMs)
            else -> null // Unknown dimension - skip
        }
    }

    /**
     * Evaluate enforcement for resume dimension.
     *
     * Spec rules:
     * - LIVE content never resumes
     * - Resume only if position > 10 seconds
     * - Clear resume when remaining < 10 seconds
     */
    private fun evaluateResumeEnforcement(
        result: ShadowComparisonService.SpecComparisonResult,
        sipState: InternalPlayerUiState,
        playbackType: PlaybackType,
        durationMs: Long?,
    ): EnforcementResult {
        val sipResume = sipState.resumeStartMs

        return when (result.parityKind) {
            // SIP matches spec - no action needed
            ShadowComparisonService.ParityKind.ExactMatch,
            ShadowComparisonService.ParityKind.SpecPreferredSIP,
            ShadowComparisonService.ParityKind.DontCare -> EnforcementResult(
                dimension = "resume",
                action = EnforcementAction.NoAction,
                sipValue = sipResume,
                specValue = result.sipValue,
                reason = "SIP complies with spec or is preferred",
            )

            // SIP violates spec - need correction
            ShadowComparisonService.ParityKind.SpecPreferredLegacy,
            ShadowComparisonService.ParityKind.BothViolateSpec -> {
                determineResumeCorrection(sipResume, playbackType, durationMs, result)
            }
        }
    }

    private fun determineResumeCorrection(
        sipResume: Long?,
        playbackType: PlaybackType,
        durationMs: Long?,
        result: ShadowComparisonService.SpecComparisonResult,
    ): EnforcementResult {
        // LIVE content should never have resume
        if (playbackType == PlaybackType.LIVE && sipResume != null) {
            return EnforcementResult(
                dimension = "resume",
                action = EnforcementAction.ClearResume,
                sipValue = sipResume,
                specValue = null,
                reason = "LIVE content never resumes (Section 3.1)",
            )
        }

        // Resume position <= 10s should be cleared
        if (sipResume != null && sipResume <= ShadowComparisonService.RESUME_MIN_THRESHOLD_MS) {
            return EnforcementResult(
                dimension = "resume",
                action = EnforcementAction.ClearResume,
                sipValue = sipResume,
                specValue = null,
                reason = "Resume position <= 10s should be cleared (Section 3.3)",
            )
        }

        // Near-end position should be cleared
        if (sipResume != null && durationMs != null) {
            val remaining = durationMs - sipResume
            if (remaining <= ShadowComparisonService.NEAR_END_THRESHOLD_MS) {
                return EnforcementResult(
                    dimension = "resume",
                    action = EnforcementAction.ClearResume,
                    sipValue = sipResume,
                    specValue = null,
                    reason = "Near-end position (remaining ${remaining}ms < 10s) should be cleared (Section 3.4)",
                )
            }
        }

        // Generic violation - use the legacy value as correction if available
        val legacyValue = result.legacyValue as? Long
        return if (legacyValue != null && legacyValue > ShadowComparisonService.RESUME_MIN_THRESHOLD_MS) {
            EnforcementResult(
                dimension = "resume",
                action = EnforcementAction.FixResumePosition(legacyValue),
                sipValue = sipResume,
                specValue = legacyValue,
                reason = "Fix resume to spec-compliant position",
            )
        } else {
            EnforcementResult(
                dimension = "resume",
                action = EnforcementAction.ClearResume,
                sipValue = sipResume,
                specValue = null,
                reason = "Clear invalid resume position",
            )
        }
    }

    /**
     * Evaluate enforcement for kids gate dimension.
     *
     * Spec rules:
     * - Block when quota <= 0
     * - Unblock when quota > 0
     * - Fail-open on errors
     */
    private fun evaluateKidsEnforcement(
        result: ShadowComparisonService.SpecComparisonResult,
        sipState: InternalPlayerUiState,
    ): EnforcementResult {
        val sipBlocked = sipState.kidBlocked
        val sipRemaining = sipState.remainingKidsMinutes
        val sipActive = sipState.kidActive

        return when (result.parityKind) {
            // SIP matches spec - no action needed
            ShadowComparisonService.ParityKind.ExactMatch,
            ShadowComparisonService.ParityKind.SpecPreferredSIP,
            ShadowComparisonService.ParityKind.DontCare -> EnforcementResult(
                dimension = "kids",
                action = EnforcementAction.NoAction,
                sipValue = mapOf("blocked" to sipBlocked, "remaining" to sipRemaining),
                specValue = result.sipValue,
                reason = "SIP complies with spec or is preferred",
            )

            // SIP violates spec - need correction
            ShadowComparisonService.ParityKind.SpecPreferredLegacy,
            ShadowComparisonService.ParityKind.BothViolateSpec -> {
                determineKidsCorrection(sipActive, sipBlocked, sipRemaining, result)
            }
        }
    }

    private fun determineKidsCorrection(
        sipActive: Boolean,
        sipBlocked: Boolean,
        sipRemaining: Int?,
        result: ShadowComparisonService.SpecComparisonResult,
    ): EnforcementResult {
        // If not a kid profile, no enforcement needed
        if (!sipActive) {
            return EnforcementResult(
                dimension = "kids",
                action = EnforcementAction.NoAction,
                sipValue = mapOf("blocked" to sipBlocked, "active" to sipActive),
                specValue = null,
                reason = "Not a kid profile - no enforcement needed",
            )
        }

        // Spec: Block when quota <= 0
        if (sipRemaining != null && sipRemaining <= 0 && !sipBlocked) {
            return EnforcementResult(
                dimension = "kids",
                action = EnforcementAction.BlockKidsPlayback,
                sipValue = mapOf("blocked" to sipBlocked, "remaining" to sipRemaining),
                specValue = mapOf("blocked" to true, "remaining" to sipRemaining),
                reason = "Quota exhausted (${sipRemaining}min) - block playback (Section 4.3)",
            )
        }

        // Spec: Unblock when quota > 0
        if (sipRemaining != null && sipRemaining > 0 && sipBlocked) {
            return EnforcementResult(
                dimension = "kids",
                action = EnforcementAction.UnblockKidsPlayback,
                sipValue = mapOf("blocked" to sipBlocked, "remaining" to sipRemaining),
                specValue = mapOf("blocked" to false, "remaining" to sipRemaining),
                reason = "Quota available (${sipRemaining}min) - unblock playback (Section 4.3)",
            )
        }

        // Cannot determine - fall back to no action (fail-open)
        return EnforcementResult(
            dimension = "kids",
            action = EnforcementAction.NoAction,
            sipValue = mapOf("blocked" to sipBlocked, "remaining" to sipRemaining),
            specValue = null,
            reason = "Cannot determine correct state - fail-open (Section 4.5)",
        )
    }

    /**
     * Evaluate enforcement for position dimension.
     *
     * Position drift is usually allowed within tolerance.
     * Large drift may indicate a sync issue that needs investigation.
     */
    private fun evaluatePositionEnforcement(
        result: ShadowComparisonService.SpecComparisonResult,
        sipState: InternalPlayerUiState,
        durationMs: Long?,
    ): EnforcementResult {
        val sipPosition = sipState.currentPositionMs

        // Position is typically DontCare or ExactMatch with flag
        // We only enforce if there's a significant drift that needs correction
        return when (result.parityKind) {
            ShadowComparisonService.ParityKind.DontCare -> EnforcementResult(
                dimension = "position",
                action = EnforcementAction.NoAction,
                sipValue = sipPosition,
                specValue = result.sipValue,
                reason = "Position within tolerance",
            )

            ShadowComparisonService.ParityKind.ExactMatch -> {
                // Check if flagged for drift
                val hasDriftFlag = result.flags.any { it.contains("positionDrift") }
                if (hasDriftFlag) {
                    val legacyPos = result.legacyValue as? Long
                    if (legacyPos != null && sipPosition != null) {
                        EnforcementResult(
                            dimension = "position",
                            action = EnforcementAction.NormalizePosition(legacyPos),
                            sipValue = sipPosition,
                            specValue = legacyPos,
                            reason = "Large position drift detected - consider normalization",
                        )
                    } else {
                        EnforcementResult(
                            dimension = "position",
                            action = EnforcementAction.NoAction,
                            sipValue = sipPosition,
                            specValue = null,
                            reason = "Position drift flagged but cannot normalize",
                        )
                    }
                } else {
                    EnforcementResult(
                        dimension = "position",
                        action = EnforcementAction.NoAction,
                        sipValue = sipPosition,
                        specValue = result.sipValue,
                        reason = "Position matches",
                    )
                }
            }

            else -> EnforcementResult(
                dimension = "position",
                action = EnforcementAction.NoAction,
                sipValue = sipPosition,
                specValue = result.sipValue,
                reason = "Position - ${result.parityKind}",
            )
        }
    }
}
