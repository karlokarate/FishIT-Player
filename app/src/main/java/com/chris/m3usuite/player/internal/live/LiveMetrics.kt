package com.chris.m3usuite.player.internal.live

/**
 * Metrics for live TV playback diagnostics.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 – TASK 1: LIVE METRICS FOR SHADOW DIAGNOSTICS
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This data class exposes diagnostic counters and state for SIP validation and shadow
 * diagnostics aggregation. It is designed to be:
 * - **Read-only**: All properties are immutable.
 * - **Lightweight**: Contains only primitive counters and basic state.
 * - **Non-intrusive**: Never affects runtime behavior or playback logic.
 *
 * **Shadow Diagnostics Integration**:
 * - ShadowDiagnosticsAggregator can subscribe to [LivePlaybackController.liveMetrics].
 * - Metrics are updated internally by DefaultLivePlaybackController.
 * - Metrics are only meaningful when SIP is active (not during legacy player usage).
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * @param epgRefreshCount Total number of EPG refresh operations performed.
 * @param epgCacheHitCount Number of times cached EPG data was used (fallback on errors).
 * @param epgStaleDetectionCount Number of times stale EPG was detected and refreshed.
 * @param channelSkipCount Number of channels skipped during navigation (invalid URLs, etc.).
 * @param lastEpgRefreshTimestamp Timestamp of last successful EPG refresh (System.currentTimeMillis).
 */
data class LiveMetrics(
    val epgRefreshCount: Int = 0,
    val epgCacheHitCount: Int = 0,
    val epgStaleDetectionCount: Int = 0,
    val channelSkipCount: Int = 0,
    val lastEpgRefreshTimestamp: Long = 0L,
)
