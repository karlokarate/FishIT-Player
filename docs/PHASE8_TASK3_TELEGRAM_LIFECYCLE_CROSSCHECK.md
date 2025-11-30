# Phase 8 – Task 3: Telegram ↔ PlayerLifecycle Cross-Check

**Date:** 2025-11-29  
**Phase:** 8 (InternalPlayerLifecycle refactor)  
**Task:** 3 (validation + conflict detection + lifecycle hardening)  
**Status:** ✅ COMPLETE – All violations resolved

---

## 1. Overview

This document presents the results of a comprehensive scan of all Telegram-related code in the FishIT-Player repository to identify any places where Telegram code interferes with the Phase 8 lifecycle rules.

**Phase 8 Governs:**
- Orientation locking / orientation restore
- Worker pausing/resuming
- System UI handling (immersive mode, cutout)
- Screen-on flags
- Player session lifetime (create/release/replace)
- Activity lifecycle (onResume, onPause, onStop, onDestroy)

**Key Principle:** Lifecycle is the single source of truth. No architecture changes allowed in Phase 8.

---

## 2. Full Violation Table

### 2.1 Scanned Telegram Packages

| Package | Files Scanned |
|---------|---------------|
| `telegram/core` | T_TelegramServiceClient.kt, T_TelegramSession.kt, T_ChatBrowser.kt, T_TelegramFileDownloader.kt, StreamingConfig.kt, TelegramFileLoader.kt |
| `telegram/player` | TelegramFileDataSource.kt, TelegramDataSource_Legacy.kt |
| `telegram/work` | TelegramSyncWorker.kt |
| `telegram/ui` | TelegramSettingsViewModel.kt, TelegramLogViewModel.kt, TelegramLogScreen.kt |
| `telegram/ui/feed` | TelegramActivityFeedViewModel.kt, TelegramActivityFeedScreen.kt |
| `telegram/parser` | MediaParser.kt, TgContentHeuristics.kt, TelegramMetadataExtractor.kt, TelegramItemBuilder.kt, TelegramBlockGrouper.kt |
| `telegram/ingestion` | TelegramHistoryScanner.kt, TelegramUpdateHandler.kt, TelegramIngestionCoordinator.kt |
| `telegram/domain` | TelegramItemMapper.kt, TelegramDomainModels.kt |
| `telegram/util` | TelegramPlayUrl.kt |
| `telegram/repository` | TelegramSyncStateRepository.kt |
| `telegram/logging` | TelegramLogRepository.kt |
| `telegram/config` | AppConfig.kt, ConfigLoader.kt |
| `telegram/prefetch` | TelegramPrefetcherHolder.kt, TelegramThumbPrefetcher.kt |
| `telegram/image` | TelegramThumbFetcher.kt |
| `ui/screens` | TelegramDetailScreen.kt |
| `data/repo` | TelegramContentRepository.kt |
| `ui/layout` | FishTelegramContent.kt |

### 2.2 Violation Analysis Results

| File Path | Code Snippet | Classification |
|-----------|--------------|----------------|
| `telegram/core/T_TelegramServiceClient.kt` | No orientation, system UI, or player manipulation | **F) OK** |
| `telegram/core/T_TelegramSession.kt` | No lifecycle interference | **F) OK** |
| `telegram/core/T_ChatBrowser.kt` | No lifecycle interference | **F) OK** |
| `telegram/core/T_TelegramFileDownloader.kt` | No player ownership, only file I/O | **F) OK** |
| `telegram/player/TelegramFileDataSource.kt` | DataSource only - no player ownership | **F) OK** |
| `telegram/player/TelegramDataSource_Legacy.kt` | DataSource only - no player ownership | **F) OK** |
| `telegram/work/TelegramSyncWorker.kt` | ✅ Now uses `PlaybackPriority.isPlaybackActive` for throttling | **F) OK (RESOLVED)** |
| `telegram/ui/TelegramSettingsViewModel.kt` | No lifecycle manipulation | **F) OK** |
| `telegram/ui/feed/TelegramActivityFeedViewModel.kt` | No lifecycle manipulation | **F) OK** |
| `telegram/ui/feed/TelegramActivityFeedScreen.kt` | `collectAsStateWithLifecycle` (Compose best practice) | **F) OK** |
| `telegram/ui/TelegramLogScreen.kt` | No lifecycle manipulation | **F) OK** |
| `ui/screens/TelegramDetailScreen.kt:161` | Uses `PlayerChooser.start()` → `openInternal` callback | **F) OK** |
| `telegram/util/TelegramPlayUrl.kt` | URL builder only | **F) OK** |
| `telegram/parser/*` | Stateless parsing, no lifecycle | **F) OK** |
| `telegram/ingestion/*` | Data processing only | **F) OK** |
| `telegram/domain/*` | Domain models only | **F) OK** |
| `telegram/logging/*` | Logging only | **F) OK** |
| `telegram/config/*` | Configuration only | **F) OK** |

