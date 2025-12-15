# Frozen Module Manifest - Implementation Summary

**Date:** 2025-12-15  
**Status:** ✅ Implemented and Enforced  
**PR:** This is the one-time stub PR that adds reserved modules and freezes the manifest

---

## Overview

This document summarizes the implementation of the Frozen Module Manifest system as specified in `docs/v2/FROZEN_MODULE_MANIFEST.md`. After this PR merges, **NO new modules** may be added to the project.

## What Was Created

### 1. Three Reserved Stub Modules

#### `:infra:imaging`
- **Path:** `infra/imaging/`
- **Purpose:** Global Coil/ImageLoader/OkHttp cache + provisioning (source-agnostic)
- **Status:** Empty DI module with TODOs
- **Contract:** Transport layers MUST NOT own Coil/ImageLoader configuration

#### `:infra:work`
- **Path:** `infra/work/`
- **Purpose:** WorkManager scheduling/orchestration (catalog sync, background fetch)
- **Status:** Empty scheduler API with TODOs
- **Contract:** Consumes `core:catalog-sync` contracts; app-v2 only triggers via interfaces

#### `:player:ui-api`
- **Path:** `player/ui-api/`
- **Purpose:** UI-facing interfaces/contracts only (no implementation, no screens)
- **Status:** Interface placeholder with TODOs
- **Contract:** NO Compose, NO Hilt, interfaces only

### 2. Frozen Manifest Enforcement Script

**File:** `scripts/ci/check-frozen-manifest.sh`

**Checks:**
1. ✅ All `build.gradle.kts` files are in the frozen module list
2. ✅ `settings.gradle.kts` changes are limited to the one-time stub PR
3. ✅ All `include()` statements match the frozen manifest

**Behavior:**
- Allows exactly 3 module additions: `:infra:imaging`, `:infra:work`, `:player:ui-api`
- After this PR merges, ANY change to `settings.gradle.kts` will fail CI
- Any new `build.gradle.kts` outside the frozen list will fail CI

### 3. Integration with Architecture Guardrails

**File:** `scripts/ci/check-arch-guardrails.sh`

The main architecture guardrails script now calls `check-frozen-manifest.sh` as part of its checks. Both must pass for CI to succeed.

---

## Module Manifest (Final)

Total modules: **42** (frozen after this PR)

### App (1)
- `:app-v2`

### Core (11)
- `:core:model`
- `:core:player-model`
- `:core:feature-api`
- `:core:persistence`
- `:core:metadata-normalizer`
- `:core:catalog-sync`
- `:core:firebase`
- `:core:ui-imaging`
- `:core:ui-theme`
- `:core:ui-layout`
- `:core:app-startup`

### Playback & Player (7 + 1 new = 8)
- `:playback:domain`
- `:playback:telegram`
- `:playback:xtream`
- `:player:ui`
- `:player:ui-api` ⭐ NEW (reserved)
- `:player:internal`
- `:player:miniplayer`
- `:player:nextlib-codecs`

### Pipelines (4)
- `:pipeline:telegram`
- `:pipeline:xtream`
- `:pipeline:io`
- `:pipeline:audiobook`

### Features (8)
- `:feature:home`
- `:feature:library`
- `:feature:live`
- `:feature:detail`
- `:feature:telegram-media`
- `:feature:audiobooks`
- `:feature:settings`
- `:feature:onboarding`

### Infrastructure (7 + 2 new = 9)
- `:infra:logging`
- `:infra:tooling`
- `:infra:transport-telegram`
- `:infra:transport-xtream`
- `:infra:data-telegram`
- `:infra:data-xtream`
- `:infra:data-home`
- `:infra:imaging` ⭐ NEW (reserved)
- `:infra:work` ⭐ NEW (reserved)

### Tools (1)
- `:tools:pipeline-cli`

---

## CI Enforcement

### Existing Workflows

The frozen manifest check is integrated into:
- `.github/workflows/v2-arch-gates.yml` (via `check-arch-guardrails.sh`)

### Failure Scenarios

CI will **FAIL** if:
1. A new `build.gradle.kts` appears outside the frozen module paths
2. `settings.gradle.kts` is modified after this PR (except version bumps)
3. A new `include()` statement is added to `settings.gradle.kts`
4. An `include()` statement references a module not in the frozen list

### Bypass Protection

There is **NO allowlist** for module manifest violations. The freeze is absolute and cannot be bypassed.

---

## Testing Performed

### ✅ Module Compilation
```bash
./gradlew :infra:imaging:assemble
./gradlew :infra:work:assemble
./gradlew :player:ui-api:assemble
```
**Result:** All modules compile successfully

### ✅ Code Formatting
```bash
./gradlew :infra:imaging:ktlintFormat
./gradlew :infra:work:ktlintFormat
./gradlew :player:ui-api:ktlintFormat
```
**Result:** All modules pass ktlint

### ✅ Frozen Manifest Check
```bash
bash scripts/ci/check-frozen-manifest.sh
```
**Result:** ✅ Module manifest frozen - no violations

### ✅ Architecture Guardrails
```bash
bash scripts/ci/check-arch-guardrails.sh
```
**Result:** Frozen manifest check passes (2 pre-existing player:ui violations unrelated to this PR)

### ✅ Unauthorized Module Detection
Created `infra/unauthorized/build.gradle.kts` as a test:
```bash
bash scripts/ci/check-frozen-manifest.sh
```
**Result:** ❌ VIOLATION: Unauthorized module detected: infra/unauthorized

---

## Post-Merge Behavior

After this PR merges to `architecture/v2-bootstrap`:

### ✅ Allowed
- Filling in TODOs in the three reserved modules
- Adding code to existing modules
- Refactoring within module boundaries
- Version bumps in `settings.gradle.kts` pluginManagement block

### ❌ Forbidden
- Creating new module directories
- Adding new `build.gradle.kts` files
- Adding new `include()` statements to `settings.gradle.kts`
- Modifying the module structure in any way

---

## Next Steps

1. **Immediate:** Merge this PR to freeze the module manifest
2. **Short-term:** Fill TODOs in reserved modules as needed:
   - Implement `infra:imaging` when Coil integration is ready
   - Implement `infra:work` when WorkManager scheduling is ready
   - Implement `player:ui-api` when player UI split is ready
3. **Long-term:** All future work happens within the frozen module set

---

## References

- `docs/v2/FROZEN_MODULE_MANIFEST.md` - Authoritative specification
- `scripts/ci/check-frozen-manifest.sh` - Enforcement script
- `AGENTS.md` - Architecture rules and layer boundaries
- `.github/workflows/v2-arch-gates.yml` - CI workflow

---

## Verification Checklist

- [x] All three reserved modules created with proper structure
- [x] All modules have `build.gradle.kts` with correct namespace
- [x] All modules have README.md with purpose and TODOs
- [x] All modules have placeholder source files with documentation
- [x] `settings.gradle.kts` updated with three new includes
- [x] `check-frozen-manifest.sh` script created and made executable
- [x] `check-arch-guardrails.sh` updated to call frozen manifest check
- [x] All modules compile successfully
- [x] All modules pass ktlint formatting
- [x] Frozen manifest check passes
- [x] Unauthorized module detection works correctly
- [x] Changes committed and pushed to PR branch
