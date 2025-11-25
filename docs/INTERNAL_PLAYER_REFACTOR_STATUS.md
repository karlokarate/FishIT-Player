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

## Phase 1 Completed (2025-11-24)

### What Was Done

Phase 1 of the refactor roadmap has been completed. All call sites in the application now use `PlaybackContext` to describe playback sessions, while preserving existing runtime behavior.

**Bridge Entry Point Created:**
- `InternalPlayerEntry.kt` - A Phase 1 bridge function that:
  - Accepts the new typed `PlaybackContext` model
  - Maps `PlaybackType` enum to legacy string types ("vod", "series", "live")
  - Delegates to the monolithic legacy `InternalPlayerScreen` implementation
  - Preserves all existing runtime behavior

**Call Sites Updated:**

All internal player call sites now construct and pass `PlaybackContext`:

1. **MainActivity navigation composable** (`player?url=...` route)
   - Builds `PlaybackContext` based on type parameter
   - VOD: Uses `PlaybackType.VOD` with `mediaId`
   - SERIES: Uses `PlaybackType.SERIES` with `seriesId`, `season`, `episodeNumber`, `episodeId`
   - LIVE: Uses `PlaybackType.LIVE` with `mediaId`, `liveCategoryHint`, `liveProviderHint`

2. **LiveDetailScreen** (direct call)
   - Constructs `PlaybackContext` with `PlaybackType.LIVE` and category hints
   - Replaced direct `InternalPlayerScreen` call with `InternalPlayerEntry`

3. **SeriesDetailScreen** (fallback when `openInternal` is null)
   - Constructs `PlaybackContext` with `PlaybackType.SERIES`
   - Replaced direct `InternalPlayerScreen` call with `InternalPlayerEntry`
   - Note: Fallback path is missing some series context (by design in current implementation)

4. **VodDetailScreen, TelegramDetailScreen, SeriesDetailScreen** (via `openInternal` callbacks)
   - These screens use `openInternal` callbacks that navigate via route strings
   - The navigation is handled by the MainActivity composable (above)
   - Therefore all paths now use `PlaybackContext`

### Current Architecture

```
Call Sites (VOD/SERIES/LIVE/Telegram Detail Screens)
    ↓
InternalPlayerEntry (Phase 1 Bridge)
    ↓
InternalPlayerScreen (Legacy Monolithic Implementation)
```

### Build Status

✅ **Project builds successfully** with all Phase 1 changes

### What's Next

Phase 1 establishes the foundation for future refactor phases:

- **Phase 2**: ResumeManager and KidsPlaybackGate integration (already imported, ready to wire)
- **Phase 3-10**: Progressive replacement of legacy functionality with modular SIP implementations

The typed `PlaybackContext` at all call sites means future phases can switch to the modular SIP-based orchestrator without modifying call sites.

---

## Phase 1 Final Verification (2025-11-24)

**Phase 1 is fully complete.** All player call sites now use typed PlaybackContext and route through InternalPlayerEntry. The legacy monolithic InternalPlayerScreen remains the active runtime implementation. The SIP modules serve as the reference architecture for upcoming phases 2–10.

### Verification Summary

All internal player call sites across the entire repository have been verified:

1. **MainActivity Navigation** (`player?url=...` route) - Builds PlaybackContext from route parameters for VOD/SERIES/LIVE
2. **LiveDetailScreen** - Direct InternalPlayerEntry call with LIVE context
3. **SeriesDetailScreen** - Direct InternalPlayerEntry fallback with SERIES context
4. **VodDetailScreen** - Routes via openInternal lambda through MainActivity navigation
5. **TelegramDetailScreen** - Routes via openInternal lambda through MainActivity navigation
6. **LibraryScreen** - Routes via onOpenInternal lambda through MainActivity navigation
7. **StartScreen** - Routes via onOpenInternal lambda through MainActivity navigation

**No additional call sites found.** Every path to the internal player now uses the typed PlaybackContext model.

### Build Status

✅ **`./gradlew :app:assembleDebug` completes successfully** with no errors related to Phase 1 changes.

---

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

---

## Phase 2 Preparation Started

- Integrated SIP domain modules for resume and kids gate.
- Added Phase2Stubs.kt as anchor point for upcoming integration.
- Legacy InternalPlayerScreen remains the active runtime implementation.
- No functional changes performed.

### SIP Modules Verified for Phase 2

The following modules are now in place and ready for Phase 2 integration:

**Domain Layer (`internal/domain/`):**
- `ResumeManager.kt` - Interface + `DefaultResumeManager` implementation
- `KidsPlaybackGate.kt` - Interface + `DefaultKidsPlaybackGate` implementation
- `PlaybackContext.kt` - Domain context for playback sessions

**State Layer (`internal/state/`):**
- `InternalPlayerState.kt` - Contains `InternalPlayerUiState`, `AspectRatioMode`, `InternalPlayerController`

**Session Layer (`internal/session/`):**
- `InternalPlayerSession.kt` - Session management with resume and kids gate support
- `Phase2Stubs.kt` - Anchor point for Phase 2 integration (NEW)

**Source Resolution (`internal/source/`):**
- `InternalPlaybackSourceResolver.kt` - URL resolution with `PlaybackSourceResolver` and `ResolvedPlaybackSource`

**UI/System Layers:**
- `internal/ui/InternalPlayerControls.kt` - Player controls reference
- `internal/system/InternalPlayerSystemUi.kt` - System UI management reference

### Phase 2 Status: 🔄 **PREPARATION COMPLETE**

All SIP modules for resume and kids gate are integrated, verified, and ready for use.
The `Phase2Stubs.kt` anchor file provides a stable integration point for upcoming work.
No runtime changes have been made - legacy `InternalPlayerScreen` remains the active implementation.

---

## Phase 2 – Resume & Kids Gate (Preparation Started)

- Verified that Phase 2 SIP abstractions (ResumeManager, KidsPlaybackGate, etc.) are imported and compile.
- Documented parity expectations between legacy InternalPlayerScreen behavior and modular implementations.
- Added Phase2Integration.kt as stable integration anchor for resume and kids logic.
- Legacy InternalPlayerScreen remains the active runtime implementation.
- No runtime behavior changes have been made yet.

### Legacy Behavior Mapping

The following legacy behaviors in `InternalPlayerScreen.kt` have been mapped to Phase 2 abstractions:

**Resume Handling:**
| Legacy Location | Legacy Behavior | Phase 2 Abstraction |
|-----------------|-----------------|---------------------|
| L572-608 | Load resume position on start, seek if >10s | `ResumeManager.loadResumePositionMs()` |
| L692-722 | Save/clear resume every ~3s | `ResumeManager.handlePeriodicTick()` |
| L636-664 | Save/clear resume on ON_DESTROY | Future: `InternalPlayerLifecycle` (Phase 8) |
| L798-806 | Clear resume on STATE_ENDED | `ResumeManager.handleEnded()` |

**Kids/Screen-Time Gate:**
| Legacy Location | Legacy Behavior | Phase 2 Abstraction |
|-----------------|-----------------|---------------------|
| L547-569 | Check kid profile & remaining time on start | `KidsPlaybackGate.evaluateStart()` |
| L725-744 | Tick usage every ~60s, block if limit reached | `KidsPlaybackGate.onPlaybackTick()` |
| L2282-2290 | Show AlertDialog when blocked | `InternalPlayerUiState.kidBlocked` |

### Integration Anchor

`Phase2Integration.kt` provides stable integration hooks that mirror legacy behavior:
- `loadInitialResumePosition()` - Resume loading on playback start
- `onPlaybackTick()` - Periodic resume save and kids gate tick
- `onPlaybackEnded()` - Clear resume on playback completion
- `evaluateKidsGateOnStart()` - Kids gate evaluation before playback

These functions are NOT called from production code paths and serve as the integration anchor for future work that will gradually move logic from the legacy screen into the modular session.

---

## Phase 2 – Step 2 Completed

- Phase2Integration.kt now mirrors all legacy behavioral segments:
  - L547–569 (KidsGate start)
  - L572–608 (Resume start)
  - L692–722 (Resume tick)
  - L725–744 (Kids tick)
  - L798–806 (Playback end)
- ResumeManager and KidsPlaybackGate contain full parity TODOs for future migration.
- No runtime behavior modified.
- All SIP modules remain reference-only.

### Detailed Behavioral Mapping in Phase2Integration.kt

Each integration function now includes:
- **Legacy Parameter Shape**: The exact parameters used by legacy InternalPlayerScreen
- **Modular Parameter Shape**: The equivalent PlaybackContext-based parameters
- **Behavioral Parity Notes**: Line-by-line correspondence to legacy code
- **Threshold Rules**: Exact threshold values (e.g., >10s for resume, <10s for near-end clear)
- **Timing Contracts**: Expected call frequency (3s for ticks, 60s for kids gate)
- **Side Effects**: What UI or player state changes are expected

