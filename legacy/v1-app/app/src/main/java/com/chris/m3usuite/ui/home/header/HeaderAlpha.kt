package com.chris.m3usuite.ui.home.header

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import kotlin.math.min

/** Maps a LazyListState scroll to 0..1 for header scrim opacity. */
@Composable
fun rememberHeaderAlpha(
    listState: LazyListState,
    thresholdPx: Int = 280,
): Float {
    val alphaState =
        remember(listState) {
            derivedStateOf {
                val index = listState.firstVisibleItemIndex
                val off = listState.firstVisibleItemScrollOffset
                if (index > 0) 1f else min(1f, off.toFloat() / thresholdPx)
            }
        }
    return alphaState.value
}
