@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.chris.m3usuite.data.repo.EpgRepository
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.util.AppAsyncImage
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import com.chris.m3usuite.ui.layout.FishLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch

data class LiveTileContent(
    val title: String?,
    val logo: Any?,
    val contentScale: ContentScale,
    val selected: Boolean,
    val resumeFraction: Float?,
    val topStartBadge: (@Composable () -> Unit)?,
    val topEndBadge: (@Composable () -> Unit)?,
    val bottomEndActions: (@Composable RowScope.() -> Unit)?,
    val footer: (@Composable () -> Unit)?,
    val overlay: (@Composable BoxScope.() -> Unit)?,
    val onFocusChanged: ((Boolean) -> Unit)?,
    val onClick: () -> Unit
)

private fun MediaItem.isTelegramItem(): Boolean {
    if (source?.equals("TG", ignoreCase = true) == true) return true
    return tgChatId != null || tgMessageId != null || tgFileId != null
}

@Composable
fun FishTelegramBadge(
    modifier: Modifier = Modifier,
    small: Boolean = false
) {
    val size = if (small) 20.dp else 24.dp
    Surface(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        shape = CircleShape,
        color = Color(0xFF229ED9),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "T",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
fun buildLiveTileContent(
    media: MediaItem,
    selected: Boolean = false,
    onOpenDetails: (() -> Unit)? = null,
    onPlayDirect: (() -> Unit)? = null
): LiveTileContent {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val epgRepo = remember(store) { EpgRepository(ctx, store) }
    val scope = rememberCoroutineScope()

    val epgNow = remember { mutableStateOf("") }
    val epgNext = remember { mutableStateOf("") }
    val nowStartMs = remember { mutableStateOf<Long?>(null) }
    val nowEndMs = remember { mutableStateOf<Long?>(null) }
    val focusedState = remember { mutableStateOf(false) }

    observeEpgCache(media, epgNow, epgNext, nowStartMs, nowEndMs)
    fetchEpgOnFocus(media, focusedState, epgRepo, epgNow, epgNext, nowStartMs, nowEndMs)

    val overlay: (@Composable BoxScope.() -> Unit) = {
        LiveTileOverlay(
            media = media,
            epgNow = epgNow.value,
            epgNext = epgNext.value,
            nowStartMs = nowStartMs.value,
            nowEndMs = nowEndMs.value,
            onPlayDirect = onPlayDirect,
            onOpenDetails = onOpenDetails
        )
    }

    val topStartBadge: (@Composable () -> Unit) = {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(0xFF1DB954))
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.85f), shape = CircleShape)
        )
    }

    val topEndBadge: (@Composable () -> Unit)? = if (media.isTelegramItem()) {
        { FishTelegramBadge() }
    } else null

    val onFocusChanged: (Boolean) -> Unit = { focused ->
        focusedState.value = focused
        if (focused) {
            scope.launch {
                FishLogging.logLiveFocus(ctx, media)
            }
        }
    }

    val onClick: () -> Unit = {
        onOpenDetails?.invoke()
    }

    return LiveTileContent(
        title = media.name,
        logo = media.logo ?: media.poster ?: media.backdrop,
        contentScale = ContentScale.Fit,
        selected = selected,
        resumeFraction = null,
        topStartBadge = topStartBadge,
        topEndBadge = topEndBadge,
        bottomEndActions = null,
        footer = null,
        overlay = overlay,
        onFocusChanged = onFocusChanged,
        onClick = onClick
    )
}

@Composable
private fun observeEpgCache(
    media: MediaItem,
    epgNow: MutableState<String>,
    epgNext: MutableState<String>,
    nowStartMs: MutableState<Long?>,
    nowEndMs: MutableState<Long?>
) {
    val ctx = LocalContext.current
    val epgChannelId = remember(media.epgChannelId) { media.epgChannelId?.trim().orEmpty() }
    DisposableEffect(epgChannelId) {
        if (epgChannelId.isEmpty()) {
            return@DisposableEffect onDispose { }
        }
        val store = com.chris.m3usuite.data.obx.ObxStore.get(ctx)
        val box = store.boxFor(com.chris.m3usuite.data.obx.ObxEpgNowNext::class.java)
        val query = box.query(com.chris.m3usuite.data.obx.ObxEpgNowNext_.channelId.equal(epgChannelId)).build()
        fun apply(row: com.chris.m3usuite.data.obx.ObxEpgNowNext?) {
            epgNow.value = row?.nowTitle.orEmpty()
            epgNext.value = row?.nextTitle.orEmpty()
            nowStartMs.value = row?.nowStartMs
            nowEndMs.value = row?.nowEndMs
        }
        apply(query.findFirst())
        val sub = query.subscribe().on(io.objectbox.android.AndroidScheduler.mainThread()).observer { res ->
            apply(res.firstOrNull())
        }
        return@DisposableEffect onDispose { sub.cancel() }
    }
}

