# AGENTS – FishIT-Player v2 (architecture/v2-bootstrap)

This document defines the rules for ALL automated agents (Copilot, Codex, etc.) working on the **v2 rebuild** of FishIT-Player in this repository.

It applies to:

- The branch `architecture/v2-bootstrap`
- Any feature / topic branches derived from the v2 rebuild (e.g. `feature/*` based on `architecture/v2-bootstrap`)

It does **not** define behavior for other repositories or for the historic v1 codebase.

---

## ⚠️ CRITICAL: Player Layer Isolation (MUST READ FIRST)

> **HARD RULE:** The Player layer (`player/**`, `playback/**`) is **source-agnostic**.
> It does NOT know about Telegram, Xtream, or any specific transport implementation.

### What the Player Layer Knows

| Allowed | Description |
|---------|-------------|
| `PlaybackSourceResolver` | Resolves `PlaybackContext` → `MediaSource` |
| `Set<PlaybackSourceFactory>` | Injected via `@Multibinds` (can be empty) |
| `PlaybackContext` | Source-agnostic playback descriptor |
| `RawMediaMetadata` | Canonical media from normalizer |

### What the Player Layer MUST NOT Know

| Forbidden | Why |
|-----------|-----|
| `TdlibClientProvider` | ⚠️ **v1 legacy pattern** – must NOT be reintroduced in v2 |
| `DefaultTelegramClient` | Internal to `transport-telegram` – not exposed |
| `TelegramTransportClient` | Transport layer – belongs in `infra/transport-telegram` |
| `TelegramAuthClient` etc. | Transport interfaces – only `playback/telegram` may use these |
| `XtreamApiClient` | Transport layer – belongs in `infra/transport-xtream` |
| Any `*Impl` from transport | Player only consumes interfaces |
| TDLib types (`TdApi.*`) | Raw TDLib – never exposed outside transport |

### Binding Rule for Playback Modules (ALL Sources)

> **HARD RULE:** Every playback source (Telegram, Xtream, Local, Audiobook, future) MUST use `@Multibinds` + `@IntoSet` pattern.
> This is the canonical way to add playback capabilities without changing the player.

**Pattern:**

```kotlin
// playback/domain/di/PlaybackDomainModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackDomainModule {
    // Empty set when no sources are available – player works with fallback stream
    @Multibinds abstract fun bindPlaybackSourceFactories(): Set<PlaybackSourceFactory>
}

// Each source contributes its factory via @IntoSet:

// playback/telegram/di/TelegramPlaybackModule.kt
@Binds @IntoSet abstract fun bindTelegramFactory(impl: TelegramPlaybackSourceFactoryImpl): PlaybackSourceFactory

// playback/xtream/di/XtreamPlaybackModule.kt  
@Binds @IntoSet abstract fun bindXtreamFactory(impl: XtreamPlaybackSourceFactoryImpl): PlaybackSourceFactory

// playback/local/di/LocalPlaybackModule.kt (future)
@Binds @IntoSet abstract fun bindLocalFactory(impl: LocalPlaybackSourceFactoryImpl): PlaybackSourceFactory

// playback/audiobook/di/AudiobookPlaybackModule.kt (future)
@Binds @IntoSet abstract fun bindAudiobookFactory(impl: AudiobookPlaybackSourceFactoryImpl): PlaybackSourceFactory
```

**Expected Playback Modules:**

| Module | Status | Notes |
|--------|--------|-------|
| `playback/domain` | ✅ Ready | Base contracts + `@Multibinds` declaration |
| `playback/telegram` | ⏸️ Disabled | Waiting for `transport-telegram` typed interfaces |
| `playback/xtream` | ✅ Ready | Can be enabled when needed |
| `playback/local` | 🔮 Future | For `pipeline/io` local files |
| `playback/audiobook` | 🔮 Future | For `pipeline/audiobook` |

### Agent Instructions

1. **DO NOT** re-enable `TelegramPlaybackModule` until `DefaultTelegramClient` implements all typed interfaces in `transport-telegram`.
2. **DO NOT** add imports from `infra/transport-*` into `player/**` modules.
3. **DO NOT** "fix" DI errors by making the player depend on transport implementations.
4. **DO NOT** reintroduce `TdlibClientProvider` anywhere – it is a v1 legacy pattern.
5. **DO** use `@Multibinds` to allow empty factory sets.
6. **DO** use fallback streams (e.g., Big Buck Bunny) for testing when no factories are available.
7. **DO** create new `playback/*` modules using the `@IntoSet` pattern for new sources.
8. **DO** ensure each `PlaybackSourceFactory` declares which `SourceType` values it handles.

> **Test First:** The player must work with zero `PlaybackSourceFactory` entries. If it doesn't compile without transport, the architecture is broken.

### Player Test Status

> ✅ **The player is currently test-ready** via `DebugPlaybackScreen` with Big Buck Bunny stream.
> No Telegram or Xtream transport is required to test player functionality.

---

## 1. Scope & Branch Policy

1.1. This repository is currently in **v2 rebuild mode**.  
The active app is defined by the v2 modules:

- `/app-v2/**`
- `/core/**`
- `/infra/**`
- `/feature/**`
- `/player/**`
- `/playback/**`
- `/pipeline/**`
- `/docs/v2/**`
- `/docs/meta/**`
- `/scripts/**`

1.2. **Never merge into `main` from this branch or its descendants.**

- No automated merges to `main`.
- No rebases or force pushes to `main`.

1.3. Branch operations:

