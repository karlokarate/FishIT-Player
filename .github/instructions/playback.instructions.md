---
applyTo: 
  - playback/domain/**
  - playback/telegram/**
  - playback/xtream/**
  - playback/local/**
  - playback/audiobook/**
---

# 🏆 PLATIN Instructions:  playback/*

**Version:** 1.0  
**Last Updated:** 2026-02-04  
**Status:** Active

> **PLATIN STANDARD** - Playback Source Resolution & Factory Pattern. 
>
> **Purpose:** Transform `PlaybackContext` into playable `PlaybackSource` via source-specific factories.
> This layer provides the bridge between player (source-agnostic) and transport (source-specific).
>
> **Critical Principle:** Player NEVER knows about Telegram/Xtream.  All source logic lives here. 

---

## 🔴 ABSOLUTE HARD RULES

### 1. PlaybackSourceFactory Pattern (MANDATORY)

```kotlin
// ✅ CORRECT: Every source implements PlaybackSourceFactory
@Singleton
class TelegramPlaybackSourceFactoryImpl @Inject constructor(
    private val remoteResolver: TelegramRemoteResolver,
    private val fileClient: TelegramFileClient,
) : PlaybackSourceFactory {
    
    override fun supports(sourceType: SourceType): Boolean =
        sourceType == SourceType.TELEGRAM
    
    override suspend fun createSource(context: PlaybackContext): PlaybackSource {
        // Build tg: // URI, return PlaybackSource
    }
}

// ❌ WRONG:  Direct player dependency
class TelegramPlayback @Inject constructor(
    private val player: ExoPlayer,  // WRONG - player internals! 
) {
    fun play(context: PlaybackContext) {
        player.setMediaItem(...)  // WRONG - player logic!
    }
}
```

**Hard Rule:** ALL sources use `@Binds @IntoSet` for DI registration.

---

### 2. Hilt DI Registration (MANDATORY)

```kotlin
// ✅ CORRECT: @Multibinds in playback/domain
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackDomainModule {
    @Multibinds
    abstract fun bindPlaybackSourceFactories(): Set<PlaybackSourceFactory>
}

// ✅ CORRECT: @IntoSet in playback/telegram
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramPlaybackModule {
    @Binds @IntoSet
    abstract fun bindFactory(impl: TelegramPlaybackSourceFactoryImpl): PlaybackSourceFactory
}

// ❌ WRONG: Missing @IntoSet
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramPlaybackModule {
    @Binds  // WRONG - not in set! 
    abstract fun bindFactory(impl: TelegramPlaybackSourceFactoryImpl): PlaybackSourceFactory
}
```

---

### 3. Layer Boundary Enforcement

```kotlin
// ✅ ALLOWED imports
import com.fishit.player.core.playermodel.*                  // PlaybackContext
import com.fishit.player.playback.domain.*                   // Interfaces
import com.fishit.player.infra.transport.telegram.*          // Transport interfaces
import androidx.media3.datasource.*                          // DataSource
import androidx.media3.exoplayer.source.*                    // MediaSource

// ❌ FORBIDDEN imports
import com.fishit.player.pipeline.*                          // Pipeline
import com.fishit.player.infra.data.*                        // Data layer
import com.fishit.player.player.internal.*                   // Player internals
import org.drinkless.td.TdApi.*                              // Direct TDLib
```

**OkHttp Exception (playback/xtream ONLY):**

`playback/xtream` MAY use OkHttp **ONLY via the shared `@XtreamHttpClient` OkHttpClient** from `infra/transport-xtream`:

```kotlin
// ✅ CORRECT: Inject shared @XtreamHttpClient (playback/xtream)
@Inject constructor(
    @XtreamHttpClient private val xtreamOkHttpClient: OkHttpClient
) {
    // Override followSslRedirects for CDN compatibility
    private val playbackClient by lazy {
        xtreamOkHttpClient.newBuilder().followSslRedirects(true).build()
    }
}

// ❌ FORBIDDEN: Creating separate OkHttpClient instances
// ❌ FORBIDDEN: Direct OkHttp import in other playback modules
// playback/telegram MUST NOT import okhttp3.*
// playback/local MUST NOT import okhttp3.*
```

**HTTP Architecture:**
- Uses shared `@XtreamHttpClient` from `infra/transport-xtream` (derived from `@PlatformHttpClient` in `infra/networking`)
- Chucker gating is centralized in `infra/networking` debug/release source sets
- No separate OkHttp wrapper needed — `DefaultXtreamDataSourceFactoryProvider` injects `@XtreamHttpClient` directly


---

### 4. No Business Logic

```kotlin
// ❌ FORBIDDEN
fun normalizeTitle(title: String): String              // → core/metadata-normalizer
fun classifyMediaType(mime: String): MediaType         // → pipeline
fun generateGlobalId(... ): String                      // → core/metadata-normalizer
suspend fun searchTmdb(title: String): TmdbRef?        // → core/metadata-normalizer

// ✅ CORRECT
suspend fun createSource(context: PlaybackContext): PlaybackSource  // Pure mapping
fun buildMediaItem(context: PlaybackContext): MediaItem             // Pure construction
fun createDataSource(uri: Uri): DataSource                          // Pure transport
```

---

## 📋 Module Responsibilities

### playback/domain

**Purpose:** Contracts & default implementations for playback orchestration. 

| Component | Responsibility | Forbidden |
|-----------|----------------|-----------|
| `PlaybackSourceFactory` | Interface for source resolution | Implementation details |
| `PlaybackSource` | Resolved descriptor (URI, DataSourceType, MIME) | Source-specific code |
| `DataSourceType` | Enum for factory selection | Transport calls |
| `ResumeManager` | Resume position persistence | Direct DB access |
| `KidsPlaybackGate` | Screen time enforcement | UI logic |
| `PlayerEntryPoint` | Feature→Player abstraction | Player internals |

**Public Surface:**
- `PlaybackSourceFactory` interface
- `PlaybackSource` data class
- `DataSourceType` enum
- `PlaybackSourceException`
- `ResumeManager` interface
- `KidsPlaybackGate` interface
- `PlayerEntryPoint` interface

**Default Implementations:**
- `ObxResumeManager` - Uses CanonicalMediaRepository for persistence
- `DefaultKidsPlaybackGate` - Stub (Phase 9 will add real logic)

**Dependencies:**
- `core/model` - Core types
- `core/player-model` - PlaybackContext
- `core/persistence` - Repository interfaces
- `infra/logging` - UnifiedLog

---

### playback/telegram

**Purpose:** Telegram-specific playback source factory. 

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| Build `tg://` URIs | ✅ | Pipeline imports |
| Create TelegramFileDataSource | ✅ | Direct TDLib calls |
| RemoteId-first resolution | ✅ | Normalization logic |
| MP4 moov validation | ✅ | Repository access |

**Public Surface:**
- `TelegramPlaybackSourceFactoryImpl` - Factory implementation
- `TelegramFileDataSource` - Media3 DataSource for `tg://` URIs
- `TelegramFileDataSourceFactory` - Factory for ExoPlayer
- `TelegramPlaybackMode` - PROGRESSIVE_FILE vs FULL_FILE
- `TelegramPlaybackModeDetector` - MIME-based mode selection
- `TelegramFileReadyEnsurer` - Streaming readiness validation

**Architecture:**
```
PlaybackContext (player) 
    ↓
TelegramPlaybackSourceFactoryImpl (parses context)
    ↓
TelegramRemoteResolver (transport - resolves remoteId→fileId)
    ↓
PlaybackSource (tg:// URI + DataSourceType.TELEGRAM_FILE)
    ↓
TelegramFileDataSource (Media3 - handles streaming)
    ↓
TelegramFileClient (transport - download primitives)
    ↓
FileDataSource (Media3 - actual I/O)
```

**URI Format Contract:**
```
tg://file/<fileId>? chatId=<chatId>&messageId=<messageId>&remoteId=<remoteId>&mimeType=<mimeType>
```

**Resolution Priority:**
1. **chatId + messageId** (PREFERRED - always fresh, via TelegramRemoteResolver)
2. **fileId** (Fast path - same session, may be stale)
3. **remoteId** (Fallback - cross-session stable)

**Platinum Playback Rules:**
- ✅ ALL video formats playable (MP4, MKV, WebM, AVI)
- ✅ "moov atom not found" is NEVER fatal (fallback to FULL_FILE)
- ✅ Zero-copy streaming via TDLib cache (no ByteArray buffers)
- ✅ Progressive download with `isStreamable=true`

**Dependencies:**
- `playback/domain` - Interfaces
- `infra/transport-telegram` - TelegramRemoteResolver, TelegramFileClient
- `androidx.media3:media3-datasource` - DataSource base classes

---

### playback/xtream

**Purpose:** Xtream-specific playback source factory.

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| Build authenticated URLs | ✅ | Pipeline imports |
| HLS/TS format selection | ✅ | Direct HTTP calls |
| Per-request headers | ✅ | Repository access |

**Public Surface:**
- `XtreamPlaybackSourceFactoryImpl` - Factory implementation
- `XtreamDataSourceFactoryProvider` - Provides DataSource.Factory with per-request headers
- `DefaultXtreamDataSourceFactoryProvider` - Uses shared `@XtreamHttpClient` OkHttpClient
- `HlsCapabilityDetector` - Runtime detection of HLS module availability

**Architecture:**
```
PlaybackContext (player)
    ↓
XtreamPlaybackSourceFactoryImpl (extracts VOD_ID, STREAM_ID, etc.)
    ↓
XtreamUrlBuilder (transport - builds authenticated URL)
    ↓
PlaybackSource (HTTP URL + DataSourceType.XTREAM_HTTP)
    ↓
OkHttpDataSource.Factory (via @XtreamHttpClient, followSslRedirects=true)
```

**Playback Hints Contract:**
```kotlin
// From RawMediaMetadata. playbackHints
context.extras = mapOf(
    PlaybackHintKeys. Xtream. CONTENT_TYPE to "vod",  // live | vod | series
    PlaybackHintKeys. Xtream.VOD_ID to vodId. toString(),
    PlaybackHintKeys.Xtream. CONTAINER_EXT to "mkv",
)
```

**Extension Resolution (Sniffing-First Architecture):**
- ExoPlayer uses native content sniffing for ALL progressive formats
- MIME type is ONLY set for adaptive streaming (HLS/DASH/SS)
- `container_extension` is SSOT for VOD/Series file extension
- LIVE defaults to `ts` (most reliable for IPTV)

**Compile-Time Gating (Issue #564):**
- Chucker gating centralized in `infra/networking` (debug/release source sets)
- `playback/xtream` inherits Chucker via `@XtreamHttpClient` → `@PlatformHttpClient` chain
- Release builds: NO Chucker, zero overhead (no-op interceptor in release source set)

**Dependencies:**
- `playback/domain` - Interfaces
- `infra/transport-xtream` - XtreamUrlBuilder
- `androidx.media3:media3-datasource` - DataSource base classes
- `okhttp3` - HTTP client (via `@XtreamHttpClient` from transport-xtream)

---

### playback/local (Future)

**Purpose:** Local file playback (for `pipeline/io`).

**Planned Components:**
- `LocalPlaybackSourceFactoryImpl` - File URI resolution
- `LocalFileDataSource` - ContentResolver integration

**Status:** Reserved, not yet implemented.

---

### playback/audiobook (Future)

**Purpose:** Audiobook playback (for `pipeline/audiobook`).

**Planned Components:**
- `AudiobookPlaybackSourceFactoryImpl` - Chapter handling
- `AudiobookProgressTracker` - Resume per chapter

**Status:** Reserved, not yet implemented.

---

## ⚠️ Critical Architecture Patterns

### Factory Resolution in Player

```kotlin
// Player (source-agnostic)
@Singleton
class PlaybackSourceResolver @Inject constructor(
    private val factories: Set<@JvmSuppressWildcards PlaybackSourceFactory>,
) {
    suspend fun resolve(context: PlaybackContext): PlaybackSource {
        val factory = factories.find { it. supports(context.sourceType) }
            ?: return resolveFallback(context)
        
        return factory.createSource(context)
    }
}

// ✅ Player never knows about Telegram/Xtream! 
// ✅ Adding new source = create factory + bind via @IntoSet
// ✅ Zero player code changes needed
```

---

### Media3 DataSource Pattern

```kotlin
// ✅ CORRECT: Custom DataSource delegates to FileDataSource
class TelegramFileDataSource(
    private val remoteResolver: TelegramRemoteResolver,
    private val fileClient: TelegramFileClient,
) : BaseDataSource(/* ... */) {
    
    private var fileDataSource: FileDataSource? = null
    
    override fun open(dataSpec: DataSpec): Long {
        // 1. Parse tg:// URI
        val chatId = dataSpec.uri.getQueryParameter("chatId")?.toLongOrNull()
        val messageId = dataSpec.uri.getQueryParameter("messageId")?.toLongOrNull()
        
        // 2. Resolve via transport (remoteId-first)
        val resolved = remoteResolver.resolveMedia(TelegramRemoteId(chatId, messageId))
        
        // 3. Ensure file ready
        readyEnsurer.ensureStreamingReady(resolved. fileId, dataSpec. position)
        
        // 4.  Delegate to FileDataSource (zero-copy)
        val file = fileClient.getFile(resolved.fileId)
        val localUri = Uri.fromFile(File(file.localPath))
        
        fileDataSource = FileDataSource()
        return fileDataSource!!.open(dataSpec. withUri(localUri))
    }
    
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        fileDataSource?.read(buffer, offset, length) ?: throw IOException("Not opened")
    
    override fun close() {
        fileDataSource?. close()
        fileDataSource = null
    }
}
```

---

### Resume Position Management

```kotlin
// ✅ CORRECT: ResumeManager uses CanonicalMediaRepository
@Singleton
class ObxResumeManager @Inject constructor(
    private val canonicalMediaRepository: CanonicalMediaRepository,
) : ResumeManager {
    
    override suspend fun getResumePoint(contentId: String): ResumePoint? {
        val canonicalId = parseCanonicalId(contentId)
        val resumeInfo = canonicalMediaRepository.getCanonicalResume(
            canonicalId = canonicalId,
            profileId = DEFAULT_PROFILE_ID,
        )
        
        return resumeInfo?.let { info ->
            ResumePoint(
                contentId = contentId,
                type = PlaybackType.VOD,
                positionMs = info.positionMs,
                durationMs = info. durationMs,
                updatedAt = info.updatedAt,
                profileId = DEFAULT_PROFILE_ID,
            )
        }
    }
    
    override suspend fun saveResumePosition(
        context: PlaybackContext,
        positionMs: Long,
        durationMs: Long,
    ) {
        val canonicalId = parseCanonicalId(context. canonicalId)
        val sourceRef = MediaSourceRef(
            sourceType = mapPlayerSourceType(context.sourceType),
            sourceId = PipelineItemId(context.sourceKey ?: context.canonicalId),
            sourceLabel = context.title,
            addedAt = System.currentTimeMillis(),
        )
        
        canonicalMediaRepository.setCanonicalResume(
            canonicalId = canonicalId,
            profileId = DEFAULT_PROFILE_ID,
            positionMs = positionMs,
            durationMs = durationMs,
            sourceRef = sourceRef,
        )
    }
}

