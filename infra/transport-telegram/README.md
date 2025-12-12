# transport-telegram

**Purpose:** Wraps TDLib SDK, provides typed Telegram interfaces and wrapper DTOs (`TgMessage`, `TgChat`, `TgContent`) to higher layers.

**Migration Status:** 🔄 Phase B1 (Typed Interface Contracts) - Interfaces created, implementation pending

## ✅ Allowed

- TDLib client lifecycle & calls (internal to `DefaultTelegramClient`)
- Mapping `TdApi.*` → `TgMessage`, `TgChat`, `TgContent`, `TgThumbnail`
- Auth state machine (login, code, password, ready)
- Connection state handling
- File resolution & download primitives
- TDLib logging bridge via internal configuration (UnifiedLog)

## ❌ Forbidden

- `RawMediaMetadata` (belongs to Pipeline)
- Pipeline imports (`pipeline/*`)
- Repository access (`data/*`)
- Playback logic
- UI imports
- Normalization / TMDB / heuristics
- Exposing `TdlibClientProvider` to upper layers (v1 legacy pattern)

## Public Surface (v2 Typed Interfaces)

The following typed interfaces are exported for consumption by pipeline and imaging layers:

| Interface | Purpose |
|-----------|---------|
| `TelegramAuthClient` | Authentication operations (login, logout, auth state) |
| `TelegramHistoryClient` | Chat history, message fetching → returns `TgMessage` |
| `TelegramFileClient` | File download operations → returns `TgFile` |
| `TelegramThumbFetcher` | Thumbnail fetching for Coil integration |

**Internal (not exported):**
- `DefaultTelegramClient` – implements all interfaces, owns TDLib state
- `TdlibClientProvider` – ⚠️ **v1 legacy pattern**, internal use only, must NOT be exposed to upper layers

## Wrapper DTOs

- `TgMessage` – Transport-level message wrapper
- `TgChat` – Transport-level chat wrapper
- `TgContent` – Media content wrapper (video, audio, document)
- `TgThumbnail` – Thumbnail wrapper

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
4. ❌ Exposing `TdlibClientProvider` or TDLib types to upper layers
