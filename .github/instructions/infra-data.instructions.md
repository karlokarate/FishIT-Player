---
applyTo: 
  - infra/data-telegram/**
  - infra/data-xtream/**
  - infra/data-home/**
  - infra/data-library/**
  - infra/data-live/**
  - infra/data-detail/**
---

# 🏆 PLATIN Instructions:   infra/data-*

> **PLATIN STANDARD** - Repository Implementation Layer.  
>
> **Purpose:** Implements repository interfaces for domain layer consumption.  Bridges between
> ObjectBox persistence and domain models.  This layer provides reactive Flows, query operations,
> and upsert logic for catalog data.
>
> **Key Principle:** Works ONLY with `RawMediaMetadata` (source-agnostic canonical model).
> NO pipeline DTOs, NO transport DTOs, NO business logic. 

---

## 🔴 ABSOLUTE HARD RULES

### 1. RawMediaMetadata ONLY - NO Pipeline/Transport DTOs

```kotlin
// ✅ CORRECT:   Repository interface uses RawMediaMetadata
interface XtreamCatalogRepository {
    fun observeVod(categoryId: String?  = null): Flow<List<RawMediaMetadata>>
    suspend fun upsertAll(items: List<RawMediaMetadata>)
}

interface TelegramContentRepository {
    fun observeByChat(chatId: Long): Flow<List<RawMediaMetadata>>
    suspend fun upsert(item: RawMediaMetadata)
}

// ❌ FORBIDDEN:  Exposing pipeline/transport DTOs
interface XtreamCatalogRepository {
    fun observeVod(): Flow<List<XtreamVodItem>>        // WRONG - pipeline DTO!  
    suspend fun upsertStreams(items: List<XtreamVodStream>)  // WRONG - transport DTO!
}

interface TelegramContentRepository {
    fun observeMessages(): Flow<List<TgMessage>>       // WRONG - transport DTO!
    suspend fun upsert(item: TelegramMediaItem)        // WRONG - pipeline DTO! 
}
```

### 2. ObjectBox Entities Are Internal - NEVER Exposed

```kotlin
// ✅ CORRECT:  ObjectBox entities are implementation details
@Singleton
class ObxXtreamCatalogRepository @Inject constructor(
    private val boxStore: BoxStore,
) : XtreamCatalogRepository {
    private val vodBox by lazy { boxStore.boxFor<ObxVod>() }  // private
    
    override fun observeVod(categoryId: String? ): Flow<List<RawMediaMetadata>> {
        val query = vodBox.query(/* ... */).build()
        return query.asFlow().map { entities -> 
            entities.map { it.toRawMediaMetadata() }  // Internal mapping
        }
    }
}

// ❌ FORBIDDEN:  Exposing ObjectBox entities
interface XtreamCatalogRepository {
    fun observeVodEntities(): Flow<List<ObxVod>>       // WRONG - internal entity!
    suspend fun getEntity(id: Long): ObxVod?           // WRONG - exposes entity!
}
```

### 3. No Business Logic or Normalization

```kotlin
// ❌ FORBIDDEN in Data Layer
fun normalizeTitle(title: String): String              // → core/metadata-normalizer
fun classifyMediaType(item: RawMediaMetadata): MediaType  // → pipeline
fun generateGlobalId(metadata: RawMediaMetadata): String  // → core/metadata-normalizer
fun extractSeasonEpisode(title: String): Pair<Int?, Int?>?   // → pipeline
suspend fun searchTmdb(title: String): TmdbRef?        // → core/metadata-normalizer

// ✅ CORRECT:   Pure persistence operations
suspend fun upsert(item: RawMediaMetadata)             // Store as-is
suspend fun getBySourceId(sourceId: String): RawMediaMetadata?  // Retrieve
fun observeAll(): Flow<List<RawMediaMetadata>>         // React to changes
```

### 4. No Transport or Pipeline Imports (With Adapter Exception)

```kotlin
// ❌ FORBIDDEN in repository implementations
import com.fishit.player.pipeline.telegram.*           // Pipeline
import com.fishit.player.pipeline.xtream.*             // Pipeline
import com.fishit.player.infra.transport.telegram.internal.*  // Transport internals
import com.fishit.player.infra.transport.xtream.internal.*    // Transport internals
import org.drinkless.td.TdApi.*                         // TDLib
import okhttp3.*                                        // HTTP client

