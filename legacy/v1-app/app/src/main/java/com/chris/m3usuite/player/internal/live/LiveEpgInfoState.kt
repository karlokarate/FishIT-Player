package com.chris.m3usuite.player.internal.live

/**
 * State for Live EPG (Electronic Program Guide) information.
 *
 * This data class provides structured EPG information for Live-TV playback,
 * including current/next program titles and progress percentage.
 *
 * **Phase 3 – Task 2: SIP Live-TV Interaction & UX Polish**
 *
 * This state is populated by [LivePlaybackController] whenever the EPG overlay updates.
 * It provides a consistent interface for UI components to display EPG information.
 *
 * @property nowTitle Current program title (now playing), or null if unavailable.
 * @property nextTitle Next program title (upcoming), or null if unavailable.
 * @property progressPercent Progress percentage through current program (0.0-100.0).
 *                           For LIVE content, this is typically 0.0 as live streams have no duration.
 */
data class LiveEpgInfoState(
    val nowTitle: String? = null,
    val nextTitle: String? = null,
    val progressPercent: Float = 0.0f,
    val lastUpdatedAtRealtimeMs: Long? = null,
)
