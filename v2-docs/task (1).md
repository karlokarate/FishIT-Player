# Phase 2 – Core Persistence Implementation Task (Safe, Clean, v2-Compliant)

You are a GitHub Copilot Agent working on the **FishIT-Player** project.

Your mission is to **start Phase 2** according to the specification in:

- `v2-docs/PHASE_2_TASK_PIPELINE_STUBS.md`

and correctly implement **Phase 2 – Task 1 (Core Persistence / ObjectBox)** in a way that:

- uses the **correct branch**,  
- avoids committing **any build artifacts or generated files**,  
- and strictly follows the **v2 architecture and documentation**.

Do **not** assume the previous PR or its state – we are treating that as discarded.

---

## Step 0 – Work on the correct branch

1. Confirm that you are working on the branch:
   - `architecture/v2-bootstrap`

2. If you need a feature branch, create it from this branch (for example):
   - `feature/v2-phase2-core-persistence`

3. Do **not** modify or rebase `main` as part of this task.

---

## Step 1 – Git hygiene and .gitignore hardening

Before adding any new persistence code, ensure the repository cannot accidentally commit build artifacts again.

1. Open the root-level `.gitignore` and ensure it contains, at minimum, a **universal Gradle build ignore** pattern:

   ```gitignore
   # Gradle build outputs
   **/build/
   ```

   Do not remove other relevant ignore rules (e.g. `.gradle/`, `local.properties`, etc.).

2. Check for already tracked build artifacts in the current branch:

   - Use `git ls-files | grep "/build/"` to detect any tracked `build/` files.
   - If any tracked build outputs are found (e.g. `core/persistence/build/**`, `core/model/build/**`):
     - remove them from Git index without deleting local files, e.g.:
       ```bash
       git rm -r --cached core/persistence/build core/model/build
       ```

3. Commit this cleanup (if needed) with a clear message, for example:
   - `Cleanup: ensure Gradle build outputs are ignored for v2`

4. Only proceed once:
   - `.gitignore` properly ignores **all** `build/` directories (`**/build/`), and
   - `git ls-files` no longer shows any `build/` paths.

---

## Step 2 – Read Phase 2 specification and v2 architecture docs

Read carefully:

- `v2-docs/PHASE_2_TASK_PIPELINE_STUBS.md`
- `docs/APP_VISION_AND_SCOPE.md`
- `docs/ARCHITECTURE_OVERVIEW_V2.md`
- `docs/IMPLEMENTATION_PHASES_V2.md`

From these, extract the requirements for **Phase 2 – Task 1 (Core Persistence)**:

- ObjectBox as the persistence engine.
- Required entities (around 17 – Vod, Series, Episode, Live, TelegramMessage, Profile, ResumeMark, ScreenTimeEntry, etc.).
- Repository interfaces and responsibilities (Profile, Resume, Content, Screentime).
- ContentId scheme (`"vod:123"`, `"series:456:1:3"`, etc.).
- Module and dependency boundaries:
  - All ObjectBox usage must live in `:core:persistence`.
  - No pipelines, features or player modules talk directly to ObjectBox.

Do **not** implement anything before this step is complete.

---

## Step 3 – Implement ObjectBox setup in :core:persistence

In the `:core:persistence` module:

1. Configure `build.gradle.kts`:

   - Apply the ObjectBox plugin according to the project’s chosen version (5.x as used elsewhere).
   - Add the correct ObjectBox dependencies.
   - Keep configuration minimal and aligned with v2 rules (no custom build output directories, no committing generated schema).
   - Ensure kapt/ksp integration is consistent with the rest of the v2 project.

2. Create `ObxStore.kt` (or equivalent file) that:

   - Provides a single `BoxStore` instance for v2.
   - Manages BoxStore lifecycle correctly.
   - Is exposed via Hilt as `@Singleton` from `:core:persistence` only.

3. Do **not** add any code or config that writes to tracked locations under `build/`.  
   All ObjectBox generated content must remain under ignored `build/` paths.

---

## Step 4 – Implement ObjectBox entities for v2

In `:core:persistence` (file layout may follow project conventions):

1. Port the required entities from v1 (read-only reference) and adapt them to v2:

   - Example entities (actual list from v1/v2 docs):
     - `ObxVod`
     - `ObxSeries`
     - `ObxEpisode`
     - `ObxLive`
     - `ObxTelegramMessage`
     - `ObxProfile`
     - `ObxResumeMark`
     - `ObxScreenTimeEntry`
     - etc.

