
# Telegram Metadata Extraction Guide (Based on `docs/telegram/exports/exports`)

## 1. Purpose and Scope

This document defines **how Copilot Agents must analyze the real Telegram export JSON files** in the repository and use them to:

- understand the **actual structure** of movie and series chats,
- identify **all available metadata** (including those hidden in text-only messages),
- design and adjust the **Telegram DTOs** and mappers so that no relevant fields are lost,
- keep the pipeline **contract-compliant** with the central normalization system
  (`MEDIA_NORMALIZATION_AND_UNIFICATION.md` and `MEDIA_NORMALIZATION_CONTRACT.md`).

The analysis MUST be based on the real JSON exports under:

```text
docs/telegram/exports/exports/*.json
```

This guide does **not** duplicate those JSONs, but describes **how** to read and interpret them, and what to extract.

---

## 2. Source Data: Telegram Export JSON Files

### 2.1 Location

All exports are stored under:

```text
docs/telegram/exports/exports/
```

Each file typically represents **one chat** (channel, group, or bot conversation), e.g.:

- `docs/telegram/exports/exports/-1001180440610.json`
- `docs/telegram/exports/exports/-1001414928982.json`
- `docs/telegram/exports/exports/<other-chat-id>.json`

### 2.2 High-Level Structure (per file)

While the exact structure may vary, each JSON file is expected to contain:

- **Chat-level metadata**
  - `chatId` or similar chat identifier
  - `title` (chat title)
  - `exportedAt` or similar
  - message count, etc.

- **Messages array**
  - `messages`: list of message objects, each representing a Telegram message.

Each message object typically includes:

- `id` (messageId)
- `chatId`
- `date` / `editDate`
- flags such as `isOutgoing`, `isPinned`, `isChannelPost`
- `replyToMessageId`, `mediaAlbumId` (if applicable)
- `content`:
  - structure depends on the message type (video, document, audio, photo, text, etc.)

Agents must **not** assume a single schema; instead, they must inspect all JSON files and infer the **full set of variants** used in this project.

---

## 3. Types of Messages and What Can Be Extracted

Agents MUST analyze **every chat export file** and identify at least the following message types and their fields:

### 3.1 Media Messages (Video / Movie Files)

These messages usually contain:

- `content.video` **or** `content.document` (for videos sent as documents)
  - `fileName` (often containing scene-style info, e.g. `X-Men.2000.1080p.BluRay.x264-GROUP.mkv`)
  - `mimeType` (e.g. `video/x-matroska`)
  - `duration` in seconds
  - `width`, `height`
  - nested `file` / `document` object with:
    - `remote.id` → Telegram `file_id`
    - `remote.uniqueId` → Telegram `file_unique_id`
  - optional `thumbnail` with:
    - `width`, `height`
    - nested `file.remote.id` / `file.remote.uniqueId`
- `caption`:
  - `content.caption.text` (human-readable title, languages, commentary)

**Agents MUST:**

- record how these fields appear across different chats (movies in different groups, e.g. German-only, multi-language, 3D, UHD, etc.),
- ensure the DTOs capture:
  - `fileName`
  - `mimeType`
  - `durationSecs`
  - `width`, `height`
  - `fileId`, `fileUniqueId`
  - thumbnail metadata (if present)
  - `caption`

### 3.2 Archive Messages (RAR/ZIP with Episodes or Splits)

In series groups and some movie groups, content appears as documents:

- `content.document`:
  - `fileName` such as:
    - multi-episode RARs: `Die Schlümpfe - Staffel 9 - Episode 422-427.rar`
    - split archives: `Chain Letter - 3D.zip.001`, `.002`, ...
  - `mimeType` often `application/octet-stream` or `application/vnd.rar`
  - nested `document` object with:
    - `remote.id`, `remote.uniqueId`
- `caption.text`:
  - may be empty or contain additional hints.

**Agents MUST:**

- capture `fileName`, `mimeType`, `fileId`, `fileUniqueId`, `caption` as raw values,
- NOT attempt to reconstruct episodes/parts in the pipeline – this stays in domain logic/normalizer.

### 3.3 Audio Messages (Music / Soundtracks / Audio-Episodes)

Some chats may contain `content.audio` with:

- `duration` in seconds
- `title` (track title)
- `performer`
- `fileName`
- `mimeType` (e.g. `audio/mpeg`)
- nested `audio.remote.id`, `audio.remote.uniqueId`

**Agents MUST** map these into audio-capable DTO fields (even if the current app does not fully exploit them yet).

### 3.4 Photo Messages (Covers, Posters, Stills)

Some chats send **photos only** with or without captions:

- `content.photo.sizes`:
  - array of sizes with:
    - `width`, `height`
    - nested `file.remote.id` + `file.remote.uniqueId`
- `caption.text`:
  - may contain a title or description.

**Agents MUST:**

- identify photo-only messages,
- capture all available sizes in a `TelegramPhotoSize` DTO,
- derive:
  - a “main” size (largest) for display,
  - a “thumbnail” size (smallest) for preview,
- keep captions as raw text.

### 3.5 Pure Text Metadata Messages (Title/Year/FSK/Genres/TMDB URL)

Critical: Many chats encode **rich metadata in text messages**, often with additional machine-parsable fields.

Examples (see JSON in:

- `docs/telegram/exports/exports/-1001180440610.json` for movies,
- `docs/telegram/exports/exports/-1001414928982.json` for series,
- and similar files):

