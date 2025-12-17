# Telegram Structured Bundles Contract

**Version:** 2.0  
**Date:** 2025-12-17  
**Status:** Binding – authoritative specification for structured Telegram message handling  
**Scope:** Detection, grouping, and processing of structured Telegram message clusters

> **⚠️ This contract is binding.** All implementations in `pipeline/telegram` MUST follow these rules. Violations are bugs and MUST be fixed immediately.

---

## 1. Definitions

### 1.1 Structured Bundle

A **Structured Bundle** is a group of 2-3 Telegram messages that:
- Share the same `date` (Unix timestamp in seconds)
- Pass the Bundle Cohesion Gate (see R1b)
- Together describe a single media item
- Contain structured metadata in TEXT messages

### 1.2 Bundle Types

| Type | Composition | Description |
|------|-------------|-------------|
| `FULL_3ER` | PHOTO + TEXT + VIDEO | Complete bundle with poster, metadata, and video |
| `COMPACT_2ER` | TEXT + VIDEO or PHOTO + VIDEO | Compact bundle without all three components |
| `SINGLE` | Single message | No bundle, normal processing |

### 1.3 BundleKey

A **BundleKey** is the unique identifier for a bundle:
- **Composition:** `(chatId, timestamp, discriminator)`
- **discriminator:** Album ID if provided (from Telegram/TDLib), else deterministic fallback based on messageId proximity
- **Purpose:** Enables deterministic bundle recognition beyond timestamp matching

### 1.4 Structured Metadata Fields

Fields that may appear in TEXT messages from structured chats:

| Field | Type | Example | Usage |
|-------|------|---------|-------|
| `tmdbUrl` | String | `"https://www.themoviedb.org/movie/12345-name"` | TMDB ID extraction |
| `tmdbRating` | Double | `7.5` | Rating display |
| `year` | Int | `2020` | Release year |
| `originalTitle` | String | `"The Movie"` | Original title |
| `genres` | List<String> | `["Action", "Drama"]` | Genre tags |
| `fsk` | Int | `12` | Age rating (Kids filter) |
| `director` | String | `"John Doe"` | Director |
| `lengthMinutes` | Int | `120` | Runtime |
| `productionCountry` | String | `"US"` | Production country |

### 1.5 Work, PlayableAsset, and WorkKey

| Term | Definition | Usage |
|------|------------|-------|
| **Work** | Canonicalizable entity (movie/episode) resolved downstream | A logical work that can have multiple playback variants |
| **PlayableAsset** | Concrete video file/stream reference (Telegram remoteId/fileId etc.) | A playable file belonging to a Work |
| **WorkKey** | `tmdb:<type>:<id>` when structured TMDB data is present (type from URL), else pipeline-local key (NOT globalId; MUST be marked as pipeline-local and ephemeral) | Temporary key for grouping assets before normalization |

---

## 2. Contract Rules

### 2.1 Bundle Detection (MANDATORY)

**R1: Bundle Candidate Grouping**
> Messages with identical `date` (Unix timestamp) MUST be grouped into a **BundleCandidate**.

**R1b: Bundle Cohesion Gate (MANDATORY)**
> A BundleCandidate MAY only be treated as a Structured Bundle if:
> 1. It contains at least one VIDEO message, AND
> 2. It satisfies a deterministic cohesion rule:
>    - **Primary:** If Telegram/TDLib provides any album/group identifier (e.g., media group/album ID), that MUST be the primary discriminator; timestamp becomes secondary.
>    - **Fallback:** If no album/group ID is available, the discriminator MUST be computed from messageId proximity and type pattern:
>      - The candidate is cohesive if the smallest and largest messageId in the candidate are within a fixed maximum span (constant: ≤ 3 * 2^20 = 3,145,728, justified by observed pattern), OR
>      - if it matches the known step-pattern 2^20 within tolerance (documented as "confidence heuristic", but still deterministic).
> 3. If cohesion fails, the candidate MUST be split into SINGLE processing units (no bundle).

