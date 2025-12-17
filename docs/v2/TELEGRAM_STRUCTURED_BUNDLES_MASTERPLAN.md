# Telegram Structured Bundles – Masterplan

**Version:** 2.0  
**Date:** 2025-12-17  
**Status:** Draft – ready for implementation  
**Scope:** Detection and processing of structured Telegram message clusters (PHOTO→TEXT→VIDEO)

---

## Executive Summary

Analysis of 398 Telegram chat exports revealed that 8 chats contain **structured metadata** enabling dramatic pipeline optimization:

- **Zero-Parsing-Path:** TMDB IDs, titles, year, FSK directly extractable from TEXT messages
- **Zero-API-Call-Path:** No TMDB API calls needed for base metadata
- **Bundle Concept:** PHOTO→TEXT→VIDEO clusters with identical timestamp as logical unit

This insight enables **ultra-fast onboarding** for structured chats while maintaining support for the regular parsing path for unstructured chats.

---

## 1. Analysis Results

### 1.1 Chat Classification

| Chat-ID | Name | Pattern | TMDB | Videos | Photos |
|---------|------|---------|------|--------|--------|
| -1001434421634 | Mel Brooks 🥳 | 3-cluster | 9 | 9 | 7 |
| -1001452246125 | 🎬 Filme von 2001 bis 2010 🎥 | 3-cluster | 8 | 8+ | 8+ |
| -1001203115098 | 🎬⚠️ Filme ab: 2020 ⚠️🎥 | 3-cluster | 8 | 8+ | 8+ |
| -1001180440610 | 🎬 Filme von 2011 bis 2019 🎥 | 3-cluster | 8 | 8+ | 8+ |
| -1001491030766 | John Carpenter | 3-cluster | 6 | 6+ | 6+ |
| -1001326220574 | 🎬Filme kompakt!🎥 | 2-cluster | 8 | 8 | 8 |
| -1001545742878 | Der Frühe Vogel | Mixed | 5 | 5+ | 5+ |
| -1001452717239 | Film & Serien JtL | Mixed | 1 | 1+ | 1+ |

### 1.2 Message Structure (JSON Export Analysis)

**3-cluster (PHOTO → TEXT → VIDEO):**

```
Timestamp: 1731704712 (identical for all 3 messages)
├── PHOTO: content.sizes[] (multiple resolutions up to 1000x1500)
├── TEXT:  tmdbUrl, tmdbRating, year, originalTitle, genres, fsk, director, lengthMinutes
└── VIDEO: content.duration, content.fileName, content.file.remoteId
```

**Typical TEXT Fields (structured):**

```json
{
  "tmdbUrl": "https://www.themoviedb.org/movie/12345-movie-name",
  "tmdbRating": 7.5,
  "year": 2020,
  "originalTitle": "The Movie",
  "genres": ["Action", "Drama"],
  "fsk": 12,
  "director": "John Doe",
  "lengthMinutes": 120,
  "productionCountry": "US"
}
```

### 1.3 ID Correlation

- Messages in the same cluster have **identical Unix timestamp** (`date`)
- Message IDs differ by exactly **1,048,576** (2²⁰) within a cluster
- Order: PHOTO (lowest ID) → TEXT → VIDEO (highest ID)

---

## 2. Architecture Concept

### 2.1 Current Data Flow Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│  TDLib (Native) → TdApi.Message                                      │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  transport-telegram                                                   │
│  DefaultTelegramTransportClient                                       │
│  TdApi.Message → TgMessage, TgContent (DTOs)                          │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  pipeline/telegram                                                    │
│  TelegramPipelineAdapter                                              │
│  TgMessage → TelegramMediaItem (with toMediaItem())                    │
│                                                                       │
│  TelegramCatalogPipelineImpl                                          │
│  TelegramMediaItem → RawMediaMetadata (with toRawMediaMetadata())      │
│  Emits: TelegramCatalogEvent.ItemDiscovered(TelegramCatalogItem)      │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  core/metadata-normalizer (CENTRAL)                                   │
│  RawMediaMetadata → NormalizedMediaMetadata                           │
│  TMDB lookups, title cleaning, globalId computation                   │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  data-telegram                                                        │
│  NormalizedMediaMetadata → ObxTelegramItem (ObjectBox)                │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.2 Extended Data Flow (Structured Bundles)

