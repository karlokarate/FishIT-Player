package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import com.chris.m3usuite.model.MediaItem
import kotlinx.coroutines.launch

/**
 * Content builder for Telegram tiles so FishRow/FishTile can render them without legacy Home rows.
 */
data class TelegramTileContent(
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
fun FishTelegramBadge(size: Dp = 22.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF229ED9), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .focusProperties { canFocus = false }
    ) {
        Text(
            text = "T",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun buildTelegramTileContent(
    media: MediaItem,
    onPlay: (() -> Unit)? = null
): TelegramTileContent {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val poster = media.poster ?: media.backdrop ?: media.logo ?: media.images.firstOrNull()
    val footer: (@Composable () -> Unit)? = media.plot?.takeIf { it.isNotBlank() }?.let { plot ->
        { FishMeta.PlotFooter(plot) }
    }
    val onClick: () -> Unit = {
        onPlay?.invoke()
    }
    val onFocusChanged: ((Boolean) -> Unit)? = { focused ->
        if (focused) scope.launch { FishLogging.logTelegramFocus(ctx, media) }
    }
    val topEndBadge: (@Composable () -> Unit)? = { FishTelegramBadge() }
    val bottomEndActions: (@Composable RowScope.() -> Unit)? = if (onPlay != null) {
        {
            with(FishActions) { LiveBottomActions(onPlay = onPlay, onOpenDetails = null) }
        }
    } else null

    return TelegramTileContent(
        title = media.name.takeIf { it.isNotBlank() } ?: media.sortTitle.ifBlank { "Telegram" },
        poster = poster,
        contentScale = ContentScale.Fit,
        selected = false,
        topStartBadge = null,
        topEndBadge = topEndBadge,
        bottomEndActions = bottomEndActions,
        footer = footer,
        overlay = null,
        onFocusChanged = onFocusChanged,
        onClick = onClick
    )
}

fun MediaItem.isTelegramItem(): Boolean {
    return (source?.equals("TG", ignoreCase = true) == true) ||
        tgChatId != null || tgMessageId != null || tgFileId != null
}
