package com.chris.m3usuite.player.internal.bridge

import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.player.internal.domain.PlaybackContext

/**
 * Phase 3 Shadow Mode:
 * Runs the modular InternalPlayerSession in parallel without controlling playback.
 * Output is used purely for future verification and diagnostics.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 STATUS: SHADOW-MODE ENTRY POINT (NON-RUNTIME)
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This entry point allows shadow-mode invocation of the modular SIP session from
 * Phase 3+ verification tasks. It does NOT control playback and does NOT interact
 * with the real player or modify runtime behavior.
 *
 * **CURRENT STATUS:**
 * - NOT called by runtime code paths.
 * - Runtime flow remains: InternalPlayerEntry → legacy InternalPlayerScreen
 * - Future Phase 3–4 verification workflows may invoke this entry point to:
 *   1. Observe parallel modular session behavior
 *   2. Compare modular vs legacy state emissions
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
     * **FUTURE PHASE 3 USAGE:**
     * ```kotlin
     * // Example verification workflow (not yet implemented)
     * InternalPlayerShadow.startShadowSession(
     *     url = currentUrl,
     *     startMs = providedStartMs,
     *     mimeType = providedMimeType,
     *     mediaItem = preparedMediaItem,
     *     playbackContext = playbackContext,
     *     onShadowStateChanged = { shadowState ->
     *         // Compare shadowState with legacy state
     *         diagnosticsLogger.logShadowComparison(legacyState, shadowState)
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
     */
    @Suppress("UNUSED_PARAMETER")
    fun startShadowSession(
        url: String,
        startMs: Long?,
        mimeType: String?,
        mediaItem: MediaItem?,
        playbackContext: PlaybackContext,
        onShadowStateChanged: ((ShadowSessionState) -> Unit)? = null,
    ) {
        // TODO(Phase 3): Instantiate modular InternalPlayerSession without wiring to UI or ExoPlayer.
        // This session must not interact with the real player or modify runtime behavior.
        //
        // Implementation steps for future Phase 3 work:
        // 1. Create a "headless" version of rememberInternalPlayerSession that doesn't
        //    require @Composable context or real ExoPlayer
        // 2. Wire ResumeManager and KidsPlaybackGate in passive mode
        // 3. Emit state updates through onShadowStateChanged callback
        // 4. Log all state transitions for comparison with legacy behavior
        //
        // For now, this is a no-op placeholder that defines the shadow-mode contract.
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
