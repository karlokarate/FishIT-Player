# Phase 2 Parallelization Plan

**Date:** 2025-12-05  
**Branch:** `architecture/v2-bootstrap`  
**Phase:** Phase 2 – Pipeline Stubs & Core Persistence Implementation

---

## Executive Summary

This document defines the parallelization strategy for Phase 2 of the FishIT Player v2 architecture implementation. Phase 2 focuses on implementing core persistence (ObjectBox) and pipeline stub modules (Xtream, Telegram, IO, Audiobook) while maintaining strict module isolation to enable safe parallel development.

**Key Outcomes:**
- 8 distinct tasks with clearly defined write scopes
- 4 tasks can run fully in parallel (P2-T2, P2-T3, P2-T4, P2-T5)
- 3 sequential checkpoints for integration and validation
- Zero merge conflicts through strict module-scoped write access

---

## Phase 2 Context

### Goals
Phase 2 establishes the foundation for all content pipelines in FishIT Player v2:

1. **Core Persistence** – Port ObjectBox from v1 and implement v2 repository interfaces
2. **Pipeline Stubs** – Create interface-based stubs for all 4 pipelines (Xtream, Telegram, IO, Audiobook)
3. **Playback Domain Integration** – Wire pipeline interfaces into playback domain for future use
4. **Build & Quality Validation** – Ensure all modules compile and pass quality gates

### Modules in Scope

**Core Modules:**
- `:core:persistence` – ObjectBox entities, repositories, DataStore wrappers
- `:core:model` – Shared domain models and types

**Pipeline Modules (stubs only):**
- `:pipeline:xtream` – Xtream/IPTV content pipeline interfaces
- `:pipeline:telegram` – Telegram media pipeline interfaces
- `:pipeline:io` – Local file/network IO pipeline interfaces
- `:pipeline:audiobook` – Audiobook pipeline interfaces

**Integration Modules:**
- `:playback:domain` – Minimal updates to support pipeline integration
- `:player:internal` – Minimal updates for pipeline-aware source resolution

### Out of Scope
- Full pipeline implementations (deferred to Phase 3+)
- UI implementations in `:feature:*` modules
- Firebase integration (Phase 5+)
- Production data flows (Phase 3+)

---

## Task Breakdown & Dependencies

### Task Definitions

#### P2-T1: Core Persistence
**Description:** Port ObjectBox from v1 and implement v2 repository interfaces

**Primary Write Scope:**
- `:core:persistence/`

**Read-Only Dependencies:**
- `:core:model/` (may define interfaces that persistence implements)
- `app/src/main/java/com/chris/m3usuite/data/obx/` (v1 ObjectBox entities for reference)

**Deliverables:**
- Port `ObxStore` singleton pattern
- Port all v1 ObjectBox entities: `ObxCategory`, `ObxLive`, `ObxVod`, `ObxSeries`, `ObxEpisode`, `ObxEpgNowNext`, `ObxProfile`, `ObxProfilePermissions`, `ObxResumeMark`, `ObxTelegramMessage`
- Implement repository interfaces: `ProfileRepository`, `EntitlementRepository`, `LocalMediaRepository`, `SubtitleStyleStore`, `ResumeRepository`
- DataStore wrappers for preferences
- Unit tests for core repository logic

**Blocking Dependencies:** None (can start immediately)

---

#### P2-T2: Xtream Pipeline Stub
**Description:** Create interface-based stub for Xtream/IPTV pipeline

**Primary Write Scope:**
- `:pipeline:xtream/`

**Read-Only Dependencies:**
- `:core:model/` (use existing `PlaybackType`, `PlaybackContext`)

**Deliverables:**
- Domain models: `XtreamVodItem`, `XtreamSeriesItem`, `XtreamEpisode`, `XtreamChannel`, `XtreamEpgEntry`
- Interfaces: `XtreamCatalogRepository`, `XtreamLiveRepository`, `XtreamPlaybackSourceFactory`
- Stub implementations returning empty/mock data
- Package documentation (`package-info.kt`)
- Helper: `XtreamVodItem.toPlaybackContext(): PlaybackContext`

**Blocking Dependencies:** None (can run in parallel with P2-T1)

---

#### P2-T3: Telegram Pipeline Stub
**Description:** Create interface-based stub for Telegram media pipeline

