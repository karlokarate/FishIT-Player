# Corrected Commit Message for 068a525

**Original Commit:** 068a525dc906f86b5bec699218b68a98e1838486  
**Branch:** architecture/v2-bootstrap  
**Date:** 2026-01-09 15:39:10 +0000  
**Author:** karlokarate <chrisfischtopher@googlemail.com>

## Original Commit Message (INCOMPLETE)

```
fix(devcontainer): correct user paths from codespace to vscode

- devcontainer.json: Fix remoteEnv ANDROID_HOME/SDK_ROOT paths
- settings.json: Fix android.sdk and terminal env paths
- post-create.sh: Use $HOME variable instead of hardcoded path
- launch.json: Remove broken Kotlin debug config (extension disabled)
- persistence/README.md: Add NX_* entity documentation
- DefaultObxDatabaseInspector.kt: Fix exportSchema indentation

All paths now correctly reference /home/vscode/ instead of
/home/codespace/ to match actual Codespace user.
```

## CORRECTED Commit Message (COMPREHENSIVE)

```
feat(persistence): OBX PLATIN Refactor Phase 0 - NX Entity Schema & Infrastructure (#621)

This commit establishes the foundational infrastructure for the OBX PLATIN
refactoring initiative, introducing the new NX_* entity schema and supporting
systems to replace 23 legacy Obx* entities with 16 unified NX_* entities.

**BREAKING CHANGES:**
- Introduces new NX_* entity schema (non-breaking as Phase 0 - not yet active)
- Adds CatalogMode kill-switch infrastructure for gradual migration
- Creates comprehensive SSOT contracts and roadmap documentation

**NX Entity System (16 New Entities)**

Core Work Graph:
- NX_Work: Central UI SSOT for canonical media works
- NX_WorkSourceRef: Links works to pipeline sources (multi-account ready)
- NX_WorkVariant: Playback variants (quality/encoding/language)
- NX_WorkRelation: Series ↔ Episode relationships

User State:
- NX_WorkUserState: Per-work user state (resume, watched, favorites)
- NX_WorkRuntimeState: Transient runtime state (buffering, errors)

Ingest & Audit:
- NX_IngestLedger: Audit trail for all ingest decisions (ACCEPT/REJECT/SKIP)

Profile System:
- NX_Profile: User profiles (main, kids, guest)
- NX_ProfileRule: Content filtering rules per profile
- NX_ProfileUsage: Profile usage tracking (screen time)

Source Management:
- NX_SourceAccount: Multi-account credentials per source

Cloud Sync (Preparation):
- NX_CloudOutboxEvent: Pending cloud sync events

Content Discovery:
- NX_WorkEmbedding: Vector embeddings for semantic search

Migration Support:
- NX_WorkRedirect: Canonical merge redirects
- NX_Category: Content categories
- NX_WorkCategoryRef: Work ↔ Category links

**Key Infrastructure Components**

core/persistence:
- NxEntities.kt: All 16 NX_* entity definitions
- NxEnums.kt: WorkType, IngestDecision, IngestReasonCode, SourceType enums
- NxKeyGenerator.kt: Deterministic key generation (workKey, sourceKey, variantKey)
- CatalogModePreferences.kt: Kill-switch for LEGACY/DUAL/NX_ONLY modes
- ObxWriteConfig.kt: Device-aware batch sizing (FireTV safety)

core/debug-settings:
- NxMigrationSettingsRepository.kt: Runtime mode control (DataStore-backed)
- NxMigrationMode.kt: CatalogReadMode, CatalogWriteMode, MigrationMode enums

**SSOT Contracts & Documentation**

New Binding Contracts:
- contracts/NX_SSOT_CONTRACT.md: Key formats, invariants, ingest rules
- docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md: 6-phase execution plan (28-39 days)
- docs/v2/OBX_KILL_SWITCH_GUIDE.md: Emergency rollback procedures
- docs/v2/OBX_PLATIN_DETEKT_GUARDRAILS_CONTRACT.md: CI enforcement rules

Supporting Documentation:
- docs/v2/OBX_PLATIN_REFACTOR_GITHUB_ISSUE.md: Issue #621 template
- docs/v2/obx/*: Entity traceability, dependency graphs, phase summaries

**Key Formats (Deterministic)**

```
workKey:      <workType>:<canonicalSlug>:<year|LIVE>
sourceKey:    <sourceType>:<accountKey>:<sourceId>
variantKey:   <sourceKey>#<qualityTag>:<languageTag>
authorityKey: <authority>:<type>:<id>
```

**SSOT Invariants (BINDING)**

- INV-01: Every ingest creates exactly one NX_IngestLedger entry (no silent drops)
- INV-02: Every ACCEPTED ingest triggers one NX_Work resolution (link-or-create)
- INV-03: Every NX_Work visible in UI has ≥1 SourceRef and ≥1 Variant
- INV-04: sourceKey is globally unique across all accounts
- INV-10: Every NX_Work has ≥1 NX_WorkSourceRef
- INV-11: Every NX_Work has ≥1 NX_WorkVariant with valid playbackHints
- INV-12: workKey is globally unique
- INV-13: accountKey is mandatory in all NX_WorkSourceRef

**Migration Strategy**

Phase 0-3: LEGACY/LEGACY (default, safe)
Phase 4:   DUAL_READ/DUAL_WRITE (validation)
Phase 5:   NX_ONLY/NX_ONLY (target state)
Phase 6:   NX_ONLY/NX_ONLY (cleanup legacy)

**DevContainer Fixes (Original Scope)**

- devcontainer.json: Fix remoteEnv ANDROID_HOME/SDK_ROOT paths (/home/vscode/)
- .vscode/settings.json: Fix android.sdk and terminal env paths
- post-create.sh: Use $HOME variable instead of hardcoded /home/codespace/
- launch.json: Remove broken Kotlin debug config (extension disabled)
- DefaultObxDatabaseInspector.kt: Fix exportSchema indentation

**GitHub Infrastructure**

- .github/copilot-instructions.md: Repository-wide Copilot guidance
- .github/instructions/*: 21 path-scoped PLATIN instruction files
- .github/workflows/*: CI/CD workflows for v2 architecture
- .github/agents/*: Custom agent configurations
- .github/codex/*: Bot automation infrastructure

**Testing**

- NxMigrationModeTest.kt: Enum validation tests
- NxKeyGeneratorTest.kt: Key format validation tests
- ObxWriteConfigTest.kt: Device-aware batch sizing tests

**References**

- Issue: #621 (OBX PLATIN Refactor)
- Contract: contracts/NX_SSOT_CONTRACT.md (v1.0)
- Roadmap: docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md
- Architecture: AGENTS.md Section 4

**Impact Analysis**

Files Changed: 2,274 (mostly new infrastructure)
Core Changes:
- 16 new NX_* entities (827 lines in NxEntities.kt)
- 375 lines of enums (NxEnums.kt)
- Comprehensive contracts and documentation (>5,000 lines)
- Kill-switch infrastructure for safe gradual migration

Risk Level: LOW (Phase 0 - entities defined but not yet active)
Rollback: Native (CatalogReadMode.LEGACY default ensures zero impact)

**Phase 0 Status: ✅ COMPLETE**

- [x] NX_SSOT_CONTRACT.md created and comprehensive
- [x] All 16 NX_* entities defined with proper indexes/uniqueness
- [x] CatalogMode kill-switch infrastructure implemented
- [x] Deterministic key generators implemented
- [x] Comprehensive documentation and contracts created
- [x] Safe defaults configured (LEGACY mode only)

**Next Steps (Phase 1)**

- [ ] Implement repository interfaces for NX_* entities
- [ ] Create migration worker (Legacy → NX)
- [ ] Build normalizer gate with ingest rules
- [ ] Implement data quality verifier worker
```