// ✅ ALLOWED
import com.fishit.player.core.model.*                   // Core types
import com.fishit.player.core.persistence.*             // ObjectBox entities
import com.fishit.player.infra.logging.*                // UnifiedLog
import kotlinx.coroutines.*                             // Coroutines

// ✅ ALLOWED (ONLY in adapters, NOT repository implementations)
import com.fishit.player.infra.transport.telegram.api.TelegramAuthClient     // API interface
import com.fishit.player.infra.transport.telegram.api.TelegramHistoryClient  // API interface
import com.fishit.player.infra.transport.telegram.dto.*                      // Transport DTOs
```

**Adapter Exception (CRITICAL CLARIFICATION):**

Repository **adapters** (classes ending in `*Adapter` or `*RepositoryAdapter`) MAY import:
- ✅ Transport **API interfaces** (`TelegramAuthClient`, `TelegramHistoryClient`, `XtreamApiClient`)
- ✅ Transport **DTOs** (`TgMessage`, `TgContent`, `XtreamVodStream`)
- ❌ Transport **implementations** (`DefaultTelegramClient`, `DefaultXtreamApiClient`)
- ❌ Transport **internals** (any class in `/internal/` package)

**Purpose:** Adapters bridge transport APIs to feature domain interfaces (e.g., `TelegramAuthRepositoryAdapter` implements `TelegramAuthRepository` from `core/feature-api` by delegating to `TelegramAuthClient`).

**Example:**
```kotlin
// ✅ CORRECT: Adapter imports API interface, NOT implementation
@Singleton
class TelegramAuthRepositoryAdapter @Inject constructor(
    private val authClient: TelegramAuthClient,  // ✅ API interface from transport/api
) : TelegramAuthRepository {  // Domain interface from core/feature-api
    override fun observeAuthState() = authClient.observeAuthState()
}