**Primary Write Scope:**
- `:pipeline:telegram/`

**Read-Only Dependencies:**
- `:core:model/` (use existing `PlaybackType`, `PlaybackContext`)
- `:core:persistence/` (reference `ObxTelegramMessage` entity)

**Deliverables:**
- Domain models: `TelegramMediaItem`, `TelegramChat`, `TelegramMessage`
- Interfaces: `TelegramContentRepository`, `TelegramDownloadManager`, `TelegramStreamingSettingsProvider`, `TelegramPlaybackSourceFactory`
- Stub implementations returning empty/mock data
- Package documentation (`package-info.kt`)
- Helper: `TelegramMediaItem.toPlaybackContext(): PlaybackContext`

**Blocking Dependencies:** None (can run in parallel with P2-T1, P2-T2)

---

#### P2-T4: IO Pipeline Stub
**Description:** Create interface-based stub for local/network file IO pipeline

**Primary Write Scope:**
- `:pipeline:io/`

**Read-Only Dependencies:**
- `:core:model/` (use existing `PlaybackType`, `PlaybackContext`)

**Deliverables:**
- Domain models: `IoMediaItem`, `IoSource`
- Interfaces: `IoContentRepository`, `IoPlaybackSourceFactory`
- Stub implementations for local file scanning
- Package documentation (`package-info.kt`)
- Helper: `IoMediaItem.toPlaybackContext(): PlaybackContext`

**Blocking Dependencies:** None (can run in parallel with P2-T1, P2-T2, P2-T3)

---

#### P2-T5: Audiobook Pipeline Stub
**Description:** Create interface-based stub for audiobook pipeline

**Primary Write Scope:**
- `:pipeline:audiobook/`

**Read-Only Dependencies:**
- `:core:model/` (use existing `PlaybackType`, `PlaybackContext`)

**Deliverables:**
- Domain models: `AudiobookItem`, `AudiobookChapter`
- Interfaces: `AudiobookRepository`, `AudiobookPlaybackSourceFactory`
- Stub implementations returning empty/mock data
- Package documentation (`package-info.kt`)
- Helper: `AudiobookItem.toPlaybackContext(): PlaybackContext`

**Blocking Dependencies:** None (can run in parallel with P2-T1, P2-T2, P2-T3, P2-T4)

---

#### P2-T6: Playback Domain Integration
**Description:** Wire pipeline interfaces into playback domain

**Primary Write Scope:**
- `:playback:domain/`

**Read-Only Dependencies:**
- `:core:model/`
- `:core:persistence/` (completed P2-T1)
- All `:pipeline:*/` modules (completed P2-T2, P2-T3, P2-T4, P2-T5)

**Deliverables:**
- Update `ResumeManager` implementations to use `ResumeRepository` from `:core:persistence`
- Update `KidsPlaybackGate` to use profile repositories
- Add Hilt modules for pipeline dependency injection
- Integration tests validating pipeline interface contracts

**Blocking Dependencies:** 
- MUST wait for P2-T1 (Core Persistence)
- SHOULD wait for P2-T2, P2-T3, P2-T4, P2-T5 (Pipeline stubs) for full integration

---

#### P2-T7: Integration Testing
**Description:** Write integration tests across module boundaries

**Primary Write Scope:**
- `:app-v2/src/androidTest/`
- `:core:persistence/src/androidTest/`

**Read-Only Dependencies:**
- All completed Phase 2 modules

**Deliverables:**
- ObjectBox entity CRUD tests
- Repository integration tests
- Pipeline stub contract validation tests
- Hilt dependency injection tests
- Test documentation

**Blocking Dependencies:**
- MUST wait for P2-T1, P2-T6 (core functionality must exist)
- SHOULD wait for P2-T2, P2-T3, P2-T4, P2-T5 (to test pipeline stubs)

---

#### P2-T8: Build & Quality Validation
**Description:** Ensure all modules build cleanly and pass quality gates

**Primary Write Scope:**
- None (read-only validation task)
- May update `.gitignore`, `detekt-config.yml` if needed

**Read-Only Dependencies:**
- All Phase 2 modules