### Full Parity TODOs Added

**DefaultResumeManager TODOs:**
- `>10s rule`: Only restore resume positions greater than 10 seconds
- `<10s near-end clear`: Clear resume when remaining playback is less than 10 seconds
- `Multi-ID mapping`: VOD uses mediaId, SERIES uses composite key (seriesId+season+episodeNum)
- `Periodic tick timing contract (3s)`: Called every ~3 seconds during playback
- `Duration guard`: Skip save/clear when duration is unknown or invalid
- `STATE_ENDED clear`: Clear resume unconditionally on playback end

**DefaultKidsPlaybackGate TODOs:**
- `Profile detection`: currentProfileId.first() → ObxProfile lookup
- `Kid profile type check`: prof?.type == "kid"
- `Daily quota`: remainingMinutes() returns MINUTES for quota comparison
- `Block transitions`: kidBlocked = remain <= 0; player.playWhenReady = false
- `60-second accumulation`: Tick every 60 seconds, not every 3 seconds
- `Pause/Play event interactions`: Stop accumulation during pause
- `Fail-open exception handling`: Exceptions result in allow (kidActive = false)

### Verification

- ✅ `./gradlew :app:assembleDebug` builds successfully
- ✅ Runtime flow unchanged: InternalPlayerEntry → legacy InternalPlayerScreen
- ✅ All SIP modules compile but are not executed at runtime

---

## Phase 2 – Modular Resume & Kids Gate Implemented (Not Wired)

**Date:** 2025-11-25

This phase implements the actual modular logic inside the SIP Phase 2 modules and their session integration, while keeping them unused in runtime (no changes to navigation or InternalPlayerEntry).

### What Was Done

**DefaultResumeManager Implementation (Mirrors Legacy Behavior):**

1. `loadResumePositionMs(playbackContext)` - Mirrors legacy L572-608:
   - Only restores if position > 10 seconds (legacy threshold)
   - Uses `mediaId` for VOD content
   - Uses composite series key (`seriesId` + `season` + `episodeNumber`) for SERIES content
   - Returns `null` for LIVE content
   - Returns `null` when no stored resume entry exists or stored position ≤ 10 seconds

2. `handlePeriodicTick(playbackContext, positionMs, durationMs)` - Mirrors legacy L692-722:
   - Assumes caller invokes at ~3s intervals (does not re-implement timing)
   - Saves resume position for VOD/SERIES
   - Clears resume when remaining duration < 10 seconds
   - No-op for LIVE content
   - Guards against invalid duration (duration ≤ 0)

3. `handleEnded(playbackContext)` - Mirrors legacy L798-806:
   - Clears resume for VOD/SERIES when playback fully ends (STATE_ENDED)
   - No-op for LIVE content

**DefaultKidsPlaybackGate Implementation (Mirrors Legacy Behavior):**

1. `evaluateStart()` - Mirrors legacy L547-569 "KidsGate start":
   - Looks up `currentProfileId` → `ObxProfile`
   - Determines `isKid` via `profile?.type == "kid"`
   - Initializes remaining daily quota based on `ScreenTimeRepository`
   - Fail-open behavior: On exceptions or missing profile, returns safe state (`kidActive = false`)

2. `onPlaybackTick(currentState, deltaSecs)` - Mirrors legacy L725-744:
   - Assumes caller passes exact `deltaSecs` intervals (e.g., 60 seconds)
   - Decrements remaining daily quota using `ScreenTimeRepository.tickUsageIfPlaying`
   - If quota ≤ 0: Returns state indicating kid playback must be blocked
   - Maintains block transitions parity: Once blocked, state remains blocked
   - Fail-open: Exceptions do not crash; returns safe state

**SIP InternalPlayerSession Integration:**

The SIP `InternalPlayerSession` already integrates both modules:
- Instantiates `DefaultResumeManager` and `DefaultKidsPlaybackGate` using existing dependencies
- On session start: Uses `Phase2Integration.loadInitialResumePosition` to determine initial seek position
- On periodic playback position updates (3s loop): Calls resume and kids gate tick handlers
- Updates `InternalPlayerUiState` fields: `kidActive`, `kidBlocked`, `kidProfileId`
- On playback end: Uses `resumeManager.handleEnded()` to clear resume

### New Unit Tests

Added `InternalPlayerSessionPhase2Test.kt` with comprehensive tests:

