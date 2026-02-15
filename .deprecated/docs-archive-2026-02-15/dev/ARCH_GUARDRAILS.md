# v2 Architecture Guardrails

This document defines the static analysis rules that enforce v2 architecture boundaries.

## Overview

To prevent architecture violations from being merged, we use dual enforcement:
1. **Detekt static analysis** - Import rules, method call rules
2. **Grep-based checks** - Pattern-based checks for layer boundaries

### Telegram Auth Contract & Wiring — Frozen

- **Canonical file:** `core/feature-api/src/main/kotlin/com/fishit/player/core/feature/auth/TelegramAuthRepository.kt` (contains both `TelegramAuthRepository` + `TelegramAuthState`).
- **No duplicates:** No other file may declare the interface or state.
- **Feature deps:** `feature/onboarding` must **not** depend on `infra/*`, `pipeline/*`, `playback/*`, or `app-v2` (core contracts + UI only).
- **DI binding:** Exactly one binding for `TelegramAuthRepository`, located in `infra/data-telegram` (none in `app-v2`).
- **Transport imports:** `infra/data-telegram` may use the transport **API surface only**; transport internals/TDLib types are forbidden.
- **Check locally:** `bash scripts/ci/check-telegram-auth-guardrails.sh`

### Duplicate Contracts & Shadow Types — Frozen

Global guardrails prevent contract duplication and shadow UI/Domain types across the entire codebase.

**Enforced Single Sources of Truth:**
- `TelegramAuthRepository` + `TelegramAuthState` → `core/feature-api`
- `XtreamAuthRepository` + `XtreamAuthState` → `core/feature-api`
- `RawMediaMetadata` → `core/model`
- `MetadataNormalizer` → `core/metadata-normalizer`

**Forbidden Shadow Types:**
- `UiTelegramAuthState`, `UiXtreamAuthState`, `Ui*AuthState` (use core contract directly)
- `DomainTelegramAuthState`, `DomainXtreamAuthState` (use import aliasing instead)

**Role Duplication Smells:**
Files containing `Bridge`, `Stopgap`, `Temp`, `Copy`, `Old`, `Backup`, `Alt` in their names are flagged as architectural smells (outside legacy/).

- **Check locally:** `bash scripts/ci/check-duplicate-contracts.sh`
- **Integrated in:** `scripts/ci/check-arch-guardrails.sh`

**Enforced Rules:**
1. **Layer boundary rules** - Features cannot import pipeline/transport/player-internal
2. **Logging contract** - Only UnifiedLog allowed outside infra/logging
3. **Secret safety** - No credentials or tokens in logs
4. **Player UI isolation** - Player UI cannot use Hilt EntryPoints or engine wiring (NEW)

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

## Player UI Layer Rules (NEW)

**Player UI (`player/**/ui/**`) MUST NOT:**
- ❌ Use Hilt `@EntryPoint` or `EntryPointAccessors` (DI anti-pattern)
- ❌ Reference engine wiring classes:
  - `PlaybackSourceResolver`
  - `ResumeManager`
  - `KidsPlaybackGate`
  - `NextlibCodecConfigurator`
- ❌ Import `com.fishit.player.internal.*` from other player modules
  - Exception: `player/internal/ui` can import from `player/internal` (same module)

**Why these rules exist:**
- **Prevents DI window climbing**: EntryPoints bypass constructor injection and create hidden dependencies
- **Enforces proper DI**: UI components must use `@HiltViewModel` with constructor injection
- **Maintains layer isolation**: UI should interact through high-level interfaces, not engine internals
- **Prevents tight coupling**: Cross-module internal imports create fragile dependencies

**Enforcement:** Grep-based checks in `scripts/ci/check-arch-guardrails.sh` (NO allowlist bypass)

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

### "Hilt EntryPoint forbidden in player:ui"
**Problem:** Using `@EntryPoint` or `EntryPointAccessors` in player UI components

**Fix:** Use `@HiltViewModel` with constructor injection:
```kotlin
// ❌ Wrong - EntryPoint anti-pattern
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerServiceEntryPoint {
    fun getPlayerSession(): InternalPlayerSession
}

@Composable
fun PlayerScreen(context: Context) {
    val entryPoint = EntryPointAccessors.fromApplication(
        context,
        PlayerServiceEntryPoint::class.java
    )
    val session = entryPoint.getPlayerSession()
}

// ✅ Correct - Constructor injection with ViewModel
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerEntry: PlayerEntryPoint
) : ViewModel() {
    // Use playerEntry here
}

@Composable
fun PlayerScreen(viewModel: PlayerViewModel = hiltViewModel()) {
    // Access state from ViewModel
}
```

### "Engine wiring classes forbidden in player:ui"
**Problem:** Directly referencing internal engine components like `PlaybackSourceResolver`, `ResumeManager`, etc.

