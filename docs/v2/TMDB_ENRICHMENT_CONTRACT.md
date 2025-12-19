# TMDB Enrichment Contract v1.0

**Version:** 1.0  
**Date:** 2025-12-19  
**Status:** Binding Contract  
**Scope:** TMDB metadata enrichment, canonical identity, imaging SSOT

---

## 0. Binding References

This contract MUST be implemented in compliance with:

- `AGENTS.md` (architecture rules)
- `GLOSSARY_v2_naming_and_modules.md` (naming)
- `MEDIA_NORMALIZATION_CONTRACT.md` (canonical media)
- `LOGGING_CONTRACT_V2.md` (logging)

---

## 1. Core Principle: Enrichment-Only

### T-1 TMDB is Enrichment-Only (MANDATORY)

TMDB access is **resolver-only**:

| Layer | TMDB Access | Status |
|-------|-------------|--------|
| `core/metadata-normalizer` | ✅ ALLOWED | Only module with TMDB API dependency |
| `pipeline/*` | ❌ FORBIDDEN | May pass-through upstream IDs only |
| `infra/transport-*` | ❌ FORBIDDEN | No TMDB calls |
| `feature/*` | ❌ FORBIDDEN | No TMDB calls |
| `playback/*` | ❌ FORBIDDEN | No TMDB calls |
| `player/*` | ❌ FORBIDDEN | No TMDB calls |
| `app-v2` | ❌ FORBIDDEN | No TMDB calls (workers may schedule, not call) |

### T-2 Pipeline Pass-Through (MANDATORY)

Pipelines MAY pass-through TMDB IDs from upstream sources:

```kotlin
// ALLOWED: Xtream provider supplies TMDB ID
ExternalIds(tmdb = TmdbRef(TmdbMediaType.MOVIE, upstreamTmdbId))

// ALLOWED: Telegram Structured Bundle contains TMDB URL
ExternalIds(tmdb = TmdbRef(TmdbMediaType.TV, parsedTmdbId))

// FORBIDDEN: Pipeline calls TMDB API
tmdbClient.searchMovie(title) // ❌ NEVER in pipeline
```

---

## 2. Typed Canonical Keys

### T-3 Typed TMDB Canonical Keys (MANDATORY)

When a TMDB reference exists, canonical keys MUST use typed format:

| Media Type | Canonical Key Format | Example |
|------------|---------------------|---------|
| Movie | `tmdb:movie:{id}` | `tmdb:movie:550` |
| TV Show | `tmdb:tv:{id}` | `tmdb:tv:1396` |
| Episode | `tmdb:tv:{seriesId}` + season/episode | `tmdb:tv:1396` + S05E16 |

### T-4 Episode Handling (MANDATORY)

Episodes use the **series TMDB ID** (TV type), NOT episode-specific IDs:

```kotlin
// Correct: Episode uses series TMDB ID
TmdbRef(TmdbMediaType.TV, seriesTmdbId = 1396)
// Combined with: season = 5, episode = 16

// API call: GET /tv/1396/season/5/episode/16
```

---

## 3. Race-Free Image SSOT

### T-5 TMDB Reference ≠ Images Ready (MANDATORY)

The presence of a `TmdbRef` does NOT imply images are available:

```kotlin
// tmdbRef exists but images may not be fetched yet
val media = NormalizedMedia(
    tmdb = TmdbRef(TmdbMediaType.MOVIE, 550),
    poster = null,  // Images not yet fetched
    backdrop = null
)
```

### T-6 Image Priority Chain (MANDATORY)

UI MUST use this deterministic priority for poster selection:

```kotlin
val primaryPoster: ImageRef? =
    canonical.tmdbPosterRef       // 1. TMDB canonical poster
        ?: source.bestPosterRef   // 2. Source-provided poster (Telegram thumb, Xtream icon)
        ?: placeholder            // 3. Placeholder
```

### T-7 Upgrade-Only Policy (MANDATORY)

Image source transitions are **upgrade-only**:

| Current | New | Action |
|---------|-----|--------|
| Source poster | TMDB poster | ✅ UPGRADE (replace source with TMDB) |
| TMDB poster | Source poster | ❌ FORBIDDEN (never revert) |
| Placeholder | Source/TMDB | ✅ UPGRADE |

```kotlin
// FORBIDDEN: Reverting from TMDB to source
if (existing.tmdbPosterRef != null && newSourcePoster != null) {
    // DO NOT replace existing.tmdbPosterRef with newSourcePoster
}
```

---

## 4. Deterministic Scoring

### T-8 Match Score Components (MANDATORY)

`TmdbMatchScore` MUST use fixed components totaling 0..100:

| Component | Range | Weight | Description |
|-----------|-------|--------|-------------|
| `titleSimilarity` | 0..60 | 60% | Levenshtein/Jaro-Winkler normalized |
| `yearScore` | 0..20 | 20% | Exact=20, ±1=15, ±2=10, else=0 |
| `kindAgreement` | 0..10 | 10% | Media type matches TMDB result type |
| `episodeExact` | 0..10 | 10% | Season/episode match (TV only) |

