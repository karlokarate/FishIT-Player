# TDLib Final Review – Complete Implementation Checklist

This document tracks all tasks required to fully implement the TDLib integration according to `tdlibAgent.md` and `TDLIB_TASK_GROUPING.md`.

**Status Legend:**
- `[ ]` = Not started / Incomplete
- `[x]` = Fully implemented, tested, and verified
- `[~]` = Partially implemented (not allowed for completion)

**Rules for checking off items:**
1. Implementation must be complete in code
2. Build must be green (no compilation errors)
3. Tests must pass (if applicable)
4. CI must be green (if applicable)
5. Wiring/Integration must work end-to-end

---

## Cluster A: Core / Engine Foundation

### Package Restructuring (Section 1.2)

- [ ] 1.1 Verify `T_TelegramServiceClient.kt` exists in `telegram/core` (not `ui/screens`)
- [ ] 1.2 Verify `T_TelegramSession.kt` exists in `telegram/core` (not `telegram/session`)
- [ ] 1.3 Verify `T_ChatBrowser.kt` exists in `telegram/core` (not `telegram/browser`)
- [ ] 1.4 Verify `T_TelegramFileDownloader.kt` exists in `telegram/core` (not `telegram/downloader`)
- [ ] 1.5 Remove deprecated `TelegramServiceClient.kt` from `ui/screens` (maintain backward compatibility first)
- [ ] 1.6 Remove or deprecate old `TelegramSession.kt` in `telegram/session`
- [ ] 1.7 Remove or deprecate old `ChatBrowser.kt` in `telegram/browser`
- [ ] 1.8 Remove or deprecate old `TelegramFileDownloader.kt` in `telegram/downloader`

### T_TelegramServiceClient Implementation (Section 3.1)

- [ ] 2.1 Singleton pattern implemented with `getInstance(context)` method
- [ ] 2.2 Internal `CoroutineScope(SupervisorJob() + Dispatchers.IO)` created
- [ ] 2.3 Single `TdlClient` instance managed internally
- [ ] 2.4 `T_TelegramSession` instance created and managed
- [ ] 2.5 `T_ChatBrowser` instance created and managed
- [ ] 2.6 `T_TelegramFileDownloader` instance created and managed
- [ ] 2.7 `authState: StateFlow<TelegramAuthState>` exposed
- [ ] 2.8 `connectionState: StateFlow<TgConnectionState>` exposed
- [ ] 2.9 `syncState: StateFlow<TgSyncState>` exposed
- [ ] 2.10 `activityEvents: SharedFlow<TgActivityEvent>` exposed
- [ ] 2.11 `suspend fun ensureStarted(context, settings)` implemented
- [ ] 2.12 `suspend fun login(phone, code, password)` implemented
- [ ] 2.13 `suspend fun listChats(context, limit)` implemented
- [ ] 2.14 `suspend fun resolveChatTitle(chatId)` implemented
- [ ] 2.15 `fun downloader(): T_TelegramFileDownloader` implemented
- [ ] 2.16 `ConfigLoader.loadConfig(context)` integration works
- [ ] 2.17 TDLib update flows distributed to Session/Browser/Downloader/Feed
- [ ] 2.18 Engine supervisor with restart logic implemented
- [ ] 2.19 Reconnect on network changes implemented
- [ ] 2.20 ProcessLifecycleOwner integration for lifecycle management
- [ ] 2.21 Unit tests for ServiceClient core functionality

### T_TelegramSession Overhaul (Section 3.2)

- [ ] 3.1 Constructor takes injected `TdlClient` (not creating own)
- [ ] 3.2 `authEvents: SharedFlow<AuthEvent>` maintained
- [ ] 3.3 Uses `authorizationStateUpdates` from tdl-coroutines
- [ ] 3.4 `suspend fun startAuthLoop()` implemented
- [ ] 3.5 `suspend fun sendPhoneNumber(phone)` implemented
- [ ] 3.6 `suspend fun sendCode(code)` implemented
- [ ] 3.7 `suspend fun sendPassword(password)` implemented
- [ ] 3.8 All auth functions run in ServiceClient scope
- [ ] 3.9 `AuthorizationState*` to `AuthEvent` mapping works
- [ ] 3.10 Uses `TelegramLogRepository` for logging (not println)
- [ ] 3.11 Unit tests for auth state mapping

### T_ChatBrowser Migration (Section 3.3)

