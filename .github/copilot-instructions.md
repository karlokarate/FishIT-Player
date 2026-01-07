# GitHub Copilot Instructions for FishIT-Player

This document provides repository-specific instructions for GitHub Copilot to help you work more effectively in this Android TV/mobile streaming application codebase.

> ⚠️ **CRITICAL:** This file works in conjunction with `AGENTS.md`. All agents (Copilot, Codex, etc.) MUST follow both documents. In case of conflict, `AGENTS.md` takes precedence.

---

## 🚨 MANDATORY: Read Instructions Before ANY Action

> **HARD RULE (NO EXCEPTIONS):** Before performing ANY task, change, review, response, or action, ALL agents MUST:
>
> 1. **Read the instruction index first:**  
>    https://github.com/karlokarate/FishIT-Player/blob/architecture%2Fv2-bootstrap/.github%2Finstructions%2F_index.instructions.md
>
> 2. **Identify the path-scoped instruction file(s)** that apply to the files being modified or reviewed.
>
> 3. **Read and understand ALL applicable instructions** before planning or executing any changes.
>
> 4. **Verify path-scope compliance** in the planning phase - check that ALL planned changes conform to the relevant instructions.
>
> 5. **Any violation of path-scoped instructions is a VIOLATION** and must be corrected before proceeding.

**This rule applies to:**
- Code changes (new files, modifications, refactors)
- Code reviews and suggestions
- Answering questions about architecture
- Planning tasks or features
- Creating PRs or issues
- ANY interaction with the codebase

**Violation Consequences:**
- Changes that violate path-scoped instructions are considered bugs
- Reviews that miss instruction violations are incomplete
- Agents must self-audit against instructions before finalizing any output

---

## ⚠️ Path-Scoped Instructions (Auto-Applied)

This repository uses **path-scoped instruction files** in `.github/instructions/` that are **automatically applied** by VS Code Copilot when editing files in matching paths.

**Key modules with dedicated instructions:**

| Instruction File                           | Applies To                    |
| ------------------------------------------ | ----------------------------- |
| `core-model.instructions.md`               | `core/model/**`               |
| `pipeline.instructions.md`                 | `pipeline/**`                 |
| `player.instructions.md`                   | `player/**`                   |
| `playback.instructions.md`                 | `playback/**`                 |
| `infra-transport-telegram.instructions.md` | `infra/transport-telegram/**` |
| `infra-data.instructions.md`               | `infra/data-*/**`             |
| `feature-*.instructions.md`                | `feature/**`                  |

> **Full inventory:** See `AGENTS.md` Section 2.5 for complete list of 21 instruction files.

These instructions define **PLATIN quality standards** with:

- Allowed/forbidden imports
- Canonical patterns and code examples
- Hard rules for layer boundaries
- Module-specific best practices

> ⚠️ **For Cloud Tasks (Copilot Workspace, GitHub.com):** Path-scoped files are NOT auto-loaded.
> The critical rules are summarized below in "Layer Boundary Hard Rules".

---

## 🔴 Layer Boundary Hard Rules (CRITICAL for Cloud Tasks)

> These rules are extracted from path-scoped instructions for agents that cannot read `.github/instructions/`.

### Layer Hierarchy (top to bottom)

```
UI (feature/*) → Domain (core/*-domain) → Data (infra/data-*) → Pipeline (pipeline/*) → Transport (infra/transport-*) → Core Model (core/model)
```

### Forbidden Cross-Layer Imports

| Layer         | MUST NOT Import From                                                            |
| ------------- | ------------------------------------------------------------------------------- |
| **Pipeline**  | `infra/data-*`, `core/persistence`, `playback/*`, `feature/*`, `io.objectbox.*` |
| **Transport** | `pipeline/*`, `infra/data-*`, `playback/*`, `feature/*`, `core/persistence`     |
| **Data**      | Pipeline DTOs (`TelegramMediaItem`, `XtreamVodItem`, etc.)                      |
| **Playback**  | Pipeline DTOs - use `RawMediaMetadata` only                                     |
| **Player**    | `pipeline/*`, `infra/transport-*`, `infra/data-*`, TDLib types (`TdApi.*`)      |

