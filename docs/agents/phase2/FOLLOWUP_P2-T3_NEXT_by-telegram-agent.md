# Phase 2 Task 3 (P2-T3) NEXT STEPS - Telegram Pipeline

**Agent:** telegram-agent  
**Created:** 2025-12-06  
**Status:** PENDING  
**Dependencies:** Phase 3 (Metadata Normalization Core)

## Overview

This document outlines the future work needed to complete the Telegram pipeline integration after Phase 3 (Metadata Normalization Core) is implemented.

**CRITICAL: Type Definitions:**
- `RawMediaMetadata` - Defined in `:core:model` (NOT in `:pipeline:telegram`)
- `NormalizedMediaMetadata` - Defined in `:core:model` (NOT in `:pipeline:telegram`)
- `ExternalIds` - Defined in `:core:model` (NOT in `:pipeline:telegram`)
- `SourceType` - Defined in `:core:model` (NOT in `:pipeline:telegram`)

**CRITICAL: Normalization Behavior:**
- `MediaMetadataNormalizer` - Implemented in `:core:metadata-normalizer` (NOT in `:pipeline:telegram`)
- `TmdbMetadataResolver` - Implemented in `:core:metadata-normalizer` (NOT in `:pipeline:telegram`)

The Telegram pipeline NEVER defines these types or implements normalization behavior locally.

**Prerequisites:**
1. `:core:model` module has `RawMediaMetadata` and related type definitions
2. `:core:metadata-normalizer` module created with behavior implementations
3. `MediaMetadataNormalizer` interface and implementation (centralized)
4. `TmdbMetadataResolver` interface and implementation (centralized)
5. `CanonicalMediaRepository` for persistence (centralized)

---

## 1. Implement Real toRawMediaMetadata()

**Task:** Convert the structure-only documentation in `TelegramRawMetadataContract.kt` into a working extension function.

**CRITICAL:** This implementation provides RAW data ONLY. NO cleaning, NO normalization, NO TMDB lookups.

