package com.fishit.player.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.detail.domain.DetailBundle
import com.fishit.player.core.detail.domain.EpisodeIndexItem
import com.fishit.player.core.detail.domain.UnifiedDetailLoader
import com.fishit.player.core.model.CanonicalMediaId
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.model.MediaKind
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.ids.CanonicalId
import com.fishit.player.core.model.ids.PipelineItemId
import com.fishit.player.core.model.repository.CanonicalMediaWithSources
import com.fishit.player.core.model.repository.CanonicalResumeInfo
import com.fishit.player.core.detail.domain.DetailEnrichmentService
import com.fishit.player.feature.detail.series.EnsureEpisodePlaybackReadyUseCase
import com.fishit.player.feature.detail.ui.helper.DetailEpisodeItem
import com.fishit.player.infra.logging.UnifiedLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for unified detail screen with cross-pipeline source selection.
 *
 * **Architecture (Dec 2025 - selectedSource Elimination):**
 *
 * This ViewModel does NOT store a `selectedSource: MediaSourceRef` snapshot.
 * Instead, source selection is ALWAYS derived from `state.media.sources` at the
 * moment of use via [SourceSelection.resolveActiveSource].
 *
 * **Why:**
 * - Eliminates race conditions where async enrichment updates `media.sources` but
 *   stale `selectedSource` retains outdated playbackHints (e.g., missing containerExtension).
 * - Single Source of Truth: `media.sources` is the SSOT for all source data.
 *
 * **Selection Model:**
 * - `selectedSourceKey: PipelineItemId?` - Optional stable key for explicit user selection.
 * - If null, [SourceSelection.resolveActiveSource] picks the best source automatically.
 * - UI calls [resolveActiveSource] to derive the current source for display/playback.
 *
 * **Playback Flow (Race-Proof):**
 * 1. User clicks Play → [play] is called
 * 2. Resolve activeSource from CURRENT state.media
 * 3. Check if source is playback-ready ([SourceSelection.isPlaybackReady])
 * 4. If missing hints → await [DetailEnrichmentService.ensureEnriched] with timeout
 * 5. Re-resolve activeSource from refreshed media (SSOT)
 * 6. Build PlaybackContext and emit StartPlayback event
 */
