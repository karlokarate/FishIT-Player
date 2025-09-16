package com.chris.m3usuite.core.http

import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicReference

object RequestHeadersProvider {
    private val atomic: AtomicReference<Map<String, String>> = AtomicReference(emptyMap())
    private val _state = MutableStateFlow<Map<String, String>>(emptyMap())
    val state: StateFlow<Map<String, String>> get() = _state

    /**
     * Start observing SettingsStore to keep an in-memory snapshot for headers.
     * Safe to call multiple times; subsequent calls will simply update the snapshot.
     */
    fun start(scope: CoroutineScope, store: SettingsStore) {
        scope.launch {
            combine(store.userAgent, store.referer, store.extraHeadersJson) { ua, ref, extrasJson ->
                val base = buildMap<String, String> {
                    val fallbackUa = com.chris.m3usuite.BuildConfig.DEFAULT_UA.ifBlank { "IBOPlayer/1.4 (Android)" }
                    if (ua.isNotBlank()) put("User-Agent", ua) else if (fallbackUa.isNotBlank()) put("User-Agent", fallbackUa)
                    if (ref.isNotBlank()) put("Referer", ref)
                }
                merge(base, parseExtraHeaders(extrasJson))
            }.collect { snap ->
                atomic.set(snap)
                _state.value = snap
            }
        }
    }

    /**
     * Lifecycle-friendly variant: caller wraps with repeatOnLifecycle and calls this suspending collector.
     */
    suspend fun collect(store: SettingsStore) {
        combine(store.userAgent, store.referer, store.extraHeadersJson) { ua, ref, extrasJson ->
            val base = buildMap<String, String> {
                val fallbackUa = "IBOPlayer/1.4 (Android)"
                if (ua.isNotBlank()) put("User-Agent", ua) else if (fallbackUa.isNotBlank()) put("User-Agent", fallbackUa)
                if (ref.isNotBlank()) put("Referer", ref)
            }
            merge(base, parseExtraHeaders(extrasJson))
        }.collect { snap ->
            atomic.set(snap)
            _state.value = snap
        }
    }

    /** Non-blocking access used from OkHttp interceptors. */
    fun snapshot(): Map<String, String> = atomic.get()

    /** Convenience: compute once (blocking) and seed the snapshot, for callers without a scope. */
    fun ensureSeededBlocking(store: SettingsStore): Map<String, String> {
        val cur = atomic.get()
        if (cur.isNotEmpty()) return cur
        val seeded = defaultHeadersBlocking(store)
        atomic.set(seeded)
        _state.value = seeded
        return seeded
    }

    suspend fun defaultHeaders(store: SettingsStore): Map<String, String> {
        val storedUa = store.userAgent.first()
        val fallbackUa = com.chris.m3usuite.BuildConfig.DEFAULT_UA.ifBlank { "IBOPlayer/1.4 (Android)" }
        val ua = if (storedUa.isNotBlank()) storedUa else fallbackUa
        val ref = store.referer.first()
        val extrasJson = store.extraHeadersJson.first()
        val base = buildMap {
            if (ua.isNotBlank()) put("User-Agent", ua)
            if (ref.isNotBlank()) put("Referer", ref)
        }
        return merge(base, parseExtraHeaders(extrasJson))
    }

    fun defaultHeadersBlocking(store: SettingsStore): Map<String, String> =
        runBlocking { defaultHeaders(store) }

    fun parseExtraHeaders(json: String): Map<String, String> = runCatching {
        val node = Json.parseToJsonElement(json).jsonObject
        node.mapValues { it.value.jsonPrimitive.content }
    }.getOrElse { emptyMap() }

    fun merge(a: Map<String, String>, b: Map<String, String>): Map<String, String> =
        (a.toMutableMap().apply { putAll(b) }).toMap()
}
