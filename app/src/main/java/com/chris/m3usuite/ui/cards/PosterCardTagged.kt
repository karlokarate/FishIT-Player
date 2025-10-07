package com.chris.m3usuite.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Wrapper around PosterCard that draws a small blue "T" tag (Telegram). */
@Composable
fun PosterCardTagged(
    title: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "T"
) {
    Box(modifier = modifier) {
        PosterCard(title = title, imageUrl = imageUrl, onClick = onClick, modifier = Modifier.matchParentSize())
        // Small top-left badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(Color(0xFF1976D2))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(tag, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

