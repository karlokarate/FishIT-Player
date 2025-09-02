package com.chris.m3usuite.core.http

import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object RequestHeadersProvider {
    suspend fun defaultHeaders(store: SettingsStore): Map<String, String> {
        val ua = store.userAgent.first().ifBlank { "IBOPlayer/1.4 (Android)" }
        val ref = store.referer.first()
        val extrasJson = store.extraHeadersJson.first()
        val base = buildMap {
            put("User-Agent", ua)
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
