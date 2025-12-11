package com.fishit.player.core.ui.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fishit.player.core.ui.theme.FishColors
import com.fishit.player.core.ui.theme.FishShapes
import com.fishit.player.core.ui.theme.FishTheme
import com.fishit.player.core.ui.theme.LocalFishDimens

/**
 * FishTile - Primary content tile for media items
 *
 * Displays poster/thumbnail with optional:
 * - Source frame (colored border for Telegram/Xtream/Local)
 * - Progress bar
 * - Title overlay
 * - Focus scaling and glow effects
 *
 * @param title Media title
 * @param poster Poster URL or ImageRef
 * @param sourceColors List of source colors for multi-source frame
 * @param resumeFraction Progress 0.0-1.0 for resume bar
 * @param onClick Click handler
 * @param onFocusChanged Focus change callback
 * @param modifier Modifier
 * @param aspectRatio Tile aspect ratio (default 2:3 for movies)
 * @param showTitle Whether to show title on tile
 * @param topStartBadge Optional badge in top-start corner
 * @param topEndBadge Optional badge in top-end corner
 * @param overlay Optional overlay content
 */
@Composable
fun FishTile(
    title: String?,
    poster: Any?,
    modifier: Modifier = Modifier,
    sourceColors: List<Color> = emptyList(),
    resumeFraction: Float? = null,
    aspectRatio: Float = 2f / 3f,
    showTitle: Boolean = true,
    isNew: Boolean = false,
    topStartBadge: (@Composable () -> Unit)? = null,
    topEndBadge: (@Composable () -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    val dimens = LocalFishDimens.current
    var isFocused by remember { mutableStateOf(false) }

    // Animate scale on focus
    val scale by animateFloatAsState(
        targetValue = if (isFocused) dimens.focusScale else 1f,
        animationSpec = tween(durationMillis = FishTheme.motion.focusScaleDurationMs),
        label = "tileScale"
    )

    // Border color based on sources
    val borderBrush = remember(sourceColors) {
        when {
            sourceColors.isEmpty() -> Brush.linearGradient(
                listOf(Color.Transparent, Color.Transparent)
            )
            sourceColors.size == 1 -> Brush.linearGradient(
                listOf(sourceColors.first(), sourceColors.first())
            )
            else -> Brush.linearGradient(sourceColors)
        }
    }

    val focusBorder = if (isFocused && sourceColors.isEmpty()) {
        BorderStroke(dimens.focusBorderWidth, FishColors.Primary)
    } else {
        null
    }

    Box(
        modifier = modifier
            .width(dimens.tileWidth)
            .aspectRatio(aspectRatio)
            .scale(scale)
            .then(
                if (sourceColors.isNotEmpty()) {
                    Modifier.border(
                        width = dimens.focusBorderWidth,
                        brush = borderBrush,
                        shape = FishShapes.Tile
                    )
                } else if (focusBorder != null) {
                    Modifier.border(focusBorder, FishShapes.Tile)
                } else {
                    Modifier
                }
            )
            .shadow(
                elevation = if (isFocused && dimens.enableGlow) 8.dp else 2.dp,
                shape = FishShapes.Tile,
                ambientColor = if (isFocused) FishColors.FocusGlow else Color.Transparent,
                spotColor = if (isFocused) FishColors.FocusGlow else Color.Transparent
            )
            .clip(FishShapes.Tile)
            .background(FishColors.SurfaceVariant)
            .focusable()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                onFocusChanged?.invoke(focusState.isFocused)
            }
            .tvClickable(onClick = onClick)
    ) {
        // Poster Image
        AsyncImage(
            model = poster,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient scrim for title readability
        if (showTitle && title != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }

        // Progress bar
        if (resumeFraction != null && resumeFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.progressBarHeight)
                    .align(Alignment.BottomCenter)
            ) {
                // Background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.3f))
                )
                // Progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth(resumeFraction)
                        .height(dimens.progressBarHeight)
                        .background(FishColors.Primary)
                )
            }
        }

        // Badges
        topStartBadge?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                it()
            }
        }

        topEndBadge?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                it()
            }
        }

        // "NEW" badge
        if (isNew) {
            NewBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
        }

        // Custom overlay
        overlay?.invoke(this)
    }
}

/**
 * "NEW" badge for newly added content
 */
@Composable
fun NewBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = FishColors.Accent,
                shape = FishShapes.ButtonSmall
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "NEW",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black
        )
    }
}

/**
 * Extension for TV-friendly clickable behavior
 */
fun Modifier.tvClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this.then(
    Modifier.clickable(
        enabled = enabled,
        onClick = onClick
    )
)
