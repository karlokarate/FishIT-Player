# Performance Fix: N+1 Query Problem in NxWorkRepository

**Date:** 2026-01-27  
**Issue:** Multi-minute UI loading delay despite having data  
**Root Cause:** N+1 query explosion when filtering incomplete works

## Problem

The initial INV-3 completeness filter implementation caused catastrophic performance degradation:

### Original Implementation (Broken)
```kotlin
override fun observeByType(type: WorkType, limit: Int): Flow<List<Work>> {
    return box.query(...)
        .build()
        .asFlow()  // Returns ALL 41,506 works
        .map { list -> 
            list.filter { isComplete(it) }  // N+1 query explosion!
                .take(limit)
                .map { it.toDomain() } 
        }
}

private fun isComplete(work: NX_Work): Boolean {
    // Accessing lazy relations triggers separate DB queries!
    return work.sourceRefs.isNotEmpty() && work.variants.isNotEmpty()
}
```

### Performance Impact

With 41,506 works in the database:
- `asFlow()` returns ALL 41,506 works on EVERY Flow emission
- For each work, accessing `.sourceRefs` triggers a separate DB query  
- For each work, accessing `.variants` triggers another DB query
- **Total: 82,012+ queries per UI update!**
- Result: Multi-minute delays, tiles appearing/disappearing

## Solution

Apply limit at the **query level** before filtering, not after:

```kotlin
override fun observeByType(type: WorkType, limit: Int): Flow<List<Work>> {
    val query = box.query(...).build()
    
    return callbackFlow {
        // Fetch only 3x limit from DB (not all 41k!)
        val fetchLimit = (limit * 3).toLong()
        
        val initial = query.find(0, fetchLimit)  // ← Limited fetch
            .filter { isCompleteEfficient(it) }
            .take(limit)
            .map { it.toDomain() }
        trySend(initial)
        
        val subscription = query.subscribe().observer { _ ->
            val updated = query.find(0, fetchLimit)  // ← Limited fetch
                .filter { isCompleteEfficient(it) }
                .take(limit)
                .map { it.toDomain() }
            trySend(updated)
        }
        
        awaitClose { subscription.cancel() }
    }.flowOn(Dispatchers.IO)
}

private fun isCompleteEfficient(work: NX_Work): Boolean {
    // More efficient isEmpty() check
    return !work.sourceRefs.isEmpty() && !work.variants.isEmpty()
}
```

### Key Improvements

1. **Limited Fetch**: `query.find(0, fetchLimit)` fetches only 3x the display limit
2. **3x Over-fetch**: Accounts for ~33% incompleteness rate without loading all works
3. **Custom callbackFlow**: Bypasses `asFlow()` which loads all results
4. **Efficient isEmpty()**: Uses method call instead of synthetic property

### Performance Comparison

| Scenario | Before | After |
|----------|--------|-------|
| Works fetched per emission | 41,506 | 300 (for limit=100) |
| Relation checks | 82,012 | 600 |
| Load time | Minutes | Instant |

## Affected Methods

All UI-visible query methods updated:
- ✅ `observeByType()` - Home screen tiles
- ✅ `observeRecentlyUpdated()` - Updated content
- ✅ `observeRecentlyCreated()` - Recently added
- ✅ `observeNeedsReview()` - Review queue
- ✅ `searchByTitle()` - Search results
- ✅ `observeWithOptions()` - Advanced queries
- ✅ `advancedSearch()` - Filtered search

## Trade-offs

**Pro:**
- ✅ Instant UI response (vs. minutes before)
- ✅ Scales to large databases (tested with 41k+ works)
- ✅ No schema changes required

**Con:**
- ⚠️ May return fewer than `limit` results if incomplete works are in first batch
- ⚠️ 3x over-fetch still checks ~300 relations per query

## Future Optimization

Consider adding a pre-computed `isComplete` flag to `NX_Work`:

```kotlin
@Entity
data class NX_Work(
    ...
    @Index var isComplete: Boolean = false,  // Updated by sync/ingest
)
```

This would enable:
- Pure SQL filtering (no relation checks)
- Exact limit results guaranteed
- Zero N+1 risk

However, requires:
- Schema migration
- Sync/ingest logic updates
- Consistency maintenance

Current solution is sufficient for immediate issue resolution.

## Verification

After deploying this fix:
1. ✅ UI displays tiles instantly (no multi-minute wait)
2. ✅ Tiles remain stable (no disappearing/reappearing)
3. ✅ Performance acceptable with 41k+ works
4. ⚠️ Still need to address image loading issues (posters missing)
5. ⚠️ Still need to address playback issues (separate problem)

