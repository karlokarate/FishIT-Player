package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.ui.selection.LocalAssignSelection
import kotlinx.coroutines.launch

data class SeriesTileContent(
    val title: String?,
    val poster: Any?,
    val contentScale: ContentScale,
    val selected: Boolean,
    val topStartBadge: (@Composable () -> Unit)?,
    val topEndBadge: (@Composable () -> Unit)?,
    val bottomEndActions: (@Composable RowScope.() -> Unit)?,
    val footer: (@Composable () -> Unit)?,
    val overlay: (@Composable BoxScope.() -> Unit)?,
    val onFocusChanged: ((Boolean) -> Unit)?,
    val onClick: () -> Unit
)

@Composable
fun buildSeriesTileContent(
    media: MediaItem,
    allowAssign: Boolean = true,
    onOpenDetails: (() -> Unit)? = null,
    onPlayDirect: (() -> Unit)? = null, // usually series plays via episode; kept for parity
    onAssignToKid: (() -> Unit)? = null
): SeriesTileContent {
    val ctx = LocalContext.current
    val assign = LocalAssignSelection.current
    val scope = rememberCoroutineScope()

    val poster = media.poster ?: media.backdrop
    val selected = assign?.selectedSnapshot?.contains(media.id) == true

    val topStartBadge: (@Composable () -> Unit)? = if (allowAssign && assign != null) {
        { FishActions.AssignBadge(selected = selected) { assign.toggle(media.id) } }
    } else null

    val bottomEndActions: (@Composable RowScope.() -> Unit)? = if (assign?.active != true) {
        if (onPlayDirect != null || (allowAssign && onAssignToKid != null)) {
            {
                with(FishActions) {
                    if (onPlayDirect != null) {
                        VodBottomActions(onPlay = onPlayDirect)
                    }
                    if (allowAssign) {
                        AssignBottomAction(onAssign = onAssignToKid)
                    }
                }
            }
        } else null
    } else null

    val onClick: () -> Unit = {
        if (assign?.active == true) assign.toggle(media.id) else onOpenDetails?.invoke()
    }

    val footer: (@Composable () -> Unit)? = null // could show seasons/episodes later

    val displayTitle = FishMeta.displayVodTitle(media) // reuse year logic

    val onFocusChanged: ((Boolean) -> Unit)? = { focused ->
        if (focused) {
            scope.launch { FishLogging.logSeriesFocus(ctx, media) }
        }
    }

    return SeriesTileContent(
        title = displayTitle,
        poster = poster,
        contentScale = ContentScale.Fit,
        selected = selected,
        topStartBadge = topStartBadge,
        topEndBadge = null,
        bottomEndActions = bottomEndActions,
        footer = footer,
        overlay = null,
        onFocusChanged = onFocusChanged,
        onClick = onClick
    )
}
