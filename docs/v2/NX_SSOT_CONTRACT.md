# NX_SSOT_CONTRACT.md

**Version:** 1.0  
**Status:** Binding  
**Scope:** OBX PLATIN Refactor – Phase 0.1 (Issue #621)  
**Created:** 2026-01-09

---

## Executive Summary

This contract defines the **single source of truth (SSOT)** rules for the NX_ entity graph that replaces the legacy Obx* persistence layer in FishIT Player v2.

### Core Principle: Deterministic Work Graph

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         NX WORK GRAPH (SSOT)                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   INGEST                       RESOLUTION                    UI         │
│   ──────                       ──────────                    ──         │
│                                                                         │
│   RawMediaMetadata ─────►  Normalizer Gate                              │
│         │                       │                                       │
│         │                       ├── REJECT ──► NX_IngestLedger (SKIP)   │
│         │                       │                                       │
│         ▼                       ▼                                       │
│   Classification ────────► Link-or-Create                               │
│   (Clip/Episode/Movie)          │                                       │
│         │                       ├── FOUND ───► Link to existing Work    │
│         │                       │                                       │
│         ▼                       ▼                                       │
│   NX_IngestLedger ◄──── NX_Work (created if new)                        │
│   (ACCEPTED)                    │                                       │
│         │                       ├──► NX_WorkSourceRef                   │
│         │                       │                                       │
│         │                       ├──► NX_WorkVariant                     │
│         │                       │                                       │
│         ▼                       ▼                                       │
│   UI reads ONLY ◄──────── NX_Work Graph                                 │
│   from NX_*                                                             │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 1. Key Format Specifications

All keys in the NX_ graph follow deterministic, collision-free formats.

### 1.1 workKey

**Purpose:** Globally unique identifier for a canonical media work.

**Format:**

```
workKey = "<workType>:<canonicalSlug>:<year|LIVE>"
```

**Components:**

| Component | Description | Example |
|-----------|-------------|---------|
| `workType` | One of: `movie`, `episode`, `series`, `clip`, `live`, `audiobook` | `movie` |
| `canonicalSlug` | Lowercase, hyphenated, normalized title | `the-matrix` |
| `year` | 4-digit year for VOD, or `LIVE` for live content | `1999` |

**Examples:**

```
movie:the-matrix:1999
episode:breaking-bad:s01e01
series:breaking-bad:2008
clip:funny-cat-video:2024
live:sport1:LIVE
audiobook:harry-potter-1:1997
```

**Rules:**

1. `canonicalSlug` MUST be lowercase ASCII with hyphens replacing spaces
2. Special characters MUST be stripped (not replaced)
3. Leading articles ("The", "A", "An") are preserved but lowercased
4. For episodes: `workKey` represents the EPISODE, not the series
5. For series root: use `series:<slug>:<year>`

**Slug Generation Algorithm:**

```kotlin
fun generateCanonicalSlug(title: String): String {
    return title
        .lowercase()
        .trim()
        .replace(Regex("[^a-z0-9\\s-]"), "")  // Strip special chars
        .replace(Regex("\\s+"), "-")           // Spaces to hyphens
        .replace(Regex("-+"), "-")             // Collapse multiple hyphens
        .trim('-')                              // Trim leading/trailing hyphens
}
```

---

### 1.2 authorityKey

**Purpose:** Links a work to an external authority (TMDB, IMDB, TVDB, etc.)

**Format:**

```
authorityKey = "<authority>:<type>:<id>"
```

**Components:**

| Component | Description | Valid Values |
|-----------|-------------|--------------|
| `authority` | External service identifier | `tmdb`, `imdb`, `tvdb`, `trakt` |
| `type` | Media type qualifier | `movie`, `tv`, `episode`, `person` |
| `id` | Authority-specific identifier | String (IMDB) or Int (TMDB) |

**Examples:**

```
tmdb:movie:603          // The Matrix on TMDB
tmdb:tv:1396            // Breaking Bad (series) on TMDB
tmdb:episode:62085      // Breaking Bad S01E01 on TMDB
imdb:movie:tt0133093    // The Matrix on IMDB
tvdb:series:81189       // Breaking Bad on TVDB
```

**Rules:**

1. `authority` MUST be lowercase
2. `type` MUST match the authority's type system
3. `id` format depends on authority (IMDB uses `tt` prefix, TMDB uses integers)
4. Legacy `tmdb:<id>` format is deprecated; new writes MUST use `tmdb:<type>:<id>`

---

### 1.3 sourceKey

**Purpose:** Uniquely identifies a source reference within the multi-account context.

**Format:**

```
sourceKey = "<sourceType>:<accountKey>:<sourceId>"
```

**Components:**

| Component | Description | Example |
|-----------|-------------|---------|
| `sourceType` | Pipeline source identifier | `telegram`, `xtream`, `local`, `plex` |
| `accountKey` | Account-scoped identifier (MANDATORY) | `tg:123456789`, `xtream:provider1:user1` |
| `sourceId` | Source-specific item identifier | `chat:-100123:msg:456`, `vod:12345` |

**Account Key Formats by Source:**

| Source | accountKey Format | Example |
|--------|-------------------|---------|
| Telegram | `tg:<userId>` | `tg:123456789` |
| Xtream | `xtream:<serverHost>:<username>` | `xtream:provider.com:john` |
| Local | `local:<deviceId>` | `local:device-abc123` |
| Plex | `plex:<serverId>:<userId>` | `plex:xyz789:user1` |

**Examples:**

```
telegram:tg:123456789:chat:-100123456:msg:789012
xtream:xtream:provider.com:john:vod:12345
local:local:device-abc123:file:/storage/movies/matrix.mkv
```

**Rules:**

1. `accountKey` is **MANDATORY** – no sourceKey without account context
2. `sourceId` must be stable within the source (e.g., Telegram remoteId, not fileId)
3. Multi-account support requires unique accountKeys per login

---

### 1.4 variantKey

**Purpose:** Uniquely identifies a playback variant (quality/encoding/language).

**Format:**

```
variantKey = "<sourceKey>#<qualityTag>:<languageTag>"
```

**Components:**

| Component | Description | Example |
|-----------|-------------|---------|
| `sourceKey` | Parent source reference | (see 1.3) |
| `qualityTag` | Resolution or quality indicator | `1080p`, `4k`, `sd`, `hd` |
| `languageTag` | Audio language (ISO 639-1) | `en`, `de`, `original` |

**Examples:**

```
telegram:tg:123456789:chat:-100123:msg:456#1080p:en
xtream:xtream:provider.com:john:vod:12345#4k:original
local:local:device-abc123:file:/movies/matrix.mkv#source:original
```

**Rules:**

1. `qualityTag` defaults to `source` if unknown
2. `languageTag` defaults to `original` if unknown
3. Same source may have multiple variants (multi-quality Xtream, etc.)

---

## 2. Ingest Reason Codes

All ingest operations MUST produce exactly one `NX_IngestLedger` entry.

### 2.1 IngestDecision Enum

```kotlin
enum class IngestDecision {
    ACCEPTED,   // Item was ingested into NX_Work graph
    REJECTED,   // Item was explicitly rejected (invalid/unsupported)
    SKIPPED     // Item was skipped (duplicate/already exists)
}
```

### 2.2 IngestReasonCode Enum

```kotlin
enum class IngestReasonCode(
    val decision: IngestDecision,
    val description: String
) {
    // ═══════════════════════════════════════════════════════════════════
    // ACCEPTED: Item ingested successfully
    // ═══════════════════════════════════════════════════════════════════
    
    ACCEPTED_NEW_WORK(
        IngestDecision.ACCEPTED,
        "New NX_Work created for this item"
    ),
    
    ACCEPTED_LINKED_EXISTING(
        IngestDecision.ACCEPTED,
        "Linked to existing NX_Work (canonical match)"
    ),
    
    ACCEPTED_NEW_VARIANT(
        IngestDecision.ACCEPTED,
        "New variant added to existing NX_Work"
    ),
    
    ACCEPTED_NEW_SOURCE(
        IngestDecision.ACCEPTED,
        "New source reference added to existing NX_Work"
    ),
    
    // ═══════════════════════════════════════════════════════════════════
    // REJECTED: Item explicitly rejected (will not be ingested)
    // ═══════════════════════════════════════════════════════════════════
    
    REJECTED_TOO_SHORT(
        IngestDecision.REJECTED,
        "Duration below minimum threshold (< 60 seconds for non-clip)"
    ),
    
    REJECTED_NOT_PLAYABLE(
        IngestDecision.REJECTED,
        "No playable variant available (missing URL/file)"
    ),
    
    REJECTED_INVALID_METADATA(
        IngestDecision.REJECTED,
        "Essential metadata missing or malformed"
    ),
    
    REJECTED_UNSUPPORTED_FORMAT(
        IngestDecision.REJECTED,
        "Media format not supported for playback"
    ),
    
    REJECTED_ADULT_CONTENT(
        IngestDecision.REJECTED,
        "Adult content filtered by profile rules"
    ),
    
    REJECTED_BLOCKED_SOURCE(
        IngestDecision.REJECTED,
        "Source is explicitly blocked by user/profile"
    ),
    
    REJECTED_PARSE_ERROR(
        IngestDecision.REJECTED,
        "Failed to parse source metadata"
    ),
    
    // ═══════════════════════════════════════════════════════════════════
    // SKIPPED: Item not processed (already handled or deferred)
    // ═══════════════════════════════════════════════════════════════════
    
    SKIPPED_DUPLICATE_SOURCE(
        IngestDecision.SKIPPED,
        "Exact sourceKey already exists in graph"
    ),
    
    SKIPPED_PENDING_MIGRATION(
        IngestDecision.SKIPPED,
        "Item deferred to migration worker"
    ),
    
    SKIPPED_LIVE_CHANNEL(
        IngestDecision.SKIPPED,
        "Live channel deferred (no canonical merge in Phase 0-6)"
    ),
    
    SKIPPED_RATE_LIMITED(
        IngestDecision.SKIPPED,
        "Ingest rate limit exceeded, will retry"
    );
    
    companion object {
        fun fromDecision(decision: IngestDecision): List<IngestReasonCode> =
            entries.filter { it.decision == decision }
    }
}
```

### 2.3 NX_IngestLedger Entity

```kotlin
@Entity
data class NX_IngestLedger(
    @Id var id: Long = 0,
    
    // Source identification
    @Index var sourceKey: String = "",
    var sourceType: String = "",           // telegram, xtream, local, etc.
    var accountKey: String = "",
    
    // Decision
    var decision: String = "",             // ACCEPTED, REJECTED, SKIPPED
    var reasonCode: String = "",           // IngestReasonCode.name
    var reasonDetail: String? = null,      // Additional context (error message, etc.)
    
    // Timestamps
    var ingestedAt: Long = 0,              // Unix millis
    var processedAt: Long = 0,             // Unix millis (when decision was made)
    
    // Link to work (only for ACCEPTED)
    var linkedWorkKey: String? = null,
    
    // Raw input snapshot (for debugging)
    var rawTitleSnapshot: String? = null,
    var rawDurationMs: Long? = null,
    var rawMediaType: String? = null
)
```

**Invariants:**

1. Every ingest attempt creates EXACTLY ONE ledger entry
2. `sourceKey` + `ingestedAt` must be unique (no duplicate processing)
3. `linkedWorkKey` is non-null IFF `decision == ACCEPTED`
4. Silent drops are **FORBIDDEN** – every item must have a ledger trace

---

## 3. Classification Heuristics

Content classification determines `workType` assignment.

### 3.1 Duration-Based Classification

| Classification | Duration Threshold | workType |
|----------------|-------------------|----------|
| Short Clip | < 60 seconds | `clip` |
| Standard Content | ≥ 60 seconds | `movie`, `episode`, or `series` |
| Feature Film | ≥ 40 minutes (2,400,000 ms) | Likely `movie` |
| Episode | Has season/episode metadata | `episode` |

### 3.2 Classification Rules (Priority Order)

```kotlin
fun classifyWorkType(raw: RawMediaMetadata): WorkType {
    // 1. Explicit type from source takes precedence
    if (raw.mediaType != MediaType.UNKNOWN) {
        return when (raw.mediaType) {
            MediaType.MOVIE -> WorkType.MOVIE
            MediaType.EPISODE -> WorkType.EPISODE
            MediaType.SERIES -> WorkType.SERIES
            MediaType.LIVE -> WorkType.LIVE
            MediaType.AUDIOBOOK -> WorkType.AUDIOBOOK
            else -> classifyByHeuristics(raw)
        }
    }
    
    // 2. Fall back to heuristics
    return classifyByHeuristics(raw)
}

private fun classifyByHeuristics(raw: RawMediaMetadata): WorkType {
    val durationMs = raw.durationMs ?: return WorkType.UNKNOWN
    
    // Short clip detection
    if (durationMs < CLIP_THRESHOLD_MS) {
        return WorkType.CLIP
    }
    
    // Episode detection (has season/episode)
    if (raw.season != null && raw.episode != null) {
        return WorkType.EPISODE
    }
    
    // Feature film detection (40+ minutes without S/E)
    if (durationMs >= FEATURE_THRESHOLD_MS && raw.season == null) {
        return WorkType.MOVIE
    }
    
    // Default to unknown for manual review
    return WorkType.UNKNOWN
}

companion object {
    const val CLIP_THRESHOLD_MS = 60_000L          // 60 seconds
    const val FEATURE_THRESHOLD_MS = 2_400_000L   // 40 minutes
}
```

### 3.3 WorkType Enum

```kotlin
enum class WorkType {
    MOVIE,      // Feature film, standalone
    EPISODE,    // Part of a series (has S/E)
    SERIES,     // Series root (metadata only, no playback)
    CLIP,       // Short content (< 60s)
    LIVE,       // Live streaming content
    AUDIOBOOK,  // Audio-only book content
    UNKNOWN     // Requires manual review (NEEDS_REVIEW flag)
}
```

### 3.4 NEEDS_REVIEW Flag

Items classified as `WorkType.UNKNOWN` MUST set a `needsReview: Boolean = true` flag on `NX_Work`.

UI should provide a filterable view for `NEEDS_REVIEW` works in the debug screen.

---

## 4. SSOT Invariants (BINDING)

These invariants MUST be enforced at all times. Violations are bugs.

### 4.1 Ingest Path Invariants

| ID | Invariant | Enforcement |
|----|-----------|-------------|
| INV-01 | Every ingest candidate creates exactly one `NX_IngestLedger` entry | Ledger write in normalizer gate |
| INV-02 | No silent drops – `REJECTED` or `SKIPPED` must have a reason code | Enum-only reason codes |
| INV-03 | `ACCEPTED` items MUST link to an `NX_Work` | Transaction rollback on failure |
| INV-04 | `sourceKey` is globally unique across all accounts | Database unique constraint |

### 4.2 Work Graph Invariants

| ID | Invariant | Enforcement |
|----|-----------|-------------|
| INV-10 | Every `NX_Work` has ≥1 `NX_WorkSourceRef` | Verifier worker check |
| INV-11 | Every `NX_Work` has ≥1 `NX_WorkVariant` with valid playback hints | Verifier worker check |
| INV-12 | `workKey` is globally unique | Database unique constraint |
| INV-13 | `accountKey` is mandatory in all `NX_WorkSourceRef` | Non-null constraint |

### 4.3 UI Read Invariants

| ID | Invariant | Enforcement |
|----|-----------|-------------|
| INV-20 | UI reads ONLY from `NX_*` entities (after Phase 4) | Detekt ForbiddenImport |
| INV-21 | No BoxStore access outside repository implementations | Detekt guardrail |
| INV-22 | No legacy `Obx*` imports in feature modules | Detekt guardrail |

---

## 5. Key Generation Utilities

### 5.1 KeyGenerator Interface

```kotlin
interface NxKeyGenerator {
    fun generateWorkKey(
        workType: WorkType,
        canonicalTitle: String,
        year: Int?,
        season: Int? = null,
        episode: Int? = null
    ): String
    
    fun generateAuthorityKey(
        authority: String,
        type: String,
        id: String
    ): String
    
    fun generateSourceKey(
        sourceType: SourceType,
        accountKey: String,
        sourceId: String
    ): String
    
    fun generateVariantKey(
        sourceKey: String,
        qualityTag: String = "source",
        languageTag: String = "original"
    ): String
}
```

### 5.2 Reference Implementation

```kotlin
object NxKeyGeneratorImpl : NxKeyGenerator {
    
    override fun generateWorkKey(
        workType: WorkType,
        canonicalTitle: String,
        year: Int?,
        season: Int?,
        episode: Int?
    ): String {
        val slug = generateCanonicalSlug(canonicalTitle)
        val yearPart = when (workType) {
            WorkType.LIVE -> "LIVE"
            else -> year?.toString() ?: "UNKNOWN"
        }
        
        return when (workType) {
            WorkType.EPISODE -> {
                val sePart = "s${season?.toString()?.padStart(2, '0') ?: "00"}e${episode?.toString()?.padStart(2, '0') ?: "00"}"
                "episode:$slug:$sePart"
            }
            else -> "${workType.name.lowercase()}:$slug:$yearPart"
        }
    }
    
    override fun generateAuthorityKey(
        authority: String,
        type: String,
        id: String
    ): String = "${authority.lowercase()}:${type.lowercase()}:$id"
    
    override fun generateSourceKey(
        sourceType: SourceType,
        accountKey: String,
        sourceId: String
    ): String = "${sourceType.name.lowercase()}:$accountKey:$sourceId"
    
    override fun generateVariantKey(
        sourceKey: String,
        qualityTag: String,
        languageTag: String
    ): String = "$sourceKey#${qualityTag.lowercase()}:${languageTag.lowercase()}"
    
    private fun generateCanonicalSlug(title: String): String {
        return title
            .lowercase()
            .trim()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifEmpty { "untitled" }
    }
}
```

---

## 6. Cross-References

### 6.1 SSOT Documents

| Document | Scope |
|----------|-------|
| `AGENTS.md` Section 4 | Layer boundaries, forbidden imports |
| `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md` | RawMediaMetadata, NormalizedMediaMetadata |
| `docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md` | Phase execution plan |
| `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md` | Telegram ID persistence rules |
| `docs/v2/OBX_PLATIN_DETEKT_GUARDRAILS_CONTRACT.md` | BoxStore Detekt rules |

### 6.2 Path-Scoped Instructions

| Module | Instruction File |
|--------|------------------|
| `core/persistence/**` | `.github/instructions/core-persistence.instructions.md` |
| `infra/data-*/**` | `.github/instructions/infra-data.instructions.md` |
| `pipeline/**` | `.github/instructions/pipeline.instructions.md` |

### 6.3 Related Issues

| Issue | Description |
|-------|-------------|
| #621 | OBX PLATIN Refactor (Parent) |

---

## 7. Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-09 | Initial contract: Key formats, IngestReasonCode, Classification heuristics |

---

## 8. Acceptance Criteria (Phase 0.1)

- [ ] This contract (`NX_SSOT_CONTRACT.md`) exists in `docs/v2/`
- [ ] Key formats are deterministic and documented
- [ ] `IngestReasonCode` enum covers all ingest outcomes
- [ ] Classification heuristics are unambiguous
- [ ] Contract is referenced by Issue #621
- [ ] Contract is linked in `AGENTS.md` or `docs/v2/` index
