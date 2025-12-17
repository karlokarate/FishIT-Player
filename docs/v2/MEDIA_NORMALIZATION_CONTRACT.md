
# MEDIA_NORMALIZATION_CONTRACT.md

This contract defines the **formal rules, data shapes, and responsibilities** for media metadata normalization and cross-pipeline unification in the FishIT-Player v2 architecture.

This file is the **single authoritative copy** of the media normalization contract. No other `MEDIA_NORMALIZATION_CONTRACT.md` files may exist outside of `docs/v2/`.

It is **binding** for:
- all pipeline modules (Telegram, Xtream, IO, Audiobook, Local, Plex, etc.),
- the `:core:metadata-normalizer` module,
- and any domain services that consume canonical media identity.

This contract is implementation-agnostic: it describes **what** must happen, not **how** it is internally implemented.

---

## 1. Core Concepts

### 1.1 RawMediaMetadata

**Definition:**

```kotlin
data class RawMediaMetadata(
    val originalTitle: String,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val durationMinutes: Int? = null,
    val externalIds: ExternalIds = ExternalIds(),
    val sourceType: SourceType,
    val sourceLabel: String,
    val sourceId: String,
    val pipelineIdTag: PipelineIdTag = PipelineIdTag.UNKNOWN,
    val globalId: String = "",  // MUST remain empty in pipelines; assigned centrally
    val poster: ImageRef? = null,
    val backdrop: ImageRef? = null,
    val thumbnail: ImageRef? = null,
    val placeholderThumbnail: ImageRef? = null,
)
```

**Semantics:**

- `originalTitle`  
  - Human-readable title as provided by the source (file name, API title, caption, etc.).
  - Must **not** be pre-cleaned (no stripping of edition tags, groups, or resolution tags).
- `year`  
  - Release year (movie) or original air date year (episode), if available.
- `season` / `episode`  
  - For series/episodes only; null for movies.
- `durationMinutes`  
  - Media runtime in minutes, if available.
- `externalIds`  
  - IDs provided by upstream sources (e.g., TMDB, IMDB, TVDB) without transformation.
- `sourceType`  
  - Enum-like value identifying the pipeline: `TELEGRAM`, `XTREAM`, `IO`, `AUDIOBOOK`, `PLEX`, `LOCAL`, etc.
- `sourceLabel`  
  - Human-readable label for UI (e.g., "Telegram Chat: X-Men Group", "Xtream Server: Provider A").
- `sourceId`  
  - Stable, unique identifier within the pipeline for this item (e.g., remoteId, contentId, URL, file path).

**Contract:**

- Pipelines must **never** perform their own title normalization or heuristics.
- Pipelines must provide **best-effort** population of all non-nullable fields.
- Pipelines must not guess TMDB/IMDB IDs; they may only pass through IDs provided by the source.

---

### 1.2 NormalizedMediaMetadata

**Definition:**

```kotlin
data class NormalizedMediaMetadata(
    val canonicalTitle: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val tmdbId: String?,
    val externalIds: ExternalIds
)
```

**Semantics:**

- `canonicalTitle`
  - Cleaned, normalized title used as part of canonical identity.
  - Deterministic for the same input (same raw → same normalized).
- `year`, `season`, `episode`
  - Possibly refined versions of the raw values after normalization and TMDB enrichment.
- `tmdbId`
  - Primary external ID used for canonical identity when present.
  - Must originate from the TMDB resolver (or a trusted upstream source like Xtream/Plex).
- `externalIds`
  - Aggregated external IDs (TMDB, IMDB, TVDB, etc.).

**Contract:**

- Only the **normalizer** and/or **TMDB resolver** may populate or change `canonicalTitle`, `tmdbId`, and refined `year/season/episode`.
- Pipelines must treat `NormalizedMediaMetadata` as **read-only**.

---

### 1.3 CanonicalMediaId (Conceptual)

The actual type may live in `:core:model`, but conceptually:

```kotlin
data class CanonicalMediaId(
    val kind: MediaKind,      // MOVIE or EPISODE
    val key: String           // unique key, e.g. "tmdb:12345" or "movie:x-men:2000"
)
```

**Identity Rules (ordered by priority):**

1. If `tmdbId != null`:
   - `CanonicalMediaId.key = "tmdb:<tmdbId>"`
   - `CanonicalMediaId.kind` is determined from TMDB metadata (movie/episode).
2. If no `tmdbId`, but normalized title + year (+ S/E for episodes) are known:
   - Movies:
     - `CanonicalMediaId.key = "movie:<canonicalTitle>:<year>"`
   - Episodes:
     - `CanonicalMediaId.key = "episode:<canonicalTitle>:S<season>E<episode>"`
