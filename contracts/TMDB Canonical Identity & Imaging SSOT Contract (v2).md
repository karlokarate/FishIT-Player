
# TMDB Canonical Identity & Imaging SSOT Contract (v2)

**Filename (proposed):** `/contracts/TMDB_CANONICAL_ID_AND_IMAGING_CONTRACT.md`
**Status:** Proposed Binding Contract (to be added to `/contracts` inventory)
**Version:** 1.0
**Last Updated:** 2025-12-16

## 0. Relationship to Existing Binding Contracts

This contract is **additive** and must be read together with:

* **Naming & vocabulary:** `GLOSSARY_v2_naming_and_modules.md`
* **Canonical media + normalization responsibilities:** `MEDIA_NORMALIZATION_CONTRACT.md`
* **Logging rules:** `LOGGING_CONTRACT_V2.md`
* **Agent rules + documentation language requirements:** `AGENTS.md`

If any wording here conflicts with an older binding contract, **the binding contract with explicit authority and newer version wins**. If versions are equal, escalate and resolve explicitly.

---

## 1. Purpose

Define **non-negotiable, deterministic rules** for:

1. **Canonical identity** using **TMDB as the preferred Canonical ID** when available.
2. **Images/posters/backdrops** using **TMDB as the single source of truth (SSOT)** when available.
3. **Conflict and race-condition handling** when the same logical work is discovered from multiple pipelines (notably **Xtream vs Telegram/TDLib**) to avoid UI flicker and non-deterministic merges.

---

## 2. Scope

Binding for all v2 modules that touch media identity or images:

* `pipeline/*` (Telegram, Xtream, IO, Audiobook, …)
* `core/metadata-normalizer` (normalizer + TMDB resolver)
* `core/model`, `core/persistence` (canonical storage, source refs)
* UI feature modules consuming canonical media + images (`feature/*`, `app-v2`)

---

## 3. Definitions

* **Canonical ID**: The **single stable identifier** representing a logical work (movie or episode) across all pipelines. See `CanonicalMediaId` concept in the normalization contract.
* **TMDB ID**: External ID from TMDB. When available it is the **preferred canonical key**.
* **SSOT (Single Source of Truth)**: A designated authority that overrides other sources for a data category.
* **Pipeline metadata**: Source-provided metadata (Telegram captions, Xtream EPG/VOD fields, filenames, etc.).
* **Placeholder image**: Any non-TMDB image used only to render a tile quickly until TMDB assets are resolved (e.g., Telegram mini-thumb).

---

## 4. Canonical Identity Rules

### 4.1 TMDB-first Canonical Key

If a valid TMDB ID is known for a work, then:

* Movies: `canonicalId.key = "tmdb:movie:<tmdbId>"`
* TV (series root for episodes): `canonicalId.key = "tmdb:tv:<tmdbId>"`
* This is the **highest priority identity**, overriding all other identity strategies.

> Legacy compatibility: older data may contain untyped `tmdb:<id>`. Implementations must treat it
> as a legacy alias for lookups and converge new writes to the typed format.

### 4.2 Where TMDB IDs May Come From

TMDB IDs may only be introduced via:

1. **Pass-through external IDs** from trusted upstream sources (e.g., Xtream/Plex when they explicitly provide TMDB IDs).
2. **TMDB resolver** inside `core/metadata-normalizer`.

**Pipelines MUST NOT** “guess” TMDB IDs and MUST NOT call TMDB directly.

### 4.3 Deterministic Fallback Identity

If no TMDB ID is available, canonical identity follows the existing canonical identity rules defined in `MEDIA_NORMALIZATION_CONTRACT.md` (title/year (+ S/E for episodes), deterministic and stable).

> Note: Implementations may internally hash these keys for storage/indexing, but the **semantic canonical identity** must remain deterministic and reconstructable from normalized inputs.

### 4.4 Canonical Upgrade (Fallback → TMDB)

