package com.chris.m3usuite.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chris.m3usuite.BuildConfig
import com.chris.m3usuite.R
import com.chris.m3usuite.prefs.SettingsStore
import com.chris.m3usuite.telegram.service.TelegramServiceClient
import com.chris.m3usuite.tg.TgGate
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
    override suspend fun doWork(): Result {
        if (TgGate.mirrorOnly()) {
            SchedulingGateway.notifyTelegramSyncIdle()
            return Result.success()
        }
        return try {
            setForeground(createForegroundInfo())
            withContext(Dispatchers.IO) {
                try {
                    val settings = SettingsStore(applicationContext)
                    if (!settings.tgEnabled.first()) {
                        SchedulingGateway.notifyTelegramSyncIdle()
                        return@withContext Result.success()
                    }
                    val mode = inputData.getString(KEY_MODE) ?: MODE_ALL
                    val triggerRefresh = inputData.getBoolean(KEY_TRIGGER_REFRESH, false)
                    val csv = settings.tgSelectedChatsCsv.first()
                    val chatIds = csv.split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()
                    Log.i("TgWorker", "start mode=${mode} selectedChats=${chatIds.size}")
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
                    try {
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
                        setProgress(workDataOf("processed" to 0, "total" to chatIds.size))
                        var totalMessages = 0
                        var totalVodNew = 0
                        var totalSeriesEpisodes = 0
                        val newSeriesIds = mutableSetOf<Long>()
                        chatIds.forEachIndexed { idx, chatId ->
                            SchedulingGateway.notifyTelegramSyncProgress(mode, idx, chatIds.size)
                            val result = service.pullChatHistoryAwait(chatId, 200, fetchAll = true)
                            totalMessages += result.processedMessages
                            totalVodNew += result.newVod
                            totalSeriesEpisodes += result.newSeriesEpisodes
                            newSeriesIds += result.newSeriesIds.toList()
                            Log.i(
                                "TgWorker",
                                "pulled chatId=${chatId} processed=${result.processedMessages} newVod=${result.newVod} newSeriesEpisodes=${result.newSeriesEpisodes}"
                            )
                            setProgress(workDataOf("processed" to (idx + 1), "total" to chatIds.size))
                            SchedulingGateway.notifyTelegramSyncProgress(mode, idx + 1, chatIds.size)
                        }
                        val seriesStats = runCatching {
                            com.chris.m3usuite.data.repo.TelegramSeriesIndexer.rebuildWithStats(applicationContext)
                        }
                            .onFailure { e -> Log.w("TgWorker", "series rebuild failed: ${e.message}") }
                            .getOrNull()
                        if (seriesStats != null) {
                            Log.i(
                                "TgWorker",
                                "indexer done series=${seriesStats.seriesCount} new=${seriesStats.newSeries} episodes=${seriesStats.episodeCount} newEpisodes=${seriesStats.newEpisodes}"
                            )
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
                } finally {
                    runCatching { com.chris.m3usuite.data.obx.ObxStore.get(applicationContext).closeThreadResources() }
                }
            }
        } catch (t: Throwable) {
            Log.e("TgWorker", "sync failed", t)
            Result.failure(workDataOf("error" to (t.message ?: t::class.simpleName ?: "Unbekannter Fehler")))
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
        private const val FG_CHANNEL_ID = "telegram_sync"
        private const val FG_NOTIFICATION_ID = 0x746773

        fun enqueue(ctx: Context, mode: String, refreshHome: Boolean) {
            scheduleNow(ctx, mode, refreshHome)
        }

        fun scheduleNow(ctx: Context, mode: String = MODE_ALL, refreshHome: Boolean = true) {
            if (TgGate.mirrorOnly()) {
                SchedulingGateway.cancelTelegramWork(ctx)
                SchedulingGateway.notifyTelegramSyncIdle()
                return
            }
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

    private fun createForegroundInfo(): ForegroundInfo {
        ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, FG_CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.notification_telegram_sync_title))
            .setContentText(applicationContext.getString(R.string.notification_telegram_sync_text))
            .setSmallIcon(R.drawable.ic_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(FG_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = manager.getNotificationChannel(FG_CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            FG_CHANNEL_ID,
            context.getString(R.string.notification_channel_telegram_sync),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }
}
