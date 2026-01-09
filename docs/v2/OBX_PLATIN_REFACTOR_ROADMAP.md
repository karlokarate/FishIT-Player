---

🧭 AGENT BRIEFING (AUTHORITATIVE)

> Scope & Authority

This roadmap is the binding execution blueprint for Issue #621 (OBX PLATIN Refactor). It defines what must be built, what must NOT be built, and in which order.

Hard Rules for Agents & Contributors:

NX_Work is the only UI SSOT. UI must never read from legacy Obx* entities.

Every ingest candidate must create exactly one IngestLedger entry
(ACCEPTED | REJECTED | SKIPPED) – no silent drops.

An accepted ingest does NOT always create a new NX_Work.
It triggers one deterministic resolution attempt that links or creates.

TMDB IDs are authorities, not blind canonical IDs.
Promotion to primary identity happens only after validation.

Live content is migrated as-is.
Channel-level canonical merging is explicitly out of scope for this roadmap.

UI navigation / session state (screen, row, tile, MiniPlayer return context) is explicitly NOT stored in OBX and must use DataStore/session mechanisms.


Forbidden in this roadmap:

Backward compatibility layers

Partial legacy reuse

Firebase/cloud writes (outbox only)

Live channel canonical merge logic


If any task or change conflicts with this briefing, the briefing wins.




---

OBX Layering PLATIN Refactor – Execution Roadmap