// ❌ WRONG: Direct ObjectBox access
class ResumeManagerImpl @Inject constructor(
    private val boxStore: BoxStore,  // WRONG - bypasses repository!
) : ResumeManager {
    override suspend fun saveResumePosition(... ) {
        val box = boxStore.boxFor(ObxCanonicalResumeMark::class.java)  // WRONG! 
        box.put(...)  // WRONG!
    }
}
```

---

## 📐 Architecture Position

```
Feature Layer (UI)
      ↓
PlayerEntryPoint (playback/domain interface)
      ↓
Player Layer (player/internal)
      ↓
PlaybackSourceResolver (player/internal)
      ↓
Set<PlaybackSourceFactory> (injected via DI) ← YOU ARE HERE
      ├── TelegramPlaybackSourceFactoryImpl (playback/telegram)
      ├── XtreamPlaybackSourceFactoryImpl (playback/xtream)
      ├── LocalPlaybackSourceFactoryImpl (playback/local - future)
      └── AudiobookPlaybackSourceFactoryImpl (playback/audiobook - future)
      ↓
PlaybackSource (URI + DataSourceType + MIME)
      ↓
Media3 DataSource (source-specific)
      ├── TelegramFileDataSource → TelegramFileClient (transport)
      ├── XtreamHttpDataSource → XtreamUrlBuilder (transport)
      ├── FileDataSource (local files)
      └── Standard HTTP DataSource (fallback)
      ↓
