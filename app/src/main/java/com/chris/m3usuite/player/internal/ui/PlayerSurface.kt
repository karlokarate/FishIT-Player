package com.chris.m3usuite.player.internal.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.chris.m3usuite.player.internal.domain.PlaybackType
import com.chris.m3usuite.player.internal.state.AspectRatioMode
import com.chris.m3usuite.player.internal.subtitles.EdgeStyle
import com.chris.m3usuite.player.internal.subtitles.SubtitleStyle
import kotlin.math.abs
import android.graphics.Color as AndroidColor

/**
 * Phase 5 Gesture Constants.
 *
 * Named constants to avoid magic numbers in gesture handling logic.
 * Contract: INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md Section 8
 */
private object PlayerSurfaceConstants {
    /** Minimum swipe distance to recognize a gesture (in pixels). */
    const val SWIPE_THRESHOLD_PX = 60f

    /** Threshold for distinguishing small vs large swipes (in pixels). */
    const val LARGE_SWIPE_THRESHOLD_PX = 150f

    /** Seek delta for small swipes (10 seconds). */
    const val SMALL_SEEK_DELTA_MS = 10_000L

    /** Seek delta for large swipes (30 seconds). */
    const val LARGE_SEEK_DELTA_MS = 30_000L
}

/**
 * PlayerSurface composable encapsulates the ExoPlayer PlayerView and handles gesture input.
 *
 * **Phase 3 Step 3.D**: This composable now supports horizontal swipe gestures for Live channel zapping.
 * **Phase 4 Group 3**: This composable now applies subtitle styling to the PlayerView.
 * **Phase 5 Group 1**: Black bars must be black - PlayerView and Compose container backgrounds are set to black.
 * **Phase 5 Group 2**: AspectRatioMode (FIT/FILL/ZOOM) is applied correctly via toResizeMode().
 * **Phase 5 Group 3**: Trickplay gesture callbacks for VOD/SERIES content.
 *
 * For LIVE playback:
 * - Horizontal swipe right → calls `onJumpLiveChannel(+1)` (next channel)
 * - Horizontal swipe left → calls `onJumpLiveChannel(-1)` (previous channel)
 *
 * For VOD/SERIES playback:
 * - Horizontal swipe → calls `onStepSeek` with direction-based delta
 * - Double tap sides → future enhancement for ±10s seek
 *
 * Gesture thresholds:
 * - Horizontal swipe threshold: 60px (matches legacy implementation)
 * - Distinguishes horizontal vs vertical gestures based on drag axis dominance
 *
 * **Black Bar Contract (Phase 5 Section 4.2):**
 * - PlayerView background is black
 * - Shutter/background color is black (before first frame)
 * - Compose container uses Color.Black background
 * - Non-video areas (letterbox/pillarbox) are always black, never white
 *
 * **Trickplay Behavior (Phase 5 Section 6):**
 * - Aspect ratio and black background remain unchanged during trickplay
 * - Gestures trigger callbacks; actual speed control is managed by session
 *
 * @param player The ExoPlayer instance to render
 * @param aspectRatioMode The aspect ratio mode for the player view
 * @param playbackType The type of playback (VOD, SERIES, or LIVE)
 * @param subtitleStyle The subtitle style to apply to the player view
 * @param isKidMode Whether the current profile is in kid mode (disables subtitle rendering)
 * @param onTap Callback invoked when the surface is tapped (typically toggles controls)
 * @param onJumpLiveChannel Callback for Live channel zapping. Only invoked for LIVE playback.
 *                          Delta values: +1 for next channel, -1 for previous channel.
 * @param onStepSeek Callback for seek preview. Invoked for VOD/SERIES horizontal swipes.
 *                   Delta values: positive for forward, negative for backward.
 */
