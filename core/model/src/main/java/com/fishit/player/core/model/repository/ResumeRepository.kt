package com.fishit.player.core.model.repository

import com.fishit.player.core.model.PlaybackType

/**
 * Repository interface for managing resume positions in FishIT Player v2.
 *
 * Resume behavior contract (from v1):
 * - Save resume positions only after >10 seconds watched
 * - Clear resume when remaining time < 10 seconds
 * - LIVE content never has resume positions
 */
interface ResumeRepository {
    /**
     * Resume position data.
     */
    data class ResumePosition(
        val contentId: String,
        val positionSecs: Int,
        val updatedAt: Long,
    )

    /**
     * Get resume position for content.
     *
     * @param contentId Content identifier (e.g., "vod:123", "series:456:1:3")
     * @param type Playback type for type-specific logic
     * @return Resume position in seconds, or null if no resume exists
     */
    suspend fun getResumePosition(
        contentId: String,
        type: PlaybackType,
    ): Int?

    /**
     * Save resume position for content.
     *
     * @param contentId Content identifier
     * @param type Playback type
     * @param positionSecs Position in seconds (only saved if > 10)
     * @param durationSecs Total duration in seconds for threshold check
     */
    suspend fun saveResumePosition(
        contentId: String,
        type: PlaybackType,
        positionSecs: Int,
        durationSecs: Int?,
    )

    /**
     * Clear resume position for content.
     * Called when content is finished or manually cleared.
     */
    suspend fun clearResumePosition(
        contentId: String,
        type: PlaybackType,
    )

    /**
     * Get all resume positions, ordered by most recent.
     * Used for "Continue Watching" features.
     */
    suspend fun getAllResumePositions(limit: Int = 20): List<ResumePosition>

    /**
     * Clear all resume positions older than the specified time.
     *
     * @param olderThanMs Clear positions older than this timestamp
     */
    suspend fun clearOldResumePositions(olderThanMs: Long)
}