### Pipeline Hard Rules

```kotlin
// ✅ CORRECT: Pipeline produces ONLY RawMediaMetadata
fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata

// ❌ FORBIDDEN: Exporting internal DTOs, computing globalId, normalization
fun getMediaItems(): List<TelegramMediaItem>  // WRONG
globalId = FallbackCanonicalKeyGenerator.generate(...)  // WRONG
val cleanedTitle = title.replace(...)  // WRONG → normalizer's job
```

### Player Hard Rules

```kotlin
// ✅ CORRECT: Player is 100% source-agnostic
class InternalPlayerSession @Inject constructor(
    private val sourceResolver: PlaybackSourceResolver,  // Abstraction
)

// ❌ FORBIDDEN: Source-specific code in player
class InternalPlayerSession @Inject constructor(
    private val telegramClient: TelegramFileClient,  // WRONG!
    private val xtreamUrlBuilder: XtreamUrlBuilder,  // WRONG!
)
```

### Transport Hard Rules (Telegram)

```kotlin
// ✅ CORRECT: Use typed interfaces
interface TelegramAuthClient { ... }
interface TelegramHistoryClient { ... }
interface TelegramFileClient { ... }

// ❌ FORBIDDEN: Exposing TDLib types
fun getMessage(): TdApi.Message  // WRONG - use TgMessage!
fun getClient(): TdlClient       // WRONG - never expose!
```

---

## ⚠️ MANDATORY: Contracts Folder

**Before making ANY code changes, you MUST read the relevant contracts from `/contracts/`:**

| Contract                            | Scope                  | When to Read                   |
| ----------------------------------- | ---------------------- | ------------------------------ |
| `GLOSSARY_v2_naming_and_modules.md` | Global naming          | **ALWAYS** - before any change |
| `MEDIA_NORMALIZATION_CONTRACT.md`   | Pipelines → Normalizer | Pipeline/metadata changes      |
| `LOGGING_CONTRACT_V2.md`            | All modules            | Logging code changes           |
| `INTERNAL_PLAYER_*.md`              | Player phases          | Player/playback changes        |
| `TELEGRAM_PARSER_CONTRACT.md`       | Telegram pipeline      | Telegram features              |

**Hard Rule:** Violations of contracts are bugs and must be fixed immediately.

See `AGENTS.md` Section 15 for full contract compliance requirements.

---

## Project Overview

FishIT-Player is a Kotlin-based Android application for streaming media content with support for:

- **Xtream Codes API** integration for live TV, VOD, and series
- **Telegram media integration** via TDLib for video streaming from Telegram chats
- **Android TV** with full DPAD/remote control support
- **Multi-profile system** with kid/guest profiles and content filtering

## Technology Stack

- **Language:** Kotlin 2.0+
- **UI:** Jetpack Compose with Material3
- **Database:** ObjectBox (primary), DataStore Preferences
- **Networking:** OkHttp 5.x, Coil 3.x for images
- **Media:** Media3/ExoPlayer with FFmpeg extension
- **Build:** Gradle 8.13+, AGP 8.5+, JDK 21

## Active Project Documentation

The `docs/` folder contains current project specifications and implementation guides:

### UI/Layout System (Fish\*)

- **`docs/fish_layout.md`** - Fish module overview: FishTheme tokens, FishTile, FishRow, FishHeader, content modules (VOD/Series/Live)
- **`docs/fish_migration_checklist.md`** - Migration status from legacy cards to Fish\* components
- **`docs/detail_scaffold.md`** - DetailScaffold v1: unified detail headers with hero backdrop, MetaChips, MediaActionBar
- **`docs/media_actions.md`** - MediaActionBar action model for detail screens (Play, Resume, Trailer, etc.)
- **`docs/tv_forms.md`** - FishForms: DPAD-first form components (Switch, Slider, TextField, Select)
- **`docs/ui_state.md`** - UiState pattern: Loading/Empty/Error/Success state handling

