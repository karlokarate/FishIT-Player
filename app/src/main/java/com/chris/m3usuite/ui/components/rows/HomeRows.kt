@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.chris.m3usuite.ui.util.rememberImageHeaders
import com.chris.m3usuite.ui.util.buildImageRequest
import com.chris.m3usuite.ui.fx.ShimmerBox
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import com.chris.m3usuite.ui.fx.tvFocusGlow
import com.chris.m3usuite.ui.fx.ShimmerCircle
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
// use fillMaxSize() for broad Compose compatibility
import androidx.compose.ui.draw.clip
import com.chris.m3usuite.domain.selectors.extractYearFrom
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.data.repo.EpgRepository
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.collectAsState
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.fx.ShimmerCircle
import com.chris.m3usuite.ui.common.AppIconButton
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.key.key

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
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    selected: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
    insertionLeft: Boolean = false,
    insertionRight: Boolean = false
) {
    val ctx = LocalContext.current
    val headers = rememberImageHeaders()
    val store = remember { SettingsStore(ctx) }
    val db = remember { DbProvider.get(ctx) }
    val ua by store.userAgent.collectAsState(initial = "")
    val ref by store.referer.collectAsState(initial = "")
    val extraJson by store.extraHeadersJson.collectAsState(initial = "")
    var epgNow by remember { mutableStateOf("") }
    var epgNext by remember { mutableStateOf("") }
    var nowStartMs by remember { mutableStateOf<Long?>(null) }
    var nowEndMs by remember { mutableStateOf<Long?>(null) }
    var epgProgress by remember { mutableStateOf<Float?>(null) }
    var focused by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    // Show cached EPG immediately if present (reactive to DB updates from prefetch)
    val epgChannelId = remember(item.epgChannelId) { item.epgChannelId?.trim().orEmpty() }
    if (epgChannelId.isNotEmpty()) {
        val row by remember(epgChannelId) { db.epgDao().observeByChannel(epgChannelId) }.collectAsState(initial = null)
        LaunchedEffect(row?.updatedAt) {
            epgNow = row?.nowTitle.orEmpty()
            epgNext = row?.nextTitle.orEmpty()
            nowStartMs = row?.nowStartMs
            nowEndMs = row?.nowEndMs
            epgProgress = if (nowStartMs != null && nowEndMs != null && nowEndMs!! > nowStartMs!!) {
                val now = System.currentTimeMillis()
                ((now - nowStartMs!!).coerceAtLeast(0).toFloat() / (nowEndMs!! - nowStartMs!!).toFloat()).coerceIn(0f, 1f)
            } else null
        }
    }

    // Load EPG when tile becomes active (focused). Prefetch happens separately for favorites.
    LaunchedEffect(item.streamId, focused) {
        val sid = item.streamId ?: return@LaunchedEffect
        if (!focused) return@LaunchedEffect
        try {
            val repo = EpgRepository(ctx, SettingsStore(ctx))
            val list = repo.nowNext(sid, 2)
            val first = list.getOrNull(0)
            val second = list.getOrNull(1)
            epgNow = first?.title.orEmpty()
            epgNext = second?.title.orEmpty()
            val start = first?.start?.toLongOrNull()?.let { it * 1000 }
            val end = first?.end?.toLongOrNull()?.let { it * 1000 }
            nowStartMs = start; nowEndMs = end
            if (start != null && end != null && end > start) {
                val now = System.currentTimeMillis()
                val progress = ((now - start).coerceAtLeast(0).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
                epgProgress = progress
            } else epgProgress = null
        } catch (_: Throwable) { epgNow = ""; epgNext = "" }
    }
    val shape = RoundedCornerShape(14.dp)
    val borderBrush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
    Card(
        modifier = Modifier
            .height(rowItemHeight().dp)
            .padding(end = 6.dp)
            .combinedClickable(
                onClick = { onOpenDetails(item) },
                onLongClick = { onLongPress?.invoke() }
            )
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp) {
                    when (ev.key) {
                        Key.DirectionLeft -> { onMoveLeft?.invoke(); return@onPreviewKeyEvent onMoveLeft != null }
                        Key.DirectionRight -> { onMoveRight?.invoke(); return@onPreviewKeyEvent onMoveRight != null }
                        else -> {}
                    }
                }
                false
            }
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .border(1.dp, borderBrush, shape)
            .drawWithContent {
                drawContent()
                val grad = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 1f to Color.Transparent)
                drawRect(brush = grad)
            }
            .tvFocusGlow(focused = focused, shape = shape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Explicit insertion indicator lines inside tile bounds
            if (insertionLeft) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            if (insertionRight) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            if (preview && !item.url.isNullOrBlank()) {
                val url = item.url!!
                AndroidView(
                    factory = { c ->
                        val view = PlayerView(c)
                        val httpFactory = DefaultHttpDataSource.Factory()
                            .setAllowCrossProtocolRedirects(true)
                            .apply {
                                val base = buildMap<String, String> {
                                    if (ua.isNotBlank()) put("User-Agent", ua)
                                    if (ref.isNotBlank()) put("Referer", ref)
                                }
                                val extras = com.chris.m3usuite.core.http.RequestHeadersProvider.parseExtraHeaders(extraJson)
                                val merged = com.chris.m3usuite.core.http.RequestHeadersProvider.merge(base, extras)
                                if (merged.isNotEmpty()) setDefaultRequestProperties(merged)
                            }
                        val dsFactory = DefaultDataSource.Factory(c, httpFactory)
                        val mediaFactory = DefaultMediaSourceFactory(dsFactory)
                        val player = ExoPlayer.Builder(c)
                            .setMediaSourceFactory(mediaFactory)
                            .build().apply {
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
                    update = { v -> (v.tag as? ExoPlayer)?.let { if (!it.playWhenReady) it.playWhenReady = true } },
                    onRelease = { v -> (v.tag as? ExoPlayer)?.release() }
                )
            }

            // Circular logo with shimmer placeholder (top-aligned)
            val sz = 77.dp
            val logoTop = 8.dp
            val epgTop = logoTop + sz + 8.dp
            val labelTop = if (epgNow.isNotBlank() || epgNext.isNotBlank()) epgTop + 8.dp else (logoTop + sz + 8.dp)
            val logoUrl = item.logo ?: item.poster
            run {
                var loaded by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = logoTop)
                        .size(sz)
                ) {
                    if (!loaded) {
                        ShimmerCircle(Modifier.fillMaxSize())
                    }
                    if (logoUrl != null) {
                        AsyncImage(
                            model = buildImageRequest(ctx, logoUrl, headers),
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape),
                            onLoading = { loaded = false },
                            onSuccess = { loaded = true },
                            onError   = { loaded = true }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
                        )
                    }
                }
            }

            // Selection overlay
            if (selected) {
                Box(Modifier.fillMaxWidth().fillMaxHeight().graphicsLayer { alpha = 0.18f }.background(Color.Yellow.copy(alpha = 0.2f)))
            }

            // Channel name always visible (bottom center)
            // Moved play/info below the name to avoid covering the logo
            // LIVE indicator (top-left)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1DB954))
            )

            // EPG under logo (now + next)
            if (epgNow.isNotBlank() || epgNext.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = epgTop)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (epgNow.isNotBlank()) {
                        val meta = remember(nowStartMs, nowEndMs) {
                            if (nowStartMs != null && nowEndMs != null && nowEndMs!! > nowStartMs!!) {
                                val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                val range = fmt.format(java.util.Date(nowStartMs!!)) + "–" + fmt.format(java.util.Date(nowEndMs!!))
                                val rem = ((nowEndMs!! - System.currentTimeMillis()).coerceAtLeast(0L) / 60000L).toInt()
                                " $range • noch ${rem}m"
                            } else ""
                        }
                        Text(
                            text = "Jetzt: ${epgNow}${meta}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (epgNext.isNotBlank()) {
                        Text(
                            text = "Danach: ${epgNext}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            // Label und Actions direkt unter dem Icon platzieren (keine Überlagerung)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = labelTop, start = 8.dp, end = 8.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIconButton(icon = AppIcon.PlayCircle, contentDescription = "Abspielen", onClick = { onPlayDirect(item) }, size = 24.dp)
                    AppIconButton(icon = AppIcon.Info, contentDescription = "Details", onClick = { onOpenDetails(item) }, size = 24.dp)
                }
                // Sendername im Rahmen mit dunklem BG (70% Opacity), weiße Schrift, bis 2 Zeilen
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            // Progress bar (current programme)
            epgProgress?.let { p ->
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
                        .fillMaxWidth(p)
                        .height(3.dp)
                        .background(Color(0xFF2196F3))
                )
            }
        }
    }
}

