package com.fishit.player.v2.work

import androidx.work.Data
import androidx.work.WorkerParameters
import java.util.UUID

/**
 * Helper for reading TMDB enrichment worker InputData.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-14: TMDB InputData keys (MANDATORY)
 * - W-17: FireTV Safety (MANDATORY)
 * - W-22: TMDB Scope Priority (MANDATORY)
 */
data class TmdbWorkerInputData(
    /** Unique run identifier for correlation */
    val runId: String,
    /** TMDB scope: DETAILS_BY_ID | RESOLVE_MISSING_IDS | BOTH */
    val tmdbScope: String,
    /** Force refresh even if already resolved */
    val forceRefresh: Boolean,
    /** Batch size hint (clamped per device class) */
    val batchSizeHint: Int,
    /** Cursor for pagination/continuation */
    val batchCursor: String?,
    /** Device class for FireTV safety */
    val deviceClass: String,
    /** Maximum runtime in ms */
    val maxRuntimeMs: Long,
) {
    /** Returns true if this is a FireTV low-RAM device */
    val isFireTvLowRam: Boolean
        get() = deviceClass == WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM

    /**
     * Returns appropriate batch size clamped per device class (W-17).
     *
     * FireTV low-RAM: clamp to 10-25
     * Normal devices: clamp to 50-150
     */
    val effectiveBatchSize: Int
        get() =
            if (isFireTvLowRam) {
                batchSizeHint.coerceIn(
                    WorkerConstants.TMDB_FIRETV_BATCH_SIZE_MIN,
                    WorkerConstants.TMDB_FIRETV_BATCH_SIZE_MAX,
                )
            } else {
                batchSizeHint.coerceIn(
                    WorkerConstants.TMDB_NORMAL_BATCH_SIZE_MIN,
                    WorkerConstants.TMDB_NORMAL_BATCH_SIZE_MAX,
                )
            }

    /** Returns retry limit based on force refresh mode */
    val retryLimit: Int
        get() =
            if (forceRefresh) {
                WorkerConstants.RETRY_LIMIT_EXPERT
            } else {
                WorkerConstants.RETRY_LIMIT_AUTO
            }

    companion object {
        /**
         * Parse InputData from WorkerParameters.
         */
        fun from(workerParams: WorkerParameters): TmdbWorkerInputData = from(workerParams.inputData)

        /**
         * Parse InputData from Data directly.
         */
        fun from(data: Data): TmdbWorkerInputData {
            val deviceClass =
                data.getString(WorkerConstants.KEY_DEVICE_CLASS)
                    ?: WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET
            val isFireTv = deviceClass == WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM

            return TmdbWorkerInputData(
                runId =
                    data.getString(WorkerConstants.KEY_SYNC_RUN_ID)
                        ?: UUID.randomUUID().toString(),
                tmdbScope =
                    data.getString(WorkerConstants.KEY_TMDB_SCOPE)
                        ?: WorkerConstants.TMDB_SCOPE_BOTH,
                forceRefresh = data.getBoolean(WorkerConstants.KEY_TMDB_FORCE_REFRESH, false),
                batchSizeHint =
                    data.getInt(
                        WorkerConstants.KEY_TMDB_BATCH_SIZE_HINT,
                        if (isFireTv) {
                            WorkerConstants.TMDB_FIRETV_BATCH_SIZE_DEFAULT
                        } else {
                            WorkerConstants.TMDB_NORMAL_BATCH_SIZE_DEFAULT
                        },
                    ),
                batchCursor = data.getString(WorkerConstants.KEY_TMDB_BATCH_CURSOR),
                deviceClass = deviceClass,
                maxRuntimeMs =
                    data.getLong(
                        WorkerConstants.KEY_MAX_RUNTIME_MS,
                        WorkerConstants.DEFAULT_MAX_RUNTIME_MS,
                    ),
            )
        }

        /**
         * Build InputData for TMDB workers.
         */
        fun buildInputData(
            runId: String = UUID.randomUUID().toString(),
            tmdbScope: String = WorkerConstants.TMDB_SCOPE_BOTH,
            forceRefresh: Boolean = false,
            batchSizeHint: Int? = null,
            batchCursor: String? = null,
            deviceClass: String = WorkerConstants.DEVICE_CLASS_ANDROID_PHONE_TABLET,
            maxRuntimeMs: Long = WorkerConstants.DEFAULT_MAX_RUNTIME_MS,
        ): Data {
            val isFireTv = deviceClass == WorkerConstants.DEVICE_CLASS_FIRETV_LOW_RAM
            val defaultBatchSize =
                if (isFireTv) {
                    WorkerConstants.TMDB_FIRETV_BATCH_SIZE_DEFAULT
                } else {
                    WorkerConstants.TMDB_NORMAL_BATCH_SIZE_DEFAULT
                }

            return Data
                .Builder()
                .putString(WorkerConstants.KEY_SYNC_RUN_ID, runId)
                .putString(WorkerConstants.KEY_TMDB_SCOPE, tmdbScope)
                .putBoolean(WorkerConstants.KEY_TMDB_FORCE_REFRESH, forceRefresh)
                .putInt(WorkerConstants.KEY_TMDB_BATCH_SIZE_HINT, batchSizeHint ?: defaultBatchSize)
                .putString(WorkerConstants.KEY_DEVICE_CLASS, deviceClass)
                .putLong(WorkerConstants.KEY_MAX_RUNTIME_MS, maxRuntimeMs)
                .apply {
                    if (batchCursor != null) {
                        putString(WorkerConstants.KEY_TMDB_BATCH_CURSOR, batchCursor)
                    }
                }.build()
        }
    }
}

/**
 * TMDB enrichment result state for batch processing.
 */
sealed class TmdbEnrichmentResult {
    /**
     * Batch completed successfully.
     * @property itemsProcessed Number of items processed in this batch
     * @property itemsResolved Number of items successfully resolved
     * @property itemsFailed Number of items that failed resolution
     * @property hasMore True if more items need processing
     * @property nextCursor Cursor for next batch (if hasMore)
     */
    data class Success(
        val itemsProcessed: Int,
        val itemsResolved: Int,
        val itemsFailed: Int,
        val hasMore: Boolean,
        val nextCursor: String?,
    ) : TmdbEnrichmentResult()

    /**
     * Batch failed with retryable error.
     * @property reason Failure reason
     * @property itemsProcessedBeforeFailure Items processed before failure
     */
    data class RetryableFailure(
        val reason: String,
        val itemsProcessedBeforeFailure: Int,
    ) : TmdbEnrichmentResult()

    /**
     * Non-retryable failure (e.g., API key missing).
     * @property reason Failure reason
     */
    data class PermanentFailure(
        val reason: String,
    ) : TmdbEnrichmentResult()

    /**
     * No candidates to process (all resolved or cooldown).
     */
    data object NoCandidates : TmdbEnrichmentResult()

    /**
     * TMDB enrichment is disabled (no API key).
     */
    data object Disabled : TmdbEnrichmentResult()
}
