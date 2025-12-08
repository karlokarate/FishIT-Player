# Gold: Telegram Core Integration Patterns

## Overview
This document captures the proven patterns from v1's Telegram/TDLib integration that should be preserved in v2.

## Source Files
- `T_TelegramServiceClient.kt` (821 lines) - Unified Telegram Engine
- `T_TelegramSession.kt` - Auth state management
- `T_ChatBrowser.kt` - Chat and message browsing
- `T_TelegramFileDownloader.kt` - File download orchestration
- `TelegramFileDataSource.kt` - Zero-copy streaming for playback

---

## 1. Unified Telegram Engine Pattern

### Key Pattern: Single TdlClient Instance
**v1 Implementation:** `T_TelegramServiceClient`

```kotlin
/**
 * GOLD: Single Process-Wide TdlClient Instance
 * 
 * Why this works:
 * - TDLib enforces single-instance per process
 * - All auth, browsing, downloads share one TDLib session
 * - Update distribution to all components via StateFlows
 * - Reconnection handling on network changes
 */
object T_TelegramServiceClient {
    private var _client: TdlClient? = null
    
    // State flows for external consumption
    val authState: StateFlow<TelegramAuthState>
    val connectionState: StateFlow<TgConnectionState>
    val syncState: StateFlow<TgSyncState>
    val activityEvents: SharedFlow<TgActivityEvent>
}
```

### Auth State Machine
**Pattern:** Clear sealed class hierarchy for auth flows

```kotlin
sealed class TelegramAuthState {
    object Idle : TelegramAuthState()
    object Connecting : TelegramAuthState()
    object WaitingForPhone : TelegramAuthState()
    object WaitingForCode : TelegramAuthState()
    object WaitingForPassword : TelegramAuthState()
    object Ready : TelegramAuthState()
    data class Error(val message: String) : TelegramAuthState()
}
```

**Why preserve:** UI can reactively bind to auth state without polling.

### Connection State Tracking
**Pattern:** Separate connection state from auth state

```kotlin
sealed class TgConnectionState {
    object Disconnected : TgConnectionState()
    object Connecting : TgConnectionState()
    object Connected : TgConnectionState()
    data class Error(val message: String) : TgConnectionState()
}
```

**Why preserve:** Network state is distinct from auth state. Connection can drop while auth is still valid.

---

## 2. Zero-Copy Streaming Architecture

### Key Pattern: Delegate to FileDataSource
**v1 Implementation:** `TelegramFileDataSource`

```kotlin
/**
 * GOLD: Zero-Copy Streaming via Delegation
 * 
 * Why this works:
 * - TDLib downloads to cache directory (unavoidable)
 * - We delegate to Media3's FileDataSource for actual I/O
 * - No ByteArray buffers, no custom position tracking
 * - ExoPlayer/FileDataSource handles seeking and scrubbing
 */
@UnstableApi
class TelegramFileDataSource(
    private val serviceClient: T_TelegramServiceClient,
) : DataSource {
    private var delegate: FileDataSource? = null
    
    override fun open(dataSpec: DataSpec): Long {
        // 1. Parse tg:// URL
        // 2. Call TDLib ensureFileReady()
        // 3. Get local file path
        // 4. Delegate to FileDataSource
        val localPath = runBlocking {
            serviceClient.ensureFileReadyWithMp4Validation(...)
        }
        delegate = FileDataSource().apply {
            open(dataSpec.withUri(Uri.fromFile(File(localPath))))
        }
    }
}
```

**Why preserve:** Avoids memory copies, leverages ExoPlayer's optimized file handling.

### RemoteId-First URL Format
**Pattern:** Cross-session stable media identification

```kotlin
/**
 * GOLD: RemoteId-First URL Format
 * 
 * Format: tg://file/<fileId>?chatId=...&messageId=...&remoteId=...&uniqueId=...
 * 
 * Resolution Strategy:
 * 1. If fileId is valid (> 0), use it directly (fast path - same session)
 * 2. If fileId is 0 or invalid, resolve via getRemoteFile(remoteId)
 * 3. Call ensureFileReady() with TDLib-optimized parameters
 * 
 * Why this works:
 * - fileId changes between TDLib sessions (ephemeral)
 * - remoteId is stable across sessions (persistent)
 * - Priority: fast path first, fallback to resolution
 */
```

**Why preserve:** Enables playback to survive app restarts and session changes.

---

## 3. Chat Browsing & History Scanning

### Key Pattern: Cursor-Based Pagination
**v1 Implementation:** `T_ChatBrowser`

```kotlin
/**
 * GOLD: Efficient History Traversal
 * 
 * Pattern: Cursor-based pagination with message ID cursors
 * 
 * Why this works:
 * - TDLib uses message IDs as cursors (not offset-based)
 * - fromMessageId=0 means start from latest
 * - Each page returns new fromMessageId for next page
 * - No need to track page numbers
 */
suspend fun getMessagesPage(
    chatId: Long,
    fromMessageId: Long = 0,
    limit: Int = 100
): List<TgMessage> {
    val result = client.execute(
        GetChatHistory(
            chatId = chatId,
            fromMessageId = fromMessageId,
            limit = limit,
            offset = 0,
            onlyLocal = false
        )
    )
    return result.messages
}
```

**Why preserve:** Natural fit for TDLib's API, efficient for large chat histories.

### Lazy Thumbnail Loading
**Pattern:** On-demand thumbnail fetching

