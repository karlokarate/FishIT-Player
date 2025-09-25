package com.chris.m3usuite.ui.components.rows

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.items
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

    val initialPrefetchCount = remember(state, ordered) {
        val savedIndex = state.firstVisibleItemIndex
        val extend = savedIndex + config.lazyLoadWindow
        val desired = maxOf(config.initialWindow, extend + 1)
        minOf(ordered.size, desired)
    }
    var count by remember(ordered, initialPrefetchCount) { mutableStateOf(initialPrefetchCount) }

    // Lazy-load erweitern
    LaunchedEffect(state, ordered.size) {
        snapshotFlow { state.firstVisibleItemIndex }
            .collect { idx ->
                if (idx > count - config.lazyLoadWindow && count < ordered.size) {
                    count = (count + config.lazyLoadStep).coerceAtMost(ordered.size)
                }
            }
    }

    // Sichtbare Keys → Prefetch, zentral gedrosselt
    LaunchedEffect(state, ordered, onPrefetchKeys, config.debounceVisibleMs) {
        if (onPrefetchKeys == null) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long } }
            .distinctUntilChanged()
            .debounce(config.debounceVisibleMs)
            .collect { keys -> if (keys.isNotEmpty()) onPrefetchKeys(keys) }
    }

    LazyRow(
        state = state,
        flingBehavior = fling,
        contentPadding = config.contentPadding
    ) {
        if (leading != null) item("leading") { leading() }
        items(ordered.take(count), key = { itemKey(it) }) { m ->
            itemContent(m)
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

    LazyRow(
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
            itemContent(index, mi)
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
