package com.fishit.player.internal.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fishit.player.internal.R
import com.fishit.player.internal.state.InternalPlayerState
import kotlinx.coroutines.delay

/** Auto-hide controls after this duration of inactivity. */
private const val CONTROLS_AUTO_HIDE_MS = 3000L

/** Debounce delay for seek operations (TV remote spam protection). */
private const val SEEK_DEBOUNCE_MS = 200L

/**
 * Player control overlay with play/pause, seek, and progress.
 *
 * Performance optimizations:
 * - Slider uses local seeking state to avoid recomposition storm
 * - Time display uses derivedStateOf to minimize recompositions
 * - Seek operations are debounced for TV remote protection
 * - Auto-hide timer resets on any user interaction
 */
@Composable
fun InternalPlayerControls(
    state: InternalPlayerState,
    onTogglePlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onTapSurface: () -> Unit,
    onHideControls: () -> Unit = {},
    // Track selection callbacks (Phase 6/7 wiring)
    onAudioTrackClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    onSpeedClick: () -> Unit = {},
    // State indicators for button styling
    hasSubtitles: Boolean = false,
    hasMultipleAudioTracks: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Auto-hide timer: resets on any interaction
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Reset timer on any user action
    val resetAutoHide = {
        lastInteractionTime = System.currentTimeMillis()
    }

    // Auto-hide effect
    LaunchedEffect(state.areControlsVisible, lastInteractionTime) {
        if (state.areControlsVisible && !state.isLive) {
            delay(CONTROLS_AUTO_HIDE_MS)
            if (System.currentTimeMillis() - lastInteractionTime >= CONTROLS_AUTO_HIDE_MS) {
                onHideControls()
            }
        }
    }

    // Debounced seek handlers for TV remote protection
    var lastSeekTime by remember { mutableLongStateOf(0L) }
    val debouncedSeekForward = {
        val now = System.currentTimeMillis()
        if (now - lastSeekTime >= SEEK_DEBOUNCE_MS) {
            lastSeekTime = now
            resetAutoHide()
            onSeekForward()
        }
    }
    val debouncedSeekBackward = {
        val now = System.currentTimeMillis()
        if (now - lastSeekTime >= SEEK_DEBOUNCE_MS) {
            lastSeekTime = now
            resetAutoHide()
            onSeekBackward()
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        resetAutoHide()
                        onTapSurface()
                    },
                ),
    ) {
        AnimatedVisibility(
            visible = state.areControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Top gradient with title
                TopBar(
                    title = state.context?.title ?: "",
                    subtitle = state.context?.subtitle,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.weight(1f))

                // Center play controls
                CenterControls(
                    isPlaying = state.isPlaying,
                    onTogglePlayPause = {
                        resetAutoHide()
                        onTogglePlayPause()
                    },
                    onSeekForward = debouncedSeekForward,
                    onSeekBackward = debouncedSeekBackward,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.weight(1f))

                // Bottom bar with seek and time
                BottomBar(
                    state = state,
                    onSeekTo = { position ->
                        resetAutoHide()
                        onSeekTo(position)
                    },
                    onToggleMute = {
                        resetAutoHide()
                        onToggleMute()
                    },
                    onAudioTrackClick = {
                        resetAutoHide()
                        onAudioTrackClick()
                    },
                    onSubtitleClick = {
                        resetAutoHide()
                        onSubtitleClick()
                    },
                    onSpeedClick = {
                        resetAutoHide()
                        onSpeedClick()
                    },
                    hasSubtitles = hasSubtitles,
                    hasMultipleAudioTracks = hasMultipleAudioTracks,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                    ),
                )
                // Safe area padding for notch/cutout
                .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CenterControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Seek backward button with TV focus
        FocusableIconButton(
            onClick = onSeekBackward,
            contentDescription = stringResource(R.string.player_seek_backward),
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Replay10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Play/Pause button with crossfade animation and TV focus
        FocusableIconButton(
            onClick = onTogglePlayPause,
            contentDescription = if (isPlaying) {
                stringResource(R.string.player_pause)
            } else {
                stringResource(R.string.player_play)
            },
            modifier = Modifier.size(72.dp),
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith
                        fadeOut(animationSpec = tween(150))
                },
                label = "PlayPauseAnimation",
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Seek forward button with TV focus
        FocusableIconButton(
            onClick = onSeekForward,
            contentDescription = stringResource(R.string.player_seek_forward),
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Forward10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

/**
 * IconButton wrapper with TV focus state visualization.
 * Shows a border when focused for Fire TV / Android TV navigation.
 */
@Composable
private fun FocusableIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomBar(
    state: InternalPlayerState,
    onSeekTo: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onAudioTrackClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onSpeedClick: () -> Unit,
    hasSubtitles: Boolean,
    hasMultipleAudioTracks: Boolean,
    modifier: Modifier = Modifier,
) {
    // Local seeking state to avoid recomposition storm during drag
    var isSeeking by remember { mutableStateOf(false) }
    var localSliderPosition by remember { mutableLongStateOf(state.positionMs) }

    // Sync local position when not seeking
    LaunchedEffect(state.positionMs, isSeeking) {
        if (!isSeeking) {
            localSliderPosition = state.positionMs
        }
    }

    // Toggle between elapsed and remaining time
    var showRemainingTime by remember { mutableStateOf(false) }

    // Optimized time strings using derivedStateOf to minimize recompositions
    val currentTimeText by remember(localSliderPosition, state.durationMs, showRemainingTime) {
        derivedStateOf {
            if (showRemainingTime) {
                "-${formatTime(state.durationMs - localSliderPosition)}"
            } else {
                formatTime(localSliderPosition)
            }
        }
    }
    val totalTimeText by remember(state.durationMs) {
        derivedStateOf { formatTime(state.durationMs) }
    }

    Column(
        modifier =
            modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    ),
                )
                // Safe area padding for navigation bar
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        // Progress slider with buffered progress
        if (!state.isLive) {
            Slider(
                value = if (isSeeking) localSliderPosition.toFloat() else state.positionMs.toFloat(),
                onValueChange = { value ->
                    isSeeking = true
                    localSliderPosition = value.toLong()
                },
                onValueChangeFinished = {
                    // Only seek when user releases the slider
                    onSeekTo(localSliderPosition)
                    isSeeking = false
                },
                valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f),
                colors =
                    SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                modifier = Modifier.fillMaxWidth(),
            )

            // Buffered progress indicator (secondary track simulation)
            if (state.bufferedPositionMs > 0 && state.durationMs > 0) {
                val bufferedFraction = (state.bufferedPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedFraction)
                        .height(2.dp)
                        .padding(start = 16.dp, end = 16.dp)
                        .background(Color.White.copy(alpha = 0.5f))
                )
            }
        } else {
            // Pulsing live indicator
            PulsingLiveIndicator()
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Time and volume row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Time display - clickable to toggle remaining time
            Text(
                text =
                    if (state.isLive) {
                        stringResource(R.string.player_live)
                    } else {
                        "$currentTimeText / $totalTimeText"
                    },
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showRemainingTime = !showRemainingTime },
            )

            // Control buttons row (right side)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Speed button
                FocusableIconButton(
                    onClick = onSpeedClick,
                    contentDescription = stringResource(R.string.player_playback_speed),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = if (state.playbackSpeed != 1.0f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White
                        },
                    )
                }

                // Audio track button (only show if multiple audio tracks)
                if (hasMultipleAudioTracks) {
                    FocusableIconButton(
                        onClick = onAudioTrackClick,
                        contentDescription = stringResource(R.string.player_audio_track),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }

                // Subtitle button
                FocusableIconButton(
                    onClick = onSubtitleClick,
                    contentDescription = stringResource(R.string.player_subtitles),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (hasSubtitles) {
                            Icons.Default.ClosedCaption
                        } else {
                            Icons.Default.ClosedCaptionOff
                        },
                        contentDescription = null,
                        tint = if (hasSubtitles) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White
                        },
                    )
                }

                // Volume button with TV focus
                FocusableIconButton(
                    onClick = onToggleMute,
                    contentDescription = if (state.isMuted) {
                        stringResource(R.string.player_unmute)
                    } else {
                        stringResource(R.string.player_mute)
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Pulsing live indicator with GPU-efficient infinite animation.
 */
@Composable
private fun PulsingLiveIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "LivePulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "LivePulseAlpha",
    )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .alpha(alpha)
                        .background(Color.Red, shape = CircleShape),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.player_live),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * Formats milliseconds as MM:SS or HH:MM:SS.
 */
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
