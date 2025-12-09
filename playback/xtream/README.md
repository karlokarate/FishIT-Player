# playback-xtream

**Purpose:** Creates `PlaybackContext` from `RawMediaMetadata` for Xtream media playback.

## ✅ Allowed
- Creating `PlaybackContext` from `RawMediaMetadata`
- `XtreamPlaybackSourceFactory` for player integration
- URL building for stream playback
- DataSource implementations for Media3

## ❌ Forbidden
- Pipeline DTOs (`XtreamVodItem`, `XtreamChannel`)
- Repository access
- Domain-level heuristics
- UI imports
- Direct HTTP calls (use Transport abstractions)

## Public Surface
- `XtreamPlaybackSourceFactory` interface
- `XtreamStreamDataSource` (Media3 DataSource)

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| `core:model`, `transport-xtream` (URL builder) | `pipeline/*`, `data/*`, `feature/*` |

```
Transport ← Pipeline ← Data ← Domain ← UI
   │                              │
   └──────► Playback ◄────────────┘
               ▲
              YOU
```

## Common Mistakes
1. ❌ Defining extensions on `XtreamVodItem`
2. ❌ Importing pipeline model types
3. ❌ Accessing Xtream repositories