@HiltViewModel
class UnifiedDetailViewModel
    @Inject
    constructor(
        private val useCases: UnifiedDetailUseCases,
        private val detailEnrichmentService: DetailEnrichmentService,
        private val unifiedDetailLoader: UnifiedDetailLoader,
        private val ensureEpisodePlaybackReadyUseCase: EnsureEpisodePlaybackReadyUseCase,
    ) : ViewModel() {
        companion object {
            private const val TAG = "UnifiedDetailVM"

            // Retry/Timeout constants
            private const val ENRICHMENT_RETRY_DELAY_MS = 500L
            private const val ENRICHMENT_MAX_RETRIES = 3
        }

        private val _state = MutableStateFlow(UnifiedDetailState())
        val state: StateFlow<UnifiedDetailState> = _state.asStateFlow()

        private val _events = MutableSharedFlow<UnifiedDetailEvent>()
        val events = _events.asSharedFlow()

        /**
         * Load canonical media by smart ID detection.
         *
         * This is the PRIMARY method for loading media in the unified detail screen. It intelligently
         * detects whether the provided ID is:
         * - A canonical key (e.g., `movie:inception:2010`)
         * - A source ID (e.g., `msg:123:456`, `xtream:vod:123`)
         *
         * And routes to the appropriate lookup method.
         *
         * @param mediaId Either a canonical key or source ID string
         */
        fun loadByMediaId(mediaId: String) {
            viewModelScope.launch {
                useCases.loadBySmartId(mediaId).collect { mediaState -> handleMediaState(mediaState) }
            }
        }

        /** Load canonical media by canonical ID. */
        fun loadByCanonicalId(canonicalId: CanonicalMediaId) {
            viewModelScope.launch {
                useCases.loadCanonicalMedia(canonicalId).collect { mediaState ->
                    handleMediaState(mediaState)
                }
            }
        }

        /**
         * Load canonical media by source ID (reverse lookup).
         *
         * Use when navigating from a pipeline-specific item to unified detail.
         */
        fun loadBySourceId(sourceId: PipelineItemId) {
            viewModelScope.launch {
                useCases.findBySourceId(sourceId).collect { mediaState -> handleMediaState(mediaState) }
            }
        }

        /** Handle media state update from any load method. */
        private fun handleMediaState(mediaState: UnifiedMediaState) {
            when (mediaState) {
                is UnifiedMediaState.Loading -> {
                    _state.update { it.copy(isLoading = true, error = null) }
                }
                is UnifiedMediaState.NotFound -> {
                    _state.update { it.copy(isLoading = false, error = "Media not found") }
                }
                is UnifiedMediaState.Error -> {
                    _state.update { it.copy(isLoading = false, error = mediaState.message) }
                }
                is UnifiedMediaState.Success -> {
                    // Initial state update (fast path - show data immediately)
                    // Note: We do NOT set selectedSource. Selection is derived via resolveActiveSource().
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            media = mediaState.media,
                            resume = mediaState.resume,
                            // selectedSourceKey is preserved if user manually selected before
                            sourceGroups = useCases.sortSourcesForDisplay(mediaState.media.sources),
                        )
                    }

                    // PLATIN: Use UnifiedDetailLoader for ONE API call that fetches EVERYTHING
                    // This replaces separate enrichment + series loading calls
                    viewModelScope.launch {
                        loadDetailWithUnifiedLoader(mediaState.media)
                    }
                }
            }
        }

        /**
         * 🏆 PLATIN Implementation: Load all detail data with ONE API call.
         *
         * For Series: metadata + seasons + ALL episodes in single call
         * For VOD: metadata + playback hints in single call
         *
         * This eliminates the 3x API call problem where getSeriesInfo was called
         * separately by enrichment, season loading, and episode loading.
         *
         * **Race-Proof Design:**
         * - UnifiedDetailLoader ALWAYS returns a bundle for Xtream sources
         * - Fresh-but-empty cache triggers API call (not null return)
         * - No legacy fallback for Xtream - prevents take(1) race condition
         */
        private suspend fun loadDetailWithUnifiedLoader(media: CanonicalMediaWithSources) {
            val startMs = System.currentTimeMillis()

            try {
                // Single API call via UnifiedDetailLoader (PLATIN)
                val bundle = unifiedDetailLoader.loadDetailImmediate(media)

                if (bundle == null) {
                    // PLATIN: Only null for NON-Xtream sources (e.g., Telegram)
                    // OR when Xtream API returns null (network error, invalid ID, etc.)
                    val hasXtreamSource = media.sources.any { it.sourceType == SourceType.XTREAM }
                    if (hasXtreamSource) {
                        // Xtream source but bundle null - API call failed or returned null
                        // This is NOT a critical error - the basic data is already in state.media
                        // Just log it and continue with what we have
                        UnifiedLog.w(TAG) { 
                            "loadDetailWithUnifiedLoader: Xtream API returned null for ${media.mediaType}" +
                                " (canonicalId=${media.canonicalId.key.value}). Using cached data."
                        }
                        // DON'T show error to user - we already have basic media data
                        // The detail screen can work without enrichment (just won't have plot/cast)
                    } else {
                        // Non-Xtream: Enrichment only (no seasons/episodes)
                        UnifiedLog.d(TAG) { "loadDetailWithUnifiedLoader: non-Xtream source, enrichment only" }
                        val enriched = detailEnrichmentService.enrichImmediate(media)
                        if (enriched !== media) {
                            _state.update { currentState ->
                                currentState.copy(
                                    media = enriched,
                                    sourceGroups = useCases.sortSourcesForDisplay(enriched.sources),
                                )
                            }
                        }
                    }
                    return
                }

                // Apply bundle data based on type
                when (bundle) {
                    is DetailBundle.Series -> applySeriesBundle(bundle, media)
                    is DetailBundle.Vod -> applyVodBundle(bundle, media)
                    is DetailBundle.Live -> applyLiveBundle(bundle, media)
                }

                val durationMs = System.currentTimeMillis() - startMs
                UnifiedLog.i(TAG) {
                    "loadDetailWithUnifiedLoader: completed in ${durationMs}ms " +
                        "type=${bundle::class.simpleName}"
                }
            } catch (e: Exception) {
                UnifiedLog.e(TAG, e) { "loadDetailWithUnifiedLoader: failed" }
                _state.update { it.copy(error = "Details konnten nicht geladen werden: ${e.message}") }
            }
        }

        /**
         * Apply Series bundle to state - seasons and episodes are already loaded.
         */
        private suspend fun applySeriesBundle(
            bundle: DetailBundle.Series,
            media: CanonicalMediaWithSources,
        ) {
            // Update media if enrichment data available
            if (bundle.plot != null || bundle.poster != null || bundle.backdrop != null) {
                val enrichedMedia = media.copy(
                    plot = bundle.plot ?: media.plot,
                    poster = bundle.poster ?: media.poster,
                    backdrop = bundle.backdrop ?: media.backdrop,
                    rating = bundle.rating ?: media.rating,
                    trailer = bundle.trailer ?: media.trailer,
                    imdbId = bundle.imdbId ?: media.imdbId,
                )
                _state.update { currentState ->
                    currentState.copy(
                        media = enrichedMedia,
                        sourceGroups = useCases.sortSourcesForDisplay(enrichedMedia.sources),
                    )
                }
            }

            // Update seasons and episodes from bundle
            val seasonNumbers = bundle.seasons.map { it.seasonNumber }.sorted()
            val firstSeason = seasonNumbers.firstOrNull() ?: 1

            _state.update { currentState ->
                currentState.copy(
                    seasons = seasonNumbers,
                    selectedSeason = currentState.selectedSeason ?: firstSeason,
                )
            }

            // Convert bundle episodes to UI items
            val selectedSeason = _state.value.selectedSeason ?: firstSeason
            val episodes = bundle.episodesBySeason[selectedSeason] ?: emptyList()
            val episodeItems = mapToDetailEpisodeItems(episodes, bundle.seriesId, selectedSeason)

            _state.update { currentState ->
                currentState.copy(episodes = episodeItems)
            }

            UnifiedLog.d(TAG) {
                "applySeriesBundle: ${seasonNumbers.size} seasons, " +
                    "${bundle.totalEpisodeCount} total episodes, " +
                    "${episodeItems.size} episodes for season $selectedSeason"
            }
        }

        /**
         * Apply VOD bundle to state - playback hints already included.
         */
        private fun applyVodBundle(
            bundle: DetailBundle.Vod,
            media: CanonicalMediaWithSources,
        ) {
            // Update media with enrichment data
            if (bundle.plot != null || bundle.poster != null || bundle.backdrop != null) {
                val enrichedMedia = media.copy(
                    plot = bundle.plot ?: media.plot,
                    poster = bundle.poster ?: media.poster,
                    backdrop = bundle.backdrop ?: media.backdrop,
                    rating = bundle.rating ?: media.rating,
                    trailer = bundle.trailer ?: media.trailer,
                    imdbId = bundle.imdbId ?: media.imdbId,
                )
                _state.update { currentState ->
                    currentState.copy(
                        media = enrichedMedia,
                        sourceGroups = useCases.sortSourcesForDisplay(enrichedMedia.sources),
                    )
                }
            }

            // Note: Playback hints (containerExtension, directUrl) are already persisted
            // in the source by UnifiedDetailLoader, so no additional action needed
            UnifiedLog.d(TAG) {
                "applyVodBundle: vodId=${bundle.vodId} containerExtension=${bundle.containerExtension}"
            }
        }

        /**
         * Apply Live bundle to state (minimal - Live doesn't need much enrichment).
         */
        private fun applyLiveBundle(
            bundle: DetailBundle.Live,
            media: CanonicalMediaWithSources,
        ) {
            // Live channels typically don't have plot/backdrop, just EPG
            UnifiedLog.d(TAG) { "applyLiveBundle: channelId=${bundle.channelId}" }
        }

        // ==========================================================================
        // Source Selection (Derived, NOT Stored)
        // ==========================================================================

        /**
         * Get the currently active source (DERIVED from state.media.sources).
         *
         * This should be called whenever the UI needs to display or use the selected source.
         * It always returns the most up-to-date source from media.sources.
         */
        fun resolveActiveSource(): MediaSourceRef? {
            val currentState = _state.value
            return SourceSelection.resolveActiveSource(
                media = currentState.media,
                selectedSourceKey = currentState.selectedSourceKey,
                resume = currentState.resume,
            )
        }

        /**
         * Manually select a source for playback.
         *
         * This stores only the stable sourceKey (PipelineItemId), NOT the full MediaSourceRef.
         * The actual source data is always derived from state.media.sources.
         *
         * @param source The source to select (only its sourceId is stored)
         */
        fun selectSource(source: MediaSourceRef) {
            _state.update { it.copy(selectedSourceKey = source.sourceId) }
        }

        /**
         * Select a source by key (without needing the full MediaSourceRef).
         *
         * @param sourceKey The PipelineItemId of the source to select
         */
        fun selectSourceByKey(sourceKey: PipelineItemId) {
            _state.update { it.copy(selectedSourceKey = sourceKey) }
        }

        /**
         * Clear manual source selection (revert to auto-selection).
         */
        fun clearSourceSelection() {
            _state.update { it.copy(selectedSourceKey = null) }
        }

        // ==========================================================================
        // Playback (Race-Proof)
        // ==========================================================================

        /**
         * Start playback with the active source.
         *
         * **Race-Proof Implementation:**
         * 1. Resolve activeSource from CURRENT state.media
         * 2. Check if all required playbackHints are present
         * 3. If missing → await ensureEnriched with timeout
         * 4. Re-resolve activeSource from refreshed media (SSOT)
         * 5. Only then emit StartPlayback event
         */
        fun play() {
            val currentState = _state.value
            val media = currentState.media ?: return
            val activeSource = resolveActiveSource() ?: return

            viewModelScope.launch {
                playWithSource(
                    media = media,
                    sourceKey = activeSource.sourceId,
                    resumePositionMs = currentState.resume?.positionMs ?: 0,
                )
            }
        }

        /** Start playback from the beginning (ignoring resume). */
        fun playFromStart() {
            val currentState = _state.value
            val media = currentState.media ?: return
            val activeSource = resolveActiveSource() ?: return

            viewModelScope.launch {
                playWithSource(
                    media = media,
                    sourceKey = activeSource.sourceId,
                    resumePositionMs = 0,
                )
            }
        }

        /** Resume playback at stored position. */
        fun resume() {
            val currentState = _state.value
            val resume = currentState.resume ?: return
            val media = currentState.media ?: return
            val activeSource = resolveActiveSource() ?: return

            viewModelScope.launch {
                // Calculate resume position for this specific source
                // IMPORTANT: Different sources have different durations!
                val sourceDuration = activeSource.durationMs ?: resume.durationMs
                val resumePosition = resume.calculatePositionForSource(activeSource.sourceId, sourceDuration)

                playWithSource(
                    media = media,
                    sourceKey = activeSource.sourceId,
                    resumePositionMs = resumePosition.positionMs,
                    isExactPosition = resumePosition.isExact,
                    approximationNote = resumePosition.note,
                )
            }
        }

        /**
         * Internal: Start playback with a specific source (race-proof).
         *
         * Ensures playbackHints are present before starting playback.
         */
        private suspend fun playWithSource(
            media: CanonicalMediaWithSources,
            sourceKey: PipelineItemId,
            resumePositionMs: Long,
            isExactPosition: Boolean = true,
            approximationNote: String? = null,
        ) {
            // Step 1: Resolve initial source
            var source = media.sources.find { it.sourceId == sourceKey }
            if (source == null) {
                UnifiedLog.w(TAG) { "play: source not found key=${sourceKey.value}" }
                _events.emit(UnifiedDetailEvent.ShowError("Source not available"))
                return
            }

            // Step 2: Check if playback-ready
            val missingHints = SourceSelection.getMissingPlaybackHints(source)
            UnifiedLog.d(TAG) {
                "play: canonicalId=${media.canonicalId.key.value} sourceKey=${sourceKey.value} missingHints=$missingHints"
            }

            // Step 3: If missing hints, await enrichment
            if (missingHints.isNotEmpty()) {
                UnifiedLog.i(TAG) { "play: awaiting enrichment for hints=$missingHints" }

                val refreshedMedia =
                    detailEnrichmentService.ensureEnriched(
                        canonicalId = media.canonicalId,
                        sourceKey = sourceKey,
                        requiredHints = missingHints,
                    )

                if (refreshedMedia != null) {
                    // Update state with refreshed media
                    _state.update {
                        it.copy(
                            media = refreshedMedia,
                            sourceGroups = useCases.sortSourcesForDisplay(refreshedMedia.sources),
                        )
                    }

                    // Re-resolve source from refreshed media (SSOT)
                    source = refreshedMedia.sources.find { it.sourceId == sourceKey }
                    if (source == null) {
                        UnifiedLog.e(TAG) { "play: source disappeared after enrichment key=${sourceKey.value}" }
                        _events.emit(UnifiedDetailEvent.ShowError("Source no longer available"))
                        return
                    }

                    // Re-check hints
                    val stillMissing = SourceSelection.getMissingPlaybackHints(source)
                    if (stillMissing.isNotEmpty()) {
                        UnifiedLog.w(TAG) { "play: hints still missing after enrichment: $stillMissing" }
                        // Continue anyway - playback may still work with fallbacks
                    }
                } else {
                    UnifiedLog.w(TAG) { "play: enrichment failed, proceeding with partial hints" }
                    // Continue anyway - playback factory has fallbacks
                }
            }

            // Step 4: Emit playback event with current source
            _events.emit(
                UnifiedDetailEvent.StartPlayback(
                    canonicalId = media.canonicalId,
                    source = source,
                    resumePositionMs = resumePositionMs,
                    isExactPosition = isExactPosition,
                    approximationNote = approximationNote,
                ),
            )
        }

        // ==========================================================================
        // Resume Calculation
        // ==========================================================================

        /**
         * Get the calculated resume position for a specific source.
         *
         * IMPORTANT: Different sources of the same movie have different durations. This method
         * calculates the appropriate resume position based on percentage.
         *
         * @param source The source to calculate resume for
         * @return Calculated position with exact/approximated flag
         */
        fun getResumePositionForSource(source: MediaSourceRef): ResumeCalculation? {
            val resume = _state.value.resume ?: return null

            val sourceDuration = source.durationMs ?: return null
            val position = resume.calculatePositionForSource(source.sourceId, sourceDuration)

            return ResumeCalculation(
                sourceId = source.sourceId,
                positionMs = position.positionMs,
                durationMs = sourceDuration,
                isExact = position.isExact,
                approximationNote = position.note,
                wasLastPlayed = source.sourceId == resume.lastSourceId,
            )
        }

        // ==========================================================================
        // UI Helpers
        // ==========================================================================

        /** Open source picker dialog. */
        fun showSourcePicker() {
            _state.update { it.copy(showSourcePicker = true) }
        }

        /** Close source picker dialog. */
        fun hideSourcePicker() {
            _state.update { it.copy(showSourcePicker = false) }
        }

        /** Open trailer in YouTube or WebView. */
        fun openTrailer() {
            val trailer = _state.value.media?.trailer?.takeIf { it.isNotBlank() } ?: return
            viewModelScope.launch {
                _events.emit(UnifiedDetailEvent.OpenTrailer(trailer))
            }
        }

        /** Clear resume position. */
        fun clearResume() {
            val media = _state.value.media ?: return

            viewModelScope.launch {
                useCases.markCompleted(media.canonicalId)
                _state.update { it.copy(resume = null) }
            }
        }

        /** Check for better quality versions. */
        fun checkForBetterQuality(): MediaSourceRef? {
            val activeSource = resolveActiveSource() ?: return null
            val media = _state.value.media ?: return null

            return useCases.findBetterQualitySource(activeSource, media.sources)
        }

        /** Filter sources by language. */
        fun filterByLanguage(language: String): List<MediaSourceRef> {
            val media = _state.value.media ?: return emptyList()
            return useCases.findSourcesWithLanguage(media.sources, language)
        }

        // ==========================================================================
        // Series-specific methods (PLATIN - no legacy race conditions)
        // ==========================================================================

        /**
         * Extract Xtream series ID from canonical media ID.
         *
         * BUG FIX: Series canonical IDs may not contain numeric IDs (e.g., "series:title:unknown")
         * So we MUST extract from Xtream source IDs first, not from canonical key.
         *
         * Format: "xtream:series:12345"
         */
        private fun extractSeriesId(canonicalId: CanonicalMediaId): Int? {
            val key = canonicalId.key.value
            val media = _state.value.media

            // PRIORITY 1: Extract from Xtream source ID (most reliable)
            if (media != null) {
                // Try to extract from first Xtream source
                val xtreamSource = media.sources.find { it.sourceId.value.startsWith("xtream:") }
                if (xtreamSource != null) {
                    // Parse source ID formats:
                    // - Series: "xtream:series:12345"
                    // - Episode: "xtream:series:12345:episode:54321"
                    val parts = xtreamSource.sourceId.value.split(":")
                    val seriesId = when {
                        parts.size == 3 && parts[1] == "series" -> parts[2].toIntOrNull()
                        parts.size >= 5 && parts[1] == "series" && parts[3] == "episode" -> parts[2].toIntOrNull()
                        else -> null
                    }
                    if (seriesId != null) return seriesId
                }
            }

            // PRIORITY 2: Try to parse from canonical key if it follows Xtream pattern
            // NX format: src:xtream:<account>:series:<id> or legacy: xtream:series:<id>
            if (key.contains(":series:")) {
                // Extract series ID from pattern: ...series:<id>...
                val seriesIndex = key.indexOf(":series:")
                if (seriesIndex >= 0) {
                    val afterSeries = key.substring(seriesIndex + ":series:".length)
                    val potentialId = afterSeries.split(":").firstOrNull()?.toIntOrNull()
                    if (potentialId != null) return potentialId
                }
            }

            // PRIORITY 3: Canonical key format "series:<slug>:<year>" - no numeric ID
            // This is normal for normalized series without Xtream source
            // Log this case for debugging but don't spam warnings
            if (key.startsWith("series:") && !key.contains(":series:")) {
                UnifiedLog.d(TAG) { "Series canonical ID has no numeric ID: $key (expected when no Xtream source exists)" }
            }

            return null
        }

        /**
         * Convert EpisodeIndexItem to DetailEpisodeItem.
         */
        private fun EpisodeIndexItem.toDetailEpisodeItem(): DetailEpisodeItem =
            DetailEpisodeItem(
                id = sourceKey,
                canonicalId =
                    CanonicalMediaId(
                        kind = MediaKind.EPISODE,
                        key = CanonicalId(sourceKey),
                    ),
                season = seasonNumber,
                episode = episodeNumber,
                title = title ?: "Episode $episodeNumber",
                thumbnail =
                    thumbUrl
                        ?.takeIf { url ->
                            url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))
                        }?.let { ImageRef.Http(it) },
                durationMs = durationSecs?.toLong()?.times(1000),
                plot = plot,
                rating = rating,
                airDate = airDate,
                qualityHeight = videoHeight,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                audioChannels = audioChannels,
                sources = emptyList(), // Will be resolved on playback
                hasResume = false, // TODO: load from ResumeRepository
                resumePercent = 0,
            )

        /**
         * Map a list of EpisodeIndexItems to DetailEpisodeItems.
         *
         * Helper for PLATIN unified loader which provides episodes directly.
         */
        private fun mapToDetailEpisodeItems(
            episodes: List<EpisodeIndexItem>,
            seriesId: Int,
            seasonNumber: Int,
        ): List<DetailEpisodeItem> = episodes.map { it.toDetailEpisodeItem() }

        /**
         * 🏆 PLATIN: Select a season for episode display.
         *
         * **Race-Proof Design:**
         * - Episodes are already loaded in memory via applySeriesBundle()
         * - No API call needed - just filter from existing bundle data
         * - If bundle not yet loaded, trigger a PLATIN load
         */
        fun selectSeason(season: Int) {
            val media = _state.value.media ?: return

            _state.update { it.copy(selectedSeason = season, episodesLoading = true) }

            viewModelScope.launch {
                // PLATIN: Get episodes from UnifiedDetailLoader (already cached from initial load)
                val bundle = unifiedDetailLoader.loadDetailImmediate(media)
                
                when (bundle) {
                    is DetailBundle.Series -> {
                        val episodes = bundle.episodesBySeason[season] ?: emptyList()
                        val episodeItems = mapToDetailEpisodeItems(episodes, bundle.seriesId, season)
                        
                        _state.update { currentState ->
                            currentState.copy(
                                episodes = episodeItems,
                                episodesLoading = false,
                            )
                        }
                        
                        UnifiedLog.d(TAG) { "selectSeason: Loaded ${episodeItems.size} episodes for season $season (PLATIN)" }
                    }
                    else -> {
                        UnifiedLog.w(TAG) { "selectSeason: Expected Series bundle but got ${bundle?.javaClass?.simpleName}" }
                        _state.update { it.copy(episodesLoading = false) }
                    }
                }
            }
        }

        /** Play a specific episode. */
        fun playEpisode(episode: DetailEpisodeItem) {
            viewModelScope.launch {
                try {
                    UnifiedLog.d(TAG) { "Starting episode playback: ${episode.id}" }

                    // Ensure episode has playback hints (stream_id, container_extension)
                    val ensureResult = ensureEpisodePlaybackReadyUseCase.invoke(episode.id)

                    when (ensureResult) {
                        is EnsureEpisodePlaybackReadyUseCase.Result.Ready -> {
                            playEpisodeWithHints(episode, ensureResult.hints)
                        }
                        is EnsureEpisodePlaybackReadyUseCase.Result.Enriching -> {
                            UnifiedLog.i(TAG) { "Episode enrichment in progress: ${episode.id}, waiting..." }

                            // Show loading state
                            _state.update { it.copy(episodesLoading = true) }

                            // Exponential backoff retry
                            var retryCount = 0
                            var retryResult: EnsureEpisodePlaybackReadyUseCase.Result? = null

                            while (retryCount < ENRICHMENT_MAX_RETRIES) {
                                delay(ENRICHMENT_RETRY_DELAY_MS * (retryCount + 1))
                                retryResult = ensureEpisodePlaybackReadyUseCase.invoke(episode.id)

                                if (retryResult is EnsureEpisodePlaybackReadyUseCase.Result.Ready) {
                                    break
                                }
                                retryCount++
                                UnifiedLog.d(TAG) { "Episode enrichment retry $retryCount/$ENRICHMENT_MAX_RETRIES: ${episode.id}" }
                            }

                            _state.update { it.copy(episodesLoading = false) }

                            if (retryResult is EnsureEpisodePlaybackReadyUseCase.Result.Ready) {
                                playEpisodeWithHints(episode, retryResult.hints)
                            } else {
                                UnifiedLog.w(TAG) { "Episode enrichment timeout after $ENRICHMENT_MAX_RETRIES retries: ${episode.id}" }
                                _events.emit(UnifiedDetailEvent.ShowError("Episode wird vorbereitet, bitte später erneut versuchen"))
                            }
                        }
                        is EnsureEpisodePlaybackReadyUseCase.Result.Failed -> {
                            UnifiedLog.e(TAG) { "Episode not ready: ${ensureResult.reason}" }
                            _events.emit(UnifiedDetailEvent.ShowError("Episode nicht verfügbar: ${ensureResult.reason}"))
                        }
                    }
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, e) { "Playback failed for episode ${episode.id}" }
                    _events.emit(UnifiedDetailEvent.ShowError("Wiedergabe fehlgeschlagen"))
                }
            }
        }

        /**
         * Internal: Start episode playback with enriched hints.
         */
        private suspend fun playEpisodeWithHints(
            episode: DetailEpisodeItem,
            hints: com.fishit.player.core.detail.domain.EpisodePlaybackHints,
        ) {
            val episodeStreamId =
                hints.streamId
                    ?: throw IllegalStateException("Episode missing streamId: ${episode.id}")

            val containerExt = hints.containerExtension
            if (containerExt == null) {
                UnifiedLog.w(TAG) {
                    "Episode ${episode.id} missing containerExtension from API, falling back to 'mkv'. " +
                        "This may cause playback issues if the actual format is different."
                }
            }
            val finalContainerExt = containerExt ?: "mkv"

            val media = _state.value.media
            val seriesId = if (media != null) extractSeriesId(media.canonicalId) else null

            UnifiedLog.d(TAG) {
                "Episode playback ready [series=$seriesId, season=${episode.season}, episode=${episode.episode}, " +
                    "episodeId=${episode.id}, streamId=$episodeStreamId, containerExt=$finalContainerExt]"
            }

            val source =
                MediaSourceRef(
                    sourceType = SourceType.XTREAM,
                    sourceId = PipelineItemId(episode.id),
                    sourceLabel = "${episode.title} (S${episode.season}E${episode.episode})",
                    durationMs = episode.durationMs,
                    playbackHints =
                        buildMap {
                            put(PlaybackHintKeys.Xtream.CONTENT_TYPE, "series")
                            put(PlaybackHintKeys.Xtream.EPISODE_ID, episodeStreamId.toString())
                            put(PlaybackHintKeys.Xtream.CONTAINER_EXT, finalContainerExt)
                            put(PlaybackHintKeys.Xtream.SEASON_NUMBER, episode.season.toString())
                            put(PlaybackHintKeys.Xtream.EPISODE_NUMBER, episode.episode.toString())
                            if (seriesId != null) {
                                put(PlaybackHintKeys.Xtream.SERIES_ID, seriesId.toString())
                            }
                        },
                )

            _events.emit(
                UnifiedDetailEvent.StartPlayback(
                    canonicalId = episode.canonicalId,
                    source = source,
                    resumePositionMs = _state.value.episodeResumes[episode.id]?.positionMs ?: 0,
                ),
            )
        }

        // ==========================================================================
        // Live-specific methods
        // ==========================================================================

        /** Start live stream playback (no resume for live content). */
        fun playLive() {
            val currentState = _state.value
            val media = currentState.media ?: return
            val activeSource = resolveActiveSource() ?: return

            viewModelScope.launch {
                // Live streams typically don't need enrichment wait - play immediately
                _events.emit(
                    UnifiedDetailEvent.StartPlayback(
                        canonicalId = media.canonicalId,
                        source = activeSource,
                        resumePositionMs = 0, // Live always starts "now"
                    ),
                )
            }
        }
    }

