package com.chris.m3usuite.ui.fx
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.R
import com.chris.m3usuite.ui.debug.safePainter
import kotlin.math.min

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    showFish: Boolean = true,
    fishSize: Dp? = null,
    fishSizeRatio: Float = 0.28f,
    fishAlpha: Float = 0.18f,
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    var widthPx by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0f) }
    val anim = rememberInfiniteTransition(label = "shimmer")
    val x by anim.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(animation = tween(1300, easing = LinearEasing)),
        label = "x",
    )
    val brush =
        Brush.horizontalGradient(
            colors = listOf(base, highlight, base),
            startX = widthPx * x,
            endX = widthPx * (x + 1f),
        )
    Box(
        modifier
            .onSizeChanged { size ->
                widthPx = size.width.toFloat()
                heightPx = size.height.toFloat()
            }.clip(RoundedCornerShape(cornerRadius))
            .background(brush),
    ) {
        if (showFish) {
            // Gentle pulse + slow rotation for a pleasant Material3 effect
            val pulse by anim.animateFloat(
                initialValue = 0.12f,
                targetValue = 0.30f,
                animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing)),
                label = "fishPulse",
            )
            val density = LocalDensity.current
            val dynamicDp =
                remember(widthPx, heightPx, fishSizeRatio) {
                    val px = min(widthPx, heightPx)
                    val computed = (px * fishSizeRatio).coerceAtLeast(24f).coerceAtMost(72f)
                    with(density) { computed.toDp() }
                }
            val resolvedSize = fishSize ?: dynamicDp
            Image(
                painter = safePainter(id = R.drawable.fisch_header),
                contentDescription = null,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(resolvedSize)
                        .alpha((fishAlpha * (0.7f + 0.3f * (pulse / 0.30f))).coerceIn(0f, 1f)),
            )
        }
    }
}

@Composable
fun ShimmerCircle(
    modifier: Modifier = Modifier,
    showFish: Boolean = true,
    fishSize: Dp? = null,
    fishSizeRatio: Float = 0.40f,
    fishAlpha: Float = 0.18f,
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    var widthPx by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0f) }
    val anim = rememberInfiniteTransition(label = "shimmerCircle")
    val x by anim.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(animation = tween(1300, easing = LinearEasing)),
        label = "x",
    )
    val brush =
        Brush.horizontalGradient(
            colors = listOf(base, highlight, base),
            startX = widthPx * x,
            endX = widthPx * (x + 1f),
        )
    Box(
        modifier
            .onSizeChanged { size ->
                widthPx = size.width.toFloat()
                heightPx = size.height.toFloat()
            }.clip(CircleShape)
            .background(brush),
    ) {
        if (showFish) {
            val density = LocalDensity.current
            val dynamicDp =
                remember(widthPx, heightPx, fishSizeRatio) {
                    val px = min(widthPx, heightPx)
                    val computed = (px * fishSizeRatio).coerceAtLeast(20f).coerceAtMost(56f)
                    with(density) { computed.toDp() }
                }
            val resolvedSize = fishSize ?: dynamicDp
            Image(
                painter = safePainter(id = R.drawable.fisch_header),
                contentDescription = null,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(resolvedSize)
                        .alpha(fishAlpha),
            )
        }
    }
}
