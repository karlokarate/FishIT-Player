package com.chris.m3usuite.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.chris.m3usuite.core.logging.AppLog
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.ui.fx.ShimmerBox
import com.chris.m3usuite.ui.home.LocalChromeRowFocusSetter
import com.chris.m3usuite.ui.layout.LocalFishDimens
import com.chris.m3usuite.ui.state.rememberRouteListState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged

/** Configuration for FocusKit media rows. */
data class RowConfig(
    val stateKey: String? = null,
    val debugKey: String? = null,
    val contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    val initialFocusEligible: Boolean = true,
    val edgeLeftExpandChrome: Boolean = false,
)

/** Callback used by rows to prefetch data for visible item keys (IDs). */
typealias OnPrefetchKeys = suspend (visibleKeys: List<Long>) -> Unit

/** Callback used by paged rows to prefetch data for visible indices. */
typealias OnPrefetchPaged = suspend (visibleIndices: List<Int>, items: LazyPagingItems<MediaItem>) -> Unit

@Composable
fun MediaRowCore(
    items: List<MediaItem>,
    config: RowConfig = RowConfig(),
    leading: (@Composable (() -> Unit))? = null,
    onPrefetchKeys: OnPrefetchKeys? = null,
    itemKey: (MediaItem) -> Long = { it.id },
    itemModifier: @Composable (
        index: Int,
        absoluteIndex: Int,
        media: MediaItem,
        base: Modifier,
        state: LazyListState,
    ) -> Modifier = { _, _, _, base, _ -> base },
    itemContent: @Composable (MediaItem) -> Unit,
) {
    if (items.isEmpty() && leading == null) return
    val dims = LocalFishDimens.current
    val stateKey = config.stateKey ?: remember(items) { "row:${items.hashCode()}" }
    val listState = rememberRouteListState(stateKey)
    val isTv = FocusKit.isTvDevice(LocalContext.current)
    val focusModifier = if (isTv) FocusKit.run { Modifier.focusGroup() } else Modifier
    val spacing = Arrangement.spacedBy(dims.tileSpacingDp)
    val setRowFocus = LocalChromeRowFocusSetter.current

    if (onPrefetchKeys != null) {
        LaunchedEffect(listState, items) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
                .distinctUntilChanged()
                .collect { indices ->
                    if (indices.isNotEmpty()) {
                        val keys =
                            indices.mapNotNull { idx ->
                                items.getOrNull(idx)?.let { m -> itemKey(m) }
                            }
                        if (keys.isNotEmpty()) onPrefetchKeys(keys)
                    }
                }
        }
    }

    LazyRow(
        state = listState,
        modifier = focusModifier,
        contentPadding = config.contentPadding,
        horizontalArrangement = spacing,
    ) {
        if (leading != null) {
            item(key = "leading") {
                Box(Modifier.padding(end = dims.tileSpacingDp)) { leading() }
            }
        }
        itemsIndexed(items, key = { _, media -> itemKey(media) }) { index, media ->
            val base =
                FocusKit.run {
                    Modifier.tvFocusableItem(
                        stateKey = stateKey,
                        index = index,
                        onFocused = { setRowFocus(stateKey) },
                        debugTag = config.debugKey,
                    )
                }
            val decorated = itemModifier(index, index, media, base, listState)
            Box(decorated) { itemContent(media) }
        }
    }
}

@Composable
fun MediaRowCorePaged(
    items: LazyPagingItems<MediaItem>,
    config: RowConfig = RowConfig(),
    leading: (@Composable (() -> Unit))? = null,
    onPrefetchPaged: OnPrefetchPaged? = null,
    shimmerRefreshCount: Int = 10,
    shimmerAppendCount: Int = 6,
    itemKey: (index: Int) -> Long = { idx -> items[idx]?.id ?: idx.toLong() },
    itemModifier: @Composable (
        index: Int,
        absoluteIndex: Int,
        media: MediaItem,
        base: Modifier,
        state: LazyListState,
    ) -> Modifier = { _, _, _, base, _ -> base },
    itemContent: @Composable (index: Int, MediaItem) -> Unit,
) {
    val dims = LocalFishDimens.current
    val stateKey = config.stateKey ?: remember(items) { "rowPaged:${items.hashCode()}" }
    val listState = rememberRouteListState(stateKey)
    val isTv = FocusKit.isTvDevice(LocalContext.current)
    val focusModifier = if (isTv) FocusKit.run { Modifier.focusGroup() } else Modifier
    val spacing = Arrangement.spacedBy(dims.tileSpacingDp)
    val setRowFocus = LocalChromeRowFocusSetter.current

    LaunchedEffect(listState, items, onPrefetchPaged) {
        if (onPrefetchPaged == null) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collect { indices ->
                if (indices.isEmpty()) return@collect
                try {
                    onPrefetchPaged(indices, items)
                } catch (_: CancellationException) {
                    // Row was disposed; ignore silently
                } catch (t: Throwable) {
                    AppLog.log(
                        category = "focus",
                        level = AppLog.Level.DEBUG,
                        message = "row prefetch cancelled/failed",
                        extras = mapOf("reason" to (t.message ?: "unknown")),
                    )
                }
            }
    }

    LazyRow(
        state = listState,
        modifier = focusModifier,
        contentPadding = config.contentPadding,
        horizontalArrangement = spacing,
    ) {
        if (leading != null) {
            item(key = "leading") {
                Box(Modifier.padding(end = dims.tileSpacingDp)) { leading() }
            }
        }

        val count = items.itemCount
        if (count == 0 && items.loadState.refresh is LoadState.Loading) {
            items(shimmerRefreshCount) { index ->
                val base = FocusKit.run { Modifier.tvFocusableItem(stateKey, index) }
                Box(
                    base
                        .size(dims.tileWidthDp, dims.tileHeightDp)
                        .clip(
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(dims.tileCornerDp),
                        ),
                ) { ShimmerBox() }
            }
        } else {
            items(count = count, key = itemKey) { index ->
                val media = items[index]
                val base =
                    FocusKit.run {
                        Modifier.tvFocusableItem(
                            stateKey = stateKey,
                            index = index,
                            onFocused = { setRowFocus(stateKey) },
                            debugTag = config.debugKey,
                        )
                    }
                if (media != null) {
                    val decorated = itemModifier(index, index, media, base, listState)
                    Box(decorated) { itemContent(index, media) }
                } else {
                    Box(
                        base
                            .size(dims.tileWidthDp, dims.tileHeightDp)
                            .clip(
                                androidx.compose.foundation.shape
                                    .RoundedCornerShape(dims.tileCornerDp),
                            ),
                    ) { ShimmerBox() }
                }
            }
            if (items.loadState.append is LoadState.Loading) {
                items(shimmerAppendCount) { idx ->
                    val base =
                        FocusKit.run {
                            Modifier.tvFocusableItem(
                                stateKey = stateKey,
                                index = count + idx,
                                onFocused = { setRowFocus(stateKey) },
                                debugTag = config.debugKey,
                            )
                        }
                    Box(
                        base
                            .size(dims.tileWidthDp, dims.tileHeightDp)
                            .clip(
                                androidx.compose.foundation.shape
                                    .RoundedCornerShape(dims.tileCornerDp),
                            ),
                    ) { ShimmerBox() }
                }
            }
        }
    }
}