/**
 * State for unified detail screen.
 *
 * **IMPORTANT (Dec 2025):** This state does NOT contain `selectedSource: MediaSourceRef`.
 * Source selection is DERIVED from `media.sources` via [SourceSelection.resolveActiveSource].
 *
 * @property selectedSourceKey Optional stable key for manual source selection.
 *   If null, best source is auto-selected. The full MediaSourceRef is always
 *   derived from media.sources at the moment of use.
 */
data class UnifiedDetailState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val media: CanonicalMediaWithSources? = null,
    val resume: CanonicalResumeInfo? = null,
    val selectedSourceKey: PipelineItemId? = null, // Key only, NOT MediaSourceRef!
    val sourceGroups: List<SourceGroup> = emptyList(),
    val showSourcePicker: Boolean = false,
    // Series-specific state
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int? = null,
    val episodes: List<DetailEpisodeItem> = emptyList(),
    val episodesLoading: Boolean = false,
    val episodeResumes: Map<String, CanonicalResumeInfo> = emptyMap(),
    // Live-specific state
    val liveNowPlaying: LiveProgramInfo? = null,
) {
    /** Effective media type for UI rendering */
    val effectiveMediaType: MediaType
        get() = media?.mediaType ?: MediaType.UNKNOWN

    /** Whether this is a series with episodes */
    val isSeries: Boolean
        get() = effectiveMediaType == MediaType.SERIES

    /** Whether this is a series episode */
    val isSeriesEpisode: Boolean
        get() = effectiveMediaType == MediaType.SERIES_EPISODE

    /** Whether this is live content */
    val isLive: Boolean
        get() = effectiveMediaType == MediaType.LIVE

    /** Whether this is audio content (audiobook, podcast, music) */
    val isAudio: Boolean
        get() = effectiveMediaType in listOf(MediaType.AUDIOBOOK, MediaType.PODCAST, MediaType.MUSIC)

    /** Episodes for the currently selected season */
    val displayedEpisodes: List<DetailEpisodeItem>
        get() =
            if (selectedSeason != null) {
                episodes.filter { it.season == selectedSeason }
            } else {
                episodes
            }

    /** Whether media has multiple sources */
    val hasMultipleSources: Boolean
        get() = (media?.sources?.size ?: 0) > 1

    /** Available source types for badge display */
    val availableSourceTypes: List<SourceType>
        get() = sourceGroups.map { it.sourceType }

    /** Whether resume is available and significant */
    val canResume: Boolean
        get() = resume?.hasSignificantProgress == true

    /** Resume progress as percentage (0-100) */
    val resumeProgressPercent: Int
        get() = ((resume?.progressPercent ?: 0f) * 100).toInt()

    /**
     * Get the active source DERIVED from media.sources.
     *
     * This is a convenience property for UI - it always derives from current media.
     * For playback, the ViewModel re-derives at the moment of play().
     */
    val activeSource: MediaSourceRef?
        get() = SourceSelection.resolveActiveSource(media, selectedSourceKey, resume)

    /** Quality label for active source (derived) */
    val activeSourceQualityLabel: String?
        get() = activeSource?.quality?.toDisplayLabel()

    /** YouTube trailer URL (if available) */
    val trailer: String?
        get() = media?.trailer?.takeIf { it.isNotBlank() }
}