---

## 3. Detailed Violation Analysis

### 3.1 ✅ RESOLVED: TelegramSyncWorker Playback-Aware Throttling

**File:** `app/src/main/java/com/chris/m3usuite/telegram/work/TelegramSyncWorker.kt`

**Resolution Date:** 2025-11-29

**Previous Issue:**
- The worker executed heavy I/O operations without checking `PlaybackPriority.isPlaybackActive`
- When playback was active, sync operations could cause stuttering

**Resolution:**
1. Added `PlaybackPriority` import and integration
2. Added `throttleIfPlaybackActive(mode: String)` helper method that:
   - Checks `PlaybackPriority.isPlaybackActive.value`
   - Delays by `PlaybackPriority.PLAYBACK_THROTTLE_MS` (500ms) when active
   - Logs throttling via `TelegramLogRepository`
3. Updated `calculateParallelism()` to return 1 when playback is active
4. Added throttle call before heavy sync operations in `doWork()`

**Code After Fix:**
```kotlin
// Phase 8: Playback-aware throttling before heavy sync operations
throttleIfPlaybackActive(mode)

// ...

private fun calculateParallelism(): Int {
    // Phase 8: Minimal parallelism during active playback
    if (PlaybackPriority.isPlaybackActive.value) {
        return 1
    }
    // ... normal parallelism calculation
}

private suspend fun throttleIfPlaybackActive(mode: String) {
    if (PlaybackPriority.isPlaybackActive.value) {
        TelegramLogRepository.info(
            "TelegramSyncWorker",
            "Playback active, using throttled mode",
            mapOf("mode" to mode),
        )
        delay(PlaybackPriority.PLAYBACK_THROTTLE_MS)
    }
}
```

**Tests Added:**
- `TelegramSyncWorkerTest.kt` extended with Phase 8 playback-aware throttling tests
- Tests verify worker respects `PlaybackPriority` state
- Tests verify consistency with other workers (XtreamDetailsWorker, XtreamDeltaImportWorker, ObxKeyBackfillWorker)

**Severity:** ✅ RESOLVED - No longer a concern

---

## 4. Findings Summary

### 4.1 What Telegram Code Does NOT Do (Compliant Areas)

| Category | Status | Notes |
|----------|--------|-------|
| Orientation lock/unlock | ✅ No violations | Telegram never calls `setRequestedOrientation()` |
| System UI flags | ✅ No violations | Telegram never manipulates immersive mode or cutouts |
| Screen-on flags | ✅ No violations | Telegram never sets `FLAG_KEEP_SCREEN_ON` |
| PlayerView/Surface manipulation | ✅ No violations | Telegram uses DataSource pattern only |
| ExoPlayer creation/release | ✅ No violations | Telegram never calls `ExoPlayer.Builder()` |
| Player pause/resume | ✅ No violations | Telegram never calls `player.play()` or `player.pause()` |
| Activity lifecycle methods | ✅ No violations | Telegram never overrides `onResume/onPause/onStop/onDestroy` |
| PlaybackSession ownership | ✅ No violations | Telegram never accesses `PlaybackSession` directly |
| Navigation to player | ✅ Compliant | Uses `PlayerChooser.start()` → callback → navigation |

### 4.2 ✅ All Violations Resolved

| Violation Type | Count | Files | Status |
|----------------|-------|-------|--------|
| A) LIFECYCLE VIOLATION | 0 | - | N/A |
| B) PLAYER SESSION VIOLATION | 0 | - | N/A |
| C) ORIENTATION VIOLATION | 0 | - | N/A |
| D) WORKER VIOLATION | 0 | ~~TelegramSyncWorker.kt~~ | ✅ RESOLVED |
| E) UI VIOLATION | 0 | - | N/A |
| F) OK | 18+ | All files | ✅ Compliant |

---

## 5. ✅ Completed Refactors

### 5.1 TelegramSyncWorker – Playback-Aware Throttling (IMPLEMENTED)

**Implementation Date:** 2025-11-29

**Changes Made:**
1. Added `PlaybackPriority` import
2. Added `throttleIfPlaybackActive(mode: String)` helper method
3. Updated `calculateParallelism()` to return 1 when playback is active
4. Added throttle call before heavy sync operations
5. Added comprehensive logging via `TelegramLogRepository`