### Playback System

- **`docs/playback_launcher.md`** - PlaybackLauncher v1: centralized playback orchestration
- **`/contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md`** - Resume and kids/screen-time behavior contract
- **`docs/INTERNAL_PLAYER_REFACTOR_STATUS.md`** - Internal player modular refactor progress (Phase 1-3)
- **`docs/INTERNAL_PLAYER_REFACTOR_ROADMAP.md`** - Player refactor roadmap and phases

### Telegram Integration

- **`.github/tdlibAgent.md`** - **Single Source of Truth** for all TDLib/Telegram work
- **`docs/TDLIB_TASK_GROUPING.md`** - TDLib task clusters and parallelization strategy
- **`docs/AGENT_AUTOMATION_README.md`** - Automated workflows for TDLib cluster development

### Diagnostics & Tools

- **`docs/LOG_VIEWER.md`** - In-app log viewer feature for Telegram logs

## Code Conventions

### Kotlin Style

- Use `ktlint` for code formatting (`./gradlew ktlintFormat`)
- Follow Kotlin coding conventions
- Prefer immutable data (`val` over `var`, data classes)
- Use coroutines and Flows for async operations
- Avoid nullable types where possible; prefer sealed classes for state

### Compose UI

- Use `FocusKit` (`com.chris.m3usuite.ui.focus.FocusKit`) as the single entry point for TV focus handling
- Use `FishTheme`, `FishTile`, `FishRow` components from `ui/layout/` for consistent styling
- For TV buttons, use `TvButton`, `TvTextButton`, `TvOutlinedButton`, `TvIconButton` from `ui/common/TvButtons.kt`
- Use `MediaActionBar` for detail screen CTAs (Play, Resume, etc.)
- Apply `tvClickable`/`tvFocusableItem` modifiers for TV-focusable elements

### Data Layer

- ObjectBox is the primary store; avoid Room for new features
- Use repositories from `data/repo/` for data access
- For kid/guest profiles, always use `MediaQueryRepository` for filtered queries
- Xtream content uses `XtreamObxRepository` and `XtreamClient` from `core/xtream/`

### Telegram Integration

- Refer to `.github/tdlibAgent.md` as the Single Source of Truth for Telegram/TDLib work
- Use `T_TelegramServiceClient` (Unified Telegram Engine) for all TDLib operations
- Never access `TdlClient` directly outside `telegram/core` package

## Build & Test Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Testing
./gradlew testDebugUnitTest
./gradlew test

# Code Quality
./gradlew ktlintCheck        # Check code style
./gradlew ktlintFormat       # Auto-format code
./gradlew detekt             # Static analysis
./gradlew lintDebug          # Android lint

