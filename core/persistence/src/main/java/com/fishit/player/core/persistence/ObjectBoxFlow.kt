package com.fishit.player.core.persistence

import io.objectbox.query.Query
import io.objectbox.reactive.DataObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Lifecycle-safe ObjectBox reactive flow extensions.
 *
 * These extensions replace the experimental `subscribe().toFlow()` pattern with a
 * proper lifecycle-aware implementation that:
 * - Automatically disposes subscriptions when the collector cancels
 * - Emits initial query results immediately
 * - Runs query work on IO dispatcher
 * - Avoids resource leaks from undisposed DataObservers
 *
 * **Why not `subscribe().toFlow()`?**
 * The ObjectBox Kotlin extension `toFlow()` is marked `@ExperimentalCoroutinesApi`
 * and relies on internal Flow builders that may not properly handle cancellation
 * in all Coroutine contexts. This custom implementation uses `callbackFlow` with
 * explicit `awaitClose` to guarantee subscription cleanup.
 *
 * **Usage:**
 * ```kotlin
 * val query = box.query().equal(MyEntity_.name, "test").build()
 * query.asFlow().collect { results ->
 *     // Handle results
 * }
 * ```
 *
 * @see <a href="/docs/v2/OBJECTBOX_REACTIVE_PATTERNS.md">ObjectBox Reactive Patterns</a>
 */
object ObjectBoxFlow {

    /**
     * Convert an ObjectBox [Query] to a lifecycle-safe [Flow] of lists.
     *
     * - Emits the initial query result immediately upon collection
     * - Emits updated results whenever the underlying data changes
     * - Automatically unsubscribes when the flow collector is cancelled
     *
     * @return Flow that emits List<T> on each data change
     */
    fun <T> Query<T>.asFlow(): Flow<List<T>> = callbackFlow {
        // Create observer that emits to the channel
        val observer = DataObserver<List<T>> { data ->
            trySend(data)
        }

        // Subscribe and get the subscription handle for cleanup
        val subscription = subscribe().observer(observer)

        // Emit initial result immediately (ObjectBox subscription may not emit initial)
        val initial = find()
        trySend(initial)

        // Wait for cancellation and clean up
        awaitClose {
            subscription.cancel()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Convert an ObjectBox [Query] to a lifecycle-safe [Flow] of single nullable result.
     *
     * Useful for queries that expect 0 or 1 result.
     *
     * - Emits the first result or null immediately upon collection
     * - Emits updated result whenever the underlying data changes
     * - Automatically unsubscribes when the flow collector is cancelled
     *
     * @return Flow that emits T? on each data change
     */
    fun <T> Query<T>.asSingleFlow(): Flow<T?> = callbackFlow {
        // Create observer that emits first element to the channel
        val observer = DataObserver<List<T>> { data ->
            trySend(data.firstOrNull())
        }

        // Subscribe and get the subscription handle for cleanup
        val subscription = subscribe().observer(observer)

        // Emit initial result immediately
        val initial = findFirst()
        trySend(initial)

        // Wait for cancellation and clean up
        awaitClose {
            subscription.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