```
┌──────────────────────────────────────────────────────────────────────┐
│  transport-telegram                                                   │
│  TgMessage[] → Messages with identical timestamp                      │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  pipeline/telegram/grouper  [NEW]                                     │
│                                                                       │
│  TelegramMessageBundler                                               │
│  ├── Groups messages by identical timestamp (BundleCandidate)         │
│  ├── Applies Bundle Cohesion Gate (R1b):                              │
│  │   - Album/Group ID primary, messageId proximity fallback           │
│  │   - Span ≤ 3*2^20 or step-pattern 2^20                             │
│  ├── Classifies: Structured (3-cluster/2-cluster) vs Unstructured     │
│  └── Emits: TelegramMessageBundle or individual TgMessage             │
│                                                                       │
│  TelegramStructuredMetadataExtractor                                  │
│  ├── Extracts TEXT fields: tmdbUrl, tmdbType, year, fsk, etc.        │
│  ├── Applies Schema Guards (R4): invalid values → null                │
│  └── Maps PHOTO.sizes[] to ImageRef                                   │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  pipeline/telegram/mapper                                             │
│                                                                       │
│  TelegramMediaItem (extended with bundle fields)                      │
│  ├── structuredTmdbId: String?      // extracted from tmdbUrl         │
│  ├── structuredTmdbType: TelegramTmdbType? // MOVIE or TV            │
│  ├── structuredRating: Double?      // tmdbRating pass-through        │
│  ├── structuredYear: Int?           // year pass-through              │
│  ├── structuredFsk: Int?            // fsk for Kids filter            │
│  ├── structuredGenres: List<String>?// genres pass-through            │
│  ├── posterSizes: List<TelegramPhotoSize>? // from PHOTO message      │
│  └── bundleType: BundleType         // FULL_3ER, COMPACT_2ER, SINGLE  │
│                                                                       │
│  Multi-Asset Emission (R7, R8):                                       │
│  ├── Per bundle: 1x RawMediaMetadata + Nx PlayableAsset              │
│  ├── Lossless: All VIDEOs are emitted                                │
│  └── Primary Asset Selection (R8b): sizeBytes → duration → messageId │
│                                                                       │
│  toRawMediaMetadata() (extended)                                      │
│  ├── externalIds.tmdbId + tmdbType from structuredTmdb*              │
│  ├── year from structuredYear (after Schema Guard)                   │
│  ├── poster from posterSizes (max pixel area, R9)                    │
│  └── ageRating from structuredFsk                                     │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  core/metadata-normalizer                                             │
│                                                                       │
│  Checks: Does RawMediaMetadata already have tmdbId?                   │
│  ├── YES:  Skip TMDB lookup, directly use normalTitle from TMDB cache│
│  └── NO: Normal path (title parsing, TMDB search, etc.)               │
│                                                                       │
│  Canonical Linking (Contract Section 2.5):                            │
│  ├── tmdbId present → canonicalId = tmdb:<type>:<id>                 │
│  └── All PlayableAssets are linked to same canonicalId               │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Contract Compliance

### 3.1 MEDIA_NORMALIZATION_CONTRACT.md

> "Pipelines must not guess TMDB/IMDB IDs; they may only pass through IDs provided by the source."

✅ **Compliant:** Structured Bundles pass through TMDB IDs provided by the source (Telegram chat operator). The pipeline doesn't "guess"; it reads structured fields.

### 3.2 Global Pipeline Rules

| Rule | Status | Implementation |
|------|--------|-----------------|
| Pipeline must not normalize | ✅ | Title is passed RAW |
| Pipeline must not make TMDB lookups | ✅ | TMDB ID is pass-through |
| globalId remains empty | ✅ | Normalizer computes |
| Pipeline doesn't export DTOs | ✅ | Only RawMediaMetadata leaves pipeline |

### 3.3 Layer Boundaries

| Layer | Allowed | Forbidden |
|-------|---------|-----------|
| transport-telegram | TgMessage, TgContent | RawMediaMetadata |
| pipeline/telegram | TelegramMediaItem (internal), RawMediaMetadata (export) | ObxTelegram*, TMDB client |
| data-telegram | RawMediaMetadata, ObxTelegramItem | TelegramMediaItem, TgMessage |

---

## 4. Model Extensions

### 4.1 TelegramMediaItem (Extensions)

```kotlin
// pipeline/telegram/model/TelegramMediaItem.kt