**R2: Bundle Classification**
> A bundle MUST be classified by content types:
> - `FULL_3ER`: Has VIDEO + TEXT + PHOTO
> - `COMPACT_2ER`: Has VIDEO + (TEXT or PHOTO)
> - `SINGLE`: Only one message type

**R3: Order Invariant**
> Within a bundle, the message ID order follows:
> - PHOTO typically has the lowest `messageId`
> - TEXT has the middle `messageId`
> - VIDEO has the highest `messageId`
> - This order is usable for identification but NOT authoritative for sorting.

### 2.2 Metadata Extraction (MANDATORY)

**R4: Pass-Through with Schema Guards**
> Structured fields MUST be extracted RAW and passed through, except for type parsing and schema guards.
> 
> **Schema Guards MUST** set obviously invalid values to null:
> - `year` valid range: 1800..2100, else null
> - `tmdbRating` valid range: 0.0..10.0, else null
> - `fsk` valid range: 0..21, else null
> - `lengthMinutes` valid range: 1..600, else null
> 
> No derived values, no cleaning, no normalization.

**R5: TMDB ID + Type Extraction**
> The pipeline MUST parse TMDB ID and TMDB media type from `tmdbUrl` using these mandatory patterns:
> - `/movie/(\d+)` ⇒ `tmdbType = MOVIE`, `tmdbId = digits`
> - `/tv/(\d+)` ⇒ `tmdbType = TV`, `tmdbId = digits`
> 
> Any other TMDB URL format MUST result in `tmdbId=null` and MUST log `TMDB-URL parse failed (WARN)` via UnifiedLog.
> 
> **Structured Field:**
> - `structuredTmdbType: TelegramTmdbType? = null` with enum `MOVIE`, `TV`

**R6: FSK Usage**
> The `fsk` field MUST be passed through as `ageRating` in `RawMediaMetadata`.
> This enables the Kids filter without TMDB lookup.

### 2.3 Mapping Rules (MANDATORY)

**R7: Bundle → Catalog Items**
> A Structured Bundle MUST produce:
> - Exactly one Raw metadata record (per BundleKey), AND
> - One playable asset record per VIDEO within the bundle

**R8: Multi-VIDEO Mapping (MANDATORY, lossless)**
> If a bundle contains multiple VIDEO messages, the pipeline MUST create multiple playable assets, all linked to the same WorkKey derived from the same structured metadata (TMDB ID when present).
> 
> The pipeline MUST NOT drop video variants.

**R8b: Deterministic Primary Asset Selection (MANDATORY)**
> A "primary" asset MUST be deterministically designated for UI defaults:
> 1. Largest `sizeBytes`
> 2. then longest `duration`
> 3. then lowest `messageId`
> 
> Non-primary assets MUST be retained as alternates.

**R9: Poster Selection**
> If PHOTO exists, the selected poster MUST be the size with maximum pixel area (`width * height`).
> 
> Ties MUST be broken deterministically by:
> 1. Larger `height`
> 2. Larger `width`
> 3. Lowest `messageId`

### 2.4 Contract Compliance

**R10: MEDIA_NORMALIZATION_CONTRACT**
> All rules from `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md` continue to apply:
> - Pipeline MUST NOT normalize
> - Pipeline MUST NOT perform TMDB lookups
> - `globalId` MUST remain empty

**R11: Layer Boundaries**
> Structured Bundles are processed entirely in `pipeline/telegram`:
> - Transport provides `TgMessage` (no bundle logic)
> - Pipeline groups, extracts, maps
> - Data receives `RawMediaMetadata` (no bundle internals)
> 
> **Explicit Export Restriction:**
> - `TelegramMediaItem` and all bundle-internal DTOs MUST remain pipeline-internal and MUST NOT be exported across module boundaries.
> - Only `RawMediaMetadata` and the defined "PlayableAsset" export structure may leave the pipeline.