```kotlin
/**
 * GOLD: Lazy Thumbnail Loading
 * 
 * Why this works:
 * - Don't pre-download all thumbnails
 * - Fetch on-demand when UI needs them
 * - Use TDLib's thumbnail file references
 * - Cache results in memory/disk
 */
suspend fun getThumbnailPath(thumbnail: TdApi.Thumbnail): String? {
    if (thumbnail.file.local.isDownloadingCompleted) {
        return thumbnail.file.local.path
    }
    // Download on demand
    return downloadFile(thumbnail.file.id, priority = 1)
}
```

**Why preserve:** Reduces memory and network usage for large media libraries.

---

## 4. File Download Orchestration

### Key Pattern: Priority-Based Downloads
**v1 Implementation:** `T_TelegramFileDownloader`

```kotlin
/**
 * GOLD: Priority-Based Download Queue
 * 
 * Priority Levels:
 * - 32: Critical (playback headers, immediate thumbnails)
 * - 16: High (preload next episode)
 * - 8: Normal (user-requested downloads)
 * - 1: Low (background prefetch)
 * 
 * Why this works:
 * - TDLib respects priority parameter
 * - Critical items (MP4 headers) download first
 * - Background work doesn't block playback
 */
suspend fun ensureFileReady(
    fileId: Int,
    priority: Int = 8,
    offsetBytes: Long = 0,
    limitBytes: Long = 0
): String {
    client.execute(
        DownloadFile(
            fileId = fileId,
            priority = priority,
            offset = offsetBytes.toInt(),
            limit = limitBytes.toInt(),
            synchronous = false
        )
    )
    // Wait for completion via updates
}
```

**Why preserve:** Ensures smooth playback while managing network bandwidth.

### MP4 Header Validation
**Pattern:** Validate moov atom before playback

```kotlin
/**
 * GOLD: MP4 Header Validation
 * 
 * Why this works:
 * - MP4 files need complete moov atom to be playable
 * - Don't rely on fixed byte thresholds
 * - Parse actual MP4 structure
 * - Fail fast if file is corrupted
 */
suspend fun ensureFileReadyWithMp4Validation(
    fileId: Int,
    isMp4: Boolean
): String {
    val path = ensureFileReady(fileId, priority = 32, offset = 0, limit = 0)
    if (isMp4) {
        val valid = Mp4HeaderParser.validateMoovAtom(path)
        if (!valid) throw IOException("MP4 moov atom incomplete")
    }
    return path
}
```

**Why preserve:** Prevents playback errors from incomplete/corrupted downloads.

---

## 5. v2 Porting Guidance

### What to Port

1. **Auth State Machine** → Port to `pipeline/telegram/tdlib/TelegramTdlibClient`
   - Keep sealed class hierarchy
   - Expose StateFlows for UI consumption

2. **Zero-Copy Streaming** → Port to `player/internal/source/telegram/TelegramFileDataSource`
   - Keep FileDataSource delegation
   - Update to use `TelegramTdlibClient` interface (not singleton)

3. **RemoteId-First URLs** → Port to `pipeline/telegram/model/TelegramUri`
   - Keep URL format unchanged
   - Document resolution strategy

4. **Chat Browsing** → Port to `pipeline/telegram/repository/TdlibTelegramContentRepository`
   - Keep cursor-based pagination
   - Return `RawMediaMetadata` per v2 contract

5. **Priority Downloads** → Port to `pipeline/telegram/tdlib/TdlibTelegramClient`
   - Keep priority levels
   - Add MP4 validation

### What to Change

1. **Singleton → Interface**
   - v1: `object T_TelegramServiceClient`
   - v2: `interface TelegramTdlibClient` + `class TdlibTelegramClient`
   - Reason: Testability, dependency injection

2. **No Normalization**
   - v1: Parser included title cleaning
   - v2: Emit RawMediaMetadata only
   - Reason: MEDIA_NORMALIZATION_CONTRACT.md

3. **Logging**
   - v1: `import com.chris.m3usuite.core.logging.UnifiedLog`
   - v2: `import com.fishit.player.infra.logging.UnifiedLog`
   - Reason: Module boundaries

4. **No UI in Pipeline**
   - v1: `TelegramFeedScreen` in telegram package
   - v2: Move to `feature/telegram-media`
   - Reason: Module boundaries

### Implementation Phases

**Phase 1: Core TDLib Client** (CURRENT)
- [ ] Port auth state machine
- [ ] Port connection state tracking
- [ ] Port basic file download
- [ ] Add TelegramTdlibClient interface

**Phase 2: Streaming**
- [ ] Port TelegramFileDataSource
- [ ] Port RemoteId resolution
- [ ] Add MP4 validation
- [ ] Integrate with InternalPlaybackSourceResolver

**Phase 3: Repository**
- [ ] Port chat browsing
- [ ] Port history scanning
- [ ] Implement toRawMediaMetadata()
- [ ] Add lazy thumbnail loading

**Phase 4: Testing**
- [ ] Unit tests for auth state machine
- [ ] Unit tests for URL parsing
- [ ] Integration tests for streaming
- [ ] Performance tests for history scanning

---

## References

- v1 Source: `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/telegram/core/`
- v2 Target: `/pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/`
- v2 Contract: `/docs/v2/TELEGRAM_TDLIB_V2_INTEGRATION.md`
- v2 Streaming: `/player/internal/src/main/java/com/fishit/player/internal/source/telegram/`
