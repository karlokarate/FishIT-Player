package com.chris.m3usuite.ui.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow

/**
 * ScrollStateRegistry – route/key-scoped list/grid states with a global in-memory cache.
 * This preserves positions even when Navigation pops the backstack entry entirely.
 */
private object ScrollStateCache {
    val list = mutableMapOf<String, Pair<Int, Int>>()
    val grid = mutableMapOf<String, Pair<Int, Int>>()
}

private val listSaver: Saver<LazyListState, Pair<Int, Int>> =
    Saver(
        save = { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset },
        restore = { (idx, off) -> LazyListState(firstVisibleItemIndex = idx, firstVisibleItemScrollOffset = off) }
    )

private val gridSaver: Saver<LazyGridState, Pair<Int, Int>> =
    Saver(
        save = { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset },
        restore = { (idx, off) -> LazyGridState(firstVisibleItemIndex = idx, firstVisibleItemScrollOffset = off) }
    )

@Composable
fun rememberRouteListState(routeKey: String): LazyListState {
    val cached = ScrollStateCache.list[routeKey]
    val state = if (cached != null)
        rememberSaveable(key = routeKey, saver = listSaver) { LazyListState(cached.first, cached.second) }
    else
        rememberSaveable(key = routeKey, saver = listSaver) { LazyListState() }

    LaunchedEffect(routeKey, state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collectLatest { pair -> ScrollStateCache.list[routeKey] = pair }
    }
    return state
}

@Composable
fun rememberRouteGridState(routeKey: String): LazyGridState {
    val cached = ScrollStateCache.grid[routeKey]
    val state = if (cached != null)
        rememberSaveable(key = routeKey, saver = gridSaver) { LazyGridState(cached.first, cached.second) }
    else
        rememberSaveable(key = routeKey, saver = gridSaver) { LazyGridState() }

    LaunchedEffect(routeKey, state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collectLatest { pair -> ScrollStateCache.grid[routeKey] = pair }
    }
    return state
}
