> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Phase 3 Xtream Pipeline – Next Steps (Within Pipeline Boundaries)

**Agent ID:** xtream-agent  
**Task:** Xtream Pipeline Production Implementation  
**Date:** 2025-12-06  
**Status:** Planning - Phase 3 Prep Complete ✅

---

## Overview

This document outlines the next steps for the Xtream pipeline strictly within the boundaries of `:pipeline:xtream`. It explicitly excludes work that belongs in other modules (`:core:metadata-normalizer`, `:player:internal`, `:infra:*`).

---

## Contract-Compliant Architecture

### ✅ What Xtream Pipeline DOES:
- Fetch content from Xtream API
- Store content in ObjectBox (via `:core:persistence` entities)
- Expose domain models through repository interfaces
- Provide `toRawMediaMetadata()` for normalization pipeline
- Build playback URLs for Xtream streams

### ❌ What Xtream Pipeline DOES NOT DO:
- Title normalization or cleaning (handled by `:core:metadata-normalizer`)
- TMDB lookups or enrichment (handled by `:core:metadata-normalizer`)
- Canonical identity computation (handled by `:core:persistence` + normalizer)
- DataSource implementations (belong in `:player:internal` or `:infra:*`)
- SAF/SMB/ContentResolver integration (belong in `:infra:*`)
- Media3 DataSource factories (belong in `:player:internal`)

---

## Phase 3 Next Steps

### 1. DTO vs Domain Model Separation

**Goal:** Separate API response DTOs from domain models.

**Current State:**
- Domain models exist: `XtreamVodItem`, `XtreamEpisode`, `XtreamSeriesItem`, `XtreamChannel`
- No API client or DTOs yet

**Implementation:**
1. Create `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/api/`:
   - `XtreamApiClient.kt` - OkHttp-based API client
   - `XtreamApiResponse.kt` - Response wrapper types
   - `dto/XtreamVodDto.kt` - API response for VOD items
   - `dto/XtreamSeriesDto.kt` - API response for series
   - `dto/XtreamEpisodeDto.kt` - API response for episodes
   - `dto/XtreamChannelDto.kt` - API response for channels
   - `dto/XtreamCategoryDto.kt` - API response for categories
   - `dto/XtreamEpgDto.kt` - API response for EPG data

2. Create mappers:
   - `mapper/XtreamDtoMappers.kt` - Convert DTOs to domain models
   - Keep mappers simple - no business logic, just field mapping

3. DTOs should:
   - Match Xtream API JSON exactly (use kotlinx.serialization)
   - Include ALL fields returned by API (even if unused)
   - Use nullable types for optional fields
   - Have no business logic

4. Domain models should:
   - Represent clean domain concepts
   - Be independent of API structure
   - Be used by repositories and UI

**Reference:**
- Xtream API endpoints: `/player_api.php?action=...`
- v1 XtreamClient and XtreamModels for API structure

---

### 2. XtreamApiClient Implementation

**Goal:** Create HTTP client for Xtream API.

**Implementation:**
1. Create `XtreamApiClient` interface:
   ```kotlin
   interface XtreamApiClient {
       suspend fun authenticate(url: String, username: String, password: String): AuthResult
       suspend fun getVodStreams(): List<XtreamVodDto>
       suspend fun getVodCategories(): List<XtreamCategoryDto>
       suspend fun getSeries(): List<XtreamSeriesDto>
       suspend fun getSeriesInfo(seriesId: Int): SeriesInfoDto
       suspend fun getLiveStreams(): List<XtreamChannelDto>
       suspend fun getLiveCategories(): List<XtreamCategoryDto>
       suspend fun getEpgForChannel(channelId: Int): List<XtreamEpgDto>
   }
   ```

2. Implement using OkHttp (already a dependency):
   - Use kotlinx.serialization for JSON parsing
   - Handle authentication (username/password in URL params)
   - Handle errors (network, 404, 401, etc.)
   - Add retry logic for transient failures
   - Cache authentication tokens if applicable

3. NO DataSource work in this module:
   - API client only fetches metadata
   - Stream URLs are returned as strings
   - Actual playback handled by `:player:internal`

**Testing:**
- Unit tests with MockWebServer
- Test error handling
- Test authentication flows

---

### 3. Repository Implementation (ObjectBox Persistence)

**Goal:** Implement repositories using ObjectBox for local caching.

**Implementation:**
1. Use existing ObjectBox entities from `:core:persistence`:
   - `ObxVod`
   - `ObxSeries`
   - `ObxEpisode`
   - `ObxLive`
   - `ObxCategory`
   - `ObxEpgNowNext`

2. Implement `XtreamCatalogRepositoryImpl`:
   ```kotlin
   class XtreamCatalogRepositoryImpl(
       private val apiClient: XtreamApiClient,
       private val objectBox: BoxStore,
   ) : XtreamCatalogRepository {
       // Fetch from API → Store in ObjectBox → Expose via Flow
   }
   ```

