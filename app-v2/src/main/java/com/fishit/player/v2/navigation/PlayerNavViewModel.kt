package com.fishit.player.v2.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    ) : ViewModel() {
        private val _state = MutableStateFlow(PlayerNavState())
        val state: StateFlow<PlayerNavState> = _state

        fun load(
            sourceId: String,
            sourceType: com.fishit.player.core.model.SourceType,
        ) {
            viewModelScope.launch {
                when (sourceType) {
                    com.fishit.player.core.model.SourceType.XTREAM -> loadXtreamContext(sourceId)
                    else -> _state.value = PlayerNavState(error = "Unsupported source: $sourceType")
                }
            }
        }

        private suspend fun loadXtreamContext(sourceId: String) {
            val raw =
                when {
                    sourceId.startsWith("xtream:live:") -> xtreamLiveRepository.getBySourceId(sourceId)
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
                        XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE to XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_LIVE,
                        XtreamPlaybackSourceFactoryImpl.EXTRA_STREAM_ID to streamId.toString(),
                    ),
            )
        }

        private fun buildVodContext(
            sourceId: String,
            title: String,
        ): PlaybackContext? {
            val parsed = parseXtreamVodSourceId(sourceId) ?: return null
            val extras = mutableMapOf(
                XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE to XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_VOD,
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
        private data class XtreamSourceIdParts(val id: Int, val extension: String?)

        private companion object {
            const val TAG = "PlayerNavViewModel"
        }
    }

data class PlayerNavState(
    val context: PlaybackContext? = null,
    val error: String? = null,
)
