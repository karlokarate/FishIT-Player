# DTO Playbook (v2)

> **Status:** BINDING  
> **Scope:** All v2 modules  
> **Enforcement:** Detekt rules + CI grep gates  
> **Last Updated:** 2025-01-23

This document defines the repo-wide standard for Data Transfer Objects (DTOs) and UI Models. All agents and developers MUST follow these rules when creating or modifying data classes.

---

## 🚨 Core Principle: Gates > Schema

> "Ein Schema" ist weniger wichtig als "ein Gate".

The best DTO schema is worthless if enforcement is missing. This playbook defines both:
1. **Schema Rules** - How DTOs should be structured
2. **Quality Gates** - How violations are detected and blocked

---

## 1. DTO Categories (Strict)

### A) Transport DTO (`infra/transport-*`)

**Purpose:** 1:1 mirror of HTTP/SDK/JSON/TDLib responses

**Location:** `infra/transport-*/src/.../api/` or `infra/transport-*/src/.../dto/`

**Rules:**
- Field names match external source (use `@SerializedName` / `@Serializable` for snake_case)
- May be "ugly" (nullable, nested) but MUST be typed
- **NO logic, NO normalization, NO "fixing"**
- **NO imports from** `pipeline/*`, `feature/*`, `core/persistence`

**Naming Convention:**
```kotlin
// Pattern: *ApiDto, *TransportDto, *TdlibDto, *Response
data class XtreamVodApiDto(...)
data class TgMessageDto(...)
data class XtreamCategoryResponse(...)
```

**Canonical Example:**
```kotlin
// infra/transport-xtream/src/.../api/XtreamVodApiDto.kt
@Serializable
data class XtreamVodApiDto(
    @SerializedName("stream_id") val streamId: Long,
    @SerializedName("name") val name: String?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("rating") val rating: String?, // API returns String!
    @SerializedName("added") val added: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("tmdb_id") val tmdbId: String?,
    @SerializedName("direct_source") val directSource: String?,
)
```

---

### B) Pipeline DTO (`pipeline/*`) - INTERNAL ONLY

**Purpose:** Intermediate form to simplify Transport → RawMediaMetadata mapping

**Location:** `pipeline/*/src/.../internal/` (MUST be `internal` visibility)

**Rules:**
- **NEVER exported** (not in public API, not shared across modules)
- Used only for mapping convenience (untangle, rename, combine)
- **ALWAYS ends in `RawMediaMetadata`**
- **NO normalization** (no title cleaning, no TMDB lookups)
- **NO imports from** `infra/data-*`, `core/persistence`, `feature/*`

**Naming Convention:**
```kotlin
// Pattern: *PipelineDto (always internal)
internal data class TelegramMediaPipelineDto(...)
internal data class XtreamVodPipelineDto(...)
```

**Canonical Example:**
```kotlin
// pipeline/telegram/src/.../internal/TelegramMediaPipelineDto.kt
internal data class TelegramMediaPipelineDto(
    val messageId: Long,
    val chatId: Long,
    val fileName: String?,
    val mimeType: String?,
    val fileSize: Long?,
    val duration: Int?,
    val width: Int?,
    val height: Int?,
    val caption: String?,
    val thumbnailRemoteId: String?,
) {
    // Extension function to convert to canonical output
    fun toRawMediaMetadata(): RawMediaMetadata = RawMediaMetadata(
        sourceType = SourceType.TELEGRAM,
        sourceId = "$chatId:$messageId",
        rawTitle = fileName ?: caption ?: "Unknown",
        // ... rest of mapping
    )
}
```

---

### C) Core SSOT Models (`core/model`) - NOT DTOs!

**Purpose:** Canonical data models that are the Single Source of Truth

**Location:** `core/model/src/.../`

**These are NOT "DTOs"** - they are the sacred, stable, versioned core types:

| Type | Source | Consumer |
|------|--------|----------|
| `RawMediaMetadata` | Pipelines produce | Normalizer consumes |
| `NormalizedMedia` | Normalizer produces | Domain/Features consume |
| `ImageRef` | All layers | All layers (sealed interface) |
| `MediaType`, `SourceType` | Definition | All layers (enums) |

