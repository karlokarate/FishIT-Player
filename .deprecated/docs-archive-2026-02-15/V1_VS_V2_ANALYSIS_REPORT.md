# v1 vs v2 Documentation Analysis Report

> **Date:** 2025-12-04 (Updated: 2025-12-05)  
> **Purpose:** Analyze v1 implementation (code + docs) to detect mismatches, errors, or unclear parts in v2 markdowns.  
> **Scope:** ObjectBox vs Room, tdlib-coroutines (g00sha), reusable v1 components, proposed doc corrections.  
> **Extended Scope:** SIP Player (Phase 1-8), Logging System, AppImageLoader, Xtream Pipeline, UI Components

---

## ⚡ How to Use This Document

> **This is the master porting reference for v2 development.**  
> It is referenced from:
> - `AGENTS.md` (global repo rules)
> - `AGENTS_V2.md` (v2 execution guide)
> - `ARCHITECTURE_OVERVIEW_V2.md`
> - `IMPLEMENTATION_PHASES_V2.md`
> - `APP_VISION_AND_SCOPE.md`
>
> **Before implementing any v2 module:**
> 1. Check **Section 0** (Tier 1/2 classification) for existing v1 quality
> 2. Consult **Appendix A** for complete file mapping with target v2 modules
> 3. Review **Appendix C** for phase-specific contracts (Phase 4-8)
>
> **Do NOT rewrite Tier 1 systems.** Port them with minimal changes.

---

## Executive Summary

After analyzing the v1 codebase and comparing it against the v2 documentation, this report identifies:

1. **2 Critical Mismatches** - v2 docs incorrectly state "Room or equivalent" when v1 uses **ObjectBox exclusively** ✅ FIXED
2. **Reusable v1 Components** - Classified as MUST/SHOULD/MUST NOT reuse
3. **Proposed Exact Edits** - Line-by-line corrections for all three v2 docs ✅ APPLIED
4. **NEW: SIP Player Architecture** - 9-phase refactored internal player, production-ready for v2
5. **NEW: Unified Logging System** - UnifiedLog with ring buffer, filtering, Firebase integration
6. **NEW: Production-Quality Components** - FocusKit, Fish* Layout System, DetailScaffold, MediaActionBar

---

## 0. v1 Quality Assessment: What's Already Excellent

The v1 codebase contains several **production-quality subsystems** that should be ported to v2 with minimal changes. These represent months of refinement and should NOT be rewritten:

### 🏆 Tier 1: Port Directly (Zero Changes Needed)

| System | Quality | Lines | Why Excellent |
|--------|---------|-------|---------------|
| **SIP Player (Phase 1-8)** | ⭐⭐⭐⭐⭐ | ~5000+ | 9-phase modular refactor, contract-driven, 150+ tests |
| **UnifiedLog** | ⭐⭐⭐⭐⭐ | 578 | Ring buffer, filtering, Firebase Crashlytics, file export |
| **FocusKit** | ⭐⭐⭐⭐⭐ | 1353 | Central TV/DPAD focus facade, FocusZones, performance-tuned |
| **Fish* Layout System** | ⭐⭐⭐⭐⭐ | ~2000 | 14 composable files, token-based theming, TV-first |
| **Xtream Pipeline** | ⭐⭐⭐⭐⭐ | ~3000 | XtreamClient, Seeder, Delta-Import, per-host pacing |
| **AppImageLoader** | ⭐⭐⭐⭐⭐ | 153 | Coil 3, 256MB disk cache, Telegram thumb fetcher |

### 🥈 Tier 2: Port with Minor Adaptation

| System | Quality | Adaptation Needed |
|--------|---------|-------------------|
| **PlaybackSession** | ⭐⭐⭐⭐ | Already shares player across Full↔MiniPlayer |
| **DetailScaffold** | ⭐⭐⭐⭐ | Unified VOD/Series/Live detail headers |
| **MediaActionBar** | ⭐⭐⭐⭐ | Centralized CTAs (Play/Resume/Trailer) |
| **TvButtons** | ⭐⭐⭐⭐ | TV focus glow + scale animations |
| **MiniPlayerManager** | ⭐⭐⭐⭐ | In-app PiP with resize/snap anchors |
| **HomeChromeScaffold** | ⭐⭐⭐⭐ | TV chrome with auto-collapse/expand |

---

## 1. Critical Mismatches

### 1.1 Database Technology: Room vs ObjectBox

#### v2 Doc Statements (INCORRECT)

