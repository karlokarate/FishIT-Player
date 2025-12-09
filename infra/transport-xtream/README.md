# transport-xtream

**Purpose:** HTTP client for Xtream Codes API, provides raw API responses to Pipeline layer.

## ✅ Allowed
- HTTP calls via OkHttp
- `XtreamApiClient` for API requests
- `XtreamDiscovery` (port & capability detection)
- `XtreamUrlBuilder` for URL construction
- Mapping API JSON → transport DTOs

## ❌ Forbidden
- `XtreamVodItem`, `XtreamSeriesItem` (pipeline DTOs)
- `RawMediaMetadata`
- Repository access
- Playback logic
- UI imports
- Normalization / TMDB

## Public Surface
- `XtreamApiClient` interface
- `XtreamDiscovery`, `XtreamUrlBuilder`
- API response DTOs (`XtreamLiveStream`, `XtreamVodStream`, etc.)

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| `core:model`, OkHttp | `pipeline/*`, `data/*`, `playback/*`, `feature/*` |

```
Transport ← Pipeline ← Data ← Domain ← UI
   ▲
  YOU
```

## Common Mistakes
1. ❌ Creating `XtreamVodItem` here (belongs in Pipeline)
2. ❌ Importing pipeline model types
3. ❌ Accessing repositories from transport
