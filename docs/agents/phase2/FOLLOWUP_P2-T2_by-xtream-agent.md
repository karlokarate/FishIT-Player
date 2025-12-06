# Phase 2 – P2-T2 Xtream Pipeline – Follow-up Tasks

**Agent ID:** xtream-agent  
**Task:** P2-T2 Xtream Pipeline STUB  
**Date:** 2025-12-06  
**Status:** Stub Implementation Complete ✅

---

## Overview

The Phase 2 stub implementation of the Xtream pipeline is now complete. This document outlines recommended follow-up work to evolve the stub into a production-ready pipeline.

---

## Phase 3: Production Implementation

### Priority 1: Core Xtream Integration

#### Task 3.1: Xtream API Client
**Estimated Effort:** Medium  
**Dependencies:** None

**Deliverables:**
- Implement `XtreamApiClient` using OkHttp
- Support Xtream API authentication (username, password, portal URL)
- Implement API endpoints:
  - `/player_api.php?username=X&password=Y&action=get_live_streams`
  - `/player_api.php?username=X&password=Y&action=get_vod_streams`
  - `/player_api.php?username=X&password=Y&action=get_series`
  - `/player_api.php?username=X&password=Y&action=get_series_info&series_id=Z`
  - `/player_api.php?username=X&password=Y&action=get_live_categories`
  - `/player_api.php?username=X&password=Y&action=get_vod_categories`
  - `/player_api.php?username=X&password=Y&action=get_series_categories`
  - `/player_api.php?username=X&password=Y&action=get_simple_data_table&stream_id=Z`
- Error handling and retry logic
- Response deserialization using kotlinx.serialization

**Reference:**
- v1 implementation: `app/src/main/java/com/chris/m3usuite/core/xtream/XtreamClient.kt`
- v1 models: `app/src/main/java/com/chris/m3usuite/core/xtream/XtreamModels.kt`

#### Task 3.2: ObjectBox Persistence
**Estimated Effort:** Medium  
**Dependencies:** Task 3.1

**Deliverables:**
- Implement `XtreamCatalogRepositoryImpl` using ObjectBox
- Implement `XtreamLiveRepositoryImpl` using ObjectBox
- Reuse existing ObjectBox entities from `core:persistence`:
  - `ObxVod`, `ObxSeries`, `ObxEpisode`, `ObxLive`, `ObxCategory`
- Implement sync logic: fetch from API → store in ObjectBox → expose via Flow
- Handle incremental updates and cache invalidation
- Map ObjectBox entities to domain models (XtreamVodItem, XtreamSeriesItem, etc.)

**Reference:**
- v1 implementation: `app/src/main/java/com/chris/m3usuite/data/repo/XtreamObxRepository.kt`

#### Task 3.3: Playback URL Building
**Estimated Effort:** Small  
**Dependencies:** Task 3.1

**Deliverables:**
- Implement `XtreamPlaybackSourceFactoryImpl`
- Build proper Xtream stream URLs:
  - VOD: `http://{portal}/{username}/{password}/{vod_id}.{ext}`
  - Series: `http://{portal}/series/{username}/{password}/{episode_id}.{ext}`
  - Live: `http://{portal}/{username}/{password}/{stream_id}` (HLS or MPEG-TS)
- Add authentication headers if required by specific panels
- Handle different stream formats (HLS, MPEG-TS, direct HTTP)

**Reference:**
- v1 URL building logic in `XtreamObxRepository` and playback helpers

---

### Priority 2: EPG Integration

#### Task 3.4: EPG Data Sync
**Estimated Effort:** Medium  
**Dependencies:** Task 3.2

**Deliverables:**
- Implement EPG data fetching from Xtream API
- Store EPG entries in ObjectBox (`ObxEpgNowNext` from v1 or new entity)
- Implement `getEpgForChannel` and `getCurrentEpg` in `XtreamLiveRepositoryImpl`
- Background sync worker for EPG updates (daily refresh)
- Handle EPG time zones and local time conversion

**Reference:**
- v1 EPG handling patterns (if available in v1 codebase)

---

### Priority 3: Advanced Features

#### Task 3.5: Categories and Filtering
**Estimated Effort:** Small  
**Dependencies:** Task 3.2

**Deliverables:**
- Implement category support in repositories
- Add filtering by category in `getVodItems`, `getSeriesItems`, `getChannels`
- Fetch and store category metadata

#### Task 3.6: Search Implementation
**Estimated Effort:** Small  
**Dependencies:** Task 3.2

