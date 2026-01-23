/**
 * NX-based HomeContentRepository implementation.
 *
 * This implementation reads from the NX work graph (v2 SSOT) instead of the
 * legacy ObxCanonicalMedia layer.
 *
 * **Architecture:**
 * - Feature layer (feature:home) defines HomeContentRepository interface
 * - This implementation provides the data from NX repositories
 * - All reads go through NxWorkRepository, NxWorkUserStateRepository
 *
 * **Migration Note:**
 * This replaces HomeContentRepositoryAdapter which reads from legacy Obx* entities.
 * The old implementation is kept for comparison until NX data is fully populated.
 *
 * **Profile Simplification:**
 * Currently uses DEFAULT_PROFILE_KEY until ProfileManager is implemented in v2.
 * The NX repositories are profile-aware, but we use a single profile for now.
 */
package com.fishit.player.infra.data.nx.home

import com.fishit.player.core.home.domain.HomeContentRepository
import com.fishit.player.core.home.domain.HomeMediaItem
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkRepository.Work
import com.fishit.player.core.model.repository.NxWorkRepository.WorkType
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.model.repository.NxWorkUserStateRepository
import com.fishit.player.core.model.userstate.WorkUserState
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NxHomeContentRepositoryImpl @Inject constructor(
    private val workRepository: NxWorkRepository,
    private val sourceRefRepository: NxWorkSourceRefRepository,
    private val userStateRepository: NxWorkUserStateRepository,
) : HomeContentRepository {

    companion object {
        private const val TAG = "NxHomeContentRepo"
        private const val CONTINUE_WATCHING_LIMIT = 20
        private const val RECENTLY_ADDED_LIMIT = 50
        private const val MOVIES_LIMIT = 200
        private const val SERIES_LIMIT = 200
        private const val CLIPS_LIMIT = 100
        private const val LIVE_LIMIT = 100
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
        
        /**
         * Default profile key until ProfileManager is implemented in v2.
         * All user states (resume marks, favorites, etc.) use this key.
         */
        private const val DEFAULT_PROFILE_KEY = "default"
    }

    // ==================== Primary Content Methods ====================

    override fun observeContinueWatching(): Flow<List<HomeMediaItem>> {
        return userStateRepository.observeContinueWatching(
            profileKey = DEFAULT_PROFILE_KEY,
            limit = CONTINUE_WATCHING_LIMIT,
        ).map { userStates ->
            userStates.mapNotNull { state ->
                val work = workRepository.get(state.workKey) ?: return@mapNotNull null
                val sourceType = determineSourceType(state.workKey)
                work.toHomeMediaItem(
                    sourceType = sourceType,
                    resumePosition = state.resumePositionMs,
                    duration = state.totalDurationMs.takeIf { it > 0 } ?: work.runtimeMs ?: 0L,
                )
            }
        }.catch { e ->
            UnifiedLog.e(TAG, e) { "Failed to observe continue watching" }
            emit(emptyList())
        }
    }

    override fun observeRecentlyAdded(): Flow<List<HomeMediaItem>> {
        return workRepository.observeRecentlyUpdated(limit = RECENTLY_ADDED_LIMIT)
            .map { works ->
                val now = System.currentTimeMillis()
                works
                    .filter { it.type != WorkType.EPISODE } // Episodes don't show as standalone tiles
                    .mapNotNull { work ->
                        val sourceType = determineSourceType(work.workKey)
                        work.toHomeMediaItem(
                            sourceType = sourceType,
                            isNew = (now - work.createdAtMs) < SEVEN_DAYS_MS,
                        )
                    }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe recently added" }
                emit(emptyList())
            }
    }

    override fun observeMovies(): Flow<List<HomeMediaItem>> {
        return workRepository.observeByType(WorkType.MOVIE, limit = MOVIES_LIMIT)
            .map { works ->
                works.mapNotNull { work ->
                    val sourceType = determineSourceType(work.workKey)
                    work.toHomeMediaItem(sourceType = sourceType)
                }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe movies" }
                emit(emptyList())
            }
    }

    override fun observeSeries(): Flow<List<HomeMediaItem>> {
        return workRepository.observeByType(WorkType.SERIES, limit = SERIES_LIMIT)
            .map { works ->
                works.mapNotNull { work ->
                    val sourceType = determineSourceType(work.workKey)
                    work.toHomeMediaItem(sourceType = sourceType)
                }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe series" }
                emit(emptyList())
            }
    }

    override fun observeClips(): Flow<List<HomeMediaItem>> {
        return workRepository.observeByType(WorkType.CLIP, limit = CLIPS_LIMIT)
            .map { works ->
                works.mapNotNull { work ->
                    val sourceType = determineSourceType(work.workKey)
                    work.toHomeMediaItem(sourceType = sourceType)
                }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe clips" }
                emit(emptyList())
            }
    }

    override fun observeXtreamLive(): Flow<List<HomeMediaItem>> {
        return workRepository.observeByType(WorkType.LIVE_CHANNEL, limit = LIVE_LIMIT)
            .map { works ->
                works.mapNotNull { work ->
                    work.toHomeMediaItem(sourceType = SourceType.XTREAM)
                }
            }
            .catch { e ->
                UnifiedLog.e(TAG, e) { "Failed to observe Xtream live" }
                emit(emptyList())
            }
    }

    // ==================== Legacy Methods (backward compatibility) ====================

    override fun observeTelegramMedia(): Flow<List<HomeMediaItem>> {
        // Combine movies, series, clips - filtered for Telegram source
        return combine(
            observeMovies(),
            observeSeries(),
            observeClips(),
        ) { movies, series, clips ->
            (movies + series + clips)
                .filter { it.sourceType == SourceType.TELEGRAM }
                .take(200)
        }
    }

    override fun observeXtreamVod(): Flow<List<HomeMediaItem>> {
        return observeMovies().map { movies ->
            movies.filter { it.sourceType == SourceType.XTREAM }
        }
    }

    override fun observeXtreamSeries(): Flow<List<HomeMediaItem>> {
        return observeSeries().map { series ->
            series.filter { it.sourceType == SourceType.XTREAM }
        }
    }

    // ==================== Mapping Helpers ====================

    /**
     * Map NX_Work to HomeMediaItem.
     */
    private fun Work.toHomeMediaItem(
        sourceType: SourceType,
        resumePosition: Long = 0L,
        duration: Long = runtimeMs ?: 0L,
        isNew: Boolean = false,
    ): HomeMediaItem {
        return HomeMediaItem(
            id = workKey,
            title = displayTitle,
            poster = posterRef?.let { parseImageRef(it) },
            placeholderThumbnail = null, // TODO: Add to NX_Work if needed
            backdrop = backdropRef?.let { parseImageRef(it) },
            mediaType = mapWorkTypeToMediaType(type),
            sourceType = sourceType,
            sourceTypes = listOf(sourceType), // TODO: Expand from source refs
            resumePosition = resumePosition,
            duration = duration,
            isNew = isNew,
            year = year,
            rating = rating?.toFloat(),
            genres = genres,
            navigationId = workKey,
            navigationSource = sourceType,
        )
    }

    /**
     * Determine the best source type for a work by checking its source refs.
     * Priority: XTREAM > TELEGRAM > IO > UNKNOWN
     */
    private suspend fun determineSourceType(workKey: String): SourceType {
        val sourceRefs = sourceRefRepository.findByWorkKey(workKey)
        if (sourceRefs.isEmpty()) return SourceType.UNKNOWN

        // Priority order
        return when {
            sourceRefs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.XTREAM } ->
                SourceType.XTREAM
            sourceRefs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.TELEGRAM } ->
                SourceType.TELEGRAM
            sourceRefs.any { it.sourceType == NxWorkSourceRefRepository.SourceType.IO } ->
                SourceType.IO
            else -> SourceType.UNKNOWN
        }
    }

    private fun mapWorkTypeToMediaType(type: WorkType): MediaType {
        return when (type) {
            WorkType.MOVIE -> MediaType.MOVIE
            WorkType.SERIES -> MediaType.SERIES
            WorkType.EPISODE -> MediaType.SERIES_EPISODE
            WorkType.LIVE_CHANNEL -> MediaType.LIVE
            WorkType.CLIP -> MediaType.CLIP
            WorkType.AUDIOBOOK -> MediaType.AUDIOBOOK
            WorkType.MUSIC_TRACK -> MediaType.MUSIC
            WorkType.UNKNOWN -> MediaType.UNKNOWN
        }
    }

    /**
     * Parse serialized ImageRef string back to ImageRef.
     * Format: "http:url", "tg:remoteId", "file:path", "inline:Nbytes"
     */
    private fun parseImageRef(serialized: String): ImageRef? {
        val colonIndex = serialized.indexOf(':')
        if (colonIndex < 0) return null

        val type = serialized.substring(0, colonIndex)
        val value = serialized.substring(colonIndex + 1)

        return when (type) {
            "http" -> ImageRef.Http(url = value)
            "tg" -> ImageRef.TelegramThumb(remoteId = value)
            "file" -> ImageRef.LocalFile(path = value)
            else -> null
        }
    }
}
