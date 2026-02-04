---
applyTo: 
  - player/internal/**
  - player/miniplayer/**
  - player/input/**
  - player/ui/**
  - player/nextlib-codecs/**
---

# 🏆 PLATIN Instructions:  player/*

**Version:** 1.0  
**Last Updated:** 2026-02-04  
**Status:** Active

> **PLATIN STANDARD** - Internal Player (SIP) & Player UI Layer. 
>
> **Purpose:** Source-agnostic player engine with ExoPlayer/Media3 integration.
> Player knows ONLY `PlaybackContext` - all source specifics live in `playback/*` modules. 
>
> **Critical Principle:** Player is 100% source-agnostic.  Adding Telegram/Xtream/Audiobook
> requires ZERO player code changes - only new `PlaybackSourceFactory` implementations.

---

## 🔴 ABSOLUTE HARD RULES

### 1. Player Source-Agnosticism (CRITICAL - READ FIRST)

```kotlin
// ✅ CORRECT: Player uses only PlaybackContext
@Singleton
class InternalPlayerSession @Inject constructor(
    private val context: Context,
    private val sourceResolver: PlaybackSourceResolver,  // Abstraction
    private val resumeManager: ResumeManager,            // Abstraction
    private val kidsPlaybackGate: KidsPlaybackGate,     // Abstraction
) {
    suspend fun play(playbackContext: PlaybackContext) {
        // Resolve source via factory pattern
        val source = sourceResolver.resolve(playbackContext)
        
        // Build MediaItem
        val mediaItem = buildMediaItem(source)
        
        // Play
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
    }
}

// ❌ FORBIDDEN: Source-specific code in player
@Singleton
class InternalPlayerSession @Inject constructor(
    private val telegramClient: TelegramFileClient,      // WRONG - source-specific!
    private val xtreamUrlBuilder: XtreamUrlBuilder,      // WRONG - source-specific! 
) {
    suspend fun play(context: PlaybackContext) {
        when (context.sourceType) {
            SourceType.TELEGRAM -> {
                // WRONG - source-specific logic in player!
                val file = telegramClient.downloadFile(...)
            }
            SourceType.XTREAM -> {
                // WRONG - source-specific logic in player!
                val url = xtreamUrlBuilder.buildUrl(...)
            }
        }
    }
}
```

**Why This Matters:**
- Player must work with ANY source without code changes
- Adding Audiobook/Local/NewSource = create factory, bind via DI, done
- Player never imports pipeline, transport, or data modules

---

### 2. Layer Boundary Enforcement

```kotlin
// ✅ ALLOWED imports in player/*
import com.fishit.player.core.playermodel.*                  // PlaybackContext, PlaybackState
import com.fishit.player.playback.domain.*                   // Interfaces only
import androidx.media3.*                                     // ExoPlayer/Media3
import androidx.compose.*                                    // Compose UI (player/ui only)
import com.fishit.player.infra.logging.*                     // UnifiedLog

// ❌ FORBIDDEN imports in player/*
import com.fishit.player.pipeline.*                          // Pipeline
import com.fishit.player.infra.transport.*                   // Transport
import com.fishit.player.infra.data.*                        // Data layer
import org.drinkless.td.TdApi.*                              // TDLib
import okhttp3.*                                              // HTTP client
import com.fishit.player.core.persistence.obx.*              // ObjectBox entities
```

---

### 3. Player UI Architecture (player/ui)

**Hard Rules for `player/ui`:**

```kotlin
// ❌ FORBIDDEN in player/ui
import dagger.hilt.EntryPoint                                // Anti-pattern!
import dagger.hilt.EntryPointAccessors                       // Anti-pattern!
import com.fishit.player.internal.session.InternalPlayerSession  // Engine internals! 
import com.fishit.player.internal.source.PlaybackSourceResolver  // Engine wiring! 

// ✅ CORRECT in player/ui
import com.fishit.player.playback.domain.PlayerEntryPoint    // High-level abstraction
import androidx.hilt.navigation.compose.hiltViewModel        // ViewModel injection

// ✅ CORRECT:  Use ViewModel with constructor injection
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerEntry: PlayerEntryPoint,  // High-level interface
) : ViewModel() {
    fun play(context: PlaybackContext) {
        viewModelScope.launch {
            playerEntry.start(context)
        }
    }
}

// UI uses ViewModel
@Composable
fun PlayerScreen(
    context: PlaybackContext,
    onExit: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    // Access state via ViewModel
}
```

**Why EntryPoints are Forbidden:**
- Bypasses proper dependency injection
- Creates hidden dependencies
- Makes testing impossible
- Violates layer isolation

---

## 📋 Module Responsibilities

### player/internal

**Purpose:** SIP (Simplified Internal Player) core engine.

| Component | Responsibility | Forbidden |
|-----------|----------------|-----------|
| `InternalPlayerSession` | ExoPlayer lifecycle, state management | Source-specific logic |
| `InternalPlayerState` | Immutable state model | UI code |
| `PlaybackSourceResolver` | Factory injection + source resolution | Source-specific imports |
| `SubtitleTrackManager` | Subtitle discovery + selection | Transport calls |
| `AudioTrackManager` | Audio track discovery + selection | Pipeline imports |
| `PlayerDataSourceModule` | DI for DataSource. Factory map | Business logic |

**Public Surface:**
- `InternalPlayerSession` - Player engine
- `InternalPlayerState` - UI state
- `InternalPlayerEntry` - Composable entry point
- `getPlayer(): Player? ` - For UI attachment

**Architecture:**
```
InternalPlayerSession
    ├── ExoPlayer instance (lifecycle)
    ├── PlaybackSourceResolver (factory injection)
    ├── ResumeManager (persistence abstraction)
    ├── KidsPlaybackGate (policy abstraction)
    ├── SubtitleTrackManager (track selection)
    ├── AudioTrackManager (track selection)
    └── NextlibCodecConfigurator (FFmpeg codecs)
```

**Phase Status:**
- ✅ Phase 0-4: Core + Factories
- ✅ Phase 5: MiniPlayer integration
- ✅ Phase 6: Subtitles/CC
- ✅ Phase 7: Audio tracks
- ⏳ Phase 8-14: Series mode, Kids gate, Error handling, etc.

**Dependencies:**
- `core/player-model` - PlaybackContext, PlaybackState
- `playback/domain` - Interfaces only
- `player/nextlib-codecs` - FFmpeg codecs
- `androidx.media3:media3-exoplayer: 1.8.0` - ExoPlayer
- `infra/logging` - UnifiedLog

---

### player/miniplayer

**Purpose:** In-app MiniPlayer (floating overlay, not system PiP).

| Component | Responsibility | Forbidden |
|-----------|----------------|-----------|
| `MiniPlayerState` | Immutable state (visibility, mode, anchor, size) | ExoPlayer access |
| `MiniPlayerMode` | NORMAL, RESIZE modes | Source-specific code |
| `MiniPlayerAnchor` | 6 anchor positions | Pipeline imports |
| `MiniPlayerManager` | State machine for transitions | Direct player access |
| `DefaultMiniPlayerManager` | StateFlow-based implementation | Transport calls |
| `MiniPlayerCoordinator` | High-level orchestration | Business logic |
| `MiniPlayerOverlay` | Compose UI with drag/resize | Repository access |

**Public Surface:**
- `MiniPlayerState` - State model
- `MiniPlayerManager` interface
- `DefaultMiniPlayerManager` - Implementation
- `MiniPlayerCoordinator` - High-level API
- `MiniPlayerOverlay` - Composable overlay
- `PlayerWithMiniPlayerState` - Combined state

**Architecture:**
```
InternalPlayerSession (player/internal)
    ↓
MiniPlayerManager (player/miniplayer)
    ↓
MiniPlayerState (immutable)
    ↓
MiniPlayerOverlay (Compose UI)
```

**Key Features:**
- Fullscreen ↔ MiniPlayer transitions
- Drag to move (non-TV)
- Resize mode (FF/RW on TV)
- Snap to anchors
- Return navigation context

**Dependencies:**
- `core/player-model` - PlaybackContext
- `player/internal` - For player state
- `androidx.media3:media3-ui:1.8.0` - PlayerView integration
- Compose UI libraries

---

### player/input

**Purpose:** TV remote / DPAD input handling.

| Component | Responsibility | Forbidden |
|-----------|----------------|-----------|
| `TvInputAction` | Logical TV actions | Source-specific logic |
| `TvInputController` | Input coordinator | Direct player commands |
| `TvKeyDebouncer` | Debouncing for Fire TV | Pipeline imports |

**Public Surface:**
- `TvInputAction` enum
- `TvInputController` interface
- `TvKeyDebouncer` - Debounce helper

**Key Mappings:**
- FF/RW → Seek forward/backward
- Play/Pause → Toggle playback
- Menu → Enter resize mode (MiniPlayer)
- DPAD → Move MiniPlayer (resize mode)

**Dependencies:**
- `core/player-model` - PlaybackContext
- `player/internal` - For player state
- Compose UI for key event handling

---

### player/ui

**Purpose:** Player UI components (controls, dialogs, overlays).

| Component | Responsibility | Forbidden |
|-----------|----------------|-----------|
| `InternalPlayerControls` | Controls overlay | Engine wiring classes |
| `PlayerSurface` | Video surface + gestures | Hilt EntryPoints |
| `SeekPreviewOverlay` | Trickplay preview | Direct session access |
| Subtitle/CC menu | Track selection UI | Business logic |

**Public Surface:**
- `InternalPlayerControls` - Composable controls
- `PlayerSurface` - Composable video surface
- `SeekPreviewOverlay` - Composable seek preview

**Architecture (CRITICAL):**
```
✅ CORRECT Pattern: 
PlayerScreen (UI)
    ↓
PlayerViewModel (Hilt)
    ↓
PlayerEntryPoint (abstraction)
    ↓
InternalPlayerSession (engine)

❌ WRONG Pattern:
PlayerScreen (UI)
    ↓
EntryPointAccessors. fromApplication(...)
    ↓
InternalPlayerSession (direct engine access!)
```

**Hard Rules:**
- NO Hilt EntryPoints (`@EntryPoint`, `EntryPointAccessors`)
- NO engine wiring classes (`PlaybackSourceResolver`, `ResumeManager`)
- NO `com.fishit.player.internal.*` imports from other modules
- USE `@HiltViewModel` with constructor injection
- USE `PlayerEntryPoint` abstraction

**Dependencies:**
- `playback/domain` - PlayerEntryPoint interface
- `core/player-model` - PlaybackContext
- Compose UI libraries
- Hilt for ViewModel injection

---

### player/nextlib-codecs

**Purpose:** FFmpeg software decoders via NextLib. 

| Component | Responsibility | Forbidden |
|-----------|----------------|-----------|
| `NextlibCodecConfigurator` | RenderersFactory creation | Source-specific code |
| `NextlibCodecsModule` | Hilt DI binding | Pipeline imports |

**Supported Codecs:**
- **Audio:** Vorbis, Opus, FLAC, ALAC, MP3, AAC, AC3, EAC3, DTS, TrueHD
- **Video:** H.264, HEVC, VP8, VP9
- **NOT included:** AV1 (use hardware or Media3 native)

**Public Surface:**
- `NextlibCodecConfigurator` interface
- `DefaultNextlibCodecConfigurator` implementation

**License Warning:**
- NextLib is GPL-3.0 due to FFmpeg
- Affects binary distribution
- Consult legal before public release

**Dependencies:**
- `dev. g000sha256.nextlib:media3ext:1.8.0-0. 9.0` - Must match Media3 version
- `androidx.media3:media3-exoplayer:1.8.0` - Player core

---

## ⚠️ Critical Architecture Patterns

### PlaybackSourceResolver (Factory Injection)

```kotlin
// ✅ CORRECT:  Inject Set<PlaybackSourceFactory>
@Singleton
class PlaybackSourceResolver @Inject constructor(
    private val factories: Set<@JvmSuppressWildcards PlaybackSourceFactory>,
) {
    suspend fun resolve(context: PlaybackContext): PlaybackSource {
        val factory = factories.find { it.supports(context.sourceType) }
            ?: return resolveFallback(context)
        
        return factory.createSource(context)
    }
}

// Player uses resolver
class InternalPlayerSession @Inject constructor(
    private val sourceResolver: PlaybackSourceResolver,
) {
    suspend fun play(context: PlaybackContext) {
        val source = sourceResolver.resolve(context)
        // Build MediaItem from source
    }
}
```

**Key Benefits:**
- Empty factory set is valid (fallback to test stream)
- Adding new source = zero player changes
- Player compiles without any transport dependencies

---

### DataSource Factory Map (Media3 Integration)

```kotlin
// ✅ CORRECT: Inject Map<DataSourceType, DataSource. Factory>
@Singleton
class InternalPlayerSession @Inject constructor(
    private val dataSourceFactories: Map<DataSourceType, DataSource.Factory>,
) {
    private fun createPlayer(): ExoPlayer {
        // Select MediaSourceFactory based on DataSourceType
        val mediaSourceFactory = when (source.dataSourceType) {
            DataSourceType.TELEGRAM_FILE -> {
                val factory = dataSourceFactories[DataSourceType.TELEGRAM_FILE]
                    ?: DefaultDataSource.Factory(context)
                ProgressiveMediaSource.Factory(factory)
            }
            DataSourceType.XTREAM_HTTP -> {
                val factory = dataSourceFactories[DataSourceType.XTREAM_HTTP]
                    ?: DefaultHttpDataSource.Factory()
                HlsMediaSource.Factory(factory)
            }
            else -> {
                DefaultMediaSourceFactory(context)
            }
        }
        
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(codecConfigurator. createRenderersFactory(context))
            .build()
    }
}

// DI Module provides factories
@Module
@InstallIn(SingletonComponent::class)
object PlayerDataSourceModule {
    @Provides
    @Singleton
    fun provideDataSourceFactories(
        telegramFactory: TelegramFileDataSourceFactory,
    ): Map<DataSourceType, DataSource.Factory> = mapOf(
        DataSourceType.TELEGRAM_FILE to telegramFactory,
    )
}
```

---

### Resume Management

```kotlin
// ✅ CORRECT: Use ResumeManager abstraction
class InternalPlayerSession @Inject constructor(
    private val resumeManager: ResumeManager,
) {
    suspend fun play(context: PlaybackContext) {
        // Load resume position
        val resumePoint = resumeManager.getResumePoint(context.canonicalId)
        
        // Apply resume
        val startPositionMs = resumePoint?.positionMs ?: context.startPositionMs
        
        // Play
        player?.setMediaItem(mediaItem, startPositionMs)
        player?.prepare()
        player?.play()
    }
    
    private fun handlePositionUpdate(positionMs: Long) {
        scope.launch {
            currentContext?. let { context ->
                resumeManager.saveResumePosition(
                    context = context,
                    positionMs = positionMs,
                    durationMs = player?.duration ?: 0L,
                )
            }
        }
    }
}

// ❌ WRONG: Direct repository access
class InternalPlayerSession @Inject constructor(
    private val canonicalMediaRepository: CanonicalMediaRepository,  // WRONG!
) {
    suspend fun play(context: PlaybackContext) {
        val resume = canonicalMediaRepository.getCanonicalResume(...)  // WRONG!
    }
}
```

---

### MiniPlayer State Machine

```kotlin
// ✅ CORRECT: MiniPlayerManager state transitions
@Singleton
class DefaultMiniPlayerManager @Inject constructor() :  MiniPlayerManager {
    private val _state = MutableStateFlow(MiniPlayerState.INITIAL)
    override val state: StateFlow<MiniPlayerState> = _state.asStateFlow()
    
    override fun enterMiniPlayer(
        fromRoute: String,
        mediaId: Long?,
        rowIndex: Int?,
        itemIndex: Int?,
    ) {
        _state.update { it.copy(
            visible = true,
            returnRoute = fromRoute,
            returnMediaId = mediaId,
            returnRowIndex = rowIndex,
            returnItemIndex = itemIndex,
        )}
    }
    
    override fun exitMiniPlayer(returnToFullPlayer: Boolean) {
        _state.update { it.copy(visible = false) }
        
        // Navigation handled by coordinator, not here
    }
}

// UI observes state
@Composable
fun MiniPlayerOverlay(
    miniPlayerState: MiniPlayerState,
    playerState: InternalPlayerState,
    onFullPlayerClick: () -> Unit,
) {
    if (miniPlayerState.visible) {
        // Render overlay
    }
}
```

---

## 📐 Architecture Position

```
Feature Layer (UI)
    ↓
PlayerEntryPoint (playback/domain)
    ↓
player/internal (engine) ← YOU ARE HERE
    ├── InternalPlayerSession (ExoPlayer lifecycle)
    ├── PlaybackSourceResolver (factory injection)
    ├── SubtitleTrackManager (track selection)
    ├── AudioTrackManager (track selection)
    └── MiniPlayerManager (in-app overlay)
    ↓
PlaybackSourceFactory (playback/*)
    ├── TelegramPlaybackSourceFactoryImpl
    ├── XtreamPlaybackSourceFactoryImpl
    └── Future sources... 
    ↓
DataSource (playback/*)
    ├── TelegramFileDataSource
    ├── XtreamHttpDataSource
    └── FileDataSource (fallback)
    ↓
ExoPlayer/Media3
```

---

## 🔍 Pre-Change Verification

```bash
# 1. No forbidden imports in player layer
grep -rn "import.*pipeline\|import.*infra\.transport\|import.*infra\.data" player/

# 2. No source-specific code in player
grep -rn "TelegramMediaItem\|XtreamVodItem\|TgMessage" player/

# 3. No Hilt EntryPoints in player/ui
grep -rn "dagger\.hilt\.EntryPoint\|@EntryPoint\|EntryPointAccessors" player/ui/

# 4. No engine wiring classes in player/ui
grep -rn "\bPlaybackSourceResolver\b\|\bResumeManager\b\|\bKidsPlaybackGate\b" player/ui/

# 5. No com.fishit.player.internal. * imports from other player modules
grep -rn "import com\.fishit\.player\.internal\." player/ | grep -v "player/internal/src" | grep -v "import com\.fishit\.player\.internal\.ui\."

# All should return empty! 
```

---

## ✅ PLATIN Checklist

### Common (All Player Modules)
- [ ] Source-agnostic (no Telegram/Xtream imports)
- [ ] NO pipeline imports
- [ ] NO transport imports (use abstractions)
- [ ] NO data layer imports (use repositories)
- [ ] Uses PlaybackContext exclusively
- [ ] Uses UnifiedLog for logging
- [ ] Proper StateFlow usage for state management

### player/internal Specific
- [ ] `InternalPlayerSession` uses injected abstractions only
- [ ] `PlaybackSourceResolver` injects `Set<PlaybackSourceFactory>`
- [ ] DataSource.Factory map injected via `PlayerDataSourceModule`
- [ ] Resume via `ResumeManager` interface
- [ ] Kids gate via `KidsPlaybackGate` interface
- [ ] Subtitle/Audio managers attach to ExoPlayer events
- [ ] Position updates save resume periodically
- [ ] NO source-specific logic in session

### player/miniplayer Specific
- [ ] `MiniPlayerState` is immutable
- [ ] `MiniPlayerManager` uses StateFlow
- [ ] Return navigation context stored (route, row, item indices)
- [ ] Fullscreen ↔ MiniPlayer transitions work
- [ ] Snap to anchors on drag end
- [ ] NO ExoPlayer direct access (uses player state)

### player/ui Specific
- [ ] NO Hilt EntryPoints
- [ ] NO engine wiring class imports
- [ ] Uses `@HiltViewModel` with constructor injection
- [ ] Uses `PlayerEntryPoint` abstraction
- [ ] NO `com.fishit.player.internal.*` imports from other modules

### player/nextlib-codecs Specific
- [ ] Provides `NextlibCodecConfigurator` interface
- [ ] NO source-specific code
- [ ] GPL-3.0 license documented

---

## 📚 Reference Documents (Priority Order)

1. **`/AGENTS.md`** - Section 13 "Player Layer Isolation" (CRITICAL)
2. **`/docs/v2/internal-player/PLAYER_ARCHITECTURE_V2.md`** - Complete architecture
3. **`/docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md`** - Phase status
4. **`/docs/dev/ARCH_GUARDRAILS.md`** - Layer boundary enforcement
5. **`/contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md`** - Behavior contract
6. **`/contracts/INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md`** - MiniPlayer contract
7. **`/player/internal/README.md`** - Module-specific rules
8. **`/player/miniplayer/README.md`** - MiniPlayer specifics

---

## 🚨 Common Violations & Solutions

### Violation 1: Source-Specific Code in Player

```kotlin
// ❌ WRONG
if (context.sourceType == SourceType.TELEGRAM) {
    val fileId = context.extras["fileId"]?. toIntOrNull()
    // Telegram-specific logic
}

// ✅ CORRECT
val source = sourceResolver.resolve(context)  // Factory handles specifics
val mediaItem = buildMediaItem(source)
```

### Violation 2: Hilt EntryPoint in player/ui

```kotlin
// ❌ WRONG
@Composable
fun PlayerScreen(context: Context) {
    val entryPoint = EntryPointAccessors.fromApplication(
        context,
        PlayerServiceEntryPoint::class.java
    )
    val session = entryPoint.getPlayerSession()
}

// ✅ CORRECT
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerEntry: PlayerEntryPoint,
) : ViewModel()

@Composable
fun PlayerScreen(viewModel: PlayerViewModel = hiltViewModel()) {
    // Access via ViewModel
}
```

### Violation 3: Direct Repository Access

```kotlin
// ❌ WRONG
@Inject constructor(
    private val canonicalMediaRepository: CanonicalMediaRepository,
)

// ✅ CORRECT
@Inject constructor(
    private val resumeManager: ResumeManager,  // Abstraction
)
```

### Violation 4: Pipeline Imports

```kotlin
// ❌ WRONG
import com.fishit.player.pipeline.telegram.TelegramMediaItem

// ✅ CORRECT
import com.fishit.player.core.playermodel.PlaybackContext
```

---

**End of PLATIN Instructions for player/***