@Composable
private fun fetchEpgOnFocus(
    media: MediaItem,
    focusedState: MutableState<Boolean>,
    epgRepo: EpgRepository,
    epgNow: MutableState<String>,
    epgNext: MutableState<String>,
    nowStartMs: MutableState<Long?>,
    nowEndMs: MutableState<Long?>
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(media.streamId) {
        val streamId = media.streamId ?: return@LaunchedEffect
        snapshotFlow { focusedState.value }
            .distinctUntilChanged()
            .filter { it }
            .debounce(120)
            .collect {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val list = epgRepo.nowNext(streamId, 2)
                        val first = list.getOrNull(0)
                        val second = list.getOrNull(1)
                        epgNow.value = first?.title.orEmpty()
                        epgNext.value = second?.title.orEmpty()
                        val start = first?.start?.toLongOrNull()?.times(1000)
                        val end = first?.end?.toLongOrNull()?.times(1000)
                        nowStartMs.value = start
                        nowEndMs.value = end
                    }
                }
            }
    }
}

private fun computeProgress(startMs: Long?, endMs: Long?): Float? {
    if (startMs == null || endMs == null || endMs <= startMs) return null
    val now = System.currentTimeMillis()
    return ((now - startMs).coerceAtLeast(0L).toFloat() / (endMs - startMs).toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun BoxScope.LiveTileOverlay(
    media: MediaItem,
    epgNow: String,
    epgNext: String,
    nowStartMs: Long?,
    nowEndMs: Long?,
    onPlayDirect: (() -> Unit)?,
    onOpenDetails: (() -> Unit)?
) {
    val logo = media.logo ?: media.poster ?: media.backdrop
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp)
            .size(76.dp)
    ) {
        var loaded by remember(logo) { mutableStateOf(false) }
        if (!loaded) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = CircleShape
            ) {}
        }
        if (logo != null) {
            AppAsyncImage(
                url = logo,
                contentDescription = media.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape),
                crossfade = false,
                onLoading = { loaded = false },
                onSuccess = { loaded = true },
                onError = { loaded = true }
            )
        }
    }

    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 96.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (epgNow.isNotBlank()) {
            val schedule = remember(nowStartMs, nowEndMs) {
                formatEpgRange(nowStartMs, nowEndMs)
            }
            Text(
                text = "Jetzt: ${epgNow}${schedule}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
        if (epgNext.isNotBlank()) {
            Text(
                text = "Danach: ${epgNext}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }

    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 150.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIconButton(
                icon = AppIcon.PlayCircle,
                contentDescription = "Abspielen",
                onClick = { onPlayDirect?.invoke() },
                size = 24.dp,
                modifier = Modifier.focusProperties { canFocus = false }
            )
            AppIconButton(
                icon = AppIcon.Info,
                contentDescription = "Details",
                onClick = { onOpenDetails?.invoke() },
                size = 24.dp,
                modifier = Modifier.focusProperties { canFocus = false }
            )
        }
        Surface(
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.75f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Text(
                text = media.name,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }

    mediaStreamProgressBar(epgProgress = computeProgress(nowStartMs, nowEndMs))
}

@Composable
private fun BoxScope.mediaStreamProgressBar(epgProgress: Float?) {
    epgProgress?.let { progress ->
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.15f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress)
                .height(3.dp)
                .background(Color(0xFF2196F3))
        )
    }
}

private fun formatEpgRange(startMs: Long?, endMs: Long?): String {
    if (startMs == null || endMs == null || endMs <= startMs) return ""
    return kotlin.runCatching {
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val start = fmt.format(java.util.Date(startMs))
        val end = fmt.format(java.util.Date(endMs))
        val remaining = ((endMs - System.currentTimeMillis()).coerceAtLeast(0L) / 60000L).toInt()
        " ${start}–${end} • noch ${remaining}m"
    }.getOrDefault("")
}
