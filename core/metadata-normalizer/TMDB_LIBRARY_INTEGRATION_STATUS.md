# TMDB API Library Integration Notes

## Issue: Internal Constructor in app.moviebase:tmdb-api-jvm:1.6.0

**Status:** Known Limitation  
**Date:** 2025-12-18

### Problem

The `app.moviebase:tmdb-api-jvm:1.6.0` library's `Tmdb3` class has an internal constructor, preventing direct instantiation:

```kotlin
e: Cannot access 'constructor(config: TmdbClientConfig): Tmdb3': it is internal in 'app/moviebase/tmdb/Tmdb3'.
```

### Impact

- Full TMDB API integration is blocked until a public initialization method is identified or the library is updated
- Current implementation returns `TmdbResolutionResult.Disabled` as a safe fallback
- All contract-defined data structures, scoring logic, and interfaces are implemented and tested
- Caching infrastructure (LruTtlCache) is complete

### What Works

✅ Contract definition (`TMDB_ENRICHMENT_CONTRACT.md`)  
✅ Config system (`TmdbConfig` via Hilt)  
✅ Resolution interfaces and result types  
✅ Deterministic match scoring (`TmdbMatchScore` + tests)  
✅ Cache infrastructure (`LruTtlCache`)  
✅ DI setup (Hilt modules)  
✅ Guardrails (no imports in pipeline/feature modules)

### What's Pending

⏸️ Actual TMDB API client instantiation  
⏸️ Details fetching (Path A)  
⏸️ Search + scoring (Path B)

### Potential Solutions

1. **Check for factory methods**: Investigate if library provides public factory/builder patterns
2. **Use reflection** (not recommended for production)
3. **Switch library**: Consider alternative TMDB SDKs:
   - `com.uwetrottmann.tmdb2:tmdb-java` (already in dependencies, may be viable alternative)
   - Official TMDB Kotlin SDK (if available)
4. **Wait for library update**: File issue with app.moviebase maintainers

### Recommended Next Steps

1. Test `com.uwetrottmann.tmdb2:tmdb-java:2.11.0` (already in build.gradle.kts)
2. If successful, update `DefaultTmdbMetadataResolver` to use that library
3. If not, evaluate other TMDB client options

### Files Affected

- `DefaultTmdbMetadataResolver.kt` - Currently returns Disabled
- Tests using real TMDB API are skipped (unit tests for scoring logic work)

---

This file serves as a record of the integration attempt and status.