| File | Line | Current Text |
|------|------|--------------|
| `APP_VISION_AND_SCOPE.md` | 175 | `A local database (Room or equivalent) for structured data, reusing v1 where sensible` |
| `ARCHITECTURE_OVERVIEW_V2.md` | 60 | `Local DB (Room or equivalent)` |

#### v1 Reality (CORRECT)

**ObjectBox is the ONLY local database in v1.** Room has been completely removed.

**Evidence from v1 codebase:**

```kotlin
// data/obx/ObxStore.kt - Singleton wrapper
class ObxStore private constructor(private val boxStore: BoxStore) {
    companion object {
        @Volatile private var instance: ObxStore? = null
        fun get(context: Context): BoxStore = instance?.boxStore ?: ...
    }
}

// data/obx/ObxEntities.kt - Entity definitions
@Entity data class ObxCategory(...)
@Entity data class ObxLive(...)
@Entity data class ObxVod(...)
@Entity data class ObxSeries(...)
@Entity data class ObxEpisode(...)
@Entity data class ObxEpgNowNext(...)
@Entity data class ObxProfile(...)
@Entity data class ObxProfilePermissions(...)
@Entity data class ObxTelegramMessage(...)
@Entity data class ObxResumeMark(...)
```

**Files using ObjectBox (20+ files):**
- `data/obx/ObxStore.kt` - Core BoxStore singleton
- `data/obx/ObxEntities.kt` - All entity definitions
- `data/repo/XtreamObxRepository.kt` - Xtream content queries
- `data/repo/ResumeRepository.kt` - Resume marks using `ObxResumeMark`
- `data/repo/ProfileObxRepository.kt` - Profile storage
- `work/ObxKeyBackfillWorker.kt` - Background key updates
- `player/datasource/RarDataSource.kt` - Uses `ObxTelegramMessage`

**AGENTS.md explicitly states:**
> "ObjectBox primary store: ObjectBox is the primary local store for content (categories, live, vod, series, episodes, epg_now_next). Telegram metadata is now stored in ObjectBox as well (`ObxTelegramMessage`). Room has been removed from app flows."

#### Required Fix

Both v2 docs must be updated to explicitly state **ObjectBox** instead of "Room or equivalent".

---

## 2. Reusable v1 Components Classification

### 2.1 MUST Reuse (Direct Port)

These components are production-tested, critical infrastructure that must be ported to v2 modules:

| Component | v1 Location | v2 Target Module | Rationale |
|-----------|-------------|------------------|-----------|
| `T_TelegramServiceClient` | `telegram/core/T_TelegramServiceClient.kt` | `:pipeline:telegram` | Core TDLib integration with auth states, connection states, sync states |
| `T_TelegramFileDownloader` | `telegram/core/T_TelegramFileDownloader.kt` | `:pipeline:telegram` | Download queue management, priority system, progress tracking (1621 lines) |
| `TelegramFileDataSource` | `telegram/player/TelegramFileDataSource.kt` | `:pipeline:telegram` | Zero-copy Media3 DataSource for `tg://` URLs (413 lines) |
| `DelegatingDataSourceFactory` | `player/datasource/DelegatingDataSourceFactory.kt` | `:player:internal` or `:pipeline:xtream` | Routes URLs to correct DataSource based on scheme (`tg://`, `rar://`, `http://`) |
| `RarDataSource` | `player/datasource/RarDataSource.kt` | `:pipeline:telegram` | RAR archive streaming for Telegram (with ObjectBox lookup) |
| `ObxStore` | `data/obx/ObxStore.kt` | `:core:persistence` | BoxStore singleton pattern |
| `ObxEntities` | `data/obx/ObxEntities.kt` | `:core:persistence` | Entity definitions for content, profiles, resume marks |

### 2.2 SHOULD Reuse (Adapt & Port)

These components work well but may need interface updates for v2 architecture:

| Component | v1 Location | v2 Target Module | Adaptation Needed |
|-----------|-------------|------------------|-------------------|
| `ResumeRepository` | `data/repo/ResumeRepository.kt` | `:core:persistence` impl for `ResumeManager` | Implement v2 `ResumeManager` interface using OBX queries |
| `XtreamObxRepository` | `data/repo/XtreamObxRepository.kt` | `:pipeline:xtream` | Port query methods, adapt to v2 interfaces |
| `TelegramStreamingSettingsProvider` | `telegram/domain/TelegramStreamingSettingsProvider.kt` | `:pipeline:telegram` | Keep interface, port implementation |
| `T_TelegramSession` | `telegram/core/T_TelegramSession.kt` | `:pipeline:telegram` | Session management for TDLib |
| `ConfigLoader` / `AppConfig` | `telegram/config/*` | `:pipeline:telegram` | Telegram configuration loading |
| `Mp4HeaderParser` | `telegram/util/Mp4HeaderParser.kt` | `:pipeline:telegram` | MP4 validation before playback |
| `StreamingConfigRefactor` | `telegram/core/StreamingConfigRefactor.kt` | `:pipeline:telegram` | Streaming constants and configuration |

