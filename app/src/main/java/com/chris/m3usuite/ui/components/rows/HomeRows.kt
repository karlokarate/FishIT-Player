package com.chris.m3usuite.ui.components.rows

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.skin.isTvDevice
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import com.chris.m3usuite.data.db.MediaItem

@Composable
private fun rowItemHeight(): Int {
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val base = if (isLandscape) 210 else 180
    return base
}

@Composable
fun MediaCard(
    item: MediaItem,
    onClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    val ctx = LocalContext.current
    val h = rowItemHeight()
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .height(h.dp)
            .padding(end = 12.dp)
            .tvClickable { onClick(item) }
    ) {
        // Prefer poster/logo/backdrop in this order (fallback to any image field in MediaItem)
        val model = remember(item.poster ?: item.logo ?: item.backdrop) {
            item.poster ?: item.logo ?: item.backdrop
        }
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(model).build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .aspectRatio(16f / 9f)
        )
        if (showTitle) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** Show last 5 resume items in a horizontal row (no header) */
@Composable
fun ResumeRow(
    items: List<MediaItem>,
    onClick: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return
    val slice = remember(items) { items.take(5) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(slice, key = { it.id }) { m ->
            MediaCard(item = m, onClick = onClick)
        }
    }
}

/** Live TV row, no textual header, horizontally scrollable */
@Composable
fun LiveRow(
    items: List<MediaItem>,
    onClick: (MediaItem) -> Unit
) {
    if (items.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(items, key = { it.id }) { m ->
            // For live, logos often look better; hide title for a cleaner rail
            MediaCard(item = m, onClick = onClick, showTitle = false)
        }
    }
}

/** Series row, horizontally scrollable */
@Composable
fun SeriesRow(
    items: List<MediaItem>,
    onClick: (MediaItem) -> Unit
) {
    if (items.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(items, key = { it.id }) { m ->
            MediaCard(item = m, onClick = onClick)
        }
    }
}

/** VOD row, horizontally scrollable */
@Composable
fun VodRow(
    items: List<MediaItem>,
    onClick: (MediaItem) -> Unit
) {
    if (items.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(items, key = { it.id }) { m ->
            MediaCard(item = m, onClick = onClick)
        }
    }
}

