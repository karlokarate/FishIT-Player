package com.fishit.player.core.catalogsync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ChannelSyncBuffer].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelSyncBufferTest {
    @Test
    fun `send and receive items successfully`() =
        runTest {
            val buffer = ChannelSyncBuffer<Int>(capacity = 10)

            // Send items
            launch {
                repeat(5) { buffer.send(it) }
                buffer.close()
            }

            // Receive items
            val received = mutableListOf<Int>()
            try {
                while (true) {
                    received.add(buffer.receive())
                }
            } catch (e: ClosedReceiveChannelException) {
                // Expected when buffer is closed and empty
            }

            assertEquals(listOf(0, 1, 2, 3, 4), received)
        }

    @Test
    fun `buffer respects capacity and triggers backpressure`() =
        runTest(StandardTestDispatcher()) {
            val buffer = ChannelSyncBuffer<Int>(capacity = 3)

            // Send 3 items (fills buffer)
            buffer.send(1)
            buffer.send(2)
            buffer.send(3)

            // Try to send 4th item (should suspend due to backpressure)
            val senderJob = launch { buffer.send(4) }

            // Verify sender is suspended (backpressure)
            advanceTimeBy(100)
            assertFalse(senderJob.isCompleted)

            // Receive one item (makes space)
            val item = buffer.receive()
            assertEquals(1, item)

            // Now sender should complete
            advanceTimeBy(100)
            assertTrue(senderJob.isCompleted)

            // Verify backpressure was tracked
            val metrics = buffer.getMetrics()
            assertTrue(metrics.backpressureEvents > 0)
        }

    @Test
    fun `tryReceive returns null when buffer is empty`() =
        runTest {
            val buffer = ChannelSyncBuffer<Int>(capacity = 10)

            // Buffer is empty
            val item = buffer.tryReceive()
            assertNull(item)

            // Send item
            buffer.send(42)

            // Now tryReceive should succeed
            val received = buffer.tryReceive()
            assertNotNull(received)
            assertEquals(42, received)
        }

    @Test
    fun `close prevents further sends but allows draining`() =
        runTest {
            val buffer = ChannelSyncBuffer<Int>(capacity = 10)

            // Send items
            buffer.send(1)
            buffer.send(2)
            buffer.send(3)

            // Close buffer
            buffer.close()

            // Can still receive existing items
            assertEquals(1, buffer.receive())
            assertEquals(2, buffer.receive())
            assertEquals(3, buffer.receive())

            // Next receive throws
            var caught = false
            try {
                buffer.receive()
            } catch (e: ClosedReceiveChannelException) {
                caught = true
            }
            assertTrue(caught)
        }

    @Test
    fun `metrics track sent and received items`() =
        runTest {
            val buffer = ChannelSyncBuffer<Int>(capacity = 10)

            // Send items
            repeat(5) { buffer.send(it) }

            // Receive some items
            buffer.receive()
            buffer.receive()

            val metrics = buffer.getMetrics()
            assertEquals(5, metrics.itemsSent)
            assertEquals(2, metrics.itemsReceived)
            assertEquals(3, metrics.itemsInBuffer)
            assertTrue(metrics.elapsedMs >= 0)
        }

    @Test
    fun `multiple consumers can receive from same buffer`() =
        runTest {
            val buffer = ChannelSyncBuffer<Int>(capacity = 100)
            val received1 = mutableListOf<Int>()
            val received2 = mutableListOf<Int>()
            val received3 = mutableListOf<Int>()

            // Producer
            launch {
                repeat(30) { buffer.send(it) }
                buffer.close()
            }

            // 3 Consumers
            val consumer1 =
                async {
                    try {
                        while (true) received1.add(buffer.receive())
                    } catch (e: ClosedReceiveChannelException) {
                        // Done
                    }
                }
            val consumer2 =
                async {
                    try {
                        while (true) received2.add(buffer.receive())
                    } catch (e: ClosedReceiveChannelException) {
                        // Done
                    }
                }
            val consumer3 =
                async {
                    try {
                        while (true) received3.add(buffer.receive())
                    } catch (e: ClosedReceiveChannelException) {
                        // Done
                    }
                }

            consumer1.await()
            consumer2.await()
            consumer3.await()

            // All items should be received (distributed among consumers)
            val totalReceived = received1.size + received2.size + received3.size
            assertEquals(30, totalReceived)

            // Each consumer got some items (not necessarily equal distribution)
            assertTrue(received1.isNotEmpty() || received2.isNotEmpty() || received3.isNotEmpty())
        }

    @Test
    fun `buffer handles cancellation gracefully`() =
        runTest {
            val buffer = ChannelSyncBuffer<Int>(capacity = 10)

            val producerJob =
                launch {
                    try {
                        repeat(100) {
                            buffer.send(it)
                            delay(10)
                        }
                    } catch (e: CancellationException) {
                        // Expected
                        throw e
                    }
                }

            // Let producer send a few items
            delay(50)

            // Cancel producer
            producerJob.cancel()

            // Verify we can still read sent items
            val received = mutableListOf<Int>()
            try {
                repeat(10) {
                    // Try to receive a few times
                    received.add(buffer.receive())
                }
            } catch (e: ClosedReceiveChannelException) {
                // Buffer may close before we read all
            }

            assertTrue(received.isNotEmpty())
        }

    @Test
    fun `metrics show throughput calculation`() =
        runTest {
            val buffer = ChannelSyncBuffer<Int>(capacity = 100)

            // Send and receive items with some delay
            launch {
                repeat(10) {
                    buffer.send(it)
                    delay(10)
                }
                buffer.close()
            }

            // Receive items
            try {
                while (true) {
                    buffer.receive()
                    delay(5)
                }
            } catch (e: ClosedReceiveChannelException) {
                // Done
            }

            val metrics = buffer.getMetrics()
            assertEquals(10, metrics.itemsSent)
            assertEquals(10, metrics.itemsReceived)
            assertTrue(metrics.throughputPerSec > 0) // Should have some throughput
            assertTrue(metrics.elapsedMs > 0) // Should have elapsed time
        }
}
