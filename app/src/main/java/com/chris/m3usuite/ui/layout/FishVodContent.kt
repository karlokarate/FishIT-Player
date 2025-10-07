package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.ui.selection.LocalAssignSelection
import kotlinx.coroutines.launch

data class VodTileContent(
    val title: String?,
    val poster: Any?,
    val resumeFraction: Float?,
    val showNew: Boolean,
    val selected: Boolean,
    val topStartBadge: (@Composable () -> Unit)?,
    val bottomEndActions: (@Composable RowScope.() -> Unit)?,
    val footer: (@Composable () -> Unit)?,
    val onFocusChanged: ((Boolean) -> Unit)?,
    val onClick: () -> Unit
)

@Composable
fun buildVodTileContent(
    media: MediaItem,
    newIds: Set<Long> = emptySet(),
    allowAssign: Boolean = true,
    onOpenDetails: (() -> Unit)? = null,
    onPlayDirect: (() -> Unit)? = null,
    onAssignToKid: (() -> Unit)? = null
): VodTileContent {
    val ctx = LocalContext.current
    val assign = LocalAssignSelection.current
    val scope = rememberCoroutineScope()

    val poster = FishMeta.pickVodPoster(media)
    val resumeFraction = FishMeta.rememberVodResumeFraction(media).value
    val showNew = newIds.contains(media.id)
    val selected = assign?.selectedSnapshot?.contains(media.id) == true

    val topStartBadge: (@Composable () -> Unit)? = if (allowAssign && assign != null) {
        { FishActions.AssignBadge(selected = selected) { assign.toggle(media.id) } }
    } else null

    val bottomEndActions: (@Composable RowScope.() -> Unit)? = if (assign?.active != true) {
        {
            // Play (if available)
            FishActions.VodBottomActions(onPlay = onPlayDirect)
            // Assign (if allowed and action provided)
            if (allowAssign) FishActions.AssignBottomAction(onAssign = onAssignToKid)
        }
    } else null

    val onClick: () -> Unit = {
        if (assign?.active == true) assign.toggle(media.id) else onOpenDetails?.invoke()
    }

    val footer: (@Composable () -> Unit)? = media.plot?.takeIf { it.isNotBlank() }?.let { plot ->
        { FishMeta.PlotFooter(plot) }
    }

    val displayTitle = FishMeta.displayVodTitle(media)

    val onFocusChanged: ((Boolean) -> Unit)? = { focused ->
        if (focused) scope.launch { FishLogging.logVodFocus(ctx, media) }
    }

    return VodTileContent(
        title = displayTitle,
        poster = poster,
        resumeFraction = resumeFraction,
        showNew = showNew,
        selected = selected,
        topStartBadge = topStartBadge,
        bottomEndActions = bottomEndActions,
        footer = footer,
        onFocusChanged = onFocusChanged,
        onClick = onClick
    )
}
