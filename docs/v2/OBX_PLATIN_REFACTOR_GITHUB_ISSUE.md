# GitHub Issue Body for OBX PLATIN Refactor

**Title:** OBX Layering PLATIN Refactor - Execution Tasks (Sub-issue #621)

**Labels:** enhancement, refactor, persistence, high-priority

---

## Context

This sub-issue for #621 defines all necessary and sequentially executable tasks to refactor our OBX layering to PLATIN standard according to the comprehensive blueprint in #621.

**No backward compatibility, no deprecation. Complete replacement of old logic.**

## Architecture Overview

### Transformation: 23 Legacy Entities → 16 NX_ Entities

See full mapping in `docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md`

**Key Changes:**
- Unified Work Graph (NX_Work as SSOT)
- Multi-Account Ready (accountKey in all sourceKey)
- Playback Variants (separate quality/encoding variants)
- Ingest Ledger (explicit Accept/Reject/Skip tracking)
- Cloud-Ready (Firebase sync prepared)

## Phase Breakdown

### Phase 0: Contracts, Keys, Modes, Guardrails ⏱️ 2-3 days

**Tasks:**
- [ ] 0.1 Create `NX_SSOT_CONTRACT.md` with key formats, IngestReasonCode enum, heuristics
- [ ] 0.2 Implement runtime mode switches in DataStore (CatalogReadMode, CatalogWriteMode, MigrationMode, NxUiVisibility)
- [ ] 0.3 Add Detekt custom rules (no BoxStore outside repos, no secrets, ledger required)

**Acceptance:**
- [ ] NX_SSOT_CONTRACT.md exists and is complete
- [ ] DataStore mode switches implemented with sane defaults
- [ ] Detekt rules active and tested in CI
- [ ] Kill-switch (rollback to LEGACY) documented and verified

---

### Phase 1: NX_ Schema + Repositories ⏱️ 5-7 days

**Tasks:**
- [ ] 1.1 Create all 16 NX_ entity classes in `core/persistence/obx/NxEntities.kt` with uniqueness/index constraints
- [ ] 1.2 Implement Repository interfaces + implementations (13 repos: Work, WorkSourceRef, WorkVariant, WorkRelation, WorkUserState, WorkRuntimeState, WorkEmbedding, IngestLedger, Profile, ProfileRule, ProfileUsage, SourceAccount, CloudOutbox)

**Acceptance:**
- [ ] All 16 NX_ entities compile without errors
- [ ] All 13 repositories implemented and tested
- [ ] ObjectBox store starts without errors
- [ ] Uniqueness works (tests passed)
- [ ] No UI code accesses BoxStore directly (Detekt validated)

---

### Phase 2: NX Ingest Path ⏱️ 4-5 days

**Tasks:**
- [ ] 2.1 Implement Normalizer Gate (hard rules: <60s reject, not playable reject, ACCEPTED creates Work+SourceRef+Variant)
- [ ] 2.2 Enforce accountKey in all SourceRefs (multi-account ready from day 1)
- [ ] 2.3 Add cache cleanup hook (only after successful persist)

**Acceptance:**
- [ ] Normalizer Gate rejects items correctly
- [ ] All accepted items have Work+SourceRef+Variant
- [ ] Ledger coverage for ingest path is 100%
- [ ] AccountKey present in all SourceRefs

---

### Phase 3: Migration Worker ⏱️ 5-7 days

**Tasks:**
- [ ] 3.1 Implement one-shot migration worker (Legacy → NX, batched, resumable, idempotent)
- [ ] 3.2 Map all 23 legacy entities to NX equivalents (see roadmap doc)
- [ ] 3.3 Implement data quality verifier worker (read-only, finds invariant violations)

**Acceptance:**
- [ ] Migration worker exists and is idempotent
- [ ] All 23 legacy entities have mapping to NX
- [ ] Progress tracking works (can resume after crash)
- [ ] Verifier worker finds invariant violations
- [ ] After migration: Work/SourceRef/Variant counts > 0

---

### Phase 4: Dual-Read UI ⏱️ 7-10 days

**Tasks:**
- [ ] 4.1 Update UI repositories to support CatalogReadMode toggle
  - [ ] Home screen (ContinueWatching, RecentlyAdded, Favorites, Live)
  - [ ] Library screen (Movies/Series/Clips/Live with filters)
  - [ ] Detail screen (Work metadata, sources, variants, resume)
  - [ ] Series navigation (seasons, episodes)
  - [ ] Search screen (text search)
  - [ ] Live TV screen (channels, EPG)
- [ ] 4.2 Add debug dump screen (entity counts, violations, NEEDS_REVIEW works)

**Acceptance:**
- [ ] All critical screens work in both LEGACY and NX modes
- [ ] Debug dump screen shows NX state correctly
- [ ] Performance acceptable (no 10s+ load times)
- [ ] NEEDS_REVIEW works are filterable

---

### Phase 5: Stop-Write Legacy ⏱️ 2-3 days

**Tasks:**
- [ ] 5.1 Optional: DUAL_WRITE validation period (1-2 weeks runtime)
- [ ] 5.2 Switch to NX_ONLY write mode
- [ ] 5.3 Disable all legacy write paths (log warnings if attempted)
- [ ] 5.4 Test kill-switch rollback procedure

**Acceptance:**
- [ ] CatalogWriteMode = NX_ONLY active
- [ ] Legacy write paths disabled
- [ ] Kill-switch rollback tested and documented
- [ ] App functional with NX-only writes

---

### Phase 6: Stop-Read Legacy + Cleanup ⏱️ 3-4 days

**Tasks:**
- [ ] 6.1 Remove UI dependencies on Obx* entities
- [ ] 6.2 Implement DB cleanup worker (delete legacy boxes/entities)
- [ ] 6.3 Optional: Remove legacy entity classes from schema

**Acceptance:**
- [ ] UI has no Obx* imports
- [ ] Cleanup worker deletes legacy data
- [ ] App is fully NX-only
- [ ] Storage space freed

---

## Final Acceptance Criteria

**Must Pass Before Completion:**
- [ ] All 16 NX_ entities created with proper indexes/uniqueness
- [ ] Migration completes without OutOfMemory (batched processing)
- [ ] After migration: NX_Work > 0, NX_WorkSourceRef > 0, NX_WorkVariant > 0
- [ ] UI functional in both LEGACY and NX modes (dual-read works)
- [ ] Every discovered item creates NX_IngestLedger entry
- [ ] Works with NEEDS_REVIEW are debuggable via dump screen
- [ ] Multi-account ready: accountKey in all source refs
- [ ] Resume works cross-source (percentage-based positioning)
- [ ] Kill-switch rollback tested and documented
- [ ] No secrets in OBX or logs (Detekt enforced)
- [ ] Detekt rules prevent BoxStore access outside repositories

---

## Timeline Estimate

- **Total:** 28-39 days of development
- **Critical Path:** Phase 0 → Phase 1 → Phase 2 → Phase 3 → Phase 4
- **Parallel Work:** Documentation + Testing during Phase 4

---

## Dependencies

- ObjectBox 4.0.3+ (current stable)
- DataStore for mode switches and checkpoints
- Encrypted DataStore for credentials (no OBX)
- Detekt custom rules for guardrails

---

## Risks & Mitigation

| Risk | Mitigation |
|------|------------|
| Migration OutOfMemory on large datasets | Batched processing + resume capability |
| Data loss during migration | Keep legacy entities until Phase 6 cleanup |
| Performance degradation with NX queries | Indexes on all query paths + benchmark tests |
| Kill-switch needed mid-rollout | Mode toggles allow instant rollback to LEGACY |

---

## References

- **Parent Issue:** #621 (Obx Blueprint)
- **Roadmap:** `docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md`
- **Contracts:** `/contracts/MEDIA_NORMALIZATION_CONTRACT.md`, `/contracts/TMDB_ENRICHMENT_CONTRACT.md`, `/contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`
- **Architecture:** `AGENTS.md` Section 4
- **Instructions:** `.github/instructions/core-persistence.instructions.md`

---

## How to Create This Issue

1. Copy the content above (everything from "Context" onwards)
2. Go to https://github.com/karlokarate/FishIT-Player/issues/new
3. Set title: **OBX Layering PLATIN Refactor - Execution Tasks (Sub-issue #621)**
4. Paste the body content
5. Add labels: `enhancement`, `refactor`, `persistence`, `high-priority`
6. Link to parent issue #621 in the description
7. Create issue
