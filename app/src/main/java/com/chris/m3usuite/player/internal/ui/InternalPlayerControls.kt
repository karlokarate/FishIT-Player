package com.chris.m3usuite.player.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.chris.m3usuite.core.debug.GlobalDebug
import com.chris.m3usuite.playback.PlaybackSession
import com.chris.m3usuite.player.internal.state.InternalPlayerController
import com.chris.m3usuite.player.internal.state.InternalPlayerUiState
import com.chris.m3usuite.tv.input.GlobalTvInputHost
import com.chris.m3usuite.tv.input.TvAction
import com.chris.m3usuite.tv.input.TvInputController
import com.chris.m3usuite.tv.input.toTvScreenContext
import com.chris.m3usuite.ui.focus.FocusZoneId
import com.chris.m3usuite.ui.focus.focusZone
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Phase 5 Controls Constants.
 *
 * Named constants for auto-hide timeouts and UI styling.
 * Contract: INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md Section 7
 */
object ControlsConstants {
    /** Auto-hide timeout for TV devices (7 seconds). Contract: 5-7s for TV. */
    const val AUTO_HIDE_TIMEOUT_TV_MS = 7_000L

    /** Auto-hide timeout for phone/tablet devices (4 seconds). Contract: 3-5s for touch. */
    const val AUTO_HIDE_TIMEOUT_TOUCH_MS = 4_000L

    /** Overlay background opacity (semi-transparent black). */
    const val OVERLAY_BACKGROUND_OPACITY = 0.7f

    /** Animation duration for fade transitions (ms). */
    const val FADE_ANIMATION_DURATION_MS = 150

    /** Animation duration for controls fade transitions (ms). */
    const val CONTROLS_FADE_ANIMATION_DURATION_MS = 200
}

/**
 * InternalPlayerContent - Main SIP player content composable
 *
 * **Phase 5 Group 3 & 4:**
 * - Trickplay indicator overlay when trickplayActive is true
 * - Seek preview overlay when seekPreviewVisible is true
 * - Controls auto-hide with configurable timeouts (TV: 7s, phone: 4s)
 * - Tap-to-toggle controls visibility
 *
 * **Phase 6 Task 3:**
 * - Optional TV input host integration for global key event handling
 * - Reads quickActionsVisible and focusedAction from TvInputController
 * - Logs TV input events via GlobalDebug when debug sink is connected
 *
 * @param player The ExoPlayer instance
 * @param state The player UI state
 * @param controller The player controller callbacks
 * @param isTv Whether running on TV (affects auto-hide timeout and input handling)
 * @param tvInputHost Optional GlobalTvInputHost for TV key event routing (Phase 6)
 * @param tvInputController Optional TvInputController for observing quick actions state (Phase 6)
 */
