package com.chris.m3usuite.ui.home.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsets.Companion.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

object FishITHeaderHeights {
    val topBar = 56.dp
    val spacer = 8.dp
    val total = topBar + spacer
}

/** Translucent overlay header with app icon + settings gear; alpha controls scrim intensity. */
@Composable
fun FishITHeader(
    title: String,
    onSettings: () -> Unit,
    scrimAlpha: Float, // 0f..1f depending on scroll
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = (0.85f * scrimAlpha).coerceIn(0f, 0.85f)),
                    1f to Color.Transparent
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp)
    ) {
        // Top bar
        Row(
            Modifier
                .height(FishITHeaderHeights.topBar)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.chris.m3usuite.R.drawable.fisch),
                contentDescription = title,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen")
            }
        }
        Spacer(Modifier.height(FishITHeaderHeights.spacer))
    }
}
