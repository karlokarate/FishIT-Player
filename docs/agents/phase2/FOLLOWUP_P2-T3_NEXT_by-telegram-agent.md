# Phase 2 Task 3 (P2-T3) NEXT STEPS - Telegram Pipeline

**Agent:** telegram-agent  
**Created:** 2025-12-06  
**Updated:** 2025-12-06 (Export analysis completed)  
**Status:** PENDING  
**Dependencies:** Phase 3 (Metadata Normalization Core)

## Overview

This document outlines the future work needed to complete the Telegram pipeline integration after Phase 3 (Metadata Normalization Core) is implemented.

**Phase 2 Work Completed:**
1. ✅ Initial STUB implementation (41 tests)
2. ✅ Real Telegram export analysis (398 JSON files)
3. ✅ DTO enhancements based on real data (52 tests total)
4. ✅ Contract compliance verification (MEDIA_NORMALIZATION_CONTRACT.md)

**Export Analysis Findings Summary:**
- 46 video messages with scene-style filenames
- 16 document messages (RARs, ZIPs with episode info)
- 43 photo messages with multiple size variants
- 87 text metadata messages with rich fields (year, FSK, TMDB URL, genres)
- All DTOs now reflect actual Telegram export structures

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

**Task:** Convert the structure-only documentation in `TelegramRawMetadataContract.kt` into a working extension function, incorporating findings from real export analysis.

**CRITICAL:** This implementation provides RAW data ONLY. NO cleaning, NO normalization, NO TMDB lookups.

**Export Analysis Insight:** Filenames like `Das.Ende.der.Welt.2012.1080p.BluRay.x264-AWESOME.mkv` must be preserved exactly.

