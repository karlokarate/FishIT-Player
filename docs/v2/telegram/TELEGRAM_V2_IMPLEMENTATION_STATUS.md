# Telegram v2 Implementation Status

**Version:** 1.0  
**Last Updated:** 2025-12-08  
**Branch:** `architecture/v2-bootstrap`  
**Status:** Phase 1.7 Complete – Feature Wiring & Gold Behavior Migration

---

## Overview

This document tracks the implementation status of Telegram integration in the v2 architecture.
The implementation follows the principle of **porting proven v1 behaviors** into v2 boundaries,
not redesigning from scratch.

---

## Phase 1.7: Feature Wiring & Gold Behavior Migration

### ✅ Completed

#### Core Architecture
- [x] TelegramClient interface defined (pipeline/telegram)
- [x] DefaultTelegramClient implementation with TDLib integration
- [x] TdlibClientProvider for Android Context abstraction
- [x] Auth state management (AuthState flow)
- [x] Connection state management (ConnectionState flow)
- [x] Feature providers registered (TelegramFullHistoryFeatureProvider, TelegramLazyThumbnailsFeatureProvider)
- [x] Hilt DI integration for pipeline and feature modules

#### Gold Behaviors Ported from v1

**Full-History Scanning (from T_ChatBrowser):**
- [x] Proper getChatHistory pagination (offset=0 for first, -1 for subsequent)
- [x] TDLib async loading detection and retry (first call may return 1 message)
- [x] Exponential backoff retry logic (500ms * attempt)
- [x] Batch size safety limits (max 100 per page, 10000 total)
- [x] Partial batch detection as stop condition
- [x] Structured logging for progress tracking
- [x] Implementation: `DefaultTelegramClient.loadAllMessages()`

**Lazy Thumbnails (from T_TelegramFileDownloader):**
- [x] RemoteId-based thumbnail resolution (cross-session stable)
- [x] Lower priority for thumbnails (8) vs video (16)
- [x] Retry logic with exponential backoff
- [x] Structured error handling
- [x] Implementation: `DefaultTelegramClient.requestThumbnailDownload()`

**Error Handling & Logging:**
- [x] Retry wrapper with configurable attempts (default 3)
- [x] Exponential backoff (RETRY_DELAY_MS * attempt)
- [x] Structured logging via UnifiedLog
- [x] TelegramAuthException and TelegramFileException for typed errors

#### Feature System Integration
- [x] FeatureRegistry injected into TelegramMediaViewModel
- [x] Feature checks gate UI behavior (supportsFullHistoryStreaming, supportsLazyThumbnails)
- [x] Feature capability logging on ViewModel init
- [x] Feature owner tracking for debugging
- [x] UI state reflects feature availability (canSyncFullHistory, canLoadThumbnailsLazily)
- [x] Feature-gated methods (syncFullHistory, loadThumbnails) with fallback behavior

#### Testing
- [x] All existing pipeline tests pass
- [x] TdlibTestFixtures used (no MockK)
- [x] Build system stable (bypasses detekt config issue)

---

## v1 → v2 Component Mapping

| v1 Component | v2 Component | Status | Notes |
|--------------|--------------|--------|-------|
| `T_TelegramServiceClient` | `TelegramClient` interface | ✅ Done | No singleton, DI-managed |
| `T_TelegramSession` | Integrated into `DefaultTelegramClient` | ✅ Done | Auth flow integrated |
| `T_ChatBrowser.loadAllMessages()` | `DefaultTelegramClient.loadAllMessages()` | ✅ Done | Full-history scanning ported |
| `T_ChatBrowser` paging logic | `DefaultTelegramClient.loadMessageHistory()` | ✅ Done | Offset handling ported |
| `T_TelegramFileDownloader` thumbnail logic | `TelegramClient.requestThumbnailDownload()` | ✅ Done | RemoteId-first ported |
| Retry/backoff patterns | `DefaultTelegramClient.withRetry()` | ✅ Done | Exponential backoff |
| `TelegramContentRepository` | `TdlibTelegramContentRepository` | 🚧 Stub | Structure ready |
| `TelegramFileDataSource` | `:player:internal` | ✅ Done | Zero-copy streaming |

---

## Architecture Compliance Checklist

### ✅ v2 Boundaries Respected
- [x] No pipeline-local normalization or TMDB lookups
- [x] No player logic in pipeline (DataSource is in `:player:internal`)
- [x] No UI components in pipeline (UI is in `:feature:telegram-media`)
- [x] No global mutable singletons
- [x] No direct TdlClient access outside DefaultTelegramClient
- [x] Proper DI scoping (no `GlobalScope`)