data class TelegramMediaItem(
    // ... existing fields ...
    
    // === Structured Bundle Fields (NEW) ===
    
    /** TMDB ID from structured TEXT message (e.g., "12345" from tmdbUrl) */
    val structuredTmdbId: String? = null,
    
    /** TMDB type from structured TEXT message (MOVIE or TV) */
    val structuredTmdbType: TelegramTmdbType? = null,
    
    /** TMDB rating from structured TEXT message */
    val structuredRating: Double? = null,
    
    /** Year from structured TEXT message (overrides parser heuristic) */
    val structuredYear: Int? = null,
    
    /** FSK age rating for Kids filter */
    val structuredFsk: Int? = null,
    
    /** Genres from structured TEXT message */
    val structuredGenres: List<String>? = null,
    
    /** Director from structured TEXT message */
    val structuredDirector: String? = null,
    
    /** Original title from structured TEXT message */
    val structuredOriginalTitle: String? = null,
    
    /** Production country */
    val structuredProductionCountry: String? = null,
    
    /** Runtime in minutes */
    val structuredLengthMinutes: Int? = null,
    
    /** Bundle type for debugging/logging */
    val bundleType: TelegramBundleType = TelegramBundleType.SINGLE,
    
    /** Message ID of TEXT message in bundle (for debugging) */
    val textMessageId: Long? = null,
    
    /** Message ID of PHOTO message in bundle (for debugging) */
    val photoMessageId: Long? = null,
)

enum class TelegramBundleType {
    /** Complete 3-cluster: PHOTO + TEXT + VIDEO */
    FULL_3ER,
    
    /** Compact 2-cluster: TEXT + VIDEO or PHOTO + VIDEO */
    COMPACT_2ER,
    
    /** Single message (no bundle) */
    SINGLE,
}

enum class TelegramTmdbType {
    /** TMDB Movie (/movie/<id>) */
    MOVIE,
    
    /** TMDB TV Show (/tv/<id>) */
    TV,
}
```

### 4.2 RawMediaMetadata (Required Extensions)

```kotlin
// core/model/RawMediaMetadata.kt

data class RawMediaMetadata(
    // ... existing fields ...
    
    /** Age rating (FSK/MPAA/etc.) for Kids filter */
    val ageRating: Int? = null,
    
    /** Rating (e.g., TMDB score 0.0-10.0) */
    val rating: Double? = null,
)
```

### 4.3 ExternalIds (Check/Extend)

```kotlin
// Check: Does ExternalIds already have tmdbId as String?
data class ExternalIds(
    val tmdbId: String? = null,  // "12345" from tmdbUrl
    val imdbId: String? = null,
    val tvdbId: String? = null,
)
```

---

## 5. New Components

### 5.1 TelegramMessageBundler

**Path:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/grouper/TelegramMessageBundler.kt`

**Responsibility:**

- Groups TgMessage list by identical timestamp (BundleCandidate)
- Applies Bundle Cohesion Gate (Contract R1b)
- Classifies clusters by type (3-cluster, 2-cluster, Single)
- Emits TelegramMessageBundle for related messages
- Splits cohesion-failed candidates into SINGLE units

