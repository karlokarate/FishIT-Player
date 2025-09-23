package com.chris.m3usuite.core.telemetry

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Leichte Telemetrie mit erweiterbaren Sinks (Logcat, Crashlytics via Reflection).
 * - Telemetry.event(name, attrs)
 * - Telemetry.error(name, throwable, attrs)
 * - Telemetry.begin(name, attrs).close()
 * - Telemetry.trace(name) { ... }
 */
object Telemetry {

    interface Sink {
        fun event(name: String, attrs: Map<String, Any?> = emptyMap())
        fun error(name: String, t: Throwable, attrs: Map<String, Any?> = emptyMap())
    }

    private val sinks = CopyOnWriteArrayList<Sink>()

    fun register(sink: Sink) {
        sinks += sink
    }

    fun clear() {
        sinks.clear()
    }

    fun registerDefault(context: Context) {
        // Immer Logcat
        register(LogcatSink())

        // Crashlytics optional via Reflection (keine harte Abhängigkeit)
        runCatching { register(CrashlyticsSink()) }
    }

    fun event(name: String, attrs: Map<String, Any?> = emptyMap()) {
        sinks.forEach {
            runCatching { it.event(name, attrs) }.onFailure { Log.w("Telemetry", "event sink failed", it) }
        }
    }

    fun error(name: String, t: Throwable, attrs: Map<String, Any?> = emptyMap()) {
        sinks.forEach {
            runCatching { it.error(name, t, attrs) }.onFailure { Log.w("Telemetry", "error sink failed", it) }
        }
    }

    fun begin(name: String, attrs: Map<String, Any?> = emptyMap()): Closeable {
        val start = SystemClock.elapsedRealtime()
        return Closeable {
            val dur = SystemClock.elapsedRealtime() - start
            event("$name.end", attrs + mapOf("durationMs" to dur))
        }
    }

    inline fun <T> trace(name: String, attrs: Map<String, Any?> = emptyMap(), block: () -> T): T {
        val c = begin(name, attrs)
        return try { block() } finally { c.close() }
    }

    // --- Sinks ---

    private class LogcatSink : Sink {
        override fun event(name: String, attrs: Map<String, Any?>) {
            Log.d("Telemetry", "event=$name attrs=${attrs.toLogLine()}")
        }
        override fun error(name: String, t: Throwable, attrs: Map<String, Any?>) {
            Log.e("Telemetry", "error=$name attrs=${attrs.toLogLine()}", t)
        }
    }

    /**
     * Firebase Crashlytics via Reflection (keine Build-Dep zwingend).
     */
    private class CrashlyticsSink : Sink {
        private val clazz = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
        private val instance = clazz.getMethod("getInstance").invoke(null)
        private val logMethod = clazz.getMethod("log", String::class.java)
        private val recordMethod = clazz.getMethod("recordException", Throwable::class.java)

        override fun event(name: String, attrs: Map<String, Any?>) {
            val line = "event=$name ${attrs.toLogLine()}"
            logMethod.invoke(instance, line)
        }

        override fun error(name: String, t: Throwable, attrs: Map<String, Any?>) {
            logMethod.invoke(instance, "error=$name ${attrs.toLogLine()}")
            recordMethod.invoke(instance, t)
        }
    }

    private fun Map<String, Any?>.toLogLine(): String =
        entries.joinToString(" ") { (k, v) -> "$k=${v.toString().take(200)}" }
}
