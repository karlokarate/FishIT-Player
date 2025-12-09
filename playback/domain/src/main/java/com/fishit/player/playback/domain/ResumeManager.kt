package com.fishit.player.playback.domain

import com.fishit.player.core.model.ResumePoint
import com.fishit.player.core.playermodel.PlaybackContext

/**
 * Manages resume positions for playback content.
 *
 * Implementations handle persistence and retrieval of resume points.
 * Uses [PlaybackContext] from core:player-model.
 */
interface ResumeManager {

    /**
     * Gets the resume point for the given content, if any.
     */
    suspend fun getResumePoint(contentId: String): ResumePoint?

    /**
     * Updates or creates a resume point.
     *
     * @param context The playback context (uses canonicalId as content identifier)
     * @param positionMs Current position in milliseconds
     * @param durationMs Total duration in milliseconds
     */
    suspend fun saveResumePoint(
        context: PlaybackContext,
        positionMs: Long,
        durationMs: Long
    )

    /**
     * Clears the resume point for the given content.
     * Called when content is finished or user explicitly clears.
     */
    suspend fun clearResumePoint(contentId: String)

    /**
     * Gets all resume points for the current profile.
     */
    suspend fun getAllResumePoints(): List<ResumePoint>
}
