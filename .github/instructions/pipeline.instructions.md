---
applyTo: 
  - pipeline/telegram/**
  - pipeline/xtream/**
  - pipeline/io/**
  - pipeline/audiobook/**
---

# 🏆 PLATIN Instructions: pipeline/*

**Version:** 1.1  
**Last Updated:** 2026-01-07  
**Status:** Active

> **PLATIN STANDARD** - Catalog Producers with Zero Contamination.
>
> **Purpose:** Transform transport-layer DTOs into `RawMediaMetadata` for the Data layer.
> Pipelines are **pure catalog producers** – they ingest, map, emit. Nothing else.

---

## 🔴 ABSOLUTE HARD RULES

### 1. Single Output Type: RawMediaMetadata ONLY

```kotlin
// ✅ CORRECT: Pipeline produces RawMediaMetadata
fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata
fun XtreamVodItem.toRawMediaMetadata(): RawMediaMetadata
fun IoMediaItem.toRawMediaMetadata(): RawMediaMetadata

// ❌ FORBIDDEN: Exporting internal DTOs
fun getMediaItems(): List<TelegramMediaItem>  // WRONG - internal only
fun getVodItems(): List<XtreamVodItem>        // WRONG - internal only
```

### 2. globalId MUST Remain Empty (CI-GUARDED)

```kotlin
// ✅ CORRECT: Leave globalId with default empty value
return RawMediaMetadata(
    originalTitle = rawTitle,
    sourceType = SourceType.TELEGRAM,
    pipelineIdTag = PipelineIdTag.TELEGRAM,
    // globalId not specified → uses default ""
)

// ❌ FORBIDDEN: Computing globalId in pipeline
import com.fishit.player.core.metadata.FallbackCanonicalKeyGenerator  // VIOLATION!
globalId = FallbackCanonicalKeyGenerator.generate(...)               // VIOLATION!
globalId = "movie:something:2024"                                    // VIOLATION!
```

**CI Guard:** `FALLBACK_CANONICAL_KEY_GUARD` fails build if imported outside `core/metadata-normalizer/`.

### 3. NO Normalization, NO TMDB Lookups

```kotlin
// ❌ FORBIDDEN in pipelines
fun normalizeTitle(raw: String): String     // WRONG → normalizer's job
suspend fun fetchTmdbId(title: String): Int // WRONG → normalizer's job
val cleanedTitle = title.replace(...)       // WRONG → normalizer's job
val year = extractYearFromTitle(title)      // WRONG → normalizer's job

// ✅ CORRECT: Pass through RAW values unchanged
originalTitle = message.caption ?: fileName  // Raw, unmodified
year = structuredYear                        // Only if source provides it explicitly
```

### 4. Layer Boundary Imports

```kotlin
// ✅ ALLOWED imports
import com.fishit.player.infra.transport.telegram.api.*  // Transport DTOs
import com.fishit.player.infra.transport.xtream.*        // Transport DTOs
import com.fishit.player.core.model.*                     // Core model types
import com.fishit.player.infra.logging.UnifiedLog        // Logging only

// ❌ FORBIDDEN imports
import com.fishit.player.infra.data.*                    // Data layer
import com.fishit.player.core.persistence.*              // Persistence
import com.fishit.player.playback.*                      // Playback
import com.fishit.player.feature.*                       // UI/Feature
import com.fishit.player.core.metadata.*                 // Normalizer
import okhttp3.*                                         // Direct network
import org.drinkless.td.TdApi.*                          // Direct TDLib
import io.objectbox.*                                    // ObjectBox
```

### 5. Internal DTOs Stay Internal

```kotlin
// ✅ Pipeline-internal DTOs (NEVER exported)
data class TelegramMediaItem(...)     // pipeline/telegram/model/
data class XtreamVodItem(...)         // pipeline/xtream/model/
data class XtreamSeriesItem(...)      // pipeline/xtream/model/
data class XtreamEpisode(...)         // pipeline/xtream/model/
data class IoMediaItem(...)           // pipeline/io/

// These types MUST NOT appear in:
// - Data layer (infra/data-*)
// - Domain layer (core/*-domain)
// - Feature layer (feature/*)
// - Playback layer (playback/*)
```

---

## 📋 Pipeline Module Structure

```
pipeline/<name>/
├── adapter/           # Transport adapters (interfaces, no *Impl except Pipeline)
├── capability/        # *CapabilityProvider classes
│   └── di/           # Hilt @IntoSet bindings
├── catalog/           # Catalog orchestration, cursors, classifiers
├── debug/             # Debug services (hidden behind debug nav)
├── grouper/           # Grouping logic (Telegram bundles)
├── mapper/            # toRawMediaMetadata() extensions
├── model/             # Internal DTOs (NEVER exported)
└── <Name>CatalogPipeline.kt
```

---

## 📋 Telegram Pipeline Specifics

### Structured Bundle Processing (Contract v2.2)

```kotlin
// Bundle types per TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md
enum class TelegramBundleType {
    FULL_3ER,    // PHOTO + TEXT + VIDEO(s)
    COMPACT_2ER, // TEXT + VIDEO(s) or PHOTO + VIDEO(s)
    SINGLE,      // No bundle
}

// R7: Lossless emission - one RawMediaMetadata per VIDEO
// Bundle with 3 VIDEOs → 3 RawMediaMetadata items
// Pipeline MUST NOT drop VIDEO variants
```

### Schema Guards (R4)

```kotlin
// Values outside these ranges MUST be set to null:
year: 1800..2100       // else null
tmdbRating: 0.0..10.0  // else null
fsk: 0..21             // else null
lengthMinutes: 1..600  // else null
```

### TMDB Pass-Through Only

```kotlin
// ✅ CORRECT: Extract TMDB ID from URL, pass through
val (tmdbType, tmdbId) = TelegramTmdbType.parseFromUrl(tmdbUrl) ?: (null to null)

externalIds = ExternalIds(
    tmdb = if (tmdbId != null && tmdbType != null) {
        TmdbRef(type = tmdbType.toTmdbMediaType(), id = tmdbId)
    } else null
)

// ❌ FORBIDDEN: TMDB API lookups
val tmdbId = tmdbClient.searchMovie(title)  // WRONG
```

**TmdbMediaType Mapping (toTmdbMediaType() extension):**

```kotlin
/**
 * Maps pipeline-specific TMDB type to core TmdbMediaType.
 * Episodes use TV type (series-level TMDB ID + season/episode from metadata).
 */
