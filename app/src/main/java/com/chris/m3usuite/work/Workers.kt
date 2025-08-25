package com.chris.m3usuite.work

import android.content.Context
import androidx.work.*
import com.chris.m3usuite.data.repo.PlaylistRepository
import com.chris.m3usuite.data.repo.XtreamRepository
import com.chris.m3usuite.prefs.SettingsStore
import java.util.concurrent.TimeUnit

class XtreamRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = XtreamRepository(applicationContext, SettingsStore(applicationContext))
        val cfg = repo.configureFromM3uUrl()
        val r = if (cfg != null) repo.importAll() else PlaylistRepository(applicationContext, SettingsStore(applicationContext)).refreshFromM3U()
        return if (r.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<XtreamRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("xtream_refresh", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}

class XtreamEnrichmentWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = XtreamRepository(applicationContext, SettingsStore(applicationContext))
        val db = com.chris.m3usuite.data.db.DbProvider.get(applicationContext)
        val vods = db.mediaDao().listByType("vod", 10000, 0)
        for (v in vods) runCatching { repo.enrichVodDetailsOnce(v.id) }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<XtreamEnrichmentWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("xtream_enrich", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
