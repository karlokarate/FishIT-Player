package com.chris.m3usuite.work

import android.content.Context
import androidx.work.*
import com.chris.m3usuite.core.epg.XmlTv
import com.chris.m3usuite.core.xtream.XtreamClient
import com.chris.m3usuite.core.xtream.XtreamConfig
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.EpgNowNext
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class EpgRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return if (refreshFavoritesNow(applicationContext, aggressive = false)) Result.success() else Result.success()
    }

    companion object {
        private val refreshMutex = Mutex()
        // Public: immediate refresh of favorites (used from UI at app start / timer)
        suspend fun refreshFavoritesNow(
            context: Context,
            aggressive: Boolean = false,
            xtreamConcurrency: Int = 6,
            xtreamTimeoutMs: Long = 4000L
        ): Boolean = refreshMutex.withLock {
            val TAG = "EPGWorker"
            val db = DbProvider.get(context)
            val mediaDao = db.mediaDao()
            val epgDao = db.epgDao()
            val settings = SettingsStore(context)

            val favCsv = settings.favoriteLiveIdsCsv.first()
            val favIds = favCsv.split(',').mapNotNull { it.toLongOrNull() }.toSet()
            if (favIds.isEmpty()) {
                android.util.Log.d(TAG, "favorites=0 skip")
                return false
            }
            val items = withContext(Dispatchers.IO) { favIds.mapNotNull { id -> runCatching { mediaDao.byId(id) }.getOrNull() } }
            android.util.Log.d(TAG, "favorites=${items.size} aggressive=$aggressive conc=$xtreamConcurrency timeoutMs=$xtreamTimeoutMs")

            // Cleanup of stale cache (>24h)
            withContext(Dispatchers.IO) { epgDao.deleteOlderThan(System.currentTimeMillis() - 24*60*60*1000) }
            val now = System.currentTimeMillis()

            // Build preferred results via Xtream if enabled and configured
            val useXtream = settings.epgFavUseXtream.first()
            val host = settings.xtHost.first(); val user = settings.xtUser.first(); val pass = settings.xtPass.first()
            val xtConfigured = host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
            val xtPort = settings.xtPort.first(); val xtOut = settings.xtOutput.first()
            val byChannel = mutableMapOf<String, Pair<com.chris.m3usuite.core.epg.XmlTvProg?, com.chris.m3usuite.core.epg.XmlTvProg?>>()

            if (useXtream && xtConfigured) {
                val cfg = XtreamConfig(host, xtPort, user, pass, xtOut)
                val client = XtreamClient(context, settings, cfg)
                val sem = Semaphore(xtreamConcurrency)
                coroutineScope {
                    for (it in items) {
                        val ch = it.epgChannelId?.trim().orEmpty()
                        val sid = it.streamId
                        if (ch.isNotEmpty() && sid != null) {
                            launch {
                                sem.acquire()
                                try {
                                    val list = withTimeoutOrNull(xtreamTimeoutMs) {
                                        com.chris.m3usuite.data.repo.EpgRepository.fetchXtreamShortEpg(context, settings, cfg, sid, 2)
                                    } ?: emptyList()
                                    if (list.isNotEmpty()) {
                                        val n = list.getOrNull(0)
                                        val x = list.getOrNull(1)
                                        val nowProg = n?.let { p -> com.chris.m3usuite.core.epg.XmlTvProg(p.title, p.start?.toLongOrNull()?.times(1000) ?: 0L, p.end?.toLongOrNull()?.times(1000) ?: 0L) }
                                        val nextProg = x?.let { p -> com.chris.m3usuite.core.epg.XmlTvProg(p.title, p.start?.toLongOrNull()?.times(1000) ?: 0L, p.end?.toLongOrNull()?.times(1000) ?: 0L) }
                                        synchronized(byChannel) { byChannel[ch] = (nowProg to nextProg) }
                                    }
                                } finally {
                                    sem.release()
                                }
                            }
                        }
                    }
                }
                android.util.Log.d(TAG, "xtream_ok=${byChannel.size}")
            }

            // XMLTV fill for remaining favorites lacking Xtream data
            val remainingCh = items.mapNotNull { it.epgChannelId?.trim() }.filter { it.isNotEmpty() && it !in byChannel.keys }.toSet()
            val anyXtream = byChannel.isNotEmpty()
            val shouldCallXmltv = remainingCh.isNotEmpty() && (!aggressive || !anyXtream)
            if (shouldCallXmltv) {
                val idx = XmlTv.indexNowNext(context, settings, remainingCh)
                byChannel.putAll(idx)
                android.util.Log.d(TAG, "xmltv_fill=${idx.size}")
            }

            if (byChannel.isEmpty()) return false
            val rows = byChannel.map { (ch, pair) ->
                val (n, x) = pair
                EpgNowNext(
                    channelId = ch,
                    nowTitle = n?.title,
                    nowStartMs = n?.startMs,
                    nowEndMs = n?.stopMs,
                    nextTitle = x?.title,
                    nextStartMs = x?.startMs,
                    nextEndMs = x?.stopMs,
                    updatedAt = now
                )
            }
            withContext(Dispatchers.IO) { epgDao.upsertAll(rows) }
            android.util.Log.d(TAG, "upserted=${rows.size}")
            return true
        }

        // Periodic schedule (WorkManager min interval = 15m). Keeps background refresh running.
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<EpgRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("epg_refresh", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
