# transport-telegram

**Purpose:** Wraps TDLib SDK, provides raw Telegram DTOs (`TgMessage`, `TgChat`, `TgContent`) to higher layers.

## ✅ Allowed

- TDLib client lifecycle & calls
- Mapping `TdApi.*` → `TgMessage`, `TgChat`, `TgContent`, `TgThumbnail`
- Auth state machine (login, code, password, ready)
- Connection state handling
- File resolution & download primitives
- TDLib logging bridge via `TdlibClientProvider.installLogging()` (UnifiedLog)

## ❌ Forbidden

- `RawMediaMetadata` (belongs to Pipeline)
- Pipeline imports (`pipeline/*`)
- Repository access (`data/*`)
- Playback logic
- UI imports
- Normalization / TMDB / heuristics

## Public Surface

- `TelegramTransportClient` interface
- `TgMessage`, `TgChat`, `TgContent`, `TgThumbnail` DTOs
- `TdlibClientProvider` for client access

## Dependencies

| May Import | Must Never Import |
|------------|-------------------|
| `core:model` (primitives only) | `pipeline/*`, `data/*`, `playback/*`, `feature/*` |

```text
Transport ← Pipeline ← Data ← Domain ← UI
   ▲
  YOU
```

## Common Mistakes

1. ❌ Returning `RawMediaMetadata` instead of `TgMessage`
2. ❌ Importing `TelegramMediaItem` from pipeline
3. ❌ Calling repositories directly from transport
