package com.chris.m3usuite.player.internal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.focus.FocusKit

/**
 * Phase T2 styled player control button.
 *
 * Visual Contract:
 * - White icon tint
 * - Circular semi-transparent black background (40% opacity normal, 65% opacity focused)
 * - Scale 1.0 → 1.15 on focus for DPAD devices
 *
 * This component is used across all player control overlays (TV and touch)
 * to maintain consistent styling as per Phase T2 specifications.
 */
object PlayerControlButtonDefaults {
    /** Normal background opacity (unfocused) */
    const val BACKGROUND_OPACITY_NORMAL = 0.40f

    /** Focused background opacity */
    const val BACKGROUND_OPACITY_FOCUSED = 0.65f

    /** Scale factor when focused (DPAD) */
    const val FOCUS_SCALE = 1.15f

    /** Default icon size for TV controls */
    val TV_ICON_SIZE = 32.dp

    /** Default icon size for mobile controls - seek buttons */
    val MOBILE_SEEK_ICON_SIZE = 48.dp

    /** Default icon size for mobile controls - play/pause button */
    val MOBILE_PLAY_ICON_SIZE = 56.dp

    /** Default button size for TV controls */
    val TV_BUTTON_SIZE = 48.dp

    /** Default button size for mobile seek controls */
    val MOBILE_SEEK_BUTTON_SIZE = 68.dp

    /** Default button size for mobile play/pause button */
    val MOBILE_PLAY_BUTTON_SIZE = 80.dp
}

/**
 * A styled player control button with Phase T2 visual contract.
 *
 * Features:
 * - Circular semi-transparent black background
 * - White icon tint
 * - Scale animation on focus (TV/DPAD)
 * - DPAD focusable on TV devices
 *
 * @param icon The icon to display
 * @param contentDescription Accessibility description
 * @param onClick Called when the button is clicked
 * @param modifier Modifier for the button container
 * @param iconSize Size of the icon
 * @param enabled Whether the button is enabled
 */
@Composable
fun PlayerControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = PlayerControlButtonDefaults.TV_ICON_SIZE,
    enabled: Boolean = true,
) {
    var isFocused by remember { mutableStateOf(false) }

    // Animate scale on focus for TV/DPAD
    val scale by animateFloatAsState(
        targetValue = if (isFocused) PlayerControlButtonDefaults.FOCUS_SCALE else 1f,
        label = "PlayerControlButtonScale",
    )

    // Animate background opacity on focus
    val backgroundOpacity by animateFloatAsState(
        targetValue =
            if (isFocused) {
                PlayerControlButtonDefaults.BACKGROUND_OPACITY_FOCUSED
            } else {
                PlayerControlButtonDefaults.BACKGROUND_OPACITY_NORMAL
            },
        label = "PlayerControlButtonBgOpacity",
    )

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(CircleShape)
                .background(Color.Black.copy(alpha = backgroundOpacity))
                .focusable(enabled)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused || focusState.hasFocus
                }.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = Color.White,
        )
    }
}

/**
 * TV-specific player control button with FocusKit integration.
 *
 * This variant uses FocusKit.tvClickable for proper TV focus handling
 * including focus visuals and scale effects.
 *
 * @param icon The icon to display
 * @param contentDescription Accessibility description
 * @param onClick Called when the button is clicked
 * @param modifier Modifier for the button container
 * @param iconSize Size of the icon
 * @param enabled Whether the button is enabled
 */
@Composable
fun TvPlayerControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = PlayerControlButtonDefaults.TV_ICON_SIZE,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = PlayerControlButtonDefaults.BACKGROUND_OPACITY_NORMAL))
                .then(
                    FocusKit.run {
                        Modifier.tvClickable(
                            enabled = enabled,
                            role = Role.Button,
                            scaleFocused = PlayerControlButtonDefaults.FOCUS_SCALE,
                            scalePressed = PlayerControlButtonDefaults.FOCUS_SCALE,
                            shape = CircleShape,
                            brightenContent = false,
                            onClick = onClick,
                        )
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = Color.White,
        )
    }
}
