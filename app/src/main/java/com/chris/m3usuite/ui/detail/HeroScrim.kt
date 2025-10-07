package com.chris.m3usuite.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
// matchParentSize not available on older Compose in this project; use fillMaxSize instead
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.util.AppHeroImage

// Shared alpha used for the hero image in scrim/header and for full-screen hero backgrounds
const val HERO_SCRIM_IMAGE_ALPHA: Float = 0.5f

@Composable
fun HeroScrim(
    url: Any?,
    height: Dp = 260.dp,
    alpha: Float = HERO_SCRIM_IMAGE_ALPHA
) {
    Box(Modifier.fillMaxWidth().height(height)) {
        AppHeroImage(
            url = url,
            contentDescription = null,
            crossfade = true,
            modifier = Modifier.fillMaxSize().graphicsLayer(alpha = alpha)
        )
        // Top and bottom gradient for readability
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.55f),
                    0.25f to Color.Transparent,
                    0.75f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.65f)
                )
            )
        )
    }
}
