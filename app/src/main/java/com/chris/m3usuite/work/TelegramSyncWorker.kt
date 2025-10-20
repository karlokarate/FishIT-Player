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
        if (!settings.tgEnabled.first()) {
            SchedulingGateway.notifyTelegramSyncIdle()
            return@withContext Result.success()
        }
        val mode = inputData.getString(KEY_MODE) ?: MODE_ALL
        val triggerRefresh = inputData.getBoolean(KEY_TRIGGER_REFRESH, false)
        // Einheitliche Quelle: tgSelectedChatsCsv
        val csv = settings.tgSelectedChatsCsv.first()
        val chatIds = csv.split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()
        android.util.Log.i("TgWorker", "start mode=${mode} selectedChats=${chatIds.size}")
        if (chatIds.isEmpty()) {
            SchedulingGateway.notifyTelegramSyncIdle()
            return@withContext Result.success()
        }

        val apiId = settings.tgApiId.first().takeIf { it > 0 } ?: BuildConfig.TG_API_ID
        val apiHash = settings.tgApiHash.first().ifBlank { BuildConfig.TG_API_HASH }
        if (apiId <= 0 || apiHash.isBlank()) {
            SchedulingGateway.notifyTelegramSyncFailed(mode, "Telegram API Schlüssel fehlen")
            return@withContext Result.failure()
        }

        val service = TelegramServiceClient(applicationContext)
        return@withContext try {
            service.bind()
            SchedulingGateway.notifyTelegramSyncStarted(mode, chatIds.size)
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
                SchedulingGateway.notifyTelegramSyncFailed(mode, "Telegram nicht authentifiziert (Timeout)")
                return@withContext Result.failure(workDataOf("error" to "Telegram nicht authentifiziert (Timeout)"))
            }
            // Progress reporting
            setProgress(workDataOf("processed" to 0, "total" to chatIds.size))
            var totalMessages = 0
            var totalVodNew = 0
            var totalSeriesEpisodes = 0
            val newSeriesIds = mutableSetOf<Long>()
            // Immer vollständiger Backfill: fetchAll = true
            chatIds.forEachIndexed { idx, chatId ->
                // pageSize ist nur die Batch-Größe pro API-Call; bei fetchAll=true wird die gesamte Historie eingelesen.
                val all = true
                SchedulingGateway.notifyTelegramSyncProgress(mode, idx, chatIds.size)
                val result = service.pullChatHistoryAwait(chatId, pageSize = 200, fetchAll = all)
                totalMessages += result.processedMessages
                totalVodNew += result.newVod
                totalSeriesEpisodes += result.newSeriesEpisodes
                newSeriesIds += result.newSeriesIds.toList()
                android.util.Log.i("TgWorker", "pulled chatId=${chatId} processed=${result.processedMessages} newVod=${result.newVod} newSeriesEpisodes=${result.newSeriesEpisodes}")
                setProgress(workDataOf("processed" to (idx + 1), "total" to chatIds.size))
                SchedulingGateway.notifyTelegramSyncProgress(mode, idx + 1, chatIds.size)
            }
            // Nach vollständigem Backfill: Serien-Index neu aufbauen
            val seriesStats = runCatching { com.chris.m3usuite.data.repo.TelegramSeriesIndexer.rebuildWithStats(applicationContext) }
                .onFailure { e -> android.util.Log.w("TgWorker", "series rebuild failed: ${e.message}") }
                .getOrNull()
            if (seriesStats != null) {
                android.util.Log.i("TgWorker", "indexer done series=${seriesStats.seriesCount} new=${seriesStats.newSeries} episodes=${seriesStats.episodeCount} newEpisodes=${seriesStats.newEpisodes}")
            }
            val summary = when (mode) {
                MODE_SERIES, MODE_ALL -> {
                    val stats = seriesStats
                    val newSeries = stats?.newSeries ?: newSeriesIds.size
                    val newEpisodes = stats?.newEpisodes ?: totalSeriesEpisodes
                    SchedulingGateway.TelegramSyncResult(
                        moviesAdded = totalVodNew,
                        seriesAdded = newSeries,
                        episodesAdded = newEpisodes
                    )
                }
                else -> SchedulingGateway.TelegramSyncResult(
                    moviesAdded = totalVodNew,
                    seriesAdded = 0,
                    episodesAdded = 0
                )
            }
            SchedulingGateway.notifyTelegramSyncCompleted(mode, summary)
            SchedulingGateway.onTelegramSyncCompleted(applicationContext, triggerRefresh)
            Result.success(
                workDataOf(
                    "mode" to mode,
                    "processed_chats" to chatIds.size,
                    "messages_processed" to totalMessages,
                    "vod_new" to totalVodNew,
                    "series_new" to summary.seriesAdded,
                    "series_episode_new" to summary.episodesAdded
                )
            )
        } catch (e: Throwable) {
            SchedulingGateway.notifyTelegramSyncFailed(mode, e.message ?: e::class.simpleName ?: "Unbekannter Fehler")
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
        const val MODE_ALL = "all"
        private const val KEY_MODE = "mode"
        private const val KEY_TRIGGER_REFRESH = "trigger_refresh"

        fun enqueue(ctx: Context, mode: String, refreshHome: Boolean) {
            scheduleNow(ctx, mode, refreshHome)
        }

        fun scheduleNow(ctx: Context, mode: String = MODE_ALL, refreshHome: Boolean = true) {
            val input = workDataOf(
                KEY_MODE to mode,
                KEY_TRIGGER_REFRESH to refreshHome
            )
            val req = OneTimeWorkRequestBuilder<TelegramSyncWorker>()
                .setInputData(input)
                .build()
            val unique = when (mode) {
                MODE_SERIES -> SchedulingGateway.NAME_TG_SYNC_SERIES
                MODE_VOD -> SchedulingGateway.NAME_TG_SYNC_VOD
                else -> SchedulingGateway.NAME_TG_SYNC_ALL
            }
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork(unique, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
