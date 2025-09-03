package com.chris.m3usuite.ui.fx

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import com.chris.m3usuite.R

@Composable
fun FishBackground(
    modifier: Modifier = Modifier,
    size: Dp = 560.dp,
    alpha: Float = 0.05f,
    fastSpinMillis: Int = 500,
    loadingSpinMillis: Int = 1000
) {
    // Loading-driven infinite rotation uses an InfiniteTransition for reliability
    val isLoading = FishSpin.isLoading.collectAsState().value
    val infinite = rememberInfiniteTransition(label = "fishBg")
    val loadingAngle = if (isLoading) {
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(loadingSpinMillis, easing = LinearEasing)),
            label = "deg"
        ).value
    } else 0f

    // Kick animation overlays one quick spin on top of loading angle
    val kick = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        FishSpin.spinTrigger.collect {
            kick.stop(); kick.snapTo(0f)
            kick.animateTo(360f, tween(fastSpinMillis, easing = LinearEasing))
            kick.snapTo(0f)
        }
    }

    val angle = (loadingAngle + kick.value) % 360f

    Image(
        painter = painterResource(id = R.drawable.fisch),
        contentDescription = null,
        modifier = modifier.graphicsLayer { this.alpha = alpha; rotationZ = angle }
    )
}
