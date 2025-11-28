# Telegram Parser Contract – FishIT Player

**Version:** 1.0  
**Status:** Draft – authoritative functional spec for Telegram ingestion & parsing  
**Scope:** Turning Telegram messages (via TDLib + CLI JSON exports) into stable, rich FishIT domain items, backed by ObjectBox, consumable by the Internal Player and all library UIs.

This contract is **implementation-agnostic** but assumes:

- TDLib access via `tdlib-coroutines`
- Existing Telegram integration modules (e.g. `T_TelegramServiceClient`, `T_TelegramFileDownloader`, `TelegramContentRepository`, `TelegramFileDataSource`, `StreamingConfig`, `TelegramLogRepository`) already exist and must be adapted, not duplicated.
- JSON exports and CLI modules are stored under `docs/` as reference fixtures and behavior examples.

---

## 1. Inputs & Reference Artifacts

### 1.1 TDLib live data

Primary input in production:

- TDLib update stream (via `tdlib-coroutines`) providing:
  - `UpdateNewMessage`, `UpdateMessageEdited`, `UpdateDeleteMessages`, etc.
  - `getChatHistory(...)` for explicit history scans.

These are wrapped through existing TDLib modules and MUST NOT be accessed through ad-hoc raw TDLib calls.

### 1.2 CLI JSON exports

For offline tests and behavior reference:

- JSON exports from the CLI under `docs/telegram/exports/`:
  - One JSON file per chat.
  - Each file contains the 25 newest messages of that chat.
  - Simplified DTOs for `video/photo/text` messages; all other message types serialized as raw TDLib JSON.

- CLI source under `docs/telegram/cli/`:
  - Contains the working implementation of `getChatHistory` pagination, `onlyLocal` handling, `limit`, `offset`, and retry logic.
  - This logic is the **reference implementation** for:
    - message paging,
    - retry behavior,
    - how to get a consistent, non-duplicated window of messages.

### 1.3 Message schema (normalized view)

From CLI JSON (simplified view):

- `MessageVideo`:
  - Top-level: `id`, `chatId`, `date`, `dateIso`
  - `content` fields:
    - `duration`, `width`, `height`, `fileName`, `mimeType`, `supportsStreaming`
    - `file.remoteId`, `file.uniqueId`, `file.size`, `file.id`
    - `thumbnail.file.remoteId`, `thumbnail.file.uniqueId`, `thumbnail.file.size`, `thumbnail.file.id`
    - `caption` (optional)

- `MessagePhoto`:
  - Top-level: `id`, `chatId`, `date`, `dateIso`
  - `content.type = "photo"`
  - `content.sizes[]`:
    - sorted by resolution
    - each size has `file.remoteId`, `file.uniqueId`, `file.size`, `file.id`, `width`, `height`

- `MessageText`:
  - Top-level: `id`, `chatId`, `date`, `dateIso`
  - `text` (raw full text from Telegram)
  - Parsed fields (if present in the text):
    - `title`, `originalTitle`, `year`, `lengthMinutes`, `fsk`,
      `productionCountry`, `collection`, `director`,
      `tmdbRating`, `genres[]`, `tmdbUrl`

These structures must be mirrored by internal DTOs used for parsing, both for CLI JSON and live TDLib messages.

---

## 2. Design Goals

1. **Single Canonical Path from Telegram → Library Items**  
   All Telegram content must flow through one parser pipeline to produce normalized domain objects.

2. **Block-based Grouping (Video + Photo + Text)**  
   Messages belonging to the same content item (movie/clip/episode) are grouped into blocks based on time proximity and semantic matches.

3. **Rich Metadata Extraction**  
   Use all available metadata (title, year, genres, TMDb URL, etc.) to populate UI and detail screens.

4. **Stable IDs for Playback & Images**  
   Store TDLib `remoteId` / `uniqueId` references for both video and artwork, to support streaming and image loading from the TDLib cache.

5. **Flows, State Machines, and Coroutines**  
   The ingestion and parser layers must be:
   - coroutine-based (no `GlobalScope`),
   - modeled with explicit state machines,
   - exposing their results as `Flow` / `StateFlow`.