@Composable
fun InternalPlayerContent(
    player: ExoPlayer?,
    state: InternalPlayerUiState,
    controller: InternalPlayerController,
    isTv: Boolean = false,
    tvInputHost: GlobalTvInputHost? = null,
    tvInputController: TvInputController? = null,
) {
    val ctx = LocalContext.current
    // Note: Activity was previously used for native PiP, but Phase 7 removed that call
    // from this UI button. Native PiP is now only used for lifecycle-based entry.

    // ════════════════════════════════════════════════════════════════════════════════
    // Phase 8 Task 6b: Collect playback error from PlaybackSession
    // ════════════════════════════════════════════════════════════════════════════════
    val playbackError by PlaybackSession.playbackError.collectAsState()

    // ════════════════════════════════════════════════════════════════════════════
    // Phase 6 Task 3: Observe TV input controller state
    // ════════════════════════════════════════════════════════════════════════════
    // For now, log the focused action via GlobalDebug when it changes
    // Future: Use quickActionsVisible to show/hide quick actions panel
    val quickActionsVisible: State<Boolean>? = tvInputController?.quickActionsVisible
    val focusedAction: State<TvAction?>? = tvInputController?.focusedAction

    LaunchedEffect(focusedAction?.value) {
        focusedAction?.value?.let { action ->
            GlobalDebug.logDpad(
                action = "TvInput:FocusedAction",
                extras = mapOf("action" to action.name),
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Phase 5 Group 4: Auto-hide timer
    // ════════════════════════════════════════════════════════════════════════════
    // Contract Section 7.2: TV 5-7s, phone 3-5s timeouts
    val autoHideTimeoutMs =
        if (isTv) {
            ControlsConstants.AUTO_HIDE_TIMEOUT_TV_MS
        } else {
            ControlsConstants.AUTO_HIDE_TIMEOUT_TOUCH_MS
        }

    // Auto-hide effect: Hides controls after timeout when no blocking overlay is open
    LaunchedEffect(state.controlsVisible, state.controlsTick, state.hasBlockingOverlay, state.trickplayActive) {
        if (!state.controlsVisible) return@LaunchedEffect

        // Never auto-hide when:
        // - A blocking overlay is open (CC menu, settings, etc.)
        // - Trickplay is actively being adjusted
        if (state.hasBlockingOverlay || state.trickplayActive) return@LaunchedEffect

        val startTick = state.controlsTick
        delay(autoHideTimeoutMs)

        // Only hide if no new activity occurred
        if (state.controlsTick == startTick) {
            controller.onHideControls()
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Phase 6 Task 3: Build TvScreenContext from player state
    // ════════════════════════════════════════════════════════════════════════════
    // This creates a context that reflects the player's current state for
    // the TV input pipeline to use for action resolution.
    val tvScreenContext = remember(state) { state.toTvScreenContext() }

    // Build the modifier with optional TV key event handling
    val boxModifier =
        if (isTv && tvInputHost != null) {
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { keyEvent ->
                    // Convert Compose key event to Android KeyEvent for the host
                    val androidKeyEvent = keyEvent.nativeKeyEvent
                    // Forward to GlobalTvInputHost for processing
                    val handled = tvInputHost.handleKeyEvent(androidKeyEvent, tvScreenContext)
                    // If handled by TV input system, consume the event
                    // Otherwise let it fall through to existing handlers
                    handled
                }
        } else {
            Modifier.fillMaxSize()
        }

    Box(modifier = boxModifier) {
        // Phase 3 Step 3.D + Phase 4 Group 3 + Phase 5 Groups 3 & 4:
        // PlayerSurface with gesture handling, subtitle styling, trickplay, and tap-to-toggle
        PlayerSurface(
            player = player,
            aspectRatioMode = state.aspectRatioMode,
            playbackType = state.playbackType,
            subtitleStyle = state.subtitleStyle,
            isKidMode = state.kidActive,
            onTap = {
                // Phase 5 Group 4: Tap toggles controls visibility
                controller.onToggleControlsVisibility()
                controller.onUserInteraction()
            },
            onJumpLiveChannel = controller.onJumpLiveChannel,
            onStepSeek = { deltaMs ->
                // Phase 5 Group 3: Step seek for VOD/SERIES
                controller.onStepSeek(deltaMs)
                controller.onUserInteraction()
            },
            onUserInteraction = controller.onUserInteraction,
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

        // ════════════════════════════════════════════════════════════════════════
        // Phase 5 Group 3: Trickplay Indicator Overlay
        // ════════════════════════════════════════════════════════════════════════
        // Contract Section 6.2 Rule 2: Clear visual feedback during trickplay
        AnimatedVisibility(
            visible = state.trickplayActive,
            enter =
                fadeIn(
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = ControlsConstants.FADE_ANIMATION_DURATION_MS,
                        ),
                ),
            exit =
                fadeOut(
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = ControlsConstants.FADE_ANIMATION_DURATION_MS,
                        ),
                ),
            modifier = Modifier.align(Alignment.Center),
        ) {
            TrickplayIndicator(speed = state.trickplaySpeed)
        }

        // ════════════════════════════════════════════════════════════════════════
        // Phase 5 Group 3: Seek Preview Overlay
        // ════════════════════════════════════════════════════════════════════════
        // Shows target position during seek preview
        AnimatedVisibility(
            visible = state.seekPreviewVisible && state.seekPreviewTargetMs != null,
            enter =
                fadeIn(
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = ControlsConstants.FADE_ANIMATION_DURATION_MS,
                        ),
                ),
            exit =
                fadeOut(
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = ControlsConstants.FADE_ANIMATION_DURATION_MS,
                        ),
                ),
            modifier = Modifier.align(Alignment.Center),
        ) {
            state.seekPreviewTargetMs?.let { targetMs ->
                SeekPreviewOverlay(
                    currentPositionMs = state.positionMs,
                    targetPositionMs = targetMs,
                    durationMs = state.durationMs,
                )
            }
        }

        // Phase 3 Step 3.C & Task 2: EPG overlay (LIVE only, with AnimatedVisibility)
        // AnimatedVisibility uses epgOverlayVisible directly without delays
        AnimatedVisibility(
            visible = state.isLive && state.epgOverlayVisible,
            enter =
                fadeIn(
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = ControlsConstants.CONTROLS_FADE_ANIMATION_DURATION_MS,
                        ),
                ),
            exit =
                fadeOut(
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = ControlsConstants.CONTROLS_FADE_ANIMATION_DURATION_MS,
                        ),
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

        // Kid mode status hint
        if (state.kidActive && !state.kidBlocked) {
            val warn = state.remainingKidsMinutes != null && state.remainingKidsMinutes <= 5
            KidStatusHint(
                remainingMinutes = state.remainingKidsMinutes,
                warn = warn,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
            )
        }

        // Kid block overlay
        if (state.kidBlocked) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier =
                        Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp),
                            ).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Bildschirmzeit abgelaufen",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    val remainText =
                        state.remainingKidsMinutes?.let { "Restzeit: ${maxOf(it, 0)} min" }
                            ?: "Bitte zurück zur Startseite"
                    Spacer(Modifier.height(8.dp))
                    Text(text = remainText, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════════
        // Phase 5 Group 4: Controls with Auto-Hide
        // ════════════════════════════════════════════════════════════════════════
        // Contract Section 7.1: Controls auto-hide after period of inactivity
        AnimatedVisibility(
            visible = state.controlsVisible,
            enter =
                fadeIn(
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = ControlsConstants.CONTROLS_FADE_ANIMATION_DURATION_MS,
                        ),
                ),
            exit =
                fadeOut(
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = ControlsConstants.CONTROLS_FADE_ANIMATION_DURATION_MS,
                        ),
                ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Phase 6 Task 5: Mark controls container as PLAYER_CONTROLS FocusZone
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .focusZone(FocusZoneId.PLAYER_CONTROLS),
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
                    onCcClick = controller.onToggleCcMenu,
                    onSettingsClick = controller.onToggleSettingsDialog,
                    // Phase 7: PIP button now uses MiniPlayerManager instead of native PiP
                    // No more enterPictureInPictureMode() calls from UI button
                    onPipClick = controller.onEnterMiniPlayer,
                )
                Spacer(Modifier.height(8.dp))
                // Phase 6 Task 5: The timeline/seekbar is part of PLAYER_CONTROLS zone
                // A dedicated TIMELINE zone can be added if separate focus behavior is needed
                ProgressRow(state = state, onScrubTo = controller.onSeekTo)
            }
        }

        if (state.showDebugInfo) {
            DebugInfoOverlay(
                state = state,
                player = player,
            )
        }

        // ════════════════════════════════════════════════════════════════════════
        // Phase 8 Task 6b: Playback Error Overlay
        // ════════════════════════════════════════════════════════════════════════
        // Soft error display with Retry/Close options.
        // Non-blocking: Player UI remains visible in the background.
        // Kids Mode uses generic messages (no technical details).
        PlaybackErrorOverlay(
            error = playbackError,
            isKidMode = state.kidActive,
            onRetry = { PlaybackSession.retry() },
            onClose = {
                PlaybackSession.clearError()
                PlaybackSession.stop()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp), // Above controls
        )

        // Phase 4 Group 4: CC Menu Dialog
        // Shows when state.showCcMenuDialog is true
        // Contract Section 8: CC/Subtitle UI for SIP Only
        if (state.showCcMenuDialog && !state.kidActive) {
            CcMenuDialog(
                currentStyle = state.subtitleStyle,
                availableTracks = state.availableSubtitleTracks,
                selectedTrack = state.selectedSubtitleTrack,
                onDismiss = controller.onToggleCcMenu,
                onApplyStyle = { style ->
                    // Wire to controller callback which delegates to SubtitleStyleManager.updateStyle()
                    controller.onUpdateSubtitleStyle(style)
                },
                onApplyPreset = { preset ->
                    // Wire to controller callback which delegates to SubtitleStyleManager.applyPreset()
                    controller.onApplySubtitlePreset(preset)
                },
                onSelectTrack = { track ->
                    // Wire to controller callback which applies track selection to player
                    controller.onSelectSubtitleTrack(track)
                },
            )
        }
    }
}

