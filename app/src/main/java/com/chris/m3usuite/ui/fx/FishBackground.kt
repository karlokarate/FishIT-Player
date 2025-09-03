package com.chris.m3usuite.ui.fx

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import com.chris.m3usuite.R

@Composable
fun FishBackground(
    modifier: Modifier = Modifier,
    size: Dp = 560.dp,
    alpha: Float = 0.05f,
    fastSpinMillis: Int = 500,
    loadingSpinMillis: Int = 1000
) {
    val scope = rememberCoroutineScope()
    val angle = remember { Animatable(0f) }
    val isLoading by FishSpin.isLoading.collectAsState()

    LaunchedEffect(isLoading) {
        if (isLoading) {
            // Continuous rotation until loading turns off
            while (FishSpin.isLoading.value) {
                val target = angle.value + 360f
                angle.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = loadingSpinMillis, easing = LinearEasing)
                )
            }
            angle.snapTo(0f)
        }
    }

    LaunchedEffect(Unit) {
        FishSpin.spinTrigger.collect {
            // One quick spin regardless of current state
            angle.stop()
            angle.snapTo(0f)
            angle.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = fastSpinMillis, easing = LinearEasing)
            )
            angle.snapTo(0f)
        }
    }

    Image(
        painter = painterResource(id = R.drawable.fisch),
        contentDescription = null,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha; rotationZ = angle.value }
    )
}

