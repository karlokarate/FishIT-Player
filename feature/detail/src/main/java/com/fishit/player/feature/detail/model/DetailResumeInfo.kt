package com.fishit.player.feature.detail.model

/**
 * UI model for resume information in detail screen.
 *
 * **Architecture (v2 - INV-6 compliant):**
 * - Represents NX_WorkUserState in UI-friendly format.
 * - Contains exactly what the "Continue Watching" badge and Resume button need.
 * - No logic beyond simple display helpers.
 *
 * **Cross-Source Resume:**
 * Uses percentage-based positioning to enable resuming across different sources
 * (e.g., started on Telegram, continue on Xtream).
 */
data class DetailResumeInfo(
    /** Work key this resume belongs to */
    val workKey: String,
    /** Profile ID (for multi-profile support) */
    val profileId: Long,
    /** Resume position in milliseconds (from last source) */
    val positionMs: Long,
    /** Total duration in milliseconds (from last source) */
    val durationMs: Long,
    /** Progress as percentage (0.0 - 1.0) - PRIMARY for cross-source */
    val progressPercent: Float,
    /** True if watched to completion (>90%) */
    val isCompleted: Boolean,
    /** Number of times watched to completion */
    val watchCount: Int,
    /** Last source key used (for same-source detection) */
    val lastSourceKey: String?,
    /** Last source type used */
    val lastSourceType: String?,
    /** Timestamp of last watch activity */
    val lastWatchedAt: Long?,
    /** True if favorited */
    val isFavorite: Boolean,
    /** True if in watchlist */
    val inWatchlist: Boolean,
) {
    /** True if there's significant progress to resume (>2% and <95%) */
    val hasResumeProgress: Boolean
        get() = progressPercent > 0.02f && progressPercent < 0.95f

    /** Progress as percentage integer (0-100) for UI display */
    val progressPercentInt: Int
        get() = (progressPercent * 100).toInt()

    /** Remaining time display string (e.g., "45m left") */
    val remainingTimeDisplay: String
        get() {
            val remainingMs = durationMs - positionMs
            val remainingMins = (remainingMs / 60_000).toInt()
            return when {
                remainingMins >= 60 -> "${remainingMins / 60}h ${remainingMins % 60}m left"
                remainingMins > 0 -> "${remainingMins}m left"
                else -> "Almost done"
            }
        }

    /** Position display string (e.g., "1:23:45") */
    val positionDisplay: String
        get() = formatDuration(positionMs)

    /** Duration display string (e.g., "2:15:30") */
    val durationDisplay: String
        get() = formatDuration(durationMs)

    /**
     * Calculate resume position for a specific source.
     *
     * @param sourceKey The source to resume on
     * @param sourceDurationMs Duration of that source
     * @return Position in ms to seek to
     */
    fun calculatePositionForSource(
        sourceKey: String,
        sourceDurationMs: Long,
    ): Long =
        if (sourceKey == lastSourceKey) {
            // Same source - use exact position
            positionMs
        } else {
            // Different source - use percentage
            (progressPercent * sourceDurationMs).toLong()
        }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    companion object {
        /** Empty resume info for items never watched */
        val EMPTY = DetailResumeInfo(
            workKey = "",
            profileId = 0,
            positionMs = 0,
            durationMs = 0,
            progressPercent = 0f,
            isCompleted = false,
            watchCount = 0,
            lastSourceKey = null,
            lastSourceType = null,
            lastWatchedAt = null,
            isFavorite = false,
            inWatchlist = false,
        )
    }
}
