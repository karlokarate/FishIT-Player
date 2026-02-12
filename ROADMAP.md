# FishIT-Player v2 – Roadmap

<!-- markdownlint-disable MD024 -->

This roadmap tracks open work on the v2 rebuild (branch `architecture/v2-bootstrap`).

**Structure:** Organized by theme (not sequential phases). Work items can progress in parallel.

For v1 roadmap history, see `legacy/docs/ROADMAP_v1.md`.

---

## 1. NX Schema Migration (OBX → NX Refactor)

**Status:** 🚧 Phase 1/7 in progress  
**Goal:** Replace 23 legacy ObjectBox entities with 16 unified NX_* entities implementing proper SSOT Work Graph.

**Binding Contract:** `contracts/NX_SSOT_CONTRACT.md` (v1.0)

### Phase 0: Foundation ✅ COMPLETE

- [x] Define 16 NX_* entities with uniqueness constraints
- [x] Create SSOT contract with 7 binding invariants (INV-01 through INV-13)
- [x] Implement kill-switch infrastructure (LEGACY/DUAL/NX_ONLY modes)
- [x] Document emergency rollback procedures

### Phase 1: NX Schema + Repositories (5-7 days) 🚧 IN PROGRESS

- [ ] Implement NxWorkRepository
- [ ] Implement NxWorkSourceRefRepository
- [ ] Implement NxWorkVariantRepository
- [ ] Implement NxWorkRelationRepository
- [ ] Implement NxWorkUserStateRepository
- [ ] Implement NxIngestLedgerRepository
- [ ] Add comprehensive unit tests for each repository
- [ ] **Category support:** Add NX_Category entity and NX_WorkCategoryRef linking table
  - Needed for Live/Library category grouping (see TODOs in NxLiveContentRepositoryImpl, NxLibraryContentRepositoryImpl)
- [ ] **Enhanced NX_Work fields:**
  - [ ] Add `channelNumber` field for live channels (NxLiveContentRepositoryImpl)
  - [ ] Add `isAvailable` logic for availability checks (WorkDetailMapper)
  - [ ] Add `lastSourceKey` and `lastVariantKey` to NX_WorkUserState (WorkDetailMapper, NxDetailMediaRepositoryImpl)
  - [ ] Store Telegram-specific metadata: `remoteId`, `mimeType` (NxTelegramMediaRepositoryImpl)
- [ ] **NX Repository optimizations:**
  - [ ] Optimize NxWorkSourceRefRepository with proper ObjectBox link queries for large datasets
  - [ ] Optimize NxWorkVariantRepository with proper ObjectBox link queries for large datasets
  - [ ] Add `isDeleted` flag to NX_Work entity for soft delete support (NxWorkRepositoryImpl)
- [ ] **EPG prefetch for favorites:** Implement background worker for periodic EPG updates (NxLiveContentRepositoryImpl)

### Phase 2: NX Ingest Path (4-5 days) 🔲 PLANNED

- [ ] Create NxCatalogIngestor (consumes RawMediaMetadata)
- [ ] Implement deterministic key generation per contract
- [ ] Implement ingest ledger (ACCEPT/REJECT/SKIP tracking)
- [ ] Add validation for INV-01 through INV-13

### Phase 3: Migration Worker (5-7 days) 🔲 PLANNED

- [ ] Implement one-time migration worker (Obx* → NX_*)
- [ ] Add progress tracking and checkpointing
- [ ] Test on production-sized datasets

### Phase 4: Dual-Read UI (7-10 days) 🔲 PLANNED

- [ ] Update all feature ViewModels to read from BOTH Obx* and NX_*
- [ ] Add UI toggle to switch between sources
- [ ] Verify feature parity (Home, Library, Detail, Live, Telegram Media)

### Phase 5: Stop-Write Legacy (2-3 days) 🔲 PLANNED

- [ ] Disable writes to Obx* repositories
- [ ] Enable NX_ONLY mode
- [ ] Monitor for regressions

### Phase 6: Stop-Read Legacy + Cleanup (3-4 days) 🔲 PLANNED

