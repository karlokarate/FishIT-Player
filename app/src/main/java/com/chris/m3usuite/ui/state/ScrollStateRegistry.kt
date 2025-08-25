package com.chris.m3usuite.ui.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Speichert pro Route/Key die Scrollposition – damit Rotation nicht nach oben springt.
 */
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
fun rememberRouteListState(routeKey: String): LazyListState =
    rememberSaveable(routeKey, saver = listSaver) { LazyListState() }

@Composable
fun rememberRouteGridState(routeKey: String): LazyGridState =
    rememberSaveable(routeKey, saver = gridSaver) { LazyGridState() }