**ResumeManager Tests:**
- Does not resume when stored position ≤ 10 seconds
- Resumes when stored position > 10 seconds
- Returns null for LIVE content
- Clears resume when remaining duration < 10 seconds
- Saves resume when remaining duration ≥ 10 seconds
- Clears resume on playback ended
- Does not save when duration is invalid

**KidsPlaybackGate Tests:**
- Kid profile detection returns proper state
- Returns blocked state when quota exhausted
- 60-second accumulation triggers quota decrement
- Quota exhaustion leads to blocked state
- Non-kid profiles are not affected
- Blocked state persists

**Phase2Integration Tests:**
- `loadInitialResumePosition` delegates correctly
- `onPlaybackTick` handles both resume and kids gate
- `onPlaybackEnded` clears resume
- `evaluateKidsGateOnStart` delegates correctly

### Runtime Status

- ✅ Runtime path is unchanged: `InternalPlayerEntry` → legacy `InternalPlayerScreen`
- ✅ SIP session remains a non-runtime, future target for Phase 3+
- ✅ No functional changes to the production player flow
- ✅ All modular implementations are complete and tested

### Build & Test Status

- ✅ `./gradlew :app:assembleDebug` builds successfully
- ✅ `./gradlew :app:test` passes all tests including new Phase 2 tests
- ✅ All SIP modules compile and are ready for future runtime wiring

### What's Next

Phase 2 modular implementation is complete. The following phases remain:

- **Phase 3+**: Actual runtime switchover from legacy `InternalPlayerScreen` to modular SIP-based architecture
- **Phase 7**: `RememberPlayerController` implementation
- **Phase 8**: Lifecycle management (`ON_DESTROY` save/clear)

The typed `PlaybackContext` at all call sites and the complete modular implementations mean future phases can switch to the SIP-based orchestrator without modifying call sites or re-implementing resume/kids logic.

---

## Phase 2 – Final Stabilization Completed

**Date:** 2025-11-25

This phase finalizes Phase 2 by stabilizing the modular session infrastructure and preparing it for Phase 3 activation.

### What Was Done

**SIP InternalPlayerSession Hardened with Defensive Guards:**

1. **Negative durationMs Guard:**
   - ResumeManager skips save/clear when durationMs <= 0
   - Matches legacy behavior that guards against unknown duration

2. **positionMs > durationMs Guard:**
   - Near-end logic uses remaining calculation which handles this case
   - Negative remaining treated as near-end (clears resume)

3. **Unknown/Malformed PlaybackContext Guards:**
   - Null mediaId for VOD: No-op (no crash)
   - Null seriesId/season/episodeNumber for SERIES: No-op
   - Guards at all entry points in ResumeManager and KidsPlaybackGate

4. **LIVE Playback Resume Guard:**
   - Explicit check in initial seek: `playbackContext.type == PlaybackType.LIVE → null`
   - ResumeManager returns null for LIVE type
   - No resume save/clear operations for LIVE content

**Extended InternalPlayerUiState Fields (Phase 3 Ready):**

| Field | Type | Phase 3 UI Consumption |
|-------|------|------------------------|
| `isResumingFromLegacy` | Boolean | Show "Resuming..." indicator during initial seek |
| `resumeStartMs` | Long? | Show toast "Resumed from X:XX" after seek completes |
| `remainingKidsMinutes` | Int? | Show countdown timer in controls for kid profiles |

**SIP-Only Integration Tests:**

New test file: `InternalPlayerSessionPhase2IntegrationTest.kt`

Tests verify:
- ResumeManager + KidsGate coordination through Phase2Integration
- SIP session correctly updates InternalPlayerUiState
- Blocking transitions do not affect runtime (no actual player)
- SIP session emits stable, predictable state for Phase 3
- Defensive guards for edge cases:
  - Negative durationMs
  - positionMs > durationMs
  - Null mediaId/seriesId
  - Null kidProfileId

**Inline Documentation Added:**

- Comprehensive phase activation roadmap in InternalPlayerSession
- Legacy behavior mirroring annotations for all modules
- Independence guarantees documentation (no ViewModel, Navigation, ObjectBox, or legacy state dependencies)

### Files Modified

1. `InternalPlayerSession.kt` - Added defensive guards and phase documentation
2. `InternalPlayerState.kt` - Extended with Phase 3 annotated fields
3. `InternalPlayerSessionPhase2IntegrationTest.kt` - New SIP-only integration tests

### Runtime Status

