# TDLib Task Grouping and Cluster Analysis

This document provides a comprehensive breakdown of all tasks from `tdlibAgent.md` organized into parallelizable task clusters.

## Overview

The tasks have been analyzed and grouped into 5 distinct clusters that:
- Target different modules/directories to minimize merge conflicts
- Can be developed in parallel by different teams/agents
- Are thematically cohesive within each cluster
- Follow clear architectural boundaries

---

## Task Extraction from tdlibAgent.md

### Section 1.2: Migration IST → SOLL (Package Restructuring)

**Tasks:**
1. Move `TelegramServiceClient.kt` from `ui/screens` to `telegram/core` and rename to `T_TelegramServiceClient`
2. Move `TelegramSession.kt` to `telegram/core` and rename to `T_TelegramSession`
3. Move `ChatBrowser.kt` to `telegram/core` and rename to `T_ChatBrowser`
4. Move `TelegramFileDownloader.kt` to `telegram/core` and rename to `T_TelegramFileDownloader`
5. Move `TelegramDataSource.kt` to `telegram/player`
6. Move `TelegramSyncWorker.kt` to `telegram/work`
7. Move `TelegramSettingsViewModel.kt` from `ui/screens` to `telegram/ui`
8. Keep `TelegramViewModel.kt` in `telegram/ui` but deprecate for new features
9. Create new files: `telegram/parser/TgContentHeuristics.kt`, `telegram/logging/TelegramLogRepository.kt`, `telegram/ui/TelegramLogViewModel.kt`, `telegram/ui/feed/TelegramActivityFeedViewModel.kt`, `telegram/ui/feed/TelegramActivityFeedScreen.kt`
10. Update all import paths and DI/Factory constructs

### Section 3.1: T_TelegramServiceClient (Unified Telegram Engine)

**Tasks:**
11. Implement `T_TelegramServiceClient` class with internal CoroutineScope
12. Create single TdlClient instance
13. Create instances of T_TelegramSession, T_ChatBrowser, T_TelegramFileDownloader
14. Implement API: authState, connectionState, syncState, activityEvents StateFlows/SharedFlows
15. Implement methods: ensureStarted, login, listChats, resolveChatTitle, downloader()
16. Configure TdlClient using ConfigLoader
17. Distribute TDLib update flows to Session/Browser/Downloader/Activity Feed
18. Implement Engine-Supervisor with restart logic, reconnect on network changes
19. Integrate with ProcessLifecycleOwner for lifecycle management

### Section 3.2: T_TelegramSession Overhaul

**Tasks:**
20. Adjust constructor to inject TdlClient (not create own)
21. Maintain authEvents SharedFlow
22. Use authorizationStateUpdates from tdl-coroutines API
23. Implement functions: startAuthLoop, sendPhoneNumber, sendCode, sendPassword in ServiceClient scope
24. Map AuthorizationState to AuthEvent
25. Use TelegramLogRepository instead of println for logging

### Section 3.3: T_ChatBrowser Migration

**Tasks:**
26. Rename ChatBrowser to T_ChatBrowser in telegram/core package
27. Adjust constructor to take TdlClient or T_TelegramSession
28. Consolidate public API: getTopChats, getChat, loadMessagesPaged, observeMessages
29. Use ServiceClient scope instead of creating own scopes

### Section 3.4: TelegramSettingsViewModel ServiceClient Integration

**Tasks:**
30. Rebuild constructor to inject T_TelegramServiceClient + SettingsStore
31. Remove direct TdlClient creation
32. Build TelegramSettingsState to use ServiceClient.authState
33. Implement isLoadingChats, availableChats, selectedChats based on Browser/Settings data
34. Delegate login actions to serviceClient.login
35. Implement chat picker using serviceClient.listChats
36. Write selection to SettingsStore (TG_SELECTED_*_CHATS_CSV)

### Section 4.1: TelegramSyncWorker (Turbo-Sync)

