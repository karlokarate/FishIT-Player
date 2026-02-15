````markdown
# FishIT v2 Pipelines – Finalization Plan (Telegram + Xtream + Normalizer + Playback)

> This document is a **machine-readable implementation plan** for Copilot.  
> All items are **mandatory**. No “optional” behaviors.  
> Target branch: `architecture/v2-bootstrap`.

---

## Phase 0 – Global Data Model & IDs

### Task 0.1 – Introduce global pipeline ID tags and source keys

**Goal:** Every media record knows:
- which pipeline it comes from (`PipelineIdTag`),
- its pipeline-local ID (`sourceId`),
- and a global ID (`globalId`) that is shared across pipelines for the same work.

**Files to create/modify:**

- `core/model/src/main/java/com/fishit/player/core/model/PipelineIdTag.kt`
- `core/model/src/main/java/com/fishit/player/core/model/SourceKey.kt`

**Implementation requirements:**

- Create `enum class PipelineIdTag(val code: String)` with at least:
  - `TELEGRAM("tg")`
  - `XTREAM("xc")`
  - `IO("io")`
  - `AUDIOBOOK("ab")`
  - `UNKNOWN("unk")`
- Create `data class SourceKey(val pipeline: PipelineIdTag, val sourceId: String)`.

All later tasks must use these exact types.

---

### Task 0.2 – Define core media model types used by pipelines

**Goal:** Provide shared core model types required by the v2 pipeline contracts and the existing `Telegram*` extension files in `/mnt/data`.

**Files to create:**

- `core/model/src/main/java/com/fishit/player/core/model/MediaType.kt`
- `core/model/src/main/java/com/fishit/player/core/model/SourceType.kt`
- `core/model/src/main/java/com/fishit/player/core/model/ExternalIds.kt`
- `core/model/src/main/java/com/fishit/player/core/model/ImageRef.kt`
- `core/model/src/main/java/com/fishit/player/core/model/RawMediaMetadata.kt`

**Implementation requirements:**

- `enum class MediaType` must at least cover:
  - `MOVIE`
  - `SERIES`
  - `SERIES_EPISODE`
  - `LIVE`
  - `AUDIO`
  - `OTHER`
- `enum class SourceType` must at least cover:
  - `TELEGRAM`
  - `XTREAM`
  - `IO`
  - `AUDIOBOOK`
- `data class ExternalIds`:
  - fields: `tmdbMovieId: Long?`, `tmdbTvId: Long?`, `imdbId: String?`, extendable.
- `sealed class ImageRef`:
  - `data class Url(val url: String, val headers: Map<String,String> = emptyMap())`
  - `data class InlineBytes(val bytes: ByteArray, val width: Int?, val height: Int?)`
  - `data class TelegramThumb(val fileId: Int, val remoteId: String?, val uniqueId: String, val width: Int?, val height: Int?)`
  - You may add other variants later; these are required.
- `data class RawMediaMetadata` **must** have exactly these fields and names:
  - `val originalTitle: String`
  - `val mediaType: MediaType`
  - `val year: Int?`
  - `val season: Int?`
  - `val episode: Int?`
  - `val durationMinutes: Int?`
  - `val externalIds: ExternalIds`
  - `val sourceType: SourceType`
  - `val sourceLabel: String`
  - `val sourceId: String`               // pipeline-local ID (Telegram remoteId, Xtream streamId, …)
  - `val pipelineIdTag: PipelineIdTag`    // must be set by pipelines (`tg`, `xc`, …)
  - `val globalId: String`               // canonical ID across pipelines (see Task 0.3)
  - `val poster: ImageRef?`
  - `val backdrop: ImageRef?`
  - `val thumbnail: ImageRef?`
  - `val placeholderThumbnail: ImageRef?`

Constructor and `copy()` must expose all fields.

---

### Task 0.3 – Canonical key generator

**Goal:** Provide a deterministic canonical key for media when TMDB identity is unavailable, based on normalized title and year.

**File to create:**

- `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/FallbackCanonicalKeyGenerator.kt`

**Implementation requirements:**

- Implement:
  ```kotlin
  object FallbackCanonicalKeyGenerator {
      fun generateFallbackCanonicalId(
          originalTitle: String,
          year: Int?,
          season: Int?,
          episode: Int?,
          mediaType: MediaType,
      ): CanonicalId?
  }
```

