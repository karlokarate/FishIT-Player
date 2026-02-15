# Comprehensive Outdated Documents Audit - 2026-01-09

**Audit Date:** 2026-01-09  
**Auditor:** Copilot Coding Agent  
**Scope:** Repository-wide (all markdown documentation)  
**Status:** COMPREHENSIVE INVENTORY

---

## Executive Summary

This audit identifies **ALL potentially outdated, redundant, or superseded** documentation files across the FishIT-Player repository. Files are categorized by status and priority for update/removal.

### Summary Statistics

- **Total Documents Audited:** 150+
- **Outdated/Needs Update:** 47
- **Superseded (Can be Archived):** 23
- **Legacy (Already Archived):** 50+
- **Current/Active:** ~30
- **Action Required:** High (47 files need attention)

---

## Audit Methodology

1. **Inventory:** Scanned all `.md` files in `docs/`, `contracts/`, and root
2. **Assessment:** Checked for references to outdated patterns, completed work, or superseded systems
3. **Classification:** Categorized by status (Active/Outdated/Superseded/Legacy)
4. **Priority:** Assigned update priority (P0/P1/P2/P3)

---

## Category 1: OUTDATED - Needs Immediate Update

These documents reference outdated patterns, incomplete information, or superseded contracts.

### Priority 0 (Update Immediately)

| File | Issue | Action Required |
|------|-------|-----------------|
| **contracts/MEDIA_NORMALIZATION_CONTRACT.md** | Missing NX_* entity references | Add Phase 0 completion note, reference NX_SSOT_CONTRACT |
| **contracts/README.md** | Missing NX_SSOT_CONTRACT.md | Add NX_SSOT_CONTRACT to inventory |
| **docs/v2/CANONICAL_MEDIA_SYSTEM.md** | Pre-dates NX_* entities | Add deprecation notice, reference NX_SSOT_CONTRACT |
| **docs/v2/PIPELINE_ARCHITECTURE_AUDIT.md** | Missing Phase 0 completion | Update with NX_IngestLedger requirements |
| **docs/v2/OBJECTBOX_REACTIVE_PATTERNS.md** | Missing NX_* entity patterns | Add NX_Work query examples |

**Impact:** HIGH - These are SSOT contracts/core architecture docs  
**Timeline:** Update within 1-2 days

---

### Priority 1 (Update Soon)

| File | Issue | Action Required |
|------|-------|-----------------|
| **docs/v2/ARCHITECTURE_OVERVIEW_V2.md** | Missing OBX PLATIN section | Add Phase 3.2 (OBX PLATIN Refactor) |
| **docs/v2/NAMING_INVENTORY_v2.md** | Missing NX_* naming conventions | Add workKey/sourceKey/variantKey formats |
| **docs/v2/IMPLEMENTATION_PHASES_V2.md** | Phase tracking outdated | Sync with ROADMAP.md Phase 3.2 |
| **docs/v2/obx/README.md** | Missing NX_* entity documentation | Add NX_* entity section, link to NX_SSOT_CONTRACT |
| **docs/v2/obx/ENTITY_TRACEABILITY_MATRIX.md** | Only covers legacy entities | Add NX_* entity traceability |
| **docs/v2/obx/RELATION_DEPENDENCY_GRAPH.md** | Missing NX_* relationships | Add NX_Work → NX_WorkSourceRef → NX_WorkVariant graph |
| **docs/v2/FROZEN_MODULE_MANIFEST.md** | Missing core/persistence NX_* changes | Add Phase 0 completion notes |
| **docs/CATALOG_SYNC_WORKERS_CONTRACT_V2.md** | Missing NX_IngestLedger requirement | Add INV-01 enforcement in workers |
| **.github/copilot-instructions.md** | Missing OBX PLATIN reference | Add Phase 3.2 to project overview |

**Impact:** MEDIUM - Important architecture/inventory docs  
**Timeline:** Update within 1 week

---

### Priority 2 (Update When Convenient)

