# Pipeline Finalization – Implementation Status

> Last updated: 2025-01-XX (auto-generated)

## Overall Status: ✅ COMPLETED

### Post-review Hardening

- Telegram classifier now fires a warm-up callback and unsuppresses COLD chats when they become WARM/HOT (hook `onChatWarmUp` for one-time ingestion).
- Xtream globalId generation disambiguates titles without year by including source identifiers, preventing cross-title merges.
- Playback honors manual variant overrides (SourceKey) before preference sorting in `VariantPlaybackOrchestrator`.
- Normalizer filters permanently dead variants via `VariantHealthStore` and skips empty groups; language remains null when unknown to avoid preference bias.

All 8 phases (0-7) from `pipeline_finalization.md` have been fully implemented and verified.

---

## Phase 0 – Global Data Model & IDs ✅

### Task 0.1 – Pipeline ID Tags and Source Keys ✅

- **File:** `core/model/src/main/java/com/fishit/player/core/model/PipelineIdTag.kt`
  - Enum with TELEGRAM, XTREAM, IO, AUDIOBOOK, UNKNOWN
  - Short codes: "tg", "xc", "io", "ab", "unk"
- **File:** `core/model/src/main/java/com/fishit/player/core/model/SourceKey.kt`
  - Data class combining PipelineIdTag + sourceId

### Task 0.2 – Core Media Model Types ✅

- Already existed in codebase from previous phases
- `RawMediaMetadata` extended with new fields

### Task 0.3 – Canonical Key Generator ✅

- **File:** `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/FallbackCanonicalKeyGenerator.kt`
  - Scene-tag stripping and slug-based canonical key generation
  - Episode format: `episode:<slug>:SxxExx`, movie format: `movie:<slug>[:<year>]`
  - Returns null for unlinked media (e.g., LIVE or insufficient metadata)

---

## Phase 1 – Variant Model & Normalized Media ✅

### Task 1.1 – MediaVariant Model ✅

- **File:** `core/model/src/main/java/com/fishit/player/core/model/MediaVariant.kt`
  - sourceKey, qualityTag, resolutionHeight, language, isOmu, sourceUrl, available

### Task 1.2 – NormalizedMedia Model ✅

- **File:** `core/model/src/main/java/com/fishit/player/core/model/NormalizedMedia.kt`
  - canonicalId, title, year, mediaType, primaryPipelineIdTag, primarySourceId, variants

### Task 1.3 – Variant Selection ✅

- **File:** `core/model/src/main/java/com/fishit/player/core/model/VariantSelector.kt`
  - Score-based sorting algorithm
  - Availability → Language → Quality → Pipeline priority

---

## Phase 2 – Normalization & Cross-pipeline Merge ✅

### Task 2.1 – Raw → Normalized Merge ✅

- **File:** `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/Normalizer.kt`
  - Groups RawMediaMetadata by canonicalId
  - Creates NormalizedMedia with sorted variants
  - Uses VariantSelector for ordering

---

## Phase 3 – Telegram Pipeline Enhancements ✅

### Task 3.1 – Telegram Raw Metadata Extensions ✅

- **File:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramRawMetadataExtensions.kt`
  - Sets `pipelineIdTag = PipelineIdTag.TELEGRAM`
  - Leaves `globalId` empty; canonical identity assigned by normalizer

### Task 3.2 – Chat Classification ✅

- **File:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/catalog/TelegramChatMediaProfile.kt`
  - Tracks media density per chat