**Deliverables:**
- Run `./gradlew assembleDebug` successfully
- Run `./gradlew testDebugUnitTest` successfully
- Run `./gradlew ktlintCheck` with zero errors
- Run `./gradlew detekt` with zero errors
- Run `./gradlew lintDebug` with zero critical issues
- Document any quality gate exceptions (if unavoidable)

**Blocking Dependencies:**
- MUST wait for all other Phase 2 tasks (P2-T1 through P2-T7)

---

## Parallelization Strategy for Phase 2

### Wave 1: Foundation (Fully Parallel)
**Duration Estimate:** 3-5 days  
**Parallel Tasks:** P2-T1, P2-T2, P2-T3, P2-T4, P2-T5

These 5 tasks have **zero overlap** in write scopes and can be executed simultaneously by 5 different agents:

| Agent | Task | Write Scope | Start Condition |
|-------|------|-------------|-----------------|
| Agent-A | P2-T1 | `:core:persistence/` | Immediate |
| Agent-B | P2-T2 | `:pipeline:xtream/` | Immediate |
| Agent-C | P2-T3 | `:pipeline:telegram/` | Immediate |
| Agent-D | P2-T4 | `:pipeline:io/` | Immediate |
| Agent-E | P2-T5 | `:pipeline:audiobook/` | Immediate |

**Synchronization Point:** All agents must complete and merge before Wave 2 begins.

---

### Wave 2: Integration (Sequential)
**Duration Estimate:** 2-3 days  
**Sequential Task:** P2-T6

This task **reads from** all Wave 1 outputs and **writes to** `:playback:domain/`:

| Agent | Task | Write Scope | Start Condition |
|-------|------|-------------|-----------------|
| Agent-F | P2-T6 | `:playback:domain/` | After P2-T1, P2-T2, P2-T3, P2-T4, P2-T5 merged |

**Synchronization Point:** P2-T6 must complete and merge before Wave 3 begins.

---

### Wave 3: Validation (Sequential)
**Duration Estimate:** 1-2 days  
**Sequential Tasks:** P2-T7, P2-T8 (can partially overlap)

These tasks validate all previous work:

| Agent | Task | Write Scope | Start Condition |
|-------|------|-------------|-----------------|
| Agent-G | P2-T7 | Test directories only | After P2-T6 merged |
| Agent-H | P2-T8 | None (validation) | After P2-T7 merged |

**Note:** P2-T7 and P2-T8 can partially overlap if P2-T7 focuses on writing tests while P2-T8 runs quality checks on existing code.

---

### Dependency Graph

```
         ┌─────────┐
         │ P2-T1   │  Core Persistence
         │ Agent-A │
         └────┬────┘
              │
    ┌─────────┼─────────────────┐
    │         │                 │
┌───▼───┐ ┌───▼───┐ ┌───▼───┐ ┌───▼───┐
│ P2-T2 │ │ P2-T3 │ │ P2-T4 │ │ P2-T5 │
│Agent-B│ │Agent-C│ │Agent-D│ │Agent-E│
│Xtream │ │Telegr.│ │  IO   │ │Audio  │
└───┬───┘ └───┬───┘ └───┬───┘ └───┬───┘
    │         │         │         │
    └─────────┼─────────┼─────────┘
              │         │
          ┌───▼─────────▼───┐
          │     P2-T6        │  Playback Integration
          │    Agent-F       │
          └────────┬─────────┘
                   │
          ┌────────▼─────────┐
          │     P2-T7        │  Integration Tests
          │    Agent-G       │
          └────────┬─────────┘
                   │
          ┌────────▼─────────┐
          │     P2-T8        │  Quality Validation
          │    Agent-H       │
          └──────────────────┘
```

**Total Duration Estimate:** 6-10 days with parallel execution (vs 15-20 days sequential)

---

## Agent Workflow Summary for Phase 2

### Onboarding Process
Every agent MUST follow this sequence before coding:

1. **Read Documentation**
   - `v2-docs/APP_VISION_AND_SCOPE.md`
   - `v2-docs/ARCHITECTURE_OVERVIEW_V2.md`
   - `v2-docs/IMPLEMENTATION_PHASES_V2.md` (focus on Phase 2 section)
   - `docs/agents/phase2/PHASE2_PARALLELIZATION_PLAN.md` (this document)
   - `docs/agents/phase2/AGENT_PROTOCOL_PHASE2.md`

