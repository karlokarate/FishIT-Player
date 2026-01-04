package com.fishit.player.core.ui.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fishit.player.core.ui.theme.FishColors
import com.fishit.player.core.ui.theme.FishTheme
import com.fishit.player.core.ui.theme.LocalFishDimens

/**
 * TV Focus Modifiers - PLATINUM Focus Handling for TV & Mobile
 *
 * Provides unified focus handling that works on:
 * - **TV**: DPAD navigation with visible focus indicators
 * - **Mobile**: Touch interaction with optional focus visuals
 *
 * ## Usage
 *
 * ```kotlin
 * // Basic TV-focusable clickable
 * Surface(
 *     modifier = Modifier.tvFocusable(onClick = { /* action */ })
 * )
 *
 * // With focus state callback
 * Card(
 *     modifier = Modifier.tvFocusable(
 *         onClick = { playEpisode() },
 *         onFocusChanged = { isFocused -> updateUI(isFocused) }
 *     )
 * )
 *
 * // Custom focus appearance
 * Box(
 *     modifier = Modifier.tvFocusable(
 *         onClick = { /* action */ },
 *         focusScale = 1.05f,
 *         focusGlowColor = FishColors.Primary,
 *         focusGlowWidth = 3.dp
 *     )
 * )
 * ```
 *
 * ## Architecture
 *
 * This is a **core/ui-layout** component designed for reuse across:
 * - `feature/detail` - Episode cards, chapter cards
 * - `feature/live` - EPG entries, channel cards
 * - `feature/audiobooks` - Chapter list items
 * - `feature/settings` - Setting items
 *
 * @see FishTile for poster tiles (uses similar focus logic)
 * @see FishHorizontalCard for horizontal list items
 * @see FishChipRow for focusable chip selections
 */

/**
 * Makes a composable TV-focusable with DPAD navigation support.
 *
 * Combines:
 * - `focusable()` - Registers for DPAD focus
 * - `onFocusChanged` - Tracks focus state
 * - `clickable` - Touch and DPAD select
 * - Focus scale animation (TV feel)
 * - Focus glow border (visibility)
 *
 * @param enabled Whether interaction is enabled
 * @param onClick Click/select handler
 * @param onFocusChanged Optional focus state callback
 * @param focusScale Scale factor when focused (default from FishDimens)
 * @param enableScale Whether to animate scale on focus
 * @param enableGlow Whether to draw focus glow border
 * @param focusGlowColor Glow border color (default: FishColors.FocusGlow)
 * @param focusGlowWidth Glow border width
 * @param cornerRadius Corner radius for glow border
 */
fun Modifier.tvFocusable(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    focusScale: Float? = null,
    enableScale: Boolean = true,
    enableGlow: Boolean = true,
    focusGlowColor: Color = FishColors.FocusGlow,
    focusGlowWidth: Dp? = null,
    cornerRadius: Dp? = null,
): Modifier =
    composed {
        val dimens = LocalFishDimens.current
        var isFocused by remember { mutableStateOf(false) }

        // Determine actual scale (parameter > dimens)
        val actualScale = focusScale ?: dimens.focusScale

        // Determine actual glow config (parameter > dimens)
        val actualGlowWidth = focusGlowWidth ?: dimens.focusBorderWidth
        val actualCornerRadius = cornerRadius ?: dimens.tileCorner

        // Animate scale on focus
        val scale by animateFloatAsState(
            targetValue = if (isFocused && enableScale) actualScale else 1f,
            animationSpec = tween(durationMillis = FishTheme.motion.focusScaleDurationMs),
            label = "tvFocusScale",
        )

        this
            .focusable(enabled)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                onFocusChanged?.invoke(focusState.isFocused)
            }.scale(scale)
            .then(
                if (enableGlow) {
                    Modifier.tvFocusGlow(
                        isFocused = isFocused,
                        glowColor = focusGlowColor,
                        glowWidth = actualGlowWidth,
                        cornerRadius = actualCornerRadius,
                    )
                } else {
                    Modifier
                },
            ).clickable(enabled = enabled, onClick = onClick)
    }

/**
 * Simplified TV-focusable for items that only need click + focus tracking.
 *
 * Use this when you want to handle focus visuals yourself.
 *
 * @param enabled Whether interaction is enabled
 * @param onClick Click/select handler
 * @param onFocusChanged Focus state callback (required for manual visuals)
 */
fun Modifier.tvFocusableBasic(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
): Modifier =
    this
        .focusable(enabled)
        .onFocusChanged { onFocusChanged(it.isFocused) }
        .clickable(enabled = enabled, onClick = onClick)

/**
 * Draws a focus glow border around a composable.
 *
 * This is a visual-only modifier - does NOT handle focus registration.
 * Use `tvFocusable()` for complete focus handling.
 *
 * @param isFocused Whether the item is currently focused
 * @param glowColor Glow border color
 * @param glowWidth Glow border stroke width
 * @param cornerRadius Corner radius for rounded rect
 */
fun Modifier.tvFocusGlow(
    isFocused: Boolean,
    glowColor: Color = FishColors.FocusGlow,
    glowWidth: Dp = 2.dp,
    cornerRadius: Dp = 8.dp,
): Modifier =
    this.drawBehind {
        if (isFocused) {
            val strokeWidth = glowWidth.toPx()
            val halfStroke = strokeWidth / 2
            drawRoundRect(
                color = glowColor,
                topLeft = Offset(halfStroke, halfStroke),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                cornerRadius = CornerRadius(cornerRadius.toPx()),
                style = Stroke(width = strokeWidth),
            )
        }
    }
