package com.chris.m3usuite.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import androidx.work.WorkerParameters
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.data.repo.ScreenTimeRepository
import java.util.concurrent.TimeUnit

class ScreenTimeResetWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val store = ObxStore.get(applicationContext)
            val box = store.boxFor(com.chris.m3usuite.data.obx.ObxProfile::class.java)
            val kids = box.all.filter { it.type == "kid" }
            val repo = ScreenTimeRepository(applicationContext)
            kids.forEach { kid -> repo.resetToday(kid.id) }
            // re-schedule next run at next midnight
            schedule(applicationContext)
            // cleanup thread-locals
            store.closeThreadResources()
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_ONE = "screen_time_daily_reset_once"
        fun schedule(ctx: Context) {
            // schedule one-time run at next local midnight, then re-schedule from within doWork
            val now = LocalDateTime.now()
            val nextMidnight = now.plusDays(1).truncatedTo(ChronoUnit.DAYS)
            val delay = java.time.Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1000L)
            val req = OneTimeWorkRequestBuilder<ScreenTimeResetWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(UNIQUE_ONE, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
