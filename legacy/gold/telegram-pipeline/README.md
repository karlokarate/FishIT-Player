# Gold: Telegram Pipeline

Curated patterns from the v1 Telegram/TDLib integration.

## Documentation

✅ **`GOLD_TELEGRAM_CORE.md`** (341 lines) - Complete extraction of Telegram patterns

## Key Patterns Extracted

1. **Unified Telegram Engine** – Single TdlClient instance pattern
2. **Auth State Machine** – Clear sealed class hierarchy for auth flows
3. **Zero-Copy Streaming** – Delegate to FileDataSource for playback
4. **RemoteId-First URLs** – Cross-session stable media identification (`tg://file/<fileId>?remoteId=...`)
5. **Chat Browsing** – Cursor-based pagination for efficient history traversal
6. **Lazy Thumbnails** – On-demand thumbnail loading (not pre-fetched)
7. **Priority Downloads** – TDLib priority levels (32=critical, 16=high, 8=normal, 1=low)
8. **MP4 Header Validation** – Structure-based validation before playback

## v2 Target Modules

- `pipeline/telegram/tdlib/` - TDLib client abstraction (interface + impl)
- `player/internal/source/telegram/` - TelegramFileDataSource for streaming
- `pipeline/telegram/repository/` - TdlibTelegramContentRepository

## v2 Status

🔄 **Phase 2 In Progress** - TDLib integration actively being ported

See `/docs/v2/TELEGRAM_TDLIB_V2_INTEGRATION.md` for implementation status.

## Porting Checklist

- [ ] Port auth state machine to TelegramTdlibClient
- [ ] Port zero-copy streaming to player/internal
- [ ] Port RemoteId resolution logic
- [ ] Port chat browsing with cursor pagination
- [ ] Port lazy thumbnail loading
- [ ] Port priority download orchestration
- [ ] Port MP4 header validation
- [ ] Write unit tests for all patterns

## References

- **Gold Doc:** `GOLD_TELEGRAM_CORE.md` (this folder)
- **v1 Source:** `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/telegram/`
- **v2 Contract:** `/docs/v2/TELEGRAM_TDLIB_V2_INTEGRATION.md`
