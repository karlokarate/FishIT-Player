# Telegram Structured Bundles Contract

**Version:** 2.2  
**Date:** 2025-12-18  
**Status:** Binding – authoritative specification for structured Telegram message handling  
**Scope:** Detection, grouping, and processing of structured Telegram message clusters

> **⚠️ This contract is binding.** All implementations in `pipeline/telegram` MUST follow these rules. Violations are bugs and MUST be fixed immediately.

---

## 1. Definitions

### 1.1 Structured Bundle

A **Structured Bundle** is a group of **2..N** Telegram messages that:
- Share the same `date` (Unix timestamp in seconds) and pass the **Cohesion Gate** (R1b)
- Must include **≥1 VIDEO** message (may include multiple VIDEO messages)
- May include optional TEXT and/or PHOTO messages
- Together describe a single logical media item
- Contain structured metadata in TEXT messages (when present)

### 1.2 Bundle Types

| Type | Composition | Description |
|------|-------------|-------------|
| `FULL_3ER` | PHOTO + TEXT + VIDEO(s) | Complete bundle with poster, metadata, and video(s) |
| `COMPACT_2ER` | TEXT + VIDEO(s) or PHOTO + VIDEO(s) | Compact bundle without all three components |
| `SINGLE` | Single message | No bundle, normal processing |

### 1.3 BundleKey (Pipeline-Internal)

A **BundleKey** is the unique identifier for grouping messages into a bundle:
- **Composition:** `(chatId, timestamp, discriminator)`
- **discriminator:**
  - **Primary:** Album/group ID if provided by Telegram/TDLib
  - **Fallback:** Deterministic discriminator derived from messageId proximity/step pattern
- **Purpose:** Enables deterministic bundle recognition within the pipeline
- **Scope:** Pipeline-internal only; does NOT leave the pipeline

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

---

## 2. Contract Rules

### 2.1 Bundle Detection (MANDATORY)

**R1: Timestamp Grouping**
> Messages with identical `date` (Unix timestamp) MUST be grouped into a **BundleCandidate**.

**R1b: Bundle Cohesion Gate (MANDATORY)**
> A BundleCandidate MAY only be accepted as a Structured Bundle if:
> 
> 1. It contains **≥1 VIDEO** message, AND
> 2. It satisfies a **deterministic cohesion rule**:
>    - **Primary:** If Telegram/TDLib provides an album/group identifier, that MUST be used as the discriminator; timestamp is secondary.
>    - **Fallback:** If no album/group ID is available, cohesion is validated via messageId proximity:
>      - The candidate is cohesive if the span between smallest and largest messageId is **≤ 3 × 2²⁰** (3,145,728), OR
>      - The candidate matches the known step-pattern **2²⁰** (1,048,576) within tolerance.
> 3. **If cohesion fails:** Each message becomes `SINGLE`; no regrouping across timestamps; no semantic matching using captions/titles.

**R2: Bundle Classification**
> A bundle MUST be classified by content types:
> - `FULL_3ER`: Has VIDEO(s) + TEXT + PHOTO
> - `COMPACT_2ER`: Has VIDEO(s) + (TEXT or PHOTO)
> - `SINGLE`: Only one message type or no VIDEO

**R3: Order Invariant**
> Within a bundle, the message ID order follows:
> - PHOTO typically has the lowest `messageId`
> - TEXT has the middle `messageId`
> - VIDEO has the highest `messageId`
> - This order is usable for identification but NOT authoritative for sorting.

### 2.2 Metadata Extraction (MANDATORY)

**R4: Pass-Through with Schema Guards**
> Structured fields MUST be extracted RAW and passed through with minimal sanity checks.
> - No title cleaning
> - No normalization
> - No derived values
> 
> **Schema Guards (MANDATORY):** The following range validations MUST be applied; out-of-range values MUST be set to `null`:
> - `year`: valid range **1800..2100**, else `null`
> - `tmdbRating`: valid range **0.0..10.0**, else `null`
> - `fsk`: valid range **0..21**, else `null`
> - `lengthMinutes`: valid range **1..600**, else `null`

