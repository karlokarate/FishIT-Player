# data-telegram

**Purpose:** Persists Telegram media as `RawMediaMetadata`, provides Flows for Domain/UI consumption.

## ✅ Allowed
- Storing `RawMediaMetadata` to persistence
- Providing `Flow<List<RawMediaMetadata>>` observation APIs
- `upsertAll()` for catalog sync
- Querying by chat, search, filters
- Using persistence entities internally
- **Adapters:** Implementing feature-level domain interfaces (e.g. `feature/.../domain/TelegramAuthRepository`) that map to transport APIs
- **Transport API usage:** Importing transport API surfaces (e.g. `infra/transport-telegram/api/*`, `TelegramAuthClient`) without touching transport internals

## ❌ Forbidden
- Pipeline DTOs (`TelegramMediaItem`, `TelegramChatSummary`)
- Transport DTOs (`TgMessage`, `TgContent`)
- TDLib types (`TdApi.*`)
- Transport internals/impl (`infra/transport-telegram/internal`, `DefaultTelegramClient`, `TdlibAuthSession`)
- UI imports (`feature/*/ui`, `feature/*/presentation`)
- Playback logic
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
