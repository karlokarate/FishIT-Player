# FishIT Player v2 – Agents & Execution Guide

## ⛔ CRITICAL: Branch Protection (IMMUTABLE RULE)

> **This rule is IMMUTABLE and must NEVER be changed, ignored, or overridden
> by any agent, automation, or contributor without explicit written approval
> from the repository owner (@karlokarate).**

- **NO MERGE TO `main`** until v2 is fully completed and owner-approved.
- This branch (`architecture/v2-bootstrap`) is for v2 development ONLY.
- No force-pushes, no rebases onto main, no premature merges.
- Any request to merge into main MUST be refused until owner gives explicit approval.
- This protection remains active until the owner explicitly lifts it in writing.

---

> Single Source of Truth for **all AI assistants and automation** working on
> FishIT Player v2 on branch `architecture/v2-bootstrap`.

This document summarizes and consolidates all v2-specific rules and contracts
from:

- `v2-docs/APP_VISION_AND_SCOPE.md`
- `v2-docs/ARCHITECTURE_OVERVIEW_V2.md`
- `v2-docs/IMPLEMENTATION_PHASES_V2.md`
- `v2-docs/V1_VS_V2_ANALYSIS_REPORT.md` ⭐ **v1 Quality Assessment & Porting Strategy**

It is **mandatory reading** for any v2-related task.

---

## ⚡ CRITICAL: v1 Porting Reference

**Before implementing any v2 module**, agents MUST consult
`v2-docs/V1_VS_V2_ANALYSIS_REPORT.md` to:

1. **Check Tier 1/2 classification** – Tier 1 components (SIP Player, UnifiedLog,
   FocusKit, Fish* Layout, Xtream Pipeline, AppImageLoader) should be ported
   with minimal changes, not rewritten.
2. **Use the file mapping in Appendix A** – ~17,000 lines of production-tested
   v1 code are documented with exact paths and target v2 modules.
3. **Respect contract documents** – Phase-specific contracts (Phase 4-8) are
   listed in Appendix C and define behavior precisely.
4. **Avoid duplicate work** – Many abstractions already exist in v1; check
   before creating new interfaces.

This analysis report is updated whenever new v1 quality assessments are made.

---

## 1. Scope & Branch Rules

- v2 work happens in the dedicated branch `architecture/v2-bootstrap`.
- Legacy app (`:app` and related modules) remains intact and is **read-only**
  for v2 tasks:
  - Use it as behavioral reference only.
  - Port stable implementations into v2 modules instead of wiring new features
    into legacy code.
- v2 lives **inside the same repository** as a new generation next to v1
  (Strangler pattern):
  - New entry module: `:app-v2`.
  - New v2 modules: `:core:*`, `:playback:*`, `:player:internal`,
    `:pipeline:*`, `:feature:*`, `:infra:*` as defined below.

### ⚠️ PRE-CREATE VALIDATION (MANDATORY for ALL Agents)

**Before creating ANY new file, agents MUST verify:**

1. **Path Validation:**
   - ✅ ALLOWED: `core/*/src/main/java/com/fishit/player/...`
   - ✅ ALLOWED: `feature/*/src/main/java/com/fishit/player/...`
   - ✅ ALLOWED: `pipeline/*/src/main/java/com/fishit/player/...`
   - ✅ ALLOWED: `playback/*/src/main/java/com/fishit/player/...`
   - ✅ ALLOWED: `player/*/src/main/java/com/fishit/player/...`
   - ✅ ALLOWED: `infra/*/src/main/java/com/fishit/player/...`
   - ✅ ALLOWED: `app-v2/src/main/java/com/fishit/player/...`
   - ❌ FORBIDDEN: `app/src/main/java/com/chris/...` (Legacy!)

2. **Package Validation:**
   - ✅ MUST USE: `package com.fishit.player.*`
   - ❌ NEVER USE: `package com.chris.m3usuite.*`

3. **If in doubt, STOP and ask the user.**

Violation of these rules requires immediate rollback. See
`v2-docs/CANONICAL_MEDIA_MIGRATION_STATUS.md` for the current audit status.

Additional safety rules for this branch:

