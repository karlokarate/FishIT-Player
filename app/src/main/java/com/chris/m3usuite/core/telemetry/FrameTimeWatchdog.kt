package com.chris.m3usuite.core.telemetry

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Misst grob die Frame-Dauer (Choreographer) und meldet "Jank" bei > thresholdMs.
 * Zusätzlich ein sehr einfacher ANR-Watchdog, der Mainthread-Unresponsiveness meldet.
 */
object FrameTimeWatchdog {
    fun install(
        thresholdMs: Long = 32L, // 60 Hz Jank-Schwelle
        anrTimeoutMs: Long = 5000L, // ANR-Warnschwelle
        warmupMs: Long = 10_000L, // Startup-Warmup: keine ANR-Warnung in den ersten 10s
    ): Closeable {
        val running = AtomicBoolean(true)

        // Jank-Tracker
        val main = Handler(Looper.getMainLooper())
        val choreographer = Choreographer.getInstance()
        var lastNanos = 0L
        val frameCallback =
            object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (!running.get()) return
                    if (lastNanos != 0L) {
                        val deltaMs = (frameTimeNanos - lastNanos) / 1_000_000.0
                        if (deltaMs > thresholdMs) {
                            Telemetry.event(
                                "Frame.Jank",
                                mapOf("deltaMs" to deltaMs, "thresholdMs" to thresholdMs),
                            )
                        }
                    }
                    lastNanos = frameTimeNanos
                    choreographer.postFrameCallback(this)
                }
            }
        main.post { choreographer.postFrameCallback(frameCallback) }

        // ANR-Tracker: prüft, ob der Mainthread innerhalb von anrTimeoutMs tickt.
        val lastBeatMs = AtomicLong(SystemClock.uptimeMillis())
        val anrStrikes = AtomicInteger(0)
        val startMs = System.currentTimeMillis()
        val lastWarnMs = AtomicLong(0L)
        val pinger =
            object : Runnable {
                override fun run() {
                    if (!running.get()) return
                    // Post a heartbeat on the main thread; when processed it updates lastBeatMs
                    main.post { lastBeatMs.set(SystemClock.uptimeMillis()) }
                    main.postDelayed(this, anrTimeoutMs / 2)
                }
            }
        main.post(pinger)

        // ANR-Watchdog-Thread
        val watchdog =
            Thread({
                while (running.get()) {
                    try {
                        Thread.sleep(anrTimeoutMs)
                    } catch (_: InterruptedException) {
                        break
                    }
                    if (!running.get()) break
                    val now = SystemClock.uptimeMillis()
                    val sinceBeat = now - lastBeatMs.get()
                    if (sinceBeat >= anrTimeoutMs) {
                        // Only warn after warmup and debounce; then rate-limit to 60s
                        if (System.currentTimeMillis() - startMs >= warmupMs) {
                            val strikesNow = anrStrikes.incrementAndGet()
                            val elapsedSinceWarn = SystemClock.uptimeMillis() - lastWarnMs.get()
                            if (strikesNow >= 2 && elapsedSinceWarn >= 60_000L) {
                                lastWarnMs.set(now)
                                Telemetry.event(
                                    "ANR.Warning",
                                    mapOf("timeoutMs" to anrTimeoutMs, "strikes" to strikesNow),
                                )
                            }
                        }
                    } else {
                        anrStrikes.set(0)
                    }
                }
            }, "ANR-Watchdog").apply { isDaemon = true }
        watchdog.start()

        return Closeable {
            running.set(false)
            main.removeCallbacksAndMessages(null)
            watchdog.interrupt()
        }
    }
}
