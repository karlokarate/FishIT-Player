# pipeline-telegram

**Purpose:** Transforms `TgMessage` from Transport into `RawMediaMetadata` for Data layer consumption and streams live media updates (with warm-up ingestion when chats heat up).

## ✅ Allowed
- Receiving `TgMessage`, `TgContent` from Transport
- Internal DTOs: `TelegramMediaItem`, `TelegramChatSummary`
- Mapping internal DTOs → `RawMediaMetadata`
- Emitting `TelegramCatalogEvent` streams
- `TelegramMessageCursor` for paginated history
- **Structured Bundle Grouping** (per `TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md`)
- **Pass-through of structured TMDB-IDs** from TEXT messages

## ❌ Forbidden
- Direct TDLib API calls (`TdApi.*`)
- Network calls / file downloads
- Repository access (`data/*`)
- Playback logic
- UI imports
- Normalization heuristics (TMDB, title cleanup)
- Persistence entities (`ObxTelegram*`)
- **TMDB-Lookups** (only pass-through of source-provided IDs allowed)

## Public Surface
- `TelegramCatalogPipeline`, `TelegramCatalogEvent`, `TelegramCatalogItem`
- `toRawMediaMetadata()` extension

## Structured Bundles (NEW)

Per `contracts/TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md`:

- **Bundle:** 2-3 messages with identical timestamp forming one media item
- **Supported Types:** `FULL_3ER` (PHOTO+TEXT+VIDEO), `COMPACT_2ER` (TEXT+VIDEO)
- **Pass-Through Fields:** `tmdbId`, `year`, `fsk`, `genres`, `rating` from TEXT messages
- **No Normalization:** All structured fields are passed RAW to `RawMediaMetadata`

See [TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md](../../docs/v2/TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md) for implementation details.

## Dependencies
| May Import | Must Never Import |
|------------|-------------------|
| `transport-telegram`, `core:model` | `data/*`, `playback/*`, `core:persistence`, `feature/*` |

\`\`\`
Transport ← Pipeline ← Data ← Domain ← UI
              ▲
             YOU
\`\`\`

## Common Mistakes
1. ❌ Importing `ObxTelegramMessage` (persistence layer)
2. ❌ Exporting `TelegramMediaItem` to Data layer
3. ❌ Calling TMDB/IMDB APIs directly
4. ❌ Placing Adapter/Source implementations here (→ `transport-telegram`)
5. ❌ Importing OkHttp or TDLib directly
6. ❌ Computing `globalId` in pipeline (normalizer's job)
7. ❌ Normalizing structured field values

## ⚠️ Guard Flags (AGENTS.md 4.6)
- **No *Impl classes** except `TelegramCatalogPipelineImpl`
- **No cross-layer imports** (Transport ⇄ Pipeline ⇄ Data)

## Related Contracts
- [TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md](/contracts/TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md)
- [MEDIA_NORMALIZATION_CONTRACT.md](/docs/v2/MEDIA_NORMALIZATION_CONTRACT.md)
- [TELEGRAM_PARSER_CONTRACT.md](/contracts/TELEGRAM_PARSER_CONTRACT.md)