/**
 * Trickplay indicator overlay showing speed and direction.
 *
 * Phase 5 Group 3: Contract Section 6.2 Rule 2
 *
 * Display format:
 * - Positive speed: "2x ►►" (fast-forward)
 * - Negative speed: "◀◀ 2x" (rewind)
 */
@Composable
private fun TrickplayIndicator(speed: Float) {
    val isForward = speed > 0
    val absSpeed = abs(speed)
    val speedText =
        if (absSpeed == absSpeed.toInt().toFloat()) {
            "${absSpeed.toInt()}x"
        } else {
            "${"%.1f".format(absSpeed)}x"
        }

    Box(
        modifier =
            Modifier
                .background(
                    color = Color.Black.copy(alpha = ControlsConstants.OVERLAY_BACKGROUND_OPACITY),
                    shape = RoundedCornerShape(8.dp),
                ).padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (!isForward) {
                // Rewind: ◀◀ 2x
                Text(
                    text = "◀◀",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = speedText,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            if (isForward) {
                // Forward: 2x ►►
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "►►",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Seek preview overlay showing current position → target position.
 *
 * Phase 5 Group 3: Contract Section 6.2 Rule 2
 */
@Composable
private fun SeekPreviewOverlay(
    currentPositionMs: Long,
    targetPositionMs: Long,
    durationMs: Long,
) {
    Box(
        modifier =
            Modifier
                .background(
                    color = Color.Black.copy(alpha = ControlsConstants.OVERLAY_BACKGROUND_OPACITY),
                    shape = RoundedCornerShape(8.dp),
                ).padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Target position (large)
            Text(
                text = formatMs(targetPositionMs),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )

            // Current → Target indicator
            val delta = targetPositionMs - currentPositionMs
            val sign = if (delta >= 0) "+" else ""
            Text(
                text = "$sign${formatMs(abs(delta))}",
                color = Color.White.copy(alpha = ControlsConstants.OVERLAY_BACKGROUND_OPACITY),
                fontSize = 16.sp,
            )

            // Progress indicator
            if (durationMs > 0) {
                Spacer(Modifier.height(8.dp))
                val progress = (targetPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )
            }
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
            if (!state.kidActive && state.availableSubtitleTracks.isNotEmpty()) {
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

@Composable
private fun KidStatusHint(
    remainingMinutes: Int?,
    warn: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg =
        if (warn) {
            Color(0xFFB71C1C).copy(alpha = 0.8f)
        } else {
            Color(0xFF2E7D32).copy(alpha = 0.8f)
        }
    val text =
        when {
            remainingMinutes == null -> "Kid-Profil aktiv"
            remainingMinutes <= 0 -> "Zeit abgelaufen"
            else -> "Kid: $remainingMinutes min"
        }
    Text(
        text = text,
        color = Color.White,
        modifier =
            modifier
                .background(bg, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        fontWeight = FontWeight.SemiBold,
    )
}
