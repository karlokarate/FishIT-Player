# TMDB Enrichment Contract (v2 MVP)

**Version:** 1.0  
**Last Updated:** 2025-12-18  
**Status:** Binding Contract

## 0. Relationship to Existing Contracts

This contract is **additive** and must be read together with:

* **Naming & vocabulary:** `GLOSSARY_v2_naming_and_modules.md`
* **Canonical media + normalization responsibilities:** `MEDIA_NORMALIZATION_CONTRACT.md`
* **Logging rules:** `LOGGING_CONTRACT_V2.md`
* **TMDB canonical identity & imaging:** `TMDB Canonical Identity & Imaging SSOT Contract (v2).md`
* **Agent rules:** `AGENTS.md`

---

## 1. Purpose

Define **binding rules** for TMDB enrichment in the v2 architecture, specifically:

1. **TMDB is enrichment-only** (resolver-only; never in pipelines or UI)
2. **Canonical key preference:** `tmdb:<tmdbId>` when available
3. **Race-free image rule:** TMDB images are SSOT only when explicitly populated
4. **Upgrade-only policy:** Source images → TMDB images; never automatic reversion
5. **Persisted attempt state:** Track resolution attempts for future worker coordination

---

## 2. Scope

Binding for all v2 modules that touch TMDB enrichment:

* `:core:metadata-normalizer` (TMDB resolver implementation)
* `:infra:data-*` (persistence of TMDB state)
* `:pipeline:*` (MUST NOT call TMDB; pass-through only)
* `:feature:*` (UI consuming enriched metadata)
* `WorkManager` workers (future; defer to follow-up task)

---

## 3. Core Principles

### 3.1 TMDB is Enrichment-Only

**TMDB enrichment happens ONLY in `:core:metadata-normalizer`.**

**Pipelines MUST NOT:**
- Call TMDB API directly
- Implement TMDB search/match logic
- Guess or infer TMDB IDs
- Import `app.moviebase.tmdb` classes

**Pipelines MAY:**
- Pass through TMDB IDs from trusted sources (e.g., Xtream, Plex when they provide them)
- Populate `externalIds.tmdbId` in `RawMediaMetadata` when source provides it

**UI MUST NOT:**
- Call TMDB API
- Import TMDB client classes
- Implement image selection logic (use canonical fields only)

### 3.2 Canonical Key Preference

When a valid TMDB ID is known:
- `canonicalId.key = "tmdb:<tmdbId>"`
- This is the **highest priority identity**

When TMDB ID is not available:
- Fallback to deterministic canonical identity (title/year or title/SxxExx)
- See `MEDIA_NORMALIZATION_CONTRACT.md` for fallback rules

### 3.3 Race-Free Image Rule (MANDATORY)

**Critical:** `tmdbId` being present does NOT mean TMDB images are ready.

**TMDB images are SSOT only when:**
- `NormalizedMediaMetadata.poster` contains TMDB-sourced `ImageRef.Http` with TMDB URL, OR
- Canonical persistence entity has `tmdbPosterRef` / `tmdbBackdropRef` populated

**UI MUST choose images by priority:**

```kotlin
// Preferred poster selection
val primaryPoster = canonical.tmdbPosterRef 
    ?: source.bestPosterRef 
    ?: placeholderImage
```

**Never automatically downgrade:**
- Once TMDB images are set, they MUST NOT be automatically replaced with source images
- Manual override is reserved for future feature (Phase 2+)

### 3.4 Upgrade-Only Policy

**Image upgrade flow:**
1. Initial state: Source provides `poster`, `backdrop`, `thumbnail` in `RawMediaMetadata`
2. Normalizer copies source images to `NormalizedMediaMetadata` (provisional)
3. TMDB resolver populates TMDB images when available
4. Canonical entity stores both: `tmdbPosterRef` (SSOT) and `sourcePosterRef` (fallback)

**Forbidden:**
- Reverting TMDB images back to source images automatically
- Replacing TMDB images with different TMDB images without version tracking

---

## 4. TMDB Resolver Architecture

### 4.1 Resolver Interface

