package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.chris.m3usuite.telegram.domain.TelegramItem
import com.chris.m3usuite.telegram.domain.TelegramItemType

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

// =============================================================================
// Phase D: TelegramItem-based composables
// =============================================================================

/**
 * Renders a TelegramItem domain object with the blue "T" badge.
 *
 * Phase D.2: New composable that accepts TelegramItem directly.
 * - Uses TelegramItem.metadata for title/year/genres
 * - Uses TelegramItem.posterRef for thumbnails
 * - Routes image loading through TelegramFileLoader
 *
 * **IMPORTANT**: Uses ensureImageDownloaded(TelegramImageRef) instead of
 * ensureThumbDownloaded(fileId) because fileIds are volatile and can become
 * stale after TDLib session changes. remoteIds are stable across sessions.
 *
 * @param item TelegramItem domain object
 * @param modifier Modifier for styling
 * @param showNew Show "NEU" badge
 * @param resumeFraction Resume progress fraction (0..1)
 * @param onClick Click handler
 */
@Composable
fun FishTelegramItemContent(
    item: TelegramItem,
    modifier: Modifier = Modifier,
    showNew: Boolean = false,
    resumeFraction: Float? = null,
    onClick: () -> Unit,
) {
    val fileLoader = rememberTelegramFileLoader()

    // Use posterRef.remoteId for identity stability (remoteId is stable across sessions)
    // fileId is volatile and can become stale after app restarts
    var thumbPath by remember(item.posterRef?.remoteId) {
        mutableStateOf<String?>(null)
    }

    // Load thumbnail via TelegramFileLoader using remoteId-first resolution
    LaunchedEffect(item.posterRef?.remoteId) {
        val posterRef = item.posterRef
        if (thumbPath == null && posterRef != null) {
            // Use ensureImageDownloaded which uses remoteId-first resolution
            // This avoids 404 errors from stale fileIds
            thumbPath = fileLoader.ensureImageDownloaded(posterRef)
        }
    }

    // Generate title from metadata
    val title = item.metadata.title ?: "Untitled"

    // Determine tile style based on item type
    when (item.type) {
        TelegramItemType.SERIES_EPISODE -> {
            FishTile(
                title = title,
                poster = thumbPath,
                modifier = modifier,
                showNew = showNew,
                resumeFraction = resumeFraction,
                topStartBadge = { TelegramBadge() },
                onClick = onClick,
            )
        }
        TelegramItemType.AUDIOBOOK,
        TelegramItemType.RAR_ITEM,
        -> {
            // Archive/audiobook items get a different visual treatment
            FishTile(
                title = title,
                poster = thumbPath,
                modifier = modifier,
                showNew = showNew,
                resumeFraction = null, // No resume for archives
                topStartBadge = { TelegramBadge() },
                onClick = onClick,
            )
        }
        TelegramItemType.POSTER_ONLY -> {
            // Poster-only items (no video)
            FishTile(
                title = title,
                poster = thumbPath,
                modifier = modifier,
                showNew = showNew,
                resumeFraction = null, // No resume for poster-only
                topStartBadge = { TelegramBadge() },
                onClick = onClick,
            )
        }
        else -> {
            // MOVIE, CLIP - standard VOD treatment
            FishTile(
                title = title,
                poster = thumbPath,
                modifier = modifier,
                showNew = showNew,
                resumeFraction = resumeFraction,
                topStartBadge = { TelegramBadge() },
                onClick = onClick,
            )
        }
    }
}

/**
 * Row for displaying TelegramItem objects using FishRowLight pattern.
 *
 * Phase D.2: New row composable that accepts TelegramItem list directly.
 * Uses FishRowLight since FishRow is typed for MediaItem.
 *
 * @param items List of TelegramItem domain objects
 * @param stateKey State key for row persistence
 * @param title Row title
 * @param modifier Modifier for styling
 * @param onItemClick Click handler receiving TelegramItem
 */
@Composable
fun FishTelegramItemRow(
    items: List<TelegramItem>,
    stateKey: String,
    title: String,
    modifier: Modifier = Modifier,
    onItemClick: (TelegramItem) -> Unit,
) {
    FishRowLight(
        stateKey = stateKey,
        itemCount = items.size,
        itemKey = { idx -> items[idx].anchorMessageId },
        modifier = modifier,
        title = title,
    ) { index ->
        val item = items[index]
        FishTelegramItemContent(
            item = item,
            onClick = { onItemClick(item) },
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
