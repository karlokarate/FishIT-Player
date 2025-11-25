package com.chris.m3usuite.player.internal.shadow

import com.chris.m3usuite.player.internal.state.InternalPlayerUiState

/**
 * Compares legacy state vs shadow state for diagnostics.
 * Must never affect runtime behavior.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 STATUS: DIAGNOSTICS-ONLY PARITY COMPARISON SERVICE
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This service provides comparison utilities for verifying parity between:
 * - Legacy player state (from InternalPlayerScreen)
 * - Shadow SIP player state (from InternalPlayerShadow/InternalPlayerSession)
 *
 * **USAGE:**
 * - Diagnostics and verification workflows only
 * - Not wired into runtime player behavior
 * - Called from shadow session callbacks for comparison logging
 *
 * **PARITY CHECKS:**
 * - Resume position parity: Ensures both players resume to the same position
 * - Kids gate parity: Ensures both players block/allow playback consistently
 * - Position offset: Tracks drift between legacy and shadow position tracking
 */
object ShadowComparisonService {

    /**
     * Result of comparing legacy and shadow states.
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
     * Compare legacy state with shadow state for parity verification.
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
        val offset = if (
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
}
