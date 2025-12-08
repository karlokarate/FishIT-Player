# FishIT-Player v2 – REBUILD & LEGACY CAGE  
## Phase 0: Move ALL Old Code into `legacy/` (PRIO 1)

---

## 0. Purpose of this document

We are rebuilding FishIT-Player **from scratch in the `architecture/v2-bootstrap` branch**.

- Everything that is **v2** lives under:
  - `app-v2/`, `core/`, `infra/`, `feature/`, `player/`, `playback/`, `pipeline/`, `docs/v2/`, `docs/meta/`.

- Everything that is **legacy (v1)** – including the latest v1 PRs from `main`  
  (`unlimited Telegram history`, `lazy thumbnails`, `cache buttons`,  
  `settings/DataStore fix`, etc.) – will be moved into **a single top-level folder `legacy/`**.

**Invariant starting with Phase 0:**

> **Outside of `legacy/` there must be no v1/old code left.**  
> Anything that is not under `legacy/` is considered v2 and may be actively developed.

Inside `legacy/` we will, in a **follow-up step**, keep only the **“gold nuggets”**  
(v1 code and docs that we still want as reference for v2).  
“Legacy-legacy” code – things that were already obsolete or unused in v1 – will be  
**completely removed from the branch**, so only the valuable parts remain.

---

## 1. Target repository layout after Phase 0

```text
/ (repo root)
  V2_PORTAL.md
  AGENTS_V2.md
  ROADMAP.md
  ...

  app-v2/
  core/
  infra/
  feature/
  player/
  playback/
  pipeline/
  docs/
    v2/
    meta/

  scripts/
    api-tests/
    build/

  legacy/
    v1-app/        (old app branch)
    v1-pipelines/  (if any old pipeline modules exist)
    docs/          (v1 / historical docs)
    tools/         (purely v1-related tools/scripts)
    gold/          (later: curated “gold nuggets”)
```

**Invariant:**

- **Only v2 modules** are part of the Gradle build.
- `legacy/` is **fully detached from the build** (no entry in `settings.gradle`).
- Whenever we reuse ideas from v1 for v2, the process is:
  1. Re-implement the behavior in a proper v2 module.
  2. Keep the *original* v1 code as a reference in exactly **one** place under `legacy/`.
  3. Never keep multiple different copies of the same v1 code inside `legacy/`.

---

## 2. Phase 0 – Step by step

### 2.1 Create the `legacy/` folder

1. In the repository root, create a new folder `legacy/`.
2. Add `legacy/README.md` with:

   ```markdown
   # Legacy area

   Everything under `legacy/` belongs to the old v1 generation
   or to archived code/docs.

   - This code and these documents are **not under active development**.
   - They serve only as **reference / “gold source”** for the v2 rebuild.
   - New features, contracts and implementations happen exclusively
     outside of `legacy/` in the v2 modules.
   ```

---

### 2.2 Move the entire v1 app into `legacy/v1-app/`

Goal: **The old app branch must not appear at the root anymore.**

1. Create folder `legacy/v1-app/`.
2. Move the complete `app/` folder → into `legacy/v1-app/`.
3. Special case:  
   If Copilot already dropped “v2-style” code into `app/`  
   (for example `SourceBadge.kt` that imports `com.fishit.player.core.model`), then:
   - first copy that code into an appropriate v2 module  
     (for example `feature/library` or `core/ui`, using `package com.fishit.player...`),
   - then keep the original under `legacy/v1-app/` only as reference  
     **or** delete it there if it is 100% identical and redundant.

4. Update `settings.gradle.kts`:
   - remove the `":app"` / v1 app module from the build.
   - keep only `:app-v2` and other v2 subprojects.

After this, the old app is entirely inside the `legacy/` cage, and it is clear:
**any code under `legacy/v1-app` is v1.**

---

### 2.3 Move all other v1 / legacy code areas into `legacy/`

Goal: **No v1 code, v1 tools or v1 namespaces may exist outside of `legacy/`.**

Heuristic:

- Any file with `package com.chris.m3usuite...` is v1.
- Any module that depends only on the old app and isn’t relevant for v2 is v1/legacy.

Steps:

1. If there are additional v1 modules outside of `app/` (e.g. old pipelines, old tools):
   - Move them to `legacy/v1-pipelines/` or `legacy/tools/` as appropriate.
2. Under `legacy/` you can roughly mirror the original layout, but:
   - do **not** register any of these as Gradle modules,
   - treat everything as archival only.

---

### 2.4 Collect all v1 / historical docs under `legacy/docs/`

Goal: **Every document is clearly either “v2” or “legacy”. Nothing ambiguous.**

1. Create `legacy/docs/`, optionally with subfolders:
   - `legacy/docs/v1/` for original v1 architecture/specs,
   - `legacy/docs/archive/` for old status and completion reports,
   - `legacy/docs/telegram/` for v1 Telegram-specific reference docs,
   - `legacy/docs/ffmpegkit/`, etc., as needed.

2. Move:
   - All documents that describe **v1 architecture** (e.g. old `ARCHITECTURE_OVERVIEW.md`, `IMPLEMENTATION_SUMMARY.md`, v1 Tdlib setup, old release docs, etc.) into `legacy/docs/v1/`.
   - Everything under `docs/archive/` into `legacy/docs/archive/`.
   - Special v1 reports/guides (e.g. v1 Tdlib cluster, v1 upgrade reports) into dedicated subfolders.

