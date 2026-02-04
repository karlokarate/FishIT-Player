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

### 🔴 Navigation Blockers (HIGH PRIORITY)

- [ ] **Settings navigation:** Wire Settings-Button action (app-v2/navigation/AppNavHost.kt:88)
- [ ] **Player navigation:** Wire Play-Button to player route (app-v2/navigation/AppNavHost.kt:119)

### Home Screen 🚧 PARTIAL

- [x] Basic structure with debug content
- [x] Wire catalog data from NxWorkRepository
- [x] Implement Continue Watching row
- [x] Implement Recently Added row
- [x] Add genre/category rows
- [ ] Navigate to detail screen on item click (feature/home/HomeViewModel.kt:415)
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

### Telegram Pipeline ✅ COMPLETE

- [x] TDLib integration via typed interfaces
- [x] Structured bundle detection (PHOTO + TEXT + VIDEO)
- [x] remoteId-first architecture
- [ ] **Bundle albumId support:** Add albumId to TgMessage when transport exposes it (pipeline/telegram/TelegramMessageBundler.kt:147)

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

