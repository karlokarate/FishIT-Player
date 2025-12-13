package com.fishit.player.v2.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.model.extensions.MediaSourceRefExtensions.parseXtreamVodId
import com.fishit.player.core.playermodel.PlaybackContext
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
            sourceType: SourceType,
        ) {
            viewModelScope.launch {
                when (sourceType) {
                    SourceType.XTREAM -> loadXtreamContext(sourceId)
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
                    else -> {
                        UnifiedLog.w(TAG) { "Unsupported media type for autoplay: ${raw.mediaType}" }
                        _state.value = PlayerNavState(error = "Playback not supported")
                        return
                    }
                }

            _state.value = PlayerNavState(context = context)
        }

        private fun buildLiveContext(
            sourceId: String,
            title: String,
        ): PlaybackContext {
            val streamId =
                sourceId.removePrefix("xtream:live:").toIntOrNull()
                    ?: return PlaybackContext(
                        canonicalId = sourceId,
                        sourceType = SourceType.XTREAM,
                        title = title,
                        isLive = true,
                        isSeekable = false,
                        extras = emptyMap(),
                    )

            return PlaybackContext(
                canonicalId = "xtream:live:$streamId",
                sourceType = SourceType.XTREAM,
                uri = null, // Factory will build URL from session
                title = title,
                isLive = true,
                isSeekable = false,
                extras =
                    buildXtreamExtras(
                        contentType = XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_LIVE,
                        streamId = streamId.toString(),
                    ),
            )
        }

        private fun buildVodContext(
            sourceId: String,
            title: String,
        ): PlaybackContext {
            val vodId =
                parseXtreamVodId(sourceId)
                    ?: return PlaybackContext(
                        canonicalId = sourceId,
                        sourceType = SourceType.XTREAM,
                        title = title,
                        extras = emptyMap(),
                    )

            return PlaybackContext(
                canonicalId = "xtream:vod:$vodId",
                sourceType = SourceType.XTREAM,
                uri = null, // Factory will build URL from session
                title = title,
                extras =
                    buildXtreamExtras(
                        contentType = XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_VOD,
                        vodId = vodId.toString(),
                    ),
            )
        }

        private fun buildXtreamExtras(
            contentType: String,
            streamId: String? = null,
            vodId: String? = null,
        ): Map<String, String> {
            val extras = mutableMapOf<String, String>()
            extras[XtreamPlaybackSourceFactoryImpl.EXTRA_CONTENT_TYPE] = contentType
            streamId?.let { extras[XtreamPlaybackSourceFactoryImpl.EXTRA_STREAM_ID] = it }
            vodId?.let { extras[XtreamPlaybackSourceFactoryImpl.EXTRA_VOD_ID] = it }
            return extras
        }

        private companion object {
            const val TAG = "PlayerNavViewModel"
        }
    }

data class PlayerNavState(
    val context: PlaybackContext? = null,
    val error: String? = null,
)