- Agents **may** create feature branches derived from `architecture/v2-bootstrap` (e.g. `feature/phase1-feature-system`), but only when explicitly instructed.
- Agents must **not** rename or delete existing protected branches (`main`, `architecture/v2-bootstrap`) under any circumstances.

1.4. Other branches/repos may have their own `AGENTS.md`.  
This file is the single source of truth for the **v2 rebuild** in this repo.

---

## 2. Allowed vs Forbidden Paths

2.1. Agents MAY modify and create files ONLY under:

- `/app-v2/**`
- `/core/**`
- `/infra/**`
- `/feature/**`
- `/player/**`
- `/playback/**`
- `/pipeline/**`
- `/docs/v2/**`
- `/docs/meta/**`
- `/scripts/**`
- Root-level files explicitly mentioned in this document (e.g. `AGENTS.md`, `V2_PORTAL.md`, `ARCHITECTURE_OVERVIEW.md`, `CHANGELOG.md`, `ROADMAP.md`).

2.2. Agents MUST treat the following as **read-only**:

- `/legacy/**`
- `/app/**` (entire v1 app)
- `/docs/legacy/**`
- `/docs/archive/**`

No formatting, no refactors, no cleanup. Legacy is reference only.

2.3. New modules under the v2 structure are **allowed and encouraged** when they fit the architecture (e.g. `core/feature-api`, new `feature/*` modules).

2.4. `settings.gradle.kts`:

- Agents **may and must** update `settings.gradle.kts` when new v2 modules are introduced.
- Never re-add the v1 app module or any legacy modules to the build.

---

## 3. Legacy & v1 Namespace Rules

3.1. All v1 code lives under `/legacy/**`.  
It is a **gold source** for behavior and ideas, but not an active codebase.

3.2. Hard rule:

> Any occurrence of `com.chris.m3usuite` **outside** of `/legacy/**` is a bug and must be removed.

3.3. Porting from legacy to v2:

- Agents **should** port good ideas from legacy to v2, but:
  - Do **not** copy/paste large chunks.
  - Always:
    1. Read the legacy code,
    2. Extract the behavior / best patterns,
    3. Re-implement it in v2 using the v2 architecture (canonical media, normalizer, SIP, etc.).
- Never add new code under `legacy/`. If something is worth keeping, it must be implemented in v2.

3.4. **Gold Nuggets** – Curated v1 patterns for v2 implementation

**`/legacy/gold/`** contains production-tested patterns extracted from v1 (36 patterns from ~12,450 lines):

- **`telegram-pipeline/`** – Telegram/TDLib integration (8 patterns: unified engine, zero-copy streaming, RemoteId URLs, priority downloads, MP4 validation, lazy thumbnails, auth state machine, cursor pagination)
- **`xtream-pipeline/`** – Xtream API (8 patterns: rate limiting, dual-TTL cache, alias rotation, multi-port discovery, capability detection, EPG prefetch, category fallback, graceful degradation)
- **`ui-patterns/`** – TV focus & navigation (10 patterns: FocusKit, focus zones, tvClickable, DPAD handling, focus memory, row navigation, TV forms)
- **`logging-telemetry/`** – Logging (10 patterns: UnifiedLog facade, ring buffer, source categories, structured events, log viewer, async processing, performance monitor)

**When to use Gold Nuggets:**

- **Before implementing** Telegram, Xtream, TV/focus, or logging features → read the relevant gold document first
- Each pattern includes:
  - Code examples with "why preserve" rationale
  - v2 target module mappings (e.g., `pipeline/telegram/tdlib/`, `core/ui-focus/`)
  - Implementation phase checklists
  - v2 improvements from code review
- **Always re-implement** using v2 architecture – never copy/paste from v1
- Follow all v2 contracts (MEDIA_NORMALIZATION_CONTRACT, LOGGING_CONTRACT_V2, etc.)

See **`/legacy/gold/EXTRACTION_SUMMARY.md`** for complete porting guidance and **`GOLD_EXTRACTION_FINAL_REPORT.md`** in the root for an overview.

---

## 4. Core Architecture Principles (v2)

4.1. Canonical Media & Normalizer

- All pipelines (Telegram, Xtream, Audiobook, IO) produce **RawMediaMetadata** only.
- All normalization, TMDB lookups, and cross-pipeline unification happen in the **central normalizer/resolver** (`core/metadata-normalizer`), not in pipelines.
- Agents MUST follow:
  - `docs/v2/CANONICAL_MEDIA_SYSTEM.md`
  - `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md`
  - `docs/v2/MEDIA_NORMALIZER_DESIGN.md`

4.2. Pipelines

- Pipelines live under `/pipeline/**` and are v2 modules:
  - `pipeline/telegram`
  - `pipeline/xtream`
  - `pipeline/audiobook`
  - `pipeline/io`
- Pipelines:
  - never perform UI logic,
  - never embed player logic,
  - never own global caches,
  - never call TMDB or external metadata services directly (always via normalizer/resolver),
  - never depend on TDLib types or `TdlibClientProvider` (use adapters that consume `TgMessage` wrapper types).

4.3. Internal Player (SIP) & Playback

- Playback logic is centralised in the Internal Player (SIP) under `/player/internal` and related `playback/domain` modules.
- **The player is source-agnostic**: It knows only `PlaybackContext` and `PlaybackSourceFactory` sets.
- **The player is test-ready**: Debug playback via `DebugPlaybackScreen` with Big Buck Bunny stream.
- Agents MUST NOT implement player behavior in:
  - pipelines,
  - feature modules,
  - random helpers.
- Instead, follow:
  - `docs/v2/internal-player/**`