```kotlin
interface TmdbMetadataResolver {
    /**
     * Resolve TMDB metadata for a raw media item.
     * 
     * @param raw Raw metadata from pipeline
     * @return Enriched metadata with TMDB fields populated when found
     */
    suspend fun resolve(raw: RawMediaMetadata): TmdbResolutionResult
}

sealed interface TmdbResolutionResult {
    data class Success(
        val tmdbId: TmdbId,
        val canonicalTitle: String,
        val year: Int?,
        val poster: ImageRef.Http?,
        val backdrop: ImageRef.Http?,
        val externalIds: ExternalIds
    ) : TmdbResolutionResult
    
    data class NotFound(val reason: String) : TmdbResolutionResult
    data class Ambiguous(val reason: String, val candidates: List<TmdbCandidate>) : TmdbResolutionResult
    object Disabled : TmdbResolutionResult
    data class Failed(val error: Throwable) : TmdbResolutionResult
}
```

### 4.2 Resolution Paths

**Path A: TMDB ID Already Present**
1. Extract `tmdbId` from `raw.externalIds.tmdbId`
2. Fetch details by ID (no search)
3. Populate SSOT fields: `canonicalTitle`, `year`, `poster`, `backdrop`
4. **MUST NOT** overwrite `tmdbId` with different ID (MVP: warn + keep original)

**Path B: TMDB ID Missing**
1. Search deterministically using `canonicalTitle` (+ `year` when available)
2. Apply scoring rules (see Section 5)
3. On ACCEPT: Set `tmdbId` and populate SSOT fields
4. On AMBIGUOUS: Do NOT set `tmdbId` (log candidates for manual review)
5. On REJECT: Do NOT set `tmdbId`

### 4.3 Configuration

```kotlin
data class TmdbConfig(
    val apiKey: String,
    val language: String = "en-US",
    val region: String? = null
)
```

**API Key Source:**
- MUST come from non-committed source (BuildConfig, gradle.properties, environment)
- MUST NOT be hardcoded in source
- If missing/blank: Resolver returns `TmdbResolutionResult.Disabled` (no crash)

**Logging:**
- MUST use `UnifiedLog` only (no `Log.*`, no `Timber.*`)
- MUST NEVER log `apiKey` or full URLs with API keys
- Tag: `core/metadata-normalizer/DefaultTmdbMetadataResolver`

---

## 5. Deterministic Match Scoring

### 5.1 Score Components

```kotlin
data class TmdbMatchScore(
    val titleSimilarity: Int,    // 0..60 points
    val yearScore: Int,           // 0..20 points
    val kindAgreement: Int,       // 0..10 points
    val episodeExact: Int         // 0..10 points (episodes only)
) {
    val total: Int get() = titleSimilarity + yearScore + kindAgreement + episodeExact
}
```

**Title Similarity (0..60):**
- Exact match (normalized, case-insensitive): 60
- Levenshtein distance scaling: 60 * (1 - distance / maxLength)
- Minimum: 0

**Year Score (0..20):**
- Exact match: 20
- ±1 year: 15
- ±2 years: 10
- ±3 years: 5
- >3 years or missing: 0

**Kind Agreement (0..10):**
- MediaType matches (MOVIE ↔ movie, SERIES_EPISODE ↔ tv): 10
- Mismatch or unknown: 0

**Episode Exact (0..10, episodes only):**
- Season AND episode match exactly: 10
- Season matches, episode differs: 5
- No season/episode data: 0

### 5.2 Decision Rules (Binding)

**ACCEPT** if:
- `bestScore >= 85` AND
- `(bestScore - secondBestScore) >= 10`

**AMBIGUOUS** if:
- `bestScore >= 70` AND
- `(bestScore - secondBestScore) < 10`

**REJECT** otherwise:
- All scores < 70

**Edge Cases:**
- Single candidate with score >= 85: ACCEPT
- Single candidate with score < 85: REJECT
- No candidates: REJECT

---

## 6. Minimal Resolver Caching

### 6.1 Cache Requirements

**Two bounded in-memory LRU caches:**

1. **detailsByTmdbId**
   - Key: `TmdbId`
   - Value: TMDB detail response
   - TTL: 7 days
   - Max entries: 256

