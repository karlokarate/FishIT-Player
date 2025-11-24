# Internal Player Refactor - Integration Status

## Overview

This document tracks the integration of the refactored internal player modules from the ZIP file (`tools/tdlib neu.zip`) into the FishIT-Player repository.

## Date

2025-11-24

## What Was Integrated

### Successfully Integrated Modules

The following modules were successfully extracted from the ZIP and placed into their correct package locations:

#### Domain Layer (`com.chris.m3usuite.player.internal.domain`)
- ✅ **PlaybackContext.kt** - High-level domain context for playback sessions
  - Defines `PlaybackContext` data class with playback metadata
  - Defines `PlaybackType` enum (VOD, SERIES, LIVE)
  - Decoupled from ExoPlayer, TDLib, and UI concerns

- ✅ **ResumeManager.kt** - Resume position management abstraction
  - Interface for loading and saving resume positions
  - Supports VOD and series resume logic
  - Handles periodic ticks and playback completion

- ✅ **KidsPlaybackGate.kt** - Kids mode and screen time enforcement
  - Interface for evaluating playback start based on kid profiles
  - Tracks playback time and screen time limits
  - Returns `KidsGateState` for UI feedback

#### State Layer (`com.chris.m3usuite.player.internal.state`)
- ✅ **InternalPlayerState.kt** - Core UI state for the internal player
  - Defines `InternalPlayerUiState` with playback metadata
  - Includes `AspectRatioMode` enum
  - Defines `InternalPlayerController` interface for commands

#### Source Resolution (`com.chris.m3usuite.player.internal.source`)
- ✅ **InternalPlaybackSourceResolver.kt** - Playback source resolution
  - Resolves URLs, headers, and MIME types
  - Defines `ResolvedPlaybackSource` data class
  - Includes helper function `inferMimeTypeFromFileName`

### Not Integrated (Missing Dependencies)

The following modules from the ZIP could not be integrated because they depend on infrastructure that doesn't exist in the current codebase:

- ❌ **InternalPlayerScreen.kt** - Requires `RememberPlayerController` and other missing types
- ❌ **InternalPlayerSession.kt** - Requires `RememberPlayerController`, `attachPlayer`, and other missing APIs
- ❌ **InternalPlayerControls.kt** - Requires Material Icons that aren't imported (Replay10, Forward30, etc.)
- ❌ **InternalPlayerSystemUi.kt** - Requires missing string resources and Toast APIs
- ❌ **TelegramContentRepository.kt** - Major API changes that break existing call sites
- ❌ **TelegramDetailScreen.kt** - Incompatible with current implementation
- ❌ **StreamingConfig.kt** - Different from existing version
- ❌ **T_TelegramFileDownloader.kt** - Different from existing version
- ❌ **RarDataSource.kt** - Major changes not compatible with current usage
- ❌ **ObxEntities.kt** - Only contains ObxTelegramMessage, missing other entities

### Documentation
- ✅ **InternalPlayer_Refactor_Roadmap.md** - Copied to `docs/INTERNAL_PLAYER_REFACTOR_ROADMAP.md`

## Legacy Files

No files were marked as legacy because the new InternalPlayerScreen could not be integrated to replace the existing one.

The existing `InternalPlayerScreen.kt` (2568 lines) remains as the active implementation.

## Module Layout (Current State)

```
app/src/main/java/com/chris/m3usuite/player/
  InternalPlayerScreen.kt (existing - not replaced)
  
  internal/
    domain/
      PlaybackContext.kt ✅ NEW
      ResumeManager.kt ✅ NEW
      KidsPlaybackGate.kt ✅ NEW
    
    state/
      InternalPlayerState.kt ✅ NEW
    
    source/
      InternalPlaybackSourceResolver.kt ✅ NEW
```

## Build Status

✅ **Project builds successfully** with the integrated modules.

The new domain and state modules compile without errors and are ready to be used by future refactoring work.

## Next Steps (Future Work)

According to the refactor roadmap, the following phases remain:

1. **Phase 1 (Partial)** - PlaybackContext is integrated, but InternalPlayerScreen needs updating to accept it
2. **Phase 2 (Partial)** - ResumeManager and KidsPlaybackGate are integrated but not yet used
3. **Phase 3-10** - All remaining phases (Live-TV, Subtitles, PlayerSurface, TV controls, etc.)

