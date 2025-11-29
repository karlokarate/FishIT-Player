package com.chris.m3usuite.player.miniplayer

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.chris.m3usuite.R
import com.chris.m3usuite.playback.PlaybackSession
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.focus.FocusZoneId
import com.chris.m3usuite.ui.focus.focusZone
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Animation duration constants.
 */
private const val ANIMATION_DURATION_MS = 200
private const val HINT_SHOW_DURATION_MS = 4000L

/**
 * Visual constants for the MiniPlayer.
 */
private val CORNER_RADIUS = 16.dp
private val SHADOW_ELEVATION = 12.dp
private val RESIZE_SCALE = 1.03f
private val RESIZE_BORDER_WIDTH = 3.dp

/**
 * MiniPlayer overlay composable for Phase 7.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 7 – In-App MiniPlayer UI with Polish & UX Improvements
 * PHASE 8 – UI Rebinding & Rotation Resilience
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This composable renders a small video surface in a corner of the screen using the
 * shared ExoPlayer from [PlaybackSession]. It provides:
 * - Play/Pause toggle
 * - Tap/click to open full player
 * - Visual polish (drop shadow, rounded corners, translucent control background)
 * - Resize mode visuals (border, scale-up, focus glow)
 * - Smooth animations for show/hide and size changes
 * - Touch gestures for non-TV devices (drag to move)
 * - First-time hints and resize mode hints
 *
 * **Key Principles:**
 * - Uses the shared PlaybackSession, never creates its own ExoPlayer
 * - Positions based on [MiniPlayerState.anchor]
 * - Sized according to [MiniPlayerState.size]
 * - Marked with [FocusZoneId.MINI_PLAYER] for TV focus management
 *
 * **Phase 8 Rotation Resilience (Contract Section 4.4):**
 * - MiniPlayerState is preserved across config changes via singleton DefaultMiniPlayerManager
 * - visible, mode, anchor, size, and position are maintained during rotation
 * - Video surface rebinds to existing PlaybackSession without recreating the player
 * - No flicker or reset of playback state beyond normal surface rebind
 *
 * **Contract Reference:**
 * - INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md Sections 3.2, 4.1, 4.2
 * - INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 4.4
 */