### T-9 Decision Thresholds (MANDATORY)

Fixed thresholds for deterministic decisions:

```kotlin
enum class TmdbMatchDecision {
    ACCEPT,    // Use this match
    AMBIGUOUS, // Multiple close matches
    REJECT     // No acceptable match
}

fun decide(results: List<ScoredResult>): TmdbMatchDecision {
    if (results.isEmpty()) return REJECT
    
    val best = results.maxByOrNull { it.score } ?: return REJECT
    val secondBest = results.filter { it != best }.maxByOrNull { it.score }
    
    // ACCEPT: High confidence AND clear winner
    if (best.score >= 85 && (secondBest == null || best.score - secondBest.score >= 10)) {
        return ACCEPT
    }
    
    // AMBIGUOUS: Close scores or moderate confidence
    if (best.score >= 70 && secondBest != null && best.score - secondBest.score < 10) {
        return AMBIGUOUS
    }
    
    // REJECT: Low confidence
    return if (best.score < 70) REJECT else AMBIGUOUS
}
```

---

## 5. Resolver Behavior

### T-10 Details-by-ID Priority (MANDATORY)

If `externalIds.tmdb` (TmdbRef) exists, resolver MUST use **details-by-ID only**:

```kotlin
suspend fun enrich(normalized: NormalizedMediaMetadata): NormalizedMediaMetadata {
    // PRIORITY 1: Existing TMDB ref → fetch details directly
    val existingRef = normalized.externalIds.tmdb
    if (existingRef != null) {
        return fetchDetailsByTmdbRef(existingRef, normalized)
    }
    
    // PRIORITY 2: Search by title/year → score → accept/reject
    return searchAndScore(normalized)
}
```

### T-11 Never Overwrite Existing TmdbRef (MANDATORY)

Resolver MUST NOT replace an existing `TmdbRef` with a different one:

```kotlin
// FORBIDDEN: Replacing existing ref
if (existing.tmdb != null && searchResult.tmdbId != existing.tmdb.id) {
    UnifiedLog.w(TAG) { "WARN: Search returned different ID, keeping existing" }
    return existing // Keep original, do not replace
}
```

### T-12 Disabled When No API Key (MANDATORY)

If `TmdbConfig.apiKey` is blank, resolver MUST be disabled (no crash, no API calls):

```kotlin
if (config.apiKey.isBlank()) {
    UnifiedLog.d(TAG) { "TMDB resolver disabled: no API key" }
    return normalized // Pass-through unchanged
}
```

---

## 6. Caching (FireTV-Safe)

### T-13 In-Memory Caches (MANDATORY)

Resolver MUST use bounded in-memory caches:

| Cache | TTL | Max Size | Key |
|-------|-----|----------|-----|
| `detailsByTmdbRef` | 7 days | 256 entries | `TmdbRef` |
| `searchByQueryKey` | 24 hours | 256 entries | `"${title}|${year}"` |

### T-14 FireTV Constraints (MANDATORY)

Caches MUST be bounded to prevent OOM on FireTV Stick:

```kotlin
// Use LRU eviction with fixed max size
private val detailsCache = LruCache<TmdbRef, TmdbDetails>(256)
private val searchCache = LruCache<String, List<TmdbSearchResult>>(256)
```

---

## 7. Persisted Resolve-State

### T-15 Resolve-State Schema (MANDATORY)

Each canonical media item MUST support these fields:

| Field | Type | Description |
|-------|------|-------------|
| `tmdbResolveState` | Enum | UNRESOLVED, RESOLVED, UNRESOLVABLE_PERMANENT, STALE_REFRESH_REQUIRED |
| `tmdbResolveAttempts` | Int | Number of resolution attempts |
| `lastTmdbAttemptAt` | Long? | Epoch millis of last attempt |
| `tmdbNextEligibleAt` | Long? | Epoch millis when next attempt is allowed |
| `tmdbLastFailureReason` | String? | Last failure reason (for diagnostics) |
| `tmdbLastResolvedAt` | Long? | Epoch millis when successfully resolved |
| `tmdbResolvedBy` | Enum | PASS_THROUGH, DETAILS_BY_ID, SEARCH_MATCH, MANUAL_OVERRIDE |

### T-16 Resolve-State Values (MANDATORY)

```kotlin
enum class TmdbResolveState {
    /** Not yet attempted */
    UNRESOLVED,
    
    /** Successfully resolved (has TmdbRef) */
    RESOLVED,
    
    /** Permanently unresolvable (max attempts, no match, marked skip) */
    UNRESOLVABLE_PERMANENT,
    
    /** Was resolved but needs refresh (stale SSOT) */
    STALE_REFRESH_REQUIRED
}

enum class TmdbResolvedBy {
    /** TMDB ref passed through from upstream (pipeline) */
    PASS_THROUGH,
    
    /** Details fetched by existing TMDB ID */
    DETAILS_BY_ID,
    
    /** Resolved via search + scoring */
    SEARCH_MATCH,
    
    /** Manual override by user (reserved for future) */
    MANUAL_OVERRIDE
}
```