**Tasks:**
37. Implement doWork() to load SettingsStore
38. Call T_TelegramServiceClient.ensureStarted
39. Determine relevant chat IDs (VOD/Series/Feed)
40. Start parallel sync tasks per chat/group with Dispatchers.IO.limitedParallelism(n)
41. Derive n from device profile (CPU cores, device class)
42. For each chat: load messages via T_ChatBrowser.loadMessagesPaged
43. Apply MediaParser.parseMessage
44. Use TgContentHeuristics to sharpen classification
45. Update TelegramContentRepository
46. Implement modes: MODE_ALL, MODE_SELECTION_CHANGED, MODE_BACKFILL_SERIES
47. Use TelegramLogRepository.logSyncEvent for progress logging
48. Update T_TelegramServiceClient.syncState

### Section 4.2: TelegramDataSource (Zero-Copy Streaming)

**Tasks:**
49. Adjust constructor to inject T_TelegramServiceClient and/or T_TelegramFileDownloader
50. Implement open(dataSpec) to parse tg://file/<fileId>?chatId=...&messageId=...
51. Request in-memory stream/ringbuffer from T_TelegramFileDownloader
52. Inform TransferListener correctly (onTransferStart)
53. Implement read() to read bytes directly from in-memory buffer
54. Continue download in background as needed
55. Implement close() to cancel/release streams and call TransferListener.onTransferEnd
56. Ensure DelegatingDataSourceFactory creates TelegramDataSource for tg:// URLs

### Section 4.3: TelegramContentRepository Refinement

**Tasks:**
57. Ensure encodeTelegramId and isTelegramItem are stable and unique
58. Ensure Play URL format: tg://file/<fileId>?chatId=...&messageId=...
59. Provide functions: getTelegramVod, getTelegramSeries, getTelegramFeedItems, searchTelegramContent
60. Integrate SettingsStore to consider which chats are indexed (VOD vs Series vs Feed)

### Section 5.1: TelegramLogRepository & Log Screen

**Tasks:**
61. Create TelegramLogRepository with TgLogEntry data class
62. Implement in-memory ringbuffer (500 entries)
63. Provide StateFlow<List<TgLogEntry>> and SharedFlow<TgLogEntry>
64. Use DiagnosticsLogger internally for logcat/file
65. Instrument T_TelegramServiceClient, T_TelegramSession, T_ChatBrowser, T_TelegramFileDownloader, TelegramSyncWorker, TelegramDataSource to call TelegramLogRepository.log
66. Create TelegramLogViewModel
67. Create Log Screen UI (Compose) with filter by level/source, DPAD-compatible
68. Add export option (share intent)
69. Implement short overlays: listen to events in StartScreen/Library/Settings, show Snackbar/Toast for level >= WARN

### Section 5.2: Telegram Activity Feed

**Tasks:**
70. Define TgActivityEvent (NewMovie, NewEpisode, NewArchive, ChatUpdated)
71. Have T_TelegramServiceClient emit TgActivityEvent to activityEvents on relevant updates
72. Create TelegramActivityFeedViewModel to listen to activityEvents
73. Use TelegramContentRepository to load associated MediaItems
74. Provide UI-friendly feed state (StateFlow<FeedState>)
75. Create TelegramActivityFeedScreen with feed list (TV-focusable and Touch)
76. Enable direct play/call of items
77. Make accessible from Start/Library/Settings (menu point "Telegram-Feed")

### Section 6.1: Quality & Debug

**Tasks:**
78. Ensure tdl-coroutines-android contains native code for arm64-v8a and armeabi-v7a
79. Do not include own libtdjni.so (keep b/libtd out of classpath)
80. Add R8/ProGuard rules for dev.g000sha256.tdl.dto.* if missing
81. Add leakcanary-android for Telegram scopes
82. Add kotlinx-coroutines-debug for debug builds with kotlinx.coroutines.debug property
83. Add androidx.profileinstaller for startup time optimization
84. Add JetBrains Kover for test coverage (Gradle plugin kover)

