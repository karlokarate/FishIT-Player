# Feature: ui.screen.home

## Metadata
- **ID:** `ui.screen.home`
- **Scope:** UI_SCREEN
- **Owner:** `feature:home`

## Dependencies
- `core:feature-api` - FeatureRegistry for capability detection
- `infra:logging` - Unified logging

## Guarantees
- Provides main landing page and navigation hub
- Displays available content sources based on registered features
- Adapts UI dynamically based on feature availability
- Logs feature availability at screen initialization

## Failure Modes
- No features available → show empty state with onboarding
- Partial feature availability → show only available sections

## Logging & Telemetry
- Log tag: `HomeViewModel`
- Events: screen_initialized, feature_availability_logged

## Test Requirements
- ViewModel tests for feature detection logic
- Compose UI tests for dynamic section rendering
