# Feature: infra.logging.unified

## Metadata
- **ID:** `infra.logging.unified`
- **Scope:** APP
- **Owner:** `infra:logging`

## Dependencies
- None (foundational infrastructure)

## Guarantees
- Provides centralized logging facade (UnifiedLog API)
- Supports structured logging with tags and levels (VERBOSE, DEBUG, INFO, WARN, ERROR)
- Backend agnostic (currently Timber, can be swapped)
- Thread-safe logging operations
- Configurable minimum log level filtering

## Failure Modes
- Backend initialization failure → falls back to silent no-op
- Log overflow → handled by backend (Timber)

## Logging & Telemetry
- Self-logging disabled to avoid recursion
- Backend uses android.util.Log as ultimate fallback

## Test Requirements
- Unit tests for level filtering and tag formatting
- Mock backend tests for output verification