4.3.1. Transport vs Pipeline vs Player (binding rule)

> **Hard Rule:** These layers have distinct responsibilities and must not leak abstractions.

| Layer | Responsibility | What It Knows |
|-------|----------------|---------------|
| **Transport** | Talks to external APIs (TDLib, HTTP) | Raw TDLib DTOs, HTTP responses |
| **Pipeline** | Consumes transport-level wrapper types, produces `RawMediaMetadata` | `TgMessage`, `TgContent`, transport interfaces |
| **Player** | Consumes `PlaybackContext` and `PlaybackSourceFactory` sets only | Source-agnostic; no transport or pipeline types |
| **UI** | Consumes domain-level UI models from repositories | Never imports pipeline, transport, or player internals |

**What Transport Exposes (Telegram):**
- `TelegramAuthClient` – authentication operations
- `TelegramHistoryClient` – chat history, message fetching
- `TelegramFileClient` – file download operations
- `TelegramThumbFetcher` – thumbnail fetching for imaging

**What Transport Hides:**
- TDLib types (`TdApi.*`)
- `TdlibClientProvider` (v1 legacy pattern – must NOT be reintroduced)
- g00sha TDLib internals
- `DefaultTelegramClient` implementation details

4.4. No global mutable Singletons (hard clause)

> Do not introduce new global singletons with mutable state.

- Use proper DI and scoping instead (app scope, pipeline scope, session scope).
- **Only exception:** if something truly cannot work without a singleton (e.g. a framework constraint).  
  In that case:
  - The agent MUST clearly document:
    - why a singleton is needed,
    - what its scope and lifecycle are,
    - how it avoids race conditions and memory leaks.
  - This must be highlighted in the PR description and relevant docs.

4.5. Layer Boundary Enforcement (mandatory audit)

> **Hard Rule:** Each layer may only import from layers directly below it in the hierarchy.

**Layer Hierarchy (top to bottom):**

```text
UI → Domain → Data → Pipeline → Transport → core:model
```

**Forbidden Cross-Layer Imports:**

| Layer | MUST NOT Import From |
|-------|---------------------|
| Pipeline | Persistence (`data/obx/*`), Data layer, Playback, UI |
| Transport | Pipeline, Data layer, Playback, UI, Persistence |
| Data | Pipeline DTOs (`TelegramMediaItem`, `XtreamVodItem`, etc.) |
| Playback | Pipeline DTOs (use `RawMediaMetadata` only) |

**Allowed Flow:**

- **Transport** produces: `TgMessage`, `TgChat`, API responses
- **Pipeline** produces: `RawMediaMetadata` (via `toRawMediaMetadata()`)
- **Data** consumes: `RawMediaMetadata` from Pipeline, stores to DB
- **Playback** receives: `RawMediaMetadata` or `PlaybackContext`

**Mandatory Audit Process:**

After ANY change to modules in:

- `pipeline/**`
- `infra/transport-*`
- `infra/data-*`
- `playback/**`

Agents MUST:

1. **Run the Layer Boundary Audit Checklist:**
   - `docs/v2/PIPELINE_ARCHITECTURE_AUDIT_CHECKLIST.md`

2. **Execute automated verification:**

   ```bash
   # Check Pipeline for forbidden imports
   grep -rn "import.*data\.obx\|import.*ObxTelegram\|import.*ObxXtream" pipeline/
   
   # Check Data for forbidden Pipeline DTO imports
   grep -rn "import.*TelegramMediaItem\|import.*XtreamVodItem\|import.*XtreamSeriesItem\|import.*XtreamChannel" infra/data-*/
   
   # Check Playback for forbidden Pipeline DTO imports
   grep -rn "import.*TelegramMediaItem\|import.*XtreamVodItem\|import.*XtreamChannel" playback/
   ```

3. **Document any exceptions** with clear justification in the PR description.

4. **Zero tolerance:** Any violation found must be fixed before merge.

4.6. Pipeline Module Purity (hard clause)

> **Hard Rule:** Pipeline modules must remain pure catalog producers. No infrastructure code inside pipelines.

**Forbidden in Pipeline Modules:**

| Anti-Pattern | Where It Belongs |
|--------------|------------------|
| Adapter implementations (e.g., `TelegramPipelineAdapter` impl) | `infra/transport-*` |
| Source implementations (e.g., `XtreamCatalogSource` impl) | `infra/transport-*` |
| Repository implementations | `infra/data-*` |
| DataSource implementations | `playback/*` |
| HTTP clients, TDLib clients | `infra/transport-*` |
| ObjectBox entities | `core/persistence` |
| Normalization heuristics | `core/metadata-normalizer` |

**What Pipeline Modules MAY Contain:**

- Catalog contract interfaces (`TelegramCatalogPipeline`, `XtreamCatalogPipeline`)
- Catalog event types (`TelegramCatalogEvent`, `XtreamCatalogEvent`)
- Internal DTOs (`TelegramMediaItem`, `XtreamVodItem`) – never exported
- `toRawMediaMetadata()` extension functions
- DI modules for wiring interfaces

**Verification:**

```bash
# Ensure no *Impl classes in pipeline except CatalogPipelineImpl
find pipeline/ -name "*Impl.kt" | grep -v "CatalogPipelineImpl"

# Ensure no OkHttp/TDLib imports in pipeline
grep -rn "import okhttp3\|import org.drinkless.td\|import dev.g000sha256.tdl" pipeline/
```

4.7. Bridge Duplicate Detection (MANDATORY)