// ❌ WRONG: Importing implementation or internals
import com.fishit.player.infra.transport.telegram.internal.DefaultTelegramClient  // WRONG!
```


### 5. No UI or Playback Dependencies

```kotlin
// ❌ FORBIDDEN
import com.fishit.player.feature.*                     // UI/Feature
import com.fishit.player.playback.*                    // Playback domain
import androidx.compose.*                               // Compose UI
import androidx.lifecycle.*                             // ViewModel (unless adapters)
```

---

## 📋 Module Responsibilities

### infra/data-telegram

**Purpose:** Persists Telegram media as `RawMediaMetadata`, provides reactive Flows. 

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| Store `RawMediaMetadata` to ObjectBox | ✅ | Storing `TgMessage` or `TelegramMediaItem` |
| Observe media by chat | ✅ | Fetching messages from TDLib |
| Search by title | ✅ | Title normalization |
| Upsert from pipeline | ✅ | Pipeline imports |
| Map `ObxTelegramMessage` ↔ `RawMediaMetadata` | ✅ | Exposing ObjectBox entities |

**Public Interface:**
- `TelegramContentRepository` - CRUD operations for Telegram media

**Internal Entities:**
- `ObxTelegramMessage` - ObjectBox entity (NEVER exported)

**Source ID Format:**
- `"msg:{chatId}:{messageId}"` - Stable identifier across sessions

**Adapters (ALLOWED):**
- Feature-level repository adapters (e.g., `TelegramAuthRepositoryAdapter`) that map transport APIs to domain interfaces
- **MUST NOT** touch transport internals (`DefaultTelegramClient`, `TdlibAuthSession`)
- **MAY** import transport API surface (`TelegramAuthClient`, `TelegramHistoryClient`)

---

### infra/data-xtream

**Purpose:** Persists Xtream catalog as `RawMediaMetadata`, provides reactive Flows.

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| Store VOD/Series/Live to ObjectBox | ✅ | Storing `XtreamVodItem` or `XtreamVodStream` |
| Observe by category | ✅ | Making HTTP calls to Xtream API |
| Search catalog | ✅ | Pipeline imports |
| Upsert from pipeline | ✅ | Transport imports |
| Episode index management | ✅ | TMDB enrichment (belongs in normalizer) |

**Public Interfaces:**
- `XtreamCatalogRepository` - VOD, Series, Episodes
- `XtreamLiveRepository` - Live TV channels
- `XtreamSeriesIndexRepository` - Season/episode index

**Internal Entities:**
- `ObxVod`, `ObxSeries`, `ObxEpisode`, `ObxLive` - ObjectBox entities (NEVER exported)
- `ObxSeasonIndex`, `ObxEpisodeIndex` - Series hierarchy

**Source ID Formats:**
- VOD: `"xtream:vod:{vodId}"`
- Series: `"xtream:series:{seriesId}"`
- Episode: `"xtream:episode:{seriesId}:{seasonNum}:{episodeNum}"`
- Live: `"xtream:live:{streamId}"`

---

### infra/data-home

**Purpose:** Aggregates content from multiple sources for Home screen.

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| Aggregate across sources | ✅ | Source-specific logic (use source repositories) |
| Continue watching | ✅ | Resume logic (belongs in playback domain) |
| Recently added | ✅ | Classification logic |

**Public Interface:**
- `HomeContentRepository` - Home screen content aggregation

---

### infra/data-library

**Purpose:** Library feature content adapters.

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| Map entities to domain models | ✅ | Direct ObjectBox access (use source repositories) |
| Category filtering | ✅ | Transport calls |

**Public Interface:**
- `LibraryContentRepository` - Library screen content

**Note:** May use direct entity mapping when `RawMediaMetadata` lacks required fields (categoryId, genres).

---

### infra/data-live

**Purpose:** Live TV content adapters.

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| Channel listing | ✅ | EPG data (separate system) |
| Category filtering | ✅ | Transport calls |

**Public Interface:**
- `LiveContentRepository` - Live TV screen content

---

### infra/data-detail

**Purpose:** Detail screen data adapters.

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| Series episode listing | ✅ | Episode index refresh (belongs in use case) |
| Related content | ✅ | TMDB calls |

**Public Interface:**
- `DetailContentRepository` - Detail screen content

---

## ⚠️ Critical Architecture Patterns

### ObjectBox Reactive Flows - Correct Pattern

```kotlin
// ✅ CORRECT:   Re-query on change trigger
fun observeAll(): Flow<List<RawMediaMetadata>> {
    val query = box.query().order(ObxTelegramMessage_. date, QueryBuilder. DESCENDING).build()
    
    return query.asFlow().map { entities -> 
        entities.map { it.toRawMediaMetadata() }  // Re-query result
    }
}

// ❌ WRONG:  Expecting data in observer
fun observeAll(): Flow<List<RawMediaMetadata>> {
    return callbackFlow {
        val observer = box.subscribe().observer { data ->  // 'data' is NOT the list!
            trySend(data)  // WRONG - data is a change trigger, not the result
        }
        // ... 
    }
}
```

**Why? ** ObjectBox `DataObserver` is a **change trigger only**. The actual data must be retrieved via `query. find()`.

---

### Source ID Parsing & Construction

```kotlin
// ✅ CORRECT:  Deterministic source ID format
// Telegram: 
fun TelegramRemoteId.toSourceId(): String = "msg: $chatId:$messageId"
fun parseSourceId(sourceId: String): Pair<Long, Long>? {
    val parts = sourceId.removePrefix("msg:").split(":")
    if (parts.size != 2) return null
    return Pair(parts[0].toLongOrNull() ?: return null, 
                parts[1].toLongOrNull() ?: return null)
}

