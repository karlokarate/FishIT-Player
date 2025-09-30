package com.chris.m3usuite.ui.tv

import androidx.compose.foundation.lazy.LazyListState

/**
 * Centers the given item index in the viewport when possible. Falls back to
 * a regular scroll if metrics are not yet available.
 */
suspend fun LazyListState.centerItemSafely(index: Int) {
    if (index < 0) return
    val info = layoutInfo
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    val visible = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (visible != null) {
        // If already fully visible, do not re-center to avoid large jumps.
        val left = visible.offset
        val right = visible.offset + visible.size
        if (left >= 0 && right <= viewport) {
            com.chris.m3usuite.core.debug.GlobalDebug.logRowScrollPlan(index, 0, reason = "skip:fullyVisible")
            return
        }
        // Otherwise, minimally adjust to bring the item fully into view.
        val desired = when {
            left < 0 -> 0
            right > viewport -> (viewport - visible.size).coerceAtLeast(0)
            else -> left
        }
        com.chris.m3usuite.core.debug.GlobalDebug.logRowScrollPlan(index, desired, reason = "adjust:clip")
        animateScrollToItem(index, desired)
    } else {
        // Item not visible yet – still attempt to place it near center.
        val approxOffset = (viewport / 3).coerceAtLeast(0)
        com.chris.m3usuite.core.debug.GlobalDebug.logRowScrollPlan(index, approxOffset, reason = "approx:notVisible")
        scrollToItem(index, approxOffset)
    }
}
