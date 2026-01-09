# MCP Server Long-Term Context Update - 2026-01-09

**Update Date:** 2026-01-09  
**Context:** OBX PLATIN Refactor Phase 0 Completion & Critical Insights  
**Priority:** HIGH - Foundational architectural decisions made

---

## Executive Summary for MCP

This context update captures **critical architectural decisions** and **key learnings** from today's OBX PLATIN Refactor Phase 0 completion. These insights must inform all future work on Issue #621 and persistence layer interactions.

### Top-Level Summary

1. ✅ **Phase 0 COMPLETED** - All foundation work done (commit 068a525)
2. 🔥 **CRITICAL FINDING** - Addendum work (contracts/kill-switch) is PREREQUISITE, not supplementary
3. ✅ **16 NX_* entities defined** - Replaces 23 legacy entities
4. ✅ **SSOT invariants established** - 7 binding rules prevent data corruption
5. ✅ **Kill-switch infrastructure** - Safe gradual migration path secured

---

## Critical Architectural Decisions (BINDING)

### 1. accountKey is MANDATORY in All sourceKeys (INV-13)

**Decision:** Every `sourceKey` MUST include `accountKey` from day one.

**Format:**
```
sourceKey = "<sourceType>:<accountKey>:<sourceId>"

Examples:
telegram:acc1:msg:123:456  // User A's Telegram account
telegram:acc2:msg:123:456  // User B's Telegram account  
xtream:acc3:vod:789        // User C's Xtream server
```

**Why This Matters:**

- **Prevents collisions:** Two users with same Telegram chat = different sourceKeys
- **Multi-account ready:** No future rework needed when adding account switching
- **Globally unique:** accountKey ensures sourceKey uniqueness across all users

**Impact:** This decision prevents an entire class of bugs and future rework.

**Code Locations:**
- `core/persistence/obx/NxKeyGenerator.kt` (key generation)
- `contracts/NX_SSOT_CONTRACT.md` (INV-13)

---

### 2. Percentage-Based Resume (Cross-Source)

**Decision:** Resume position stored as **percentage** in `NX_WorkUserState`, not absolute milliseconds.

**Implementation:**
```kotlin
NX_WorkUserState(
    workKey = "movie:the-matrix:1999",
    resumePercent = 0.4,        // 40% watched
    lastPositionMs = 120000,    // Last known position
    lastVariantKey = "telegram:acc1:msg:123:456#1080p:en"
)
```

**Why This Matters:**

- **Cross-source resume works:** User watches 40% on Telegram, can resume at 40% on Xtream
- **Source-agnostic:** Works even if file durations differ slightly
- **Future-proof:** Works with variable bitrate streams

**Impact:** Massive UX improvement - users can switch sources seamlessly.

**Code Locations:**
- `core/persistence/obx/NxEntities.kt` (NX_WorkUserState)
- `contracts/NX_SSOT_CONTRACT.md` (Section 6.2)

---

### 3. NX_IngestLedger - Audit Trail (INV-01)

**Decision:** Every ingest candidate creates **exactly one** `NX_IngestLedger` entry with decision and reason.

**Possible Decisions:**
```kotlin
enum class IngestDecision {
    ACCEPTED,  // Created/linked to NX_Work
    REJECTED,  // Filtered out (too short, invalid, etc.)
    SKIPPED    // Already exists, rate-limited, etc.
}

enum class IngestReasonCode {
    // ACCEPTED reasons
    ACCEPTED_NEW_WORK,
    ACCEPTED_LINKED_EXISTING,
    ACCEPTED_NEW_VARIANT,
    ACCEPTED_NEW_SOURCE,
    
    // REJECTED reasons  
    REJECTED_TOO_SHORT,        // < 60s
    REJECTED_NOT_PLAYABLE,
    REJECTED_INVALID_FORMAT,
    REJECTED_DUPLICATE,
    REJECTED_BLACKLISTED,
    REJECTED_NO_METADATA,
    
    // SKIPPED reasons
    SKIPPED_ALREADY_EXISTS,
    SKIPPED_SOURCE_DISABLED,
    SKIPPED_RATE_LIMITED
}
```

**Why This Matters:**