**Location:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/mapper/TelegramRawMetadataExtensions.kt` (new file)

**Implementation Steps:**

1. **Add dependencies:**
   ```kotlin
   // pipeline/telegram/build.gradle.kts
   dependencies {
       implementation(project(":core:model"))  // For RawMediaMetadata types
       implementation(project(":core:metadata-normalizer"))  // For normalization behavior (optional)
   }
   ```

2. **Implement the extension function:**
   ```kotlin
   import com.fishit.player.core.model.RawMediaMetadata
   import com.fishit.player.core.model.ExternalIds
   import com.fishit.player.core.model.SourceType
   
   fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata {
       return RawMediaMetadata(
           // CRITICAL: extractRawTitle() does simple field priority ONLY
           // NO cleaning, NO tag stripping, NO normalization
           originalTitle = extractRawTitle(),
           year = this.year,
           season = this.seasonNumber,
           episode = this.episodeNumber,
           durationMinutes = this.durationSecs?.let { it / 60 },
           externalIds = ExternalIds(), // Telegram doesn't provide external IDs
           sourceType = SourceType.TELEGRAM,
           sourceLabel = buildTelegramSourceLabel(),
           sourceId = this.remoteId ?: "msg:${chatId}:${messageId}"
       )
   }
   
   private fun TelegramMediaItem.extractRawTitle(): String {
       // Priority: title > episodeTitle > caption > fileName
       // CRITICAL: NO cleaning of technical tags - pass raw source data AS-IS
       // Example: "Movie.2020.1080p.BluRay.x264-GROUP" -> returned unchanged
       return when {
           title.isNotBlank() -> title
           episodeTitle?.isNotBlank() == true -> episodeTitle!!
           caption?.isNotBlank() == true -> caption!!
           fileName?.isNotBlank() == true -> fileName!!
           else -> "Untitled Media $messageId"
       }
   }
   
   private fun TelegramMediaItem.buildTelegramSourceLabel(): String {
       // Example: "Telegram Chat: Movies HD"
       // Will be enriched with actual chat names when real TDLib integration exists
       return "Telegram Chat: $chatId"
   }
   ```

3. **Add unit tests:**
   - Test title extraction priority (title > episodeTitle > caption > fileName)
   - Test that NO cleaning happens (tags preserved)
   - Test year, season, episode, duration mapping
   - Test sourceId generation (remoteId vs fallback)
   - Test that externalIds is always empty for Telegram

**Contract Requirements:**
- ✅ NO title cleaning (preserve resolution tags, codec info, release groups)
- ✅ NO normalization or heuristics
- ✅ NO TMDB lookups
- ✅ Simple field priority selector
- ✅ All fields passed as-is from source

---

## 2. Real TDLib Integration

**Task:** Implement real TDLib-based repositories to replace stubs.

**Background:** The v1 architecture has TDLib integration components that need to be ported to v2.

**Components to Port (from .github/tdlibAgent.md):**

### 2.1 Core TDLib Wrapper
- **T_TelegramServiceClient** - Main TDLib client wrapper
  - Authentication state management
  - Connection lifecycle
  - Message sync orchestration
  - File download queue

### 2.2 File Download System
- **T_TelegramFileDownloader** - Download queue manager
  - Priority-based download queue
  - Progress tracking
  - Pause/resume support
  - Network-aware scheduling

### 2.3 Streaming Integration
- **TelegramFileDataSource** - Media3 DataSource implementation
  - Zero-copy streaming from TDLib
  - 16MB windowed downloads
  - Progress callbacks
  - Seek support

- **TelegramStreamingSettingsProvider** - Streaming configuration
  - Window size settings
  - Buffer configuration
  - Network preferences

### 2.4 Repository Implementations

**TdLibTelegramContentRepository (replaces StubTelegramContentRepository):**

```kotlin
class TdLibTelegramContentRepository(
    private val telegramServiceClient: T_TelegramServiceClient,
    private val obxTelegramMessageBox: Box<ObxTelegramMessage>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : TelegramContentRepository {
    
    override suspend fun getAllMediaItems(
        offset: Int,
        limit: Int
    ): List<TelegramMediaItem> = withContext(dispatcher) {
        obxTelegramMessageBox
            .query()
            .orderDesc(ObxTelegramMessage_.date)
            .build()
            .find(offset.toLong(), limit.toLong())
            .map { TelegramMappers.fromObxTelegramMessage(it) }
    }
    
    override suspend fun getMediaItemsByChat(
        chatId: Long,
        offset: Int,
        limit: Int
    ): List<TelegramMediaItem> = withContext(dispatcher) {
        obxTelegramMessageBox
            .query(ObxTelegramMessage_.chatId.equal(chatId))
            .orderDesc(ObxTelegramMessage_.date)
            .build()
            .find(offset.toLong(), limit.toLong())
            .map { TelegramMappers.fromObxTelegramMessage(it) }
    }
    
    override suspend fun refresh() {
        // Trigger TDLib sync via telegramServiceClient
        telegramServiceClient.refreshChats()
    }
    
    // Implement remaining interface methods...
}
```

**TdLibTelegramPlaybackSourceFactory (replaces StubTelegramPlaybackSourceFactory):**

```kotlin
class TdLibTelegramPlaybackSourceFactory(
    private val telegramServiceClient: T_TelegramServiceClient,
    private val streamingSettingsProvider: TelegramStreamingSettingsProvider
) : TelegramPlaybackSourceFactory {
    
    override suspend fun createPlaybackContext(
        mediaItem: TelegramMediaItem
    ): PlaybackContext? {
        if (!mediaItem.isPlayable()) return null
        
        return PlaybackContext(
            contentId = mediaItem.toTelegramUri(),
            sourceType = PlaybackType.TELEGRAM,
            title = mediaItem.title,
            subtitle = buildSubtitle(mediaItem),
            durationMs = mediaItem.durationSecs?.times(1000)?.toLong(),
            extras = buildExtras(mediaItem)
        )
    }
    
    private fun buildExtras(mediaItem: TelegramMediaItem): Bundle {
        return Bundle().apply {
            putLong("chatId", mediaItem.chatId)
            putLong("messageId", mediaItem.messageId)
            putInt("fileId", mediaItem.fileId ?: 0)
            putString("remoteId", mediaItem.remoteId)
            // Add series metadata if applicable
            if (mediaItem.isSeries) {
                putString("seriesName", mediaItem.seriesName)
                putInt("season", mediaItem.seasonNumber ?: 0)
                putInt("episode", mediaItem.episodeNumber ?: 0)
            }
        }
    }
}
```

---

## 3. Pipeline Feeding Metadata to Normalizer

**Task:** Integrate Telegram pipeline with the centralized metadata normalizer (EXTERNAL service).

**CRITICAL:** The normalizer and TMDB resolver are EXTERNAL services provided by `:core:metadata-normalizer`.
The Telegram pipeline NEVER implements normalization, TMDB lookups, or canonical identity logic.

**Type Ownership:**
- **Data types (`:core:model`):** RawMediaMetadata, NormalizedMediaMetadata, ExternalIds, SourceType
- **Behavior (`:core:metadata-normalizer`):** MediaMetadataNormalizer, TmdbMetadataResolver

**Use Case:** When a user browses Telegram media, the app should:
1. Load media items from `TelegramContentRepository`
2. Convert to `RawMediaMetadata` via `toRawMediaMetadata()` (raw data only)
3. Send to `MediaMetadataNormalizer.normalize()` (EXTERNAL service)
4. Optionally enrich via `TmdbMetadataResolver.enrich()` (EXTERNAL service)
5. Store in `CanonicalMediaRepository` (EXTERNAL service)
6. Enable cross-pipeline features (unified detail, resume)

**Integration Points:**

### 3.1 Feature Layer Integration

**Location:** `:feature:telegram-media` (to be created)

```kotlin
class TelegramMediaViewModel(
    private val contentRepository: TelegramContentRepository,
    // EXTERNAL services from :core:metadata-normalizer (behavior)
    private val metadataNormalizer: MediaMetadataNormalizer,
    private val tmdbResolver: TmdbMetadataResolver,
    private val canonicalMediaRepo: CanonicalMediaRepository
) : ViewModel() {
    
    suspend fun loadAndNormalizeMedia(chatId: Long) {
        // 1. Load raw media from Telegram pipeline
        val telegramItems = contentRepository.getMediaItemsByChat(chatId)
        
        // 2. Convert to raw metadata (NO cleaning, NO normalization here)
        val rawMetadata = telegramItems.map { it.toRawMediaMetadata() }
        
        // 3. Normalize each item using EXTERNAL normalizer
        val normalized = rawMetadata.map { raw ->
            val norm = metadataNormalizer.normalize(raw)  // EXTERNAL
            // Optionally enrich with TMDB using EXTERNAL resolver
            tmdbResolver.enrich(norm)  // EXTERNAL
        }
        
        // 4. Store canonical representations using EXTERNAL repository
        normalized.forEach { enriched ->
            val canonicalId = canonicalMediaRepo.upsertCanonicalMedia(enriched)  // EXTERNAL
            // Link Telegram source to canonical media
            val sourceRef = MediaSourceRef(
                sourceType = SourceType.TELEGRAM,
                sourceId = /* from raw metadata */,
                sourceLabel = /* from raw metadata */,
                // Add quality, language, subtitle info
            )
            canonicalMediaRepo.addOrUpdateSourceRef(canonicalId, sourceRef)  // EXTERNAL
        }
    }
}
```

**Responsibilities Breakdown:**
- **Telegram Pipeline:** Provides raw `TelegramMediaItem` and converts to `RawMediaMetadata`
- **Types (`:core:model`):** RawMediaMetadata, ExternalIds, SourceType definitions
- **MediaMetadataNormalizer (EXTERNAL - `:core:metadata-normalizer`):** Title cleaning, tag stripping, parsing
- **TmdbMetadataResolver (EXTERNAL - `:core:metadata-normalizer`):** TMDB lookups, external ID resolution
- **CanonicalMediaRepository (EXTERNAL):** Persistence, cross-pipeline linking

### 3.2 Background Sync Integration

**Task:** Sync Telegram messages in the background and automatically normalize/enrich them using EXTERNAL services.

**Worker:** `TelegramSyncWorker` (to be created)

```kotlin
class TelegramSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val contentRepository: TelegramContentRepository,
    // EXTERNAL services from :core:metadata-normalizer (behavior)
    private val metadataNormalizer: MediaMetadataNormalizer,
    private val canonicalMediaRepo: CanonicalMediaRepository
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            // 1. Refresh Telegram content (TDLib data access)
            contentRepository.refresh()
            
            // 2. Get new/updated items from Telegram
            val newItems = contentRepository.getRecentMediaItems(limit = 100)
            
            // 3. Convert to raw metadata (NO cleaning in pipeline)
            // 4. Normalize using EXTERNAL normalizer
            // 5. Store using EXTERNAL repository
            newItems.forEach { item ->
                val raw = item.toRawMediaMetadata()  // Pipeline: raw data only
                val normalized = metadataNormalizer.normalize(raw)  // EXTERNAL: cleaning/normalization
                canonicalMediaRepo.upsertCanonicalMedia(normalized)  // EXTERNAL: storage
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

**Separation of Concerns:**
- **TelegramSyncWorker:** Orchestrates the workflow
- **TelegramContentRepository:** Telegram data access (TDLib)
- **toRawMediaMetadata():** Raw field mapping (no cleaning)
- **Types (`:core:model`):** RawMediaMetadata, ExternalIds, SourceType
- **MediaMetadataNormalizer (EXTERNAL - `:core:metadata-normalizer`):** Title cleaning, tag stripping
- **CanonicalMediaRepository (EXTERNAL):** Persistence

### 3.3 Batching Strategy

For large Telegram libraries (thousands of media items):

**Approach 1: Lazy Normalization**
- Normalize on-demand when user views/plays an item
- Cache normalized results
- Fast initial load, slower first-time access

**Approach 2: Background Batch Normalization**
- Normalize in background worker (100 items at a time)
- Store results in canonical media database
- Fast access, slower initial sync

**Recommendation:** Use Approach 2 with priority queue:
1. High priority: Recently accessed, currently visible
2. Medium priority: Same series/season as recent access
3. Low priority: Older content

---

## 4. Real ObxTelegramMessage Integration

**Task:** Integrate with real ObjectBox persistence.

**Current State:**
- `ObxTelegramMessage` entity already exists in `:core:persistence`
- `TelegramMappers` already has bidirectional mapping
- Stub implementations don't use real ObjectBox

**Real Implementation:**

```kotlin
class TdLibTelegramContentRepository(
    private val boxStore: BoxStore // Injected via Hilt
) : TelegramContentRepository {
    
    private val messageBox: Box<ObxTelegramMessage> by lazy {
        boxStore.boxFor(ObxTelegramMessage::class.java)
    }
    
    override suspend fun getAllMediaItems(
        offset: Int,
        limit: Int
    ): List<TelegramMediaItem> = withContext(Dispatchers.IO) {
        messageBox
            .query()
            .orderDesc(ObxTelegramMessage_.date)
            .build()
            .find(offset.toLong(), limit.toLong())
            .map { TelegramMappers.fromObxTelegramMessage(it) }
    }
    
    suspend fun insertOrUpdateMessage(message: ObxTelegramMessage) {
        withContext(Dispatchers.IO) {
            messageBox.put(message)
        }
    }
    
    suspend fun deleteMessage(id: Long) {
        withContext(Dispatchers.IO) {
            messageBox.remove(id)
        }
    }
}
```

**Sync Flow:**
1. TDLib reports new/updated messages
2. Parse TDLib message → `ObxTelegramMessage`
3. Store in ObjectBox via `messageBox.put()`
4. Notify observers via Flow
5. UI refreshes automatically

---

## 5. Testing Strategy

### 5.1 Unit Tests

**TelegramMetadataNormalizationTest.kt:**
- Test `toRawMediaMetadata()` mapping
- Test title extraction priority
- Test that NO cleaning happens
- Test field mappings (year, season, episode, duration)
- Test sourceId generation

**TdLibTelegramContentRepositoryTest.kt:**
- Mock ObjectBox queries
- Test pagination
- Test filtering (by chat, by series)
- Test search
- Test refresh behavior

### 5.2 Integration Tests

**TelegramNormalizationIntegrationTest.kt:**
- Real TelegramMediaItem → RawMediaMetadata → NormalizedMediaMetadata flow
- Test with scene-style filenames (e.g., "X-Men.2000.1080p.BluRay.x264-GROUP")
- Test with series episodes (e.g., "The Mandalorian S01E05")
- Verify NO cleaning in Telegram pipeline (happens in normalizer)

### 5.3 End-to-End Tests

**TelegramPlaybackE2ETest.kt:**
- Load media from Telegram
- Normalize metadata
- Create PlaybackContext
- Start playback
- Verify resume tracking

---

## 6. Hilt/DI Integration

**Module:** `TelegramPipelineModule`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object TelegramPipelineModule {
    
    @Provides
    @Singleton
    fun provideTelegramServiceClient(
        // TDLib dependencies
    ): T_TelegramServiceClient {
        return T_TelegramServiceClient(/* ... */)
    }
    
    @Provides
    @Singleton
    fun provideTelegramContentRepository(
        boxStore: BoxStore,
        serviceClient: T_TelegramServiceClient
    ): TelegramContentRepository {
        return TdLibTelegramContentRepository(boxStore, serviceClient)
    }
    
    @Provides
    @Singleton
    fun provideTelegramPlaybackSourceFactory(
        serviceClient: T_TelegramServiceClient,
        streamingSettings: TelegramStreamingSettingsProvider
    ): TelegramPlaybackSourceFactory {
        return TdLibTelegramPlaybackSourceFactory(serviceClient, streamingSettings)
    }
}
```

---

## 7. Migration from v1 Telegram Implementation

**V1 Components to Port:**
- `T_TelegramServiceClient` → Port to `:pipeline:telegram` or `:infra:telegram`
- `TelegramFileDataSource` → Port to `:player:internal`
- `TelegramSyncWorker` → Port to `:feature:telegram-media`
- `TelegramLogRepository` → Port to `:infra:logging`

**Migration Strategy:**
1. Create new v2 modules (`:pipeline:telegram` already exists)
2. Copy v1 code as starting point
3. Refactor to v2 architecture (separate pipeline from player)
4. Update package names (`com.fishit.player` instead of `com.chris.m3usuite`)
5. Add v2 tests
6. Deprecate v1 code (keep as read-only reference)

---

## 8. Documentation to Create

1. **TELEGRAM_PIPELINE_GUIDE.md** - User-facing guide
   - How to connect Telegram account
   - How to browse Telegram media
   - How streaming works
   - Troubleshooting

2. **TELEGRAM_ARCHITECTURE.md** - Developer guide
   - TDLib integration details
   - Sync strategy
   - Streaming architecture
   - Resume tracking

3. **TELEGRAM_TESTING.md** - Testing guide
   - How to test without real Telegram account
   - Mock TDLib responses
   - Integration test setup

---

## 9. Success Criteria

Phase 3 prep is complete when:
- ✅ `toRawMediaMetadata()` implemented and tested
- ✅ Real `TdLibTelegramContentRepository` implemented
- ✅ Real `TdLibTelegramPlaybackSourceFactory` implemented
- ✅ ObjectBox integration working
- ✅ TDLib sync worker implemented
- ✅ Feature layer can load and normalize Telegram media
- ✅ Background sync stores canonical media representations
- ✅ Cross-pipeline resume works (Telegram → Xtream → IO)
- ✅ Unified detail screens show all versions across pipelines
- ✅ All tests passing (unit + integration + e2e)

---

## 10. Timeline Estimate

**Phase 3 Dependencies:** 2-3 weeks
- `:core:metadata-normalizer` module creation
- `MediaMetadataNormalizer` implementation
- `TmdbMetadataResolver` implementation

**Telegram Integration:** 2-3 weeks
- Implement `toRawMediaMetadata()` (1 day)
- Port TDLib components (1 week)
- Real repository implementations (3 days)
- Feature layer integration (3 days)
- Background sync worker (2 days)
- Testing (3 days)

**Total:** 4-6 weeks after Phase 3 core is complete

---

## Dependencies

**Required Modules:**
- `:core:model` (types: RawMediaMetadata, ExternalIds, SourceType)
- `:core:metadata-normalizer` (behavior: normalization, TMDB resolution)
- `:core:persistence` (exists)
- `:infra:logging` (exists)

**Optional but Recommended:**
- `:infra:telegram` (TDLib wrapper components)
- `:feature:telegram-media` (UI layer)

**External Libraries:**
- `org.drinkless:tdlib` (TDLib Java bindings)
- `com.uwetrottmann.tmdb2:tmdb-java` (TMDB API client, for Phase 3)

---

This document provides a complete roadmap for the next phase of Telegram pipeline development.
All work must continue to comply with the Media Normalization Contract.
