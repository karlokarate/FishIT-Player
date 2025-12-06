# Phase 2 Task 3 (P2-T3) Follow-up Notes

**Agent:** telegram-agent  
**Task:** Telegram Pipeline STUB Implementation  
**Completed:** 2025-12-06  
**Status:** ✅ COMPLETE

## Summary

Successfully implemented Phase 2 Task 3 (P2-T3) Telegram Pipeline STUB according to specifications:
- Defined domain models (TelegramMediaItem, TelegramChatSummary, TelegramMessageStub)
- Defined repository interfaces (TelegramContentRepository, TelegramPlaybackSourceFactory)
- Implemented stub repositories returning deterministic empty/mock data
- Created mapping utilities for ObxTelegramMessage (structure only)
- Implemented comprehensive unit tests (41 tests, 100% passing)
- NO real TDLib integration (stub only)

## Deliverables

### 1. Domain Models (`pipeline/telegram/model/`)
- **TelegramMediaItem.kt** - Complete domain model with all fields from ObxTelegramMessage
  - Includes helper methods: `toTelegramUri()`, `isPlayable()`
  - Supports series metadata (season, episode, title)
  - Maps to Telegram URI scheme: `tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>`

- **TelegramChatSummary.kt** - Chat overview model
  - Basic chat metadata (title, type, media count)

- **TelegramMessageStub.kt** - Stub message representation
  - Companion object with `empty()` and `withMedia()` factory methods
  - Used for testing without TDLib

### 2. Repository Interfaces (`pipeline/telegram/repository/`)
- **TelegramContentRepository.kt** - Content access interface
  - Methods: getAllMediaItems, getMediaItemsByChat, getRecentMediaItems
  - Supports: search, series filtering, pagination
  - Includes: refresh() method for future sync

- **TelegramPlaybackSourceFactory.kt** - Playback conversion interface
  - Converts TelegramMediaItem → PlaybackContext
  - Validates playability (fileId + mimeType required)

### 3. Stub Implementations (`pipeline/telegram/stub/`)
- **StubTelegramContentRepository.kt**
  - Default: returns empty lists/null
  - `withMockData()`: returns 2 deterministic mock items + 1 chat
  - Supports pagination (offset, limit)

- **StubTelegramPlaybackSourceFactory.kt**
  - Creates PlaybackContext with correct PlaybackType.TELEGRAM
  - Builds subtitle from series info (S1E5 format)
  - Populates extras with all metadata
  - Validates playability

### 4. Mapping Utilities (`pipeline/telegram/mapper/`)
- **TelegramMappers.kt**
  - `fromObxTelegramMessage()` - ObxTelegramMessage → TelegramMediaItem
  - `toObxTelegramMessage()` - TelegramMediaItem → ObxTelegramMessage
  - `extractTitle()` - Smart title extraction with fallbacks
  - Preserves all fields during round-trip conversion

### 5. Unit Tests (`pipeline/telegram/test/`)
- **StubTelegramContentRepositoryTest.kt** (16 tests)
  - Empty stub behavior validation
  - Mock data stub validation
  - Pagination tests

- **StubTelegramPlaybackSourceFactoryTest.kt** (15 tests)
  - PlaybackContext creation
  - URI generation
  - Subtitle formatting (series + standalone)
  - Extras population
  - Playability validation

- **TelegramMappersTest.kt** (10 tests)
  - Field mapping validation
  - Title extraction logic
  - Round-trip conversion
  - Null handling

## Test Results

```
41 tests completed, 0 failed
- StubTelegramContentRepositoryTest: 16/16 ✅
- StubTelegramPlaybackSourceFactoryTest: 15/15 ✅
- TelegramMappersTest: 10/10 ✅
```

## Build Verification

```bash
# Compilation successful
./gradlew :pipeline:telegram:compileDebugKotlin
BUILD SUCCESSFUL

# All tests passing
./gradlew :pipeline:telegram:test
BUILD SUCCESSFUL in 6s
41 tests completed, 0 failed
```

## Git Scope Verification

✅ Only `pipeline/telegram/**` files modified/created:
```
M  pipeline/telegram/build.gradle.kts
A  pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/mapper/TelegramMappers.kt
A  pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramChatSummary.kt
A  pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramMediaItem.kt
A  pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramMessageStub.kt
A  pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/repository/TelegramContentRepository.kt
A  pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/repository/TelegramPlaybackSourceFactory.kt
A  pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/stub/StubTelegramContentRepository.kt
A  pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/stub/StubTelegramPlaybackSourceFactory.kt
A  pipeline/telegram/src/test/java/com/fishit/player/pipeline/telegram/mapper/TelegramMappersTest.kt
A  pipeline/telegram/src/test/java/com/fishit/player/pipeline/telegram/stub/StubTelegramContentRepositoryTest.kt
A  pipeline/telegram/src/test/java/com/fishit/player/pipeline/telegram/stub/StubTelegramPlaybackSourceFactoryTest.kt
A  docs/agents/phase2/agent-telegram-agent_P2-T3_progress.md
A  docs/agents/phase2/FOLLOWUP_P2-T3_by-telegram-agent.md
```

