package com.chris.m3usuite.ui.components.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Shows a focus-driven title overlay with a short delay so tiles can animate in smoothly.
 */
@Composable
fun FocusTitleOverlay(
    title: String?,
    focused: Boolean,
    modifier: Modifier = Modifier,
    delayMs: Long = 220L,
    maxLines: Int = 2
) {
    if (title.isNullOrBlank()) return
    var visible by remember { mutableStateOf(false) }
    val focusState = rememberUpdatedState(focused)

    LaunchedEffect(focused) {
        if (focusState.value) {
            delay(delayMs)
            if (focusState.value) {
                visible = true
            }
        } else {
            visible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "focusTitleAlpha"
    )

    if (alpha <= 0f) return

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(alpha)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(color = Color.White),
                textAlign = TextAlign.Center,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
