package com.chris.m3usuite.player.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chris.m3usuite.player.internal.state.PlayerHotState
import kotlin.math.abs

/**
 * Isolated playback indicator Composables for Phase 8 performance optimization.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 5: Compose & FocusKit Performance Hardening
 * Contract: INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 9.1
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * These small, focused Composables observe ONLY the PlayerHotState fields they need,
 * preventing unnecessary recomposition of large layout trees when position updates occur.
 *
 * **Design Principle:**
 * - Each Composable takes only the specific fields it needs from PlayerHotState
 * - Or takes a minimal PlayerHotState if multiple fields are needed
 * - Large layout Composables should use PlayerColdState and delegate hot paths to these
 *
 * **Contract Reference:**
 * - Section 9.1 Rule 1: Hot paths in isolated small Composables
 * - Section 9.1 Rule 2: Progress, isPlaying, buffering are hot paths
 */

/**
 * Progress bar that only recomposes when position or duration changes.
 *
 * This is a small, focused Composable that observes only position/duration.
 * It should be used instead of embedding progress logic in large layout Composables.
 *
 * @param positionMs Current playback position in milliseconds.
 * @param durationMs Total duration in milliseconds.
 * @param onScrubTo Callback when user scrubs to a new position.
 * @param modifier Modifier for the progress bar container.
 */
@Composable
fun PositionProgressBar(
    positionMs: Long,
    durationMs: Long,
    onScrubTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(0L)
    val position = positionMs.coerceIn(0L, duration)

    Column(modifier = modifier) {
        Slider(
            value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
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

/**
 * Time display that only shows current position and duration.
 *
 * @param positionMs Current playback position in milliseconds.
 * @param durationMs Total duration in milliseconds.
 * @param modifier Modifier for the time display.
 */
@Composable
fun PositionTimeDisplay(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "${formatMs(positionMs)} / ${formatMs(durationMs)}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}

/**
 * Buffering indicator that only shows when buffering is true.
 *
 * This small Composable should be observed separately from layout Composables
 * to prevent recomposition when buffering state toggles.
 *
 * @param isBuffering Whether buffering is currently occurring.
 * @param modifier Modifier for the indicator.
 */
@Composable
fun BufferingIndicator(
    isBuffering: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isBuffering,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp,
        )
    }
}

/**
 * Play/Pause state indicator icon.
 *
 * Observes only isPlaying field to minimize recomposition.
 *
 * @param isPlaying Whether media is currently playing.
 */
@Composable
fun PlayPauseStateIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val text = if (isPlaying) "▶" else "⏸"
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier,
    )
}

/**
 * Isolated trickplay indicator.
 *
 * Observes only trickplay-related fields from PlayerHotState.
 *
 * @param trickplayActive Whether trickplay is active.
 * @param trickplaySpeed Current trickplay speed multiplier.
 * @param modifier Modifier for the indicator.
 */
@Composable
fun IsolatedTrickplayIndicator(
    trickplayActive: Boolean,
    trickplaySpeed: Float,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = trickplayActive,
        enter = fadeIn(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = ControlsConstants.FADE_ANIMATION_DURATION_MS,
            ),
        ),
        exit = fadeOut(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = ControlsConstants.FADE_ANIMATION_DURATION_MS,
            ),
        ),
        modifier = modifier,
    ) {
        val isForward = trickplaySpeed > 0
        val absSpeed = abs(trickplaySpeed)
        val speedText =
            if (absSpeed == absSpeed.toInt().toFloat()) {
                "${absSpeed.toInt()}x"
            } else {
                "${"%.1f".format(absSpeed)}x"
            }

        Box(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = ControlsConstants.OVERLAY_BACKGROUND_OPACITY),
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (!isForward) {
                    Text(
                        text = "◀◀",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = speedText,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (isForward) {
                    Spacer(Modifier.height(8.dp))
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
}

/**
 * Isolated seek preview overlay.
 *
 * Observes only seek preview fields from PlayerHotState.
 *
 * @param visible Whether the seek preview is visible.
 * @param currentPositionMs Current playback position.
 * @param targetPositionMs Target seek position.
 * @param durationMs Total duration.
 * @param modifier Modifier for the overlay.
 */
@Composable
fun IsolatedSeekPreviewOverlay(
    visible: Boolean,
    currentPositionMs: Long,
    targetPositionMs: Long?,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && targetPositionMs != null,
        enter = fadeIn(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = ControlsConstants.FADE_ANIMATION_DURATION_MS,
            ),
        ),
        exit = fadeOut(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = ControlsConstants.FADE_ANIMATION_DURATION_MS,
            ),
        ),
        modifier = modifier,
    ) {
        targetPositionMs?.let { targetMs ->
            Box(
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = ControlsConstants.OVERLAY_BACKGROUND_OPACITY),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Target position (large)
                    Text(
                        text = formatMs(targetMs),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    // Current → Target indicator
                    val delta = targetMs - currentPositionMs
                    val sign = if (delta >= 0) "+" else ""
                    Text(
                        text = "$sign${formatMs(abs(delta))}",
                        color = Color.White.copy(alpha = ControlsConstants.OVERLAY_BACKGROUND_OPACITY),
                        fontSize = 16.sp,
                    )

                    // Progress indicator
                    if (durationMs > 0) {
                        Spacer(Modifier.height(8.dp))
                        val progress = (targetMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper function to format milliseconds as MM:SS.
 */
private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = (ms / 1000L).toInt()
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}
