# v2 Architecture Guardrails

This document defines the static analysis rules that enforce v2 architecture boundaries.

## Overview

To prevent architecture violations from being merged, we use Detekt static analysis to enforce:
1. **Layer boundary rules** - Features cannot import pipeline/transport/player-internal
2. **Logging contract** - Only UnifiedLog allowed outside infra/logging
3. **Secret safety** - No credentials or tokens in logs

## Feature Layer Import Rules

**Features (`feature/**`) may ONLY import:**
- ✅ Domain abstractions (`core/*`, `playback/domain`)
- ✅ Logging (`infra/logging` - UnifiedLog ONLY)

**Features MUST NOT import:**
- ❌ `*.pipeline.*` - Pipeline layer (use domain models instead)
- ❌ `*.transport.*` - Transport layer (use repository interfaces)
- ❌ `*.player.internal.*` - Player internals (use PlayerEntryPoint from playback/domain)
- ❌ `*.infra.data.*` - Data layer (define repository interface in feature, implement in infra)
- ❌ `*.infra.network.*` - Network layer (use repository abstractions)
- ❌ `*.infra.persistence.*` - Persistence layer (use repository abstractions)
- ❌ `*.infra.transport.*` - Transport layer (use repository abstractions)

**Why these rules exist:**
- Prevents pipeline DTOs (`RawMediaMetadata`, `XtreamVodItem`) from leaking into UI
- Enforces dependency inversion (feature owns interface, infra implements)
- Maintains clear separation of concerns per AGENTS.md Section 4.5

## Logging Contract Rules

Per `LOGGING_CONTRACT_V2.md`:

**Allowed:**
- ✅ `UnifiedLog` from `infra/logging` (lambda-based API)

**Forbidden outside `infra/logging`:**
- ❌ `android.util.Log.*`
- ❌ `kotlin.io.println`
- ❌ `kotlin.io.print`
- ❌ `Throwable#printStackTrace()`
- ❌ `timber.log.Timber.*`

**Why:**
- Centralized logging configuration
- Structured logging with tagging
- Secret redaction in one place
- Performance (lambda-based API avoids string concat in production)

## Common Fixes

### "Cannot import pipeline.*"
**Problem:** `import com.fishit.player.pipeline.telegram.*`

**Fix:** Use domain model instead:
```kotlin
// ❌ Wrong
fun play(item: RawMediaMetadata)  // Pipeline DTO

// ✅ Correct
fun play(item: TelegramMediaItem)  // Feature domain model
```

### "Cannot import player.internal.*"
**Problem:** `import com.fishit.player.internal.session.InternalPlayerSession`

**Fix:** Use abstraction:
```kotlin
// ❌ Wrong
@Inject constructor(private val session: InternalPlayerSession)

// ✅ Correct
@Inject constructor(private val playerEntry: PlayerEntryPoint)
```

### "Cannot import infra.data.*"
**Problem:** `implementation(project(":infra:data-telegram"))`

**Fix:** Use dependency inversion:
1. Define repository interface in `feature/*/domain/`
2. Implement in `infra/data-*` (adapter pattern)
3. Feature depends on interface, not implementation

### "Cannot use println/Log"
**Problem:** `println("Debug: $value")` or `Log.d(TAG, "message")`

**Fix:** Use UnifiedLog:
```kotlin
// ❌ Wrong
println("Starting playback")
Log.d(TAG, "User: ${user.id}")

// ✅ Correct
UnifiedLog.d(TAG) { "Starting playback" }
UnifiedLog.d(TAG) { "User: ${user.id}" }  // Lazy evaluation
```

## Detekt Configuration

**Status:** ⚠️ Detekt configuration currently has a pre-existing validation error that prevents rules from running.

**TODO:** Fix `detekt-config.yml` validation issue, then add:

```yaml
style:
  ForbiddenImport:
    active: true
    imports:
      # Layer boundary rules
      - value: '*.pipeline.*'
        reason: 'Features must use domain models. See docs/dev/ARCH_GUARDRAILS.md'
      - value: '*.transport.*'
        reason: 'Features must use repository interfaces. See docs/dev/ARCH_GUARDRAILS.md'
      - value: '*.player.internal.*'
        reason: 'Features must use PlayerEntryPoint. See docs/dev/ARCH_GUARDRAILS.md'
      - value: '*.infra.data.*'
        reason: 'Features must define repository interfaces. See docs/dev/ARCH_GUARDRAILS.md'
      
      # Logging rules
      - value: 'android.util.Log'
        reason: 'Use UnifiedLog. See LOGGING_CONTRACT_V2.md'
    
    excludes:
      - '**/infra/logging/**'
      - '**/legacy/**'
      - '**/infra/data-*/**'  # Data adapters may import transport
      - '**/pipeline/**'
      - '**/playback/**'
      - '**/player/internal/**'

  ForbiddenMethodCall:
    active: true
    methods:
      - value: 'kotlin.io.println'
        reason: 'Use UnifiedLog. See LOGGING_CONTRACT_V2.md'
      - value: 'java.lang.Throwable#printStackTrace'
        reason: 'Use UnifiedLog.e(TAG, throwable). See LOGGING_CONTRACT_V2.md'
    
    excludes:
      - '**/infra/logging/**'
      - '**/legacy/**'
      - '**/test/**'
```

## CI Enforcement

Once Detekt is fixed, add to CI pipeline (`.github/workflows/*.yml`):

```yaml
- name: Run Detekt
  run: ./gradlew detekt
```

This will block PRs that violate architecture rules.

## References

- [AGENTS.md Section 4.5](../../AGENTS.md) - Layer Boundary Enforcement
- [LOGGING_CONTRACT_V2.md](../../contracts/LOGGING_CONTRACT_V2.md) - Logging rules
- [GLOSSARY_v2_naming_and_modules.md](../../contracts/GLOSSARY_v2_naming_and_modules.md) - Naming conventions

## Enforcement Status

| Rule | Status | Enforced By |
|------|--------|-------------|
| No pipeline imports in features | ✅ Implemented | Code review + this PR |
| No transport imports in features | ✅ Implemented | Code review + this PR |
| No player.internal in features | ✅ Implemented | Code review + this PR |
| No infra.data in features | ✅ Implemented | Code review + this PR |
| UnifiedLog only | ⚠️ Manual | Code review (Detekt blocked) |
| No println/printStackTrace | ⚠️ Manual | Code review (Detekt blocked) |
| Detekt in CI | ❌ Blocked | Pre-existing Detekt config error |

**Next Steps:**
1. Fix `detekt-config.yml` validation error
2. Add forbidden import/method rules
3. Add Detekt to CI as required check