6. **Offline & Live Symmetry**  
   Parsing logic must behave identically for:
   - offline CLI JSON test data,
   - live TDLib data in production.

---

## 2.1 Finalized Design Decisions

The following decisions are **FINAL** and MUST be followed by all implementation work. See `TELEGRAM_PARSER_COPILOT_TASK.md` for detailed rationale.

### File ID Strategy
- `remoteId` and `uniqueId` are the **primary identifiers** for all media files
- `fileId` is volatile and **MUST NEVER be the primary key**
- Playback: Use `getRemoteFile(remoteId)` to resolve `fileId` at playback time

### ObjectBox Migration
- **Do NOT migrate** legacy `ObxTelegramMessage` data
- Create new `ObxTelegramItem` entity aligned with new domain model
- Full resync is acceptable; old pipeline is non-functional

### Parser Architecture
- Legacy `MediaInfo`/`ParsedItem` types MUST NOT be used in new pipeline
- New pipeline: `ExportMessage` → `TelegramBlockGrouper` → `TelegramMetadataExtractor` → `TelegramItemBuilder`

### Grouping Strategy
- **120-second time-window** is the canonical grouping mechanism
- Structured triplet detection (Video+Text+Photo) is optional refinement within blocks

### Adult Content Detection (Conservative)
- **Primary**: Chat title only (`AdultHeuristics.isAdultChatTitle()`)
- **Secondary**: Caption ONLY for extreme terms: `bareback`, `gangbang`, `bukkake`, `fisting`, `bdsm`, `deepthroat`, `cumshot`
- **NOT USED**: FSK values, genres, broad NSFW heuristics

### Aspect Ratios
- Poster: `width/height <= 0.85`
- Backdrop: `width/height >= 1.6`

### JSON Library
- ALL new parser code MUST use `kotlinx.serialization`

### Auth & Ingestion Constraints
- Ingestion MUST NOT run unless auth state is `READY`
- All auth transitions logged via `TelegramLogRepository`

---

## 3. Terminology

- **ExportMessage** – internal DTO representing one Telegram message (video, photo, text, or “other”), independent of TDLib DTOs or JSON.
- **MessageBlock** – group of messages within the same chat that belong to a single library item.
- **TelegramItem** – normalized domain item (movie/episode/clip/audiobook/rar item) produced by the parser.
- **Ingestion** – process of fetching messages from TDLib (history + updates) and feeding them to the parser.
- **TDLib Cache** – local TDLib database and file storage; used best-effort with `onlyLocal` where possible.

---

## 4. High-Level Architecture

The Telegram domain is split into these conceptual modules:

- `ingestion/`  
  - `TelegramHistoryScanner` (per-chat history scans via TDLib)  
  - `TelegramUpdateHandler` (live updates via TDLib stream)  
  - `TelegramIngestionCoordinator` (orchestrates per-chat scanners)

- `parser/`  
  - `TelegramMessageModels` (ExportMessage DTOs mirroring CLI JSON)  
  - `TelegramBlockGrouper` (time-based grouping into MessageBlocks)  
  - `TelegramMetadataExtractor` (title/year/genres/etc. from text, filename, caption)  
  - `TelegramItemBuilder` (MessageBlock → TelegramItem mapping)

- `domain/`  
  - `TelegramItem` (normalized item for FishIT)  
  - `TelegramMediaRef`, `TelegramImageRef`, `TelegramMetadata` DTOs

- `repository/`  
  - `TelegramContentRepository` (ObjectBox persistence and query APIs)  
  - `TelegramSyncStateRepository` (per-chat scan progress, last scanned messageId, scan status)

- `ui/`  
  - `TelegramRowsViewModel` and other view models consuming repository Flows for tiles and detail screens.

Existing Telegram modules must be updated to act as the data source and gateway into this architecture, not as parallel implementations.

---

## 5. Data Model & Mapping

### 5.1 ExportMessage DTOs

