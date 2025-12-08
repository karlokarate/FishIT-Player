> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Telegram Pipeline Cleanup Checklist

- [ ] Start: Align Telegram selection sources  
  - `StartViewModel` reads `tgSelectedVodChatsCsv`/`tgSelectedSeriesChatsCsv` while the settings UI writes only `tgSelectedChatsCsv`. Migrate Start (and repos) to the unified selection or write the per-type CSVs from settings so rows render for the selected chats.
- [ ] Library UI: Remove legacy data path  
  - Replace `getTelegramVodByChat()`/`getTelegramSeriesByChat()` (legacy `ObxTelegramMessage` + `MediaItem`) with the new `ObxTelegramItem` domain flow in `LibraryScreen` so Telegram rows show synced items.
- [ ] Prefetcher: Switch to new store and ensure service is started  
  - `TelegramThumbPrefetcher` currently observes legacy flows and uses the downloader without guaranteeing `T_TelegramServiceClient.ensureStarted()`. Point it to `ObxTelegramItem` data and guard downloader access by starting/binding the service.
- [ ] Player MIME resolver: Drop legacy OBX lookup  
  - `InternalPlaybackSourceResolver.resolveTelegramMimeFromObx` still queries `ObxTelegramMessage`. Replace with `ObxTelegramItem` (or remoteId-first inference) to keep playback working after legacy removal.
- [ ] Playback URLs: Eliminate fileId-only paths  
  - Legacy `buildTelegramUrl`/`toMediaItem` still emit `tg://` URLs without `remoteId`/`uniqueId`. Ensure all playback requests use `TelegramPlayUrl` with remoteId-first fields so TDLib rotation doesn’t break playback.
- [ ] Detail screens: Remove legacy `TelegramDetailScreen` path  
  - Delete/retire the legacy detail flow that reads `ObxTelegramMessage`; route all Telegram navigation to the new `TelegramItemDetailScreen` (remoteId-first, `ObxTelegramItem`).
- [ ] Live updates: Wire `TelegramUpdateHandler`  
  - The handler exists but isn’t started. Bind it after auth READY so `UpdateNewMessage` events are persisted into `ObxTelegramItem`, keeping UI fresh without manual sync.
- [ ] Data duplication cleanup  
  - Remove remaining `ObxTelegramMessage` usages and entities once all readers/writers are migrated, to avoid double stores and stale data.
- [ ] CI/QA sanity  
  - After migration, verify: Start + Library rows show Telegram items, thumbnails load, playback works (tg:// remoteId-first), and no code path references legacy OBX.
