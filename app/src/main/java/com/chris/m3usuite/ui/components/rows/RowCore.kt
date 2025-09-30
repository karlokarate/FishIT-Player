@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.chris.m3usuite.ui.components.rows

import androidx.compose.foundation.layout.PaddingValues
import com.chris.m3usuite.ui.compat.focusGroup
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.LoadState
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.model.hasArtwork
import com.chris.m3usuite.ui.fx.ShimmerBox
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import com.chris.m3usuite.ui.tv.centerItemSafely
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.launch

/**
 * Zentrale Konfiguration der Row-Engine.
 */
data class RowConfig(
    val stateKey: String? = null,
    val debugKey: String? = null,
    val contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
    val initialWindow: Int = 30,
    val lazyLoadWindow: Int = 20,
    val lazyLoadStep: Int = 50,
    val debounceVisibleMs: Long = 100L,
    // Controls whether this row may request the very first focus on TV
    val initialFocusEligible: Boolean = true,
    // When true and the focused index is at the left-most item, DPAD LEFT expands Home chrome (Start only)
    val edgeLeftExpandChrome: Boolean = false
)

/**
 * Generische Prefetch-Hooks (Listen-Variante nutzt sichtbare Keys; Paged-Variante nutzt sichtbare Indizes).
 */
typealias OnPrefetchKeys = suspend (visibleKeys: List<Long>) -> Unit
typealias OnPrefetchPaged = suspend (visibleIndices: List<Int>, items: LazyPagingItems<MediaItem>) -> Unit

/**
 * Gemeinsame List-Engine:
 * - dedupliziert Items per id
 * - einheitliches Snapping/State
 * - lazy-load bei Scroll
 * - Sichtbarkeitsstrom → distinct + debounce → onPrefetchKeys()
 */