ExoPlayer
```

---

## 🔍 Pre-Change Verification

```bash
# 1. No forbidden imports
grep -rn "import.*pipeline\|import.*infra\.data\|import.*player\.internal" playback/

# 2. No business logic
grep -rn "normalizeTitle\|classifyMediaType\|generateGlobalId\|searchTmdb" playback/

# 3. No direct transport implementation imports
grep -rn "import org\.drinkless\.td\|import okhttp3\." playback/telegram/ playback/xtream/

# 4. All factories bound via @IntoSet
find playback/ -name "*PlaybackModule. kt" -exec grep -L "@IntoSet" {} \;

# All should return empty! 
```

---

## ✅ PLATIN Checklist

### Common (All Playback Modules)
- [ ] Implements `PlaybackSourceFactory` interface
- [ ] Bound via `@Binds @IntoSet` in Hilt module
- [ ] NO pipeline imports (`TelegramMediaItem`, `XtreamVodItem`)
- [ ] NO data layer imports (use domain repositories)
- [ ] NO player internal imports (use `PlaybackContext` only)
- [ ] NO business logic (normalization, classification)
- [ ] Uses UnifiedLog for all logging
- [ ] Creates `PlaybackSource` with correct `DataSourceType`

### playback/telegram Specific
- [ ] `supports(SourceType.TELEGRAM)` returns true
- [ ] Uses `TelegramRemoteResolver` for remoteId-first resolution
- [ ] Builds `tg://` URIs with chatId + messageId
- [ ] `TelegramFileDataSource` implements Media3 `DataSource`
- [ ] Handles MP4 moov atom detection (PROGRESSIVE_FILE vs FULL_FILE)
- [ ] Never treats "moov not found" as fatal error
- [ ] Uses `TelegramFileReadyEnsurer` for streaming readiness
- [ ] Delegates to FileDataSource for actual I/O (zero-copy)

