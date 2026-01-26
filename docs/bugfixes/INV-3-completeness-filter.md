# INV-3 Completeness Filter Fix

**Issue:** User reported "No content found" in UI despite having 41,506 NX_Work rows with 56,012 SourceRefs and 55,376 Variants.

**Root Cause:** `NxWorkRepositoryImpl` queries were returning ALL works without filtering for completeness per INV-3.

## INV-3 Contract Requirement

Per `contracts/NX_SSOT_CONTRACT.md` INV-3:

> Every `NX_Work` visible in the UI must have:
> - ≥1 `NX_WorkSourceRef`
> - ≥1 `NX_WorkVariant` with valid `playbackHints`

## Problem Flow (Before Fix)

1. `NxWorkRepositoryImpl.observeByType(MOVIE)` returns ALL movie works
2. Some works have no `sourceRefs` or no `variants`
3. `NxHomeContentRepositoryImpl.determineSourceType()` called for each work
4. Works without sourceRefs → returns `SourceType.UNKNOWN`
5. UI filters out or fails to render items with UNKNOWN source
6. Result: Empty UI despite data in database

## Solution

Added `isComplete()` filter to all UI-visible query methods in `NxWorkRepositoryImpl`:

```kotlin
private fun isComplete(work: NX_Work): Boolean {
    return work.sourceRefs.isNotEmpty() && work.variants.isNotEmpty()
}
```

Applied to:
- `observeByType()` - Movies, Series, Clips, Live channels
- `observeRecentlyCreated()` - Recently Added section
- `observeRecentlyUpdated()` - Updated content
- `observeNeedsReview()` - Review queue
- `searchByTitle()` - Search results
- `observeWithOptions()` - Advanced queries
- `advancedSearch()` - Advanced search

## Impact

- **Performance:** More efficient - filters at database query level before mapping
- **Correctness:** Enforces INV-3 contract at the SSOT layer
- **UI:** Only complete, playable works reach the UI layer
- **Data Quality:** Highlights incomplete data early (diagnostics can identify these works)

## Incomplete Works

Works may be incomplete due to:
- Partial ingestion (interrupted sync)
- Failed variant generation
- Missing source account links
- Race conditions during multi-step ingestion

These works remain in the database for debugging but are hidden from UI until complete.

## Diagnostics

Use `NxWorkDiagnostics` to find incomplete works:
- `findWorksMissingSources()` - Works without any sourceRefs
- `findWorksMissingVariants()` - Works without any variants

## Testing

Build verified: `./gradlew :infra:data-nx:compileDebugKotlin` - SUCCESS

Manual testing recommended:
1. Deploy to device with existing incomplete works
2. Verify UI shows content (only complete works)
3. Check diagnostics screen for incomplete work count
4. Verify search/filtering still works correctly
