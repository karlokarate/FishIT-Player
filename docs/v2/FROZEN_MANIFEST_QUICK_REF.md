# Frozen Module Manifest - Quick Reference

> ⚠️ **The module structure is FROZEN. NO new modules can be added.**

## What This Means

After the frozen manifest PR merges:
- ✅ You CAN add code to existing modules
- ✅ You CAN refactor within module boundaries
- ✅ You CAN fill TODOs in reserved modules (imaging, work, ui-api)
- ❌ You CANNOT create new modules
- ❌ You CANNOT modify `settings.gradle.kts` includes
- ❌ You CANNOT add new `build.gradle.kts` files

## Reserved Modules (Added in Freeze PR)

These modules are empty stubs ready to be filled when needed:

| Module | Purpose | Fill When |
|--------|---------|-----------|
| `:infra:imaging` | Global ImageLoader/Coil/OkHttp | Need centralized image loading |
| `:infra:work` | WorkManager scheduling | Need background sync |
| `:player:ui-api` | Player UI contracts | Ready to split player UI |

## CI Enforcement

Script: `scripts/ci/check-frozen-manifest.sh`

Runs as part of: `scripts/ci/check-arch-guardrails.sh`

**Fails if:**
- New `build.gradle.kts` appears anywhere
- `settings.gradle.kts` modified (except version bumps)
- New `include()` statement added

**No exceptions.** The freeze is absolute.

## Complete Module List

Total: **68 modules** (frozen)

### Core (11)
`:core:model`, `:core:player-model`, `:core:feature-api`, `:core:persistence`, `:core:metadata-normalizer`, `:core:catalog-sync`, `:core:firebase`, `:core:ui-imaging`, `:core:ui-theme`, `:core:ui-layout`, `:core:app-startup`

### Playback & Player (9)
`:playback:domain`, `:playback:telegram`, `:playback:xtream`, `:player:ui`, `:player:ui-api`, `:player:internal`, `:player:miniplayer`, `:player:nextlib-codecs`

### Pipelines (4)
`:pipeline:telegram`, `:pipeline:xtream`, `:pipeline:io`, `:pipeline:audiobook`

### Features (8)
`:feature:home`, `:feature:library`, `:feature:live`, `:feature:detail`, `:feature:telegram-media`, `:feature:audiobooks`, `:feature:settings`, `:feature:onboarding`

### Infrastructure (9)
`:infra:logging`, `:infra:tooling`, `:infra:transport-telegram`, `:infra:transport-xtream`, `:infra:data-telegram`, `:infra:data-xtream`, `:infra:data-home`, `:infra:imaging`, `:infra:work`

### App & Tools (2)
`:app-v2`, `:tools:pipeline-cli`

## When You Need New Functionality

Instead of creating a new module:
1. Identify which existing module should own the feature
2. Add the code to that module
3. If it doesn't fit anywhere, review with architecture team
4. The freeze exists to prevent module sprawl

## Local Verification

```bash
# Check if your changes violate the freeze
bash scripts/ci/check-frozen-manifest.sh

# Should see:
# ✅ Module manifest frozen - no violations
```

## See Also

- `docs/v2/FROZEN_MODULE_MANIFEST.md` - Full specification
- `docs/v2/FROZEN_MODULE_MANIFEST_IMPLEMENTATION.md` - Implementation details
- `AGENTS.md` - Architecture rules
