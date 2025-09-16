package com.chris.m3usuite.data.repo

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.chris.m3usuite.core.epg.XmlTv
import com.chris.m3usuite.data.obx.ObxEpgNowNext
import com.chris.m3usuite.data.obx.ObxLive
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.core.xtream.EndpointPortStore
import com.chris.m3usuite.core.xtream.ProviderCapabilityStore
import com.chris.m3usuite.core.xtream.XtShortEPGProgramme
import com.chris.m3usuite.core.xtream.XtreamClient
import com.chris.m3usuite.prefs.SettingsStore
// Room removed; OBX-only
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient

/**
 * Lightweight EPG helper with short TTL cache per streamId.
 * Focus: Now/Next (get_short_epg) performance and reliability.
 */
class EpgRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val ttlMillis: Long = 90_000L,
    private val emptyTtlMillis: Long = 10_000L
) {
    private val TAG = "EPGRepo"
    private data class Cache(val atElapsedMs: Long, val data: List<XtShortEPGProgramme>)
    private val cache = LinkedHashMap<Int, Cache>(128, 0.75f, true)
    private val emptyCache = LinkedHashMap<Int, Long>(128, 0.75f, true)
    private val maxEntries = 2000
    private fun trimIfNeeded() {
        while (cache.size > maxEntries) {
            val it = cache.entries.iterator()
            if (it.hasNext()) { it.next(); it.remove() } else break
        }
        while (emptyCache.size > maxEntries) {
            val it = emptyCache.entries.iterator()
            if (it.hasNext()) { it.next(); it.remove() } else break
        }
    }
    private val lock = Mutex()
    private fun secStrToMs(s: String?): Long? = s?.toLongOrNull()?.let { it * 1000 }

    private suspend fun buildClient(): XtreamClient? = withContext(Dispatchers.IO) {
        val host = settings.xtHost.first()
        val user = settings.xtUser.first()
        val pass = settings.xtPass.first()
        if (host.isBlank() || user.isBlank() || pass.isBlank()) return@withContext null
        val port = settings.xtPort.first()
        val scheme = if (port == 443) "https" else "http"
        val http = com.chris.m3usuite.core.http.HttpClientFactory.create(context, settings)
        val caps = ProviderCapabilityStore(context)
        val portStore = EndpointPortStore(context)
        val client = XtreamClient(http)
        client.initialize(
            scheme = scheme,
            host = host,
            username = user,
            password = pass,
            basePath = null,
            store = caps,
            portStore = portStore,
            portOverride = port
        )
        client
    }

    suspend fun nowNext(streamId: Int, limit: Int = 2): List<XtShortEPGProgramme> = withContext(Dispatchers.IO) {
        // Fast path: valid cache
        val cached: List<XtShortEPGProgramme>? = lock.withLock {
            val eAt = emptyCache[streamId]
            if (eAt != null && (SystemClock.elapsedRealtime() - eAt) < emptyTtlMillis) {
                Log.d(TAG, "sid=$streamId cache=empty within ${emptyTtlMillis}ms")
                return@withLock emptyList<XtShortEPGProgramme>()
            }
            val c = cache[streamId]
            if (c != null && (SystemClock.elapsedRealtime() - c.atElapsedMs) < ttlMillis) {
                Log.d(TAG, "sid=$streamId cache=hit size=${c.data.size}")
                return@withLock c.data
            }
            null
        }
        if (cached != null) return@withContext cached
        val tag = "XtreamEPG"
        // Room removed; OBX-only

        // Check persistent cache first via tvg-id
        // Prefer OBX channel mapping; optionally check Room cache if enabled
        val obxLive = runCatching {
            ObxStore.get(context).boxFor(ObxLive::class.java).query(com.chris.m3usuite.data.obx.ObxLive_.streamId.equal(streamId.toLong())).build().findFirst()
        }.getOrNull()
        val chanId = obxLive?.epgChannelId
        // Skip Room persistent cache; rely on OBX + network

        // Helper: XMLTV fallback using tvg-id mapped from DB
        suspend fun fallbackXmlTv(): List<XtShortEPGProgramme> {
            val epgUrl = settings.epgUrl.first()
            if (epgUrl.isBlank()) return emptyList()
            val chan = chanId
            if (!chan.isNullOrBlank()) {
                val (now, next) = XmlTv.currentNext(context, settings, chan)
                if (now != null || next != null) {
                    val list = mutableListOf<XtShortEPGProgramme>()
                    if (now != null) list += XtShortEPGProgramme(title = now.title, start = (now.startMs/1000).toString(), end = (now.stopMs/1000).toString())
                    if (next != null) list += XtShortEPGProgramme(title = next.title, start = (next.startMs/1000).toString(), end = (next.stopMs/1000).toString())
                    Log.i(tag, "XMLTV fallback used for sid=$streamId channel=$chan count=${list.size}")
                    return list
                }
            }
            return emptyList()
        }

        // Try Xtream first if configured
        val client = buildClient()
        val xtreamRes: List<XtShortEPGProgramme> = if (client != null) {
            val raw = runCatching { client.fetchShortEpg(streamId, limit) }.getOrNull()
            val list = if (!raw.isNullOrBlank()) parseShortEpg(raw) else emptyList()
            if (list.isNotEmpty()) Log.d(TAG, "sid=$streamId source=xtream size=${list.size}")
            list
        } else emptyList()

        var final = xtreamRes.ifEmpty { fallbackXmlTv().also { if (it.isNotEmpty()) Log.d(TAG, "sid=$streamId source=xmltv size=${it.size}") } }
        // Soft fallback: if network yielded nothing but we have a stale OBX row, reuse it to avoid blank UI
        if (final.isEmpty() && !chanId.isNullOrBlank()) {
            val row = withContext(Dispatchers.IO) {
                val box = ObxStore.get(context).boxFor(ObxEpgNowNext::class.java)
                box.query(com.chris.m3usuite.data.obx.ObxEpgNowNext_.channelId.equal(chanId)).build().findFirst()
            }
            if (row != null) {
                val list = mutableListOf<XtShortEPGProgramme>()
                val nStart = row.nowStartMs
                val nEnd = row.nowEndMs
                val nTitle = row.nowTitle
                if (nTitle != null && nStart != null && nEnd != null) {
                    list += XtShortEPGProgramme(title = nTitle, start = (nStart / 1000).toString(), end = (nEnd / 1000).toString())
                }
                val xStart = row.nextStartMs
                val xEnd = row.nextEndMs
                val xTitle = row.nextTitle
                if (xTitle != null && xStart != null && xEnd != null) {
                    list += XtShortEPGProgramme(title = xTitle, start = (xStart / 1000).toString(), end = (xEnd / 1000).toString())
                }
                final = list
                Log.d(TAG, "sid=$streamId source=obx-stale ch=$chanId size=${final.size}")
            }
        }
        // Persist into caches if we have a channel id and actual content
        if (!chanId.isNullOrBlank() && final.isNotEmpty()) {
            val now = final.getOrNull(0)
            val next = final.getOrNull(1)
            val nowStart = secStrToMs(now?.start)
            val nowEnd = secStrToMs(now?.end)
            val nextStart = secStrToMs(next?.start)
            val nextEnd = secStrToMs(next?.end)
            // Also persist into ObjectBox for fast startup/offline
            runCatching {
                val box = ObxStore.get(context).boxFor(ObxEpgNowNext::class.java)
                val obx = ObxEpgNowNext(
                    channelId = chanId,
                    nowTitle = now?.title,
                    nowStartMs = nowStart,
                    nowEndMs = nowEnd,
                    nextTitle = next?.title,
                    nextStartMs = nextStart,
                    nextEndMs = nextEnd,
                    updatedAt = System.currentTimeMillis()
                )
                val existing = box.query(com.chris.m3usuite.data.obx.ObxEpgNowNext_.channelId.equal(chanId)).build().findFirst()
                if (existing != null) obx.id = existing.id
                box.put(obx)
            }
            Log.d(TAG, "sid=$streamId persist ch=$chanId now=${now?.title} next=${next?.title}")
        }
        // Cache hit bookkeeping: content uses normal TTL; empty uses short TTL
        if (final.isNotEmpty()) {
            lock.withLock { cache[streamId] = Cache(SystemClock.elapsedRealtime(), final); trimIfNeeded() }
        } else {
            lock.withLock { emptyCache[streamId] = SystemClock.elapsedRealtime(); trimIfNeeded() }
        }
        Log.d(TAG, "sid=$streamId result size=${final.size}")
        final.take(limit)
    }

    private fun parseShortEpg(jsonStr: String): List<XtShortEPGProgramme> = runCatching {
        val root = Json.parseToJsonElement(jsonStr)
        if (root is JsonArray) {
            root.jsonArray.mapNotNull { el ->
                val obj = el.jsonObject
                XtShortEPGProgramme(
                    title = obj["title"]?.jsonPrimitive?.contentOrNull,
                    start = obj["start"]?.jsonPrimitive?.contentOrNull,
                    end = obj["end"]?.jsonPrimitive?.contentOrNull,
                )
            }
        } else emptyList()
    }.getOrDefault(emptyList())
}
