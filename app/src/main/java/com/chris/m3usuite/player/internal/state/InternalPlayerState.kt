package com.chris.m3usuite.player.internal.state

import androidx.compose.runtime.Immutable
import androidx.media3.common.PlaybackException
import com.chris.m3usuite.player.internal.domain.PlaybackType

@Immutable
data class InternalPlayerUiState(
    val playbackType: PlaybackType = PlaybackType.VOD,

    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val isLooping: Boolean = false,
    val playbackError: PlaybackException? = null,
    val sleepTimerRemainingMs: Long? = null,

    // Kids / Screen-Time
    val kidActive: Boolean = false,
    val kidBlocked: Boolean = false,
    val kidProfileId: Long? = null,

    // UI dialogs / overlays
    val showSettingsDialog: Boolean = false,
    val showTracksDialog: Boolean = false,
    val showSpeedDialog: Boolean = false,
    val showSleepTimerDialog: Boolean = false,
    val showDebugInfo: Boolean = false,

    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
) {
    val isLive: Boolean
        get() = playbackType == PlaybackType.LIVE

    val isSeries: Boolean
        get() = playbackType == PlaybackType.SERIES
}

enum class AspectRatioMode {
    FIT,
    FILL,
    ZOOM,
    STRETCH,
}

/**
 * High-level controller API for the player screen.
 *
 * The screen and the UI modules should only talk to this abstraction,
 * never directly to ExoPlayer or TDLib.
 */
data class InternalPlayerController(
    val onPlayPause: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onSeekBy: (Long) -> Unit,
    val onChangeSpeed: (Float) -> Unit,
    val onToggleLoop: () -> Unit,
    val onEnterPip: () -> Unit,
    val onToggleSettingsDialog: () -> Unit,
    val onToggleTracksDialog: () -> Unit,
    val onToggleSpeedDialog: () -> Unit,
    val onToggleSleepTimerDialog: () -> Unit,
    val onToggleDebugInfo: () -> Unit,
    val onCycleAspectRatio: () -> Unit,
)