- Text in `message.text` such as:

  ```text
  Titel: Das Ende der Welt - Die 12 Prophezeiungen der Maya
  Originaltitel: The 12 Disasters of Christmas
  Erscheinungsjahr: 2012
  Länge: 89 Minuten
  Produktionsland: Kanada
  FSK: 12
  Regie: Steven R. Monroe
  TMDbRating: 4.456
  Genres: TV-Film, Action, Science Fiction
  Weitere Infos: https://www.themoviedb.org/movie/149722-...
  ```

- Additionally, the same message may include pre-parsed fields in JSON (depending on how the export was generated):
  - `year`
  - `lengthMinutes`
  - `fsk`
  - `originalTitle`
  - `productionCountry`
  - `genres` (array)
  - `tmdbRating`
  - `tmdbUrl` (or similar)

**Agents MUST:**

- recognize such messages as **metadata messages**, not as playable media,
- design a dedicated DTO like:

  ```kotlin
  data class TelegramMetadataMessage(
      val chatId: Long,
      val messageId: Long,
      val date: Long,
      val title: String?,
      val originalTitle: String?,
      val year: Int?,
      val lengthMinutes: Int?,
      val fsk: Int?,
      val productionCountry: String?,
      val genres: List<String>,
      val tmdbUrl: String?,
      val tmdbRating: Double?,
      val rawText: String
  )
  ```

- NOT parse TMDB IDs here; they can be extracted later in the normalizer or a dedicated resolver.

---

## 4. DTO Design Guidelines

Based on the above, the Telegram pipeline should distinguish:

### 4.1 TelegramMediaItem (playable content)

Represents a single playable media item (video, document, audio, photo).

**Key fields:**

- Identification:
  - `chatId`, `messageId`, `mediaAlbumId`, `date`
- Type and media:
  - `mediaType: TelegramMediaType` (`VIDEO`, `DOCUMENT`, `AUDIO`, `PHOTO`, `OTHER`)
  - `fileName`, `mimeType`
  - `durationSecs`, `width`, `height`
  - `fileId`, `fileUniqueId`
- Visuals:
  - `thumbnailFileId`, `thumbnailUniqueId`, `thumbnailWidth`, `thumbnailHeight`
  - `photoSizes: List<TelegramPhotoSize>` for photo-only messages
- Text:
  - `caption: String?`

**Rules:**

- All values must be **raw** as provided in the JSON (no cleaning or normalization).
- Scene-style names (e.g. `X-Men.2000.1080p.BluRay.x264-GROUP.mkv`) must be preserved as-is.
- No attempt to infer episodes, parts, seasons in this DTO.

### 4.2 TelegramMetadataMessage (work-level metadata)

Represents a pure metadata message (usually text) that describes a movie or a series.

As shown above, it must capture:

- Titles, year
- Length / episodes information
- FSK/age rating
- Countries, genres
- TMDB URL / rating
- Entire raw text

### 4.3 Future Combined View

A higher-level repository structure may combine these:

```kotlin
data class TelegramMediaWithMeta(
    val media: TelegramMediaItem,
    val meta: TelegramMetadataMessage?
)
```

The mapping from messages to `TelegramMediaWithMeta` is a **repository/domain concern**, not part of `RawMediaMetadata` or the normalizer.

---

## 5. RawMediaMetadata Mapping (High-Level)

When later implementing `TelegramMediaItem.toRawMediaMetadata()` (Phase 3+):

- Use `TelegramMediaItem` for:
  - `originalTitle` (from caption or fileName),
  - `durationMinutes` (from `durationSecs`),
  - `sourceType = SourceType.TELEGRAM`,
  - `sourceLabel` (chat title / id),
  - `sourceId` (`tg:file:<fileId>` or `tg:msg:<chatId>:<messageId>`).

- Use `TelegramMetadataMessage` for:
  - `year`,
  - `lengthMinutes` (fallback if durationSecs missing),
  - optional FSK/genres (stored in a separate structure, not directly in RawMediaMetadata),
  - `tmdbUrl` as a raw external hint (converted to tmdbId later by the normalizer/domain, not in the pipeline).

Normalization, cleaning, TMDB lookups and canonical identity **do not** happen in the Telegram pipeline and are governed by the central contracts.

---

## 6. Copilot Agent Responsibilities

Any Copilot Agent working on the Telegram pipeline MUST:

1. Read and understand:
   - `docs/v2/MEDIA_NORMALIZATION_AND_UNIFICATION.md`
   - `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md`
   - This document.

2. Analyze **every** JSON chat under `docs/telegram/exports/exports/*.json`:
   - at least 7 movie-oriented chats,
   - at least 7 series-oriented chats,
   - identify all distinct message structures and metadata patterns.

3. Adjust:
   - `TelegramMediaItem` and related DTOs,
   - `TelegramMetadataMessage` (or equivalent),
   - `TelegramMappers.kt` mapping logic,
   - and tests,

   so that all relevant fields from the JSONs are captured and passed through as raw values.

4. Never:
   - introduce any title cleaning, normalization, or TMDB lookups in the pipeline,
   - implement `RawMediaMetadata` inside the Telegram pipeline module (types live in `:core:model`),
   - attempt to derive canonical identity in the Telegram pipeline.

All behavior beyond raw extraction and mapping is delegated to the central metadata normalizer and TMDB resolver.
