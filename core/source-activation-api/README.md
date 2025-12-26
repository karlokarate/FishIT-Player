# core:source-activation-api

**Purpose:** API contracts for source activation state management.

**Package:** `com.fishit.player.core.sourceactivation`

---

## Overview

This module provides source-agnostic API contracts for tracking which pipeline sources 
(Xtream, Telegram, IO) are currently active. It was extracted from `core:model` to 
maintain the principle that `core:model` contains only pure data classes, not store/state interfaces.

## Types

| Type | Description |
|------|-------------|
| `SourceId` | Enum: `XTREAM`, `TELEGRAM`, `IO` (Note: distinct from `PipelineIdTag` which includes `AUDIOBOOK`) |
| `SourceErrorReason` | Enum for activation errors: `LOGIN_REQUIRED`, `INVALID_CREDENTIALS`, `PERMISSION_MISSING`, `TRANSPORT_ERROR` |
| `SourceActivationState` | Sealed interface: `Inactive`, `Active`, `Error(reason)` |
| `SourceActivationSnapshot` | Data class with activation state for all sources + computed `activeSources`, `hasActiveSources` |
| `SourceActivationStore` | Interface for observing/modifying activation state |

## Allowed

- Defining API contracts (interfaces, sealed classes, enums)
- Adding new `SourceErrorReason` values as needed
- Adding convenience extensions on types defined here

## Forbidden

- Implementation classes (belong in `infra/work`)
- Transport-layer dependencies
- UI or Android framework dependencies
- Persistence or database code

## Dependencies

- `kotlinx-coroutines-core` (for `Flow` in `SourceActivationStore`)

## Consumers

- `core:catalog-sync` – uses `SourceId` for worker chain
- `infra:work` – implements `SourceActivationStore`
- `infra:data-xtream` – calls `setXtreamActive/Inactive`
- `feature:home`, `feature:settings` – observes activation state
- `app-v2` – bootstrap and observers

## Architecture Context

This module permanently fixes the circular dependency that previously existed:

```text
Before (circular):
  catalog-sync → data-xtream → catalog-sync (via SourceActivationStore)

After (no cycle):
  source-activation-api ← catalog-sync
  source-activation-api ← data-xtream
```

Both `catalog-sync` and `data-xtream` depend on `source-activation-api`, 
but neither depends on the other.

## Contract Reference

- `AGENTS.md` Section 4 – Layer Boundaries
- `GLOSSARY_v2_naming_and_modules.md` Section 1.5 – Source Activation Terms