> **HARD RULE:** Duplicate DTO/model definitions across layers are architecture violations.
> Agents MUST detect and resolve them immediately.

**What is a Bridge Duplicate?**

A Bridge Duplicate occurs when:
1. The same data type (e.g., `TgContent`, `TgMessage`) is defined in multiple locations
2. A "bridge function" exists to convert between the duplicate definitions
3. Imports use aliases to disambiguate (e.g., `import ...TgContent as ApiTgContent`)

**Real Example (resolved Dec 2025):**
```kotlin
// BAD: Two TgContent definitions existed:
// 1. Inline in TelegramTransportClient.kt (sealed class TgContent)
// 2. In api/TgContent.kt (sealed interface TgContent)
// Bridge function toApiContent() converted between them
// TelegramPipelineAdapter imported both with alias
```

**Detection Checklist (MANDATORY before any transport/pipeline change):**

```bash
# 1. Check for aliased imports (strong indicator of duplicates)
grep -rn "import.*as Api\|import.*as Transport\|import.*as Old" pipeline/ infra/

# 2. Check for bridge/conversion functions
grep -rn "toApi\|fromApi\|toTransport\|fromTransport\|Bridge" pipeline/ infra/

# 3. Check for duplicate class definitions
grep -rn "^sealed class Tg\|^sealed interface Tg\|^data class Tg" infra/transport-*/

# 4. Verify single source of truth for DTOs
ls infra/transport-telegram/src/main/java/com/fishit/player/infra/transport/telegram/api/
```

**Resolution Protocol:**

When a Bridge Duplicate is detected:

1. **STOP** – Do not proceed with other changes
2. **IDENTIFY** the canonical location:
   - DTOs belong in `api/` package (e.g., `infra/transport-telegram/.../api/`)
   - Inline definitions in interface files are legacy and must be removed
3. **COMPARE** the definitions:
   - Newer, more complete definition is canonical
   - Check git history: `git log --oneline --follow -10 -- <file>`
4. **MIGRATE**:
   - Delete the inline/legacy definition
   - Update all imports to use the canonical `api/` package
   - Remove bridge functions (they become unnecessary)
   - Update mapping functions to use unified types
5. **VERIFY** compilation of all affected modules:
   ```bash
   ./gradlew :infra:transport-telegram:compileDebugKotlin \
             :pipeline:telegram:compileDebugKotlin \
             :playback:telegram:compileDebugKotlin --no-daemon
   ```

**Canonical DTO Locations:**

| Layer | Package | Example DTOs |
|-------|---------|--------------|
| Transport (Telegram) | `infra/transport-telegram/.../api/` | `TgMessage`, `TgContent`, `TgFile`, `TgPhotoSize` |
| Transport (Xtream) | `infra/transport-xtream/.../api/` | `XtreamChannel`, `XtreamVod`, `XtreamSeries` |
| Core Model | `core/model/` | `RawMediaMetadata`, `MediaType`, `SourceType` |

**Prevention:**

- New DTOs MUST be created in `api/` package, not inline
- Never define transport types inside interface files
- Use module READMEs to document canonical DTO locations

---

## 5. Logging, Telemetry & Cache

5.1. Logging (mandatory)

- All new v2 behavior must integrate with the unified logging system.
- Agents MUST respect and extend:
  - `docs/v2/LOGGING_CONTRACT_V2.md`
  - `docs/v2/logging/**`
- No ad-hoc `println` or unstructured logging. Use the logging abstractions.

5.2. Telemetry (TODO)

- Important code paths (player, pipelines, UI, cache) should emit telemetry:
  - Player stats,
  - Pipeline events,
  - UI responsiveness / jank, where relevant.
- **Note:** A dedicated `core/telemetry` module does not exist in v2 yet.
- For now, use `infra/logging` (UnifiedLog) for structured events.
- When telemetry is implemented:
  - Use the v2 logging infrastructure as foundation.
  - Document telemetry events in `docs/v2/logging/`.

5.3. Cache & Storage (TODO)

- **Note:** A dedicated `/infra/cache/**` module does not exist in v2.
- Cache-related infrastructure is distributed across:
  - `infra/imaging` – Coil/ImageLoader cache provisioning
  - `infra/work` – WorkManager scheduling for background sync/cleanup
  - Transport modules manage their own caches (TDLib files, HTTP responses)
- Agents MUST NOT:
  - manually delete cache directories,
  - hardcode file paths,
  - bypass existing cache abstractions.
- Cache features (e.g. "Clear TDLib cache", "Clear Xtream cache", "Clear logs") must:
  - be implemented as explicit actions in the respective transport/infra modules,
  - log their actions through the logging system (UnifiedLog),
  - respect concurrency constraints (e.g. mutex/semaphore to avoid overlapping operations).

---

## 6. UI, Features & Profile Management

6.1. UI Structure

- UI features belong in `/feature/**` modules:
  - `feature/home`
  - `feature/library`
  - `feature/live`
  - `feature/detail`
  - `feature/telegram-media`
  - `feature/settings`
  - `feature/audiobooks`
- Agents SHOULD:
  - attach UI changes to the correct existing feature module,
  - only introduce new feature modules if it is structurally necessary and consistent with the established pattern.

6.2. Feature System

- UI must not hardcode pipeline or player behavior; it should rely on features and contracts.
- When the Feature API exists:
  - Always prefer using FeatureIds & FeatureRegistry instead of ad-hoc capability checks.

6.3. Profile Management

- Profile management (profiles, kids/guest modes, restrictions) is considered its own **core feature**.
- Agents must:
  - not hardcode profile-specific behavior in random places,
  - centralize profile-related logic in the appropriate core/profile modules (once they are in place),
  - respect the future profile contracts when they are introduced.