* Behavior:

  * Normalize `originalTitle` by stripping obvious scene tags (resolution, codecs, groups) and collapsing whitespace.
  * Build episode keys as `episode:<slug>:SxxExx` only when season and episode are present.
  * Build movie keys as `movie:<slug>[:<year>]`.
  * Return null for media without sufficient metadata (including LIVE).
* Pipelines leave `globalId` empty; the normalizer assigns canonical identity centrally.

---

## Phase 1 – Variant Model & Normalized Media

### Task 1.1 – Define MediaVariant model

**File to create:**

* `core/model/src/main/java/com/fishit/player/core/model/MediaVariant.kt`

**Implementation requirements:**

* Implement:

  ```kotlin
  data class MediaVariant(
      val sourceKey: SourceKey,
      val qualityTag: String,      // e.g. "SD", "HD", "FHD", "UHD", "CAM"
      val resolutionHeight: Int?,  // e.g. 480, 720, 1080, 2160
      val language: String?,       // ISO-639-1 ("de", "en", …) or null when unknown
      val isOmu: Boolean,          // Original with subtitles
      val sourceUrl: String?,      // non-null for URL-based sources (Xtream), null for Telegram file-based until resolved
      var available: Boolean = true
  )
  ```

---

### Task 1.2 – Define NormalizedMedia model

**File to create:**

* `core/model/src/main/java/com/fishit/player/core/model/NormalizedMedia.kt`

**Implementation requirements:**

* Implement:

  ```kotlin
  data class NormalizedMedia(
      val globalId: String,
      val title: String,
      val year: Int?,
      val mediaType: MediaType,
      val primaryPipelineIdTag: PipelineIdTag,
      val primarySourceId: String,
      val variants: MutableList<MediaVariant>
  )
  ```
* Invariants:

  * `variants` is **never empty**.
  * `primaryPipelineIdTag` + `primarySourceId` always match the **currently best** variant (see Task 1.3).

---

### Task 1.3 – Variant selection (best-quality + preferences)

**File to create:**

* `core/model/src/main/java/com/fishit/player/core/model/VariantSelector.kt`

**Implementation requirements:**

* Define:

  ```kotlin
  data class VariantPreferences(
      val preferredLanguage: String,       // default: system language
      val preferOmu: Boolean,              // default: false
      val preferXtream: Boolean = true     // default: true
  )

  object VariantSelector {
      fun sortByPreference(
          variants: List<MediaVariant>,
          prefs: VariantPreferences
      ): List<MediaVariant>
  }
  ```

* Sorting score must apply these rules in **strict order**:

  1. **Availability**

     * Only `available == true` variants are considered; false ones are sorted to the end.

  2. **Language priority**

     * +40 if `language == prefs.preferredLanguage`.
     * +20 if `isOmu && prefs.preferOmu`.
     * +10 if `!isOmu && !prefs.preferOmu` (dubbed when OmU not preferred).

  3. **Quality priority**

     * Add `resolutionHeight ?: 0` directly to score.

       * This ensures 1080 > 720 > 480 etc.
     * Add +20 if `qualityTag` contains `"WEB"` or `"BluRay"` (case-insensitive).
     * Subtract 100 if `qualityTag` contains `"CAM"` (case-insensitive).

  4. **Pipeline priority**

     * Add +15 if `sourceKey.pipeline == PipelineIdTag.XTREAM && prefs.preferXtream`.

* `sortByPreference` must return a **new list** sorted by descending score, stable sort.

---

## Phase 2 – Normalization & Cross-pipeline Merge

### Task 2.1 – Implement Raw → Normalized merge

**File to create:**

* `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/Normalizer.kt`

*(If `core/metadata-normalizer` module does not exist, create it and register in `settings.gradle.kts`.)*

**Implementation requirements:**

* Implement:

  ```kotlin
  object Normalizer {
      fun normalize(rawItems: List<RawMediaMetadata>, prefs: VariantPreferences): List<NormalizedMedia>
  }
  ```