2. **Select Task**
   - Choose an available task from Wave 1, 2, or 3 based on current phase progress
   - Verify task dependencies are satisfied (check `blocking-dependencies` in task definition)

3. **Create Progress File**
   - Use template from `AGENT_PROTOCOL_PHASE2.md`
   - File name: `agent-<agent-id>_P2-<task-id>_progress.md`
   - Example: `agent-alice_P2-T1_progress.md`

4. **Perform Read-Only Inspection**
   - Explore assigned write scope modules
   - Review dependencies in read-only modules
   - Document findings in progress file before making changes

### Development Process

1. **Iterative Development**
   - Update progress file at each milestone
   - Run targeted tests frequently (e.g., `./gradlew :core:persistence:testDebugUnitTest`)
   - Commit early and often with descriptive messages

2. **Quality Gates**
   - Run `./gradlew ktlintCheck` before each commit
   - Run module-specific tests before marking task complete
   - Address any linting or test failures immediately

3. **Communication**
   - Progress file serves as primary communication channel
   - Update `Current Status` field regularly: `Planned` → `In Progress` → `Blocked` → `Completed`
   - Document any blocking issues with details

### Completion Process

1. **Pre-PR Checklist**
   - All module-specific tests pass
   - All code follows ktlint formatting
   - All new code has package-level or class-level documentation
   - No `TODO` or `FIXME` comments in production code

2. **Create Follow-Up Task File**
   - Use template from `AGENT_PROTOCOL_PHASE2.md`
   - File name: `FOLLOWUP_P2-<task-id>_by-<agent-id>.md`
   - Example: `FOLLOWUP_P2-T1_by-alice.md`
   - Include summary, remaining work, dependencies, risks, next steps, test commands

3. **Open Pull Request**
   - Title: `[Phase 2][P2-TX] Brief Description`
   - Body: Link to progress file and follow-up file
   - Request review from appropriate team members

---

## Per-Task Guidance

### P2-T1: Core Persistence

**Approach:**
1. Create `:core:persistence/` package structure:
   ```
   com.fishit.player.core.persistence/
     ├── objectbox/
     │   ├── ObxStore.kt
     │   ├── entities/
     │   │   ├── ObxCategory.kt
     │   │   ├── ObxLive.kt
     │   │   ├── ObxVod.kt
     │   │   ├── ObxSeries.kt
     │   │   ├── ObxEpisode.kt
     │   │   ├── ObxEpgNowNext.kt
     │   │   ├── ObxProfile.kt
     │   │   ├── ObxProfilePermissions.kt
     │   │   ├── ObxResumeMark.kt
     │   │   └── ObxTelegramMessage.kt
     │   └── repositories/
     │       ├── ProfileRepositoryImpl.kt
     │       ├── EntitlementRepositoryImpl.kt
     │       ├── LocalMediaRepositoryImpl.kt
     │       ├── SubtitleStyleStoreImpl.kt
     │       └── ResumeRepositoryImpl.kt
     └── datastore/
         └── PreferencesManager.kt
   ```

2. Port ObjectBox entities from v1 (`app/src/main/java/com/chris/m3usuite/data/obx/ObxEntities.kt`):
   - Copy entity definitions verbatim (they are production-tested)
   - Update package names to `com.fishit.player.core.persistence.objectbox.entities`
   - Ensure ObjectBox annotations are preserved

3. Implement repository interfaces:
   - Define interfaces in `:core:model/` if they don't exist
   - Implement in `:core:persistence/`
   - Use constructor injection for dependencies (Hilt)

4. Write unit tests:
   - Test CRUD operations for each entity
   - Test repository implementations with in-memory ObjectBox store
   - Test DataStore read/write operations

**Key References:**
- v1 ObjectBox: `app/src/main/java/com/chris/m3usuite/data/obx/`
- v1 Repositories: `app/src/main/java/com/chris/m3usuite/data/repo/`

**Quality Criteria:**
- All tests pass: `./gradlew :core:persistence:testDebugUnitTest`
- Zero ktlint errors: `./gradlew :core:persistence:ktlintCheck`
- Module builds: `./gradlew :core:persistence:assembleDebug`

---

### P2-T2: Xtream Pipeline Stub