**Deliverables:**
- Implement `search` in `XtreamCatalogRepositoryImpl`
- Implement `searchChannels` in `XtreamLiveRepositoryImpl`
- Use ObjectBox query capabilities for efficient text search

#### Task 3.7: Favorites and User Lists
**Estimated Effort:** Medium  
**Dependencies:** Task 3.2

**Deliverables:**
- Add favorite marking for VOD, series, and channels
- Persist favorites in ObjectBox
- Add filter methods to repositories: `getFavoriteVods`, `getFavoriteChannels`, etc.

---

### Priority 4: Quality & Optimization

#### Task 3.8: DataSource Integration
**Estimated Effort:** Medium  
**Dependencies:** Task 3.3

**Deliverables:**
- Port `DelegatingDataSourceFactory` from v1 to route URLs by scheme
- Port `RarDataSource` for RAR archive support (if needed)
- Integrate with internal player's `InternalPlaybackSourceResolver`
- Test with different Xtream stream formats

**Reference:**
- v1: `app/src/main/java/com/chris/m3usuite/player/datasource/`

#### Task 3.9: Background Sync
**Estimated Effort:** Small  
**Dependencies:** Task 3.2

**Deliverables:**
- Implement `XtreamSyncWorker` using WorkManager
- Periodic sync of catalog and EPG data
- Handle offline mode gracefully (use cached data)
- Add user settings for sync frequency

**Reference:**
- v1: `app/src/main/java/com/chris/m3usuite/work/XtreamDeltaImportWorker.kt`

---

## Phase 4: Feature Integration

### Task 4.1: Feature Module Wiring
**Estimated Effort:** Medium  
**Dependencies:** Phase 3 complete

**Deliverables:**
- Wire Xtream repositories into `:feature:library`
- Wire Xtream repositories into `:feature:live`
- Implement ViewModels for browsing VOD, series, and channels
- Add UI screens using Fish* components

### Task 4.2: Internal Player Integration
**Estimated Effort:** Small  
**Dependencies:** Task 3.8

**Deliverables:**
- Register `XtreamPlaybackSourceFactory` with `InternalPlaybackSourceResolver`
- Test VOD, series, and live playback through internal player
- Verify resume position tracking works for Xtream content

---

## Testing Checklist

### Unit Tests
- [ ] XtreamApiClient network requests (use MockWebServer)
- [ ] Repository implementations (use in-memory ObjectBox)
- [ ] URL building logic
- [ ] EPG time handling
- [ ] Search and filtering

### Integration Tests
- [ ] End-to-end sync flow (API → ObjectBox → UI)
- [ ] Playback with real Xtream panel (test credentials)
- [ ] EPG data display in live TV

### Performance Tests
- [ ] Large catalog handling (10k+ VOD items)
- [ ] EPG data refresh performance
- [ ] Search query performance

---

## Migration from v1

The following v1 components can be ported directly:

1. **XtreamClient.kt** → Adapt to v2 package structure
2. **XtreamModels.kt** → Use as reference for API response models
3. **XtreamObxRepository.kt** → Split into CatalogRepositoryImpl and LiveRepositoryImpl
4. **DelegatingDataSourceFactory** → Port to `:pipeline:xtream`
5. **XtreamDeltaImportWorker.kt** → Adapt to v2 WorkManager setup

**Important:** Do not copy v1 code directly. Refactor to match v2 architecture patterns (interfaces, dependency injection, Flow-based APIs).

---

## Documentation Updates

After production implementation:
- [ ] Update `pipeline/xtream/README.md` with usage examples
- [ ] Document Xtream API requirements and panel compatibility
- [ ] Add troubleshooting guide for common Xtream panel issues
- [ ] Update v2 architecture docs to reflect production status

---

## Estimated Total Effort

- **Priority 1 (Core):** ~2-3 weeks
- **Priority 2 (EPG):** ~1 week
- **Priority 3 (Advanced):** ~1-2 weeks
- **Priority 4 (Integration):** ~1 week

**Total:** 5-7 weeks for full production implementation

---

## Notes

1. **ObjectBox Entities:** Reuse existing entities from `core:persistence` to avoid duplication.
2. **Xtream Panel Variations:** Different IPTV panels may have slight API differences. Build flexibility into the API client.
3. **Performance:** Test with large catalogs (10k+ items) to ensure UI remains responsive.
4. **Error Handling:** Implement robust error handling for network failures and invalid credentials.
5. **Offline Mode:** Ensure app works with cached data when network is unavailable.

---

**End of Follow-up Document**