@Composable
fun MediaRowCore(
    items: List<MediaItem>,
    config: RowConfig = RowConfig(),
    leading: (@Composable (() -> Unit))? = null,
    onPrefetchKeys: OnPrefetchKeys? = null,
    itemKey: (MediaItem) -> Long = { it.id },
    itemContent: @Composable (MediaItem) -> Unit
) {
    if (items.isEmpty()) return
    val ctx = LocalContext.current
    val distinct = remember(items) { items.distinctBy { itemKey(it) } }
    // Temporarily disable artwork-first reordering: preserve incoming item order
    val ordered = remember(distinct) { distinct }
    val state: LazyListState =
        config.stateKey?.let { com.chris.m3usuite.ui.state.rememberRouteListState(it) } ?: rememberLazyListState()
    val isTv = com.chris.m3usuite.ui.skin.isTvDevice(LocalContext.current)
    val fling = if (isTv) ScrollableDefaults.flingBehavior() else rememberSnapFlingBehavior(state)
    val pendingScrollIndex = remember { mutableStateOf(-1) }
    val latestFocusIdx = remember { mutableStateOf<Int?>(null) }
    val firstFocus = remember { FocusRequester() }
    // Ensure the first item's FocusRequester is actually attached before arming enter
    val firstAttached = remember { mutableStateOf(false) }
    val leadingOffset = if (leading != null) 1 else 0
    val initRequested = remember { mutableStateOf(false) }
    val enterEnabled = remember { mutableStateOf(false) }
    val skipFirstCenter = remember { mutableStateOf(true) }
    val pendingRowFocusIdx = remember { mutableStateOf<Int?>(null) }
    val currentFocusIdx = remember { mutableStateOf<Int?>(null) }
    val focusManager = LocalFocusManager.current
    val datasetTick = remember { mutableStateOf(0) }

    suspend fun centerOnIndex(absIndex: Int) { state.centerItemSafely(absIndex) }

    // Ensure the very first tile takes focus initially on TV once it's visible
    LaunchedEffect(state, isTv, firstAttached.value, config.initialFocusEligible) {
        if (!isTv || !config.initialFocusEligible) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { indices ->
                if (!initRequested.value && firstAttached.value && indices.contains(leadingOffset)) {
                    initRequested.value = true
                    // request focus first
                    kotlinx.coroutines.delay(16)
                    runCatching {
                        com.chris.m3usuite.core.debug.GlobalDebug.logTree("focusReq:RowCore:first")
                        firstFocus.requestFocus()
                    }
                    // Nudge focus into the first tile so halo/scale are visible
                    runCatching { focusManager.moveFocus(FocusDirection.Enter) }
                    // then arm enter
                    enterEnabled.value = true
                    com.chris.m3usuite.core.debug.GlobalDebug.logTree("enter:Set:RowCore")
                    // Immediately announce expected focused tile for GlobalDebug (even before further interaction)
                    runCatching {
                        val first = ordered.firstOrNull()
                        val sid = first?.streamId
                        val type = first?.type
                        if (first != null && sid != null && type != null) {
                            val obxTitle = withContext(Dispatchers.IO) {
                                runCatching {
                                    val store = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
                                    when (type) {
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
                            com.chris.m3usuite.core.debug.GlobalDebug.logTileFocus(type, sid.toString(), first.name, obxTitle)
                            val node = when (type) { "live" -> "row:live"; "vod" -> "row:vod"; "series" -> "row:series"; else -> "row:?" }
                            com.chris.m3usuite.core.debug.GlobalDebug.logTree(node, "tile:$sid")
                        }
                    }
                }
            }
    }

    // When dataset changes (e.g., seeding adds items and replaces head), re-assert focus into the first/last known item
    LaunchedEffect(ordered) { datasetTick.value += 1 }
    LaunchedEffect(isTv, datasetTick.value) {
        if (!isTv || !firstAttached.value || !initRequested.value) return@LaunchedEffect
        // Only reassert if this row already owns focus (avoid grabbing focus across rows)
        val target = currentFocusIdx.value ?: return@LaunchedEffect
        // Wait a frame so updated nodes are attached, then nudge focus back into the tile if visible
        kotlinx.coroutines.delay(16)
        val visible = state.layoutInfo.visibleItemsInfo.any { it.index == target }
        if (visible) {
            runCatching {
                firstFocus.requestFocus()
                focusManager.moveFocus(FocusDirection.Enter)
                com.chris.m3usuite.core.debug.GlobalDebug.logTree("focusReq:RowCore:reassert")
            }
        }
    }

    // Debug: log scroll start/stop to correlate with DPAD and centering; flush last focus request on stop
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collect { moving ->
                runCatching {
                    com.chris.m3usuite.core.debug.GlobalDebug.logTree(if (moving) "row:scroll:start" else "row:scroll:stop")
                }
                if (!moving) {
                    latestFocusIdx.value?.let { idx ->
                        latestFocusIdx.value = null
                        pendingScrollIndex.value = idx
                    }
                    if (pendingRowFocusIdx.value != null && config.stateKey != null) {
                        val target = pendingRowFocusIdx.value
                        if (target != null) {
                            com.chris.m3usuite.ui.state.writeRowFocus(config.stateKey, target)
                            currentFocusIdx.value = target
                            com.chris.m3usuite.core.debug.GlobalDebug.logRowNav(
                                "FOCUS",
                                config.debugKey ?: config.stateKey,
                                target,
                                target,
                                "persist:idle"
                            )
                        }
                        pendingRowFocusIdx.value = null
                    }
                }
            }
    }

    // Rely on LazyRow virtualization; do not gate item count manually to keep DPAD traversal predictable.

    // Sichtbare Keys → Prefetch, zentral gedrosselt
    LaunchedEffect(state, ordered, onPrefetchKeys, config.debounceVisibleMs) {
        if (onPrefetchKeys == null) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long } }
            .distinctUntilChanged()
            .debounce(config.debounceVisibleMs)
            .collect { keys -> if (keys.isNotEmpty()) onPrefetchKeys(keys) }
    }

    val scope = rememberCoroutineScope()
    val chromeExpand = com.chris.m3usuite.ui.home.LocalChromeExpand.current
    // Execute pending minimal scroll to make focused item visible
    LaunchedEffect(pendingScrollIndex.value) {
        val target = pendingScrollIndex.value
        if (target >= 0) {
            centerOnIndex(target)
            pendingScrollIndex.value = -1
        }
    }
    val listModifier = if (isTv) Modifier.focusGroup() else Modifier
    LazyRow(
        modifier = listModifier.then(
            if (isTv) Modifier.onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> {
                        val storedIdx = config.stateKey?.let { com.chris.m3usuite.ui.state.readRowFocus(it).index }
                        val pendingIdx = pendingRowFocusIdx.value
                        val focusIdx = currentFocusIdx.value
                        val fallbackIdx = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: leadingOffset
                        val currentIdx = focusIdx ?: pendingIdx ?: storedIdx ?: fallbackIdx
                        // Log current window before attempting move
                        runCatching {
                            val li = state.layoutInfo
                            val vis = li.visibleItemsInfo
                            val first = vis.firstOrNull()?.index ?: -1
                            val last = vis.lastOrNull()?.index ?: -1
                            val items = vis.joinToString(",") { "${it.index}@${it.offset}/${it.size}" }
                            com.chris.m3usuite.core.debug.GlobalDebug.logRowWindow(config.debugKey ?: config.stateKey, first, last, li.viewportStartOffset, li.viewportEndOffset, items)
                        }
                        // Try moving focus first; only if not moved and truly at edge-left, expand chrome
                        val moved = focusManager.moveFocus(FocusDirection.Left)
                        if (!moved && config.edgeLeftExpandChrome && currentIdx <= leadingOffset) {
                            com.chris.m3usuite.core.debug.GlobalDebug.logRowNav("LEFT", config.stateKey, currentIdx, null, "expandChrome")
                            chromeExpand?.invoke()
                            return@onPreviewKeyEvent true
                        }
                        com.chris.m3usuite.core.debug.GlobalDebug.logRowNav("LEFT", config.debugKey ?: config.stateKey, currentIdx, null, if (moved) "systemMove" else "bubble")
                        moved
                    }
                    Key.DirectionRight -> {
                        val storedIdx = config.stateKey?.let { com.chris.m3usuite.ui.state.readRowFocus(it).index }
                        val pendingIdx = pendingRowFocusIdx.value
                        val focusIdx = currentFocusIdx.value
                        val fallbackIdx = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: leadingOffset
                        val currentIdx = focusIdx ?: pendingIdx ?: storedIdx ?: fallbackIdx
                        // Log current window before attempting move
                        runCatching {
                            val li = state.layoutInfo
                            val vis = li.visibleItemsInfo
                            val first = vis.firstOrNull()?.index ?: -1
                            val last = vis.lastOrNull()?.index ?: -1
                            val items = vis.joinToString(",") { "${it.index}@${it.offset}/${it.size}" }
                            com.chris.m3usuite.core.debug.GlobalDebug.logRowWindow(config.debugKey ?: config.stateKey, first, last, li.viewportStartOffset, li.viewportEndOffset, items)
                        }
                        // Single controlled focus move; if not moved, let event bubble
                        val moved = focusManager.moveFocus(FocusDirection.Right)
                        com.chris.m3usuite.core.debug.GlobalDebug.logRowNav("RIGHT", config.debugKey ?: config.stateKey, currentIdx, null, if (moved) "systemMove" else "bubble")
                        moved
                    }
                    
                    else -> false
                }
            } else Modifier
        ),
        state = state,
        flingBehavior = fling,
        userScrollEnabled = !isTv,
        contentPadding = config.contentPadding
    ) {
        if (leading != null) item("leading") { leading() }
        itemsIndexed(ordered, key = { _, it -> itemKey(it) }) { idx, m ->
            // Observe descendant focus and center that item. Index in LazyRow accounts for optional leading.
            val absIdx = idx + leadingOffset
            var focused by remember { mutableStateOf(false) }
            val setRow = com.chris.m3usuite.ui.home.LocalChromeRowFocusSetter.current
            val baseMod = if (isTv) Modifier.onFocusEvent { st ->
                focused = st.isFocused || st.hasFocus
                if (st.hasFocus) {
                    config.stateKey?.let { key -> setRow(key) }
                    if (!state.isScrollInProgress) {
                        if (config.stateKey != null) {
                            com.chris.m3usuite.ui.state.writeRowFocus(config.stateKey, absIdx)
                        }
                        pendingRowFocusIdx.value = null
                        currentFocusIdx.value = absIdx
                    } else {
                        pendingRowFocusIdx.value = absIdx
                        currentFocusIdx.value = absIdx
                        com.chris.m3usuite.core.debug.GlobalDebug.logRowNav("FOCUS", config.debugKey ?: config.stateKey, absIdx, absIdx, "skipWrite:scrolling")
                    }
                    if (skipFirstCenter.value && absIdx == leadingOffset) {
                        // First focus on left-most tile: don't center yet
                        skipFirstCenter.value = false
                    } else {
                        val info = state.layoutInfo
                        val viewport = info.viewportEndOffset - info.viewportStartOffset
                        val vis = info.visibleItemsInfo.firstOrNull { it.index == absIdx }
                        val fullyVisible = vis != null && vis.offset >= 0 && (vis.offset + vis.size) <= viewport
                        if (!fullyVisible) {
                            if (state.isScrollInProgress) {
                                latestFocusIdx.value = absIdx
                            } else {
                                pendingScrollIndex.value = absIdx
                            }
                            com.chris.m3usuite.core.debug.GlobalDebug.logRowNav("FOCUS", config.debugKey ?: config.stateKey, absIdx, absIdx, "scroll:queued")
                        } else {
                            com.chris.m3usuite.core.debug.GlobalDebug.logRowNav("FOCUS", config.debugKey ?: config.stateKey, absIdx, absIdx, "noScroll:fullyVisible")
                        }
                    }
                    runCatching { com.chris.m3usuite.core.debug.GlobalDebug.logTree("row:focusIdx", "idx:$absIdx") }
                }
            } else Modifier
            val itemMod = if (isTv && idx == 0) baseMod
                .focusable()
                .focusRequester(firstFocus)
                .onFocusEvent { st ->
                    if (st.isFocused && enterEnabled.value) {
                        runCatching { focusManager.moveFocus(FocusDirection.Enter) }
                    }
                }
            else baseMod
            val visualMod = if (isTv) com.chris.m3usuite.ui.skin.run { Modifier.tvFocusFrame() } else Modifier
            if (isTv && idx == 0) {
                androidx.compose.runtime.SideEffect { firstAttached.value = true }
            }
            // When a tile gains focus, emit a concise GlobalDebug focus log with OBX title (if available)
            if (isTv) {
                LaunchedEffect(focused, m.streamId, m.type, absIdx) {
                    if (focused) {
                        val sid = m.streamId
                        if (sid != null) {
                            val obxTitle = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    val store = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
                                    when (m.type) {
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
                            com.chris.m3usuite.core.debug.GlobalDebug.logTileFocus(m.type, sid.toString(), m.name, obxTitle)
                            val node = when (m.type) { "live" -> "row:live"; "vod" -> "row:vod"; "series" -> "row:series"; else -> "row:?" }
                            com.chris.m3usuite.core.debug.GlobalDebug.logTree(node, "tile:$sid")
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Box(itemMod.then(visualMod)) { itemContent(m) }
        }
    }
}

/**
 * Paged-Engine:
 * - einheitliches Snapping/State
 * - sichtbare Indizes → onPrefetchPaged()
 * - Skeletons für refresh/append
 */
@Composable
fun MediaRowCorePaged(
    items: LazyPagingItems<MediaItem>,
    config: RowConfig = RowConfig(),
    leading: (@Composable (() -> Unit))? = null,
    onPrefetchPaged: OnPrefetchPaged? = null,
    shimmerRefreshCount: Int = 10,
    shimmerAppendCount: Int = 6,
    itemKey: (index: Int) -> Long = { idx -> items[idx]?.id ?: idx.toLong() },
    itemContent: @Composable (index: Int, MediaItem) -> Unit
) {
    val ctx = LocalContext.current
    val state: LazyListState =
        config.stateKey?.let { com.chris.m3usuite.ui.state.rememberRouteListState(it) } ?: rememberLazyListState()
    val isTv = com.chris.m3usuite.ui.skin.isTvDevice(LocalContext.current)
    val fling = if (isTv) ScrollableDefaults.flingBehavior() else rememberSnapFlingBehavior(state)
    val pendingScrollIndex = remember { mutableStateOf(-1) }
    val latestFocusIdx = remember { mutableStateOf<Int?>(null) }
    val firstFocus = remember { FocusRequester() }
    // Ensure the first item's FocusRequester is attached before enabling custom enter
    val firstAttached = remember { mutableStateOf(false) }
    val leadingOffset = if (leading != null) 1 else 0
    val initRequested = remember { mutableStateOf(false) }
    val skipFirstCenter = remember { mutableStateOf(true) }
    val enterEnabled = remember { mutableStateOf(false) }
    val pendingRowFocusIdx = remember { mutableStateOf<Int?>(null) }
    val currentFocusIdx = remember { mutableStateOf<Int?>(null) }
    val focusManagerPaged = LocalFocusManager.current

    suspend fun centerOnIndex(absIndex: Int) { state.centerItemSafely(absIndex) }

    LaunchedEffect(pendingScrollIndex.value) {
        val target = pendingScrollIndex.value
        if (target >= 0) {
            centerOnIndex(target)
            pendingScrollIndex.value = -1
        }
    }

    // Ensure the very first tile takes focus initially on TV once it's visible
    LaunchedEffect(state, isTv, firstAttached.value, config.initialFocusEligible) {
        if (!isTv || !config.initialFocusEligible) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { indices ->
                if (!initRequested.value && firstAttached.value && indices.contains(leadingOffset)) {
                    initRequested.value = true
                    // request focus first
                    kotlinx.coroutines.delay(16)
                    runCatching {
                        com.chris.m3usuite.core.debug.GlobalDebug.logTree("focusReq:RowCorePaged:first")
                        firstFocus.requestFocus()
                    }
                    // Nudge focus into the first tile so halo/scale are visible
                    runCatching { focusManagerPaged.moveFocus(FocusDirection.Enter) }
                    // then arm enter
                    enterEnabled.value = true
                    com.chris.m3usuite.core.debug.GlobalDebug.logTree("enter:Set:RowCorePaged")
                    // Immediately announce expected focused tile for GlobalDebug (if available)
                    runCatching {
                        val first = items.peek(0)
                        val sid = first?.streamId
                        val type = first?.type
                        if (first != null && sid != null && type != null) {
                            val obxTitle = withContext(Dispatchers.IO) {
                                runCatching {
                                    val store = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
                                    when (type) {
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
                            com.chris.m3usuite.core.debug.GlobalDebug.logTileFocus(type, sid.toString(), first.name, obxTitle)
                            val node = when (type) { "live" -> "row:live"; "vod" -> "row:vod"; "series" -> "row:series"; else -> "row:?" }
                            com.chris.m3usuite.core.debug.GlobalDebug.logTree(node, "tile:$sid")
                        }
                    }
                }
            }
    }

    // Debug: log scroll start/stop (paged rows)
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collect { moving ->
                runCatching {
                    com.chris.m3usuite.core.debug.GlobalDebug.logTree(if (moving) "row:scroll:start" else "row:scroll:stop")
                }
                if (!moving) {
                    latestFocusIdx.value?.let { idx ->
                        latestFocusIdx.value = null
                        pendingScrollIndex.value = idx
                    }
                    if (pendingRowFocusIdx.value != null && config.stateKey != null) {
                        val target = pendingRowFocusIdx.value
                        if (target != null) {
                            com.chris.m3usuite.ui.state.writeRowFocus(config.stateKey, target)
                            currentFocusIdx.value = target
                            com.chris.m3usuite.core.debug.GlobalDebug.logRowNav(
                                "FOCUS",
                                config.stateKey,
                                target,
                                target,
                                "persist:idle"
                            )
                        }
                        pendingRowFocusIdx.value = null
                    }
                }
            }
    }

    // Sichtbare Indizes → Prefetch, zentral gedrosselt
    LaunchedEffect(state, items, onPrefetchPaged, config.debounceVisibleMs) {
        if (onPrefetchPaged == null) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .debounce(config.debounceVisibleMs)
            .collect { indices ->
                if (indices.isNotEmpty()) onPrefetchPaged(indices, items)
            }
    }

    val pagedModifier = if (isTv) Modifier.focusGroup() else Modifier
    val scopePaged = rememberCoroutineScope()
    val chromeExpandPaged = com.chris.m3usuite.ui.home.LocalChromeExpand.current
    LazyRow(
        modifier = pagedModifier.then(
            if (isTv) Modifier.onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> {
                        val storedIdx = config.stateKey?.let { com.chris.m3usuite.ui.state.readRowFocus(it).index }
                        val pendingIdx = pendingRowFocusIdx.value
                        val focusIdx = currentFocusIdx.value
                        val fallbackIdx = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: leadingOffset
                        val currentIdx = focusIdx ?: pendingIdx ?: storedIdx ?: fallbackIdx
                        // Try moving focus first; only if not moved and truly at edge-left, expand chrome
                        val moved = focusManagerPaged.moveFocus(FocusDirection.Left)
                        if (!moved && config.edgeLeftExpandChrome && currentIdx <= leadingOffset) {
                            com.chris.m3usuite.core.debug.GlobalDebug.logRowNav("LEFT", config.stateKey, currentIdx, null, "expandChrome")
                            chromeExpandPaged?.invoke(); return@onPreviewKeyEvent true
                        }
                        com.chris.m3usuite.core.debug.GlobalDebug.logRowNav("LEFT", config.stateKey, currentIdx, null, if (moved) "systemMove" else "bubble")
                        moved
                    }
                    Key.DirectionRight -> {
                        val storedIdx = config.stateKey?.let { com.chris.m3usuite.ui.state.readRowFocus(it).index }
                        val pendingIdx = pendingRowFocusIdx.value
                        val focusIdx = currentFocusIdx.value
                        val fallbackIdx = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: leadingOffset
                        val currentIdx = focusIdx ?: pendingIdx ?: storedIdx ?: fallbackIdx
                        // Single controlled focus move; if not moved, let event bubble
                        val moved = focusManagerPaged.moveFocus(FocusDirection.Right)
                        com.chris.m3usuite.core.debug.GlobalDebug.logRowNav("RIGHT", config.stateKey, currentIdx, null, if (moved) "systemMove" else "bubble")
                        moved
                    }
                    
                    else -> false
                }
            } else Modifier
        ),
        state = state,
        flingBehavior = fling,
        userScrollEnabled = !isTv,
        contentPadding = config.contentPadding
    ) {
        val isRefreshing = items.loadState.refresh is LoadState.Loading && items.itemCount == 0
        if (isRefreshing) {
            items(shimmerRefreshCount, key = { it }) {
                Column(Modifier.height(rowItemHeight().dp)) {
                    ShimmerBox(modifier = Modifier.aspectRatio(16f / 9f), cornerRadius = 14.dp)
                }
            }
        }

        items(items.itemCount, key = { idx -> itemKey(idx) }) { index ->
            val mi = items[index] ?: return@items
            val absIdx = index + leadingOffset
            // Observe descendant focus only; avoid adding extra focus targets.
            var focused by remember { mutableStateOf(false) }
            val setRow = com.chris.m3usuite.ui.home.LocalChromeRowFocusSetter.current
            val baseMod = if (isTv) Modifier.onFocusEvent { st ->
                focused = st.isFocused || st.hasFocus
                if (st.hasFocus) {
                    config.stateKey?.let { key -> setRow(key) }
                    if (!state.isScrollInProgress) {
                        if (config.stateKey != null) {
                            com.chris.m3usuite.ui.state.writeRowFocus(config.stateKey, absIdx)
                        }
                        currentFocusIdx.value = absIdx
                        pendingRowFocusIdx.value = null
                    } else {
                        pendingRowFocusIdx.value = absIdx
                        currentFocusIdx.value = absIdx
                        com.chris.m3usuite.core.debug.GlobalDebug.logRowNav("FOCUS", config.stateKey, absIdx, absIdx, "skipWrite:scrolling")
                    }
                    if (skipFirstCenter.value && absIdx == leadingOffset) {
                        skipFirstCenter.value = false
                    } else {
                        val info = state.layoutInfo
                        val viewport = info.viewportEndOffset - info.viewportStartOffset
                        val vis = info.visibleItemsInfo.firstOrNull { it.index == absIdx }
                        val fullyVisible = vis != null && vis.offset >= 0 && (vis.offset + vis.size) <= viewport
                        if (!fullyVisible) {
                            if (state.isScrollInProgress) latestFocusIdx.value = absIdx else pendingScrollIndex.value = absIdx
                            com.chris.m3usuite.core.debug.GlobalDebug.logRowNav("FOCUS", config.stateKey, absIdx, absIdx, "scroll:queued")
                        } else {
                            com.chris.m3usuite.core.debug.GlobalDebug.logRowNav("FOCUS", config.stateKey, absIdx, absIdx, "noScroll:fullyVisible")
                        }
                    }
                    runCatching { com.chris.m3usuite.core.debug.GlobalDebug.logTree("row:focusIdx", "idx:$absIdx") }
                }
            } else Modifier
            val itemMod = if (isTv && index == 0) baseMod
                .focusable()
                .focusRequester(firstFocus)
                .onFocusEvent { st ->
                    if (st.isFocused && enterEnabled.value) {
                        runCatching { focusManagerPaged.moveFocus(FocusDirection.Enter) }
                    }
                }
            else baseMod
            val visualMod = if (isTv) com.chris.m3usuite.ui.skin.run { Modifier.tvFocusFrame() } else Modifier
            if (isTv && index == 0) {
                androidx.compose.runtime.SideEffect { firstAttached.value = true }
            }
            // Focus log for paged rows as well
            if (isTv) {
                LaunchedEffect(focused, mi.streamId, mi.type, absIdx) {
                    if (focused) {
                        val sid = mi.streamId
                        if (sid != null) {
                            val obxTitle = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    val store = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
                                    when (mi.type) {
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
                            com.chris.m3usuite.core.debug.GlobalDebug.logTileFocus(mi.type, sid.toString(), mi.name, obxTitle)
                            val node = when (mi.type) { "live" -> "row:live"; "vod" -> "row:vod"; "series" -> "row:series"; else -> "row:?" }
                            com.chris.m3usuite.core.debug.GlobalDebug.logTree(node, "tile:$sid")
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Box(itemMod.then(visualMod)) { itemContent(index, mi) }
        }

        val isAppending = items.loadState.append is LoadState.Loading
        if (isAppending) {
            items(shimmerAppendCount, key = { it + 100_000 }) {
                Column(Modifier.height(rowItemHeight().dp)) {
                    ShimmerBox(modifier = Modifier.aspectRatio(16f / 9f), cornerRadius = 14.dp)
                }
            }
        }
    }
}
