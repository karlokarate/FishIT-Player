# Copilot Task Hints – PLATIN Quick Reference

> **Purpose:** Quick-access hints for Copilot to execute tasks efficiently.
> This file is read by Copilot for task automation.

---

## 🚀 Quick Commands

### Build

```bash
./gradlew :app-v2:assembleDebug          # Build v2 debug APK
./gradlew :app-v2:assembleRelease        # Build v2 release APK
./scripts/build/safe-build.sh assembleDebug  # Memory-safe build
```

### Quality (MUST pass before commit)

```bash
./gradlew ktlintCheck                    # Code style
./gradlew ktlintFormat                   # Auto-format
./gradlew detekt                         # Static analysis
./gradlew lintDebug                      # Android lint
```

### Testing

```bash
./gradlew testDebugUnitTest              # Unit tests
./gradlew :pipeline:telegram:test        # Pipeline tests only
./gradlew :player:internal:test          # Player tests only
./tools/pipeline-test-fast.sh            # Fast pipeline tests
```

### Module-Specific Compilation

```bash
./gradlew :core:model:compileDebugKotlin
./gradlew :pipeline:telegram:compileDebugKotlin
./gradlew :infra:transport-telegram:compileDebugKotlin
./gradlew :player:internal:compileDebugKotlin
```

---

## 📋 PLATIN Checklists

### Before Any Change

- [ ] Read relevant contract from `/contracts/`
- [ ] Check path-scoped instructions in `.github/instructions/`
- [ ] Read module README.md

### After Any Change  

- [ ] `./gradlew ktlintCheck` passes
- [ ] `./gradlew detekt` passes
- [ ] Relevant tests pass
- [ ] No layer boundary violations

---

## 🎯 Module Quick Reference

| Module | Package | Key Files |
|--------|---------|-----------|
| Core Model | `core.model` | `RawMediaMetadata.kt`, `MediaType.kt` |
| Pipeline | `pipeline.*` | `*CatalogPipeline.kt`, `*Mapper.kt` |
| Transport | `infra.transport.*` | `*Client.kt`, `api/*.kt` |
| Data | `infra.data.*` | `*Repository.kt`, `Obx*.kt` |
| Player | `player.internal` | `InternalPlayerSession.kt` |
| Playback | `playback.*` | `*PlaybackSourceFactory.kt` |

---

## 🔴 Common Anti-Patterns (AVOID)

```kotlin
// ❌ WRONG: Pipeline importing Data layer
import com.fishit.player.infra.data.telegram.*

// ❌ WRONG: globalId computed in pipeline
globalId = FallbackCanonicalKeyGenerator.generate(...)

// ❌ WRONG: Player importing Transport
import com.fishit.player.infra.transport.telegram.*

// ❌ WRONG: Exposing TDLib types
fun getMessage(): TdApi.Message
```

---

## 🏆 PLATIN Patterns (USE THESE)

```kotlin
// ✅ Pipeline output
fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata

// ✅ Player source-agnostic
class InternalPlayerSession @Inject constructor(
    private val sourceResolver: PlaybackSourceResolver,
)

// ✅ Transport typed interface
interface TelegramFileClient {
    suspend fun downloadFile(remoteId: String): File
}
```

---

## 📁 Important Files

| File | Purpose |
|------|---------|
| `AGENTS.md` | **PRIMARY AUTHORITY** |
| `/contracts/GLOSSARY_v2_naming_and_modules.md` | Naming rules |
| `/contracts/MEDIA_NORMALIZATION_CONTRACT.md` | Pipeline rules |
| `.github/instructions/*.instructions.md` | Path-scoped rules |
| `.github/prompts/*.md` | Prompt templates |
