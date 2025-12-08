package com.chris.m3usuite.ui.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * RememberHelpers – Composable helpers that read/write ScrollStateRegistry for scroll positions.
 */

private val listSaver: Saver<LazyListState, Pair<Int, Int>> =
    Saver(
        save = { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset },
        restore = { (idx, off) -> LazyListState(firstVisibleItemIndex = idx, firstVisibleItemScrollOffset = off) },
    )

private val gridSaver: Saver<LazyGridState, Pair<Int, Int>> =
    Saver(
        save = { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset },
        restore = { (idx, off) -> LazyGridState(firstVisibleItemIndex = idx, firstVisibleItemScrollOffset = off) },
    )

@Composable
fun rememberRouteListState(routeKey: String): LazyListState {
    val cached = ScrollStateRegistry.list[routeKey]
    val state =
        if (cached != null) {
            rememberSaveable("routeList", routeKey, saver = listSaver) { LazyListState(cached.first, cached.second) }
        } else {
            rememberSaveable("routeList", routeKey, saver = listSaver) { LazyListState() }
        }

    LaunchedEffect(routeKey, state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collectLatest { pair -> ScrollStateRegistry.list[routeKey] = pair }
    }
    return state
}

@Composable
fun rememberRouteGridState(routeKey: String): LazyGridState {
    val cached = ScrollStateRegistry.grid[routeKey]
    val state =
        if (cached != null) {
            rememberSaveable("routeGrid", routeKey, saver = gridSaver) { LazyGridState(cached.first, cached.second) }
        } else {
            rememberSaveable("routeGrid", routeKey, saver = gridSaver) { LazyGridState() }
        }

    LaunchedEffect(routeKey, state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collectLatest { pair -> ScrollStateRegistry.grid[routeKey] = pair }
    }
    return state
}
