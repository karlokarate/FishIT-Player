package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.chris.m3usuite.model.MediaItem

@Composable
fun VodFishTile(
    media: MediaItem,
    isNew: Boolean,
    allowAssign: Boolean,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: ((MediaItem) -> Unit)? = null,
) {
    val content =
        buildVodTileContent(
            media = media,
            newIds = if (isNew) setOf(media.id) else emptySet(),
            allowAssign = allowAssign,
            onOpenDetails = { onOpenDetails(media) },
            onPlayDirect = { onPlayDirect(media) },
            onAssignToKid = onAssignToKid?.let { handler -> { handler(media) } },
        )
    FishTile(
        title = content.title,
        poster = content.poster,
        contentScale = content.contentScale,
        resumeFraction = content.resumeFraction,
        showNew = content.showNew,
        selected = content.selected,
        topStartBadge = content.topStartBadge,
        topEndBadge = content.topEndBadge,
        bottomEndActions = content.bottomEndActions,
        footer = content.footer,
        overlay = content.overlay,
        onFocusChanged = content.onFocusChanged,
        onClick = content.onClick,
    )
}

@Composable
fun SeriesFishTile(
    media: MediaItem,
    isNew: Boolean,
    allowAssign: Boolean,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    onAssignToKid: ((MediaItem) -> Unit)? = null,
) {
    val content =
        buildSeriesTileContent(
            media = media,
            allowAssign = allowAssign,
            onOpenDetails = { onOpenDetails(media) },
            onPlayDirect = { onPlayDirect(media) },
            onAssignToKid = onAssignToKid?.let { handler -> { handler(media) } },
        )
    FishTile(
        title = content.title,
        poster = content.poster,
        contentScale = content.contentScale,
        selected = content.selected,
        topStartBadge = content.topStartBadge,
        topEndBadge = content.topEndBadge,
        bottomEndActions = content.bottomEndActions,
        footer = content.footer,
        overlay = content.overlay,
        onFocusChanged = content.onFocusChanged,
        onClick = content.onClick,
    )
}

@Composable
fun LiveFishTile(
    media: MediaItem,
    onOpenDetails: (MediaItem) -> Unit,
    onPlayDirect: (MediaItem) -> Unit,
    selected: Boolean = false,
    extraOverlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val content =
        buildLiveTileContent(
            media = media,
            selected = selected,
            onOpenDetails = { onOpenDetails(media) },
            onPlayDirect = { onPlayDirect(media) },
        )
    val overlay: (@Composable BoxScope.() -> Unit)? =
        when {
            content.overlay == null && extraOverlay == null -> null
            content.overlay != null && extraOverlay == null -> content.overlay
            content.overlay == null && extraOverlay != null -> extraOverlay
            else -> {
                {
                    content.overlay?.invoke(this)
                    extraOverlay?.invoke(this)
                }
            }
        }
    FishTile(
        title = content.title,
        poster = content.logo,
        contentScale = content.contentScale,
        selected = content.selected,
        resumeFraction = content.resumeFraction,
        topStartBadge = content.topStartBadge,
        topEndBadge = content.topEndBadge,
        bottomEndActions = content.bottomEndActions,
        footer = content.footer,
        overlay = overlay,
        onFocusChanged = content.onFocusChanged,
        onClick = content.onClick,
    )
}

@Composable
fun TelegramFishTile(
    media: MediaItem,
    onOpenDetails: (MediaItem) -> Unit,
    onPlay: ((MediaItem) -> Unit)? = null,
) {
    // TODO: buildTelegramTileContent not yet implemented, using buildVodTileContent as fallback
    val content =
        buildVodTileContent(
            media = media,
            onOpenDetails = { onOpenDetails(media) },
            onPlayDirect = onPlay?.let { handler -> { handler(media) } },
        )
    FishTile(
        title = content.title,
        poster = content.poster,
        contentScale = content.contentScale,
        selected = content.selected,
        topStartBadge = content.topStartBadge,
        topEndBadge = content.topEndBadge,
        bottomEndActions = content.bottomEndActions,
        footer = content.footer,
        overlay = content.overlay,
        onFocusChanged = content.onFocusChanged,
        onClick = content.onClick,
    )
}
