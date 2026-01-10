package com.fishit.player.core.model.userstate

/**
 * Per-profile, per-work user state.
 *
 * SSOT identity:
 * - profileKey: stable across devices (cloud-ready)
 * - workKey: stable canonical work identity
 *
 * This is a pure domain model (no persistence annotations).
 * Implementations map this to/from persistence entities.
 */
data class WorkUserState(
    // Identity
    val profileKey: String,
    val workKey: String,
    // Resume / Playback
    val positionMs: Long = 0L,
    val durationMsAtLastPlay: Long = 0L,
    val lastPlayedAtMs: Long? = null,
    val lastUsedSourceKey: String? = null,
    val lastUsedVariantKey: String? = null,
    // Watch state
    val isWatched: Boolean = false,
    val watchCount: Int = 0,
    // User actions / library signals
    val isFavorite: Boolean = false,
    val inWatchlist: Boolean = false,
    val isHidden: Boolean = false,
    val userRating: Int? = null, // 1..5, validated in repository implementation
    // Cross-device merge + future cloud
    val lastUpdatedAtMs: Long = 0L,
    val lastUpdatedByDeviceId: String = "",
    val cloudSyncState: CloudSyncState = CloudSyncState.CLEAN,
    // Audit (optional but useful)
    val createdAtMs: Long = 0L,
) {
    companion object {
        /** Works are considered watched when resume >= 90% or explicitly marked. */
        const val WATCHED_THRESHOLD_PCT = 0.9f
    }

    /**
     * Resume percentage (0.0 to 1.0).
     * Returns 0.0 if durationMsAtLastPlay is 0.
     */
    val resumePercentage: Float
        get() =
            if (durationMsAtLastPlay > 0L) {
                (positionMs.toFloat() / durationMsAtLastPlay).coerceIn(0f, 1f)
            } else {
                0f
            }

    /**
     * True if this qualifies for "Continue Watching".
     * - positionMs > 0
     * - resumePercentage < 90%
     */
    val isContinueWatching: Boolean
        get() = positionMs > 0L && resumePercentage < WATCHED_THRESHOLD_PCT
}
