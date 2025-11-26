package com.chris.m3usuite.player.internal.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.chris.m3usuite.player.internal.state.InternalPlayerController
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import com.chris.m3usuite.player.internal.system.requestPictureInPicture

@Composable
fun InternalPlayerContent(
    player: ExoPlayer?,
    state: InternalPlayerUiState,
    controller: InternalPlayerController,
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity

    Box(
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        // Phase 3 Step 3.D + Phase 4 Group 3: PlayerSurface with gesture handling and subtitle styling
        PlayerSurface(
            player = player,
            aspectRatioMode = state.aspectRatioMode,
            playbackType = state.playbackType,
            subtitleStyle = state.subtitleStyle,
            isKidMode = state.kidActive,
            onTap = {
                // Future: toggle controls visibility
                // For now, no-op (controls are always shown in SIP reference)
            },
            onJumpLiveChannel = controller.onJumpLiveChannel,
        )

        // Phase 3 Step 3.C: Live channel name header (LIVE only)
        if (state.isLive && state.liveChannelName != null) {
            LiveChannelHeader(
                channelName = state.liveChannelName,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
            )
        }

        // Phase 3 Step 3.C & Task 2: EPG overlay (LIVE only, with AnimatedVisibility)
        // AnimatedVisibility uses epgOverlayVisible directly without delays
        AnimatedVisibility(
            visible = state.isLive && state.epgOverlayVisible,
            enter =
                fadeIn(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(durationMillis = 200),
                ),
            exit =
                fadeOut(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(durationMillis = 200),
                ),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
        ) {
            LiveEpgOverlay(
                nowTitle = state.liveNowTitle,
                nextTitle = state.liveNextTitle,
            )
        }

        // Overlay-Controls (vereinfacht)
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            MainControlsRow(
                state = state,
                onPlayPause = controller.onPlayPause,
                onSeekBack = { controller.onSeekBy(-10_000) },
                onSeekForward = { controller.onSeekBy(30_000) },
                onToggleLoop = controller.onToggleLoop,
                onChangeAspectRatio = controller.onCycleAspectRatio,
                onSpeedClick = controller.onToggleSpeedDialog,
                onTracksClick = controller.onToggleTracksDialog,
                onCcClick = controller.onToggleCcMenu, // Phase 4 Group 4
                onSettingsClick = controller.onToggleSettingsDialog,
                onPipClick = { requestPictureInPicture(activity) },
            )
            Spacer(Modifier.height(8.dp))
            ProgressRow(state = state, onScrubTo = controller.onSeekTo)
        }

        if (state.showDebugInfo) {
            DebugInfoOverlay(
                state = state,
                player = player,
            )
        }

        // Phase 4 Group 4: CC Menu Dialog
        // Shows when state.showCcMenuDialog is true
        // Contract Section 8: CC/Subtitle UI for SIP Only
        if (state.showCcMenuDialog && !state.kidActive) {
            // TODO: Extract available tracks from player
            // For now, use empty list as placeholder until full integration
            val availableTracks = emptyList<com.chris.m3usuite.player.internal.subtitles.SubtitleTrack>()

            CcMenuDialog(
                currentStyle = state.subtitleStyle,
                availableTracks = availableTracks,
                selectedTrack = state.selectedSubtitleTrack,
                onDismiss = controller.onToggleCcMenu,
                onApplyStyle = { style ->
                    // TODO: Wire to SubtitleStyleManager.updateStyle()
                    // This will be implemented when we integrate with session
                },
                onApplyPreset = { preset ->
                    // TODO: Wire to SubtitleStyleManager.applyPreset()
                    // This will be implemented when we integrate with session
                },
                onSelectTrack = { track ->
                    // TODO: Wire to SubtitleSelectionPolicy.persistSelection()
                    // This will be implemented when we integrate with session
                },
            )
        }
    }
}

@Composable
private fun MainControlsRow(
    state: InternalPlayerUiState,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleLoop: () -> Unit,
    onChangeAspectRatio: () -> Unit,
    onSpeedClick: () -> Unit,
    onTracksClick: () -> Unit,
    onCcClick: () -> Unit, // Phase 4 Group 4
    onSettingsClick: () -> Unit,
    onPipClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSeekBack) {
                // TODO: Replace with Replay10 icon when available
                Icon(
                    imageVector = Icons.Filled.FastRewind,
                    contentDescription = "Replay 10s",
                )
            }
            IconButton(onClick = onPlayPause) {
                val icon =
                    if (state.isPlaying) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    }
                Icon(
                    imageVector = icon,
                    contentDescription = "Play/Pause",
                )
            }
            IconButton(onClick = onSeekForward) {
                // TODO: Replace with Forward30 icon when available
                Icon(
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = "Forward 30s",
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleLoop) {
                val icon =
                    if (state.isLooping) {
                        Icons.Filled.Repeat
                    } else {
                        Icons.Outlined.Repeat
                    }
                Icon(
                    imageVector = icon,
                    contentDescription = "Loop",
                )
            }
            IconButton(onClick = onChangeAspectRatio) {
                // TODO: Replace with AspectRatio icon when available
                Icon(
                    imageVector = Icons.Filled.CropFree,
                    contentDescription = "Aspect Ratio",
                )
            }
            IconButton(onClick = onSpeedClick) {
                // TODO: Replace with Speed icon when available
                Icon(
                    imageVector = Icons.Filled.AvTimer,
                    contentDescription = "Speed",
                )
            }
            IconButton(onClick = onTracksClick) {
                Icon(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = "Tracks",
                )
            }
            // Phase 4 Group 4: CC button
            // Visibility rules (Contract Section 8.1):
            // - Visible only for non-kid profiles
            // - Visible only if at least one subtitle track exists
            if (!state.kidActive && state.selectedSubtitleTrack != null) {
                IconButton(onClick = onCcClick) {
                    Icon(
                        imageVector = Icons.Filled.ClosedCaption,
                        contentDescription = "Subtitles & CC",
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Settings",
                )
            }
            IconButton(onClick = onPipClick) {
                // TODO: Replace with PictureInPictureAlt icon when available
                Icon(
                    imageVector = Icons.Filled.PictureInPicture,
                    contentDescription = "Picture-in-Picture",
                )
            }
        }
    }
}