```kotlin
/**
 * Groups Telegram messages by identical timestamp into bundles.
 *
 * Bundle Detection:
 * - Messages with identical `date` (Unix timestamp) are grouped as BundleCandidate
 * - Bundle Cohesion Gate (Contract R1b):
 *   - Primary: Album/Group ID from Telegram/TDLib
 *   - Fallback: messageId proximity (span ≤ 3*2^20) or step-pattern 2^20
 * - Order in bundles: PHOTO (lowest msgId) → TEXT → VIDEO (highest msgId)
 *
 * Per MEDIA_NORMALIZATION_CONTRACT: No normalization here.
 * Bundle fields are RAW extracted and passed to TelegramMediaItem.
 */
class TelegramMessageBundler {
    
    /**
     * Groups messages by timestamp and applies Cohesion Gate.
     *
     * @param messages Unsorted list of TgMessage
     * @return List of TelegramMessageBundle (sorted by newest timestamp)
     *         Cohesion-failed candidates are returned as SINGLE
     */
    fun groupByTimestamp(messages: List<TgMessage>): List<TelegramMessageBundle>
    
    /**
     * Classifies a bundle type based on contained message types.
     */
    fun classifyBundle(messages: List<TgMessage>): TelegramBundleType
    
    /**
     * Checks Bundle Cohesion (Contract R1b).
     * 
     * @return true if candidate is cohesive, false otherwise
     */
    fun checkBundleCohesion(candidate: List<TgMessage>): Boolean
}

data class TelegramMessageBundle(
    val timestamp: Long,
    val messages: List<TgMessage>,
    val bundleType: TelegramBundleType,
    val videoMessage: TgMessage?,
    val textMessage: TgMessage?,
    val photoMessage: TgMessage?,
)
```

### 5.2 TelegramStructuredMetadataExtractor

**Path:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/grouper/TelegramStructuredMetadataExtractor.kt`

**Responsibility:**

- Extracts structured fields from TEXT messages
- Parses TMDB URL to ID + Type (MOVIE or TV)
- Applies Schema Guards
- Returns RAW values without normalization

```kotlin
/**
 * Extracts structured metadata from Telegram TEXT messages.
 *
 * Supported Fields:
 * - tmdbUrl → tmdbId + tmdbType (via Regex /movie/(\d+) or /tv/(\d+))
 * - tmdbRating, year, fsk, genres, director, originalTitle
 * - lengthMinutes, productionCountry
 *
 * Per MEDIA_NORMALIZATION_CONTRACT: All values RAW extracted with Schema Guards.
 * Schema Guards (Contract R4):
 * - year: 1800..2100 else null
 * - tmdbRating: 0.0..10.0 else null
 * - fsk: 0..21 else null
 * - lengthMinutes: 1..600 else null
 */
class TelegramStructuredMetadataExtractor {
    
    /**
     * Checks if a TEXT message contains structured fields.
     */
    fun hasStructuredFields(textMessage: TgMessage): Boolean
    
    /**
     * Extracts all structured fields from a TEXT message.
     *
     * @return StructuredMetadata or null if no structured fields
     */
    fun extractStructuredMetadata(textMessage: TgMessage): StructuredMetadata?
    
    /**
     * Extracts TMDB ID and Type from URL.
     * 
     * Supported patterns (Contract R5):
     * - /movie/(\d+) → (id, MOVIE)
     * - /tv/(\d+) → (id, TV)
     * 
     * Example: "https://www.themoviedb.org/movie/12345-name" → ("12345", MOVIE)
     */
    fun extractTmdbFromUrl(tmdbUrl: String?): Pair<String?, TelegramTmdbType?>
}

data class StructuredMetadata(
    val tmdbId: String?,
    val tmdbType: TelegramTmdbType?,
    val tmdbRating: Double?,
    val year: Int?,
    val fsk: Int?,
    val genres: List<String>,
    val director: String?,
    val originalTitle: String?,
    val lengthMinutes: Int?,
    val productionCountry: String?,
)
```

### 5.3 TelegramBundleToMediaItemMapper

**Path:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/mapper/TelegramBundleToMediaItemMapper.kt`

**Responsibility:**

- Converts TelegramMessageBundle → TelegramMediaItem(s)
- Merges VIDEO, TEXT, PHOTO data
- Applies Primary Asset Selection Rules
- Emits multiple assets for multi-video bundles (lossless)

