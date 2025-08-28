package com.chris.m3usuite.ui.components.rows

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import android.net.Uri
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chris.m3usuite.ui.util.rememberImageHeaders
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.skin.tvClickable
// isTvDevice removed (unused)
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import com.chris.m3usuite.data.db.MediaItem
import com.chris.m3usuite.data.db.DbProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.chris.m3usuite.domain.selectors.extractYearFrom
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.core.xtream.XtreamClient
import com.chris.m3usuite.core.xtream.XtreamConfig
import kotlinx.coroutines.flow.first
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton

@Composable
private fun rowItemHeight(): Int {
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val sw = cfg.smallestScreenWidthDp
    val isTablet = sw >= 600
    // Tune heights: phone 180/200, tablet 210/230 depending on orientation
    val base = when {
        isTablet && isLandscape -> 230
        isTablet -> 210
        isLandscape -> 200
        else -> 180
    }
    return (base * 1.2f).toInt() // +20%
}

@Composable
fun MediaCard(
    item: MediaItem,
    onClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    val ctx = LocalContext.current
    val headers = rememberImageHeaders()
    val h = rowItemHeight()
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .height(h.dp)
            .padding(end = 12.dp)
            .tvClickable { onClick(item) }
    ) {
        // Prefer poster/logo/backdrop in this order (fallback to any image field in MediaItem)
        val raw = remember(item.poster ?: item.logo ?: item.backdrop) {
            item.poster ?: item.logo ?: item.backdrop
        }
        AsyncImage(
            model = buildImageRequest(ctx, raw, headers),
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

@Composable
fun LiveTileCard(
    item: MediaItem,
    onClick: (MediaItem) -> Unit
) {
    val ctx = LocalContext.current
    val headers = rememberImageHeaders()
    var epg by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    LaunchedEffect(item.streamId) {
        try {
            val store = SettingsStore(ctx)
            val host = store.xtHost.first(); val user = store.xtUser.first(); val pass = store.xtPass.first(); val out = store.xtOutput.first(); val port = store.xtPort.first()
            if (item.streamId != null && host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                val client = XtreamClient(ctx, store, XtreamConfig(host, port, user, pass, out))
                epg = client.shortEPG(item.streamId, 1).firstOrNull()?.title.orEmpty()
            }
        } catch (_: Throwable) { epg = "" }
    }
    Card(
        modifier = Modifier
            .height(rowItemHeight().dp)
            .padding(end = 6.dp)
            .tvClickable(scaleFocused = 1.12f, scalePressed = 1.16f, elevationFocusedDp = 18f) { preview = !preview }
            .onFocusChanged { focused = it.isFocused || it.hasFocus },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (preview && !item.url.isNullOrBlank()) {
                // Inline preview player (muted), dimmed to 70% opacity
                val url = item.url!!
                AndroidView(
                    factory = { c ->
                        val view = PlayerView(c)
                        val player = ExoPlayer.Builder(c).build().apply {
                            volume = 0f
                            repeatMode = ExoPlayer.REPEAT_MODE_ALL
                            playWhenReady = true
                            setMediaItem(ExoMediaItem.fromUri(Uri.parse(url)))
                            prepare()
                        }
                        view.player = player
                        view.useController = false
                        view.tag = player
                        view
                    },
                    modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = 0.7f),
                    update = { v ->
                        val p = (v.tag as? ExoPlayer)
                        if (p != null && p.playWhenReady != true) {
                            p.playWhenReady = true
                        }
                    },
                    onRelease = { v -> (v.tag as? ExoPlayer)?.release() }
                )
            } else {
                // Fallback preview image/logo
                AsyncImage(
                    model = buildImageRequest(ctx, item.logo ?: item.poster, headers),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(32.dp)
                )
            }

            // Channel name shown only on focus
            if (focused) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
            }
            // EPG pill stays
            if (epg.isNotBlank()) {
                Text(
                    text = epg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                )
            }
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppIconButton(icon = AppIcon.PlayCircle, contentDescription = "Öffnen", onClick = { onClick(item) }, size = 24.dp)
                AppIconButton(icon = AppIcon.Info, contentDescription = "Details", onClick = { onClick(item) }, size = 24.dp)
            }
        }
    }
}

