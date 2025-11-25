package com.chris.m3usuite.player.internal.ui

import android.app.Activity
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
        // Hier würdest du die eigentliche Video-Surface einbauen (PlayerView/AndroidView)
        // In deinem bestehenden Code ist das der PlayerView-Block – den kannst du 1:1 hierher ziehen.

        // ════════════════════════════════════════════════════════════════════════════
        // Phase 3 Step 3: Live TV EPG Overlay (SIP only)
        // ════════════════════════════════════════════════════════════════════════════
        //
        // Renders EPG overlay for LIVE playback when epgOverlayVisible is true.
        // This overlay shows:
        // - Current channel name (liveChannelName)
        // - Now playing title (liveNowTitle)
        // - Up next title (liveNextTitle)
        //
        // The overlay is visually lightweight and coexists with existing controls.
        // Legacy InternalPlayerScreen still owns runtime live UI until final migration.
        if (state.isLive && state.epgOverlayVisible) {
            LiveEpgOverlay(
                channelName = state.liveChannelName,
                nowTitle = state.liveNowTitle,
                nextTitle = state.liveNextTitle,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // Live channel name display (always visible for LIVE when name available)
        if (state.isLive && state.liveChannelName != null && !state.epgOverlayVisible) {
            LiveChannelNameBar(
                channelName = state.liveChannelName,
                modifier = Modifier.align(Alignment.TopCenter),
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
                onSettingsClick = controller.onToggleSettingsDialog,
                onPipClick = { requestPictureInPicture(activity) },
            )
            Spacer(Modifier.height(8.dp))
            // Hide progress row for LIVE content (no seeking)
            if (!state.isLive) {
                ProgressRow(state = state, onScrubTo = controller.onSeekTo)
            }
        }

        if (state.showDebugInfo) {
            DebugInfoOverlay(
                state = state,
                player = player,
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

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = (ms / 1000L).toInt()
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}

// ════════════════════════════════════════════════════════════════════════════════════════════════════
// Phase 3 Step 3: Live TV EPG Overlay Components (SIP only)
// ════════════════════════════════════════════════════════════════════════════════════════════════════
//
// These components render the Live TV EPG overlay for SIP-based playback.
// They are driven by InternalPlayerUiState fields populated by LivePlaybackController.
//
// Legacy InternalPlayerScreen still owns runtime live UI until final migration.

/**
 * EPG overlay bar showing channel name and now/next program titles.
 *
 * Displayed when:
 * - playbackType == LIVE
 * - epgOverlayVisible == true
 *
 * Auto-hides based on LivePlaybackController timing logic.
 */
@Composable
fun LiveEpgOverlay(
    channelName: String?,
    nowTitle: String?,
    nextTitle: String?,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            // Channel name
            if (channelName != null) {
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
            }

            // Now/Next row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Now playing
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Now",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = nowTitle ?: "No information",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }

                // Up next
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Next",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = nextTitle ?: "No information",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Simple channel name bar when EPG overlay is hidden.
 *
 * Shows just the channel name for context during LIVE playback.
 */
@Composable
fun LiveChannelNameBar(
    channelName: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier =
            modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = channelName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