If a work was previously stored with a fallback canonical ID and later receives a TMDB match:

* The system **MUST merge** the fallback record into the TMDB canonical record.
* The system **MUST preserve** user state (resume, watched, lists) by maintaining an **alias/redirect** from the old canonical key to the new TMDB canonical key.
* The merge result MUST be deterministic.

---

## 5. Metadata Authority and Merge Policy

### 5.1 Final Authority (Canonical Metadata)

When TMDB data is available:

* **TMDB is SSOT** for:

  * canonical title
  * year / release date
  * season/episode structure
  * plot/overview
  * genres
  * primary credits where used (director/creator/cast)
  * canonical posters/backdrops/logos (see imaging section)

Pipeline metadata may remain as **variant/source-specific annotations**, but must not override canonical fields.

### 5.2 Provisional Authority (Before TMDB Resolves)

To avoid race-condition flicker, the system MUST select a **single provisional metadata winner** for initial UI display when TMDB is not yet available.

**Provisional priority order (default):**

1. **Xtream**
2. **Telegram / TDLib**
3. **IO**
4. **Audiobook**
5. Others

Rationale: Xtream metadata is typically available faster and more structured for VOD/series. (This is a UX/perf policy; final canonical is still TMDB-first.)

### 5.3 Merge Rules (What Must Never Happen)

* Never “ping-pong” canonical fields between sources once TMDB exists.
* Never overwrite a valid TMDB ID with a different ID unless explicitly proven invalid and logged (resolver-level decision).

---

## 6. Imaging Contract: TMDB as SSOT

### 6.1 TMDB Images Are the Default

If TMDB images exist for a canonical work:

* UI MUST use **TMDB poster/backdrop/logo** as the canonical images.
* These images MUST be persisted/referenced as canonical media assets (not pipeline assets).

### 6.2 Pipeline Images Are Placeholders Only

Pipelines may provide images only as **placeholders**, including:

* Telegram mini-thumbnails (blurred/low-res) embedded in DTOs
* Provider icons or generic tiles (source branding)

Pipeline images:

* MUST NOT become the canonical poster/backdrop when TMDB images are available.
* MUST be treated as temporary UI assets (until TMDB images arrive).

### 6.3 Where Image Fetching Is Allowed

* TMDB image URL building, selection, caching policy MUST live in central v2 infrastructure (e.g., `core/ui-imaging` + normalizer/resolver), not in pipelines.
* Pipelines MUST NOT embed TMDB base URLs, image-size logic, or TMDB CDN assumptions.

---

## 7. UI Rendering Rules (Preferred Poster Rule)

For list tiles and detail pages:

1. If `tmdbPoster` exists → render it.
2. If not yet available → render pipeline placeholder (mini-thumb or generic icon).
3. When `tmdbPoster` becomes available → replace with a smooth transition (no layout jump).

UI MUST be resilient to asynchronous canonical upgrades (fallback → TMDB) without “duplicating” the same work in the UI.

---

## 8. Persistence Requirements

Persistence MUST store:

* `canonicalId` (TMDB-based when available, else deterministic fallback)
* TMDB-enriched canonical fields and canonical images when available
* SourceRefs / variants linking each pipeline item to the canonical work (pipeline tag + stable pipeline sourceId)
* Alias mapping for canonical upgrades (fallback → TMDB)

Persistence MUST NOT store:

* volatile pipeline internals as canonical keys (e.g., Telegram fileId)
* secrets / credentials (also logging must avoid them)

---

## 9. Race Conditions & Concurrency Guarantees

### 9.1 Simultaneous Discovery (Xtream + Telegram)

When the same logical work is discovered concurrently:

* Provisional metadata winner MUST be selected deterministically (Section 5.2).
* When TMDB resolves, TMDB becomes SSOT and replaces canonical fields (Section 5.1).
* Both sources MUST remain as variants linked to the same canonical work.

### 9.2 Idempotency