- Do **not** delete, move or rename existing legacy modules (e.g. `:app` and
  other v1 modules) on this branch.
- Do **not** modify legacy source files, legacy Gradle modules or legacy docs
  except where absolutely necessary to:
  - wire in new v2 modules in `settings.gradle.kts`, or
  - keep the project building.
- Treat legacy code and docs purely as **reference material** to be ported into
  the new v2 structure.
- All new v2 work **must** happen in:
  - new v2 Gradle modules listed below,
  - new or existing v2 docs (`v2-docs/`), and
  - the new app entry `:app-v2`.
- Never "clean up" legacy code as part of a v2 task.
- When porting behavior from v1:
  - Read the legacy implementation.
  - Reimplement the equivalent behavior in the appropriate v2 module.
  - Do **not** change the legacy implementation itself.

Whenever a task is explicitly scoped to v2, **do not** touch v1 modules unless
the task says so.

---

## 2. Product & UX Principles (What v2 Is / Is Not)

- v2 is a **modular, offline-first, multi-pipeline media client** built around
  a **single internal player (SIP)**.
- v2 is a **pure consumer client**:
  - No media-server behavior.
  - No social network or messaging system.
  - No cloud-driven UI DSL that defines layouts from the backend.
- Pipelines provide content from:
  - Xtream / IPTV
  - Telegram (tdlib-coroutines / g00sha)
  - Local / IO storage (device, SAF, later network shares)
  - Audiobooks (future)
- The internal player applies **consistent playback behavior across all
  pipelines** for:
  - Start & resume
  - Kids / screen-time limits
  - Subtitles & Closed Captions
  - Live-TV controls & EPG integration
  - TV remote / DPAD navigation
- Offline-first:
  - Locally available content must always remain playable without network.
  - Firebase and remote services are **optional enhancers**, never hard
    requirements for app startup or local playback.

If a change conflicts with these product principles, **stop**, propose a
contract update, and only continue once the vision document and this guide are
aligned.

---

## 3. Language & i18n Rules

- **Code language:**
  - All code, identifiers, comments, and docstrings must be in **English**.
- **User-facing text:**
  - No new hardcoded user-facing strings in Kotlin / Compose.
  - All user-visible text must go through Android string resources.
  - Every new user-facing string must be added for at least:
    - `values/strings.xml` (English)
    - `values-de/strings.xml` (German)
- **Debug / developer-only UI:**
  - May be English-only in meaning,
  - But still must use string resources (no literals in code).

If you introduce any new UI text, you **must** add resource entries in both
languages as part of the same change.

---

## 4. v2 Architecture Layers & Modules

### 4.1 Layers (Top → Bottom)

1. `AppShell` (in `:app-v2`)
2. `Feature Shells` (UI feature modules)
3. `Pipelines` (Telegram, Xtream, IO, Audiobook)
4. `PlaybackDomain` (pipeline-agnostic playback logic)
5. `Internal Player (SIP)` (Media3/ExoPlayer based)
6. `Core & Infrastructure` (models, persistence, Firebase, logging, tooling)

Each layer has a **strict dependency direction**: higher layers may depend on
lower layers as allowed below, never the other way around.

### 4.2 v2 Modules (Gradle)

### App & Core

- `:app-v2`
  - New entry app module for v2.
  - Hosts AppShell, DI wiring (Hilt), top-level navigation, and minimal
    startup UI.
  - Must **not** contain pipeline logic, player logic, or direct Xtream /
    Telegram SDK calls.

- `:core:model`
  - Cross-cutting models and interfaces:
    - `PlaybackContext`, `PlaybackType`
    - `Profile`, kids profile info
    - `FeatureId`, `FeatureDescriptor`, `FeatureRegistry`
    - `DeviceProfile`
    - Result / error types
  - Pure Kotlin; no UI dependencies.

- `:core:persistence`
  - Persistence abstractions and impls:
    - DataStore wrappers
    - Local DB obx where v1 reuse makes sense
    - File storage abstractions
  - Repositories such as:
    - `ProfileRepository`
    - `EntitlementRepository`
    - `LocalMediaRepository`
    - `SubtitleStyleStore`

