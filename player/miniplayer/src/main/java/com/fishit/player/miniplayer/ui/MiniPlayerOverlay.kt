package com.fishit.player.miniplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fishit.player.internal.state.InternalPlayerState
import com.fishit.player.miniplayer.MiniPlayerAnchor
import com.fishit.player.miniplayer.MiniPlayerManager
import com.fishit.player.miniplayer.MiniPlayerMode
import com.fishit.player.miniplayer.MiniPlayerState
import com.fishit.player.miniplayer.SAFE_MARGIN_DP
import kotlin.math.roundToInt

/**
 * Animation duration constants.
 */
private const val ANIMATION_DURATION_MS = 200

/**
 * Visual constants for the MiniPlayer.
 */
private val CORNER_RADIUS = 16.dp
private val SHADOW_ELEVATION = 12.dp
private val RESIZE_SCALE = 1.03f
private val RESIZE_BORDER_WIDTH = 3.dp

/**
 * MiniPlayer overlay composable.
 *
 * ════════════════════════════════════════════════════════════════════════════════
 * PHASE 5 – In-App MiniPlayer UI
 * ════════════════════════════════════════════════════════════════════════════════
 *
 * This composable renders a small video placeholder in a corner of the screen.
 * It provides:
 * - Play/Pause toggle
 * - Tap to expand to full player
 * - Close button to dismiss
 * - Visual polish (drop shadow, rounded corners)
 * - Resize mode visuals (border, scale-up)
 * - Smooth animations for show/hide and size changes
 * - Touch gestures for drag to move
 *
 * **Note:** The actual video surface (PlayerView) should be provided by the host
 * app through the [videoContent] slot, as this module doesn't depend on Media3.
 *
 * @param miniPlayerState Current MiniPlayer state
 * @param playerState Current player state (for play/pause)
 * @param miniPlayerManager Manager for state updates
 * @param onRequestFullPlayer Callback when user taps to expand
 * @param onDismiss Callback when user dismisses the MiniPlayer
 * @param onTogglePlayPause Callback to toggle playback
 * @param videoContent Composable slot for the video surface
 * @param modifier Modifier for the overlay container
 */
@Composable
fun MiniPlayerOverlay(
    miniPlayerState: MiniPlayerState,
    playerState: InternalPlayerState,
    miniPlayerManager: MiniPlayerManager,
    onRequestFullPlayer: () -> Unit,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    videoContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // Determine if we're in resize mode
    val isResizeMode = miniPlayerState.mode == MiniPlayerMode.RESIZE

    // Animated size for smooth resize transitions
    val animatedWidth by animateDpAsState(
        targetValue = miniPlayerState.size.width,
        animationSpec = tween(ANIMATION_DURATION_MS),
        label = "miniPlayerWidth"
    )
    val animatedHeight by animateDpAsState(
        targetValue = miniPlayerState.size.height,
        animationSpec = tween(ANIMATION_DURATION_MS),
        label = "miniPlayerHeight"
    )

    // Scale up slightly when in resize mode for visual feedback
    val scale by animateFloatAsState(
        targetValue = if (isResizeMode) RESIZE_SCALE else 1f,
        animationSpec = tween(ANIMATION_DURATION_MS),
        label = "miniPlayerScale"
    )

    AnimatedVisibility(
        visible = miniPlayerState.visible,
        enter = fadeIn(tween(ANIMATION_DURATION_MS)) + slideInVertically(tween(ANIMATION_DURATION_MS)) { it },
        exit = fadeOut(tween(ANIMATION_DURATION_MS)) + slideOutVertically(tween(ANIMATION_DURATION_MS)) { it },
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidthPx = constraints.maxWidth.toFloat()
            val screenHeightPx = constraints.maxHeight.toFloat()
            val safeMarginPx = with(density) { SAFE_MARGIN_DP.toPx() }
            val miniWidthPx = with(density) { animatedWidth.toPx() }
            val miniHeightPx = with(density) { animatedHeight.toPx() }

            // Calculate position based on anchor
            val anchorOffset = calculateAnchorOffset(
                anchor = miniPlayerState.anchor,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                miniWidthPx = miniWidthPx,
                miniHeightPx = miniHeightPx,
                safeMarginPx = safeMarginPx
            )

            // Apply custom position offset if set
            val totalOffset = Offset(
                x = anchorOffset.x + (miniPlayerState.position?.x ?: 0f),
                y = anchorOffset.y + (miniPlayerState.position?.y ?: 0f)
            )

            // Track drag state
            var dragOffset by remember { mutableStateOf(Offset.Zero) }

            Surface(
                modifier = Modifier
                    .offset { IntOffset(totalOffset.x.roundToInt(), totalOffset.y.roundToInt()) }
                    .size(animatedWidth, animatedHeight)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(SHADOW_ELEVATION, RoundedCornerShape(CORNER_RADIUS))
                    .clip(RoundedCornerShape(CORNER_RADIUS))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                if (!isResizeMode) {
                                    miniPlayerManager.enterResizeMode()
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                miniPlayerManager.moveBy(dragAmount)
                            },
                            onDragEnd = {
                                miniPlayerManager.snapToNearestAnchor(
                                    screenWidthPx = screenWidthPx,
                                    screenHeightPx = screenHeightPx,
                                    density = density
                                )
                                miniPlayerManager.confirmResize()
                            }
                        )
                    }
                    .clickable { onRequestFullPlayer() },
                shape = RoundedCornerShape(CORNER_RADIUS),
                border = if (isResizeMode) {
                    BorderStroke(RESIZE_BORDER_WIDTH, MaterialTheme.colorScheme.primary)
                } else null,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = SHADOW_ELEVATION
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Video content slot
                    videoContent()

                    // Semi-transparent control overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )

                    // Control buttons
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play/Pause button
                        IconButton(onClick = onTogglePlayPause) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = Color.White
                            )
                        }

                        // Expand button
                        IconButton(onClick = onRequestFullPlayer) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Expand",
                                tint = Color.White
                            )
                        }
                    }

                    // Close button in corner
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    // Resize mode indicator
                    if (isResizeMode) {
                        Text(
                            text = "Resize Mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calculate the base offset for an anchor position.
 */
private fun calculateAnchorOffset(
    anchor: MiniPlayerAnchor,
    screenWidthPx: Float,
    screenHeightPx: Float,
    miniWidthPx: Float,
    miniHeightPx: Float,
    safeMarginPx: Float
): Offset {
    return when (anchor) {
        MiniPlayerAnchor.TOP_LEFT -> Offset(safeMarginPx, safeMarginPx)
        MiniPlayerAnchor.TOP_RIGHT -> Offset(screenWidthPx - miniWidthPx - safeMarginPx, safeMarginPx)
        MiniPlayerAnchor.BOTTOM_LEFT -> Offset(safeMarginPx, screenHeightPx - miniHeightPx - safeMarginPx)
        MiniPlayerAnchor.BOTTOM_RIGHT -> Offset(
            screenWidthPx - miniWidthPx - safeMarginPx,
            screenHeightPx - miniHeightPx - safeMarginPx
        )
        MiniPlayerAnchor.CENTER_TOP -> Offset(
            (screenWidthPx - miniWidthPx) / 2,
            safeMarginPx
        )
        MiniPlayerAnchor.CENTER_BOTTOM -> Offset(
            (screenWidthPx - miniWidthPx) / 2,
            screenHeightPx - miniHeightPx - safeMarginPx
        )
    }
}
