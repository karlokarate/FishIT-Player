package com.fishit.player.core.persistence

import io.objectbox.query.Query
import io.objectbox.reactive.DataSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Lifecycle-safe ObjectBox reactive flow extensions.
 *
 * These extensions replace the experimental `subscribe().toFlow()` pattern with a
 * proper lifecycle-aware implementation that:
 * - Automatically disposes subscriptions when the collector cancels
 * - Emits initial query results immediately
 * - Re-runs query.find() on each change notification (correct pattern)
 * - Runs query work on IO dispatcher
 * - Avoids resource leaks from undisposed DataObservers
 *
 * **Why re-query on change?**
 * ObjectBox's DataObserver callback does NOT provide the updated data directly.
 * The observer is a "change trigger" only. We must call `query.find()` or
 * `query.findFirst()` inside the callback to get the actual updated results.
 * Using `DataObserver<List<T>>` is incorrect and will not emit proper updates.
 *
 * **Usage:**
 * ```kotlin
 * val query = box.query().equal(MyEntity_.name, "test").build()
 * query.asFlow().collect { results ->
 *     // Handle results - emits on initial + every DB change
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
     * - Re-queries and emits updated results whenever the underlying data changes
     * - Automatically unsubscribes when the flow collector is cancelled
     *
     * **Important:** The DataObserver is a change trigger only. We re-run
     * `query.find()` on each notification to get fresh data.
     *
     * @return Flow that emits List<T> on each data change
     */
    fun <T> Query<T>.asFlow(): Flow<List<T>> =
        callbackFlow {
            val query = this@asFlow

            // Emit initial result immediately
            val initial = withContext(Dispatchers.IO) { query.find() }
            trySend(initial)

            // Subscribe to changes - observer is a trigger only, not a data receiver.
            // ObjectBox will call onData() when relevant entities change.
            // We must re-query to get the actual updated data.
            var subscription: DataSubscription? = null
            subscription =
                query.subscribe().observer { _ ->
                    // Re-query on each change notification
                    val updated = query.find()
                    trySend(updated)
                }

            // Wait for cancellation and clean up subscription
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
     * - Re-queries and emits updated result whenever the underlying data changes
     * - Automatically unsubscribes when the flow collector is cancelled
     *
     * **Important:** The DataObserver is a change trigger only. We re-run
     * `query.findFirst()` on each notification to get fresh data.
     *
     * @return Flow that emits T? on each data change
     */
    fun <T> Query<T>.asSingleFlow(): Flow<T?> =
        callbackFlow {
            val query = this@asSingleFlow

            // Emit initial result immediately
            val initial = withContext(Dispatchers.IO) { query.findFirst() }
            trySend(initial)

            // Subscribe to changes - observer is a trigger only
            var subscription: DataSubscription? = null
            subscription =
                query.subscribe().observer { _ ->
                    // Re-query on each change notification
                    val updated = query.findFirst()
                    trySend(updated)
                }

            // Wait for cancellation and clean up subscription
            awaitClose {
                subscription.cancel()
            }
        }.flowOn(Dispatchers.IO)
}