3. If neither of the above is available, the item **cannot** be assigned a stable `CanonicalMediaId` and must be treated as unlinked for canonical features.

> **Implementation note (v2):** The concrete ID wrappers live in
> `core/model/src/main/java/com/fishit/player/core/model/ids/` as Kotlin value classes
> (`CanonicalId`, `PipelineItemId`, `RemoteId`, `TmdbId`). These wrappers preserve the existing
> String/Int storage formats while preventing accidental ID mix-ups at compile time. Xtream
> `mediaType` is taken directly from the endpoint (no inference), and LIVE entries are treated as
> unlinked (no canonical id generation).

---

## 2. Module Responsibilities

### 2.1 Pipelines (Telegram, Xtream, IO, Audiobook, etc.)

Each pipeline must provide:

```kotlin
fun PipelineMediaItem.toRawMediaMetadata(): RawMediaMetadata
```

**Pipelines MUST:**

- Fill all applicable fields from its own domain model.
- Pass through external IDs if the upstream source exposes them (e.g., Xtream/Plex providing TMDB IDs).
- Keep `originalTitle` as close to the source as possible (no subjective edits).

**Pipelines MUST NOT:**

- Normalize titles.
- Strip edition/resolution tags.
- Conduct TMDB or other external database searches.
- Attempt to group/merge media items into canonical works.
- Decide cross-pipeline identity.
- **Compute or assign `globalId`** – this field MUST remain empty (`""`). Canonical identity is computed centrally by `:core:metadata-normalizer`.
- Import or use `FallbackCanonicalKeyGenerator` – this utility is exclusively for the normalizer layer.

### 2.1.1 GlobalId Isolation (HARD RULE)

> **Pipeline Contract:** Pipelines MUST leave `RawMediaMetadata.globalId` empty (`""`).
> Canonical identity computation is the exclusive responsibility of `:core:metadata-normalizer`.

**Rationale:**
- Cross-pipeline deduplication requires cleaned/normalized titles.
- Pipelines emit RAW data (scene tags, edition markers, etc.) that must be processed first.
- Computing globalId before normalization would create inconsistent/duplicate canonical IDs.

**Enforcement:**
- CI guardrails (`FALLBACK_CANONICAL_KEY_GUARD`) fail the build if any file outside `core/metadata-normalizer/` imports or uses `FallbackCanonicalKeyGenerator`.
- CI guardrails (`PIPELINE_GLOBALID_ASSIGNMENT_GUARD`) fail the build if any `pipeline/**/*.kt` file assigns to `globalId` (except empty string).

**Allowed in Pipelines:**
```kotlin
// ✅ CORRECT: Leave globalId with default empty value (omit or don't specify)
return RawMediaMetadata(
    originalTitle = rawTitle,
    // ... other fields ...
    // globalId not specified - uses default ""
)
```

**Forbidden in Pipelines:**
```kotlin
// ❌ VIOLATION: Pipeline computing globalId
import com.fishit.player.core.metadata.FallbackCanonicalKeyGenerator
import com.fishit.player.core.model.MediaType

return RawMediaMetadata(
    originalTitle = rawTitle,
    globalId =
            FallbackCanonicalKeyGenerator.generateFallbackCanonicalId(
                    originalTitle = rawTitle,
                    year = year,
                    season = null,
                    episode = null,
                    mediaType = MediaType.MOVIE,
            ), // FORBIDDEN
)
```

Pipelines remain fully functional without the normalizer and unifier; they can still:
- list items,
- start playback using their own `PlaybackContext`,
- provide per-pipeline details.

---

### 2.2 Metadata Normalizer (`:core:metadata-normalizer`)

Provides:

```kotlin
interface MediaMetadataNormalizer {
    suspend fun normalize(raw: RawMediaMetadata): NormalizedMediaMetadata
}
```

**Responsibilities:**

1. **Title Normalization:**
   - Strip technical tags (resolution, codec, release group, source tags).
   - Normalize whitespace, case, punctuation.
   - Remove edition tags or track them separately.
2. **Structural Parsing:**
   - Extract year, season, episode if missing but inferable from naming conventions (scene-style).
3. **Determinism:**
   - For the same `RawMediaMetadata`, the function must produce the same `NormalizedMediaMetadata`.

The Metadata Normalizer must not:
- Hit external services (TMDB, TVDB, etc.) directly.

---

### 2.3 TMDB Resolver

Provides:

```kotlin
interface TmdbMetadataResolver {
    suspend fun enrich(metadata: NormalizedMediaMetadata): NormalizedMediaMetadata
}
```