---

## 7. Build, Dependencies & Tools

7.1. Environment

- Keep environment-specific hints simple:
  - Use the existing Gradle/Kotlin/Android toolchain unless there is a **clear technical reason** to change it.
  - Prefer stability and quality over “latest just for the sake of it”.

7.2. Dependency upgrades

- Controlled upgrades are allowed:
  - Agents may propose Kotlin / Gradle / library version bumps,
  - BUT they must:
    - check breaking changes,
    - adjust code and tests,
    - document the rationale (changelog + short summary).
- For significant upgrades, agents must propose the change and wait for user confirmation before applying.

7.3. Quality tools

- Quality is a first-class concern:
  - Detekt, Lint, test suites, and other quality tools must generally remain enabled and respected.
- Temporarily disabling a check is allowed only when:
  - it is necessary to unblock a critical change,
  - the agent clearly documents:
    - why it was disabled,
    - how and when it should be re-enabled,
  - a follow-up task is created to restore the quality gate.

7.4. External tools & optimizers

- When relevant, agents should suggest using external tools:
  - static analysis,
  - formatters,
  - performance profilers,
  - layout optimizers,
  - build-speed analyzers.
- Before integrating new external tools, agents must propose them and wait for user approval.

---

## 8. Testing Policy

8.1. General rule: **Test a lot, but do not overdo it.**

- Non-trivial changes (new pipeline behaviors, new player features, new cache actions, etc.) must include:
  - at least one new unit test, or
  - adapted existing tests that cover the new behavior.

8.2. Focus on:

- Pipelines:
  - RawMetadata mapping,
  - edge cases for history, paging, throttling.
- Normalizer/Resolver:
  - deterministic behavior for given inputs.
- Player:
  - lifecycle, state transitions, error handling.
- Cache:
  - correct domain behavior, no data loss, proper logging.

8.3. Agents should not introduce heavy, flaky end-to-end tests without clear value. Prioritize focused, reliable tests.

---

## 9. Language & Style

9.1. All code, identifiers and comments in the codebase must be written in **English**.

9.2. All new documentation created by agents (Markdown, contracts, feature specs) must be written in **English**.

9.3. When deriving from legacy documents or code:

- Never blindly copy large sections.
- Always:
  - pick only the best ideas,
  - rephrase and refine them in clean English,
  - adapt to the v2 architecture.

---

## 10. Interaction with the User & Proposals

10.1. When an agent identifies:

- a better library version,
- a better tool,
- a significantly improved approach to a feature or subsystem,

then the agent MUST:

1. Propose the improvement in a clear, concise way (pros, cons, risks).
2. Wait for explicit user feedback / confirmation.
3. Only then implement the change.

10.2. For ambiguous architecture decisions:

- Prefer:
  - reading the relevant v2 docs under `docs/v2/**`,
  - making a best-effort architectural decision that respects this `AGENTS.md`.
- If the decision would cause large structural change (new module layout, major refactors), propose a plan first and wait for user approval.

---

## 11. Pre- & Post-Change Checklists (MANDATORY)

For **every non-trivial change**, agents MUST mentally (and in the PR description) run through these checklists.

### 11.1. Pre-change checklist

Before making changes, confirm:

1. **Branch & scope**
   - [ ] Working on `architecture/v2-bootstrap` or a v2-derived feature branch.
   - [ ] No intention to merge to `main`.

2. **Paths**
   - [ ] All planned edits are under allowed v2 paths.
   - [ ] No writes planned under `/legacy/**` or `/app/**`.

3. **Module README (MANDATORY)**
   - [ ] Before modifying any file in a module, **read the module's README.md first**.
   - [ ] Confirm the change respects the module's "Allowed" and "Forbidden" sections.
   - [ ] READMEs are located at:
     - `infra/transport-telegram/README.md`
     - `infra/transport-xtream/README.md`
     - `pipeline/telegram/README.md`
     - `pipeline/xtream/README.md`
     - `infra/data-telegram/README.md`
     - `infra/data-xtream/README.md`
     - `playback/telegram/README.md`
     - `playback/xtream/README.md`
     - `playback/domain/README.md`
     - `core/model/README.md`
     - `core/metadata-normalizer/README.md`

4. **Naming Contract (MANDATORY)**
   - [ ] Read `/contracts/GLOSSARY_v2_naming_and_modules.md` before creating new classes/packages.
   - [ ] All new classes follow naming patterns in Glossary Section 5.2.
   - [ ] All new packages follow patterns in Glossary Section 5.1.
   - [ ] No forbidden patterns from Glossary Section 5.3 are introduced.
   - [ ] Terminology in comments/docs matches Glossary Section 1.

5. **Contracts (MANDATORY)**
   - [ ] All relevant contracts from `/contracts/` have been read (see Section 15.2).
   - [ ] For pipeline changes: Read `MEDIA_NORMALIZATION_CONTRACT.md`.
   - [ ] For player changes: Read all `INTERNAL_PLAYER_*` contracts.
   - [ ] For logging changes: Read `LOGGING_CONTRACT_V2.md`.

6. **Docs**
   - [ ] Relevant v2 docs under `docs/v2/**` have been read:
     - Canonical Media / Normalizer (for pipeline/metadata changes),
     - Internal Player docs (for player changes),
     - Logging/Telemetry/Cache docs (for infra changes).

7. **Architecture rules**
   - [ ] No pipeline-local normalization or TMDB lookups.
   - [ ] No new global mutable singletons.
   - [ ] Logging and telemetry integration identified.