- [ ] 4.1 Class renamed from `ChatBrowser` to `T_ChatBrowser`
- [ ] 4.2 Package is `telegram.core` (not `telegram.browser`)
- [ ] 4.3 Constructor takes `TdlClient` or `T_TelegramSession`
- [ ] 4.4 `suspend fun getTopChats(limit): List<Chat>` implemented
- [ ] 4.5 `suspend fun getChat(chatId): Chat` implemented
- [ ] 4.6 `suspend fun loadMessagesPaged(chatId, ...): List<Message>` implemented
- [ ] 4.7 `fun observeMessages(chatId): Flow<List<Message>>` implemented
- [ ] 4.8 Uses ServiceClient scope (no internal scope creation)
- [ ] 4.9 Update flows (`newMessageUpdates`, `chatPositionUpdates`) consumed
- [ ] 4.10 Unit tests for chat/message operations

---

## Cluster B: Sync / Worker / Repository

### TelegramContentRepository Refinement (Section 4.3)

- [ ] 5.1 `encodeTelegramId(...)` is stable and unique
- [ ] 5.2 `isTelegramItem(...)` correctly identifies Telegram items
- [ ] 5.3 Play URL format: `tg://file/<fileId>?chatId=...&messageId=...`
- [ ] 5.4 `fun getTelegramVod(): Flow<List<MediaItem>>` implemented
- [ ] 5.5 `fun getTelegramSeries(): Flow<List<MediaItem>>` implemented
- [ ] 5.6 `fun getTelegramFeedItems(): Flow<List<MediaItem>>` implemented
- [ ] 5.7 `fun searchTelegramContent(query): Flow<List<MediaItem>>` implemented
- [ ] 5.8 SettingsStore integration (VOD/Series/Feed chat distinction)
- [ ] 5.9 Repository unit tests (ID mapping, URL generation)

### TgContentHeuristics Implementation (Section 2.4)

- [ ] 6.1 File `telegram/parser/TgContentHeuristics.kt` exists
- [ ] 6.2 Multi-format season/episode detection (S01E02, 1x02, "Episode 4")
- [ ] 6.3 Adult content filtering implemented
- [ ] 6.4 Movie/series classification with confidence scoring
- [ ] 6.5 Metadata quality assessment implemented
- [ ] 6.6 `fun classify(parsed, chatTitle, fileName): HeuristicResult` works
- [ ] 6.7 `fun guessSeasonEpisode(text): SeasonEpisode?` works
- [ ] 6.8 Combined evaluation (chat title + filename + caption)
- [ ] 6.9 Unit tests for heuristic classification
- [ ] 6.10 Unit tests for episode parsing (SxxEyy, "Episode 4", etc.)

### TelegramSyncWorker (Turbo-Sync) (Section 4.1)

- [ ] 7.1 File moved to `telegram/work/TelegramSyncWorker.kt`
- [ ] 7.2 `doWork()` loads `SettingsStore`
- [ ] 7.3 Calls `T_TelegramServiceClient.ensureStarted(context, settings)`
- [ ] 7.4 Determines relevant chat IDs (VOD/Series/Feed)
- [ ] 7.5 Parallel sync with `Dispatchers.IO.limitedParallelism(n)`
- [ ] 7.6 Parallelism `n` derived from device profile (CPU cores, device class)
- [ ] 7.7 Per-chat: messages loaded via `T_ChatBrowser.loadMessagesPaged`
- [ ] 7.8 Per-message: `MediaParser.parseMessage` applied
- [ ] 7.9 Per-message: `TgContentHeuristics` sharpens classification
- [ ] 7.10 `TelegramContentRepository` updated with results
- [ ] 7.11 `MODE_ALL` implemented
- [ ] 7.12 `MODE_SELECTION_CHANGED` implemented
- [ ] 7.13 `MODE_BACKFILL_SERIES` implemented
- [ ] 7.14 `TelegramLogRepository.logSyncEvent` used for progress
- [ ] 7.15 `T_TelegramServiceClient.syncState` updated
- [ ] 7.16 Worker scheduled via `SchedulingGateway`
- [ ] 7.17 Worker integration tests (mock TDLib)

---

## Cluster C: Streaming / DataSource

### TelegramDataSource Zero-Copy Implementation (Section 4.2)

