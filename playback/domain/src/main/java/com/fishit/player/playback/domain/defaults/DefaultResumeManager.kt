package com.fishit.player.playback.domain.defaults

import com.fishit.player.core.model.PlaybackType
import com.fishit.player.core.model.ResumePoint
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.playermodel.PlaybackContext
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
        val contentId = context.canonicalId
        resumePoints[contentId] = ResumePoint(
            contentId = contentId,
            type = mapSourceTypeToPlaybackType(context.sourceType),
            positionMs = positionMs,
            durationMs = durationMs,
            profileId = null // TODO: Add profile tracking in Phase 6
        )
    }

    override suspend fun clearResumePoint(contentId: String) {
        resumePoints.remove(contentId)
    }

    override suspend fun getAllResumePoints(): List<ResumePoint> {
        return resumePoints.values.toList()
    }

    private fun mapSourceTypeToPlaybackType(sourceType: SourceType): PlaybackType {
        return when (sourceType) {
            SourceType.TELEGRAM -> PlaybackType.TELEGRAM
            SourceType.XTREAM -> PlaybackType.VOD
            SourceType.IO -> PlaybackType.IO
            SourceType.AUDIOBOOK -> PlaybackType.AUDIOBOOK
            SourceType.LOCAL -> PlaybackType.IO
            SourceType.PLEX -> PlaybackType.VOD
            SourceType.OTHER, SourceType.UNKNOWN -> PlaybackType.VOD
        }
    }
}
