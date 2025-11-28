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

Copilot MUST start by listing all affected locations. This section is intentionally empty and should be filled by Copilot.

### 2.1 Discovery checklist (to be filled by Copilot)

- [ ] Locate all existing Telegram-related modules (clients, downloaders, repositories).
- [ ] Locate all ObjectBox entities related to Telegram or chat content.
- [ ] Locate existing logic that parses Telegram messages or builds tiles from Telegram data.
- [ ] Locate any test suites or fixtures for Telegram integration.
- [ ] Identify all places where Telegram content is exposed to UI (rows, tiles, detail screens).
- [ ] Identify existing TDLib/tdlib-coroutines setup (session management, authorization state, update loops).
- [ ] Identify any current use of JSON-based Telegram exports or offline fixtures (if any).
- [ ] Identify any existing code that conflicts with the new contract or will need to be deprecated.

Copilot should update this list with concrete file paths and classes as soon as it has scanned the repository.

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

- [ ] Implement a mapper from TDLib DTOs (`dev.g000sha256.tdl.dto.Message*`) to `ExportMessage` that matches the CLI JSON schema.
- [ ] Implement `TelegramHistoryScanner`:
  - [ ] Use `T_TelegramServiceClient` to call `getChatHistory(...)`.
  - [ ] Reproduce the CLI paging logic (from `docs/telegram/cli/`) for `fromMessageId`, `offset`, `limit`, and retries.
  - [ ] Support both `onlyLocal = true` and `onlyLocal = false` modes.
  - [ ] Emit `ExportMessage`s to the parser layer.
- [ ] Implement `TelegramUpdateHandler`:
  - [ ] Listen for TDLib updates via the existing TDLib client.
  - [ ] Map new/edited/deleted messages into `ExportMessage`s and feed them into the parser.
  - [ ] Update or remove affected `TelegramItem`s in ObjectBox.
- [ ] Implement `TelegramIngestionCoordinator`:
  - [ ] Manage per-chat scanning state (`ChatScanState` and `ScanStatus`).
  - [ ] Expose a `StateFlow<List<ChatScanState>>` for diagnostics.
  - [ ] Provide APIs to trigger initial backfills and resume partial scans.

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
