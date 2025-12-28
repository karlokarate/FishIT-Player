package com.fishit.player.v2.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.model.MediaSourceRef
import com.fishit.player.core.model.MediaType
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

        /** Builds extras map for PlaybackSourceFactory based on source type. */
        private fun buildExtrasForSource(source: MediaSourceRef): Map<String, String> =
            buildMap {
                val sourceIdValue = source.sourceId.value

                when (source.sourceType) {
                    com.fishit.player.core.model.SourceType.XTREAM -> {
                        when {
                            sourceIdValue.startsWith("xtream:vod:") -> {
                                put(
                                    XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE,
                                    XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_VOD,
                                )
                                // Accept legacy format: xtream:vod:{id}:{ext}
                                put(
                                    XtreamPlaybackSourceFactoryImpl.EXTRA_VOD_ID,
                                    sourceIdValue
                                        .removePrefix("xtream:vod:")
                                        .split(":")
                                        .firstOrNull()
                                        .orEmpty(),
                                )
                            }
                            sourceIdValue.startsWith("xtream:series:") -> {
                                put(
                                    XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE,
                                    XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_SERIES,
                                )
                                put(
                                    XtreamPlaybackSourceFactoryImpl.EXTRA_SERIES_ID,
                                    sourceIdValue.removePrefix("xtream:series:"),
                                )
                            }
                            sourceIdValue.startsWith("xtream:episode:") -> {
                                val parts = sourceIdValue.removePrefix("xtream:episode:").split(":")
                                if (parts.size >= 3) {
                                    put(
                                        XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE,
                                        XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_SERIES,
                                    )
                                    put(XtreamPlaybackSourceFactoryImpl.EXTRA_SERIES_ID, parts[0])
                                    put(XtreamPlaybackSourceFactoryImpl.EXTRA_SEASON_NUMBER, parts[1])
                                    put(XtreamPlaybackSourceFactoryImpl.EXTRA_EPISODE_NUMBER, parts[2])
                                }
                            }
                            sourceIdValue.startsWith("xtream:live:") -> {
                                put(
                                    XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE,
                                    XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_LIVE,
                                )
                                put(
                                    XtreamPlaybackSourceFactoryImpl.EXTRA_STREAM_ID,
                                    sourceIdValue.removePrefix("xtream:live:"),
                                )
                            }
                        }
                        source.format?.container?.let {
                            put(XtreamPlaybackSourceFactoryImpl.EXTRA_CONTAINER_EXT, it)
                        }
                    }
                    com.fishit.player.core.model.SourceType.TELEGRAM -> {
                        if (sourceIdValue.startsWith("msg:")) {
                            val parts = sourceIdValue.removePrefix("msg:").split(":")
                            if (parts.size >= 2) {
                                put("chatId", parts[0])
                                put("messageId", parts[1])
                            }
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

        private fun isLiveContent(source: MediaSourceRef): Boolean = source.sourceId.value.startsWith("xtream:live:")

        private suspend fun loadTelegramContext(sourceId: String) {
            // TODO: Implement Telegram context loading from repository
            _state.value = PlayerNavState(error = "Telegram playback not yet implemented via route")
        }

        private suspend fun loadXtreamContext(sourceId: String) {
            val raw =
                when {
                    sourceId.startsWith("xtream:live:") ->
                        xtreamLiveRepository.getBySourceId(sourceId)
                    else -> xtreamCatalogRepository.getBySourceId(sourceId)
                }

            if (raw == null) {
                UnifiedLog.w(TAG) { "No media found for $sourceId" }
                _state.value = PlayerNavState(error = "Item unavailable")
                return
            }

            val context =
                when (raw.mediaType) {
                    MediaType.LIVE -> buildLiveContext(sourceId, raw.originalTitle)
                    MediaType.MOVIE -> buildVodContext(sourceId, raw.originalTitle)
                    else -> null
                }

            if (context == null) {
                _state.value = PlayerNavState(error = "Playback not supported")
            } else {
                _state.value = PlayerNavState(context = context)
            }
        }

        private fun buildLiveContext(
            sourceId: String,
            title: String,
        ): PlaybackContext? {
            val streamId = sourceId.removePrefix("xtream:live:").toIntOrNull() ?: return null
            return PlaybackContext(
                canonicalId = "xtream:live:$streamId",
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
                canonicalId = "xtream:vod:${parsed.id}",
                sourceType = SourceType.XTREAM,
                uri = null, // Factory builds URL from XtreamApiClient session
                title = title,
                extras = extras,
            )
        }

        /**
         * Parses Xtream VOD sourceId format: xtream:vod:{id} or xtream:vod:{id}:{ext}
         * @return Parsed ID and optional extension, or null if invalid format
         */
        private fun parseXtreamVodSourceId(sourceId: String): XtreamSourceIdParts? {
            val prefix = "xtream:vod:"
            if (!sourceId.startsWith(prefix)) return null

            val remainder = sourceId.removePrefix(prefix)
            val parts = remainder.split(":")
            val id = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val extension = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            return XtreamSourceIdParts(id, extension)
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
