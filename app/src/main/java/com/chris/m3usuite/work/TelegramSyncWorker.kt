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
            if (!settings.tgEnabled.first()) return@runCatching Result.success()

            // Ensure TDLib is present and authenticated
            val authStateFlow = kotlinx.coroutines.flow.MutableStateFlow(com.chris.m3usuite.telegram.TdLibReflection.AuthState.UNKNOWN)
            val client = com.chris.m3usuite.telegram.TdLibReflection.getOrCreateClient(applicationContext, authStateFlow)
            if (client == null) return@runCatching Result.success()
            val authObj = com.chris.m3usuite.telegram.TdLibReflection.buildGetAuthorizationState()
                ?.let { com.chris.m3usuite.telegram.TdLibReflection.sendForResult(client, it, 1000) }
            val auth = com.chris.m3usuite.telegram.TdLibReflection.mapAuthorizationState(authObj)
            if (auth != com.chris.m3usuite.telegram.TdLibReflection.AuthState.AUTHENTICATED) return@runCatching Result.success()

            // Parse selected chat IDs by mode
            val csv = when (mode) {
                MODE_SERIES -> settings.tgSelectedSeriesChatsCsv.first()
                else -> settings.tgSelectedVodChatsCsv.first()
            }.trim()
            val chatIds = csv.split(',').mapNotNull { it.trim().toLongOrNull() }.distinct()
            if (chatIds.isEmpty()) return@runCatching Result.success()

            val box = com.chris.m3usuite.data.obx.ObxStore.get(applicationContext)
                .boxFor(com.chris.m3usuite.data.obx.ObxTelegramMessage::class.java)

            var processed = 0
            setProgressAsync(workDataOf("processed" to processed))

            fun indexMessageLike(chatId: Long, messageObj: Any) {
                try {
                    val content = runCatching { messageObj.javaClass.getDeclaredField("content").apply { isAccessible = true }.get(messageObj) }.getOrNull()
                    val fileObj = com.chris.m3usuite.telegram.TdLibReflection.findFirstFile(content) ?: return
                    val info = com.chris.m3usuite.telegram.TdLibReflection.extractFileInfo(fileObj) ?: return
                    val messageId = com.chris.m3usuite.telegram.TdLibReflection.extractMessageId(messageObj) ?: return
                    val unique = com.chris.m3usuite.telegram.TdLibReflection.extractFileUniqueId(fileObj)
                    val supports = com.chris.m3usuite.telegram.TdLibReflection.extractSupportsStreaming(content)
                    val caption = com.chris.m3usuite.telegram.TdLibReflection.extractCaptionOrText(messageObj)
                    val duration = com.chris.m3usuite.telegram.TdLibReflection.extractDurationSecs(content)
                    val mime = com.chris.m3usuite.telegram.TdLibReflection.extractMimeType(content)
                    val dims = com.chris.m3usuite.telegram.TdLibReflection.extractVideoDimensions(content)
                    val parsed = com.chris.m3usuite.telegram.TelegramHeuristics.parse(caption)
                    val date = com.chris.m3usuite.telegram.TdLibReflection.extractMessageDate(messageObj) ?: (System.currentTimeMillis() / 1000)
                    val thumbFileId = com.chris.m3usuite.telegram.TdLibReflection.extractThumbFileId(content)

                    val existing = box.query(
                        com.chris.m3usuite.data.obx.ObxTelegramMessage_.chatId.equal(chatId)
                            .and(com.chris.m3usuite.data.obx.ObxTelegramMessage_.messageId.equal(messageId))
                    ).build().findFirst()
                    val row = existing ?: com.chris.m3usuite.data.obx.ObxTelegramMessage(chatId = chatId, messageId = messageId)
                    row.fileId = info.fileId
                    row.fileUniqueId = unique
                    row.supportsStreaming = supports
                    row.caption = caption
                    row.captionLower = caption?.lowercase()
                    row.date = date
                    row.localPath = info.localPath
                    row.thumbFileId = thumbFileId
                    row.durationSecs = duration
                    row.mimeType = mime
                    row.sizeBytes = info.expectedSize.takeIf { it > 0 }
                    row.width = dims?.first
                    row.height = dims?.second
                    row.language = parsed.language
                    box.put(row)
                } catch (_: Throwable) { /* ignore single message failures */ }
            }

            // Fetch recent history per selected chat and index messages minimally
            for ((idx, chatId) in chatIds.withIndex()) {
                val fn = com.chris.m3usuite.telegram.TdLibReflection.buildGetChatHistory(chatId, 0L, 0, 200, false)
                val res = fn?.let { com.chris.m3usuite.telegram.TdLibReflection.sendForResult(client, it, 6000) }
                val list = if (res != null) com.chris.m3usuite.telegram.TdLibReflection.extractMessagesArray(res) else emptyList()
                for (m in list) {
                    indexMessageLike(chatId, m)
                    processed++
                    if (processed % 10 == 0) setProgressAsync(workDataOf("processed" to processed))
                }
                // Update progress coarse per chat to keep UI responsive
                setProgressAsync(workDataOf("processed" to processed))
            }

            // Note: Mapping to VOD/Series entities will be layered on top via
            // captions (SxxExx) in a follow-up pass; this worker performs the
            // minimal backfill so UI and DataSources can resolve TG playback.

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