fun TelegramTmdbType.toTmdbMediaType(): TmdbMediaType = when (this) {
    TelegramTmdbType.MOVIE -> TmdbMediaType.MOVIE
    TelegramTmdbType.TV_SERIES -> TmdbMediaType.TV
    TelegramTmdbType.TV_EPISODE -> TmdbMediaType.TV  // ⚠️ Use series-level ID!
}

// Example: Episode metadata
return RawMediaMetadata(
    originalTitle = "Breaking Bad - S01E01",
    mediaType = MediaType.SERIES_EPISODE,
    season = 1,
    episode = 1,
    externalIds = ExternalIds(
        tmdb = TmdbRef(
            type = TmdbMediaType.TV,  // ✅ Series-level type
            id = 1396,  // Breaking Bad series ID (NOT episode ID)
        )
    )
)
```

**Critical Rule for Episodes:**
- Episodes ALWAYS use `TmdbMediaType.TV` with **series-level ID**
- Season/episode numbers stored in `RawMediaMetadata.season`/`episode` fields
- NEVER create episode-specific TmdbRef (no such TMDB API endpoint)


### ImageRef Usage (remoteId-First)

```kotlin
// ✅ CORRECT: Use ImageRef.TelegramThumb with remoteId
thumbnail = ImageRef.TelegramThumb(
    remoteId = thumbRemoteId,   // Stable across sessions
    chatId = chatId,
    messageId = messageId,
)

// ✅ CORRECT: Minithumbnail as InlineBytes
placeholderThumbnail = ImageRef.InlineBytes(
    bytes = minithumbnailBytes,
    mimeType = "image/jpeg",
)

// ❌ FORBIDDEN: Raw URLs or fileId storage
thumbnail = thumbnailUrl  // WRONG - use ImageRef
```

---

## 📋 Xtream Pipeline Specifics

### TMDB ID Pass-Through

```kotlin
// Xtream API often provides TMDB IDs directly
externalIds = ExternalIds(
    tmdb = vodInfo.tmdbId?.let { 
        TmdbRef(type = TmdbMediaType.MOVIE, id = it) 
    }
)
```

### MediaType from Endpoint

```kotlin
// Use mediaType directly from endpoint, no inference
mediaType = when {
    endpoint == "vod" -> MediaType.MOVIE
    endpoint == "series" -> MediaType.SERIES
    endpoint == "live" -> MediaType.LIVE
    else -> MediaType.UNKNOWN
}
```

### PlaybackHints for Streaming

```kotlin
playbackHints = mapOf(
    PlaybackHintKeys.XTREAM_SERVER_URL to serverUrl,
    PlaybackHintKeys.XTREAM_STREAM_ID to streamId.toString(),
)
```

---

## 📋 IO Pipeline Specifics

```kotlin
// Minimal extraction - normalizer handles everything else
fun IoMediaItem.toRawMediaMetadata(): RawMediaMetadata =
    RawMediaMetadata(
        originalTitle = fileName,           // RAW filename
        mediaType = inferMediaType(),       // From extension only
        durationMs = durationMs,            // Already milliseconds
        sourceType = SourceType.IO,
        sourceId = toContentId(),
        pipelineIdTag = PipelineIdTag.IO,
        // year, season, episode = null → normalizer extracts from filename
    )
