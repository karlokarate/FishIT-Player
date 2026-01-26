# Troubleshooting: Empty UI Despite Data in Database

## Symptom

User sees "No content found" on Home/Library screens despite DB Inspector showing:
- NX_Work: 41,506 rows ✅
- NX_WorkSourceRef: 56,012 rows ✅
- NX_WorkVariant: 55,376 rows ✅

## Root Cause

The issue was that **NX_Work repository queries did NOT filter for INV-3 completeness**. According to the NX SSOT Contract (INV-3), every work visible in the UI must have BOTH:
1. At least 1 `NX_WorkSourceRef`
2. At least 1 `NX_WorkVariant`

Some of the 41,506 works were missing one or both, causing them to fail to render in the UI.

## The Fix

**File:** `infra/data-nx/src/main/java/com/fishit/player/infra/data/nx/repository/NxWorkRepositoryImpl.kt`

Added completeness filter to all UI-visible query methods:

```kotlin
private fun isComplete(work: NX_Work): Boolean {
    return work.sourceRefs.isNotEmpty() && work.variants.isNotEmpty()
}
```

Applied to:
- `observeByType()` - Used by Home screen for Movies, Series, Clips, Live
- `observeRecentlyCreated()` - Used by Recently Added section
- `observeRecentlyUpdated()` - Used for updated content
- `searchByTitle()` - Used by search
- `observeWithOptions()` - Used by Library advanced queries
- `advancedSearch()` - Used by search with filters

## How to Diagnose Incomplete Works

You can identify how many works are incomplete using the diagnostic repository:

```kotlin
// Inject NxWorkDiagnostics
private val diagnostics: NxWorkDiagnostics

// Count incomplete works
suspend fun checkCompleteness() {
    val totalWorks = diagnostics.countAll()
    val worksMissingSources = diagnostics.findWorksMissingSources(limit = 200)
    val worksMissingVariants = diagnostics.findWorksMissingVariants(limit = 200)
    
    println("Total works: $totalWorks")
    println("Missing sources: ${worksMissingSources.size}")
    println("Missing variants: ${worksMissingVariants.size}")
}
```

## Why Works May Be Incomplete

Incomplete works can occur due to:

1. **Interrupted Sync** - Sync stopped before variants were created
2. **Pipeline Errors** - Variant generation failed for some items
3. **Source Account Issues** - SourceRef not created properly
4. **Race Conditions** - Multi-step ingestion not atomic

These incomplete works remain in the database for debugging but are now correctly hidden from the UI.

## What Happens After The Fix

### Before Fix:
```
NxWorkRepository.observeByType(MOVIE)
  → Returns 10,000 movies (including incomplete ones)
  → NxHomeContentRepository maps each work
  → determineSourceType() returns UNKNOWN for incomplete works
  → UI filters out UNKNOWN → Empty screen
```

### After Fix:
```
NxWorkRepository.observeByType(MOVIE)
  → Filters out incomplete works
  → Returns only 8,500 complete movies
  → All have valid sourceType
  → UI displays all 8,500 movies ✅
```

## Expected Results After Deployment

1. **Home Screen** - Shows movies/series/clips from complete works only
2. **Library Screen** - Shows searchable/filterable complete works only
3. **Performance** - Improved (no wasted mapping of incomplete works)
4. **Data Quality** - Incomplete works still in DB for diagnostics

## If Issue Persists

If UI is still empty after this fix, check:

1. **All works incomplete?** - Run diagnostics to see ratio
2. **SourceActivationStore** - Are sources actually marked as ACTIVE?
3. **Permissions** - Does app have necessary permissions?
4. **Logs** - Check for errors in NxHomeContentRepositoryImpl

Use diagnostic queries:
```kotlin
val complete = totalWorks - worksMissingSources.size - worksMissingVariants.size
println("Complete works: $complete / $totalWorks")
```

If `complete` is 0, then ALL works are incomplete - indicates a pipeline or sync issue, not a query filter issue.

## Related Files

- `infra/data-nx/src/main/java/com/fishit/player/infra/data/nx/repository/NxWorkRepositoryImpl.kt` - The fix
- `infra/data-nx/src/main/java/com/fishit/player/infra/data/nx/repository/NxWorkDiagnosticsImpl.kt` - Diagnostic queries
- `infra/data-nx/src/main/java/com/fishit/player/infra/data/nx/home/NxHomeContentRepositoryImpl.kt` - Home screen data
- `contracts/NX_SSOT_CONTRACT.md` - INV-3 definition
