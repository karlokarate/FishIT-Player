package com.chris.m3usuite.player.internal.shadow

import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import kotlin.math.abs

/**
 * Compares legacy state vs shadow state vs Behavior Contract for diagnostics.
 * Must never affect runtime behavior.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 STEP 4: SPEC-DRIVEN DIAGNOSTICS-ONLY PARITY COMPARISON SERVICE
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This service provides spec-driven comparison utilities for verifying compliance:
 * - SIP state vs Behavior Contract (spec)
 * - Legacy state vs Behavior Contract (spec)
 * - SIP vs Legacy (for context only)
 *
 * **KEY PRINCIPLE:**
 * Legacy behavior is NOT the source of truth. The Behavior Contract (see
 * docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md) defines correctness.
 *
 * **USAGE:**
 * - Diagnostics and verification workflows only
 * - Not wired into runtime player behavior
 * - Called from shadow session callbacks for comparison logging
 *
 * **SPEC-BASED CLASSIFICATION:**
 * - ExactMatch: SIP == Spec AND Legacy == Spec
 * - SpecPreferredSIP: SIP == Spec, Legacy violates Spec
 * - SpecPreferredLegacy: Legacy == Spec, SIP violates Spec
 * - BothViolateSpec: Both are wrong according to spec
 * - DontCare: Divergence allowed by spec
 */
object ShadowComparisonService {
    // ════════════════════════════════════════════════════════════════════════════
    // Behavior Contract Constants (from docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md)
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Resume position threshold: Only restore if position > 10 seconds.
     * From Behavior Contract Section 3.3.
     */
    const val RESUME_MIN_THRESHOLD_MS: Long = 10_000L

    /**
     * Near-end threshold: Clear resume when remaining < 10 seconds.
     * From Behavior Contract Section 3.4.
     */
    const val NEAR_END_THRESHOLD_MS: Long = 10_000L

    /**
     * Position offset tolerance: Allowed drift between SIP and Legacy.
     * This is a "don't care" tolerance for minor timing differences.
     */
    const val POSITION_TOLERANCE_MS: Long = 1_000L

    /**
     * Kids gate tick interval: 60-second accumulation for quota decrement.
     * From Behavior Contract Section 4.4.
     */
    const val KIDS_TICK_ACCUMULATION_SECS: Int = 60

    // ════════════════════════════════════════════════════════════════════════════
    // Legacy ComparisonResult (backward compatibility)
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Result of comparing legacy and shadow states (legacy format for backward compatibility).
     *
     * This result is used for diagnostics logging and verification.
     * No runtime decisions are made based on these values.
     *
     * @property resumeParityOk True if both states have the same resume start position
     * @property kidsGateParityOk True if both states have the same kids blocked status
     * @property positionOffsetMs Difference between legacy and shadow position tracking (null if unavailable)
     * @property flags List of diagnostic flags indicating specific mismatches
     */
    data class ComparisonResult(
        val resumeParityOk: Boolean,
        val kidsGateParityOk: Boolean,
        val positionOffsetMs: Long?,
        val flags: List<String> = emptyList(),
    )