### 2.3 MUST NOT Reuse (v1-Specific, Deprecated)

These components are v1-specific or deprecated and should not be ported:

| Component | v1 Location | Reason |
|-----------|-------------|--------|
| `WindowState` | `T_TelegramFileDownloader.kt` | Deprecated - marked with `@Deprecated`, custom windowing removed |
| Room DAOs/Entities | `data/room/*` (if any remain) | Room is removed; ObjectBox is the only DB |
| `InternalPlayerScreen` (v1) | `player/InternalPlayerScreen.kt` | v2 has new `InternalPlayerEntry` composable |
| Legacy Compose Navigation | `ui/navigation/*` | v2 uses new navigation architecture |

---

## 3. tdlib-coroutines (g00sha) Integration Analysis

### v2 Docs Correctly Reference

Both `APP_VISION_AND_SCOPE.md` and `ARCHITECTURE_OVERVIEW_V2.md` correctly mention:
- "tdlib-coroutines (g00sha) for Telegram API integration"
- `:pipeline:telegram` containing "tdlib-coroutines integration"

### v1 Implementation Details

The v1 uses `dev.g000sha256.tdl` package extensively:

```kotlin
// T_TelegramServiceClient.kt
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*

// T_TelegramFileDownloader.kt
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.File
```

### Recommendation

Add explicit mention in `ARCHITECTURE_OVERVIEW_V2.md` that the v1 Telegram core components (`T_TelegramServiceClient`, `T_TelegramFileDownloader`, `TelegramFileDataSource`) should be **directly ported** to `:pipeline:telegram`.

---

## 4. Proposed Exact Edits to v2 Docs

### 4.1 APP_VISION_AND_SCOPE.md

#### Edit 1: Line 175 - Replace Room with ObjectBox

**OLD:**
```markdown
  - A local database (Room or equivalent) for structured data, reusing v1 where sensible
```

**NEW:**
```markdown
  - A local database (ObjectBox) for structured data, directly reusing v1 ObjectBox entities and store pattern
```

### 4.2 ARCHITECTURE_OVERVIEW_V2.md

#### Edit 1: Line 60 - Replace Room with ObjectBox

**OLD:**
```markdown
    - Local DB (Room or equivalent)
```

**NEW:**
```markdown
    - Local DB (ObjectBox - reused from v1)
```

#### Edit 2: Lines 64-70 - Add ObjectBox-specific details

**OLD:**
```markdown
  - Repository interfaces and/or implementations for:
    - `ProfileRepository`
    - `EntitlementRepository`
    - `LocalMediaRepository` (for generic IO content)
    - `SubtitleStyleStore` (persistence for subtitle style preferences)
    - Cached feature flags / entitlements
```

**NEW:**
```markdown
  - Repository interfaces and/or implementations for:
    - `ProfileRepository` (backed by `ObxProfile`, `ObxProfilePermissions`)
    - `EntitlementRepository`
    - `LocalMediaRepository` (for generic IO content)
    - `SubtitleStyleStore` (persistence for subtitle style preferences)
    - `ResumeRepository` (backed by `ObxResumeMark`)
    - Cached feature flags / entitlements
  - ObjectBox entities ported from v1:
    - `ObxCategory`, `ObxLive`, `ObxVod`, `ObxSeries`, `ObxEpisode`
    - `ObxEpgNowNext`, `ObxProfile`, `ObxProfilePermissions`
    - `ObxResumeMark`, `ObxTelegramMessage`
```

#### Edit 3: Lines 161-168 - Add explicit v1 component reuse list for :pipeline:telegram

After the existing content, add:

**ADD after line 168:**
```markdown
  - **Ported from v1** (MUST reuse):
    - `T_TelegramServiceClient` - Core TDLib integration (auth, connection, sync states)
    - `T_TelegramFileDownloader` - Download queue with priority system
    - `TelegramFileDataSource` - Zero-copy Media3 DataSource for `tg://` URLs
    - `TelegramStreamingSettingsProvider` - Streaming configuration
    - `Mp4HeaderParser` - MP4 validation before playback