- `:core:firebase`
  - Firebase façade (optional at runtime):
    - `FirebaseFeatureFlagProvider` implements `FeatureFlagProvider`
    - `FirebaseRemoteProfileStore` implements `RemoteProfileStore`
  - v2 must run without Firebase by falling back to local providers.

### Playback & Player

- `:playback:domain`
  - Pipeline-agnostic playback logic and contracts only.
  - Interfaces and default implementations for:
    - `ResumeManager`
    - `KidsPlaybackGate`
    - `SubtitleStyleManager`
    - `SubtitleSelectionPolicy`
    - `LivePlaybackController`
    - `TvInputController`
  - Depends only on `:core:model`, `:core:persistence`, and `:infra:logging`.
  - **Must not** depend on pipelines, UI, or Firebase.

- `:player:internal`
  - v2 Structured Internal Player (SIP), using Media3 / ExoPlayer.
  - Organized into packages such as:
    - `internal.state`, `internal.session`, `internal.source`, `internal.ui`,
      `internal.subtitles`, `internal.live`, `internal.tv`, `internal.mini`,
      `internal.system`, `internal.debug`.
  - Public Composable entrypoint:
    - `InternalPlayerEntry(playbackContext: PlaybackContext, ...)`.
  - Uses only pipeline-agnostic contracts from `:playback:domain` and
    `:core:model`.
  - **Must not** depend directly on any `:pipeline:*` or `:feature:*` module.

### Pipelines (no UI)

- `:pipeline:telegram`
  - Telegram media integration using tdlib-coroutines.
  - Contains domain models, repositories, download manager, settings provider,
    and `TelegramPlaybackSourceFactory`.
  - May provide helpers like `TelegramMediaItem.toPlaybackContext(...)`.
  - **No Compose, no feature UI, no dependency on `:player:internal`.**

- `:pipeline:xtream`
  - Xtream / IPTV integration.
  - Domain models (VOD, Series, Episode, LiveChannel, EPG),
    `XtreamCatalogRepository`, `XtreamLiveRepository`,
    `XtreamPlaybackSourceFactory`.
  - Reuses stable v1 code for HTTP, URL building, `DelegatingDataSourceFactory`,
    `RarDataSource`.
  - **No UI, no dependency on `:feature:*` or `:player:internal`.**

- `:pipeline:io`
  - Local / IO content integration.
  - `IoMediaItem`, `IoContentRepository`, `IoPlaybackSourceFactory`.

- `:pipeline:audiobook`
  - Audiobook integration.
  - `AudiobookItem`, `AudiobookRepository`, `AudiobookPlaybackSourceFactory`.
  - May start as minimal / stub but must exist from day one.

### Feature Shells (UI only)

- `:feature:home` – unified start screen.
- `:feature:library` – VOD/Series overview.
- `:feature:live` – Live-TV shell.
- `:feature:telegram-media` – Telegram media shell.
- `:feature:audiobooks` – Audiobook UI shell.
- `:feature:settings` – profiles, entitlements, preferences, subtitle styles.

Feature modules:

- Contain only UI + ViewModel logic.
- Depend on `:core:*`, `:playback:domain`, `:player:internal` (for
  `InternalPlayerEntry`) and relevant `:pipeline:*` modules via interfaces.
- Must use string resources with English and German entries.
- **Must not** depend directly on tdlib, ExoPlayer, or other raw SDKs.

### Infrastructure

- `:infra:logging` – unified logging (`UnifiedLog`, Crashlytics bridge, etc.).
- `:infra:tooling` – dev/QA tooling, static analysis wiring, optional
  architecture tests.

---

## 5. Allowed vs. Forbidden Dependencies

### 5.1 Allowed

- `:app-v2` → may depend on all v2 modules (`:core:*`, `:playback:domain`,
  `:player:internal`, `:pipeline:*`, `:feature:*`, `:infra:*`).
- `:feature:*` → may depend on:
  - `:core:*`
  - `:playback:domain`
  - `:player:internal`
  - Relevant `:pipeline:*`
  - `:infra:logging`