8. **Bridge Duplicate Detection (MANDATORY for transport/pipeline changes)**
   - [ ] Run detection checklist from Section 4.7 before any transport/pipeline change.
   - [ ] Verify no aliased imports (`import ... as ApiXxx`, `import ... as TransportXxx`).
   - [ ] Verify no bridge/conversion functions (`toApi*`, `fromApi*`, `toTransport*`).
   - [ ] Verify single DTO definition location (all in `api/` package).
8. **Plan**
   - [ ] Intended changes are scoped and incremental.
   - [ ] Large refactors or tool upgrades have been discussed with the user (or will be proposed first).

### 11.2. Post-change checklist

After making changes, confirm:

1. **Code & Structure**
   - [ ] No accidental edits in `/legacy/**` or `/app/**`.
   - [ ] No new `com.chris.m3usuite` references outside `legacy/`.
   - [ ] New modules (if any) are wired into `settings.gradle.kts` correctly.

2. **Naming Contract Compliance**
   - [ ] All new classes follow Glossary naming patterns (Section 5.2).
   - [ ] No `*FeatureProvider` in `pipeline/*` modules (use `*CapabilityProvider`).
   - [ ] No `feature/` package in `pipeline/*` modules (use `capability/`).
   - [ ] All vocabulary in code/comments matches Glossary definitions.

3. **Bridge Duplicate Verification (MANDATORY for transport/pipeline changes)**
   - [ ] No new duplicate DTO definitions introduced (see Section 4.7).
   - [ ] All bridge functions removed (no `toApi*`, `fromApi*`).
   - [ ] All aliased imports removed.
   - [ ] DTOs are in canonical `api/` package locations.

4. **Quality**
   - [ ] Code compiles.
   - [ ] Relevant tests added/updated and passing (at least locally).
   - [ ] No new obvious Lint/Detekt violations introduced, or exceptions are documented.

5. **Logging & Telemetry**
   - [ ] New feature paths emit appropriate logs.
   - [ ] Telemetry events are integrated where sensible.

6. **Docs & Changelog**
   - [ ] Any behavioral change is reflected in `docs/v2/**` where needed.
   - [ ] Significant changes are recorded in the v2 changelog (`CHANGELOG.md` or `CHANGELOG_V2.md`).
   - [ ] If the change affects roadmap items, the roadmap has been updated or a note has been added.

7. **User proposal (if applicable)**
   - [ ] If a new tool, dependency upgrade, or alternative approach is involved, a clear proposal has been written and the user’s confirmation has been obtained before the actual change.

---

## 12. Pipeline Migration Philosophy (Telegram & Xtream)

The existing Telegram and Xtream pipelines in v1 are **functionally proven and battle-tested**.

The goal of v2 is **NOT** to redesign everything from scratch, but to:

- **Port** the good, battle-tested behavior from legacy to the new architecture
  (RawMediaMetadata → Normalizer/Resolver → SIP).
- **Remove** architectural debt (singletons, ad-hoc normalization, tight coupling).
- **Only add** new behavior where it clearly improves quality or correctness.

### 12.1. Rules for Pipeline Work

When working on `pipeline/telegram` or `pipeline/xtream`:

1. **Prefer migration over invention**
   - Port existing v1 behavior into v2 modules instead of inventing new flows.
   - Use legacy code and docs under `/legacy/**` as the primary source of truth for how things currently work.

2. **Focus areas**
   - Clean separation between raw data mapping and normalization.
   - Proper scoping, DI, logging/telemetry integration.
   - Removing known pain points (thumbnail floods, cache behavior, etc.).

3. **Do NOT over-test or overcomplicate**
   - Reuse existing test ideas where possible.
   - Add focused tests around the new boundaries (Raw → Normalizer, Normalizer → SIP).
   - Do not try to re-derive entire behavior analytically from scratch.

### 12.2. Reference Artifacts for Pipelines

**Telegram Pipeline:**

- Legacy code: `/legacy/v1-app/.../telegram/**`
- CLI reference: `/legacy/docs/telegram/cli/**` (working TDLib integration)
- JSON exports: `/legacy/docs/telegram/exports/**` (real message fixtures)
- Contracts: `/legacy/docs/telegram/TELEGRAM_PARSER_CONTRACT.md`

**Xtream Pipeline:**

- Legacy code: `/legacy/v1-app/.../xtream/**`
- URL building, auth, category parsing are all proven patterns.

### 12.3. Migration Mindset

> When in doubt, check the legacy pipeline behavior first and adapt it into the v2 boundaries instead of inventing completely new flows.

This means:

- Legacy is **reference, not garbage**.
- The user-facing behavior should stay the same or improve.
- Only the **internal architecture** changes (where responsibilities live).

---

## 13. Player Migration (SIP) – Mandatory Rules

The player migration follows a **strict phased plan** documented in `docs/v2/player migrationsplan.md`.

### 13.1. Player Migration Plan Authority

> **Hard Rule:** The Player Migration Plan is binding. Any conflicts with other contracts or architecture rules MUST be escalated to the user for resolution before proceeding.

- Agents MUST follow the migration phases in order (Phase 0 → Phase 14).
- Agents MUST NOT skip phases or implement features from later phases prematurely.
- If a conflict arises between this plan and other contracts (e.g., `AGENTS.md` Section 4, `INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md`), the agent MUST:
  1. Stop implementation,
  2. Document the conflict clearly,
  3. Ask the user for resolution.

### 13.2. Player Layer Structure (Binding)

