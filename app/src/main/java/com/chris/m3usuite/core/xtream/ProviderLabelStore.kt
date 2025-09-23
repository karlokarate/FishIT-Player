package com.chris.m3usuite.core.xtream

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import com.chris.m3usuite.core.util.CategoryNormalizer

/**
 * Derives friendly provider display labels from API data (category names, titles).
 * Stores a persistent mapping providerKey -> bestLabel in SharedPreferences.
 */
class ProviderLabelStore private constructor(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("provider_labels", Context.MODE_PRIVATE)

    fun labelFor(key: String?): String {
        val k = key?.trim().orEmpty()
        if (k.isEmpty()) return "Unbekannt"
        val map = load()
        val saved = map[k]
        // Default to canonical display label derived from the normalized key
        return saved ?: CategoryNormalizer.displayLabel(k)
    }

    /** Update mapping if the candidate seems better (longer, contains '+', or nothing saved yet). */
    fun learn(key: String?, candidateRaw: String?) {
        val k = key?.trim()?.lowercase()?.replace(' ', '_') ?: return
        if (k.isEmpty()) return
        // Derive a stable display label from the candidate by normalizing to key and mapping to canonical label
        val candKey = CategoryNormalizer.normalizeKey(candidateRaw)
        val cand = CategoryNormalizer.displayLabel(candKey)
        if (cand.isEmpty()) return
        val map = load().toMutableMap()
        val current = map[k]
        val better = when {
            current == null -> true
            cand.length > current.length -> true
            !current.contains("+") && cand.contains("+") -> true
            else -> false
        }
        if (better) {
            map[k] = cand
            save(map)
        }
    }

    private fun cleanup(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val trimmed = s.trim()
        // Collapse all whitespace (including newlines) without regex escapes
        val sb = StringBuilder()
        var inWs = false
        for (ch in trimmed) {
            if (ch.isWhitespace()) {
                if (!inWs) {
                    sb.append(' ')
                    inWs = true
                }
            } else {
                sb.append(ch)
                inWs = false
            }
        }
        return sb.toString()
    }

    private fun prettifyFallback(k: String): String = CategoryNormalizer.displayLabel(k)

    private fun load(): Map<String, String> {
        val raw = prefs.getString("map", null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val it = obj.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    put(key, obj.getString(key))
                }
            }
        }.getOrElse { emptyMap() }
    }

    private fun save(map: Map<String, String>) {
        val obj = JSONObject()
        for ((k, v) in map) obj.put(k, v)
        prefs.edit { putString("map", obj.toString()) }
    }

    companion object {
        @Volatile private var inst: ProviderLabelStore? = null
        fun get(context: Context): ProviderLabelStore {
            val cur = inst
            if (cur != null) return cur
            val created = ProviderLabelStore(context)
            inst = created
            return created
        }
    }
}