### 2.5 Canonical Linking Rule (Binding)

**Canonical Linking:**
> - If `externalIds.tmdbId` is present in `RawMediaMetadata`, downstream MUST compute `canonicalId` as `tmdb:<type>:<id>`.
> - All playable assets (including alternates) MUST be linkable to the same `canonicalId` downstream.
> - The pipeline MUST NOT compute `canonicalId`/`globalId`; it only passes through TMDB ID/type and structured raw fields.

**Single Source of Truth (SSOT):**
> - One work has one canonical ID (TMDB preferred) and a single SSOT for metadata (TMDB).
> - All playable files across pipelines attach to the same canonical ID.
> - Pipeline responsibility: Pass through structured IDs and types, NOT compute canonical identity.

---

## 3. Data Model Extensions

### 3.1 TelegramMediaItem (REQUIRED)

The following fields MUST be added to `TelegramMediaItem`:

```kotlin
// Structured Bundle Fields
val structuredTmdbId: String? = null
val structuredTmdbType: TelegramTmdbType? = null  // NEW: MOVIE or TV
val structuredRating: Double? = null
val structuredYear: Int? = null
val structuredFsk: Int? = null
val structuredGenres: List<String>? = null
val structuredDirector: String? = null
val structuredOriginalTitle: String? = null
val structuredLengthMinutes: Int? = null
val structuredProductionCountry: String? = null
val bundleType: TelegramBundleType = TelegramBundleType.SINGLE
val textMessageId: Long? = null
val photoMessageId: Long? = null
```

**Enum for TMDB Type:**
```kotlin
enum class TelegramTmdbType {
    MOVIE,
    TV
}
```

### 3.2 RawMediaMetadata (REQUIRED)

The following fields MUST be added to `RawMediaMetadata`:

```kotlin
val ageRating: Int? = null  // FSK/MPAA/etc. for Kids filter
val rating: Double? = null  // TMDB rating etc.
```

### 3.3 toRawMediaMetadata() Mapping (REQUIRED)

**Important:** Multi-video bundles require multiple asset emission (see R7, R8).

```kotlin
fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata {
    // Structured Bundle fields take precedence
    val effectiveYear = structuredYear ?: year
    val effectiveDuration = structuredLengthMinutes ?: durationSecs?.let { it / 60 }
    
    // Schema Guards (R4)
    val validatedYear = effectiveYear?.takeIf { it in 1800..2100 }
    val validatedRating = structuredRating?.takeIf { it in 0.0..10.0 }
    val validatedFsk = structuredFsk?.takeIf { it in 0..21 }
    val validatedLength = effectiveDuration?.takeIf { it in 1..600 }
    
    return RawMediaMetadata(
        originalTitle = structuredOriginalTitle ?: extractRawTitle(),
        year = validatedYear,
        durationMinutes = validatedLength,
        ageRating = validatedFsk,
        rating = validatedRating,
        externalIds = ExternalIds(
            tmdbId = structuredTmdbId,  // Pass-through from TEXT
        ),
        // ... other fields
    )
}
```

**Multi-Asset Note:**
> For bundles with multiple VIDEOs, the mapping logic must emit multiple catalog items sharing the same `RawMediaMetadata` core but with different playback references (remoteId, fileId).

---

## 4. Component Specifications

### 4.1 TelegramMessageBundler

**Package:** `com.fishit.player.pipeline.telegram.grouper`

**Responsibility:** Groups TgMessage lists by timestamp and checks cohesion

**Contract:**
- MUST group all messages with the same `date` as BundleCandidate
- MUST apply Bundle Cohesion Gate (R1b)
- MUST return `TelegramMessageBundle` with correct `bundleType`
- MUST correctly identify content types (VIDEO/TEXT/PHOTO)
- MUST split cohesion-failed candidates into SINGLE units
- MUST NOT perform normalization

### 4.2 TelegramStructuredMetadataExtractor

