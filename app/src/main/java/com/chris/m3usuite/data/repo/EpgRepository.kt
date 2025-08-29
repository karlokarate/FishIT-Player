package com.chris.m3usuite.data.repo

import android.content.Context
import android.util.Log
import com.chris.m3usuite.core.xtream.XtreamClient
import com.chris.m3usuite.core.xtream.XtreamConfig
import com.chris.m3usuite.core.xtream.XtShortEPGProgramme
import com.chris.m3usuite.prefs.SettingsStore
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
        val cfg = config() ?: return@withContext emptyList()
        val client = XtreamClient(context, settings, cfg)
        val res = runCatching { client.shortEPG(streamId, limit) }.onFailure {
            Log.w(tag, "shortEPG failed for sid=$streamId on ${cfg.portalBase}: ${it.message}")
        }.getOrDefault(emptyList())
        if (res.isEmpty()) {
            // Diagnostics: check auth state to differentiate empty vs bad creds
            runCatching { client.handshake() }.onSuccess { hs ->
                val a = hs.userInfo?.auth
                Log.w(tag, "shortEPG empty for sid=$streamId; handshake auth=$a exp=${hs.userInfo?.expDate}")
            }.onFailure {
                Log.w(tag, "handshake failed during epg diagnostics: ${it.message}")
            }
        }
        lock.withLock { cache[streamId] = Cache(System.currentTimeMillis(), res) }
        res
    }
}