```

#### Edit 4: Lines 175-183 - Add explicit v1 component reuse list for :pipeline:xtream

After the existing content, add:

**ADD after line 183:**
```markdown
  - **Ported from v1** (MUST reuse):
    - `DelegatingDataSourceFactory` - Routes URLs to correct DataSource by scheme
    - `RarDataSource` - RAR archive entry streaming
    - `XtreamObxRepository` - ObjectBox-backed content queries (adapted to v2 interfaces)
```

### 4.3 IMPLEMENTATION_PHASES_V2.md

No explicit Room references found, but recommend adding a note in Phase 2:

#### Edit 1: Add ObjectBox note in Phase 2 (after Telegram integration mention)

**ADD in Phase 2 section:**
```markdown
- **ObjectBox Reuse:**
  - Port `ObxStore` singleton pattern to `:core:persistence`
  - Reuse v1 entity definitions (`ObxEntities.kt`) directly
  - Adapt `ResumeRepository`, `ProfileObxRepository` to implement v2 interfaces
```

---

## 5. Summary Table

| Category | Count | Details |
|----------|-------|---------|
| Critical Mismatches | 2 | Room vs ObjectBox (both docs) ✅ FIXED |
| Tier 1 Systems (Port Directly) | 6 | SIP Player, UnifiedLog, FocusKit, Fish*, Xtream, AppImageLoader |
| Tier 2 Systems (Minor Adapt) | 6 | PlaybackSession, DetailScaffold, MediaActionBar, TvButtons, MiniPlayer, HomeChrome |
| MUST Reuse Components | 7 | T_TelegramServiceClient, T_TelegramFileDownloader, TelegramFileDataSource, DelegatingDataSourceFactory, RarDataSource, ObxStore, ObxEntities |
| SHOULD Reuse Components | 7 | ResumeRepository, XtreamObxRepository, TelegramStreamingSettingsProvider, T_TelegramSession, ConfigLoader, Mp4HeaderParser, StreamingConfigRefactor |
| MUST NOT Reuse | 4 | WindowState, Room DAOs, InternalPlayerScreen (v1 monolith), Legacy Navigation |
| Proposed Doc Edits | 6 | 1 in APP_VISION, 4 in ARCH_OVERVIEW, 1 in IMPL_PHASES ✅ APPLIED |

---

## 6. Action Items

1. ~~**IMMEDIATE:** Apply the 2 Room→ObjectBox edits~~ ✅ DONE
2. ~~**IMMEDIATE:** Add v1 component reuse lists to `ARCHITECTURE_OVERVIEW_V2.md`~~ ✅ DONE
3. **PHASE 1:** Port SIP Player modules to `:player:internal` (structure already exists)
4. **PHASE 1:** Port UnifiedLog to `:infra:logging`
5. **PHASE 2:** Port Telegram core files to `:pipeline:telegram`
6. **PHASE 3:** Port Xtream pipeline to `:pipeline:xtream`
7. **ONGOING:** Port FocusKit and Fish* to shared UI module

---

## 7. NEW: SIP Player Architecture (Phase 1-8 Complete)

The v1 Internal Player has been completely refactored from a **2568-line monolith** to a **modular SIP architecture**. This is production-ready for v2.

### 7.1 SIP Module Structure

```
player/internal/
├── bridge/                    # Legacy bridge layer
├── domain/                    # Pure domain models
│   ├── PlaybackContext.kt     # Typed playback session descriptor
│   ├── PlaybackType.kt        # VOD, SERIES, LIVE enum
│   ├── ResumeManager.kt       # Resume position abstraction
│   ├── KidsPlaybackGate.kt    # Kids/screen-time enforcement
│   └── Default*.kt            # Default implementations
├── state/
│   └── InternalPlayerState.kt # Immutable UI state model
├── session/
│   └── InternalPlayerSession.kt # ExoPlayer lifecycle (1393 lines)
├── source/
│   └── PlaybackSourceResolver.kt # URL/MIME resolution
├── live/
│   ├── LivePlaybackController.kt # Channel navigation
│   ├── DefaultLivePlaybackController.kt
│   ├── LiveChannel.kt
│   ├── EpgOverlayState.kt
│   └── LiveMetrics.kt
├── subtitles/
│   ├── SubtitleStyleManager.kt
│   ├── SubtitleSelectionPolicy.kt
│   └── SubtitleStyle.kt
├── ui/
│   ├── InternalPlayerControls.kt
│   ├── PlayerSurface.kt
│   └── CcMenuDialog.kt
├── shadow/                    # Transition/animation helpers
└── system/
    └── InternalPlayerSystemUi.kt
