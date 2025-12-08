# Telegram TDLib v2 Integration

This document describes the TDLib integration in the v2 architecture, including what was ported from v1 and how it fits into the v2 module structure.

## Overview

The TDLib integration has been ported from v1 to v2 with adaptations to respect v2 architecture boundaries and contracts. The integration uses g00sha's tdlib-coroutines library and follows the principle of separating concerns across modules.

## v1 → v2 Component Mapping

### Core TDLib Integration (`:pipeline:telegram`)

| v1 Component | v2 Component | Status | Notes |
|--------------|--------------|--------|-------|
| `T_TelegramServiceClient` | `TelegramTdlibClient` interface + `TdlibTelegramClient` impl | Phase 2 Stub | Interface defined, implementation stubbed for Phase 2 |
| `T_TelegramSession` | Integrated into `TdlibTelegramClient` | Phase 2 Stub | Auth flow management integrated |
| `T_ChatBrowser` | Integrated into `TdlibTelegramClient` | Phase 2 Stub | Media fetching methods defined |
| `T_TelegramFileDownloader` | `TelegramTdlibClient.ensureFileReady()` | Phase 2 Stub | File download management simplified |
| `TelegramContentRepository` | `TdlibTelegramContentRepository` | Phase 2 Stub | Repository implementation using TDLib client |

### Streaming & Player (`:player:internal`)

| v1 Component | v2 Component | Status | Notes |
|--------------|--------------|--------|-------|
| `TelegramFileDataSource` | `TelegramFileDataSource` | Implemented | Zero-copy streaming via FileDataSource delegation |
| `TelegramStreamingSettingsProvider` | Not yet ported | Deferred | Settings provider to be added in later phase |
| Integration with ExoPlayer | `InternalPlaybackSourceResolver` | Updated | Recognizes `tg://` URIs for Telegram content |

### Persistence (`:core:persistence`)

| v1 Component | v2 Component | Status | Notes |
|--------------|--------------|--------|-------|
| `ObxTelegramMessage` | Already exists | Reused | ObjectBox entity reused from v1 |
| Resume logic | Delegated to `ResumeManager` | Architecture | Resume handled by domain layer, not TDLib layer |

## Architecture Principles

### 1. Module Boundaries

- **`:pipeline:telegram`**: TDLib client abstraction, repository interfaces, domain models
  - NO player logic (DataSource belongs in player)
  - NO UI components (belong in feature modules)
  - NO resume/caching business logic (delegated to domain/persistence)

- **`:player:internal`**: Telegram DataSource for streaming
  - Uses TDLib client via abstraction
  - Delegates to FileDataSource for zero-copy streaming
  - Integrated with InternalPlaybackSourceResolver

- **`:core:persistence`**: ObjectBox entities and persistence
  - Reuses v1 `ObxTelegramMessage`
  - Resume positions managed by `ResumeManager`

### 2. Media Normalization Contract

The Telegram pipeline follows `MEDIA_NORMALIZATION_CONTRACT.md`:

- **NO title cleaning or normalization** in pipeline code
- **NO TMDB lookups** in pipeline
- **NO cross-pipeline identity decisions** in pipeline
- Raw metadata provided via `TelegramMediaItem.toRawMediaMetadata()` (future implementation)
- All normalization delegated to `:core:metadata-normalizer`

### 3. RemoteId-First Semantics

Telegram URIs use RemoteId-first format for cross-session stability:

```
tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>&remoteId=<remoteId>&uniqueId=<uniqueId>
```

- `fileId`: Session-specific TDLib file ID (fast path)
- `remoteId`: Stable remote ID (cross-session)
- Resolution priority: fileId → remoteId resolution

### 4. Zero-Copy Streaming

The streaming architecture avoids unnecessary data copying:

1. TDLib downloads file to its cache directory (unavoidable)
2. `TelegramFileDataSource` ensures file is ready via TDLib
3. Delegates to Media3's `FileDataSource` for actual I/O
4. No ByteArray buffers, no custom position tracking
5. ExoPlayer/FileDataSource handles seeking and scrubbing

## Phase 2 Status

### What's Implemented ✅

