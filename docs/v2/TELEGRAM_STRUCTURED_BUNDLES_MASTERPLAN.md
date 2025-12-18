# Telegram Structured Bundles – Masterplan

**Version:** 2.1  
**Date:** 2025-12-18  
**Status:** Draft – ready for implementation  
**Scope:** Detection and processing of structured Telegram message clusters (PHOTO→TEXT→VIDEO)

---

## Executive Summary

Analysis of 398 Telegram chat exports revealed that 8 chats contain **structured metadata** enabling dramatic pipeline optimization:

- **Zero-Parsing-Path:** TMDB IDs, titles, year, FSK directly extractable from TEXT messages
- **Zero-API-Call-Path:** No TMDB API calls needed for base metadata
- **Bundle Concept:** PHOTO→TEXT→VIDEO clusters with identical timestamp as logical unit
- **Lossless Emission:** One `RawMediaMetadata` per VIDEO (downstream merges variants)

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
│  TgMessage → TelegramMediaItem (with toMediaItem())                   │
│                                                                       │
│  TelegramCatalogPipelineImpl                                          │
│  TelegramMediaItem → RawMediaMetadata (with toRawMediaMetadata())     │
│  Emits: TelegramCatalogEvent.ItemDiscovered(TelegramCatalogItem)      │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  core/metadata-normalizer (CENTRAL)                                   │
│  RawMediaMetadata → NormalizedMedia                                   │
│  TMDB lookups (if needed), title cleaning, canonicalId computation    │
│  Multiple RawMediaMetadata with same tmdbId → single NormalizedMedia  │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  data-telegram                                                        │
│  NormalizedMedia → ObxTelegramItem (ObjectBox)                        │
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
│  ├── Classifies: Structured (3-cluster/2-cluster) vs Unstructured     │
│  └── Emits: TelegramMessageBundle or individual TgMessage             │
│                                                                       │
│  TelegramStructuredMetadataExtractor                                  │
│  ├── Extracts TEXT fields: tmdbUrl, year, fsk, genres, etc.           │
│  └── Maps PHOTO.sizes[] to ImageRef (max pixel area)                  │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  pipeline/telegram/mapper                                             │
│                                                                       │
│  TelegramBundleToMediaItemMapper                                      │
│  ├── Per accepted bundle: Nx TelegramMediaItem (one per VIDEO)        │
│  ├── Each item carries same structured metadata (tmdbId, year, etc.)  │
│  └── LOSSLESS: No VIDEO variant is dropped                            │
│                                                                       │
│  TelegramMediaItem (extended with bundle fields)                      │
│  ├── structuredTmdbId: String?      // extracted from tmdbUrl         │
│  ├── structuredRating: Double?      // tmdbRating pass-through        │
│  ├── structuredYear: Int?           // year pass-through              │
│  ├── structuredFsk: Int?            // fsk for Kids filter            │
│  ├── structuredGenres: List<String>?// genres pass-through            │
│  ├── posterRef: ImageRef?           // from PHOTO (max pixel area)    │
│  └── bundleType: BundleType         // FULL_3ER, COMPACT_2ER, SINGLE  │
│                                                                       │
│  toRawMediaMetadata() (extended)                                      │
│  ├── sourceId: unique per VIDEO (chatId_messageId)                    │
│  ├── externalIds.tmdbId from structuredTmdbId (enables unification)   │
│  ├── year from structuredYear                                         │
│  ├── poster from posterRef                                            │
│  └── ageRating from structuredFsk                                     │
│                                                                       │
│  OUTPUT: Nx RawMediaMetadata (one per VIDEO in bundle)                │
└───────────────────────────┬──────────────────────────────────────────┘
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  core/metadata-normalizer                                             │
│                                                                       │
│  Checks: Does RawMediaMetadata have externalIds.tmdbId?               │
│  ├── YES:  MUST NOT do title search; MAY validate/refresh TMDB data   │
│  └── NO:   Normal path (title parsing, TMDB search, etc.)             │
│                                                                       │
│  Downstream Unification:                                              │
│  ├── Multiple RawMediaMetadata with same tmdbId → single NormalizedMedia │
│  ├── Each source becomes a variant in NormalizedMedia.variants        │
│  └── canonicalId = tmdb:<tmdbId>                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Contract Compliance