**Package:** `com.fishit.player.pipeline.telegram.grouper`

**Responsibility:** Extracts structured fields from TEXT messages

**Contract:**
- MUST recognize all defined Structured Fields (Section 1.4)
- MUST parse TMDB URL to ID + Type (Rule R5) - supports `/movie/` and `/tv/`
- MUST apply Schema Guards (Rule R4) - set invalid values to null
- MUST return missing fields as `null`
- MUST NOT invent or derive values

### 4.3 TelegramBundleToMediaItemMapper

**Package:** `com.fishit.player.pipeline.telegram.mapper`

**Responsibility:** Converts bundles to TelegramMediaItem(s) - supports multi-asset emission

**Contract:**
- MUST apply Primary Asset Selection Rules (Rule R8b)
- MUST apply Poster Selection Rules (Rule R9) - max pixel area
- MUST correctly set all bundle fields
- MUST correctly set `bundleType`
- MUST emit multiple assets for multi-video bundles (lossless, Rule R8)
- MUST mark non-primary assets as alternates

---

## 5. Behavioral Rules

### 5.1 Fallback for Unstructured Chats

When a chat contains no structured bundles:
- Messages are treated as `SINGLE`
- Existing parsing logic is applied
- No regression for existing functionality

### 5.2 Error Handling

| Error | Behavior |
|-------|----------|
| TEXT without structured fields | Treat as normal TEXT |
| Bundle without VIDEO | Emit NO item (only VIDEO is playable) |
| TMDB URL unparseable | `structuredTmdbId = null` |
| Invalid field values | Set field to `null`, do not throw error |

### 5.3 Logging (MANDATORY)

The following events MUST be logged using UnifiedLog:

| Event | Log Level | Content |
|-------|-----------|---------|
| Bundle detected | DEBUG | `chatId`, `timestamp`, `bundleType`, `messageIds` |
| Structured metadata extracted | DEBUG | `chatId`, `tmdbId`, `tmdbType`, `year`, `fsk` |
| TMDB URL parse failed | WARN | `chatId`, `messageId`, `tmdbUrl` |
| Bundle rejected (cohesion failed) | DEBUG | `chatId`, `timestamp`, `messageIds`, `reason` |
| Bundle statistics per chat | INFO | `chatId`, `bundleCount`, `singleCount` |

**Mandatory Per-Chat Metrics:**

The following metrics MUST be tracked and logged per chat:

| Metric | Type | Description |
|--------|------|-------------|
| `bundleCandidateCount` | Counter | Number of timestamp groupings |
| `bundleAcceptedCount` | Counter | Number of accepted bundles (cohesion successful) |
| `bundleRejectedCount` | Counter | Number of rejected bundles (cohesion failed) |
| `bundlesByType` | Map<BundleType, Count> | Distribution: FULL_3ER, COMPACT_2ER, SINGLE |
| `orphanTextCount` | Counter | TEXT with structured fields but no accepted bundle |
| `orphanPhotoCount` | Counter | PHOTO without accepted bundle |
| `videoVariantCountTotal` | Counter | Total number of emitted video assets |
| `multiVideoBundleCount` | Counter | Number of bundles with >1 VIDEO |

**Logging Example:**
```kotlin
private const val TAG = "TelegramBundler"

UnifiedLog.d(TAG) { "Bundle detected: chatId=$chatId, bundleType=$bundleType, messageIds=$messageIds" }
UnifiedLog.w(TAG) { "TMDB URL parse failed: chatId=$chatId, url=$tmdbUrl" }
UnifiedLog.d(TAG) { "Bundle rejected (cohesion failed): chatId=$chatId, reason=$reason" }
```

---

## 6. Test Requirements

### 6.1 Required Unit Tests