**R5: TMDB ID Extraction**
> The TMDB ID MUST be extracted from `tmdbUrl` via Regex:
> ```
> /movie/(\d+)
> /tv/(\d+)
> ```
> Example: `"https://www.themoviedb.org/movie/12345-name"` → `"12345"`
> 
> If the URL does not match either pattern, `structuredTmdbId` MUST be set to `null` and a WARN log MUST be emitted via UnifiedLog.

**R6: FSK Usage**
> The `fsk` field MUST be passed through as `ageRating` in `RawMediaMetadata`.
> This enables the Kids filter without TMDB lookup.

### 2.3 Mapping Rules (MANDATORY)

**R7: Bundle → Emitted Raw Items (MANDATORY, lossless)**
> For each accepted Structured Bundle, the pipeline MUST emit **exactly one `RawMediaMetadata` per VIDEO message** contained in the bundle.
> 
> The pipeline MUST NOT drop any VIDEO variant.
> 
> Example: A bundle with 3 VIDEO messages produces 3 `RawMediaMetadata` items.

**R8: Shared externalIds for Downstream Unification (MANDATORY)**
> If structured TMDB metadata is present in the TEXT message, then **every `RawMediaMetadata` emitted from that bundle** MUST carry:
> - `externalIds.tmdbId = structuredTmdbId`
> - Same `structuredYear`, `structuredFsk`, `structuredRating`, etc.
> 
> This enables the downstream normalizer to unify items into a single canonical media entry (with multiple variants).

**R8b: No UI Selection Policy in Pipeline (MANDATORY)**
> The pipeline MUST NOT define "primary asset for UI defaults".
> 
> Selection of default variant is a **downstream concern** (normalizer/unifier/UI policies), NOT pipeline responsibility.
> 
> The pipeline MAY pass through raw technical facts per emitted item (e.g., `sizeBytes`, `duration`, `resolution`) only if such raw fields already exist in the pipeline model.

**R9: Poster Selection**
> If a PHOTO message exists in the bundle:
> - Select the size with maximum pixel area (`width * height`)
> - Ties MUST be broken deterministically by: larger `height` → larger `width`
> - The selected poster is attached to **each** emitted `RawMediaMetadata` from that bundle

### 2.4 Contract Compliance

**R10: MEDIA_NORMALIZATION_CONTRACT**
> All rules from `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md` continue to apply:
> - Pipeline MUST NOT normalize
> - Pipeline MUST NOT perform TMDB lookups
> - `globalId` MUST remain empty (computed by normalizer)
> - Pipeline MUST NOT group/merge into canonical works

**R11: Layer Boundaries**
> Structured Bundles are processed entirely in `pipeline/telegram`:
> - Transport provides `TgMessage` (no bundle logic)
> - Pipeline groups, extracts, maps
> - Data receives `RawMediaMetadata` (no bundle internals)
> 
> **Explicit Export Restriction:**
> - `TelegramMediaItem` and all bundle-internal DTOs MUST remain pipeline-internal and MUST NOT be exported across module boundaries.
> - **Only `RawMediaMetadata` MAY leave the pipeline.**
> - There is no additional pipeline export structure.

### 2.5 Downstream Canonical Linking (Informational)

> **Note:** This section describes downstream behavior for context. The pipeline does NOT implement this.

**Canonical ID Format:**
> - If `externalIds.tmdbId` is present in `RawMediaMetadata`, the downstream normalizer computes `canonicalId` as `tmdb:<tmdbId>`.
> - Multiple `RawMediaMetadata` items with the same `externalIds.tmdbId` are unified into a single `NormalizedMedia` with multiple variants.

**Pipeline Responsibility:**
> - The pipeline MUST NOT compute `canonicalId`/`globalId`.
> - The pipeline MUST NOT group/merge items into canonical works.
> - The pipeline only passes through structured IDs to enable downstream unification.

**Downstream Normalizer Behavior:**
> - If `externalIds.tmdbId` is present, the normalizer MUST NOT perform a title search.
> - The normalizer MAY validate/refresh TMDB details via the provided `tmdbId`.

---

## 3. Data Model Extensions

### 3.1 TelegramMediaItem (REQUIRED)

The following fields MUST be added to `TelegramMediaItem`:

```kotlin
// Structured Bundle Fields
val structuredTmdbId: String? = null
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

### 3.2 RawMediaMetadata (REQUIRED)

The following fields MUST be added to `RawMediaMetadata`:

```kotlin
val ageRating: Int? = null  // FSK/MPAA/etc. for Kids filter
val rating: Double? = null  // TMDB rating etc.
```

### 3.3 toRawMediaMetadata() Mapping (REQUIRED)

**Important:** Multi-video bundles require multiple `RawMediaMetadata` emissions (one per VIDEO).

```kotlin
fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata {
    // Structured Bundle fields take precedence
    val effectiveYear = structuredYear ?: year
    val effectiveDuration = structuredLengthMinutes ?: durationSecs?.let { it / 60 }
    
    return RawMediaMetadata(
        sourceId = "${chatId}_${messageId}",  // Unique per VIDEO
        originalTitle = structuredOriginalTitle ?: extractRawTitle(),
        year = effectiveYear,
        durationMinutes = effectiveDuration,
        ageRating = structuredFsk,
        rating = structuredRating,
        externalIds = ExternalIds(
            tmdbId = structuredTmdbId,  // Pass-through from TEXT
        ),
        // ... other fields
    )
}
```

---

## 4. Component Specifications

### 4.1 TelegramMessageBundler

**Package:** `com.fishit.player.pipeline.telegram.grouper`

**Responsibility:** Groups TgMessage lists by timestamp

**Contract:**
- MUST group all messages with the same `date`
- MUST return `TelegramMessageBundle` with correct `bundleType`
- MUST correctly identify content types (VIDEO/TEXT/PHOTO)
- MUST NOT perform normalization

### 4.2 TelegramStructuredMetadataExtractor

**Package:** `com.fishit.player.pipeline.telegram.grouper`

**Responsibility:** Extracts structured fields from TEXT messages

**Contract:**
- MUST recognize all defined Structured Fields (Section 1.4)
- MUST parse TMDB URL to ID (Rule R5) - supports `/movie/` and `/tv/`
- MUST return missing fields as `null`
- MUST NOT invent or derive values

### 4.3 TelegramBundleToMediaItemMapper

**Package:** `com.fishit.player.pipeline.telegram.mapper`

**Responsibility:** Converts bundles to TelegramMediaItem(s) - supports lossless multi-video emission

**Contract:**
- MUST emit one `TelegramMediaItem` per VIDEO in the bundle (lossless, Rule R7)
- MUST apply Poster Selection Rules (Rule R9) - max pixel area
- MUST correctly set all bundle fields on each emitted item
- MUST correctly set `bundleType` on each emitted item
- MUST NOT define "primary asset" or UI selection policy (Rule R8b)

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
| Cohesion Gate failed | Split into SINGLE units, no regrouping |
| TMDB URL unparseable | `structuredTmdbId = null`, log WARN |
| Field value out of Schema Guard range | Set field to `null` (per R4) |

### 5.3 Logging (MANDATORY)

The following events MUST be logged using **UnifiedLog** with stable, class-based TAGs:

| Event | Log Level | TAG | Content |
|-------|-----------|-----|---------|
| Bundle detected | DEBUG | `TelegramMessageBundler` | `chatId`, `timestamp`, `bundleType`, `videoCount` |
| Cohesion Gate rejected | DEBUG | `TelegramMessageBundler` | `chatId`, `timestamp`, `reason`, `messageIds` |
| Structured metadata extracted | DEBUG | `TelegramStructuredMetadataExtractor` | `chatId`, `tmdbId`, `year`, `fsk` |
| Schema Guard applied | DEBUG | `TelegramStructuredMetadataExtractor` | `chatId`, `field`, `originalValue` |
| TMDB URL parse failed | WARN | `TelegramStructuredMetadataExtractor` | `chatId`, `messageId`, `tmdbUrl` |
| Bundle statistics per chat | INFO | `TelegramMessageBundler` | `chatId`, `bundleCount`, `rejectedCount`, `singleCount`, `emittedItemCount` |

**Logging Examples:**
```kotlin
// TelegramMessageBundler.kt
private const val TAG = "TelegramMessageBundler"

UnifiedLog.d(TAG) { "Bundle detected: chatId=$chatId, bundleType=$bundleType, videoCount=$videoCount" }
UnifiedLog.i(TAG) { "Chat stats: chatId=$chatId, bundles=$bundleCount, singles=$singleCount, emitted=$emittedCount" }