## Why This Correction Matters

The original commit message severely **understated** the scope and importance of this work:

### Original Message Problems

1. **Title misleading**: "fix(devcontainer)" suggests only path fixes
2. **Missing feat type**: This is a major feature, not a fix
3. **No Issue reference**: #621 not mentioned
4. **NX entities buried**: "Add NX_* entity documentation" drastically understates the creation of 16 full entities
5. **No architecture context**: Doesn't explain this is Phase 0 of a major refactor
6. **Missing impact**: 2,274 files changed not acknowledged
7. **No contract references**: Doesn't mention binding SSOT contracts

### Actual Commit Significance

This commit represents:

- **Phase 0 completion** of a 6-phase, 28-39 day refactoring initiative
- **Foundation work** for replacing the entire persistence layer
- **Architectural shift** from 23 scattered entities to 16 unified ones
- **Multi-account readiness** (accountKey mandatory in all keys)
- **Audit trail system** (NX_IngestLedger for transparency)
- **Kill-switch infrastructure** (safe gradual migration)
- **Comprehensive SSOT** (contracts prevent future drift)

### Importance for Issue #621

This commit establishes the **critical foundation** for Issue #621 success:

1. **Deterministic key formats** prevent collision issues
2. **SSOT invariants** prevent silent data drops
3. **Kill-switch** enables safe production rollout
4. **Comprehensive docs** enable team alignment
5. **Multi-account ready** from day one (no future rework)

## Recommendation

Since this commit is already on `origin/architecture/v2-bootstrap`, we should:

1. ✅ **Document the correction** (this file)
2. ✅ **Reference in Issue #621** with correct summary
3. ✅ **Update MCP context** to reflect actual work done
4. ✅ **Audit documentation** to ensure alignment
5. ❌ **Don't force-push rewrite** (risks breaking others' work)

## Cross-References

- Original commit: `068a525dc906f86b5bec699218b68a98e1838486`
- Branch: `architecture/v2-bootstrap`
- Issue: #621
- Contracts: `contracts/NX_SSOT_CONTRACT.md`, `docs/v2/OBX_PLATIN_REFACTOR_ROADMAP.md`
- Related docs: All files in `docs/v2/obx/`

---

**Last Updated:** 2026-01-09  
**Status:** AUTHORITATIVE CORRECTION  
**Author:** Copilot Coding Agent