- [ ] 8.1 File moved to `telegram/player/TelegramDataSource.kt`
- [ ] 8.2 Constructor injects `T_TelegramServiceClient` and/or `T_TelegramFileDownloader`
- [ ] 8.3 `open(dataSpec)` parses `tg://file/<fileId>?chatId=...&messageId=...`
- [ ] 8.4 Requests in-memory stream/ringbuffer from `T_TelegramFileDownloader`
- [ ] 8.5 `TransferListener.onTransferStart` called correctly
- [ ] 8.6 `read(buffer, offset, length)` reads from in-memory buffer
- [ ] 8.7 Background download continues as needed
- [ ] 8.8 `close()` cancels/releases streams
- [ ] 8.9 `TransferListener.onTransferEnd` called on close
- [ ] 8.10 `DelegatingDataSourceFactory` creates `TelegramDataSource` for `tg://` URLs
- [ ] 8.11 Zero-copy streaming tested with sample video

### Coil 3.x Thumbnail Integration (Section 6.2)

- [ ] 9.1 Coil 3.x dependency added to build.gradle.kts
- [ ] 9.2 Thumbnail generation for Telegram media (via `MediaMetadataRetriever` or Media3)
- [ ] 9.3 `FishTelegramContent` extended to show cover images
- [ ] 9.4 Thumbnail caching implemented
- [ ] 9.5 UI shows thumbnails for Telegram content

---

## Cluster D: UI / Activity Feed / Settings

### TelegramSettingsViewModel Integration (Section 3.4)

- [ ] 10.1 File moved to `telegram/ui/TelegramSettingsViewModel.kt`
- [ ] 10.2 Constructor injects `T_TelegramServiceClient` + `SettingsStore`
- [ ] 10.3 No direct `TdlClient` creation
- [ ] 10.4 `TelegramSettingsState.authState` uses `ServiceClient.authState`
- [ ] 10.5 `isLoadingChats`, `availableChats`, `selectedChats` based on Browser/Settings
- [ ] 10.6 `onPhoneEntered` delegates to `serviceClient.login(phone, null, null)`
- [ ] 10.7 `onCodeEntered` delegates to `serviceClient.login(null, code, null)`
- [ ] 10.8 `onPasswordEntered` delegates to `serviceClient.login(null, null, password)`
- [ ] 10.9 Chat picker uses `serviceClient.listChats(context, limit)`
- [ ] 10.10 Selection written to `SettingsStore` (`TG_SELECTED_*_CHATS_CSV`)
- [ ] 10.11 Settings UI wired to navigation
- [ ] 10.12 End-to-end login flow works (phone → code → password)

### Telegram Activity Feed (Section 5.2)

- [ ] 11.1 `TgActivityEvent` sealed class defined (NewMovie, NewEpisode, NewArchive, ChatUpdated)
- [ ] 11.2 `T_TelegramServiceClient` emits events to `activityEvents` SharedFlow
- [ ] 11.3 File `telegram/ui/feed/TelegramActivityFeedViewModel.kt` exists
- [ ] 11.4 ViewModel listens to `activityEvents`
- [ ] 11.5 ViewModel uses `TelegramContentRepository` to load MediaItems
- [ ] 11.6 ViewModel provides `StateFlow<FeedState>` for UI
- [ ] 11.7 File `telegram/ui/feed/TelegramActivityFeedScreen.kt` exists
- [ ] 11.8 Feed list UI is TV-focusable (DPAD navigation)
- [ ] 11.9 Feed list UI is touch-friendly
- [ ] 11.10 Direct play/navigation from feed items works
- [ ] 11.11 Feed accessible from Start/Library/Settings ("Telegram-Feed" entry)
- [ ] 11.12 Empty state UI when feed is empty
- [ ] 11.13 Error state UI for feed errors

### Chat Picker Enhancements (Section 6.2)

- [ ] 12.1 Drag/reorder UI for chat picker (Compose or custom pattern)
- [ ] 12.2 Chat reordering works with DPAD on TV
- [ ] 12.3 Chat reordering works with touch on Phone/Tablet
- [ ] 12.4 Chat order persisted in SettingsStore

### Jetpack Glance Widgets (Section 6.2)

- [ ] 13.1 Jetpack Glance dependency added
- [ ] 13.2 Widget "New Telegram Films" created
- [ ] 13.3 Widget "New Telegram Series" created
- [ ] 13.4 Widgets use `TelegramContentRepository.getTelegramFeedItems()`
- [ ] 13.5 Widgets update on new content

### UI Layout Components

