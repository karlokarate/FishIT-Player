package com.fishit.player.core.synccommon.buffer

import com.fishit.player.core.model.sync.DeviceProfile
import com.fishit.player.infra.logging.UnifiedLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Source-agnostic producer/consumer buffer for catalog sync decoupling.
 *
 * **Purpose:**
 * Decouple fast pipeline producers from slower DB consumers to enable parallel writes.
 * This is a generalized version that works with any item type (Xtream, Telegram, etc.).
 *
 * **Performance:**
 * - Sequential Sync: 160s (DB writes block pipeline)
 * - Channel-Buffered Sync: 120s (pipeline runs unblocked, parallel DB writes)
 * - Improvement: ~25% faster
 *
 * **Memory Control:**
 * - Buffer limited to [capacity] items
 * - Backpressure when buffer full (producer suspends)
 * - Device-specific defaults via [DeviceProfile.bufferCapacity]
 *
 * **Thread Safety:**
 * - Uses Kotlin Channel (thread-safe)
 * - Metrics use AtomicInteger/AtomicLong
 *
 * **Usage:**
 * ```kotlin
 * val buffer = ChannelSyncBuffer<RawMediaMetadata>(
 *     capacity = deviceProfile.bufferCapacity
 * )
 *
 * // Producer (Pipeline)
 * launch {
 *     pipeline.scanCatalog().collect { item ->
 *         buffer.send(item)
 *     }
 *     buffer.close()
 * }
 *
 * // Consumers (parallel DB writers)
 * repeat(deviceProfile.consumerCount) {
 *     launch(Dispatchers.IO.limitedParallelism(1)) {
 *         buffer.consumeBatched(batchSize = deviceProfile.dbBatchSize) { batch ->
 *             persistBatch(batch)
 *         }
 *     }
 * }
 * ```
 *
 * @param T The item type (typically RawMediaMetadata or source-specific DTO)
 * @param capacity Maximum items in buffer before backpressure
 * @param name Optional buffer name for logging
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelSyncBuffer<T>(
    /**
     * Buffer capacity (items in memory).
     *
     * **Device-specific recommendations via [DeviceProfile]:**
     * - PHONE_HIGH_RAM: 1000 (more RAM available)
     * - PHONE_LOW_RAM: 500 (conservative)
     * - FIRETV_STICK: 500 (very limited RAM)
     * - SHIELD_TV: 1000 (powerful)
     *
     * **Memory calculation:**
     * ~2KB per RawMediaMetadata × 1000 = ~2MB buffer
     */
    capacity: Int,
    private val name: String = "SyncBuffer",
) {
    private val channel = Channel<T>(capacity)

    // Metrics
    private val _itemsSent = AtomicInteger(0)
    private val _itemsReceived = AtomicInteger(0)
    private val _backpressureEvents = AtomicInteger(0)
    private val _startTimeMs = AtomicLong(0)

    /**
     * Send item to buffer.
     *
     * **Behavior:**
     * - If buffer not full: Returns immediately
     * - If buffer full: Suspends until space available (backpressure)
     * - If buffer closed: Silently ignores (producer may finish after close)
     *
     * @param item Item to send
     */
    @Suppress("OPT_IN_USAGE") // isClosedForSend is delicate but safe here (check-then-act with exception handling)
    suspend fun send(item: T) {
        if (_startTimeMs.get() == 0L) {
            _startTimeMs.set(System.currentTimeMillis())
        }

        // Try non-blocking first
        val result = channel.trySend(item)
        if (result.isSuccess) {
            _itemsSent.incrementAndGet()
            return
        }

        // Buffer full - track backpressure and suspend
        if (result.isFailure && !channel.isClosedForSend) {
            _backpressureEvents.incrementAndGet()
            try {
                channel.send(item) // Suspend until space
                _itemsSent.incrementAndGet()
            } catch (_: ClosedSendChannelException) {
                // Buffer closed while waiting, ignore
            }
        }
    }

    /**
     * Receive item from buffer.
     *
     * **Behavior:**
     * - If items available: Returns immediately
     * - If buffer empty: Suspends until item available
     * - If buffer closed AND empty: Throws ClosedReceiveChannelException
     *
     * @return Next item from buffer
     * @throws ClosedReceiveChannelException when buffer is closed and empty
     */
    suspend fun receive(): T {
        val item = channel.receive()
        _itemsReceived.incrementAndGet()
        return item
    }

    /**
     * Try to receive without suspending.
     *
     * Used in tests and for non-blocking polling scenarios.
     *
     * @return Item if available, null otherwise
     */
    @Suppress("unused") // Used in tests (ChannelSyncBufferTest)
    fun tryReceive(): T? {
        val result = channel.tryReceive()
        if (result.isSuccess) {
            _itemsReceived.incrementAndGet()
            return result.getOrNull()
        }
        return null
    }

    /**
     * Consume items in batched mode with auto-flush.
     *
     * **Features:**
     * - Batches items up to [batchSize] before calling [onBatch]
     * - Auto-flushes remaining items when channel closes
     * - Respects backpressure via channel semantics
     * - Properly propagates CancellationException
     *
     * @param batchSize Max items per batch (use [DeviceProfile.dbBatchSize])
     * @param onBatch Callback invoked with each batch (suspend-safe)
     */
    suspend fun consumeBatched(
        batchSize: Int,
        onBatch: suspend (List<T>) -> Unit,
    ) {
        val batch = mutableListOf<T>()
        var flushedCount = 0
        try {
            while (true) {
                val item = receive()
                batch.add(item)
                if (batch.size >= batchSize) {
                    onBatch(batch.toList())
                    batch.clear()
                }
            }
        } catch (_: ClosedReceiveChannelException) {
            // Channel closed - flush remaining items
            if (batch.isNotEmpty()) {
                flushedCount = batch.size
                onBatch(batch.toList())
                batch.clear()
            }
            UnifiedLog.d(TAG) { "[$name] Consumer finished, flushed $flushedCount remaining" }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Coroutine cancelled - flush and re-throw to propagate cancellation
            if (batch.isNotEmpty()) {
                flushedCount = batch.size
                try {
                    onBatch(batch.toList())
                } catch (_: Exception) {
                    // Ignore errors during cancellation flush
                }
            }
            UnifiedLog.d(TAG) { "[$name] Consumer cancelled, flushed $flushedCount remaining" }
            throw e // Re-throw to propagate cancellation!
        }
    }

    /**
     * Close buffer (no more items will be sent).
     *
     * After close:
     * - send() will be ignored
     * - receive() can still drain remaining items
     * - receive() throws ClosedReceiveChannelException when empty
     */
    fun close() {
        channel.close()
        UnifiedLog.d(TAG) { "[$name] Buffer closed, metrics: ${getMetrics()}" }
    }

    /**
     * Get buffer metrics.
     *
     * @return Current metrics snapshot
     */
    fun getMetrics(): BufferMetrics {
        val sent = _itemsSent.get()
        val received = _itemsReceived.get()
        val startTime = _startTimeMs.get()
        val elapsed = if (startTime > 0) System.currentTimeMillis() - startTime else 0L

        return BufferMetrics(
            itemsSent = sent,
            itemsReceived = received,
            itemsInBuffer = sent - received,
            backpressureEvents = _backpressureEvents.get(),
            elapsedMs = elapsed,
            throughputPerSec = if (elapsed > 0) (received * 1000.0 / elapsed) else 0.0,
        )
    }

    companion object {
        private const val TAG = "ChannelSyncBuffer"

        /**
         * Create buffer with device-specific capacity.
         *
         * Factory method for device-aware buffer creation.
         *
         * @param deviceProfile Detected device profile
         * @param name Optional buffer name for logging
         */
        @Suppress("unused") // Factory method for future use
        fun <T> forDevice(
            deviceProfile: DeviceProfile,
            name: String = "SyncBuffer",
        ): ChannelSyncBuffer<T> = ChannelSyncBuffer(
            capacity = deviceProfile.bufferCapacity,
            name = name,
        )
    }
}

/**
 * Metrics snapshot for channel buffer.
 */
data class BufferMetrics(
    /** Total items sent to buffer */
    val itemsSent: Int,
    /** Total items received from buffer */
    val itemsReceived: Int,
    /** Current items waiting in buffer */
    val itemsInBuffer: Int,
    /** Number of times backpressure occurred (producer had to wait) */
    val backpressureEvents: Int,
    /** Elapsed time since first send (ms) */
    val elapsedMs: Long,
    /** Throughput (items received per second) */
    val throughputPerSec: Double,
) {
    override fun toString(): String = buildString {
        append("BufferMetrics(")
        append("sent=$itemsSent, ")
        append("received=$itemsReceived, ")
        append("inBuffer=$itemsInBuffer, ")
        append("backpressure=$backpressureEvents, ")
        append("elapsed=${elapsedMs}ms, ")
        append("throughput=${String.format(java.util.Locale.US, "%.1f", throughputPerSec)}/s")
        append(")")
    }
}