**Approach:**
1. Create `:pipeline:xtream/` package structure:
   ```
   com.fishit.player.pipeline.xtream/
     ├── model/
     │   ├── XtreamVodItem.kt
     │   ├── XtreamSeriesItem.kt
     │   ├── XtreamEpisode.kt
     │   ├── XtreamChannel.kt
     │   └── XtreamEpgEntry.kt
     ├── repository/
     │   ├── XtreamCatalogRepository.kt (interface)
     │   ├── XtreamLiveRepository.kt (interface)
     │   └── XtreamCatalogRepositoryStub.kt (impl)
     ├── source/
     │   └── XtreamPlaybackSourceFactory.kt
     └── ext/
         └── XtreamExtensions.kt (toPlaybackContext)
   ```

2. Define domain models:
   - Use data classes with all relevant fields (ID, title, description, thumbnail URL, etc.)
   - Make models serializable if needed for future use
   - Add KDoc comments explaining purpose

3. Define repository interfaces:
   - Methods for listing, filtering, searching content
   - Suspend functions for async operations
   - Use Flow<T> for reactive data streams

4. Implement stub repositories:
   - Return empty lists or mock data
   - Use `delay()` to simulate network calls
   - Add TODO comments for Phase 3 implementation

5. Create helper extension:
   ```kotlin
   fun XtreamVodItem.toPlaybackContext(): PlaybackContext =
       PlaybackContext(
           type = PlaybackType.VOD,
           vodId = this.id,
           // ... other fields
       )
   ```

**Key References:**
- v1 Xtream: `app/src/main/java/com/chris/m3usuite/core/xtream/`
- Phase 2 contract: `v2-docs/IMPLEMENTATION_PHASES_V2.md` (Phase 3 Xtream section)

**Quality Criteria:**
- Stub implementations compile and return predictable data
- All interfaces documented with KDoc
- Extension functions tested with unit tests
- Zero ktlint errors: `./gradlew :pipeline:xtream:ktlintCheck`

---

### P2-T3: Telegram Pipeline Stub

**Approach:**
1. Create `:pipeline:telegram/` package structure:
   ```
   com.fishit.player.pipeline.telegram/
     ├── model/
     │   ├── TelegramMediaItem.kt
     │   ├── TelegramChat.kt
     │   └── TelegramMessage.kt
     ├── repository/
     │   ├── TelegramContentRepository.kt (interface)
     │   └── TelegramContentRepositoryStub.kt (impl)
     ├── download/
     │   ├── TelegramDownloadManager.kt (interface)
     │   └── TelegramDownloadManagerStub.kt (impl)
     ├── streaming/
     │   ├── TelegramStreamingSettingsProvider.kt (interface)
     │   └── TelegramStreamingSettingsProviderStub.kt (impl)
     ├── source/
     │   └── TelegramPlaybackSourceFactory.kt
     └── ext/
         └── TelegramExtensions.kt (toPlaybackContext)
   ```

2. Define domain models:
   - Reference `ObxTelegramMessage` from `:core:persistence` for field inspiration
   - Use data classes with fields like: chatId, messageId, fileId, mimeType, size, caption, etc.
   - Add KDoc comments

3. Define repository interfaces:
   - Methods for listing media by chat, by date, by type
   - Paging support (offset/limit or cursor-based)
   - Suspend functions with Flow<T> for reactive streams

4. Implement stub repositories:
   - Return empty lists or mock TelegramMediaItem instances
   - Simulate async behavior with `delay()`

5. Create helper extension:
   ```kotlin
   fun TelegramMediaItem.toPlaybackContext(): PlaybackContext =
       PlaybackContext(
           type = PlaybackType.TELEGRAM,
           telegramFileId = this.fileId,
           telegramChatId = this.chatId,
           telegramMessageId = this.messageId,
       )
   ```

**Key References:**
- v1 Telegram: `app/src/main/java/com/chris/m3usuite/telegram/`
- `.github/tdlibAgent.md` (Telegram integration specifications)
- Phase 2 contract: `v2-docs/IMPLEMENTATION_PHASES_V2.md` (Phase 2 Telegram section)

**Quality Criteria:**
- All interfaces compile and have KDoc
- Stub implementations return predictable data
- Extension functions have unit tests
- Zero ktlint errors: `./gradlew :pipeline:telegram:ktlintCheck`

