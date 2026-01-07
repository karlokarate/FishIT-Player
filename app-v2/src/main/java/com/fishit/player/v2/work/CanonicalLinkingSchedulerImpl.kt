package com.fishit.player.v2.work

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.fishit.player.core.catalogsync.CanonicalLinkingScheduler
import com.fishit.player.core.model.SourceType
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-based implementation of [CanonicalLinkingScheduler].
 *
 * **Task 2: Hot Path Entlastung**
 * Schedules backlog workers to process items that were persisted without canonical linking
 * during fast initial sync.
 *
 * **Architecture:**
 * - Enqueues one worker per source type
 * - Uses unique work names for deduplication
 * - Applies exponential backoff for retries
 * - Respects device class for batch sizing
 *
 * @see CanonicalLinkingBacklogWorker for worker implementation
 */
@Singleton
class CanonicalLinkingSchedulerImpl
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) : CanonicalLinkingScheduler {
        companion object {
            private const val TAG = "CanonicalLinkingScheduler"

            /** Work name format: canonical_linking_backlog_{source} */
            private const val WORK_NAME_FORMAT = "canonical_linking_backlog_%s"

            /** Tag for all canonical linking workers */
            private const val TAG_CANONICAL_LINKING = "canonical_linking"
        }

        override fun scheduleBacklogProcessing(
            sourceType: SourceType,
            estimatedItemCount: Long,
            delayMs: Long,
        ) {
            if (sourceType == SourceType.UNKNOWN) {
                UnifiedLog.w(TAG) { "Skipping backlog for UNKNOWN source" }
                return
            }

            val workName = WORK_NAME_FORMAT.format(sourceType.name.lowercase())
            val runId = UUID.randomUUID().toString()

            // Determine batch size based on estimated count and device class
            // For now, use a simple default - can be enhanced later
            val batchSize = CanonicalLinkingScheduler.DEFAULT_BATCH_SIZE

            val inputData =
                CanonicalLinkingInputData(
                    runId = runId,
                    sourceType = sourceType,
                    batchSize = batchSize,
                    maxRuntimeMs = WorkerConstants.DEFAULT_MAX_RUNTIME_MS,
                ).toData()

            val workRequest =
                OneTimeWorkRequestBuilder<CanonicalLinkingBacklogWorker>()
                    .setInputData(inputData)
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .addTag(TAG_CANONICAL_LINKING)
                    .addTag("source_${sourceType.name.lowercase()}")
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkerConstants.BACKOFF_INITIAL_SECONDS,
                        TimeUnit.SECONDS,
                    )
                    .build()

            // Use KEEP policy to avoid canceling running work
            workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.KEEP,
                workRequest,
            )

            UnifiedLog.i(TAG) {
                "Scheduled backlog processing: source=$sourceType runId=$runId " +
                    "estimatedItems=$estimatedItemCount delayMs=$delayMs batchSize=$batchSize"
            }
        }

        override fun cancelBacklogProcessing(sourceType: SourceType) {
            val workName = WORK_NAME_FORMAT.format(sourceType.name.lowercase())
            workManager.cancelUniqueWork(workName)
            UnifiedLog.i(TAG) { "Cancelled backlog processing: source=$sourceType" }
        }

        override suspend fun isBacklogProcessingActive(sourceType: SourceType): Boolean {
            val workName = WORK_NAME_FORMAT.format(sourceType.name.lowercase())
            val workInfos = workManager.getWorkInfosForUniqueWork(workName).firstOrNull()

            return workInfos?.any { workInfo ->
                workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED
            } ?: false
        }
    }