3. Flow pattern:
   - Return Flow from ObjectBox query
   - Trigger background sync when data is stale
   - UI gets reactive updates when data changes

4. Sync strategy:
   - Full sync on first launch
   - Incremental updates for changes
   - Background sync worker (see Phase 3 Task 3.9 in FOLLOWUP_P2-T2)

**Testing:**
- Unit tests with in-memory ObjectBox
- Test sync logic
- Test Flow emissions

---

### 4. Playback URL Building

**Goal:** Build correct Xtream stream URLs.

**Implementation:**
1. Implement `XtreamPlaybackSourceFactoryImpl`:
   ```kotlin
   class XtreamPlaybackSourceFactoryImpl(
       private val credentials: XtreamCredentials
   ) : XtreamPlaybackSourceFactory {
       override fun createSource(context: PlaybackContext): XtreamPlaybackSource {
           return when (context.type) {
               PlaybackType.VOD -> buildVodUrl(context)
               PlaybackType.EPISODE -> buildEpisodeUrl(context)
               PlaybackType.LIVE -> buildLiveUrl(context)
               else -> error("Unsupported type")
           }
       }
   }
   ```

2. URL formats:
   - VOD: `http://{portal}/{username}/{password}/{vod_id}.{ext}`
   - Series: `http://{portal}/series/{username}/{password}/{episode_id}.{ext}`
   - Live: `http://{portal}/{username}/{password}/{stream_id}` (HLS/MPEG-TS)

3. Add auth headers if needed by specific panels

**NO DataSource Implementation:**
- This factory only builds URLs and returns descriptors
- Actual DataSource/Media3 integration belongs in `:player:internal`

---

### 5. Enhanced Domain Models

**Goal:** Add fields that will be available from real Xtream API (currently stubs).

**Fields to Add (when API is implemented):**

#### XtreamVodItem:
- `year: Int?` - Release year
- `duration: String?` - Runtime (format: "HH:MM:SS" or minutes)
- `rating: Float?` - User rating
- `plot: String?` - Description
- `director: String?` - Director name
- `cast: String?` - Cast list
- `genre: String?` - Genre
- `tmdbId: String?` - TMDB ID if provided by panel
- `imdbId: String?` - IMDB ID if provided by panel

#### XtreamEpisode:
- `airDate: String?` - Original air date
- `duration: String?` - Runtime
- `plot: String?` - Episode description
- `rating: Float?` - Episode rating

#### XtreamSeriesItem:
- `year: Int?` - Series start year
- `plot: String?` - Series description
- `genre: String?` - Genre
- `rating: Float?` - Series rating
- `tmdbId: String?` - TMDB ID if provided by panel
- `imdbId: String?` - IMDB ID if provided by panel

**Important:**
- Add these fields to DTOs and domain models
- Update `toRawMediaMetadata()` to pass through these fields
- Still NO normalization in pipeline - just pass raw values

---

### 6. EPG Integration

**Goal:** Fetch and expose EPG data for live channels.

**Implementation:**
1. Fetch EPG from Xtream API
2. Store in `ObxEpgNowNext` (or new entity if needed)
3. Implement `getEpgForChannel` and `getCurrentEpg` in `XtreamLiveRepositoryImpl`
4. Handle time zones and local time conversion
5. Background sync for EPG updates (daily refresh)

**Scope:**
- EPG data fetching and storage ONLY
- EPG UI rendering belongs in `:feature:live`

---

### 7. Categories and Filtering

**Goal:** Support content organization by categories.

**Implementation:**
1. Fetch category metadata from Xtream API
2. Store in `ObxCategory`
3. Add filtering by category in repository methods:
   - `getVodItems(categoryId: String?)`
   - `getSeriesItems(categoryId: String?)`
   - `getChannels(categoryId: String?)`
4. Expose category list: `getCategories(): Flow<List<Category>>`

---

### 8. Search Implementation

**Goal:** Enable text search within Xtream content.

**Implementation:**
1. Use ObjectBox query capabilities for text search
2. Implement in repositories:
   - `search(query: String): Flow<List<XtreamMediaItem>>`
   - `searchChannels(query: String): Flow<List<XtreamChannel>>`
3. Search across: title, plot, cast, genre
4. Return combined results from VOD + Series + Episodes

---

### 9. Favorites and User Lists

**Goal:** Allow users to mark favorites.

**Implementation:**
1. Add `isFavorite: Boolean` field to ObjectBox entities
2. Implement repository methods:
   - `markAsFavorite(id: Int, isFavorite: Boolean)`
   - `getFavoriteVods(): Flow<List<XtreamVodItem>>`
   - `getFavoriteSeries(): Flow<List<XtreamSeriesItem>>`
   - `getFavoriteChannels(): Flow<List<XtreamChannel>>`
3. Persist favorites across app restarts

---

### 10. Background Sync Worker

**Goal:** Periodic sync of catalog and EPG data.