```

### 7.2 Phase Completion Status

| Phase | Feature | Status | Lines/Tests |
|-------|---------|--------|-------------|
| **Phase 1** | PlaybackContext & Entry Point | ✅ COMPLETE | Contract-driven |
| **Phase 2** | Resume & Kids Gate | ✅ COMPLETE | 150+ tests |
| **Phase 3** | Live-TV Controller | ✅ COMPLETE | 68+ controller tests |
| **Phase 4** | Subtitles/CC Menu | ✅ COMPLETE | 95 subtitle tests |
| **Phase 5** | PlayerSurface & Trickplay | ✅ COMPLETE | Black bars, aspect ratio |
| **Phase 6** | TV Input System | ✅ COMPLETE | TvKeyRole, FocusZones |
| **Phase 7** | PlaybackSession & MiniPlayer | ✅ COMPLETE | Shared player singleton |
| **Phase 8** | Performance & Lifecycle | ✅ COMPLETE | Warm resume, worker throttle |

### 7.3 v2 Integration Strategy

The SIP modules are **already structured for v2**:
- `player/internal/domain/` → `:playback:domain` interfaces
- `player/internal/session/` → `:player:internal` ExoPlayer management
- `player/internal/ui/` → `:player:internal` Compose UI
- `player/internal/live/` → `:playback:domain` + `:pipeline:xtream` bridge

**No restructuring needed** – just move files and adjust imports.

---

## 8. NEW: Unified Logging System

### 8.1 UnifiedLog Architecture

```kotlin
// core/logging/UnifiedLog.kt (578 lines)
object UnifiedLog {
    // Ring buffer with 1000 entries max
    private val ringBuffer = ArrayDeque<Entry>(MAX_ENTRIES)
    
    // StateFlows for UI observation
    val entries: StateFlow<List<Entry>>
    val events: SharedFlow<Entry>  // Live events
    val filterState: StateFlow<FilterState>
    
    // Log levels: VERBOSE, DEBUG, INFO, WARN, ERROR
    enum class Level { ... }
    
    // Predefined categories for filtering
    enum class SourceCategory {
        PLAYBACK, TELEGRAM_DOWNLOAD, TELEGRAM_AUTH, TELEGRAM_SYNC,
        THUMBNAILS, UI_FOCUS, NETWORK, DIAGNOSTICS, APP, OTHER
    }
    
    // File buffer for session export
    fun enableFileBuffer()
    fun exportSession(): File
    
    // Firebase Crashlytics integration
    // Errors automatically forwarded
}
```

### 8.2 LogViewer UI

```kotlin
// logs/LogViewerViewModel.kt + logs/ui/LogViewerScreen.kt
- Live and historical log entries
- Level/category filtering via Settings toggles
- Export to cacheDir/applog_*.txt
- Integrates with GlobalDebug for focus/nav logging
```

### 8.3 v2 Integration

- Port to `:infra:logging` module
- Keep singleton pattern for process-wide logging
- Integrate with Crashlytics via `:core:firebase`

---

## 9. NEW: AppImageLoader

### 9.1 Architecture

```kotlin
// ui/util/AppImageLoader.kt (153 lines)
object AppImageLoader {
    fun get(context: Context): ImageLoader {
        // Coil 3 with tuned caches:
        // - Disk: 256 MiB under app cache
        // - Memory: 25% of available
        // - Telegram thumb fetcher registered
        // - Hardware bitmaps enabled
    }
    
