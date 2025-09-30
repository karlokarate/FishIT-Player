package com.chris.m3usuite.ui.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.fx.ShimmerBox
import com.chris.m3usuite.ui.components.common.FocusTitleOverlay
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.util.AppAsyncImage

private val PosterShape = RoundedCornerShape(14.dp)
private val TileShape = RoundedCornerShape(14.dp)

/**
+ PosterCard – 2:3 Karte mit Fokus-Overlay, Shimmer und TV-Hover.
 */
@Composable
fun PosterCard(
    title: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(PosterShape)
            .then(
                if (onClick != null) {
                    Modifier
                        .tvClickable(onClick = onClick)
                        .focusScaleOnTv()
                } else Modifier
            )
    ) {
        if (isLoading) {
            ShimmerBox(modifier = Modifier.matchParentSize(), shape = PosterShape)
        }
        if (!imageUrl.isNullOrBlank()) {
            AppAsyncImage(
                url = imageUrl,
                contentDescription = title ?: "Poster",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.matchParentSize(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = PosterShape
            ) {}
        }
        FocusTitleOverlay(
            title = title,
            focused = focused,
            modifier = Modifier.padding(8.dp)
        )
        // Observe focus state (simple)
        androidx.compose.ui.Modifier
        androidx.compose.foundation.layout.Box(
            Modifier
                .matchParentSize()
                .onFocusChanged { focused = it.isFocused }
        )
    }
}

/**
+ ChannelCard – 16:9 Live-Karte mit optionalem Titel-Overlay.
 */
@Composable
fun ChannelCard(
    title: String?,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(TileShape)
            .then(
                if (onClick != null) {
                    Modifier
                        .tvClickable(onClick = onClick)
                        .focusScaleOnTv()
                } else Modifier
            )
    ) {
        if (isLoading) {
            ShimmerBox(modifier = Modifier.matchParentSize(), shape = TileShape)
        }
        if (!logoUrl.isNullOrBlank()) {
            AppAsyncImage(
                url = logoUrl,
                contentDescription = title ?: "Channel",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.matchParentSize(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = TileShape
            ) {}
        }
        FocusTitleOverlay(
            title = title,
            focused = focused,
            modifier = Modifier.padding(8.dp),
            maxLines = 1
        )
        androidx.compose.foundation.layout.Box(
            Modifier
                .matchParentSize()
                .onFocusChanged { focused = it.isFocused }
        )
    }
}

/**
+ SeasonCard – kleine, fokussierbare Kapsel-Karte für Staffelangaben.
 */
@Composable
fun SeasonCard(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        color = bg,
        contentColor = fg,
        shape = CircleShape,
        modifier = modifier
            .then(if (onClick != null) Modifier.tvClickable(onClick = onClick) else Modifier)
            .focusScaleOnTv()
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
+ EpisodeCard – schlanke Zeilenkarte mit Titel/Meta, optionalem Fortschritt.
 */
@Composable
fun EpisodeCard(
    title: String,
    subtitle: String? = null,
    progress: Float? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .then(if (onClick != null) Modifier.tvClickable(onClick = onClick) else Modifier)
            .focusScaleOnTv()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        if (progress != null && progress > 0f) {
            val a by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), animationSpec = tween(200), label = "episodeProgress")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Box(