- **No silent drops:** Every item accounted for
- **Debugging:** "Why isn't my content showing?" = query ledger
- **Data quality:** Track acceptance rates, rejection patterns
- **Transparency:** Users can see why content was filtered

**Impact:** Transforms debugging from guesswork to data-driven analysis.

**Code Locations:**
- `core/persistence/obx/NxEntities.kt` (NX_IngestLedger)
- `core/persistence/obx/NxEnums.kt` (enums)
- `contracts/NX_SSOT_CONTRACT.md` (INV-01)

---

### 4. Kill-Switch Infrastructure (CatalogModePreferences)

**Decision:** All catalog operations gated by runtime mode toggles stored in DataStore.

**Modes:**
```kotlin
enum class CatalogReadMode {
    LEGACY,     // Read from Obx* only (safe default)
    DUAL_READ,  // Read both, prefer NX_* (validation)
    NX_ONLY     // Read from NX_* only (target)
}

enum class CatalogWriteMode {
    LEGACY,     // Write to Obx* only (safe default)
    DUAL_WRITE, // Write to both (validation)
    NX_ONLY     // Write to NX_* only (target)
}
```

**Migration Path:**
```
Phase 0-3:  LEGACY/LEGACY    (default, safe)
Phase 4:    DUAL_READ/DUAL_WRITE (validation)
Phase 5:    NX_ONLY/NX_ONLY  (target)
Phase 6:    NX_ONLY/NX_ONLY  (cleanup legacy)
```

**Emergency Rollback:**
```bash
# Via ADB
adb shell am broadcast -a com.fishit.player.DEBUG_RESET_CATALOG_MODE

# Via Debug Settings
Settings → Debug Tools → OBX Migration → Rollback to Legacy
```

**Why This Matters:**

- **Production safety:** Instant rollback if issues arise
- **Gradual migration:** Each mode validated before next
- **Zero downtime:** Switch modes without app restart
- **Risk mitigation:** Failures don't brick production

**Impact:** Enables safe production rollout of major refactoring work.

**Code Locations:**
- `core/persistence/config/CatalogModePreferences.kt`
- `core/debug-settings/nx/NxMigrationSettingsRepository.kt`
- `docs/v2/OBX_KILL_SWITCH_GUIDE.md`

---

### 5. Deterministic Key Formats (SSOT)

**Decision:** All keys follow **strict deterministic formats** to prevent collisions and enable debugging.

**Key Formats:**

1. **workKey** (globally unique canonical work):
   ```
   format: <workType>:<canonicalSlug>:<year|LIVE>
   
   examples:
   movie:the-matrix:1999
   episode:breaking-bad:s01e01
   series:breaking-bad:2008
   live:sport1:LIVE
   ```

2. **sourceKey** (globally unique source reference):
   ```
   format: <sourceType>:<accountKey>:<sourceId>
   
   examples:
   telegram:acc1:msg:123:456
   xtream:acc2:vod:789
   local:acc3:file:path/to/file.mp4
   ```

3. **variantKey** (playback variant):
   ```
   format: <sourceKey>#<qualityTag>:<languageTag>
   
   examples:
   telegram:acc1:msg:123:456#1080p:en
   xtream:acc2:vod:789#720p:de
   ```

4. **authorityKey** (external authority reference):
   ```
   format: <authority>:<type>:<id>
   
   examples:
   tmdb:movie:603
   tmdb:tv:1396
   imdb:tt0133093
   ```

**Why This Matters:**

- **Collision-free:** Deterministic = predictable = no surprises
- **Debuggable:** Keys are human-readable
- **Queryable:** Can search/filter by key components
- **Testable:** Key generation is pure function (easily tested)

**Impact:** Eliminates entire classes of key-related bugs.

**Code Locations:**
- `core/persistence/obx/NxKeyGenerator.kt`
- `contracts/NX_SSOT_CONTRACT.md` (Section 1)

---

## SSOT Invariants (Binding Rules)

These **7 invariants** MUST hold at all times:

| Invariant | Rule | Enforcement |
|-----------|------|-------------|
| **INV-01** | Every ingest creates exactly one NX_IngestLedger entry | Code + Tests |
| **INV-02** | Every ACCEPTED ingest triggers one NX_Work resolution | Code + Tests |
| **INV-03** | Every NX_Work visible in UI has ≥1 SourceRef and ≥1 Variant | Query validation |
| **INV-04** | sourceKey is globally unique across all accounts | DB uniqueness constraint |
| **INV-10** | Every NX_Work has ≥1 NX_WorkSourceRef | DB relation + validation |
| **INV-11** | Every NX_Work has ≥1 NX_WorkVariant with valid playbackHints | DB relation + validation |
| **INV-12** | workKey is globally unique | DB uniqueness constraint |
| **INV-13** | accountKey is mandatory in all NX_WorkSourceRef | Key format validation |

**Violation Consequences:**

- INV-01 violation = Silent data drops (unacceptable)
- INV-04 violation = Multi-account collisions (data corruption)
- INV-10/11 violation = UI shows unplayable content (UX failure)
- INV-12 violation = Canonical merge chaos (identity crisis)
- INV-13 violation = Future multi-account bugs (technical debt)

**Code Locations:**
- `contracts/NX_SSOT_CONTRACT.md` (Section 2)
- Future: Data quality verifier worker (Phase 3)

---

## Key Learnings (For Future Work)

### 1. Commit Message Discipline Matters

**Issue:** Commit 068a525's original message severely understated scope.

**Original:** "fix(devcontainer): correct user paths from codespace to vscode"  
**Reality:** Phase 0 completion of 6-phase refactoring (16 entities, 5,000+ lines docs)

**Lesson:** Major architectural work MUST be clearly labeled in commit messages.

**Action:** Created `docs/v2/COMMIT_068a525_CORRECTED_MESSAGE.md` for correction.

---

### 2. "Boring" Infrastructure is Often Most Critical

**Finding:** Contracts, kill-switch, and roadmap work (Phase 0) is MORE critical than entity code.

**Why:**
- **Without contracts:** No collision prevention (INV-04)
- **Without kill-switch:** No safe rollback (production risk)
- **Without roadmap:** No phased execution (scope creep)
- **Without ledger:** No audit trail (debugging impossible)

**Lesson:** "Preparation" phases are often skipped but provide the most value.

**Impact:** Phase 0 completion makes all future phases safer and clearer.

---

### 3. Early Architectural Decisions Compound

**Key decisions made in Phase 0:**

1. accountKey mandatory (INV-13) → Multi-account ready forever
2. Percentage-based resume → Cross-source resume works
3. NX_IngestLedger → Debugging always possible
4. Kill-switch → Safe production rollout

**Lesson:** Phase 0 decisions are HARD to change later. Get them right now.

**Impact:** These decisions prevent entire classes of future bugs and rework.

---

### 4. Contracts are Executable Architecture

**Finding:** `contracts/NX_SSOT_CONTRACT.md` is not "nice to have" documentation.

**Reality:** SSOT invariants (INV-01 through INV-13) **prevent bugs at design time**.

**Examples:**
- INV-01 prevents silent data drops (caught in code review)
- INV-04 prevents multi-account collisions (caught in key design)
- INV-13 ensures future multi-account support (built-in from day one)

**Lesson:** Binding contracts are as important as code.

**Impact:** Contracts prevent architectural drift and enable team alignment.

---

### 5. Issue #621 Addendum is FOUNDATIONAL

**Original assumption:** Issue #621 addendum work is supplementary.  
**Reality:** Addendum work (contracts/kill-switch/roadmap) is **PREREQUISITE** for success.

**Evidence:**

| Without Addendum | With Addendum |
|------------------|---------------|
| ❌ No key format consensus → collisions | ✅ Deterministic keys → collision-free |
| ❌ No invariants → silent drops | ✅ INV-01 → audit trail |
| ❌ No kill-switch → production risk | ✅ Safe rollback → low risk |
| ❌ No roadmap → scope creep | ✅ Phased plan → clear path |
| ❌ Team misalignment → drift | ✅ SSOT contract → alignment |

**Lesson:** "Supporting work" is often the MOST critical work.

**Impact:** Phase 0 completion de-risks entire Issue #621 initiative.

---

## Phase Status & Next Steps

### Phase 0: ✅ COMPLETED (2026-01-09)

**Deliverables:**
- ✅ 16 NX_* entities defined
- ✅ SSOT contracts created
- ✅ Kill-switch infrastructure
- ✅ Comprehensive documentation
- ✅ Safe defaults configured