    suspend fun preload(urls: Collection<Any?>, ...)
}
```

### 9.2 Integration with Telegram

```kotlin
// telegram/image/TelegramThumbFetcher.kt
// Custom Coil Fetcher for tg-thumb:// URLs
// Zero-copy integration with T_TelegramFileDownloader
```

---

## 10. NEW: Xtream Pipeline (Production-Ready)

### 10.1 Components

| Component | Location | Lines | Description |
|-----------|----------|-------|-------------|
| **XtreamClient** | `core/xtream/XtreamClient.kt` | 903 | API client with rate limiting, caching |
| **XtreamSeeder** | `core/xtream/XtreamSeeder.kt` | 147 | Initial content seeding (200/category) |
| **XtreamObxRepository** | `data/repo/XtreamObxRepository.kt` | ~1000 | ObjectBox queries, delta import |
| **CapabilityDiscoverer** | `core/xtream/CapabilityDiscoverer.kt` | - | Port detection, panel caps |
| **XtreamDeltaImportWorker** | `work/XtreamDeltaImportWorker.kt` | - | Periodic 12h refresh |

### 10.2 Key Features

- **Per-host pacing:** 120ms minimum interval between calls
- **In-memory cache:** 60s TTL (15s for EPG)
- **Parallel limiting:** Semaphore(4) for EPG fetches
- **Delta import:** Updates without orphan pruning
- **Aggregate indexes:** ObxIndexProvider/Year/Genre for fast grouping

### 10.3 v2 Target

Port entire `core/xtream/` to `:pipeline:xtream` with these changes:
- Replace `SettingsStore` with v2 `EntitlementRepository`
- Adapt to v2 `XtreamCatalogRepository` interface
- Keep ObjectBox entities in `:core:persistence`

---

## 11. NEW: Fish* Layout System

### 11.1 Component Inventory

```
ui/layout/
├── FishTheme.kt        # Token provider (FishDimens)
├── FishTile.kt         # Unified tile with focus/reflection
├── FishRow.kt          # FishRowLight, FishRow, FishRowPaged
├── FishHeader.kt       # Floating beacon overlay headers
├── FishVodContent.kt   # VOD tile composition
├── FishSeriesContent.kt
├── FishLiveContent.kt
├── FishTelegramContent.kt
├── FishForm.kt         # TV-first form components
├── FishActions.kt      # Assign/Play action buttons
├── FishMeta.kt         # Poster/title/plot/resume helpers
├── FishLogging.kt      # Focus logging with OBX resolution
├── FishResumeTile.kt   # Global resume card
└── FishMediaTiles.kt   # Media-type-specific tiles
```

### 11.2 FishTheme Tokens

```kotlin
data class FishDimens(
    val tileWidthDp: Dp = 180.dp,
    val tileHeightDp: Dp = 270.dp,
    val tileCornerDp: Dp = 14.dp,
    val tileSpacingDp: Dp = 12.dp,
    val focusScale: Float = 1.10f,
    val focusBorderWidthDp: Dp = 2.5.dp,
    val reflectionAlpha: Float = 0.18f,
    val enableGlow: Boolean = true,
    val showTitleWhenUnfocused: Boolean = false,
)
```

### 11.3 v2 Integration

Create `:ui:fish` or `:ui:layout` module containing:
- All Fish* composables
- FocusKit dependency for TV focus
- FishTheme tokens as CompositionLocal

---

## 12. NEW: FocusKit (TV Focus Facade)

### 12.1 Core Features

```kotlin
// ui/focus/FocusKit.kt (1353 lines)
object FocusKit {
    // Focus zones for programmatic navigation
    enum class FocusZoneId {
        PLAYER_CONTROLS, QUICK_ACTIONS, TIMELINE, CC_BUTTON,
        LIVE_OVERLAY, EPG, DEBUG_PANEL, MINIPLAYER, ...
    }
    
    // Modifier extensions
    fun Modifier.tvClickable(...)      // Click + focus handling
    fun Modifier.tvFocusFrame(...)     // Visual focus indicator
    fun Modifier.tvFocusableItem(...)  # Focusable with logging
    fun Modifier.focusGroup()          // Container for DPAD nav
    
    // Row wrappers
    @Composable fun TvRowLight(...)
    @Composable fun TvRowMedia(...)
    @Composable fun TvRowPaged(...)
    
    // DPAD helpers
    fun onDpadAdjustLeftRight(...)
    fun onDpadAdjustUpDown(...)
    
    // Focus neighbors
    fun focusNeighbors(...)
    
    // TV button re-exports
    @Composable fun TvButton(...)
    @Composable fun TvTextButton(...)
    @Composable fun TvOutlinedButton(...)
    @Composable fun TvIconButton(...)
}
```

### 12.2 v2 Integration

- Port to `:ui:focus` or keep in `:player:internal` for TV controls
- Essential for all TV screens
- Works with Fish* layout system

---

## Appendix A: v1 File Locations for Porting (Extended)

```
# ══════════════════════════════════════════════════════════════════════════════
# TIER 1: PORT DIRECTLY (Zero Changes)
# ══════════════════════════════════════════════════════════════════════════════

