package com.fishit.player.feature.detail

import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.MediaKind
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.ids.asCanonicalId
import com.fishit.player.core.model.repository.CanonicalMediaRepository
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.core.model.repository.CanonicalResumeInfo
import com.fishit.player.core.detail.domain.DetailEnrichmentService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use cases for unified detail screen with cross-pipeline source selection.
 *
 * Provides logic for:
 * - Loading canonical media with all sources
 * - Selecting the best source for playback
 * - Syncing resume across sources
 * - Version comparison and selection
 * - **On-demand enrichment** (Xtream get_vod_info or TMDB)
 *
 * ## ID Resolution Strategy
 *
 * The unified detail screen can receive two types of IDs:
 * 1. **CanonicalKey** (e.g., `movie:inception:2010`) - from Continue Watching, Recently Added
 * 2. **SourceId** (e.g., `msg:123:456` or `xtream:vod:123`) - from Telegram/Xtream rows
 *
 * The [loadBySmartId] method intelligently detects which type and routes appropriately.
 *
 * ## On-Demand Enrichment (Dec 2025)
 *
 * When detail page opens and plot is missing:
 * - **Xtream source available** → fetch via `get_vod_info` (rich provider data)
 * - **Telegram-only (no Xtream)** → enrich via TMDB API (if TMDB ID present)
 *
 * This ensures optimal metadata without redundant API calls.
 */
