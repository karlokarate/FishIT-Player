package com.chris.m3usuite.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.common.AccentCard
import com.chris.m3usuite.ui.theme.DesignTokens
import com.chris.m3usuite.ui.util.AppHeroImage

/**
 * Full-screen detail backdrop used across detail screens to ensure identical look.
 * - Renders a full-screen hero image with the same alpha as Series
 * - Adds vertical + radial gradient overlays with identical colors/opacities
 * - Wraps foreground content in an AccentCard with consistent paddings
 */
@Composable
fun DetailBackdrop(
    heroUrl: Any?,
    isAdult: Boolean,
    pads: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // Layer 1: full-screen hero
        heroUrl?.let { url ->
            AppHeroImage(
                url = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer(alpha = HERO_SCRIM_IMAGE_ALPHA),
                crossfade = true
            )
        }
        // Accent colors align with Series detail
        val accent = if (!isAdult) DesignTokens.KidAccent else DesignTokens.Accent
        // Foreground content area with scaffold paddings
        Box(Modifier.fillMaxSize().padding(pads)) {
            // Vertical gradient for readability
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                            1f to androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                        )
                    )
            )
            // Radial accent glow
            val radius = with(LocalDensity.current) { 680.dp.toPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (!isAdult) 0.20f else 0.12f),
                                Color.Transparent
                            ),
                            radius = radius
                        )
                    )
            )
            // Foreground card content
            AccentCard(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                accent = accent
            ) {
                content()
            }
        }
    }
}

