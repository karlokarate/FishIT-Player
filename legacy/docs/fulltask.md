You are working on the FishIT Player repository.

Context / problems (from current Telegram Logs):

1. Thumbnail prefetching (`TelegramThumbPrefetcher` + `TelegramFileLoader`) produces大量 errors:
   - `IllegalStateException: Service not started` when Telegram engine is not running.
   - `Failed to get file <fileId>: 404 - Not Found` when using stale `fileId`s.

2. Playback via `TelegramFileDataSource` sometimes fails:
   - `Timeout waiting for file ready: fileId=..., downloaded=..., required=...`,
   - due to a fixed, large `requiredPrefixSize` that may exceed:
     - current downloaded bytes or even
     - total file size for small clips.

3. After app restart, all thumbnails appear “gone” until re-fetched:
   - Thumbnails are stored in TDLib’s own cache (`/no_backup/tdlib-db/thumbnails/..._109.jpg`),
   - but our code treats `fileId` as primary and repeatedly fails on stale IDs instead of resolving via `remoteId`.

Design constraints from the human:

- **RemoteId-only semantics**:
  - `remoteId` / `uniqueId` are the only stable identifiers for Telegram media.
  - `fileId` MUST be treated as optional, volatile cache; never required.
- **Streaming-first player**:
  - Trickplay/Thumbnails/Playback MUST work while streaming.
  - Do NOT require full-file downloads or long-lived full-file caches.
- **No extra thumbnail mirroring**:
  - Do NOT copy thumbnails into our own app cache.
  - Always use TDLib’s own thumbnail cache, resolving paths via `remoteId` when needed.
- **Robustness**:
  - Prefetcher must not spam `Service not started` or `404` errors.
  - Playback must tolerate slow or partial downloads with flexible `minBytes` and larger timeouts.

Your task is to implement code changes that fix these issues, following the above constraints.

────────────────────────────────────────
PART 1 – RemoteId-first thumbnail loading (TelegramFileLoader + TelegramThumbPrefetcher)
────────────────────────────────────────

Goals:
- Stop using `fileId` as the primary key for thumbnail loading.
- Use `remoteId` as the canonical key; `fileId` is an optional hint only.
- Avoid errors when Telegram service is not started.

Tasks:

1. Change the thumbnail loader API from `fileId`-centric to `TelegramImageRef`-centric:

   - Currently:
     ```kotlin
     suspend fun ensureThumbDownloaded(fileId: Int): String?
     ```
   - Replace with:
     ```kotlin
     suspend fun ensureThumbDownloaded(ref: TelegramImageRef): String?
     ```
     where `TelegramImageRef` already contains:
     - `remoteId: String`
     - `uniqueId: String`
     - `fileId: Int?` (optional)
     - `width`, `height`, `sizeBytes`

2. Implement **remoteId-first** logic in `TelegramFileLoader.ensureThumbDownloaded`:

   - Pseudocode:

     ```kotlin
     suspend fun ensureThumbDownloaded(ref: TelegramImageRef): String? {
         // 0) Check Telegram service state – fail fast if not started
         if (!telegramServiceClient.isStarted || !telegramServiceClient.isAuthReady) {
             logDebug("Skipping thumb download – Telegram not ready")
             return null
         }

         // 1) If we have a fileId, try it once
         ref.fileId?.takeIf { it > 0 }?.let { cachedFileId ->
             val result = tryDownloadThumbByFileId(cachedFileId)
             if (result is ThumbResult.Success) return result.localPath
             if (result is ThumbResult.NotFound404) {
                 // mark this fileId as stale for this session
                 logWarn("Stale fileId=$cachedFileId, will fall back to remoteId=${ref.remoteId}")
             }
             // for other errors (network, timeout), just continue to remoteId
         }

         // 2) Resolve a fresh fileId via remoteId in the current TDLib DB
         val fileInfo = telegramServiceClient.downloader().getFileInfoByRemoteId(ref.remoteId)
             ?: return null // remoteId unknown in this session

         val newFileId = fileInfo.id
         // optional: update ref.fileId in-memory / repo if appropriate

         // 3) Download thumb via new fileId
         val result = tryDownloadThumbByFileId(newFileId)
         return if (result is ThumbResult.Success) result.localPath else null
     }
     ```

   - Implement `tryDownloadThumbByFileId(fileId: Int)` to encapsulate `downloadFile` + `getFile` and handle exceptions cleanly.

3. Gate `TelegramThumbPrefetcher` on Telegram engine state:

   - Before each prefetch batch, check:
     ```kotlin
     if (!telegramServiceClient.isStarted || !telegramServiceClient.isAuthReady) {
         logInfo("Prefetch skipped – Telegram not started or not READY")
         return
     }
     ```
   - Do NOT start prefetch loops when Telegram is disabled or engine is shut down.
   - This will eliminate `IllegalStateException("Service not started")` spam.

4. Adjust all call sites of `ensureThumbDownloaded(fileId)` to use `TelegramImageRef` instead:
   - e.g. in `prefetchImages()` where `posterRef`/`backdropRef` exists.

5. Logging:
   - For 404 (file not found in TDLib for this session), log a concise warning *once* per `remoteId` and do not retry endlessly.
   - For service-not-started, log once and skip prefetch, not every second.

────────────────────────────────────────
PART 2 – Streaming-friendly ensureFileReady (TelegramFileDataSource / T_TelegramFileDownloader)
────────────────────────────────────────