**Responsibilities:**

- Use TMDB search APIs to:
  - resolve ambiguous titles,
  - correct years,
  - assign `tmdbId` when possible,
  - fill in external IDs and refined metadata (e.g. official title, original title, release date, season data).
- Follow API limits and caching policies defined elsewhere.

**Priority Rules:**

1. If `metadata.externalIds.tmdbId` already exists:
   - Validate or refresh, do NOT silently overwrite with a different ID unless the previous is clearly invalid.
2. If no `tmdbId`, but `canonicalTitle` and year (and possibly season/episode) are present:
   - Perform TMDB search and pick the best match (clear, deterministic criteria).
3. On ambiguous matches:
   - The resolver must either:
     - return without setting a `tmdbId`, or
     - log and mark the item for manual review (depending on configuration).

---

### 2.4 Canonical Media Storage (Persistence Layer)

Canonical media and source references are stored and served from `:core:persistence` via dedicated repository interfaces, for example:

```kotlin
interface CanonicalMediaRepository {
    suspend fun upsertCanonicalMedia(normalized: NormalizedMediaMetadata): CanonicalMediaId
    suspend fun addOrUpdateSourceRef(canonicalId: CanonicalMediaId, source: MediaSourceRef)
    suspend fun findByCanonicalId(id: CanonicalMediaId): CanonicalMediaWithSources?
}
```

**Contract:**

- Persistence layer must not re-implement normalization or TMDB logic.
- It stores canonical representation and all linked source references.
- It must guarantee that a given `CanonicalMediaId` is unique and stable.

---

## 3. Processing Flow

### 3.1 Single Item Registration Flow

1. Pipeline produces a `PipelineMediaItem`.
2. Pipeline adapter calls:
   ```kotlin
   val raw = pipelineItem.toRawMediaMetadata()
   ```
3. Normalizer processes:
   ```kotlin
   val normalized = mediaMetadataNormalizer.normalize(raw)
   ```
4. TMDB resolver enriches (optionally / asynchronously):
   ```kotlin
   val enriched = tmdbMetadataResolver.enrich(normalized)
   ```
5. Canonical media is persisted:
   ```kotlin
   val canonicalId = canonicalMediaRepository.upsertCanonicalMedia(enriched)
   canonicalMediaRepository.addOrUpdateSourceRef(canonicalId, sourceRefFrom(raw, enriched))
   ```

### 3.2 Constraints

- Normalization + TMDB enrichment may be:
  - synchronous for small data sets, or
  - batched/async for large libraries,
  but the contract of the functions stays the same.

- Pipelines may operate independently without waiting for normalization; canonical features may simply not be available yet for that item.

---

## 4. Agent Compliance Checklist

Any new pipeline or pipeline refactor MUST satisfy:

1. `toRawMediaMetadata()` implemented and tested.
2. No title-cleaning or cross-media matching logic in pipeline code.
3. No TMDB/IMDB/TVDB calls inside pipeline modules.
4. Tests exist that:
   - validate correct `RawMediaMetadata` mapping,
   - do NOT verify normalization or TMDB behavior.

Any new normalizer or TMDB-related change MUST ensure:

1. Deterministic normalization for equal inputs.
2. Clear, documented rules for ambiguous TMDB matches.
3. No direct dependence on pipeline-specific classes.

---

## 5. Backwards Compatibility & Migration

- Existing v2 pipelines may initially continue to function without using the metadata normalizer.
- Once this contract is in place:
  - new features relying on canonical identity (cross-pipeline resume, unified detail pages, global search) must use `RawMediaMetadata → NormalizedMediaMetadata → CanonicalMediaId`.
- Legacy v1 code and metadata formats are not required to adhere to this contract, but any v1 → v2 migration logic must produce `RawMediaMetadata` that follows these rules.

---

## 6. Violations

Any of the following is considered a **contract violation**:

- Pipeline code performing title normalization or TMDB lookups.
- Direct computation of `CanonicalMediaId` in a pipeline.
- Pipeline sets `RawMediaMetadata.globalId` or calls any canonical-id generator (FallbackCanonicalKeyGenerator/generateFallbackCanonicalId).
- Persistence layer changing normalization or TMDB identity rules.
- Tests in pipeline modules that assert normalized titles or TMDB-dependent behavior.

Violations must be refactored so that all non-raw responsibilities move into:
- `:core:metadata-normalizer`
- TMDB resolver implementation
- canonical media repository / domain services.

---

This contract is the authoritative specification for media normalization and unification in the v2 architecture.  
All future work on pipelines, normalization, and canonical media must comply with these rules.
