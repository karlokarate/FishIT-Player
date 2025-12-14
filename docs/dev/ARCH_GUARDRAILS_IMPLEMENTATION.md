# Architecture Guardrails Implementation - Phase A1

## Overview

This document describes the architecture guardrails implementation that enforces layer boundaries and prevents illegal wiring in PRs.

## Implementation Components

### 1. Detekt Configuration (`detekt-config.yml`)

**Enhanced with layer boundary rules:**

- **Feature Layer Rules:**
  - ❌ `com.fishit.player.pipeline.*` - Must use domain models, not pipeline DTOs
  - ❌ `com.fishit.player.infra.data.*` - Must define repository interfaces in domain package
  - ❌ `com.fishit.player.infra.transport.*` - Must use repository/domain interfaces
  - ❌ `com.fishit.player.player.internal.*` - Must use PlayerEntryPoint abstraction

- **Logging Contract Enforcement:**
  - ❌ `android.util.Log` imports
  - ❌ `timber.log.Timber` imports
  - ❌ `kotlin.io.println` calls (ForbiddenMethodCall)
  - ❌ `printStackTrace` calls (ForbiddenMethodCall)

- **v2 Namespace Enforcement:**
  - ❌ `com.chris.m3usuite.*` imports in v2 modules

### 2. Grep-Based Script (`scripts/ci/check-arch-guardrails.sh`)

**Provides comprehensive layer boundary checking:**

- **Feature Layer:** Blocks pipeline/data/transport/player.internal imports
- **Playback Layer:** Blocks pipeline DTOs and data layer imports
- **Pipeline Layer:** Blocks data/playback/player imports
- **Logging Contract:** Detects direct Log/Timber usage
- **v2 Namespace:** Detects v1 imports in v2 modules

**Successfully detects real violations:**
- Example: Onboarding feature importing transport layer

### 3. CI Workflow (`.github/workflows/v2-arch-gates.yml`)

**Two-stage enforcement:**

**Job A: Architecture Gates**
- Runs Detekt with layer boundary rules
- Runs grep-based architecture checks
- Uploads Detekt reports for analysis

**Job B: Wiring Compile Gates**
- Compiles critical modules in Release mode:
  - `:app-v2:compileReleaseKotlin`
  - `:feature:home:compileReleaseKotlin`
  - `:feature:telegram-media:compileReleaseKotlin`
  - `:playback:domain:compileReleaseKotlin`

**Triggers:** PRs to `main` and `architecture/v2-bootstrap` branches

## Known Limitations

### Detekt ForbiddenImport Limitations

Detekt 1.23.8's `ForbiddenImport` rule has **rule-level excludes** only, not per-import excludes. This means:

- Cannot enforce "pipeline can't import playback" while "feature CAN import playback"
- Requires broad exclusions that may weaken enforcement
- Baseline files may suppress new violations

### Workaround: Dual Enforcement

The grep-based script provides precise per-layer enforcement that Detekt cannot:

1. **Detekt**: Catches violations at build time (fast feedback)
2. **Grep Script**: Guarantees comprehensive checking (reliable fallback)

Both run in CI to maximize violation detection.

## Current Violations Detected

**Onboarding Feature (feature/onboarding):**
- ❌ Directly imports `infra.transport.telegram.TelegramAuthClient`
- ❌ Directly imports `infra.transport.xtream.*` classes
- **Required Fix:** Create repository abstraction in feature domain package

## Usage

### Local Testing

```bash
# Run Detekt
./gradlew detekt

# Run grep-based checks
./scripts/ci/check-arch-guardrails.sh

# Test compilation gates
./gradlew :app-v2:compileReleaseKotlin
./gradlew :feature:home:compileReleaseKotlin
```

### CI Behavior

- **Green PR:** No violations, all modules compile
- **Red PR:** Layer boundary violations or compile failures detected
- **Blocking:** CI is configured as required check

## References

- **AGENTS.md Section 4.5** - Layer hierarchy and forbidden imports
- **LOGGING_CONTRACT_V2.md** - Logging requirements
- **GLOSSARY_v2_naming_and_modules.md** - v2 naming conventions

## Future Improvements

1. **Custom Detekt Rule Plugin:** Enable per-layer rules with fine-grained control
2. **Automated Fixes:** Suggest repository abstraction patterns for violations
3. **Violation Dashboard:** Track and visualize architecture compliance over time
4. **Pre-commit Hooks:** Run guardrails locally before push