```text
┌─────────────────────────────────────────────────────────────┐
│  UI Layer (feature/player-ui, feature/*)                    │
│    - Player chrome, dialogs, snackbars                      │
│    - Consumes StateFlows from player/internal               │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Domain Layer (domain/*)                                    │
│    - UseCases: PlayItemUseCase, KidsGate, SeriesMode        │
│    - Builds PlaybackContext from DomainMediaItem            │
│    - Manages profiles, policies, preferences                │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Player Core (player/internal)                              │
│    - InternalPlayerSession, InternalPlayerState             │
│    - InternalPlaybackSourceResolver                         │
│    - Subtitles, Live, MiniPlayer engine                     │
│    - NO source-specific logic                               │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Player Input (player/input)                                │
│    - Input contexts (TOUCH, REMOTE)                         │
│    - PlayerInputHandler → PlayerCommands                    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Playback Sources (playback/telegram, playback/xtream)      │
│    - PlaybackSourceFactory implementations                  │
│    - DataSources (TelegramFileDataSource, etc.)             │
│    - Uses Transport layer for network/file access           │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  Transport Layer (infra/transport-*)                        │
│    - TelegramTransportClient, XtreamApiClient               │
│    - Raw TDLib/HTTP access                                  │
└─────────────────────────────────────────────────────────────┘
```

### 13.3. Player Hard Rules

| Rule | Description |
|------|-------------|
| **P-HR1** | Player modules (`player/*`, `playback/*`) MUST NOT import `pipeline/**` or `data/**`. |
| **P-HR2** | `core/player-model` contains ONLY primitives: `PlaybackContext`, `PlaybackState`, `PlaybackError`, `SourceType`. No source-specific classes. |
| **P-HR3** | Input handling (Touch, D-Pad) lives in `player/input`, NOT in UI or Transport. |
| **P-HR4** | Player is stateless regarding profiles/preferences. Domain provides these; player applies them. |
| **P-HR5** | DataSources for specific sources (Telegram, Xtream) belong in `playback/*`, NOT in `player/internal`. |
| **P-HR6** | Player does not perform Kids/Guest filtering – Domain does. Player only receives gated `PlaybackContext`. |

### 13.4. Migration Phase Tracking

The migration plan has 15 phases (0-14). Current status is tracked in:
- `docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md`

Before starting work on any player-related change, agents MUST:
1. Check current phase status,
2. Confirm the change fits the current or earlier phase,
3. Update phase status after completing phase milestones.

### 13.5. SIP Reuse Mandate

> **Hard Rule:** Maximize reuse of battle-tested SIP code from v1.

- Port existing `InternalPlayerSession`, `InternalPlayerState`, `SubtitleSelectionPolicy`, `MiniPlayerManager`, etc.
- Do NOT reinvent player behavior that already works in v1.
- Adapt v1 code to v2 architecture (DI, logging, layer boundaries).

### 13.6. Conflict Escalation Protocol

If agent encounters:
- A migration step that contradicts AGENTS.md Section 4 (Layer Boundaries),
- A behavior contract that conflicts with migration plan phases,
- An architectural ambiguity not covered by documentation,

Then agent MUST:
1. **STOP** implementation immediately,
2. **Document** the conflict with specific file/line references,
3. **ASK** the user: "Konflikt gefunden: [description]. Wie soll ich vorgehen?"
4. **WAIT** for explicit user resolution before proceeding.

---

## 14. v2 Naming Contract (BINDING)

This section establishes a **binding naming contract** for all v2 development. Violations must be flagged and corrected immediately.

### 14.1. Authoritative Source

> **Hard Rule:** The document `docs/v2/GLOSSARY_v2_naming_and_modules.md` is the single authoritative source for all naming conventions, vocabulary definitions, and module taxonomy in v2.

All agents, code, documentation, and discussions MUST conform to the vocabulary and naming conventions defined in the Glossary. This contract is **global and binding** for the entire v2 app development.

### 14.2. Mandatory Vocabulary Compliance

Agents MUST use ONLY the vocabulary defined in the Glossary. Key terms include:

| Term | Definition | Where Used |
|------|------------|------------|
| **AppFeature** | User-visible product capability in `feature/*` modules | UI/Feature modules only |
| **AppShell** | Application entry point, main activity, navigation host in `app-v2` | app-v2 infrastructure only |
| **PipelineCapability** | Technical capability in `pipeline/*/capability/` | Pipeline modules only |
| **FeatureId** | Unique identifier for both AppFeatures and PipelineCapabilities | core/feature-api |
| **RawMediaMetadata** | Canonical data class produced by all pipelines | core/model |
| **NormalizedMedia** | Aggregated normalized media after enrichment | core/model |

The complete vocabulary is defined in `docs/v2/GLOSSARY_v2_naming_and_modules.md` Section 1.

### 14.3. Module Naming Rules

All new modules MUST follow the established naming schema:

| Module Type | Package Pattern | Class Naming |
|-------------|-----------------|--------------|
| Pipeline Capability | `pipeline.<name>.capability` | `*CapabilityProvider` |
| App Feature | `feature.<name>` | `*FeatureProvider` |
| Pipeline Mapper | `pipeline.<name>.mapper` | `*Mapper`, `*Extensions` |
| Transport | `infra.transport.<name>` | `*Client`, `*Adapter` |
| Data | `infra.data.<name>` | `*Repository`, `Obx*Entity` |

### 14.4. Forbidden Naming Patterns (Hard Rules)

