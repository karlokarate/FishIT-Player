@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
package com.chris.m3usuite.ui.components.rows

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import com.chris.m3usuite.ui.compat.focusGroup
import androidx.compose.ui.viewinterop.AndroidView
import android.net.Uri
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
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
import com.chris.m3usuite.ui.util.rememberImageHeaders
import com.chris.m3usuite.ui.util.AppAsyncImage
import com.chris.m3usuite.ui.fx.ShimmerBox
import com.chris.m3usuite.ui.components.common.FocusTitleOverlay
import com.chris.m3usuite.ui.skin.tvClickable
import com.chris.m3usuite.ui.skin.focusScaleOnTv
import com.chris.m3usuite.ui.fx.tvFocusGlow
// isTvDevice removed (unused)
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.focusable
import androidx.compose.ui.layout.ContentScale
import com.chris.m3usuite.model.MediaItem
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import com.chris.m3usuite.ui.fx.ShimmerCircle
// use fillMaxSize() for broad Compose compatibility
import androidx.compose.ui.draw.clip
import com.chris.m3usuite.domain.selectors.extractYearFrom
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.data.repo.EpgRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.FlowPreview
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import android.os.SystemClock
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.key.key
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.media3.common.util.UnstableApi
import androidx.paging.compose.LazyPagingItems
import androidx.paging.LoadState

val LocalRowItemHeightOverride = compositionLocalOf<Int?> { null }
private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val LIVE_TILE_ASPECT_RATIO = 16f / 9f
private val TILE_SHAPE = RoundedCornerShape(14.dp)

@Composable
private fun PlayOverlay(visible: Boolean, sizeDp: Int = 56) {
    val a by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(150), label = "playFade")
    if (a > 0f) {
        Box(Modifier.fillMaxSize()) {
            AppIconButton(
                icon = AppIcon.PlayCircle,
                contentDescription = "Abspielen",
                onClick = {},
                modifier = Modifier.align(Alignment.Center).alpha(a),
                size = sizeDp.dp
            )
        }
    }
}

