package com.fishit.player.infra.transport.telegram.util

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [TelegramRetry] utility.
 *
 * Tests cover:
 * - Successful operation on first attempt
 * - Retry on transient failures
 * - Exhaustion after max attempts
 * - Exponential backoff calculation
 * - Jitter application
 * - Custom retry predicates
 */
class TelegramRetryTest {

    // ========== Success Cases ==========

    @Test
    fun `returns success on first attempt`() = runTest {
        var callCount = 0

        val result =
                TelegramRetry.withRetry(
                        config = RetryConfig.DEFAULT,
                        operationName = "test",
                ) {
                    callCount++
                    "success"
                }

        assertTrue("Should be success", result is RetryResult.Success)
        assertEquals("success", (result as RetryResult.Success).value)
        assertEquals(1, callCount)
    }

    @Test
    fun `retries and succeeds on second attempt`() = runTest {
        var callCount = 0

        val result =
                TelegramRetry.withRetry(
                        config = RetryConfig(maxAttempts = 3, baseDelayMs = 1, maxDelayMs = 10),
                        operationName = "test",
                ) {
                    callCount++
                    if (callCount < 2) throw RuntimeException("Transient failure")
                    "success"
                }

        assertTrue("Should be success", result is RetryResult.Success)
        assertEquals("success", (result as RetryResult.Success).value)
        assertEquals(2, callCount)
    }

    @Test
    fun `retries multiple times before success`() = runTest {
        var callCount = 0

        val result =
                TelegramRetry.withRetry(
                        config = RetryConfig(maxAttempts = 5, baseDelayMs = 1, maxDelayMs = 10),
                        operationName = "test",
                ) {
                    callCount++
                    if (callCount < 4) throw RuntimeException("Transient failure")
                    "success"
                }

        assertTrue("Should be success", result is RetryResult.Success)
        assertEquals(4, callCount)
    }

    // ========== Exhaustion Cases ==========

    @Test
    fun `exhausts after max attempts`() = runTest {
        var callCount = 0

        val result =
                TelegramRetry.withRetry(
                        config = RetryConfig(maxAttempts = 3, baseDelayMs = 1, maxDelayMs = 10),
                        operationName = "test",
                ) {
                    callCount++
                    throw RuntimeException("Persistent failure")
                }

        assertTrue("Should be exhausted", result is RetryResult.Exhausted)
        val exhausted = result as RetryResult.Exhausted
        assertEquals(3, exhausted.attempts)
        assertEquals(3, callCount)
        assertEquals("Persistent failure", exhausted.lastException.message)
    }

    @Test
    fun `getOrThrow throws on exhaustion`() = runTest {
        val result =
                TelegramRetry.withRetry(
                        config = RetryConfig(maxAttempts = 2, baseDelayMs = 1, maxDelayMs = 10),
                        operationName = "test",
                ) { throw IllegalStateException("Test error") }

        try {
            result.getOrThrow()
            fail("Should have thrown")
        } catch (e: IllegalStateException) {
            assertEquals("Test error", e.message)
        }
    }

    @Test
    fun `getOrNull returns null on exhaustion`() = runTest {
        val result =
                TelegramRetry.withRetry(
                        config = RetryConfig(maxAttempts = 1, baseDelayMs = 1, maxDelayMs = 10),
                        operationName = "test",
                ) { throw RuntimeException("Error") }

        assertEquals(null, result.getOrNull())
    }

    // ========== Custom Retry Predicate ==========

    @Test
    fun `respects shouldRetry predicate - non-retryable error`() = runTest {
        var callCount = 0

        val result =
                TelegramRetry.withRetry(
                        config = RetryConfig(maxAttempts = 5, baseDelayMs = 1),
                        operationName = "test",
                        shouldRetry = {
                            it !is IllegalArgumentException
                        }, // Don't retry IllegalArgumentException
                ) {
                    callCount++
                    throw IllegalArgumentException("Non-retryable")
                }

        assertTrue("Should be exhausted", result is RetryResult.Exhausted)
        assertEquals(1, callCount) // Should not retry
    }

