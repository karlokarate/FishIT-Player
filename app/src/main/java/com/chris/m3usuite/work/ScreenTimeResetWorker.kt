package com.chris.m3usuite.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chris.m3usuite.data.db.DbProvider
import com.chris.m3usuite.data.repo.ScreenTimeRepository
import java.util.concurrent.TimeUnit

class ScreenTimeResetWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = DbProvider.get(applicationContext)
            val kids = db.profileDao().all().filter { it.type == "kid" }
            val repo = ScreenTimeRepository(applicationContext)
            kids.forEach { kid -> repo.resetToday(kid.id) }
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "screen_time_daily_reset"
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ScreenTimeResetWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}

