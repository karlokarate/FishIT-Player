# Phase 2 Task 3 (P2-T3) NEXT STEPS - Telegram Pipeline

**Agent:** telegram-agent  
**Created:** 2025-12-06  
**Status:** PENDING  
**Dependencies:** Phase 3 (Metadata Normalization Core)

## Overview

This document outlines the future work needed to complete the Telegram pipeline integration after Phase 3 (Metadata Normalization Core) is implemented.

**Prerequisites:**
1. `:core:metadata-normalizer` module created
2. `RawMediaMetadata` and `NormalizedMediaMetadata` data classes defined
3. `MediaMetadataNormalizer` interface and implementation
4. `TmdbMetadataResolver` interface and implementation
5. `CanonicalMediaRepository` for persistence

---

## 1. Implement Real toRawMediaMetadata()

**Task:** Convert the structure-only implementation in `TelegramMetadataNormalization.kt` into a working extension function.

**Location:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/mapper/TelegramMetadataNormalization.kt`

**Implementation Steps:**

1. **Add dependency on :core:metadata-normalizer:**
   ```kotlin
   // pipeline/telegram/build.gradle.kts
   dependencies {
       implementation(project(":core:metadata-normalizer"))
   }
   ```

2. **Implement the extension function:**
   ```kotlin
   fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata {
       return RawMediaMetadata(
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
       // NO cleaning of technical tags - pass raw source data
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

**Task:** Integrate Telegram pipeline with the centralized metadata normalizer.

**Use Case:** When a user browses Telegram media, the app should:
1. Load media items from `TelegramContentRepository`
2. Convert to `RawMediaMetadata` via `toRawMediaMetadata()`
3. Send to `MediaMetadataNormalizer.normalize()`
4. Optionally enrich via `TmdbMetadataResolver.enrich()`
5. Store in `CanonicalMediaRepository`
6. Enable cross-pipeline features (unified detail, resume)

**Integration Points:**

### 3.1 Feature Layer Integration

**Location:** `:feature:telegram-media` (to be created)

```kotlin
class TelegramMediaViewModel(
    private val contentRepository: TelegramContentRepository,
    private val metadataNormalizer: MediaMetadataNormalizer,
    private val tmdbResolver: TmdbMetadataResolver,
    private val canonicalMediaRepo: CanonicalMediaRepository
) : ViewModel() {
    
    suspend fun loadAndNormalizeMedia(chatId: Long) {
        // 1. Load raw media from Telegram pipeline
        val telegramItems = contentRepository.getMediaItemsByChat(chatId)
        
        // 2. Convert to raw metadata
        val rawMetadata = telegramItems.map { it.toRawMediaMetadata() }
        
        // 3. Normalize each item
        val normalized = rawMetadata.map { raw ->
            val norm = metadataNormalizer.normalize(raw)
            // Optionally enrich with TMDB
            tmdbResolver.enrich(norm)
        }
        
        // 4. Store canonical representations
        normalized.forEach { enriched ->
            val canonicalId = canonicalMediaRepo.upsertCanonicalMedia(enriched)
            // Link Telegram source to canonical media
            val sourceRef = MediaSourceRef(
                sourceType = SourceType.TELEGRAM,
                sourceId = /* from raw metadata */,
                sourceLabel = /* from raw metadata */,
                // Add quality, language, subtitle info
            )
            canonicalMediaRepo.addOrUpdateSourceRef(canonicalId, sourceRef)
        }
    }
}
```

### 3.2 Background Sync Integration

**Task:** Sync Telegram messages in the background and automatically normalize/enrich them.

**Worker:** `TelegramSyncWorker` (to be created)

```kotlin
class TelegramSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val contentRepository: TelegramContentRepository,
    private val metadataNormalizer: MediaMetadataNormalizer,
    private val canonicalMediaRepo: CanonicalMediaRepository
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            // 1. Refresh Telegram content
            contentRepository.refresh()
            
            // 2. Get new/updated items
            val newItems = contentRepository.getRecentMediaItems(limit = 100)
            
            // 3. Normalize and store
            newItems.forEach { item ->
                val raw = item.toRawMediaMetadata()
                val normalized = metadataNormalizer.normalize(raw)
                canonicalMediaRepo.upsertCanonicalMedia(normalized)
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

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
- `:core:metadata-normalizer` (Phase 3)
- `:core:model` (exists)
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