3. Add at the top of each legacy MD file:

   ```markdown
   > LEGACY (V1) – not valid for v2.  
   > For current architecture see `V2_PORTAL.md` and `docs/v2/**`.
   ```

---

### 2.5 Place all v2 docs under `docs/v2/` and `docs/meta/`

Goal: **All active v2 specifications live under `docs/v2/**`.**

1. Target structure:

   ```text
   docs/
     v2/
       architecture/
       canonical-media/
       internal-player/
       telegram/
       logging/
       ui/
       ffmpegkit/
       features/        (will be filled in later phases)
     meta/
       build/
       quality/
       agents/
       roadmap/
   ```

2. Move all v2 doc files (`v2-docs/**` etc.) into these subfolders.
3. Move build/agent/tool-related docs into `docs/meta/**`.
4. Create/update `V2_PORTAL.md` in the repository root to point at the canonical `docs/v2/**` docs.

---

### 2.6 Clean up the repo root & sort scripts

Goal: **The repo root remains small and clean; no random files drift around.**

1. Create a `scripts/` folder if not present:
   - `scripts/api-tests/` for test/probe scripts (`test_konigtv.sh`, `test_xtream_api.main.kts`, etc.).
   - `scripts/build/` for build helpers (`safe-build.sh`, `script.sh`, …).
2. Scripts that are **purely v1-related** and won’t be used in v2:
   - either move them into `legacy/tools/`,
   - or remove them completely if they are clearly “legacy-legacy”.

---

## 3. “Gold mines” vs. “legacy-legacy”

After the **coarse move** into `legacy/` in Phase 0, there is a second pass:

> **Only real gold nuggets will remain in `legacy/`, the rest will be deleted.**

### 3.1 Goal of the “gold mine” phase

The `legacy/` folder should:

- be **small, curated and useful**,
- contain only code/docs that we actually want to refer to during v2,
- provide for each topic (e.g. Telegram pipeline) **exactly one canonical place**.

### 3.2 Suggested gold mine structure

```text
legacy/
  gold/
    telegram-pipeline/
      README.md         (why this is gold, which files matter)
      Scanner_v1.kt
      LazyThumbs_v1.kt
      AdvancedSettings_v1.md
    xtream-pipeline/
      ...
    ui-patterns/
      ...
    logging-telemetry/
      ...
  v1-app/               (full old app – can be reduced later)
  docs/                 (full v1 doc set – can be trimmed later)
  tools/
    ...
```

**Rules for the gold mine phase:**

- Code that was *already marked as “legacy”, “deprecated” or “unused” in v1*  
  must **not** be moved into `legacy/gold/`. It should be removed entirely.
- When v2 reuses parts of v1 behavior (e.g. Telegram history scanning logic):
  - the v2 implementation is written fresh in the v2 module,
  - only the original v1 source files go to `legacy/gold/<topic>/`,
  - there must **never** be more than one instance of the same v1 file in `legacy/`.

---

## 4. Rules for working with legacy (humans & Copilot)

### 4.1 Human rules

- **All v2 code lives outside of `legacy/`.**
- When you need v1 behavior as reference:
  1. Look under the appropriate section in `legacy/gold/**`.
  2. Extract behavior and heuristics and encode them as v2 contracts.
  3. Implement new v2 code that follows the v2 architecture (canonical media, central normalizer/resolver, global image loader, etc.).
- If a file in `legacy/` is never touched again, it becomes a candidate for deletion during future cleanup iterations to keep `legacy/` small.

### 4.2 Copilot / Agent rules (English)

These rules should be placed into `.github/copilot-instructions.md` and `AGENTS_V2.md`:

```text
You MAY modify and create code ONLY under:
  - /app-v2/**
  - /core/**
  - /infra/**
  - /feature/**
  - /player/**
  - /playback/**
  - /pipeline/**
  - /docs/v2/**
  - /docs/meta/**
  - /scripts/**

You MUST NOT modify:
  - /legacy/**
  - /app/**
  - /docs/legacy/**
  - /docs/archive/**

When you need behavioral examples (e.g. Telegram history scanner, lazy thumbnails,
cache management, advanced settings):

  - Read-only reference is allowed under /legacy/gold/**.
  - Do NOT copy legacy code as-is into v2. Re-implement it using v2 architecture,
    canonical media models, the central normalizer/resolver,
    and the new global image loader.

Outside of /legacy/**, treat any `com.chris.m3usuite.*` reference as a bug and remove it.
```

---

## 5. Completion criteria for Phase 0

Phase 0 is complete when:

- There is a `legacy/` folder containing:
  - `legacy/v1-app/` (full old app, not built by Gradle),
  - `legacy/docs/` (all v1 / historical docs),
  - optionally `legacy/tools/` and first curated `legacy/gold/` sections.
- **No v1 code** exists outside of `legacy/`:
  - no `com.chris.m3usuite` packages in the v2 build,
  - no v1-specific tools/configs left at the root.
- All active v2 documents are under `docs/v2/**` and discoverable via `V2_PORTAL.md`.
- Copilot/Agents are configured so that they:
  - only touch v2 modules,
  - treat `legacy/` strictly as read-only.

From this point on, the surface of the repository is clear for you as a user:

- **Everything interesting for the rebuild**: at the top (v2).
- **Everything old but still valuable**: downstairs in the `legacy/` folder, at a single, predictable place – gradually refined down to real gold nuggets only.