### playback/xtream Specific
- [ ] `supports(SourceType.XTREAM)` returns true
- [ ] Uses `XtreamUrlBuilder` for authenticated URLs
- [ ] Passes playback hints via `PlaybackContext. extras`
- [ ] Separate headers for API vs Playback (NO gzip on streams!)
- [ ] HLS format selected when available + HLS module present
- [ ] Fallback to TS when HLS unavailable
- [ ] Returns `DataSourceType.XTREAM_HTTP` for proper redirect handling
- [ ] Chucker inherited via @XtreamHttpClient (gated in infra/networking)

### playback/domain Specific
- [ ] `@Multibinds` declared for empty set support
- [ ] Interface-only (no source-specific code)
- [ ] Default implementations use repository interfaces
- [ ] NO ObjectBox direct access (use CanonicalMediaRepository)

---

## 📚 Reference Documents (Priority Order)

1. **`/AGENTS.md`** - Section "Playback Module Factory Pattern" (CRITICAL)
2. **`/docs/v2/internal-player/PLAYER_ARCHITECTURE_V2.md`** - Layer boundaries
3. **`/docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md`** - Phase 4 implementation
4. **`/contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md`** - Playback behavior
5. **`/contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`** - remoteId-first design
6. **`/playback/domain/README.md`** - Module-specific rules
7. **`/playback/telegram/README.md`** - Telegram playback specifics
8. **`/playback/xtream/README.md`** - Xtream playback specifics

