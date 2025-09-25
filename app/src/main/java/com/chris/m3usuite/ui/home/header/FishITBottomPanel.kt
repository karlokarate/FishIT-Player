package com.chris.m3usuite.ui.home.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton

object FishITBottomHeights {
    val bar = 56.dp
}

private val BottomBaseColor = Color(0xFF05080F)

@Composable
fun FishITBottomPanel(
    selected: String, // "live" | "vod" | "series"
    onSelect: (String) -> Unit,
) {
    val focusOverlay = Color.White.copy(alpha = 0.4f)
    Row(
        Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to BottomBaseColor.copy(alpha = 0.84f),
                        0.6f to BottomBaseColor.copy(alpha = 0.92f),
                        1f to BottomBaseColor
                    )
                )
            )
            .padding(horizontal = 12.dp)
            .height(FishITBottomHeights.bar),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AppIconButton(icon = AppIcon.LiveTv,    contentDescription = "TV",     onClick = { onSelect("live") },  size = 28.dp, tvFocusOverlay = focusOverlay)
        AppIconButton(icon = AppIcon.MovieVod,  contentDescription = "Filme",  onClick = { onSelect("vod") },    size = 28.dp, tvFocusOverlay = focusOverlay)
        AppIconButton(icon = AppIcon.Series,    contentDescription = "Serien", onClick = { onSelect("series") }, size = 28.dp, tvFocusOverlay = focusOverlay)
    }
}