# SIP Player (37+ files)
player/internal/
├── domain/                           → :playback:domain
│   ├── PlaybackContext.kt
│   ├── PlaybackType.kt
│   ├── ResumeManager.kt
│   ├── KidsPlaybackGate.kt
│   └── Default*.kt
├── state/InternalPlayerState.kt      → :player:internal
├── session/InternalPlayerSession.kt  → :player:internal (1393 lines)
├── source/PlaybackSourceResolver.kt  → :player:internal
├── live/                             → :playback:domain + :pipeline:xtream
│   ├── LivePlaybackController.kt
│   ├── DefaultLivePlaybackController.kt
│   ├── LiveChannel.kt
│   └── EpgOverlayState.kt
├── subtitles/                        → :playback:domain
│   ├── SubtitleStyleManager.kt
│   ├── SubtitleSelectionPolicy.kt
│   └── SubtitleStyle.kt
├── ui/                               → :player:internal
│   ├── InternalPlayerControls.kt
│   ├── PlayerSurface.kt
│   └── CcMenuDialog.kt
└── system/InternalPlayerSystemUi.kt  → :player:internal

# Unified Logging (578 lines)
core/logging/
├── UnifiedLog.kt                     → :infra:logging
└── ...

logs/
├── LogViewerViewModel.kt             → :infra:logging or :feature:settings
└── ui/LogViewerScreen.kt             → :feature:settings

# FocusKit (1353 lines)
ui/focus/
├── FocusKit.kt                       → :ui:focus or :player:internal
├── FocusColors.kt
└── ...

# Fish* Layout System (~2000 lines)
ui/layout/
├── FishTheme.kt                      → :ui:layout
├── FishTile.kt                       → :ui:layout
├── FishRow.kt                        → :ui:layout
├── FishHeader.kt                     → :ui:layout
├── FishVodContent.kt                 → :ui:layout
├── FishSeriesContent.kt              → :ui:layout
├── FishLiveContent.kt                → :ui:layout
├── FishTelegramContent.kt            → :ui:layout
├── FishForm.kt                       → :ui:layout
├── FishActions.kt                    → :ui:layout
├── FishMeta.kt                       → :ui:layout
├── FishLogging.kt                    → :ui:layout
├── FishResumeTile.kt                 → :ui:layout
└── FishMediaTiles.kt                 → :ui:layout

# Xtream Pipeline (~3000 lines)
core/xtream/
├── XtreamClient.kt                   → :pipeline:xtream (903 lines)
├── XtreamSeeder.kt                   → :pipeline:xtream
├── XtreamConfig.kt                   → :pipeline:xtream
├── XtreamCapabilities.kt             → :pipeline:xtream
├── CapabilityDiscoverer.kt           → :pipeline:xtream
├── EndpointPortStore.kt              → :pipeline:xtream
├── ProviderCapabilityStore.kt        → :pipeline:xtream
└── XtreamUrlFactory.kt               → :pipeline:xtream

data/repo/XtreamObxRepository.kt      → :pipeline:xtream

# AppImageLoader
ui/util/
├── AppImageLoader.kt                 → :core:image (153 lines)
├── Images.kt                         → :core:image
└── ImageHeaders.kt                   → :core:image

# ══════════════════════════════════════════════════════════════════════════════
# TIER 2: PORT WITH MINOR ADAPTATION
# ══════════════════════════════════════════════════════════════════════════════

# PlaybackSession
playback/
├── PlaybackSession.kt                → :player:internal (588 lines)
├── PlaybackSessionController.kt      → :playback:domain
└── PlaybackLifecycleController.kt    → :player:internal

# MiniPlayer
player/miniplayer/
├── MiniPlayerManager.kt              → :player:internal (519 lines)
├── MiniPlayerOverlay.kt              → :player:internal
├── MiniPlayerState.kt                → :player:internal
└── MiniPlayerResizeActionHandler.kt  → :player:internal

# Detail UI
ui/detail/
├── DetailScaffold.kt                 → :ui:detail
├── DetailHeader.kt                   → :ui:detail
├── DetailHeaderExtras.kt             → :ui:detail
├── MetaChips.kt                      → :ui:detail
├── DetailBackdrop.kt                 → :ui:detail
├── DetailFacts.kt                    → :ui:detail
├── DetailPage.kt                     → :ui:detail
├── DetailSections.kt                 → :ui:detail
├── HeroScrim.kt                      → :ui:detail
└── SeriesDetailMask.kt               → :ui:detail

# Media Actions
ui/actions/
├── MediaAction.kt                    → :ui:actions
├── MediaActionBar.kt                 → :ui:actions
└── MediaActionDefaults.kt            → :ui:actions