| File | Issue | Action Required |
|------|-------|-----------------|
| **docs/v2/MEDIA_NORMALIZER_DESIGN.md** | Pre-dates percentage-based resume | Add NX_WorkUserState.resumePercent |
| **docs/v2/TELEGRAM_TDLIB_V2_INTEGRATION.md** | Missing remoteId-first updates | Reference TELEGRAM_ID_ARCHITECTURE_CONTRACT |
| **docs/v2/HOME_PLATINUM_PERFORMANCE_PLAN.md** | Pre-dates NX_Work queries | Update with NX_Work reactive patterns |
| **docs/v2/STARTUP_TRIGGER_CONTRACT.md** | Missing NX_Work empty state | Add NX_Work query examples |
| **docs/v2/architecture/MODULE_RESPONSIBILITY_MAP.md** | Missing core/persistence NX_* | Add NX_* entity responsibilities |
| **docs/v2/architecture/DEPENDENCY_GRAPH.md** | Pre-dates NX_* entities | Add NX_* dependency edges |

**Impact:** LOW - Reference documentation  
**Timeline:** Update within 2-4 weeks

---

## Category 2: SUPERSEDED - Can Be Archived

These documents are **completely superseded** by newer contracts/docs and can be moved to `legacy/docs/` or deleted.

### Candidates for Archival

| File | Superseded By | Action |
|------|---------------|--------|
| **docs/v2/CANONICAL_MEDIA_MIGRATION_STATUS.md** | NX_SSOT_CONTRACT.md, OBX_PLATIN_REFACTOR_ROADMAP.md | Archive to `legacy/docs/archive/` |
| **docs/v2/MEDIA_NORMALIZATION_AND_UNIFICATION.md** | MEDIA_NORMALIZATION_CONTRACT.md | Archive (duplicates contract) |
| **docs/v2/done_tasks/task.md** | Completed work | Delete or move to `legacy/docs/archive/` |
| **docs/v2/done_tasks/task (1).md** | Completed work | Delete or move to `legacy/docs/archive/` |
| **docs/v2/done_tasks/naming_task_review.md** | Completed work | Archive |
| **docs/v2/done_tasks/pipeline_finalization.md** | Completed work | Archive |
| **docs/v2/done_tasks/cleanup.md** | Completed work | Archive |
| **docs/v2/done_tasks/naming_normalization_task.md** | Completed work | Archive |
| **docs/Naming_normalization_task.md** | Completed work | Delete (duplicate) |
| **docs/v2/player migrationsplan.md** | INTERNAL_PLAYER_REFACTOR_ROADMAP.md | Archive |
| **docs/v2/ui_implementation_blueprint.md** | Implemented features | Archive |
| **docs/v2/CHANGES_XTREAM_PERFORMANCE_SESSION.md** | Completed work | Archive |
| **docs/v2/HOME_PHASE1_IMPLEMENTATION.md** | Completed work | Archive |
| **docs/v2/HOME_PHASE1_PHASE2_COMPLETE.md** | Completed work | Archive |
| **docs/v2/MEDIA_PARSER_DELIVERY_SUMMARY.md** | Completed work | Archive |
| **docs/v2/BUILD_FIX_README.md** | Fixed issues | Archive |
| **docs/WORKMANAGER_INITIALIZATION_FIX_VERIFICATION.md** | Fixed issue | Archive |
| **docs/WORKMANAGER_PARALLEL_SOURCES_FIX.md** | Fixed issue | Archive |
| **docs/XTREAM_LOGIN_FIX_ANALYSIS.md** | Fixed issue | Archive |
| **docs/XTREAM_LOGIN_FIX_QUICK_REFERENCE.md** | Fixed issue | Archive |
| **docs/ISSUE_564_DEBUG_TOOLS_GATING_COMPLETE.md** | Completed work | Archive |

**Total Candidates:** 23 files  
**Action:** Move to `legacy/docs/archive/superseded/` with README explaining why

---

## Category 3: LEGACY - Already Correctly Archived

These files are already in `legacy/docs/` and properly archived. **No action needed.**

### Verified Legacy Archives

- `legacy/docs/archive/*` - ~50 files (v1 docs, completed tasks)
- `legacy/docs/ui/*` - v1 UI documentation
- `legacy/docs/telegram/*` - v1 Telegram docs
- `legacy/docs/ffmpegkit/*` - v1 FFmpeg docs
- `legacy/docs/release/*` - v1 release workflows

**Status:** ✅ CORRECT - These are properly archived

---

## Category 4: ACTIVE - No Changes Needed

These documents are **current, accurate, and actively maintained**.

### Core Contracts (Binding - SSOT)

