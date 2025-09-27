@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.chris.m3usuite.ui.components.rows

import androidx.compose.foundation.layout.PaddingValues
import com.chris.m3usuite.ui.compat.focusGroup
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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

/**
 * Zentrale Konfiguration der Row-Engine.
 */
data class RowConfig(
    val stateKey: String? = null,
    val contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
    val initialWindow: Int = 30,
    val lazyLoadWindow: Int = 20,
    val lazyLoadStep: Int = 50,
    val debounceVisibleMs: Long = 100L
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
    val ordered = remember(distinct) {
        val (withArt, withoutArt) = distinct.partition { it.hasArtwork() }
        withArt + withoutArt
    }
    val state: LazyListState =
        config.stateKey?.let { com.chris.m3usuite.ui.state.rememberRouteListState(it) } ?: rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state)
    val isTv = com.chris.m3usuite.ui.skin.isTvDevice(LocalContext.current)
    val pendingScrollIndex = remember { mutableStateOf(-1) }
    val latestFocusIdx = remember { mutableStateOf<Int?>(null) }
    val firstFocus = remember { FocusRequester() }
    // Ensure the first item's FocusRequester is actually attached before arming enter
    val firstAttached = remember { mutableStateOf(false) }
    val leadingOffset = if (leading != null) 1 else 0
    val initRequested = remember { mutableStateOf(false) }
    val enterEnabled = remember { mutableStateOf(false) }
    val skipFirstCenter = remember { mutableStateOf(true) }

    suspend fun centerOnIndex(absIndex: Int) { state.centerItemSafely(absIndex) }

    LaunchedEffect(pendingScrollIndex.value) {
        val target = pendingScrollIndex.value
        if (target >= 0) {
            centerOnIndex(target)
            pendingScrollIndex.value = -1
        }
    }

    // Ensure the very first tile takes focus initially on TV once it's visible
    LaunchedEffect(state, isTv, firstAttached.value) {
        if (!isTv) return@LaunchedEffect
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
                    // then arm enter
                    enterEnabled.value = true
                    com.chris.m3usuite.core.debug.GlobalDebug.logTree("enter:Set:RowCore")
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

    val listModifier = if (isTv) Modifier.focusGroup() else Modifier
    LazyRow(
        modifier = listModifier,
        state = state,
        flingBehavior = fling,
        contentPadding = config.contentPadding
    ) {
        if (leading != null) item("leading") { leading() }
        itemsIndexed(ordered, key = { _, it -> itemKey(it) }) { idx, m ->
            // Observe descendant focus and center that item. Index in LazyRow accounts for optional leading.
            val absIdx = idx + leadingOffset
            var focused by remember { mutableStateOf(false) }
            val baseMod = if (isTv) Modifier.onFocusEvent { st ->
                focused = st.isFocused || st.hasFocus
                if (st.hasFocus) {
                    if (skipFirstCenter.value && absIdx == leadingOffset) {
                        // First focus on left-most tile: don't center yet
                        skipFirstCenter.value = false
                    } else {
                        if (state.isScrollInProgress) latestFocusIdx.value = absIdx else pendingScrollIndex.value = absIdx
                    }
                    runCatching { com.chris.m3usuite.core.debug.GlobalDebug.logTree("row:focusIdx", "idx:$absIdx") }
                }
            } else Modifier
            val itemMod = if (isTv && idx == 0) baseMod.focusRequester(firstFocus) else baseMod
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
            androidx.compose.foundation.layout.Box(itemMod) { itemContent(m) }
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
    val fling = rememberSnapFlingBehavior(state)
    val isTv = com.chris.m3usuite.ui.skin.isTvDevice(LocalContext.current)
    val pendingScrollIndex = remember { mutableStateOf(-1) }
    val latestFocusIdx = remember { mutableStateOf<Int?>(null) }
    val firstFocus = remember { FocusRequester() }
    // Ensure the first item's FocusRequester is attached before enabling custom enter
    val firstAttached = remember { mutableStateOf(false) }
    val leadingOffset = if (leading != null) 1 else 0
    val initRequested = remember { mutableStateOf(false) }
    val skipFirstCenter = remember { mutableStateOf(true) }
    val enterEnabled = remember { mutableStateOf(false) }

    suspend fun centerOnIndex(absIndex: Int) { state.centerItemSafely(absIndex) }

    LaunchedEffect(pendingScrollIndex.value) {
        val target = pendingScrollIndex.value
        if (target >= 0) {
            centerOnIndex(target)
            pendingScrollIndex.value = -1
        }
    }

    // Ensure the very first tile takes focus initially on TV once it's visible
    LaunchedEffect(state, isTv, firstAttached.value) {
        if (!isTv) return@LaunchedEffect
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
                    // then arm enter
                    enterEnabled.value = true
                    com.chris.m3usuite.core.debug.GlobalDebug.logTree("enter:Set:RowCorePaged")
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
    LazyRow(
        modifier = pagedModifier,
        state = state,
        flingBehavior = fling,
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
            val baseMod = if (isTv) Modifier.onFocusEvent { st ->
                focused = st.isFocused || st.hasFocus
                if (st.hasFocus) {
                    if (skipFirstCenter.value && absIdx == leadingOffset) {
                        skipFirstCenter.value = false
                    } else {
                        if (state.isScrollInProgress) latestFocusIdx.value = absIdx else pendingScrollIndex.value = absIdx
                    }
                    runCatching { com.chris.m3usuite.core.debug.GlobalDebug.logTree("row:focusIdx", "idx:$absIdx") }
                }
            } else Modifier
            val itemMod = if (isTv && index == 0) baseMod.focusRequester(firstFocus) else baseMod
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
            androidx.compose.foundation.layout.Box(itemMod) { itemContent(index, mi) }
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
