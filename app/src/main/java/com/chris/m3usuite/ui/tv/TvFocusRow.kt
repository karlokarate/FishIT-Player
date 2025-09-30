@file:Suppress("FunctionName")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.chris.m3usuite.ui.tv

import androidx.compose.foundation.layout.Box
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.chris.m3usuite.ui.compat.focusGroup
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
private fun isTelevision(): Boolean {
    val ctx = LocalContext.current
    val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    val pm = ctx.packageManager
    @Suppress("DEPRECATION")
    val legacyTv = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    return ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            legacyTv
}

@Composable
fun <T> TvFocusRow(
    items: List<T>,
    key: (T) -> Any,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    item: @Composable (index: Int, value: T, itemModifier: Modifier) -> Unit
) {
    val tv = isTelevision()
    val pendingScrollIndex = remember { mutableStateOf(-1) }
    val latestFocusIdx = remember { mutableStateOf<Int?>(null) }
    val firstFocus = remember { FocusRequester() }
    val initRequested = remember { mutableStateOf(false) }
    val skipFirstCenter = remember { mutableStateOf(true) }
    val enterEnabled = remember { mutableStateOf(false) }
    val firstAttached = remember { mutableStateOf(false) }

    suspend fun centerOnIndex(target: Int) {
        listState.centerItemSafely(target)
    }

    LaunchedEffect(pendingScrollIndex.value) {
        val target = pendingScrollIndex.value
        if (target >= 0) {
            centerOnIndex(target)
            pendingScrollIndex.value = -1
        }
    }

    // Ensure the left-most item gets focus initially once visible on TV
    LaunchedEffect(listState, tv) {
        if (!tv) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { indices ->
                if (!initRequested.value && indices.contains(0) && firstAttached.value) {
                    initRequested.value = true
                    // Defer one frame to ensure focusRequester is attached, then request focus
                    kotlinx.coroutines.delay(16)
                    runCatching {
                        com.chris.m3usuite.core.debug.GlobalDebug.logTree("focusReq:TvFocusRow:first")
                        firstFocus.requestFocus()
                    }
                    // Arm enter only after attempting focus
                    enterEnabled.value = true
                    com.chris.m3usuite.core.debug.GlobalDebug.logTree("enter:Set:TvFocusRow")
                }
            }
    }

    // Debounce focus requests while scrolling; apply the last requested index once idle
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { moving ->
                if (!moving) {
                    val idx = latestFocusIdx.value
                    if (idx != null) {
                        latestFocusIdx.value = null
                        pendingScrollIndex.value = idx
                    }
                }
            }
    }

    val rowModifier = if (tv) modifier.focusGroup() else modifier

    LazyRow(
        state = listState,
        modifier = rowModifier,
        horizontalArrangement = horizontalArrangement,
        contentPadding = contentPadding
    ) {
        itemsIndexed(items, key = { _, v -> key(v) }) { idx, v ->
            val m = if (tv) {
                Modifier
                    .focusable()
                    .onFocusEvent { st ->
                        if (st.isFocused) {
                            if (skipFirstCenter.value && idx == 0) {
                                skipFirstCenter.value = false
                            } else {
                                if (listState.isScrollInProgress) {
                                    latestFocusIdx.value = idx
                                } else {
                                    pendingScrollIndex.value = idx
                                }
                            }
                        }
                    }
            } else Modifier
            val withReq = if (tv && idx == 0) {
                // mark first item as attached
                androidx.compose.runtime.SideEffect { firstAttached.value = true }
                m.focusRequester(firstFocus)
            } else m
            item(idx, v, withReq)
        }
    }
}

@Composable
fun TvFocusRow(
    stateKey: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    itemSpacing: Dp = 12.dp,
    itemCount: Int,
    itemKey: ((Int) -> Any)? = null,
    itemContent: @Composable (index: Int) -> Unit
) {
    val isTv = com.chris.m3usuite.ui.skin.isTvDevice(LocalContext.current)
    val listState = com.chris.m3usuite.ui.state.rememberRouteListState(stateKey)
    val rowFocus = com.chris.m3usuite.ui.state.rememberRowFocus(stateKey)
    val restoredOnce = rememberSaveable(key = "rowRestore:$stateKey") { mutableStateOf(false) }

    LaunchedEffect(stateKey, itemCount) {
        val idx = rowFocus.value.index
        if (!restoredOnce.value && (idx in 0 until itemCount)) {
            if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                runCatching { listState.scrollToItem(idx) }
            }
            restoredOnce.value = true
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier.then(if (isTv) Modifier.focusGroup() else Modifier),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        items(count = itemCount, key = itemKey) { idx ->
            val base = com.chris.m3usuite.ui.skin.run {
                val setRow = com.chris.m3usuite.ui.home.LocalChromeRowFocusSetter.current
                Modifier.tvFocusableItem(stateKey, idx, onFocused = { setRow(stateKey) })
            }
            val visual = if (isTv) com.chris.m3usuite.ui.skin.run { Modifier.tvFocusFrame() } else Modifier
            Box(base.then(visual)) { itemContent(idx) }
        }
    }
}