# Combined quality check
./gradlew ktlintCheck detekt lintDebug test
```

## Architecture Guidelines

### File Organization

- **UI screens:** `ui/screens/`
- **UI components:** `ui/layout/` (Fish\* components), `ui/common/`
- **Focus handling:** `ui/focus/FocusKit.kt`
- **Data repositories:** `data/repo/`
- **ObjectBox entities:** `data/obx/`
- **Xtream integration:** `core/xtream/`
- **Telegram integration:** `telegram/core/`, `telegram/ui/`, `telegram/work/`
- **Player components:** `player/`
- **Background work:** `work/`

### Key Patterns

1. **MVVM Architecture:** ViewModels expose `StateFlow` and intent methods
2. **UiState Pattern:** Use `UiState` + `StatusViews` for loading states
3. **Navigation:** Use `NavHostController.navigateTopLevel()` for top-level switches
4. **Focus Management:** Use `focusGroup()` containers with `FocusRequester` for initial focus

### TV-Specific Considerations

- Always provide focusable back navigation on empty screens
- Use `TvKeyDebouncer` for seek operations to prevent endless scrubbing
- Use `TvTextFieldFocusHelper` for form fields to prevent focus traps
- Test on real Fire TV/Android TV devices when possible

## Important Files to Reference

- **`AGENTS.md`:** ⚠️ **PRIMARY AUTHORITY** - Single source of truth for architecture and workflow rules
- **`/contracts/`:** ⚠️ **MANDATORY** - All binding contracts (read before any changes)
- **`V2_PORTAL.md`:** Entry point for v2 architecture documentation
- **`ARCHITECTURE_OVERVIEW.md`:** Detailed module documentation
- **`DEVELOPER_GUIDE.md`:** Build, test, and quality tool documentation
- **`.github/tdlibAgent.md`:** Telegram integration specifications (Single Source of Truth for TDLib)
- **`docs/fish_layout.md`:** Fish\* UI component specifications
- **`/contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md`:** Player resume/kids behavior contract

## What NOT to Do

- Don't modify `.gradle/`, `.idea/`, or `app/build/` artifacts
- Don't upgrade dependencies unless fixing builds
- Don't bypass `MediaQueryRepository` for kid/guest profile queries
- Don't use raw Material3 buttons in TV paths (use TvButton variants)
- Don't create new Room entities (use ObjectBox)
- Don't access TDLib/TdlClient directly outside `telegram.core` package
- **Don't make changes without reading the relevant `/contracts/` first**
- **Don't introduce naming that violates `/contracts/GLOSSARY_v2_naming_and_modules.md`**
- **Don't bypass layer boundaries defined in `AGENTS.md` Section 4**
- **Don't introduce duplicate DTO definitions across layers (see `AGENTS.md` Section 4.7)**
- **Don't use aliased imports for DTOs (`import ... as ApiXxx`) - this indicates a duplicate**
- **Don't create bridge/conversion functions (`toApi*`, `fromApi*`) between duplicate definitions**

## Feature Flags (BuildConfig)

This project uses feature flags to gate new functionality:

- **`DETAIL_SCAFFOLD_V1`** (default ON) - Unified detail headers
- **`MEDIA_ACTIONBAR_V1`** (default ON) - Centralized media actions
- **`PLAYBACK_LAUNCHER_V1`** (default ON) - Unified playback orchestration
- **`UI_STATE_V1`** (default ON) - UiState pattern for loading states
- **`TV_FORMS_V1`** (default ON) - DPAD-first form components

When adding new features, consider using feature flags to allow incremental rollout.

## Debugging & Diagnostics

- Use `DiagnosticsLogger` for structured event logging
- Use `PerformanceMonitor` for timing operations
- Global debug toggle in Settings enables navigation/DPAD/focus logging under `GlobalDebug` tag
- Telegram operations are logged via `TelegramLogRepository`

---

## 🔧 MCP Server Integration

This repository is configured with MCP (Model Context Protocol) servers that extend Copilot's capabilities:

- **GitHub MCP**: Full GitHub API access (issues, PRs, code search, CI/CD)
- **Sequential Thinking**: Long-term context for multi-step task chains

Agents can use these capabilities automatically when:
- Creating PRs from issues
- Analyzing CI/CD failures  
- Maintaining context across task chains
- Searching and understanding the codebase

### Configuration

MCP servers are configured in:
- `.devcontainer/devcontainer.json` - For Codespaces and devcontainer environments
- `.vscode/settings.json` - For local VS Code development

### Requirements

- **GITHUB_TOKEN** environment variable (automatically available in Codespaces)
- **Docker** for GitHub MCP Server (pre-installed in devcontainer)
- **Node.js/npx** for Sequential Thinking Server (pre-installed in devcontainer)

See `AGENTS.md` Section 16 for complete MCP server documentation.
