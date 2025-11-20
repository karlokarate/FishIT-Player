package com.chris.m3usuite.metrics

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.ConcurrentHashMap

object JankReporter {
    private data class Counter(
        var frames: Long = 0,
        var janks: Long = 0,
        var lastLogMs: Long = 0,
    )

    private val counters = ConcurrentHashMap<String, Counter>()
    private const val WINDOW_MS = 5_000L

    @RequiresApi(Build.VERSION_CODES.N)
    fun record(
        route: String,
        isJank: Boolean,
        durationNs: Long,
    ) {
        val key = route.ifBlank { "unknown" }
        val now = SystemClock.uptimeMillis()
        val c = counters.computeIfAbsent(key) { Counter(lastLogMs = now) }
        c.frames++
        if (isJank) c.janks++
        if (now - c.lastLogMs >= WINDOW_MS) {
            val rate = if (c.frames > 0) 100.0 * c.janks / c.frames else 0.0
            if (com.chris.m3usuite.BuildConfig.DEBUG) {
                Log.d("JankStats", "route=$key window=${WINDOW_MS}ms frames=${c.frames} janks=${c.janks} rate=${"%.1f".format(rate)}%")
            }
            // reset sliding window
            c.frames = 0
            c.janks = 0
            c.lastLogMs = now
        }
    }
}
