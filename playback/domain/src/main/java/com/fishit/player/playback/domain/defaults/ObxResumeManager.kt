package com.fishit.player.playback.domain.defaults

import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.MediaKind
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.PlaybackType
import com.fishit.player.core.model.ResumePoint
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.ids.asCanonicalId
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.playback.domain.ResumeManager
import javax.inject.Inject
import javax.inject.Singleton
import com.fishit.player.core.playermodel.SourceType as PlayerSourceType

/**
 * ObjectBox-backed ResumeManager implementation.
 *
 * Uses [CanonicalMediaRepository] for persistence, providing:
 * - Cross-pipeline resume: same movie from Telegram/Xtream shares position
 * - Cross-source resume: different files of same content use percentage-based resume
 * - Profile-aware: each profile has independent resume positions
 *
 * Per TODO_AUDIT_BLOCKING_ISSUES.md:
 * - Replaces in-memory [DefaultResumeManager] with real persistence
 * - Uses existing `ObxCanonicalResumeMark` entity via repository
 */
@Singleton
class ObxResumeManager
    @Inject
    constructor(
        private val canonicalMediaRepository: CanonicalMediaRepository,
    ) : ResumeManager {
        companion object {
            private const val TAG = "ObxResumeManager"

            /**
             * Default profile ID until multi-profile support is added.
             * Per contract: Phase 6 will add proper profile selection.
             */
            private const val DEFAULT_PROFILE_ID = 1L
        }

        override suspend fun getResumePoint(contentId: String): ResumePoint? =
            try {
                val canonicalId = parseCanonicalId(contentId)
                val resumeInfo =
                    canonicalMediaRepository.getCanonicalResume(
                        canonicalId = canonicalId,
                        profileId = DEFAULT_PROFILE_ID,
                    )

                resumeInfo?.let { info ->
                    ResumePoint(
                        contentId = contentId,
                        type = PlaybackType.VOD, // Default; actual type derived from source
                        positionMs = info.positionMs,
                        durationMs = info.durationMs,
                        updatedAt = info.updatedAt,
                        profileId = DEFAULT_PROFILE_ID,
                    )
                }
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to get resume for $contentId: ${e.message}" }
                null
            }

        override suspend fun saveResumePoint(
            context: PlaybackContext,
            positionMs: Long,
            durationMs: Long,
        ) {
            try {
                val canonicalId = parseCanonicalId(context.canonicalId)

                // Create a minimal source ref for tracking which source was last played
                // Use sourceKey or canonicalId as the source identifier
                val sourceRef =
                    MediaSourceRef(
                        sourceType = mapPlayerSourceType(context.sourceType),
                        sourceId = PipelineItemId(context.sourceKey ?: context.canonicalId),
                        sourceLabel = context.title,
                        addedAt = System.currentTimeMillis(),
                    )

                canonicalMediaRepository.setCanonicalResume(
                    canonicalId = canonicalId,
                    profileId = DEFAULT_PROFILE_ID,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    sourceRef = sourceRef,
                )

                UnifiedLog.d(TAG) {
                    "Saved resume: ${context.canonicalId} at ${positionMs}ms / ${durationMs}ms"
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to save resume for ${context.canonicalId}" }
            }
        }

        override suspend fun clearResumePoint(contentId: String) {
            try {
                val canonicalId = parseCanonicalId(contentId)
                canonicalMediaRepository.clearCanonicalResume(
                    canonicalId = canonicalId,
                    profileId = DEFAULT_PROFILE_ID,
                )
                UnifiedLog.d(TAG) { "Cleared resume for $contentId" }
            } catch (e: Exception) {
                UnifiedLog.w(TAG) { "Failed to clear resume for $contentId: ${e.message}" }
            }
        }

        override suspend fun getAllResumePoints(): List<ResumePoint> =
            try {
                val resumeList =
                    canonicalMediaRepository.getResumeList(
                        profileId = DEFAULT_PROFILE_ID,
                        limit = 50,
                    )

                resumeList.map { entry ->
                    ResumePoint(
                        contentId = entry.media.canonicalId.key.value,
                        type =
                            when (entry.media.canonicalId.kind) {
                                MediaKind.EPISODE -> PlaybackType.VOD
                                MediaKind.MOVIE -> PlaybackType.VOD
                            },
                        positionMs = entry.resume.positionMs,
                        durationMs = entry.resume.durationMs,
                        updatedAt = entry.resume.updatedAt,
                        profileId = DEFAULT_PROFILE_ID,
                    )
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "Failed to get all resume points" }
                emptyList()
            }

        /**
         * Parse a canonical ID string to CanonicalMediaId.
         *
         * Format: "kind:key" where kind is "movie" or "episode"
         * Falls back to MOVIE if no kind prefix.
         */
        private fun parseCanonicalId(contentId: String): CanonicalMediaId {
            val parts = contentId.split(":", limit = 2)
            return if (parts.size == 2) {
                val kind =
                    when (parts[0].lowercase()) {
                        "episode" -> MediaKind.EPISODE
                        else -> MediaKind.MOVIE
                    }
                CanonicalMediaId(kind, parts[1].asCanonicalId())
            } else {
                // Assume movie if no prefix
                CanonicalMediaId(MediaKind.MOVIE, contentId.asCanonicalId())
            }
        }

        /**
         * Map player SourceType to model SourceType.
         */
        private fun mapPlayerSourceType(sourceType: PlayerSourceType): SourceType =
            when (sourceType) {
                PlayerSourceType.TELEGRAM -> SourceType.TELEGRAM
                PlayerSourceType.XTREAM -> SourceType.XTREAM
                PlayerSourceType.FILE -> SourceType.IO
                PlayerSourceType.HTTP -> SourceType.XTREAM
                PlayerSourceType.AUDIOBOOK -> SourceType.AUDIOBOOK
                PlayerSourceType.UNKNOWN -> SourceType.UNKNOWN
            }
    }