@Composable
fun SeriesTileCard(
    item: MediaItem,
    onClick: (MediaItem) -> Unit,
    isNew: Boolean = false
) {
    val ctx = LocalContext.current
    val headers = rememberImageHeaders()
    var seasons by remember(item.streamId) { mutableStateOf<Int?>(null) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(item.streamId) {
        val sid = item.streamId
        if (sid != null) {
            try {
                val db = DbProvider.get(ctx)
                val count = withContext(Dispatchers.IO) { db.episodeDao().seasons(sid).size }
                seasons = count
            } catch (_: Throwable) { seasons = null }
        }
    }
    val shape = RoundedCornerShape(14.dp)
    val borderBrush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
    Card(
        modifier = Modifier
            .height(rowItemHeight().dp)
            .padding(end = 6.dp)
            .tvClickable(scaleFocused = 1.12f, scalePressed = 1.16f, elevationFocusedDp = 18f) { onClick(item) }
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .border(1.dp, borderBrush, shape)
            .drawWithContent {
                drawContent()
                // subtle top reflection
                val grad = Brush.verticalGradient(0f to Color.White.copy(alpha = if (focused) 0.18f else 0.10f), 1f to Color.Transparent)
                drawRect(brush = grad)
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape
    ) {
        Column(Modifier.fillMaxWidth()) {
            AsyncImage(
                model = buildImageRequest(ctx, item.poster ?: item.logo ?: item.backdrop, headers),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            if (focused) {
                val year = item.year ?: extractYearFrom(item.name)
                val title = item.name.substringAfter(" - ", item.name)
                Text(
                    text = if (year != null) "$title ($year)" else title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            if (isNew) {
                Text(
                    text = "NEU",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Red,
                    modifier = Modifier.padding(start = 10.dp, bottom = 8.dp)
                )
            }
            Row(Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                AppIconButton(icon = AppIcon.PlayCircle, contentDescription = "Abspielen", onClick = { onClick(item) }, size = 24.dp)
                AppIconButton(icon = AppIcon.Info, contentDescription = "Details", onClick = { onClick(item) }, size = 24.dp)
            }
        }
    }
}

@Composable
fun VodTileCard(
    item: MediaItem,
    onClick: (MediaItem) -> Unit,
    isNew: Boolean = false
) {
    val ctx = LocalContext.current
    val headers = rememberImageHeaders()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val borderBrush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
    Card(
        modifier = Modifier
            .height(rowItemHeight().dp)
            .padding(end = 6.dp)
            .tvClickable(scaleFocused = 1.12f, scalePressed = 1.16f, elevationFocusedDp = 18f) { onClick(item) }
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .border(1.dp, borderBrush, shape)
            .drawWithContent {
                drawContent()
                val grad = Brush.verticalGradient(0f to Color.White.copy(alpha = if (focused) 0.18f else 0.10f), 1f to Color.Transparent)
                drawRect(brush = grad)
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape
    ) {
        Column(Modifier.fillMaxWidth()) {
            AsyncImage(
                model = buildImageRequest(ctx, item.poster ?: item.logo ?: item.backdrop, headers),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            if (focused) {
                val y = item.year ?: extractYearFrom(item.name)
                val title = item.name.substringAfter(" - ", item.name)
                Text(
                    text = if (y != null) "$title ($y)" else title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            if (isNew) {
                Text(
                    text = "NEU",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Red,
                    modifier = Modifier.padding(start = 10.dp, bottom = 8.dp)
                )
            }
            Row(Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                AppIconButton(icon = AppIcon.PlayCircle, contentDescription = "Abspielen", onClick = { onClick(item) }, size = 24.dp)
                AppIconButton(icon = AppIcon.Info, contentDescription = "Details", onClick = { onClick(item) }, size = 24.dp)
            }
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
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state)
    LazyRow(
        state = state,
        flingBehavior = fling,
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
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state)
    var count by remember(items) { mutableStateOf(if (items.size < 30) items.size else 30) }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .collect { idx ->
                if (idx > count - 20 && count < items.size) {
                    count = (count + 50).coerceAtMost(items.size)
                }
            }
    }
    LazyRow(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
    ) {
        items(items.take(count), key = { it.id }) { m ->
            LiveTileCard(item = m, onClick = onClick)
        }
    }
}

/** Series row, horizontally scrollable */
@Composable
fun SeriesRow(
    items: List<MediaItem>,
    onClick: (MediaItem) -> Unit,
    showNew: Boolean = false
) {
    if (items.isEmpty()) return
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state)
    var count by remember(items) { mutableStateOf(if (items.size < 30) items.size else 30) }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .collect { idx ->
                if (idx > count - 20 && count < items.size) {
                    count = (count + 50).coerceAtMost(items.size)
                }
            }
    }
    LazyRow(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
    ) {
        items(items.take(count), key = { it.id }) { m ->
            SeriesTileCard(item = m, onClick = onClick, isNew = showNew)
        }
    }
}

/** VOD row, horizontally scrollable */
@Composable
fun VodRow(
    items: List<MediaItem>,
    onClick: (MediaItem) -> Unit,
    showNew: Boolean = false
) {
    if (items.isEmpty()) return
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state)
    var count by remember(items) { mutableStateOf(if (items.size < 30) items.size else 30) }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .collect { idx ->
                if (idx > count - 20 && count < items.size) {
                    count = (count + 50).coerceAtMost(items.size)
                }
            }
    }
    LazyRow(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
    ) {
        items(items.take(count), key = { it.id }) { m ->
            VodTileCard(item = m, onClick = onClick, isNew = showNew)
        }
    }
}
