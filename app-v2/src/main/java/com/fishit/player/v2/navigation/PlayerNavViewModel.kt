package com.fishit.player.v2.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.PlaybackHintKeys
import com.fishit.player.core.model.ids.XtreamIdCodec
import com.fishit.player.core.model.repository.NxWorkRepository
import com.fishit.player.core.model.repository.NxWorkSourceRefRepository
import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.infra.data.xtream.XtreamCatalogRepository
import com.fishit.player.infra.data.xtream.XtreamLiveRepository
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.playback.xtream.XtreamPlaybackSourceFactoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerNavViewModel
    @Inject
    constructor(
        private val xtreamCatalogRepository: XtreamCatalogRepository,
        private val xtreamLiveRepository: XtreamLiveRepository,
        private val playbackPendingState: PlaybackPendingState,
        private val workRepository: NxWorkRepository,
        private val sourceRefRepository: NxWorkSourceRefRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PlayerNavState())
        val state: StateFlow<PlayerNavState> = _state

        /**
         * Loads playback context from pending state or falls back to route params.
         *
         * **Priority:**
         * 1. Check PlaybackPendingState for full context from DetailScreen
         * 2. Fallback to loading from repository based on route params
         */
        fun load(
            sourceId: String,
            sourceType: com.fishit.player.core.model.SourceType,
        ) {
            viewModelScope.launch {
                // Priority 1: Try consuming pending playback (from DetailScreen)
                val pending = playbackPendingState.consumePendingPlayback()
                if (pending != null) {
                    UnifiedLog.d(TAG) { "Using pending playback: ${pending.canonicalId.key}" }
                    val context = buildContextFromPending(pending)
                    _state.value = PlayerNavState(context = context)
                    return@launch
                }

                // Priority 2: Fallback to route-based loading
                UnifiedLog.d(TAG) { "No pending playback, loading from route: $sourceId" }
                when (sourceType) {
                    com.fishit.player.core.model.SourceType.XTREAM -> loadXtreamContext(sourceId)
                    com.fishit.player.core.model.SourceType.TELEGRAM -> loadTelegramContext(sourceId)
                    else -> _state.value = PlayerNavState(error = "Unsupported source: $sourceType")
                }
            }
        }

        /** Builds PlaybackContext from PendingPlayback (full context from DetailScreen). */
        private fun buildContextFromPending(pending: PendingPlayback): PlaybackContext {
            val source = pending.source
            val extras = buildExtrasForSource(source)

            return PlaybackContext(
                canonicalId = pending.canonicalId.key.value,
                sourceType = mapToPlayerSourceType(source.sourceType),
                sourceKey = source.sourceId.value,
                uri = null, // Factory builds URL
                title = pending.title ?: "Unknown",
                posterUrl = pending.posterUrl,
                startPositionMs = pending.resumePositionMs,
                isLive = isLiveContent(source),
                isSeekable = !isLiveContent(source),
                extras = extras,
            )
        }

        /**
         * Builds extras map for PlaybackSourceFactory based on source type.
         *
         * **SSOT Priority:** playbackHints from MediaSourceRef take precedence.
         * These are set by the pipeline during catalog sync and contain the correct
         * content type, IDs, and extensions. Fallback parsing of sourceId is only
         * used for legacy data or missing hints.
         */
        private fun buildExtrasForSource(source: MediaSourceRef): Map<String, String> =
            buildMap {
                // SSOT: Start with playbackHints from MediaSourceRef (pipeline SSOT)
                // These contain correct contentType, vodId, streamId, episodeId, containerExt, etc.
                putAll(source.playbackHints)

                val sourceIdValue = source.sourceId.value

                when (source.sourceType) {
                    com.fishit.player.core.model.SourceType.XTREAM -> {
                        // Detect content type from sourceKey via XtreamIdCodec SSOT.
                        // Supports both legacy format (xtream:vod:123) and
                        // NX format (src:xtream:account:vod:123)
                        val contentType = XtreamIdCodec.detectContentType(sourceIdValue)

                        // Only fill missing values via fallback parsing
                        when (contentType) {
                            XtreamIdCodec.ContentType.VOD -> {
                                putIfAbsent(
                                    XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE,
                                    XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_VOD,
                                )
                                // Fallback: parse vodId from sourceId if not in playbackHints
                                if (!containsKey(PlaybackHintKeys.Xtream.VOD_ID) &&
                                    !containsKey(XtreamPlaybackSourceFactoryImpl.EXTRA_VOD_ID)
                                ) {
                                    XtreamIdCodec.extractSimpleId(sourceIdValue, XtreamIdCodec.ContentType.VOD)?.let {
                                        put(XtreamPlaybackSourceFactoryImpl.EXTRA_VOD_ID, it)
                                    }
                                }
                            }
                            XtreamIdCodec.ContentType.SERIES -> {
                                putIfAbsent(
                                    XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE,
                                    XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_SERIES,
                                )
                                if (!containsKey(PlaybackHintKeys.Xtream.SERIES_ID) &&
                                    !containsKey(XtreamPlaybackSourceFactoryImpl.EXTRA_SERIES_ID)
                                ) {
                                    XtreamIdCodec.extractSimpleId(sourceIdValue, XtreamIdCodec.ContentType.SERIES)?.let {
                                        put(XtreamPlaybackSourceFactoryImpl.EXTRA_SERIES_ID, it)
                                    }
                                }
                            }
                            XtreamIdCodec.ContentType.EPISODE -> {
                                putIfAbsent(
                                    XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE,
                                    XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_SERIES,
                                )
                                // Episode ID extraction via XtreamIdCodec SSOT
                                val episodeParsed = XtreamIdCodec.parse(sourceIdValue)
                                when (episodeParsed) {
                                    is com.fishit.player.core.model.ids.XtreamParsedSourceId.EpisodeComposite -> {
                                        putIfAbsent(XtreamPlaybackSourceFactoryImpl.EXTRA_SERIES_ID, episodeParsed.seriesId.toString())
                                        putIfAbsent(XtreamPlaybackSourceFactoryImpl.EXTRA_SEASON_NUMBER, episodeParsed.season.toString())
                                        putIfAbsent(XtreamPlaybackSourceFactoryImpl.EXTRA_EPISODE_NUMBER, episodeParsed.episode.toString())
                                    }
                                    is com.fishit.player.core.model.ids.XtreamParsedSourceId.Episode -> {
                                        putIfAbsent(XtreamPlaybackSourceFactoryImpl.EXTRA_EPISODE_ID, episodeParsed.episodeId.toString())
                                    }
                                    else -> {
                                        // Fallback: try extractSimpleId for non-standard formats
                                        XtreamIdCodec.extractSimpleId(sourceIdValue, XtreamIdCodec.ContentType.EPISODE)?.let { episodePart ->
                                            putIfAbsent(XtreamPlaybackSourceFactoryImpl.EXTRA_EPISODE_ID, episodePart)
                                        }
                                    }
                                }
                            }
                            XtreamIdCodec.ContentType.LIVE -> {
                                putIfAbsent(
                                    XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE,
                                    XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_LIVE,
                                )
                                if (!containsKey(PlaybackHintKeys.Xtream.STREAM_ID) &&
                                    !containsKey(XtreamPlaybackSourceFactoryImpl.EXTRA_STREAM_ID)
                                ) {
                                    XtreamIdCodec.extractSimpleId(sourceIdValue, XtreamIdCodec.ContentType.LIVE)?.let {
                                        put(XtreamPlaybackSourceFactoryImpl.EXTRA_STREAM_ID, it)
                                    }
                                }
                            }
                            XtreamIdCodec.ContentType.UNKNOWN -> {
                                // Unknown content type, can't provide fallbacks
                            }
                        }
                        // Legacy fallback: container from MediaFormat if not in playbackHints
                        if (!containsKey(PlaybackHintKeys.Xtream.CONTAINER_EXT) &&
                            !containsKey(XtreamPlaybackSourceFactoryImpl.EXTRA_CONTAINER_EXT)
                        ) {
                            source.format?.container?.let {
                                put(XtreamPlaybackSourceFactoryImpl.EXTRA_CONTAINER_EXT, it)
                            }
                        }
                    }
                    com.fishit.player.core.model.SourceType.TELEGRAM -> {
                        // Parse via SourceIdParser SSOT (handles msg:, telegram:, tg: formats)
                        com.fishit.player.core.model.SourceIdParser.parseTelegramSourceId(sourceIdValue)
                            ?.let { (chatId, messageId) ->
                                putIfAbsent("chatId", chatId.toString())
                                putIfAbsent("messageId", messageId.toString())
                            }
                    }
                    else -> {
                        put("sourceId", sourceIdValue)
                    }
                }
            }

        private fun mapToPlayerSourceType(sourceType: com.fishit.player.core.model.SourceType): SourceType =
            when (sourceType) {
                com.fishit.player.core.model.SourceType.TELEGRAM -> SourceType.TELEGRAM
                com.fishit.player.core.model.SourceType.XTREAM -> SourceType.XTREAM
                com.fishit.player.core.model.SourceType.AUDIOBOOK -> SourceType.AUDIOBOOK
                // Local file variants → FILE
                com.fishit.player.core.model.SourceType.IO,
                com.fishit.player.core.model.SourceType.LOCAL,
                -> SourceType.FILE
                // Unsupported sources → UNKNOWN
                com.fishit.player.core.model.SourceType.PLEX,
                com.fishit.player.core.model.SourceType.OTHER,
                com.fishit.player.core.model.SourceType.UNKNOWN,
                -> SourceType.UNKNOWN
            }

        private fun isLiveContent(source: MediaSourceRef): Boolean =
            source.sourceId.value.contains(":live:")

        private suspend fun loadTelegramContext(sourceId: String) {
            // TODO: Implement Telegram context loading from repository
            _state.value = PlayerNavState(error = "Telegram playback not yet implemented via route")
        }

        /**
         * Load Xtream playback context.
         * 
         * **Smart ID Detection:**
         * Supports both workKey format (from HomeScreen tiles) and sourceId format (from DetailScreen).
         * - workKey format: `live:channel-name:...` or `movie:title:year` (from NxHomeContentRepository)
         * - sourceId format: `xtream:live:123` or `xtream:vod:456` (from pipeline)
         * 
         * For workKey format, resolves sourceId via NxWorkSourceRefRepository.
         */
        private suspend fun loadXtreamContext(sourceId: String) {
            // Detect workKey format (from HomeScreen) vs sourceId format (from DetailScreen)
            val isWorkKey = isWorkKeyFormat(sourceId)
            
            val resolvedSourceId = if (isWorkKey) {
                // Resolve workKey → sourceId via NxWorkSourceRefRepository
                val resolved = resolveWorkKeyToSourceId(sourceId)
                if (resolved == null) {
                    UnifiedLog.w(TAG) { "Could not resolve workKey to sourceId: $sourceId" }
                    _state.value = PlayerNavState(error = "Item unavailable")
                    return
                }
                UnifiedLog.d(TAG) { "Resolved workKey '$sourceId' → sourceId '$resolved'" }
                resolved
            } else {
                sourceId
            }
            
            val raw =
                when {
                    resolvedSourceId.contains(":live:") ->
                        xtreamLiveRepository.getBySourceId(resolvedSourceId)
                    else -> xtreamCatalogRepository.getBySourceId(resolvedSourceId)
                }

            if (raw == null) {
                UnifiedLog.w(TAG) { "No media found for $resolvedSourceId (original: $sourceId)" }
                _state.value = PlayerNavState(error = "Item unavailable")
                return
            }

            val context =
                when (raw.mediaType) {
                    MediaType.LIVE -> buildLiveContext(resolvedSourceId, raw.originalTitle)
                    MediaType.MOVIE -> buildVodContext(resolvedSourceId, raw.originalTitle)
                    MediaType.SERIES -> buildSeriesContext(resolvedSourceId, raw.originalTitle)
                    MediaType.SERIES_EPISODE -> buildSeriesContext(resolvedSourceId, raw.originalTitle)
                    else -> null
                }

            if (context == null) {
                _state.value = PlayerNavState(error = "Playback not supported")
            } else {
                _state.value = PlayerNavState(context = context)
            }
        }

        /**
         * Detect if the ID is a workKey (from HomeScreen) vs a sourceId (from DetailScreen).
         * 
         * workKey formats: `live:...`, `movie:...`, `series:...`, `episode:...`
         * sourceId formats: `xtream:...`, `msg:...`, `io:...`, `audiobook:...`
         */
        private fun isWorkKeyFormat(id: String): Boolean {
            // sourceId formats start with source-specific prefixes
            if (id.startsWith("xtream:") || 
                id.startsWith("msg:") || 
                id.startsWith("io:") || 
                id.startsWith("audiobook:") ||
                id.startsWith("src:")) {
                return false
            }
            // workKey formats start with content-type prefixes
            return id.startsWith("live:") || 
                   id.startsWith("movie:") || 
                   id.startsWith("series:") || 
                   id.startsWith("episode:") ||
                   id.startsWith("clip:") ||
                   id.startsWith("tmdb:")
        }

        /**
         * Resolve a workKey to its Xtream sourceId via NxWorkSourceRefRepository.
         * 
         * Looks up the work by workKey, then finds its Xtream source reference.
         * Returns the sourceItemKey (which contains the sourceId like "xtream:live:123").
         */
        private suspend fun resolveWorkKeyToSourceId(workKey: String): String? {
            // Find all source refs for this workKey
            val sourceRefs = sourceRefRepository.findByWorkKey(workKey)
            
            // Find the Xtream source ref
            val xtreamRef = sourceRefs.find { 
                it.sourceType == NxWorkSourceRefRepository.SourceType.XTREAM 
            }
            
            // sourceItemKey contains the sourceId format: "xtream:live:123", "xtream:vod:456"
            return xtreamRef?.sourceItemKey
        }

        private fun buildLiveContext(
            sourceId: String,
            title: String,
        ): PlaybackContext? {
            val streamIdStr = XtreamIdCodec.extractSimpleId(sourceId, XtreamIdCodec.ContentType.LIVE) ?: return null
            val streamId = streamIdStr.toIntOrNull() ?: return null
            return PlaybackContext(
                canonicalId = XtreamIdCodec.live(streamId),
                sourceType = SourceType.XTREAM,
                uri = null, // Factory builds URL from XtreamApiClient session
                title = title,
                isLive = true,
                isSeekable = false,
                extras =
                    mapOf(
                        XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE to
                            XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_LIVE,
                        XtreamPlaybackSourceFactoryImpl.EXTRA_STREAM_ID to
                            streamId.toString(),
                    ),
            )
        }

        private fun buildVodContext(
            sourceId: String,
            title: String,
        ): PlaybackContext? {
            val parsed = parseXtreamVodSourceId(sourceId) ?: return null
            val extras =
                mutableMapOf(
                    XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE to
                        XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_VOD,
                    XtreamPlaybackSourceFactoryImpl.EXTRA_VOD_ID to parsed.id.toString(),
                )
            // Add containerExtension if present in sourceId
            parsed.extension?.let { ext ->
                extras[XtreamPlaybackSourceFactoryImpl.EXTRA_CONTAINER_EXT] = ext
            }
            return PlaybackContext(
                canonicalId = XtreamIdCodec.vod(parsed.id),
                sourceType = SourceType.XTREAM,
                uri = null, // Factory builds URL from XtreamApiClient session
                title = title,
                extras = extras,
            )
        }

        /**
         * Build series/episode playback context from route params (fallback path).
         *
         * Supports both:
         * - Series: xtream:series:{seriesId} / src:xtream:account:series:{seriesId}
         * - Episode composite: xtream:episode:series:{seriesId}:s{season}:e{episode}
         * - Episode direct: xtream:episode:{episodeId}
         */
        private fun buildSeriesContext(
            sourceId: String,
            title: String,
        ): PlaybackContext? {
            val parsed = XtreamIdCodec.parse(sourceId) ?: return null
            val extras = mutableMapOf(
                XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE to
                    XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_SERIES,
            )

            when (parsed) {
                is com.fishit.player.core.model.ids.XtreamParsedSourceId.Series -> {
                    extras[XtreamPlaybackSourceFactoryImpl.EXTRA_SERIES_ID] = parsed.seriesId.toString()
                }
                is com.fishit.player.core.model.ids.XtreamParsedSourceId.EpisodeComposite -> {
                    extras[XtreamPlaybackSourceFactoryImpl.EXTRA_SERIES_ID] = parsed.seriesId.toString()
                    extras[XtreamPlaybackSourceFactoryImpl.EXTRA_SEASON_NUMBER] = parsed.season.toString()
                    extras[XtreamPlaybackSourceFactoryImpl.EXTRA_EPISODE_NUMBER] = parsed.episode.toString()
                }
                is com.fishit.player.core.model.ids.XtreamParsedSourceId.Episode -> {
                    extras[XtreamPlaybackSourceFactoryImpl.EXTRA_EPISODE_ID] = parsed.episodeId.toString()
                }
                else -> return null // Not a series/episode type
            }

            return PlaybackContext(
                canonicalId = sourceId, // Use original sourceId as canonical
                sourceType = SourceType.XTREAM,
                uri = null, // Factory builds URL from XtreamApiClient session
                title = title,
                extras = extras,
            )
        }

        /**
         * Parses Xtream VOD sourceId format via XtreamIdCodec SSOT.
         *
         * Supports both:
         * - Legacy: xtream:vod:{id}
         * - NX: src:xtream:account:vod:{id}
         *
         * @return Parsed ID and optional extension, or null if invalid format
         */
        private fun parseXtreamVodSourceId(sourceId: String): XtreamSourceIdParts? {
            val vodId = XtreamIdCodec.extractVodId(sourceId)?.toInt()
            if (vodId != null) {
                return XtreamSourceIdParts(vodId, extension = null)
            }
            // Fallback for non-standard formats: try extractSimpleId
            if (!sourceId.contains(":vod:")) return null
            val idStr = XtreamIdCodec.extractSimpleId(sourceId, XtreamIdCodec.ContentType.VOD) ?: return null
            val id = idStr.toIntOrNull() ?: return null
            return XtreamSourceIdParts(id, extension = null)
        }

        /** Parsed components of Xtream sourceId */
        private data class XtreamSourceIdParts(
            val id: Int,
            val extension: String?,
        )

        private companion object {
            const val TAG = "PlayerNavViewModel"
        }
    }

data class PlayerNavState(
    val context: PlaybackContext? = null,
    val error: String? = null,
)