All raw inputs (CLI JSON, TDLib messages) must be normalized to a sealed hierarchy:

```kotlin
sealed interface ExportMessage {
    val id: Long
    val chatId: Long
    val dateEpochSeconds: Long
    val dateIso: String
}

data class ExportVideo( /* fields from MessageVideo */ ) : ExportMessage
data class ExportPhoto( /* fields from MessagePhoto */ ) : ExportMessage
data class ExportText( /* fields from MessageText */ ) : ExportMessage
data class ExportOtherRaw(
    override val id: Long,
    override val chatId: Long,
    override val dateEpochSeconds: Long,
    override val dateIso: String,
    val rawJson: String,
    val messageType: String
) : ExportMessage
```

Implementations:

- MUST mirror the CLI JSON schema 1:1 for video/photo/text messages.
- MUST be constructible both from:
  - CLI JSON,
  - live TDLib DTOs (`dev.g000sha256.tdl.dto.Message*`).

### 5.2 MessageBlock

A `MessageBlock` groups several `ExportMessage`s that represent the same content item:

```kotlin
data class MessageBlock(
    val chatId: Long,
    val messages: List<ExportMessage>
)
```

Rules:

- `messages` must all share the same `chatId`.
- Messages are typically within a time window of **≤ 120 seconds** difference in `dateIso` / `dateEpochSeconds`.
- Blocks are built from messages sorted **descending by date**.

### 5.3 TelegramItem & references

A `TelegramItem` is the canonical output:

```kotlin
enum class TelegramItemType {
    MOVIE,
    SERIES_EPISODE,
    CLIP,
    AUDIOBOOK,
    RAR_ITEM,
    POSTER_ONLY
}

/**
 * Reference to a video file in TDLib.
 * 
 * IMPORTANT: remoteId and uniqueId are REQUIRED and stable across sessions.
 * fileId is OPTIONAL and may become stale - use getRemoteFile(remoteId) to refresh.
 */
data class TelegramMediaRef(
    val remoteId: String,      // REQUIRED - stable across sessions
    val uniqueId: String,      // REQUIRED - stable across sessions
    val fileId: Int? = null,   // OPTIONAL - volatile, may become stale
    val sizeBytes: Long,
    val mimeType: String?,
    val durationSeconds: Int?,
    val width: Int?,
    val height: Int?
)

/**
 * Reference to a document/archive file in TDLib.
 * Used for AUDIOBOOK and RAR_ITEM types.
 * 
 * IMPORTANT: remoteId and uniqueId are REQUIRED and stable across sessions.
 * fileId is OPTIONAL and may become stale.
 */
data class TelegramDocumentRef(
    val remoteId: String,      // REQUIRED - stable across sessions
    val uniqueId: String,      // REQUIRED - stable across sessions
    val fileId: Int? = null,   // OPTIONAL - volatile, may become stale
    val sizeBytes: Long,
    val mimeType: String?,
    val fileName: String?
)

/**
 * Reference to an image file in TDLib.
 * 
 * IMPORTANT: remoteId and uniqueId are REQUIRED and stable across sessions.
 * fileId is OPTIONAL and may become stale.
 */
data class TelegramImageRef(
    val remoteId: String,      // REQUIRED - stable across sessions
    val uniqueId: String,      // REQUIRED - stable across sessions
    val fileId: Int? = null,   // OPTIONAL - volatile, may become stale
    val width: Int,
    val height: Int,
    val sizeBytes: Long
)

data class TelegramMetadata(
    val title: String?,
    val originalTitle: String?,
    val year: Int?,
    val lengthMinutes: Int?,
    val fsk: Int?,             // For display only, NOT used for isAdult classification
    val productionCountry: String?,
    val collection: String?,
    val director: String?,
    val tmdbRating: Double?,
    val genres: List<String>,  // For display only, NOT used for isAdult classification
    val tmdbUrl: String?,
    val isAdult: Boolean       // See "Adult Content Detection" design decision
)

/**
 * Normalized domain item representing a Telegram media item.
 * 
 * Key invariants:
 * - For MOVIE/SERIES_EPISODE/CLIP: videoRef is non-null, documentRef is null
 * - For AUDIOBOOK/RAR_ITEM: documentRef is non-null, videoRef is null
 * - For POSTER_ONLY: both videoRef and documentRef are null
 * - Grouping is based on 120-second time windows (canonical)
 */
data class TelegramItem(
    val chatId: Long,
    val anchorMessageId: Long,
    val type: TelegramItemType,
    val videoRef: TelegramMediaRef?,      // For MOVIE/SERIES_EPISODE/CLIP
    val documentRef: TelegramDocumentRef?, // For AUDIOBOOK/RAR_ITEM
    val posterRef: TelegramImageRef?,
    val backdropRef: TelegramImageRef?,
    val textMessageId: Long?,
    val photoMessageId: Long?,
    val createdAtIso: String,
    val metadata: TelegramMetadata
)
```