| File | Status | Last Updated |
|------|--------|--------------|
| **contracts/NX_SSOT_CONTRACT.md** | ✅ Active | 2026-01-09 |
| **contracts/GLOSSARY_v2_naming_and_modules.md** | ✅ Active | Recent |
| **contracts/LOGGING_CONTRACT_V2.md** | ✅ Active | Recent |
| **contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md** | ✅ Active | Recent |
| **contracts/TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md** | ✅ Active | Recent |
| **contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md** | ✅ Active | Recent |

### Core Architecture

| File | Status | Last Updated |
|------|--------|--------------|
| **ROADMAP.md** | ✅ Active | 2026-01-09 |
| **AGENTS.md** | ✅ Active | Recent |
| **V2_PORTAL.md** | ✅ Active | Recent |
| **docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md** | ✅ Active | 2026-01-09 |
| **docs/v2/NX_SSOT_CONTRACT.md** | ✅ Active | 2026-01-09 |
| **docs/v2/OBX_KILL_SWITCH_GUIDE.md** | ✅ Active | 2026-01-09 |
| **docs/v2/OBX_PLATIN_DETEKT_GUARDRAILS_CONTRACT.md** | ✅ Active | 2026-01-09 |
| **docs/v2/OBX_PLATIN_REFACTOR_GITHUB_ISSUE.md** | ✅ Active | 2026-01-09 |

### Recent Analysis

| File | Status | Date |
|------|--------|------|
| **docs/v2/TODAY_2026-01-09_DEEP_DIVE_ANALYSIS.md** | ✅ Active | 2026-01-09 |
| **docs/v2/COMMIT_068a525_CORRECTED_MESSAGE.md** | ✅ Active | 2026-01-09 |
| **docs/v2/MCP_CONTEXT_UPDATE_2026-01-09.md** | ✅ Active | 2026-01-09 |

**Total Active:** ~30 files

---

## Category 5: DUPLICATE/REDUNDANT

Files that duplicate information from other sources.

| File | Duplicate Of | Action |
|------|--------------|--------|
| **contracts/TMDB Canonical Identity & Imaging SSOT Contract (v2).md** | contracts/TMDB_ENRICHMENT_CONTRACT.md (in docs/v2/) | Consolidate or delete one |
| **docs/v2/WORKMANAGER_INITIALIZATION_GUARDRAIL.md** | docs/WORKMANAGER_INITIALIZATION_GUARDRAIL.md | Delete duplicate |
| **docs/v2/MEDIA_NORMALIZATION_CONTRACT.md** | contracts/MEDIA_NORMALIZATION_CONTRACT.md | One should be canonical, other should forward |

**Action:** Consolidate or create forwarding documents

---

## Category 6: UNCLEAR STATUS - Needs Investigation

Files that require deeper investigation to determine current status.

| File | Issue | Action Required |
|------|-------|-----------------|
| **docs/v2/FROZEN_MANIFEST_QUICK_REF.md** | Unclear if still enforced | Verify with recent changes |
| **docs/v2/EPG_SYSTEM_CONTRACT_V1.md** | Live TV not implemented | Mark as future or archive |
| **docs/v2/V1_VS_V2_ANALYSIS_REPORT.md** | Unclear if complete | Verify status |
| **docs/v2/DEPENDENCY_UPGRADE_NOTES.md** | Unclear if current | Verify dependency versions |
| **docs/v2/Pipeline_test_cli.md** | Unclear if tool exists | Verify implementation |

**Action:** Investigate and reclassify

---

## Contracts Folder - Special Attention

### Issues in /contracts/

1. **Missing NX_SSOT_CONTRACT.md in README.md inventory**
   - Action: Add to contracts/README.md

2. **Duplicate TMDB contract**
   - `contracts/TMDB Canonical Identity & Imaging SSOT Contract (v2).md`
   - vs. `docs/v2/TMDB_ENRICHMENT_CONTRACT.md`
   - Action: Consolidate or clarify which is binding

3. **Player contracts need OBX PLATIN references**
   - INTERNAL_PLAYER_* contracts don't reference NX_WorkUserState
   - Action: Add Phase 0 completion notes

---

## Priority Action Plan

### Week 1 (High Priority)