---

### P2-T4: IO Pipeline Stub

**Approach:**
1. Create `:pipeline:io/` package structure:
   ```
   com.fishit.player.pipeline.io/
     ├── model/
     │   ├── IoMediaItem.kt
     │   └── IoSource.kt
     ├── repository/
     │   ├── IoContentRepository.kt (interface)
     │   └── IoContentRepositoryStub.kt (impl)
     ├── source/
     │   └── IoPlaybackSourceFactory.kt
     └── ext/
         └── IoExtensions.kt (toPlaybackContext)
   ```

2. Define domain models:
   - `IoMediaItem`: path, name, size, mimeType, lastModified
   - `IoSource`: enum (LOCAL_FILE, NETWORK_URL, etc.)

3. Define repository interface:
   - Methods for listing local files, scanning directories
   - File picker integration (future)
   - Network URL validation (future)

4. Implement stub repository:
   - Return empty list or mock local file items
   - Use `delay()` for async simulation

5. Create helper extension:
   ```kotlin
   fun IoMediaItem.toPlaybackContext(): PlaybackContext =
       PlaybackContext(
           type = PlaybackType.IO,
           ioFilePath = this.path,
       )
   ```

**Quality Criteria:**
- All interfaces documented
- Stub implementation compiles
- Extension functions tested
- Zero ktlint errors: `./gradlew :pipeline:io:ktlintCheck`

---

### P2-T5: Audiobook Pipeline Stub

**Approach:**
1. Create `:pipeline:audiobook/` package structure:
   ```
   com.fishit.player.pipeline.audiobook/
     ├── model/
     │   ├── AudiobookItem.kt
     │   └── AudiobookChapter.kt
     ├── repository/
     │   ├── AudiobookRepository.kt (interface)
     │   └── AudiobookRepositoryStub.kt (impl)
     ├── source/
     │   └── AudiobookPlaybackSourceFactory.kt
     └── ext/
         └── AudiobookExtensions.kt (toPlaybackContext)
   ```

2. Define domain models:
   - `AudiobookItem`: id, title, author, narrator, coverUrl, chapters
   - `AudiobookChapter`: chapterNumber, title, duration, fileUrl

3. Define repository interface:
   - Methods for listing audiobooks, searching by author/title
   - Methods for fetching chapters for a given audiobook

4. Implement stub repository:
   - Return empty list or mock audiobook data

5. Create helper extension:
   ```kotlin
   fun AudiobookItem.toPlaybackContext(): PlaybackContext =
       PlaybackContext(
           type = PlaybackType.AUDIOBOOK,
           audiobookId = this.id,
       )
   ```

**Quality Criteria:**
- All interfaces documented
- Stub implementation compiles
- Extension functions tested
- Zero ktlint errors: `./gradlew :pipeline:audiobook:ktlintCheck`

---

### P2-T6: Playback Domain Integration

**Approach:**
1. Update `:playback:domain/` to use `:core:persistence/`:
   - Inject `ResumeRepository` into `ResumeManager` implementations
   - Inject profile repositories into `KidsPlaybackGate` implementations
   - Replace hard-coded defaults with repository calls

