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
import com.fishit.player.infra.transport.xtream.XtreamApiClient
import com.fishit.player.playback.xtream.XtreamPlaybackSourceFactoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

@HiltViewModel
class PlayerNavViewModel
    @Inject
    constructor(
        private val xtreamCatalogRepository: XtreamCatalogRepository,
        private val xtreamLiveRepository: XtreamLiveRepository,
        private val xtreamApiClient: XtreamApiClient,
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
            return runCatching {
                val uri = xtreamApiClient.buildLiveUrl(streamId)
                PlaybackContext(
                    canonicalId = "xtream:live:$streamId",
                    sourceType = SourceType.XTREAM,
                    uri = uri,
                    title = title,
                    isLive = true,
                    isSeekable = false,
                    extras =
                        buildXtreamExtras(
                            contentType = XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_LIVE,
                            streamId = streamId.toString(),
                        ),
                )
            }.getOrElse { error ->
                UnifiedLog.w(TAG, error) { "Failed to build live context for $sourceId" }
                null
            }
        }

        private fun buildVodContext(
            sourceId: String,
            title: String,
        ): PlaybackContext? {
            val vodId = parseXtreamVodId(sourceId) ?: return null
            return runCatching {
                val uri = xtreamApiClient.buildVodUrl(vodId, null)
                PlaybackContext(
                    canonicalId = "xtream:vod:$vodId",
                    sourceType = SourceType.XTREAM,
                    uri = uri,
                    title = title,
                    extras =
                        buildXtreamExtras(
                            contentType = XtreamPlaybackSourceFactoryImpl.CONTENT_TYPE_VOD,
                            vodId = vodId.toString(),
                        ),
                )
            }.getOrElse { error ->
                UnifiedLog.w(TAG, error) { "Failed to build VOD context for $sourceId" }
                null
            }
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

            xtreamApiClient.capabilities?.let { caps ->
                val uri = runCatching { URI(caps.baseUrl) }.getOrNull()
                uri?.host?.let { extras[XtreamPlaybackSourceFactoryImpl.EXTRA_SERVER_HOST] = it }
                uri?.port?.takeIf { it > 0 }?.let { extras[XtreamPlaybackSourceFactoryImpl.EXTRA_SERVER_PORT] = it.toString() }
                uri?.scheme?.let { extras[XtreamPlaybackSourceFactoryImpl.EXTRA_SERVER_SCHEME] = it }
                extras[XtreamPlaybackSourceFactoryImpl.EXTRA_USERNAME] = caps.username
            }

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