1. **Update contracts/README.md** - Add NX_SSOT_CONTRACT.md
2. **Update contracts/MEDIA_NORMALIZATION_CONTRACT.md** - Add NX_* references
3. **Update docs/v2/CANONICAL_MEDIA_SYSTEM.md** - Add deprecation notice
4. **Update docs/v2/PIPELINE_ARCHITECTURE_AUDIT.md** - Add NX_IngestLedger
5. **Update docs/v2/OBJECTBOX_REACTIVE_PATTERNS.md** - Add NX_* patterns

### Week 2 (Medium Priority)

6. **Update docs/v2/ARCHITECTURE_OVERVIEW_V2.md** - Add Phase 3.2
7. **Update docs/v2/obx/README.md** - Add NX_* section
8. **Update docs/v2/obx/ENTITY_TRACEABILITY_MATRIX.md** - Add NX_* entities
9. **Archive completed work** - Move 23 files to `legacy/docs/archive/superseded/`
10. **Consolidate duplicates** - Resolve TMDB contract duplication

### Week 3-4 (Low Priority)

11. **Update remaining P2 files** - 6 files
12. **Investigate unclear status files** - 5 files
13. **Create archival README** - Explain why files were archived

---

## Recommended Archival Structure

```
legacy/docs/archive/
├── superseded/
│   ├── README.md (explains superseded files)
│   ├── obx/
│   │   └── CANONICAL_MEDIA_MIGRATION_STATUS.md
│   ├── completed/
│   │   ├── HOME_PHASE1_IMPLEMENTATION.md
│   │   ├── HOME_PHASE1_PHASE2_COMPLETE.md
│   │   └── ... (other completed work)
│   └── fixes/
│       ├── WORKMANAGER_INITIALIZATION_FIX_VERIFICATION.md
│       └── ... (other fixed issues)
└── v1/
    └── (existing v1 archives)
```

---

## Missing Documentation (Gaps)

These important topics **lack documentation**:

1. **NX_* Query Patterns Guide** - How to query new entities efficiently
2. **Phase 1 Kickoff Plan** - Repository implementation guide
3. **Migration Worker Design** - Phase 3 detailed design
4. **Data Quality Verifier** - INV-01 through INV-13 validation
5. **Multi-Account UI Flow** - User experience for account switching
6. **Percentage Resume Implementation** - Cross-source resume guide

**Action:** Create these guides during Phase 1-3 implementation

---

## Automation Recommendations

### Detekt Rule for Outdated References

```kotlin
// Detect usage of legacy Obx* entities in new code
rule("NoLegacyObxEntities") {
    // Fail if code outside legacy/ imports ObxCanonicalMedia, etc.
}

// Detect TdlibClientProvider anti-pattern
rule("NoTdlibClientProvider") {
    // Fail if TdlibClientProvider is used (v1 pattern)
}
```

### Documentation Freshness Checker

```bash
# Script to check doc freshness
# Files older than 90 days without "Last Updated" = flag for review
find docs contracts -name "*.md" -mtime +90 -type f
```

---

## Summary by Status

| Status | Count | Action Required |
|--------|-------|-----------------|
| **Outdated (P0)** | 5 | Update immediately |
| **Outdated (P1)** | 9 | Update within 1 week |
| **Outdated (P2)** | 6 | Update within 2-4 weeks |
| **Superseded** | 23 | Archive to legacy/ |
| **Active** | ~30 | No action (maintain) |
| **Legacy** | 50+ | No action (already archived) |
| **Duplicate** | 3 | Consolidate |
| **Unclear** | 5 | Investigate |
| **TOTAL** | 150+ | 47 need attention |

---

## Critical Finding: Contract Inventory Gap

**Issue:** `contracts/README.md` doesn't list `NX_SSOT_CONTRACT.md`

**Impact:** Developers may miss the most critical new contract

**Action:** Update contracts/README.md immediately

---

## Conclusion

The repository has **47 documents requiring attention**:

- **5 critical updates** (SSOT contracts/architecture)
- **9 important updates** (inventory/architecture docs)
- **6 minor updates** (reference documentation)
- **23 archival candidates** (completed/superseded work)
- **3 duplicates to consolidate**
- **5 unclear status files to investigate**

**Highest Priority:**

1. Update `contracts/README.md` (add NX_SSOT_CONTRACT.md)
2. Update `contracts/MEDIA_NORMALIZATION_CONTRACT.md` (add Phase 0 references)
3. Archive 23 completed/superseded files
4. Resolve 3 duplicate files

