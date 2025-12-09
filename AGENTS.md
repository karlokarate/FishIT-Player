# AGENTS – FishIT-Player v2 (architecture/v2-bootstrap)

This document defines the rules for ALL automated agents (Copilot, Codex, etc.) working on the **v2 rebuild** of FishIT-Player in this repository.

It applies to:

- The branch `architecture/v2-bootstrap`
- Any feature / topic branches derived from the v2 rebuild (e.g. `feature/*` based on `architecture/v2-bootstrap`)

It does **not** define behavior for other repositories or for the historic v1 codebase.

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
  - never call TMDB or external metadata services directly (always via normalizer/resolver).

4.3. Internal Player (SIP) & Playback

- Playback logic is centralised in the Internal Player (SIP) under `/player/internal` and related `playback/domain` modules.
- Agents MUST NOT implement player behavior in:
  - pipelines,
  - feature modules,
  - random helpers.
- Instead, follow:
  - `docs/v2/internal-player/**`

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

---

## 5. Logging, Telemetry & Cache

5.1. Logging (mandatory)

- All new v2 behavior must integrate with the unified logging system.
- Agents MUST respect and extend:
  - `docs/v2/LOGGING_CONTRACT_V2.md`
  - `docs/v2/logging/**`
- No ad-hoc `println` or unstructured logging. Use the logging abstractions.

5.2. Telemetry (mandatory)

- Important code paths (player, pipelines, UI, cache) must emit telemetry:
  - Player stats,
  - Pipeline events,
  - UI responsiveness / jank, where relevant.
- Agents must use and extend telemetry services under:
  - `core/telemetry` (or equivalent v2 modules)
  - `docs/v2/telemetry/**` (when present)

5.3. Cache & Storage

- All cache operations must go through the central cache abstractions under `/infra/cache/**`.
- Agents MUST NOT:
  - manually delete cache directories,
  - hardcode file paths,
  - bypass cache APIs.
- Cache features (e.g. “Clear TDLib cache”, “Clear Xtream cache”, “Clear logs”) must:
  - be implemented as explicit actions in the cache manager,
  - log their actions through the logging/telemetry system,
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

3. **Docs**
   - [ ] Relevant v2 docs under `docs/v2/**` have been read:
     - Canonical Media / Normalizer (for pipeline/metadata changes),
     - Internal Player docs (for player changes),
     - Logging/Telemetry/Cache docs (for infra changes).

4. **Architecture rules**
   - [ ] No pipeline-local normalization or TMDB lookups.
   - [ ] No new global mutable singletons.
   - [ ] Logging and telemetry integration identified.

5. **Plan**
   - [ ] Intended changes are scoped and incremental.
   - [ ] Large refactors or tool upgrades have been discussed with the user (or will be proposed first).

### 11.2. Post-change checklist

After making changes, confirm:

1. **Code & Structure**
   - [ ] No accidental edits in `/legacy/**` or `/app/**`.
   - [ ] No new `com.chris.m3usuite` references outside `legacy/`.
   - [ ] New modules (if any) are wired into `settings.gradle.kts` correctly.

2. **Quality**
   - [ ] Code compiles.
   - [ ] Relevant tests added/updated and passing (at least locally).
   - [ ] No new obvious Lint/Detekt violations introduced, or exceptions are documented.

3. **Logging & Telemetry**
   - [ ] New feature paths emit appropriate logs.
   - [ ] Telemetry events are integrated where sensible.

4. **Docs & Changelog**
   - [ ] Any behavioral change is reflected in `docs/v2/**` where needed.
   - [ ] Significant changes are recorded in the v2 changelog (`CHANGELOG.md` or `CHANGELOG_V2.md`).
   - [ ] If the change affects roadmap items, the roadmap has been updated or a note has been added.

5. **User proposal (if applicable)**
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

This `AGENTS.md` is the single entry point for agents in the v2 rebuild.  
For detailed architecture and feature specifications, always consult `V2_PORTAL.md` and `docs/v2/**` before making changes.