    /**
     * Compare legacy state with shadow state for parity verification (legacy method).
     *
     * This function checks critical state values that must be consistent
     * between legacy and modular implementations.
     *
     * @param legacy The state from the legacy InternalPlayerScreen
     * @param shadow The state from the modular InternalPlayerSession (via shadow mode)
     * @return A [ComparisonResult] containing parity check results and diagnostic flags
     */
    fun compare(
        legacy: InternalPlayerUiState,
        shadow: InternalPlayerUiState,
    ): ComparisonResult {
        val flags = mutableListOf<String>()

        // Resume parity
        val resumeParity = legacy.resumeStartMs == shadow.resumeStartMs
        if (!resumeParity) flags += "resumeMismatch"

        // KidsGate parity
        val kidsParity = legacy.kidBlocked == shadow.kidBlocked
        if (!kidsParity) flags += "kidsGateMismatch"

        // Position delta (safe for nulls)
        val offset =
            if (
                legacy.currentPositionMs != null &&
                shadow.currentPositionMs != null
            ) {
                legacy.currentPositionMs - shadow.currentPositionMs
            } else {
                null
            }

        return ComparisonResult(
            resumeParityOk = resumeParity,
            kidsGateParityOk = kidsParity,
            positionOffsetMs = offset,
            flags = flags,
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Spec-Driven Comparison (Phase 3 Step 4)
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Classification of spec compliance for a given dimension.
     *
     * Based on Behavior Contract Section 5: Spec vs Legacy vs SIP – Classification
     */
    enum class ParityKind {
        /**
         * SIP == Spec AND Legacy == Spec.
         * Both implementations match the specification.
         */
        ExactMatch,

        /**
         * SIP == Spec, Legacy violates Spec.
         * SIP correctly implements the spec, legacy has a bug.
         * Action: Keep SIP behavior, document legacy bug.
         */
        SpecPreferredSIP,

        /**
         * Legacy == Spec, SIP violates Spec.
         * Legacy is correct, SIP needs to be fixed.
         * Action: Fix SIP to match spec.
         */
        SpecPreferredLegacy,

        /**
         * Both SIP and Legacy violate the spec.
         * Both implementations need to be fixed.
         */
        BothViolateSpec,

        /**
         * Divergence allowed by spec (tolerated variance).
         * Minor differences that the spec explicitly allows.
         */
        DontCare,
    }

    /**
     * Result of spec-driven comparison between SIP, Legacy, and the Behavior Contract.
     *
     * This result reflects contract compliance, not legacy compliance.
     *
     * @property parityKind Classification of the comparison result
     * @property dimension The aspect being compared ("resume", "kids", "position", etc.)
     * @property legacyValue The value from legacy implementation
     * @property sipValue The value from SIP implementation
     * @property specDetails Human-readable description of what the spec requires
     * @property flags Additional diagnostic flags
     */
    data class SpecComparisonResult(
        val parityKind: ParityKind,
        val dimension: String,
        val legacyValue: Any?,
        val sipValue: Any?,
        val specDetails: String,
        val flags: List<String> = emptyList(),
    )

    /**
     * Perform spec-driven comparison for all dimensions.
     *
     * This method performs three-way comparison:
     * 1. SIP state vs Spec
     * 2. Legacy state vs Spec
     * 3. SIP vs Legacy (for context only)
     *
     * @param legacy The state from the legacy InternalPlayerScreen
     * @param sip The state from the modular InternalPlayerSession
     * @param playbackType The type of playback (VOD, SERIES, LIVE)
     * @param durationMs The known duration of the media (for near-end calculations)
     * @return List of [SpecComparisonResult] for each compared dimension
     */
    fun compareAgainstSpec(
        legacy: InternalPlayerUiState,
        sip: InternalPlayerUiState,
        playbackType: PlaybackType = PlaybackType.VOD,
        durationMs: Long? = null,
    ): List<SpecComparisonResult> {
        val results = mutableListOf<SpecComparisonResult>()

        // Resume comparison
        results += compareResumeAgainstSpec(legacy, sip, playbackType, durationMs)

        // Kids gate comparison
        results += compareKidsGateAgainstSpec(legacy, sip)

        // Position comparison
        results += comparePositionAgainstSpec(legacy, sip)

        return results
    }

    /**
     * Compare resume behavior against the Behavior Contract.
     *
     * Spec rules from Section 3:
     * - LIVE content never resumes
     * - Resume only if stored position > 10 seconds
     * - Clear resume when remaining < 10 seconds
     */
    fun compareResumeAgainstSpec(
        legacy: InternalPlayerUiState,
        sip: InternalPlayerUiState,
        playbackType: PlaybackType,
        durationMs: Long?,
    ): SpecComparisonResult {
        val legacyResume = legacy.resumeStartMs
        val sipResume = sip.resumeStartMs

        // Rule: LIVE content never resumes (Section 3.1)
        if (playbackType == PlaybackType.LIVE) {
            val legacyCompliant = legacyResume == null
            val sipCompliant = sipResume == null

            return when {
                sipCompliant && legacyCompliant ->
                    SpecComparisonResult(
                        parityKind = ParityKind.ExactMatch,
                        dimension = "resume",
                        legacyValue = legacyResume,
                        sipValue = sipResume,
                        specDetails = "LIVE content never resumes (Section 3.1)",
                    )
                sipCompliant && !legacyCompliant ->
                    SpecComparisonResult(
                        parityKind = ParityKind.SpecPreferredSIP,
                        dimension = "resume",
                        legacyValue = legacyResume,
                        sipValue = sipResume,
                        specDetails = "LIVE content never resumes; legacy incorrectly stores resume (Section 3.1)",
                        flags = listOf("legacyBug:liveResume"),
                    )
                !sipCompliant && legacyCompliant ->
                    SpecComparisonResult(
                        parityKind = ParityKind.SpecPreferredLegacy,
                        dimension = "resume",
                        legacyValue = legacyResume,
                        sipValue = sipResume,
                        specDetails = "LIVE content never resumes; SIP incorrectly stores resume (Section 3.1)",
                        flags = listOf("sipViolation:liveResume"),
                    )
                else ->
                    SpecComparisonResult(
                        parityKind = ParityKind.BothViolateSpec,
                        dimension = "resume",
                        legacyValue = legacyResume,
                        sipValue = sipResume,
                        specDetails = "LIVE content never resumes; both store resume (Section 3.1)",
                        flags = listOf("bothViolate:liveResume"),
                    )
            }
        }

        // Rule: Resume only if position > 10 seconds (Section 3.3)
        val legacyResumeCompliant = legacyResume == null || legacyResume > RESUME_MIN_THRESHOLD_MS
        val sipResumeCompliant = sipResume == null || sipResume > RESUME_MIN_THRESHOLD_MS

        // Rule: Clear resume when near end (Section 3.4)
        val legacyNearEnd = isNearEnd(legacyResume, durationMs)
        val sipNearEnd = isNearEnd(sipResume, durationMs)

        // Near-end should clear resume
        val legacyNearEndCompliant = if (legacyNearEnd) legacyResume == null else true
        val sipNearEndCompliant = if (sipNearEnd) sipResume == null else true

        // Combine compliance checks
        val legacyFullyCompliant = legacyResumeCompliant && legacyNearEndCompliant
        val sipFullyCompliant = sipResumeCompliant && sipNearEndCompliant

        // If values match and both compliant, or both values are same
        if (legacyResume == sipResume) {
            return if (sipFullyCompliant && legacyFullyCompliant) {
                SpecComparisonResult(
                    parityKind = ParityKind.ExactMatch,
                    dimension = "resume",
                    legacyValue = legacyResume,
                    sipValue = sipResume,
                    specDetails = "Resume position complies with spec (>10s threshold, near-end clearing)",
                )
            } else {
                SpecComparisonResult(
                    parityKind = ParityKind.BothViolateSpec,
                    dimension = "resume",
                    legacyValue = legacyResume,
                    sipValue = sipResume,
                    specDetails = "Both match but violate spec (threshold or near-end rule)",
                    flags = listOf("bothViolate:resumeThreshold"),
                )
            }
        }

        // Values differ - classify based on compliance
        return when {
            sipFullyCompliant && !legacyFullyCompliant ->
                SpecComparisonResult(
                    parityKind = ParityKind.SpecPreferredSIP,
                    dimension = "resume",
                    legacyValue = legacyResume,
                    sipValue = sipResume,
                    specDetails = buildResumeSpecDetails(legacyResumeCompliant, legacyNearEndCompliant, "Legacy"),
                    flags = listOf("legacyBug:resumeThreshold"),
                )
            !sipFullyCompliant && legacyFullyCompliant ->
                SpecComparisonResult(
                    parityKind = ParityKind.SpecPreferredLegacy,
                    dimension = "resume",
                    legacyValue = legacyResume,
                    sipValue = sipResume,
                    specDetails = buildResumeSpecDetails(sipResumeCompliant, sipNearEndCompliant, "SIP"),
                    flags = listOf("sipViolation:resumeThreshold"),
                )
            !sipFullyCompliant && !legacyFullyCompliant ->
                SpecComparisonResult(
                    parityKind = ParityKind.BothViolateSpec,
                    dimension = "resume",
                    legacyValue = legacyResume,
                    sipValue = sipResume,
                    specDetails = "Both violate spec resume rules (Section 3.3/3.4)",
                    flags = listOf("bothViolate:resumeThreshold"),
                )
            else ->
                SpecComparisonResult(
                    parityKind = ParityKind.DontCare,
                    dimension = "resume",
                    legacyValue = legacyResume,
                    sipValue = sipResume,
                    specDetails = "Both compliant but different values (allowed tolerance)",
                )
        }
    }

    /**
     * Check if a resume position is near the end of playback.
     *
     * @param resumePosition The resume position in milliseconds
     * @param duration The total duration in milliseconds
     * @return True if remaining time is within the near-end threshold
     */
    private fun isNearEnd(
        resumePosition: Long?,
        duration: Long?,
    ): Boolean {
        if (resumePosition == null || duration == null) return false
        val remaining = duration - resumePosition
        return remaining <= NEAR_END_THRESHOLD_MS
    }

    private fun buildResumeSpecDetails(
        thresholdCompliant: Boolean,
        nearEndCompliant: Boolean,
        source: String,
    ): String =
        buildString {
            append("$source violates spec: ")
            if (!thresholdCompliant) append("position ≤10s not cleared (Section 3.3); ")
            if (!nearEndCompliant) append("near-end position not cleared (Section 3.4)")
        }

    /**
     * Compare kids gate behavior against the Behavior Contract.
     *
     * Spec rules from Section 4:
     * - Quota decremented only when playback active and kid profile active
     * - Block when quota <= 0
     * - Fail-open on errors
     */
    fun compareKidsGateAgainstSpec(
        legacy: InternalPlayerUiState,
        sip: InternalPlayerUiState,
    ): SpecComparisonResult {
        val legacyBlocked = legacy.kidBlocked
        val sipBlocked = sip.kidBlocked
        val legacyActive = legacy.kidActive
        val sipActive = sip.kidActive
        val legacyRemaining = legacy.remainingKidsMinutes
        val sipRemaining = sip.remainingKidsMinutes

        // Rule: Block state when quota <= 0 (Section 4.3)
        // If active and remaining is known, blocked should be true when remaining <= 0
        val legacyBlockCompliant =
            if (legacyActive && legacyRemaining != null) {
                (legacyRemaining <= 0) == legacyBlocked
            } else {
                true // Can't verify without data
            }

        val sipBlockCompliant =
            if (sipActive && sipRemaining != null) {
                (sipRemaining <= 0) == sipBlocked
            } else {
                true // Can't verify without data
            }

        // If both have same blocked state
        if (legacyBlocked == sipBlocked) {
            return if (legacyBlockCompliant && sipBlockCompliant) {
                SpecComparisonResult(
                    parityKind = ParityKind.ExactMatch,
                    dimension = "kids",
                    legacyValue = mapOf("blocked" to legacyBlocked, "active" to legacyActive, "remaining" to legacyRemaining),
                    sipValue = mapOf("blocked" to sipBlocked, "active" to sipActive, "remaining" to sipRemaining),
                    specDetails = "Kids gate state matches and complies with quota blocking rule (Section 4.3)",
                )
            } else {
                SpecComparisonResult(
                    parityKind = ParityKind.BothViolateSpec,
                    dimension = "kids",
                    legacyValue = mapOf("blocked" to legacyBlocked, "active" to legacyActive, "remaining" to legacyRemaining),
                    sipValue = mapOf("blocked" to sipBlocked, "active" to sipActive, "remaining" to sipRemaining),
                    specDetails = "Both match but violate quota blocking rule (Section 4.3)",
                    flags = listOf("bothViolate:kidsBlockRule"),
                )
            }
        }

        // Different blocked states - classify
        return when {
            sipBlockCompliant && !legacyBlockCompliant ->
                SpecComparisonResult(
                    parityKind = ParityKind.SpecPreferredSIP,
                    dimension = "kids",
                    legacyValue = mapOf("blocked" to legacyBlocked, "remaining" to legacyRemaining),
                    sipValue = mapOf("blocked" to sipBlocked, "remaining" to sipRemaining),
                    specDetails = "Legacy violates quota blocking rule; SIP is correct (Section 4.3)",
                    flags = listOf("legacyBug:kidsBlockRule"),
                )
            !sipBlockCompliant && legacyBlockCompliant ->
                SpecComparisonResult(
                    parityKind = ParityKind.SpecPreferredLegacy,
                    dimension = "kids",
                    legacyValue = mapOf("blocked" to legacyBlocked, "remaining" to legacyRemaining),
                    sipValue = mapOf("blocked" to sipBlocked, "remaining" to sipRemaining),
                    specDetails = "SIP violates quota blocking rule; Legacy is correct (Section 4.3)",
                    flags = listOf("sipViolation:kidsBlockRule"),
                )
            !sipBlockCompliant && !legacyBlockCompliant ->
                SpecComparisonResult(
                    parityKind = ParityKind.BothViolateSpec,
                    dimension = "kids",
                    legacyValue = mapOf("blocked" to legacyBlocked, "remaining" to legacyRemaining),
                    sipValue = mapOf("blocked" to sipBlocked, "remaining" to sipRemaining),
                    specDetails = "Both violate quota blocking rule (Section 4.3)",
                    flags = listOf("bothViolate:kidsBlockRule"),
                )
            else ->
                SpecComparisonResult(
                    parityKind = ParityKind.DontCare,
                    dimension = "kids",
                    legacyValue = mapOf("blocked" to legacyBlocked, "remaining" to legacyRemaining),
                    sipValue = mapOf("blocked" to sipBlocked, "remaining" to sipRemaining),
                    specDetails = "Both compliant but different states (allowed variance)",
                )
        }
    }

    /**
     * Compare position tracking against the Behavior Contract.
     *
     * Spec allows minor position drift within tolerance.
     */
    fun comparePositionAgainstSpec(
        legacy: InternalPlayerUiState,
        sip: InternalPlayerUiState,
    ): SpecComparisonResult {
        val legacyPos = legacy.currentPositionMs
        val sipPos = sip.currentPositionMs

        // If either is null, can't compare
        if (legacyPos == null || sipPos == null) {
            return SpecComparisonResult(
                parityKind = ParityKind.DontCare,
                dimension = "position",
                legacyValue = legacyPos,
                sipValue = sipPos,
                specDetails = "Position unavailable for comparison",
            )
        }

        val offset = abs(legacyPos - sipPos)

        return if (offset <= POSITION_TOLERANCE_MS) {
            SpecComparisonResult(
                parityKind = ParityKind.DontCare,
                dimension = "position",
                legacyValue = legacyPos,
                sipValue = sipPos,
                specDetails = "Position drift ${offset}ms within tolerance (${POSITION_TOLERANCE_MS}ms)",
            )
        } else {
            SpecComparisonResult(
                parityKind = ParityKind.ExactMatch, // Both are "correct" but diverged
                dimension = "position",
                legacyValue = legacyPos,
                sipValue = sipPos,
                specDetails = "Position drift ${offset}ms exceeds tolerance; flagged for investigation",
                flags = listOf("positionDrift:${offset}ms"),
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Phase 3B: Enforcement Helper Methods
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Check if any spec comparison results indicate SIP-specific violations
     * that require enforcement.
     *
     * This helper returns only results where SIP violates the spec:
     * - SpecPreferredLegacy: Legacy is correct, SIP needs fixing
     * - BothViolateSpec: Both are wrong, but SIP still needs correction
     *
     * Results where SIP is correct (ExactMatch, SpecPreferredSIP, DontCare)
     * are filtered out.
     *
     * @param results List of spec comparison results
     * @return List of results that indicate SIP violations requiring enforcement
     */
    fun filterSipViolations(results: List<SpecComparisonResult>): List<SpecComparisonResult> =
        results.filter { result ->
            result.parityKind == ParityKind.SpecPreferredLegacy ||
                result.parityKind == ParityKind.BothViolateSpec
        }

    /**
     * Check if any enforcement is needed for the given spec comparison results.
     *
     * Returns true if at least one result indicates a SIP violation.
     *
     * @param results List of spec comparison results
     * @return True if enforcement is needed
     */
    fun needsEnforcement(results: List<SpecComparisonResult>): Boolean = filterSipViolations(results).isNotEmpty()

    /**
     * Build a structured diff containing all contract dimensions with their
     * comparison results.
     *
     * This is useful for diagnostics logging and debugging.
     *
     * @param results List of spec comparison results
     * @return Map of dimension name to comparison result
     */
    fun buildStructuredDiff(results: List<SpecComparisonResult>): Map<String, SpecComparisonResult> = results.associateBy { it.dimension }

    /**
     * Get a summary of all violations for logging.
     *
     * @param results List of spec comparison results
     * @return Human-readable summary of violations
     */
    fun summarizeViolations(results: List<SpecComparisonResult>): String {
        val violations = filterSipViolations(results)
        if (violations.isEmpty()) {
            return "No SIP violations detected"
        }
        return violations.joinToString("; ") { result ->
            "${result.dimension}: ${result.parityKind.name} - ${result.specDetails}"
        }
    }
}
