package com.chris.m3usuite.ui.fx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws two soft, animated contour lines (halo) over content when focused.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 * PHASE 8 – Task 5: Compose & FocusKit Performance Hardening
 * Contract: INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md Section 9.2
 * ════════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * **Performance Optimization:**
 * This modifier now uses a single `drawWithContent` call instead of stacking multiple
 * `border()` modifiers. This reduces the number of draw passes and layout calculations
 * when focus changes occur.
 *
 * **Visual Output:**
 * - When focused: Two glow rings (outer halo + inner highlight)
 * - When unfocused: No drawing overhead (early return)
 *
 * No scale effect - combines with existing TV interactions.
 *
 * @param focused Whether the element is currently focused.
 * @param shape Shape for the glow outline.
 * @param ringWidth Base width for the glow ring.
 */
@Composable
fun Modifier.tvFocusGlow(
    focused: Boolean,
    shape: Shape,
    ringWidth: Dp = 2.dp,
): Modifier {
    val alpha by animateFloatAsState(if (focused) 1f else 0f, label = "tvFocusGlowAlpha")
    val outerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val innerColor = Color.White.copy(alpha = 0.12f)

    return this.drawWithContent {
        drawContent()

        // Early return when not focused - no drawing overhead
        if (alpha <= 0f) return@drawWithContent

        val ringWidthPx = ringWidth.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)

        // Draw outer halo ring
        drawOutlineGlow(
            outline = outline,
            color = outerColor,
            strokeWidth = ringWidthPx,
            alpha = alpha,
        )

        // Draw inner highlight ring
        drawOutlineGlow(
            outline = outline,
            color = innerColor,
            strokeWidth = ringWidthPx * 0.5f,
            alpha = alpha,
        )
    }
}

/**
 * Helper function to draw an outline glow.
 *
 * Handles different outline types (Rectangle, Rounded, Generic) efficiently.
 */
private fun DrawScope.drawOutlineGlow(
    outline: Outline,
    color: Color,
    strokeWidth: Float,
    alpha: Float,
) {
    when (outline) {
        is Outline.Rectangle -> {
            drawRect(
                color = color,
                topLeft = outline.rect.topLeft,
                size = outline.rect.size,
                style = Stroke(width = strokeWidth),
                alpha = alpha,
            )
        }
        is Outline.Rounded -> {
            val path = Path().apply { addRoundRect(outline.roundRect) }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth),
                alpha = alpha,
            )
        }
        is Outline.Generic -> {
            drawPath(
                path = outline.path,
                color = color,
                style = Stroke(width = strokeWidth),
                alpha = alpha,
            )
        }
    }
}