// TelegramStructuredMetadataExtractor.kt
private const val TAG = "TelegramStructuredMetadataExtractor"

UnifiedLog.d(TAG) { "Extracted: chatId=$chatId, tmdbId=$tmdbId, year=$year" }
UnifiedLog.w(TAG) { "TMDB URL parse failed: chatId=$chatId, url=$tmdbUrl" }

// TelegramBundleToMediaItemMapper.kt
private const val TAG = "TelegramBundleToMediaItemMapper"

UnifiedLog.d(TAG) { "Mapped bundle: chatId=$chatId, emittedItems=$count" }
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
| TB-005 | Cohesion Gate accepts valid candidate | messageId span ≤ 3×2²⁰ |
| TB-006 | Cohesion Gate rejects invalid candidate | messageId span > 3×2²⁰ |
| SM-001 | TMDB URL Parsing (movie) | Standard URL `/movie/12345` |
| SM-002 | TMDB URL Parsing (tv) | TV URL `/tv/98765` |
| SM-003 | FSK extraction | `"fsk": 12` |
| SM-004 | Missing fields | TEXT without tmdbUrl |
| SM-005 | Schema Guard: year out of range | `year: 3000` → `null` |
| SM-006 | Schema Guard: rating out of range | `tmdbRating: 15.0` → `null` |
| MM-001 | Multi-video: lossless emission | Bundle with 2 VIDEOs → 2 RawMediaMetadata |
| MM-002 | Multi-video: shared externalIds | All emitted items have same tmdbId |
| MM-003 | Poster selection: max pixel area | PHOTO with 3 sizes |

### 6.2 Required Integration Tests

| Test-ID | Chat | Expectation |
|---------|------|-------------|
| INT-001 | Mel Brooks 🥳 | ≥8 bundles detected, correct item count |
| INT-002 | Filme kompakt | ≥8 COMPACT_2ER bundles detected |
| INT-003 | Unstructured chat | 0 bundles, all SINGLE |
| INT-004 | Cohesion rejection | Same-timestamp unrelated messages → rejected/split |

---

## 7. Compliance Checklist

Before each merge MUST be verified:

- [ ] No normalization in pipeline (MEDIA_NORMALIZATION_CONTRACT R10)
- [ ] No TMDB lookups in pipeline
- [ ] `globalId` remains empty (pipeline does NOT compute canonical ID)
- [ ] Structured fields are RAW extracted with Schema Guards (R4)
- [ ] TMDB ID extracted via Regex, supports /movie/ and /tv/ (R5)
- [ ] Cohesion Gate implemented (R1b)
- [ ] Lossless emission: one RawMediaMetadata per VIDEO (R7)
- [ ] No "primary asset for UI defaults" in pipeline (R8b)
- [ ] TelegramMediaItem remains pipeline-internal (R11)
- [ ] Only RawMediaMetadata leaves pipeline (R11)
- [ ] Logging per Section 5.3 using UnifiedLog with stable TAGs
- [ ] All required unit tests pass
- [ ] All required integration tests pass

---

## 8. Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-12-17 | Initial Release (German) |
| 2.0 | 2025-12-18 | English translation, v2 compliance started |
| 2.1 | 2025-12-18 | Removed Work/PlayableAsset/WorkKey; lossless emission (1x RawMediaMetadata per VIDEO); no UI primary selection in pipeline; UnifiedLog with stable TAGs |
| 2.2 | 2025-12-18 | **Gold Deluxe Platinum:** Add Cohesion Gate (R1b) + discriminator BundleKey; fix multi-video bundle definition (2..N messages); reinstate Schema Guards (R4); keep lossless RawMediaMetadata-per-VIDEO |

---

## 9. References

- [TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md](docs/v2/TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md)
- [MEDIA_NORMALIZATION_CONTRACT.md](docs/v2/MEDIA_NORMALIZATION_CONTRACT.md)
- [LOGGING_CONTRACT_V2.md](contracts/LOGGING_CONTRACT_V2.md)
- [GLOSSARY_v2_naming_and_modules.md](contracts/GLOSSARY_v2_naming_and_modules.md)
- [TELEGRAM_PARSER_CONTRACT.md](contracts/TELEGRAM_PARSER_CONTRACT.md)
- [AGENTS.md](AGENTS.md) – Sections 4, 11, 15
