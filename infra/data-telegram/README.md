# data-telegram

**Purpose:** Persists Telegram media as `RawMediaMetadata`, provides Flows for Domain/UI consumption.

## ✅ Allowed
- Storing `RawMediaMetadata` to persistence
- Providing `Flow<List<RawMediaMetadata>>` observation APIs
- `upsertAll()` for catalog sync
- Querying by chat, search, filters
- Using persistence entities internally

## ❌ Forbidden
- Pipeline DTOs (`TelegramMediaItem`, `TelegramChatSummary`)
- Transport DTOs (`TgMessage`, `TgContent`)
- TDLib types (`TdApi.*`)
- Playback logic
- UI imports
- Network calls

## Public Surface
- `TelegramContentRepository` interface
- `TelegramChatInfo` (data-layer type)

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
1. ❌ Importing `TelegramMediaItem` from pipeline
2. ❌ Using `TgMessage` instead of `RawMediaMetadata`
3. ❌ Calling TDLib directly from repository