@OptIn(UnstableApi::class)
@Composable
fun MiniPlayerOverlay(
    playbackSession: PlaybackSession,
    miniPlayerState: MiniPlayerState,
    miniPlayerManager: MiniPlayerManager,
    onRequestFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isTv = remember { FocusKit.isTvDevice(context) }
    val player = PlaybackSession.current()
    val isPlaying by playbackSession.isPlaying.collectAsState()

    // State for first-time hint visibility
    var showFirstTimeHint by remember { mutableStateOf(false) }

    // Show first-time hint when MiniPlayer becomes visible and hint hasn't been shown
    LaunchedEffect(miniPlayerState.visible, miniPlayerState.hasShownFirstTimeHint) {
        if (miniPlayerState.visible && !miniPlayerState.hasShownFirstTimeHint) {
            showFirstTimeHint = true
            delay(HINT_SHOW_DURATION_MS)
            showFirstTimeHint = false
            miniPlayerManager.markFirstTimeHintShown()
        }
    }

    // Determine if we're in resize mode
    val isResizeMode = miniPlayerState.mode == MiniPlayerMode.RESIZE

    // Animated size for smooth resize transitions
    val animatedWidth by animateDpAsState(
        targetValue = miniPlayerState.size.width,
        animationSpec = tween(ANIMATION_DURATION_MS),
        label = "miniPlayerWidth",
    )
    val animatedHeight by animateDpAsState(
        targetValue = miniPlayerState.size.height,
        animationSpec = tween(ANIMATION_DURATION_MS),
        label = "miniPlayerHeight",
    )

    // Animated scale for resize mode
    val scale by animateFloatAsState(
        targetValue = if (isResizeMode) RESIZE_SCALE else 1f,
        animationSpec = tween(ANIMATION_DURATION_MS),
        label = "miniPlayerScale",
    )

    // Calculate alignment based on anchor
    val alignment =
        when (miniPlayerState.anchor) {
            MiniPlayerAnchor.TOP_LEFT -> Alignment.TopStart
            MiniPlayerAnchor.TOP_RIGHT -> Alignment.TopEnd
            MiniPlayerAnchor.BOTTOM_LEFT -> Alignment.BottomStart
            MiniPlayerAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
            MiniPlayerAnchor.CENTER_TOP -> Alignment.TopCenter
            MiniPlayerAnchor.CENTER_BOTTOM -> Alignment.BottomCenter
        }

    // Calculate safe padding from screen edges
    val safePadding = SAFE_MARGIN_DP + 8.dp

    // Calculate pixel offset from position (if set)
    val offsetX = miniPlayerState.position?.x?.roundToInt() ?: 0
    val offsetY = miniPlayerState.position?.y?.roundToInt() ?: 0

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .focusZone(FocusZoneId.MINI_PLAYER),
        contentAlignment = alignment,
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // Build the drag modifier for non-TV devices
        val dragModifier =
            if (!isTv) {
                Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // Snap to nearest anchor when drag ends
                            miniPlayerManager.snapToNearestAnchor(
                                screenWidthPx = screenWidthPx,
                                screenHeightPx = screenHeightPx,
                                density = density,
                            )
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Enter resize mode if not already in it (for drag-to-move)
                            if (miniPlayerState.mode != MiniPlayerMode.RESIZE) {
                                miniPlayerManager.enterResizeMode()
                            }
                            miniPlayerManager.moveBy(
                                Offset(dragAmount.x, dragAmount.y),
                            )
                        },
                    )
                }
            } else {
                Modifier
            }

        // Main MiniPlayer surface with visual polish
        Surface(
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(CORNER_RADIUS),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
            border =
                if (isResizeMode) {
                    BorderStroke(RESIZE_BORDER_WIDTH, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
            modifier =
                Modifier
                    .padding(safePadding)
                    .offset { IntOffset(offsetX, offsetY) }
                    .shadow(
                        elevation = SHADOW_ELEVATION,
                        shape = RoundedCornerShape(CORNER_RADIUS),
                        clip = false,
                    ).clip(RoundedCornerShape(CORNER_RADIUS))
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.then(dragModifier),
        ) {
            Column(
                modifier =
                    Modifier
                        .then(FocusKit.run { Modifier.focusGroup() })
                        .padding(8.dp),
            ) {
                // Resize mode indicator label
                AnimatedVisibility(
                    visible = isResizeMode,
                    enter = fadeIn(tween(ANIMATION_DURATION_MS / 2)),
                    exit = fadeOut(tween(ANIMATION_DURATION_MS / 2)),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.miniplayer_resize_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                // Video surface with animated size
                // ═══════════════════════════════════════════════════════════════
                // Phase 8 Group 2: MiniPlayer surface rebinding on config changes
                // ═══════════════════════════════════════════════════════════════
                // On config changes (rotation):
                // - MiniPlayerState.visible/mode/anchor/size/position are preserved
                //   via the singleton DefaultMiniPlayerManager which survives recomposition
                // - The video surface rebinds to the existing player via update block
                // - Playback continues without interruption (no re-setting source)
                // - MiniPlayer dimensions/position are preserved via animated state
                AndroidView(
                    modifier =
                        Modifier.size(
                            width = animatedWidth,
                            height = animatedHeight,
                        ),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            // Phase 8: Set black background for consistent appearance during surface swap
                            setBackgroundColor(android.graphics.Color.BLACK)
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { view ->
                        // Phase 8: Re-attach player to surface on recomposition (e.g., after rotation)
                        // This does NOT re-set the media source - playback continues seamlessly
                        view.player = player
                    },
                )

                // Controls row with translucent background - only show in NORMAL mode
                AnimatedVisibility(
                    visible = !isResizeMode,
                    enter = fadeIn(tween(ANIMATION_DURATION_MS)),
                    exit = fadeOut(tween(ANIMATION_DURATION_MS)),
                ) {
                    // Translucent background behind controls
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Play/Pause button
                            val playPauseModifier =
                                FocusKit.run {
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
                                    imageVector =
                                        if (isPlaying) {
                                            Icons.Filled.Pause
                                        } else {
                                            Icons.Filled.PlayArrow
                                        },
                                    contentDescription =
                                        stringResource(
                                            if (isPlaying) {
                                                R.string.miniplayer_pause
                                            } else {
                                                R.string.miniplayer_play
                                            },
                                        ),
                                    modifier = Modifier.padding(12.dp),
                                )
                            }

                            // Expand to full player button
                            val expandModifier =
                                FocusKit.run {
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
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = expandModifier,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fullscreen,
                                    contentDescription = stringResource(R.string.miniplayer_expand),
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                }

                // Resize mode hint
                AnimatedVisibility(
                    visible = isResizeMode,
                    enter = fadeIn(tween(ANIMATION_DURATION_MS)),
                    exit = fadeOut(tween(ANIMATION_DURATION_MS)),
                ) {
                    Text(
                        text = stringResource(R.string.miniplayer_resize_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // First-time hint chip (floating above MiniPlayer)
        AnimatedVisibility(
            visible = showFirstTimeHint && !isResizeMode && isTv,
            enter = fadeIn(tween(ANIMATION_DURATION_MS)) + slideInVertically { -it },
            exit = fadeOut(tween(ANIMATION_DURATION_MS)) + slideOutVertically { -it },
            modifier =
                Modifier
                    .align(alignment)
                    .padding(safePadding)
                    .offset {
                        IntOffset(
                            x = offsetX,
                            y = offsetY - with(density) { (animatedHeight + 48.dp).roundToPx() },
                        )
                    },
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Text(
                    text = stringResource(R.string.miniplayer_first_time_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
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
        enter =
            fadeIn(tween(ANIMATION_DURATION_MS)) +
                slideInVertically(tween(ANIMATION_DURATION_MS)) { it / 2 },
        exit =
            fadeOut(tween(ANIMATION_DURATION_MS)) +
                slideOutVertically(tween(ANIMATION_DURATION_MS)) { it / 2 },
        modifier = modifier,
    ) {
        MiniPlayerOverlay(
            playbackSession = PlaybackSession,
            miniPlayerState = state,
            miniPlayerManager = miniPlayerManager,
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
