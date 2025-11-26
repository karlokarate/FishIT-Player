package com.chris.m3usuite.player.internal.live

/**
 * Domain model representing Live EPG information for future Info Panel features.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 – TASK 2: LIVE EPG INFO STATE SCAFFOLD
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This model is a preparation for a future "Live info panel" feature (Phase 3+).
 * It is populated by [DefaultLivePlaybackController] but NOT yet rendered in the UI.
 *
 * The info state provides:
 * - Current program title (nowTitle)
 * - Next program title (nextTitle)
 * - Progress percentage through the current program (progressPercent)
 *
 * **Future Use Cases:**
 * - Mini-info panel showing current program with progress bar
 * - Extended EPG display with more details
 * - Channel guide overlays
 *
 * **Design Decisions:**
 * - Kept separate from [EpgOverlayState] to allow independent evolution
 * - progressPercent is Float (0.0-1.0 range) for UI-agnostic representation
 * - Nullable titles allow graceful handling of missing EPG data
 *
 * @property nowTitle Title of the currently playing program, if available.
 * @property nextTitle Title of the next program, if available.
 * @property progressPercent Progress through the current program as a percentage (0.0 to 1.0).
 *                           0.0 means program just started, 1.0 means program is about to end.
 *                           Will be 0.0 when EPG data is unavailable or program timing is unknown.
 */
data class LiveEpgInfoState(
    val nowTitle: String?,
    val nextTitle: String?,
    val progressPercent: Float,
) {
    init {
        require(progressPercent in 0.0f..1.0f) {
            "progressPercent must be in range 0.0 to 1.0, got: $progressPercent"
        }
    }
    companion object {
        /**
         * Default state when no EPG information is available.
         */
        val EMPTY = LiveEpgInfoState(
            nowTitle = null,
            nextTitle = null,
            progressPercent = 0.0f,
        )
    }
}
