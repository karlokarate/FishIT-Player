package com.chris.m3usuite.player

import android.app.Activity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.chris.m3usuite.model.MediaItem as AppMediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.core.playback.RememberPlayerController
import com.chris.m3usuite.core.playback.rememberPlayerController
import com.chris.m3usuite.player.internal.domain.PlaybackContext
import com.chris.m3usuite.player.internal.session.applyPlayerCommand_ChangeSpeed
import com.chris.m3usuite.player.internal.session.applyPlayerCommand_PlayPause
import com.chris.m3usuite.player.internal.session.applyPlayerCommand_SeekBy
import com.chris.m3usuite.player.internal.session.applyPlayerCommand_SeekTo
import com.chris.m3usuite.player.internal.session.applyPlayerCommand_ToggleLoop
import com.chris.m3usuite.player.internal.session.rememberInternalPlayerSession
import com.chris.m3usuite.player.internal.state.AspectRatioMode
import com.chris.m3usuite.player.internal.state.InternalPlayerController
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import com.chris.m3usuite.player.internal.system.InternalPlayerSystemUi
import com.chris.m3usuite.player.internal.system.requestPictureInPicture
import com.chris.m3usuite.player.internal.ui.InternalPlayerContent
import com.chris.m3usuite.player.internal.ui.SleepTimerDialog
import com.chris.m3usuite.player.internal.ui.SpeedDialog
import com.chris.m3usuite.player.internal.ui.TracksDialog
import com.chris.m3usuite.ui.layout.FishM3uSuiteAppTheme
import com.chris.m3usuite.ui.layout.rememberImageHeaders
import com.chris.m3usuite.ui.util.LocalWindowSize

/**
 * New API:
 *
 * RememberInternalPlayerScreen(
 *   url = ...,
 *   startMs = ...,
 *   mimeType = ...,
 *   mediaItem = AppMediaItem?,
 *   playbackContext = PlaybackContext(...),
 *   onClose = ...
 * )
 */
@Composable
fun RememberInternalPlayerScreen(
    url: String,
    startMs: Long?,
    mimeType: String?,
    mediaItem: AppMediaItem?,
    playbackContext: PlaybackContext,
    onClose: () -> Unit,
) {
    val controller = rememberPlayerController()
    val settings = remember { SettingsStore(LocalContext.current) }

    InternalPlayerScreen(
        url = url,
        startMs = startMs,
        mimeType = mimeType,
        preparedMediaItem = mediaItem,
        playbackContext = playbackContext,
        onClose = onClose,
        controller = controller,
        settings = settings,
    )
}

