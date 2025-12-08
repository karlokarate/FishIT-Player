package com.chris.m3usuite.ui.home

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.chris.m3usuite.playback.PlaybackSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
data class MiniPlayerDescriptor(
    val type: String,
    val url: String,
    val mediaId: Long? = null,
    val seriesId: Int? = null,
    val season: Int? = null,
    val episodeNum: Int? = null,
    val episodeId: Int? = null,
    val mimeType: String? = null,
    val origin: String? = null,
    val liveCategory: String? = null,
    val liveProvider: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
)

@Immutable
data class MiniPlayerSnapshot(
    val descriptor: MiniPlayerDescriptor,
    val positionMs: Long,
    val durationMs: Long,
)

val LocalMiniPlayerResume = compositionLocalOf<((MiniPlayerSnapshot) -> Unit)?> { null }

object MiniPlayerState {
    private val visibleState = MutableStateFlow(false)
    private val descriptorState = MutableStateFlow<MiniPlayerDescriptor?>(null)
    private val focusEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val visible: StateFlow<Boolean> = visibleState.asStateFlow()
    val descriptor: StateFlow<MiniPlayerDescriptor?> = descriptorState.asStateFlow()
    val focusRequests: SharedFlow<Unit> = focusEvents.asSharedFlow()

    fun show() {
        if (PlaybackSession.current() != null && descriptorState.value != null) {
            visibleState.value = true
        }
    }

    fun hide() {
        visibleState.value = false
    }

    fun stopAndRelease() {
        visibleState.value = false
        descriptorState.value = null
        PlaybackSession.setSource(null)
        PlaybackSession.set(null)
    }

    fun setDescriptor(descriptor: MiniPlayerDescriptor) {
        descriptorState.value = descriptor
    }

    fun updateDescriptor(block: (MiniPlayerDescriptor?) -> MiniPlayerDescriptor?) {
        descriptorState.update(block)
        if (descriptorState.value == null) {
            visibleState.value = false
        }
    }

    fun clearDescriptor() {
        descriptorState.value = null
        visibleState.value = false
    }

    fun requestFocus() {
        focusEvents.tryEmit(Unit)
    }
}

fun MiniPlayerDescriptor.buildRoute(positionMs: Long): String {
    val encodedUrl = Uri.encode(url)
    val mimeArg = mimeType?.let { Uri.encode(it) } ?: ""
    val startArg = if (positionMs > 0) positionMs else -1L
    val originArg = origin?.takeIf { it.isNotBlank() }?.let { "&origin=${Uri.encode(it)}" } ?: ""
    val catArg = liveCategory?.takeIf { it.isNotBlank() }?.let { "&cat=${Uri.encode(it)}" } ?: ""
    val provArg = liveProvider?.takeIf { it.isNotBlank() }?.let { "&prov=${Uri.encode(it)}" } ?: ""
    val startParam = "&startMs=$startArg"
    return when (type) {
        "live" -> {
            val media = mediaId ?: -1L
            "player?url=$encodedUrl&type=live&mediaId=$media$startParam&mime=$mimeArg$originArg$catArg$provArg"
        }
        "series" -> {
            val sid = seriesId ?: -1
            val seasonArg = season ?: -1
            val episodeArg = episodeNum ?: -1
            val episodeIdArg = episodeId ?: -1
            val media = mediaId ?: -1L
            "player?url=$encodedUrl&type=series&mediaId=$media&seriesId=$sid&season=$seasonArg&episodeNum=$episodeArg&episodeId=$episodeIdArg$startParam&mime=$mimeArg"
        }
        else -> {
            val media = mediaId ?: -1L
            "player?url=$encodedUrl&type=vod&mediaId=$media$startParam&mime=$mimeArg"
        }
    }
}
