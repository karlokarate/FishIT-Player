# playback-telegram

**Purpose:** Creates `PlaybackContext` from `RawMediaMetadata` for Telegram media playback.

## ✅ Allowed
- Creating `PlaybackContext` from `RawMediaMetadata`
- `TelegramPlaybackSourceFactory` for player integration
- Using file download primitives from Transport
- DataSource implementations for Media3

## ❌ Forbidden
- Pipeline DTOs (`TelegramMediaItem`)
- Repository access
- Domain-level heuristics
- UI imports
- Direct TDLib calls (use Transport abstractions)

## Public Surface
- `TelegramPlaybackSourceFactory` interface
- `TelegramFileDataSource` (Media3 DataSource)

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| `core:model`, `transport-telegram` (file primitives) | `pipeline/*`, `data/*`, `feature/*` |

```
Transport ← Pipeline ← Data ← Domain ← UI
   │                              │
   └──────► Playback ◄────────────┘
               ▲
              YOU
```

## Common Mistakes
1. ❌ Importing `TelegramMediaItem` from pipeline
2. ❌ Calling repositories to fetch media
3. ❌ Using TDLib API directly instead of Transport
