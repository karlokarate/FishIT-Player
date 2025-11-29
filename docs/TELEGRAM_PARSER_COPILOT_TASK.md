# Copilot Implementation Playbook – Telegram Parser & Ingestion

**Audience:** GitHub Copilot / Copilot Workspace / Copilot Agents  
**Goal:** Implement the Telegram ingestion + parser system for FishIT Player according to `TELEGRAM_PARSER_CONTRACT.md`, using existing Telegram modules, CLI reference code, and JSON exports stored under `docs/`.

This document is mutable and meant to be updated by Copilot as implementation progresses.

---

## 0. Preconditions

- The following documents exist and are considered **authoritative**:
  - `docs/TELEGRAM_PARSER_CONTRACT.md`
  - `docs/telegram/cli/` – CLI source with working TDLib history paging and retry logic.
  - `docs/telegram/exports/` – JSON test fixtures for multiple chats (video/text/photo, audiobook/rar, etc.).

- Existing Telegram-related modules already exist in the repo and MUST be reused/adapted:
  - `T_TelegramServiceClient`
  - `T_TelegramFileDownloader`
  - `TelegramFileDataSource`
  - `StreamingConfig`
  - `TelegramContentRepository`
  - `TelegramLogRepository`
  - Any `Telegram*` ViewModels or repositories currently in use.

---

## 1. Kick-off Task for Copilot

**Instruction to run inside Copilot (verbatim or adapted minimally):**

> You are working on the FishIT Player repository.  
> Read `docs/TELEGRAM_PARSER_CONTRACT.md` and all files under `docs/telegram/cli/` and `docs/telegram/exports/`.  
> Your job is to design and implement a Telegram ingestion + parser system that turns Telegram messages (TDLib via tdlib-coroutines) into normalized domain items, backed by ObjectBox, exactly as described in the contract.  
> 
> Step 1: Scan the entire repository for existing Telegram-related modules, ObjectBox entities for Telegram, and any player integration points. Produce a Markdown checklist with checkboxes listing all code locations that must be created, modified, or deleted to fulfill the contract. Do not change any code yet.  
> 
> Step 2: After printing the checklist, proceed to implement the necessary modules and changes, ticking off items as you complete them and updating this Playbook file to reflect progress.

---

## 2. Repository Discovery & Impact Analysis

### 2.1 Discovery checklist – Completed Findings

- [x] **Telegram-related modules (clients, downloaders, repositories):**
  - `app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramServiceClient.kt` – Unified Telegram Engine singleton; owns the single `TdlClient` instance. **MUST be reused for all TDLib access.**
  - `app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramSession.kt` – Auth state machine, wraps TdlClient for login flow.
  - `app/src/main/java/com/chris/m3usuite/telegram/core/T_ChatBrowser.kt` – Chat list and message paging via `getChatHistory()`. **Key adapter for ingestion.**
  - `app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt` – Zero-copy windowed downloads, `ensureFileReady()`, progress Flows. **Use for playback.**
  - `app/src/main/java/com/chris/m3usuite/telegram/core/TelegramFileLoader.kt` – Thin wrapper for thumbnail downloads (`ensureThumbDownloaded()`).
  - `app/src/main/java/com/chris/m3usuite/telegram/core/StreamingConfig.kt` – Window sizes, timeouts, retry limits.
  - `app/src/main/java/com/chris/m3usuite/data/repo/TelegramContentRepository.kt` – **LEGACY**: current ObjectBox-backed repository using `ObxTelegramMessage`. **Will need redesign to match contract's `TelegramItem` fields.**
  - `app/src/main/java/com/chris/m3usuite/telegram/logging/TelegramLogRepository.kt` – Ringbuffer logging for Telegram operations.