Repeated ingestion of the same source item MUST NOT create duplicate canonical works if canonical identity can be established.

### 9.3 Observability

All canonical upgrades and conflict resolutions MUST be logged via `UnifiedLog` (no `Log.*`, no `Timber.*` outside infra/logging).

---

## 10. Guardrails & CI Checks

### 10.1 Hard Prohibitions (Pipeline Layer)

Pipeline modules MUST NOT:

* call TMDB
* implement title normalization heuristics
* implement canonical grouping
* implement TMDB image selection/URL building

### 10.2 Suggested Automated Checks

Add CI checks (grep-based or Detekt custom rule) that fail if:

* `api.themoviedb.org` appears outside `core/metadata-normalizer` (and any explicitly allowed infra module).
* `tmdb.org` CDN base patterns appear outside the imaging system module(s).
* pipeline code imports resolver/normalizer impl packages.

(Exact allowlists must be small and explicit.)

---

## 11. Example Flows

### 11.1 User Adds Xtream + Telegram Accounts

1. Xtream pipeline ingests VOD items quickly (provisional winner).
2. Telegram pipeline ingests files later; items map to raw metadata with placeholders.
3. Normalizer resolves TMDB IDs → canonical IDs become `tmdb:<id>`.
4. UI tiles initially show Xtream metadata + placeholder, then transition to TMDB poster/backdrop and canonical fields.

### 11.2 Telegram First, TMDB Later

1. Telegram produces raw items without TMDB ID → fallback canonical ID is used.
2. Placeholder mini-thumb renders immediately.
3. Resolver later finds TMDB → canonical upgrade merges records and preserves user state.
4. UI transitions to TMDB imagery and canonical fields.

---

## 12. Non-Goals

* This contract does not define TMDB API rate-limit strategy, caching TTLs, or image sizes; those belong to the imaging and resolver design docs.
* This contract does not change the v2 layer boundaries; it reinforces them.

---

      # Step-by-step (recommended rollout)

1. Add this file under `/contracts/` and register it in the contracts inventory README.
2. Add CI guardrails (grep/Detekt) for TMDB URL leakage + pipeline violations.
3. Ensure canonical-upgrade alias mapping exists and is tested (fallback → TMDB).
4. Wire UI “preferred poster rule” everywhere tiles are rendered.

---

## Externe Tools (sinnvoll für v2)

* **Quality/Style:** Detekt + KtLint + Spotless (format-on-commit), plus Gradle **Dependency Analysis Plugin** (tote/unnötige deps).
* **Perf:** Android Studio Profiler, **Macrobenchmark** + **Baseline Profiles**, StrictMode, LeakCanary.
* **Debug UX:** In-App Log Viewer (ihr habt die Grundlage mit UnifiedLog), plus optional Sentry/Firebase Crashlytics (nur infra/logging).

---

## Copilot/Codex Task (English, precise)

**Goal:** Add the binding contract file and minimal guardrails.

**Instructions:**

1. Create `/contracts/TMDB_CANONICAL_ID_AND_IMAGING_CONTRACT.md` with the exact content from this chat section “TMDB Canonical Identity & Imaging SSOT Contract (v2)”.
2. Update the `/contracts` contract inventory/README to include the new contract in the “Core Contracts” section (keep formatting consistent).
3. Add a CI-safe guardrail script under `/scripts/quality/check_tmdb_leaks.sh` that fails if TMDB API/CDN strings are used outside allowed modules (`core/metadata-normalizer`, `core/ui-imaging`, `infra/*` if needed). Keep allowlist explicit and small.
4. Wire the script into an existing GitHub Actions workflow or create a lightweight workflow step that runs on PRs.
5. Ensure all new logging uses `UnifiedLog` only.

**Acceptance Criteria:**

* New contract file exists and is referenced in the contracts inventory.
* Guardrail script exists and fails when a disallowed file contains TMDB URL patterns.
* No layer boundary violations introduced.
