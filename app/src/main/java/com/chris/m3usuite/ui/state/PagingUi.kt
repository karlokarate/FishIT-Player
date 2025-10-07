package com.chris.m3usuite.ui.state

import androidx.compose.runtime.snapshotFlow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combine multiple LazyPagingItems into a Flow<Int> of total item count.
 * Throws when any refresh load state is Error so collectors can render ErrorState via catch.
 */
fun combinedPagingCountFlow(vararg items: LazyPagingItems<*>): Flow<Int> {
    require(items.isNotEmpty())
    val states = items.map { snapshotFlow { it.loadState.refresh } }
    val counts = items.map { snapshotFlow { it.itemCount } }
    return combine(*(states + counts).toTypedArray()) { arr ->
        val half = arr.size / 2
        val loadStates = arr.take(half).map { it as LoadState }
        val totals = arr.drop(half).map { it as Int }
        loadStates.forEach { st -> if (st is LoadState.Error) throw st.error }
        totals.sum()
    }
}

