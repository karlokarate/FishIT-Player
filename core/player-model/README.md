# core:player-model

**Purpose:** Primitive, source-agnostic types for the player system.

## ✅ Allowed
- `PlaybackContext` - What to play
- `PlaybackState` - Player state enum
- `PlaybackError` - Error representation
- `SourceType` - Content source enum
- Pure Kotlin data classes and enums

## ❌ Forbidden
- ANY external dependencies (no Media3, no Hilt, no Android SDK beyond basics)
- Source-specific logic (no Telegram/Xtream specifics)
- UI imports
- Network/persistence imports

## Public Surface
- `PlaybackContext`
- `PlaybackState`
- `PlaybackError`
- `SourceType`

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| Kotlin stdlib only | Everything else |

```
This is the BOTTOM of the player layer stack.
All other player modules depend on this.
```

## Design Principle

This module contains **only primitive types**. No behavior, no logic, no dependencies.
If you need to add something that requires a dependency, it belongs in a different module.