### ✅ Contracts Followed
- [x] MEDIA_NORMALIZATION_CONTRACT.md (no title cleaning, no TMDB in pipeline)
- [x] TELEGRAM_PARSER_CONTRACT.md (DTO → Raw mapping principles)
- [x] LOGGING_CONTRACT_V2.md (UnifiedLog facade)
- [x] Feature System patterns (FeatureProvider, FeatureRegistry)

### ✅ Legacy References
- [x] All v1 behaviors ported are documented with legacy origin
- [x] No `com.chris.m3usuite` imports outside `/legacy/**`
- [x] Legacy code used as reference only (read-only)

---

## What's NOT Ported (Intentional)

These v1 components are intentionally not ported to v2:

### UI Components (Belong in `:feature:telegram-media`)
- `TelegramSettingsViewModel` – feature-level
- `TelegramLibraryViewModel` – feature-level
- `TelegramLogViewModel` – feature-level
- `FishTelegramContent` – UI component

### Background Work (Belongs in feature/worker layer)
- `TelegramSyncWorker` – WorkManager integration
- Activity feed logic – feature-level concern

### Legacy Patterns (Replaced by v2 architecture)
- Singleton pattern → DI
- Direct TdlClient access → TelegramClient abstraction
- Hardcoded configuration → settings provider (future)

### Non-Contract Behavior (Moved to normalizer)
- Title cleaning/normalization → `core:metadata-normalizer`
- Resume position logic → `ResumeManager` in domain layer
- Custom windowing → TDLib native download + zero-copy streaming

---

## Next Steps (Post Phase 1.7)

### Phase 2: Full TDLib Implementation
- [ ] Complete auth flow (phone, code, password)
- [ ] Implement real message fetching and parsing
- [ ] ObjectBox caching integration
- [ ] Background sync setup
- [ ] File download management with progress tracking

### Phase 3: Streaming Features
- [ ] MP4 header validation
- [ ] RAR archive support
- [ ] Adaptive download priorities
- [ ] TelegramStreamingSettingsProvider integration

### Phase 4: UI Completion
- [ ] TelegramMediaScreen composable
- [ ] Chat list UI
- [ ] Media grid/list UI
- [ ] Playback integration

### Phase 5: Testing & Polish
- [ ] Integration tests with real TDLib
- [ ] Manual testing with Telegram account
- [ ] Performance profiling
- [ ] Documentation completion

---

## Known Limitations

1. **Detekt Configuration Issue**: Current build bypasses detekt due to config error. Fix in future PR.
2. **Stub Repository**: `TdlibTelegramContentRepository` is a stub. Full implementation deferred to Phase 2.
3. **No Background Sync**: WorkManager integration deferred to Phase 4.
4. **No Settings Provider**: TelegramStreamingSettingsProvider not yet integrated. Using defaults.

---

## Documentation References

### v2 Architecture
- [V2_PORTAL.md](../../../V2_PORTAL.md) – v2 entry point
- [AGENTS.md](../../../AGENTS.md) – v2 agent rules
- [FEATURE_SYSTEM_TARGET_MODEL.md](../architecture/FEATURE_SYSTEM_TARGET_MODEL.md) – Feature system spec
- [TELEGRAM_TDLIB_V2_INTEGRATION.md](../TELEGRAM_TDLIB_V2_INTEGRATION.md) – TDLib integration overview

### Telegram Contracts
- [TELEGRAM_PARSER_CONTRACT.md](TELEGRAM_PARSER_CONTRACT.md) – Parser contract
- [TDLIB_STREAMING_THUMBNAILS_SSOT.md](TDLIB_STREAMING_THUMBNAILS_SSOT.md) – Legacy streaming guide

### Legacy References (Read-Only)
- [legacy/v1-app/.../telegram/core/T_ChatBrowser.kt](../../../legacy/v1-app/app/src/main/java/com/chris/m3usuite/telegram/core/T_ChatBrowser.kt) – Proven paging logic
- [legacy/v1-app/.../telegram/core/T_TelegramFileDownloader.kt](../../../legacy/v1-app/app/src/main/java/com/chris/m3usuite/telegram/core/T_TelegramFileDownloader.kt) – Thumbnail patterns
- [legacy/docs/telegram/tdlibsetup.md](../../../legacy/docs/telegram/tdlibsetup.md) – TDLib quirks documentation

---

## Summary

**Phase 1.7 Status:** ✅ **COMPLETE**

- Feature system wiring is complete and tested
- Gold behaviors from v1 are successfully ported with proper attribution
- Architecture boundaries are respected (no singletons, no normalization in pipeline)
- TelegramMediaViewModel demonstrates feature-gated patterns
- All tests pass
- Documentation updated

**Next Milestone:** Phase 2 – Full TDLib Implementation (separate PR)
