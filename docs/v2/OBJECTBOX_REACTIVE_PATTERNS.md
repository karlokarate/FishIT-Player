# ObjectBox Reactive Patterns (v2 SSOT)

> **SSOT Location:** `/docs/v2/OBJECTBOX_REACTIVE_PATTERNS.md`

## Overview

This document defines the **correct** patterns for reactive ObjectBox queries in v2.

The key insight: ObjectBox's `DataObserver` callback is a **change trigger only**.
It does NOT provide the updated data. You must re-run the query inside the callback.

---

## Core Pattern: Re-Query on Change

### Why Re-Query?

ObjectBox's subscription system notifies you when data changes, but the callback
parameter is **not reliable** for receiving the actual updated data. The correct
pattern is:

1. Subscribe to query changes
2. When notified, call `query.find()` or `query.findFirst()` again
3. Emit the fresh result

### Implementation

```kotlin
fun <T> Query<T>.asFlow(): Flow<List<T>> = callbackFlow {
    val query = this@asFlow

    // 1. Emit initial result immediately
    val initial = withContext(Dispatchers.IO) { query.find() }
    trySend(initial)

    // 2. Subscribe - observer is a TRIGGER only
    val subscription = query.subscribe().observer { _ ->
        // 3. Re-query on change notification
        val updated = query.find()
        trySend(updated)
    }

    // 4. Cancel subscription when flow is cancelled
    awaitClose { subscription.cancel() }
}.flowOn(Dispatchers.IO)
```

---

## Cancellation Rule (MANDATORY)

**All subscriptions MUST be cancelled in `awaitClose`.**

```kotlin
awaitClose {
    subscription.cancel()  // REQUIRED - prevents memory leaks
}
```

Without this, the DataObserver remains registered and:
- Holds references to the coroutine scope
- Continues to fire callbacks after the collector is gone
- Causes memory leaks

---

## Do / Don't

### ✅ DO

| Pattern | Why |
|---------|-----|
| Use `query.subscribe().observer { _ -> ... }` | Observer is a trigger |
| Call `query.find()` inside the callback | Get fresh data |
| Cancel subscription in `awaitClose` | Prevent leaks |
| Use `flowOn(Dispatchers.IO)` | Query work off main thread |
| Emit initial result before subscribing | Immediate data |

### ❌ DON'T

| Anti-Pattern | Why It's Wrong |
|--------------|----------------|
| `DataObserver<List<T>> { data -> trySend(data) }` | Data parameter unreliable |
| Relying on callback data without re-query | May receive stale/empty data |
| Forgetting `awaitClose` | Memory leak |
| Running queries on Main thread | ANR risk |

---

## Single Result Pattern

For queries expecting 0 or 1 result:

```kotlin
fun <T> Query<T>.asSingleFlow(): Flow<T?> = callbackFlow {
    val query = this@asSingleFlow

    // Emit initial
    val initial = withContext(Dispatchers.IO) { query.findFirst() }
    trySend(initial)

    // Subscribe and re-query on change
    val subscription = query.subscribe().observer { _ ->
        val updated = query.findFirst()
        trySend(updated)
    }

    awaitClose { subscription.cancel() }
}.flowOn(Dispatchers.IO)
```

---

## Implementation Location

The canonical implementation lives in:
- `core/persistence/src/main/java/.../ObjectBoxFlow.kt`

All repositories should use these extensions rather than implementing their own
subscription logic.

---

## Testing

To verify reactive behavior works:

1. Collect a flow from a query
2. Insert/update an entity matching the query
3. Verify the flow emits a new list containing the change

```kotlin
@Test
fun `asFlow emits on insert`() = runTest {
    val query = box.query().build()
    val emissions = mutableListOf<List<MyEntity>>()

    val job = launch {
        query.asFlow().take(2).toList(emissions)
    }

    // Initial emission
    advanceUntilIdle()
    assertEquals(1, emissions.size)

    // Insert triggers re-emission
    box.put(MyEntity(name = "test"))
    advanceUntilIdle()
    assertEquals(2, emissions.size)

    job.cancel()
}
```

---

## Related Documents

- `/docs/v2/WORKMANAGER_PATTERNS.md` - Background worker patterns
- `core/persistence/README.md` - Persistence module overview
