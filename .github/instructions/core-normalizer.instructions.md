---
applyTo:
  - core/metadata-normalizer/**
---

# 🏆 PLATIN Instructions: core/metadata-normalizer

> **PLATIN STANDARD** - The ONLY place for normalization logic. 
>
> **Purpose:** Normalizes `RawMediaMetadata` into enriched domain models via heuristics and TMDB lookups.
> This module OWNS all title cleanup, scene parsing, and canonical identity generation.

---

## 🔴 ABSOLUTE HARD RULES

### 1. This Module OWNS Normalization
```kotlin
// ✅ ONLY ALLOWED HERE
fun normalizeTitle(raw: String): String
fun extractSeasonEpisode(title: String): Pair<Int?, Int?>? 
fun generateGlobalId(metadata: RawMediaMetadata): String
class FallbackCanonicalKeyGenerator { ... }
class SceneNameParser { ... }
interface TmdbMetadataResolver { ... }
```

### 2. Deterministic Normalization
```kotlin
// Same input MUST produce same output - ALWAYS
val result1 = normalizer.normalize(rawMetadata)
val result2 = normalizer.normalize(rawMetadata)
assert(result1 == result2)  // Must always be true
```

### 3. No Pipeline Dependencies
```kotlin
// ✅ ALLOWED
import com.fishit.player.core.model.RawMediaMetadata
import com.fishit.player.core.model.NormalizedMediaMetadata

// ❌ FORBIDDEN
import com.fishit.player.pipeline.telegram.*      // Pipeline
import com.fishit.player.pipeline.xtream.*        // Pipeline
import TelegramMediaItem, XtreamVodItem           // Pipeline DTOs
import com.fishit.player.infra.transport.*        // Transport
import org.drinkless.td.*                          // TDLib
```

### 4. No Direct Network Calls
```kotlin
// ❌ FORBIDDEN
val response = okHttpClient.newCall(request).execute()

// ✅ CORRECT:  Use injected providers
interface TmdbMetadataResolver {
    suspend fun enrich(metadata: NormalizedMediaMetadata): NormalizedMediaMetadata
}
// Provider implementation lives in infra layer
```

---

## 📋 Module Responsibilities

| Task | This Module | Elsewhere |
|------|:-----------:|:---------:|
| Title normalization | ✅ | ❌ |
| Strip technical tags (resolution, codec, group) | ✅ | ❌ |
| Extract year/season/episode from titles | ✅ | ❌ |
| Scene release name parsing | ✅ | ❌ |
| Compute globalId/CanonicalId | ✅ | ❌ |
| TMDB search and enrichment | ✅ | ❌ |
| Adult/Family content heuristics | ✅ | ❌ |
| Language/version detection | ✅ | ❌ |
| Produce `NormalizedMediaMetadata` | ✅ | ❌ |
| Raw HTTP calls | ❌ | infra |
| Store results to DB | ❌ | data layer |

---

## 📋 Key Interfaces

### MediaMetadataNormalizer
```kotlin
interface MediaMetadataNormalizer {
    /**
     * Normalize raw pipeline metadata. 
     * MUST be deterministic - same input = same output.
     */
    suspend fun normalize(raw: RawMediaMetadata): NormalizedMediaMetadata
}
```

### TmdbMetadataResolver
```kotlin
interface TmdbMetadataResolver {
    /**
     * Enrich normalized metadata with TMDB data.
     * May return unchanged if no match found.
     */
    suspend fun enrich(metadata: NormalizedMediaMetadata): NormalizedMediaMetadata
}
```

### FallbackCanonicalKeyGenerator
```kotlin
object FallbackCanonicalKeyGenerator {
    /**
     * Generate canonical key when no TMDB ID available.
     * Format: "movie:<title>[:<year>]" or "episode:<title>:SxxExx"
     */
    fun generateFallbackCanonicalId(
        originalTitle: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        mediaType: MediaType,
    ): String
}

// ⚠️ This class MUST NOT be imported by pipeline modules! 
// CI guard:  FALLBACK_CANONICAL_KEY_GUARD
```

---

## ⚠️ CI Guard Enforcement

```bash
# FALLBACK_CANONICAL_KEY_GUARD - Fails if imported outside normalizer
grep -rn "FallbackCanonicalKeyGenerator" pipeline/ infra/ feature/ player/ playback/

# PIPELINE_GLOBALID_ASSIGNMENT_GUARD - Fails if pipelines assign globalId
grep -rn "globalId\s*=" pipeline/ | grep -v '""'

# Both must return empty! 
```

---

## 📐 Architecture Position

```
Pipeline ──► core:metadata-normalizer ──► Data
                      ▲
                 YOU ARE HERE

     ┌─────────────────────────────────────┐
     │  Receives: RawMediaMetadata         │
     │  Produces: NormalizedMediaMetadata  │
     │  May call: TmdbMetadataResolver     │
     └─────────────────────────────────────┘
```

---

## ✅ PLATIN Checklist

- [ ] All normalization logic lives here (no exceptions)
- [ ] Deterministic: same input → same output
- [ ] No pipeline imports (`TelegramMediaItem`, `XtreamVodItem`)
- [ ] No transport imports (TDLib, OkHttp)
- [ ] No direct network calls (use provider interfaces)
- [ ] No database writes (data layer responsibility)
- [ ] FallbackCanonicalKeyGenerator not exposed to pipelines
- [ ] All title cleanup uses SceneNameParser
- [ ] TMDB enrichment via TmdbMetadataResolver interface

---

## 📚 Reference Documents

1. `/docs/v2/MEDIA_NORMALIZATION_CONTRACT.md` - Section 2.2, 2.3 (AUTHORITATIVE)
2. `/docs/v2/TMDB_ENRICHMENT_CONTRACT.md` - TMDB resolution rules (AUTHORITATIVE)
3. `/AGENTS.md` - Section 4.1 (Canonical Media & Normalizer)
4. `core/metadata-normalizer/README.md`
