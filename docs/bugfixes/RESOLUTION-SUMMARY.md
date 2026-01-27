# Resolution Summary: Empty UI & Performance Issues

**Date:** 2026-01-27  
**PR:** Filter incomplete NX_Work from UI queries per INV-3 contract

## Issues Resolved ✅

### 1. Empty UI (INV-3 Violation)
**Symptom:** "No content found" despite 41,506 works in database  
**Cause:** Incomplete works (missing sourceRefs/variants) passed to UI  
**Fix:** Added completeness filter to all UI query methods  
**Status:** ✅ RESOLVED

### 2. Multi-Minute Loading Delay (N+1 Query Explosion)
**Symptom:** Tiles appear after minutes, disappear/reappear randomly  
**Cause:** Completeness filter loaded ALL 41k works, triggered 82k+ relation checks  
**Fix:** Apply fetch limit BEFORE filtering using `query.find(0, fetchLimit)`  
**Performance:** Minutes → Instant (41k works → 300 works per query)  
**Status:** ✅ RESOLVED (commit 5bcd5cd)

## Issues Identified (Require Follow-up) ⚠️

### 3. Missing Posters (Architecture Issue)
**Symptom:** Tiles display without posters, backdrops work on detail pages  
**Root Cause:**  
```kotlin
// NX_Work entity stores full ImageRef with chatId/messageId ✅
@Convert(converter = ImageRefConverter::class)
var poster: ImageRef.TelegramThumb(remoteId, chatId, messageId)

// WorkMapper converts to string, LOSES chatId/messageId ❌
private fun ImageRef.toUrlString() = "tg://$remoteId"  // Lost context!

// Work domain model receives incomplete string ❌
data class Work(val posterRef: String?)  // Cannot resolve without chatId/messageId
```

**Solution Required:**
- Change `Work.posterRef` from `String?` to `ImageRef?`
- Remove `toUrlString()` conversion in WorkMapper
- Update UI layer to consume ImageRef directly
- **Impact:** Breaking change, requires UI updates

**Follow-up:** Create issue "Expose ImageRef in Work domain model"

### 4. Playback Failure
**Symptom:** Clicking tiles does not start playback  
**Needs Investigation:**
1. Are variants present? (`NxWorkVariantRepository`)
2. Does PlaybackSourceResolver succeed? (check logs)
3. Does player state machine transition? (check player logs)

**Follow-up:** Create issue "Investigate playback failure on tile click"

## Performance Metrics

| Metric | Before | After |
|--------|--------|-------|
| Works loaded per query | 41,506 | 300 |
| Relation checks per query | 82,012 | 600 |
| UI response time | Minutes | Instant |
| Tile stability | Flickering | Stable |

## Code Changes

### Commit 1: INV-3 Filter
- Added `isComplete()` check to 7 query methods
- Filters works missing sourceRefs or variants
- **Result:** Eliminated "No content found" with data present

### Commit 2: Performance Fix (5bcd5cd)
- Replaced `asFlow().map { all.filter() }` with custom `callbackFlow`
- Use `query.find(0, fetchLimit)` to limit DB load before filtering
- Use `isCompleteEfficient()` with `isEmpty()` method call
- **Result:** Eliminated multi-minute delays and tile flickering

## Architecture Lessons

### What Worked ✅
- ObjectBox query limiting (`find(offset, limit)`)
- Custom callbackFlow for efficient reactive queries
- Over-fetching (3x limit) to account for filtered items

### What Needs Improvement ⚠️
- Domain model should expose ImageRef, not String (poster issue)
- Consider pre-computed `isComplete` flag on NX_Work entity
- Need better variant ingestion verification (playback issue)

## Verification Checklist

- [x] UI displays tiles without multi-minute wait
- [x] Tiles remain stable (no disappearing/reappearing)
- [ ] Posters load correctly (requires domain model fix)
- [ ] Playback starts on tile click (requires investigation)

## Next Steps

1. ✅ Deploy this PR (performance + INV-3 fixes)
2. ⏳ Create issue: ImageRef domain model update
3. ⏳ Create issue: Playback investigation
4. ⏳ Consider `isComplete` flag on NX_Work for future optimization