* Behavior:

  1. Group `rawItems` by `globalId`.

  2. For each group:

     * Determine a canonical title and year:

       * Start from `originalTitle` and `year` of the first item in the group.
       * (Do not perform heavy cleaning here; the heavy TMDB/title normalization can be added later. For now just use `originalTitle` as-is.)
     * Create an initial `NormalizedMedia` with:

       * `globalId` (from group key),
       * `title`,
       * `year`,
       * `mediaType` (first item's mediaType; assume group is homogeneous),
       * temporary placeholders for `primary*` and variants.
     * For each `RawMediaMetadata` in the group:

       * Create a `MediaVariant`:

         * `sourceKey = SourceKey(raw.pipelineIdTag, raw.sourceId)`
         * `qualityTag`:

           * For Telegram: derive from file name or resolution:

             * if height ≥ 2160 → `"UHD"`
             * else if height ≥ 1080 → `"FHD"`
             * else if height ≥ 720 → `"HD"`
             * else `"SD"`
           * For Xtream: use provider info (`"HD"`, `"SD"`), fallback to resolution as above.
         * `resolutionHeight`:

           * Use thumbnail/poster resolution if known, else null.
         * `language`:

           * initially set to system language; per-pipeline language detection can be refined later.
         * `isOmu`:

           * `true` if filename/category contains `"omu"` or `"[omu]"` (case-insensitive), else false.
         * `sourceUrl`:

           * For Xtream: actual stream URL.
           * For Telegram: set to `null` (will be resolved at playback).
         * `available = true`.
       * Add variant to `NormalizedMedia.variants`.
     * After all variants added:

       * Call `VariantSelector.sortByPreference(variants, prefs)` and reorder `variants` accordingly.
       * Set:

         * `primaryPipelineIdTag = variants[0].sourceKey.pipeline`
         * `primarySourceId = variants[0].sourceKey.sourceId`

  3. Return all `NormalizedMedia` objects as a list.

---

## Phase 3 – Telegram Pipeline & Transport

> This phase wires the Telegram side: transport, models, raw mapping, chat classification, and media-only updates.

### Task 3.1 – Integrate Telegram v2 model files

**Goal:** Place the 7 Telegram model/extension files from `/mnt/data` into the v2 repo under the correct package structure.

**Files to create in repo (copy content from `/mnt/data`):**

* `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramMediaItem.kt`
* `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramMediaType.kt`
* `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramPhotoSize.kt`
* `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramMetadataMessage.kt`
* `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramMessageStub.kt`
* `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramChatSummary.kt`
* `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramImageRefExtensions.kt`
* `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramRawMetadataExtensions.kt`

**Implementation requirements:**

* Ensure package declaration is `package com.fishit.player.pipeline.telegram.model` as in the provided files.
* Fix imports to reference the newly created core model types:

  * `com.fishit.player.core.model.RawMediaMetadata`
  * `com.fishit.player.core.model.MediaType`
  * `com.fishit.player.core.model.ExternalIds`
  * `com.fishit.player.core.model.SourceType`
  * `com.fishit.player.core.model.ImageRef`
* In `TelegramRawMetadataExtensions.kt`, extend the `RawMediaMetadata` constructor call to set:

  * `pipelineIdTag = PipelineIdTag.TELEGRAM`
  * leave `globalId` empty (assigned centrally by the normalizer)
  * `sourceId = remoteId ?: "msg:$chatId:$messageId"` (already present; now consistent with SourceKey).

---

### Task 3.2 – TelegramTransportClient and TDLib wrapper

**Goal:** Implement the Telegram transport layer as per the Kotlin interface the user provided in chat, and adapt it to v2 modules.

**Files to create:**

* `infra/transport-telegram/build.gradle.kts`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TelegramTransportClient.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TgChat.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TgMessage.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TgContent.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TgFile.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TgThumbnail.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TgPhotoSize.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TelegramAuthState.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TelegramConnectionState.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TelegramAuthException.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/TelegramFileException.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/internal/TdLibEngine.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/internal/TdAuthController.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/internal/TdConnectionController.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/internal/TdUpdateFlow.kt`
* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/internal/TelegramFileResolver.kt`

**Implementation requirements:**

* Implement `TelegramTransportClient` exactly as in the user’s posted interface:

  * Properties:

    * `val authState: Flow<TelegramAuthState>`
    * `val connectionState: Flow<TelegramConnectionState>`
  * Methods:

    * `suspend fun ensureAuthorized()`
    * `suspend fun isAuthorized(): Boolean`
    * `suspend fun getChats(limit: Int = 100): List<TgChat>`
    * `suspend fun fetchMessages(chatId: Long, limit: Int = 100, offsetMessageId: Long = 0): List<TgMessage>`
    * `suspend fun resolveFile(fileId: Int): TgFile`
    * `suspend fun resolveFileByRemoteId(remoteId: String): TgFile`
    * `suspend fun requestFileDownload(fileId: Int, priority: Int = 16): TgFile`
    * `suspend fun close()`
* `TdLibEngine`:

  * Encapsulate TDLib client creation, event loop, and threading.
* `TdAuthController`:

  * Map TDLib auth states into `TelegramAuthState` sealed class (Idle, Connecting, WaitingForPhone, WaitingForCode, WaitingForPassword, Ready, Error).
  * Implement `ensureAuthorized()` and `isAuthorized()` logic.
* `TdConnectionController`:

  * Map TDLib connection states into `TelegramConnectionState` (Disconnected, Connecting, Connected, Error).
* `TdUpdateFlow`:

  * Provide a `Flow<TdApi.Update>` from TDLib callbacks using `callbackFlow`.
* `TelegramFileResolver`:

  * Provide:

    ```kotlin
    suspend fun resolveFileOrThrow(fileId: Int?, remoteId: String): TgFile
    ```
  * Behavior:

    * If `fileId != null && fileId != 0`, try `resolveFile(fileId)` first.
    * On error or if `fileId` is invalid, fall back to `resolveFileByRemoteId(remoteId)`.

---

### Task 3.3 – Media-only update flow and chat classification (Hot/Warm/Cold)

**Goal:** Only media messages flow into the media pipeline; chats are classified by media density.

**Files to create/modify:**

* `infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/internal/TelegramUpdateClassifier.kt`
* `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/catalog/TelegramChatMediaProfile.kt`
* `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/catalog/TelegramChatMediaClassifier.kt`

**Implementation requirements:**

* In `TdUpdateFlow` or a separate transformer:

  * Implement:

    ```kotlin
    val mediaUpdates: Flow<TgMessage>
    ```
  * Filter logic:

    * Only pass messages whose `TgContent` is one of:

      * `Video`, `Audio`, `VoiceNote`, `VideoNote`, `Animation`, `Photo`, media `Document` (MIME audio/video).
    * All other message types (Text, Unsupported, service messages) are dropped immediately (no DB, no mapping).
* Implement `ChatMediaProfile`:

  ```kotlin
  data class ChatMediaProfile(
      val chatId: Long,
      var totalMessagesSampled: Int,
      var mediaMessagesSampled: Int,
      var lastMediaAtMillis: Long?
  ) {
      val mediaRatio: Double
          get() = if (totalMessagesSampled == 0) 0.0 else mediaMessagesSampled.toDouble() / totalMessagesSampled.toDouble()
  }

  enum class ChatMediaClass { MEDIA_HOT, MEDIA_WARM, MEDIA_COLD }
  ```
* Implement `TelegramChatMediaClassifier`:

  * Maintain an in-memory `Map<Long, ChatMediaProfile>` keyed by `chatId`.
  * Provide:

    ```kotlin
    fun classify(profile: ChatMediaProfile): ChatMediaClass
    ```
  * Classification thresholds (fixed):

    * `MEDIA_HOT` if:

      * `mediaMessagesSampled >= 20` OR `mediaRatio >= 0.3`
    * `MEDIA_WARM` if not hot AND:

      * `mediaMessagesSampled >= 3` AND `mediaRatio >= 0.05`
    * `MEDIA_COLD` otherwise.
  * On initial chat scan:

    * Sample last 200 messages.
    * Create/update profile.
    * If profile class is `MEDIA_COLD`, **do not ingest** any media from this chat into the catalog initially and mark the chat as suppressed.
  * On live `mediaUpdates`:

    * For every incoming `TgMessage` with media:

      * Update profile (`totalMessagesSampled++`, `mediaMessagesSampled++`, update `lastMediaAtMillis`).
      * Re-run classification.
      * If chat was `MEDIA_COLD` and now becomes `MEDIA_WARM` or `MEDIA_HOT`:

        * Trigger a **one-time full media ingestion** for that chat into the pipeline.

---

### Task 3.4 – Telegram → TelegramMediaItem mapping fixes

**File to modify (already provided in `/mnt/data`):**

* `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramPipelineAdapter.kt`

**Implementation requirements:**

* In `TgMessage.toMediaItem()`:

  * Convert `date` (which is seconds since epoch) to milliseconds:

    * `date = date.toLong() * 1000L`.
  * For `TgContent.Document`, use a shared `MimeDecider` in `core`:

    * Create `core/util/src/main/java/com/fishit/player/core/util/MimeDecider.kt` with:

      * `fun inferKind(mimeType: String?, fileName: String?): String?` that returns `"video"`, `"audio"`, or `null`.
    * Include additional extension-based detection (e.g. `.mp4`, `.mkv`, `.avi`, `.mp3`, `.flac`, etc.).
    * Only treat `Document` as media when `inferKind` returns `"video"` or `"audio"`.
* Ensure `TelegramMediaItem` populates:

  * `fileId`, `fileUniqueId`, `remoteId`, `chatId`, `messageId`, dimensions, captions, `photoSizes`, and fields required by `TelegramRawMetadataExtensions.kt` (year, seasonNumber, episodeNumber, durationSecs, etc.).

---

### Task 3.5 – Telegram mini-thumbnails

**Files to modify:**

* `infra/transport-telegram` (mapping from TDLib Thumbnail types to `TgThumbnail` and `TgPhotoSize`).
* `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramImageRefExtensions.kt`

**Implementation requirements:**

* For TDLib messages with `minithumbnail`:

  * Extract the byte array and map it to a field in `TelegramMediaItem` (e.g. `minithumbnailBytes`, `minithumbnailWidth`, `minithumbnailHeight`).
* In `TelegramImageRefExtensions.kt`:

  * Implement:

    * `fun TelegramMediaItem.toMinithumbnailImageRef(): ImageRef?` that returns:

      * `ImageRef.InlineBytes(bytes = minithumbnailBytes, width = minithumbnailWidth, height = minithumbnailHeight)` when present, else null.
  * `toThumbnailImageRef()` should use:

    * For photos: best available `TelegramPhotoSize` (largest resolution).
    * For videos: the TDLib video thumbnail.

---

## Phase 4 – Xtream Pipeline

> Xtream must mirror Telegram’s capabilities: raw mapping → RawMediaMetadata → Normalizer → NormalizedMedia + variants.

### Task 4.1 – Define Xtream pipeline structure

**Files to create:**

* `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/model/XtreamVodItem.kt`
* `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/model/XtreamSeriesItem.kt`
* `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/model/XtreamEpisodeItem.kt`
* `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/model/XtreamImageRefExtensions.kt`
* `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/model/XtreamRawMetadataExtensions.kt`

**Implementation requirements:**

* Model fields must cover:

  * VOD:

    * `streamId`, `title`, `year`, `durationMinutes`, `posterUrl`, `language`, `isHd`, etc.
  * Series/Episode:

    * `seriesId`, `episodeId`, `seasonNumber`, `episodeNumber`, `episodeTitle`, `seriesTitle`, `year`, `streamUrl`, etc.
* `XtreamImageRefExtensions`:

  * Provide:

    * `fun XtreamVodItem.toPosterImageRef(): ImageRef?` using poster URL.
    * `fun XtreamEpisodeItem.toThumbnailImageRef(): ImageRef?`.
* `XtreamRawMetadataExtensions`:

  * Implement:

    * `fun XtreamVodItem.toRawMedia(): RawMediaMetadata`
    * `fun XtreamEpisodeItem.toRawMedia(): RawMediaMetadata`
  * For both, set:

    * `originalTitle = title` (no cleaning),
    * `mediaType = MediaType.MOVIE` (VOD) or `MediaType.SERIES_EPISODE` (episode),
    * `year` from API,
    * `season` and `episode` for episodes,
    * `durationMinutes` from API,
    * `externalIds` from any IDs provided (IMDB/TMDB if API supports, else empty),
    * `sourceType = SourceType.XTREAM`,
    * `sourceLabel = "Xtream"`,
    * `sourceId`:

      * VOD: `"vod:$streamId"`
      * Episode: `"episode:$seriesId:$episodeId"`,
    * `pipelineIdTag = PipelineIdTag.XTREAM`,
    * leave `globalId` empty (assigned centrally by the normalizer),
    * `poster`/`thumbnail` using image ref extensions,
    * `placeholderThumbnail = null` (no mini-thumbs from Xtream).

---

## Phase 5 – Playback Integration, Moov Validation & Fallback

### Task 5.1 – Implement MP4 moov-header validation for Telegram playback only

**Files to create/modify:**

* `playback/domain/src/main/java/com/fishit/player/playback/domain/TelegramMp4Validator.kt`
* `player/internal/src/main/java/com/fishit/player/internal/source/InternalPlaybackSourceResolver.kt`

**Implementation requirements:**

* Implement `TelegramMp4Validator`:

  * Provide:

    ```kotlin
    object TelegramMp4Validator {
        suspend fun ensureFileReadyWithMp4Validation(
            file: java.io.File,
            timeoutMillis: Long
        )
    }
    ```
  * Behavior:

    * **Only used during playback**, not scanning or prefetch.
    * Loop:

      * Check current file length and prefix bytes.
      * Parse MP4 header and ensure `moov` atom is present in the prefix.
      * If `moov` is not yet fully present, wait briefly and re-check, up to `timeoutMillis`.
    * If timeout elapses without valid `moov`, throw a playback exception.
* In `InternalPlaybackSourceResolver`:

  * Extend the resolver to support Telegram playback:

    * When `PlaybackContext` indicates Telegram pipeline and contains a Telegram `SourceKey`:

      * Resolve Telegram file path via `TelegramTransportClient` and `TelegramFileResolver` (file ID or remote ID).
      * Call `TelegramMp4Validator.ensureFileReadyWithMp4Validation(...)` before returning final `file://` URI to the internal player.
  * Ensure **no moov validation** is performed during pipeline scans or preview thumbnail loading.

---

### Task 5.2 – Implement variant-based playback with automatic fallback

**Files to modify:**

* `player/internal/src/main/java/com/fishit/player/internal/session/InternalPlayerSession.kt`
* `player/internal/src/main/java/com/fishit/player/internal/source/InternalPlaybackSourceResolver.kt`

**Implementation requirements:**

* `InternalPlayerSession` must:

  * Accept a `NormalizedMedia` object (or at least its `globalId` and chosen `MediaVariant`) as playback input.
  * When asked to play a media:

    * Retrieve its `NormalizedMedia.variants`.
    * Sort them using `VariantSelector.sortByPreference(...)` with default preferences:

      * `preferredLanguage = system language`,
      * `preferOmu = false`,
      * `preferXtream = true`.
    * Try to play the first (best) variant:

      * For Xtream: use `sourceUrl` directly.
      * For Telegram: resolve local file (using transport) and run `TelegramMp4Validator.ensureFileReadyWithMp4Validation`.
    * On playback failure:

      * Mark the current variant as `available = false`.
      * Move to the next variant in the sorted list and retry.
      * Continue until a variant plays successfully or all are exhausted.
    * If all variants fail:

      * Log an error and notify the UI with a clear “media not available” state.
* **Do NOT let the player crash or exit** when the first variant fails.

---

## Phase 6 – Dead Media Detection and Cleanup

### Task 6.1 – Mark and remove dead variants

**Files to create/modify:**

* `core/model/src/main/java/com/fishit/player/core/model/VariantHealthStore.kt`
* `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/Normalizer.kt` (optional integration)
* `app-v2` (or equivalent) scheduling code for periodic cleanup

**Implementation requirements:**

* Implement `VariantHealthStore` (in-memory is sufficient):

  ```kotlin
  data class VariantHealth(
      var hardFailureCount: Int,
      var lastHardFailureMillis: Long?
  )

  object VariantHealthStore {
      private val healthBySourceKey: MutableMap<SourceKey, VariantHealth> = mutableMapOf()

      fun recordHardFailure(sourceKey: SourceKey, nowMillis: Long)
      fun isPermanentlyDead(sourceKey: SourceKey, nowMillis: Long): Boolean
  }
  ```
* Policy:

  * Increment `hardFailureCount` and update `lastHardFailureMillis` on each **confirmed 404 / file-not-found / explicit “no such file”** error.
  * `isPermanentlyDead` returns true if:

    * `hardFailureCount >= 3` AND
    * `lastHardFailureMillis` is older than 24 hours ago.
* Integration:

  * When `isPermanentlyDead(sourceKey, now)` becomes true:

    * Mark the variant in `NormalizedMedia.variants` as `available = false`.
    * On next refresh, permanently remove variants with `available == false` and `isPermanentlyDead == true`.
  * When a `NormalizedMedia` ends up with **no variants**:

    * Remove it from the main catalog.
    * In watchlist/history, mark it as “unavailable” instead of showing as playable.

---

## Phase 7 – UI: Advanced Settings for Variant Preferences

### Task 7.1 – Add advanced settings for language & source preferences

**Files to modify (adjust depending on existing settings structure):**

* `app-v2/src/main/java/com/fishit/player/app/settings/PlaybackSettingsRepository.kt`
* `app-v2/src/main/java/com/fishit/player/app/settings/PlaybackSettingsScreen.kt`

**Implementation requirements:**

* Add persistent settings:

  * `preferredLanguage` (default: system language).
  * `preferOmu` (default: false).
  * `preferXtreamSource` (default: true).
* Wire these settings to `VariantPreferences` used by `VariantSelector` and playback path.

### Task 7.2 – Manual variant selection per media

**Files to modify:**

* `app-v2/src/main/java/com/fishit/player/app/ui/details/MediaDetailsViewModel.kt`
* `app-v2/src/main/java/com/fishit/player/app/ui/details/MediaDetailsScreen.kt`

**Implementation requirements:**

* In the details screen:

  * If `NormalizedMedia.variants.size > 1`, show a **“Choose version”** button in an “Advanced” section.
  * On click:

    * Show a list of variants using human-readable labels:

      * example: `"FHD – de (Xtream)"`, `"HD – de (Telegram)"`, etc.
    * On selection:

      * Update a per-media override (in memory is sufficient for now) indicating the preferred `SourceKey` for this `globalId`.
      * When starting playback for this `NormalizedMedia`, ensure this override variant is tried **first** in the candidate list.

---

## Final Execution Order (Checklist)

Execute tasks in this order:

1. **Phase 0 – Global Data Model & IDs**

   * [ ] Task 0.1 – PipelineIdTag & SourceKey
   * [ ] Task 0.2 – Core model types (`MediaType`, `SourceType`, `ExternalIds`, `ImageRef`, `RawMediaMetadata`)
   * [ ] Task 0.3 – Canonical key generator

2. **Phase 1 – Variants & Normalized Media**

   * [ ] Task 1.1 – MediaVariant
   * [ ] Task 1.2 – NormalizedMedia
   * [ ] Task 1.3 – VariantSelector

3. **Phase 2 – Normalizer**

   * [ ] Task 2.1 – Normalizer.normalize(rawItems, prefs)

4. **Phase 3 – Telegram**

   * [ ] Task 3.1 – Integrate Telegram model and extension files from `/mnt/data` into `pipeline/telegram`
   * [ ] Task 3.2 – Implement TelegramTransportClient & internal TDLib engine
   * [ ] Task 3.3 – Media-only update flow + ChatMediaProfile + Hot/Warm/Cold classification
   * [ ] Task 3.4 – Fix Telegram mapping (timestamps, MimeDecider)
   * [ ] Task 3.5 – Implement mini-thumbnails and ImageRef wiring

5. **Phase 4 – Xtream**

   * [ ] Task 4.1 – Implement Xtream models, image extensions, RawMediaMetadata mapping

6. **Phase 5 – Playback**

   * [ ] Task 5.1 – TelegramMp4Validator & moov validation only at playback
   * [ ] Task 5.2 – Variant-based playback with automatic fallback

7. **Phase 6 – Dead media**

   * [ ] Task 6.1 – VariantHealthStore & dead variant removal

8. **Phase 7 – UI settings & manual variants**

   * [ ] Task 7.1 – Advanced settings (language, OmU, preferXtream)
   * [ ] Task 7.2 – Manual variant selection per media in details screen

After all boxes are checked, Telegram and Xtream pipelines, the normalizer, and the playback stack will be fully aligned with the agreed design:

* global IDs,
* multi-variant media,
* auto best-quality selection,
* fallback and cleanup of dead media,
* Telegram-specific flow optimization with hot/warm/cold chats,
* and Moov validation strictly at playback time.

```
```
