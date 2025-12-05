package com.fishit.player.playback.domain.defaults

import com.fishit.player.core.model.PlaybackContext
import com.fishit.player.core.model.ResumePoint
import com.fishit.player.playback.domain.ResumeManager

/**
 * Default ResumeManager that stores resume points in memory.
 *
 * This is a stub implementation for Phase 1.
 * Real persistence will be added in Phase 6.
 */
class DefaultResumeManager : ResumeManager {

    private val resumePoints = mutableMapOf<String, ResumePoint>()

    override suspend fun getResumePoint(contentId: String): ResumePoint? {
        return resumePoints[contentId]
    }

    override suspend fun saveResumePoint(
        context: PlaybackContext,
        positionMs: Long,
        durationMs: Long
    ) {
        val contentId = context.contentId ?: context.uri
        resumePoints[contentId] = ResumePoint(
            contentId = contentId,
            type = context.type,
            positionMs = positionMs,
            durationMs = durationMs,
            profileId = context.profileId
        )
    }

    override suspend fun clearResumePoint(contentId: String) {
        resumePoints.remove(contentId)
    }

    override suspend fun getAllResumePoints(): List<ResumePoint> {
        return resumePoints.values.toList()
    }
}