- ✅ Runtime path unchanged: `InternalPlayerEntry` → legacy `InternalPlayerScreen`
- ✅ SIP session remains non-runtime reference implementation
- ✅ No functional changes to production player flow
- ✅ Modular session is now verified and ready for Phase 3 activation work

### Build & Test Status

- ✅ `./gradlew :app:assembleDebug` builds successfully
- ✅ `./gradlew :app:test` passes all tests including new Phase 2 integration tests
- ✅ All SIP modules compile and are internally consistent

### Phase 3 Readiness Checklist

- [x] SIP InternalPlayerSession hardened with defensive guards
- [x] Extended modular InternalPlayerUiState fields for future UI phases
- [x] SIP-only integration tests implemented
- [x] Legacy behavior mapping fully documented
- [x] Independence from ViewModels, Navigation, ObjectBox verified
- [x] Stable, predictable state emission for Phase 3 UI modules

---

## Phase 3 – Shadow Mode Initialization Started

**Date:** 2025-11-25

This phase initiates shadow-mode activation of the modular SIP session, running it in parallel with the legacy player for verification and diagnostics.

### What Was Done

**1. Created InternalPlayerShadow Bridge (`internal/bridge/InternalPlayerShadow.kt`):**

The shadow-mode entry point allows the modular session to be invoked in observation mode:
- `startShadowSession(...)` - Starts shadow session without controlling playback
- `stopShadowSession()` - Cleans up shadow session resources
- `ShadowSessionState` - Data class for shadow state diagnostics

**Shadow Mode Principles:**
- Never controls real ExoPlayer instance
- Never modifies legacy screen state
- Never interrupts or affects user playback experience
- State is captured for diagnostics only

**2. Extended InternalPlayerUiState with Shadow-Mode Fields:**

| Field | Type | Purpose |
|-------|------|---------|
| `shadowActive` | Boolean | Indicates if shadow observation is active |
| `shadowStateDebug` | String? | Debug string for overlay diagnostics |

**Field Documentation:**
- Not consumed by runtime UI
- Used for Phase 3–4 overlay debugging and verification
- Can be toggled via developer settings (future)

**3. Added SIP-Only Shadow-Mode Tests (`InternalPlayerSessionPhase3ShadowTest.kt`):**

Comprehensive tests verify:

**Shadow Session Safety:**
- Modular session can start without UI, navigation, ObjectBox, or ExoPlayer
- Shadow session never throws even with:
  - Missing mediaItem
  - Null seriesId
  - Negative durations
  - Invalid PlaybackContext combinations

**Clean Shutdown:**
- Shadow session stops cleanly without affecting legacy behavior
- Stop is safe to call multiple times
- Stop is safe to call without prior start

**State Independence:**
- UI state shadow fields have stable defaults
- Shadow fields can be set independently
- Copy preserves shadow fields
- Shadow fields do not affect legacy fields

**Integration with Phase2 Modules:**
- Phase2Integration works with shadow fakes
- ResumeManager and KidsPlaybackGate work in shadow mode

### Runtime Status

- ✅ Runtime path unchanged: `InternalPlayerEntry` → legacy `InternalPlayerScreen`
- ✅ Shadow entry point defined but NOT called by runtime code
- ✅ Legacy player remains the active orchestrator
- ✅ No functional changes to production player flow

### Files Added/Modified

**New Files:**
- `app/src/main/java/com/chris/m3usuite/player/internal/bridge/InternalPlayerShadow.kt`
- `app/src/test/java/com/chris/m3usuite/player/internal/session/InternalPlayerSessionPhase3ShadowTest.kt`

**Modified Files:**
- `app/src/main/java/com/chris/m3usuite/player/internal/state/InternalPlayerState.kt` - Added shadow fields
- `docs/INTERNAL_PLAYER_REFACTOR_STATUS.md` - This documentation

### What's Next (Phase 3 Remaining Work)

Phase 3 is NOT complete. Remaining tasks:

- [ ] Implement shadow session internals (currently placeholder)
- [ ] Wire shadow session to observe real playback inputs
- [ ] Add diagnostics logging for shadow state
- [ ] Create verification workflow to compare modular vs legacy behavior
- [ ] Add developer toggle for shadow mode activation

### Architecture After Phase 3 Initialization