@Composable
private fun ProgressRow(
    state: InternalPlayerUiState,
    onScrubTo: (Long) -> Unit,
) {
    val duration = state.durationMs.coerceAtLeast(0L)
    val position = state.positionMs.coerceIn(0L, duration)

    Column {
        Slider(
            value =
                if (duration > 0) {
                    position.toFloat() / duration.toFloat()
                } else {
                    0f
                },
            onValueChange = { fraction ->
                val target = (fraction * duration).toLong()
                onScrubTo(target)
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatMs(position))
            Text(formatMs(duration))
        }
    }
}

@Composable
fun SpeedDialog(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Playback speed") },
        text = {
            Column {
                speeds.forEach { s ->
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onSpeedSelected(s)
                            onDismiss()
                        },
                    ) {
                        Text(
                            text =
                                if (s == currentSpeed) {
                                    "● ${s}x"
                                } else {
                                    "${s}x"
                                },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
    )
}

@Composable
fun SleepTimerDialog(
    remainingMs: Long?,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDisable()
                    onDismiss()
                },
            ) {
                Text("Disable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Sleep timer") },
        text = {
            Text(
                text =
                    if (remainingMs == null) {
                        "No active sleep timer."
                    } else {
                        val totalSec = (remainingMs / 1000L).toInt()
                        val minutes = totalSec / 60
                        val seconds = totalSec % 60
                        "Remaining: %02d:%02d".format(minutes, seconds)
                    },
            )
        },
    )
}

/**
 * Minimalistische Tracks-Auswahl (Audio + Untertitel), optional.
 */
@Composable
fun TracksDialog(
    tracks: Tracks,
    onDismiss: () -> Unit,
    onApplyOverride: (TrackSelectionOverride?) -> Unit,
) {
    val audioGroups =
        remember(tracks) {
            tracks.groups
                .filter { it.type == C.TRACK_TYPE_AUDIO }
        }
    val textGroups =
        remember(tracks) {
            tracks.groups
                .filter { it.type == C.TRACK_TYPE_TEXT }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Tracks") },
        text = {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
            ) {
                item {
                    Text("Audio", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                items(audioGroups) { group ->
                    TrackGroupItem(group = group, isText = false, onApplyOverride = onApplyOverride)
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    Text("Subtitles", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                items(textGroups) { group ->
                    TrackGroupItem(group = group, isText = true, onApplyOverride = onApplyOverride)
                }
            }
        },
    )
}

@Composable
private fun TrackGroupItem(
    group: Tracks.Group,
    isText: Boolean,
    onApplyOverride: (TrackSelectionOverride?) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        for (i in 0 until group.length) {
            val info = group.getTrackFormat(i)
            val label =
                info.label
                    ?: info.language
                    ?: if (isText) "Subtitle $i" else "Audio $i"

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                    onApplyOverride(override)
                },
            ) {
                Text(label ?: "Track $i")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun DebugInfoOverlay(
    state: InternalPlayerUiState,
    player: ExoPlayer?,
) {
    ElevatedCard(
        modifier =
            Modifier
                .padding(8.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(8.dp),
        ) {
            Text("Debug", style = MaterialTheme.typography.titleMedium)
            val pos = state.positionMs
            val dur = state.durationMs
            val speed = state.playbackSpeed
            val loop = state.isLooping
            val buf = state.isBuffering
            Text("Pos: ${formatMs(pos)} / ${formatMs(dur)}")
            Text("Speed: ${"%.2fx".format(speed)}  Loop: $loop  Buffering: $buf")
            player?.let {
                Text("Tracks: ${it.currentTracks.groups.size}")
            }
        }
    }
}

/**
 * Phase 3 Step 3.C: Live channel name header for LIVE playback.
 *
 * Displays the current channel name at the top of the player.
 * Only rendered when playbackType == LIVE and liveChannelName != null.
 */
@Composable
private fun LiveChannelHeader(
    channelName: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard {
            Text(
                text = channelName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Phase 3 Step 3.C: EPG overlay for LIVE playback.
 *
 * Displays "Now" and "Next" program titles when available.
 * Only rendered when epgOverlayVisible == true.
 *
 * The overlay is positioned at the bottom-left and does not interfere
 * with existing controls.
 */
@Composable
private fun LiveEpgOverlay(
    nowTitle: String?,
    nextTitle: String?,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(12.dp),
        ) {
            Text(
                text = "EPG",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))

            if (nowTitle != null) {
                Text(
                    text = "Now: $nowTitle",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (nextTitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Next: $nextTitle",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Show placeholder if both are null
            if (nowTitle == null && nextTitle == null) {
                Text(
                    text = "No EPG data available",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = (ms / 1000L).toInt()
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}
