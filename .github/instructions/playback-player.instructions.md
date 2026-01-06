---
applyTo: 
  - playback/domain/**
  - playback/telegram/**
  - playback/xtream/**
  - playback/local/**
  - playback/audiobook/**
  - player/internal/**
  - player/miniplayer/**
  - player/input/**
  - player/ui/**
---

# 🏆 PLATIN Instructions:  playback/* + player/*

> **PLATIN STANDARD** - Playback Orchestration & Player Layer. 
>
> **Purpose:** Source-agnostic playback architecture with strict layer isolation.
> Player NEVER knows about specific sources (Telegram, Xtream). All source specifics
> live in `playback/<source>` modules via the `PlaybackSourceFactory` pattern.
>
> **Critical Principle:** Player is source-agnostic. It MUST NOT depend on transport or pipeline modules. 

---

## 🔴 ABSOLUTE HARD RULES

### 1. Player Layer Isolation (CRITICAL - READ FIRST)

```kotlin
// ❌ FORBIDDEN IN PLAYER LAYER
import com.fishit.player.pipeline.telegram.*              // Pipeline
import com.fishit.player. pipeline.xtream.*                // Pipeline
import com.fishit. player.infra.transport.telegram.*       // Transport
import com.fishit.player.infra.transport.xtream.*         // Transport
import org.drinkless.td.TdApi.*                            // TDLib
import okhttp3.*                                           // HTTP client

// ✅ CORRECT IN PLAYER LAYER
import com.fishit.player.core.playermodel.*                // PlaybackContext
import com.fishit.player.playback.domain.*                 // Interfaces
import androidx.media3.*                                   // ExoPlayer/Media3
```

**Why This Matters:**
- Player must work with **ANY** source without code changes
- Adding a new source (Audiobook, Local, etc.) MUST NOT modify player code
- Only `playback/<source>` modules know source specifics

---

### 2. PlaybackSourceFactory Pattern (MANDATORY)

**ALL playback sources** MUST use the `@Multibinds` + `@IntoSet` pattern:

```kotlin
// ✅ CORRECT:  playback/telegram/di/TelegramPlaybackModule. kt
@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramPlaybackModule {
    @Binds @IntoSet
    abstract fun bindFactory(impl: TelegramPlaybackSourceFactoryImpl): PlaybackSourceFactory
}

// ✅ CORRECT: playback/xtream/di/XtreamPlaybackModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class XtreamPlaybackModule {
    @Binds @IntoSet
    abstract fun bindFactory(impl: XtreamPlaybackSourceFactoryImpl): PlaybackSourceFactory
}

// ✅ CORRECT: playback/domain/di/PlaybackDomainModule.kt
@Module
@InstallIn(SingletonComponent:: class)
abstract class PlaybackDomainModule {
    @Multibinds
    abstract fun bindPlaybackSourceFactories(): Set<PlaybackSourceFactory>
}
```

**Player Resolution:**

```kotlin
// player/internal/source/PlaybackSourceResolver.kt
@Singleton
class PlaybackSourceResolver @Inject constructor(
    private val factories: Set<@JvmSuppressWildcards PlaybackSourceFactory>,
) {
    suspend fun resolve(context: PlaybackContext): PlaybackSource {
        val factory = factories.find { it.supports(context. sourceType) }
        return factory?.createSource(context) ?: resolveFallback(context)
    }
}
```

---

### 3. No Business Logic in Playback Modules

```kotlin
// ❌ FORBIDDEN in playback/<source>
fun normalizeTitle(title: String): String              // → core/metadata-normalizer
fun classifyMediaType(item: Any): MediaType            // → pipeline
fun generateGlobalId(... ): String                      // → core/metadata-normalizer
suspend fun searchTmdb(title: String): TmdbRef?        // → core/metadata-normalizer
suspend fun persistResumePosition(... ):  Unit           // → playback/domain (ResumeManager)

// ✅ CORRECT in playback/<source>
suspend fun createSource(context: PlaybackContext): PlaybackSource  // Pure mapping
fun buildMediaItem(context: PlaybackContext): MediaItem             // Pure construction
fun createDataSource(uri: Uri): DataSource                          // Pure transport
```

---

### 4. PlaybackContext is Source-Agnostic

```kotlin
// ✅ CORRECT: PlaybackContext in core/player-model
data class PlaybackContext(
    val canonicalId: String,          // Stable identifier
    val sourceType: SourceType,        // TELEGRAM, XTREAM, FILE, etc.
    val sourceKey: String?,            // Opaque key for resolution
    val title: String,
    val uri: String?,                  // Direct URI if known
    val headers: Map<String, String> = emptyMap(),
    val startPositionMs: Long = 0L,
    val isLive: Boolean = false,
    val extras: Map<String, String> = emptyMap(),  // Source-specific hints
)

// ❌ FORBIDDEN: Source-specific fields in PlaybackContext
data class PlaybackContext(
    val telegramChatId: Long,          // WRONG - source-specific! 
    val telegramMessageId: Long,       // WRONG - source-specific!
    val xtreamStreamId: Int,           // WRONG - source-specific!
)
```

---

## 📋 Module Responsibilities

### playback/domain

**Purpose:** Playback orchestration interfaces and domain services. 

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| `PlaybackSourceFactory` interface | ✅ | Source-specific implementations |
| `ResumeManager` interface | ✅ | Direct persistence (use repository) |
| `KidsPlaybackGate` interface | ✅ | UI logic |
| `SubtitleSelectionPolicy` interface | ✅ | Transport calls |
| `LivePlaybackController` interface | ✅ | EPG persistence |
| Domain contracts | ✅ | Implementation details |

**Public Interfaces:**
- `PlaybackSourceFactory` - Factory interface for source resolution
- `PlaybackSource` - Resolved playback descriptor (URI, MIME, headers, DataSourceType)
- `ResumeManager` - Resume position management
- `KidsPlaybackGate` - Kids mode & screen time enforcement
- `SubtitleSelectionPolicy` - Subtitle track selection logic
- `LivePlaybackController` - Live TV channel switching & EPG
- `TvInputController` - TV remote input handling
- `PlayerEntryPoint` - Feature module playback initiation

**Implementation Classes:**
- `ObxResumeManager` - Resume persistence via CanonicalMediaRepository
- `DefaultKidsPlaybackGate` - Stub kids gate (Phase 1)
- `DefaultSubtitleSelectionPolicy` - Auto subtitle selection
- `DefaultLivePlaybackController` - Live TV orchestration
- `DefaultTvInputController` - TV remote mapping

---

### playback/telegram

**Purpose:** Telegram-specific playback source resolution. 

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| `TelegramPlaybackSourceFactoryImpl` | ✅ | Pipeline imports |
| `TelegramFileDataSource` (Media3) | ✅ | Direct TDLib calls (use transport) |
| `TelegramFileReadyEnsurer` | ✅ | Repository access |
| `tg://` URI construction | ✅ | Normalization logic |

**Public Surface:**
- `TelegramPlaybackSourceFactoryImpl` - Factory implementation
- `TelegramFileDataSource` - Media3 DataSource for TDLib streaming
- `TelegramFileDataSourceFactory` - Factory for DataSource creation
- `TelegramPlaybackMode` - PROGRESSIVE_FILE vs FULL_FILE
- `TelegramPlaybackModeDetector` - MIME-based mode selection

**Dependencies:**
- `TelegramRemoteResolver` (transport) - remoteId → fileId resolution
- `TelegramFileClient` (transport) - File download primitives
- `TelegramFileReadyEnsurer` (local) - Streaming readiness validation

**URI Format:**
```
tg://file/<remoteId>? chatId=<chatId>&messageId=<messageId>
```

**Platinum Playback:**
- ✅ ALL video formats playable (MP4, MKV, WebM, AVI)
- ✅ "moov atom not found" is NEVER fatal (fallback to FULL_FILE)
- ✅ Zero-copy streaming via TDLib cache

---

### playback/xtream

**Purpose:** Xtream-specific playback source resolution. 

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| `XtreamPlaybackSourceFactoryImpl` | ✅ | Pipeline imports |
| URL building for streams | ✅ | Direct HTTP calls (use transport) |
| Header configuration | ✅ | Repository access |

**Public Surface:**
- `XtreamPlaybackSourceFactoryImpl` - Factory implementation
- `XtreamDataSourceFactoryProvider` - DataSource factory provider for per-request headers

**Dependencies:**
- `XtreamUrlBuilder` (transport) - Authenticated URL construction

**Playback Hints:**
```kotlin
extras = mapOf(
    PlaybackHintKeys. XTREAM_SERVER_URL to serverUrl,
    PlaybackHintKeys.XTREAM_STREAM_ID to streamId. toString(),
    PlaybackHintKeys.XTREAM_USERNAME to username,
    PlaybackHintKeys.XTREAM_PASSWORD to password,
)
```

---

### playback/local (Future)

**Purpose:** Local file playback (for `pipeline/io`).

**Responsibilities:**
- File URI resolution
- Local file DataSource
- ContentResolver integration

---

### playback/audiobook (Future)

**Purpose:** Audiobook playback (for `pipeline/audiobook`).

**Responsibilities:**
- Audiobook file resolution
- Chapter metadata handling
- Progress tracking

---

### player/internal

**Purpose:** SIP Internal Player core engine.

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| `InternalPlayerSession` | ✅ | Source-specific code |
| `PlaybackSourceResolver` | ✅ | Direct transport calls |
| ExoPlayer management | ✅ | Pipeline imports |
| Media3 integration | ✅ | TDLib/OkHttp direct usage |
| NextLib codecs | ✅ | Business logic |

**Public Surface:**
- `InternalPlayerEntry` - Composable entry point
- `InternalPlayerSession` - Player session management
- `InternalPlayerState` - Player UI state
- `PlaybackSourceResolver` - Source resolution via factories

**Architecture:**
```
Feature → PlayerEntryPoint (domain)
           ↓
       InternalPlayerEntry (player/internal)
           ↓
       InternalPlayerSession
           ↓
       PlaybackSourceResolver
           ↓
       Set<PlaybackSourceFactory> (via DI)
```

---

### player/miniplayer

**Purpose:** In-app mini-player (PiP overlay for TV).

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| MiniPlayer state management | ✅ | Source-specific logic |
| Overlay UI | ✅ | Direct playback control (use session) |
| Focus handling | ✅ | Transport calls |

**Public Surface:**
- `MiniPlayerState` - Singleton state holder
- `MiniPlayerMode` - HIDDEN, MINIMIZED, DOCKED
- `MiniPlayerManager` - Interface for miniplayer control
- `MiniPlayerCoordinator` - Composable coordinator
- `MiniPlayerOverlay` - UI overlay

---

### player/input

**Purpose:** TV remote / DPAD input handling.

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| KeyEvent → TvAction mapping | ✅ | Source-specific logic |
| DPAD focus control | ✅ | Direct player commands |
| TV input configuration | ✅ | Transport calls |

**Public Surface:**
- `TvInputAction` - Logical TV input actions
- `TvInputController` - Input handling coordinator
- `TvKeyDebouncer` - Debouncing for Fire TV

---

### player/ui

**Purpose:** Player UI components (controls overlay, dialogs).

| Responsibility | Allowed | Forbidden |
|----------------|---------|-----------|
| Controls overlay | ✅ | Source-specific UI |
| Subtitle/CC menu | ✅ | Direct player access (use controller) |
| Error dialogs | ✅ | Business logic |

**Public Surface:**
- `InternalPlayerControls` - Controls overlay
- `PlayerSurface` - Video surface + gestures
- `SeekPreviewOverlay` - Trickplay preview

---

## ⚠️ Critical Architecture Patterns

### PlaybackSourceFactory Implementation

```kotlin
// ✅ CORRECT: playback/telegram/TelegramPlaybackSourceFactoryImpl.kt
@Singleton
class TelegramPlaybackSourceFactoryImpl @Inject constructor(
    private val remoteResolver: TelegramRemoteResolver,
    private val fileClient: TelegramFileClient,
) : PlaybackSourceFactory {
    
    override fun supports(sourceType:  SourceType): Boolean =
        sourceType == SourceType.TELEGRAM
    
    override suspend fun createSource(context: PlaybackContext): PlaybackSource {
        // Extract remoteId from context
        val (chatId, messageId) = parseRemoteId(context.sourceKey)
        
        // Resolve via RemoteResolver (remoteId-first)
        val resolved = remoteResolver.resolveMedia(TelegramRemoteId(chatId, messageId))
            ?: throw PlaybackSourceException("Cannot resolve Telegram media")
        
        // Build tg: // URI
        val uri = "tg://file/${resolved.mediaRemoteId}?chatId=$chatId&messageId=$messageId"
        
        return PlaybackSource(
            uri = uri,
            mimeType = resolved.mimeType,
            dataSourceType = DataSourceType.TELEGRAM_FILE,
        )
    }
}
```

---

### Media3 DataSource Implementation

```kotlin
// ✅ CORRECT: playback/telegram/TelegramFileDataSource. kt
class TelegramFileDataSource(
    private val remoteResolver: TelegramRemoteResolver,
    private val fileClient: TelegramFileClient,
    private val readyEnsurer: TelegramFileReadyEnsurer,
) : BaseDataSource(/* ... */) {
    
    override suspend fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        
        // Parse tg:// URI
        val chatId = uri.getQueryParameter("chatId")?.toLongOrNull()
        val messageId = uri.getQueryParameter("messageId")?.toLongOrNull()
        val remoteId = uri.pathSegments.getOrNull(1)
        
        // Resolution paths (in priority order):
        // 1. RemoteId-First (chatId + messageId) - PREFERRED
        // 2. Direct fileId (hint only, will retry if stale)
        // 3. Fallback to remoteId resolution
        val fileId = resolveFileId(chatId, messageId, remoteId)
        
        // Ensure streaming-ready
        readyEnsurer.ensureStreamingReady(fileId, dataSpec. position)
        
        // Open FileDataSource delegate
        val file = fileClient.getFile(fileId)
        val fileUri = Uri.fromFile(File(file.localPath))
        
        delegate = FileDataSource()
        return delegate.open(dataSpec. withUri(fileUri))
    }
}
```

---

### Resume Position Management

```kotlin
// ✅ CORRECT: playback/domain/defaults/ObxResumeManager.kt
@Singleton
class ObxResumeManager @Inject constructor(
    private val canonicalMediaRepository: CanonicalMediaRepository,
) :  ResumeManager {
    
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
                durationMs = info.durationMs,
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
```

---

## 📐 Architecture Position

```
Feature Layer
      ↓
PlayerEntryPoint (playback/domain interface)
      ↓
InternalPlayerEntry (player/internal)
      ↓
PlaybackSourceResolver
      ↓
Set<PlaybackSourceFactory> (via DI)
      ├── TelegramPlaybackSourceFactoryImpl
      ├── XtreamPlaybackSourceFactoryImpl
      ├── LocalPlaybackSourceFactoryImpl (future)
      └── AudiobookPlaybackSourceFactoryImpl (future)
      ↓
PlaybackSource (URI, DataSourceType, headers)
      ↓
Media3 MediaItem + DataSource
      ↓
ExoPlayer
```

---

## 🔍 Layer Boundary Enforcement

### Upstream Dependencies (ALLOWED)

```kotlin
// playback/domain
import com.fishit.player.core.model.*                      // Core types
import com.fishit.player.core.playermodel.*                // PlaybackContext
import com.fishit.player.core.persistence.repository.*     // CanonicalMediaRepository
import com.fishit. player.infra.logging.*                   // UnifiedLog
import kotlinx.coroutines.*                                // Coroutines

// playback/telegram
import com.fishit.player.playback.domain.*                 // Interfaces
import com.fishit.player.infra.transport. telegram.*        // Transport (typed interfaces ONLY)
import androidx.media3.*                                   // Media3/ExoPlayer

// playback/xtream
import com.fishit.player. playback.domain.*                 // Interfaces
import com.fishit.player.infra.transport. xtream.*          // Transport (XtreamUrlBuilder)
import androidx.media3.*                                   // Media3/ExoPlayer

// player/internal
import com.fishit.player.playback.domain.*                 // Interfaces
import com.fishit. player.core.playermodel.*                // PlaybackContext
import androidx.media3.*                                   // Media3/ExoPlayer
```

### Forbidden Imports (CI-GUARDED)

```kotlin
// ❌ FORBIDDEN IN ALL PLAYBACK/PLAYER MODULES
import com.fishit.player.pipeline.*                        // Pipeline
import com. fishit.player.infra.transport.telegram. internal.*  // Transport internals
import com.fishit. player.infra.transport. xtream.internal.*    // Transport internals
import org.drinkless.td.TdApi.*                            // TDLib (use transport)
import okhttp3.*                                           // HTTP (use transport)
import com.fishit.player.core.persistence.obx.*            // ObjectBox (use repositories)
```

---

## 🔍 Pre-Change Verification

```bash
# 1. No forbidden imports in player layer
grep -rn "import.*pipeline\|import.*infra\. transport\. telegram\. internal\|import.*infra\. transport\.xtream\.internal" player/internal/ player/miniplayer/ player/input/ player/ui/

# 2. No source-specific code in player layer
grep -rn "TelegramMediaItem\|XtreamVodItem\|TgMessage\|XtreamVodStream" player/internal/ player/miniplayer/

# 3. No business logic in playback modules
grep -rn "normalizeTitle\|classifyMediaType\|generateGlobalId\|searchTmdb" playback/telegram/ playback/xtream/

# 4. No direct TDLib/OkHttp in playback modules
grep -rn "import org\. drinkless\.td\|import okhttp3\." playback/telegram/ playback/xtream/

# All should return empty! 
```

---

## ✅ PLATIN Checklist

### Common (All Playback Modules)
- [ ] Implements `PlaybackSourceFactory` interface
- [ ] Bound via `@Binds @IntoSet` in Hilt module
- [ ] NO pipeline imports (`TelegramMediaItem`, `XtreamVodItem`)
- [ ] NO direct transport implementation imports (use interfaces)
- [ ] NO business logic (normalization, classification)
- [ ] NO repository access (use domain services)
- [ ] Uses UnifiedLog for all logging
- [ ] Creates `PlaybackSource` with correct `DataSourceType`

### Playback/Telegram Specific
- [ ] `supports(SourceType.TELEGRAM)` returns true
- [ ] Uses `TelegramRemoteResolver` for remoteId-first resolution
- [ ] Builds `tg://` URIs with chatId + messageId
- [ ] `TelegramFileDataSource` implements Media3 `DataSource`
- [ ] Handles MP4 moov atom detection (PROGRESSIVE_FILE vs FULL_FILE)
- [ ] Never treats "moov not found" as fatal error
- [ ] Uses `TelegramFileReadyEnsurer` for streaming readiness

### Playback/Xtream Specific
- [ ] `supports(SourceType. XTREAM)` returns true
- [ ] Uses `XtreamUrlBuilder` for authenticated URLs
- [ ] Passes playback hints via `PlaybackContext. extras`
- [ ] Separate headers for API vs Playback (NO gzip on streams!)
- [ ] Returns `DataSourceType.XTREAM_HTTP` for proper redirect handling

### Player/Internal Specific
- [ ] Source-agnostic (NO source-specific imports)
- [ ] Injects `Set<PlaybackSourceFactory>` via DI
- [ ] Uses `PlaybackSourceResolver` for source resolution
- [ ] Works with ANY `PlaybackSource` regardless of source
- [ ] NO TDLib/OkHttp/Pipeline imports
- [ ] Resume via `ResumeManager` interface
- [ ] Kids gate via `KidsPlaybackGate` interface

---

## 📚 Reference Documents (Priority Order)

1. **`/AGENTS. md`** - Section "Player Layer Isolation" (CRITICAL)
2. **`/docs/v2/internal-player/PLAYER_ARCHITECTURE_V2.md`** - Player architecture overview
3. **`/docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md`** - Migration status
4. **`/contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md`** - Resume & kids behavior
5. **`/contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`** - remoteId-first design
6. **`/playback/domain/README.md`** - Module-specific rules
7. **`/playback/telegram/README.md`** - Telegram playback specifics
8. **`/playback/xtream/README.md`** - Xtream playback specifics
9. **`/player/internal/README.md`** - Player internal docs

---

## 🚨 Common Violations & Solutions

### Violation 1: Source-Specific Code in Player

```kotlin
// ❌ WRONG (in player/internal)
if (context.sourceType == SourceType.TELEGRAM) {
    // Telegram-specific logic
}

// ✅ CORRECT
// Player resolves via factories, doesn't care about source
val source = sourceResolver.resolve(context)
val mediaItem = buildMediaItem(source)
```

### Violation 2: Direct Transport Calls in Playback Module

```kotlin
// ❌ WRONG (in playback/telegram)
val message = TdlClient.create().getMessage(chatId, messageId)

// ✅ CORRECT
val resolved = remoteResolver.resolveMedia(TelegramRemoteId(chatId, messageId))
```

### Violation 3: Missing Factory Registration

```kotlin
// ❌ WRONG:  Factory not bound
class MySourcePlaybackSourceFactoryImpl :  PlaybackSourceFactory { ... }

// ✅ CORRECT: Factory bound via DI
@Module
@InstallIn(SingletonComponent::class)
abstract class MySourcePlaybackModule {
    @Binds @IntoSet
    abstract fun bindFactory(impl: MySourcePlaybackSourceFactoryImpl): PlaybackSourceFactory
}
```

### Violation 4: Pipeline Imports in Playback

```kotlin
// ❌ WRONG (in playback/telegram)
import com.fishit.player. pipeline. telegram.TelegramMediaItem

fun toPlaybackSource(item: TelegramMediaItem): PlaybackSource { ... }

// ✅ CORRECT
fun createSource(context: PlaybackContext): PlaybackSource {
    // Use only PlaybackContext (source-agnostic)
}
```

---

**End of PLATIN Instructions for playback/* + player/***
