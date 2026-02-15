# Feature: telegram.full_history_streaming

## Metadata

| Property | Value |
|----------|-------|
| **ID** | `telegram.full_history_streaming` |
| **Scope** | `PIPELINE` |
| **Owner** | `pipeline:telegram` |
| **Status** | Active |

---

## Description

This feature enables complete, efficient scanning of Telegram chat history for media content. It provides cursor-based paging without arbitrary limits, supporting both initial sync and incremental updates.

---

## Dependencies

| FeatureId | Required | Description |
|-----------|----------|-------------|
| `media.normalize` | Yes | For normalizing raw Telegram metadata to canonical MediaItem |
| `infra.logging.unified` | Yes | For structured logging of sync operations |

---

## Guarantees

### Functional Guarantees

1. **Complete History Access**
   - Can scan entire chat history from newest to oldest message
   - No arbitrary message count limits during sync
   - Supports both public channels and private chats (with proper auth)

2. **Cursor-Based Paging**
   - Stable cursor position across app restarts
   - Resume from last known position after interruption
   - Efficient memory usage via streaming (not loading all at once)

3. **Media Detection**
   - Detects video messages (`messageVideo`)
   - Detects documents with video MIME types
   - Detects audio/podcast content
   - Extracts metadata from message captions

### Performance Guarantees

| Metric | Target |
|--------|--------|
| Message fetch batch size | 50-100 messages |
| Max memory per sync session | < 50 MB |
| Incremental sync latency | < 2s for recent messages |

---

## Failure Modes

### F1: Network Disconnection

- **Trigger:** Network unavailable during sync
- **Behavior:** Sync pauses, resumes from cursor when network returns
- **Error Code:** `TELEGRAM_SYNC_NETWORK_ERROR`
- **Fallback:** Use cached data from last successful sync

### F2: TDLib Session Expired

- **Trigger:** Auth token expired or invalidated
- **Behavior:** Emit auth required event, stop sync
- **Error Code:** `TELEGRAM_AUTH_REQUIRED`
- **Fallback:** Prompt user to re-authenticate

### F3: Chat Access Revoked

- **Trigger:** User removed from channel/chat
- **Behavior:** Mark sync as failed for that chat, continue others
- **Error Code:** `TELEGRAM_CHAT_ACCESS_DENIED`
- **Fallback:** Hide content from that chat in UI

### F4: Rate Limiting

- **Trigger:** Too many requests to Telegram API
- **Behavior:** Exponential backoff (1s, 2s, 4s, max 60s)
- **Error Code:** `TELEGRAM_RATE_LIMITED`
- **Fallback:** Automatic retry with delay

---

## Logging & Telemetry

### Log Tags

| Tag | Level | Description |
|-----|-------|-------------|
| `TelegramSync` | DEBUG | General sync operations |
| `TelegramSync.History` | DEBUG | History fetch details |
| `TelegramSync.Error` | ERROR | Sync failures |

### Telemetry Events

| Event | Properties | When |
|-------|------------|------|
| `telegram_sync_started` | `chatId`, `cursor` | Sync begins |
| `telegram_sync_page_fetched` | `chatId`, `messageCount`, `durationMs` | Each page fetch |
| `telegram_sync_completed` | `chatId`, `totalMessages`, `durationMs` | Sync completes |
| `telegram_sync_failed` | `chatId`, `errorCode`, `errorMessage` | Sync fails |

---

## Test Requirements

### Unit Tests

- [ ] `TdlibTelegramContentRepository` correctly pages through history
- [ ] Cursor is correctly persisted and restored
- [ ] Rate limiting is handled with exponential backoff
- [ ] Network errors trigger appropriate retry logic

### Integration Tests

- [ ] Full sync of a test channel with 100+ messages
- [ ] Resume sync after simulated interruption
- [ ] Handle auth expiry gracefully

### Manual Tests

- [ ] Verify sync works on real Telegram channel
- [ ] Verify memory usage stays within bounds during large sync
- [ ] Verify sync resumes correctly after app kill

---

## Implementation Notes

### Key Classes

| Class | Role |
|-------|------|
| `TdlibTelegramContentRepository` | Main sync orchestrator |
| `TelegramClient` | TDLib wrapper interface |
| `DefaultTelegramClient` | TDLib implementation |
| `TdlibMessageMapper` | Message → RawMediaMetadata |

### Cursor Storage

The sync cursor is persisted in the app's DataStore under key `telegram_sync_cursor_{chatId}`.

### Sync Algorithm

```
1. Load cursor for chatId from DataStore (or use 0 for new sync)
2. While hasMore:
   a. Fetch messages from TDLib (limit=50, fromMessageId=cursor)
   b. Map messages to RawMediaMetadata
   c. Pass to normalizer
   d. Store normalized MediaItems
   e. Update cursor to oldest message ID in batch
   f. Check for rate limiting, backoff if needed
3. Persist final cursor
4. Emit sync_completed event
```

---

## Related Documents

| Document | Purpose |
|----------|---------|
| [FEATURE_SYSTEM_TARGET_MODEL.md](../../architecture/FEATURE_SYSTEM_TARGET_MODEL.md) | Feature system overview |
| [TELEGRAM_TDLIB_V2_INTEGRATION.md](../../TELEGRAM_TDLIB_V2_INTEGRATION.md) | TDLib integration guide |
| [MEDIA_NORMALIZATION_CONTRACT.md](../../MEDIA_NORMALIZATION_CONTRACT.md) | Normalizer contract |
