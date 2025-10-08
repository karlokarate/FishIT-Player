@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
package com.chris.m3usuite.ui.components.rows

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.chris.m3usuite.domain.selectors.extractYearFrom
import com.chris.m3usuite.model.MediaItem
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.ui.common.AppIcon
import com.chris.m3usuite.ui.common.AppIconButton
import com.chris.m3usuite.ui.fx.ShimmerBox
import com.chris.m3usuite.ui.fx.tvFocusGlow
import com.chris.m3usuite.ui.layout.FishHeaderData
import com.chris.m3usuite.ui.layout.FishResumeTile
import com.chris.m3usuite.ui.layout.FishRow
import com.chris.m3usuite.ui.layout.LiveFishTile
import com.chris.m3usuite.ui.layout.LocalFishDimens
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.focus.focusScaleOnTv
import com.chris.m3usuite.ui.focus.tvClickable
import com.chris.m3usuite.ui.util.AppAsyncImage
import com.chris.m3usuite.ui.focus.focusGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collect
import kotlin.math.abs

val LocalRowItemHeightOverride = compositionLocalOf<Int?> { null }

private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val LIVE_TILE_ASPECT_RATIO = 16f / 9f
private val TILE_SHAPE = RoundedCornerShape(14.dp)

private fun isTelegram(item: MediaItem): Boolean {
    return (item.source?.equals("TG", ignoreCase = true) == true) ||
        (item.tgChatId != null || item.tgMessageId != null || item.tgFileId != null)
}

@Composable
private fun TelegramSourceBadge(
    modifier: Modifier = Modifier,
    small: Boolean = false
) {
    val size = if (small) 20.dp else 24.dp
    val bg = Color(0xFF229ED9)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
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
fun rowItemHeight(): Int {
    LocalRowItemHeightOverride.current?.let { return it }
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val sw = cfg.smallestScreenWidthDp
    val isTablet = sw >= 600
    val base = when {
        isTablet && isLandscape -> 230
        isTablet -> 210
        isLandscape -> 200
        else -> 180
    }
    return (base * 1.2f).toInt()
}

@Composable
fun LiveAddTile(
    requestInitialFocus: Boolean,
    onClick: () -> Unit
) {
    val dimens = LocalFishDimens.current
    val shape = RoundedCornerShape(dimens.tileCornerDp)
    val borderBrush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            // Wait for composition so focus requester is attached before requesting focus
            kotlinx.coroutines.delay(16)
            runCatching { focusRequester.requestFocus() }
        }
    }

    val clickable = FocusKit.run {
        Modifier.tvClickable(
            scaleFocused = dimens.focusScale,
            scalePressed = dimens.focusScale + 0.02f,
            elevationFocusedDp = 18f,
            brightenContent = false,
            autoBringIntoView = false,
            focusBorderWidth = dimens.focusBorderWidthDp,
            shape = shape,
            focusRequester = if (requestInitialFocus) focusRequester else null,
            onClick = onClick
        )
    }

    Card(
        modifier = Modifier
            .size(dimens.tileWidthDp, dimens.tileHeightDp)
            .padding(end = dimens.tileSpacingDp / 2)
            .then(clickable)
            .border(1.dp, borderBrush, shape)
            .drawWithContent {
                drawContent()
                val grad = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.12f),
                    1f to Color.Transparent
                )
                drawRect(brush = grad)
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape
    ) {
        Box(Modifier.fillMaxWidth()) {
            AppIconButton(
                icon = AppIcon.BookmarkAdd,
                contentDescription = "Sender hinzufügen",
                onClick = onClick,
                modifier = Modifier.align(Alignment.Center),
                size = 36.dp
            )
        }
    }
}