- `:pipeline:*` → may depend on:
  - `:core:model`
  - `:core:persistence`
  - `:infra:logging`
  - External SDKs (tdlib, HTTP, etc.).
- `:playback:domain` → may depend on `:core:model`, `:core:persistence`,
  `:infra:logging`.
- `:player:internal` → may depend on `:core:model`, `:playback:domain`,
  `:infra:logging`, Media3/ExoPlayer.
- `:core:*` → may depend on stdlib and minimal AndroidX/core libs, but **no**
  features, pipelines or player.
- `:infra:*` → may depend on `:core:*` and external tooling libs.

### 5.2 Forbidden (Architecture Errors)

- `:feature:*` **MUST NOT**:
  - Depend directly on tdlib types or Telegram SDK.
  - Depend directly on ExoPlayer / Media3.
  - Depend on other `:feature:*` modules except via
    `FeatureDescriptor` / `FeatureRegistry` where explicitly designed.

- `:pipeline:*` **MUST NOT**:
  - Depend on any `:feature:*` modules.
  - Depend on `:player:internal`.
  - Use Compose or any UI framework.

- `:player:internal` **MUST NOT**:
  - Depend on any `:feature:*` module.
  - Depend directly on `:pipeline:*` modules.
    - Instead, it uses pipeline-agnostic factories / descriptors configured by
      higher layers.

- `:playback:domain` **MUST NOT**:
  - Depend on any `:pipeline:*`.
  - Depend on Compose / Views.
  - Depend on Firebase directly.

Breaking these rules is considered an **architecture violation** and should be
prevented via static analysis (detekt, ArchUnit, etc.). If a legitimate use
case seems to require a forbidden dependency, update this document first.

---

## 6. Implementation Phases & Task Scoping

All v2 work follows the phases defined in
`v2-docs/IMPLEMENTATION_PHASES_V2.md`. Agents **must respect phase
boundaries**.

### 6.1 Phase Rules

- Only implement items listed for the **current phase**, unless explicitly
  instructed otherwise.
- For each phase, only modify the modules listed as "allowed to modify" in the
  phase description.
- Do not implement later-phase functionality early just because it seems
  convenient.

### 6.2 Legacy Isolation

- Legacy modules (`:app` and v1-specific code) are **off-limits** for v2
  tasks.
- They may be read for reference and porting, but must not be extended with
  new v2 features.

### 6.3 Task Scope Discipline

- A task should focus on **one module or a very small group** of modules.
- Do not perform "drive-by" changes in unrelated modules.
- Cross-module refactors require explicitly scoped architecture tasks.

---

## 7. Contracts Before Code & Handling Ambiguities

- Behavior and architecture are defined by the v2 docs first:
  - `APP_VISION_AND_SCOPE.md`
  - `ARCHITECTURE_OVERVIEW_V2.md`
  - `IMPLEMENTATION_PHASES_V2.md`
- If you find contradictions, missing rules, or unclear behavior:
  1. **Stop coding.**
  2. Propose a concrete change to the relevant markdown document(s) to resolve
     the ambiguity.
  3. Only continue implementation once the contract has been updated to
     reflect the intended behavior.

Do **not** implement diverging behavior without first aligning the contract.

---

## 8. Dependency Versions & Tooling

- Library versions are governed by `DEPENDENCY_POLICY.md` (once it exists).
- Agents **must not** arbitrarily bump dependencies as part of regular feature
  work.
- Dependency updates are allowed only in dedicated "dependency update" tasks.

Tooling requirements (ktlint, detekt, LeakCanary, StrictMode, coroutines test,
ArchUnit/architecture checks) are part of the v2 plan and should be wired
according to the implementation phases.

---

## 9. Multi-Agent / Parallel Work

- Scope tasks narrowly and respect module boundaries to minimize merge
  conflicts.
- Feature tasks:
  - Must not modify pipeline or player internals unless the task explicitly
    covers those modules.
- Pipeline tasks:
  - Must not modify feature modules or the internal player unless explicitly
    requested.
- AppShell / `:core:*`:
  - Should change infrequently and only in dedicated tasks (e.g. adding a new
    feature route or shared model).

When in doubt, prefer **smaller, well-scoped changes** and propose follow-up
tasks for broader refactors.