### Section 6.2: UX & Visuals

**Tasks:**
85. Integrate Coil 3.x for thumbnails
86. Generate thumbnails for Telegram media using MediaMetadataRetriever or Media3
87. Extend FishTelegramContent to show cover images
88. Implement Compose Drag/Reorder lists for chat picker
89. Make chat reordering DPAD-compatible on TV, Touch on Phone/Tablet
90. Create Jetpack Glance Widgets for "New Telegram Films"/"New Telegram Series"
91. Widget should use TelegramContentRepository.getTelegramFeedItems()

### Section 6.3: Tests & Lint

**Tasks:**
92. Write unit tests for MediaParser: episode heuristics (SxxEyy, "Episode 4", language tags)
93. Write unit tests for TgContentHeuristics: classification/score tests
94. Write unit tests for TelegramContentRepository: ID mapping and URL generation
95. Write instrumented tests for TV navigation: StartScreen → Telegram Row → Playback
96. Write instrumented tests for Library: Telegram tab "Films"/"Series"
97. Write instrumented tests for Activity Feed navigation + playback
98. Add lint/detekt rule: No direct TdlClient access outside telegram.core
99. Add lint/detekt rule: No monster methods (> 100 LOC) in Telegram files, clear Flow names

---

## Cluster Definitions

### Cluster A: Core / Engine
**Package:** `telegram/core`, `telegram/config`  
**Focus:** Fundamental Telegram infrastructure and unified engine

**Assigned Tasks:**
- Tasks 1-4: Move core components to telegram/core with T_ prefix
- Tasks 11-19: T_TelegramServiceClient implementation
- Tasks 20-25: T_TelegramSession overhaul
- Tasks 26-29: T_ChatBrowser migration

**Rationale:** This cluster contains the foundational "Unified Telegram Engine" that all other clusters depend on. It must be implemented first to provide stable APIs. These tasks all modify files in `telegram/core` and `telegram/config` packages with minimal overlap with other clusters. Once this engine is stable, other teams can work in parallel against its interfaces.

**Affected Modules:**
- `app/src/main/java/com/chris/m3usuite/telegram/core/`
- `app/src/main/java/com/chris/m3usuite/telegram/config/`
- Migration from: `ui/screens/TelegramServiceClient.kt`, `telegram/session/`, `telegram/browser/`, `telegram/downloader/`

**Recommended Order:**
1. Create telegram/core package structure
2. Move and rename T_TelegramSession first (foundation)
3. Move and rename T_ChatBrowser
4. Move and rename T_TelegramFileDownloader  
5. Implement T_TelegramServiceClient that orchestrates all three
6. Wire up update flows and lifecycle management

---

### Cluster B: Sync / Worker / Repository
**Package:** `telegram/work`, `data/repo`, `telegram/parser`  
**Focus:** Background synchronization, content parsing, and data persistence

**Assigned Tasks:**
- Task 6: Move TelegramSyncWorker to telegram/work
- Task 9 (partial): Create telegram/parser/TgContentHeuristics.kt
- Tasks 37-48: TelegramSyncWorker Turbo-Sync implementation
- Tasks 57-60: TelegramContentRepository refinement
- Tasks 92-94: Unit tests for Parser and Repository

**Rationale:** This cluster handles all data synchronization and persistence logic. It works with the Core cluster's APIs but modifies completely different files (`telegram/work/`, `data/repo/`, `telegram/parser/`). The worker, parser, and repository form a cohesive pipeline for processing Telegram content. Can be developed in parallel with Clusters C, D, E once Cluster A provides stable interfaces.

**Affected Modules:**
- `app/src/main/java/com/chris/m3usuite/telegram/work/`
- `app/src/main/java/com/chris/m3usuite/data/repo/TelegramContentRepository.kt`
- `app/src/main/java/com/chris/m3usuite/telegram/parser/`