| Test-ID | Description | Fixture |
|---------|-------------|---------|
| TB-001 | Grouping by timestamp | 3 messages, same timestamp |
| TB-002 | No grouping for different timestamps | 3 messages, different timestamps |
| TB-003 | FULL_3ER classification | VIDEO + TEXT + PHOTO |
| TB-004 | COMPACT_2ER classification | TEXT + VIDEO |
| TB-005 | Cohesion Gate accepts valid candidate | BundleCandidate with messageId proximity ≤ 3*2^20 |
| TB-006 | Cohesion Gate rejects invalid candidate | BundleCandidate with too large messageId span |
| SM-001 | TMDB URL Parsing (movie) | Standard URL `/movie/12345` |
| SM-002 | TMDB URL Parsing (tv) | TV URL `/tv/98765` |
| SM-003 | TMDB URL with slug | URL with `-name` suffix |
| SM-004 | FSK extraction | `"fsk": 12` |
| SM-005 | Missing fields | TEXT without tmdbUrl |
| SM-006 | Schema Guard: invalid year | `year: 3000` → null |
| SM-007 | Schema Guard: invalid rating | `tmdbRating: 15.0` → null |
| SM-008 | Schema Guard: invalid FSK | `fsk: 50` → null |
| SM-009 | Schema Guard: invalid length | `lengthMinutes: 1000` → null |
| MM-001 | Primary Asset: largest video | 2 VIDEOs, different sizes |
| MM-002 | Primary Asset: longest duration | 2 VIDEOs, same size |
| MM-003 | Multi-video bundle emits N assets | Bundle with 3 VIDEOs → 3 assets, 1 primary |
| MM-004 | Poster selection: max pixel area | PHOTO with 3 sizes, chooses largest area |
| MM-005 | Poster selection: tie-breaker | 2 sizes same area, chooses higher height |

### 6.2 Required Integration Tests

| Test-ID | Chat | Expectation |
|---------|------|-------------|
| INT-001 | Mel Brooks 🥳 | ≥8 FULL_3ER bundles detected |
| INT-002 | Filme kompakt | ≥8 COMPACT_2ER bundles detected |
| INT-003 | Unstructured chat | 0 bundles, all SINGLE |
| INT-004 | Cohesion rejection | BundleCandidate with same timestamp but unrelated messages is rejected/split |
| INT-005 | Multi-video emission | Bundle with multi-video emits ≥2 assets for one work |

---

## 7. Compliance Checklist

Before each merge MUST be verified:

- [ ] No normalization in pipeline (MEDIA_NORMALIZATION_CONTRACT R10)
- [ ] No TMDB lookups in pipeline
- [ ] `globalId` remains empty (pipeline does NOT compute canonical ID)
- [ ] Structured fields are RAW extracted with Schema Guards (R4)
- [ ] TMDB ID + Type extracted via Regex, supports /movie/ and /tv/ (R5)
- [ ] Bundle Cohesion Gate implemented (R1b)
- [ ] Multi-video bundles emit all assets lossless (R8)
- [ ] Primary Asset Selection Rules implemented (R8b)
- [ ] Poster selection via max pixel area (R9)
- [ ] TelegramMediaItem remains pipeline-internal (R11)
- [ ] Logging per Section 5.3 with all metrics using UnifiedLog
- [ ] All required unit tests pass
- [ ] All required integration tests pass

---

## 8. Version History

| Version | Date | Changes |
|---------|------|----------|
| 1.0 | 2025-12-17 | Initial Release |
| 2.0 | 2025-12-17 | English-only, UnifiedLog compliance, canonical tmdb: format, multi-variant RawMediaMetadata clarification |

---

## 9. References

- [TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md](docs/v2/TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md)
- [MEDIA_NORMALIZATION_CONTRACT.md](docs/v2/MEDIA_NORMALIZATION_CONTRACT.md)
- [TELEGRAM_PARSER_CONTRACT.md](contracts/TELEGRAM_PARSER_CONTRACT.md)
- [LOGGING_CONTRACT_V2.md](contracts/LOGGING_CONTRACT_V2.md)
- [AGENTS.md](AGENTS.md) – Sections 4, 11, 15
