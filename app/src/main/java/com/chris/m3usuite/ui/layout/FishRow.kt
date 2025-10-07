package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.ui.components.rows.RowConfig
import com.chris.m3usuite.ui.focus.FocusKit
import androidx.paging.compose.LazyPagingItems

enum class RowEngine { Light, Media, Paged }

/** Generic Light row: minimal engine, any item type (no DPAD extras). */
@Composable
fun <T> FishRowLight(
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
    if (title == null && headerAction == null) {
        FocusKit.TvRowLight(
            stateKey = stateKey,
            itemCount = itemCount,
            itemKey = itemKey,
            itemSpacing = d.tileSpacingDp,
            contentPadding = contentPadding
        ) { idx -> itemContent(idx) }
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
            FocusKit.TvRowLight(
                stateKey = stateKey,
                itemCount = itemCount,
                itemKey = itemKey,
                itemSpacing = d.tileSpacingDp,
                contentPadding = contentPadding
            ) { idx -> itemContent(idx) }
        }
    }
}

/** Media row for MediaItem lists: DPAD extras, chrome edge behavior, focus persistence. */
@Composable
fun FishRow(
    items: List<MediaItem>,
    stateKey: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    edgeLeftExpandChrome: Boolean = false,
    initialFocusEligible: Boolean = true,
    itemContent: @Composable (MediaItem) -> Unit
) {
    val d = LocalFishDimens.current
    val config = RowConfig(
        stateKey = stateKey,
        contentPadding = PaddingValues(horizontal = d.contentPaddingHorizontalDp),
        initialFocusEligible = initialFocusEligible,
        edgeLeftExpandChrome = edgeLeftExpandChrome
    )
    val rowBody: @Composable () -> Unit = {
        FocusKit.TvRowMedia(
            items = items,
            config = config,
            onPrefetchKeys = { _ -> },
            itemKey = { it.id }
        ) { m -> itemContent(m) }
    }
    if (title == null && headerAction == null) {
        rowBody()
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
    stateKey: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    edgeLeftExpandChrome: Boolean = false,
    itemContent: @Composable (index: Int, MediaItem) -> Unit
) {
    val d = LocalFishDimens.current
    val config = RowConfig(
        stateKey = stateKey,
        contentPadding = PaddingValues(horizontal = d.contentPaddingHorizontalDp),
        edgeLeftExpandChrome = edgeLeftExpandChrome
    )
    val rowBody: @Composable () -> Unit = {
        FocusKit.TvRowPaged(
            items = items,
            config = config,
            onPrefetchPaged = { _, _ -> }
        ) { idx, mi -> itemContent(idx, mi) }
    }
    if (title == null && headerAction == null) {
        rowBody()
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