**Recommended Order:**
1. Create TgContentHeuristics with classification logic
2. Refine TelegramContentRepository with new Flow APIs
3. Move TelegramSyncWorker to telegram/work
4. Implement Turbo-Sync with parallel processing
5. Add unit tests for parser and repository

---

### Cluster C: Streaming / DataSource
**Package:** `telegram/player`  
**Focus:** Zero-copy streaming and media playback integration

**Assigned Tasks:**
- Task 5: Move TelegramDataSource to telegram/player
- Tasks 49-56: TelegramDataSource Zero-Copy implementation
- Tasks 85-87: Coil 3.x integration for thumbnails

**Rationale:** This cluster focuses exclusively on media streaming and playback. It works in `telegram/player/` and player-related infrastructure, completely separate from UI, worker, and logging concerns. The streaming DataSource and thumbnail generation are tightly coupled features that benefit from joint development. Can be developed in parallel with Clusters B, D, E.

**Affected Modules:**
- `app/src/main/java/com/chris/m3usuite/telegram/player/`
- `app/src/main/java/com/chris/m3usuite/player/datasource/DelegatingDataSourceFactory.kt`
- Media thumbnail generation integration

**Recommended Order:**
1. Move TelegramDataSource to telegram/player
2. Implement Zero-Copy streaming with in-memory buffers
3. Integrate with DelegatingDataSourceFactory for tg:// URLs
4. Add Coil 3.x dependency
5. Implement thumbnail generation for Telegram media

---

### Cluster D: UI / Activity Feed / Rows
**Package:** `telegram/ui`, `telegram/ui/feed`, `ui/layout`  
**Focus:** User interface, settings, activity feed, and visual components

**Assigned Tasks:**
- Task 7: Move TelegramSettingsViewModel to telegram/ui
- Task 9 (partial): Create TelegramActivityFeedViewModel and TelegramActivityFeedScreen
- Tasks 30-36: TelegramSettingsViewModel ServiceClient integration
- Tasks 70-77: Telegram Activity Feed implementation
- Tasks 88-91: Chat picker reordering and Glance widgets
- Tasks 95-97: Instrumented UI tests

**Rationale:** This cluster contains all UI and user-facing components. It works in `telegram/ui/`, `telegram/ui/feed/`, and `ui/layout/`, which are distinct from core engine, worker, and player code. The settings, activity feed, and layout components are cohesive UI concerns that can share UI patterns and design systems. Can be developed in parallel with Clusters B and C.

**Affected Modules:**
- `app/src/main/java/com/chris/m3usuite/telegram/ui/`
- `app/src/main/java/com/chris/m3usuite/telegram/ui/feed/`
- `app/src/main/java/com/chris/m3usuite/ui/layout/FishTelegramContent.kt`
- `app/src/main/java/com/chris/m3usuite/ui/screens/` (migration source)

**Recommended Order:**
1. Move TelegramSettingsViewModel to telegram/ui
2. Integrate with T_TelegramServiceClient
3. Implement chat picker with SettingsStore integration
4. Create TelegramActivityFeedViewModel
5. Create TelegramActivityFeedScreen
6. Add chat reordering UI
7. Create Glance widgets
8. Add instrumented UI tests

---

### Cluster E: Logging / Diagnostics / Quality / Tools
**Package:** `telegram/logging`, debug tools, test infrastructure  
**Focus:** Logging, diagnostics, quality assurance, and developer tools

**Assigned Tasks:**
- Task 9 (partial): Create telegram/logging/TelegramLogRepository.kt and TelegramLogViewModel.kt
- Tasks 61-69: TelegramLogRepository & Log Screen implementation
- Tasks 78-84: Quality & debug tools (LeakCanary, coroutines debug, ProGuard, Kover)
- Tasks 98-99: Lint/Detekt rules for Telegram code quality