@Composable
fun InternalPlayerScreen(
    url: String,
    startMs: Long?,
    mimeType: String?,
    preparedMediaItem: AppMediaItem?,
    playbackContext: PlaybackContext,
    onClose: () -> Unit,
    controller: RememberPlayerController,
    settings: SettingsStore,
) {
    val context = LocalContext.current
    val windowSize = LocalWindowSize.current
    val view = LocalView.current
    val activity = context as? Activity

    val imageHeaders = rememberImageHeaders()

    var uiState by remember {
        mutableStateOf(
            InternalPlayerUiState(
                playbackType = playbackContext.type,
            ),
        )
    }
    var lastError by remember { mutableStateOf<PlaybackException?>(null) }

    val player: ExoPlayer? =
        rememberInternalPlayerSession(
            context = context,
            url = url,
            startMs = startMs,
            mimeType = mimeType,
            preparedMediaItem = preparedMediaItem,
            playbackContext = playbackContext,
            settings = settings,
            networkHeaders = imageHeaders,
            controller = controller,
            onStateChanged = { uiState = it },
            onError = { lastError = it },
        )

    // System UI (Back, screen-on, fullscreen)
    InternalPlayerSystemUi(
        isPlaying = uiState.isPlaying,
        onClose = onClose,
    )

    // Controller implementation backed by ExoPlayer
    val internalController =
        InternalPlayerController(
            onPlayPause = { applyPlayerCommand_PlayPause(player) },
            onSeekTo = { pos -> applyPlayerCommand_SeekTo(player, pos) },
            onSeekBy = { delta -> applyPlayerCommand_SeekBy(player, delta) },
            onChangeSpeed = { speed ->
                applyPlayerCommand_ChangeSpeed(player, speed)
                uiState = uiState.copy(playbackSpeed = speed)
            },
            onToggleLoop = {
                val looping = !uiState.isLooping
                applyPlayerCommand_ToggleLoop(player, looping)
                uiState = uiState.copy(isLooping = looping)
            },
            onEnterPip = { requestPictureInPicture(activity) },
            onToggleSettingsDialog = {
                uiState = uiState.copy(showSettingsDialog = !uiState.showSettingsDialog)
            },
            onToggleTracksDialog = {
                uiState = uiState.copy(showTracksDialog = !uiState.showTracksDialog)
            },
            onToggleSpeedDialog = {
                uiState = uiState.copy(showSpeedDialog = !uiState.showSpeedDialog)
            },
            onToggleSleepTimerDialog = {
                uiState = uiState.copy(showSleepTimerDialog = !uiState.showSleepTimerDialog)
            },
            onToggleDebugInfo = {
                uiState = uiState.copy(showDebugInfo = !uiState.showDebugInfo)
            },
            onCycleAspectRatio = {
                val next =
                    when (uiState.aspectRatioMode) {
                        AspectRatioMode.FIT -> AspectRatioMode.FILL
                        AspectRatioMode.FILL -> AspectRatioMode.ZOOM
                        AspectRatioMode.ZOOM -> AspectRatioMode.STRETCH
                        AspectRatioMode.STRETCH -> AspectRatioMode.FIT
                    }
                uiState = uiState.copy(aspectRatioMode = next)
            },
        )

    FishM3uSuiteAppTheme {
        InternalPlayerContent(
            player = player,
            state = uiState,
            controller = internalController,
        )

        // Speed dialog
        if (uiState.showSpeedDialog) {
            SpeedDialog(
                currentSpeed = uiState.playbackSpeed,
                onDismiss = { uiState = uiState.copy(showSpeedDialog = false) },
                onSpeedSelected = { s ->
                    applyPlayerCommand_ChangeSpeed(player, s)
                    uiState = uiState.copy(playbackSpeed = s)
                },
            )
        }

        // Sleep timer dialog
        if (uiState.showSleepTimerDialog) {
            SleepTimerDialog(
                remainingMs = uiState.sleepTimerRemainingMs,
                onDisable = {
                    settings.playerSleepTimerMinutes = 0
                    uiState = uiState.copy(sleepTimerRemainingMs = null)
                },
                onDismiss = {
                    uiState = uiState.copy(showSleepTimerDialog = false)
                },
            )
        }

        // Tracks dialog (audio + subtitles)
        if (uiState.showTracksDialog && player != null) {
            TracksDialog(
                tracks = player.currentTracks,
                onDismiss = {
                    uiState = uiState.copy(showTracksDialog = false)
                },
                onApplyOverride = { override ->
                    val builder = player.trackSelectionParameters.buildUpon()
                    builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    if (override != null) {
                        builder.addOverride(override)
                    }
                    player.trackSelectionParameters = builder.build()
                },
            )
        }

        // Simple settings dialog placeholder – you kannst hier später
        // deinen alten PlayerSettingsDialog aus dem Legacy-Screen reinhängen.
        if (uiState.showSettingsDialog) {
            AlertDialog(
                onDismissRequest = {
                    uiState = uiState.copy(showSettingsDialog = false)
                },
                confirmButton = {
                    TextButton(
                        onClick = { uiState = uiState.copy(showSettingsDialog = false) },
                    ) {
                        Text("Close")
                    }
                },
                title = { Text("Player settings") },
                text = {
                    Text("Loop: ${uiState.isLooping}\nSpeed: ${uiState.playbackSpeed}x")
                },
            )
        }

        // Kids / screen-time blocking overlay
        if (uiState.kidActive && uiState.kidBlocked) {
            AlertDialog(
                onDismissRequest = { onClose() },
                confirmButton = {
                    TextButton(onClick = { onClose() }) {
                        Text("Close")
                    }
                },
                title = { Text("Playback blocked") },
                text = {
                    Text(
                        "Screen time limit has been reached for this profile. " +
                            "Playback is blocked until more time is granted.",
                    )
                },
            )
        }

        // Error dialog (optional)
        lastError?.let { error ->
            AlertDialog(
                onDismissRequest = { lastError = null },
                confirmButton = {
                    TextButton(onClick = { lastError = null }) {
                        Text("OK")
                    }
                },
                title = { Text("Playback error") },
                text = { Text(error.message ?: "Unknown error") },
            )
        }
    }
}