### 5.4 ObjectBox mapping

There MUST be an ObjectBox entity `ObxTelegramItem` mirroring the important fields:

- Identity:
  - `chatId` (indexed)
  - `anchorMessageId` (indexed; primary logical key; typically the video's message ID)
- Item type:
  - `itemType: String` (enum name: MOVIE, SERIES_EPISODE, CLIP, AUDIOBOOK, RAR_ITEM, POSTER_ONLY)
- Video reference (for MOVIE/SERIES_EPISODE/CLIP):
  - `videoRemoteId: String?` (REQUIRED when videoRef present)
  - `videoUniqueId: String?` (REQUIRED when videoRef present)
  - `videoFileId: Int?` (OPTIONAL - volatile, may become stale)
  - `videoSizeBytes: Long?`, `videoMimeType: String?`
  - `videoDurationSeconds: Int?`, `videoWidth: Int?`, `videoHeight: Int?`
- Document reference (for AUDIOBOOK/RAR_ITEM):
  - `documentRemoteId: String?` (REQUIRED when documentRef present)
  - `documentUniqueId: String?` (REQUIRED when documentRef present)
  - `documentFileId: Int?` (OPTIONAL - volatile, may become stale)
  - `documentSizeBytes: Long?`, `documentMimeType: String?`, `documentFileName: String?`
- Images:
  - Poster: `posterRemoteId`, `posterUniqueId`, `posterFileId?`, `posterWidth`, `posterHeight`, `posterSizeBytes`
  - Backdrop: `backdropRemoteId`, `backdropUniqueId`, `backdropFileId?`, `backdropWidth`, `backdropHeight`, `backdropSizeBytes`
- Metadata:
  - `title`, `originalTitle`, `year`, `lengthMinutes`, `fsk`
  - `productionCountry`, `collection`, `director`
  - `tmdbRating`, `tmdbUrl`, `isAdult`
  - `genresJson` (serialized list as JSON string)
- Related message IDs:
  - `textMessageId: Long?`, `photoMessageId: Long?`
- Timestamps:
  - `createdAtUtc: Long` (epoch millis, based on `createdAtIso`)

**IMPORTANT**: 
- ObjectBox-generated `id` is purely technical
- The **logical identity** of an item is `(chatId, anchorMessageId)`
- `remoteId`/`uniqueId` are REQUIRED for all file references
- `fileId` is OPTIONAL and may become stale after TDLib cache changes

**Migration note**: Do NOT migrate data from legacy `ObxTelegramMessage`. Create fresh `ObxTelegramItem` entities via full resync.

---

## 6. Grouping & Matching Rules

### 6.1 Time-window grouping

Per chat:

1. Load `ExportMessage`s.
2. Sort by `dateEpochSeconds` descending.
3. Build blocks:
   - Start a new `MessageBlock` when:
     - current message is more than **120 seconds** away from the previous message in the current block, or
     - chat boundaries are crossed (new chatId).
   - Else, append to the current block.

120 seconds is a configurable constant; default is 120 seconds.

### 6.2 Block-level semantics

Within a `MessageBlock`:

- If the block contains at least one `ExportVideo`:
  - The block yields **one primary TelegramItem** anchored to a chosen `ExportVideo`.
  - If multiple videos exist, choose the best candidate (e.g., highest resolution or longest duration).
- If no video exists, but there is both `ExportPhoto` and `ExportText`:
  - The block yields a `TelegramItem` with `type = POSTER_ONLY`.
- If only a single message exists due to sparse history:
  - Still yield an item (best effort) and mark missing fields as null; ingestion can revisit the chat later.

### 6.3 Matching Text and Photo to Video

Given an anchored `ExportVideo`:

1. Find the **nearest `ExportText`** in the block:
   - Minimize time difference.
   - Optionally require loose title/year match:
     - Normalize strings: lowercase, strip punctuation, split into tokens.
     - Compare tokens between:
       - video `caption` / `fileName`
       - text `title`
     - If a `year` exists in text, it should match a year found in fileName or caption, when available.
2. Find the **best `ExportPhoto`** in the block:
   - Prefer the largest reasonable resolution (no excessive aspect ratio distortion).
   - Poster:
     - aspect ratio roughly 2:3 / 3:4.
   - Backdrop:
     - wide aspect ratio (e.g. ≥ 16:9).
   - If none qualifies, fallback to best resolution photo for both roles.
3. If no photo exists:
   - Use the video thumbnail (if available) as `posterRef`.
   - `backdropRef` may be null or also use thumbnail as fallback.

### 6.4 Metadata extraction

- Primary metadata source: `ExportText` parsed fields:
  - `title`, `originalTitle`, `year`, `lengthMinutes`, `fsk`,
    `productionCountry`, `collection`, `director`,
    `tmdbRating`, `genres`, `tmdbUrl`.
- Secondary source: `ExportVideo`:
  - Derive `title` and `year` from `fileName` pattern `<title> - <year>.mp4` if text metadata is missing.
  - Derive fallback `title` from caption when present.
- **`isAdult` (CONSERVATIVE RULES)**:
  - **Primary signal**: Chat title only.
    - Use helper: `AdultHeuristics.isAdultChatTitle(title: String): Boolean`
    - Matches patterns like "porn", "xxx", "adult", "18+" in chat titles
  - **Secondary signal**: Caption text ONLY for **extremely explicit sexual terms**:
    - Whitelist: `bareback`, `gangbang`, `bukkake`, `fisting`, `bdsm`, `deepthroat`, `cumshot`
    - Use helper: `AdultHeuristics.hasExtremeExplicitTerms(caption: String): Boolean`
  - **NOT used for adult classification**:
    - FSK value (FSK 18 does NOT imply adult content)
    - Genre-based classification
    - Broad NSFW heuristics
- If metadata is inconsistent across multiple texts:
  - Favor the text closest in time to the video anchor.
  - TMDb URL, when present, is the strongest identifier and MUST be attached.

### 6.5 RAR / Audiobook chat handling

For explicit archive/audiobook chats:

- A block may have no video but a document/archive message.
- Resulting `TelegramItem`:
  - `type = AUDIOBOOK` or `RAR_ITEM` (depending on message type and filename heuristics).
  - `videoRef` is null; instead, store file reference fields in the domain and ObjectBox, so the app can support download or custom playback later.
- Metadata still comes from text within the same block (title/year/etc.), if present.

---

## 7. Ingestion & State Machines

### 7.1 Per-chat scan state

Each chat has a scan state:

```kotlin
data class ChatScanState(
    val chatId: Long,
    val lastScannedMessageId: Long,
    val hasMoreHistory: Boolean,
    val status: ScanStatus, // IDLE, SCANNING, ERROR
    val lastError: String? = null
)
```

This is stored in `TelegramSyncStateRepository`.

### 7.2 TelegramHistoryScanner

Responsibilities:

- Use TDLib `getChatHistory(...)` in a loop to backfill history.
- Follow CLI logic for:
  - `fromMessageId`,
  - `offset` (first page offset 0, further pages offset -1),
  - `limit` (up to 100),
  - retry with incremental delay when no messages are returned.
- Feed each batch into:
  - normalizer (TDLib → ExportMessage),
  - parser pipeline (block grouper + item builder),
  - repository for persistence.

Constraints:

- MUST respect TDLib rate limits (use delays, backoff).
- MUST avoid emitting duplicate messages (use `seen` set logic similar to CLI).
- MUST support `onlyLocal` when operating on cached messages (e.g. for quick re-scans or offline-only behavior).

### 7.3 TelegramUpdateHandler

Responsibilities:

- Listen to TDLib updates for new/edited/deleted messages.
- Normalize updated messages to `ExportMessage`.
- Pass them into the same parser pipeline for incremental updates:
  - For new messages:
    - Try to attach them to an existing nearby block (by time).
    - Or start new blocks if necessary.
  - For edited/deleted messages:
    - Update or remove affected `TelegramItem`s in ObjectBox.

### 7.4 TelegramIngestionCoordinator

Responsibilities:

- Manage per-chat `TelegramHistoryScanner` instances.
- Decide when to:
  - start initial backfill,
  - resume partial scans,
  - pause/resume scanning.
- Expose a `StateFlow<List<ChatScanState>>` for diagnostics and UI.

---

## 8. Observable API (Flows)

The parser and repositories must expose their results as Flows:

- `TelegramContentRepository`:
  - `fun observeItemsByChat(chatId: Long): Flow<List<TelegramItem>>`
  - `fun observeAllItems(): Flow<List<TelegramItem>>`
- `TelegramSyncStateRepository`:
  - `fun observeScanStates(): Flow<List<ChatScanState>>`
- `TelegramIngestionCoordinator`:
  - `val scanStates: StateFlow<List<ChatScanState>>`

ViewModels (e.g. `TelegramRowsViewModel`) consume these flows and map them into UI state (rows, tiles, detail screens).

---

## 9. Integration with Existing Modules

Existing Telegram modules MUST be treated as building blocks, not rewritten from scratch:

- `T_TelegramServiceClient`:
  - Remains the primary gateway to TDLib.
  - `TelegramHistoryScanner` and `TelegramUpdateHandler` must call TDLib via this client.
- `T_TelegramFileDownloader` / `TelegramFileDataSource` / `StreamingConfig`:
  - `TelegramMediaRef.remoteId` & `.uniqueId` are used to resolve and stream files via these modules.
- `TelegramContentRepository` (existing):
  - Must be refactored/wrapped to adhere to this contract’s domain model (`TelegramItem`).
  - Old ad-hoc parsing logic must be migrated or removed.

---

## 10. Quality, Testing, and Tooling

### 10.1 Unit & Integration tests

At minimum:

- Grouping:
  - test `TelegramBlockGrouper` with synthetic message sequences to verify:
    - 120-second window behavior,
    - multi-video blocks,
    - sparse history cases.
- Metadata extraction:
  - test `TelegramMetadataExtractor` with realistic text blobs and filenames.
- Item builder:
  - test Video+Photo+Text combinations, POSTER_ONLY, RAR/AUDIOBOOK behavior.
- Ingestion:
  - test `TelegramHistoryScanner` with fake TDLib client using CLI behavior (limit/offset/onlyLocal).
- Persistence & Flows:
  - test `TelegramContentRepository` and `TelegramSyncStateRepository` using in-memory ObjectBox or test boxes.

### 10.2 Tools & Libraries

- JSON: Prefer `kotlinx.serialization` or Moshi over legacy Gson for new code.
- Concurrency: Use structured coroutines (`SupervisorJob`, `viewModelScope`, or custom scope) with `Dispatchers.IO` for I/O.
- Flow testing: Use a test helper like Turbine to assert Flow emissions.
- Static analysis: Ensure new modules pass `ktlint` and `detekt` with strict rules.

---

## 11. Evolution & Ownership

- This document is the **single source of truth** for Telegram parsing behavior.
- Any behavioral change (grouping rules, metadata mapping, item model) MUST be reflected here before implementation.
- Existing code is considered historical reference; this contract is authoritative.