**Rationale:** This cluster handles all observability, logging, and quality assurance concerns. It works in `telegram/logging/` and build configuration, which are completely separate from feature implementation in other clusters. Logging infrastructure benefits all other clusters but doesn't block their core functionality. Can be developed in parallel with all other clusters.

**Affected Modules:**
- `app/src/main/java/com/chris/m3usuite/telegram/logging/`
- `app/src/main/java/com/chris/m3usuite/diagnostics/` (integration point)
- Build configuration files (build.gradle.kts, ProGuard rules)
- Lint/Detekt configuration

**Recommended Order:**
1. Create TelegramLogRepository with ringbuffer
2. Integrate DiagnosticsLogger
3. Create TelegramLogViewModel and Log Screen UI
4. Instrument all Telegram components for logging
5. Add short overlays for warnings
6. Add LeakCanary, coroutines debug, profileinstaller
7. Add Kover for test coverage
8. Create custom lint/detekt rules

---

## Dependency Graph

```
Cluster A (Core/Engine)
    └─> Foundation for all other clusters

Cluster B (Sync/Worker/Repository)
    └─> Depends on: Cluster A APIs
    └─> Can work in parallel with: C, D, E

Cluster C (Streaming/DataSource)
    └─> Depends on: Cluster A APIs
    └─> Can work in parallel with: B, D, E

Cluster D (UI/Activity Feed)
    └─> Depends on: Cluster A APIs, Cluster B Repository APIs
    └─> Can work in parallel with: C, E

Cluster E (Logging/Quality)
    └─> Depends on: Minimal (DiagnosticsLogger exists)
    └─> Integrates with: All clusters (instrumentation)
    └─> Can work in parallel with: A, B, C, D
```

**Critical Path:**
1. Cluster A must be completed first (or at least stable API contracts defined)
2. Clusters B, C, D, E can then proceed in parallel
3. Cluster E logging can be integrated incrementally into other clusters

---

## Implementation Strategy

### Phase 1: Foundation (Cluster A - Core)
- Establish the Unified Telegram Engine
- Create stable API contracts for other clusters
- Duration estimate: 1-2 sprints

### Phase 2: Parallel Development (Clusters B, C, D, E)
- Four parallel work streams can proceed simultaneously
- Each cluster has minimal file overlap with others
- Regular integration checkpoints to ensure API compatibility
- Duration estimate: 2-3 sprints (parallel)

### Phase 3: Integration & Testing
- Combine all clusters
- End-to-end testing across all features
- Performance tuning and optimization
- Duration estimate: 1 sprint

---

## File Conflict Matrix

This matrix shows potential file conflicts between clusters (X = potential conflict):

|           | Cluster A | Cluster B | Cluster C | Cluster D | Cluster E |
|-----------|-----------|-----------|-----------|-----------|-----------|
| Cluster A | -         |           |           |           |           |
| Cluster B |           | -         |           |           |           |
| Cluster C |           |           | -         |           |           |
| Cluster D |           |           |           | -         |           |
| Cluster E |           |           |           |           | -         |

**Result:** Zero file conflicts expected between clusters (except for build config which Cluster E might touch, but those are easy to merge).

---

## Notes for Implementation

1. **Cluster A (Core) Priority:** Must be completed first to establish stable interfaces
2. **API Contracts:** Define interfaces early in Cluster A so other clusters can work against contracts
3. **Mock/Stub Strategy:** Other clusters can use mocks of Cluster A APIs during parallel development
4. **Integration Points:** Regular sync meetings to ensure API compatibility
5. **Testing Strategy:** Each cluster includes its own unit tests; integration tests come in Phase 3
6. **Documentation:** Each cluster should update this document with implementation notes

---

## Success Criteria

Each cluster is considered complete when:
1. All assigned tasks are implemented
2. Unit tests pass (where applicable)
3. Code follows project style guidelines (ktlint, detekt)
4. APIs are documented (KDoc)
5. No regressions in existing functionality
6. Integration with other clusters is tested (Phase 3)

---

_This document is a living document and should be updated as implementation progresses._
