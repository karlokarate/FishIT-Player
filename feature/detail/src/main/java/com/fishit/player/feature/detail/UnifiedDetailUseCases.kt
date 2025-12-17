package com.fishit.player.feature.detail

import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.MediaKind
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.core.model.repository.CanonicalResumeInfo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Use cases for unified detail screen with cross-pipeline source selection.
 *
 * Provides logic for:
 * - Loading canonical media with all sources
 * - Selecting the best source for playback
 * - Syncing resume across sources
 * - Version comparison and selection
 */
class UnifiedDetailUseCases
@Inject
constructor(
        private val canonicalMediaRepository: CanonicalMediaRepository,
) {

    /**
     * Load canonical media with all available sources.
     *
     * @param canonicalId The canonical media ID
     * @return Flow of media with sources and resume info
     */
    fun loadCanonicalMedia(canonicalId: CanonicalMediaId): Flow<UnifiedMediaState> = flow {
        emit(UnifiedMediaState.Loading)

        try {
            val media = canonicalMediaRepository.findByCanonicalId(canonicalId)
            if (media == null) {
                emit(UnifiedMediaState.NotFound)
                return@flow
            }

            // Get resume info for current profile (placeholder profile ID = 0)
            val resume = canonicalMediaRepository.getCanonicalResume(canonicalId, profileId = 0)

            emit(
                    UnifiedMediaState.Success(
                            media = media,
                            resume = resume,
                            selectedSource = selectBestSource(media.sources, resume),
                    )
            )
        } catch (e: Exception) {
            emit(UnifiedMediaState.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Find canonical media by source ID (reverse lookup).
     *
     * Given a pipeline-specific item, find its canonical entry.
     *
     * @param sourceId The pipeline source ID (e.g., "telegram:123:456")
     * @return Flow of media with sources
     */
    fun findBySourceId(sourceId: PipelineItemId): Flow<UnifiedMediaState> = flow {
        emit(UnifiedMediaState.Loading)

        try {
            val media = canonicalMediaRepository.findBySourceId(sourceId)
            if (media == null) {
                emit(UnifiedMediaState.NotFound)
                return@flow
            }

            val resume =
                    canonicalMediaRepository.getCanonicalResume(media.canonicalId, profileId = 0)

            emit(
                    UnifiedMediaState.Success(
                            media = media,
                            resume = resume,
                            selectedSource = selectBestSource(media.sources, resume),
                    )
            )
        } catch (e: Exception) {
            emit(UnifiedMediaState.Error(e.message ?: "Unknown error"))
        }
    }

    /** Search for canonical media by title. */
    suspend fun searchByTitle(
            query: String,
            kind: MediaKind? = null,
            limit: Int = 20,
    ): List<CanonicalMediaWithSources> = canonicalMediaRepository.search(query, kind, limit)

    /**
     * Save resume position for canonical media.
     *
     * Position applies to all sources of the same media.
     */
    suspend fun saveResume(
            canonicalId: CanonicalMediaId,
            positionMs: Long,
            durationMs: Long,
            sourceRef: MediaSourceRef,
            profileId: Long = 0,
    ) {
        canonicalMediaRepository.setCanonicalResume(
                canonicalId = canonicalId,
                profileId = profileId,
                positionMs = positionMs,
                durationMs = durationMs,
                sourceRef = sourceRef,
        )
    }

    /** Mark media as completed (watched to end). */
    suspend fun markCompleted(
            canonicalId: CanonicalMediaId,
            profileId: Long = 0,
    ) {
        canonicalMediaRepository.markCompleted(canonicalId, profileId)
    }

    /**
     * Select the best source for playback.
     *
     * Priority order:
     * 1. Last used source (if resume exists and source is available)
     * 2. Highest priority source
     * 3. Best quality source
     * 4. First available source
     */
    fun selectBestSource(
            sources: List<MediaSourceRef>,
            resume: CanonicalResumeInfo? = null,
    ): MediaSourceRef? {
        if (sources.isEmpty()) return null

        // Priority 1: Resume source if still available
        resume?.lastSourceId?.let { lastId ->
            sources.find { it.sourceId == lastId }?.let {
                return it
            }
        }

        // Priority 2: By priority value
        val byPriority = sources.maxByOrNull { it.priority }
        if (byPriority != null && byPriority.priority > 0) {
            return byPriority
        }

        // Priority 3: By quality (resolution)
        val byQuality =
                sources.filter { it.quality?.resolution != null }.maxByOrNull {
                    it.quality!!.resolution!!
                }

        if (byQuality != null) return byQuality

        // Priority 4: First source
        return sources.first()
    }

    /**
     * Sort sources for display in source picker.
     *
     * Groups by pipeline type, then sorts by quality within each group.
     */
    fun sortSourcesForDisplay(sources: List<MediaSourceRef>): List<SourceGroup> {
        val grouped = sources.groupBy { it.sourceType }

        return grouped
                .map { (type, sourcesOfType) ->
                    SourceGroup(
                            sourceType = type,
                            sources =
                                    sourcesOfType.sortedByDescending {
                                        it.quality?.resolution ?: 0
                                    },
                    )
                }
                .sortedBy { it.sourceType.ordinal }
    }

    /** Check if a higher quality version is available from another source. */
    fun findBetterQualitySource(
            currentSource: MediaSourceRef,
            allSources: List<MediaSourceRef>,
    ): MediaSourceRef? {
        val currentResolution = currentSource.quality?.resolution ?: 0

        return allSources
                .filter { it.sourceId != currentSource.sourceId }
                .filter { (it.quality?.resolution ?: 0) > currentResolution }
                .maxByOrNull { it.quality?.resolution ?: 0 }
    }

    /** Find sources with a specific language. */
    fun findSourcesWithLanguage(
            sources: List<MediaSourceRef>,
            language: String,
    ): List<MediaSourceRef> =
            sources.filter { source ->
                source.languages?.audioLanguages?.any { it.equals(language, ignoreCase = true) } ==
                        true
            }
}

/** State for unified detail screen. */
sealed class UnifiedMediaState {
    data object Loading : UnifiedMediaState()
    data object NotFound : UnifiedMediaState()
    data class Error(val message: String) : UnifiedMediaState()
    data class Success(
            val media: CanonicalMediaWithSources,
            val resume: CanonicalResumeInfo?,
            val selectedSource: MediaSourceRef?,
    ) : UnifiedMediaState()
}

/** Grouped sources by pipeline type. */
data class SourceGroup(
        val sourceType: SourceType,
        val sources: List<MediaSourceRef>,
) {
    val displayName: String
        get() =
                when (sourceType) {
                    SourceType.TELEGRAM -> "Telegram"
                    SourceType.XTREAM -> "Xtream"
                    SourceType.IO -> "Local Files"
                    SourceType.AUDIOBOOK -> "Audiobooks"
                    SourceType.PLEX -> "Plex"
                    SourceType.LOCAL -> "Local"
                    SourceType.OTHER -> "Other"
                    SourceType.UNKNOWN -> "Unknown"
                }
}
