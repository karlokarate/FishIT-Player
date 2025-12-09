# playback-domain

**Purpose:** Orchestrates app-level use cases, coordinates pipelines, repositories, and playback.

## ✅ Allowed
- Use cases (Play, Sync, Search)
- Catalog sync orchestration
- Playback orchestration via `PlaybackContext`
- Coordinating repositories and normalizers
- App-level business logic

## ❌ Forbidden
- Transport imports (TDLib, OkHttp)
- Pipeline DTOs (`TelegramMediaItem`, `XtreamVodItem`)
- UI framework imports (Compose, Views)
- Direct network calls
- Direct persistence access (use repositories)

## Public Surface
- `PlayUseCase`, `SyncCatalogUseCase`
- `SearchUseCase`, `GetMediaUseCase`
- Domain orchestration interfaces

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| `core:model`, `data/*`, `playback/*` | `transport/*`, `pipeline/*` (DTOs), `feature/*` |

```
Transport ← Pipeline ← Data ← Domain ← UI
                               ▲
                              YOU
```

## Common Mistakes
1. ❌ Importing `TelegramMediaItem` from pipeline
2. ❌ Making TDLib/Xtream API calls directly
3. ❌ Using Compose/UI imports in use cases