| Pattern | Forbidden In | Correct Alternative |
|---------|--------------|---------------------|
| `*FeatureProvider` | `pipeline/*` | Use `*CapabilityProvider` |
| `feature/` package | `pipeline/*` | Use `capability/` package |
| `com.chris.m3usuite` | anywhere except `legacy/` | Use `com.fishit.player.*` |
| Ad-hoc capability names | anywhere | Use Glossary-defined terms |

### 14.5. Violation Detection and Alerting

> **Hard Rule:** Agents MUST proactively detect and alert users to naming violations.

When an agent detects code or modules that violate these naming conventions:

1. **Immediate Alert:** The agent MUST immediately inform the user:
   > "⚠️ Naming Violation detected: [specific violation]. This conflicts with the binding naming contract in AGENTS.md Section 14 and GLOSSARY_v2_naming_and_modules.md."

2. **No Silent Violations:** Agents MUST NOT:
   - Proceed with changes that introduce naming violations
   - Ignore existing violations in files they are modifying
   - Use deprecated or non-standard terminology

3. **Mandatory Fix Proposal:** The agent MUST propose a fix aligned with the Glossary:
   > "Proposed fix: Rename `*FeatureProvider` to `*CapabilityProvider` per Glossary Section 5.2."

### 14.6. Pre-Change Naming Audit

Before any code modification, agents MUST verify:

- [ ] All new classes follow the naming patterns in Glossary Section 5.2
- [ ] All new packages follow the patterns in Glossary Section 5.1
- [ ] No forbidden patterns from Glossary Section 5.3 are introduced
- [ ] Terminology used in comments/docs matches Glossary Section 1

### 14.7. Automated Enforcement

Naming conventions are enforced via:

| Tool | Rule | Configuration |
|------|------|---------------|
| **Detekt** | `ForbiddenImport` for v1 namespace | `detekt-config.yml` |
| **Code Review** | Pattern checks for `*CapabilityProvider` vs `*FeatureProvider` | PR process |
| **Agent Audit** | This section (14.5) | Mandatory for all changes |

Automated checks in `detekt-config.yml`:
```yaml
ForbiddenImport:
  - value: 'com.chris.m3usuite.*'
    reason: 'v1 namespace forbidden in v2 modules. See AGENTS.md Section 3.2 and 14.4.'
```

### 14.8. Reference Documents

- **Primary:** `/contracts/GLOSSARY_v2_naming_and_modules.md` (authoritative)
- **Inventory:** `docs/v2/NAMING_INVENTORY_v2.md` (file-to-vocabulary mapping)
- **Feature System:** `docs/v2/architecture/FEATURE_SYSTEM_TARGET_MODEL.md`

---

## 15. Contracts Folder (MANDATORY READING)

### 15.1. Contract Authority

> **Hard Rule:** The `/contracts` folder contains all binding contracts for the v2 rebuild.
> 
> Agents **MUST** read ALL relevant contracts before modifying code in related areas.

The contracts folder is the **single source of truth** for:
- Naming conventions and vocabulary (`GLOSSARY_v2_naming_and_modules.md`)
- Media normalization rules (`docs/v2/MEDIA_NORMALIZATION_CONTRACT.md` – canonical location)
- Logging standards (`LOGGING_CONTRACT_V2.md`)
- Player behavior specifications (`INTERNAL_PLAYER_*_CONTRACT_*.md`)
- Pipeline-specific contracts (`TELEGRAM_PARSER_CONTRACT.md`, `TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md`)

- For v2, the ONLY authoritative contract copies are in docs/v2/. Any /contracts/* file must be forward-only.
- Agents must not create new contract files outside docs/v2/.
- When a contract file is requested, link to docs/v2/<NAME>.md.

### 15.2. Reading Requirements

Before any code modification, agents MUST verify they have read:

| Modification Area | Required Contracts |
|-------------------|-------------------|
| Any code change | `/contracts/GLOSSARY_v2_naming_and_modules.md` |
| Pipeline modules | `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md` |
| Logging code | `/contracts/LOGGING_CONTRACT_V2.md` |
| Player/Playback | All `/contracts/INTERNAL_PLAYER_*` files |
| Telegram features | `/contracts/TELEGRAM_PARSER_CONTRACT.md`, `/contracts/TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md` |

### 15.3. Contract Inventory

| Contract | Scope | Status |
|----------|-------|--------|
| `GLOSSARY_v2_naming_and_modules.md` | Global naming | Binding |
| `MEDIA_NORMALIZATION_CONTRACT.md` | Pipelines → Normalizer | Binding |
| `LOGGING_CONTRACT_V2.md` | All modules | Binding |
| `INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md` | Resume, Kids | Binding |
| `INTERNAL_PLAYER_*_CONTRACT_*.md` | Player phases | Binding |
| `TELEGRAM_PARSER_CONTRACT.md` | Telegram pipeline | Draft |
| `TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md` | Telegram Structured Bundles | Binding |

### 15.4. Conflict Resolution

If an agent encounters a conflict between:
- A contract and existing code → The contract is authoritative
- Two contracts → Escalate to user for resolution
- A user request and a contract → Inform user and request clarification

### 15.5. Pre-Change Contract Audit

Before making changes, agents MUST confirm:

- [ ] All relevant contracts from `/contracts/` have been read
- [ ] Proposed changes do not violate any contract
- [ ] If contracts conflict with requirements, user has been informed

---

This `AGENTS.md` is the single entry point for agents in the v2 rebuild.  
For detailed architecture and feature specifications, always consult `V2_PORTAL.md`, `/contracts/`, and `docs/v2/**` before making changes.
