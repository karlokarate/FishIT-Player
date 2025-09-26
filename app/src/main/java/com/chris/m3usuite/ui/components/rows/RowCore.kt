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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height

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
    val firstFocus = remember { FocusRequester() }
    val leadingOffset = if (leading != null) 1 else 0
    val initRequested = remember { mutableStateOf(false) }
    val enterEnabled = remember { mutableStateOf(false) }
    val skipFirstCenter = remember { mutableStateOf(true) }

    suspend fun centerOnIndex(absIndex: Int) {
        // Ensure item is visible at least once
        state.animateScrollToItem(absIndex)
        val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == absIndex } ?: return
        val viewportStart = state.layoutInfo.viewportStartOffset
        val viewportEnd = state.layoutInfo.viewportEndOffset
        val viewportSize = viewportEnd - viewportStart
        val desiredOffset = ((viewportSize - info.size) / 2f).toInt().coerceAtLeast(0)
        state.animateScrollToItem(absIndex, desiredOffset)
    }

    LaunchedEffect(pendingScrollIndex.value) {
        val target = pendingScrollIndex.value
        if (target >= 0) {
            centerOnIndex(target)
            pendingScrollIndex.value = -1
        }
    }

    // Ensure the very first tile takes focus initially on TV once it's visible
    LaunchedEffect(state, isTv) {
        if (!isTv) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { indices ->
                if (!initRequested.value && indices.contains(leadingOffset)) {
                    initRequested.value = true
                    enterEnabled.value = true
                    kotlinx.coroutines.delay(16)
                    runCatching {
                        com.chris.m3usuite.core.debug.GlobalDebug.logTree("focusReq:RowCore:first")
                        firstFocus.requestFocus()
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

    val listModifier = if (isTv) {
        var m: Modifier = Modifier.focusGroup().focusProperties { }
        if (enterEnabled.value) m = m.focusProperties { enter = { firstFocus } }
        m
    } else Modifier

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
            val baseMod = if (isTv) Modifier.onFocusEvent { st ->
                if (st.hasFocus) {
                    if (skipFirstCenter.value && absIdx == leadingOffset) {
                        // First focus on left-most tile: don't center yet
                        skipFirstCenter.value = false
                    } else {
                        pendingScrollIndex.value = absIdx
                    }
                }
            } else Modifier
            val itemMod = if (isTv && idx == 0) baseMod.focusRequester(firstFocus) else baseMod
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
    val state: LazyListState =
        config.stateKey?.let { com.chris.m3usuite.ui.state.rememberRouteListState(it) } ?: rememberLazyListState()
    val fling = rememberSnapFlingBehavior(state)
    val isTv = com.chris.m3usuite.ui.skin.isTvDevice(LocalContext.current)
    val pendingScrollIndex = remember { mutableStateOf(-1) }
    val firstFocus = remember { FocusRequester() }
    val leadingOffset = if (leading != null) 1 else 0
    val initRequested = remember { mutableStateOf(false) }
    val skipFirstCenter = remember { mutableStateOf(true) }
    val enterEnabled = remember { mutableStateOf(false) }

    suspend fun centerOnIndex(absIndex: Int) {
        state.animateScrollToItem(absIndex)
        val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == absIndex } ?: return
        val viewportStart = state.layoutInfo.viewportStartOffset
        val viewportEnd = state.layoutInfo.viewportEndOffset
        val viewportSize = viewportEnd - viewportStart
        val desiredOffset = ((viewportSize - info.size) / 2f).toInt().coerceAtLeast(0)
        state.animateScrollToItem(absIndex, desiredOffset)
    }

    LaunchedEffect(pendingScrollIndex.value) {
        val target = pendingScrollIndex.value
        if (target >= 0) {
            centerOnIndex(target)
            pendingScrollIndex.value = -1
        }
    }

    // Ensure the very first tile takes focus initially on TV once it's visible
    LaunchedEffect(state, isTv) {
        if (!isTv) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { indices ->
                if (!initRequested.value && indices.contains(leadingOffset)) {
                    initRequested.value = true
                    enterEnabled.value = true
                    kotlinx.coroutines.delay(16)
                    runCatching {
                        com.chris.m3usuite.core.debug.GlobalDebug.logTree("focusReq:RowCorePaged:first")
                        firstFocus.requestFocus()
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

    val pagedModifier = if (isTv) {
        var m: Modifier = Modifier.focusGroup().focusProperties { }
        if (enterEnabled.value) m = m.focusProperties { enter = { firstFocus } }
        m
    } else Modifier

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
            val baseMod = if (isTv) Modifier.onFocusEvent { st ->
                if (st.hasFocus) {
                    if (skipFirstCenter.value && absIdx == leadingOffset) {
                        skipFirstCenter.value = false
                    } else {
                        pendingScrollIndex.value = absIdx
                    }
                }
            } else Modifier
            val itemMod = if (isTv && index == 0) baseMod.focusRequester(firstFocus) else baseMod
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
