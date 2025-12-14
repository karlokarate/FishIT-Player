# Architecture Guardrails Implementation - Phase A1 + A1.2 + A1.3

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
- **App-v2 Layer:** Blocks player.internal/transport/pipeline imports (Phase A1.2)
- **Playback Layer:** Blocks pipeline DTOs and data layer imports
- **Pipeline Layer:** Blocks data/playback/player imports
- **Bridge/Stopgap Blockers:** Blocks TdlibClientProvider, *Bridge, *Stopgap, *Temporary, *Adapter patterns
- **Logging Contract:** Detects direct Log/Timber usage
- **v2 Namespace:** Detects v1 imports in v2 modules

### 3. Allowlist Mechanism (`scripts/ci/arch-guardrails-allowlist.txt`) - Phase A1.3

**Strict file-based exception system:**

- **Path-based allowlisting** - Uses exact file paths or glob patterns
- **Mandatory reasons** - Each entry must include a comment explaining why
- **Minimal by design** - Only legitimate exceptions (e.g., DI wiring modules)
- **Transparent reporting** - Shows which files are allowlisted during checks

**Format:**
```
path/to/file.kt # Reason for exception
app-v2/**/di/**/*.kt # Pattern-based exception with reason
```

**Current Allowlist Entries:**
- App-v2 DI modules that wire transport implementations to domain interfaces
- Bootstrap classes that observe transport state for initialization logic

**Anti-pattern Prevention:**
- ❌ No global rule disabling
- ❌ No blanket exclusions
- ✅ Each file must be explicitly allowlisted
- ✅ Allowlist is version controlled and reviewed

### 4. CI Workflow (`.github/workflows/v2-arch-gates.yml`)

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

# Test KSP gates (Phase A1.2)
./gradlew :feature:home:kspReleaseKotlin
./gradlew :app-v2:kspReleaseKotlin
```

### Adding Allowlist Exceptions (Phase A1.3)

**When to add an allowlist entry:**
- Only for legitimate architectural exceptions (e.g., DI wiring modules)
- Never to bypass violations that should be fixed
- Each entry requires code review approval

**How to add an entry:**

1. Edit `scripts/ci/arch-guardrails-allowlist.txt`
2. Add the file path with a clear reason:
   ```
   app-v2/src/main/java/com/fishit/player/v2/di/MyModule.kt # Hilt DI wiring for XYZ
   ```
3. Test locally: `./scripts/ci/check-arch-guardrails.sh`
4. Commit and include justification in PR description

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
