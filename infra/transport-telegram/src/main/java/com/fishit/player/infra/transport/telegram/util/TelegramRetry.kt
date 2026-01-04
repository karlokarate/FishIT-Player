package com.fishit.player.infra.transport.telegram.util

import com.fishit.player.infra.logging.UnifiedLog
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Configuration for exponential backoff retry with jitter.
 *
 * @property maxAttempts Maximum number of retry attempts (including initial attempt)
 * @property baseDelayMs Base delay in milliseconds (first retry delay before jitter)
 * @property maxDelayMs Maximum delay cap in milliseconds
 * @property jitterFactor Jitter factor (0.0 = no jitter, 0.2 = ±20% jitter)
 */
data class RetryConfig(
    val maxAttempts: Int = 5,
    val baseDelayMs: Long = 500L,
    val maxDelayMs: Long = 30_000L,
    val jitterFactor: Double = 0.2,
) {
    companion object {
        /** Default retry config for TDLib operations. */
        val DEFAULT = RetryConfig()

        /** Aggressive retry config for critical auth operations. */
        val AUTH =
            RetryConfig(
                maxAttempts = 7,
                baseDelayMs = 1_000L,
                maxDelayMs = 60_000L,
            )

        /** Quick retry config for transient failures. */
        val QUICK =
            RetryConfig(
                maxAttempts = 3,
                baseDelayMs = 200L,
                maxDelayMs = 2_000L,
            )
    }
}

/** Result of a retry operation. */
sealed class RetryResult<out T> {
    /** Operation succeeded with the given value. */
    data class Success<T>(
        val value: T,
    ) : RetryResult<T>()

    /** All retry attempts exhausted. */
    data class Exhausted(
        val attempts: Int,
        val lastException: Throwable,
    ) : RetryResult<Nothing>()

    /** Returns value if success, null if exhausted. */
    fun getOrNull(): T? =
        when (this) {
            is Success -> value
            is Exhausted -> null
        }

    /** Returns value if success, throws lastException if exhausted. */
    fun getOrThrow(): T =
        when (this) {
            is Success -> value
            is Exhausted -> throw lastException
        }
}

/**
 * Shared retry utility for TDLib operations with exponential backoff + jitter.
 *
 * **Exponential Backoff Formula:**
 * ```
 * delay = min(baseDelay * 2^attempt, maxDelay) * (1 ± jitter)
 * ```
 *
 * **Example with defaults (base=500ms, max=30s, jitter=0.2):**
 * - Attempt 1: 500ms * (0.8-1.2) = 400-600ms
 * - Attempt 2: 1000ms * (0.8-1.2) = 800-1200ms
 * - Attempt 3: 2000ms * (0.8-1.2) = 1600-2400ms
 * - Attempt 4: 4000ms * (0.8-1.2) = 3200-4800ms
 * - Attempt 5: 8000ms * (0.8-1.2) = 6400-9600ms
 *
 * @see RetryConfig for configuration options
 */
object TelegramRetry {
    private const val TAG = "TelegramRetry"

    /**
     * Executes an operation with exponential backoff retry.
     *
     * @param config Retry configuration
     * @param operationName Human-readable name for logging
     * @param shouldRetry Optional predicate to determine if exception is retryable
     * @param operation The suspending operation to retry
     * @return [RetryResult] with success value or exhausted info
     */
    suspend fun <T> withRetry(
        config: RetryConfig = RetryConfig.DEFAULT,
        operationName: String = "operation",
        shouldRetry: (Throwable) -> Boolean = { true },
        operation: suspend () -> T,
    ): RetryResult<T> {
        var lastException: Throwable? = null

        for (attempt in 1..config.maxAttempts) {
            try {
                val result = operation()
                if (attempt > 1) {
                    UnifiedLog.d(TAG, "$operationName succeeded on attempt $attempt")
                }
                return RetryResult.Success(result)
            } catch (e: Throwable) {
                lastException = e

                val isLastAttempt = attempt >= config.maxAttempts
                val isRetryable = shouldRetry(e)

                if (isLastAttempt || !isRetryable) {
                    if (isLastAttempt) {
                        UnifiedLog.w(
                            TAG,
                            "$operationName failed after $attempt attempts: ${e.message}",
                        )
                    } else {
                        UnifiedLog.w(TAG, "$operationName failed (non-retryable): ${e.message}")
                    }
                    return RetryResult.Exhausted(attempt, e)
                }

                val delayMs = calculateDelay(attempt, config)
                UnifiedLog.d(
                    TAG,
                    "$operationName failed (attempt $attempt/${config.maxAttempts}), retrying in ${delayMs}ms: ${e.message}",
                )

                kotlinx.coroutines.delay(delayMs)
            }
        }

        // Should not reach here, but handle defensively
        return RetryResult.Exhausted(
            config.maxAttempts,
            lastException ?: IllegalStateException("No attempts made"),
        )
    }

    /**
     * Executes an operation with retry, throwing on exhaustion.
     *
     * Convenience wrapper that throws the last exception if all attempts fail.
     *
     * @see withRetry for full documentation
     */
    suspend fun <T> executeWithRetry(
        config: RetryConfig = RetryConfig.DEFAULT,
        operationName: String = "operation",
        shouldRetry: (Throwable) -> Boolean = { true },
        operation: suspend () -> T,
    ): T = withRetry(config, operationName, shouldRetry, operation).getOrThrow()

    /**
     * Calculates delay for the given attempt with exponential backoff and jitter.
     *
     * Formula: min(baseDelay * 2^(attempt-1), maxDelay) * (1 ± jitter)
     */
    internal fun calculateDelay(
        attempt: Int,
        config: RetryConfig,
    ): Long {
        // Exponential: base * 2^(attempt-1)
        val exponentialDelay = config.baseDelayMs * 2.0.pow((attempt - 1).toDouble())

        // Cap at max
        val cappedDelay = min(exponentialDelay, config.maxDelayMs.toDouble())

        // Apply jitter: delay * (1 ± jitterFactor)
        val jitterMultiplier = 1.0 + (Random.nextDouble() * 2 - 1) * config.jitterFactor
        val finalDelay = (cappedDelay * jitterMultiplier).toLong()

        return finalDelay.coerceAtLeast(1L)
    }
}