### 3.1 MEDIA_NORMALIZATION_CONTRACT.md

> "Pipelines must not guess TMDB/IMDB IDs; they may only pass through IDs provided by the source."

✅ **Compliant:** Structured Bundles pass through TMDB IDs provided by the source (Telegram chat operator). The pipeline doesn't "guess"; it reads structured fields.

### 3.2 Global Pipeline Rules

| Rule | Status | Implementation |
|------|--------|----------------|
| Pipeline must not normalize | ✅ | Title is passed RAW |
| Pipeline must not make TMDB lookups | ✅ | TMDB ID is pass-through |
| globalId remains empty | ✅ | Normalizer computes |
| Pipeline doesn't export DTOs | ✅ | Only RawMediaMetadata leaves pipeline |
| Pipeline doesn't group/merge into canonical works | ✅ | Emits Nx RawMediaMetadata; downstream unifies |

### 3.3 Layer Boundaries

| Layer | Allowed | Forbidden |
|-------|---------|-----------|
| transport-telegram | TgMessage, TgContent | RawMediaMetadata |
| pipeline/telegram | TelegramMediaItem (internal), RawMediaMetadata (export) | ObxTelegram*, TMDB client, canonical ID computation |
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
    /** Complete 3-cluster: PHOTO + TEXT + VIDEO(s) */
    FULL_3ER,
    
    /** Compact 2-cluster: TEXT + VIDEO(s) or PHOTO + VIDEO(s) */
    COMPACT_2ER,
    
    /** Single message (no bundle) */
    SINGLE,
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

- Groups TgMessage list by identical timestamp
- Classifies clusters by type (3-cluster, 2-cluster, Single)
- Emits TelegramMessageBundle for related messages

```kotlin
/**
 * Groups Telegram messages by identical timestamp into bundles.
 *
 * Bundle Detection:
 * - Messages with identical `date` (Unix timestamp) are grouped
 * - Order in bundles: PHOTO (lowest msgId) → TEXT → VIDEO (highest msgId)
 *
 * Per MEDIA_NORMALIZATION_CONTRACT: No normalization here.
 * Bundle fields are RAW extracted and passed to TelegramMediaItem.
 */
class TelegramMessageBundler {
    
    /**
     * Groups messages by timestamp.
     *
     * @param messages Unsorted list of TgMessage
     * @return List of TelegramMessageBundle (sorted by newest timestamp)
     */
    fun groupByTimestamp(messages: List<TgMessage>): List<TelegramMessageBundle>
    
    /**
     * Classifies a bundle type based on contained message types.
     */
    fun classifyBundle(messages: List<TgMessage>): TelegramBundleType
}

data class TelegramMessageBundle(
    val timestamp: Long,
    val messages: List<TgMessage>,
    val bundleType: TelegramBundleType,
    val videoMessages: List<TgMessage>,  // All VIDEOs (may be >1)
    val textMessage: TgMessage?,
    val photoMessage: TgMessage?,
)
```

**Logging (UnifiedLog):**
```kotlin
private const val TAG = "TelegramMessageBundler"

UnifiedLog.d(TAG) { "Bundle detected: chatId=$chatId, bundleType=$bundleType, videoCount=${videoMessages.size}" }
UnifiedLog.i(TAG) { "Chat stats: chatId=$chatId, bundles=$bundleCount, singles=$singleCount, emittedItems=$emittedCount" }
```

### 5.2 TelegramStructuredMetadataExtractor

**Path:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/grouper/TelegramStructuredMetadataExtractor.kt`

**Responsibility:**

- Extracts structured fields from TEXT messages
- Parses TMDB URL to ID (supports `/movie/` and `/tv/`)
- Returns RAW values without normalization

```kotlin
/**
 * Extracts structured metadata from Telegram TEXT messages.
 *
 * Supported Fields:
 * - tmdbUrl → tmdbId (via Regex /movie/(\d+) or /tv/(\d+))
 * - tmdbRating, year, fsk, genres, director, originalTitle
 * - lengthMinutes, productionCountry
 *
 * Per MEDIA_NORMALIZATION_CONTRACT: All values RAW extracted, no cleaning.
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
     * Extracts TMDB ID from URL.
     * 
     * Example: "https://www.themoviedb.org/movie/12345-name" → "12345"
     * Example: "https://www.themoviedb.org/tv/98765-show" → "98765"
     */
    fun extractTmdbIdFromUrl(tmdbUrl: String?): String?
}

