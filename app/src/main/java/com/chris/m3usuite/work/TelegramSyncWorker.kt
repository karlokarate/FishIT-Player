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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
        android.util.Log.i("TgWorker", "start mode=${mode} selectedChats=${chatIds.size}")
        if (chatIds.isEmpty()) return@withContext Result.success()

        val apiId = settings.tgApiId.first().takeIf { it > 0 } ?: BuildConfig.TG_API_ID
        val apiHash = settings.tgApiHash.first().ifBlank { BuildConfig.TG_API_HASH }
        if (apiId <= 0 || apiHash.isBlank()) return@withContext Result.failure()

        val service = TelegramServiceClient(applicationContext)
        return@withContext try {
            service.bind()
            var ready = false
            var attempt = 0
            while (attempt < 3 && !ready) {
                attempt++
                service.start(apiId, apiHash)
                service.getAuth()
                ready = waitForAuthReady(service, timeoutMs = 8000L)
                if (!ready) delay(600L)
            }
            if (!ready) {
                return@withContext Result.failure(workDataOf("error" to "Telegram nicht authentifiziert (Timeout)"))
            }
            // Progress reporting
            setProgress(workDataOf("processed" to 0, "total" to chatIds.size))
            chatIds.forEachIndexed { idx, chatId ->
                // For series mode, fetch the entire history (no upper bound) and await completion
                val all = (mode == MODE_SERIES)
                val processed = service.pullChatHistoryAwait(chatId, 200, fetchAll = all)
                android.util.Log.i("TgWorker", "pulled chatId=${chatId} processed=${processed}")
                setProgress(workDataOf("processed" to (idx + 1), "total" to chatIds.size))
            }
            // After history backfill, rebuild aggregated Telegram series into OBX
            if (mode == MODE_SERIES) {
                val series = runCatching { com.chris.m3usuite.data.repo.TelegramSeriesIndexer.rebuild(applicationContext) }.getOrDefault(0)
                android.util.Log.i("TgWorker", "indexer done series=${series}")
            }
            SchedulingGateway.onTelegramSyncCompleted(applicationContext, triggerRefresh)
            Result.success()
        } catch (e: Throwable) {
            Result.failure(workDataOf("error" to (e.message ?: e::class.simpleName ?: "Unbekannter Fehler")))
        } finally {
            service.unbind()
        }
    }

    private suspend fun waitForAuthReady(service: TelegramServiceClient, timeoutMs: Long): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            service.authStates()
                .map { runCatching { com.chris.m3usuite.telegram.TdLibReflection.AuthState.valueOf(it) }.getOrDefault(com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNKNOWN) }
                .filter { it == com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED }
                .first()
            true
        } ?: false
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