```

---

## 📋 Logging Requirements (UnifiedLog)

```kotlin
// ✅ CORRECT: Use UnifiedLog with stable TAGs
private const val TAG = "TelegramCatalogPipeline"

UnifiedLog.d(TAG) { "Processing chat: $chatId, items: $count" }
UnifiedLog.i(TAG) { "Catalog sync complete: emitted=$emittedCount" }
UnifiedLog.w(TAG) { "Schema guard applied: year=$year out of range" }
UnifiedLog.e(TAG, exception) { "Failed to process message: $messageId" }

// ❌ FORBIDDEN
Timber.d("message")           // Use UnifiedLog
Log.d(TAG, "message")         // Use UnifiedLog
println("debug")              // Use UnifiedLog
```

---

## 📐 Architecture Position

```
Transport Layer (infra/transport-*)
    │ Produces: TgMessage, TgContent, XtreamApiResponse
    ▼
Pipeline Layer (pipeline/*) ← YOU ARE HERE
    │ Produces: RawMediaMetadata via toRawMediaMetadata()
    │ Internal: TelegramMediaItem, XtreamVodItem (NEVER exported)
    ▼
Data Layer (infra/data-*)
    │ Consumes: RawMediaMetadata
    │ Stores/Serves: RawMediaMetadata
    ▼
Domain/UI Layer
```

---

## 🔍 Pre-Change Verification

```bash
# 1. No forbidden imports
grep -rn "import.*FallbackCanonicalKeyGenerator" pipeline/
grep -rn "import.*core\.metadata\." pipeline/
grep -rn "import okhttp3\|import org.drinkless.td.TdApi" pipeline/
grep -rn "import.*infra\.data\." pipeline/
grep -rn "import.*persistence\." pipeline/

# 2. No globalId assignments (except empty string)
grep -rn "globalId\s*=" pipeline/ | grep -v '""' | grep -v "= \"\"" | grep -v "default"

# 3. No *Impl classes (except CatalogPipelineImpl)
find pipeline/ -name "*Impl.kt" | grep -v "CatalogPipelineImpl"

# 4. No internal DTO exports (should not appear in public interfaces)
grep -rn "fun.*TelegramMediaItem" pipeline/ | grep -v "private\|internal"
grep -rn "fun.*XtreamVodItem" pipeline/ | grep -v "private\|internal"

# All should return empty!
```

---

## ✅ PLATIN Checklist Before PR

### All Pipelines
- [ ] Only produces `RawMediaMetadata` (via `toRawMediaMetadata()`)
- [ ] `globalId` remains empty (`""`)
- [ ] No normalization logic (title cleanup, year extraction from titles)
- [ ] No TMDB/IMDB/TVDB lookups (only pass-through)
- [ ] No Data layer imports (`infra/data-*`, `core/persistence`)
- [ ] No Playback layer imports
- [ ] No Feature/UI layer imports
- [ ] Internal DTOs are `internal` or in `model/` package
- [ ] Uses `UnifiedLog` for all logging
- [ ] Uses `ImageRef` for all images (no raw URLs)

### Telegram Pipeline
- [ ] Structured bundle lossless emission (1 RawMediaMetadata per VIDEO)
- [ ] Schema guards applied (year, fsk, rating, lengthMinutes)
- [ ] Cohesion Gate for bundle detection
- [ ] TMDB URL parsed to typed `TmdbRef`
- [ ] `remoteId`-first for all file references

### Xtream Pipeline
- [ ] MediaType from endpoint (no inference)
- [ ] TMDB ID passed through when available
- [ ] PlaybackHints populated for streaming

---

## 📚 Reference Documents

1. `/docs/v2/MEDIA_NORMALIZATION_CONTRACT.md` - Authoritative pipeline contract (AUTHORITATIVE)
2. `/contracts/TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md` - Bundle processing rules
3. `/contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md` - remoteId-first design
4. `/contracts/GLOSSARY_v2_naming_and_modules.md` - Terminology and module taxonomy
5. `/docs/v2/PIPELINE_ARCHITECTURE_AUDIT.md` - Layer boundaries
6. `/AGENTS.md` - Section 4.6 (Pipeline Guard Flags)
7. `pipeline/telegram/README.md`, `pipeline/xtream/README.md`