Goals:
- Avoid timeouts in `ensureFileReady` caused by rigid `requiredPrefixSize` that may exceed actual file size.
- Allow playback to start with partial data when safe.
- Keep all logic streaming-friendly and lightweight.

Tasks:

1. Inspect `T_TelegramFileDownloader.ensureFileReady(...)` and related code:

   - Identify:
     - how `minBytes` / `requiredPrefixSize` is computed,
     - how timeout is applied,
     - how download progress (`downloadedPrefixSize`) is tracked.

2. Implement **flexible minBytes**:

   - When you know the total size (`fileInfo.size`):

     ```kotlin
     val totalSize = fileInfo.size ?: Long.MAX_VALUE
     val configuredMinPrefix = MIN_PREFIX_BYTES // e.g. 256 * 1024
     val requiredPrefixSize = min(configuredMinPrefix, totalSize)
     ```

   - For very small files (e.g. totalSize < configuredMinPrefix):
     - `requiredPrefixSize` MUST NOT exceed `totalSize`.
     - This avoids “waiting forever” for bytes that can never arrive.

3. Implement a more **streaming-friendly timeout** strategy:

   - Instead of a single hard timeout, consider:
     - multiple polling iterations while `downloadedPrefixSize` is increasing,
     - early success if:
       - `downloadedPrefixSize >= requiredPrefixSize`, OR
       - `downloadedPrefixSize >= MIN_START_BYTES` (e.g. 64KB) and download is still progressing.
   - Pseudocode outline:

     ```kotlin
     val startTime = now()
     var lastDownloaded = 0L

     while (now() - startTime < maxTimeoutMs) {
         val info = getFileInfo(fileId) ?: break
         val downloaded = info.local?.downloadedPrefixSize ?: 0L
         if (downloaded >= requiredPrefixSize) return Success(info.local.path)
         if (downloaded > lastDownloaded) {
             lastDownloaded = downloaded
             // reset a small inner timeout window based on progress
         }
         delay(pollInterval)
     }

     // fallback: if we have some data and the file is small, allow playback anyway
     if (lastDownloaded >= MIN_START_BYTES) return Success(info.local?.path)
     throw TimeoutException(...)
     ```

   - The key is:
     - do not block playback if reasonable partial data is already present,
     - do not require `requiredPrefixSize` to be reached when total file size is smaller.

4. In `TelegramFileDataSource`, when `ensureFileReady` throws:

   - Ensure that the error is logged clearly **once** with:
     - `fileId`, `remoteId`, `downloaded`, `required`, `totalSize?`.
   - Then surface a meaningful error to the player/ UI.
   - Do NOT loop endlessly with the same `minBytes`/timeout combination.

────────────────────────────────────────
PART 3 – Using TDLib thumbnail cache across app restarts (no mirroring)
────────────────────────────────────────

Goals:
- On app restart, thumbnails should be immediately recovered from TDLib’s own cache via remoteId.
- The app must NOT mirror thumbnails into its own directory – just re-resolve via TDLib.

Tasks:

1. Ensure that `TelegramImageRef` always stores:
   - `remoteId`,
   - `uniqueId`,
   - optionally last-known `fileId` (for the current TDLib session only, but not required).

2. On app restart and during normal thumb loading:

   - Do NOT assume previous `fileId`s are still valid.
   - Always prefer:
     - `getFileInfoByRemoteId(remoteId)` / `getRemoteFile(remoteId)` to find:
       - a TDLib-managed File with:
         - `local.path` (thumbnail path),
         - `local.isDownloadingCompleted` flag (for original video if needed).

3. For thumbnail display in UI:

   - If TDLib already has a cached thumbnail in `local.path`:
     - return that path to the ImageLoader (Coil or similar),
   - If not:
     - allow TDLib to download the thumbnail again by `remoteId` when needed.

4. Remove any code that tries to persist thumbnail paths or fileIds across app restarts as “truth”.  
   - Only `remoteId`/`uniqueId` should be considered persistent.

────────────────────────────────────────
PART 4 – Clean up logging noise and error handling
────────────────────────────────────────

Goals:
- Reduce log spam from repeated thumbnail prefetch failures.
- Make logs actionable and focused.

Tasks:

1. After implementing remoteId-first logic and service gating:

   - There should be:
     - NO more `IllegalStateException: Service not started` spam from TelegramFileLoader.
     - Significantly fewer `404` errors (only when Telegram really can’t find a file for a given remoteId).

2. Implement simple “once per error key” logging:

   - For example:
     - maintain an in-memory `Set<String>` of `(source, remoteId)` for which a 404 or permanent failure has been logged.
     - If the same error occurs again for the same remoteId, log at DEBUG or ignore.

3. Keep the informative logs, such as:

   - Prefetch batches:
     - `Prefetch batch complete total=100, success=90, failed=10, skipped=0`
   - Playback errors:
     - single concise line with:
       - fileId, remoteId, downloaded, required, totalSize.

────────────────────────────────────────
Scope Reminder
────────────────────────────────────────

- Do NOT introduce full-file caches.
- Do NOT require complete file downloads for Trickplay or playback.
- All changes must:
  - be **remoteId-first**,
  - keep the player lightweight and streaming-oriented,
  - respect Telegram engine lifecycle (no calls when service is not started),
  - use TDLib’s own cache for thumbnails across app restarts.