---

## 10. Mandatory Checklist for Any v2 Task

Before starting any v2-related implementation, agents must:

1. Confirm they are on branch `architecture/v2-bootstrap`.
2. Read (or re-skim for context):
   - `v2-docs/APP_VISION_AND_SCOPE.md`
   - `v2-docs/ARCHITECTURE_OVERVIEW_V2.md`
   - `v2-docs/IMPLEMENTATION_PHASES_V2.md`
3. Identify the **current implementation phase** and ensure the task fits into
   that phase.
4. List the modules that are allowed to change for this task and restrict
   changes to that set.
5. Ensure all new user-facing text uses string resources with English and
   German entries.
6. Verify that no new forbidden dependencies (Section 5) are introduced.

Only after this checklist passes should any code changes be made.

---

## 11. Relationship to Legacy `AGENTS.md`

- For branch `architecture/v2-bootstrap`, this file `AGENTS_V2.md` is the
  **v2-specific SSOT** for all agent work.
- The legacy `AGENTS.md` remains valid for v1 and general repo rules
  (build/CI, Telegram/TDLib constraints, etc.).
- In case of conflict between `AGENTS_V2.md` and `AGENTS.md` for **v2-specific
  topics**, `AGENTS_V2.md` wins for v2 work on this branch.

---

## 12. Approved Execution Permissions (User-Confirmed)

For branch `architecture/v2-bootstrap`, the user has explicitly approved the
following execution scope for AI agents:

1. **Workspace file access**
   - Agents may read and modify any file **within the repository
     workspace** (`/workspaces/FishIT-Player`) using the provided tooling
     (`read_file`, `apply_patch`, etc.), as long as changes are directly
     required for the current task and comply with v2/v1 separation rules.
   - Agents may **not** modify files outside the repository or system/
     home-level configuration.

2. **VS Code tasks and Gradle builds**
   - Agents may run existing VS Code tasks via `run_task`, in particular:
     - `shell: Safe Build Debug`
     - `shell: Safe Build Release`
     - `shell: Clean Build`
     - `shell: Stop All Daemons`
     - `shell: Memory Cleanup`
     - `shell: Show Memory Status`
   - If no appropriate task exists, agents may invoke Gradle directly via
     `run_in_terminal` (e.g. `./gradlew assembleDebug`, `./gradlew test`),
     provided the commands are non-interactive.
   - No `sudo` usage and no changes to system paths or tools.

3. **Shell commands for helper tasks**
   - Agents may execute non-interactive shell commands in the workspace
     (e.g. `ls`, `find`, `grep`, `tree`, `python script.py`) when needed for
     analysis, builds, or running the project.
   - Commands must not write outside the repository or alter system
     configuration.

4. **Network access from the terminal**
   - Agents may use `curl`/`wget` (or similar) for **read-only** HTTP(S)
     access to:
     - Public documentation and API references.
     - External source repositories or artefacts required for understanding
       or configuring dependencies.
   - No use of secrets, tokens, or authentication; no upload of sensitive
     project data.

5. **Project-wide search and analysis**
   - Agents may use all available search/analysis tools (`semantic_search`,
     `grep_search`, `list_dir`, `read_file`, etc.) across the entire
     workspace whenever it is useful for the task.
   - No access outside the workspace.

6. **Tests and quality tools**
   - Agents may run existing test and quality tasks (e.g. `./gradlew test`,
     ktlint, detekt, architecture checks) without further user confirmation
     when it is reasonable to validate non-trivial code changes.
   - Agents must not modify tests or quality tool configuration unless a
     task explicitly calls for it.

7. **Creating new files and modules**
   - Agents may create new files and directories within the repository (e.g.
     new v2 modules, Kotlin files, or documentation) when required by the
     task.
   - Existing legacy files/modules must not be renamed or deleted without an
     explicit task; legacy code remains read-only for v2 work as described in
     Section 1.

Within these bounds, agents are expected to execute v2 tasks **autonomously**,
without waiting for additional user confirmations for each build, test, or
edit, while still adhering to all architecture and safety rules defined in
this document and in `AGENTS.md`.