2. **searchByQueryKey**
   - Key: Normalized query string (deterministic: `"movie:title:year"` or `"tv:title:SxxExx"`)
   - Value: Search results with scores
   - TTL: 24 hours
   - Max entries: 256

### 6.2 Cache Key Normalization

**Query keys MUST be deterministic:**

```kotlin
fun buildSearchCacheKey(raw: RawMediaMetadata): String {
    val normalized = normalizeTitle(raw.originalTitle)
    return when (raw.mediaType) {
        MediaType.MOVIE -> "movie:$normalized:${raw.year ?: "unknown"}"
        MediaType.SERIES_EPISODE -> "tv:$normalized:S${raw.season}E${raw.episode}"
        else -> "unknown:$normalized"
    }
}
```

**Title normalization for cache keys:**
- Lowercase
- Remove special characters (keep alphanumeric and spaces)
- Collapse multiple spaces to single space
- Trim

---

## 7. Persisted TMDB Resolve State

### 7.1 Schema Fields (Canonical Entity)

Add to canonical persistence entity:

```kotlin
// TMDB Resolution State
var tmdbResolveState: TmdbResolveState = TmdbResolveState.UNRESOLVED
var tmdbResolveAttempts: Int = 0
var lastTmdbAttemptAt: Long = 0L  // Unix epoch seconds
var tmdbNextEligibleAt: Long = 0L  // Unix epoch seconds
var tmdbLastFailureReason: TmdbFailureReason? = null
var tmdbLastResolvedAt: Long = 0L  // Unix epoch seconds
var tmdbResolvedBy: TmdbResolvedBy = TmdbResolvedBy.PASS_THROUGH

enum class TmdbResolveState {
    UNRESOLVED,
    RESOLVED,
    UNRESOLVABLE_PERMANENT,
    STALE_REFRESH_REQUIRED
}

enum class TmdbFailureReason {
    NOT_FOUND,
    AMBIGUOUS,
    API_ERROR,
    RATE_LIMIT,
    NETWORK_ERROR,
    DISABLED
}

enum class TmdbResolvedBy {
    PASS_THROUGH,      // tmdbId from source (Xtream/Plex)
    SEARCH_MATCH,      // tmdbId from resolver search
    MANUAL_OVERRIDE    // Reserved for future
}
```

### 7.2 Repository Query APIs

Add to canonical repository:

```kotlin
interface CanonicalMediaRepository {
    /**
     * Find candidates missing TMDB ID for worker processing.
     * Filters by:
     * - tmdbResolveState = UNRESOLVED
     * - tmdbNextEligibleAt <= now
     * - Ordered by priority (addedTimestamp DESC)
     */
    suspend fun findTmdbCandidatesMissingId(limit: Int): List<CanonicalMedia>
    
    /**
     * Find candidates needing TMDB refresh.
     * Filters by:
     * - tmdbResolveState = STALE_REFRESH_REQUIRED
     * - tmdbNextEligibleAt <= now
     */
    suspend fun findTmdbCandidatesStale(limit: Int): List<CanonicalMedia>
    
    /**
     * Mark item as TMDB resolved.
     */
    suspend fun markTmdbResolved(
        itemId: String,
        tmdbId: TmdbId,
        resolvedBy: TmdbResolvedBy,
        resolvedAt: Long = System.currentTimeMillis() / 1000
    )
    
    /**
     * Mark TMDB attempt as failed.
     */
    suspend fun markTmdbAttemptFailed(
        itemId: String,
        reason: TmdbFailureReason,
        nextEligibleAt: Long
    )
}
```

### 7.3 Attempt Logic (Deferred to Workers)

**MVP: DO NOT implement attempt/cooldown logic.**

Workers (follow-up task) will enforce:
- Exponential backoff on failures
- Rate limit respect
- Batch processing
- Priority queues

---

## 8. Testing Requirements

### 8.1 Resolver Tests (Mandatory)

1. **tmdbId present → details path**
   - Input: `RawMediaMetadata` with `externalIds.tmdbId`
   - Expected: Fetch details by ID, populate SSOT fields
   - No search performed

