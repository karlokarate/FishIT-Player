package com.chris.m3usuite.ui.telemetry

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.chris.m3usuite.core.telemetry.Telemetry
import kotlinx.coroutines.flow.collectLatest

/**
 * Hängt sich an LazyPagingItems an und protokolliert Refresh/Append/Prepend-Dauern & Fehler.
 */
@Composable
fun AttachPagingTelemetry(
    tag: String,
    items: LazyPagingItems<*>,
) {
    val refreshStart = remember { TimeBox() }
    val appendStart = remember { TimeBox() }
    val prependStart = remember { TimeBox() }

    LaunchedEffect(items) {
        snapshotFlow { items.loadState }
            .collectLatest { state ->
                // Refresh
                when (val s = state.refresh) {
                    is LoadState.Loading -> refreshStart.start()
                    is LoadState.NotLoading -> {
                        val ms = refreshStart.stop()
                        if (ms != null) {
                            Telemetry.event(
                                "Paging.Refresh.Done",
                                mapOf("tag" to tag, "durationMs" to ms, "items" to items.itemCount),
                            )
                        }
                    }
                    is LoadState.Error -> {
                        val ms = refreshStart.stop()
                        Telemetry.error(
                            "Paging.Refresh.Error",
                            s.error,
                            mapOf("tag" to tag, "durationMs" to (ms ?: -1), "items" to items.itemCount),
                        )
                    }
                }
                // Append
                when (val s = state.append) {
                    is LoadState.Loading -> appendStart.start()
                    is LoadState.NotLoading -> {
                        val ms = appendStart.stop()
                        if (ms != null) {
                            Telemetry.event(
                                "Paging.Append.Done",
                                mapOf("tag" to tag, "durationMs" to ms, "items" to items.itemCount),
                            )
                        }
                    }
                    is LoadState.Error -> {
                        val ms = appendStart.stop()
                        Telemetry.error(
                            "Paging.Append.Error",
                            s.error,
                            mapOf("tag" to tag, "durationMs" to (ms ?: -1), "items" to items.itemCount),
                        )
                    }
                }
                // Prepend (falls verwendet)
                when (val s = state.prepend) {
                    is LoadState.Loading -> prependStart.start()
                    is LoadState.NotLoading -> {
                        val ms = prependStart.stop()
                        if (ms != null) {
                            Telemetry.event(
                                "Paging.Prepend.Done",
                                mapOf("tag" to tag, "durationMs" to ms, "items" to items.itemCount),
                            )
                        }
                    }
                    is LoadState.Error -> {
                        val ms = prependStart.stop()
                        Telemetry.error(
                            "Paging.Prepend.Error",
                            s.error,
                            mapOf("tag" to tag, "durationMs" to (ms ?: -1), "items" to items.itemCount),
                        )
                    }
                }
            }
    }
}

private class TimeBox {
    private var start: Long = -1

    fun start() {
        start = SystemClock.elapsedRealtime()
    }

    fun stop(): Long? = if (start > 0) (SystemClock.elapsedRealtime() - start).also { start = -1 } else null
}
