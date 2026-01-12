package com.fishit.player.core.model.userstate

/**
 * Cloud sync state for user state entities.
 */
enum class CloudSyncState {
    /**
     * State is only on this device, not intended for cloud sync.
     */
    LOCAL_ONLY,

    /**
     * State has been modified locally and needs to be synced to cloud.
     */
    DIRTY,

    /**
     * State is synchronized with cloud.
     */
    SYNCED,
}

/**
 * Domain model for per-work user state.
 *
 * This is a pure domain model (no ObjectBox annotations).
 * Implementation maps to NX_WorkUserState entity in infra/data-nx.
 *
 * @property profileKey Profile identifier (SSOT)
 * @property workKey Work identifier (SSOT)
 * @property resumePositionMs Resume position in milliseconds
 * @property totalDurationMs Total duration in milliseconds
 * @property isWatched True if watched to completion
 * @property watchCount Number of times watched
 * @property isFavorite True if favorited
 * @property userRating User rating (1-5 stars, null if not rated)
 * @property inWatchlist True if added to watchlist
 * @property isHidden True if hidden from recommendations
 * @property lastWatchedAtMs Last watched timestamp in milliseconds
 * @property createdAtMs Creation timestamp in milliseconds
 * @property updatedAtMs Last update timestamp in milliseconds
 * @property lastUpdatedByDeviceId Device that last updated this state
 * @property cloudSyncState Cloud sync state
 */
data class WorkUserState(
    val profileKey: String,
    val workKey: String,
    val resumePositionMs: Long = 0,
    val totalDurationMs: Long = 0,
    val isWatched: Boolean = false,
    val watchCount: Int = 0,
    val isFavorite: Boolean = false,
    val userRating: Int? = null,
    val inWatchlist: Boolean = false,
    val isHidden: Boolean = false,
    val lastWatchedAtMs: Long? = null,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val lastUpdatedByDeviceId: String? = null,
    val cloudSyncState: CloudSyncState = CloudSyncState.LOCAL_ONLY,
)