data class StructuredMetadata(
    val tmdbId: String?,
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

**Logging (UnifiedLog):**
```kotlin
private const val TAG = "TelegramStructuredMetadataExtractor"

UnifiedLog.d(TAG) { "Extracted: chatId=$chatId, tmdbId=$tmdbId, year=$year" }
UnifiedLog.w(TAG) { "TMDB URL parse failed: chatId=$chatId, url=$tmdbUrl" }
```

### 5.3 TelegramBundleToMediaItemMapper

**Path:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/mapper/TelegramBundleToMediaItemMapper.kt`

**Responsibility:**

- Converts TelegramMessageBundle → List<TelegramMediaItem> (one per VIDEO)
- Merges VIDEO, TEXT, PHOTO data
- **Lossless:** Emits all VIDEO variants, no dropping

```kotlin
/**
 * Maps TelegramMessageBundle to TelegramMediaItem(s).
 *
 * Mapping Rules:
 * 1. VIDEO message provides: remoteId, duration, fileName, mimeType, etc.
 * 2. TEXT message provides: structuredTmdbId, structuredYear, etc.
 * 3. PHOTO message provides: posterRef (max pixel area)
 *
 * Lossless Emission (Contract R7):
 * - Per accepted bundle: Nx TelegramMediaItem (one per VIDEO)
 * - All VIDEOs are emitted, no variant is dropped
 * - Each item carries the same structured metadata (for downstream unification)
 *
 * No UI Selection Policy (Contract R8b):
 * - Pipeline does NOT select "primary" asset
 * - Selection of default variant is a downstream concern
 */
class TelegramBundleToMediaItemMapper {
    
    /**
     * Maps bundle to TelegramMediaItem(s).
     * 
     * @return List of items (one per VIDEO in bundle)
     */
    fun mapBundleToMediaItems(bundle: TelegramMessageBundle): List<TelegramMediaItem>
    
    /**
     * Selects best photo based on max pixel area (width * height).
     * Ties broken by: larger height → larger width
     */
    fun selectBestPhoto(photos: List<TgContent.Photo>): TgContent.Photo
}
```

**Logging (UnifiedLog):**
```kotlin
private const val TAG = "TelegramBundleToMediaItemMapper"

UnifiedLog.d(TAG) { "Mapped bundle: chatId=$chatId, emittedItems=${items.size}" }
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
- [ ] **3.3** Unit tests with real JSON fixtures from `/legacy/docs/telegram/exports/`
- [ ] **3.4** Edge cases: Single messages, incomplete bundles

**Estimated Effort:** 6-8 hours

### Phase 4: Structured Metadata Extractor (Priority: MEDIUM)

- [ ] **4.1** Implement TelegramStructuredMetadataExtractor
  - [ ] `hasStructuredFields()` with field detection
  - [ ] `extractStructuredMetadata()` with JSON parsing
  - [ ] `extractTmdbIdFromUrl()` with Regex for /movie/ and /tv/
- [ ] **4.2** Create StructuredMetadata data class
- [ ] **4.3** Unit tests with real TEXT messages from JSON exports

**Estimated Effort:** 4-6 hours

### Phase 5: Bundle-to-MediaItem Mapper (Priority: MEDIUM)

- [ ] **5.1** Implement TelegramBundleToMediaItemMapper
  - [ ] `mapBundleToMediaItems()` with lossless multi-video emission
  - [ ] `selectBestPhoto()` with max pixel area
- [ ] **5.2** Integration into TelegramPipelineAdapter
- [ ] **5.3** Unit tests for mapping (lossless emission, shared externalIds)

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

- [ ] **7.1** metadata-normalizer: tmdbId check before lookup
  - [ ] If `externalIds.tmdbId` present → MUST NOT do title search
  - [ ] MAY validate/refresh TMDB details via tmdbId
- [ ] **7.2** Unification: Multiple RawMediaMetadata with same tmdbId → single NormalizedMedia with variants
- [ ] **7.3** Performance tests: Structured vs Unstructured chats

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

- Test: Messages with same timestamp are grouped
- Test: Messages with different timestamps remain separate
- Test: Bundle classification (3-cluster, 2-cluster, Single)
- Test: Sorting within bundle by messageId

**TelegramStructuredMetadataExtractor:**

- Test: TMDB URL parsing (various formats)
  - `/movie/<id>` → id
  - `/tv/<id>` → id
  - invalid format → null + WARN log
- Test: FSK extraction (numeric)
- Test: Genre list parsing
- Test: Missing fields → null

**TelegramBundleToMediaItemMapper:**

- Test: Complete 3-cluster → List<TelegramMediaItem>
- Test: 2-cluster (TEXT+VIDEO) → List<TelegramMediaItem>
- Test: **Lossless emission:** Bundle with 3 VIDEOs → 3 items
- Test: **Shared externalIds:** All emitted items have same tmdbId
- Test: Poster selection via max pixel area

### 7.2 Integration Tests

**Fixtures:** Real JSON exports from `/legacy/docs/telegram/exports/exports/`

- Test: Chat with 3-clusters (e.g., "Mel Brooks")
  - Assert: Bundles detected, correct total item count
- Test: Chat with 2-clusters (e.g., "Filme kompakt")
  - Assert: COMPACT_2ER bundles detected
- Test: Chat with mixed patterns
- Test: Chat without structured data (fallback)
  - Assert: No regression for unstructured chats

### 7.3 Regression Tests

- Test: Existing `toRawMediaMetadata()` remains unchanged for single messages
- Test: No breaking changes for unstructured chats

---

## 8. Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Structured chats change format | Medium | Medium | Feature flag for bundle detection |
| TMDB URL format changes | Low | Low | Multiple regex patterns |
| Performance with large chats | Low | Medium | Batch processing, lazy evaluation |
| Transport layer delivers different structure | Low | High | Adapter pattern isolates pipeline |

---

## 9. Metrics & Success Criteria

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| Structured chat detection | 100% of 8 known chats | Unit test coverage |
| TMDB lookups for structured chats | 0 (title search skipped) | Logging counter via UnifiedLog |
| Ingest time per structured item | < 50ms | Performance logging |
| Lossless emission | 100% of VIDEOs emitted | Integration tests |
| Regression errors | 0 | CI pipeline |

---

## 10. References

- [MEDIA_NORMALIZATION_CONTRACT.md](docs/v2/MEDIA_NORMALIZATION_CONTRACT.md)
- [LOGGING_CONTRACT_V2.md](contracts/LOGGING_CONTRACT_V2.md)
- [TELEGRAM_PARSER_CONTRACT.md](contracts/TELEGRAM_PARSER_CONTRACT.md)
- [GOLD_TELEGRAM_CORE.md](legacy/gold/telegram-pipeline/GOLD_TELEGRAM_CORE.md)
- [GLOSSARY_v2_naming_and_modules.md](contracts/GLOSSARY_v2_naming_and_modules.md)
- [AGENTS.md](AGENTS.md) – Sections 4, 11, 15

---

## Appendix A: JSON Message Examples

### A.1 TEXT Message with Structured Fields

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
| **Structured Bundle** | Group of 2-3 Telegram messages with identical timestamp that together describe a media item |
| **3-cluster** | Bundle of PHOTO + TEXT + VIDEO(s) |
| **2-cluster** | Bundle of TEXT + VIDEO(s) or PHOTO + VIDEO(s) |
| **Zero-Parsing-Path** | Path without title parsing thanks to structured metadata |
| **Pass-Through** | TMDB ID/year/etc. are passed unchanged from source to RawMediaMetadata |
| **Lossless Emission** | Pipeline emits one RawMediaMetadata per VIDEO; no variants are dropped |
| **Downstream Unification** | Normalizer merges multiple RawMediaMetadata with same tmdbId into single NormalizedMedia with variants |
| **BundleKey** | Pipeline-internal key `(chatId, timestamp)` for grouping messages |
| **UnifiedLog** | v2 logging façade for all modules (see LOGGING_CONTRACT_V2.md) |
