package com.chris.m3usuite.player.miniplayer

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.chris.m3usuite.playback.PlaybackSession
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.focus.FocusZoneId
import com.chris.m3usuite.ui.focus.focusZone
import kotlin.math.roundToInt

/**
 * MiniPlayer overlay composable for Phase 7.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – In-App MiniPlayer UI Skeleton (Group 3)
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This composable renders a small video surface in a corner of the screen using the
 * shared ExoPlayer from [PlaybackSession]. It provides minimal controls:
 * - Play/Pause toggle
 * - Tap/click to open full player
 *
 * **Key Principles:**
 * - Uses the shared PlaybackSession, never creates its own ExoPlayer
 * - Positions based on [MiniPlayerState.anchor]
 * - Sized according to [MiniPlayerState.size]
 * - Marked with [FocusZoneId.MINI_PLAYER] for TV focus management
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Sections 3.2, 4.1, 4.2
 */
@OptIn(UnstableApi::class)
@Composable
fun MiniPlayerOverlay(
    playbackSession: PlaybackSession,
    miniPlayerState: MiniPlayerState,
    onRequestFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isTv = remember { FocusKit.isTvDevice(context) }
    val player = PlaybackSession.current()
    val isPlaying by playbackSession.isPlaying.collectAsState()

    // Determine if we're in resize mode
    val isResizeMode = miniPlayerState.mode == MiniPlayerMode.RESIZE

    // Calculate alignment based on anchor
    val alignment = when (miniPlayerState.anchor) {
        MiniPlayerAnchor.TOP_LEFT -> Alignment.TopStart
        MiniPlayerAnchor.TOP_RIGHT -> Alignment.TopEnd
        MiniPlayerAnchor.BOTTOM_LEFT -> Alignment.BottomStart
        MiniPlayerAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
    }

    // Calculate padding to keep MiniPlayer away from edges
    val padding = 24.dp

    // Calculate pixel offset from position (if set)
    val density = LocalDensity.current
    val offsetX = miniPlayerState.position?.x?.roundToInt() ?: 0
    val offsetY = miniPlayerState.position?.y?.roundToInt() ?: 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusZone(FocusZoneId.MINI_PLAYER),
        contentAlignment = alignment,
    ) {
        // Surface with resize mode indicator (border)
        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
            border = if (isResizeMode) {
                BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
            modifier = Modifier
                .padding(padding)
                .offset { IntOffset(offsetX, offsetY) },
        ) {
            Column(
                modifier = Modifier
                    .then(FocusKit.run { Modifier.focusGroup() })
                    .padding(8.dp),
            ) {
                // Resize mode indicator label
                if (isResizeMode) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Text(
                            text = "RESIZE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                // Video surface
                AndroidView(
                    modifier = Modifier
                        .size(
                            width = miniPlayerState.size.width,
                            height = miniPlayerState.size.height,
                        ),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    update = { view ->
                        view.player = player
                    },
                )

                // Controls row - only show in NORMAL mode
                if (!isResizeMode) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Play/Pause button
                        val playPauseModifier = FocusKit.run {
                            Modifier
                                .focusScaleOnTv(debugTag = "miniplayer:playpause")
                                .tvClickable(
                                    enabled = true,
                                    debugTag = "miniplayer:playpause",
                                    onClick = {
                                        playbackSession.togglePlayPause()
                                    },
                                )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = playPauseModifier,
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.padding(12.dp),
                            )
                        }

                        // Expand to full player button
                        val expandModifier = FocusKit.run {
                            Modifier
                                .focusScaleOnTv(debugTag = "miniplayer:expand")
                                .tvClickable(
                                    enabled = true,
                                    debugTag = "miniplayer:expand",
                                    onClick = onRequestFullPlayer,
                                )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = expandModifier,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fullscreen,
                                contentDescription = "Expand to full player",
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                } else {
                    // Resize mode hint
                    Text(
                        text = "FF/RW: Size • DPAD: Move • OK: Confirm • Back: Cancel",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Container composable that shows the MiniPlayer overlay when visible.
 *
 * This composable should be placed at the root level of the app scaffold (e.g., in HomeChromeScaffold)
 * so that it renders above all screens.
 *
 * Usage:
 * ```kotlin
 * Box {
 *     AppScreens()
 *     MiniPlayerOverlayContainer(
 *         miniPlayerManager = DefaultMiniPlayerManager,
 *         onRequestFullPlayer = { navController.navigate(playerRoute) }
 *     )
 * }
 * ```
 *
 * @param miniPlayerManager The MiniPlayer state manager
 * @param onRequestFullPlayer Callback when user wants to expand to full player
 */
@Composable
fun MiniPlayerOverlayContainer(
    miniPlayerManager: MiniPlayerManager,
    onRequestFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by miniPlayerManager.state.collectAsState()
    val isResizeMode = state.mode == MiniPlayerMode.RESIZE

    AnimatedVisibility(
        visible = state.visible && PlaybackSession.current() != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        MiniPlayerOverlay(
            playbackSession = PlaybackSession,
            miniPlayerState = state,
            onRequestFullPlayer = {
                // If in resize mode, cancel it first
                if (isResizeMode) {
                    miniPlayerManager.cancelResize()
                }
                miniPlayerManager.exitMiniPlayer(returnToFullPlayer = true)
                onRequestFullPlayer()
            },
        )
    }
}
