# pipeline-telegram

**Purpose:** Transforms `TgMessage` from Transport into `RawMediaMetadata` for Data layer consumption.

## ✅ Allowed
- Receiving `TgMessage`, `TgContent` from Transport
- Internal DTOs: `TelegramMediaItem`, `TelegramChatSummary`
- Mapping internal DTOs → `RawMediaMetadata`
- Emitting `TelegramCatalogEvent` streams
- `TelegramMessageCursor` for paginated history

## ❌ Forbidden
- Direct TDLib API calls (`TdApi.*`)
- Network calls / file downloads
- Repository access (`data/*`)
- Playback logic
- UI imports
- Normalization heuristics (TMDB, title cleanup)
- Persistence entities (`ObxTelegram*`)

## Public Surface
- `TelegramCatalogPipeline`, `TelegramCatalogEvent`, `TelegramCatalogItem`
- `toRawMediaMetadata()` extension

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| `transport-telegram`, `core:model` | `data/*`, `playback/*`, `core:persistence`, `feature/*` |

```
Transport ← Pipeline ← Data ← Domain ← UI
              ▲
             YOU
```

## Common Mistakes
1. ❌ Importing `ObxTelegramMessage` (persistence layer)
2. ❌ Exporting `TelegramMediaItem` to Data layer
3. ❌ Calling TMDB/IMDB APIs directly
