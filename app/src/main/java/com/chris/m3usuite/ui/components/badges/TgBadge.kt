package com.chris.m3usuite.ui.components.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small blue "T" badge for Telegram-sourced tiles.
 *
 * Intended to be overlaid on poster/channel tiles without disturbing other overlays.
 * Suggested placement: top-start with slight padding and z-index above tile content.
 */
@Composable
fun TgBadge(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    corner: Dp = 4.dp,
    background: Color = Color(0xFF229ED9), // Telegram blue
    contentColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = background,
                shape = RoundedCornerShape(corner)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "T",
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Tiny variant (e.g., for dense grids).
 */
@Composable
fun TgBadgeTiny(
    modifier: Modifier = Modifier
) {
    TgBadge(
        modifier = modifier,
        size = 14.dp,
        corner = 3.dp
    )
}

/**
 * Medium variant (slightly larger tiles).
 */
@Composable
fun TgBadgeMedium(modifier: Modifier = Modifier) = TgBadge(modifier = modifier, size = 20.dp, corner = 5.dp)
