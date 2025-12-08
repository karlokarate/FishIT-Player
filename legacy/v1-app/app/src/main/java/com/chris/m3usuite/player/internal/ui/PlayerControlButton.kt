package com.chris.m3usuite.player.internal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-visibility player control button for SIP Internal Player overlay.
 *
 * **SIP UI Contract:**
 * High-contrast white icons with semi-transparent black circular background
 * improve visibility over any video content.
 *
 * Design Specifications:
 * - Circular black background with 40% opacity (normal state)
 * - Circular black background with 65% opacity (focused state)
 * - White icon tint (#FFFFFF)
 * - Scale animation: 1.0 → 1.15 when focused
 * - Optional white glow/border on focus (1.5dp white at 40% opacity)
 * - Default size: 48.dp container, 24.dp icon
 * - Default padding: ~12.dp
 *
 * This component properly maps DPAD/TV focus state to visual feedback,
 * ensuring accessibility on Android TV and Fire TV devices.
 *
 * @param onClick Action to perform when button is clicked
 * @param modifier Optional modifier to apply to the button
 * @param enabled Whether the button is enabled
 * @param contentDescription Accessibility description for the icon
 * @param size Total button size (default: 48.dp)
 * @param iconSize Size of the icon (default: 24.dp)
 * @param showFocusBorder Whether to show white border when focused (default: true)
 * @param icon Content composable that renders the icon
 */
@Composable
fun PlayerControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    showFocusBorder: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate scale: 1.0 normal → 1.15 focused → 1.10 pressed
    val scale by animateFloatAsState(
        targetValue =
            when {
                isPressed -> 1.10f
                isFocused -> 1.15f
                else -> 1.0f
            },
        label = "PlayerControlButtonScale",
    )

    // Animate background opacity: 40% normal → 65% focused
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isFocused || isPressed) 0.65f else 0.40f,
        label = "PlayerControlButtonBgAlpha",
    )

    // Animate focus border opacity: 0% normal → 40% focused
    val focusBorderAlpha by animateFloatAsState(
        targetValue = if (isFocused && showFocusBorder) 0.40f else 0f,
        label = "PlayerControlButtonBorderAlpha",
    )

    Box(
        modifier =
            modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(CircleShape)
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .then(
                    if (focusBorderAlpha > 0f) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = Color.White.copy(alpha = focusBorderAlpha),
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ).focusable(enabled)
                .onFocusEvent { focusState ->
                    isFocused = focusState.isFocused || focusState.hasFocus
                }.clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null, // We handle visual feedback via background/scale
                    onClick = onClick,
                ).padding(((size - iconSize) / 2).coerceAtLeast(0.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(iconSize),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

/**
 * Convenience overload for PlayerControlButton with ImageVector icon.
 *
 * @param imageVector The vector icon to display
 * @param onClick Action to perform when button is clicked
 * @param contentDescription Accessibility description for the icon
 * @param modifier Optional modifier to apply to the button
 * @param enabled Whether the button is enabled
 * @param size Total button size (default: 48.dp)
 * @param iconSize Size of the icon (default: 24.dp)
 * @param showFocusBorder Whether to show white border when focused (default: true)
 */
@Composable
fun PlayerControlButton(
    imageVector: ImageVector,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    showFocusBorder: Boolean = true,
) {
    PlayerControlButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentDescription = contentDescription,
        size = size,
        iconSize = iconSize,
        showFocusBorder = showFocusBorder,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Convenience overload for PlayerControlButton with Painter icon.
 *
 * @param painter The painter for the icon (e.g., painterResource)
 * @param onClick Action to perform when button is clicked
 * @param contentDescription Accessibility description for the icon
 * @param modifier Optional modifier to apply to the button
 * @param enabled Whether the button is enabled
 * @param size Total button size (default: 48.dp)
 * @param iconSize Size of the icon (default: 24.dp)
 * @param showFocusBorder Whether to show white border when focused (default: true)
 */
@Composable
fun PlayerControlButton(
    painter: Painter,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    showFocusBorder: Boolean = true,
) {
    PlayerControlButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentDescription = contentDescription,
        size = size,
        iconSize = iconSize,
        showFocusBorder = showFocusBorder,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}