- [ ] Remove Obx* entity reads from all code
- [ ] Delete Obx* entities and old repositories
- [ ] Remove kill-switch infrastructure
- [ ] Update documentation

---

## 2. Player System (SIP - Simplified Internal Player)

**Status:** 🚧 Phase 8-14 in progress  
**Goal:** Complete internal player features (error handling, series mode, kids gate, profile system).

**Binding Contracts:**
- `contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md`
- `contracts/INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md`

### Completed (Phase 0-7) ✅

- [x] Core player session + state management
- [x] Playback source resolution via factory pattern
- [x] Subtitle/CC selection (Phase 4)
- [x] Audio track selection (Phase 7)
- [x] MiniPlayer (in-app overlay)
- [x] TV remote/DPAD input handling
- [x] NextLib FFmpeg codec integration

### Phase 8-14: Advanced Features 🚧 IN PROGRESS

**User Preferences & Audio:**
- [ ] Load preferred language from user preferences (player/internal/InternalPlayerSession.kt:332)
- [ ] Load surround sound preference from user settings (player/internal/InternalPlayerSession.kt:333)

**Live Streaming:**
- [ ] Implement stream URL switching for Xtream live contexts (player/internal/InternalPlayerSession.kt:575)

**MiniPlayer:**
- [ ] Properly observe InternalPlayerSession.state when session is active (player/internal/miniplayer/InternalMiniPlayerStateSource.kt:60)

**Error Handling & Recovery:**
- [ ] Graceful error states with retry logic
- [ ] Network interruption recovery
- [ ] Source availability fallback

**Series Mode:**
- [ ] Auto-play next episode
- [ ] Episode progress tracking
- [ ] "Up Next" UI

**Kids/Guest Profile Gate:**
- [ ] Screen time enforcement
- [ ] Content filtering via profile rules
- [ ] PIN protection

**Player UI Split (Future):**
- [ ] Define player UI contracts (player/ui-api/PlayerUiContract.kt:8, 24)
- [ ] Separate UI from internal player logic

---

## 3. Feature Screens & UI

**Status:** 🚧 In progress  
**Goal:** Complete all main UI screens with v2 architecture.

### Home Screen 🚧 PARTIAL

- [x] Basic structure with debug content
- [x] Wire catalog data from NxWorkRepository
- [x] Implement Continue Watching row
- [x] Implement Recently Added row
- [x] Add genre/category rows
- [x] Navigation to detail/player screens (AppNavHost.kt:135-152, 203-214)
- [ ] Implement full genre list query (feature/home/HomeViewModel.kt:485 - currently limited list)

### Detail Screen 🚧 PARTIAL

- [x] Basic unified detail for all media types
- [ ] Load resume position from ResumeRepository (feature/detail/UnifiedDetailViewModel.kt:665)
- [ ] Build quality/language/format info from variant metadata:
  - [ ] Extract quality from qualityTag/width/height (feature/detail/DetailSourceInfo.kt:101)
  - [ ] Extract languages from language field (feature/detail/DetailSourceInfo.kt:102)
  - [ ] Extract format from containerFormat (feature/detail/DetailSourceInfo.kt:103)
- [ ] Series episode list with progress indicators
- [ ] Related content recommendations

### Telegram Media Browser 🚧 PARTIAL

- [x] Basic chat list + media grid
- [ ] Add thumbnail image loading with Coil (feature/telegram-media/TelegramMediaScreen.kt:174)
- [ ] Add resume support for tap-to-play (feature/telegram-media/TelegramTapToPlayUseCase.kt:83)
- [ ] Implement Telegram context loading from repository for deep link playback (app-v2/navigation/PlayerNavViewModel.kt:217)

### Library Screen 🔲 PLANNED

- [ ] Category-based VOD browsing
- [ ] Series/Movie separation
- [ ] Search functionality
- [ ] Filter by genre

### Live Channels 🔲 PLANNED

- [ ] EPG grid view (see TODOs in core/catalog-sync/EpgSyncService.kt:13)
- [ ] Channel list with categories
- [ ] Live stream launch with EPG data

### Settings 🔲 PLANNED

