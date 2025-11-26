package com.chris.m3usuite.player.internal.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.state.AspectRatioMode
import kotlin.math.abs

/**
 * PlayerSurface composable encapsulates the ExoPlayer PlayerView and handles gesture input.
 *
 * **Phase 3 Step 3.D**: This composable now supports horizontal swipe gestures for Live channel zapping.
 *
 * For LIVE playback:
 * - Horizontal swipe right → calls `onJumpLiveChannel(+1)` (next channel)
 * - Horizontal swipe left → calls `onJumpLiveChannel(-1)` (previous channel)
 *
 * For VOD/SERIES playback:
 * - Horizontal swipe behavior remains unchanged (future: seek/trickplay)
 * - `onJumpLiveChannel` callback is NOT invoked
 *
 * Gesture thresholds:
 * - Horizontal swipe threshold: 60px (matches legacy implementation)
 * - Distinguishes horizontal vs vertical gestures based on drag axis dominance
 *
 * @param player The ExoPlayer instance to render
 * @param aspectRatioMode The aspect ratio mode for the player view
 * @param playbackType The type of playback (VOD, SERIES, or LIVE)
 * @param onTap Callback invoked when the surface is tapped (typically toggles controls)
 * @param onJumpLiveChannel Callback for Live channel zapping. Only invoked for LIVE playback.
 *                          Delta values: +1 for next channel, -1 for previous channel.
 *                          Defaults to no-op for non-LIVE usage.
 */
@Composable
fun PlayerSurface(
    player: ExoPlayer?,
    aspectRatioMode: AspectRatioMode,
    playbackType: PlaybackType,
    onTap: () -> Unit = {},
    onJumpLiveChannel: (delta: Int) -> Unit = {},
) {
    // Track drag deltas for gesture recognition
    var dragDeltaX by remember { mutableStateOf(0f) }
    var dragDeltaY by remember { mutableStateOf(0f) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // Tap gesture: toggle controls visibility
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                }
                // Drag gestures: Live channel zapping (LIVE only)
                .pointerInput(playbackType) {
                    val threshold = 60f
                    detectDragGestures(
                        onDrag = { _, dragAmount ->
                            dragDeltaX += dragAmount.x
                            dragDeltaY += dragAmount.y
                        },
                        onDragEnd = {
                            // Only handle gestures for LIVE playback
                            if (playbackType == PlaybackType.LIVE) {
                                // Determine gesture direction based on dominant axis
                                if (abs(dragDeltaX) > abs(dragDeltaY) && abs(dragDeltaX) > threshold) {
                                    // Horizontal swipe detected
                                    val delta = if (dragDeltaX < 0) +1 else -1
                                    onJumpLiveChannel(delta)
                                }
                                // Note: Vertical swipe handling (live list sheet, EPG overlay)
                                // is deferred to Phase 3 future work (not part of Step 3.D)
                            }
                            // For VOD/SERIES: Future phases will add seek/trickplay here
                            // Currently, gestures are ignored for non-LIVE content

                            // Reset deltas
                            dragDeltaX = 0f
                            dragDeltaY = 0f
                        },
                    )
                },
    ) {
        // PlayerView rendering
        if (player != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = false // Controls managed by InternalPlayerControls
                        resizeMode = aspectRatioMode.toResizeMode()
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.player = player
                    view.resizeMode = aspectRatioMode.toResizeMode()
                },
            )
        }
    }
}

/**
 * Converts [AspectRatioMode] to Media3 [AspectRatioFrameLayout] resize mode.
 */
private fun AspectRatioMode.toResizeMode(): Int =
    when (this) {
        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    }
