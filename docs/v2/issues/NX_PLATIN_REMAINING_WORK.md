# NX PLATIN Migration - Remaining Work

**Parent Issue:** #621  
**Created:** 2026-01-23  
**Status:** IN PROGRESS  
**Last Updated:** 2026-01-23

## Summary
Full architecture audit identified remaining INV-6 violations and cleanup tasks for the OBX→NX migration.

---

## ✅ Phase A: Fix Compilation Errors (COMPLETED)

**File:** `infra/data-nx/xtream/NxXtreamSeriesIndexRepository.kt`  
**Status:** ✅ DONE - All ~35 errors fixed

### Tasks:
- [x] A1: Replace kotlinx.serialization with `org.json.JSONObject` (no new dependency needed)
- [x] A2: Fix Variant construction - use `playbackHints: Map<String, String>` not individual fields
- [x] A3: Change `variantRepository.get()` to `variantRepository.getByVariantKey()`
- [x] A4: Use `container` instead of `containerFormat`

---

## 🔴 Phase B: Migrate feature:detail to NX (DEFERRED - COMPLEX)

**Severity:** CRITICAL - UI layer violates INV-6 SSOT rule  
**Status:** ⏸️ Deferred - requires comprehensive refactoring

### Files to modify:
- [ ] B1: `PlayMediaUseCase.kt` - Replace `CanonicalMediaRepository` → `NxWorkRepository`
- [ ] B2: `UnifiedDetailViewModel.kt` - Replace `CanonicalMediaWithSources` → NX DTOs
- [ ] B3: `SeriesDetailState.kt` - Replace `CanonicalResumeInfo` → `WorkUserState`
- [ ] B4: `SourceSelectionTest.kt` - Update test fixtures

### Contract Reference:
> **INV-6 (UNUMGEHBAR):** "UI reads exclusively from NX_* entity graph. No UI code may import or query legacy Obx* entities."

**Note:** This task is complex and requires a dedicated issue with proper planning.

---

## ✅ Phase C: Mark Legacy OBX Repos @Deprecated (COMPLETED)

### Files marked:
- [x] C1: `ObxXtreamCatalogRepository.kt` - Added @Deprecated with INV-6 reference
- [x] C2: `ObxXtreamLiveRepository.kt` - Added @Deprecated with INV-6 reference
- [x] C3: `ObxXtreamSeriesIndexRepository.kt` - Added @Deprecated pointing to NX replacement

---

## ✅ Phase D: Delete Unused Legacy Adapters (COMPLETED)

### Files deleted:
- [x] D1: `HomeContentRepositoryAdapter.kt` - Deleted
- [x] D2: `LibraryContentRepositoryAdapter.kt` - Deleted
- [x] D3: `LiveContentRepositoryAdapter.kt` - Deleted
- [x] D4: `TelegramContentRepositoryAdapter.kt` - Did not exist (was never created)

### Also cleaned:
- [x] Removed unused imports from `XtreamDataModule.kt`

---

## ✅ Phase E: Review Dual-Write Status (COMPLETED)

- [x] E1: Verified `CatalogSyncService` writes exclusively via `NxCatalogWriter` (NX-ONLY mode)
- [x] E2: Updated `NX_MIGRATION_STATUS.md` with current status

---

## Acceptance Criteria

- [x] Build compiles without errors (`./gradlew :infra:data-nx:compileDebugKotlin`)
- [ ] No INV-6 violations in `feature/**` modules ← **Phase B pending**
- [x] All legacy adapters deleted or marked @Deprecated
- [x] `NX_MIGRATION_STATUS.md` updated to reflect completed phases

## Remaining Work

**Only Phase B remains:** Migrate `feature:detail` to use NX repositories. This is a complex task that touches multiple files and requires careful planning to avoid breaking the detail screens.

**Recommendation:** Create a dedicated GitHub issue for Phase B with:
- Detailed file-by-file migration plan
- Test coverage requirements
- Rollback strategy

## References
- `contracts/NX_SSOT_CONTRACT.md` - INV-6 rule
- `AGENTS.md` Section 4.3.3 - NX_Work UI SSOT
- `docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md` - Full roadmap
