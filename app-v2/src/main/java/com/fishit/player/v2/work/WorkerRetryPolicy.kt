package com.fishit.player.v2.work

import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import com.fishit.player.infra.logging.UnifiedLog

/**
 * Bounded retry policy for catalog sync workers.
 *
 * Contract: CATALOG_SYNC_WORKERS_CONTRACT_V2
 * - W-19: Retry limits (AUTO=3, EXPERT=5)
 * - Workers MUST use this policy to ensure bounded retries
 * - Prevents infinite retry loops on transient errors
 *
 * **Usage:**
 * ```kotlin
 * // In worker doWork():
 * val input = WorkerInputData.from(inputData)
 * // ... do work ...
 * if (transientError) {
 *     return WorkerRetryPolicy.retryOrFail(
 *         worker = this,
 *         input = input,
 *         reasonOnFail = "TRANSIENT_ERROR_LIMIT_EXCEEDED",
 *         durationMs = System.currentTimeMillis() - startTimeMs
 *     )
 * }
 * ```
 */
object WorkerRetryPolicy {
    private const val TAG = "WorkerRetryPolicy"

    /**
     * Returns [Result.retry] if under retry limit, else [Result.failure].
     *
     * Uses [CoroutineWorker.runAttemptCount] to track attempts (1-based on first run).
     *
     * @param worker The CoroutineWorker instance
     * @param input Parsed WorkerInputData (contains sync mode for determining limit)
     * @param reasonOnFail Failure reason string for WorkerOutputData (no secrets!)
     * @param durationMs Optional duration for failure output
     * @param logMessage Optional custom log message (will append attempt info)
     * @return Result.retry() or Result.failure()
     */
    fun retryOrFail(
        worker: CoroutineWorker,
        input: WorkerInputData,
        reasonOnFail: String,
        durationMs: Long? = null,
        logMessage: String? = null,
    ): Result {
        val attemptCount = worker.runAttemptCount // 1-based: first run = 1
        val retryLimit = input.retryLimit // AUTO=3, EXPERT=5

        return if (attemptCount < retryLimit) {
            val attemptsRemaining = retryLimit - attemptCount
            UnifiedLog.w(TAG) {
                "${logMessage ?: "Transient error"} - retrying (attempt=$attemptCount, remaining=$attemptsRemaining, limit=$retryLimit)"
            }
            Result.retry()
        } else {
            UnifiedLog.e(TAG) {
                "${logMessage ?: "Retry limit exceeded"} - failing (attempt=$attemptCount, limit=$retryLimit, reason=$reasonOnFail)"
            }
            Result.failure(
                WorkerOutputData.failure(
                    reason = reasonOnFail,
                    durationMs = durationMs,
                ),
            )
        }
    }

    /**
     * Check if retry limit is exceeded without creating a Result.
     *
     * Useful when worker needs to make decisions based on attempt count before calling retryOrFail.
     *
     * @param worker The CoroutineWorker instance
     * @param input Parsed WorkerInputData
     * @return true if retries are exhausted
     */
    fun isRetryLimitExceeded(
        worker: CoroutineWorker,
        input: WorkerInputData,
    ): Boolean = worker.runAttemptCount >= input.retryLimit

    /**
     * Get current attempt info for logging.
     *
     * @param worker The CoroutineWorker instance
     * @param input Parsed WorkerInputData
     * @return Human-readable attempt info string
     */
    fun getAttemptInfo(
        worker: CoroutineWorker,
        input: WorkerInputData,
    ): String {
        val attemptCount = worker.runAttemptCount
        val retryLimit = input.retryLimit
        return "attempt=$attemptCount/$retryLimit"
    }
}
