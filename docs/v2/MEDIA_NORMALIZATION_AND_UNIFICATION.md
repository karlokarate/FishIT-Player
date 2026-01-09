# ⚠️ DEPRECATED DOCUMENT ⚠️

> **Deprecation Date:** 2026-01-09  
> **Status:** SUPERSEDED  
> **Reason:** This document is superseded by the official binding contract.
> 
> **✅ Use This Instead:**  
> - **contracts/MEDIA_NORMALIZATION_CONTRACT.md** - Official binding contract (authoritative)  
> - **docs/v2/MEDIA_NORMALIZATION_CONTRACT.md** - Detailed implementation guide  
> - **contracts/NX_SSOT_CONTRACT.md** - NX_* entity system (Phase 0 complete)  
> - **docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md** - Migration roadmap
> 
> **Note:** The concepts described below are correct but this document duplicates the official contract. Always refer to the binding contracts in `/contracts/` for authoritative rules.

---

# ~~Media Normalization & Cross-Pipeline Unification (v2 Architecture)~~

**Purpose of this Document:**  
~~Define a single, deterministic, pipeline-agnostic metadata model and unification process so that the FishIT-Player can reliably treat media from **all pipelines** (Telegram, Xtream, IO, Audiobook, Local, Plex, etc.) as one unified library.  
This includes: canonical identity, centralized normalization, TMDB-first resolution, and agent responsibilities.~~

⚠️ **OUTDATED** - Use official contracts listed above instead.

~~This document replaces all implicit assumptions about title matching, identity heuristics, normalization strategies, and pipeline metadata formats.~~

---

# 🎯 1. Overall Goals

1. Every media work (movie, series, episode) receives a **global, pipeline-independent identity** (`CanonicalMediaId`).
2. Every pipeline provides **RawMediaMetadata only** — no normalization, no title cleaning, no heuristics.
3. All normalization happens **centrally** inside:

   ```
   :core:metadata-normalizer
   ```

4. TMDB IDs act as **absolute identity keys** when available.
5. Without TMDB IDs, titles, years, and season/episode numbers are normalized through a **shared, deterministic heuristic layer** (regex-based, scene-naming aware).
6. Features enabled by this:
   - cross-pipeline resume  
   - unified detail screen  
   - version selection across Telegram/Xtream/IO/etc.  
   - best-source selection  
   - grouping multiple versions of the same media work  
   - predictable search & categorization

---

# 📌 1.1 Telegram ID Architecture (remoteId-First)

**Contract:** `contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`

For Telegram pipeline, special rules apply to file IDs:

## Persisted (Stable Across Sessions)

- `chatId: Long` – for chat lookups and history API
- `messageId: Long` – for message reload and pagination
- `remoteId: String` – for all file references (video, thumbnail, poster)

## NOT Persisted (Volatile/Redundant)

- `fileId: Int` – session-local, becomes stale after TDLib changes
- `uniqueId: String` – no API to resolve back to file

## Resolution Flow

```
remoteId (persisted) → getRemoteFile(remoteId) → fileId (runtime) → downloadFile(fileId)
```

## Coil Image Cache

- **Memory Cache:** ENABLED (decode once, display multiple times)
- **Disk Cache:** DISABLED for Telegram (TDLib already caches files)
- **Cache Key:** `tg:${remoteId}` (stable across sessions)

---

# 🧱 2. Tools Used

## 🧩 2.1 `tmdb-java` (Apache 2.0 License)

**Purpose:**

- Find movies/series via TMDB search
- Obtain reliable `tmdbId`
- Fetch titles, release years, posters, runtime, genres

**Gradle Integration:**

```kotlin
dependencies {
    implementation("com.uwetrottmann.tmdb2:tmdb-java:<latest-version>")
}
```

---

## 🧩 2.2 Scene/Title Parser (Custom Kotlin Regex Engine)

We implement a **dedicated Kotlin-based scene/title parser**, inspired by behavior and regex patterns from Sonarr, Radarr, GuessIt, and FileBot.

We do *not* embed their code directly — only port behaviors and patterns.

This parser extracts:

- cleaned title ("x-men")
- release year
- season/episode (S01E05 / 1x05)
- edition tags (extended, directors cut, etc.)
- resolution & media source (1080p, 4K, BluRay, WebRip)

This parser lives in:

```
:core:metadata-normalizer
```

---

# 🧩 2.3 Centralized Metadata Normalizer Module

New module added:

```
:core:metadata-normalizer
```

### Types

```kotlin
data class RawMediaMetadata(
    val originalTitle: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val durationMinutes: Int? = null,
    val externalIds: ExternalIds = ExternalIds(),
    val sourceType: SourceType,
    val sourceLabel: String,
    val sourceId: String
)

data class NormalizedMediaMetadata(
    val canonicalTitle: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val tmdbId: String?,
    val externalIds: ExternalIds
)
```

### Services

```kotlin
interface MediaMetadataNormalizer {
    suspend fun normalize(raw: RawMediaMetadata): NormalizedMediaMetadata
}
```

### TMDB Resolver

```kotlin
interface TmdbMetadataResolver {
    suspend fun enrich(metadata: NormalizedMediaMetadata): NormalizedMediaMetadata
}
```

### Rules