```
Call Sites (VOD/SERIES/LIVE/Telegram Detail Screens)
    ↓
InternalPlayerEntry (Phase 1 Bridge)
    ↓
InternalPlayerScreen (Legacy - ACTIVE)
    ↓ (future: shadow observation)
InternalPlayerShadow (Shadow - PASSIVE, NOT WIRED YET)
    ↓
InternalPlayerSession (SIP - NOT CONTROLLING PLAYBACK)
```

---

## Phase 3 – Step 2: Legacy↔Shadow Parity Comparison Implemented

**Date:** 2025-11-25

This step implements the comparison pipeline between legacy and shadow state for diagnostics-only verification.

### What Was Done

**1. Created ShadowComparisonService (`internal/shadow/ShadowComparisonService.kt`):**

A diagnostics-only service for comparing legacy and shadow state:
- `compare(legacy, shadow)` - Compares two `InternalPlayerUiState` instances
- Returns `ComparisonResult` with parity flags and position offset

**ComparisonResult fields:**
| Field | Type | Description |
|-------|------|-------------|
| `resumeParityOk` | Boolean | True if both states have same resumeStartMs |
| `kidsGateParityOk` | Boolean | True if both states have same kidBlocked status |
| `positionOffsetMs` | Long? | Difference between legacy and shadow position (null if unavailable) |
| `flags` | List<String> | Diagnostic flags: "resumeMismatch", "kidsGateMismatch" |

**2. Added Comparison Callback to InternalPlayerShadow:**

Extended `startShadowSession(...)` with new parameter:
```kotlin
onShadowComparison: ((ShadowComparisonService.ComparisonResult) -> Unit)? = null
```

Added utility function:
```kotlin
fun invokeComparison(
    legacyState: InternalPlayerUiState,
    shadowState: InternalPlayerUiState,
    callback: ((ShadowComparisonService.ComparisonResult) -> Unit)?
)
```

**3. Extended InternalPlayerUiState with Comparison Fields:**

| Field | Type | Purpose |
|-------|------|---------|
| `currentPositionMs` | Long? | Current position for parity comparison (separate from runtime positionMs) |
| `comparisonDurationMs` | Long? | Duration for parity comparison (separate from runtime durationMs) |

**Documentation in InternalPlayerState.kt:**
```
These fields exist to enable parity comparison between
legacy and shadow sessions (Phase 3–4).
They MUST NOT drive any runtime UI behavior yet.
```

**4. Added Dedicated Tests (`InternalPlayerShadowComparisonTest.kt`):**

Comprehensive tests verify:
- Resume parity detection (matching, null, mismatch cases)
- Kids-gate parity detection (blocked/unblocked cases)
- Position offset calculation (positive, negative, zero, null cases)
- ComparisonResult flags (empty, single, multiple)
- Callback invocation (with/without callback, matching/mismatching states)
- No runtime path impact (state immutability, placeholder behavior)
- InternalPlayerUiState comparison fields (defaults, independence, copy preservation)

### Files Added/Modified

**New Files:**
- `app/src/main/java/com/chris/m3usuite/player/internal/shadow/ShadowComparisonService.kt`
- `app/src/test/java/com/chris/m3usuite/player/internal/session/InternalPlayerShadowComparisonTest.kt`

**Modified Files:**
- `app/src/main/java/com/chris/m3usuite/player/internal/state/InternalPlayerState.kt` - Added comparison fields
- `app/src/main/java/com/chris/m3usuite/player/internal/bridge/InternalPlayerShadow.kt` - Added comparison callback
- `docs/INTERNAL_PLAYER_REFACTOR_STATUS.md` - This documentation

### Runtime Status

- ✅ Runtime path unchanged: `InternalPlayerEntry` → legacy `InternalPlayerScreen`
- ✅ Comparison service is diagnostics-only (never affects playback)
- ✅ Shadow session placeholder does not invoke callbacks
- ✅ No functional changes to production player flow

### Build & Test Status

- ✅ `./gradlew :app:assembleDebug` builds successfully
- ✅ `./gradlew :app:test` passes all tests including new comparison tests

### Phase 3 Status

Phase 3 is NOT complete. This was Step 2 of Phase 3.

Completed Phase 3 steps:
- [x] Step 1: Shadow mode initialization (InternalPlayerShadow entry point)
- [x] Step 2: Legacy↔Shadow parity comparison pipeline

Remaining Phase 3 work:
- [ ] Implement shadow session internals
- [ ] Wire shadow session to observe real playback inputs
- [ ] Add diagnostics logging for shadow state
- [ ] Create verification workflow to compare modular vs legacy behavior
- [ ] Add developer toggle for shadow mode activation
