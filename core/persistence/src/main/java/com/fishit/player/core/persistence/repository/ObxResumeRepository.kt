package com.fishit.player.core.persistence.repository

import com.fishit.player.core.model.PlaybackType
import com.fishit.player.core.model.repository.ResumeRepository
import com.fishit.player.core.persistence.obx.*
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ObjectBox-backed implementation of [ResumeRepository].
 * 
 * Implements resume behavior contract:
 * - Save only if position > 10 seconds
 * - Clear if remaining time < 10 seconds
 * - Never save for LIVE content
 */
@Singleton
class ObxResumeRepository @Inject constructor(
    private val boxStore: BoxStore
) : ResumeRepository {
    
    private val resumeBox = boxStore.boxFor<ObxResumeMark>()
    
    companion object {
        private const val MIN_RESUME_THRESHOLD_SECS = 10
        private const val NEAR_END_THRESHOLD_SECS = 10
    }
    
    override suspend fun getResumePosition(
        contentId: String,
        type: PlaybackType
    ): Int? = withContext(Dispatchers.IO) {
        if (type == PlaybackType.LIVE) return@withContext null
        
        resumeBox.query(
            ObxResumeMark_.contentId.equal(contentId)
                .and(ObxResumeMark_.type.equal(type.toObxType()))
        )
            .build()
            .findFirst()
            ?.positionSecs
    }
    
    override suspend fun saveResumePosition(
        contentId: String,
        type: PlaybackType,
        positionSecs: Int,
        durationSecs: Int?
    ) = withContext(Dispatchers.IO) {
        // Never save resume for LIVE content
        if (type == PlaybackType.LIVE) return@withContext
        
        // Only save if position > 10 seconds
        if (positionSecs <= MIN_RESUME_THRESHOLD_SECS) {
            return@withContext
        }
        
        // Clear if near end (remaining < 10 seconds)
        if (durationSecs != null) {
            val remaining = durationSecs - positionSecs
            if (remaining < NEAR_END_THRESHOLD_SECS) {
                clearResumePosition(contentId, type)
                return@withContext
            }
        }
        
        // Find existing or create new
        val existing = resumeBox.query(
            ObxResumeMark_.contentId.equal(contentId)
                .and(ObxResumeMark_.type.equal(type.toObxType()))
        )
            .build()
            .findFirst()
        
        if (existing != null) {
            existing.positionSecs = positionSecs
            existing.updatedAt = System.currentTimeMillis()
            resumeBox.put(existing)
        } else {
            val newMark = ObxResumeMark(
                contentId = contentId,
                type = type.toObxType(),
                positionSecs = positionSecs,
                updatedAt = System.currentTimeMillis()
            )
            resumeBox.put(newMark)
        }
    }
    
    override suspend fun clearResumePosition(
        contentId: String,
        type: PlaybackType
    ) = withContext(Dispatchers.IO) {
        val existing = resumeBox.query(
            ObxResumeMark_.contentId.equal(contentId)
                .and(ObxResumeMark_.type.equal(type.toObxType()))
        )
            .build()
            .findFirst()
        
        if (existing != null) {
            resumeBox.remove(existing)
        }
    }
    
    override suspend fun getAllResumePositions(limit: Int): List<ResumeRepository.ResumePosition> =
        withContext(Dispatchers.IO) {
            resumeBox.query()
                .orderDesc(ObxResumeMark_.updatedAt)
                .build()
                .find(0, limit.toLong())
                .mapNotNull { mark ->
                    mark.contentId?.let {
                        ResumeRepository.ResumePosition(
                            contentId = it,
                            positionSecs = mark.positionSecs,
                            updatedAt = mark.updatedAt
                        )
                    }
                }
        }
    
    override suspend fun clearOldResumePositions(olderThanMs: Long) = withContext(Dispatchers.IO) {
        val old = resumeBox.query(ObxResumeMark_.updatedAt.less(olderThanMs))
            .build()
            .find()
        
        resumeBox.remove(old)
    }
    
    private fun PlaybackType.toObxType() = when (this) {
        PlaybackType.VOD -> "vod"
        PlaybackType.SERIES -> "series"
        PlaybackType.LIVE -> "live"
        PlaybackType.TELEGRAM -> "telegram"
        PlaybackType.AUDIOBOOK -> "audiobook"
        PlaybackType.IO -> "io"
    }
}