**Rules:**
- **Stable API** - Changes require careful versioning consideration
- **Clear separation:** Raw (pre-normalization) vs Normalized (post-normalization)
- **Pipelines produce ONLY `RawMediaMetadata`**
- **Features consume ONLY domain/normalized types, NEVER Raw**

**Canonical Example:**
```kotlin
// core/model/src/.../RawMediaMetadata.kt
data class RawMediaMetadata(
    val sourceType: SourceType,
    val sourceId: String,
    val rawTitle: String,
    val rawYear: Int? = null,
    val rawSeason: Int? = null,
    val rawEpisode: Int? = null,
    val rawPoster: ImageRef? = null,
    val rawBackdrop: ImageRef? = null,
    val rawPlot: String? = null,
    val rawRating: Double? = null,
    val rawDurationMs: Long? = null,
    val rawGenres: List<String> = emptyList(),
    val hints: Map<String, String> = emptyMap(),
)
```

---

### D) Feature UI Models (`feature/*`)

**Purpose:** Exactly what Compose needs to render - nothing more

**Location:** `feature/*/src/.../model/` or `feature/*/src/.../ui/`

**Rules:**
- **NO imports from** `pipeline/*`, `infra/transport-*`, `core/persistence`, `infra/data-*`
- **NO monster objects** - prefer 3-7 small focused models
- **Layout-friendly types:** Strings, enums, flags, `ImageRef`, IDs
- **Images MUST use `ImageRef`** - never raw URL strings
- **NO logic in models** (no methods except trivial computed properties)

**Naming Convention:**
```kotlin
// Pattern: *UiModel, *UiState, *ItemModel, *Info
data class MediaDetailUiModel(...)
data class SourceSelectionUiState(...)
data class MediaItemModel(...)
data class DetailMediaInfo(...)
```

**Canonical Example:**
```kotlin
// feature/detail/src/.../model/DetailMediaInfo.kt
data class DetailMediaInfo(
    val workKey: String,
    val title: String,
    val mediaType: String,
    val year: Int?,
    val poster: ImageRef?,      // NOT String!
    val backdrop: ImageRef?,    // NOT String!
    val plot: String?,
    val rating: Double?,
    val sources: List<DetailSourceInfo>,
) {
    // ALLOWED: trivial computed properties (no logic)
    val hasMultipleSources: Boolean get() = sources.size > 1
    val isSeries: Boolean get() = mediaType == "SERIES"
}
```

---

## 2. Universal DTO Rules

### 2.1 DTO = Pure Data

```kotlin
// ✅ CORRECT: Pure data, no methods
data class MediaItemDto(
    val id: String,
    val title: String,
    val poster: ImageRef?,
)

// ❌ FORBIDDEN: Logic in DTO
data class MediaItemDto(
    val id: String,
    val title: String,
    val poster: ImageRef?,
) {
    fun getDisplayTitle(): String = title.trim().capitalize()  // WRONG!
    fun fetchPoster(): Bitmap { ... }  // ABSOLUTELY WRONG!
}
```

### 2.2 Clean Nullability

```kotlin
// ✅ CORRECT: Nullable only when source is truly optional
data class VodDto(
    val id: Long,           // Required - non-null
    val title: String,      // Required - non-null
    val plot: String?,      // Optional from API - nullable
    val rating: Double?,    // Optional from API - nullable
)

// ❌ FORBIDDEN: Defensive nullability everywhere
data class VodDto(
    val id: Long?,          // WRONG - ID is never null
    val title: String?,     // WRONG - Title is always present
    val plot: String?,
)
```

### 2.3 Defaults Only When Semantically Correct

```kotlin
// ✅ CORRECT: Empty list when "no items" is valid state
data class CategoryDto(
    val id: String,
    val name: String,
    val items: List<ItemDto> = emptyList(),  // Empty is valid
)

// ❌ FORBIDDEN: Default hiding real null
data class CategoryDto(
    val id: String,
    val name: String = "",        // WRONG - empty string hides missing data
    val parentId: String = "",    // WRONG - should be null if no parent
)
```

### 2.4 No Helper Normalization

