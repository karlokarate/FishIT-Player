package com.chris.m3usuite.core.logging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Unified logging facade for runtime logging.
 * - Single master switch + per-category switches
 * - Bounded in-memory ring buffer + live stream
 * - Safe: never throws, drops oldest on backpressure
 */
object AppLog {
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }

    private const val MAX_BUFFER = 1_000

    data class Entry(
        val timestamp: Long = System.currentTimeMillis(),
        val category: String,
        val level: Level,
        val message: String,
        val extras: Map<String, String> = emptyMap(),
    )

    private val masterEnabled = AtomicBoolean(false)
    private val categoryEnabled: AtomicReference<Map<String, Boolean>> = AtomicReference(emptyMap())
    private val buffer = ArrayDeque<Entry>(MAX_BUFFER)
    private val _history = MutableStateFlow<List<Entry>>(emptyList())
    private val _events =
        MutableSharedFlow<Entry>(
            replay = 0,
            extraBufferCapacity = 32,
        )
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    val history: StateFlow<List<Entry>> = _history.asStateFlow()
    val events: SharedFlow<Entry> = _events.asSharedFlow()

    internal fun resetForTest() {
        masterEnabled.set(false)
        categoryEnabled.set(emptyMap())
        clearBuffer()
    }

    fun setMasterEnabled(enabled: Boolean) {
        masterEnabled.set(enabled)
        if (!enabled) {
            clearBuffer()
        }
    }

    fun setCategoryEnabled(category: String, enabled: Boolean) {
        val current = categoryEnabled.get()
        val updated = ConcurrentHashMap(current)
        updated[category.lowercase()] = enabled
        categoryEnabled.set(updated)
    }

    fun setCategoriesEnabled(categories: Set<String>) {
        val updated = categories.associate { it.lowercase() to true }
        categoryEnabled.set(updated)
    }

    fun log(
        category: String,
        level: Level = Level.INFO,
        message: String,
        extras: Map<String, String> = emptyMap(),
        bypassMaster: Boolean = false,
    ) {
        val catKey = category.lowercase()
        val categories = categoryEnabled.get()
        if (!bypassMaster && !masterEnabled.get()) return
        if (categories.isNotEmpty() && categories[catKey] != true) return

        val safeExtras = redactExtras(extras)
        val safeMessage = redactMessage(message)

        val entry =
            Entry(
                category = catKey,
                level = level,
                message = safeMessage,
                extras = safeExtras,
            )

        scope.launch {
            try {
                // bounded buffer: drop oldest
                synchronized(buffer) {
                    if (buffer.size >= MAX_BUFFER) buffer.removeFirstOrNull()
                    buffer.addLast(entry)
                    _history.value = buffer.toList()
                }
                _events.tryEmit(entry)
            } catch (_: Throwable) {
                // swallow
            }
        }
    }

    private fun redactExtras(raw: Map<String, String>): Map<String, String> =
        raw
            .filterKeys {
                !it.contains("token", true) &&
                    !it.contains("password", true) &&
                    !it.contains("secret", true) &&
                    !it.contains("auth", true) &&
                    !it.contains("cookie", true)
            }.mapValues { (_, v) -> redactValue(v) }

    private fun redactMessage(message: String): String = redactValue(message)

    private fun redactValue(value: String): String {
        val patterns =
            listOf(
                Regex("(?i)bearer\\s+[A-Za-z0-9._-]+"),
                Regex("(?i)(api[_-]?key|token|auth)=([A-Za-z0-9._-]+)"),
                Regex("(?i)(session|sid|cookie)=([A-Za-z0-9+/=_-]+)"),
            )
        var result = value
        patterns.forEach { regex ->
            result =
                regex.replace(result) { matchResult ->
                    val grp = matchResult.groups[1]?.value ?: ""
                    val prefix = if (grp.isNotBlank()) "$grp=" else ""
                    "$prefix<redacted>"
                }
        }
        return result
    }

    private fun clearBuffer() {
        synchronized(buffer) {
            buffer.clear()
            _history.value = emptyList()
        }
    }
}
