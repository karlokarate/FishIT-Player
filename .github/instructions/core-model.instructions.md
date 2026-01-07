---
applyTo: 
  - core/model/**
---

# Copilot Instructions:  core/model

**Version:** 1.0  
**Last Updated:** 2026-01-07  
**Status:** Active

> **Module Purpose:** Central, source-agnostic data models shared across ALL layers. 
> This is the foundational module - every other module in the project depends on it. 
> 
> **Quality Standard:** PLATIN - Zero tolerance for violations. 

---

## 🔴 ABSOLUTE HARD RULES

### 1. Pure Data Only - NO Behavior
```kotlin
// ✅ ALLOWED:  Data classes, enums, sealed interfaces, value classes
data class RawMediaMetadata(...)
enum class MediaType { MOVIE, SERIES, SERIES_EPISODE, LIVE, ...  }
sealed interface ImageRef { ... }
@JvmInline value class TmdbId(val id: Int)

// ❌ FORBIDDEN: Any logic, network, persistence, UI
class MediaNormalizer { ...  }        // → core/metadata-normalizer
suspend fun fetchFromTmdb() { ... }  // → infra layer
fun normalize(title: String) { ... } // → core/metadata-normalizer
```

### 2. Kotlin Stdlib + Annotations ONLY
```kotlin
// ✅ ALLOWED
import kotlin.time.Duration
import kotlinx.serialization.Serializable

// ❌ FORBIDDEN - Zero exceptions
import okhttp3.*                    // Network
import androidx.room.*              // Persistence
import io.objectbox.*               // ObjectBox
import org.drinkless.td.*           // TDLib
import androidx.compose.*           // UI
import dagger.*                     // DI (except annotations)
import androidx.media3.*            // Player
```

### 3. Source-Agnostic Design
```kotlin
// ❌ FORBIDDEN:  Source-specific fields
data class RawMediaMetadata(
    val telegramChatId: Long,     // WRONG
    val xtreamStreamId: Int,      // WRONG
    val tdlibFileId: Int,         // WRONG
)

// ✅ CORRECT: Generic identifiers
data class RawMediaMetadata(
    val sourceType: SourceType,   // Enum:  TELEGRAM, XTREAM, IO, ...
    val sourceId: String,         // Opaque string
    val playbackHints: Map<String, String> = emptyMap(), // Source-specific hints
)
```

---

## 📋 Canonical Types (Authoritative - Dec 2025)

### RawMediaMetadata (Pipeline Output)
```kotlin
data class RawMediaMetadata(
    // === Identity ===
    val originalTitle: String,              // RAW - no cleaning! 
    val sourceType: SourceType,
    val sourceId:  String,
    val pipelineIdTag: PipelineIdTag = PipelineIdTag.UNKNOWN,
    val globalId: String = "",              // ⚠️ MUST be empty in pipelines! 
    
    // === Classification ===
    val mediaType: MediaType = MediaType.UNKNOWN,
    val year: Int?  = null,
    val season: Int? = null,
    val episode: Int? = null,
    
    // === Timing ===
    val durationMs: Long? = null,           // ⚠️ ALWAYS milliseconds!
    val addedTimestamp: Long?  = null,       // Unix epoch seconds
    
    // === External IDs ===
    val externalIds: ExternalIds = ExternalIds(),
    
    // === Imaging (ImageRef only!) ===
    val poster: ImageRef? = null,
    val backdrop: ImageRef? = null,
    val thumbnail: ImageRef? = null,
    val placeholderThumbnail: ImageRef? = null,
    
    // === Rich Metadata (passthrough) ===
    val rating: Double? = null,             // 0.0-10.0
    val ageRating:  Int? = null,             // FSK:  0, 6, 12, 16, 18, 21
    val plot:  String? = null,
    val genres: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val trailer: String? = null,
    
    // === Playback ===
    val playbackHints: Map<String, String> = emptyMap(),
)
```

### TmdbRef (Gold Decision Dec 2025)
```kotlin
data class TmdbRef(
    val type: TmdbMediaType,  // MOVIE or TV only!
    val id: Int,
)

enum class TmdbMediaType {
    MOVIE,  // → GET /movie/{id}
    TV,     // → GET /tv/{id} (for series AND episodes!)
}

// ⚠️ CRITICAL: Episodes use TV type + season/episode from metadata
// NEVER create episode-specific TmdbRef! 
```

### ExternalIds (Typed TMDB Preferred)
```kotlin
data class ExternalIds(
    val tmdb: TmdbRef? = null,                    // ✅ PREFERRED
    @Deprecated val legacyTmdbId: Int? = null,   // Migration only! 
    val imdbId: String? = null,
    val tvdbId: String? = null,
)
```

### ImageRef (Sealed Interface)
```kotlin
sealed interface ImageRef {
    data class Http(val url: String, val headers: Map<String, String> = emptyMap()) : ImageRef
    data class TelegramThumb(val remoteId: String, val chatId: Long?, val messageId: Long?) : ImageRef
    data class LocalFile(val path: String) : ImageRef
    data class InlineBytes(val bytes: ByteArray, val mimeType: String = "image/jpeg") : ImageRef
}

// ⚠️ CRITICAL:  Pipelines MUST use ImageRef, never raw URLs or TDLib DTOs!
```

### MediaType (Fine-Grained)
```kotlin
enum class MediaType {
    MOVIE,           // Feature film (> 40 min)
    SERIES,          // Series container (not playable)
    SERIES_EPISODE,  // Playable episode
    LIVE,            // Live stream
    CLIP,            // Short-form (< 40 min)
    AUDIOBOOK,
    MUSIC,
    PODCAST,
    UNKNOWN,
}
```

### TmdbResolveState (Enrichment Tracking)
```kotlin
enum class TmdbResolveState {
    UNRESOLVED,              // Not yet processed
    RESOLVED,                // Successfully resolved
    UNRESOLVABLE_PERMANENT,  // Max attempts, no match
    STALE_REFRESH_REQUIRED,  // Needs refresh
}
```

---

## ⚠️ CRITICAL RULES

### globalId MUST Remain Empty in Pipelines
```kotlin
// ✅ CORRECT: Default empty string
return RawMediaMetadata(
    originalTitle = title,
    sourceType = SourceType.TELEGRAM,
    // globalId uses default ""
)

// ❌ FORBIDDEN in pipelines
import com.fishit.player.core.metadata.FallbackCanonicalKeyGenerator  // VIOLATION!
globalId = "movie: something: 2024"  // VIOLATION!
```

### Duration ALWAYS in Milliseconds
```kotlin
// ✅ CORRECT conversions
durationMs = telegramDurationSecs * 1000L
durationMs = minutesFromApi * 60_000L

// ❌ WRONG:  Storing seconds
durationMs = durationInSeconds  // WRONG!
```

### TmdbRef:  Use TV for Episodes
```kotlin
// ✅ CORRECT: Episode uses TV type
val episodeRef = TmdbRef(TmdbMediaType. TV, seriesId)  // + season/episode from metadata

// ❌ FORBIDDEN: No EPISODE type exists!
val episodeRef = TmdbRef(TmdbMediaType. EPISODE, episodeId)  // WRONG!
```

### ImageRef: No Raw URLs
```kotlin
// ✅ CORRECT
poster = ImageRef.Http(url = posterUrl)
thumbnail = ImageRef.TelegramThumb(remoteId = remoteFileId, chatId = chatId, messageId = msgId)

// ❌ FORBIDDEN
poster = "https://example.com/poster.jpg"  // Raw string!
```

---

## 📐 Architecture Position

```
              core: model (THIS MODULE)
                   ▲
     ┌─────────────┼─────────────┐
     │             │             │
 Transport    Pipeline    Data    Playback    Player    Feature
     │             │             │              │          │
     └─────────────┴─────────────┴──────────────┴──────────┘
                            ▼
                    ALL MODULES DEPEND ON THIS
```

---

## 🔍 Pre-Change Verification

```bash
# 1. No forbidden imports
grep -rn "import okhttp3\|import androidx.room\|import io.objectbox\|import org.drinkless\|import androidx.compose\|import androidx.media3" core/model/

# 2. No source-specific fields in data classes
grep -rn "telegramChatId\|xtreamStreamId\|tdlibFileId" core/model/

# 3. No logic/behavior (only data)
grep -rn "suspend fun\|fun .*{" core/model/src/main/java/ | grep -v "fun copy\|fun equals\|fun hashCode\|fun toString\|fun component"

# All should return empty! 
```

---

## ✅ PLATIN Checklist Before PR

- [ ] Only pure data classes, enums, sealed interfaces, value classes
- [ ] Zero external dependencies (only Kotlin stdlib + serialization)
- [ ] No business logic, heuristics, or normalization
- [ ] No source-specific (Telegram/Xtream) fields
- [ ] `globalId` default is empty string
- [ ] `durationMs` in milliseconds
- [ ] `TmdbRef` uses `MOVIE` or `TV` only (no EPISODE type)
- [ ] `ExternalIds` uses typed `tmdb` field (not `legacyTmdbId`)
- [ ] All images use `ImageRef` sealed interface
- [ ] All types are serializable
- [ ] Tests are pure Kotlin (no Android runtime)

---

## 📚 Reference Documents (Priority Order)

1. `/docs/v2/MEDIA_NORMALIZATION_CONTRACT.md` - Section 1. 1, 2.1
2. `/contracts/GLOSSARY_v2_naming_and_modules.md` - Section 1.2, 1.3
3. `/contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md` - ImageRef. TelegramThumb
4. `/contracts/TMDB_ENRICHMENT_CONTRACT.md` - TmdbRef, TmdbResolveState
5. `/AGENTS.md` - Section 4.1 (Canonical Media & Normalizer)
6. `core/model/README.md` - Module-specific rules