```kotlin
/**
 * Maps TelegramMessageBundle to TelegramMediaItem(s).
 *
 * Mapping Rules:
 * 1. VIDEO message provides: remoteId, duration, fileName, mimeType, etc.
 * 2. TEXT message provides: structuredTmdbId, structuredTmdbType, structuredYear, etc.
 * 3. PHOTO message provides: photoSizes for poster
 *
 * Multi-Asset Emission (Contract R7, R8):
 * - Per bundle: 1x RawMediaMetadata + Nx PlayableAsset
 * - All VIDEOs are emitted (lossless)
 *
 * Primary Asset Selection (Contract R8b):
 * - Largest file (sizeBytes)
 * - Longest duration (duration)
 * - Lowest messageId (deterministic)
 *
 * Poster Selection (Contract R9):
 * - Max pixel area (width * height)
 * - Tie-breaker: height → width → messageId
 */
class TelegramBundleToMediaItemMapper {
    
    /**
     * Maps bundle to TelegramMediaItem(s).
     * 
     * @return List of items (for multi-video > 1 element)
     */
    fun mapBundleToMediaItems(bundle: TelegramMessageBundle): List<TelegramMediaItem>
    
    /**
     * Selects primary video (Contract R8b).
     */
    fun selectPrimaryVideo(videos: List<TgContent.Video>): TgContent.Video
    
    /**
     * Selects best photo based on max pixel area (Contract R9).
     */
    fun selectBestPhoto(photos: List<TgContent.Photo>): TgContent.Photo
}
```

---

## 6. Implementation Plan

### Phase 1: Core Model Extensions (Priority: HIGH)

- [ ] **1.1** Extend RawMediaMetadata with `ageRating: Int?`
- [ ] **1.2** Extend RawMediaMetadata with `rating: Double?`  
- [ ] **1.3** Check/extend ExternalIds for tmdbId String
- [ ] **1.4** Unit tests for RawMediaMetadata extensions

**Estimated Effort:** 2-4 hours

### Phase 2: TelegramMediaItem Extensions (Priority: HIGH)

- [ ] **2.1** Extend TelegramMediaItem with Structured Bundle fields
- [ ] **2.2** Create TelegramBundleType enum
- [ ] **2.3** Extend toRawMediaMetadata() for new fields
- [ ] **2.4** Unit tests for TelegramMediaItem extensions

**Estimated Effort:** 4-6 hours

### Phase 3: Message Bundler (Priority: MEDIUM)

- [ ] **3.1** Create TelegramMessageBundle data class
- [ ] **3.2** Implement TelegramMessageBundler
  - [ ] `groupByTimestamp()` with timestamp grouping
  - [ ] `classifyBundle()` with content type analysis
  - [ ] `checkBundleCohesion()` with R1b implementation
- [ ] **3.3** Unit tests with real JSON fixtures from `/legacy/docs/telegram/exports/`
- [ ] **3.4** Edge cases: Single messages, incomplete bundles

**Estimated Effort:** 6-8 hours

### Phase 4: Structured Metadata Extractor (Priority: MEDIUM)

- [ ] **4.1** Implement TelegramStructuredMetadataExtractor
  - [ ] `hasStructuredFields()` with field detection
  - [ ] `extractStructuredMetadata()` with JSON parsing
  - [ ] `extractTmdbFromUrl()` with Regex for /movie/ and /tv/
  - [ ] Schema Guards (R4) with range validation
- [ ] **4.2** Create StructuredMetadata data class
- [ ] **4.3** Unit tests with real TEXT messages from JSON exports

**Estimated Effort:** 4-6 hours

### Phase 5: Bundle-to-MediaItem Mapper (Priority: MEDIUM)

- [ ] **5.1** Implement TelegramBundleToMediaItemMapper
  - [ ] `mapBundleToMediaItems()` with field merging and multi-asset support
  - [ ] `selectPrimaryVideo()` with R8b tie-breaker
  - [ ] `selectBestPhoto()` with max pixel area (R9)
- [ ] **5.2** Integration into TelegramPipelineAdapter
- [ ] **5.3** Unit tests for mapping and tie-breakers

**Estimated Effort:** 6-8 hours

### Phase 6: Pipeline Integration (Priority: HIGH)

- [ ] **6.1** Extend TelegramPipelineAdapter
  - [ ] `fetchMediaMessages()` with bundle grouping
  - [ ] Fallback to single-message path
- [ ] **6.2** Adapt TelegramCatalogPipelineImpl
  - [ ] Bundle-aware iteration
  - [ ] Logging for bundle statistics via UnifiedLog
- [ ] **6.3** Integration tests with real chat exports

**Estimated Effort:** 8-10 hours