class UnifiedDetailUseCases
    @Inject
    constructor(
        private val canonicalMediaRepository: CanonicalMediaRepository,
        private val detailEnrichmentService: DetailEnrichmentService,
    ) {
        /**
         * Intelligently load canonical media by detecting the ID type.
         *
         * This method handles the unified detail screen's requirement to accept both:
         * - CanonicalKey from canonical rows (Continue Watching, Recently Added)
         * - SourceId from pipeline-specific rows (Telegram Media, Xtream VOD/Series)
         *
         * **Detection Heuristics (ordered by priority):**
         * 1. Starts with `tmdb:movie:` or `tmdb:tv:` → TMDB-based CanonicalKey (preferred)
         * 2. Starts with `movie:` or `episode:` → Fallback CanonicalKey
         * 3. Starts with `msg:`, `xtream:`, `io:` → SourceId (pipeline prefix)
         * 4. Otherwise → Try both lookups (canonicalKey first, then sourceId)
         *
         * **Contract Alignment (MEDIA_NORMALIZATION_CONTRACT.md):**
         * - TMDB IDs are the preferred canonical identity
         * - Fallback uses normalized title + year
         * - All lookups route through CanonicalMediaRepository
         *
         * **On-Demand Enrichment:**
         * After successful lookup, triggers enrichment if plot is missing.
         *
         * @param id Either a canonicalKey or sourceId string
         * @return Flow of media state
         */
        fun loadBySmartId(id: String): Flow<UnifiedMediaState> =
            flow {
                emit(UnifiedMediaState.Loading)

                try {
                    val media =
                        when {
                            // Priority 1: TMDB-based canonical key (Gold Decision Dec 2025)
                            // Format: tmdb:movie:{id} or tmdb:tv:{id}
                            id.startsWith("tmdb:movie:") -> {
                                val canonicalId =
                                    CanonicalMediaId(kind = MediaKind.MOVIE, key = id.asCanonicalId())
                                canonicalMediaRepository.findByCanonicalId(canonicalId)
                            }

                            id.startsWith("tmdb:tv:") -> {
                                // TV shows can be either series (MOVIE kind for show) or episode
                                // Try MOVIE first (series as a whole), then EPISODE if needed
                                val asMovieKey =
                                    CanonicalMediaId(kind = MediaKind.MOVIE, key = id.asCanonicalId())
                                val asEpisodeKey =
                                    CanonicalMediaId(kind = MediaKind.EPISODE, key = id.asCanonicalId())
                                canonicalMediaRepository.findByCanonicalId(asMovieKey)
                                    ?: canonicalMediaRepository.findByCanonicalId(asEpisodeKey)
                            }

                            // Priority 2: Fallback canonical key patterns
                            // Format: movie:slug:year, series:slug:year, or episode:slug:SxxExx
                            // NOTE: series:* uses MediaKind.MOVIE (it's a "work" not an episode)
                            id.startsWith("movie:") || id.startsWith("series:") || id.startsWith("episode:") -> {
                                val canonicalId =
                                    CanonicalMediaId(
                                        kind =
                                            if (id.startsWith("episode:")) {
                                                MediaKind.EPISODE
                                            } else {
                                                // Both movie:* and series:* use MOVIE kind
                                                MediaKind.MOVIE
                                            },
                                        key = id.asCanonicalId(),
                                    )
                                canonicalMediaRepository.findByCanonicalId(canonicalId)
                            }

                            // Priority 3: Pipeline source ID patterns
                            // Format: msg:chatId:msgId, xtream:vod:id, io:path, audiobook:id
                            id.startsWith("msg:") ||
                                id.startsWith("xtream:") ||
                                id.startsWith("io:") ||
                                id.startsWith("audiobook:") -> {
                                canonicalMediaRepository.findBySourceId(PipelineItemId(id))
                            }

                            // Priority 4: Ambiguous ID - try all lookup strategies
                            else -> {
                                // Try as canonical key for both movie and episode
                                val asMovieKey =
                                    CanonicalMediaId(
                                        kind = MediaKind.MOVIE,
                                        key = id.asCanonicalId(),
                                    )
                                val asEpisodeKey =
                                    CanonicalMediaId(
                                        kind = MediaKind.EPISODE,
                                        key = id.asCanonicalId(),
                                    )

                                canonicalMediaRepository.findByCanonicalId(asMovieKey)
                                    ?: canonicalMediaRepository.findByCanonicalId(asEpisodeKey)
                                    ?: canonicalMediaRepository.findBySourceId(
                                        PipelineItemId(id),
                                    )
                            }
                        }

                    if (media == null) {
                        emit(UnifiedMediaState.NotFound)
                        return@flow
                    }

                    // On-demand enrichment: fetch rich metadata if plot is missing
                    // Priority: Xtream get_vod_info → TMDB (only if no Xtream source)
                    val enrichedMedia = detailEnrichmentService.enrichIfNeeded(media)

                    val resume =
                        canonicalMediaRepository.getCanonicalResume(enrichedMedia.canonicalId, profileId = 0)

                    emit(
                        UnifiedMediaState.Success(
                            media = enrichedMedia,
                            resume = resume,
                        ),
                    )
                } catch (e: Exception) {
                    emit(UnifiedMediaState.Error(e.message ?: "Unknown error"))
                }
            }

        /**
         * Load canonical media with all available sources.
         *
         * @param canonicalId The canonical media ID
         * @return Flow of media with sources and resume info
         */
        fun loadCanonicalMedia(canonicalId: CanonicalMediaId): Flow<UnifiedMediaState> =
            flow {
                emit(UnifiedMediaState.Loading)

                try {
                    val media = canonicalMediaRepository.findByCanonicalId(canonicalId)
                    if (media == null) {
                        emit(UnifiedMediaState.NotFound)
                        return@flow
                    }

                    // On-demand enrichment: fetch rich metadata if plot is missing
                    val enrichedMedia = detailEnrichmentService.enrichIfNeeded(media)

                    // Get resume info for current profile (placeholder profile ID = 0)
                    val resume = canonicalMediaRepository.getCanonicalResume(enrichedMedia.canonicalId, profileId = 0)

                    // Note: selectedSource is NOT set here. Selection is derived via
                    // SourceSelection.resolveActiveSource() at moment of use.
                    emit(
                        UnifiedMediaState.Success(
                            media = enrichedMedia,
                            resume = resume,
                        ),
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
        fun findBySourceId(sourceId: PipelineItemId): Flow<UnifiedMediaState> =
            flow {
                emit(UnifiedMediaState.Loading)

                try {
                    val media = canonicalMediaRepository.findBySourceId(sourceId)
                    if (media == null) {
                        emit(UnifiedMediaState.NotFound)
                        return@flow
                    }

                    // On-demand enrichment: fetch rich metadata if plot is missing
                    val enrichedMedia = detailEnrichmentService.enrichIfNeeded(media)

                    val resume =
                        canonicalMediaRepository.getCanonicalResume(enrichedMedia.canonicalId, profileId = 0)

                    // Note: selectedSource is NOT set here. Selection is derived via
                    // SourceSelection.resolveActiveSource() at moment of use.
                    emit(
                        UnifiedMediaState.Success(
                            media = enrichedMedia,
                            resume = resume,
                        ),
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
                }.sortedBy { it.sourceType.ordinal }
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

    data class Error(
        val message: String,
    ) : UnifiedMediaState()

    /**
     * Success state with canonical media and resume info.
     *
     * **Note (Dec 2025):** This no longer includes `selectedSource`.
     * Source selection is now DERIVED from `media.sources` via [SourceSelection.resolveActiveSource]
     * at the moment of use (in ViewModel and State).
     */
    data class Success(
        val media: CanonicalMediaWithSources,
        val resume: CanonicalResumeInfo?,
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