**Location:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/mapper/TelegramRawMetadataExtensions.kt` (new file)

**Implementation Steps:**

1. **Add dependencies:**
   ```kotlin
   // pipeline/telegram/build.gradle.kts
   dependencies {
       implementation(project(":core:model"))  // For RawMediaMetadata types
       // NO dependency on :core:metadata-normalizer (separation of concerns)
   }
   ```

2. **Implement the extension function:**
   ```kotlin
   import com.fishit.player.core.model.RawMediaMetadata
   import com.fishit.player.core.model.ExternalIds
   import com.fishit.player.core.model.SourceType
   
   /**
    * Converts TelegramMediaItem to RawMediaMetadata for central normalization.
    *
    * Based on analysis of 398 real Telegram export JSONs.
    * Provides RAW data ONLY - NO cleaning, normalization, or TMDB lookups.
    *
    * CONTRACT COMPLIANCE:
    * - Scene-style filenames preserved exactly (e.g., "Movie.2020.1080p.BluRay.x264-GROUP.mkv")
    * - Archive filenames with episode info preserved (e.g., "Series - Episode 422-427.rar")
    * - Captions and titles passed through as-is
    * - All normalization delegated to :core:metadata-normalizer
    */
   fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata {
       return RawMediaMetadata(
           // Simple field priority - NO cleaning, NO tag stripping
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
       // CRITICAL: NO cleaning - pass raw source data AS-IS
       // Real examples that stay unchanged:
       //   "Das.Ende.der.Welt.2012.1080p.BluRay.x264-AWESOME.mkv" -> AS-IS
       //   "Die Schlümpfe - Staffel 9 - Episode 422-427.rar" -> AS-IS
       //   "Das Ende der Welt - Die 12 Prophezeiungen der Maya - 2012" -> AS-IS
       return when {
           title.isNotBlank() -> title
           episodeTitle?.isNotBlank() == true -> episodeTitle!!
           caption?.isNotBlank() == true -> caption!!
           fileName?.isNotBlank() == true -> fileName!!
           else -> "Untitled Media $messageId"
       }
   }
   
   private fun TelegramMediaItem.buildTelegramSourceLabel(): String {
       // Example: "Telegram Chat: 🎬 Filme von 2011 bis 2019 🎥"
       // Will be enriched with actual chat names when real TDLib integration exists
       return "Telegram Chat: $chatId"
   }
   ```

3. **Add unit tests (based on real export patterns):**
   ```kotlin
   class TelegramRawMetadataExtensionsTest {
       @Test
       fun `preserves scene-style filename exactly`() {
           val item = TelegramMediaItem(
               fileName = "Das.Ende.der.Welt.2012.1080p.BluRay.x264-AWESOME.mkv",
               // ... other fields
           )
           val raw = item.toRawMediaMetadata()
           assertEquals("Das.Ende.der.Welt.2012.1080p.BluRay.x264-AWESOME.mkv", raw.originalTitle)
       }
       
       @Test
       fun `preserves RAR filename with episode info`() {
           val item = TelegramMediaItem(
               fileName = "Die Schlümpfe - Staffel 9 - Episode 422-427.rar",
           )
           val raw = item.toRawMediaMetadata()
           assertEquals("Die Schlümpfe - Staffel 9 - Episode 422-427.rar", raw.originalTitle)
       }
       
       @Test
       fun `uses caption when available before fileName`() {
           val item = TelegramMediaItem(
               caption = "Das Ende der Welt - Die 12 Prophezeiungen der Maya - 2012",
               fileName = "Das Ende der Welt - Die 12 Prophezeiungen der Maya - 2012.mp4",
           )
           val raw = item.toRawMediaMetadata()
           // Caption takes priority
           assertEquals("Das Ende der Welt - Die 12 Prophezeiungen der Maya - 2012", raw.originalTitle)
       }
       
       // ... more tests for title priority, year, season, episode, duration, sourceId
   }
   ```

**Contract Requirements:**
- ✅ NO title cleaning (preserve resolution tags, codec info, release groups)
- ✅ NO normalization or heuristics
- ✅ NO TMDB lookups
- ✅ Simple field priority selector
- ✅ All fields passed as-is from source

---

## 2. Handle TelegramMetadataMessage Integration

**Task:** Implement repository methods and mapping for text-only metadata messages.

**Background:** Export analysis revealed 87 text metadata messages with rich structured data (year, FSK, TMDB URL, genres).

**New DTO:** `TelegramMetadataMessage` (already created in Phase 2)

### 2.1 Repository Interface Extension

Add to `TelegramContentRepository`:
```kotlin
interface TelegramContentRepository {
    // Existing methods...
    
    /**
     * Get metadata message associated with a media item.
     * Many Telegram chats pair media messages with preceding text metadata messages.
     * 
     * @param chatId Chat ID containing the media
     * @param messageId Message ID of the media item
     * @return Metadata message if found, null otherwise
     */
    suspend fun getMetadataForMedia(chatId: Long, messageId: Long): TelegramMetadataMessage?
    
    /**
     * Get all metadata messages in a chat.
     * Useful for browsing available content before downloading.
     * 
     * @param chatId Chat ID to query
     * @return List of all metadata messages in the chat
     */
    suspend fun getMetadataMessages(chatId: Long): List<TelegramMetadataMessage>
}
```

### 2.2 Mapping Logic

The metadata messages complement media messages but are NOT converted to `RawMediaMetadata` directly (they have no playable content).

**Pipeline Repository Responsibility:**
The pipeline repository simply provides access to both media items and metadata messages:
```kotlin
// Pipeline repository methods:
suspend fun getMediaItem(chatId: Long, messageId: Long): TelegramMediaItem?
suspend fun getMetadataForMedia(chatId: Long, messageId: Long): TelegramMetadataMessage?
```

**Domain/Unifier-Level Logic (NOT Pipeline Responsibility):**
Combining media and metadata into enriched `RawMediaMetadata` happens in the domain/unifier layer, NOT in `:pipeline:telegram`:
```kotlin
// This combination logic belongs in domain services or unifier layer,
// NOT in the Telegram pipeline repository:
val mediaItem = telegramRepository.getMediaItem(chatId, messageId)
val metadata = telegramRepository.getMetadataForMedia(chatId, messageId)

// Domain layer combines the raw sources:
val raw = mediaItem.toRawMediaMetadata().let { raw ->
    metadata?.let { meta ->
        raw.copy(
            year = meta.year ?: raw.year,
            // Note: DO NOT parse tmdbUrl to ID here (contract violation)
            // The normalizer will handle TMDB URL extraction
        )
    } ?: raw
}
```

**CRITICAL:** TelegramMetadataMessage fields are RAW:
- `tmdbUrl` stays as string (NOT parsed to ID)
- `genres` stays as list of raw strings
- All normalization delegated to `:core:metadata-normalizer`

### 2.3 ObxTelegramMessage Extension

Since metadata messages don't have playable content, they may need a separate entity or flags:

**Option A:** Add flag to existing entity:
```kotlin
// In ObxTelegramMessage:
var isMetadataOnly: Boolean = false
var metadataTitle: String? = null
var metadataOriginalTitle: String? = null
// ... other metadata fields
```

**Option B:** Create separate entity (cleaner separation):
```kotlin
@Entity
data class ObxTelegramMetadata(
    @Id var id: Long = 0,
    var chatId: Long = 0,
    var messageId: Long = 0,
    var linkedMediaMessageId: Long? = null, // Link to media message if paired
    var title: String? = null,
    var originalTitle: String? = null,
    var year: Int? = null,
    var lengthMinutes: Int? = null,
    var fsk: Int? = null,
    // ... all metadata fields from TelegramMetadataMessage
)
```

**Recommendation:** Option B (separate entity) for cleaner separation and easier querying.

---

## 3. Real TDLib Integration

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
