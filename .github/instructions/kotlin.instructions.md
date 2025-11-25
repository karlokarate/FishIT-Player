---
applyTo: "**/*.kt"
---

# Kotlin Development Guidelines

## Code Style
- Use `ktlint` formatting: run `./gradlew ktlintFormat` before committing
- Prefer immutable data: `val` over `var`, data classes for models
- Use coroutines and Flows for async operations
- Avoid nullable types where possible; prefer sealed classes for state

## Compose UI
- Use `FocusKit` from `ui/focus/FocusKit.kt` for all TV focus handling
- Use Fish* components (`FishTheme`, `FishTile`, `FishRow`) from `ui/layout/`
- For TV buttons, use `TvButton*` variants from `ui/common/TvButtons.kt`
- Apply `tvClickable`/`tvFocusableItem` modifiers for focusable TV elements
- Use `MediaActionBar` for detail screen actions

## Data Layer
- ObjectBox is the primary store; avoid creating new Room entities
- Use repositories from `data/repo/` for data access
- For kid/guest profiles, use `MediaQueryRepository` for filtered queries

## Telegram Integration
- Use `T_TelegramServiceClient` for all TDLib operations
- Never access `TdlClient` directly outside `telegram/core` package
- Refer to `.github/tdlibAgent.md` for Telegram specifications

## Testing
- Unit tests go in `app/src/test/`
- Instrumented tests go in `app/src/androidTest/`
- Run tests with `./gradlew testDebugUnitTest`
