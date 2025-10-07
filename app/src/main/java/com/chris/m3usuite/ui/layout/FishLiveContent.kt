package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.chris.m3usuite.model.MediaItem

data class LiveTileContent(
    val title: String?,
    val logo: Any?,
    val epgProgress: Float?,
    val bottomEndActions: (@Composable RowScope.() -> Unit)?,
    val footer: (@Composable () -> Unit)?,
    val onFocusChanged: ((Boolean) -> Unit)?,
    val onClick: () -> Unit
)

@Composable
fun buildLiveTileContent(
    media: MediaItem,
    onOpenDetails: (() -> Unit)? = null,
    onPlayDirect: (() -> Unit)? = null
): LiveTileContent {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val logo = media.logo ?: media.poster ?: media.backdrop
    val epgProgress: Float? = null // TODO: centralize EPG now/next fraction later
    val bottomEndActions: (@Composable RowScope.() -> Unit)? = null // e.g., quick actions
    val footer: (@Composable () -> Unit)? = null
    val onFocusChanged: ((Boolean) -> Unit)? = null // add Live logging later
    val onClick: () -> Unit = { onOpenDetails?.invoke() }

    return LiveTileContent(
        title = media.name,
        logo = logo,
        epgProgress = epgProgress,
        bottomEndActions = bottomEndActions,
        footer = footer,
        onFocusChanged = onFocusChanged,
        onClick = onClick
    )
}

