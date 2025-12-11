package com.fishit.player.core.ui.layout

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fishit.player.core.ui.theme.FishTheme
import com.fishit.player.core.ui.theme.LocalFishDimens

/**
 * Row header with title and optional count
 */
@Composable
fun FishRowHeader(
    title: String,
    count: Int? = null,
    modifier: Modifier = Modifier
) {
    val dimens = LocalFishDimens.current

    Text(
        text = if (count != null) "$title ($count)" else title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(
            horizontal = dimens.contentPaddingHorizontal,
            vertical = 8.dp
        )
    )
}

/**
 * FishRow - Horizontal scrolling row of tiles
 *
 * Supports DPAD navigation with focus memory.
 *
 * @param title Row header title
 * @param items List of items to display
 * @param itemKey Key function for item identity
 * @param onRowFocused Called when any tile in row gains focus
 * @param modifier Modifier
 * @param itemContent Composable for each item
 */
@Composable
fun <T> FishRow(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    itemKey: ((T) -> Any)? = null,
    count: Int? = null,
    onRowFocused: ((Boolean) -> Unit)? = null,
    itemContent: @Composable (item: T, index: Int, isFocused: Boolean) -> Unit
) {
    val dimens = LocalFishDimens.current
    val listState = rememberLazyListState()
    var focusedIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        FishRowHeader(
            title = title,
            count = count ?: items.size
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(
                horizontal = dimens.contentPaddingHorizontal,
                vertical = 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(dimens.tileSpacing),
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
        ) {
            items(
                items = items,
                key = itemKey
            ) { item ->
                val index = items.indexOf(item)
                val isFocused = focusedIndex == index

                itemContent(item, index, isFocused)
            }
        }
    }
}

/**
 * Simpler FishRow variant with direct tile generation
 */
@Composable
fun <T> FishRowSimple(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    itemKey: ((T) -> Any)? = null,
    getTitle: (T) -> String?,
    getPoster: (T) -> Any?,
    getSourceColors: (T) -> List<Color> = { emptyList() },
    getResumeFraction: (T) -> Float? = { null },
    isNew: (T) -> Boolean = { false },
    onItemClick: (T) -> Unit
) {
    FishRow(
        title = title,
        items = items,
        modifier = modifier,
        itemKey = itemKey
    ) { item, index, isFocused ->
        FishTile(
            title = getTitle(item),
            poster = getPoster(item),
            sourceColors = getSourceColors(item),
            resumeFraction = getResumeFraction(item),
            isNew = isNew(item),
            onClick = { onItemClick(item) }
        )
    }
}

/**
 * Empty state row placeholder
 */
@Composable
fun FishRowEmpty(
    title: String,
    message: String = "No content available",
    modifier: Modifier = Modifier
) {
    val dimens = LocalFishDimens.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        FishRowHeader(title = title, count = 0)

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = dimens.contentPaddingHorizontal,
                vertical = 16.dp
            )
        )
    }
}
