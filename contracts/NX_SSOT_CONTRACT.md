# NX SSOT Contract

**Version:** 1.0  
**Status:** BINDING  
**Created:** 2026-01-09  
**Parent Issue:** #621 (OBX PLATIN Refactor)

---

## 1. Purpose & Scope

This contract defines the **Single Source of Truth (SSOT)** rules for the NX_* entity schema. All agents, code, and documentation MUST conform to these rules.

### 1.1 What This Contract Covers

- NX_* entity schema definitions
- Key format specifications
- Ingest decision rules
- Classification heuristics
- Invariant enforcement

### 1.2 What This Contract Does NOT Cover

- UI implementation details (see feature/* modules)
- Cloud sync implementation (future scope)
- Live channel canonical merging (deferred to Live-Canonical-V2)

---

## 2. SSOT Invariants (AUTHORITATIVE)

These invariants are **BINDING** and must be enforced at all times:

### INV-1: Ingest Ledger Completeness
> Every discovered ingest candidate creates exactly one `NX_IngestLedger` entry with decision `ACCEPTED | REJECTED | SKIPPED`. **No silent drops.**

```kotlin
// ✅ CORRECT
fun processCandidate(raw: RawMediaMetadata): IngestResult {
    val decision = evaluateCandidate(raw)
    ledgerRepository.insert(NX_IngestLedger(
        candidateHash = raw.computeHash(),
        decision = decision,
        reasonCode = decision.reasonCode,
        processedAt = Clock.System.now()
    ))
    return when (decision) {
        ACCEPTED -> acceptAndResolve(raw)
        REJECTED, SKIPPED -> IngestResult.NotAccepted(decision)
    }
}

// ❌ FORBIDDEN: Silent drop without ledger entry
fun processCandidate(raw: RawMediaMetadata): IngestResult {
    if (raw.durationMs < 60_000) return null // WRONG - no ledger!
}
```

### INV-2: Work Resolution Rule
> Every `ACCEPTED` ingest candidate triggers exactly one NX_Work resolution attempt, which either **links to an existing NX_Work** or **creates a new one**.

```kotlin
// Resolution flow (deterministic)
fun resolveWork(raw: RawMediaMetadata): NX_Work {
    // 1. Try authority match (TMDB > IMDB > TVDB)
    findByAuthorityId(raw.tmdbId)?.let { return it }
    findByAuthorityId(raw.imdbId)?.let { return it }
    
    // 2. Try title+year match
    findByTitleYear(raw.title, raw.year)?.let { return it }
    
    // 3. Create new work
    return createNewWork(raw)
}
```

### INV-3: Work Completeness
> Every `NX_Work` visible in the UI must have:
> - ≥1 `NX_WorkSourceRef`
> - ≥1 `NX_WorkVariant` with valid `playbackHints`

```kotlin
// Validation query
fun findIncompleteWorks(): List<NX_Work> {
    return workBox.query()
        .filter { work ->
            work.sourceRefs.isEmpty() || work.variants.isEmpty()
        }
        .build().find()
}
```

### INV-4: Multi-Account Safety
> `accountKey` is **mandatory** in all `sourceKey` values. Format: `{sourceType}:{accountIdentifier}`

```kotlin
// ✅ CORRECT
val sourceKey = "xtream:user@server.com:vod:12345"
val sourceKey = "telegram:+491234567890:chat:123:msg:456"

// ❌ FORBIDDEN: Missing accountKey
val sourceKey = "xtream:vod:12345" // WRONG - no account!
```

### INV-5: Profile-Scoped Resume
> Resume state is stored in `NX_WorkUserState` per profile, using percentage-based positioning for cross-source compatibility.

```kotlin
data class NX_WorkUserState(
    val workKey: String,
    val profileId: Long,
    val resumePercent: Float, // 0.0 to 1.0
    val lastPositionMs: Long, // For same-variant resume
    val variantKey: String?,  // Last played variant
    val updatedAt: Long
)
```

### INV-6: UI SSOT Rule (🚨 UNUMGEHBAR / NON-NEGOTIABLE)

> **🚨 HARD RULE:** UI reads **exclusively** from the NX_* entity graph.
> **No UI code, ViewModel, or feature module may import or query legacy `Obx*` entities.**

This invariant is **binding and non-negotiable**. Documented in:
- `AGENTS.md` Section 4.3.3 (Primary Authority)
- `.github/copilot-instructions.md` (Cloud Agent Reference)

**SSOT Entity Hierarchy:**
```text
NX_Work (UI SSOT) ─────────────────────────────────────────────
    │               │               │               │
    ▼               ▼               ▼               ▼
NX_WorkSourceRef  NX_WorkVariant  NX_WorkRelation  NX_WorkUserState
(Source origin)   (Playback info) (Series↔Eps)    (Resume per profile)
```

**What Each Screen Reads:**
| Screen | Primary Repository | Related Repositories |
|--------|-------------------|---------------------|
| Home | `NxWorkRepository` | `NxWorkUserStateRepository` |
| Library | `NxWorkRepository` | - |
| Detail | `NxWorkRepository` | `NxWorkRelationRepository` |
| Live TV | `NxWorkRepository` | - |
| Search | `NxWorkRepository` | - |
| Player | `NxWorkVariantRepository` | `NxWorkUserStateRepository` |

```kotlin
// ✅ CORRECT
class HomeRepository @Inject constructor(
    private val nxWorkRepository: NxWorkRepository
) {
    fun getContinueWatching() = nxWorkRepository.getContinueWatching()
}

// ❌ FORBIDDEN
class HomeRepository @Inject constructor(
    private val obxCanonicalMediaRepository: ObxCanonicalMediaRepository // WRONG!
)
```

---

## 3. Key Format Specifications

### 3.1 workKey
Unique identifier for a canonical work.

**Format:** `{workType}:{authorityType}:{authorityId}` or `{workType}:{sourceType}:{accountKey}:{sourceSpecificId}`

**Examples:**
```
movie:tmdb:12345
series:imdb:tt1234567
episode:tmdb:tv:456:s:1:e:3
live:xtream:user@server:stream:789
clip:telegram:+49123:chat:100:msg:200
```

**Generation Rules:**
1. If TMDB ID available → use `{workType}:tmdb:{tmdbId}`
2. Else if IMDB ID available → use `{workType}:imdb:{imdbId}`
3. Else if TVDB ID available → use `{workType}:tvdb:{tvdbId}`
4. Else → use source-specific format with accountKey

### 3.2 authorityKey
Identifier for external metadata authorities.

**Format:** `{authorityType}:{authorityId}`

**Examples:**
```
tmdb:movie:12345
tmdb:tv:67890
imdb:tt1234567
tvdb:series:111222
```

### 3.3 sourceKey
Unique identifier for a specific source variant.

**Format:** `{sourceType}:{accountKey}:{sourceSpecificPath}`

**Examples:**
```
xtream:user@server.com:vod:12345
xtream:user@server.com:series:100:s:2:e:5
telegram:+491234567890:chat:100500:msg:42
local:default:/movies/file.mp4
```

**accountKey Formats:**
| SourceType | accountKey Format | Example |
|------------|-------------------|---------|
| xtream | `{username}@{server}` | `user@xtream.server.com` |
| telegram | `{phoneNumber}` | `+491234567890` |
| local | `default` | `default` |

### 3.4 variantKey
Unique identifier for a playback variant (quality/encoding).

**Format:** `{sourceKey}:{quality}:{encoding}`

**Examples:**
```
xtream:user@server:vod:123:1080p:h264
xtream:user@server:vod:123:4k:hevc
telegram:+49123:chat:100:msg:42:720p:h264
```

---

## 4. IngestReasonCode Enum

All ingest decisions must use these standardized reason codes:

```kotlin
enum class IngestReasonCode {
    // ACCEPTED reasons
    ACCEPTED_NEW_WORK,           // Created new NX_Work
    ACCEPTED_LINKED_EXISTING,    // Linked to existing NX_Work
    ACCEPTED_ADDED_VARIANT,      // Added new variant to existing work
    
    // REJECTED reasons
    REJECTED_TOO_SHORT,          // Duration < 60 seconds
    REJECTED_NOT_PLAYABLE,       // No valid playback URL
    REJECTED_INVALID_FORMAT,     // Unsupported container/codec
    REJECTED_DUPLICATE_EXACT,    // Exact duplicate already exists
    REJECTED_ADULT_FILTERED,     // Adult content filtered by profile
    REJECTED_BLOCKED_CATEGORY,   // Category blocked by profile
    
    // SKIPPED reasons
    SKIPPED_ALREADY_EXISTS,      // Identical variant already in DB
    SKIPPED_PENDING_AUTHORITY,   // Waiting for TMDB resolution
    SKIPPED_RATE_LIMITED,        // API rate limit hit, retry later
}
```

---

## 5. Classification Heuristics

### 5.1 WorkType Detection

| Condition | WorkType |
|-----------|----------|
| Duration < 60s | `CLIP` |
| Duration < 20min AND no series markers | `CLIP` |
| Duration 20min-60min AND series markers | `EPISODE` |
| Duration > 60min AND movie markers | `MOVIE` |
| Has season/episode numbers | `EPISODE` |
| Is streaming channel | `LIVE` |
| Parent is series work | `EPISODE` |
| Default fallback | `MOVIE` |

### 5.2 Series Markers
- Title contains `S##E##` pattern
- Title contains `Season X Episode Y`
- Source metadata has `seriesId`
- TMDB response indicates TV show

### 5.3 Movie Markers
- Duration > 60 minutes
- No season/episode indicators
- TMDB response indicates movie
- Release year present in title

---

## 6. Entity Specifications

### 6.1 NX_Work (UI SSOT)

```kotlin
@Entity
data class NX_Work(
    @Id var id: Long = 0,
    
    // Identity
    @Unique @Index var workKey: String = "",
    @Index var workType: String = "", // MOVIE|SERIES|EPISODE|LIVE|CLIP
    
    // Display metadata
    var title: String = "",
    @Index var titleLower: String = "",
    var originalTitle: String? = null,
    @Index var year: Int? = null,
    var plot: String? = null,
    var genres: String? = null, // JSON array
    var runtime: Int? = null, // minutes
    
    // Images (JSON)
    var posterUrl: String? = null,
    var backdropUrl: String? = null,
    var imagesJson: String? = null,
    
    // Rating
    var rating: Float? = null,
    var voteCount: Int? = null,
    
    // Status
    @Index var isComplete: Boolean = false, // Has all required refs
    @Index var needsReview: Boolean = false, // Manual review needed
    @Index var tmdbResolveState: String = "PENDING", // PENDING|RESOLVED|FAILED|NOT_FOUND
    
    // Timestamps
    @Index var createdAt: Long = 0,
    @Index var updatedAt: Long = 0,
    
    // Relations (manual joins via workKey)
    // - NX_WorkAuthorityRef.workKey
    // - NX_WorkSourceRef.workKey
    // - NX_WorkVariant.workKey
    // - NX_WorkRelation.parentWorkKey / childWorkKey
    // - NX_WorkUserState.workKey
)
```

### 6.2 NX_WorkSourceRef

```kotlin
@Entity
data class NX_WorkSourceRef(
    @Id var id: Long = 0,
    
    @Index var workKey: String = "",
    @Unique @Index var sourceKey: String = "", // Includes accountKey!
    @Index var sourceType: String = "", // xtream|telegram|local
    @Index var accountKey: String = "", // Extracted for queries
    
    // Source-specific identifiers
    var vodId: Int? = null,
    var seriesId: Int? = null,
    var episodeId: Int? = null,
    var streamId: Int? = null,
    var chatId: Long? = null,
    var messageId: Long? = null,
    
    // Availability
    @Index var isAvailable: Boolean = true,
    var lastCheckedAt: Long = 0,
    
    @Index var addedAt: Long = 0,
    @Index var updatedAt: Long = 0,
)
```

### 6.3 NX_WorkVariant

```kotlin
@Entity
data class NX_WorkVariant(
    @Id var id: Long = 0,
    
    @Index var workKey: String = "",
    @Unique @Index var variantKey: String = "",
    @Index var sourceKey: String = "",
    
    // Quality info
    var width: Int? = null,
    var height: Int? = null,
    @Index var qualityLabel: String? = null, // SD|720p|1080p|4K
    var bitrate: Long? = null,
    var codec: String? = null,
    
    // File info
    var containerFormat: String? = null, // mp4|mkv|ts
    var sizeBytes: Long? = null,
    @Index var language: String? = null,
    
    // Playback hints (JSON)
    var playbackHintsJson: String? = null,
    
    // Streaming
    var supportsStreaming: Boolean = true,
    var playbackUrl: String? = null,
    
    @Index var createdAt: Long = 0,
    @Index var updatedAt: Long = 0,
)
```

### 6.4 NX_IngestLedger

```kotlin
@Entity
data class NX_IngestLedger(
    @Id var id: Long = 0,
    
    // Candidate identification
    @Index var candidateHash: String = "", // Hash of raw metadata
    @Index var sourceType: String = "",
    @Index var accountKey: String = "",
    
    // Decision
    @Index var decision: String = "", // ACCEPTED|REJECTED|SKIPPED
    @Index var reasonCode: String = "", // IngestReasonCode value
    var reasonDetail: String? = null,
    
    // Outcome (for ACCEPTED)
    var workKey: String? = null,
    var sourceKey: String? = null,
    var variantKey: String? = null,
    var isNewWork: Boolean = false,
    
    // Metadata snapshot
    var candidateTitle: String? = null,
    var candidateDuration: Int? = null,
    
    @Index var processedAt: Long = 0,
)
```

---

## 7. Runtime Mode Switches

### 7.1 CatalogReadMode
```kotlin
enum class CatalogReadMode {
    LEGACY,     // Read from Obx* entities only
    NX_ONLY,    // Read from NX_* entities only
    DUAL_READ   // Read from both, prefer NX_*
}
```

### 7.2 CatalogWriteMode
```kotlin
enum class CatalogWriteMode {
    LEGACY,     // Write to Obx* entities only
    NX_ONLY,    // Write to NX_* entities only
    DUAL_WRITE  // Write to both for validation
}
```

### 7.3 Default Configuration
```kotlin
object CatalogModeDefaults {
    val READ_MODE = CatalogReadMode.LEGACY  // Safe default
    val WRITE_MODE = CatalogWriteMode.LEGACY // Safe default
}
```

---

## 8. Validation Queries

### 8.1 Find Incomplete Works
```kotlin
// Works missing required relations
nxWorkBox.query()
    .apply(NX_Work_.isComplete.equal(false))
    .build().find()
```

### 8.2 Find Works Needing Review
```kotlin
nxWorkBox.query()
    .apply(NX_Work_.needsReview.equal(true))
    .build().find()
```

### 8.3 Validate Invariant Coverage
```kotlin
// All ingest candidates have ledger entries
val totalCandidates = computeTotalCandidates()
val ledgerCount = ledgerBox.count()
assert(totalCandidates == ledgerCount) { "INV-1 violation: missing ledger entries" }

// All accepted candidates have works
val acceptedCount = ledgerBox.query()
    .apply(NX_IngestLedger_.decision.equal("ACCEPTED"))
    .build().count()
val worksWithSources = workBox.query()
    .apply(NX_Work_.isComplete.equal(true))
    .build().count()
assert(acceptedCount <= worksWithSources) { "INV-2 violation: orphan accepts" }
```

---

## 9. References

- **Parent Issue:** #621
- **Roadmap:** `docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md`
- **Entity Docs:** `docs/v2/obx/`
- **Architecture Authority:** `AGENTS.md` Section 4
- **Normalization Contract:** `/contracts/MEDIA_NORMALIZATION_CONTRACT.md`

---

## 10. Change Log

| Date | Version | Author | Changes |
|------|---------|--------|---------|
| 2026-01-09 | 1.0 | Copilot | Initial contract creation |
