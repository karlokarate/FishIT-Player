package com.fishit.player.core.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fishit.player.core.imaging.compose.FishImage
import com.fishit.player.core.model.ImageRef
import com.fishit.player.core.ui.theme.FishShapes

/**
 * FishHorizontalCard
 *
 * Generic TV + mobile friendly horizontal card.
 * Intended for: episodes, chapters, EPG entries, setting rows, etc.
 *
 * Gold patterns applied:
 * - Consistent DPAD focus indicator + scale
 */
@Composable
fun FishHorizontalCard(
    title: String,
    subtitle: String? = null,
    description: String? = null,
    thumbnail: ImageRef? = null,
    thumbnailOverlay: (@Composable BoxScope.() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = FishShapes.Small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier =
            modifier
                .fillMaxWidth()
                .tvFocusable(
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (thumbnail != null) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 120.dp, height = 68.dp)
                            .clip(FishShapes.TileSmall),
                ) {
                    FishImage(
                        imageRef = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )

                    if (thumbnailOverlay != null) {
                        thumbnailOverlay()
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(120.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Helper overlay: darken thumbnail background. */
@Composable
fun BoxScope.FishThumbnailScrim(alpha: Float = 0.3f) {
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = alpha)),
    )
}
