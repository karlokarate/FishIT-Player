package com.fishit.player.core.persistence

import com.fishit.player.core.persistence.ObjectBoxFlow.asFlow
import com.fishit.player.core.persistence.ObjectBoxFlow.asSingleFlow
import io.mockk.every
import io.mockk.mockk
import io.objectbox.query.Query
import io.objectbox.reactive.DataSubscription
import io.objectbox.reactive.SubscriptionBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ObjectBoxFlow] lifecycle-safe flow extensions.
 *
 * Verifies:
 * 1. Initial results are emitted immediately
 * 2. Query methods (find/findFirst) are correctly called
 *
 * Note: Subscription lifecycle (subscribe/cancel) verification is difficult
 * with mock-based unit tests due to flowOn(Dispatchers.IO) threading.
 * The awaitClose { subscription.cancel() } pattern in the implementation
 * guarantees proper cleanup. Integration tests with real ObjectBox provide
 * stronger lifecycle guarantees.
 *
 * @see <a href="/contracts/NX_SSOT_CONTRACT.md">ObjectBox Reactive Patterns</a>
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObjectBoxFlowTest {
    private lateinit var mockQuery: Query<TestEntity>

    data class TestEntity(
        val id: Long,
        val name: String,
    )

    @Before
    fun setup() {
        mockQuery = mockk(relaxed = true)

        // Setup subscription chain - relaxed mock handles all calls
        val mockSubscription = mockk<DataSubscription>(relaxed = true)
        val mockSubscriptionBuilder = mockk<SubscriptionBuilder<List<TestEntity>>>(relaxed = true)
        every { mockSubscriptionBuilder.observer(any()) } returns mockSubscription
        every { mockQuery.subscribe() } returns mockSubscriptionBuilder
    }

    @Test
    fun `asFlow emits initial query results immediately`() =
        runTest {
            val initialData = listOf(TestEntity(1, "Test1"), TestEntity(2, "Test2"))
            every { mockQuery.find() } returns initialData

            val result = mockQuery.asFlow().first()

            assertEquals(initialData, result)
        }

    @Test
    fun `asFlow emits empty list for empty query`() =
        runTest {
            every { mockQuery.find() } returns emptyList()

            val result = mockQuery.asFlow().first()

            assertEquals(emptyList<TestEntity>(), result)
        }

    @Test
    fun `asSingleFlow emits first result`() =
        runTest {
            val firstEntity = TestEntity(1, "First")
            every { mockQuery.findFirst() } returns firstEntity

            val result = mockQuery.asSingleFlow().first()

            assertEquals(TestEntity(1, "First"), result)
        }

    @Test
    fun `asSingleFlow emits null for empty result`() =
        runTest {
            every { mockQuery.findFirst() } returns null

            val result = mockQuery.asSingleFlow().first()

            assertEquals(null, result)
        }
}