```kotlin
// ✅ CORRECT: Raw data, no cleaning
data class RawMediaMetadata(
    val rawTitle: String,  // "  Movie.Title.2024.1080p.WEB-DL  " - as-is!
)

// ❌ FORBIDDEN: Cleaning in DTO
data class RawMediaMetadata(
    val rawTitle: String,
) {
    val cleanTitle: String get() = rawTitle.trim().replace(".", " ")  // WRONG!
}
```

### 2.5 Explicit Provenance

```kotlin
// ✅ CORRECT: Explicit source tracking
data class RawMediaMetadata(
    val sourceType: SourceType,   // TELEGRAM, XTREAM, LOCAL
    val sourceId: String,         // Unique within source
    val sourceAccountKey: String, // Which account provided this
)

// ❌ FORBIDDEN: Magic composite IDs
data class RawMediaMetadata(
    val globalId: String,  // WRONG - opaque, unmaintainable
)
```

---

## 3. Quality Gates (MANDATORY)

### 3.1 Forbidden Imports Gate

**Rule:** Feature modules MUST NOT import from transport, pipeline, or persistence layers.

**Enforcement:** Detekt `ForbiddenImport` rule + CI grep gate

```yaml
# detekt-config.yml
ForbiddenImport:
  active: true
  imports:
    # Feature must not see Transport
    - value: 'com.fishit.player.infra.transport.*'
      reason: 'Feature modules must not import transport DTOs. Use domain models.'
    # Feature must not see Pipeline
    - value: 'com.fishit.player.pipeline.*'
      reason: 'Feature modules must not import pipeline DTOs. Use domain models.'
    # Feature must not see Persistence entities
    - value: 'com.fishit.player.core.persistence.obx.*'
      reason: 'Feature modules must not import ObjectBox entities. Use NX repositories.'
    - value: 'io.objectbox.*'
      reason: 'Feature modules must not use ObjectBox directly.'
```

### 3.2 DTO Purity Gate

**Rule:** DTOs must not have functions (only data properties).

**Enforcement:** Detekt custom rule or CI grep gate

```bash
# CI grep gate: Check for functions in DTO/UiModel files
# scripts/ci/dto-purity-check.sh
#!/bin/bash
set -e

echo "Checking DTO purity (no functions in DTOs)..."

# Find all *Dto.kt, *UiModel.kt, *Info.kt files
FILES=$(find . -path ./legacy -prune -o -name "*Dto.kt" -print -o -name "*UiModel.kt" -print -o -name "*Info.kt" -print | grep -v "/build/")

VIOLATIONS=0
for file in $FILES; do
    # Check for "fun " that's not inside a companion object
    if grep -n "^\s*fun\s\|^\s*suspend\s*fun\s" "$file" | grep -v "companion object" | grep -v "override fun equals\|override fun hashCode\|override fun toString"; then
        echo "❌ VIOLATION: Function found in DTO: $file"
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

if [ $VIOLATIONS -gt 0 ]; then
    echo "❌ Found $VIOLATIONS DTO purity violations"
    exit 1
fi

echo "✅ DTO purity check passed"
```

### 3.3 ImageRef Gate

**Rule:** Feature UI models must use `ImageRef`, never raw URL strings for images.

**Enforcement:** Code review + naming convention check

```bash
# scripts/ci/imageref-check.sh
#!/bin/bash
set -e

echo "Checking ImageRef usage in feature UI models..."

# Find UI models with String fields that look like URLs
FILES=$(find feature/ -name "*UiModel.kt" -o -name "*Info.kt" -o -name "*ItemModel.kt" | grep -v "/build/")

VIOLATIONS=0
for file in $FILES; do
    # Check for fields named *Url, *url, *URL that are String type
    if grep -En "val\s+(poster|backdrop|thumbnail|image|icon|cover)(Url|URL|url)\s*:\s*String" "$file"; then
        echo "❌ VIOLATION: URL string field found (should be ImageRef): $file"
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

if [ $VIOLATIONS -gt 0 ]; then
    echo "❌ Found $VIOLATIONS ImageRef violations"
    exit 1
fi

echo "✅ ImageRef check passed"
```

### 3.4 Layer Boundary Gate

**Rule:** Each layer may only import from layers below it.

