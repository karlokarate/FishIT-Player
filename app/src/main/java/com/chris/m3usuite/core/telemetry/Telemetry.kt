package com.chris.m3usuite.core.telemetry

import android.content.Context
import android.os.SystemClock
import com.chris.m3usuite.core.logging.AppLog
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
        fun event(
            name: String,
            attrs: Map<String, Any?> = emptyMap(),
        )

        fun error(
            name: String,
            t: Throwable,
            attrs: Map<String, Any?> = emptyMap(),
        )
    }

    private val sinks = CopyOnWriteArrayList<Sink>()
    private val appLogSink = AppLogSink()
    private var crashlyticsSink: CrashlyticsSink? = null

    fun register(sink: Sink) {
        if (!sinks.contains(sink)) {
            sinks += sink
        }
    }

    fun clear() {
        sinks.clear()
    }

    @Suppress("UNUSED_PARAMETER")
    fun registerDefault(context: Context) {
        // AppLog sink as primary (LogViewer surface)
        register(appLogSink)
    }

    fun setExternalEnabled(enabled: Boolean) {
        if (enabled) {
            if (crashlyticsSink == null) {
                crashlyticsSink = runCatching { CrashlyticsSink() }.onFailure { crashlyticsSink = null }.getOrNull()
            }
            crashlyticsSink?.let { register(it) }
        } else {
            crashlyticsSink?.let { sinks.remove(it) }
            crashlyticsSink = null
        }
    }

    fun event(
        name: String,
        attrs: Map<String, Any?> = emptyMap(),
    ) {
        sinks.forEach {
            runCatching { it.event(name, attrs) }
                .onFailure { err ->
                    if (it !is AppLogSink) {
                        AppLog.log(
                            category = "diagnostics",
                            level = AppLog.Level.WARN,
                            message = "telemetry sink failed on event=$name",
                            extras =
                                mapOf(
                                    "sink" to it.javaClass.simpleName,
                                    "error" to (err.message ?: "unknown"),
                                ),
                        )
                    }
                }
        }
    }

    fun error(
        name: String,
        t: Throwable,
        attrs: Map<String, Any?> = emptyMap(),
    ) {
        sinks.forEach {
            runCatching { it.error(name, t, attrs) }
                .onFailure { err ->
                    if (it !is AppLogSink) {
                        AppLog.log(
                            category = "diagnostics",
                            level = AppLog.Level.WARN,
                            message = "telemetry sink failed on error=$name",
                            extras =
                                mapOf(
                                    "sink" to it.javaClass.simpleName,
                                    "error" to (err.message ?: "unknown"),
                                ),
                        )
                    }
                }
        }
    }

    fun begin(
        name: String,
        attrs: Map<String, Any?> = emptyMap(),
    ): Closeable {
        val start = SystemClock.elapsedRealtime()
        return Closeable {
            val dur = SystemClock.elapsedRealtime() - start
            event("$name.end", attrs + mapOf("durationMs" to dur))
        }
    }

    inline fun <T> trace(
        name: String,
        attrs: Map<String, Any?> = emptyMap(),
        block: () -> T,
    ): T {
        val c = begin(name, attrs)
        return try {
            block()
        } finally {
            c.close()
        }
    }

    // --- Sinks ---

    private class AppLogSink : Sink {
        override fun event(
            name: String,
            attrs: Map<String, Any?>,
        ) {
            AppLog.log(
                category = "diagnostics",
                level = AppLog.Level.DEBUG,
                message = "telemetry event: $name",
                extras = attrs.toExtras(),
                bypassMaster = true,
            )
        }

        override fun error(
            name: String,
            t: Throwable,
            attrs: Map<String, Any?>,
        ) {
            AppLog.log(
                category = "diagnostics",
                level = AppLog.Level.ERROR,
                message = "telemetry error: $name",
                extras = attrs.toExtras() + ("exception" to (t.message ?: t.javaClass.simpleName)),
                bypassMaster = true,
            )
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

        override fun event(
            name: String,
            attrs: Map<String, Any?>,
        ) {
            val line = "event=$name ${attrs.toLogLine()}"
            logMethod.invoke(instance, line)
        }

        override fun error(
            name: String,
            t: Throwable,
            attrs: Map<String, Any?>,
        ) {
            logMethod.invoke(instance, "error=$name ${attrs.toLogLine()}")
            recordMethod.invoke(instance, t)
        }
    }

    private fun Map<String, Any?>.toLogLine(): String = entries.joinToString(" ") { (k, v) -> "$k=${v.toString().take(200)}" }

    private fun Map<String, Any?>.toExtras(): Map<String, String> = entries.associate { (k, v) -> k to (v?.toString()?.take(200) ?: "") }
}