**Current Implementation:**
```kotlin
// Phase 8: Playback-aware throttling before heavy sync operations
throttleIfPlaybackActive(mode)

// ...

private fun calculateParallelism(): Int {
    // Phase 8: Minimal parallelism during active playback
    if (PlaybackPriority.isPlaybackActive.value) {
        return 1
    }
    // ... normal parallelism calculation
}

private suspend fun throttleIfPlaybackActive(mode: String) {
    if (PlaybackPriority.isPlaybackActive.value) {
        TelegramLogRepository.info(
            "TelegramSyncWorker",
            "Playback active, using throttled mode",
            mapOf("mode" to mode),
        )
        delay(PlaybackPriority.PLAYBACK_THROTTLE_MS)
    }
}
```

**Dependencies Used:**
- `PlaybackPriority` object from `playback/` package (Phase 8 Group 5)
- `TelegramLogRepository` for diagnostics logging

**Tests Added:**
- `TelegramSyncWorkerTest.kt` extended with Phase 8 playback-aware throttling tests

---

## 6. Telegram ↔ PlayerLifecycle Contract

### 6.1 Allowed (Telegram MAY do)

| Action | Description | Example |
|--------|-------------|---------|
| Build TelegramItem | Create domain models from parsed content | `TelegramItemBuilder.buildItem()` |
| Build TelegramMediaRef | Create reference to Telegram media | `TelegramPlayUrl.buildFileUrl()` |
| Convert TelegramMediaRef → PlaybackSource | Resolve via central resolver | Via `PlayerChooser.start()` callback |
| Provide poster/backdrop thumbnails | Use TelegramThumbFetcher | Via Coil/AsyncImage |
| Trigger navigation into InternalPlayerEntry | Via openInternal callback | `TelegramDetailScreen` → `play()` |
| Use DataSource pattern for streaming | Implement DataSource interface | `TelegramFileDataSource` |
| Store/retrieve content metadata | Use ObjectBox via repositories | `TelegramContentRepository` |
| Run background sync workers | Use WorkManager | `TelegramSyncWorker` |

### 6.2 Forbidden (Telegram MAY NOT do)

| Action | Reason | Current Status |
|--------|--------|----------------|
| Create or release ExoPlayer | Player owned by PlaybackSession only | ✅ Not done |
| Modify PlayerView or PlayerSurface | Surface managed by SIP controls | ✅ Not done |
| Manipulate Activity lifecycle | Lifecycle owned by Phase 8 system | ✅ Not done |
| Toggle orientation lock | Orientation owned by Phase 8 system | ✅ Not done |
| Toggle system UI modes | System UI owned by Phase 8 system | ✅ Not done |
| Pause/Resume Xtream workers | Worker scheduling owned by SchedulingGateway | ✅ Not done |
| Pause/Resume Telegram workers directly | Should use central scheduling | ✅ Not done (uses SchedulingGateway) |
| Replace PlaybackSession | Session owned by PlaybackSession singleton | ✅ Not done |
| Bypass SIP PlaybackContext | Must use InternalPlayerEntry bridge | ✅ Not done |
| Access PlaybackSession directly | Must go through navigation | ✅ Not done |
| Call `player.play()` or `player.pause()` | Playback control owned by session | ✅ Not done |

### 6.3 Required (Telegram MUST do)

| Requirement | Implementation | Status |
|-------------|----------------|--------|
| TelegramItem → PlaybackContext(type=VOD) | Via navigation route parameters | ✅ Done |
| TelegramMediaRef → MediaItem via central resolver | Via PlayerChooser → openInternal | ✅ Done |
| Playback ALWAYS starts via InternalPlayerEntry | Via navigation composable in MainActivity | ✅ Done |
| SIP Session controls player lifetime | PlaybackSession.acquire() | ✅ Not Telegram's responsibility |
| No duplicate logic for lifecycle/orientation/workers | Single source of truth in Phase 8 | ✅ Compliant |
| Workers must check PlaybackPriority | Throttle during active playback | ✅ Done (TelegramSyncWorker) |

---

## 7. Playback Flow Verification

### 7.1 Current Telegram Playback Flow

```
TelegramDetailScreen
    │
    ├── User clicks "Play" or "Resume"
    │
    ├── play() function called
    │       │
    │       └── PlayerChooser.start(
    │               url = tg://file/...,
    │               buildInternal = { startMs, mimeType ->
    │                   openInternal(item.playUrl, startMs, mimeType)
    │               }
    │           )
    │
    └── openInternal callback
            │
            └── Navigation to "player?url=...&type=vod&mediaId=..."
                    │
                    └── MainActivity navigation composable
                            │
                            └── Builds PlaybackContext(type=VOD)
                                    │
                                    └── InternalPlayerEntry(playbackContext)
                                            │
                                            └── InternalPlayerScreen (legacy)
```

### 7.2 Flow Compliance

