package com.chris.m3usuite.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chris.m3usuite.ui.actions.MediaAction

@Composable
fun DetailScaffold(
    title: String,
    subtitle: String? = null,
    heroUrl: Any?,
    posterUrl: Any?,
    actions: List<MediaAction>,
    meta: DetailMeta? = null,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    headerExtras: @Composable ColumnScope.() -> Unit = {},
    collapsibleMeta: Boolean = false,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(state = listState, modifier = modifier) {
        item {
            DetailHeader(
                title = title,
                subtitle = subtitle,
                heroUrl = heroUrl,
                posterUrl = posterUrl,
                actions = actions,
                meta = meta,
                headerExtras = headerExtras,
                collapsibleMeta = collapsibleMeta
            )
        }
        content()
    }
}