@Composable
fun SeriesTileCard(
    item: MediaItem,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: (MediaItem) -> Unit,
    isNew: Boolean = false,
    showAssign: Boolean = true
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
    var armed by remember { mutableStateOf(false) }
    var armTime by remember { mutableStateOf(0L) }
    Card(
        modifier = Modifier
            .height(rowItemHeight().dp)
            .padding(end = 6.dp)
            .tvClickable(scaleFocused = 1.12f, scalePressed = 1.16f, elevationFocusedDp = 18f) {
                val now = System.currentTimeMillis()
                if (armed && now - armTime < 1500) {
                    armed = false
                    onOpenDetails(item)
                } else {
                    armed = true
                    armTime = now
                }
            }
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
            run {
                var loaded by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (!loaded) ShimmerBox(
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                        cornerRadius = 0.dp
                    )
                    AsyncImage(
                        model = buildImageRequest(ctx, item.poster ?: item.logo ?: item.backdrop, headers),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onLoading = { loaded = false },
                        onSuccess = { loaded = true },
                        onError = { loaded = true }
                    )
                    // Minimal resume tooltip on focus (series)
                    val db = remember(ctx) { DbProvider.get(ctx) }
                    var resumeSecs by remember(item.streamId) { mutableStateOf<Int?>(null) }
                    var totalSecs by remember(item.streamId) { mutableStateOf<Int?>(null) }
                    LaunchedEffect(item.streamId) {
                        val sid = item.streamId
                        if (sid != null) {
                            try {
                                val last = withContext(Dispatchers.IO) { db.resumeDao().recentEpisodes(50).firstOrNull { it.seriesStreamId == sid } }
                                if (last != null) {
                                    resumeSecs = last.positionSecs
                                    val ep = withContext(Dispatchers.IO) { db.episodeDao().byEpisodeId(last.episodeId) }
                                    totalSecs = ep?.durationSecs
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                    // Thin progress line near bottom if last-episode resume exists
                    if ((resumeSecs ?: 0) > 0 && (totalSecs ?: 0) > 0) {
                        val errorColor = MaterialTheme.colorScheme.error
                        Canvas(Modifier.matchParentSize()) {
                            val w = size.width
                            val h = size.height
                            val y = h - 10f
                            val margin = w * 0.06f
                            val start = Offset(margin, y)
                            val end = Offset(w - margin, y)
                            val frac = (resumeSecs!!.toFloat() / totalSecs!!.toFloat()).coerceIn(0f, 1f)
                            val fillEnd = Offset(start.x + (end.x - start.x) * frac, y)
                            drawLine(color = Color.White.copy(alpha = 0.35f), start = start, end = end, strokeWidth = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            drawLine(color = errorColor, start = start, end = fillEnd, strokeWidth = 3.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                    }
                    if (focused && (resumeSecs ?: 0) > 0 && (totalSecs ?: 0) > 0) {
                        val secs = resumeSecs!!.coerceAtLeast(0)
                        val total = totalSecs!!.coerceAtLeast(1)
                        val pct = (secs * 100) / total
                        Box(Modifier.matchParentSize()) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.65f),
                                contentColor = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp)
                            ) {
                                Text(
                                    text = "Weiter ${String.format("%d:%02d", secs / 60, secs % 60)} (${pct}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
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
                AppIconButton(icon = AppIcon.PlayCircle, contentDescription = "Abspielen", onClick = { onPlayDirect(item) }, size = 24.dp)
                if (showAssign) {
                    AppIconButton(icon = AppIcon.BookmarkAdd, contentDescription = "Für Kinder freigeben", onClick = { onAssignToKid(item) }, size = 24.dp)
                }
            }
        }
    }
}

@Composable
fun VodTileCard(
    item: MediaItem,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: (MediaItem) -> Unit,
    isNew: Boolean = false,
    showAssign: Boolean = true
) {
    val ctx = LocalContext.current
    val headers = rememberImageHeaders()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val borderBrush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
    var armed by remember { mutableStateOf(false) }
    var armTime by remember { mutableStateOf(0L) }
    Card(
        modifier = Modifier
            .height(rowItemHeight().dp)
            .padding(end = 6.dp)
            .tvClickable(scaleFocused = 1.12f, scalePressed = 1.16f, elevationFocusedDp = 18f) {
                val now = System.currentTimeMillis()
                if (armed && now - armTime < 1500) {
                    armed = false
                    onOpenDetails(item)
                } else {
                    armed = true
                    armTime = now
                }
            }
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
            run {
                var loaded by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (!loaded) ShimmerBox(
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                        cornerRadius = 0.dp
                    )
                    AsyncImage(
                        model = buildImageRequest(ctx, item.poster ?: item.logo ?: item.backdrop, headers),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onLoading = { loaded = false },
                        onSuccess = { loaded = true },
                        onError = { loaded = true }
                    )
                    // Resume progress overlay (thin line near bottom)
                    val db = remember(ctx) { DbProvider.get(ctx) }
                    var resumeSecs by remember(item.id) { mutableStateOf<Int?>(null) }
                    LaunchedEffect(item.id) {
                        try { resumeSecs = withContext(Dispatchers.IO) { db.resumeDao().getVod(item.id)?.positionSecs } } catch (_: Throwable) {}
                    }
                    val total = item.durationSecs ?: 0
                    if ((resumeSecs ?: 0) > 0 && total > 0) {
                        val errorColor = MaterialTheme.colorScheme.error
                        Canvas(Modifier.matchParentSize()) {
                            val w = size.width
                            val h = size.height
                            val y = h - 10f
                            val margin = w * 0.06f
                            val start = Offset(margin, y)
                            val end = Offset(w - margin, y)
                            val frac = (resumeSecs!!.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            val fillEnd = Offset(start.x + (end.x - start.x) * frac, y)
                            drawLine(color = Color.White.copy(alpha = 0.35f), start = start, end = end, strokeWidth = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            drawLine(color = errorColor, start = start, end = fillEnd, strokeWidth = 3.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                        // Minimal tooltip on focus
                        if (focused) {
                            val secs = resumeSecs!!.coerceAtLeast(0)
                            val pct = if (total > 0) ((secs * 100) / total) else 0
                            Box(Modifier.matchParentSize()) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.Black.copy(alpha = 0.65f),
                                    contentColor = Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp)
                                ) {
                                    Text(
                                        text = "Weiter ${String.format("%d:%02d", secs / 60, secs % 60)} (${pct}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
                AppIconButton(icon = AppIcon.PlayCircle, contentDescription = "Abspielen", onClick = { onPlayDirect(item) }, size = 24.dp)
                if (showAssign) {
                    AppIconButton(icon = AppIcon.BookmarkAdd, contentDescription = "Für Kinder freigeben", onClick = { onAssignToKid(item) }, size = 24.dp)
                }
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
    // Ensure no duplicate ids to keep LazyRow keys unique
    val slice = remember(items) { items.distinctBy { it.id }.take(5) }
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
    leading: (@Composable (() -> Unit))? = null,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return
    // Deduplicate by stable id to avoid key collisions
    val unique = remember(items) { items.distinctBy { it.id } }
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state)
    var count by remember(unique) { mutableStateOf(if (unique.size < 30) unique.size else 30) }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .collect { idx ->
                if (idx > count - 20 && count < unique.size) {
                    count = (count + 50).coerceAtMost(unique.size)
                }
            }
    }
    LazyRow(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
    ) {
        if (leading != null) {
            item("leading") { leading() }
        }
        items(unique.take(count), key = { it.id }) { m ->
            LiveTileCard(item = m, onOpenDetails = onOpenDetails, onPlayDirect = onPlayDirect)
        }
    }
}

@Composable
fun LiveAddTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val borderBrush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
    Card(
        modifier = Modifier
            .height(rowItemHeight().dp)
            .padding(end = 6.dp)
            .tvClickable(scaleFocused = 1.12f, scalePressed = 1.16f, elevationFocusedDp = 18f) { onClick() }
            .border(1.dp, borderBrush, shape)
            .drawWithContent {
                drawContent()
                val grad = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 1f to Color.Transparent)
                drawRect(brush = grad)
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape
    ) {
        Box(Modifier.fillMaxWidth()) {
            AppIconButton(
                icon = AppIcon.BookmarkAdd,
                contentDescription = "Sender hinzufügen",
                onClick = onClick,
                modifier = Modifier.align(Alignment.Center),
                size = 36.dp
            )
        }
    }
}

@Composable
fun ReorderableLiveRow(
    items: List<MediaItem>,
    onOpen: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    onAdd: () -> Unit,
    onReorder: (List<Long>) -> Unit,
    onRemove: (List<Long>) -> Unit
) {
    val state = rememberLazyListState()
    // Ensure stable, unique keys to avoid LazyRow key collisions
    val order = remember(items) { androidx.compose.runtime.mutableStateListOf<Long>().apply { addAll(items.map { it.id }.distinct()) } }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var targetKey by remember { mutableStateOf<Long?>(null) }
    var insertAfter by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val tileLift by animateFloatAsState(if (draggingId != null) 1.05f else 1f, label = "lift")

    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
    ) {
        item("leading") { LiveAddTile(onClick = onAdd) }
        items(order, key = { it }) { id ->
            val mi = items.find { it.id == id } ?: return@items
            val isDragging = draggingId == id
            val dragTranslationX = if (isDragging) dragOffset else 0f
            val trans by animateFloatAsState(dragTranslationX, label = "drag")
            val padStart = if (targetKey == id && !insertAfter) 10.dp else 0.dp
            val padEnd = if (targetKey == id && insertAfter) 10.dp else 0.dp
            Box(
                Modifier
                    .padding(start = padStart, end = padEnd)
                    .graphicsLayer { translationX = trans; scaleX = if (isDragging) tileLift else 1f; scaleY = if (isDragging) tileLift else 1f; shadowElevation = if (isDragging) 18f else 0f }
                    .pointerInput(id) {
                        // Drag starts only after a long press to keep swipe-scrolling smooth on touch devices
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggingId = id; dragOffset = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.x
                                val visible = state.layoutInfo.visibleItemsInfo
                                val current = visible.find { it.key == id }
                                if (current != null) {
                                    val center = current.offset + dragOffset + current.size / 2f
                                    // Only consider real channel items (exclude leading/add tile and any non-Long keys)
                                    val others = visible.filter { it.key is Long && it.key != id }
                                    val target = others.minByOrNull { kotlin.math.abs(center - (it.offset + it.size / 2f)) }
                                    if (target != null) {
                                        val toKey = target.key as Long
                                        val from = order.indexOf(id)
                                        val to = order.indexOf(toKey)
                                        insertAfter = center > (target.offset + target.size / 2f)
                                        targetKey = toKey
                                        if (from != -1 && to != -1 && from != to) {
                                            order.removeAt(from)
                                            val insertIndex = if (insertAfter) to + (if (from < to) 0 else 1) else to + (if (from < to) -1 else 0)
                                            order.add(insertIndex.coerceIn(0, order.size), id)
                                        }
                                    }
                                }
                            },
                            onDragEnd = { draggingId = null; dragOffset = 0f; targetKey = null; onReorder(order.toList()) },
                            onDragCancel = { draggingId = null; dragOffset = 0f; targetKey = null }
                        )
                    }
            ) {
                LiveTileCard(
                    item = mi,
                    onOpenDetails = { if (draggingId == null) onOpen(mi.id) },
                    onPlayDirect = { if (draggingId == null) onPlay(mi.id) },
                    selected = id in selected,
                    onLongPress = { selected = if (id in selected) selected - id else selected + id },
                    onMoveLeft = {
                        val idx = order.indexOf(id)
                        if (idx > 0) { order.removeAt(idx); order.add(idx - 1, id); onReorder(order.toList()) }
                    },
                    onMoveRight = {
                        val idx = order.indexOf(id)
                        if (idx != -1 && idx < order.lastIndex) { order.removeAt(idx); order.add(idx + 1, id); onReorder(order.toList()) }
                    },
                    insertionLeft = (targetKey == id && !insertAfter),
                    insertionRight = (targetKey == id && insertAfter)
                )
            }
        }
    }
    if (selected.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
            TextButton(onClick = { selected = emptySet() }) { Text("Abbrechen") }
            if (selected.size == 1) {
                val id = selected.first()
                TextButton(onClick = {
                    val idx = order.indexOf(id)
                    if (idx > 0) { order.removeAt(idx); order.add(idx - 1, id); onReorder(order.toList()) }
                }) { Text("← Verschieben") }
                TextButton(onClick = {
                    val idx = order.indexOf(id)
                    if (idx != -1 && idx < order.lastIndex) { order.removeAt(idx); order.add(idx + 1, id); onReorder(order.toList()) }
                }) { Text("Verschieben →") }
            }
            TextButton(onClick = { onRemove(selected.toList()); selected = emptySet() }) { Text("Entfernen (${selected.size})") }
        }
    }
}

/** Series row, horizontally scrollable */
@Composable
fun SeriesRow(
    items: List<MediaItem>,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: (MediaItem) -> Unit,
    showNew: Boolean = false,
    showAssign: Boolean = true
) {
    if (items.isEmpty()) return
    // Deduplicate by id to keep keys stable/unique
    val unique = remember(items) { items.distinctBy { it.id } }
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state)
    var count by remember(unique) { mutableStateOf(if (unique.size < 30) unique.size else 30) }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .collect { idx ->
                if (idx > count - 20 && count < unique.size) {
                    count = (count + 50).coerceAtMost(unique.size)
                }
            }
    }
    LazyRow(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
    ) {
        items(unique.take(count), key = { it.id }) { m ->
            SeriesTileCard(
                item = m,
                onOpenDetails = onOpenDetails,
                onPlayDirect = onPlayDirect,
                onAssignToKid = onAssignToKid,
                isNew = showNew,
                showAssign = showAssign
            )
        }
    }
}

/** VOD row, horizontally scrollable */
@Composable
fun VodRow(
    items: List<MediaItem>,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: (MediaItem) -> Unit,
    showNew: Boolean = false,
    showAssign: Boolean = true
) {
    if (items.isEmpty()) return
    // Deduplicate by id to keep keys stable/unique
    val unique = remember(items) { items.distinctBy { it.id } }
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state)
    var count by remember(unique) { mutableStateOf(if (unique.size < 30) unique.size else 30) }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .collect { idx ->
                if (idx > count - 20 && count < unique.size) {
                    count = (count + 50).coerceAtMost(unique.size)
                }
            }
    }
    LazyRow(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
    ) {
        items(unique.take(count), key = { it.id }) { m ->
            VodTileCard(
                item = m,
                onOpenDetails = onOpenDetails,
                onPlayDirect = onPlayDirect,
                onAssignToKid = onAssignToKid,
                isNew = showNew,
                showAssign = showAssign
            )
        }
    }
}