✅ No changes to forbidden areas:
- ❌ core/persistence/** (frozen)
- ❌ telegram/tdlib/, settings/, datastore/, player/, feature/**
- ❌ Real TDLib integration

## Integration Notes for Phase 2 Continuation

### Media Normalization & Canonical Identity Integration

**IMPORTANT:** All future Telegram pipeline work MUST comply with the centralized media normalization and TMDB resolution contract.

#### Normalization Contract Overview

The v2 architecture introduces a **centralized normalization layer** that handles:
- Title cleaning and normalization
- Technical tag stripping (resolution, codec, release group)
- Scene-style naming pattern parsing
- TMDB/IMDB/TVDB lookup and identity resolution
- Cross-pipeline canonical identity assignment

**Architecture:**
- **Data types** (`RawMediaMetadata`, `NormalizedMediaMetadata`, `ExternalIds`, `SourceType`) → defined in `:core:model`
- **Normalization behavior** (`MediaMetadataNormalizer`, `TmdbMetadataResolver`) → implemented in `:core:metadata-normalizer`

**Pipelines (including Telegram) provide RAW metadata ONLY.**

#### Key Requirements for Telegram Pipeline:

1. **Telegram will provide RawMediaMetadata mapping:**
   - Future phase will implement `TelegramMediaItem.toRawMediaMetadata()` to feed the centralizer.
   - **Structure documented in:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/mapper/TelegramRawMetadataContract.kt`
   - The mapping passes through raw titles **without any modification**.
   - Priority: title > episodeTitle > caption > fileName
   - **CRITICAL:** Types like `RawMediaMetadata`, `ExternalIds`, `SourceType` are NOT and MUST NOT be defined in `:pipeline:telegram`
   - These types will be defined in `:core:model` (Phase 3)
   - Normalization behavior implemented in `:core:metadata-normalizer` (Phase 3)

2. **extractTitle() MUST stay a simple field priority selector:**
   - The existing `extractTitle()` function in `TelegramMappers.kt` only selects the best source field.
   - It does **NOT** strip resolution tags, codec info, or scene-style naming patterns.
   - It does **NOT** clean, normalize, or transform titles in any way.
   - All title cleaning is handled centrally by normalization behavior in `:core:metadata-normalizer`.

3. **NO normalization or TMDB logic in `:pipeline:telegram`:**
   - ❌ Do NOT implement title cleaning or heuristics
   - ❌ Do NOT strip technical tags (1080p, x264, BluRay, etc.)
   - ❌ Do NOT perform TMDB/IMDB/TVDB searches
   - ❌ Do NOT attempt to match or unify media across sources
   - ❌ Do NOT compute canonical identity
   - ✅ DO provide raw source fields as-is

4. **TDLib is purely for Telegram data access:**
   - TDLib integration focuses on:
     - Message sync and browsing
     - File downloads and streaming
     - Chat management
   - Canonical identity and cross-pipeline unification are handled by the shared normalizer layer.

5. **Telegram does NOT provide external IDs:**
   - Telegram messages typically don't include TMDB/IMDB/TVDB IDs.
   - The `externalIds` field in `RawMediaMetadata` will be empty for Telegram sources.
   - TMDB resolution will be done centrally based on normalized titles.

6. **Reference documentation:**
   - `v2-docs/MEDIA_NORMALIZATION_AND_UNIFICATION.md` - Architecture overview
   - `v2-docs/MEDIA_NORMALIZATION_CONTRACT.md` - Formal rules and pipeline responsibilities (AUTHORITATIVE)
   - `pipeline/telegram/mapper/TelegramRawMetadataContract.kt` - Structure-only documentation (NOT implementation)

**Module Ownership:**
- **Types (`:core:model`):**
  - `RawMediaMetadata` - Data type for raw pipeline metadata
  - `NormalizedMediaMetadata` - Data type for normalized metadata
  - `ExternalIds` - External identifier collection
  - `SourceType` - Pipeline source enum
- **Behavior (`:core:metadata-normalizer`):**
  - `MediaMetadataNormalizer` - Normalization logic
  - `TmdbMetadataResolver` - TMDB lookup and enrichment

The Telegram pipeline only references these types in documentation, never defines them locally.

**Contract Compliance ensures:**
- Cross-pipeline resume tracking (same movie from Telegram/Xtream/IO = same resume point)
- Unified detail screens (all versions of a movie/episode grouped together)
- Version selection across pipelines (quality, language, subtitles)
- Predictable TMDB-first canonical identity
- Clean separation of concerns (pipeline = raw data, types in `:core:model`, behavior in `:core:metadata-normalizer`)

---

### Ready for Next Phase (P2-T4)
The stub implementation provides:
1. **Complete interface contracts** - Ready for real implementations
2. **Test infrastructure** - Tests can be extended for real TDLib
3. **Domain model stability** - UI layers can integrate now using stubs

### Real TDLib Integration (Future Task)
When implementing real TDLib integration:

1. **Replace Stub Implementations:**
   - Create `TdLibTelegramContentRepository` implementing `TelegramContentRepository`
   - Create `TdLibTelegramPlaybackSourceFactory` implementing `TelegramPlaybackSourceFactory`

2. **Add TDLib Dependencies:**
   ```kotlin
   // pipeline/telegram/build.gradle.kts
   implementation("org.drinkless:tdlib:...")
   implementation("io.github.g00sha:tdlib-coroutines:...")
   ```

3. **Port v1 Components (from .github/tdlibAgent.md):**
   - T_TelegramServiceClient (auth, connection, sync)
   - T_TelegramFileDownloader (download queue)
   - TelegramFileDataSource (Media3 zero-copy streaming)
   - TelegramStreamingSettingsProvider (streaming config)

4. **Implement Real Repository:**
   - Query ObxTelegramMessage via ObjectBox
   - Use TelegramMappers for conversion
   - Integrate with TDLib for live queries

5. **Wire to Feature Layer:**
   - `:feature:telegram-media` can use `TelegramContentRepository` interface
   - No code changes needed in feature layer when swapping stub → real

### Hilt/DI Integration (Future)
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object TelegramPipelineModule {
    @Provides
    @Singleton
    fun provideTelegramContentRepository(
        // inject real dependencies
    ): TelegramContentRepository {
        // return real implementation
        // return TdLibTelegramContentRepository(...)
    }
}
```

### ObxTelegramMessage Integration
The mapping structure is ready:
- All fields from `ObxTelegramMessage` are mapped
- Title extraction follows priority: title > episodeTitle > caption > fileName
- Series metadata fully supported
- Round-trip conversion tested

### Telegram URI Scheme
Standardized format implemented:
```
tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>
```

This matches v1 TelegramFileDataSource expectations.

## Architecture Compliance

✅ **Phase Boundaries Respected:**
- Only P2-T3 scope implemented
- No Phase 3/4 features added prematurely

✅ **Module Dependencies:**
- `:pipeline:telegram` depends on `:core:model`, `:core:persistence`, `:infra:logging`
- No reverse dependencies
- No forbidden dependencies (no UI, no player)

✅ **Contract-First Design:**
- Interfaces defined first
- Stub implementations for testing
- Real implementations can be swapped in later

✅ **Test-Driven:**
- 41 comprehensive unit tests
- 100% pass rate
- Tests validate structure, not behavior (stub phase)

## Files Created/Modified

### Created (14 files)
1. `docs/agents/phase2/agent-telegram-agent_P2-T3_progress.md`
2. `docs/agents/phase2/FOLLOWUP_P2-T3_by-telegram-agent.md`
3. `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramMediaItem.kt`
4. `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramChatSummary.kt`
5. `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramMessageStub.kt`
6. `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/repository/TelegramContentRepository.kt`
7. `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/repository/TelegramPlaybackSourceFactory.kt`
8. `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/stub/StubTelegramContentRepository.kt`
9. `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/stub/StubTelegramPlaybackSourceFactory.kt`
10. `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/mapper/TelegramMappers.kt`
11. `pipeline/telegram/src/test/java/com/fishit/player/pipeline/telegram/stub/StubTelegramContentRepositoryTest.kt`
12. `pipeline/telegram/src/test/java/com/fishit/player/pipeline/telegram/stub/StubTelegramPlaybackSourceFactoryTest.kt`
13. `pipeline/telegram/src/test/java/com/fishit/player/pipeline/telegram/mapper/TelegramMappersTest.kt`

### Modified (1 file)
1. `pipeline/telegram/build.gradle.kts` (added test dependencies)

### Total Impact
- ~650 lines of production code
- ~400 lines of test code
- 14 new files
- 1 modified file

## Recommendations

1. **UI Integration:** `:feature:telegram-media` can now integrate using stub repository
2. **Parallel Development:** Real TDLib implementation can proceed independently
3. **Testing Strategy:** Keep stub tests for regression; add integration tests for real impl
4. **Documentation:** Update ARCHITECTURE_OVERVIEW_V2.md with Telegram pipeline details

## Notes

- Stub implementation is fully functional and deterministic
- All tests pass without any TDLib dependencies
- Ready for integration with `:feature:telegram-media` UI layer
- Real TDLib integration follows in subsequent Phase 2 tasks
- ObxTelegramMessage structure is respected and mapped correctly