@Composable
fun rowItemHeight(): Int {
    LocalRowItemHeightOverride.current?.let { return it }
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
    val chromeToggle by rememberUpdatedState(com.chris.m3usuite.ui.home.LocalChromeToggle.current)
    val tileHeight = rowItemHeight().dp
    val tileWidth = tileHeight * POSTER_ASPECT_RATIO
    var focused by remember { mutableStateOf(false) }
    var leftDownAt by remember { mutableStateOf<Long?>(null) }
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .width(tileWidth)
            .padding(end = 12.dp)
            .focusable()
            .onPreviewKeyEvent { ev ->
                val toggle = chromeToggle
                if (toggle == null) return@onPreviewKeyEvent false
                if (ev.key != Key.DirectionLeft) return@onPreviewKeyEvent false
                if (ev.type == KeyEventType.KeyDown) { leftDownAt = SystemClock.uptimeMillis(); false }
                else if (ev.type == KeyEventType.KeyUp) {
                    val start = leftDownAt; leftDownAt = null
                    if (start != null && SystemClock.uptimeMillis() - start >= 300L) {
                        com.chris.m3usuite.core.debug.GlobalDebug.logDpad("LEFT_LONG", mapOf("tile" to (item.streamId ?: item.id), "type" to item.type))
                        toggle(); true
                    } else false
                } else false
            }
            .onKeyEvent { ev ->
                val n = ev.nativeKeyEvent
                if (n.action == android.view.KeyEvent.ACTION_UP) {
                    val code = n.keyCode
                    if (code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                        com.chris.m3usuite.core.debug.GlobalDebug.logDpad("CENTER", mapOf("tile" to (item.streamId ?: item.id), "type" to item.type))
                    }
                }
                false
            }
            .onFocusEvent { focused = it.isFocused || it.hasFocus }
            .focusScaleOnTv(focusedScale = 1.12f, pressedScale = 1.12f)
            .tvClickable(
                brightenContent = false,
                autoBringIntoView = false,
                scaleFocused = 1f,
                scalePressed = 1.02f
            ) { onClick(item) }
            .tvFocusGlow(focused = focused, shape = TILE_SHAPE)
    ) {
        // Debug: log focused tile + OBX title in parentheses + tree path
        LaunchedEffect(focused, item.streamId, item.type) {
            if (focused) {
                val sid = item.streamId
                if (sid != null) {
                    val ctxLocal = ctx
                    val obxTitle = withContext(Dispatchers.IO) {
                        runCatching {
                            val store = com.chris.m3usuite.data.obx.ObxStore.get(ctxLocal)
                            when (item.type) {
                                "live" -> store.boxFor(com.chris.m3usuite.data.obx.ObxLive::class.java)
                                    .query(com.chris.m3usuite.data.obx.ObxLive_.streamId.equal(sid))
                                    .build().findFirst()?.name
                                "vod" -> store.boxFor(com.chris.m3usuite.data.obx.ObxVod::class.java)
                                    .query(com.chris.m3usuite.data.obx.ObxVod_.vodId.equal(sid))
                                    .build().findFirst()?.name
                                "series" -> store.boxFor(com.chris.m3usuite.data.obx.ObxSeries::class.java)
                                    .query(com.chris.m3usuite.data.obx.ObxSeries_.seriesId.equal(sid))
                                    .build().findFirst()?.name
                                else -> null
                            }
                        }.getOrNull()
                    }
                    com.chris.m3usuite.core.debug.GlobalDebug.logTileFocus(item.type, sid.toString(), item.name, obxTitle)
                    val node = when (item.type) { "live" -> "row:live"; "vod" -> "row:vod"; "series" -> "row:series"; else -> "row:?" }
                    com.chris.m3usuite.core.debug.GlobalDebug.logTree(node, "tile:$sid")
                }
            }
        }
        // Prefer poster/logo/backdrop in this order (fallback to any image field in MediaItem)
        val raw = remember(item.poster ?: item.logo ?: item.backdrop) {
            item.poster ?: item.logo ?: item.backdrop
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(tileHeight)
                .clip(TILE_SHAPE)
        ) {
            AppAsyncImage(
                url = raw,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                crossfade = false
            )
            com.chris.m3usuite.ui.components.common.FocusTitleOverlay(
                title = item.name,
                focused = focused
            )
        }
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

@androidx.annotation.OptIn(UnstableApi::class)
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
    val chromeToggle by rememberUpdatedState(com.chris.m3usuite.ui.home.LocalChromeToggle.current)
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val headers = rememberImageHeaders()
    val store = remember { com.chris.m3usuite.prefs.SettingsStore(ctx) }
    val epgRepo = remember(store) { EpgRepository(ctx, store) }
    val roomEnabled by store.roomEnabled.collectAsStateWithLifecycle(initialValue = false)
    val ua by store.userAgent.collectAsStateWithLifecycle(initialValue = "")
    val ref by store.referer.collectAsStateWithLifecycle(initialValue = "")
    val extraJson by store.extraHeadersJson.collectAsStateWithLifecycle(initialValue = "")
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
        // Subscribe to OBX EPG updates by channelId
        val box = remember { com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxEpgNowNext::class.java) }
        DisposableEffect(epgChannelId) {
            val q = box.query(com.chris.m3usuite.data.obx.ObxEpgNowNext_.channelId.equal(epgChannelId)).build()
            fun apply(row: com.chris.m3usuite.data.obx.ObxEpgNowNext?) {
                epgNow = row?.nowTitle.orEmpty()
                epgNext = row?.nextTitle.orEmpty()
                nowStartMs = row?.nowStartMs
                nowEndMs = row?.nowEndMs
                epgProgress = if (nowStartMs != null && nowEndMs != null && nowEndMs!! > nowStartMs!!) {
                    val now = System.currentTimeMillis()
                    ((now - nowStartMs!!).coerceAtLeast(0).toFloat() / (nowEndMs!! - nowStartMs!!).toFloat()).coerceIn(0f, 1f)
                } else null
            }
            apply(q.findFirst())
            val sub = q.subscribe().on(io.objectbox.android.AndroidScheduler.mainThread()).observer { res -> apply(res.firstOrNull()) }
            onDispose { sub.cancel() }
        }
    }

    // Load EPG when tile becomes active (focused). Prefetch happens separately for favorites.
    @OptIn(FlowPreview::class)
    LaunchedEffect(item.streamId) {
        val sid = item.streamId ?: return@LaunchedEffect
        if (item.type != "live") return@LaunchedEffect
        snapshotFlow { focused }
            .distinctUntilChanged()
            .debounce(120)
            .filter { it }
            .collect {
                runCatching {
                    val list = epgRepo.nowNext(sid, 2)
                    val first = list.getOrNull(0)
                    val second = list.getOrNull(1)
                    epgNow = first?.title.orEmpty()
                    epgNext = second?.title.orEmpty()
                    val start = first?.start?.toLongOrNull()?.let { it * 1000 }
                    val end = first?.end?.toLongOrNull()?.let { it * 1000 }
                    nowStartMs = start; nowEndMs = end
                    epgProgress = if (start != null && end != null && end > start) {
                        val now = System.currentTimeMillis()
                        ((now - start).coerceAtLeast(0).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
                    } else null
                }.onFailure { epgNow = ""; epgNext = "" }
            }
    }
    val shape = RoundedCornerShape(14.dp)
    val borderBrush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
    val tileHeight = rowItemHeight().dp
    val tileWidth = tileHeight * LIVE_TILE_ASPECT_RATIO
    var leftDownAtMs by remember { mutableStateOf<Long?>(null) }
    val navKeysMod = if (onMoveLeft != null || onMoveRight != null) {
        Modifier.onPreviewKeyEvent { ev ->
            when (ev.type) {
                KeyEventType.KeyDown -> {
                    if (ev.key == Key.DirectionLeft) leftDownAtMs = SystemClock.uptimeMillis()
                    false
                }
                KeyEventType.KeyUp -> {
                    when (ev.key) {
                        Key.DirectionLeft -> {
                            val start = leftDownAtMs; leftDownAtMs = null
                            if (start != null && SystemClock.uptimeMillis() - start >= 300L) {
                                com.chris.m3usuite.core.debug.GlobalDebug.logDpad("LEFT_LONG", mapOf("tile" to (item.streamId ?: item.id), "type" to item.type))
                                val toggle = chromeToggle
                                if (toggle != null) { toggle(); return@onPreviewKeyEvent true }
                            }
                            com.chris.m3usuite.core.debug.GlobalDebug.logDpad("LEFT", mapOf("tile" to (item.streamId ?: item.id), "type" to item.type))
                            onMoveLeft?.invoke() ?: focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionRight -> {
                            com.chris.m3usuite.core.debug.GlobalDebug.logDpad("RIGHT", mapOf("tile" to (item.streamId ?: item.id), "type" to item.type))
                            onMoveRight?.invoke() ?: focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)
                            return@onPreviewKeyEvent true
                        }
                        else -> {}
                    }
                    false
                }
                else -> false
            }
        }
    } else Modifier

    Card(
        modifier = Modifier
            .height(tileHeight)
            .width(tileWidth)
            .padding(end = 6.dp)
            .focusable()
            .onPreviewKeyEvent { ev ->
                val toggle = chromeToggle
                if (toggle == null) return@onPreviewKeyEvent false
                if (ev.key != Key.DirectionLeft) return@onPreviewKeyEvent false
                if (ev.type == KeyEventType.KeyDown) { leftDownAtMs = SystemClock.uptimeMillis(); false }
                else if (ev.type == KeyEventType.KeyUp) {
                    val start = leftDownAtMs; leftDownAtMs = null
                    if (start != null && SystemClock.uptimeMillis() - start >= 300L) {
                        com.chris.m3usuite.core.debug.GlobalDebug.logDpad("LEFT_LONG", mapOf("tile" to (item.streamId ?: item.id), "type" to item.type))
                        toggle(); true
                    } else false
                } else false
            }
            .onKeyEvent { ev ->
                val n = ev.nativeKeyEvent
                if (n.action == android.view.KeyEvent.ACTION_UP) {
                    val code = n.keyCode
                    if (code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                        com.chris.m3usuite.core.debug.GlobalDebug.logDpad("CENTER", mapOf("tile" to (item.streamId ?: item.id), "type" to item.type))
                    }
                }
                false
            }
            .combinedClickable(
                onClick = { onOpenDetails(item) },
                onLongClick = { onLongPress?.invoke() }
            )
            .then(navKeysMod)
            .onFocusEvent { focused = it.isFocused || it.hasFocus }
            .focusScaleOnTv(focusedScale = 1.12f, pressedScale = 1.12f)
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
        // Debug: log focused tile + OBX title in parentheses + tree path
        LaunchedEffect(focused, item.streamId) {
            if (focused) {
                val sid = item.streamId
                if (sid != null) {
                    val ctxLocal = ctx
                    val obxTitle = withContext(Dispatchers.IO) {
                        runCatching {
                            val store = com.chris.m3usuite.data.obx.ObxStore.get(ctxLocal)
                            store.boxFor(com.chris.m3usuite.data.obx.ObxLive::class.java)
                                .query(com.chris.m3usuite.data.obx.ObxLive_.streamId.equal(sid))
                                .build().findFirst()?.name
                        }.getOrNull()
                    }
                    com.chris.m3usuite.core.debug.GlobalDebug.logTileFocus("live", sid.toString(), item.name, obxTitle)
                    com.chris.m3usuite.core.debug.GlobalDebug.logTree("row:live", "tile:$sid")
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(tileHeight)
        ) {
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
                        val httpFactory = DefaultHttpDataSource.Factory().apply {
                            // Prefer explicit UA at factory level
                            val effUa = (if (ua.isNotBlank()) ua else "IBOPlayer/1.4 (Android)")
                            if (effUa.isNotBlank()) setUserAgent(effUa)
                        }
                            .setAllowCrossProtocolRedirects(true)
                            .apply {
                                val base = buildMap<String, String> {
                                    val effUa = (if (ua.isNotBlank()) ua else "IBOPlayer/1.4 (Android)")
                                    if (effUa.isNotBlank()) put("User-Agent", effUa)
                                    if (ref.isNotBlank()) put("Referer", ref)
                                    put("Accept", "*/*")
                                }
                                val extras = com.chris.m3usuite.core.http.RequestHeadersProvider.parseExtraHeaders(extraJson)
                                val merged = com.chris.m3usuite.core.http.RequestHeadersProvider.merge(base, extras)
                                runCatching {
                                    android.util.Log.d(
                                        "TilePreviewHTTP",
                                        "prepare ua=\"${merged["User-Agent"] ?: ""}\" ref=\"${merged["Referer"] ?: ""}\" accept=\"${merged["Accept"] ?: ""}\""
                                    )
                                }
                                if (merged.isNotEmpty()) setDefaultRequestProperties(merged)
                            }
                        val dsFactory = DefaultDataSource.Factory(c, httpFactory)
                        val mediaFactory = DefaultMediaSourceFactory(dsFactory)
                        val renderers = DefaultRenderersFactory(c)
                            .setEnableDecoderFallback(true)
                            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                        val player = ExoPlayer.Builder(c)
                            .setRenderersFactory(renderers)
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
                        AppAsyncImage(
                            url = logoUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape),
                            crossfade = false,
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
            // Center play overlay while focused
            PlayOverlay(visible = focused)
            FocusTitleOverlay(
                title = item.name,
                focused = focused
            )
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
    var leftDownAt by remember { mutableStateOf<Long?>(null) }
    val chromeToggle by rememberUpdatedState(com.chris.m3usuite.ui.home.LocalChromeToggle.current)
    val headers = rememberImageHeaders()
    var seasons by remember(item.streamId) { mutableStateOf<Int?>(null) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(item.streamId) {
        val sid = item.streamId ?: return@LaunchedEffect
        try {
            val obx = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
            val eps = withContext(Dispatchers.IO) { obx.boxFor(com.chris.m3usuite.data.obx.ObxEpisode::class.java).query(com.chris.m3usuite.data.obx.ObxEpisode_.seriesId.equal(sid.toLong())).build().find() }
            seasons = eps.map { it.season }.distinct().size
        } catch (_: Throwable) { seasons = null }
    }
    val shape = RoundedCornerShape(14.dp)
    val borderBrush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
    val tileHeight = rowItemHeight().dp
    val tileWidth = tileHeight * POSTER_ASPECT_RATIO
    Card(
        modifier = Modifier
            .height(tileHeight)
            .width(tileWidth)
            .padding(end = 6.dp)
            .focusable()
            .onPreviewKeyEvent { ev ->
                val toggle = chromeToggle
                if (toggle == null) return@onPreviewKeyEvent false
                if (ev.key != Key.DirectionLeft) return@onPreviewKeyEvent false
                if (ev.type == KeyEventType.KeyDown) { leftDownAt = SystemClock.uptimeMillis(); false }
                else if (ev.type == KeyEventType.KeyUp) {
                    val start = leftDownAt; leftDownAt = null
                    if (start != null && SystemClock.uptimeMillis() - start >= 300L) {
                        com.chris.m3usuite.core.debug.GlobalDebug.logDpad("LEFT_LONG", mapOf("tile" to (item.streamId ?: item.id), "type" to item.type))
                        toggle(); true
                    } else false
                } else false
            }
            .onKeyEvent { ev ->
                val n = ev.nativeKeyEvent
                if (n.action == android.view.KeyEvent.ACTION_UP) {
                    val code = n.keyCode
                    if (code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                        com.chris.m3usuite.core.debug.GlobalDebug.logDpad("CENTER", mapOf("tile" to (item.streamId ?: item.id), "type" to item.type))
                    }
                }
                false
            }
            .focusScaleOnTv(focusedScale = 1.12f, pressedScale = 1.12f)
            .tvClickable(
                scaleFocused = 1f,
                scalePressed = 1.02f,
                elevationFocusedDp = 18f,
                brightenContent = false,
                autoBringIntoView = false
            ) { onOpenDetails(item) }
            .onFocusEvent { focused = it.isFocused || it.hasFocus }
            .border(1.dp, borderBrush, shape)
            .drawWithContent {
                drawContent()
                // subtle top reflection
                val grad = Brush.verticalGradient(0f to Color.White.copy(alpha = if (focused) 0.18f else 0.10f), 1f to Color.Transparent)
                drawRect(brush = grad)
            }
            .tvFocusGlow(focused = focused, shape = shape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape
    ) {
        // Debug: log focused tile + OBX title in parentheses
        LaunchedEffect(focused, item.streamId) {
            if (focused) {
                val sid = item.streamId
                if (sid != null) {
                    val ctxLocal = ctx
                    val obxTitle = withContext(Dispatchers.IO) {
                        runCatching {
                            val store = com.chris.m3usuite.data.obx.ObxStore.get(ctxLocal)
                            store.boxFor(com.chris.m3usuite.data.obx.ObxSeries::class.java)
                                .query(com.chris.m3usuite.data.obx.ObxSeries_.seriesId.equal(sid))
                                .build().findFirst()?.name
                        }.getOrNull()
                    }
                    com.chris.m3usuite.core.debug.GlobalDebug.logTileFocus("series", sid.toString(), item.name, obxTitle)
                }
            }
        }
        Column(Modifier.fillMaxWidth()) {
            run {
                var loaded by remember(item.id) { mutableStateOf(false) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(tileHeight)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                ) {
                    if (!loaded) {
                        ShimmerBox(modifier = Modifier.fillMaxSize(), cornerRadius = 0.dp)
                    }
                    AppAsyncImage(
                        url = item.poster ?: item.logo ?: item.backdrop,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        crossfade = false,
                        onLoading = { loaded = false },
                        onSuccess = { loaded = true },
                        onError = { loaded = true }
                    )
                    PlayOverlay(visible = focused)
                    FocusTitleOverlay(
                        title = item.name,
                        focused = focused
                    )
                    FocusTitleOverlay(
                        title = item.name,
                        focused = focused
                    )
                    // Minimal resume tooltip on focus (series)
                    var resumeSecs by remember(item.streamId) { mutableStateOf<Int?>(null) }
                    var totalSecs by remember(item.streamId) { mutableStateOf<Int?>(null) }
                    LaunchedEffect(item.streamId) {
                        val sid = item.streamId
                        if (sid != null) {
                            try {
                                val last = withContext(Dispatchers.IO) { com.chris.m3usuite.data.repo.ResumeRepository(ctx).recentEpisodes(50).firstOrNull { it.seriesId == sid } }
                                if (last != null) {
                                    resumeSecs = last.positionSecs
                                    val ep = withContext(Dispatchers.IO) {
                                        val b = com.chris.m3usuite.data.obx.ObxStore.get(ctx).boxFor(com.chris.m3usuite.data.obx.ObxEpisode::class.java)
                                        b.query(
                                            com.chris.m3usuite.data.obx.ObxEpisode_.seriesId.equal(sid.toLong())
                                                .and(com.chris.m3usuite.data.obx.ObxEpisode_.season.equal(last.season.toLong()))
                                        ).build().find().firstOrNull { it.episodeNum == last.episodeNum }
                                    }
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
                    // Overlay: action buttons bottom-right (avoid extra layout space)
                    Row(
                        Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AppIconButton(icon = AppIcon.PlayCircle, contentDescription = "Abspielen", onClick = { onPlayDirect(item) }, size = 24.dp)
                        if (showAssign) {
                            AppIconButton(icon = AppIcon.BookmarkAdd, contentDescription = "Für Kinder freigeben", onClick = { onAssignToKid(item) }, size = 24.dp)
                        }
                    }
                    // Overlay: NEW badge (top-left)
                    if (isNew) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.28f),
                            contentColor = Color.Red,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        ) {
                            Text(text = "NEU", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
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
                val plot = item.plot
                if (!plot.isNullOrBlank()) {
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
            // actions and badges are overlaid inside the image box now
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
    val store = remember { com.chris.m3usuite.prefs.SettingsStore(ctx) }
    val obxRepo = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }
    val headers = rememberImageHeaders()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val borderBrush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
    val tileHeight = rowItemHeight().dp
    val tileWidth = tileHeight * POSTER_ASPECT_RATIO
    Card(
        modifier = Modifier
            .height(tileHeight)
            .width(tileWidth)
            .padding(end = 6.dp)
            .focusable()
            .focusScaleOnTv(focusedScale = 1.12f, pressedScale = 1.12f)
            .tvClickable(
                scaleFocused = 1f,
                scalePressed = 1.02f,
                elevationFocusedDp = 18f,
                brightenContent = false,
                autoBringIntoView = false
            ) { onOpenDetails(item) }
            .onFocusEvent { focused = it.isFocused || it.hasFocus }
            .border(1.dp, borderBrush, shape)
            .drawWithContent {
                drawContent()
                val grad = Brush.verticalGradient(0f to Color.White.copy(alpha = if (focused) 0.18f else 0.10f), 1f to Color.Transparent)
                drawRect(brush = grad)
            }
            .tvFocusGlow(focused = focused, shape = shape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape
    ) {
        Column(Modifier.fillMaxWidth()) {
            run {
                var loaded by remember(item.id) { mutableStateOf(false) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(tileHeight)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                ) {
                    if (!loaded) {
                        ShimmerBox(modifier = Modifier.fillMaxSize(), cornerRadius = 0.dp)
                    }
                    AppAsyncImage(
                        url = item.poster ?: item.logo ?: item.backdrop,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        crossfade = false,
                        onLoading = { loaded = false },
                        onSuccess = { loaded = true },
                        onError = { loaded = true }
                    )
                    // Resume progress overlay (thin line near bottom)
                    var resumeSecs by remember(item.id) { mutableStateOf<Int?>(null) }
                    LaunchedEffect(item.id) {
                        try { resumeSecs = withContext(Dispatchers.IO) { com.chris.m3usuite.data.repo.ResumeRepository(ctx).getVodResume(item.id) } } catch (_: Throwable) {}
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
                    // Overlay: action buttons bottom-right
                    Row(
                        Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AppIconButton(icon = AppIcon.PlayCircle, contentDescription = "Abspielen", onClick = { onPlayDirect(item) }, size = 24.dp)
                        if (showAssign) {
                            AppIconButton(icon = AppIcon.BookmarkAdd, contentDescription = "Für Kinder freigeben", onClick = { onAssignToKid(item) }, size = 24.dp)
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
                val plot = item.plot
                if (!plot.isNullOrBlank()) {
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
            // actions and badges are overlaid inside the image box now
        }
    }

    // On-demand VOD detail import to populate plot when a tile gains focus
    // Avoid spamming by gating on focus + missing plot + valid streamId.
    val requested = remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused && !requested.value && (item.plot.isNullOrBlank()) && item.streamId != null) {
            requested.value = true
            runCatching { obxRepo.importVodDetailOnce(item.streamId!!) }
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
    val state = com.chris.m3usuite.ui.state.rememberRouteListState("home:resume")
    com.chris.m3usuite.ui.tv.TvFocusRow(
        items = slice,
        key = { it.id },
        listState = state,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) { _, m, itemMod ->
        MediaCard(item = m, onClick = onClick, modifier = itemMod)
    }
}

/** Live TV row, no textual header, horizontally scrollable */
@Composable
fun LiveRow(
    items: List<MediaItem>,
    stateKey: String? = null,
    leading: (@Composable (() -> Unit))? = null,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return
    val ctx = LocalContext.current
    val store = remember { com.chris.m3usuite.prefs.SettingsStore(ctx) }
    val obx = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }

    MediaRowCore(
        items = items,
        config = RowConfig(stateKey = stateKey),
        leading = leading,
        onPrefetchKeys = { keys ->
            val sids = keys.mapNotNull { id ->
                items.firstOrNull { it.id == id && it.type == "live" }?.streamId
            }
            if (sids.isNotEmpty()) {
                obx.prefetchEpgForVisible(sids, perStreamLimit = 2, parallelism = 4)
            }
        },
        itemKey = { it.id }
    ) { m ->
        LiveTileCard(
            item = m,
            onOpenDetails = onOpenDetails,
            onPlayDirect = onPlayDirect
        )
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
            .tvClickable(
                scaleFocused = 1.06f,
                scalePressed = 1.08f,
                elevationFocusedDp = 18f,
                brightenContent = false,
                autoBringIntoView = false
            ) { onClick() }
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
    onRemove: (List<Long>) -> Unit,
    stateKey: String? = null
) {
    val state = stateKey?.let { com.chris.m3usuite.ui.state.rememberRouteListState(it) } ?: rememberLazyListState()
    // Ensure stable, unique keys to avoid LazyRow key collisions
    val order = remember(items) { androidx.compose.runtime.mutableStateListOf<Long>().apply { addAll(items.map { it.id }.distinct()) } }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var targetKey by remember { mutableStateOf<Long?>(null) }
    var insertAfter by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val tileLift by animateFloatAsState(if (draggingId != null) 1.05f else 1f, label = "lift")

    val isTv = com.chris.m3usuite.ui.skin.isTvDevice(LocalContext.current)
    // Center-on-focus helpers for TV (leading add tile occupies index 0)
    val pendingScrollIndex = remember { mutableStateOf(-1) }
    val firstFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val skipFirstCenter = remember { mutableStateOf(true) }
    val enterEnabled = remember { mutableStateOf(false) }
    val hasContent = remember(order) { order.isNotEmpty() }

    LaunchedEffect(pendingScrollIndex.value) {
        val target = pendingScrollIndex.value
        if (target >= 0) {
            state.animateScrollToItem(target)
            val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            if (info != null) {
                val viewportStart = state.layoutInfo.viewportStartOffset
                val viewportEnd = state.layoutInfo.viewportEndOffset
                val viewportSize = viewportEnd - viewportStart
                val desiredOffset = ((viewportSize - info.size) / 2f).toInt().coerceAtLeast(0)
                state.animateScrollToItem(target, desiredOffset)
            }
            pendingScrollIndex.value = -1
        }
    }
    // Activate enter once the leading (index 0) or first content (index 1) becomes visible
    LaunchedEffect(state, isTv, hasContent) {
        if (!isTv) return@LaunchedEffect
        kotlinx.coroutines.flow.flow {
            emit(Unit)
        }
        androidx.compose.runtime.snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { indices ->
                val targetIndex = if (hasContent) 1 else 0
                if (!enterEnabled.value && indices.contains(targetIndex)) {
                    enterEnabled.value = true
                    // Defer one frame to ensure focusRequester is attached
                    kotlinx.coroutines.delay(16)
                    runCatching { firstFocus.requestFocus() }
                }
            }
    }

    // Enable focus enter only once a target (leading or first content) is visible
    val rowModifier = if (isTv) {
        var m: Modifier = Modifier.focusGroup().focusProperties { }
        if (enterEnabled.value) m = m.focusProperties { enter = { firstFocus } }
        m
    } else Modifier

    LazyRow(
        modifier = rowModifier,
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp)
    ) {
        item("leading") {
            // If there are no content items, attach firstFocus to the leading tile to satisfy enter
            val leadingMod = if (isTv && !hasContent) Modifier.focusRequester(firstFocus) else Modifier
            Box(leadingMod) { LiveAddTile(onClick = onAdd) }
        }
        itemsIndexed(order, key = { idx, it -> it }) { idx, id ->
            val mi = items.find { it.id == id } ?: return@itemsIndexed
            val isDragging = draggingId == id
            val dragTranslationX = if (isDragging) dragOffset else 0f
            val trans by animateFloatAsState(dragTranslationX, label = "drag")
            val padStart = if (targetKey == id && !insertAfter) 10.dp else 0.dp
            val padEnd = if (targetKey == id && insertAfter) 10.dp else 0.dp
            Box(
                Modifier
                    .padding(start = padStart, end = padEnd)
                    .graphicsLayer { translationX = trans; scaleX = if (isDragging) tileLift else 1f; scaleY = if (isDragging) tileLift else 1f; shadowElevation = if (isDragging) 18f else 0f }
                    .then(
                        if (isTv)
                            Modifier.onFocusEvent { st ->
                                if (st.hasFocus) {
                                    val absIdx = idx + 1 // account for leading tile
                                    if (skipFirstCenter.value && absIdx == 1) {
                                        skipFirstCenter.value = false
                                    } else {
                                        pendingScrollIndex.value = absIdx
                                    }
                                }
                            }.then(if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        else Modifier
                    )
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
    stateKey: String? = null,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: (MediaItem) -> Unit,
    newIds: Set<Long> = emptySet(),
    showNew: Boolean = false,
    showAssign: Boolean = true
) {
    if (items.isEmpty()) return
    val ctx = LocalContext.current
    val store = remember { com.chris.m3usuite.prefs.SettingsStore(ctx) }
    val obx = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }

    MediaRowCore(
        items = items,
        config = RowConfig(stateKey = stateKey),
        onPrefetchKeys = { keys ->
            val sids = keys.mapNotNull { id ->
                if (id in 3_000_000_000_000L until 4_000_000_000_000L) (id - 3_000_000_000_000L).toInt() else null
            }
            if (sids.isNotEmpty()) obx.importSeriesDetailsForIds(sids, max = 8)
        },
        itemKey = { it.id }
    ) { m ->
        SeriesTileCard(
            item = m,
            onOpenDetails = onOpenDetails,
            onPlayDirect = onPlayDirect,
            onAssignToKid = onAssignToKid,
            isNew = showNew || newIds.contains(m.id),
            showAssign = showAssign
        )
    }
}

/** VOD row, horizontally scrollable */
@Composable
fun VodRow(
    items: List<MediaItem>,
    stateKey: String? = null,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: (MediaItem) -> Unit,
    newIds: Set<Long> = emptySet(),
    showNew: Boolean = false,
    showAssign: Boolean = true
) {
    if (items.isEmpty()) return
    val ctx = LocalContext.current
    val store = remember { com.chris.m3usuite.prefs.SettingsStore(ctx) }
    val obx = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }

    MediaRowCore(
        items = items,
        config = RowConfig(stateKey = stateKey),
        onPrefetchKeys = { keys ->
            val vodIds = keys.mapNotNull { id ->
                if (id in 2_000_000_000_000L until 3_000_000_000_000L) (id - 2_000_000_000_000L).toInt() else null
            }.distinct()
            if (vodIds.isNotEmpty()) obx.importVodDetailsForIds(vodIds, max = 12)
        },
        itemKey = { it.id }
    ) { m ->
        VodTileCard(
            item = m,
            onOpenDetails = onOpenDetails,
            onPlayDirect = onPlayDirect,
            onAssignToKid = onAssignToKid,
            isNew = showNew || newIds.contains(m.id),
            showAssign = showAssign
        )
    }
}

/** VOD row backed by Paging3; keeps horizontal row optics with skeleton placeholders. */
@Composable
fun VodRowPaged(
    items: LazyPagingItems<MediaItem>,
    stateKey: String? = null,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: (MediaItem) -> Unit,
    showAssign: Boolean = true
) {
    val ctx = LocalContext.current
    val store = remember { com.chris.m3usuite.prefs.SettingsStore(ctx) }
    val obx = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }

    MediaRowCorePaged(
        items = items,
        config = RowConfig(stateKey = stateKey),
        onPrefetchPaged = { indices, lp ->
            val count = lp.itemCount
            if (count <= 0) return@MediaRowCorePaged
            val vodIds = indices
                .filter { it in 0 until count }
                .mapNotNull { idx ->
                    val media = lp.peek(idx)
                    val id = media?.id ?: return@mapNotNull null
                    if (id in 2_000_000_000_000L until 3_000_000_000_000L) (id - 2_000_000_000_000L).toInt() else null
                }.distinct()
            if (vodIds.isNotEmpty()) obx.importVodDetailsForIds(vodIds, max = 12)
        }
    ) { _, mi ->
        VodTileCard(
            item = mi,
            onOpenDetails = onOpenDetails,
            onPlayDirect = onPlayDirect,
            onAssignToKid = onAssignToKid,
            isNew = false,
            showAssign = showAssign
        )
    }
}

/** Live row backed by Paging3; preserves horizontal row look and prefetches EPG for visible items. */
@Composable
fun LiveRowPaged(
    items: LazyPagingItems<MediaItem>,
    stateKey: String? = null,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
) {
    val ctx = LocalContext.current
    val store = remember { com.chris.m3usuite.prefs.SettingsStore(ctx) }
    val obx = remember { com.chris.m3usuite.data.repo.XtreamObxRepository(ctx, store) }

    MediaRowCorePaged(
        items = items,
        config = RowConfig(stateKey = stateKey),
        onPrefetchPaged = { indices, lp ->
            val count = lp.itemCount
            if (count <= 0) return@MediaRowCorePaged
            val sids = indices
                .filter { it in 0 until count }
                .mapNotNull { idx ->
                    val media = lp.peek(idx)
                    media?.takeIf { it.type == "live" }?.streamId
                }
            if (sids.isNotEmpty()) obx.prefetchEpgForVisible(sids, perStreamLimit = 2, parallelism = 4)
        }
    ) { _, mi ->
        LiveTileCard(
            item = mi,
            onOpenDetails = onOpenDetails,
            onPlayDirect = onPlayDirect
        )
    }
}

// (duplicate SeriesRow removed – logic added to primary SeriesRow above)

/** Series row backed by Paging3; preserves horizontal row look with skeletons. */
@Composable
fun SeriesRowPaged(
    items: LazyPagingItems<MediaItem>,
    stateKey: String? = null,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: (MediaItem) -> Unit,
    showAssign: Boolean = true
) {
    // Prefetch optional: Serien-Details bei Paged kann (wenn gewünscht) analog implementiert werden

    MediaRowCorePaged(
        items = items,
        config = RowConfig(stateKey = stateKey),
        onPrefetchPaged = null // optional später ergänzen
    ) { _, mi ->
        SeriesTileCard(
            item = mi,
            onOpenDetails = onOpenDetails,
            onPlayDirect = onPlayDirect,
            onAssignToKid = onAssignToKid,
            isNew = false,
            showAssign = showAssign
        )
    }
}
