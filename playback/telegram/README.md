# playback-telegram

**Purpose:** Creates `PlaybackContext` from `RawMediaMetadata` for Telegram media playback.

## Telegram Platinum Playback (v2)

**Key Feature:** All Telegram video files (MP4, MKV, WebM, AVI) are playable. "moov atom not found" is NEVER a fatal error.

**Playback Modes:**
- **PROGRESSIVE_FILE:** MP4 with moov atom at start (faststart-optimized) → Fast progressive streaming
- **FULL_FILE:** MKV, WebM, AVI, or MP4 without moov → Full download before playback

See `TelegramPlaybackMode.kt` and `TelegramPlaybackModeDetector.kt` for implementation details.

## ✅ Allowed
- Creating `PlaybackContext` from `RawMediaMetadata`
- `TelegramPlaybackSourceFactory` for player integration
- Using file download primitives from Transport
- DataSource implementations for Media3
- MIME-based playback mode detection

## ❌ Forbidden
- Pipeline DTOs (`TelegramMediaItem`)
- Repository access
- Domain-level heuristics
- UI imports
- Direct TDLib calls (use Transport abstractions)

## Public Surface
- `TelegramPlaybackSourceFactory` interface
- `TelegramFileDataSource` (Media3 DataSource)
- `TelegramPlaybackMode` enum (playback mode types)
- `TelegramPlaybackModeDetector` object (MIME-based mode selection)

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| `core:model`, `transport-telegram` (file primitives), `playback:domain` | `pipeline/*`, `data/*`, `feature/*` |

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
4. ❌ Treating "moov not found" as a fatal error (it's now a fallback to FULL_FILE mode)