**Implementation:**
1. Create `XtreamSyncWorker` using WorkManager
2. Sync frequency:
   - Catalog: Daily or on-demand
   - EPG: Every 6-12 hours
3. Handle offline mode gracefully (use cached data)
4. Add user settings for sync frequency
5. Respect battery and network constraints

**Reference:**
- v1: `XtreamDeltaImportWorker.kt`

---

## What Happens in Phase 3 (Outside Pipeline)

### In :core:metadata-normalizer:
1. Move `RawMediaMetadata`, `ExternalIds`, `SourceType` from `:pipeline:xtream`
2. Implement `MediaMetadataNormalizer` with title cleaning
3. Implement `TmdbMetadataResolver` for TMDB enrichment
4. Scene/title parser for extracting year, season, episode from dirty titles
5. Normalization rules for deterministic canonical titles

### In :core:persistence:
1. `CanonicalMedia` and `MediaSourceRef` entities
2. `CanonicalMediaRepository` for unified identity
3. Cross-pipeline resume tracking
4. Version grouping across pipelines

### In :player:internal or :infra:*:
1. `DelegatingDataSourceFactory` for routing URLs by scheme
2. `RarDataSource` for RAR archive support (if needed)
3. Integration with Media3/ExoPlayer
4. Xtream-specific DataSource if custom streaming logic needed

### In :feature:library and :feature:live:
1. ViewModels consuming Xtream repositories
2. UI screens using Fish* components
3. Detail screens showing unified metadata
4. Playback initiation using `PlaybackContext`

---

## Timeline Estimate

### Phase 3.1: Core API Integration (2-3 weeks)
- DTO/Domain separation
- XtreamApiClient implementation
- Repository implementation
- Playback URL building

### Phase 3.2: EPG & Categories (1 week)
- EPG data fetching
- Category support
- Filtering

### Phase 3.3: Advanced Features (1-2 weeks)
- Search implementation
- Favorites
- Background sync worker

### Phase 3.4: Enhanced Metadata (1 week)
- Add real fields from API
- Update `toRawMediaMetadata()` to pass through new fields
- Update tests

**Total:** 5-7 weeks within pipeline boundaries

---

## Testing Strategy

### Unit Tests:
- DTO to domain mapping
- API client with MockWebServer
- Repository with in-memory ObjectBox
- URL building logic
- `toRawMediaMetadata()` with enhanced fields

### Integration Tests:
- End-to-end sync flow (API → ObjectBox → Flow)
- Real Xtream panel testing (test credentials)

### Contract Compliance Tests:
- Verify NO normalization in pipeline code
- Verify NO TMDB lookups in pipeline code
- Verify raw metadata extraction only

---

## Key Principles

1. **Stay Within Boundaries:**
   - Pipeline = domain logic + data fetching + local caching
   - NO DataSource, NO SAF/SMB, NO Media3 factories
   - Those belong in `:player:internal` or `:infra:*`

2. **Contract Compliance:**
   - `toRawMediaMetadata()` passes through raw fields
   - NO title cleaning or normalization
   - NO TMDB lookups
   - Reference `v2-docs/MEDIA_NORMALIZATION_CONTRACT.md`

3. **Separation of Concerns:**
   - DTOs = API structure
   - Domain models = business logic
   - Mappers = simple conversion

4. **Reactive Patterns:**
   - Use Flow for reactive data
   - ObjectBox queries trigger Flow updates
   - UI automatically updates when data changes

5. **Testability:**
   - Constructor injection (no Hilt modules yet)
   - In-memory ObjectBox for tests
   - MockWebServer for API tests

---

## Dependencies

### Current (Phase 2):
- `:core:model` - PlaybackContext
- `:core:persistence` - ObjectBox entities
- OkHttp (already declared)
- kotlinx.serialization
- kotlinx.coroutines

### Future (Phase 3+):
- `:core:metadata-normalizer` - RawMediaMetadata types (will move there)
- WorkManager (for sync worker)

**NO NEW DEPENDENCIES on player/feature modules**

---

## Documentation Updates Required

After implementation:
1. Update `pipeline/xtream/README.md` with:
   - Usage examples
   - API client configuration
   - Repository usage patterns
   - Playback URL format documentation

2. Update `v2-docs/ARCHITECTURE_OVERVIEW_V2.md`:
   - Xtream pipeline status = "Production Ready"
   - Document DTO vs domain separation

3. Add troubleshooting guide:
   - Common Xtream panel issues
   - Authentication failures
   - Network errors

---

## Migration from v1

### Components to Port:
1. `XtreamClient.kt` → Refactor to v2 API client
2. `XtreamModels.kt` → Use as reference for DTOs
3. `XtreamObxRepository.kt` → Split into Catalog + Live repositories
4. `XtreamDeltaImportWorker.kt` → Adapt to v2 WorkManager

### DO NOT Port:
- v1 normalization logic (handled centrally in v2)
- v1 DataSource implementations (belong in `:player:internal`)
- v1 UI components (v2 uses Fish* components)

---

**End of Next Steps Document**
