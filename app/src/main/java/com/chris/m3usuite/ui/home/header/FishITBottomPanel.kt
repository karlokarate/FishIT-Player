package com.chris.m3usuite.ui.home.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.chris.m3usuite.ui.skin.focusScaleOnTv

object FishITBottomHeights {
    val bar = 56.dp
}

@Composable
fun FishITBottomPanel(
    selected: String, // "live" | "vod" | "series"
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black
                )
            )
            .padding(horizontal = 12.dp)
            .height(FishITBottomHeights.bar),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        @Composable
        fun tab(label: String, id: String) {
            val sel = selected == id
            TextButton(onClick = { onSelect(id) }, modifier = Modifier.focusScaleOnTv()) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        tab("TV", "live")
        tab("Filme", "vod")
        tab("Serien", "series")
    }
}
