package com.chris.m3usuite.player.internal.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.chris.m3usuite.player.internal.domain.PlaybackType
import kotlin.math.abs

/**
 * Player surface composable with gesture handling for SIP-based playback.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 3 STEP 3: GESTURE HOOKS (SIP ONLY)
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This composable wraps the video surface and provides gesture handling:
 *
 * **Horizontal swipe:**
 * - For LIVE: Jump channel (+1/-1) via [onJumpLiveChannel]
 * - For VOD/SERIES: Seek/trickplay via [onSeekBy] (unchanged legacy behavior)
 *
 * **Vertical swipe:**
 * - For LIVE: Toggle live list/quick actions via [onToggleLiveList]
 * - For VOD/SERIES: No action (future: brightness/volume)
 *
 * **Tap:**
 * - Toggle controls visibility via [onToggleControls]
 *
 * **Note:** This does NOT change existing DPAD/TV key handling (Phase 6).
 * Does NOT introduce new navigation routes.
 *
 * @param playbackType The current playback type (VOD, SERIES, LIVE)
 * @param onToggleControls Called when user taps to toggle control visibility
 * @param onSeekBy Called for VOD/SERIES horizontal swipe with seek delta in ms
 * @param onJumpLiveChannel Called for LIVE horizontal swipe with channel delta (+1/-1)
 * @param onToggleLiveList Called for LIVE vertical swipe to toggle channel list
 * @param modifier Modifier for the container
 * @param content The video surface content (typically AndroidView with PlayerView)
 */
@Composable
fun PlayerSurface(
    playbackType: PlaybackType,
    onToggleControls: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onJumpLiveChannel: (delta: Int) -> Unit,
    onToggleLiveList: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Track accumulated horizontal drag for gesture detection
    var horizontalDragAccumulated by remember { mutableFloatStateOf(0f) }
    var verticalDragAccumulated by remember { mutableFloatStateOf(0f) }

    // Gesture thresholds
    val horizontalSwipeThreshold = 100f // pixels
    val verticalSwipeThreshold = 100f // pixels
    val seekDeltaMs = 10_000L // 10 seconds per swipe unit

    Box(
        modifier =
            modifier
                .fillMaxSize()
                // Tap gesture to toggle controls
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onToggleControls() },
                    )
                }
                // Horizontal drag gesture
                .pointerInput(playbackType) {
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDragAccumulated = 0f },
                        onDragEnd = {
                            if (abs(horizontalDragAccumulated) >= horizontalSwipeThreshold) {
                                val direction = if (horizontalDragAccumulated > 0) 1 else -1

                                when (playbackType) {
                                    PlaybackType.LIVE -> {
                                        // For LIVE: Jump channel (+1 for right swipe, -1 for left)
                                        onJumpLiveChannel(direction)
                                    }
                                    PlaybackType.VOD, PlaybackType.SERIES -> {
                                        // For VOD/SERIES: Seek by delta
                                        val delta = direction * seekDeltaMs
                                        onSeekBy(delta)
                                    }
                                }
                            }
                            horizontalDragAccumulated = 0f
                        },
                        onDragCancel = { horizontalDragAccumulated = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            horizontalDragAccumulated += dragAmount
                        },
                    )
                }
                // Vertical drag gesture
                .pointerInput(playbackType) {
                    detectVerticalDragGestures(
                        onDragStart = { verticalDragAccumulated = 0f },
                        onDragEnd = {
                            if (abs(verticalDragAccumulated) >= verticalSwipeThreshold) {
                                when (playbackType) {
                                    PlaybackType.LIVE -> {
                                        // For LIVE: Toggle live list/quick actions
                                        onToggleLiveList()
                                        // TODO("Phase 3 – extended live UI"):
                                        // Implement full live channel list with channel selection
                                    }
                                    PlaybackType.VOD, PlaybackType.SERIES -> {
                                        // For VOD/SERIES: No action yet
                                        // TODO: Consider brightness/volume gestures in future phase
                                    }
                                }
                            }
                            verticalDragAccumulated = 0f
                        },
                        onDragCancel = { verticalDragAccumulated = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            verticalDragAccumulated += dragAmount
                        },
                    )
                },
    ) {
        content()
    }
}
