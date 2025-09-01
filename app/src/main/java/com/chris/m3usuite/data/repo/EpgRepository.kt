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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/**
 * Lightweight EPG helper with short TTL cache per streamId.
 * Focus: Now/Next (get_short_epg) performance and reliability.
 */
class EpgRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val ttlMillis: Long = 90_000L
) {
    private data class Cache(val at: Long, val data: List<XtShortEPGProgramme>)
    private val cache = mutableMapOf<Int, Cache>()
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
        lock.withLock {
            val c = cache[streamId]
            if (c != null && (System.currentTimeMillis() - c.at) < ttlMillis) return@withLock c.data
        }
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
                if (list.isNotEmpty()) return@withContext list.take(limit)
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
            val client = XtreamClient(context, settings, cfg)
            val res = runCatching { client.shortEPG(streamId, limit) }.onFailure {
                Log.w(tag, "shortEPG failed for sid=$streamId on ${cfg.portalBase}: ${it.message}")
            }.getOrDefault(emptyList())
            if (res.isEmpty()) {
                // Diagnostics: check auth state to differentiate empty vs bad creds
                runCatching { client.handshake() }.onSuccess { hs ->
                    val a = hs.userInfo?.auth
                    Log.w(tag, "shortEPG empty for sid=$streamId; handshake auth=$a")
                }.onFailure {
                    Log.w(tag, "handshake failed during epg diagnostics: ${it.message}")
                }
            }
            res
        } else emptyList()

        var final = if (xtreamRes.isNotEmpty()) xtreamRes else fallbackXmlTv()
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
            }
        }
        // Persist into DB cache if we have a channel id
        if (!chanId.isNullOrBlank()) {
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
        }
        lock.withLock { cache[streamId] = Cache(System.currentTimeMillis(), final) }
        final.take(limit)
    }
}