1. **If `tmdbId` is present → it becomes the canonical identity.**
2. If not:
   - Normalize title via regex-cleaning
   - Extract year, season, episode
   - Search TMDB using normalized title & year
3. If TMDB match exists:
   - Set `tmdbId`
4. Normalized output becomes the basis for:
   - CanonicalMediaId
   - Resume unification
   - Unified detail screens
   - Version grouping

---

# 🧭 3. Pipeline Agent Responsibilities

## ❗ Mandatory for ALL pipelines

### Pipelines MUST expose

```kotlin
fun PipelineMediaItem.toRawMediaMetadata(): RawMediaMetadata
```

### Pipelines MUST NOT

- implement their own title cleaning  
- apply heuristics  
- perform TMDB searches  
- unify or match media across versions  
- decide identity between items  

### Pipelines MUST

- extract all raw metadata they can provide (title, year, season/episode, duration)
- include external IDs when available (e.g., tmdbId from Xtream or Plex)
- populate `sourceType`, `sourceLabel`, `sourceId`
- ensure deterministic mappings

### Pipeline Tests MUST

- verify RawMediaMetadata generation  
- NOT test normalization, TMDB resolution, or canonical identity  
(those belong to the normalizer + unifier modules)

---

# 🏗 4. Required Changes to Existing Markdown Documents

## 📌 Update: `APP_VISION_AND_SCOPE.md`

Add new subsection:

- “Cross-Pipeline Media Identity Layer”
- Pipelines deliver raw metadata only
- TMDB-first canonical identity
- Normalization + unification handled centrally

---

## 📌 Update: `ARCHITECTURE_OVERVIEW_V2.md`

Add:

- new module `:core:metadata-normalizer`
- new canonical media flow:

  ```
  Pipeline → RawMediaMetadata → Normalizer → CanonicalMedia → Resume/UI
  ```

---

## 📌 Update: `IMPLEMENTATION_PHASES_V2.md`

Add new subtasks:

### Phase 3 — Metadata Normalization Core

- Introduce Raw/Normalized metadata types
- Implement MediaMetadataNormalizer
- Implement TmdbMetadataResolver
- Add metadata-normalizer module

### Phase 4 — Pipeline Metadata Integration

- Telegram: toRawMediaMetadata()
- Xtream: toRawMediaMetadata()
- IO: toRawMediaMetadata()
- Audiobook: toRawMediaMetadata()

### Phase 5 — Canonical Media Storage

- Store CanonicalMedia + MediaSourceRef in persistence
- Implement cross-pipeline resume + detail screen backend

---

## 📌 Update: `PHASE2_PARALLELIZATION_PLAN.md`

Add:

- Pipeline agents must prepare `toRawMediaMetadata()` in their stub phase
- Normalization begins only after Phase 3 is implemented
- Pipelines remain fully functional without the normalizer → preserves reusability

---

# 🤖 5. How Pipeline Agents Must Adjust Their Behavior

Each agent must:

### a) Implement this function

```kotlin
fun PipelineMediaItem.toRawMediaMetadata(): RawMediaMetadata
```

### b) Follow these constraints

- No cleaning logic  
- No regex heuristics  
- No TMDB lookup  
- No identity merging  

### c) Provide complete raw data

- best-effort extraction of titles, years, season/episode, duration  
- external IDs when the source provides them  
- stable source identifiers

### d) Tests focus ONLY on

- Raw metadata production  
NOT on:
- normalization  
- TMDB behavior  
- canonical identity  

---

# 🚀 6. Benefits for FishIT-Player

Once this system is active, the app gains:

- unified detail pages  
- pipeline-independent resume  
- version selection (quality, language, subtitles)  
- clean global search  
- grouping all versions of a movie or episode across all pipelines  
- painless addition of new pipelines (Plex, Jellyfin, more Xtream providers)

Pipelines remain fully self-contained and reusable — the intelligence lives in a clean, optional domain layer.

---

# ~~🟢 7. Status After Introducing This Document~~

~~After this document is committed:~~

~~- All future pipeline tasks MUST implement `toRawMediaMetadata()`.~~
~~- Normalization logic is strictly centralized.~~
~~- TMDB-first identity becomes standard.~~
~~- Cross-pipeline canonical identity is enabled for later phases.~~

---

# ⚠️ END OF DEPRECATED DOCUMENT ⚠️

> **This document is outdated and superseded by official binding contracts.**
> 
> **Use These Documents Instead:**
> 
> 1. **contracts/MEDIA_NORMALIZATION_CONTRACT.md** - **AUTHORITATIVE** binding contract
> 2. **docs/v2/MEDIA_NORMALIZATION_CONTRACT.md** - Detailed implementation guide
> 3. **contracts/NX_SSOT_CONTRACT.md** - NX_* entity system (Phase 0 complete)
> 4. **contracts/GLOSSARY_v2_naming_and_modules.md** - Naming conventions
> 
> **Why Use Contracts Instead:**
> - Binding rules (not suggestions)
> - Maintained and version-controlled
> - Referenced by CI/CD guardrails
> - Single source of truth for team
> 
> **Migration Status:** The normalization system described here is implemented. The new NX_* entity system (Issue #621) adds audit trails, multi-account support, and percentage-based resume on top of this foundation.
