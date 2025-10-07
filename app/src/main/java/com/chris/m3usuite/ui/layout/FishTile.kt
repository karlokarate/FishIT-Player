package com.chris.m3usuite.ui.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.util.AppAsyncImage

/**
 * Generic, reusable tile with focus glow/scale and optional overlays.
 * First cut based on Library VOD tiles.
 */
@Composable
fun FishTile(
    title: String?,
    poster: Any?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    selected: Boolean = false,
    showNew: Boolean = false,
    resumeFraction: Float? = null, // 0f..1f for progress line; null to hide
    topStartBadge: (@Composable () -> Unit)? = null,
    bottomEndActions: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val d = LocalFishDimens.current
    val shape = RoundedCornerShape(d.tileCornerDp)
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) d.focusScale else 1f, label = "fishTileScale")

    Box(
        modifier = modifier
            .size(d.tileWidthDp, d.tileHeightDp)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                shadowElevation = if (focused) 18f else 0f
                clip = false
            }
            .then(
                FocusKit.run {
                    Modifier
                        .tvFocusFrame(
                            focusedScale = 1f,
                            pressedScale = 1f,
                            focusBorderWidth = d.focusBorderWidthDp,
                            shape = shape,
                            brightenContent = false
                        )
                        .tvClickable(
                            role = androidx.compose.ui.semantics.Role.Button,
                            scaleFocused = 1f,
                            scalePressed = 1f,
                            autoBringIntoView = false,
                            focusBorderWidth = 0.dp,
                            brightenContent = false,
                            debugTag = "FishTile",
                            onClick = onClick
                        )
                }
            )
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged?.invoke(focused)
            },
        contentAlignment = Alignment.Center
    ) {
        // Poster + reflection overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)), shape)
        ) {
            var loaded by remember(poster) { mutableStateOf(false) }
            if (!loaded) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.1f)))
            AppAsyncImage(
                url = poster,
                contentDescription = title,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                crossfade = false,
                onLoading = { loaded = false },
                onSuccess = { loaded = true },
                onError = { loaded = true }
            )
            // Reflection (top)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(d.tileHeightDp * 0.35f)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (focused) d.reflectionAlpha else d.reflectionAlpha * 0.6f),
                            1f to Color.Transparent
                        )
                    )
            )
            if (showNew && topStartBadge == null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.28f),
                    contentColor = Color.Red,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) { Text(text = "NEU", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) }
            }
            if (topStartBadge != null) Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { topStartBadge() }
            // Resume progress line
            resumeFraction?.let { f ->
                val clamped = f.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(clamped)
                        .height(3.dp)
                        .background(Color(0xFF2196F3))
                )
            }
            // Actions slot (bottom-end)
            bottomEndActions?.let {
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) { it() }
            }
        }

        // Footer (focused)
        if (focused) {
            Column(Modifier.align(Alignment.BottomStart)) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                footer?.let { Box(Modifier.padding(horizontal = 10.dp).fillMaxWidth()) { it() } }
            }
        }

        if (selected) Box(Modifier.matchParentSize().border(3.dp, MaterialTheme.colorScheme.primary, shape))

        // Global glow via FocusKit wrapper (gate via tokens)
        if (LocalFishDimens.current.enableGlow) {
            FocusKit.run {
                Box(Modifier.matchParentSize().tvFocusGlow(focused = focused, shape = shape))
            }
        }
    }
}
