package com.chris.m3usuite.player.internal.state

import androidx.compose.runtime.Immutable

/**
 * ════════════════════════════════════════════════════════════════════════════════
 * Phase 8 Task 5: Hot/Cold State Split for Compose Performance
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * **HOT STATE** - Frequently updating fields that change during playback.
 *
 * These fields update at high frequency (every 100ms-1000ms) during active playback:
 * - Position updates every ~1s
 * - Buffering state toggles during loading
 * - Playing state toggles on play/pause
 * - Trickplay state updates during fast-forward/rewind
 *
 * **Design Rationale:**
 * - HOT state is collected ONLY in small, focused composables (e.g., PositionIndicator, BufferingSpinner)
 * - These small composables are cheap to recompose
 * - Large layout composables NEVER observe HOT state directly
 * - This prevents unnecessary recomposition of expensive UI trees
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 9.1
 * - INTERNAL_PLAYER_PHASE8_CHECKLIST.md Group 7
 *
 * @property positionMs Current playback position in milliseconds
 * @property durationMs Total duration in milliseconds (usually stable after loading)
 * @property isPlaying Whether playback is active
 * @property isBuffering Whether player is buffering
 * @property trickplayActive Whether trickplay mode is active
 * @property trickplaySpeed Current trickplay speed multiplier
 * @property controlsTick Counter incremented on user activity (for auto-hide timer)
 * @property controlsVisible Whether controls are currently visible
 */
@Immutable
data class PlayerHotState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val trickplayActive: Boolean = false,
    val trickplaySpeed: Float = 1f,
    val controlsTick: Int = 0,
    val controlsVisible: Boolean = true,
) {
    /**
     * Returns formatted position string (MM:SS).
     * Computed property to avoid repeated formatting.
     */
    val formattedPosition: String
        get() = formatMs(positionMs)

    /**
     * Returns formatted duration string (MM:SS).
     * Computed property to avoid repeated formatting.
     */
    val formattedDuration: String
        get() = formatMs(durationMs)

    /**
     * Returns progress as a fraction (0f..1f).
     */
    val progressFraction: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    companion object {
        /**
         * Extracts HOT state fields from full [InternalPlayerUiState].
         *
         * This is used to derive the HOT state when collecting from a ViewModel
         * that manages the full state.
         */
        fun fromFullState(state: InternalPlayerUiState): PlayerHotState = PlayerHotState(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            trickplayActive = state.trickplayActive,
            trickplaySpeed = state.trickplaySpeed,
            controlsTick = state.controlsTick,
            controlsVisible = state.controlsVisible,
        )

        private fun formatMs(ms: Long): String {
            if (ms <= 0L) return "00:00"
            val totalSec = (ms / 1000L).toInt()
            val minutes = totalSec / 60
            val seconds = totalSec % 60
            return "%02d:%02d".format(minutes, seconds)
        }
    }
}