- [x] **ObjectBox entities for Telegram content:**
  - `app/src/main/java/com/chris/m3usuite/data/obx/ObxEntities.kt`:
    - **`ObxTelegramMessage`** (lines 197–233) – **LEGACY entity that will need redesign.** Current fields:
      - `chatId`, `messageId`, `fileId`, `fileUniqueId` (integer fileId, not remoteId)
      - `thumbFileId`, `thumbLocalPath` (thumbnail via integer ID)
      - `posterFileId`, `posterLocalPath` (poster via integer ID)
      - `title`, `year`, `genres`, `fsk`, `description`
      - `isSeries`, `seriesName`, `seriesNameNormalized`, `seasonNumber`, `episodeNumber`, `episodeTitle`
      - **MISSING**: `videoRemoteId`, `videoUniqueId`, `posterRemoteId`, `posterUniqueId`, `backdropRemoteId`, `backdropUniqueId`, `videoMimeType`, `videoWidth`, `videoHeight`, `videoDurationSeconds` (new contract's nested TDLib IDs).
    - `ObxEpisode` (lines 86–110) – Has Telegram bridging fields `tgChatId`, `tgMessageId`, `tgFileId` but is **Xtream-centric**. May need parallel Telegram entity or extension.

- [x] **Existing parser logic:**
  - `app/src/main/java/com/chris/m3usuite/telegram/parser/MediaParser.kt` – **Existing parser** with:
    - `parseMessage()` – per-message parsing for `MessageVideo`, `MessageDocument`, `MessagePhoto`, `MessageText`.
    - `parseStructuredMovieChat()` – 3-message pattern (Video+Text+Photo) recognition.
    - `extractSeriesName()` – series name extraction from filenames.
    - **NOTE**: Currently returns `MediaInfo` and `ParsedItem` – not the contract's `TelegramItem` / `ExportMessage`. **Will need to be adapted or extended to produce contract DTOs.**
  - `app/src/main/java/com/chris/m3usuite/telegram/parser/TgContentHeuristics.kt` – Season/episode detection, language/quality tags, classification confidence. **Can be reused.**
  - `app/src/main/java/com/chris/m3usuite/telegram/models/MediaModels.kt` – `MediaInfo`, `MediaKind`, `ParsedItem`, `ChatContext`, etc. **Partially overlaps with contract DTOs; refactoring needed.**

- [x] **Test suites and fixtures:**
  - `app/src/test/java/com/chris/m3usuite/telegram/parser/MediaParserTest.kt` – Tests for `MediaParser.parseMessage()`.
  - `app/src/test/java/com/chris/m3usuite/telegram/parser/TgContentHeuristicsTest.kt` – Tests for season/episode detection.
  - `app/src/test/java/com/chris/m3usuite/telegram/parser/StructuredParserTest.kt` – Tests for 3-message pattern and series extraction.
  - `app/src/test/java/com/chris/m3usuite/telegram/repo/TelegramContentRepositoryTest.kt` – Repository tests.
  - `app/src/test/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloaderTest.kt` – Downloader tests.
  - `app/src/test/java/com/chris/m3usuite/telegram/work/TelegramSyncWorkerTest.kt` – Worker tests.
  - `app/src/test/java/com/chris/m3usuite/telegram/logging/TelegramLogRepositoryTest.kt` – Logging tests.
  - `app/src/test/java/com/chris/m3usuite/telegram/util/TelegramPlayUrlTest.kt` – URL tests.
  - `app/src/test/java/com/chris/m3usuite/telegram/player/TelegramFileDataSourceTest.kt` – DataSource tests.
  - **Fixtures**: `docs/telegram/exports/exports/*.json` – 270+ JSON export files with video/photo/text messages from CLI.

- [x] **UI integration points:**
  - `app/src/main/java/com/chris/m3usuite/ui/layout/FishTelegramContent.kt` – `FishTelegramContent()` and `FishTelegramRow()` composables for tiles with blue "T" badge. Uses `TelegramFileLoader` for lazy thumbnail loading.
  - `app/src/main/java/com/chris/m3usuite/ui/screens/TelegramDetailScreen.kt` – Detail screen that loads `ObxTelegramMessage` and builds `DetailPage`. Uses `TelegramPlayUrl.buildFileUrl()` for playback.
  - `app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt` – May render Telegram rows alongside Xtream content.
  - `app/src/main/java/com/chris/m3usuite/telegram/ui/TelegramLogScreen.kt` – Log viewer for Telegram logs.
  - `app/src/main/java/com/chris/m3usuite/telegram/ui/TelegramSettingsViewModel.kt` – Settings ViewModel.
  - `app/src/main/java/com/chris/m3usuite/telegram/ui/feed/TelegramActivityFeedViewModel.kt` / `TelegramActivityFeedScreen.kt` – Activity feed.

- [x] **TDLib/tdlib-coroutines setup:**
  - `T_TelegramServiceClient` creates the single `TdlClient` instance via `TdlClient.create()`.
  - `T_TelegramSession` handles auth state via `authEvents` Flow.
  - `T_ChatBrowser` exposes `loadMessagesPaged()`, `loadAllMessages()`, `observeMessages()`.
  - `T_TelegramFileDownloader` uses `client.downloadFile()`, `client.getFile()`, `client.fileUpdates` Flow.
  - **CLI Reference**: `docs/telegram/cli/src/main/kotlin/tdltest/ChatBrowser.kt` – Shows `getChatHistory()` paging with `fromMessageId`, `offset`, `limit`, `onlyLocal`, retries.

- [x] **Current use of JSON exports:**
  - JSON files under `docs/telegram/exports/exports/` are **not currently loaded at runtime**. They serve as reference fixtures.
  - CLI code in `docs/telegram/cli/` **exports JSON** using `SimpleMessageVideo`, `SimpleMessagePhoto`, `SimpleMessageText` data classes with nested `SimpleRemoteFile` (`id`, `size`, `remoteId`, `uniqueId`).
  - **These DTOs define the target schema** for `ExportMessage` in the contract.

- [x] **Player integration:**
  - `app/src/main/java/com/chris/m3usuite/telegram/player/TelegramFileDataSource.kt` – Media3 DataSource for `tg://file/<fileId>?chatId=...&messageId=...` URLs. Delegates to `FileDataSource` after TDLib prepares file.
  - `app/src/main/java/com/chris/m3usuite/telegram/util/TelegramPlayUrl.kt` – Builds `tg://file/<fileId>?chatId=...&messageId=...` URLs.
  - `app/src/main/java/com/chris/m3usuite/player/datasource/DelegatingDataSourceFactory.kt` – Routes `tg://` schemes to `TelegramFileDataSource`.
  - **NOTE**: Current playback uses **integer `fileId`** (TDLib local ID), not `remoteId`/`uniqueId`. The contract specifies storing `remoteId`/`uniqueId` for stable references. **This needs alignment.**

- [x] **Background sync:**
  - `app/src/main/java/com/chris/m3usuite/telegram/work/TelegramSyncWorker.kt` – WorkManager-based sync with modes `MODE_ALL`, `MODE_SELECTION_CHANGED`, `MODE_BACKFILL_SERIES`. Calls `TelegramContentRepository.indexChatMessages()`. **Will need to integrate new parser pipeline.**

- [x] **Code that conflicts with the new contract:**
  - **`ObxTelegramMessage`**: Uses integer `fileId` / `thumbFileId` / `posterFileId` instead of contract's `remoteId` / `uniqueId` fields. **Needs redesign.**
  - **`MediaInfo` / `ParsedItem`**: Partial overlap with contract's `TelegramItem` / `ExportMessage`. **Should be migrated to contract DTOs.**
  - **`TelegramContentRepository.indexChatMessages()`**: Currently parses messages ad-hoc. **Should use new parser pipeline.**
  - **No `TelegramBlockGrouper`**: The 120-second time-window grouping is not implemented; structured parsing only detects explicit Video+Text+Photo triplets.
  - **No `ChatScanState` / `TelegramSyncStateRepository`**: Per-chat scan progress is not persisted.

---

## 3. Implementation Plan (High-Level)

### 3.1 New core modules to implement

Copilot must introduce or extend modules roughly along these lines (exact package names should match the repo’s conventions):

- `telegram/ingestion/`
  - `TelegramHistoryScanner`
  - `TelegramUpdateHandler`
  - `TelegramIngestionCoordinator`

- `telegram/parser/`
  - `TelegramMessageModels` (ExportMessage DTOs)
  - `TelegramBlockGrouper`
  - `TelegramMetadataExtractor`
  - `TelegramItemBuilder`

- `telegram/domain/`
  - `TelegramItem`
  - `TelegramMediaRef`
  - `TelegramImageRef`
  - `TelegramMetadata`
  - `ChatScanState`, `ScanStatus`

- `telegram/repository/`
  - `TelegramContentRepository` (extended or newly structured)
  - `TelegramSyncStateRepository`

- `telegram/ui/`
  - ViewModels exposing Flows of `TelegramItem` to the UI.

### 3.2 Implementation checklist (to be adapted and extended by Copilot)

This is a starting point. Copilot MUST refine, reorder, and extend this checklist based on the actual repo structure and the contract.

#### Phase A – Offline parser based on CLI JSON

- [ ] Read `docs/TELEGRAM_PARSER_CONTRACT.md` completely and keep it open as reference.
- [ ] Implement `ExportMessage` DTOs (`ExportVideo`, `ExportPhoto`, `ExportText`, `ExportOtherRaw`) mirroring the CLI JSON schema.
- [ ] Implement a JSON loader that reads `docs/telegram/exports/*.json` into `List<ExportMessage>` per chat, using a modern JSON library (prefer `kotlinx.serialization` or Moshi).
- [ ] Implement `TelegramBlockGrouper`:
  - [ ] Sort messages per chat by `date` descending.
  - [ ] Group messages into `MessageBlock`s using the 120-second window rule.
- [ ] Implement `TelegramMetadataExtractor`:
  - [ ] Parse titles, years, genres, TMDb URLs, etc. from `ExportText`.
  - [ ] Provide fallback extraction from `ExportVideo.fileName` and `caption` when text metadata is missing.
- [ ] Implement `TelegramItemBuilder`:
  - [ ] Given a `MessageBlock`, choose an anchor video when present.
  - [ ] Attach nearest text and photo messages to the anchor using time + loose title/year matching.
  - [ ] Choose poster and backdrop images from `ExportPhoto.sizes` according to the contract.
  - [ ] Handle POSTER_ONLY and AUDIOBOOK/RAR_ITEM cases.
- [ ] Implement unit tests that:
  - [ ] Use `docs/telegram/exports/` samples as fixtures.
  - [ ] Assert the expected number of `TelegramItem`s per chat.
  - [ ] Assert specific mappings (e.g. that TMDb URLs, FSK, and genres are correctly mapped for representative chats).

#### Phase B – ObjectBox integration

- [ ] Design or update an ObjectBox entity (or entities) to persist `TelegramItem`.
- [ ] Implement mappers between `TelegramItem` and the ObjectBox entity/entities.
- [ ] Implement `TelegramContentRepository` methods:
  - [ ] `suspend fun importItems(items: List<TelegramItem>)`
  - [ ] `fun observeItemsByChat(chatId: Long): Flow<List<TelegramItem>>`
  - [ ] `fun observeAllItems(): Flow<List<TelegramItem>>`
- [ ] Add tests verifying:
  - [ ] Items round-trip correctly through ObjectBox (save → load → equals).
  - [ ] Flows emit expected values when new items are inserted.

#### Phase C – Live TDLib ingestion

- [x] Implement a mapper from TDLib DTOs (`dev.g000sha256.tdl.dto.Message*`) to `ExportMessage` that matches the CLI JSON schema.
- [x] Implement `TelegramHistoryScanner`:
  - [x] Use `T_TelegramServiceClient` to call `getChatHistory(...)`.
  - [x] Reproduce the CLI paging logic (from `docs/telegram/cli/`) for `fromMessageId`, `offset`, `limit`, and retries.
  - [x] Support both `onlyLocal = true` and `onlyLocal = false` modes.
  - [x] Emit `ExportMessage`s to the parser layer.
- [x] Implement `TelegramUpdateHandler`:
  - [x] Listen for TDLib updates via the existing TDLib client.
  - [x] Map new/edited/deleted messages into `ExportMessage`s and feed them into the parser.
  - [x] Update or remove affected `TelegramItem`s in ObjectBox.
  - **Note**: Stub implementation for Phase C; full real-time processing deferred to Phase D.
- [x] Implement `TelegramIngestionCoordinator`:
  - [x] Manage per-chat scanning state (`ChatScanState` and `ScanStatus`).
  - [x] Expose a `StateFlow<List<ChatScanState>>` for diagnostics.
  - [x] Provide APIs to trigger initial backfills and resume partial scans.

#### Phase D – UI integration

- [ ] Update or create ViewModels (e.g. `TelegramRowsViewModel`) to:
  - [ ] Subscribe to `TelegramContentRepository` flows.
  - [ ] Map `TelegramItem`s into UI-specific models for tiles and detail screens.
- [ ] Ensure tiles use:
  - [ ] `posterRef` as image source.
  - [ ] Metadata fields for titles, years, genres, TMDb rating, etc.
- [ ] Ensure detail screens use:
  - [ ] `backdropRef` as background.
  - [ ] Full metadata, including TMDb URL (for deep linking).
- [ ] Wire playback:
  - [ ] When a user selects an item, use `TelegramMediaRef.remoteId` / `uniqueId` plus existing Telegram playback components (`TelegramFileDataSource`, `T_TelegramFileDownloader`, `StreamingConfig`) to build the `MediaItem` for the Internal Player.

#### Phase E – Quality & Diagnostics

- [ ] Add unit and integration tests for:
  - [ ] Grouping and matching logic (BlockGrouper + ItemBuilder).
  - [ ] Ingestion state machine behavior under typical and edge-case conditions.
- [ ] Ensure new modules comply with existing static analysis tools (e.g., `ktlint`, `detekt`) and update configuration if necessary.
- [ ] Add minimal logging to:
  - [ ] Trace parsing decisions (e.g. why a block became a POSTER_ONLY item).
  - [ ] Debug ingestion issues (rate limiting, TDLib errors).

Copilot is expected to expand this checklist based on the real codebase and tick items as it completes them.

---

## 3.3 Repository-Specific Implementation Plan

The following plan is concrete, repository-aware, and sequences work from offline parser → ObjectBox redesign → live TDLib ingestion → UI integration → cleanup. Each item references actual file paths and distinguishes between NEW, EXISTING (refactor), and LEGACY (deprecate/remove) modules.

### Phase A – Offline Parser Based on CLI JSON (NEW modules)

- [x] **A.1 Create `ExportMessage` DTOs** matching CLI JSON schema:
  - **NEW**: `app/src/main/java/com/chris/m3usuite/telegram/parser/ExportMessageModels.kt`
  - Sealed interface `ExportMessage` with `id`, `chatId`, `dateEpochSeconds`, `dateIso`.
  - `ExportVideo` with nested `ExportRemoteFile` (id, size, remoteId, uniqueId), `ExportThumbnail`, `duration`, `width`, `height`, `fileName`, `mimeType`, `supportsStreaming`, `caption`.
  - `ExportPhoto` with `ExportPhotoSize[]` containing `ExportRemoteFile` per size.
  - `ExportText` with raw text and parsed fields (`title`, `originalTitle`, `year`, `lengthMinutes`, `fsk`, `productionCountry`, `collection`, `director`, `tmdbRating`, `genres[]`, `tmdbUrl`).
  - `ExportOtherRaw` for unsupported message types.

- [x] **A.2 Create JSON loader for offline fixtures**:
  - **NEW**: `app/src/test/java/com/chris/m3usuite/telegram/parser/ExportFixtures.kt` (test-only)
  - Use `kotlinx.serialization` with `Json { ignoreUnknownKeys = true }`.
  - `fun loadChatExport(jsonFile: File): ChatExport` returning `chatId`, `title`, `messages: List<ExportMessage>`.
  - Parse `docs/telegram/exports/exports` files.

- [x] **A.3 Implement `TelegramBlockGrouper`**:
  - **NEW**: `app/src/main/java/com/chris/m3usuite/telegram/parser/TelegramBlockGrouper.kt`
  - Sort messages by `dateEpochSeconds` descending.
  - Group into `MessageBlock(chatId, messages)` using 120-second window rule.
  - Configurable window constant.

- [x] **A.4 Extend `TelegramMetadataExtractor`**:
  - **NEW**: `app/src/main/java/com/chris/m3usuite/telegram/parser/TelegramMetadataExtractor.kt`
  - Parse `title`, `originalTitle`, `year`, `lengthMinutes`, `fsk`, `productionCountry`, `collection`, `director`, `tmdbRating`, `genres[]`, `tmdbUrl` from `ExportText`.
  - Fallback extraction from `ExportVideo.fileName` and `caption`.
  - Adult detection delegated to AdultHeuristics (chat title + extreme terms only).

- [x] **A.5 Implement `TelegramItemBuilder`**:
  - **NEW**: `app/src/main/java/com/chris/m3usuite/telegram/parser/TelegramItemBuilder.kt`
  - Given a `MessageBlock`:
    - Choose anchor `ExportVideo` (best resolution/longest duration).
    - Find nearest `ExportText` by time, with loose title/year matching.
    - Choose `posterRef` and `backdropRef` from `ExportPhoto.sizes` by aspect ratio (≤0.85 for poster, ≥1.6 for backdrop).
    - Fallback to video `thumbnail` if no photo.
  - Handle `POSTER_ONLY` (no video), `RAR_ITEM`, `AUDIOBOOK` cases.
  - Output: contract's `TelegramItem`, `TelegramMediaRef`, `TelegramImageRef`, `TelegramDocumentRef`, `TelegramMetadata`.

- [x] **A.6 Create domain DTOs per contract**:
  - **NEW**: `app/src/main/java/com/chris/m3usuite/telegram/domain/TelegramDomainModels.kt`
  - `TelegramItemType` enum: MOVIE, SERIES_EPISODE, CLIP, AUDIOBOOK, RAR_ITEM, POSTER_ONLY.
  - `TelegramMediaRef(remoteId, uniqueId, sizeBytes, mimeType, durationSeconds, width, height)` – for video files.
  - `TelegramDocumentRef(remoteId, uniqueId, sizeBytes, mimeType, fileName)` – for documents/archives (RAR, ZIP, audiobooks).
  - `TelegramImageRef(remoteId, uniqueId, width, height, sizeBytes)`.
  - `TelegramMetadata(title, originalTitle, year, lengthMinutes, fsk, productionCountry, collection, director, tmdbRating, genres[], tmdbUrl, isAdult)`.
  - `TelegramItem(chatId, anchorMessageId, type, videoRef?, documentRef?, posterRef, backdropRef, textMessageId, photoMessageId, createdAtIso, metadata)`.
  - **NOTE**: `videoRef` is used for MOVIE/SERIES_EPISODE/CLIP; `documentRef` is used for RAR_ITEM/AUDIOBOOK.

- [x] **A.7 Add unit tests for offline parser**:
  - **NEW**: `app/src/test/java/com/chris/m3usuite/telegram/parser/ExportMessageLoaderTest.kt`
  - **NEW**: `app/src/test/java/com/chris/m3usuite/telegram/parser/TelegramBlockGrouperTest.kt`
  - **NEW**: `app/src/test/java/com/chris/m3usuite/telegram/parser/TelegramItemBuilderTest.kt`
  - **NEW**: `app/src/test/java/com/chris/m3usuite/telegram/parser/TelegramMetadataExtractorTest.kt`
  - Load `docs/telegram/exports/exports/` fixtures, assert expected `TelegramItem` counts and field mappings.

### Phase B – ObjectBox Integration (NEW entities, EXISTING repo refactor)

- [x] **B.1 Design new ObjectBox entity `ObxTelegramItem`**:
  - **NEW**: Added to `app/src/main/java/com/chris/m3usuite/data/obx/ObxEntities.kt`
  - Fields per contract §5.4:
    - Identity: `@Id id: Long`, `@Index chatId: Long`, `@Index anchorMessageId: Long` (logical key).
    - Video: `videoRemoteId: String?`, `videoUniqueId: String?`, `videoFileId: Int?`, `videoSizeBytes: Long?`, `videoMimeType: String?`, `videoDurationSeconds: Int?`, `videoWidth: Int?`, `videoHeight: Int?`.
    - Document: `documentRemoteId: String?`, `documentUniqueId: String?`, `documentFileId: Int?`, `documentSizeBytes: Long?`, `documentMimeType: String?`, `documentFileName: String?`.
    - Poster: `posterRemoteId: String?`, `posterUniqueId: String?`, `posterFileId: Int?`, `posterWidth: Int?`, `posterHeight: Int?`, `posterSizeBytes: Long?`.
    - Backdrop: `backdropRemoteId: String?`, `backdropUniqueId: String?`, `backdropFileId: Int?`, `backdropWidth: Int?`, `backdropHeight: Int?`, `backdropSizeBytes: Long?`.
    - Metadata: `title`, `originalTitle`, `year`, `lengthMinutes`, `fsk`, `productionCountry`, `collection`, `director`, `tmdbRating`, `tmdbUrl`, `isAdult`, `genresJson` (serialized list).
    - Type: `itemType: String` (enum name).
    - Timestamps: `createdAtIso: String?`, `createdAtUtc: Long?`, `textMessageId: Long?`, `photoMessageId: Long?`.

- [x] **B.1b Add `ObxChatScanState` entity**:
  - **NEW**: Added to `app/src/main/java/com/chris/m3usuite/data/obx/ObxEntities.kt`
  - Fields: `@Id id: Long`, `@Index chatId: Long`, `lastScannedMessageId: Long`, `hasMoreHistory: Boolean`, `status: String`, `lastError: String?`, `@Index updatedAt: Long`.

- [x] **B.2 Create mappers domain ↔ ObjectBox**:
  - **NEW**: `app/src/main/java/com/chris/m3usuite/telegram/domain/TelegramItemMapper.kt`
  - `fun TelegramItem.toObx(): ObxTelegramItem`
  - `fun ObxTelegramItem.toDomain(): TelegramItem`
  - `fun ChatScanState.toObx(): ObxChatScanState`
  - `fun ObxChatScanState.toDomain(): ChatScanState`

- [x] **B.2b Add `ChatScanState` and `ScanStatus` domain models**:
  - **NEW**: Added to `app/src/main/java/com/chris/m3usuite/telegram/domain/TelegramDomainModels.kt`
  - `ScanStatus` enum: IDLE, SCANNING, ERROR
  - `ChatScanState` data class with persistence-ready fields

- [x] **B.3 Refactor `TelegramContentRepository`**:
  - **EXISTING/REFACTOR**: `app/src/main/java/com/chris/m3usuite/data/repo/TelegramContentRepository.kt`
  - Added methods:
    - `suspend fun upsertItems(items: List<TelegramItem>)` – bulk upsert by `(chatId, anchorMessageId)`.
    - `fun observeItemsByChat(chatId: Long): Flow<List<TelegramItem>>`
    - `fun observeAllItems(): Flow<List<TelegramItem>>`
    - `suspend fun getItem(chatId: Long, anchorMessageId: Long): TelegramItem?`
    - `suspend fun deleteItem(chatId: Long, anchorMessageId: Long)`
    - `suspend fun clearAllItems()`
    - `suspend fun getTelegramItemCount(): Long`
  - **DEPRECATED** old `indexChatMessages()` with @Deprecated annotation.

- [x] **B.3b Create `TelegramSyncStateRepository`**:
  - **NEW**: `app/src/main/java/com/chris/m3usuite/telegram/repository/TelegramSyncStateRepository.kt`
  - Methods:
    - `fun observeScanStates(): Flow<List<ChatScanState>>`
    - `suspend fun updateScanState(state: ChatScanState)`
    - `suspend fun getScanState(chatId: Long): ChatScanState?`
    - `suspend fun clearScanState(chatId: Long)`
    - `suspend fun clearAllScanStates()`

- [x] **B.4 Add tests for ObjectBox round-trip**:
  - **NEW**: `app/src/test/java/com/chris/m3usuite/telegram/repo/ObxTelegramItemRoundTripTest.kt`
    - Verifies TelegramItem ↔ ObxTelegramItem round-trip for all item types
    - Tests metadata, video refs, document refs, poster/backdrop refs
    - Tests genres JSON serialization/deserialization
    - Tests all TelegramItemType enum values
  - **NEW**: `app/src/test/java/com/chris/m3usuite/telegram/repo/TelegramSyncStateRepositoryTest.kt`
    - Verifies ChatScanState ↔ ObxChatScanState round-trip
    - Tests all ScanStatus enum values
    - Verifies repository API structure
  - **EXTENDED**: `app/src/test/java/com/chris/m3usuite/telegram/repo/TelegramContentRepositoryTest.kt`
    - Added tests for new Phase B domain-oriented APIs

### Phase C – Live TDLib Ingestion (NEW ingestion modules, EXISTING adapters)

- [x] **C.1 Create mapper TDLib → `ExportMessage`**:
  - **NEW**: `app/src/main/java/com/chris/m3usuite/telegram/parser/TdlMessageMapper.kt`
  - `fun Message.toExportMessage(): ExportMessage`
  - Extract `remoteId`, `uniqueId` from `message.content.video.video.remote`, `message.content.photo.sizes[*].photo.remote`, etc.
  - Match CLI JSON schema exactly.
  - **Completed**: Maps MessageVideo, MessagePhoto, MessageText, MessageDocument, MessageAudio → corresponding ExportMessage variants.

- [x] **C.2 Implement `TelegramHistoryScanner`**:
  - **NEW**: `app/src/main/java/com/chris/m3usuite/telegram/ingestion/TelegramHistoryScanner.kt`
  - Use `T_TelegramServiceClient.browser().loadMessagesPaged()` (or wrap `getChatHistory` directly).
  - Reproduce CLI paging logic: `fromMessageId`, `offset` (0 first page, -1 subsequent), `limit`, retry delays.
  - Support `onlyLocal = true/false`.
  - Emit `List<ExportMessage>` per batch to parser pipeline.
  - **Completed**: ScanConfig/ScanResult data classes, scan() and scanSingleBatch() methods with retry logic.

- [x] **C.3 Implement `TelegramUpdateHandler`**:
  - **NEW**: `app/src/main/java/com/chris/m3usuite/telegram/ingestion/TelegramUpdateHandler.kt`
  - Collect `T_TelegramServiceClient.activityEvents` (or `client.newMessageUpdates` directly).
  - Map new/edited messages to `ExportMessage`, feed into parser for incremental update.
  - Delete affected `TelegramItem` on `UpdateDeleteMessages`.
  - **Completed**: Stub implementation for Phase C. Full real-time processing deferred to Phase D.

- [x] **C.4 Implement `TelegramIngestionCoordinator`**:
  - **NEW**: `app/src/main/java/com/chris/m3usuite/telegram/ingestion/TelegramIngestionCoordinator.kt`
  - Manage per-chat `ChatScanState` (lastScannedMessageId, hasMoreHistory, status, lastError).
  - Expose `StateFlow<List<ChatScanState>>` for diagnostics.
  - APIs: `startBackfill(chatId)`, `resumeScan(chatId)`, `pauseScan(chatId)`.
  - **Completed**: Orchestrates TelegramHistoryScanner → TdlMessageMapper → TelegramBlockGrouper → TelegramItemBuilder → TelegramContentRepository.

- [x] **C.5 Create `TelegramSyncStateRepository`**:
  - **NEW**: `app/src/main/java/com/chris/m3usuite/telegram/repository/TelegramSyncStateRepository.kt`
  - Persist `ChatScanState` in ObjectBox or DataStore.
  - `fun observeScanStates(): Flow<List<ChatScanState>>`
  - **Completed**: Already implemented in Phase B.

- [x] **C.6 Refactor `TelegramSyncWorker`**:
  - **EXISTING/REFACTOR**: `app/src/main/java/com/chris/m3usuite/telegram/work/TelegramSyncWorker.kt`
  - Replace direct `indexChatMessages()` with:
    1. `TelegramHistoryScanner.scan(chatId)` → `List<ExportMessage>`
    2. `TelegramBlockGrouper.group(messages)` → `List<MessageBlock>`
    3. `TelegramItemBuilder.build(block)` → `List<TelegramItem>`
    4. `TelegramContentRepository.importItems(items)`
  - **Completed**: syncChat() now delegates to TelegramIngestionCoordinator.startBackfill(). Legacy methods marked @Deprecated.

### Phase D – UI Integration (EXISTING composables, playback updates)

- [ ] **D.1 Update `FishTelegramContent` to use new domain model**:
  - **EXISTING/REFACTOR**: `app/src/main/java/com/chris/m3usuite/ui/layout/FishTelegramContent.kt`
  - Use `posterRef.remoteId` / `uniqueId` to load images via `TelegramFileLoader`.
  - Display metadata (title, year, genres, rating).

- [ ] **D.2 Update `TelegramDetailScreen`**:
  - **EXISTING/REFACTOR**: `app/src/main/java/com/chris/m3usuite/ui/screens/TelegramDetailScreen.kt`
  - Load `TelegramItem` (or `ObxTelegramItem`) instead of `ObxTelegramMessage`.
  - Use `backdropRef` for hero image.
  - Display `tmdbUrl` for deep linking.

- [ ] **D.3 Update playback URL construction**:
  - **EXISTING/REFACTOR**: `app/src/main/java/com/chris/m3usuite/telegram/util/TelegramPlayUrl.kt`
  - Decide: keep `fileId`-based URL or switch to `remoteId`-based.
  - **If switching**: Update `TelegramFileDataSource` to resolve `remoteId` → `fileId` via TDLib `getRemoteFile()`.
  - **If keeping `fileId`**: Store integer `fileId` alongside `remoteId`/`uniqueId` in `TelegramMediaRef`.

- [ ] **D.4 Create or update ViewModels**:
  - **EXISTING/REFACTOR**: Consider adding `TelegramRowsViewModel` or extending `StartViewModel` to expose `Flow<List<TelegramItem>>` for home rows.

### Phase E – Quality, Cleanup & Diagnostics

- [ ] **E.1 Add parser unit tests**:
  - Extend `app/src/test/java/com/chris/m3usuite/telegram/parser/` with tests covering:
    - `TelegramBlockGrouper` 120-second window edge cases.
    - `TelegramItemBuilder` multi-video blocks, POSTER_ONLY, RAR/AUDIOBOOK.
    - `TdlMessageMapper` vs. offline fixture parity.

- [ ] **E.2 Add ingestion integration tests**:
  - Mock `T_ChatBrowser.loadMessagesPaged()` to verify `TelegramHistoryScanner` paging and retry logic.

- [ ] **E.3 Ensure lint/detekt compliance**:
  - Run `./gradlew ktlintCheck detekt lintDebug` on new modules.

- [ ] **E.4 Add logging for parser decisions**:
  - Use `TelegramLogRepository.debug()` to trace grouping, anchor selection, poster/backdrop choice.

- [ ] **E.5 Deprecate/remove legacy code after migration**:
  - **LEGACY/DEPRECATE**:
    - `ObxTelegramMessage` – keep for migration, then remove.
    - `MediaInfo`, `ParsedItem` in `MediaModels.kt` – migrate to contract DTOs.
    - `TelegramContentRepository.indexChatMessages()` – remove once new pipeline is stable.

---

## 4. Constraints & Best Practices

- **Do not duplicate logic** that already exists in Telegram modules if it can be safely refactored and reused.
- **Prefer modern libraries and patterns:**
  - `kotlinx.serialization` or Moshi over legacy Gson for new parsing code.
  - Structured concurrency with coroutine scopes clearly tied to lifecycle owners or dedicated services.
  - `Flow` / `StateFlow` for observable state and streams.
- **Maintain a clear separation of concerns:**
  - Ingestion (TDLib I/O) vs parsing (pure transformation) vs persistence vs UI.
- **Keep everything testable:**
  - Parser and grouping logic should be pure functions that are easy to unit test without TDLib.

---

## 5. Updating This Playbook

As Copilot implements the system, it MUST:

- Update checklists with real file paths and concrete class names.
- Mark completed items with `[x]`.
- Add new sections for bugs, known limitations, and future improvements.
- Keep this document in sync with the actual behavior of the codebase.

---

## 6. Resolved Design Decisions

The following design decisions have been finalized and MUST be followed by all implementation work.

---

### 6.1 Playback File ID Strategy — FINAL DECISION

- `remoteId` and `uniqueId` are the **primary identifiers** for media and artwork.
- `fileId` is a volatile TDLib-local cache value and **MUST NEVER be the primary key**.
- **Playback flow**:
  1. Given `remoteId`, call `getRemoteFile(remoteId)` to resolve or refresh a valid `fileId`.
  2. Then call `downloadFile(fileId)` or `getFile(fileId)` to prepare the file.
- The new domain model MUST store:
  - `remoteId` (required)
  - `uniqueId` (required)
  - `fileId` (optional — cached for convenience, may become stale)
  - Full video/photo/document metadata (size, width, height, mime, duration, etc.)

---

### 6.2 Migration Strategy for ObxTelegramMessage — FINAL DECISION

- **Do NOT migrate or reuse** the legacy ObjectBox model.
- Introduce a new ObjectBox entity `ObxTelegramItem` aligned with the new domain model.
- Old data may be discarded; a **full resync is acceptable** because the old pipeline is non-functional.
- Keep `ObxTelegramMessage` only temporarily to avoid breaking legacy screens, but **no new code may depend on it**.

---

### 6.3 MediaInfo / ParsedItem — FINAL DECISION

- These are **legacy parsing types** and MUST NOT be used in the new pipeline.
- A fresh parser pipeline will be built:
  - `ExportMessage` DTOs (from CLI JSON schema)
  - `TelegramBlockGrouper`
  - `TelegramMetadataExtractor`
  - `TelegramItemBuilder`
- Old parser modules (`MediaParser`, `MediaInfo`, `ParsedItem`) should be marked for deprecation after migration.

---

### 6.4 Grouping Strategy — FINAL DECISION

- The **120-second time-window block grouping** is the **canonical** grouping mechanism.
- Structured triplet detection (Video+Text+Photo) is **optional** and only applied inside blocks as a refinement.
- This ensures consistent behavior regardless of message ordering or chat structure.

---

### 6.5 Audiobook / RAR Handling — FINAL DECISION

- **Audiobook detection**:
  - Based on file extensions: `.mp3`, `.m4b`, `.aac`, `.flac`
  - OR explicit chat title indicators: "Hörbuch", "Audiobook", "AudioBook"
- **RAR/Archive detection**:
  - Based on file extensions: `.rar`, `.zip`, `.7z`
- For these item types:
  - `videoRef` is **NULL**
  - `documentRef` is used instead with full file reference fields

---

### 6.6 Adult Content Detection (isAdult) — FINAL DECISION

**Conservative detection rules only:**

- **Primary signal**: Chat title only.
  - Helper: `AdultHeuristics.isAdultChatTitle(title: String): Boolean`
  - Matches common adult indicators in chat titles (e.g., "porn", "xxx", "adult", "18+")

- **Secondary signal**: Caption text ONLY for **extremely explicit sexual terms**:
  - Whitelist: `bareback`, `gangbang`, `bukkake`, `fisting`, `bdsm`, `deepthroat`, `cumshot`
  - Helper: `AdultHeuristics.hasExtremeExplicitTerms(caption: String): Boolean`

- **NOT USED for adult classification**:
  - FSK value (FSK 18 does NOT imply adult content)
  - Genre-based classification
  - Broad NSFW heuristics or fuzzy matching

---

### 6.7 Poster / Backdrop Aspect Ratio — FINAL DECISION

- **Poster**: aspect ratio `width / height <= 0.85` (portrait orientation)
- **Backdrop**: aspect ratio `width / height >= 1.6` (landscape orientation)
- **Fallback logic**:
  - If no photo matches poster criteria → use largest available photo
  - If no photo matches backdrop criteria → leave backdrop null or use poster
  - If no photos exist → use video thumbnail as poster; backdrop is optional

---

### 6.8 TMDb URL Extraction — FINAL DECISION

1. **Step 1**: Parse structured URL entities from `TextEntityTypeTextUrl` if available.
2. **Step 2**: Fallback to regex extraction from raw text:
   ```
   https?://(?:www\.)?themoviedb\.org/(movie|tv)/\d+
   ```
3. **First valid match wins** — no priority between movie/tv types.

---

### 6.9 JSON Library — FINAL DECISION

- **ALL new parser code MUST use `kotlinx.serialization`**.
- Gson may remain for legacy components only.
- For reading CLI-generated JSON fixtures, use `Json { ignoreUnknownKeys = true }` configuration.

---

### 6.10 Chat Scan Persistence — FINAL DECISION

- Create a new ObjectBox entity `ObxChatScanState` with fields:
  - `chatId: Long` (indexed)
  - `lastScannedMessageId: Long`
  - `hasMoreHistory: Boolean`
  - `status: String` (enum: IDLE, SCANNING, ERROR)
  - `lastError: String?`
  - `updatedAt: Long` (epoch millis)
- Used by `TelegramIngestionCoordinator` to resume scans across app restarts.

---

### 6.11 Auth & Ingestion Constraints — FINAL DECISION

- **Ingestion MUST NOT run unless auth state is `READY`**.
- The auth state machine exposes:
  ```kotlin
  sealed class AuthState {
      object NOT_AUTHORIZED : AuthState()
      object WAIT_PHONE : AuthState()
      object WAIT_CODE : AuthState()
      object WAIT_PASSWORD : AuthState()
      object READY : AuthState()
      data class ERROR(val message: String) : AuthState()
  }
  ```
- `TelegramIngestionCoordinator` MUST check `authState == READY` before starting any scan.
- If TDLib DB is already authorized, skip re-login and emit `READY` directly.
- All auth transitions MUST be logged via `TelegramLogRepository`.