### T-17 Repository Query APIs (MANDATORY for Task 5)

Repository MUST provide these query APIs for worker batching:

```kotlin
interface TmdbResolutionQueries {
    /**
     * Find items with TmdbRef but missing SSOT data (poster, metadata).
     * For DETAILS_BY_ID enrichment.
     */
    suspend fun findCandidatesDetailsByIdMissingSsot(limit: Int): List<CanonicalMedia>
    
    /**
     * Find items without TmdbRef that are eligible for search.
     * Respects cooldown (tmdbNextEligibleAt <= now).
     */
    suspend fun findCandidatesMissingTmdbRefEligible(limit: Int, now: Long): List<CanonicalMedia>
    
    /**
     * Mark item as having TMDB details applied.
     */
    suspend fun markTmdbDetailsApplied(
        canonicalKey: String,
        tmdbRef: TmdbRef,
        resolvedBy: TmdbResolvedBy,
        resolvedAt: Long
    )
    
    /**
     * Mark a failed resolution attempt.
     */
    suspend fun markTmdbResolveAttemptFailed(
        canonicalKey: String,
        reason: String,
        attemptAt: Long,
        nextEligibleAt: Long
    )
    
    /**
     * Mark item as successfully resolved.
     */
    suspend fun markTmdbResolved(
        canonicalKey: String,
        tmdbRef: TmdbRef,
        resolvedBy: TmdbResolvedBy,
        resolvedAt: Long
    )
}
```

---

## 8. Logging

### T-18 UnifiedLog Only (MANDATORY)

All TMDB-related logging MUST use `UnifiedLog`:

```kotlin
// Correct
UnifiedLog.d(TAG) { "TMDB details fetched: tmdbRef=$tmdbRef" }

// Forbidden
Log.d(TAG, "...")        // Android Log
println("...")           // Console
logger.info("...")       // Other loggers
```

### T-19 No Secrets in Logs (MANDATORY)

NEVER log API keys or sensitive data:

```kotlin
// FORBIDDEN
UnifiedLog.d(TAG) { "Using API key: ${config.apiKey}" }

// ALLOWED
UnifiedLog.d(TAG) { "TMDB resolver enabled: hasApiKey=${config.apiKey.isNotBlank()}" }
```

### T-20 Standard Log Events (MANDATORY)

| Event | Level | When |
|-------|-------|------|
| `TMDB_DISABLED` | DEBUG | API key missing |
| `TMDB_DETAILS_HIT` | DEBUG | Details-by-ID success |
| `TMDB_SEARCH_ACCEPT` | DEBUG | Search match accepted |
| `TMDB_SEARCH_AMBIGUOUS` | WARN | Multiple close matches |
| `TMDB_SEARCH_REJECT` | DEBUG | No acceptable match |
| `TMDB_CACHE_HIT` | DEBUG | Cache hit |
| `TMDB_API_ERROR` | ERROR | API call failed |

---

## 9. Dependency Constraints

### T-21 Single Module Dependency (MANDATORY)

TMDB API dependency (`app.moviebase:tmdb-api`) MUST exist **only** in `:core:metadata-normalizer`:

```kotlin
// core/metadata-normalizer/build.gradle.kts
dependencies {
    implementation(libs.tmdb.api)  // ONLY here
}
```

### T-22 Verification Command (MANDATORY)

Before merge, verify no leakage:

```bash
# Must return empty
grep -rn "app\\.moviebase" --include="*.kt" --include="*.kts" \
    pipeline feature infra/transport-* playback player app-v2
```

---

## 10. Implementation Status

| Component | Status | Module |
|-----------|--------|--------|
| Version Catalog | ✅ | `gradle/libs.versions.toml` |
| TmdbConfig | 🔲 | `core/metadata-normalizer` |
| TmdbMatchScore | 🔲 | `core/metadata-normalizer` |
| DefaultTmdbMetadataResolver | 🔲 | `core/metadata-normalizer` |
| Resolve-State Schema | 🔲 | `core/persistence` |
| Repository Query APIs | 🔲 | `core/persistence` |
| TMDB Workers | 🔲 Deferred | Task 5 |

---

## 11. Explicitly Deferred to Task 5

The following are **NOT** part of this contract and will be implemented in Task 5:

- `TmdbEnrichmentOrchestratorWorker`
- `TmdbEnrichmentBatchWorker`
- `TmdbEnrichmentContinuationWorker`
- Cooldown/attempt enforcement in workers
- Batch processing logic

This contract defines the **resolver and schema** only. Workers consume the resolver and update resolve-state.
