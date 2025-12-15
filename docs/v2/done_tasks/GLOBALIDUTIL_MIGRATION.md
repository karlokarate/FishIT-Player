# GlobalIdUtil to CanonicalIdUtil Migration

**Date:** 2025-12-15  
**Status:** ✅ Complete  
**Related Contracts:** `MEDIA_NORMALIZATION_CONTRACT.md`, `GLOSSARY_v2_naming_and_modules.md`

## Overview

Moved canonical ID generation logic out of `core:model` into `core:metadata-normalizer` to enforce the architectural principle that the model layer must remain a pure contract/types module with zero normalization logic.

## Problem Statement

`GlobalIdUtil` in `core:model` violated multiple architectural principles:

1. **Model layer contamination**: `core:model` must be pure contracts, but `GlobalIdUtil` contained normalization heuristics (scene tag stripping)
2. **Pipeline access**: Pipelines could import `GlobalIdUtil` and bypass the normalizer, violating `MEDIA_NORMALIZATION_CONTRACT.md`
3. **Normalization duplication**: Scene tag stripping logic duplicated metadata-normalizer behavior

## Solution

### 1. Created `CanonicalIdUtil` in `core:metadata-normalizer`

**Location:** `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/CanonicalIdUtil.kt`

**Key features:**
- TMDB ID takes priority when available
- Handles season-only metadata (for series-level)
- Handles episode-level metadata (season + episode)
- SHA-256 hashing with 16-character hex prefix (`cm:<16hex>`)
- **Removed scene tag stripping** - normalization delegated to dedicated components

**Algorithm:**
```kotlin
if (tmdbId != null) {
    baseString = "tmdb:{id}|S{season}E{episode}"
} else {
    baseString = "{canonicalTitle}|{year}|S{season}E{episode}"
}
canonicalId = "cm:" + sha256(baseString).take(16)
```

### 2. Deprecated `GlobalIdUtil` in `core:model`

**Location:** `core/model/src/main/java/com/fishit/player/core/model/GlobalIdUtil.kt`

**Changes:**
- Replaced implementation with error-level deprecation stub
- Provides clear compiler errors with migration guidance
- Prevents any new usage while maintaining API surface

**Deprecation message:**
```kotlin
@Deprecated(
    message = "Do not use GlobalIdUtil. Use CanonicalIdUtil in core:metadata-normalizer instead. Pipelines must NOT generate canonical IDs.",
    level = DeprecationLevel.ERROR
)
```

### 3. Updated `Normalizer.kt`

**Location:** `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/Normalizer.kt`

**Changes:**
- Removed `GlobalIdUtil` import
- Uses `CanonicalIdUtil.canonicalHashId()` instead
- Added basic `normalizeTitle()` helper (lowercase, trim, collapse spaces)
- Scene tag stripping removed

**Expected behavior:**
- Pipelines leave `globalId` empty
- Normalizer generates canonical IDs on-demand during normalization
- TMDB IDs take priority for matching

### 4. Updated Pipeline Modules

**Telegram:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramRawMetadataExtensions.kt`
- Removed `GlobalIdUtil` import
- Sets `globalId = ""` in `toRawMediaMetadata()`

**Xtream:** `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/mapper/XtreamRawMetadataExtensions.kt`
- Removed `GlobalIdUtil` import
- Sets `globalId = ""` in all `toRawMediaMetadata()` functions
- Removed `canonicalIdWithFallback()` helper

### 5. Added CI Guardrail

**Location:** `scripts/ci/check-canonical-id-isolation.sh`

**Enforces:**
1. No `GlobalIdUtil` imports outside `core:metadata-normalizer`
2. No `CanonicalIdUtil` imports outside `core:metadata-normalizer`
3. No calls to `generateCanonicalId()` or `canonicalHashId()` outside normalizer
4. Pipelines leave `globalId` empty

**Violations result in:**
- Build failure
- Actionable error messages
- References to relevant contracts

## Architecture Compliance

✅ **Per `MEDIA_NORMALIZATION_CONTRACT.md`:**
- Pipelines provide raw metadata only (no normalization, no ID generation)
- Only the normalizer may generate canonical IDs
- TMDB IDs from upstream sources pass through without modification

✅ **Per `GLOSSARY_v2_naming_and_modules.md`:**
- Model layer is pure contracts (no heuristics, no business logic)
- Normalizer is sole source of canonical identity
- Layer boundaries enforced via CI

✅ **Per `AGENTS.md` Section 4.5:**
- Pipeline layer does not import from data/normalizer layers
- Layer hierarchy respected: UI → Domain → Data → Pipeline → Transport → core:model

## Testing

- ✅ `core:metadata-normalizer` compiles
- ✅ `pipeline:telegram` compiles
- ✅ `pipeline:xtream` compiles
- ✅ Architecture guardrails pass
- ✅ New canonical ID isolation check passes
- ✅ No CodeQL vulnerabilities detected

## Migration Path for Future Code

**If you need canonical ID generation:**
1. Check if you're in `core:metadata-normalizer` → Use `CanonicalIdUtil.canonicalHashId()`
2. If you're in pipeline code → **DO NOT** generate IDs. Leave `globalId = ""`
3. If you're in data/UI code → Use the `globalId` from normalized metadata

**Contract violation examples:**
```kotlin
// ❌ FORBIDDEN in pipeline code
import com.fishit.player.core.model.GlobalIdUtil
globalId = GlobalIdUtil.generateCanonicalId(title, year)

// ❌ FORBIDDEN in pipeline code
import com.fishit.player.core.metadata.CanonicalIdUtil
globalId = CanonicalIdUtil.canonicalHashId(title, year)

// ✅ CORRECT in pipeline code
globalId = "" // Let normalizer generate it

// ✅ CORRECT in normalizer code
import com.fishit.player.core.metadata.CanonicalIdUtil
val id = CanonicalIdUtil.canonicalHashId(
    canonicalTitle = normalizedTitle,
    year = year,
    season = season,
    episode = episode,
    tmdbId = tmdbId
)
```

## Files Changed

1. `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/CanonicalIdUtil.kt` - **NEW**
2. `core/model/src/main/java/com/fishit/player/core/model/GlobalIdUtil.kt` - Deprecated stub
3. `core/model/src/main/java/com/fishit/player/core/model/RawMediaMetadata.kt` - Updated doc
4. `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/Normalizer.kt` - Uses CanonicalIdUtil
5. `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/model/TelegramRawMetadataExtensions.kt` - Removed GlobalIdUtil
6. `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/mapper/XtreamRawMetadataExtensions.kt` - Removed GlobalIdUtil
7. `scripts/ci/check-canonical-id-isolation.sh` - **NEW** CI guardrail
8. `docs/v2/NAMING_INVENTORY_v2.md` - Updated inventory

## References

- **Contracts:** `contracts/MEDIA_NORMALIZATION_CONTRACT.md`, `contracts/GLOSSARY_v2_naming_and_modules.md`
- **Architecture:** `AGENTS.md` Section 4.5 (Layer Boundary Enforcement)
- **CI:** `.github/workflows/v2-arch-gates.yml` (will include new guardrail)