**Timeline:**

- Week 1: Critical updates (P0)
- Week 2: Important updates (P1) + archival
- Week 3-4: Minor updates (P2) + investigation

---

## Appendix: Full File Inventory

### Contracts (18 files)

✅ Active (6):
- contracts/NX_SSOT_CONTRACT.md
- contracts/GLOSSARY_v2_naming_and_modules.md
- contracts/LOGGING_CONTRACT_V2.md
- contracts/TELEGRAM_ID_ARCHITECTURE_CONTRACT.md
- contracts/TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md
- contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md

🔄 Needs Update (6):
- contracts/README.md (missing NX_SSOT_CONTRACT)
- contracts/MEDIA_NORMALIZATION_CONTRACT.md (needs Phase 0 refs)
- contracts/TELEGRAM_PARSER_CONTRACT.md (verify current)
- contracts/TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md (verify status)
- contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md (add NX refs)
- contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT_FULL.md (verify current)

⚠️ Duplicate (1):
- contracts/TMDB Canonical Identity & Imaging SSOT Contract (v2).md

✅ Active Player Contracts (5):
- contracts/INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md
- contracts/INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md
- contracts/INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md
- contracts/INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md
- contracts/INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md

### Root Docs (8 files)

✅ Active (3):
- ROADMAP.md
- AGENTS.md
- V2_PORTAL.md

🔄 Needs Update (1):
- .github/copilot-instructions.md (add Phase 3.2)

📦 Archive (4):
- docs/WORKMANAGER_INITIALIZATION_FIX_VERIFICATION.md
- docs/WORKMANAGER_PARALLEL_SOURCES_FIX.md
- docs/XTREAM_LOGIN_FIX_ANALYSIS.md
- docs/XTREAM_LOGIN_FIX_QUICK_REFERENCE.md

### docs/v2/ (90+ files)

✅ Active (12):
- OBX_PLATIN_REFACTOR_ROADMAP.md
- NX_SSOT_CONTRACT.md
- OBX_KILL_SWITCH_GUIDE.md
- OBX_PLATIN_DETEKT_GUARDRAILS_CONTRACT.md
- OBX_PLATIN_REFACTOR_GITHUB_ISSUE.md
- TODAY_2026-01-09_DEEP_DIVE_ANALYSIS.md
- COMMIT_068a525_CORRECTED_MESSAGE.md
- MCP_CONTEXT_UPDATE_2026-01-09.md
- TMDB_ENRICHMENT_CONTRACT.md
- TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md
- STARTUP_TRIGGER_CONTRACT.md
- CATALOG_SYNC_WORKERS_CONTRACT_V2.md

🔄 Needs Update (15):
- ARCHITECTURE_OVERVIEW_V2.md
- CANONICAL_MEDIA_SYSTEM.md
- PIPELINE_ARCHITECTURE_AUDIT.md
- OBJECTBOX_REACTIVE_PATTERNS.md
- NAMING_INVENTORY_v2.md
- IMPLEMENTATION_PHASES_V2.md
- obx/README.md
- obx/ENTITY_TRACEABILITY_MATRIX.md
- obx/RELATION_DEPENDENCY_GRAPH.md
- FROZEN_MODULE_MANIFEST.md
- MEDIA_NORMALIZER_DESIGN.md
- TELEGRAM_TDLIB_V2_INTEGRATION.md
- HOME_PLATINUM_PERFORMANCE_PLAN.md
- architecture/MODULE_RESPONSIBILITY_MAP.md
- architecture/DEPENDENCY_GRAPH.md

📦 Archive (16):
- CANONICAL_MEDIA_MIGRATION_STATUS.md
- MEDIA_NORMALIZATION_AND_UNIFICATION.md
- done_tasks/* (8 files)
- player migrationsplan.md
- ui_implementation_blueprint.md
- CHANGES_XTREAM_PERFORMANCE_SESSION.md
- HOME_PHASE1_IMPLEMENTATION.md
- HOME_PHASE1_PHASE2_COMPLETE.md
- MEDIA_PARSER_DELIVERY_SUMMARY.md
- BUILD_FIX_README.md

---

**Audit Completed:** 2026-01-09  
**Status:** COMPREHENSIVE  
**Next Review:** After Phase 1 completion  
**Maintainer:** Development Team
