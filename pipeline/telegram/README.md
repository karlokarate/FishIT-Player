# Telegram Media Pipeline Module

**Phase:** Phase 2 Stub Implementation  
**Status:** Stub interfaces and models only

## Overview

This module provides interface-based stubs for the Telegram media content pipeline. It defines domain models and repository interfaces for accessing Telegram media content, but does not implement actual TDLib integration (that's Phase 3+).

## Architecture

This is a Phase 2 stub module that:
- Defines domain models (`TelegramMediaItem`, `TelegramChat`, `TelegramMessage`)
- Defines repository interfaces for content access
- Provides stub implementations that return deterministic empty/mock data
- Maps to v1's `ObxTelegramMessage` entity structure (read-only reference)
- Supports conversion to `PlaybackContext` for playback domain integration

## Package Structure

- `model/` - Domain models for Telegram content
- `repository/` - Repository interfaces and stub implementations
- `download/` - Download manager interface and stub
- `streaming/` - Streaming settings provider interface and stub
- `source/` - Playback source factory for Media3 integration
- `ext/` - Extension functions for model mapping

## Phase 2 Constraints

- NO actual TDLib calls (stub implementations only)
- NO threading or coroutine complexity (simple suspend functions)
- NO production data flows (Phase 3+ will wire real implementation)
- Read-only reference to v1 code; no modifications to legacy modules

## Future Work (Phase 3+)

- Real TDLib integration via T_TelegramServiceClient
- Windowed zero-copy streaming for large files
- Background sync workers for content updates
- Content heuristics for movie/series classification
- Integration with :core:persistence for ObjectBox storage

## Testing

Run unit tests:
```bash
./gradlew :pipeline:telegram:testDebugUnitTest
```

## Key Classes

- `TelegramMediaItem` - Domain model for playable Telegram media
- `TelegramContentRepository` - Interface for content access
- `TelegramPlaybackSourceFactory` - Factory for tg:// URL generation
- `TelegramExtensions.kt` - Extension functions for PlaybackContext conversion