- **File:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/catalog/TelegramChatMediaClassifier.kt`
  - Hot/Warm/Cold classification (HOT: ≥20 media OR ≥30%, WARM: ≥3 AND ≥5%, COLD: below)

### Task 3.3 – MIME Detection ✅

- **File:** `core/model/src/main/java/com/fishit/player/core/model/MimeDecider.kt`
  - MIME/extension-based media type detection
  - `MimeMediaKind` enum (VIDEO, AUDIO, OTHER)
  - Live media updates seed the classifier with chat samples and suppress cold chats until a warm-up ingestion is triggered by `mediaUpdates`.

---

## Phase 4 – Xtream Pipeline Enhancements ✅

### Task 4.1 – Xtream Raw Metadata Extensions ✅

- **File:** `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/model/XtreamRawMetadataExtensions.kt`
  - All content types (VOD, Series, Episode, Channel) set `pipelineIdTag = PipelineIdTag.XTREAM`
  - All types leave `globalId` empty; canonical identity assigned by normalizer

---

## Phase 5 – Playback Integration ✅

### Task 5.1 – Telegram MP4 Validator ✅

- **File:** `playback/domain/src/main/java/com/fishit/player/playback/domain/TelegramMp4Validator.kt`
  - MP4 moov atom validation for progressive Telegram downloads
  - Validates file structure before playback

### Task 5.2 – Variant Playback Orchestrator ✅

- **File:** `player/internal/src/main/java/com/fishit/player/player/internal/source/VariantPlaybackOrchestrator.kt`
  - Variant-based playback with automatic fallback
  - Uses VariantSelector.sorted() for priority ordering
  - Marks failed variants via VariantHealthStore

---

## Phase 6 – Dead Media Detection ✅

### Task 6.1 – Variant Health Store ✅

- **File:** `core/model/src/main/java/com/fishit/player/core/model/VariantHealthStore.kt`
  - Tracks variant failures
  - Dead variant detection: ≥3 failures + 24h since first failure = permanently dead
  - APIs: `markFailed()`, `isAvailable()`, `isDeadPermanently()`

---

## Phase 7 – UI Settings Integration ✅

### Task 7.1 – Playback Settings Repository ✅

- **File:** `feature/settings/src/main/java/com/fishit/player/feature/settings/PlaybackSettingsRepository.kt`
  - DataStore-based persistence for VariantPreferences
  - Stores preferredLanguage, preferOmu, preferXtream

### Task 7.2 – Manual Variant Selection Store ✅

- **File:** `feature/detail/src/main/java/com/fishit/player/feature/detail/ManualVariantSelectionStore.kt`
  - In-memory store for user's manual variant selections
  - Per-media manual variant override support

---

## Build Verification ✅

All modules compile successfully:

| Module | Status |
|--------|--------|
| `core:model` | ✅ |
| `core:metadata-normalizer` | ✅ |
| `pipeline:telegram` | ✅ |
| `pipeline:xtream` | ✅ |
| `playback:domain` | ✅ |
| `player:internal` | ✅ |
| `feature:settings` | ✅ |
| `feature:detail` | ✅ |

---

## Files Created/Modified Summary

### New Files Created (15)
1. `core/model/PipelineIdTag.kt`
2. `core/model/SourceKey.kt`
3. `core/metadata-normalizer/FallbackCanonicalKeyGenerator.kt`
4. `core/model/MediaVariant.kt`
5. `core/model/NormalizedMedia.kt`
6. `core/model/VariantSelector.kt`
7. `core/model/MimeDecider.kt`
8. `core/model/VariantHealthStore.kt`
9. `core/metadata-normalizer/Normalizer.kt`
10. `pipeline/telegram/catalog/TelegramChatMediaProfile.kt`
11. `pipeline/telegram/catalog/TelegramChatMediaClassifier.kt`
12. `playback/domain/TelegramMp4Validator.kt`
13. `player/internal/source/VariantPlaybackOrchestrator.kt`
14. `feature/settings/PlaybackSettingsRepository.kt`
15. `feature/detail/ManualVariantSelectionStore.kt`

### Files Modified (5)
1. `core/model/RawMediaMetadata.kt` - Added pipelineIdTag, globalId fields
2. `pipeline/telegram/model/TelegramRawMetadataExtensions.kt` - Leaves globalId empty for normalizer assignment
3. `pipeline/xtream/model/XtreamRawMetadataExtensions.kt` - Leaves globalId empty for normalizer assignment
4. `feature/settings/build.gradle.kts` - Added DataStore + Hilt dependencies
5. `feature/detail/build.gradle.kts` - Created with proper dependencies

### Settings Updated
- `settings.gradle.kts` - Added `include(":feature:detail")`

---

## Architecture Compliance

All implementations follow the v2 architecture rules:

- ✅ No pipeline-local normalization or TMDB lookups
- ✅ No new global mutable singletons
- ✅ Pipeline modules remain pure catalog producers
- ✅ Layer boundaries respected (Transport → Pipeline → Data → Domain → UI)
- ✅ Logging via UnifiedLog (where applicable)
- ✅ No forbidden cross-layer imports