2. Ensure for each entity:

   - Primary keys and relations match v2 domain requirements.
   - Field types, nullability and naming reflect v2 core models (no v1-only leftovers).
   - The entities support the **ContentId** addressing scheme where needed.

3. Do not introduce any UI, Player, or Pipeline logic into entities.

---

## Step 5 – Implement repository interfaces and implementations

Following v2 docs and `PHASE_2_TASK_PIPELINE_STUBS.md`:

1. Define repository interfaces in the appropriate shared module (as specified by v2 docs; e.g. `:core:model` or similar):

   - `ProfileRepository`
   - `ResumeRepository`
   - `ContentRepository`
   - `ScreenTimeRepository`

2. Implement their ObjectBox-backed versions in `:core:persistence`:

   - `ObxProfileRepository`
   - `ObxResumeRepository`
   - `ObxContentRepository`
   - `ObxScreenTimeRepository`

3. Behavior expectations:

   - All DB work runs on `Dispatchers.IO` and uses `suspend` where appropriate.
   - Business rules from v1 that are explicitly required in v2 must be preserved, e.g.:
     - Resume positions: save only after >10s watched, clear when remaining <10s.
     - Default profile auto-creation with type ADULT if none exists.
     - Screentime tracked as minutes per day for Kids profiles.

4. Repositories must:

   - Depend only on ObjectBox + core models.
   - Not depend on pipelines, player, or feature modules.
   - Have APIs that align with v2 playback and domain expectations (no UI-specific naming).

---

## Step 6 – Hilt DI wiring and module boundaries

1. Create Hilt modules (e.g. `PersistenceModule`, `ObxStoreModule`) in `:core:persistence` that:

   - Provide `BoxStore` as a `@Singleton`.
   - Provide each repository implementation bound to its interface.
   - Are annotated with `@Module` + `@InstallIn(SingletonComponent::class)`.

2. Verify module boundaries:

   - All ObjectBox / BoxStore usage must remain inside `:core:persistence`.
   - Pipelines and features may only see **repository interfaces** and core models, not ObjectBox classes.

3. Do not introduce reverse dependencies (no `:core:persistence` depending on pipelines or features).

---

## Step 7 – Tests and validation

1. Add or update tests for:

   - `ObxProfileRepository`
   - `ObxResumeRepository`
   - `ObxContentRepository`
   - `ObxScreenTimeRepository`

2. Test at least:

   - Basic CRUD behavior.
   - Resume rules (thresholds, clearing behavior, ContentId routing).
   - Default profile creation logic.
   - Screentime accumulation and limit handling (if specified in v2 docs).

3. Ensure tests are deterministic and do not rely on global mutable state that would break under parallel runs.

4. Run at minimum:

   ```bash
   ./gradlew :core:persistence:test
   ```

   and, if configured, broader v2 test tasks.

---

## Step 8 – Final git hygiene check (no build artifacts committed)

Before considering this task complete:

1. Run:

   ```bash
   git status
   git ls-files | grep "/build/" || true
   ```

2. Confirm that:

   - No `build/` paths are tracked in Git.
   - Only source files (`.kt`, config, and docs) are added/changed as part of this task.

3. If any build outputs were accidentally staged, remove them from the index with `git rm --cached` and re-run `git status` until clean.

---

## Step 9 – Documentation and status update

1. If a Phase 2 status or progress doc exists (e.g. `v2-docs/PHASE_2_IMPLEMENTATION_STATUS.md`), update it to reflect:

   - Phase 2 – Task 1 (Core Persistence) implemented and tested.
   - Short summary of what was done.

2. Optionally add a brief note (or update existing) under `docs/v2-cleanup/OBJECTBOX_REVIEW_SUMMARY.md` to indicate that:

   - the missing persistence code has now been implemented correctly on the right branch,
   - build outputs are properly ignored by `.gitignore`.

---

## Constraints

- Do NOT modify or refactor any v1 persistence code.
- Do NOT change v2 architecture contracts or dependency rules.
- Do NOT commit build/ or generated ObjectBox artifacts.
- Focus strictly on:
  - implementing v2 Core Persistence (ObjectBox) for Phase 2 – Task 1,
  - ensuring `.gitignore` and Gradle configuration prevent a repeat of previous PR issues,
  - keeping all changes scoped to the correct branch and v2 modules.
