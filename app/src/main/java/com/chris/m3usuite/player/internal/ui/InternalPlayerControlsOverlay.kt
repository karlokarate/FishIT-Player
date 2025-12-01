package com.chris.m3usuite.player.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.focus.FocusZoneId
import com.chris.m3usuite.ui.focus.focusGroup
import com.chris.m3usuite.ui.focus.focusZone

/**
 * SIP Internal Player responsive controls overlay.
 *
 * This overlay provides device-responsive control layouts:
 *
 * **TV/DPAD Devices:**
 * - Small, discrete controls
 * - Bottom-aligned layout
 * - DPAD-focusable with scale animation on focus
 * - Follows Phase 8 lifecycle rules
 *
 * **Phone/Tablet (Touch Devices):**
 * - Large, centered controls for easy tapping
 * - White icons with circular semi-transparent black backgrounds
 * - Optimized touch targets
 *
 * **Contract References:**
 * - Phase T2 styling: White icons + circular black background
 * - TELEGRAM_SIP_PLAYER_INTEGRATION.md: UI/lifecycle constraints
 * - INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md: SIP overlay rules
 *
 * **Scope Limits:**
 * - Does NOT modify SIP PlayerSession, PlaybackContext, lifecycle, or player logic
 * - Does NOT change Telegram playback wiring
 * - Only adjusts overlay UI layout and sizing, device-responsive
 */
@Composable
fun InternalPlayerControlsOverlay(
    isPlaying: Boolean,
    controlsVisible: Boolean,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = detectPlayerUiMode()

    AnimatedVisibility(
        visible = controlsVisible,
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
        modifier = modifier,
    ) {
        when (mode) {
            PlayerUiMode.TV -> {
                // TV: Keep existing small, bottom-aligned controls with DPAD focus
                BottomAlignedTvControls(
                    isPlaying = isPlaying,
                    onPlayPause = onPlayPause,
                    onSeekForward = onSeekForward,
                    onSeekBackward = onSeekBackward,
                )
            }
            PlayerUiMode.PHONE, PlayerUiMode.TABLET -> {
                // Touch: Large, centered controls for easy tapping
                CenteredMobileControls(
                    isPlaying = isPlaying,
                    onPlayPause = onPlayPause,
                    onSeekForward = onSeekForward,
                    onSeekBackward = onSeekBackward,
                )
            }
        }
    }
}

/**
 * Centered mobile controls layout for phone/tablet devices.
 *
 * Features:
 * - Large, touch-friendly buttons
 * - Centered on screen with bottom padding
 * - White icons with circular semi-transparent black backgrounds (Phase T2)
 */
@Composable
private fun CenteredMobileControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Seek backward button (-10s)
            PlayerControlButton(
                icon = Icons.Rounded.Replay10,
                contentDescription = "Seek Backward 10 seconds",
                onClick = onSeekBackward,
                modifier = Modifier.size(PlayerControlButtonDefaults.MOBILE_SEEK_BUTTON_SIZE),
                iconSize = PlayerControlButtonDefaults.MOBILE_SEEK_ICON_SIZE,
            )

            // Play/Pause button (larger, central)
            PlayerControlButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = onPlayPause,
                modifier = Modifier.size(PlayerControlButtonDefaults.MOBILE_PLAY_BUTTON_SIZE),
                iconSize = PlayerControlButtonDefaults.MOBILE_PLAY_ICON_SIZE,
            )

            // Seek forward button (+10s)
            PlayerControlButton(
                icon = Icons.Rounded.Forward10,
                contentDescription = "Seek Forward 10 seconds",
                onClick = onSeekForward,
                modifier = Modifier.size(PlayerControlButtonDefaults.MOBILE_SEEK_BUTTON_SIZE),
                iconSize = PlayerControlButtonDefaults.MOBILE_SEEK_ICON_SIZE,
            )
        }
    }
}

/**
 * Bottom-aligned TV controls layout for DPAD/remote devices.
 *
 * Features:
 * - Small, discrete controls
 * - Bottom-aligned layout
 * - DPAD-focusable with FocusZone integration
 * - Scale 1.0 → 1.15 on focus
 * - Follows Phase 6 FocusZone system
 */
@Composable
private fun BottomAlignedTvControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusGroup()
                    .focusZone(FocusZoneId.PLAYER_CONTROLS),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Seek backward button (-10s) - TV variant with FocusKit
                TvPlayerControlButton(
                    icon = Icons.Rounded.Replay10,
                    contentDescription = "Seek Backward 10 seconds",
                    onClick = onSeekBackward,
                    modifier = Modifier.size(PlayerControlButtonDefaults.TV_BUTTON_SIZE),
                    iconSize = PlayerControlButtonDefaults.TV_ICON_SIZE,
                )

                // Play/Pause button - TV variant with FocusKit
                TvPlayerControlButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    onClick = onPlayPause,
                    modifier = Modifier.size(PlayerControlButtonDefaults.TV_BUTTON_SIZE),
                    iconSize = PlayerControlButtonDefaults.TV_ICON_SIZE,
                )

                // Seek forward button (+10s) - TV variant with FocusKit
                TvPlayerControlButton(
                    icon = Icons.Rounded.Forward10,
                    contentDescription = "Seek Forward 10 seconds",
                    onClick = onSeekForward,
                    modifier = Modifier.size(PlayerControlButtonDefaults.TV_BUTTON_SIZE),
                    iconSize = PlayerControlButtonDefaults.TV_ICON_SIZE,
                )
            }
        }
    }
}
