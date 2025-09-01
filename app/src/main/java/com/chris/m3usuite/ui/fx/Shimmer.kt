package com.chris.m3usuite.ui.fx

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, cornerRadius: Dp = 12.dp) {
  val base = MaterialTheme.colorScheme.surfaceVariant
  val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
  var widthPx by remember { mutableStateOf(0f) }
  val anim = rememberInfiniteTransition(label = "shimmer")
  val x by anim.animateFloat(
    initialValue = -1f,
    targetValue = 2f,
    animationSpec = infiniteRepeatable(animation = tween(1300, easing = LinearEasing)),
    label = "x"
  )
  val brush = Brush.horizontalGradient(
    colors = listOf(base, highlight, base),
    startX = widthPx * x,
    endX = widthPx * (x + 1f)
  )
  Box(
    modifier
      .onSizeChanged { size -> widthPx = size.width.toFloat() }
      .clip(RoundedCornerShape(cornerRadius))
      .background(brush)
  )
}

@Composable
fun ShimmerCircle(modifier: Modifier = Modifier) {
  val base = MaterialTheme.colorScheme.surfaceVariant
  val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
  var widthPx by remember { mutableStateOf(0f) }
  val anim = rememberInfiniteTransition(label = "shimmerCircle")
  val x by anim.animateFloat(
    initialValue = -1f,
    targetValue = 2f,
    animationSpec = infiniteRepeatable(animation = tween(1300, easing = LinearEasing)),
    label = "x"
  )
  val brush = Brush.horizontalGradient(
    colors = listOf(base, highlight, base),
    startX = widthPx * x,
    endX = widthPx * (x + 1f)
  )
  Box(
    modifier
      .onSizeChanged { size -> widthPx = size.width.toFloat() }
      .clip(CircleShape)
      .background(brush)
  )
}