### Phase 7: Normalizer Optimization (Priority: LOW)

- [ ] **7.1** metadata-normalizer: TMDB ID check before lookup
  - [ ] If `externalIds.tmdbId` present → Skip search
  - [ ] Directly use TMDB details API (if needed) instead of search
- [ ] **7.2** Performance tests: Structured vs Unstructured chats

**Estimated Effort:** 4-6 hours

### Phase 8: Documentation & Cleanup (Priority: LOW)

- [ ] **8.1** Finalize Contract document
- [ ] **8.2** README updates for affected modules
- [ ] **8.3** Update CHANGELOG.md
- [ ] **8.4** Extend Gold folder with Structured Bundle patterns

**Estimated Effort:** 2-4 hours

---

## 7. Test Strategy

### 7.1 Unit Tests

**TelegramMessageBundler:**

- Test: Messages with same timestamp are grouped as BundleCandidate
- Test: Messages with different timestamps remain separate
- Test: Bundle classification (3-cluster, 2-cluster, Single)
- Test: Sorting within bundle by messageId
- Test: Bundle Cohesion Gate accepts valid candidate (messageId span ≤ 3*2^20)
- Test: Bundle Cohesion Gate rejects invalid candidate (too large span)
- Test: Cohesion Gate with album ID (primary discriminator)

**TelegramStructuredMetadataExtractor:**

- Test: TMDB URL parsing (various formats)
  - `/movie/<id>` → (id, MOVIE)
  - `/tv/<id>` → (id, TV)
  - invalid format → (null, null) + WARN log via UnifiedLog
- Test: FSK extraction (numeric)
- Test: Genre list parsing
- Test: Missing fields → null
- Test: Schema Guards
  - Year outside 1800..2100 → null
  - Rating outside 0.0..10.0 → null
  - FSK outside 0..21 → null
  - Length outside 1..600 → null

**TelegramBundleToMediaItemMapper:**

- Test: Complete 3-cluster → TelegramMediaItem
- Test: 2-cluster (TEXT+VIDEO) → TelegramMediaItem
- Test: Primary Asset Selection with multiple videos (sizeBytes → duration → messageId)
- Test: Multi-video bundle emits N assets for one work (lossless)
- Test: Poster selection via max pixel area (width * height)
- Test: Poster selection tie-breaker (height → width → messageId)

### 7.2 Integration Tests

**Fixtures:** Real JSON exports from `/legacy/docs/telegram/exports/exports/`

- Test: Chat with 3-clusters (e.g., "Mel Brooks")
  - Assert: ≥8 FULL_3ER bundles detected
- Test: Chat with 2-clusters (e.g., "Filme kompakt")
  - Assert: ≥8 COMPACT_2ER bundles detected
- Test: Chat with mixed patterns
- Test: Chat without structured data (fallback)
  - Assert: No regression for unstructured chats
- Test: Cohesion Rejection
  - Fixture: BundleCandidate with same timestamp but unrelated messages
  - Assert: Bundle is rejected/split into SINGLE units
- Test: Multi-Video Emission
  - Fixture: Bundle with multiple VIDEOs in one timestamp
  - Assert: ≥2 assets emitted for one work, all linked to same tmdbId

### 7.3 Regression Tests

- Test: Existing `toRawMediaMetadata()` remains unchanged for single messages
- Test: No breaking changes for unstructured chats
- Test: Schema guards let valid values pass through unchanged
- Test: Multi-video emission doesn't change single-video behavior

---

## 8. Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Structured chats change format | Medium | Medium | Feature flag for bundle detection |
| TMDB URL format changes | Low | Low | Multiple regex patterns |
| Performance with large chats | Low | Medium | Batch processing, lazy evaluation |
| Transport layer delivers different structure | Low | High | Adapter pattern isolates pipeline |

---

## 9. Metrics & Success Criteria

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| Structured chat detection | 100% of 8 known chats | Unit test coverage |
| TMDB lookups for structured chats | 0 | Logging counter via UnifiedLog |
| Ingest time per structured item | < 50ms | Performance logging |
| Regression errors | 0 | CI pipeline |
| Bundle cohesion rejection rate | < 5% in structured chats | Metrics dashboard |
| Multi-video asset emission | 100% of all VIDEOs emitted (lossless) | Integration tests |

