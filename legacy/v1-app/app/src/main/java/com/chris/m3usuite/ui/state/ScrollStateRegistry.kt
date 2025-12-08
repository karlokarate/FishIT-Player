package com.chris.m3usuite.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * ScrollStateRegistry – global in-memory registry for scroll and focus state.
 *
 * - List/Grid scroll positions are cached per route/key in maps (see RememberHelpers).
 * - Row focus memory stores the last focused index per route/key.
 */

data class RowFocus(
    val index: Int = 0,
)

internal object ScrollStateRegistry {
    // Scroll caches (LazyList/LazyGrid)
    val list: MutableMap<String, Pair<Int, Int>> = mutableMapOf()
    val grid: MutableMap<String, Pair<Int, Int>> = mutableMapOf()

    // Focus cache per row/key
    private val focus: MutableMap<String, RowFocus> = mutableMapOf()

    fun readRowFocus(key: String): RowFocus = focus[key] ?: RowFocus()

    fun writeRowFocus(
        key: String,
        index: Int,
    ) {
        focus[key] = RowFocus(index)
    }
}

fun readRowFocus(key: String): RowFocus = ScrollStateRegistry.readRowFocus(key)

fun writeRowFocus(
    key: String,
    index: Int,
) = ScrollStateRegistry.writeRowFocus(key, index)

private val rowFocusSaver: Saver<MutableState<RowFocus>, Int> =
    Saver(
        save = { it.value.index },
        restore = { idx -> mutableStateOf(RowFocus(index = idx)) },
    )

@Composable
fun rememberRowFocus(key: String): MutableState<RowFocus> {
    val initial = ScrollStateRegistry.readRowFocus(key)
    val state =
        rememberSaveable("rowFocus", key, saver = rowFocusSaver) {
            mutableStateOf(initial)
        }
    LaunchedEffect(key, state.value.index) {
        ScrollStateRegistry.writeRowFocus(key, state.value.index)
    }
    return state
}
