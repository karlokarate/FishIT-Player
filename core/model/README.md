# core-model

**Purpose:** Central, source-agnostic data models shared across all layers.

## ✅ Allowed
- `RawMediaMetadata` definition
- `MediaType`, `RawMediaKind`, `SourceType` enums
- `ImageRef` for image references
- `ExternalIds` for TMDB/IMDB IDs
- Simple value types (IDs, timestamps)

## ❌ Forbidden
- Source-specific logic (Telegram, Xtream)
- Network / HTTP calls
- Database / persistence code
- Playback logic
- UI imports
- Business logic / heuristics

## Public Surface
- `RawMediaMetadata` data class
- `MediaType`, `SourceType`, `RawMediaKind` enums
- `ImageRef`, `ExternalIds` types

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| Kotlin stdlib only | Everything else |

```
              core:model
                  ▲
    ┌─────────────┼─────────────┐
Transport    Pipeline    Data    Playback
```

## Common Mistakes
1. ❌ Adding Telegram/Xtream specific fields
2. ❌ Importing TDLib or OkHttp types
3. ❌ Adding normalization logic here