**Fix:** Use high-level abstractions:
```kotlin
// ❌ Wrong - Direct engine wiring reference
class PlayerControls(
    private val resolver: PlaybackSourceResolver,
    private val resumeManager: ResumeManager
)

// ✅ Correct - Use domain-level abstraction
@HiltViewModel
class PlayerControlsViewModel @Inject constructor(
    private val playerEntry: PlayerEntryPoint  // High-level interface
) : ViewModel() {
    // Engine components are encapsulated inside PlayerEntryPoint
}
```

## Detekt Configuration

**Status:** ✅ Detekt configuration is valid and runs successfully.

**Implemented:** The forbidden import and method call rules have been added to `detekt-config.yml`:

```yaml
style:
  ForbiddenImport:
    active: true
    imports:
      - value: 'com.fishit.player.pipeline.*'
        reason: 'Features must use domain models, not pipeline DTOs.'
      - value: 'com.fishit.player.infra.transport.*'
        reason: 'Features must use repository interfaces, not transport clients.'
      - value: 'com.fishit.player.player.internal.*'
        reason: 'Features must use PlayerEntryPoint abstraction from playback/domain.'
      - value: 'com.fishit.player.internal.*'
        reason: 'Features must use PlayerEntryPoint abstraction from playback/domain.'
      - value: 'com.fishit.player.infra.data.*'
        reason: 'Features must define repository interfaces in their domain package.'
      - value: 'com.fishit.player.infra.network.*'
        reason: 'Features must use repository abstractions, not network layer.'
      - value: 'com.fishit.player.infra.persistence.*'
        reason: 'Features must use repository abstractions, not persistence layer.'
      - value: 'android.util.Log'
        reason: 'Use UnifiedLog from :infra:logging instead.'
      - value: 'timber.log.Timber'
        reason: 'Use UnifiedLog from :infra:logging instead.'
    
    excludes:
      - '**/infra/**'
      - '**/pipeline/**'
      - '**/playback/**'
      - '**/player/**'
      - '**/tools/**'
      - '**/core/**'
      - '**/legacy/**'
      - '**/src/test/**'
      - '**/src/androidTest/**'

  ForbiddenMethodCall:
    active: true
    methods:
      - value: 'kotlin.io.println'
        reason: 'Use UnifiedLog. See LOGGING_CONTRACT_V2.md'
      - value: 'kotlin.io.print'
        reason: 'Use UnifiedLog. See LOGGING_CONTRACT_V2.md'
      - value: 'java.lang.Throwable#printStackTrace'
        reason: 'Use UnifiedLog.e(TAG, throwable). See LOGGING_CONTRACT_V2.md'
    
    excludes:
      - '**/infra/logging/**'
      - '**/legacy/**'
      - '**/src/test/**'
      - '**/src/androidTest/**'
```

**Note:** The ForbiddenImport rules detect SOURCE-level import violations. However, if a module has a BUILD-level dependency (in `build.gradle.kts`), the imports are considered valid by Detekt. For comprehensive layer boundary enforcement, consider adding `dependency-analysis-gradle-plugin` to detect build-level violations.

## CI Enforcement

Architecture guardrails are enforced in the PR CI pipeline (`.github/workflows/v2-arch-gates.yml`):

**1. Detekt Static Analysis:**
```yaml
- name: Run Detekt (Layer Boundaries + Logging Contract)
  run: ./gradlew detekt --no-daemon --stacktrace
```

**2. Grep-based Architecture Checks:**
```yaml
- name: Run Grep-based Architecture Checks
  run: ./scripts/ci/check-arch-guardrails.sh
```

Both checks must pass for PRs to be merged. The grep-based checks provide additional enforcement for patterns that Detekt cannot easily express (like player:ui EntryPoint usage).

## References

- [AGENTS.md Section 4.5](../../AGENTS.md) - Layer Boundary Enforcement
- [LOGGING_CONTRACT_V2.md](../../contracts/LOGGING_CONTRACT_V2.md) - Logging rules
- [GLOSSARY_v2_naming_and_modules.md](../../contracts/GLOSSARY_v2_naming_and_modules.md) - Naming conventions

## Enforcement Status

| Rule | Status | Enforced By |
|------|--------|-------------|
| No pipeline imports in features | ✅ Active | Detekt ForbiddenImport |
| No transport imports in features | ✅ Active | Detekt ForbiddenImport |
| No player.internal in features | ✅ Active | Detekt ForbiddenImport |
| No infra.data in features | ✅ Active | Detekt ForbiddenImport |
| UnifiedLog only | ✅ Active | Detekt ForbiddenImport + ForbiddenMethodCall |
| No println/printStackTrace | ✅ Active | Detekt ForbiddenMethodCall |
| No Hilt EntryPoints in player:ui | ✅ Active | Grep script (no allowlist) |
| No engine wiring in player:ui | ✅ Active | Grep script (no allowlist) |
| No cross-module internal imports in player:ui | ✅ Active | Grep script (no allowlist) |
| Dual enforcement in CI | ✅ Active | v2-arch-gates workflow |

**Status:** ✅ All architecture guardrails are active and enforced in CI.

**Note:** Player UI guardrails (Hilt EntryPoints, engine wiring) do NOT support allowlist bypass. These are hard architectural boundaries that cannot be circumvented.
