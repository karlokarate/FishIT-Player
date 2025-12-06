# Phase 2 – Task 4: IO Pipeline Stub

**Agent ID:** `io-agent`  
**Task:** P2-T4 – IO Pipeline Stub Implementation  
**Branch:** `feature/v2-phase2-p2t4-io-pipeline`  
**Base Branch:** `architecture/v2-bootstrap`  
**Status:** 🚧 In Progress  
**Started:** 2025-12-06

---

## Task Scope

Implement the `:pipeline:io` module stub with:
- Domain models (`IoMediaItem`, `IoSource`)
- Repository and factory interfaces
- Stub implementations (no real filesystem access)
- Helper extensions for PlaybackContext conversion
- Unit tests (pure, no Android framework dependencies)

---

## Write Scope

**Primary Module:**
- `:pipeline:io/` - Full implementation

**Read-Only References:**
- `:core:model/` - PlaybackContext, PlaybackType
- v2-docs/ - Architecture and phase documentation
- AGENTS_V2.md - Agent guidelines

**Do NOT Modify:**
- `:core:persistence/`
- Other `:pipeline:*` modules
- UI/Player modules

---

## Implementation Progress

### Phase 1: Documentation Review ✅
- [x] Read `v2-docs/ARCHITECTURE_OVERVIEW_V2.md`
- [x] Read `v2-docs/IMPLEMENTATION_PHASES_V2.md`
- [x] Read `AGENTS_V2.md`
- [x] Review Phase 2 Task 1 completion status
- [x] Understand PlaybackContext and PlaybackType models
- [x] Review existing pipeline structure (telegram, xtream)

### Phase 2: Progress File Creation ✅
- [x] Create `docs/agents/phase2/` directory
- [x] Create this progress file

### Phase 3: Domain Models (In Progress)
- [ ] Define `IoMediaItem` data class
- [ ] Define `IoSource` sealed class hierarchy
- [ ] Add proper KDoc documentation

### Phase 4: Interface Definitions (Not Started)
- [ ] Define `IoContentRepository` interface
- [ ] Define `IoPlaybackSourceFactory` interface
- [ ] Add method signatures for discovery, browsing, filtering

### Phase 5: Stub Implementations (Not Started)
- [ ] Implement `StubIoContentRepository`
- [ ] Implement `StubIoPlaybackSourceFactory`
- [ ] Return deterministic fake/empty data

### Phase 6: Helper Extensions (Not Started)
- [ ] Add `IoMediaItem.toPlaybackContext()` extension
- [ ] Update `package-info.kt` with comprehensive documentation

### Phase 7: Unit Tests (Not Started)
- [ ] Create test source directory
- [ ] Add repository interface tests
- [ ] Add factory interface tests
- [ ] Add extension function tests
- [ ] Ensure all tests are pure (no filesystem/Android dependencies)

### Phase 8: Build Verification (Not Started)
- [ ] Run `:pipeline:io:compileDebugKotlin`
- [ ] Run `:pipeline:io:test`
- [ ] Verify no build artifacts in git

### Phase 9: Documentation (Not Started)
- [ ] Create `FOLLOWUP_P2-T4_by-io-agent.md`
- [ ] Document future work items (SAF integration, real FS access, etc.)

### Phase 10: Final Review & PR (Not Started)
- [ ] Review all changes
- [ ] Update this progress file with final status
- [ ] Prepare PR description

---

## Architecture Compliance

### Package Naming ✅
- Using `com.fishit.player.pipeline.io.*` (not com.chris.m3usuite)

### Dependency Rules ✅
- `:pipeline:io` only depends on:
  - `:core:model`
  - `:core:persistence`
  - `:infra:logging`
  - Kotlin stdlib and coroutines

### No UI Dependencies ✅
- No Compose imports
- No Android UI framework usage
- Pure Kotlin domain logic

---

## Key Decisions

### 1. IoSource Hierarchy
Using sealed class for extensibility:
- `IoSource.LocalFile` - Device storage paths
- `IoSource.Saf` - Android SAF URIs (stub)
- `IoSource.Smb` - Network SMB shares (stub)
- `IoSource.GenericUri` - Fallback for other URI schemes

### 2. Stub Implementation Strategy
- Return empty lists for discovery methods
- Provide 1-2 fake items for testing interfaces
- All methods are callable and deterministic
- No actual filesystem or Android framework calls

### 3. Test Strategy
- Pure Kotlin unit tests using JUnit
- No Robolectric needed (no Android dependencies)
- Focus on interface contracts, not behavior
- Verify stub implementations are callable

---

## Technical Notes

### ContentId Scheme
Following the resume contract from Phase 2 Task 1:
- IO content uses: `"io:file:{uri}"`
- Allows resume tracking in `ObxResumeMark`

### Platform Agnostic Design
- Core interfaces and models have no Android dependencies
- Platform-specific code (SAF, ContentResolver) will be added in future phases
- Current stub keeps module testable on any JVM

---

## Files Created/Modified

### Created
- `docs/agents/phase2/agent-io-agent_P2-T4_progress.md` (this file)
- (pending: domain models, interfaces, implementations, tests)

### Modified
- (pending: package-info.kt update)

---

## Next Steps

1. Implement domain models (IoMediaItem, IoSource)
2. Define repository and factory interfaces
3. Create stub implementations
4. Add helper extensions
5. Write unit tests
6. Verify build and tests pass

---

**Last Updated:** 2025-12-06  
**Current Phase:** Phase 3 - Domain Models
