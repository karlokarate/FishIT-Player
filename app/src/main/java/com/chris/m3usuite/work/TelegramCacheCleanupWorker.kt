package com.chris.m3usuite.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chris.m3usuite.data.obx.ObxStore
import com.chris.m3usuite.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Trims Telegram local cache to user-defined size (GB) by deleting oldest files and nulling DB localPath. */
class TelegramCacheCleanupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = SettingsStore(applicationContext)
        val wipeAll = inputData.getBoolean(KEY_WIPE_ALL, false)
        val enabled = store.tgEnabled.first()
        if (!enabled && !wipeAll) return@withContext Result.success()

        val base = applicationContext.filesDir

        if (wipeAll) {
            // Delete all files under filesDir and clear localPath in DB
            kotlin.runCatching {
                base.walkTopDown().filter { it.isFile }.forEach { it.delete() }
            }
            kotlin.runCatching {
                val box = ObxStore.get(applicationContext).boxFor(com.chris.m3usuite.data.obx.ObxTelegramMessage::class.java)
                val all = box.all
                if (all.isNotEmpty()) {
                    all.forEach { it.localPath = null }
                    box.put(all)
                }
            }
            return@withContext Result.success()
        }
        // Collect all existing local files for trim
        val messages = kotlin.runCatching { base.walkTopDown().filter { it.isFile }.toList() }.getOrElse { emptyList() }
        val totalBytes = messages.sumOf { it.length() }
        val limitGb = store.tgCacheLimitGb.first().coerceIn(1, 50)
        val limitBytes = limitGb.toLong() * 1024L * 1024L * 1024L
        if (totalBytes <= limitBytes) return@withContext Result.success()

        // Sort by lastModified ascending (oldest first)
        val toDelete = messages.sortedBy { it.lastModified() }.iterator()
        var bytes = totalBytes
        var deleted = 0
        while (bytes > limitBytes && toDelete.hasNext()) {
            val f: File = toDelete.next()
            val sz = f.length()
            if (kotlin.runCatching { f.delete() }.getOrDefault(false)) {
                deleted++
                bytes -= sz
            }
        }
        Result.success()
    }

    companion object {
        private const val UNIQUE = "tg_cache_cleanup"
        private const val KEY_WIPE_ALL = "wipe_all"
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<TelegramCacheCleanupWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun wipeAll(context: Context) {
            val data = androidx.work.workDataOf(KEY_WIPE_ALL to true)
            val req = androidx.work.OneTimeWorkRequestBuilder<TelegramCacheCleanupWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("tg_cache_wipe_once", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