@Composable
fun PlayerSurface(
    player: ExoPlayer?,
    aspectRatioMode: AspectRatioMode,
    playbackType: PlaybackType,
    subtitleStyle: SubtitleStyle = SubtitleStyle(),
    isKidMode: Boolean = false,
    onTap: () -> Unit = {},
    onJumpLiveChannel: (delta: Int) -> Unit = {},
    onStepSeek: (deltaMs: Long) -> Unit = {},
) {
    // Track drag deltas for gesture recognition
    var dragDeltaX by remember { mutableStateOf(0f) }
    var dragDeltaY by remember { mutableStateOf(0f) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // ═══════════════════════════════════════════════════════════════
                // Phase 5 Group 1: Black bars must be black
                // ═══════════════════════════════════════════════════════════════
                // Contract Section 4.2 Rule 3: Compose container must use black background
                // This ensures non-video areas (letterbox/pillarbox) are black, not white
                .background(Color.Black)
                // Tap gesture: toggle controls visibility
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                }
                // Drag gestures: Live channel zapping (LIVE) or seek/trickplay (VOD/SERIES)
                .pointerInput(playbackType) {
                    detectDragGestures(
                        onDrag = { _, dragAmount ->
                            dragDeltaX += dragAmount.x
                            dragDeltaY += dragAmount.y
                        },
                        onDragEnd = {
                            // Determine gesture direction based on dominant axis
                            val swipeDistance = abs(dragDeltaX)
                            val isHorizontalSwipe =
                                swipeDistance > abs(dragDeltaY) &&
                                    swipeDistance > PlayerSurfaceConstants.SWIPE_THRESHOLD_PX

                            if (isHorizontalSwipe) {
                                // Horizontal swipe detected
                                if (playbackType == PlaybackType.LIVE) {
                                    // LIVE: Channel zapping
                                    val delta = if (dragDeltaX < 0) +1 else -1
                                    onJumpLiveChannel(delta)
                                } else {
                                    // VOD/SERIES: Seek preview (Phase 5 Group 3)
                                    // Determine seek magnitude based on swipe distance
                                    val seekMs =
                                        if (swipeDistance > PlayerSurfaceConstants.LARGE_SWIPE_THRESHOLD_PX) {
                                            PlayerSurfaceConstants.LARGE_SEEK_DELTA_MS
                                        } else {
                                            PlayerSurfaceConstants.SMALL_SEEK_DELTA_MS
                                        }
                                    // Direction: positive delta = forward, negative = backward
                                    val seekDelta = if (dragDeltaX > 0) seekMs else -seekMs
                                    onStepSeek(seekDelta)
                                }
                            }
                            // Note: Vertical swipe handling deferred to future phases

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

                        // ═══════════════════════════════════════════════════════════════
                        // Phase 5 Group 1: Black bars must be black
                        // ═══════════════════════════════════════════════════════════════
                        // Contract Section 4.2 Rules 1-2: PlayerView background and shutter must be black
                        // This ensures non-video areas and initial buffering state show black, not white
                        setBackgroundColor(AndroidColor.BLACK)
                        setShutterBackgroundColor(AndroidColor.BLACK)

                        // ═══════════════════════════════════════════════════════════════
                        // Phase 4 Group 3: Apply subtitle style to PlayerView
                        // ═══════════════════════════════════════════════════════════════
                        //
                        // Apply SubtitleStyle to Media3 subtitleView if subtitles should be shown.
                        // Kid Mode: Subtitles are disabled (style is stored but not applied)
                        if (!isKidMode) {
                            subtitleView?.apply {
                                setApplyEmbeddedStyles(true)
                                setApplyEmbeddedFontSizes(true)
                                setFractionalTextSize(subtitleStyle.textScale)

                                // Convert SubtitleStyle colors with opacity to ARGB
                                val fgColor = applyOpacity(subtitleStyle.foregroundColor, subtitleStyle.foregroundOpacity)
                                val bgColor = applyOpacity(subtitleStyle.backgroundColor, subtitleStyle.backgroundOpacity)

                                // Map EdgeStyle to CaptionStyleCompat edge type
                                val edgeType =
                                    when (subtitleStyle.edgeStyle) {
                                        EdgeStyle.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
                                        EdgeStyle.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                                        EdgeStyle.SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                                        EdgeStyle.GLOW -> CaptionStyleCompat.EDGE_TYPE_RAISED
                                    }

                                val captionStyle =
                                    CaptionStyleCompat(
                                        fgColor,
                                        bgColor,
                                        0x00000000, // windowColor transparent
                                        edgeType,
                                        0x00000000, // edgeColor
                                        null, // typeface
                                    )
                                setStyle(captionStyle)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.player = player
                    view.resizeMode = aspectRatioMode.toResizeMode()

                    // Phase 4 Group 3: Update subtitle style when it changes
                    if (!isKidMode) {
                        view.subtitleView?.apply {
                            setFractionalTextSize(subtitleStyle.textScale)

                            val fgColor = applyOpacity(subtitleStyle.foregroundColor, subtitleStyle.foregroundOpacity)
                            val bgColor = applyOpacity(subtitleStyle.backgroundColor, subtitleStyle.backgroundOpacity)

                            val edgeType =
                                when (subtitleStyle.edgeStyle) {
                                    EdgeStyle.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
                                    EdgeStyle.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                                    EdgeStyle.SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                                    EdgeStyle.GLOW -> CaptionStyleCompat.EDGE_TYPE_RAISED
                                }

                            val captionStyle =
                                CaptionStyleCompat(
                                    fgColor,
                                    bgColor,
                                    0x00000000,
                                    edgeType,
                                    0x00000000,
                                    null,
                                )
                            setStyle(captionStyle)
                        }
                    }
                },
            )
        }
    }
}

/**
 * Helper function to apply opacity to an ARGB color.
 * Converts opacity (0f..1f) to alpha channel (0..255).
 */
private fun applyOpacity(
    argb: Int,
    opacity: Float,
): Int {
    val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
    val rgb = argb and 0x00FFFFFF
    return (alpha shl 24) or rgb
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
