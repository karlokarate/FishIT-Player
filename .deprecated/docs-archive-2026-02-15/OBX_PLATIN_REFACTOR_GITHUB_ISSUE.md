---

GitHub Issue Body for OBX PLATIN Refactor (CORRECTED)

Title: OBX Layering PLATIN Refactor – Execution Tasks (Sub-issue #621)

Labels: enhancement, refactor, persistence, high-priority


---

Context

This sub-issue for #621 defines all necessary and sequentially executable tasks to refactor our OBX layering to PLATIN standard according to the comprehensive blueprint in #621.

No backward compatibility, no deprecation. Complete replacement of old logic.


---

Architecture Overview

Transformation: 23 Legacy Entities → 16 NX_ Entities

See full mapping in docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md

Key Changes:

Unified Work Graph (NX_Work as the only UI SSOT)

Multi-Account Ready (accountKey mandatory in all sourceKeys)

Playback Variants (separate quality/encoding variants)

Ingest Ledger (explicit ACCEPT / REJECT / SKIP – no silent drops)

Cloud-Ready (Firebase sync prepared via outbox pattern)



---

SSOT Invariants (AUTHORITATIVE)

Every discovered ingest candidate creates exactly one NX_IngestLedger entry
(ACCEPTED | REJECTED | SKIPPED) – no silent drops.

Every ACCEPTED ingest candidate triggers exactly one NX_Work resolution attempt,
which either links to an existing NX_Work or creates a new one.

Every NX_Work visible in the UI must have:

≥1 NX_WorkSourceRef

≥1 NX_WorkVariant with valid playbackHints


Multi-account safe: accountKey is mandatory in all sourceKeys (collision-free).

Resume is profile-scoped and cross-source, stored in NX_WorkUserState (percentage-based positioning).

UI reads exclusively from NX_ graph, never from legacy Obx* entities.



---

Phase Breakdown

Phase 0: Contracts, Keys, Modes, Guardrails ⏱️ 2–3 days

Tasks:

[ ] 0.1 Create NX_SSOT_CONTRACT.md

Deterministic key formats (workKey, authorityKey, sourceKey, variantKey)

IngestReasonCode enum

Classification heuristics (Clip / Episode / Movie thresholds)


[ ] 0.2 Implement runtime mode switches in DataStore
(CatalogReadMode, CatalogWriteMode, MigrationMode, NxUiVisibility)

[ ] 0.3 Add Detekt custom rules
(no BoxStore outside repositories, no secrets in logs/OBX, ledger required)


Acceptance:

[ ] NX_SSOT_CONTRACT.md exists and is complete

[ ] Mode switches implemented with sane defaults

[ ] Detekt rules active and enforced in CI

[ ] Kill-switch rollback to LEGACY documented and verified



---

Phase 1: NX_ Schema + Repositories ⏱️ 5–7 days

Tasks:

[ ] 1.1 Create all 16 NX_ entity classes in core/persistence/obx/
with proper uniqueness and index constraints

[ ] 1.2 Implement repository interfaces + implementations
(Work, WorkSourceRef, WorkVariant, WorkRelation, WorkUserState, WorkRuntimeState,
WorkEmbedding, IngestLedger, Profile, ProfileRule, ProfileUsage, SourceAccount, CloudOutbox, WorkRedirect)


Acceptance:

[ ] All 16 NX_ entities compile without errors

[ ] All repositories implemented and tested

[ ] ObjectBox store starts cleanly

[ ] Uniqueness constraints validated by tests

[ ] No UI code accesses BoxStore directly (Detekt validated)



---

Phase 2: NX Ingest Path ⏱️ 4–5 days

Tasks:

[ ] 2.1 Implement Normalizer Gate

< 60s → REJECT

not playable → REJECT

ACCEPTED → resolve to NX_Work + create SourceRef + Variant


[ ] 2.2 Enforce accountKey in all SourceRefs (multi-account ready from day one)

[ ] 2.3 Add cache cleanup hook (only after successful NX persist)


Acceptance:

[ ] Reject rules applied correctly

[ ] Every ACCEPTED item has Work + SourceRef + Variant

[ ] 100% ledger coverage for ingest path

[ ] accountKey present in all SourceRefs



---

Phase 3: Migration Worker ⏱️ 5–7 days

Tasks:

[ ] 3.1 Implement one-shot migration worker (Legacy → NX)

batched

resumable

idempotent


[ ] 3.2 Map all 23 legacy entities to NX equivalents (see roadmap)

[ ] 3.3 Implement read-only data quality verifier worker
(detects invariant violations, no writes)


Acceptance:

[ ] Migration worker is idempotent

[ ] All legacy entities mapped

[ ] Progress tracking allows resume after crash

[ ] Verifier detects works without SourceRef/Variant

[ ] After migration: NX_Work > 0, NX_WorkSourceRef > 0, NX_WorkVariant > 0



---

Phase 4: Dual-Read UI ⏱️ 7–10 days

Tasks:

[ ] 4.1 Update UI repositories to support CatalogReadMode

Home (Continue Watching, Recently Added, Favorites, Live)

Library (Movies / Series / Clips / Live with filters)

Detail (Work metadata, sources, variants, resume)

Series navigation (seasons, episodes)

Search (text search)

Live TV (channels, EPG)


[ ] 4.2 Add debug dump screen
(entity counts, invariant violations, NEEDS_REVIEW works)


Acceptance:

[ ] All critical screens work in LEGACY and NX modes

[ ] Debug dump screen correctly reflects NX state

[ ] Performance acceptable (no multi-second stalls)

[ ] NEEDS_REVIEW works are visible and filterable



---

Phase 5: Stop-Write Legacy ⏱️ 2–3 days

Tasks:

[ ] 5.1 Optional short DUAL_WRITE validation window

[ ] 5.2 Switch to CatalogWriteMode = NX_ONLY

[ ] 5.3 Disable all legacy write paths (log warnings if hit)

[ ] 5.4 Test kill-switch rollback procedure


Acceptance:

[ ] NX_ONLY write mode active

[ ] Legacy write paths disabled

[ ] Kill-switch rollback tested and documented

[ ] App functional with NX-only writes



---

Phase 6: Stop-Read Legacy + Cleanup ⏱️ 3–4 days

Tasks:

[ ] 6.1 Remove UI dependencies on Obx* entities

[ ] 6.2 Implement DB cleanup worker (delete legacy boxes)

[ ] 6.3 Optional: Remove legacy entity classes from schema


Acceptance:

[ ] UI has no Obx* imports

[ ] Cleanup worker frees storage

[ ] App is fully NX-only



---

Important Clarifications (PLATIN)

TMDB integration:
TMDB IDs are attached via NX_WorkAuthorityRef.
Promotion to primary canonical identity happens only after successful validation, never blindly during migration.

Live content:
Live items are migrated as NX_Work(workType = LIVE) without aggressive canonical merging.
Channel-level canonicalization is explicitly deferred to a follow-up issue (Live-Canonical-V2).

UI / Session state:
Navigation state (screen / row / tile / MiniPlayer return context) is out of scope for OBX
and must be stored via DataStore / session mechanisms, not NX entities.



---

Final Acceptance Criteria

[ ] All 16 NX_ entities created with proper indexes and uniqueness

[ ] Migration completes without OutOfMemory (batched + resumable)

[ ] NX_Work, NX_WorkSourceRef, NX_WorkVariant counts > 0

[ ] UI fully functional in LEGACY and NX modes

[ ] Every ingest candidate produces an IngestLedger entry

[ ] NEEDS_REVIEW works are debuggable

[ ] accountKey present in all SourceRefs

[ ] Resume works cross-source and cross-device

[ ] Kill-switch rollback tested

[ ] No secrets in OBX or logs (Detekt enforced)



---

Timeline Estimate

Total: 28–39 development days

Critical Path: Phase 0 → 1 → 2 → 3 → 4

Parallel: Documentation + tests during Phase 4



---

References

Parent Issue: #621 (OBX Blueprint)

Roadmap: docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md

Contracts:
/contracts/MEDIA_NORMALIZATION_CONTRACT.md
/contracts/TMDB_ENRICHMENT_CONTRACT.md
/contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md

Architecture Authority: AGENTS.md (Section 4)
