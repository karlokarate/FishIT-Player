package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.telegram.core.T_TelegramServiceClient
import com.chris.m3usuite.telegram.core.TelegramFileLoader

/**
 * Shared TelegramFileLoader instance to avoid repeated instantiation per composable.
 * Uses singleton T_TelegramServiceClient for efficient resource usage.
 */
@Composable
private fun rememberTelegramFileLoader(): TelegramFileLoader {
    val context = LocalContext.current
    return remember {
        val serviceClient = T_TelegramServiceClient.getInstance(context)
        TelegramFileLoader(serviceClient)
    }
}

/**
 * Renders a Telegram media item with the blue "T" badge.
 * Conforms to existing FishTile pattern for UI consistency.
 *
 * Supports all Telegram MediaKinds:
 * - Movie (VOD)
 * - Series
 * - Episode
 * - Clip
 * - Archive
 *
 * Features:
 * - Thumbnail/cover display (when available)
 * - DPAD/TV focus support
 * - Consistent size and animations
 */
@Composable
fun FishTelegramContent(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    showNew: Boolean = false,
    resumeFraction: Float? = null,
    onPlay: (() -> Unit)? = null,
    onAssign: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val fileLoader = rememberTelegramFileLoader()

    var thumbPath by remember(mediaItem.posterId, mediaItem.localPosterPath) {
        mutableStateOf(mediaItem.localPosterPath)
    }

    LaunchedEffect(mediaItem.posterId) {
        if (thumbPath == null && mediaItem.posterId != null) {
            thumbPath = fileLoader.ensureThumbDownloaded(mediaItem.posterId!!)
        }
    }

    val posterModel = thumbPath ?: mediaItem.localPosterPath ?: mediaItem.poster

    // Determine tile style based on media type
    when (mediaItem.type) {
        "series" -> {
            // Use SeriesFishTile style for series content
            FishTile(
                title = mediaItem.name,
                poster = posterModel,
                modifier = modifier,
                showNew = showNew,
                resumeFraction = resumeFraction,
                topStartBadge = {
                    TelegramBadge()
                },
                onClick = onClick,
            )
        }
        "episode" -> {
            // Use standard tile with episode indicator
            FishTile(
                title = mediaItem.name,
                poster = posterModel,
                modifier = modifier,
                showNew = showNew,
                resumeFraction = resumeFraction,
                topStartBadge = {
                    TelegramBadge()
                },
                onClick = onClick,
            )
        }
        else -> {
            // Default VOD/Movie/Clip/Archive style
            FishTile(
                title = mediaItem.name,
                poster = posterModel,
                modifier = modifier,
                showNew = showNew,
                resumeFraction = resumeFraction,
                topStartBadge = {
                    TelegramBadge()
                },
                onClick = onClick,
            )
        }
    }
}

/**
 * Blue "T" badge for Telegram content.
 * Positioned at top-start of tile.
 */
@Composable
private fun TelegramBadge() {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .background(Color(0xFF0088CC), CircleShape)
                .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "T",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Row for displaying Telegram content using FishRow pattern.
 * Ensures parallel structure with existing Xtream rows.
 */
@Composable
fun FishTelegramRow(
    items: List<MediaItem>,
    stateKey: String,
    title: String,
    modifier: Modifier = Modifier,
    onItemClick: (MediaItem) -> Unit,
) {
    FishRow(
        items = items,
        stateKey = stateKey,
        title = title,
        modifier = modifier,
        header =
            FishHeaderData.Text(
                text = title,
                anchorKey = "telegram_$stateKey",
            ),
    ) { item ->
        FishTelegramContent(
            mediaItem = item,
            onClick = { onItemClick(item) },
        )
    }
}