### Prerequisites for Completing Integration

Before the remaining modules can be integrated, the codebase needs:

1. **RememberPlayerController** - A new player lifecycle management abstraction
2. **Material Icons** - Import missing icon resources (Replay10, Forward30, PlayArrow, Pause, etc.)
3. **String Resources** - Add missing string resources referenced by new UI modules
4. **API Evolution** - Update existing InternalPlayerScreen callers to use PlaybackContext

### Recommended Approach

1. Start using the integrated domain modules (PlaybackContext, ResumeManager, KidsPlaybackGate) in the existing InternalPlayerScreen
2. Gradually extract functionality from the monolithic InternalPlayerScreen into the new architecture
3. Add missing infrastructure (RememberPlayerController, icons, resources)
4. Once infrastructure exists, integrate the remaining modules from the ZIP

## Notes

- The ZIP file contains modules from an external development environment (ChatGPT project folder)
- These modules represent a future state of the refactor, not the current state
- The integration is designed to be non-breaking: all existing functionality continues to work
- The new modules provide the foundation for future refactoring work

## SIP Reference Modules Imported (Phase 2 Follow-up - 2025-11-24)

All remaining SIP modules from `tools/tdlib neu.zip` have been imported as reference implementations.

### Fully Integrated and Compilable Modules

**UI/System Layer:**
- ✅ **InternalPlayerControls.kt** - Player controls, dialogs (speed, tracks, debug), and overlays with fallback Material Icons
- ✅ **InternalPlayerSystemUi.kt** - System UI management (back handler, screen-on, fullscreen, PiP)

**Session Layer:**
- ✅ **InternalPlayerSession.kt** - Player lifecycle management, resume handling, kids gate integration
  - Note: Sleep timer and subtitle support commented out pending API availability

**Refactored Modules (with "Refactor" suffix to avoid conflicts):**
- ✅ **StreamingConfigRefactor.kt** - Streaming configuration object
- ✅ **T_TelegramFileDownloaderRefactor.kt** - File downloader with DownloadProgressRefactor type
- ✅ **RarDataSourceRefactor.kt** - RAR archive data source (implementation stubbed pending fileDownloader API)
- ✅ **ObxEntitiesRefactor.kt** - Refactored ObxTelegramMessage entity

**Infrastructure Stubs:**
- ✅ **RememberPlayerController.kt** - Stub interface for player lifecycle (TODO: Phase 7 implementation)
- ✅ **R.string.pip_not_available** - String resource for PiP unavailable message

### Reference-Only Modules (saved as .txt)

The following modules have extensive missing dependencies and are saved as .txt files for reference:

- 📄 **InternalPlayerScreenRefactor.kt.txt** - Modular screen orchestrator
  - Requires: FishM3uSuiteAppTheme, LocalWindowSize, playerSleepTimerMinutes
  
- 📄 **TelegramContentRepositoryRefactor.kt.txt** - Refactored Telegram content repository
  - Incompatible with current ObjectBox query API

### Build Status

✅ **Project builds successfully** (`./gradlew :app:assembleDebug`)

All compilable modules are integrated and the legacy monolithic InternalPlayerScreen.kt remains as the active runtime implementation. The SIP modules serve as reference implementations for future phases (3-10) of the refactor roadmap.

### Why This Was Done

> "All SIP modules from tools/tdlib neu.zip are now available in the repo as compilable reference implementations (or .txt for incompatible modules).
The old monolithic InternalPlayerScreen and associated streaming classes remain active for runtime.
This allows future phases (3-10) of the roadmap to progressively replace legacy behaviour with modular implementations without losing any functionality."

## Files Previously in ZIP (Now Integrated)

The following files have been extracted and integrated from `tools/tdlib neu.zip`:

- InternalPlayerScreen.kt
- InternalPlayerSession.kt
- InternalPlayerControls.kt
- InternalPlayerSystemUi.kt
- InternalPlayerSourceResolver.kt (duplicate of InternalPlaybackSourceResolver.kt)
- Updated versions of TelegramContentRepository, StreamingConfig, T_TelegramFileDownloader, RarDataSource
- Updated TelegramDetailScreen.kt
- Updated ObxEntities.kt (refactored ObxTelegramMessage)

These can serve as reference implementations for the corresponding future phases of the refactor.
