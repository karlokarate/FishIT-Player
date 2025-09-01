package com.chris.m3usuite.ui.fx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.border
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Zeichnet zwei weiche, animierte Konturlinien (Halo) über dem Inhalt,
 * wenn das Element fokussiert ist. Kein Scale – lässt sich mit existierenden
 * TV-Interaktionen kombinieren.
 */
@Composable
fun Modifier.tvFocusGlow(
  focused: Boolean,
  shape: Shape,
  ringWidth: Dp = 2.dp
): Modifier {
  val alpha by animateFloatAsState(if (focused) 1f else 0f, label = "tvFocusGlowAlpha")
  return if (alpha > 0f) {
    val outer = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f * alpha)
    val inner = Color.White.copy(alpha = 0.12f * alpha)
    this
      .border(ringWidth, outer, shape)
      .border(ringWidth * 0.5f, inner, shape)
  } else this
}