2. Add Hilt modules for pipeline DI:
   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   object PipelineBindingsModule {
       @Provides
       @Singleton
       fun provideXtreamCatalogRepository(): XtreamCatalogRepository =
           XtreamCatalogRepositoryStub()
       
       // ... similar for other pipelines
   }
   ```

3. Write integration tests:
   - Test that `ResumeManager` correctly reads/writes resume points via `ResumeRepository`
   - Test that `KidsPlaybackGate` enforces rules based on profile data
   - Test that all pipeline factories can be injected via Hilt

**Key References:**
- v1 Resume: `app/src/main/java/com/chris/m3usuite/playback/domain/`
- v1 Kids Gate: `app/src/main/java/com/chris/m3usuite/playback/kids/`

**Quality Criteria:**
- All integration tests pass
- No direct ObjectBox calls outside `:core:persistence/`
- Hilt dependency graph compiles
- Zero ktlint errors: `./gradlew :playback:domain:ktlintCheck`

---

### P2-T7: Integration Testing

**Approach:**
1. Create test suites in `:app-v2/src/androidTest/`:
   ```
   com.fishit.player.test/
     ├── PersistenceIntegrationTest.kt
     ├── PipelineStubContractTest.kt
     └── HiltDependencyTest.kt
   ```

2. Write ObjectBox CRUD tests:
   - Test inserting, updating, deleting all entity types
   - Test repository query methods
   - Use in-memory ObjectBox store for speed

3. Write pipeline contract tests:
   - Verify all pipeline repositories return expected data types
   - Verify all `toPlaybackContext()` extensions work correctly
   - Verify stub implementations never throw exceptions

4. Write Hilt DI tests:
   - Verify all interfaces can be injected
   - Verify singleton scope is correct
   - Verify no circular dependencies

**Quality Criteria:**
- All tests pass: `./gradlew :app-v2:connectedAndroidTest`
- Test coverage report generated
- All critical paths tested
- Test documentation explains what each test validates

---

### P2-T8: Build & Quality Validation

**Approach:**
1. Run full project build:
   ```bash
   ./gradlew clean assembleDebug
   ```

2. Run all unit tests:
   ```bash
   ./gradlew testDebugUnitTest
   ```

3. Run ktlint:
   ```bash
   ./gradlew ktlintCheck
   ```

4. Run detekt:
   ```bash
   ./gradlew detekt
   ```

5. Run Android lint:
   ```bash
   ./gradlew lintDebug
   ```

6. Document results:
   - Create report with pass/fail for each check
   - Document any exceptions (e.g., acceptable lint warnings)
   - Create action items for any failures

**Quality Criteria:**
- Zero build errors
- Zero test failures
- Zero ktlint errors (auto-fix with `ktlintFormat` if needed)
- Zero critical detekt issues
- Zero critical lint issues
- Report delivered to team

---

## Conflict Prevention Rules

### Write Scope Enforcement
Each agent MUST adhere strictly to their task's **Primary Write Scope**:

❌ **NEVER WRITE TO:**
- Modules owned by other tasks
- Legacy v1 modules (`app/` and related v1 code)
- Root project files (except `.gitignore` in P2-T8)
- Other agents' progress files

✅ **MAY READ FROM:**
- Any module in the project
- Any documentation file
- Other agents' progress files (to check status)

✅ **MAY WRITE TO:**
- Own progress file (`agent-<id>_P2-<task-id>_progress.md`)
- Own follow-up file (`FOLLOWUP_P2-<task-id>_by-<id>.md`)
- Assigned module(s) in write scope

### Module-Level Locking
Once an agent starts a task:
1. Agent creates progress file with status `In Progress`
2. Other agents MUST NOT start the same task
3. Agent updates progress file regularly
4. Agent sets status to `Completed` when done

### Merge Conflict Resolution
If merge conflicts occur (should be rare):
1. Agent who encounters conflict documents it in progress file
2. Agent sets status to `Blocked` with details
3. Team reviews conflict and assigns resolution owner
4. Resolution owner updates both conflicting files
5. Resolution owner documents resolution in both progress files

---

## Success Criteria for Phase 2

Phase 2 is considered complete when:

✅ All 8 tasks (P2-T1 through P2-T8) have status `Completed`  
✅ All follow-up files created  
✅ All PRs merged to `architecture/v2-bootstrap` branch  
✅ Project builds successfully: `./gradlew assembleDebug`  
✅ All tests pass: `./gradlew testDebugUnitTest`  
✅ All quality gates pass: `ktlintCheck`, `detekt`, `lintDebug`  
✅ All modules documented with package-level KDoc  
✅ Phase 3 can begin (agents can start implementing full pipelines)

---

## References

- **v2 Architecture:** `v2-docs/ARCHITECTURE_OVERVIEW_V2.md`
- **v2 Implementation Phases:** `v2-docs/IMPLEMENTATION_PHASES_V2.md`
- **v2 Vision & Scope:** `v2-docs/APP_VISION_AND_SCOPE.md`
- **Agent Protocol:** `docs/agents/phase2/AGENT_PROTOCOL_PHASE2.md`
- **v1 Analysis Report:** `v2-docs/V1_VS_V2_ANALYSIS_REPORT.md` (for porting guidance)

---

**Document Version:** 1.0  
**Last Updated:** 2025-12-05  
**Maintained By:** v2 Architecture Team