---

## 10. Engineering Guardrails

These technical guardrails MUST be followed during implementation:

### 10.1 Code Quality Tools

**Detekt + ktlint/Spotless (MANDATORY in CI):**
- All modules affected by Structured Bundle work MUST pass Detekt and ktlint/Spotless in CI
- Configuration: `detekt-config.yml` (repository root)
- Pre-commit hook recommended for local development
- Violations block merge

**Gradle Dependency Analysis Plugin:**
- MUST be used to prevent accidental layer boundary violations
- Pipeline modules MUST NOT import `core/data` or `infra/data-*`
- Pipeline modules MUST NOT import `core/persistence` (ObxTelegram*, etc.)
- Violations lead to build failure

### 10.2 Test Fixtures & Drift Detection

**Golden-File / Snapshot-Style Fixtures:**
- JSON export inputs for tests MUST be versioned as golden files
- Changes to fixtures MUST be explicitly reviewed
- Automatic drift detection via hash comparison in CI
- Location: `/test-data/telegram/structured-bundles/`

**Fixture Coverage:**
- At least 3 example bundles per type (FULL_3ER, COMPACT_2ER)
- At least 2 rejection cases (cohesion failure)
- At least 1 multi-video bundle

### 10.3 Performance Benchmarking (Recommended)

**Macrobenchmark / Perfetto:**
- Recommended for measuring ingest performance after code implementation
- Baseline: Unstructured chats (current path)
- Target: Structured chats ≥5x faster (Zero-Parsing-Path)
- Documentation of benchmark results in `/docs/v2/benchmarks/`

**Not in this task:**
- Macrobenchmark setup is documented but NOT part of markdown changes
- Implementation occurs in separate code task

### 10.4 Compliance Automation

**CI Pipeline Checks (MUST):**
- Contract Compliance Checklist (Section 7 in Contract) is automatically verified
- TMDB lookup calls in pipeline modules → Build failure
- `globalId` assignment in pipeline → Build failure (already via PIPELINE_GLOBALID_ASSIGNMENT_GUARD)
- Export of `TelegramMediaItem` outside pipeline → Build failure

**Documentation Sync:**
- Contract and Masterplan MUST remain synchronized
- Changes to one document MUST be reflected in the other
- Review checklist in PR template added

### 10.5 Logging (UnifiedLog)

**All logging MUST use UnifiedLog:**
- Use lambda-based API for hot paths: `UnifiedLog.d(TAG) { "message $value" }`
- Use string-based API only for constant messages: `UnifiedLog.d(TAG, "constant")`
- Never use `android.util.Log` or `Timber` outside `infra/logging`
- See [LOGGING_CONTRACT_V2.md](contracts/LOGGING_CONTRACT_V2.md) for details

**Example:**
```kotlin
private const val TAG = "TelegramBundler"

UnifiedLog.d(TAG) { "Bundle detected: chatId=$chatId, type=$bundleType" }
UnifiedLog.w(TAG) { "TMDB URL parse failed: url=$tmdbUrl" }
UnifiedLog.e(TAG, exception) { "Failed to process bundle: chatId=$chatId" }
```

---

## 11. References

- [MEDIA_NORMALIZATION_CONTRACT.md](docs/v2/MEDIA_NORMALIZATION_CONTRACT.md)
- [TELEGRAM_PARSER_CONTRACT.md](contracts/TELEGRAM_PARSER_CONTRACT.md)
- [LOGGING_CONTRACT_V2.md](contracts/LOGGING_CONTRACT_V2.md)
- [GOLD_TELEGRAM_CORE.md](legacy/gold/telegram-pipeline/GOLD_TELEGRAM_CORE.md)
- [GLOSSARY_v2_naming_and_modules.md](contracts/GLOSSARY_v2_naming_and_modules.md)
- [AGENTS.md](AGENTS.md) – Sections 4, 11, 15

---

## Appendix A: JSON Message Examples

### A.1 TEXT Message with Structured Fields