Version: 1.1
Status: Planning (Binding for Issue #621)
Parent Issue: #621
Created: 2026-01-08


---

Executive Summary

This roadmap defines the phase-by-phase execution plan to refactor the ObjectBox persistence layer from 23 legacy Obx* entities into 16 unified NX_* entities implementing a proper SSOT Work Graph.

Key Metrics

From: 23 legacy entities with scattered responsibilities

To: 16 NX_* entities with strict ownership

Duration: 28–39 development days

Phases: 7 (0–6), sequential and rollback-safe

Risk Mitigation: Runtime mode toggles + hard kill-switch


Core Transformations

1. Unified Work Graph
NX_Work as central UI SSOT, created via deterministic resolution (link-or-create).


2. Multi-Account Ready
accountKey is mandatory in every sourceKey.


3. Playback Variants
Separate NX_WorkVariant per quality / encoding / language.


4. Ingest Ledger
Explicit ACCEPT / REJECT / SKIP tracking – no silent drops.


5. Profile System Simplification
Unified NX_ProfileRule replacing multiple Kid/Permission entities.


6. Cloud-Ready (Preparation Only)
NX_WorkUserState + NX_CloudOutboxEvent prepared.
No cloud writes implemented in this roadmap.




---

See Full Documentation

This is a summary and sequencing document.
All executable task details live in the GitHub issue.

Parent Issue: #621

Roadmap (this file): execution order & scope

Contract (to be created): /contracts/NX_SSOT_CONTRACT.md

Architecture Authority: AGENTS.md (Section 4)



---

Phase Overview

**Phase 0: Contracts & Guardrails (2–3 days)** ✅ **COMPLETED** (2026-01-09, Commit 068a525)

Phase 1: NX Schema + Repositories (5–7 days)

Phase 2: Ingest Path (4–5 days)

Phase 3: Migration Worker (5–7 days)

Phase 4: Dual-Read UI (7–10 days)

Phase 5: Stop-Write Legacy (2–3 days)

Phase 6: Stop-Read Legacy + Cleanup (3–4 days)



---

Phase 0 – Contracts, Keys, Guardrails (2–3 days) ✅ **COMPLETED**

**Completion Date:** 2026-01-09  
**Commit:** 068a525dc906f86b5bec699218b68a98e1838486  
**Status:** ✅ ALL TASKS COMPLETE

Goals

Remove ambiguity before code exists.

Prevent agents from inventing rules.


Deliverables ✅

✅ **NX_SSOT_CONTRACT.md** (contracts/ and docs/v2/)

✅ Deterministic key formats (workKey, authorityKey, sourceKey, variantKey)

✅ IngestReasonCode enum (16 reason codes defined)

✅ Classification heuristics (Clip / Episode / Movie thresholds)


✅ **Runtime mode toggles in DataStore:**

✅ CatalogReadMode (LEGACY/DUAL_READ/NX_ONLY)

✅ CatalogWriteMode (LEGACY/DUAL_WRITE/NX_ONLY)

✅ MigrationMode (OFF/INCREMENTAL/FULL_REBUILD)

✅ NxUiVisibility (HIDE/SHOW)


✅ **Detekt rules documented:**

✅ No BoxStore outside repositories

✅ No secrets in logs / OBX

✅ Ledger write mandatory for ingest


✅ **16 NX_* entities defined** (NxEntities.kt, 827 lines)

✅ **Enums and key generators** (NxEnums.kt, NxKeyGenerator.kt)

✅ **Kill-switch infrastructure** (CatalogModePreferences.kt)

✅ **Comprehensive documentation** (5,000+ lines across multiple docs)


Acceptance ✅

✅ Contract exists and is referenced by Issue #621

✅ Mode toggles work at runtime (DataStore-backed)

✅ CI enforcement documented (OBX_PLATIN_DETEKT_GUARDRAILS_CONTRACT.md)

✅ Kill-switch rollback documented and tested (OBX_KILL_SWITCH_GUIDE.md)

✅ All 16 NX_* entities created with proper indexes and uniqueness constraints

✅ Safe defaults configured (LEGACY mode only, zero production impact)


**Phase 0 Artifacts:**

- `contracts/NX_SSOT_CONTRACT.md` (v1.0, binding)
- `docs/v2/NX_SSOT_CONTRACT.md` (detailed version)
- `docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md` (this file)
- `docs/v2/OBX_KILL_SWITCH_GUIDE.md` (emergency procedures)
- `docs/v2/OBX_PLATIN_DETEKT_GUARDRAILS_CONTRACT.md` (CI rules)
- `docs/v2/OBX_PLATIN_REFACTOR_GITHUB_ISSUE.md` (Issue #621 template)
- `core/persistence/obx/NxEntities.kt` (16 entity definitions)
- `core/persistence/obx/NxEnums.kt` (WorkType, IngestDecision, etc.)
- `core/persistence/obx/NxKeyGenerator.kt` (deterministic key generation)
- `core/persistence/config/CatalogModePreferences.kt` (kill-switch)
- `core/debug-settings/nx/*` (migration mode UI controls)

**Corrected Documentation:**

- `docs/v2/COMMIT_068a525_CORRECTED_MESSAGE.md` (comprehensive commit message)
- `docs/v2/TODAY_2026-01-09_DEEP_DIVE_ANALYSIS.md` (deep dive analysis)



---

Phase 1 – NX Schema + Repositories (5–7 days)

**Status:** 🔲 PENDING (Awaiting Phase 0 completion) → ✅ **READY TO START**

Goals

Establish new persistence foundation without touching UI.


Deliverables

16 NX_* ObjectBox entities with indexes and uniqueness

Repository interfaces + implementations for:

Work graph

Variants

Relations

User state

Runtime state

Ledger

Profiles & rules

Source accounts

Outbox

Redirects (merge)



Acceptance

ObjectBox store boots cleanly

Uniqueness constraints enforced by tests

No UI or feature code accesses BoxStore directly



---

Phase 2 – NX Ingest Path (4–5 days)

Goals

Make NX the authoritative write path.


Rules

< 60s → REJECT

Not playable → REJECT

ACCEPTED → resolve NX_Work (link or create)


Deliverables

Normalizer gate enforcement

Mandatory accountKey in all sourceKeys

Cache cleanup hook after successful NX persist


Acceptance

100% ingest ledger coverage

Every ACCEPTED item results in Work + SourceRef + Variant

No SourceRef exists without accountKey



---

Phase 3 – Migration Worker (5–7 days)

Goals

Migrate legacy data safely and observably.


Deliverables

One-shot migration worker:

Batched

Resumable

Idempotent


Mapping of all 23 legacy entities

Read-only verifier worker:

Finds works without SourceRef/Variant

Reports invariant violations



Acceptance

Migration resumes after crash

NX_Work / SourceRef / Variant counts > 0

Verifier produces actionable report



---

Phase 4 – Dual-Read UI (7–10 days)

Goals

Prove NX is a real SSOT before cutting legacy.


Deliverables

UI repositories support CatalogReadMode

NX-backed implementations for:

Home

Library

Detail

Series navigation

Search

Live TV


Debug dump screen:

Entity counts

Invariant violations

NEEDS_REVIEW works



Acceptance

All screens work in LEGACY and NX mode

No multi-second blocking queries

Debug visibility for unclassified works



---

Phase 5 – Stop-Write Legacy (2–3 days)

Goals

Prevent data divergence.


Deliverables

Optional short DUAL_WRITE validation

Switch to CatalogWriteMode = NX_ONLY

Legacy write paths disabled (warn if hit)

Kill-switch rollback test


Acceptance

NX_ONLY active

Legacy writes impossible

Rollback works without data loss



---

Phase 6 – Stop-Read Legacy + Cleanup (3–4 days)

Goals

Remove legacy permanently.


Deliverables

Remove all UI reads of Obx*

Cleanup worker deletes legacy boxes

Optional schema removal of legacy entities


Acceptance

App is fully NX-only

Storage reclaimed

No legacy imports remain



---

Explicit Non-Goals (Important)

Live channel canonical merge – deferred to separate roadmap

Firebase sync implementation – outbox only

UI navigation/session state in OBX – must use DataStore

Backward compatibility – intentionally dropped



---

Final Note

This roadmap is intentionally strict.
Any shortcut that violates SSOT, determinism, or guardrails is out of scope and must be rejected.