@Composable
fun ReorderableLiveRow(
    items: List<MediaItem>,
    onOpen: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    onAdd: () -> Unit,
    onReorder: (List<Long>) -> Unit,
    onRemove: (List<Long>) -> Unit,
    stateKey: String? = null,
    initialFocusEligible: Boolean = true,
    edgeLeftExpandChrome: Boolean = false,
    header: FishHeaderData? = null
) {
    val context = LocalContext.current
    val isTv = FocusKit.isTvDevice(context)
    val focusManager = LocalFocusManager.current
    val dimens = LocalFishDimens.current

    val order = remember(items) { mutableStateListOf<Long>().apply { addAll(items.map { it.id }.distinct()) } }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var targetKey by remember { mutableStateOf<Long?>(null) }
    var insertAfter by remember { mutableStateOf(false) }
    val tileLift by animateFloatAsState(if (draggingId != null) 1.05f else 1f, label = "liveReorderLift")
    val skipInnerClickFlags = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(items) {
        val incomingIds = items.map { it.id }
        val incomingSet = incomingIds.toSet()
        order.removeAll { it !in incomingSet }
        incomingIds.forEach { id -> if (!order.contains(id)) order.add(id) }
        selected = selected.filter { it in incomingSet }.toSet()
        val stale = skipInnerClickFlags.keys - incomingSet
        stale.forEach { skipInnerClickFlags.remove(it) }
    }

    val itemsById = remember(items) { items.associateBy { it.id } }
    val displayItems = remember(order, itemsById) { order.mapNotNull { itemsById[it] } }

    FishRow(
        items = displayItems,
        stateKey = stateKey,
        leading = {
            LiveAddTile(
                requestInitialFocus = isTv && displayItems.isEmpty() && initialFocusEligible,
                onClick = onAdd
            )
        },
        edgeLeftExpandChrome = edgeLeftExpandChrome,
        initialFocusEligible = initialFocusEligible && displayItems.isNotEmpty(),
        header = header,
        itemModifier = { _, _, media, base, state ->
            val id = media.id
            val isDragging = draggingId == id
            val dragTranslation by animateFloatAsState(
                targetValue = if (isDragging) dragOffset else 0f,
                label = "liveReorderDrag_" + id
            )

            val moveLeft: (() -> Unit)? = if (selected.isNotEmpty()) {
                {
                    val pos = order.indexOf(id)
                    if (pos > 0) {
                        order.removeAt(pos)
                        order.add(pos - 1, id)
                        onReorder(order.toList())
                    }
                }
            } else null

            val moveRight: (() -> Unit)? = if (selected.isNotEmpty()) {
                {
                    val pos = order.indexOf(id)
                    if (pos != -1 && pos < order.lastIndex) {
                        order.removeAt(pos)
                        order.add(pos + 1, id)
                        onReorder(order.toList())
                    }
                }
            } else null

            val navModifier = if (moveLeft != null || moveRight != null) {
                FocusKit.run {
                    Modifier.onDpadAdjustLeftRight(
                        onLeft = {
                            com.chris.m3usuite.core.debug.GlobalDebug.logDpad(
                                "LEFT",
                                mapOf("tile" to (media.streamId ?: media.id), "type" to media.type)
                            )
                            if (moveLeft != null) {
                                moveLeft()
                            } else {
                                focusManager.moveFocus(FocusDirection.Left)
                            }
                        },
                        onRight = {
                            com.chris.m3usuite.core.debug.GlobalDebug.logDpad(
                                "RIGHT",
                                mapOf("tile" to (media.streamId ?: media.id), "type" to media.type)
                            )
                            if (moveRight != null) {
                                moveRight()
                            } else {
                                focusManager.moveFocus(FocusDirection.Right)
                            }
                        }
                    )
                }
            } else Modifier

            val clickModifier = Modifier.combinedClickable(
                enabled = draggingId == null,
                onClick = {
                    if (draggingId == null) {
                        skipInnerClickFlags[id] = true
                        onOpen(id)
                    }
                },
                onLongClick = {
                    if (draggingId == null) {
                        selected = if (id in selected) selected - id else selected + id
                    }
                }
            )

            val dragModifier = Modifier.pointerInput(id, draggingId, order.toList()) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        draggingId = id
                        dragOffset = 0f
                        targetKey = null
                        insertAfter = false
                        skipInnerClickFlags[id] = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount.x

                        val visible = state.layoutInfo.visibleItemsInfo
                        val current = visible.find { it.key == id }
                        if (current != null) {
                            val center = current.offset + dragOffset + current.size / 2f
                            val targetInfo = visible
                                .filter { it.key is Long && it.key != id }
                                .minByOrNull { candidate ->
                                    val candidateCenter = candidate.offset + candidate.size / 2f
                                    abs(center - candidateCenter)
                                }
                            val toKey = targetInfo?.key as? Long
                            if (toKey != null) {
                                insertAfter = center > (targetInfo.offset + targetInfo.size / 2f)
                                targetKey = toKey
                                val from = order.indexOf(id)
                                val to = order.indexOf(toKey)
                                if (from != -1 && to != -1 && from != to) {
                                    order.removeAt(from)
                                    val insertIndex = if (insertAfter) {
                                        to + if (from < to) 0 else 1
                                    } else {
                                        to + if (from < to) -1 else 0
                                    }
                                    order.add(insertIndex.coerceIn(0, order.size), id)
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggingId = null
                        dragOffset = 0f
                        val snapshot = order.toList()
                        targetKey = null
                        insertAfter = false
                        onReorder(snapshot)
                    },
                    onDragCancel = {
                        draggingId = null
                        dragOffset = 0f
                        targetKey = null
                        insertAfter = false
                    }
                )
            }

            base
                .padding(end = dimens.tileSpacingDp)
                .graphicsLayer {
                    translationX = dragTranslation
                    scaleX = if (isDragging) tileLift else 1f
                    scaleY = if (isDragging) tileLift else 1f
                    shadowElevation = if (isDragging) 18f else 0f
                }
                .then(navModifier)
                .then(clickModifier)
                .then(dragModifier)
        }
    ) { media ->
        val id = media.id
        LiveFishTile(
            media = media,
            onOpenDetails = {
                val shouldSkip = skipInnerClickFlags[id] == true
                if (draggingId == null && !shouldSkip) {
                    onOpen(id)
                }
                skipInnerClickFlags[id] = false
            },
            onPlayDirect = {
                if (draggingId == null) onPlay(id)
            },
            selected = id in selected,
            extraOverlay = {
                if (id in selected) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    )
                }
                if (targetKey == id && !insertAfter) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                if (targetKey == id && insertAfter) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        )
    }

    if (selected.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(
                start = dimens.contentPaddingHorizontalDp,
                top = 4.dp,
                bottom = 4.dp
            )
        ) {
            TextButton(onClick = { selected = emptySet() }) { Text("Abbrechen") }
            if (selected.size == 1) {
                val id = selected.first()
                TextButton(onClick = {
                    val idx = order.indexOf(id)
                    if (idx > 0) {
                        order.removeAt(idx)
                        order.add(idx - 1, id)
                        onReorder(order.toList())
                    }
                }) { Text("← Verschieben") }
                TextButton(onClick = {
                    val idx = order.indexOf(id)
                    if (idx != -1 && idx < order.lastIndex) {
                        order.removeAt(idx)
                        order.add(idx + 1, id)
                        onReorder(order.toList())
                    }
                }) { Text("Verschieben →") }
            }
            TextButton(onClick = {
                onRemove(selected.toList())
                selected = emptySet()
            }) {
                Text("Entfernen (${selected.size})")
            }
        }
    }
}
