# core-metadata-normalizer

**Purpose:** Normalizes `RawMediaMetadata` into enriched domain models via heuristics and external lookups.

## ✅ Allowed
- Title cleanup & normalization
- Season/Episode extraction from titles
- TMDB/IMDB lookups (via modular provider)
- Adult/Family content heuristics
- Language/version detection
- Producing `DomainMediaItem`

## ❌ Forbidden
- Transport imports (TDLib, OkHttp)
- Pipeline imports
- Data/Repository access
- Playback logic
- UI imports
- Source-specific logic

## Public Surface
- `MetadataNormalizer` interface
- `DomainMediaItem`, `DomainMediaType`
- `NormalizationResult`

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| `core:model`, metadata providers | `transport/*`, `pipeline/*`, `data/*`, `playback/*` |

```
Pipeline ──► core:metadata-normalizer ──► Data
                      ▲
                     YOU
```

## Common Mistakes
1. ❌ Importing `TelegramMediaItem` or `XtreamVodItem`
2. ❌ Making raw HTTP calls (use provider abstraction)
3. ❌ Storing results to DB (Data layer responsibility)
