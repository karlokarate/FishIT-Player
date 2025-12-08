> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Agent Automation Documentation

This document describes the automated workflows and agent-driven development processes for the FishIT-Player project.

---

## Table of Contents

1. [Overview](#overview)
2. [TDLib Cluster Workflows](#tdlib-cluster-workflows)
3. [Workflow Usage](#workflow-usage)
4. [Development Process](#development-process)
5. [Best Practices](#best-practices)

---

## Overview

The FishIT-Player project uses automated workflows to orchestrate parallel development of complex features. These workflows are designed to:

- Enable parallel development without merge conflicts
- Provide clear task boundaries and dependencies
- Support both manual triggering and agent-driven automation
- Maintain code quality and architectural consistency

---

## TDLib Cluster Workflows

The Telegram/TDLib integration has been decomposed into **5 parallelizable task clusters**, each with its own dedicated workflow. This decomposition is based on the comprehensive analysis in `.github/tdlibAgent.md` and documented in `docs/TDLIB_TASK_GROUPING.md`.

### Cluster A: Core / Engine
**Workflow:** `.github/workflows/tdlib_cluster_core.yml`  
**Trigger:** Manual (`workflow_dispatch`)

**Purpose:** Establish the foundational "Unified Telegram Engine" that all other clusters depend on.

**Scope:**
- Package: `telegram/core`, `telegram/config`
- Components: `T_TelegramServiceClient`, `T_TelegramSession`, `T_ChatBrowser`, `T_TelegramFileDownloader`

**Key Responsibilities:**
- Create single TdlClient instance per process
- Manage authentication state and flows
- Coordinate all TDLib update streams
- Provide stable API contracts for dependent clusters
- Implement lifecycle management and error recovery

**Dependencies:** None (foundation cluster)

**Task Count:** 29 tasks (Tasks 1-4, 11-29 from TDLIB_TASK_GROUPING.md)

**Critical Notes:**
- Must be completed first or have stable API contracts defined
- All other clusters depend on this one
- Zero file overlap with other clusters

---

### Cluster B: Sync / Worker / Repository
**Workflow:** `.github/workflows/tdlib_cluster_sync.yml`  
**Trigger:** Manual (`workflow_dispatch`)

**Purpose:** Handle background synchronization, content parsing, and data persistence.

**Scope:**
- Packages: `telegram/work`, `data/repo`, `telegram/parser`
- Components: `TelegramSyncWorker`, `TgContentHeuristics`, `TelegramContentRepository`

**Key Responsibilities:**
- Turbo-Sync: Parallel chat processing with adaptive parallelism
- Intelligent content classification (movies, series, episodes)
- Persistent storage via ObjectBox
- Flow-based data APIs for UI consumption

**Dependencies:** 
- Requires Cluster A APIs (T_TelegramServiceClient, T_ChatBrowser)

**Parallel Development:** Can work in parallel with Clusters C, D, E

**Task Count:** 18 tasks (Tasks 6, 9, 37-48, 57-60, 92-94)

**Key Features:**
- Device-aware parallelism (adapts to CPU cores and device class)
- Multiple sync modes: ALL, SELECTION_CHANGED, BACKFILL_SERIES
- Comprehensive unit testing

---

### Cluster C: Streaming / DataSource
**Workflow:** `.github/workflows/tdlib_cluster_streaming.yml`  
**Trigger:** Manual (`workflow_dispatch`)

**Purpose:** Implement zero-copy streaming and media playback integration.

**Scope:**
- Package: `telegram/player`
- Components: `TelegramDataSource`, thumbnail generation

**Key Responsibilities:**
- Zero-copy streaming directly from memory (no disk writes)
- In-memory ringbuffer architecture
- Integration with ExoPlayer's DataSource factory
- Thumbnail generation for visual UX

**Dependencies:**
- Requires Cluster A APIs (T_TelegramFileDownloader)

**Parallel Development:** Can work in parallel with Clusters B, D, E

**Task Count:** 9 tasks (Tasks 5, 49-56, 85-87)

**Key Features:**
- URL scheme: `tg://file/<fileId>?chatId=...&messageId=...`
- Efficient streaming without disk I/O overhead
- Coil 3.x integration for image loading

---

### Cluster D: UI / Activity Feed / Rows
**Workflow:** `.github/workflows/tdlib_cluster_ui_feed.yml`  
**Trigger:** Manual (`workflow_dispatch`)

**Purpose:** Build all user-facing interfaces and visual components.

**Scope:**
- Packages: `telegram/ui`, `telegram/ui/feed`, `ui/layout`
- Components: Settings, Activity Feed, Layout components, Widgets

**Key Responsibilities:**
- Telegram settings and login UI
- Real-time activity feed visualization
- Chat picker with drag-to-reorder
- TV and mobile UI optimization
- Home screen widgets

**Dependencies:**
- Requires Cluster A APIs (T_TelegramServiceClient)
- Requires Cluster B APIs (TelegramContentRepository)

**Parallel Development:** Can work in parallel with Clusters C, E

**Task Count:** 24 tasks (Tasks 7, 9, 30-36, 70-77, 88-91, 95-97)

**Key Features:**
- One-time login with persistent session
- DPAD-compatible TV navigation
- Jetpack Glance widgets
- Comprehensive instrumented tests

---

### Cluster E: Logging / Diagnostics / Quality
**Workflow:** `.github/workflows/tdlib_cluster_logging_tools.yml`  
**Trigger:** Manual (`workflow_dispatch`)

**Purpose:** Provide logging, diagnostics, and quality assurance infrastructure.

**Scope:**
- Package: `telegram/logging`, build configuration, lint rules
- Components: Log repository, debug tools, test coverage, lint rules

**Key Responsibilities:**
- In-app log viewer with filtering
- Memory leak detection (LeakCanary)
- Coroutine debugging support
- Test coverage metrics (Kover)
- Custom lint/detekt rules for TDLib patterns

**Dependencies:**
- Minimal (DiagnosticsLogger already exists)
- Integrates with all other clusters (instrumentation)

**Parallel Development:** Can work in parallel with ALL clusters (A, B, C, D)

**Task Count:** 20 tasks (Tasks 9, 61-69, 78-84, 98-99)

**Key Features:**
- 500-entry in-memory ringbuffer
- Export logs via share intent
- Enforces code quality standards
- Prevents architectural violations

---

## Workflow Usage

### Manual Triggering

All cluster workflows can be triggered manually from the GitHub Actions UI:

1. Go to the repository's **Actions** tab
2. Select the desired cluster workflow from the left sidebar
3. Click **"Run workflow"** button
4. Choose the branch (default: current branch)
5. Select dry run mode:
   - `true`: Analysis only, no code changes
   - `false`: Full implementation (agent-driven)
6. Click **"Run workflow"** to start

### Dry Run Mode

**Purpose:** Verify workflow configuration and analyze task scope without making code changes.

**What it does:**
- Checks out repository
- Displays cluster information and task breakdown
- Verifies documentation exists
- Checks current file structure
- Validates build state
- Shows what WOULD be implemented

**Use cases:**
- Verify workflow is correctly configured
- Review task assignments
- Check dependencies before implementation
- Ensure documentation is up-to-date

### Implementation Mode

**Purpose:** Execute actual code changes for the cluster's assigned tasks.

**What it does:**
- All dry run checks
- Creates/moves packages and files
- Implements features per task list
- Updates dependencies and imports
- Runs tests and validation
- Commits and pushes changes

**Use cases:**
- Agent-driven automated implementation
- Parallel development across clusters
- Incremental feature rollout

---

## Development Process

### Phase 1: Foundation (Cluster A)

**Duration:** 1-2 sprints

1. **Manual trigger** Cluster A workflow in dry run mode
2. Review task breakdown and dependencies
3. **Trigger implementation** (via agent or manual development)
4. Verify stable API contracts are established
5. Run tests and quality checks
6. Document API interfaces

**Success Criteria:**
- All 29 Cluster A tasks completed
- API contracts documented
- Unit tests passing
- No regressions in existing functionality

### Phase 2: Parallel Development (Clusters B, C, D, E)

**Duration:** 2-3 sprints (concurrent)

Once Cluster A is stable:

1. **Simultaneously trigger** all four cluster workflows
2. Each cluster can proceed independently
3. Regular integration checkpoints to verify API compatibility
4. Continuous testing within each cluster

**Cluster B Progress:**
- TgContentHeuristics implementation
- TelegramContentRepository enhancement
- Turbo-Sync with parallel processing
- Unit tests

**Cluster C Progress:**
- Zero-copy streaming implementation
- DataSource factory integration
- Thumbnail generation
- Performance testing

**Cluster D Progress:**
- Settings UI and login flow
- Activity Feed implementation
- Chat picker with reordering
- Widgets and instrumented tests

**Cluster E Progress:**
- Logging infrastructure
- Debug tools integration
- Test coverage setup
- Custom lint rules

**Success Criteria (per cluster):**
- All assigned tasks completed
- Unit tests passing (where applicable)
- Code follows style guidelines
- APIs documented
- No regressions

### Phase 3: Integration & Testing

**Duration:** 1 sprint

1. Merge all cluster branches
2. End-to-end integration testing
3. Performance tuning
4. Final quality checks
5. Documentation updates
6. Release preparation

---

## Best Practices

### For Cluster Development

1. **API-First Design**
   - Define interfaces early in Cluster A
   - Document contracts clearly
   - Use mocks for parallel development

2. **Minimal File Overlap**
   - Each cluster works in distinct packages
   - Zero conflicts expected between clusters
   - Build config changes are easy to merge

3. **Incremental Testing**
   - Write tests within each cluster
   - Don't wait for integration phase
   - Use mocks for dependencies

4. **Regular Synchronization**
   - Daily standups during parallel phase
   - Share API changes immediately
   - Integration checkpoints every 2-3 days

### For Workflow Management

1. **Always Start with Dry Run**
   - Verify configuration before implementation
   - Review task scope and dependencies
   - Check for documentation updates

2. **Monitor Build State**
   - Each workflow validates build before changes
   - Pre-existing issues are noted
   - Don't introduce new build failures

3. **Documentation First**
   - Update `TDLIB_TASK_GROUPING.md` as tasks complete
   - Keep `.github/tdlibAgent.md` as single source of truth
   - Document API changes in code (KDoc)

4. **Quality Gates**
   - All tests must pass before merge
   - Code style checks enforced (ktlint, detekt)
   - No direct TdlClient access outside `telegram.core`
   - No monster methods (> 100 LOC)

### For Agent-Driven Development

1. **Context Preservation**
   - Workflows provide full task context
   - Documentation is self-contained
   - Each workflow is independently executable

2. **Error Recovery**
   - Workflows check pre-conditions
   - Validate build state before changes
   - Provide clear error messages

3. **Traceability**
   - Each cluster maps to specific task IDs
   - Changes are traceable to `TDLIB_TASK_GROUPING.md`
   - Progress tracked in workflow runs

---

## Task Reference

For complete task breakdown and cluster assignments, see:
- **Task Inventory:** `docs/TDLIB_TASK_GROUPING.md`
- **Technical Specification:** `.github/tdlibAgent.md`
- **Architecture Overview:** `ARCHITECTURE_OVERVIEW.md`

---

## Workflow File Locations

```
.github/workflows/
├── tdlib_cluster_core.yml          # Cluster A: Core/Engine
├── tdlib_cluster_sync.yml          # Cluster B: Sync/Worker/Repository
├── tdlib_cluster_streaming.yml     # Cluster C: Streaming/DataSource
├── tdlib_cluster_ui_feed.yml       # Cluster D: UI/Activity Feed
└── tdlib_cluster_logging_tools.yml # Cluster E: Logging/Quality
```

---

## Success Metrics

### Per-Cluster Metrics
- ✅ All assigned tasks completed
- ✅ Unit tests passing (≥80% coverage where applicable)
- ✅ Zero regressions in existing functionality
- ✅ Code style compliance (ktlint, detekt)
- ✅ API documentation complete

### Integration Metrics
- ✅ All clusters integrated successfully
- ✅ End-to-end tests passing
- ✅ Performance targets met
- ✅ Zero architectural violations
- ✅ Documentation updated

---

## Next Steps

1. **Review** `docs/TDLIB_TASK_GROUPING.md` for detailed task breakdown
2. **Trigger** Cluster A workflow to start foundation work
3. **Monitor** progress through GitHub Actions UI
4. **Coordinate** with team during parallel development phases
5. **Integrate** completed clusters in Phase 3

---

_Last Updated: 2025-11-20_  
_Workflow Version: 1.0_  
_For questions or issues, please open a GitHub issue._
