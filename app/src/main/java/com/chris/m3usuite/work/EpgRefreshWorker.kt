package com.chris.m3usuite.work

import android.content.Context
import androidx.work.*
import com.chris.m3usuite.core.epg.XmlTv
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.db.EpgNowNext
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class EpgRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val db = DbProvider.get(applicationContext)
        val mediaDao = db.mediaDao()
        val epgDao = db.epgDao()
        val settings = SettingsStore(applicationContext)

        val live = withContext(Dispatchers.IO) { mediaDao.listByType("live", 100000, 0) }
        val chanIds = live.mapNotNull { it.epgChannelId?.trim() }.filter { it.isNotEmpty() }.toSet()
        if (chanIds.isEmpty()) return Result.success()
        // Opportunistic cleanup of stale cache (>24h)
        withContext(Dispatchers.IO) { epgDao.deleteOlderThan(System.currentTimeMillis() - 24*60*60*1000) }
        val idx = XmlTv.indexNowNext(applicationContext, settings, chanIds)
        if (idx.isEmpty()) return Result.retry()
        val now = System.currentTimeMillis()
        val rows = idx.map { (ch, pair) ->
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
        return Result.success()
    }

    companion object {
        // Run periodically (15m floor due to WorkManager constraints)
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<EpgRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("epg_refresh", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
