# Pipeline Architecture Audit Report

**Date:** 2025-12-09  
**Branch:** `architecture/v2-bootstrap`  
**Status:** ✅ All violations fixed

---

## 1. Executive Summary

This document summarizes the architectural audit and corrections made to the Telegram and Xtream pipelines to ensure full compliance with the v2 architecture contracts.

### Key Findings & Corrections

| Module | Violation Found | Correction Applied |
|--------|-----------------|-------------------|
| `pipeline/telegram` | TelegramMappers.kt imported `ObxTelegramMessage` (persistence) | File removed |
| `infra/data-telegram` | Imported `TelegramMediaItem` (pipeline DTO) | Refactored to use `RawMediaMetadata` only |
| `infra/data-xtream` | Imported `XtreamVodItem`, `XtreamChannel` etc. (pipeline DTOs) | Refactored to use `RawMediaMetadata` only |
| `playback/telegram` | Imported `TelegramMediaItem` (pipeline DTO) | Refactored to use `RawMediaMetadata` only |
| `playback/xtream` | `XtreamPlaybackExtensions.kt` defined extensions on pipeline DTOs | File removed |

---

## 2. Layer Boundaries (Authoritative)

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                             │
│  feature/home, feature/detail, feature/telegram-media, etc. │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       Domain Layer                           │
│       UseCases, CanonicalMediaRepository, Normalizer         │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                        Data Layer                            │
│    infra/data-telegram, infra/data-xtream                   │
│    Works with: RawMediaMetadata, Persistence Entities        │
│    NEVER imports from: pipeline/**                           │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      Pipeline Layer                          │
│    pipeline/telegram, pipeline/xtream                        │
│    Produces: RawMediaMetadata (via toRawMediaMetadata())     │
│    Uses internal DTOs: TelegramMediaItem, XtreamVodItem etc. │
│    NEVER exports internal DTOs to other layers               │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     Transport Layer                          │
│    infra/transport-telegram, infra/transport-xtream          │
│    Produces: TgMessage, TgChat, XtreamApiResponse            │
│    Handles: TDLib, OkHttp, network I/O                       │
└─────────────────────────────────────────────────────────────┘
```

### Hard Rules

1. **Transport → Pipeline → Data → Domain → UI**  
   Dependencies flow upward only. No layer may depend on a higher layer.

2. **Pipeline DTOs are internal**  
   `TelegramMediaItem`, `XtreamVodItem`, etc. are pipeline-internal.  
   They MUST NOT appear in Data, Domain, or UI layers.

3. **Data layer works with `RawMediaMetadata`**  
   Repositories receive `RawMediaMetadata` from catalog pipelines.  
   Repositories serve `RawMediaMetadata` to Domain/UI layers.

4. **Playback layer works with `PlaybackContext` or `RawMediaMetadata`**  
   Playback NEVER imports pipeline-specific types.

---

## 3. Corrected Files

### 3.1 Pipeline Layer

#### `pipeline/telegram/mapper/TelegramMappers.kt` — DELETED

**Violation:** Imported `ObxTelegramMessage` from `core.persistence.obx`.

**Reason:** Pipeline must not access persistence layer directly. Persistence entities belong to Data layer.

#### `pipeline/telegram/adapter/TelegramPipelineAdapter.kt` — FIXED

**Issue:** Minor type mismatch in `TelegramPhotoSize` construction.

**Fix:** Corrected parameter types and removed `type` parameter.

---

### 3.2 Data Layer

#### `infra/data-telegram/TelegramContentRepository.kt` — REFACTORED

**Before (Violation):**

```kotlin
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem
import com.fishit.player.pipeline.telegram.model.TelegramChatSummary

interface TelegramContentRepository {
    suspend fun getAllMediaItems(): List<TelegramMediaItem>  // ❌
    suspend fun getAllChats(): List<TelegramChatSummary>     // ❌
}
```

**After (Compliant):**

```kotlin
import com.fishit.player.core.model.RawMediaMetadata

interface TelegramContentRepository {
    fun observeAll(): Flow<List<RawMediaMetadata>>           // ✅
    suspend fun getAll(): List<RawMediaMetadata>             // ✅
    suspend fun upsertAll(items: List<RawMediaMetadata>)     // ✅
}
```

#### `infra/data-xtream/XtreamCatalogRepository.kt` — REFACTORED

**Before (Violation):**

```kotlin
import com.fishit.player.pipeline.xtream.model.XtreamVodItem
import com.fishit.player.pipeline.xtream.model.XtreamSeriesItem

interface XtreamCatalogRepository {
    fun getVodItems(): Flow<List<XtreamVodItem>>  // ❌
}
```

**After (Compliant):**

```kotlin
import com.fishit.player.core.model.RawMediaMetadata

interface XtreamCatalogRepository {
    fun observeVod(): Flow<List<RawMediaMetadata>>           // ✅
    suspend fun upsertAll(items: List<RawMediaMetadata>)     // ✅
}
```

#### `infra/data-xtream/XtreamLiveRepository.kt` — REFACTORED

Same pattern as above. Now uses `RawMediaMetadata` only.

---

### 3.3 Playback Layer

#### `playback/telegram/TelegramPlaybackSourceFactory.kt` — REFACTORED

**Before (Violation):**

```kotlin
import com.fishit.player.pipeline.telegram.model.TelegramMediaItem

interface TelegramPlaybackSourceFactory {
    fun createPlaybackContext(mediaItem: TelegramMediaItem): PlaybackContext  // ❌
}
```

**After (Compliant):**

```kotlin
import com.fishit.player.core.model.RawMediaMetadata

interface TelegramPlaybackSourceFactory {
    fun createPlaybackContext(metadata: RawMediaMetadata): PlaybackContext    // ✅
}
```

#### `playback/xtream/XtreamPlaybackExtensions.kt` — DELETED

**Violation:** Defined extension functions on pipeline types (`XtreamVodItem`, `XtreamEpisode`, `XtreamChannel`).

**Reason:** These extensions should live in the pipeline layer if needed, not in playback.

---

## 4. Data Flow After Correction

### Telegram Pipeline Flow

```
TDLib (dev.g000sha256.tdl)
    │
    ▼
TelegramTransportClient (infra/transport-telegram)
    │ returns: TgMessage, TgChat
    ▼
TelegramPipelineAdapter (pipeline/telegram/adapter)
    │ converts: TgMessage → TelegramMediaItem (internal)
    ▼
TelegramCatalogPipeline (pipeline/telegram/catalog)
    │ converts: TelegramMediaItem → RawMediaMetadata
    │ emits: TelegramCatalogEvent.ItemDiscovered(TelegramCatalogItem)
    ▼
CatalogSync (future: core/catalog-sync)
    │ calls: repository.upsertAll(items.map { it.raw })
    ▼
TelegramContentRepository (infra/data-telegram)
    │ stores: RawMediaMetadata
    │ serves: RawMediaMetadata to Domain/UI
    ▼
Domain / UI
```

### Xtream Pipeline Flow

```
Xtream API (OkHttp)
    │
    ▼
XtreamApiClient (infra/transport-xtream)
    │ returns: XtreamApiResponse DTOs
    ▼
XtreamCatalogSource (pipeline/xtream/catalog)
    │ returns: XtreamVodItem, XtreamChannel etc. (internal)
    ▼
XtreamCatalogPipeline (pipeline/xtream/catalog)
    │ converts: XtreamVodItem → RawMediaMetadata
    │ emits: XtreamCatalogEvent.ItemDiscovered(XtreamCatalogItem)
    ▼
CatalogSync (future: core/catalog-sync)
    │ calls: repository.upsertAll(items.map { it.raw })
    ▼
XtreamCatalogRepository / XtreamLiveRepository (infra/data-xtream)
    │ stores: RawMediaMetadata
    │ serves: RawMediaMetadata to Domain/UI
    ▼
Domain / UI
```

---

## 5. Compliance Checklist

### Pipeline Layer ✅

- [x] No imports from `core.persistence` (ObjectBox entities)
- [x] No imports from `infra/data-*` (repositories)
- [x] No imports from `playback/*`
- [x] No TMDB/IMDB lookups
- [x] Produces `RawMediaMetadata` as final output
- [x] Internal DTOs (`TelegramMediaItem`, `XtreamVodItem`) stay internal

### Data Layer ✅

- [x] No imports from `pipeline/*`
- [x] Works with `RawMediaMetadata` only
- [x] Provides Flow-based observation APIs
- [x] Provides `upsertAll()` for catalog sync

### Playback Layer ✅

- [x] No imports from `pipeline/*`
- [x] Works with `RawMediaMetadata` or `PlaybackContext`
- [x] Defines source factories for player integration

### Transport Layer ✅

- [x] No imports from `pipeline/*`, `data/*`, or `playback/*`
- [x] Produces raw transport DTOs only (`TgMessage`, `XtreamApiResponse`)
- [x] Handles network I/O and SDK integration

---

## 6. References

- `AGENTS.md` — Agent rules and architectural constraints
- `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md` — RawMediaMetadata contract
- `docs/v2/CANONICAL_MEDIA_SYSTEM.md` — Canonical media architecture
- `docs/v2/IMAGING_SYSTEM.md` — ImageRef handling

---

*This audit ensures the v2 pipeline architecture is correctly structured for long-term maintainability and clean separation of concerns.*
