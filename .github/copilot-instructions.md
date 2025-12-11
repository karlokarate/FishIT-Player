# GitHub Copilot Instructions for FishIT-Player

This document provides repository-specific instructions for GitHub Copilot to help you work more effectively in this Android TV/mobile streaming application codebase.

> ⚠️ **CRITICAL:** This file works in conjunction with `AGENTS.md`. All agents (Copilot, Codex, etc.) MUST follow both documents. In case of conflict, `AGENTS.md` takes precedence.

---

## ⚠️ MANDATORY: Contracts Folder

**Before making ANY code changes, you MUST read the relevant contracts from `/contracts/`:**

| Contract | Scope | When to Read |
|----------|-------|--------------|
| `GLOSSARY_v2_naming_and_modules.md` | Global naming | **ALWAYS** - before any change |
| `MEDIA_NORMALIZATION_CONTRACT.md` | Pipelines → Normalizer | Pipeline/metadata changes |
| `LOGGING_CONTRACT_V2.md` | All modules | Logging code changes |
| `INTERNAL_PLAYER_*.md` | Player phases | Player/playback changes |
| `TELEGRAM_PARSER_CONTRACT.md` | Telegram pipeline | Telegram features |

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

### UI/Layout System (Fish*)
- **`docs/fish_layout.md`** - Fish module overview: FishTheme tokens, FishTile, FishRow, FishHeader, content modules (VOD/Series/Live)
- **`docs/fish_migration_checklist.md`** - Migration status from legacy cards to Fish* components
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
- **UI components:** `ui/layout/` (Fish* components), `ui/common/`
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
- **`docs/fish_layout.md`:** Fish* UI component specifications
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
