package com.chris.m3usuite.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Backfill Telegram sync worker (no-op if Telegram is disabled).
 * Provides simple entry points for manual sync triggers from Settings.
 */
class TelegramSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = SettingsStore(applicationContext)
        if (!settings.tgEnabled.first()) return@withContext Result.success()
        val mode = inputData.getString(KEY_MODE) ?: MODE_VOD
        val triggerRefresh = inputData.getBoolean(KEY_TRIGGER_REFRESH, false)
        val csv = when (mode) {
            MODE_SERIES -> settings.tgSelectedSeriesChatsCsv.first()
            else -> settings.tgSelectedVodChatsCsv.first()
        }
        val chatIds = csv.split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()
        if (chatIds.isEmpty()) return@withContext Result.success()

        val apiId = settings.tgApiId.first().takeIf { it > 0 } ?: BuildConfig.TG_API_ID
        val apiHash = settings.tgApiHash.first().ifBlank { BuildConfig.TG_API_HASH }
        if (apiId <= 0 || apiHash.isBlank()) return@withContext Result.failure()

        val service = TelegramServiceClient(applicationContext)
        return@withContext try {
            service.bind()
            service.start(apiId, apiHash)
            service.getAuth()
            chatIds.forEach { chatId -> service.pullChatHistory(chatId, 200) }
            SchedulingGateway.onTelegramSyncCompleted(applicationContext, triggerRefresh)
            Result.success()
        } catch (_: Throwable) {
            Result.failure()
        } finally {
            service.unbind()
        }
    }

    companion object {
        const val MODE_VOD = "vod"
        const val MODE_SERIES = "series"
        private const val KEY_MODE = "mode"
        private const val KEY_TRIGGER_REFRESH = "trigger_refresh"

        fun enqueue(ctx: Context, mode: String, refreshHome: Boolean) {
            val input = workDataOf(
                KEY_MODE to mode,
                KEY_TRIGGER_REFRESH to refreshHome
            )
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