| Step | Phase 8 Compliance | Notes |
|------|-------------------|-------|
| TelegramDetailScreen.play() | ✅ OK | Does not touch player |
| PlayerChooser.start() | ✅ OK | Central chooser, respects settings |
| Navigation route | ✅ OK | Standard navigation |
| PlaybackContext creation | ✅ OK | Built in MainActivity |
| InternalPlayerEntry | ✅ OK | Phase 1 bridge |
| InternalPlayerScreen | ✅ OK | Legacy, not modified |

---

## 8. Conclusion

### 8.1 Summary

The Telegram integration is **fully compliant** with Phase 8 lifecycle rules:

- **18+ files** classified as **OK** (no violations)
- **Zero** lifecycle, player session, orientation, or UI violations
- ✅ `TelegramSyncWorker.kt` now uses `PlaybackPriority` for playback-aware throttling

### 8.2 Action Items

| Priority | Action | Owner | Phase | Status |
|----------|--------|-------|-------|--------|
| ~~LOW~~ | ~~Add PlaybackPriority throttling to TelegramSyncWorker~~ | ~~Phase 8~~ | ~~Group 5, Task 5.3~~ | ✅ DONE |

### 8.3 Risk Assessment

| Risk | Likelihood | Impact | Mitigation | Status |
|------|------------|--------|------------|--------|
| Sync causes playback stutter | ~~Low~~ None | ~~Low~~ N/A | ✅ Throttling implemented | RESOLVED |
| Telegram bypasses lifecycle | None | N/A | Already compliant | N/A |
| Telegram owns player | None | N/A | Uses DataSource pattern | N/A |

---

## 9. Contract Reference

This cross-check aligns with:
- `docs/INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md` Section 7
- `docs/INTERNAL_PLAYER_PHASE8_CHECKLIST.md` Group 5
- `.github/tdlibAgent.md` (Telegram architecture)
- `docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md` (Player behavior rules)

---

## 10. Phase D UI/Playback Integration Compliance Note

**Date:** 2025-11-29  
**Phase:** D (Telegram UI & Playback Integration)  
**Status:** ✅ COMPLIANT

### 10.1 Overview

Phase D implements UI integration for TelegramItem domain objects while fully respecting the Phase 8 lifecycle rules and the "Telegram ↔ PlayerLifecycle Contract" defined in this document.

### 10.2 New Components Added

| Component | Location | Contract Compliance |
|-----------|----------|---------------------|
| `TelegramLibraryViewModel` | `telegram/ui/TelegramLibraryViewModel.kt` | ✅ Standard viewModelScope, no lifecycle manipulation |
| `FishTelegramItemContent` | `ui/layout/FishTelegramContent.kt` | ✅ Stateless composable, no player interaction |
| `FishTelegramItemRow` | `ui/layout/FishTelegramContent.kt` | ✅ Uses FishRowLight, no lifecycle interference |
| `TelegramItemDetailScreen` | `ui/screens/TelegramDetailScreen.kt` | ✅ Uses PlayerChooser.start() for playback |

### 10.3 Playback Flow Verification

```
TelegramItemDetailScreen
    │
    ├── User clicks "Play" or "Resume"
    │
    ├── play() function called
    │       │
    │       └── PlayerChooser.start(
    │               url = tg://file/<fileId>?chatId=...&messageId=...,
    │               buildInternal = { startMs, mimeType ->
    │                   openInternal(item.playUrl, startMs, mimeType)
    │               }
    │           )
    │
    └── openInternal callback
            │
            └── Navigation to "player?url=...&type=vod&mediaId=..."
                    │
                    └── MainActivity navigation composable
                            │
                            └── Builds PlaybackContext(type=VOD)
                                    │
                                    └── InternalPlayerEntry(playbackContext)
```

### 10.4 Contract Compliance Summary

| Requirement | Phase D Status |
|-------------|----------------|
| Telegram MUST build PlaybackContext(type=VOD) | ✅ Via navigation route parameters |
| Telegram MUST convert TelegramMediaRef → MediaItem | ✅ Via PlayerChooser → openInternal |
| Telegram MUST navigate into InternalPlayerEntry | ✅ Via navigation composable |
| Telegram MUST NOT create/release ExoPlayer | ✅ Not done |
| Telegram MUST NOT modify PlayerView | ✅ Not done |
| Telegram MUST NOT override activity lifecycle | ✅ Not done |
| Telegram MUST NOT toggle orientation | ✅ Not done |
| Workers MUST check PlaybackPriority | ✅ TelegramSyncWorker already compliant |

### 10.5 Tests Added

- `TelegramLibraryViewModelTest.kt` - Verifies ViewModel API surface
- `TelegramDetailScreenPlaybackTest.kt` - Verifies playback URL construction and contract compliance

---

**Document Version:** 1.2  
**Author:** GitHub Copilot Agent  
**Review Status:** ✅ COMPLETE – All violations resolved, Phase D integration compliant