**Enforcement:** CI grep gate (runs on every PR)

```bash
# scripts/ci/layer-boundary-check.sh
#!/bin/bash
set -e

echo "Checking layer boundaries..."

VIOLATIONS=0

# Pipeline must not import from data/persistence
if grep -rn "import.*infra\.data\|import.*core\.persistence\.obx\|import.*io\.objectbox" pipeline/ --include="*.kt" | grep -v "/build/"; then
    echo "❌ Pipeline imports from data/persistence layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Transport must not import from pipeline
if grep -rn "import.*pipeline\." infra/transport-*/ --include="*.kt" | grep -v "/build/"; then
    echo "❌ Transport imports from pipeline layer"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Feature must not import from transport/pipeline/persistence
if grep -rn "import.*infra\.transport\|import.*pipeline\.\|import.*core\.persistence\.obx\|import.*io\.objectbox" feature/ --include="*.kt" | grep -v "/build/"; then
    echo "❌ Feature imports from transport/pipeline/persistence"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

# Player must not import from transport/pipeline/data
if grep -rn "import.*infra\.transport\|import.*pipeline\.\|import.*infra\.data" player/ --include="*.kt" | grep -v "/build/"; then
    echo "❌ Player imports from transport/pipeline/data"
    VIOLATIONS=$((VIOLATIONS + 1))
fi

if [ $VIOLATIONS -gt 0 ]; then
    echo "❌ Found $VIOLATIONS layer boundary violations"
    exit 1
fi

echo "✅ Layer boundary check passed"
```

---

## 4. Naming Convention Summary

| Layer | Pattern | Example |
|-------|---------|---------|
| Transport | `*ApiDto`, `*TransportDto`, `*Response` | `XtreamVodApiDto`, `TgMessageDto` |
| Pipeline (internal) | `*PipelineDto` | `TelegramMediaPipelineDto` |
| Core Model | `RawMediaMetadata`, `NormalizedMedia`, `MediaType` | (canonical names) |
| Feature UI | `*UiModel`, `*UiState`, `*Info`, `*ItemModel` | `DetailMediaInfo`, `SourceSelectionUiState` |
| Domain | `Domain*`, `*State`, `*Result` | `DomainDetailMedia`, `DomainResumeState` |

---

## 5. Quick Reference: What Goes Where

| Data Type | Lives In | Consumed By |
|-----------|----------|-------------|
| HTTP/TDLib response | `infra/transport-*/api/` | Pipeline mappers only |
| Intermediate mapping DTO | `pipeline/*/internal/` | Pipeline only (internal) |
| `RawMediaMetadata` | Produced by Pipeline | Normalizer |
| `NormalizedMedia` | Produced by Normalizer | Domain/Repository |
| `NX_Work` (entity) | `core/persistence/` | `infra/data-nx/` repositories only |
| `DomainDetailMedia` | `core/*-domain/` | Feature via repository |
| `DetailMediaInfo` (UI) | `feature/*/model/` | ViewModels → Compose |

---

## 6. Agent Instructions

When creating or modifying DTOs:

1. **Identify the layer** - Which of the 4 categories does this belong to?
2. **Apply naming convention** - Use the correct suffix for the layer
3. **Check imports** - Ensure no forbidden imports from upper layers
4. **Verify purity** - No functions except trivial computed properties
5. **Use ImageRef** - Never raw URL strings for images in feature layer
6. **Run gates** - Execute the CI scripts before committing

**Before any DTO change, ask:**
- [ ] Is this the right layer for this type?
- [ ] Does it follow the naming convention?
- [ ] Are there any forbidden imports?
- [ ] Is it pure data (no logic)?
- [ ] Does it use `ImageRef` for images?

---

## 7. References

- `AGENTS.md` Section 4 - Layer Boundaries
- `AGENTS.md` Section 4.3.3 - NX_Work UI SSOT
- `contracts/GLOSSARY_v2_naming_and_modules.md` - Naming conventions
- `contracts/MEDIA_NORMALIZATION_CONTRACT.md` - Raw vs Normalized
- `.github/instructions/*.instructions.md` - Path-scoped rules

---

**This playbook is BINDING. Violations are bugs.**
