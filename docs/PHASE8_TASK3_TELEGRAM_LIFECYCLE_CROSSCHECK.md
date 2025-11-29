# Phase 8 – Task 3: Telegram ↔ PlayerLifecycle Cross-Check

**Date:** 2025-11-29  
**Phase:** 8 (InternalPlayerLifecycle refactor)  
**Task:** 3 (validation + conflict detection + lifecycle hardening)  
**Status:** Detection & Alignment Complete (NO CODE CHANGES)

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
| `telegram/work/TelegramSyncWorker.kt:55` | `withContext(Dispatchers.IO)` - no playback awareness | **D) WORKER VIOLATION** |
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

### 3.1 Violation D) WORKER VIOLATION – TelegramSyncWorker

**File:** `app/src/main/java/com/chris/m3usuite/telegram/work/TelegramSyncWorker.kt`

**Location:** Lines 54-173 (`doWork()` method)

**Code Snippet:**
```kotlin
override suspend fun doWork(): Result =
    withContext(Dispatchers.IO) {
        // ... sync logic
        val parallelism = calculateParallelism()
        val dispatcher = Dispatchers.IO.limitedParallelism(parallelism)
        // ... parallel sync with no playback awareness
    }
```

**Issue:**
- The worker executes heavy I/O operations (TDLib network calls, ObjectBox writes) without checking `PlaybackPriority.isPlaybackActive`
- When playback is active, the worker should throttle its operations to prevent stuttering
- This conflicts with Phase 8 Contract Section 7.2 which requires workers to throttle during active playback

**Severity:** MODERATE - Does not break playback but could cause stuttering during sync

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

### 4.2 Single Violation Found

| Violation Type | Count | Files |
|----------------|-------|-------|
| A) LIFECYCLE VIOLATION | 0 | - |
| B) PLAYER SESSION VIOLATION | 0 | - |
| C) ORIENTATION VIOLATION | 0 | - |
| D) WORKER VIOLATION | 1 | TelegramSyncWorker.kt |
| E) UI VIOLATION | 0 | - |
| F) OK | 17+ | All other files |

---

## 5. Proposed Refactors

### 5.1 TelegramSyncWorker – Add Playback-Aware Throttling

**Current State:**
```kotlin
override suspend fun doWork(): Result =
    withContext(Dispatchers.IO) {
        // Heavy sync with no throttling
    }
```

**Proposed Refactor (Phase 8 Compliant):**
```kotlin
override suspend fun doWork(): Result =
    withContext(Dispatchers.IO) {
        // At start of doWork
        if (PlaybackPriority.isPlaybackActive.value) {
            TelegramLogRepository.info("TelegramSyncWorker", "Playback active, using throttled mode")
        }
        
        // In syncChat function, between heavy operations:
        if (PlaybackPriority.isPlaybackActive.value) {
            delay(BACKGROUND_THROTTLE_MS) // e.g., 500ms
        }
        
        // When calculating parallelism:
        val parallelism = if (PlaybackPriority.isPlaybackActive.value) {
            1 // Minimal parallelism during playback
        } else {
            calculateParallelism()
        }
    }
```

**Required Dependencies:**
- `PlaybackPriority` object must be created in `playback/` package (per Phase 8 Checklist Group 5)
- This refactor should be done as part of Phase 8 Task 5.3 (Update TelegramSyncWorker with playback awareness)

**Note:** The `PlaybackPriority` object does not yet exist in the codebase. It is planned for Phase 8 Group 5 implementation.

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
| Workers must check PlaybackPriority when it exists | Throttle during active playback | ⏳ Pending Phase 8 Group 5 |

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

The Telegram integration is **largely compliant** with Phase 8 lifecycle rules:

- **17+ files** classified as **OK** (no violations)
- **1 file** has a **minor worker violation** (TelegramSyncWorker.kt)
- **Zero** lifecycle, player session, orientation, or UI violations

### 8.2 Action Items

| Priority | Action | Owner | Phase |
|----------|--------|-------|-------|
| LOW | Add PlaybackPriority throttling to TelegramSyncWorker | Phase 8 | Group 5, Task 5.3 |

### 8.3 Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Sync causes playback stutter | Low | Low | Implement throttling in Phase 8 Group 5 |
| Telegram bypasses lifecycle | None | N/A | Already compliant |
| Telegram owns player | None | N/A | Uses DataSource pattern |

---

## 9. Contract Reference

This cross-check aligns with:
- `docs/INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md` Section 7
- `docs/INTERNAL_PLAYER_PHASE8_CHECKLIST.md` Group 5
- `.github/tdlibAgent.md` (Telegram architecture)
- `docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md` (Player behavior rules)

---

**Document Version:** 1.0  
**Author:** GitHub Copilot Agent  
**Review Status:** Detection Complete, NO CODE CHANGES