- [ ] **Navigation:** Wire Settings navigation (app-v2/navigation/AppNavHost.kt:88)
- [ ] Profile management (create/edit/delete profiles)
- [ ] Playback preferences (language, quality, subtitles)
- [ ] Cache management (TDLib, Coil, HTTP)
- [ ] Debug/diagnostic options

### Audiobooks 🔲 FUTURE

- [ ] Chapter navigation
- [ ] Bookmark system
- [ ] Playback speed control

---

## 4. Catalog Pipelines & Sync

**Status:** ✅ Mostly complete, minor TODOs remain  
**Goal:** Robust catalog ingestion from all sources (Telegram, Xtream, IO, future Audiobook).

**Binding Contracts:**
- `contracts/MEDIA_NORMALIZATION_CONTRACT.md`
- `contracts/CATALOG_SYNC_WORKERS_CONTRACT_V2.md`

### Telegram Pipeline 🚧 SYNC SERVICE PENDING

- [x] TDLib integration via typed interfaces
- [x] Structured bundle detection (PHOTO + TEXT + VIDEO)
- [x] remoteId-first architecture
- [x] TDLib→Telethon migration (Issue #703, Telethon sidecar proxy via Chaquopy)
- [ ] **Bundle albumId support:** Add albumId to TgMessage when transport exposes it (pipeline/telegram/TelegramMessageBundler.kt:147)
- [ ] **Telegram Chain Parity** → `docs/v2/TELEGRAM_CHAIN_PARITY_PLAN.md` (Section 12)
  - [ ] TelegramSyncService + DefaultTelegramSyncService
  - [ ] TelegramSessionBootstrap + TelegramChatPreloader
  - [ ] Chat Selection UI (analog CategorySelectionScreen)
  - [ ] Entity Cleanup (TelegramRemoteId → TelegramMessageId, NX-only)
  - [ ] Timeout/Retry Optimization (TelegramTransportConfig SSOT)

### Xtream Pipeline ✅ COMPLETE

- [x] Premium Contract compliance (timeouts, headers, parallelism)
- [x] VOD/Series/Live catalog sync
- [x] Multi-account ready
- [x] Rate limiting + alias rotation

### IO Pipeline 🔲 PLANNED

- [ ] **Full implementation needed:** Currently stub only (app-v2/work/IoQuickScanWorker.kt:82)
- [ ] Local file scanning
- [ ] Metadata extraction (MediaMetadataRetriever)
- [ ] Integration with Android MediaStore
- [ ] Implement CatalogSyncService.syncIo()

### Audiobook Pipeline 🔲 FUTURE

- [ ] Audiobook-specific metadata extraction
- [ ] Chapter detection
- [ ] Integration with audiobook library UI

### EPG Sync 🔲 PLANNED

- [ ] Implement `epg_sync_global` per upcoming EPG contract (core/catalog-sync/EpgSyncService.kt:13)
- [ ] EPG normalization for all live sources
- [ ] Periodic background refresh for favorites (infra/data-nx/NxLiveContentRepositoryImpl.kt:223)

---

## 5. Metadata & Enrichment

**Status:** ✅ Core complete, TMDB integration partial  
**Goal:** Normalize catalog data and enrich with TMDB/IMDB metadata.

**Binding Contracts:**
- `contracts/TMDB_ENRICHMENT_CONTRACT.md`
- `contracts/MEDIA_NORMALIZATION_CONTRACT.md`

### Normalizer ✅ COMPLETE

- [x] Scene name parsing
- [x] Title cleanup heuristics
- [x] Season/episode extraction
- [x] Deterministic fallback canonical IDs

### TMDB Integration 🚧 PARTIAL

- [ ] **TMDB API Key Configuration:** Configure BuildConfig.TMDB_API_KEY and update DefaultTmdbConfigProvider (core/metadata-normalizer/TmdbConfig.kt:71)
- [ ] **TmdbEnrichmentBatchWorker:** Implement full search resolution via TmdbMetadataResolver (app-v2/work/TmdbEnrichmentBatchWorker.kt:285, 312)
- [ ] Implement TmdbMetadataResolver.resolveByTmdbId() (infra/data-detail/DetailEnrichmentServiceImpl.kt:*)
- [ ] Background enrichment worker
- [ ] Retry logic for failed lookups

### Availability Checks 🔲 PLANNED

- [ ] Implement availability check for detail screen (infra/data-nx/detail/WorkDetailMapper.kt:129)
- [ ] Track source health (online/offline status)

---

## 6. Imaging & Media Assets

**Status:** 🚧 Partial - core exists, Coil integration pending  
**Goal:** Efficient image loading with multiple source support (HTTP, Telegram, local files).

**Current State:**
- [x] ImageRef sealed interface (HTTP, TelegramThumb, LocalFile, InlineBytes)
- [x] TelegramThumbFetcher interface
- [ ] **Coil 3 integration:** Provide @Singleton ImageLoader (infra/imaging/ImagingModule.kt:28)
- [ ] **HTTP client:** Provide @Singleton OkHttpClient for imaging (infra/imaging/ImagingModule.kt:29)
- [ ] Implement custom Fetchers for each ImageRef type
- [ ] Disk cache configuration
- [ ] Memory cache tuning for TV/mobile

---

## 7. Logging & Telemetry

**Status:** ✅ Core complete, integrations pending  
**Goal:** Comprehensive logging with optional Firebase Crashlytics and Sentry integration.

**Binding Contract:** `contracts/LOGGING_CONTRACT_V2.md`

### UnifiedLog ✅ COMPLETE

- [x] Lambda-based lazy evaluation
- [x] Level-based filtering
- [x] Tag-based categorization
- [x] In-memory ring buffer for debug UI

### External Integrations 🔲 PLANNED

- [ ] Firebase Crashlytics integration (infra/logging/UnifiedLogInitializer.kt:112)
- [ ] Sentry integration as alternative (infra/logging/UnifiedLogInitializer.kt:121)
- [ ] Breadcrumb logging for production crash debugging (infra/logging/UnifiedLogInitializer.kt:90)
- [ ] Custom log level mapping for reporting services (infra/logging/UnifiedLogInitializer.kt:91)
- [ ] Implement Firebase module (core/firebase/FirebaseModuleMarker.kt:14)

### Telemetry 🔲 PLANNED

- [ ] Player stats (buffer, playback time, errors)
- [ ] Pipeline metrics (scan duration, item count)
- [ ] UI jank detection

---

## 8. Profile & Multi-User System

**Status:** 🔲 PLANNED  
**Goal:** Multiple profiles with kids mode, screen time, and personalized content.

### Profile Management 🔲 PLANNED

- [ ] Create/edit/delete profiles (via NX_Profile entity)
- [ ] Profile switching UI
- [ ] Default profile selection

### Kids Mode 🔲 PLANNED

- [ ] Screen time enforcement (via NX_ProfileUsage tracking)
- [ ] Content filtering (via NX_ProfileRule)
- [ ] PIN protection for exit

### User State 🔲 PLANNED

- [ ] **DefaultResumeManager:** Replace in-memory storage with ObjectBox persistence (playback/domain/DefaultResumeManager.kt:34)
- [ ] Per-profile resume positions (via NX_WorkUserState)
- [ ] Per-profile favorites/watchlist
- [ ] lastSourceKey/lastVariantKey tracking (infra/data-nx TODOs)

---

## 9. Quality & Performance

**Status:** 🔲 PLANNED  
**Goal:** Production-ready quality gates, performance profiling, and optimization.

### Performance 🔲 PLANNED

- [ ] Startup time profiling
- [ ] Memory usage monitoring
- [ ] UI frame rate analysis (jank detection)
- [ ] Database query optimization (NX_Work link queries - see infra/data-nx TODOs)

### Quality Gates 🔲 PLANNED

- [ ] Detekt rules fully enforced
- [ ] Lint-clean builds
- [ ] Minimum test coverage targets
- [ ] CI performance regression detection

### Cache Management 🔲 PLANNED

- [ ] TDLib cache size display + clear action
- [ ] Coil image cache management
- [ ] HTTP cache statistics
- [ ] Background cleanup workers

---

## 10. Documentation & Contracts

**Status:** ✅ SSOT structure complete, ongoing maintenance

### SSOT Structure ✅ COMPLETE (2026-02-04)

- [x] Delete 144 obsolete docs (131 root + 13 duplicates)
- [x] Consolidate contracts to /contracts/ (20 binding contracts)
- [x] Create canonical agent rules source (docs/meta/AGENT_RULES_CANONICAL.md)
- [x] Add drift prevention via CI (verify-agent-rules.yml)
- [x] Update 22 path-scoped instruction files
- [x] Establish document zones (root, contracts, docs/dev, docs/v2, .github/instructions)

### Ongoing Maintenance

- [ ] Keep ROADMAP.md clean (remove completed work, add new open items)
- [ ] Consolidate inline annotations into contracts when they represent architecture decisions
- [ ] Update contracts when architecture evolves
- [ ] Maintain instruction files as modules change

---

## 11. Xtream Category-Based Selective Sync

**Status:** ✅ PHASE 1-4 COMPLETE (Issue #669)
**Goal:** Let users choose which Xtream categories to sync instead of downloading everything.

**User Story:**
> After entering Xtream credentials, instead of immediately starting a full catalog sync,
> the user sees all available categories from the server and selects which VOD, Series,
> and Live categories to download. Only selected categories are then synced and persisted.

**Dependencies:**
- Transport layer: Category fetchers ✅ READY (`XtreamCategoryFetcher`)
- Persistence: `NX_Category` entity ✅ EXISTS (but unused)
- Persistence: `NX_WorkCategoryRef` linking table ✅ EXISTS (but unused)

### Phase 1: Category Fetch on Credential Entry ✅ COMPLETE

**Trigger:** After user enters Xtream credentials on Startscreen, before worker starts.

- [x] **Create `XtreamCategoryPreloader`** in `core/catalog-sync`:
  - Calls `getLiveCategories()`, `getVodCategories()`, `getSeriesCategories()` via transport
  - Returns `CategoryPreloadResult` sealed class with all three lists
  - Holds results in memory (categories are small, ~1-5 KB typical)
- [x] **Add CategoryPreloadResult sealed class:**
  - `Success(liveCategories, vodCategories, seriesCategories)`
  - `Error(message, cause)`
  - For UI state handling
- [ ] **Enhance `NxKeyGenerator.xtreamAccountKey()` for readable format:**
  - Current: `xtream:<hex_hash>` (e.g., `xtream:7f3a2b1c`) – works but opaque
  - Proposed: `xtream:<normalized_host>:<short_hash>` (e.g., `xtream:iptv.server.com:7f3a`)
  - Benefits: Human-readable (which server), still unique (hash differentiates multi-account)
  - Location: `core/persistence/obx/NxKeyGenerator.kt`
  - Add `extractHost(serverUrl)` helper (strips `http://`, `www.`, port, path)
  - Use short hash (4-6 hex chars) to keep key length reasonable
- [ ] **Generate accountKey at credential validation (before sync):**
  - After successful `get_account_info` API call, immediately:
    1. Call `NxKeyGenerator.xtreamAccountKey(host, username)`
    2. Create `NX_SourceAccount` with accountKey, host, username, encrypted credential
  - accountKey must exist BEFORE any category fetch or sync
  - All subsequent NX_* entities will reference this accountKey
- [ ] **Modify Settings/Onboarding flow:**
  - After credential validation succeeds, do NOT start sync worker immediately
  - Instead navigate to Category Selection screen

### Phase 2: Category Selection UI ✅ COMPLETE

**New screen:** `feature/settings` `CategorySelectionScreen`

- [x] **Three collapsible sections:**
  - VOD Categories (expandable)
  - Series Categories (expandable)
  - Live Categories (expandable)
- [x] **Per-section controls:**
  - "Select All" checkbox at top of each section
  - Toggling "Select All" checks/unchecks all children
- [x] **Individual category checkboxes:**
  - Display `categoryName` from API
  - Store `categoryId` for sync filtering
- [x] **Save/Cancel buttons:**
  - "Save" persists selection via `NxCategorySelectionRepository`
  - "Cancel" returns to previous screen without syncing
- [x] **Loading state:**
  - Show spinner while categories are being fetched
  - Handle empty category lists gracefully
- [x] **Empty state:**
  - "No categories available" message if lists empty
- [x] **ViewModel:**
  - `CategorySelectionViewModel` uses `XtreamCategoryPreloader`
  - Handles preloading, selection state, persistence

### Phase 3: Category Selection Persistence ✅ COMPLETE

**Storage:** Per-account category selection in `NX_XtreamCategorySelection` entity.

- [x] **Create `NX_XtreamCategorySelection` entity** in `core/persistence`:
  - Uses JSON-encoded lists for selected category IDs
  - References `accountKey` for per-account storage
  - Includes `selectAll*` flags for each content type
- [x] **Create `CategorySelectionMapper`** for domain ↔ entity mapping:
  - Converts between entity format and domain `CategorySelection`
  - Handles JSON serialization/deserialization
- [x] **Create `NxCategorySelectionRepository`** interface in `core/model`:
  - `saveSelection(accountKey, type, selectedIds)`
  - `getSelectedCategoryIds(accountKey, type): List<String>`
  - `clearSelection(accountKey, type)`
  - `setSelectAll(accountKey, type, value: Boolean)`
- [x] **Create `NxCategorySelectionRepositoryImpl`** in `infra/data-nx`:
  - ObjectBox-backed implementation

### Phase 4: Selective Sync Worker Modification ✅ COMPLETE

**Goal:** Modify `CatalogSyncService.syncXtreamBuffered()` to filter by selected categories.

- [x] **Add category filter parameters to `XtreamCatalogConfig`:**
  - `vodCategoryIds: Set<String>` (empty = all)
  - `seriesCategoryIds: Set<String>` (empty = all)
  - `liveCategoryIds: Set<String>` (empty = all)
- [x] **Update pipeline phases with filtering:**
  - `VodItemPhase`: Filters by `vodItem.categoryId`
  - `SeriesItemPhase`: Filters by `seriesItem.categoryId`
  - `LiveChannelPhase`: Filters by `liveChannel.categoryId`
  - Empty filter set = include all (backward compatible)
- [x] **Update `CatalogSyncService.syncXtreamBuffered()` interface:**
  - Added `vodCategoryIds`, `seriesCategoryIds`, `liveCategoryIds` parameters
- [x] **Update `DefaultCatalogSyncService` implementation:**
  - Passes category IDs to `XtreamCatalogConfig`
- [x] **Wire `XtreamCatalogScanWorker` to read selections:**
  - Injects `NxCategorySelectionRepository`
  - Reads selections using `accountKey` from `xtreamApiClient.capabilities`
  - Passes to `syncXtreamBuffered()` call

### Phase 5: Re-Selection & Settings Access 🔲 PLANNED

**Allow users to change category selection after initial setup.**

- [ ] **Add "Manage Categories" in Settings:**
  - Per Xtream account, show "Edit Categories" option
  - Re-opens `XtreamCategorySelectionScreen` with current selection pre-checked
- [ ] **Re-sync after selection change:**
  - When user changes selection and saves:
  - Optionally delete items from now-unselected categories
  - Trigger incremental sync for newly-selected categories
- [ ] **Show category count in settings:**
  - "VOD: 5/12 categories selected"
  - "Series: All (8 categories)"
  - "Live: 3/20 categories selected"

### Implementation Notes

**API Calls (per Xtream API spec):**
```
GET /player_api.php?action=get_live_categories
GET /player_api.php?action=get_vod_categories
GET /player_api.php?action=get_series_categories

GET /player_api.php?action=get_live_streams&category_id=X
GET /player_api.php?action=get_vod_streams&category_id=X
GET /player_api.php?action=get_series&category_id=X
```

**Benefits:**
- Reduces initial sync time dramatically (only download relevant content)
- Reduces storage usage (no unwanted categories)
- Gives users control over what appears in their library
- Enables "kids-friendly" setups by excluding adult categories

**Risk Mitigation:**
- If category fetch fails, offer "Sync All" fallback button
- Store category selection separately from content sync state
- Allow skipping category selection (default = all categories)

---

## 12. Telegram Chain Parity (Xtream-Blueprint-Abgleich)

**Status:** 🚧 Audit abgeschlossen, Implementierung ausstehend  
**Goal:** Die Telegram-Chain strukturell identisch zur Xtream-Chain aufbauen — gleiche Layer, Patterns, Lifecycle-Hooks.  
**Fahrplan:** `docs/v2/TELEGRAM_CHAIN_PARITY_PLAN.md` (vollständiges Audit + Implementierungsplan)

**Kontext:** Nach TDLib→Telethon-Migration (Issue #703, 5 Commits) fehlen wesentliche Infrastruktur-Komponenten.

### Phase 1: Infrastruktur 🔲 PLANNED

- [ ] `TelegramTransportConfig` — Timeout/Retry SSOT (analog `XtreamTransportConfig`)
- [ ] `TelegramSyncService` interface + `DefaultTelegramSyncService` — Single Entry Point
- [ ] Worker-Stub `syncTelegram()` durch Service-Aufruf ersetzen
- [ ] DI-Modul: `TelegramSyncModule`

### Phase 2: Session & Bootstrap 🔲 PLANNED

- [ ] `TelegramSessionBootstrap` — Auto-Init bei App-Start (analog `XtreamSessionBootstrap`)
- [ ] `TelegramChatPreloader` — Chat-Liste cachen + persistieren (analog `XtreamCategoryPreloader`)
- [ ] `NxTelegramChatSelectionRepository` interface (analog `NxCategorySelectionRepository`)

### Phase 3: Persistence & Data 🔲 PLANNED

- [ ] `NX_TelegramChatSelection` Entity in ObjectBox
- [ ] `NxTelegramChatSelectionRepositoryImpl` — CRUD + Sync-Gate
- [ ] Sync-Gate: `isChatSelectionComplete()` / `setChatSelectionComplete()`

### Phase 4: Chat Selection UI 🔲 PLANNED

- [ ] `ChatSelectionScreen` Composable (analog `CategorySelectionScreen`)
- [ ] `ChatSelectionViewModel` (analog `CategorySelectionViewModel`)
- [ ] Navigation-Route `Routes.TELEGRAM_CHAT_SELECTION`
- [ ] Settings-Eintrag: "Telegram Chats" (nur wenn `telegramActive`)
- [ ] Sync-Gate im Worker (`isChatSelectionComplete()` prüfen)

### Phase 5: Cleanup & Rename 🔲 PLANNED

- [ ] Rename `TelegramRemoteId` → `TelegramMessageId`
- [ ] Rename `resolveRemoteId()` → `resolveMessageMedia()`
- [ ] `ObxTelegramContentRepository` deprecieren → Delete (NX-only)
- [ ] `PlaybackHintKeys.Telegram.REMOTE_ID` evaluieren

### Phase 6: Timeout-Tuning 🔲 PLANNED

- [ ] OkHttp-Clients auf `TelegramTransportConfig` umstellen (10s connect, 30s API-read)
- [ ] Telethon connect timeout + FloodWait cap (max 120s)
- [ ] File chunk size: 512KB → 1MB
- [ ] Pagination-Hardening: per-batch timeout (60s)

### Phase 7: Verifikation 🔲 PLANNED

- [ ] Full-Chain Test: Bootstrap → Auth → Chats → Selection → Sync → Playback
- [ ] Architektur-Tests aktualisieren
- [ ] Build-Verifikation (`:app-v2:assembleDebug`)

---

## Notes

**Theme-Based Structure:**
- Work items are organized by functional area, not sequential phases
- Multiple themes can progress in parallel
- Status indicators: ✅ Complete, 🚧 In Progress, 🔲 Planned, 🔮 Future

**Roadmap Maintenance Rule:**
- Agents MUST remove completed items after finishing work
- Agents MUST add newly discovered tasks to appropriate themes
- Only open/in-progress work should remain on roadmap

**For Historical Context:**
- Phase 0-3.1 completion history: See git log before 2026-02-04
- Legacy v1 roadmap: `legacy/docs/ROADMAP_v1.md`
- OBX refactor detailed plan: Search git history for "OBX_PLATIN_REFACTOR_ROADMAP.md" (removed after Phase 0)

