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
import com.chris.m3usuite.tg.TgGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** Trims Telegram local cache to user-defined size (GB) by deleting oldest files and nulling DB localPath. */
class TelegramCacheCleanupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (TgGate.mirrorOnly()) return@withContext Result.success()
        val store = SettingsStore(applicationContext)
        val wipeAll = inputData.getBoolean(KEY_WIPE_ALL, false)
        val enabled = store.tgEnabled.first()
        if (!enabled && !wipeAll) return@withContext Result.success()

        val base = applicationContext.filesDir
        val obxStore = ObxStore.get(applicationContext)
        val msgBox = obxStore.boxFor(com.chris.m3usuite.data.obx.ObxTelegramMessage::class.java)

        fun putChunked(list: List<com.chris.m3usuite.data.obx.ObxTelegramMessage>, chunk: Int = 2000) {
            var i = 0
            val n = list.size
            while (i < n) {
                val to = min(i + chunk, n)
                msgBox.put(list.subList(i, to))
                i = to
            }
        }

        if (wipeAll) {
            // Delete all files under filesDir and clear localPath in DB (paginated)
            kotlin.runCatching {
                base.walkTopDown().filter { it.isFile }.forEach { it.delete() }
            }
            kotlin.runCatching {
                val q = msgBox.query(com.chris.m3usuite.data.obx.ObxTelegramMessage_.localPath
                    .startsWith(base.absolutePath)).build()
                val page = 5000L
                var off = 0L
                while (true) {
                    val batch = q.find(off, page)
                    if (batch.isEmpty()) break
                    batch.forEach { it.localPath = null }
                    putChunked(batch)
                    off += batch.size
                }
            }
            obxStore.closeThreadResources()
            return@withContext Result.success()
        }

        // Collect all local files for trim
        val files = runCatching { base.walkTopDown().filter { it.isFile }.toList() }.getOrElse { emptyList() }
        val totalBytes = files.sumOf { it.length() }
        val limitGb = store.tgCacheLimitGb.first().coerceIn(1, 50)
        val limitBytes = limitGb.toLong() * 1024L * 1024L * 1024L
        if (totalBytes <= limitBytes) {
            // Also ensure DB doesn't reference non-existent files from outside deletions
            clearDanglingDbPaths(base, msgBox, obxStore)
            return@withContext Result.success()
        }

        // Sort by lastModified ascending (oldest first) and delete until below threshold
        val toDelete = files.sortedBy { it.lastModified() }.iterator()
        var bytes = totalBytes
        val deletedPaths = ArrayList<String>()
        while (bytes > limitBytes && toDelete.hasNext()) {
            val f: File = toDelete.next()
            val sz = f.length()
            if (runCatching { f.delete() }.getOrDefault(false)) {
                deletedPaths.add(f.absolutePath)
                bytes -= sz
            }
        }

        // Null out localPath for deleted files (only those that truly vanished)
        if (deletedPaths.isNotEmpty()) {
            // Fast path: query messages under app files dir and test existence
            val q = msgBox.query(com.chris.m3usuite.data.obx.ObxTelegramMessage_.localPath
                .startsWith(base.absolutePath)).build()
            val page = 5000L
            var off = 0L
            while (true) {
                val batch = q.find(off, page)
                if (batch.isEmpty()) break
                val changed = batch.filter { it.localPath?.let { p -> !File(p).exists() } ?: false }
                if (changed.isNotEmpty()) putChunked(changed)
                off += batch.size
            }
        }

        // Clean up OBX thread-locals
        obxStore.closeThreadResources()
        Result.success()
    }

    private fun clearDanglingDbPaths(
        base: File,
        msgBox: io.objectbox.Box<com.chris.m3usuite.data.obx.ObxTelegramMessage>,
        obxStore: io.objectbox.BoxStore
    ) {
        val q = msgBox.query(com.chris.m3usuite.data.obx.ObxTelegramMessage_.localPath
            .startsWith(base.absolutePath)).build()
        val page = 5000L
        var off = 0L
        while (true) {
            val batch = q.find(off, page)
            if (batch.isEmpty()) break
            val changed = batch.filter { it.localPath?.let { p -> !File(p).exists() } ?: false }
            if (changed.isNotEmpty()) {
                var i = 0
                while (i < changed.size) {
                    val to = min(i + 2000, changed.size)
                    msgBox.put(changed.subList(i, to).onEach { it.localPath = null })
                    i = to
                }
            }
            off += batch.size
        }
        obxStore.closeThreadResources()
    }

    companion object {
        private const val UNIQUE = "tg_cache_cleanup"
        private const val KEY_WIPE_ALL = "wipe_all"
        private const val UNIQUE_WIPE_ONCE = "tg_cache_wipe_once"

        fun schedule(context: Context) {
            if (TgGate.mirrorOnly()) {
                cancel(context)
                return
            }
            val req = PeriodicWorkRequestBuilder<TelegramCacheCleanupWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun wipeAll(context: Context) {
            val data = androidx.work.workDataOf(KEY_WIPE_ALL to true)
            val req = androidx.work.OneTimeWorkRequestBuilder<TelegramCacheCleanupWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WIPE_ONCE, ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            runCatching { wm.cancelUniqueWork(UNIQUE) }
            runCatching { wm.cancelUniqueWork(UNIQUE_WIPE_ONCE) }
        }
    }
}