2. **tmdbId missing → search accept**
   - Input: `RawMediaMetadata` with good title + year
   - Mock: Search returns single strong match (score >= 85)
   - Expected: `TmdbResolutionResult.Success` with tmdbId set

3. **tmdbId missing → ambiguous**
   - Input: `RawMediaMetadata` with ambiguous title
   - Mock: Search returns multiple close matches (best score gap < 10)
   - Expected: `TmdbResolutionResult.Ambiguous`, tmdbId NOT set

4. **tmdbId missing → reject**
   - Input: `RawMediaMetadata` with poor match
   - Mock: Search returns weak matches (all scores < 70)
   - Expected: `TmdbResolutionResult.NotFound`, tmdbId NOT set

5. **API disabled**
   - Config: `apiKey` blank
   - Expected: `TmdbResolutionResult.Disabled` immediately, no API calls

6. **Scoring accuracy**
   - Pure unit tests for `TmdbMatchScore` calculation
   - Verify each score component (title, year, kind, episode)
   - Verify decision rules (ACCEPT/AMBIGUOUS/REJECT thresholds)

### 8.2 Pipeline Tests (Guardrail)

**Ensure NO pipeline module tests depend on TMDB:**
- Search for `app.moviebase.tmdb` imports in `pipeline/**` tests
- CI MUST fail if found

---

## 9. Guardrails & CI Checks

### 9.1 Import Restrictions

**Forbidden outside `:core:metadata-normalizer`:**
- `app.moviebase.tmdb.*` imports
- TMDB API URL patterns (`api.themoviedb.org`, `image.tmdb.org`)

**CI Check:**
```bash
# Fail if TMDB imports found in wrong places
grep -r "app.moviebase.tmdb" pipeline/** feature/** && exit 1 || exit 0
```

### 9.2 Build Verification

**MUST succeed:**
- Phone/tablet build: `./gradlew :app-v2:assembleDebug`
- FireTV 32-bit build: `./gradlew :app-v2:assembleDebug -PfireTvBuild=true`
- All tests: `./gradlew :core:metadata-normalizer:testDebugUnitTest`

---

## 10. Example Flows

### 10.1 Xtream VOD with TMDB ID

1. Xtream pipeline provides `RawMediaMetadata` with `externalIds.tmdbId = 12345`
2. Normalizer calls resolver with existing tmdbId
3. Resolver fetches details by ID (Path A)
4. Populates TMDB poster/backdrop URLs
5. Canonical entity: `tmdbResolveState = RESOLVED`, `tmdbResolvedBy = PASS_THROUGH`
6. UI renders TMDB images immediately

### 10.2 Telegram Video Without TMDB ID

1. Telegram pipeline provides `RawMediaMetadata` with caption "The Matrix 1999"
2. Normalizer calls resolver without tmdbId
3. Resolver searches TMDB (Path B)
4. Finds strong match (score 92)
5. Sets `tmdbId = 603`, populates TMDB fields
6. Canonical entity: `tmdbResolveState = RESOLVED`, `tmdbResolvedBy = SEARCH_MATCH`
7. UI transitions from Telegram thumbnail to TMDB poster

### 10.3 Ambiguous Title

1. Pipeline provides "Avatar" (no year)
2. Resolver searches, finds multiple matches:
   - Avatar (2009) - score 85
   - Avatar: The Last Airbender (2010) - score 82
3. Gap < 10 → `TmdbResolutionResult.Ambiguous`
4. `tmdbId` NOT set
5. Canonical entity: `tmdbResolveState = UNRESOLVED`, `tmdbLastFailureReason = AMBIGUOUS`
6. Worker may retry later with better heuristics

---

## 11. Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-12-18 | Initial MVP contract: resolver-only, race-free images, persisted state schema |

---

## 12. Future Enhancements (Out of Scope for MVP)

- WorkManager workers for background enrichment
- Manual TMDB ID override UI
- Multi-language support (currently en-US only)
- Image size/quality selection policies
- Stale data refresh workers
- TMDB metadata versioning

---

This contract is binding for all v2 TMDB enrichment work.