**Movie Example:**
```json
{
  "id": 387973120,
  "chatId": -1001434421634,
  "date": 1731704712,
  "dateIso": "2024-11-15T19:45:12Z",
  "tmdbUrl": "https://www.themoviedb.org/movie/12345-movie-name",
  "tmdbRating": 7.5,
  "year": 2020,
  "originalTitle": "The Movie",
  "text": "🎬 The Movie (2020)\n⭐ 7.5/10",
  "genres": ["Action", "Drama"],
  "fsk": 12,
  "director": "John Doe",
  "lengthMinutes": 120,
  "productionCountry": "US"
}
```

**TV Show Example:**
```json
{
  "id": 387973121,
  "chatId": -1001434421634,
  "date": 1731704800,
  "dateIso": "2024-11-15T19:46:40Z",
  "tmdbUrl": "https://www.themoviedb.org/tv/98765-tv-show-name",
  "tmdbRating": 8.2,
  "year": 2019,
  "originalTitle": "The TV Show",
  "text": "📺 The TV Show (2019)\n⭐ 8.2/10",
  "genres": ["Drama", "Thriller"],
  "fsk": 16,
  "director": "Jane Smith",
  "lengthMinutes": 45,
  "productionCountry": "UK"
}
```

### A.2 VIDEO Message in Bundle

```json
{
  "id": 388021760,
  "chatId": -1001434421634,
  "date": 1731704712,
  "dateIso": "2024-11-15T19:45:12Z",
  "content": {
    "type": "video",
    "duration": 7200,
    "width": 1920,
    "height": 1080,
    "fileName": "The.Movie.2020.1080p.BluRay.x264-GROUP.mkv",
    "mimeType": "video/x-matroska",
    "supportsStreaming": true,
    "file": {
      "id": 123456,
      "remoteId": "AgACAgQAAxkBAAIB...",
      "size": 5368709120
    }
  }
}
```

### A.3 PHOTO Message in Bundle

```json
{
  "id": 387924480,
  "chatId": -1001434421634,
  "date": 1731704712,
  "dateIso": "2024-11-15T19:45:12Z",
  "content": {
    "type": "photo",
    "sizes": [
      {
        "width": 90,
        "height": 135,
        "file": { "remoteId": "AgACAgQAAxk..." }
      },
      {
        "width": 320,
        "height": 480,
        "file": { "remoteId": "AgACAgQAAxk..." }
      },
      {
        "width": 1000,
        "height": 1500,
        "file": { "remoteId": "AgACAgQAAxk..." }
      }
    ]
  }
}
```

---

## Appendix B: Glossary

| Term | Definition |
|------|------------|
| **Structured Bundle** | Group of 2-3 Telegram messages with identical timestamp that together describe a media item and pass the Bundle Cohesion Gate |
| **BundleCandidate** | Messages with identical timestamp grouped as potential bundle but not yet passed through Cohesion Gate |
| **BundleKey** | Unique identifier: (chatId, timestamp, discriminator) – where discriminator is albumId or proximity-derived |
| **Bundle Cohesion Gate** | Deterministic check whether a BundleCandidate may be treated as Structured Bundle (Contract R1b) |
| **3-cluster** | Bundle of PHOTO + TEXT + VIDEO |
| **2-cluster** | Bundle of TEXT + VIDEO or PHOTO + VIDEO |
| **Zero-Parsing-Path** | Path without title parsing thanks to structured metadata |
| **Pass-Through** | TMDB ID/year/etc. are passed unchanged from source to RawMediaMetadata |
| **Schema Guards** | Allowed sanity checks that set invalid values to null (Contract R4) |
| **Work** | Canonicalizable entity (movie/episode) resolved downstream |
| **PlayableAsset** | Concrete video file/stream reference (remoteId/fileId etc.) |
| **WorkKey** | Temporary key for grouping assets: tmdb:<type>:<id> or pipeline-local |
| **Primary Asset** | Deterministically chosen "main" asset for multi-video bundles (Contract R8b) |
| **Lossless Emission** | All VIDEOs are emitted, no variants are dropped (Contract R8) |
| **Canonical Linking** | Linking all PlayableAssets to the same canonicalId downstream (Contract Section 2.5) |
| **UnifiedLog** | v2 logging façade for all modules (see LOGGING_CONTRACT_V2.md) |
