package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.focus.OnPrefetchKeys
import com.chris.m3usuite.ui.focus.OnPrefetchPaged
import com.chris.m3usuite.ui.focus.RowConfig
import com.chris.m3usuite.ui.layout.FishHeaderData
import com.chris.m3usuite.ui.layout.LocalFishHeaderController

enum class RowEngine { Light, Media, Paged }

/** Generic Light row: minimal engine, any item type (no DPAD extras). */
@Composable
fun FishRowLight(
    stateKey: String,
    itemCount: Int,
    itemKey: ((Int) -> Any)? = null,
    modifier: Modifier = Modifier,
    title: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    itemContent: @Composable (index: Int) -> Unit
) {
    val d = LocalFishDimens.current
    val contentPadding = PaddingValues(horizontal = d.contentPaddingHorizontalDp)
    val rowBody: @Composable () -> Unit = {
        FocusKit.TvRowLight(
            stateKey = stateKey,
            itemCount = itemCount,
            itemKey = itemKey,
            itemSpacing = d.tileSpacingDp,
            contentPadding = contentPadding
        ) { idx -> itemContent(idx) }
    }
    if (title == null && headerAction == null) {
        Box(modifier) { rowBody() }
    } else {
        Column(modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = d.contentPaddingHorizontalDp, vertical = 8.dp)
            ) {
                title?.let { Text(text = it, style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.weight(1f))
                headerAction?.invoke()
            }
            rowBody()
        }
    }
}

/** Media row for MediaItem lists: DPAD extras, chrome edge behavior, focus persistence. */
@Composable
fun FishRow(
    items: List<MediaItem>,
    stateKey: String?,
    modifier: Modifier = Modifier,
    title: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    leading: (@Composable (() -> Unit))? = null,
    onPrefetchKeys: OnPrefetchKeys? = null,
    edgeLeftExpandChrome: Boolean = false,
    initialFocusEligible: Boolean = true,
    debugKey: String? = null,
    itemKey: (MediaItem) -> Long = { it.id },
    header: FishHeaderData? = null,
    itemModifier: @Composable (index: Int, absoluteIndex: Int, media: MediaItem, base: Modifier, state: androidx.compose.foundation.lazy.LazyListState) -> Modifier = { _, _, _, base, _ -> base },
    itemContent: @Composable (MediaItem) -> Unit
) {
    val d = LocalFishDimens.current
    val config = RowConfig(
        stateKey = stateKey,
        debugKey = debugKey ?: stateKey,
        contentPadding = PaddingValues(horizontal = d.contentPaddingHorizontalDp),
        initialFocusEligible = initialFocusEligible,
        edgeLeftExpandChrome = edgeLeftExpandChrome
    )
    val rowBody: @Composable () -> Unit = {
        val headerController = LocalFishHeaderController.current
        val headerFocusCounter = remember(header?.anchorKey) { mutableStateOf(0) }
        FocusKit.TvRowMedia(
            items = items,
            config = config,
            leading = leading,
            onPrefetchKeys = onPrefetchKeys,
            itemKey = itemKey,
            itemModifier = { index, absoluteIndex, media, base, state ->
                var decorated = itemModifier(index, absoluteIndex, media, base, state)
                if (header != null && headerController != null) {
                    var itemHasFocus by remember { mutableStateOf(false) }
                    DisposableEffect(headerController, header.anchorKey) {
                        onDispose {
                            if (itemHasFocus) {
                                val newCount = (headerFocusCounter.value - 1).coerceAtLeast(0)
                                headerFocusCounter.value = newCount
                                if (newCount == 0) headerController.deactivate(header)
                            }
                        }
                    }
                    decorated = decorated.onFocusEvent { st ->
                        val focused = st.hasFocus || st.isFocused
                        if (focused != itemHasFocus) {
                            itemHasFocus = focused
                            if (focused) {
                                val wasZero = headerFocusCounter.value == 0
                                headerFocusCounter.value += 1
                                if (wasZero) headerController.activate(header)
                            } else {
                                headerFocusCounter.value = (headerFocusCounter.value - 1).coerceAtLeast(0)
                                if (headerFocusCounter.value == 0) headerController.deactivate(header)
                            }
                        }
                    }
                }
                decorated
            }
        ) { m -> itemContent(m) }
    }
    if (title == null && headerAction == null) {
        Box(modifier) { rowBody() }
    } else {
        Column(modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = d.contentPaddingHorizontalDp, vertical = 8.dp)
            ) {
                title?.let { Text(text = it, style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.weight(1f))
                headerAction?.invoke()
            }
            rowBody()
        }
    }
}

/** Paged media row (MediaItem) */
@Composable
fun FishRowPaged(
    items: LazyPagingItems<MediaItem>,
    stateKey: String?,
    modifier: Modifier = Modifier,
    title: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    leading: (@Composable (() -> Unit))? = null,
    onPrefetchPaged: OnPrefetchPaged? = null,
    edgeLeftExpandChrome: Boolean = false,
    debugKey: String? = null,
    itemKey: (index: Int) -> Long = { idx -> items[idx]?.id ?: idx.toLong() },
    header: FishHeaderData? = null,
    itemContent: @Composable (index: Int, MediaItem) -> Unit
) {
    val d = LocalFishDimens.current
    val config = RowConfig(
        stateKey = stateKey,
        debugKey = debugKey ?: stateKey,
        contentPadding = PaddingValues(horizontal = d.contentPaddingHorizontalDp),
        edgeLeftExpandChrome = edgeLeftExpandChrome
    )
    val rowBody: @Composable () -> Unit = {
        val headerController = LocalFishHeaderController.current
        val headerFocusCounter = remember(header?.anchorKey) { mutableStateOf(0) }
        FocusKit.TvRowPaged(
            items = items,
            config = config,
            leading = leading,
            onPrefetchPaged = onPrefetchPaged,
            itemKey = itemKey,
            itemModifier = { index, absoluteIndex, media, base, state ->
                var decorated = base
                if (header != null && headerController != null) {
                    var itemHasFocus by remember { mutableStateOf(false) }
                    DisposableEffect(headerController, header.anchorKey) {
                        onDispose {
                            if (itemHasFocus) {
                                val newCount = (headerFocusCounter.value - 1).coerceAtLeast(0)
                                headerFocusCounter.value = newCount
                                if (newCount == 0) headerController.deactivate(header)
                            }
                        }
                    }
                    decorated = decorated.onFocusEvent { st ->
                        val focused = st.hasFocus || st.isFocused
                        if (focused != itemHasFocus) {
                            itemHasFocus = focused
                            if (focused) {
                                val wasZero = headerFocusCounter.value == 0
                                headerFocusCounter.value += 1
                                if (wasZero) headerController.activate(header)
                            } else {
                                headerFocusCounter.value = (headerFocusCounter.value - 1).coerceAtLeast(0)
                                if (headerFocusCounter.value == 0) headerController.deactivate(header)
                            }
                        }
                    }
                }
                decorated
            }
        ) { idx, mi -> itemContent(idx, mi) }
    }
    if (title == null && headerAction == null) {
        Box(modifier) { rowBody() }
    } else {
        Column(modifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = d.contentPaddingHorizontalDp, vertical = 8.dp)
            ) {
                title?.let { Text(text = it, style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.weight(1f))
                headerAction?.invoke()
            }
            rowBody()
        }
    }
}
