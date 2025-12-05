package com.fishit.player.core.model.repository

/**
 * Repository interface for managing screen time tracking for kids profiles.
 *
 * Screen time is tracked as minutes per day for kids profiles.
 * When the daily limit is reached, playback should be blocked.
 */
interface ScreenTimeRepository {
    /**
     * Screen time entry for a specific day.
     */
    data class ScreenTimeEntry(
        val kidProfileId: Long,
        val dayYyyymmdd: String,
        val usedMinutes: Int,
        val limitMinutes: Int,
    )

    /**
     * Get screen time entry for a specific profile and day.
     * Creates a new entry with default limit if none exists.
     */
    suspend fun getScreenTimeEntry(
        kidProfileId: Long,
        dayYyyymmdd: String,
    ): ScreenTimeEntry

    /**
     * Add used minutes to the screen time for a profile on a specific day.
     */
    suspend fun addUsedMinutes(
        kidProfileId: Long,
        dayYyyymmdd: String,
        minutes: Int,
    )

    /**
     * Set the daily limit for a profile on a specific day.
     */
    suspend fun setDailyLimit(
        kidProfileId: Long,
        dayYyyymmdd: String,
        limitMinutes: Int,
    )

    /**
     * Check if a profile has remaining screen time for a specific day.
     * @return true if usedMinutes < limitMinutes
     */
    suspend fun hasRemainingTime(
        kidProfileId: Long,
        dayYyyymmdd: String,
    ): Boolean

    /**
     * Get remaining minutes for a profile on a specific day.
     * @return Remaining minutes (0 if limit is reached or exceeded)
     */
    suspend fun getRemainingMinutes(
        kidProfileId: Long,
        dayYyyymmdd: String,
    ): Int

    /**
     * Clear screen time entries older than the specified date.
     * Used for cleanup to avoid unbounded growth.
     */
    suspend fun clearOldEntries(olderThanYyyymmdd: String)
}