# TV Buttons
ui/common/
├── TvButtons.kt                      → :ui:common (145 lines)
├── TvEmptyState.kt                   → :ui:common
├── CardKit.kt                        → :ui:common
└── TrailerBox.kt                     → :ui:common

# HomeChrome
ui/home/
├── HomeChromeScaffold.kt             → :ui:chrome
└── ...

# ══════════════════════════════════════════════════════════════════════════════
# TELEGRAM PIPELINE (MUST PORT)
# ══════════════════════════════════════════════════════════════════════════════

telegram/core/
├── T_TelegramServiceClient.kt        → :pipeline:telegram (742 lines)
├── T_TelegramFileDownloader.kt       → :pipeline:telegram (1621 lines)
├── T_TelegramSession.kt              → :pipeline:telegram
├── StreamingConfigRefactor.kt        → :pipeline:telegram
└── ...

telegram/player/
├── TelegramFileDataSource.kt         → :pipeline:telegram (413 lines)
└── ...

telegram/domain/
├── TelegramStreamingSettingsProvider.kt → :pipeline:telegram
└── ...

telegram/image/
├── TelegramThumbFetcher.kt           → :pipeline:telegram

# ══════════════════════════════════════════════════════════════════════════════
# DATASOURCES (SHARED)
# ══════════════════════════════════════════════════════════════════════════════

player/datasource/
├── DelegatingDataSourceFactory.kt    → :player:internal or :pipeline:xtream
├── RarDataSource.kt                  → :pipeline:telegram
└── RarEntryRandomAccessSource.kt     → :pipeline:telegram

# ══════════════════════════════════════════════════════════════════════════════
# OBJECTBOX PERSISTENCE
# ══════════════════════════════════════════════════════════════════════════════

data/obx/
├── ObxStore.kt                       → :core:persistence
├── ObxEntities.kt                    → :core:persistence
│   ├── ObxCategory
│   ├── ObxLive
│   ├── ObxVod
│   ├── ObxSeries
│   ├── ObxEpisode
│   ├── ObxEpgNowNext
│   ├── ObxProfile
│   ├── ObxProfilePermissions
│   ├── ObxResumeMark
│   └── ObxTelegramMessage
└── ...

data/repo/
├── ResumeRepository.kt               → :core:persistence (ResumeManager impl)
├── ProfileObxRepository.kt           → :core:persistence
├── EpgRepository.kt                  → :pipeline:xtream
└── ...
```

---

## Appendix B: v2 Module Mapping Summary

| v2 Module | v1 Source | Lines | Priority |
|-----------|-----------|-------|----------|
| `:playback:domain` | player/internal/domain/, subtitles/ | ~500 | P1 |
| `:player:internal` | player/internal/session/, ui/, system/ | ~3000 | P1 |
| `:infra:logging` | core/logging/, logs/ | ~800 | P1 |
| `:pipeline:telegram` | telegram/core/, player/, domain/, image/ | ~3500 | P2 |
| `:pipeline:xtream` | core/xtream/, data/repo/Xtream* | ~2500 | P3 |
| `:core:persistence` | data/obx/, data/repo/*Repository | ~1500 | P1 |
| `:ui:layout` | ui/layout/Fish* | ~2000 | P2 |
| `:ui:focus` | ui/focus/FocusKit* | ~1500 | P2 |
| `:ui:detail` | ui/detail/* | ~1000 | P3 |
| `:ui:common` | ui/common/TvButtons, CardKit | ~300 | P2 |
| `:core:image` | ui/util/AppImageLoader, Images | ~400 | P2 |

**Total portable code:** ~17,000+ lines of production-tested v1 code

---

## Appendix C: Contract Documents Reference

All SIP Player phases have detailed behavior contracts:

| Phase | Contract Document |
|-------|-------------------|
| Phase 1-3 | `docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md` |
| Phase 4 | `docs/INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md` |
| Phase 5 | `docs/INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md` |
| Phase 6 | `docs/INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md` |
| Phase 6 | `docs/GLOBAL_TV_REMOTE_BEHAVIOR_MAP.md` |
| Phase 7 | `docs/INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md` |
| Phase 8 | `docs/INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md` |
| Roadmap | `docs/INTERNAL_PLAYER_REFACTOR_ROADMAP.md` |
| Status | `docs/INTERNAL_PLAYER_REFACTOR_STATUS.md` |
| SSOT | `docs/INTERNAL_PLAYER_REFACTOR_SSOT.md` |