- [ ] 14.1 `FishTelegramContent` composable exists and works
- [ ] 14.2 `FishTelegramRow` composable exists (if needed)
- [ ] 14.3 TV focus handling works for Telegram rows
- [ ] 14.4 Telegram content displayed on StartScreen
- [ ] 14.5 Telegram content displayed in Library (Films/Series tabs)

---

## Cluster E: Logging / Diagnostics / Quality

### TelegramLogRepository & Log Screen (Section 5.1)

- [ ] 15.1 File `telegram/logging/TelegramLogRepository.kt` exists
- [ ] 15.2 `TgLogEntry` data class defined (timestamp, level, source, message, details)
- [ ] 15.3 In-memory ringbuffer implemented (500 entries)
- [ ] 15.4 `val entries: StateFlow<List<TgLogEntry>>` exposed
- [ ] 15.5 `val events: SharedFlow<TgLogEntry>` exposed
- [ ] 15.6 Internal use of `DiagnosticsLogger` for logcat/file
- [ ] 15.7 Singleton pattern for easy access
- [ ] 15.8 `T_TelegramServiceClient` calls `log(...)`
- [ ] 15.9 `T_TelegramSession` calls `log(...)`
- [ ] 15.10 `T_ChatBrowser` calls `log(...)`
- [ ] 15.11 `T_TelegramFileDownloader` calls `log(...)`
- [ ] 15.12 `TelegramSyncWorker` calls `log(...)`
- [ ] 15.13 `TelegramDataSource` calls `log(...)`
- [ ] 15.14 File `telegram/ui/TelegramLogViewModel.kt` exists
- [ ] 15.15 File `telegram/ui/TelegramLogScreen.kt` exists
- [ ] 15.16 Log screen shows list of log entries
- [ ] 15.17 Log screen has filter by level (DEBUG, INFO, WARN, ERROR)
- [ ] 15.18 Log screen has filter by source
- [ ] 15.19 Log screen is DPAD-compatible (TV navigation)
- [ ] 15.20 Export functionality (share intent) implemented
- [ ] 15.21 Log screen accessible from Settings ("Telegram-Log" entry)
- [ ] 15.22 Short overlays/Snackbars for WARN+ events in StartScreen/Library/Settings

### Quality & Debug Tools (Section 6.1)

- [ ] 16.1 Verify `tdl-coroutines-android` includes arm64-v8a native libs
- [ ] 16.2 Verify `tdl-coroutines-android` includes armeabi-v7a native libs
- [ ] 16.3 No custom `libtdjni.so` in project (b/libtd removed)
- [ ] 16.4 R8/ProGuard rules for `dev.g000sha256.tdl.dto.*` present
- [ ] 16.5 LeakCanary dependency added for debug builds
- [ ] 16.6 LeakCanary configured for Telegram scopes
- [ ] 16.7 `kotlinx-coroutines-debug` added to debug builds
- [ ] 16.8 `System.setProperty("kotlinx.coroutines.debug", "on")` in debug builds
- [ ] 16.9 `androidx.profileinstaller` dependency added
- [ ] 16.10 Profile installer configured for startup optimization
- [ ] 16.11 JetBrains Kover plugin added to build.gradle.kts
- [ ] 16.12 Kover configured for Parser/Repository/Worker coverage
- [ ] 16.13 Kover coverage reports generated successfully

### Lint & Code Quality (Section 6.3)

- [ ] 17.1 Custom lint/detekt rule: "No direct TdlClient access outside telegram.core"
- [ ] 17.2 Custom lint/detekt rule: "No monster methods (> 100 LOC) in Telegram files"
- [ ] 17.3 Custom lint/detekt rule: "Clear Flow naming (*State, *Events)"
- [ ] 17.4 All Telegram files pass detekt checks
- [ ] 17.5 All Telegram files pass ktlint checks

---

## Testing & Verification

### Unit Tests (Section 6.3)

- [ ] 18.1 MediaParser tests: SxxEyy parsing
- [ ] 18.2 MediaParser tests: "Episode 4" parsing
- [ ] 18.3 MediaParser tests: Language tag detection
- [ ] 18.4 TgContentHeuristics tests: Classification accuracy
- [ ] 18.5 TgContentHeuristics tests: Confidence scoring
- [ ] 18.6 TelegramContentRepository tests: ID mapping
- [ ] 18.7 TelegramContentRepository tests: URL generation
- [ ] 18.8 T_TelegramSession tests: Auth state mapping
- [ ] 18.9 T_ChatBrowser tests: Chat/message operations
- [ ] 18.10 T_TelegramServiceClient tests: Core functionality

