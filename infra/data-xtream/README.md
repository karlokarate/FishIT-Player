# data-xtream

**Purpose:** Persists Xtream catalog as `RawMediaMetadata`, provides Flows for Domain/UI consumption.

## ✅ Allowed
- Storing `RawMediaMetadata` to persistence
- Providing `Flow<List<RawMediaMetadata>>` observation APIs
- `upsertAll()` for catalog sync
- Querying VOD, Series, Live content
- Using persistence entities internally

## ❌ Forbidden
- Pipeline DTOs (`XtreamVodItem`, `XtreamSeriesItem`, `XtreamChannel`)
- Transport DTOs (`XtreamLiveStream`, `XtreamVodStream`)
- Playback logic
- UI imports
- Network/HTTP calls

## Public Surface
- `XtreamCatalogRepository`, `XtreamLiveRepository` interfaces
- `XtreamEpgData` (data-layer type)

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| `core:model`, `core:persistence` | `pipeline/*`, `transport/*`, `playback/*`, `feature/*` |

```
Transport ← Pipeline ← Data ← Domain ← UI
                        ▲
                       YOU
```

## Common Mistakes
1. ❌ Importing `XtreamVodItem` from pipeline
2. ❌ Defining extensions on pipeline types
3. ❌ Making HTTP calls to Xtream API