1. **TDLib Abstraction Layer**
   - `TelegramTdlibClient` interface defining media-focused API
   - `TdlibTelegramClient` stub implementation (structure only)
   - Auth state and connection state flows

2. **Repository Integration**
   - `TdlibTelegramContentRepository` stub implementation
   - Follows existing `TelegramContentRepository` interface
   - Structure ready for TDLib integration

3. **Player Integration**
   - `TelegramFileDataSource` implemented for zero-copy streaming
   - `TelegramFileDataSourceFactory` for DataSource creation
   - `InternalPlaybackSourceResolver` updated to recognize `tg://` URIs

4. **Dependencies**
   - `tdlib-coroutines-android:5.0.0` added to `:pipeline:telegram`
   - Player module dependency on pipeline added
   - UnifiedLog created for logging

5. **Build System**
   - All modules compile successfully
   - Dependencies configured correctly
   - No circular dependencies

### What's Stubbed (Future Work) 🚧

1. **TDLib Client Implementation**
   - Auth flow (phone, code, password)
   - Message fetching and parsing
   - File download management
   - Chat listing

2. **Repository Operations**
   - Real TDLib query integration
   - ObjectBox caching
   - Background sync

3. **Streaming Features**
   - MP4 header validation
   - RAR archive support
   - Adaptive download priorities

4. **Configuration**
   - TelegramStreamingSettingsProvider
   - User-configurable settings

## Intentionally Not Ported

The following v1 features are intentionally not ported to v2:

### 1. UI Components
- `TelegramSettingsViewModel` → belongs in `:feature:telegram-media`
- `TelegramLibraryViewModel` → belongs in `:feature:telegram-media`
- `TelegramLogViewModel` → feature-level concern
- `FishTelegramContent` → feature UI component

### 2. Background Sync
- `TelegramSyncWorker` → WorkManager integration is feature-level
- Activity feed → feature-level concern
- Live update handling → feature-level

### 3. Legacy Patterns
- Singleton pattern → replaced with DI
- Direct TdlClient access → replaced with abstraction
- Hardcoded configuration → will use settings from feature layer

### 4. Non-Contract Behavior
- Title cleaning/normalization → delegated to metadata normalizer
- Resume position logic in TDLib layer → moved to ResumeManager
- Custom windowing → TDLib native download used instead

## Dependencies

### `:pipeline:telegram`
```kotlin
implementation("dev.g000sha256:tdl-coroutines-android:5.0.0")
implementation(project(":core:model"))
implementation(project(":core:persistence"))
implementation(project(":infra:logging"))
```

### `:player:internal`
```kotlin
implementation("dev.g000sha256:tdl-coroutines-android:5.0.0")
implementation(project(":pipeline:telegram"))
implementation("androidx.media3:media3-datasource:1.5.1")
```

## Testing Strategy

### Unit Tests (Future)
- Mock `TelegramTdlibClient` for repository tests
- Test DataSource URI parsing and file resolution
- Test playback source resolution for Telegram URIs

### Integration Tests (Future)
- Test TDLib client lifecycle
- Test file download and streaming
- Test auth flow (manual/interactive)

## Next Steps

To complete the TDLib integration:

1. **Implement Real TDLib Client** (Priority 1)
   - Complete auth flow implementation
   - Message fetching and parsing
   - File download management
   - Error handling and recovery

2. **Repository Implementation** (Priority 2)
   - Real TDLib query integration
   - ObjectBox caching integration
   - Background sync setup

3. **Streaming Features** (Priority 3)
   - MP4 header validation
   - RAR archive support
   - Streaming settings provider

4. **Testing** (Priority 4)
   - Unit tests for client and repository
   - Integration tests for streaming
   - Manual testing with real Telegram account

## References

- v1 TDLib integration: `.github/tdlibAgent.md`
- v2 Architecture: `docs/v2/ARCHITECTURE_OVERVIEW_V2.md`
- Media normalization: `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md`
- Telegram pipeline: `legacy/docs/agents/phase2/FOLLOWUP_P2-T3_by-telegram-agent.md`
- g00sha tdlib-coroutines: https://github.com/G00fY2/tdlib-coroutines