    @Test
    fun `respects shouldRetry predicate - retryable error`() = runTest {
        var callCount = 0

        val result =
                TelegramRetry.withRetry(
                        config = RetryConfig(maxAttempts = 5, baseDelayMs = 1),
                        operationName = "test",
                        shouldRetry = { it is RuntimeException },
                ) {
                    callCount++
                    if (callCount < 3) throw RuntimeException("Retryable")
                    "success"
                }

        assertTrue("Should be success", result is RetryResult.Success)
        assertEquals(3, callCount)
    }

    // ========== Delay Calculation ==========

    @Test
    fun `calculateDelay returns exponential values`() {
        val config = RetryConfig(baseDelayMs = 500, maxDelayMs = 30000, jitterFactor = 0.0)

        // Without jitter, delays should be exactly exponential
        assertEquals(500L, TelegramRetry.calculateDelay(1, config))
        assertEquals(1000L, TelegramRetry.calculateDelay(2, config))
        assertEquals(2000L, TelegramRetry.calculateDelay(3, config))
        assertEquals(4000L, TelegramRetry.calculateDelay(4, config))
        assertEquals(8000L, TelegramRetry.calculateDelay(5, config))
    }

    @Test
    fun `calculateDelay respects maxDelay cap`() {
        val config = RetryConfig(baseDelayMs = 500, maxDelayMs = 5000, jitterFactor = 0.0)

        // Attempt 10 would be 500 * 2^9 = 256000, but capped at 5000
        val delay = TelegramRetry.calculateDelay(10, config)
        assertEquals(5000L, delay)
    }

    @Test
    fun `calculateDelay applies jitter within bounds`() {
        val config = RetryConfig(baseDelayMs = 1000, maxDelayMs = 30000, jitterFactor = 0.2)

        // Run multiple times to verify jitter is applied
        val delays = (1..100).map { TelegramRetry.calculateDelay(1, config) }

        // Base delay is 1000, with 0.2 jitter should be 800-1200
        assertTrue("All delays should be >= 800", delays.all { it >= 800 })
        assertTrue("All delays should be <= 1200", delays.all { it <= 1200 })

        // Should have some variation (not all the same)
        val uniqueDelays = delays.toSet()
        assertTrue("Should have variation in delays", uniqueDelays.size > 1)
    }

    @Test
    fun `calculateDelay never returns zero or negative`() {
        val config = RetryConfig(baseDelayMs = 1, maxDelayMs = 10, jitterFactor = 0.5)

        // Run many times with extreme jitter
        val delays = (1..1000).map { TelegramRetry.calculateDelay(1, config) }

        assertTrue("All delays should be positive", delays.all { it > 0 })
    }

    // ========== RetryConfig Presets ==========

    @Test
    fun `DEFAULT config has expected values`() {
        val config = RetryConfig.DEFAULT

        assertEquals(5, config.maxAttempts)
        assertEquals(500L, config.baseDelayMs)
        assertEquals(30_000L, config.maxDelayMs)
        assertEquals(0.2, config.jitterFactor, 0.001)
    }

    @Test
    fun `AUTH config has aggressive retry`() {
        val config = RetryConfig.AUTH

        assertEquals(7, config.maxAttempts)
        assertEquals(1_000L, config.baseDelayMs)
        assertEquals(60_000L, config.maxDelayMs)
    }

    @Test
    fun `QUICK config has fast retry`() {
        val config = RetryConfig.QUICK

        assertEquals(3, config.maxAttempts)
        assertEquals(200L, config.baseDelayMs)
        assertEquals(2_000L, config.maxDelayMs)
    }

    // ========== executeWithRetry Convenience ==========

    @Test
    fun `executeWithRetry returns value on success`() = runTest {
        val result =
                TelegramRetry.executeWithRetry(
                        config = RetryConfig.QUICK,
                        operationName = "test",
                ) { 42 }

        assertEquals(42, result)
    }

    @Test
    fun `executeWithRetry throws on exhaustion`() = runTest {
        try {
            TelegramRetry.executeWithRetry(
                    config = RetryConfig(maxAttempts = 1, baseDelayMs = 1),
                    operationName = "test",
            ) { throw RuntimeException("Test error") }
            fail("Should have thrown")
        } catch (e: RuntimeException) {
            assertEquals("Test error", e.message)
        }
    }
}