/** Events from unified detail screen. */
sealed class UnifiedDetailEvent {
    /**
     * Start playback with the specified source.
     *
     * @property canonicalId The canonical media identity
     * @property source The source to play (resolved at the moment of play)
     * @property resumePositionMs Position to resume at (may be approximated for cross-source)
     * @property isExactPosition True if resuming on the same source (frame-accurate)
     * @property approximationNote UI note when position is approximated
     */
    data class StartPlayback(
        val canonicalId: CanonicalMediaId,
        val source: MediaSourceRef,
        val resumePositionMs: Long,
        val isExactPosition: Boolean = true,
        val approximationNote: String? = null,
    ) : UnifiedDetailEvent()

    data class NavigateToSeries(
        val seriesCanonicalId: CanonicalMediaId,
    ) : UnifiedDetailEvent()

    data class ShowError(
        val message: String,
    ) : UnifiedDetailEvent()

    /**
     * Open trailer in YouTube or WebView.
     *
     * @property trailerUrl YouTube URL or ID to open
     */
    data class OpenTrailer(
        val trailerUrl: String,
    ) : UnifiedDetailEvent()
}

/**
 * Calculated resume position for a specific source.
 *
 * IMPORTANT: Different sources of the same media have different durations! This data class carries
 * the calculated position and metadata about whether it's exact (same source) or approximated
 * (different source).
 */
data class ResumeCalculation(
    val sourceId: PipelineItemId,
    val positionMs: Long,
    val durationMs: Long,
    val isExact: Boolean,
    val approximationNote: String?,
    val wasLastPlayed: Boolean,
) {
    val progressPercent: Float
        get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    val progressPercentInt: Int
        get() = (progressPercent * 100).toInt()
}

/**
 * Live TV program info for EPG display.
 */
data class LiveProgramInfo(
    val title: String,
    val description: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val isLive: Boolean = true,
)