// Xtream:
fun toSourceId(type: String, id: Int): String = "xtream:$type:$id"
// Examples:  "xtream:vod: 123", "xtream:live:456"
```

---

### Entity ↔ RawMediaMetadata Mapping

```kotlin
// ✅ CORRECT:  Bidirectional mapping extensions
// Entity → RawMediaMetadata
fun ObxVod.toRawMediaMetadata(): RawMediaMetadata = RawMediaMetadata(
    originalTitle = name,
    sourceType = SourceType.XTREAM,
    sourceId = "xtream:vod:$vodId",
    pipelineIdTag = PipelineIdTag. XTREAM,
    mediaType = MediaType. MOVIE,
    year = releaseDate?.substring(0, 4)?.toIntOrNull(),
    durationMs = durationSecs?.let { it * 1000L },
    poster = posterPath?.let { ImageRef.Http(it) },
    backdrop = backdropPath?.let { ImageRef.Http(it) },
    rating = rating?.toDoubleOrNull(),
    plot = plot,
    genres = genres,
    director = director,
    cast = cast,
    externalIds = ExternalIds(
        tmdb = tmdbId?. let { TmdbRef(TmdbMediaType.MOVIE, it) },
        imdbId = imdbId,
    ),
    playbackHints = mapOf(
        PlaybackHintKeys. XTREAM_STREAM_ID to vodId. toString(),
    ),
)

// RawMediaMetadata → Entity
fun RawMediaMetadata.toObxVod(): ObxVod {
    val vodId = sourceId.removePrefix("xtream:vod: ").toIntOrNull() 
        ?: throw IllegalArgumentException("Invalid VOD sourceId: $sourceId")
    
    return ObxVod(
        vodId = vodId. toLong(),
        name = originalTitle,
        nameLower = originalTitle.lowercase(),
        // ... map all fields
    )
}
```

**Key Rules:**
- `sourceId` is the stable identifier
- `durationMs` ALWAYS in milliseconds (entity may store seconds)
- `ImageRef` for all images (no raw URLs)
- `externalIds` for TMDB/IMDB references

---

### Upsert Logic with Transactions

```kotlin
// ✅ CORRECT:  Bulk upsert in single transaction
override suspend fun upsertAll(items: List<RawMediaMetadata>) = withContext(Dispatchers.IO) {
    if (items.isEmpty()) return@withContext
    
    boxStore.runInTx {
        items.forEach { metadata ->
            when (metadata.mediaType) {
                MediaType.MOVIE -> upsertVod(metadata)
                MediaType. SERIES -> upsertSeries(metadata)
                MediaType.SERIES_EPISODE -> upsertEpisode(metadata)
                MediaType. LIVE -> upsertLiveChannel(metadata)
                else -> UnifiedLog.w(TAG) { "Unsupported media type: ${metadata.mediaType}" }
            }
        }
    }
    
    UnifiedLog.i(TAG) { "Upserted ${items.size} items" }
}

private fun upsertVod(metadata:  RawMediaMetadata) {
    val vodId = metadata.sourceId.removePrefix("xtream:vod:").toLongOrNull() ?: return
    
    val existing = vodBox.query(ObxVod_. vodId.equal(vodId)).build().findFirst()
    val entity = existing ?: ObxVod()
    
    // Update fields
    entity.vodId = vodId
    entity.name = metadata.originalTitle
    entity.nameLower = metadata.originalTitle. lowercase()
    // ... update all fields
    
    vodBox.put(entity)
}
```

**Why `runInTx`?** Single transaction for bulk operations improves performance and ensures atomicity.

---

### Repository Adapters for Feature Domain

**Pattern:** Feature layer defines domain interfaces, data layer provides adapters.

```kotlin
// ✅ CORRECT:  Adapter in data layer
// core/library-domain/LibraryContentRepository.kt (interface)
interface LibraryContentRepository {
    fun observeVod(categoryId: String? ): Flow<List<LibraryMediaItem>>
    fun observeSeries(categoryId: String?): Flow<List<LibraryMediaItem>>
}

