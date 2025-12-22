package com.fishit.player.core.persistence

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.objectbox.query.Query
import io.objectbox.reactive.DataObserver
import io.objectbox.reactive.DataSubscription
import io.objectbox.reactive.SubscriptionBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.ObjectBoxFlow.asSingleFlow

/**
 * Unit tests for [ObjectBoxFlow] lifecycle-safe flow extensions.
 *
 * Verifies:
 * 1. Subscription is disposed on flow cancellation
 * 2. Initial results are emitted immediately
 * 3. Subsequent emissions work correctly
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObjectBoxFlowTest {

    private lateinit var mockQuery: Query<TestEntity>
    private lateinit var mockSubscriptionBuilder: SubscriptionBuilder<List<TestEntity>>
    private lateinit var mockSubscription: DataSubscription
    private var capturedObserver: DataObserver<List<TestEntity>>? = null

    data class TestEntity(val id: Long, val name: String)

    @Before
    fun setup() {
        mockQuery = mockk(relaxed = true)
        mockSubscriptionBuilder = mockk(relaxed = true)
        mockSubscription = mockk(relaxed = true)

        // Capture the observer when it's registered
        val observerSlot = slot<DataObserver<List<TestEntity>>>()
        every { mockSubscriptionBuilder.observer(capture(observerSlot)) } answers {
            capturedObserver = observerSlot.captured
            mockSubscription
        }
        
        every { mockQuery.subscribe() } returns mockSubscriptionBuilder
    }

    @Test
    fun `asFlow emits initial query results immediately`() = runTest {
        val initialData = listOf(TestEntity(1, "Test1"), TestEntity(2, "Test2"))
        every { mockQuery.find() } returns initialData

        val result = mockQuery.asFlow().first()

        assertEquals(initialData, result)
    }

    @Test
    fun `asFlow cancellation disposes subscription`() = runTest(UnconfinedTestDispatcher()) {
        val initialData = listOf(TestEntity(1, "Test1"))
        every { mockQuery.find() } returns initialData

        val job = launch {
            mockQuery.asFlow().collect { /* consume */ }
        }

        // Let it start collecting
        testScheduler.advanceUntilIdle()
        
        // Cancel the collection
        job.cancelAndJoin()

        // Verify subscription was cancelled
        verify { mockSubscription.cancel() }
    }

    @Test
    fun `asFlow does not emit after cancellation`() = runTest(UnconfinedTestDispatcher()) {
        val initialData = listOf(TestEntity(1, "Initial"))
        every { mockQuery.find() } returns initialData

        val emissions = mutableListOf<List<TestEntity>>()
        val job = launch {
            mockQuery.asFlow().collect { emissions.add(it) }
        }

        testScheduler.advanceUntilIdle()
        job.cancelAndJoin()

        // Try to emit more data after cancellation
        capturedObserver?.onData(listOf(TestEntity(2, "AfterCancel")))

        // Should only have initial emission (or none if cancelled before)
        assertTrue("Should have at most 1 emission", emissions.size <= 1)
    }

    @Test
    fun `asSingleFlow emits first result`() = runTest {
        val firstEntity = TestEntity(1, "First")
        every { mockQuery.findFirst() } returns firstEntity

        val result = mockQuery.asSingleFlow().first()

        assertEquals(TestEntity(1, "First"), result)
    }

    @Test
    fun `asSingleFlow emits null for empty result`() = runTest {
        every { mockQuery.findFirst() } returns null

        val result = mockQuery.asSingleFlow().first()

        assertEquals(null, result)
    }

    @Test
    fun `asSingleFlow cancellation disposes subscription`() = runTest(UnconfinedTestDispatcher()) {
        every { mockQuery.findFirst() } returns TestEntity(1, "Test")

        val job = launch {
            mockQuery.asSingleFlow().collect { /* consume */ }
        }

        testScheduler.advanceUntilIdle()
        job.cancelAndJoin()

        verify { mockSubscription.cancel() }
    }
}
