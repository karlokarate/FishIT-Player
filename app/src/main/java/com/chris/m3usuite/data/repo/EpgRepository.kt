package com.chris.m3usuite.data.repo

import android.content.Context
import android.os.SystemClock
import com.chris.m3usuite.core.logging.AppLog
import com.chris.m3usuite.core.epg.XmlTv
import com.chris.m3usuite.core.xtream.EndpointPortStore
import com.chris.m3usuite.core.xtream.ProviderCapabilityStore
import com.chris.m3usuite.core.xtream.XtShortEPGProgramme
import com.chris.m3usuite.core.xtream.XtreamClient
import com.chris.m3usuite.data.obx.ObxEpgNowNext
import com.chris.m3usuite.data.obx.ObxLive
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicReference

/**
 * Lightweight EPG helper with short TTL cache per streamId.
 * Focus: Now/Next (get_short_epg) performance and reliability.
 */
class EpgRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val ttlMillis: Long = 90_000L,
    private val emptyTtlMillis: Long = 10_000L,
) {
    private val TAG = "EPGRepo"
    private val clientRef = AtomicReference<XtreamClient?>(null)

    private data class Cache(
        val atElapsedMs: Long,
        val data: List<XtShortEPGProgramme>,
    )

    private val cache = LinkedHashMap<Int, Cache>(128, 0.75f, true)
    private val emptyCache = LinkedHashMap<Int, Long>(128, 0.75f, true)
    private val maxEntries = 2000

    private fun trimIfNeeded() {
        while (cache.size > maxEntries) {
            val it = cache.entries.iterator()
            if (!it.hasNext()) break
            it.next()
            it.remove()
        }
        while (emptyCache.size > maxEntries) {
            val it = emptyCache.entries.iterator()
            if (!it.hasNext()) break
            it.next()
            it.remove()
        }
    }

    private val lock = Mutex()

    private suspend fun fallbackXmlTvFor(channelId: String?): List<XtShortEPGProgramme> =
        withContext(Dispatchers.IO) {
            // Best-effort XMLTV fallback for a known channel id
            if (channelId.isNullOrBlank()) return@withContext emptyList()
            val (now, next) = XmlTv.currentNext(context, settings, channelId)
            val out = mutableListOf<XtShortEPGProgramme>()
            if (now !=
                null
            ) {
                out +=
                    XtShortEPGProgramme(title = now.title, start = (now.startMs / 1000).toString(), end = (now.stopMs / 1000).toString())
            }
            if (next !=
                null
            ) {
                out +=
                    XtShortEPGProgramme(title = next.title, start = (next.startMs / 1000).toString(), end = (next.stopMs / 1000).toString())
            }
            out
        }

    private suspend fun buildClient(): XtreamClient? =
        withContext(Dispatchers.IO) {
            val host = settings.xtHost.first()
            val user = settings.xtUser.first()
            val pass = settings.xtPass.first()
            if (host.isBlank() || user.isBlank() || pass.isBlank()) return@withContext null
            val port = settings.xtPort.first()
            val scheme = if (port == 443) "https" else "http"
            val http =
                com.chris.m3usuite.core.http.HttpClientFactory
                    .create(context, settings)
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
                portOverride = port,
            )
            client
        }

    suspend fun nowNext(
        streamId: Int,
        limit: Int = 2,
    ): List<XtShortEPGProgramme> =
        withContext(Dispatchers.IO) {
            // Fast path: valid cache
            val cached: List<XtShortEPGProgramme>? =
                lock.withLock {
                    val eAt = emptyCache[streamId]
                    if (eAt != null && (SystemClock.elapsedRealtime() - eAt) < emptyTtlMillis) {
                        AppLog.log("epg", AppLog.Level.DEBUG, "sid=$streamId cache=empty within ${emptyTtlMillis}ms")
                        return@withLock emptyList<XtShortEPGProgramme>()
                    }
                    val hit = cache[streamId]
                    if (hit != null && (SystemClock.elapsedRealtime() - hit.atElapsedMs) < ttlMillis) {
                        AppLog.log("epg", AppLog.Level.DEBUG, "sid=$streamId cache=hit size=${hit.data.size}")
                        return@withLock hit.data
                    }
                    null
                }
            if (cached != null) return@withContext cached.take(limit)

            // Get EPG channelId if any (helps persistence and XMLTV fallback)
            val chanId: String? =
                withContext(Dispatchers.IO) {
                    val box = ObxStore.get(context).boxFor(ObxLive::class.java)
                    val row =
                        box
                            .query(
                                com.chris.m3usuite.data.obx.ObxLive_.streamId
                                    .equal(streamId),
                            ).build()
                            .findFirst()
                    row?.epgChannelId
                }

            // Global gate: if disabled, avoid any network/API and try stale OBX only
            if (!settings.m3uWorkersEnabled.first()) {
                if (!chanId.isNullOrBlank()) {
                    val row =
                        withContext(Dispatchers.IO) {
                            val box = ObxStore.get(context).boxFor(ObxEpgNowNext::class.java)
                            box
                                .query(
                                    com.chris.m3usuite.data.obx.ObxEpgNowNext_.channelId
                                        .equal(chanId),
                                ).build()
                                .findFirst()
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
                        // Cache bookkeeping (treat as content)
                        if (list.isNotEmpty()) {
                            lock.withLock {
                                cache[streamId] = Cache(SystemClock.elapsedRealtime(), list)
                                trimIfNeeded()
                            }
                        }
                        return@withContext list.take(limit)
                    }
                }
                // No stale data available
                lock.withLock {
                    emptyCache[streamId] = SystemClock.elapsedRealtime()
                    trimIfNeeded()
                }
                return@withContext emptyList()
            }

            // Try Xtream first if configured (cache client for repeated calls)
            val client = clientRef.get() ?: buildClient().also { if (it != null) clientRef.set(it) }
            val xtreamRes: List<XtShortEPGProgramme> =
                if (client != null) {
                    val raw = runCatching { client.fetchShortEpg(streamId, limit) }.getOrNull()
                    val list = if (!raw.isNullOrBlank()) parseShortEpg(raw) else emptyList()
                    if (list.isNotEmpty()) AppLog.log("epg", AppLog.Level.DEBUG, "sid=$streamId source=xtream size=${list.size}")
                    list
                } else {
                    emptyList()
                }

            var final =
                xtreamRes.ifEmpty {
                    fallbackXmlTvFor(chanId).also { if (it.isNotEmpty()) AppLog.log("epg", AppLog.Level.DEBUG, "sid=$streamId source=xmltv size=${it.size}") }
                }
            // Soft fallback: if network yielded nothing but we have a stale OBX row, reuse it to avoid blank UI
            if (final.isEmpty() && !chanId.isNullOrBlank()) {
                val row =
                    withContext(Dispatchers.IO) {
                        val box = ObxStore.get(context).boxFor(ObxEpgNowNext::class.java)
                        box
                            .query(
                                com.chris.m3usuite.data.obx.ObxEpgNowNext_.channelId
                                    .equal(chanId),
                            ).build()
                            .findFirst()
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
                    AppLog.log("epg", AppLog.Level.DEBUG, "sid=$streamId source=obx-stale size=${final.size}")
                }
            }

            // Persist Now/Next into OBX if possible
            if (!chanId.isNullOrBlank()) {
                val now = final.getOrNull(0)
                val next = final.getOrNull(1)
                val nowStart = now?.start?.toLongOrNull()?.times(1000)
                val nowEnd = now?.end?.toLongOrNull()?.times(1000)
                val nextStart = next?.start?.toLongOrNull()?.times(1000)
                val nextEnd = next?.end?.toLongOrNull()?.times(1000)
                withContext(Dispatchers.IO) {
                    val box = ObxStore.get(context).boxFor(ObxEpgNowNext::class.java)
                    val obx =
                        ObxEpgNowNext(
                            channelId = chanId,
                            nowTitle = now?.title,
                            nowStartMs = nowStart,
                            nowEndMs = nowEnd,
                            nextTitle = next?.title,
                            nextStartMs = nextStart,
                            nextEndMs = nextEnd,
                            updatedAt = System.currentTimeMillis(),
                        )
                    val existing =
                        box
                            .query(
                                com.chris.m3usuite.data.obx.ObxEpgNowNext_.channelId
                                    .equal(chanId),
                            ).build()
                            .findFirst()
                    if (existing != null) obx.id = existing.id
                    box.put(obx)
                }
                AppLog.log("epg", AppLog.Level.DEBUG, "sid=$streamId persist ch=$chanId now=${now?.title} next=${next?.title}")
            }
            // Cache hit bookkeeping: content uses normal TTL; empty uses short TTL
            if (final.isNotEmpty()) {
                lock.withLock {
                    cache[streamId] = Cache(SystemClock.elapsedRealtime(), final)
                    trimIfNeeded()
                }
            } else {
                lock.withLock {
                    emptyCache[streamId] = SystemClock.elapsedRealtime()
                    trimIfNeeded()
                }
            }
            AppLog.log("epg", AppLog.Level.DEBUG, "sid=$streamId result size=${final.size}")
            final.take(limit)
        }

    /**
     * Prefetch Now/Next for a batch of streamIds.
     * Uses the same caching/persist logic as [nowNext] and respects TTLs.
     * Concurrency is limited to avoid hammering the panel.
     */
    suspend fun prefetchNowNext(
        streamIds: List<Int>,
        limit: Int = 2,
    ) = withContext(Dispatchers.IO) {
        if (!settings.m3uWorkersEnabled.first()) return@withContext
        val ids = streamIds.distinct().take(50) // safety guard for accidental huge batches
        if (ids.isEmpty()) return@withContext
        val sem = Semaphore(4)
        coroutineScope {
            val jobs =
                ids.map { sid ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            try {
                                nowNext(sid, limit)
                            } catch (ce: kotlinx.coroutines.CancellationException) {
                                // ignore composition/lifecycle cancellations
                            } catch (t: Throwable) {
                                AppLog.log("epg", AppLog.Level.WARN, "prefetch sid=$sid failed: ${t.message}")
                            }
                        }
                    }
                }
            jobs.awaitAll()
        }
    }

    private fun parseShortEpg(jsonStr: String): List<XtShortEPGProgramme> =
        runCatching {
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
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())
}