### Instrumented Tests (Section 6.3)

- [ ] 19.1 TV navigation: StartScreen → Telegram Row → Playback
- [ ] 19.2 Library: Telegram tab "Films" accessible
- [ ] 19.3 Library: Telegram tab "Series" accessible
- [ ] 19.4 Activity Feed: Navigation works
- [ ] 19.5 Activity Feed: Playback from feed works
- [ ] 19.6 Settings: Login flow (phone/code/password)
- [ ] 19.7 Settings: Chat picker interaction

---

## CI/CD & Build Configuration

### Gradle & Dependencies

- [ ] 20.1 `tdl-coroutines-android:5.0.0` dependency present
- [ ] 20.2 Coil 3.x dependency added (if implementing thumbnails)
- [ ] 20.3 Jetpack Glance dependency added (if implementing widgets)
- [ ] 20.4 LeakCanary added to debugImplementation
- [ ] 20.5 kotlinx-coroutines-debug added to debugImplementation
- [ ] 20.6 androidx.profileinstaller added
- [ ] 20.7 Kover plugin applied to build
- [ ] 20.8 All dependencies synchronized and up-to-date

### CI Workflows

- [ ] 21.1 CI workflow builds debug APK successfully
- [ ] 21.2 CI workflow builds release APK successfully
- [ ] 21.3 CI workflow runs unit tests successfully
- [ ] 21.4 CI workflow runs lint/detekt successfully
- [ ] 21.5 CI workflow runs instrumented tests (if applicable)
- [ ] 21.6 CI workflow generates and uploads artifacts
- [ ] 21.7 All CI checks green on latest commit

---

## Documentation & Cleanup

### Documentation Updates

- [ ] 22.1 `tdlibAgent.md` reflects current implementation state
- [ ] 22.2 `TDLIB_TASK_GROUPING.md` updated with completion status
- [ ] 22.3 Code documentation (KDoc) for all public APIs
- [ ] 22.4 README/architecture docs mention TDLib integration
- [ ] 22.5 Migration guide from old to new Telegram classes (if needed)

### Legacy Code Cleanup

- [ ] 23.1 Old `TelegramServiceClient` in `ui/screens` removed (or clearly deprecated)
- [ ] 23.2 Old `TelegramSession` in `telegram/session` removed (or clearly deprecated)
- [ ] 23.3 Old `ChatBrowser` in `telegram/browser` removed (or clearly deprecated)
- [ ] 23.4 Old `TelegramFileDownloader` in `telegram/downloader` removed (or clearly deprecated)
- [ ] 23.5 All import paths updated to new package structure
- [ ] 23.6 No deprecation warnings for Telegram code (except intentional ones)
- [ ] 23.7 Dead code eliminated (unused classes, methods)

### Final Verification

- [ ] 24.1 Full clean build succeeds (`./gradlew clean assembleDebug assembleRelease`)
- [ ] 24.2 All unit tests pass (`./gradlew test`)
- [ ] 24.3 All instrumented tests pass (`./gradlew connectedAndroidTest`)
- [ ] 24.4 Lint/detekt passes (`./gradlew detekt lint`)
- [ ] 24.5 Manual smoke test: Login flow works
- [ ] 24.6 Manual smoke test: Chat picker works
- [ ] 24.7 Manual smoke test: Sync worker runs and indexes content
- [ ] 24.8 Manual smoke test: Telegram content appears on StartScreen
- [ ] 24.9 Manual smoke test: Telegram content playable
- [ ] 24.10 Manual smoke test: Activity feed shows events
- [ ] 24.11 Manual smoke test: Log screen accessible and functional
- [ ] 24.12 Manual smoke test: TV navigation works (DPAD)
- [ ] 24.13 Code review completed and all feedback addressed
- [ ] 24.14 Security scan (CodeQL) shows no critical issues
- [ ] 24.15 PR created: "TDLib Final Review – Wiring, Cleanup, CI & Quality"

---

## Progress Summary

**Total Tasks:** 250+  
**Completed:** 0  
**In Progress:** 0  
**Remaining:** 250+

**Completion:** 0%

---

## Notes

- This checklist should be worked through systematically, cluster by cluster
- Dependencies between clusters: A → (B, C, D, E in parallel)
- Each checkbox should only be marked `[x]` when fully verified
- Update this document with notes for any blockers or deviations
- Keep this as the single source of truth for completion status

---

_Last Updated: 2025-11-20_
