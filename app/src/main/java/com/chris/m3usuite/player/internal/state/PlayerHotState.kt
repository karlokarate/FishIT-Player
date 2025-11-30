package com.chris.m3usuite.player.internal.state

import androidx.compose.runtime.Immutable

/**
 * Hot (frequently updating) playback state.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 5: Compose & FocusKit Performance Hardening
 * Contract: INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 9
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This data class contains only the fields that update frequently during playback:
 * - Position updates (~1s intervals)
 * - Buffering state changes
 * - Play/pause state changes
 * - Trickplay state changes
 *
 * **Why This Exists:**
 * Splitting hot state from cold state allows small, focused Composables to observe only
 * the fields they need. This prevents large layout trees from recomposing when only
 * position updates occur.
 *
 * **Usage Pattern:**
 * ```kotlin
 * // In a small, focused Composable:
 * @Composable
 * fun ProgressBar(hotState: PlayerHotState, ...) {
 *     // Only recomposes when hotState changes
 * }
 *
 * // In large layout Composable:
 * @Composable
 * fun PlayerControls(coldState: PlayerColdState, ...) {
 *     // Rarely recomposes - only when metadata/style changes
 * }
 * ```
 *
 * **Contract Reference:**
 * - Section 9.1: Hot paths (progress, isPlaying, buffering) in isolated small Composables
 */
@Immutable
data class PlayerHotState(
    /** Current playback position in milliseconds. Updates every ~1s during playback. */
    val positionMs: Long = 0L,
    /** Total duration in milliseconds. Usually stable once media is prepared. */
    val durationMs: Long = 0L,
    /** Whether media is currently playing. */
    val isPlaying: Boolean = false,
    /** Whether the player is currently buffering. */
    val isBuffering: Boolean = false,
    /** Whether trickplay mode is active (fast-forward/rewind). */
    val trickplayActive: Boolean = false,
    /** Current trickplay speed multiplier (1.0 = normal, 2.0 = 2x, -2.0 = -2x rewind). */
    val trickplaySpeed: Float = 1f,
    /** Whether the seek preview overlay is visible. */
    val seekPreviewVisible: Boolean = false,
    /** Target position in milliseconds for seek preview. */
    val seekPreviewTargetMs: Long? = null,
    /** Whether player controls are currently visible. */
    val controlsVisible: Boolean = true,
    /** Counter incremented on user activity to reset auto-hide timer. */
    val controlsTick: Int = 0,
) {
    /**
     * Calculates the progress fraction (0.0 to 1.0).
     */
    val progressFraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Extension function to extract hot state from InternalPlayerUiState.
 *
 * This allows existing code to continue using InternalPlayerUiState while
 * gradually migrating performance-critical paths to use PlayerHotState.
 */
fun InternalPlayerUiState.toHotState(): PlayerHotState =
    PlayerHotState(
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        trickplayActive = trickplayActive,
        trickplaySpeed = trickplaySpeed,
        seekPreviewVisible = seekPreviewVisible,
        seekPreviewTargetMs = seekPreviewTargetMs,
        controlsVisible = controlsVisible,
        controlsTick = controlsTick,
    )