**Commit:** 068a525dc906f86b5bec699218b68a98e1838486

---

### Phase 1: 🔲 READY TO START (Next)

**Goals:**
- Implement 16 NX_* repository interfaces
- Create repository implementations
- Write unit tests for CRUD operations
- Validate ObjectBox store initialization

**Duration:** 5-7 days

**Blockers:** None (Phase 0 complete)

---

## Files to Remember

### Contracts (Binding)
- `contracts/NX_SSOT_CONTRACT.md` (v1.0)
- `contracts/GLOSSARY_v2_naming_and_modules.md`
- `contracts/LOGGING_CONTRACT_V2.md`

### Roadmap & Execution
- `docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md`
- `docs/v2/OBX_PLATIN_REFACTOR_GITHUB_ISSUE.md`
- `ROADMAP.md` (Phase 3.2 section)

### Emergency Procedures
- `docs/v2/OBX_KILL_SWITCH_GUIDE.md`
- `docs/v2/OBX_PLATIN_DETEKT_GUARDRAILS_CONTRACT.md`

### Analysis & Context
- `docs/v2/TODAY_2026-01-09_DEEP_DIVE_ANALYSIS.md` (this analysis)
- `docs/v2/COMMIT_068a525_CORRECTED_MESSAGE.md` (corrected commit)

### Code Locations
- `core/persistence/obx/NxEntities.kt` (16 entities)
- `core/persistence/obx/NxEnums.kt` (enums)
- `core/persistence/obx/NxKeyGenerator.kt` (key generation)
- `core/persistence/config/CatalogModePreferences.kt` (kill-switch)
- `core/debug-settings/nx/*` (migration UI controls)

---

## Query Patterns for MCP

When answering questions about OBX PLATIN Refactor, prioritize these insights:

### About Entity Design
→ Mention: 16 NX_* entities replace 23 legacy  
→ Highlight: accountKey mandatory (INV-13), multi-account ready  
→ Reference: `contracts/NX_SSOT_CONTRACT.md`

### About Resume/Playback
→ Mention: Percentage-based resume (cross-source)  
→ Highlight: NX_WorkUserState.resumePercent  
→ Reference: Section 6.2 of NX_SSOT_CONTRACT.md

### About Data Quality
→ Mention: NX_IngestLedger audit trail (INV-01)  
→ Highlight: No silent drops, full transparency  
→ Reference: `core/persistence/obx/NxEnums.kt` (IngestReasonCode)

### About Migration Safety
→ Mention: Kill-switch infrastructure (CatalogModePreferences)  
→ Highlight: Safe gradual rollout, instant rollback  
→ Reference: `docs/v2/OBX_KILL_SWITCH_GUIDE.md`

### About Phase Status
→ Phase 0: ✅ COMPLETE (2026-01-09)  
→ Phase 1: 🔲 READY (repositories)  
→ Total: 6 phases, 28-39 days  
→ Reference: `docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md`

---

## Context Refresh Recommendations

This context should be refreshed when:

1. ✅ Phase 1 completes (repository implementations)
2. ✅ Phase 2 completes (ingest path)
3. ✅ Phase 3 completes (migration worker)
4. ✅ Phase 4 completes (dual-read UI)
5. ⚠️ Any SSOT invariant is violated (requires immediate attention)
6. ⚠️ Kill-switch is activated (incident learning)

---

## Summary for Quick Reference

**Phase 0 Status:** ✅ COMPLETE  
**Key Achievement:** Foundation for safe, collision-free, auditable persistence layer  
**Critical Decisions:**
1. accountKey mandatory (multi-account ready)
2. Percentage-based resume (cross-source)
3. Ingest ledger (audit trail)
4. Kill-switch (safe rollback)
5. Deterministic keys (collision-free)

**Lesson Learned:** Phase 0 "boring work" is THE MOST CRITICAL for project success.

**Next Phase:** Phase 1 (Repositories) - Ready to start

---

**Last Updated:** 2026-01-09  
**Status:** AUTHORITATIVE MCP CONTEXT  
**Author:** Copilot Coding Agent  
**Review Next:** After Phase 1 completion