// infra/data-xtream/LibraryContentRepositoryAdapter.kt (implementation)
@Singleton
class LibraryContentRepositoryAdapter @Inject constructor(
    private val boxStore: BoxStore,
) : LibraryContentRepository {
    private val vodBox by lazy { boxStore.boxFor<ObxVod>() }
    
    override fun observeVod(categoryId: String?): Flow<List<LibraryMediaItem>> {
        val query = if (categoryId != null) {
            vodBox. query(ObxVod_.categoryId.equal(categoryId)).build()
        } else {
            vodBox.query().order(ObxVod_.nameLower).build()
        }
        
        return query.asFlow().map { entities -> 
            entities. map { it.toLibraryMediaItem() }
        }
    }
    
    private fun ObxVod.toLibraryMediaItem(): LibraryMediaItem = LibraryMediaItem(
        id = vodId,
        title = name,
        poster = posterPath?.let { ImageRef.Http(it) },
        categoryId = categoryId,
        categoryName = getCategoryName(categoryId),  // Cached lookup
        // ... map to domain model
    )
}
```

**Why Direct Entity Mapping?** `RawMediaMetadata` is source-agnostic and lacks provider-specific fields like `categoryId`, `genres`, `plot`. Adapters map directly from ObjectBox entities to feature domain models.

---

## 📐 Architecture Position

```
Pipeline (produces RawMediaMetadata)
              ↓
    infra/data-* ← YOU ARE HERE
    (stores RawMediaMetadata, provides Flows)
              ↓
        core/*-domain
    (use cases consume repositories)
              ↓
         feature/*
    (ViewModels observe Flows)
```

---

## 🔍 Layer Boundary Enforcement

### Upstream Dependencies (ALLOWED)

```kotlin
import com.fishit.player.core.model.*                   // Core types
import com.fishit.player.core.persistence.*             // ObjectBox entities
import com.fishit.player.infra.logging.*                // UnifiedLog
import kotlinx.coroutines.*                             // Coroutines
import io.objectbox.*                                   // ObjectBox
```

### Downstream Consumers (Domain/UI)

```kotlin
// Use cases consume repositories
private val telegramRepo: TelegramContentRepository
private val xtreamRepo: XtreamCatalogRepository

val allMedia = combine(
    telegramRepo.observeAll(),
    xtreamRepo.observeVod(),
) { telegram, xtream -> telegram + xtream }
```

### Forbidden Imports (CI-GUARDED)

```kotlin
// ❌ FORBIDDEN
import com.fishit.player.pipeline.*                    // Pipeline
import com.fishit.player.infra.transport.telegram.*    // Transport (except typed interfaces for adapters)
import com.fishit.player.infra.transport.xtream.*      // Transport
import org.drinkless.td.TdApi.*                         // TDLib
import okhttp3.*                                        // HTTP
```

---

## 🔍 Pre-Change Verification

```bash
# 1. No forbidden imports
grep -rn "import.*pipeline\|import.*infra\.transport\.telegram\.internal\|import.*infra\.transport\.xtream\.internal" infra/data-telegram/ infra/data-xtream/

# 2. No ObjectBox entity exports
grep -rn "fun.*Obx.*\|suspend fun.*Obx.*\|Flow<.*Obx.*>" infra/data-telegram/src/main/java/*/TelegramContentRepository.kt infra/data-xtream/src/main/java/*/XtreamCatalogRepository.kt

# 3. No pipeline DTO usage
grep -rn "TelegramMediaItem\|XtreamVodItem\|XtreamSeriesItem\|TgMessage\|XtreamVodStream" infra/data-telegram/ infra/data-xtream/

# 4. No business logic (normalization, classification)
grep -rn "normalizeTitle\|classifyMediaType\|generateGlobalId\|searchTmdb" infra/data-telegram/ infra/data-xtream/

# All should return empty!  
```

---

## ✅ PLATIN Checklist

### Common (All Data Modules)
- [ ] Works ONLY with `RawMediaMetadata` (no pipeline/transport DTOs)
- [ ] ObjectBox entities are internal (never exposed in interfaces)
- [ ] No business logic or normalization
- [ ] No pipeline imports
- [ ] No transport imports (except typed interfaces for adapters)
- [ ] No UI imports
- [ ] No playback domain imports
- [ ] Uses UnifiedLog for all logging
- [ ] Reactive Flows use `ObjectBoxFlow. asFlow()` with re-query pattern
- [ ] Bulk operations use `boxStore.runInTx` for transactions

### Data-Telegram Specific
- [ ] Source ID format: `"msg:{chatId}:{messageId}"`
- [ ] `ObxTelegramMessage` entity is internal
- [ ] Bidirectional mapping:  `ObxTelegramMessage` ↔ `RawMediaMetadata`
- [ ] No TDLib imports (except `TelegramAuthClient` for adapters)
- [ ] Adapters may import transport API surface (not internals)

### Data-Xtream Specific
- [ ] Source ID formats: `"xtream:vod:{id}"`, `"xtream:series:{id}"`, `"xtream:episode:{seriesId}:{s}:{e}"`, `"xtream:live:{id}"`
- [ ] `ObxVod`, `ObxSeries`, `ObxEpisode`, `ObxLive` entities are internal
- [ ] Episode index uses `ObxSeasonIndex`, `ObxEpisodeIndex`
- [ ] No HTTP calls to Xtream API
- [ ] Category lookup cached for performance

### Repository Adapters
- [ ] Adapters implement feature domain interfaces
- [ ] Direct entity mapping when `RawMediaMetadata` lacks fields
- [ ] Category name lookups are cached
- [ ] Adapters are `@Singleton` with DI

---

## 📚 Reference Documents (Priority Order)

1. **`/docs/v2/MEDIA_NORMALIZATION_CONTRACT.md`** - RawMediaMetadata contract (AUTHORITATIVE)
2. **`/docs/v2/OBJECTBOX_REACTIVE_PATTERNS.md`** - Flow patterns
3. **`/contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`** - Telegram source ID format
4. **`/AGENTS.md`** - Section 4.5 (Layer Boundary Enforcement)
5. **`/contracts/GLOSSARY_v2_naming_and_modules.md`** - Data layer definition
6. **`/contracts/LOGGING_CONTRACT_V2.md`** - Logging rules
7. **`infra/data-telegram/README.md`** - Module-specific rules
8. **`infra/data-xtream/README.md`** - Module-specific rules

---

## 🚨 Common Violations & Solutions

### Violation 1: Exposing Pipeline DTOs

```kotlin
// ❌ WRONG
interface TelegramContentRepository {
    fun observeMedia(): Flow<List<TelegramMediaItem>>  // Pipeline DTO! 
}

// ✅ CORRECT
interface TelegramContentRepository {
    fun observeAll(): Flow<List<RawMediaMetadata>>
}
```

### Violation 2: Business Logic in Repository

```kotlin
// ❌ WRONG (in data layer)
suspend fun upsert(item: RawMediaMetadata) {
    val normalized = normalizeTitle(item.originalTitle)  // WRONG - belongs in normalizer
    val enriched = tmdbClient.search(normalized)          // WRONG - belongs in normalizer
    // ... 
}

// ✅ CORRECT
suspend fun upsert(item:  RawMediaMetadata) {
    val entity = item.toObxTelegramMessage()
    box.put(entity)
}
```

### Violation 3: Exposing ObjectBox Entities

```kotlin
// ❌ WRONG
interface XtreamCatalogRepository {
    fun getVodEntity(id: Long): ObxVod?  // Exposes internal entity! 
}

// ✅ CORRECT
interface XtreamCatalogRepository {
    suspend fun getBySourceId(sourceId: String): RawMediaMetadata?
}
```

### Violation 4: Incorrect ObjectBox Flow Pattern

```kotlin
// ❌ WRONG
fun observeAll(): Flow<List<RawMediaMetadata>> = callbackFlow {
    val observer = box.subscribe().observer { data ->
        trySend(data)  // 'data' is NOT the list!
    }
    // ...
}

// ✅ CORRECT
fun observeAll(): Flow<List<RawMediaMetadata>> {
    val query = box.query().build()
    return query.asFlow().map { entities -> 
        entities.map { it.toRawMediaMetadata() }  // Re-query on change
    }
}
```

---

**End of PLATIN Instructions for infra/data-***
