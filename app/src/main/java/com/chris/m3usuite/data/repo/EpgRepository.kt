package com.chris.m3usuite.data.repo

import android.content.Context
import android.util.Log
import com.chris.m3usuite.core.xtream.XtreamClient
import com.chris.m3usuite.core.xtream.XtreamConfig
import com.chris.m3usuite.core.xtream.XtShortEPGProgramme
import com.chris.m3usuite.core.epg.XmlTv
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.EpgNowNext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import android.os.SystemClock

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

    private suspend fun config(): XtreamConfig? {
        val host = settings.xtHost.first()
        val user = settings.xtUser.first()
        val pass = settings.xtPass.first()
        if (host.isBlank() || user.isBlank() || pass.isBlank()) return null
        val port = settings.xtPort.first()
        val out = settings.xtOutput.first()
        return XtreamConfig(host, port, user, pass, out)
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
        val db = DbProvider.get(context)
        val mediaDao = db.mediaDao()
        val epgDao = db.epgDao()

        // Check persistent cache first via tvg-id
        val live = withContext(Dispatchers.IO) { runCatching { mediaDao.liveByStreamId(streamId) }.getOrNull() ?: mediaDao.listByType("live", 50000, 0).firstOrNull { it.streamId == streamId } }
        val chanId = live?.epgChannelId
        if (!chanId.isNullOrBlank()) {
            val row = withContext(Dispatchers.IO) { epgDao.byChannel(chanId) }
            if (row != null && (System.currentTimeMillis() - row.updatedAt) < ttlMillis) {
                val list = mutableListOf<XtShortEPGProgramme>()
                if (row.nowTitle != null && row.nowStartMs != null && row.nowEndMs != null) {
                    list += XtShortEPGProgramme(title = row.nowTitle, start = (row.nowStartMs/1000).toString(), end = (row.nowEndMs/1000).toString())
                }
                if (row.nextTitle != null && row.nextStartMs != null && row.nextEndMs != null) {
                    list += XtShortEPGProgramme(title = row.nextTitle, start = (row.nextStartMs/1000).toString(), end = (row.nextEndMs/1000).toString())
                }
                if (list.isNotEmpty()) {
                    Log.d(TAG, "sid=$streamId source=db-fresh ch=$chanId size=${list.size}")
                    return@withContext list.take(limit)
                }
            }
        }

        // Helper: XMLTV fallback using tvg-id mapped from DB
        suspend fun fallbackXmlTv(): List<XtShortEPGProgramme> {
            val epgUrl = settings.epgUrl.first()
            if (epgUrl.isBlank()) return emptyList()
            val dao = DbProvider.get(context).mediaDao()
            val live = runCatching { dao.liveByStreamId(streamId) }.getOrNull() ?: dao.listByType("live", 50000, 0).firstOrNull { it.streamId == streamId }
            val chan = live?.epgChannelId
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
        val cfg = config()
        val xtreamRes: List<XtShortEPGProgramme> = if (cfg != null) {
            val res = fetchXtreamShortEpg(context, settings, cfg, streamId, limit)
            if (res.isEmpty()) {
                // Diagnostics: check auth state to differentiate empty vs bad creds
                val client = XtreamClient(context, settings, cfg)
                runCatching { client.handshake() }.onSuccess { hs ->
                    val a = hs.userInfo?.auth
                    Log.w(tag, "shortEPG empty for sid=$streamId; handshake auth=$a")
                }.onFailure {
                    Log.w(tag, "handshake failed during epg diagnostics: ${it.message}")
                }
            }
            if (res.isNotEmpty()) Log.d(TAG, "sid=$streamId source=xtream size=${res.size}")
            res
        } else emptyList()

        var final = xtreamRes.ifEmpty { fallbackXmlTv().also { if (it.isNotEmpty()) Log.d(TAG, "sid=$streamId source=xmltv size=${it.size}") } }
        // Soft fallback: if network yielded nothing but we have a stale row, reuse it to avoid blank UI
        if (final.isEmpty() && !chanId.isNullOrBlank()) {
            val row = withContext(Dispatchers.IO) { epgDao.byChannel(chanId) }
            if (row != null) {
                val list = mutableListOf<XtShortEPGProgramme>()
                if (row.nowTitle != null && row.nowStartMs != null && row.nowEndMs != null) {
                    list += XtShortEPGProgramme(title = row.nowTitle, start = (row.nowStartMs/1000).toString(), end = (row.nowEndMs/1000).toString())
                }
                if (row.nextTitle != null && row.nextStartMs != null && row.nextEndMs != null) {
                    list += XtShortEPGProgramme(title = row.nextTitle, start = (row.nextStartMs/1000).toString(), end = (row.nextEndMs/1000).toString())
                }
                final = list
                Log.d(TAG, "sid=$streamId source=db-stale ch=$chanId size=${final.size}")
            }
        }
        // Persist into DB cache if we have a channel id and actual content
        if (!chanId.isNullOrBlank() && final.isNotEmpty()) {
            val now = final.getOrNull(0)
            val next = final.getOrNull(1)
            val row = EpgNowNext(
                channelId = chanId,
                nowTitle = now?.title,
                nowStartMs = secStrToMs(now?.start),
                nowEndMs = secStrToMs(now?.end),
                nextTitle = next?.title,
                nextStartMs = secStrToMs(next?.start),
                nextEndMs = secStrToMs(next?.end),
                updatedAt = System.currentTimeMillis()
            )
            withContext(Dispatchers.IO) { epgDao.upsertAll(listOf(row)) }
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

    companion object {
        private val flightMutex = Mutex()
        private val inFlight = mutableMapOf<Int, kotlinx.coroutines.Deferred<List<XtShortEPGProgramme>>>()

        suspend fun fetchXtreamShortEpg(
            context: Context,
            settings: SettingsStore,
            cfg: XtreamConfig,
            streamId: Int,
            limit: Int
        ): List<XtShortEPGProgramme> = coroutineScope {
            // Single-flight per streamId: coalesce concurrent requests
            val existing = flightMutex.withLock { inFlight[streamId] }
            if (existing != null) return@coroutineScope existing.await()
            val deferred = async(Dispatchers.IO) {
                val client = XtreamClient(context, settings, cfg)
                runCatching { client.shortEPG(streamId, limit) }.getOrDefault(emptyList())
            }
            try {
                flightMutex.withLock { inFlight[streamId] = deferred }
                deferred.await()
            } finally {
                flightMutex.withLock { inFlight.remove(streamId) }
            }
        }
    }
}