---

## 🚨 Common Violations & Solutions

### Violation 1: Missing @IntoSet Binding

```kotlin
// ❌ WRONG
@Module
@InstallIn(SingletonComponent::class)
abstract class MyPlaybackModule {
    @Binds
    abstract fun bindFactory(impl: MyFactoryImpl): PlaybackSourceFactory
}

// ✅ CORRECT
@Module
@InstallIn(SingletonComponent::class)
abstract class MyPlaybackModule {
    @Binds @IntoSet
    abstract fun bindFactory(impl: MyFactoryImpl): PlaybackSourceFactory
}
```

### Violation 2: Pipeline Imports

```kotlin
// ❌ WRONG
import com.fishit.player.pipeline.telegram.TelegramMediaItem

fun toPlaybackSource(item: TelegramMediaItem): PlaybackSource { ...  }

// ✅ CORRECT
suspend fun createSource(context: PlaybackContext): PlaybackSource {
    // Use only PlaybackContext (source-agnostic)
}
```

### Violation 3: Direct Transport Calls

```kotlin
// ❌ WRONG
val message = TdlClient.create().getMessage(chatId, messageId)

// ✅ CORRECT
val resolved = remoteResolver.resolveMedia(TelegramRemoteId(chatId, messageId))
```

### Violation 4: Business Logic in Playback

```kotlin
// ❌ WRONG
fun normalizeTitle(title: String): String {
    return title.replace("[1080p]", "").trim()
}

// ✅ CORRECT
// No normalization!  Pass raw title to player
```

---

**End of PLATIN Instructions for playback/***