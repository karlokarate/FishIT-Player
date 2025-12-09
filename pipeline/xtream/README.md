# pipeline-xtream

**Purpose:** Transforms Xtream API responses into `RawMediaMetadata` for Data layer consumption.

## ✅ Allowed
- Receiving transport DTOs from `XtreamApiClient`
- Internal DTOs: `XtreamVodItem`, `XtreamSeriesItem`, `XtreamEpisode`, `XtreamChannel`
- Mapping internal DTOs → `RawMediaMetadata`
- Emitting `XtreamCatalogEvent` streams
- `XtreamCatalogSource` for catalog iteration

## ❌ Forbidden
- Direct HTTP calls
- `XtreamApiClient` usage (use via DI adapter)
- Repository access (`data/*`)
- Playback logic
- UI imports
- Normalization heuristics (TMDB, title cleanup)
- Persistence entities

## Public Surface
- `XtreamCatalogPipeline`, `XtreamCatalogEvent`, `XtreamCatalogItem`
- `toRawMediaMetadata()` extension

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| `transport-xtream`, `core:model` | `data/*`, `playback/*`, `core:persistence`, `feature/*` |

```
Transport ← Pipeline ← Data ← Domain ← UI
              ▲
             YOU
```

## Common Mistakes
1. ❌ Importing OkHttp directly (belongs in Transport)
2. ❌ Exporting `XtreamVodItem` to Data/Playback layers
3. ❌ Storing items to DB (belongs in Data layer)
4. ❌ Placing Adapter/Source implementations here (→ `transport-xtream`)
5. ❌ Implementing `XtreamApiClient` inside pipeline

## ⚠️ Guard Flags (AGENTS.md 4.6)
- **No *Impl classes** except `XtreamCatalogPipelineImpl`
- **No cross-layer imports** (Transport ⇄ Pipeline ⇄ Data)
