package com.chris.m3usuite.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Backfill Telegram sync worker (no-op if Telegram is disabled).
 * Provides simple entry points for manual sync triggers from Settings.
 */
class TelegramSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val mode = inputData.getString(KEY_MODE) ?: MODE_VOD
            val settings = SettingsStore(applicationContext)
            val enabled = settings.tgEnabled.first()
            if (!enabled) return@runCatching Result.success()

            // Placeholder: real sync handled by TDLib event indexing; keep for manual backfill.
            // Intentionally lightweight and safe.
            Result.success()
        }.getOrElse { Result.failure() }
    }

    companion object {
        const val MODE_VOD = "vod"
        const val MODE_SERIES = "series"
        private const val KEY_MODE = "mode"

        fun enqueue(ctx: Context, mode: String) {
            val input = workDataOf(KEY_MODE to mode)
            val req = OneTimeWorkRequestBuilder<TelegramSyncWorker>()
                .setInputData(input)
                .build()
            val unique = when (mode) {
                MODE_SERIES -> SchedulingGateway.NAME_TG_SYNC_SERIES
                else -> SchedulingGateway.NAME_TG_SYNC_VOD
            }
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork(unique, ExistingWorkPolicy.REPLACE, req)
        }
    }
}

