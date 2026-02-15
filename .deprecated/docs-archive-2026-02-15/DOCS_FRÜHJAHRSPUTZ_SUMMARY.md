# Docs Frühjahrsputz - Final Summary

**Date:** 2026-02-04  
**Branch:** copilot/clean-up-docs-structure  
**Status:** Complete ✅

---

## Executive Summary

This documentation cleanup ("Frühjahrsputz" = Spring Cleaning) established a **Single Source of Truth (SSOT)** structure for FishIT-Player v2, eliminating 144 duplicate/obsolete documents and creating clear documentation zones with drift prevention mechanisms.

---

## What Was Done

### Phase 1: Root Cleanup
- **Deleted:** 131 obsolete root markdown files
- **Kept:** Only 5 essential files (README.md, V2_PORTAL.md, AGENTS.md, ROADMAP.md, CHANGELOG.md)
- **Impact:** Root directory reduced from 136 files to 5

### Phase 2: AGENTS/Copilot Synchronization
- **Created:** Canonical source at `docs/meta/AGENT_RULES_CANONICAL.md`
- **Generated:** AGENTS.md and .github/copilot-instructions.md (byte-identical)
- **Added:** Sync/verify scripts (`scripts/sync-agent-rules.sh`, `scripts/verify-agent-rules.sh`)
- **Protected:** CI workflow to prevent drift (`.github/workflows/verify-agent-rules.yml`)

### Phase 3: Contracts Consolidation
- **Moved:** MEDIA_NORMALIZATION_CONTRACT.md from docs/v2 to contracts/
- **Moved:** CATALOG_SYNC_WORKERS_CONTRACT_V2.md from docs/ to contracts/
- **Merged:** 2 TMDB contracts → single TMDB_ENRICHMENT_CONTRACT.md
- **Deleted:** Duplicate NX_SSOT_CONTRACT.md from docs/v2
- **Result:** All 20 binding contracts now in `/contracts/` (single location)

### Phase 4: Path-Scoped Instructions Updates
- **Updated:** 8 instruction files to point to `/contracts/` instead of `/docs/v2/`
- **Removed:** All references to deleted `.github/tdlibAgent.md`
- **Updated:** Binding contracts table in `_index.instructions.md`

### Phase 5: docs/ Cleanup
- **Deleted:** docs/v2/NAMING_INVENTORY_v2.md (no unique content)
- **Deleted:** docs/v2/telegram/* (5 files merged into contracts)
- **Deleted:** Duplicate MEDIA_NORMALIZATION_AND_UNIFICATION.md
- **Deleted:** Duplicate TMDB_IMPLEMENTATION_PLAN.md
- **Deleted:** .github/tdlibAgent.md (content now in contracts/TELEGRAM_*.md)

### Phase 6: Update References
- **Updated:** V2_PORTAL.md to point to correct SSOT locations
- **Updated:** contracts/README.md with new structure

---

## New Documentation Structure

```
/
├── README.md              - Entry point / router
├── V2_PORTAL.md           - V2 architecture portal
├── AGENTS.md              - Generated (DO NOT EDIT)
├── ROADMAP.md             - Project roadmap
├── CHANGELOG.md           - Version history
│
├── /contracts/            - 20 BINDING CONTRACTS (normative)
│   ├── Core Contracts (7)
│   ├── Player Contracts (8)
│   └── Pipeline Contracts (5)
│
├── /docs/
│   ├── /dev/             - Dev setup & CI guardrails
│   ├── /v2/              - Architecture overviews (explanatory)
│   └── /meta/            - Build input (not public docs)
│       └── AGENT_RULES_CANONICAL.md ← EDIT THIS
│
├── /.github/
│   ├── /instructions/    - 23 path-scoped rules
│   ├── /workflows/       - CI checks
│   └── copilot-instructions.md - Generated (DO NOT EDIT)
│
└── /scripts/
    ├── sync-agent-rules.sh
    └── verify-agent-rules.sh
```

---

## Document Zones (Mandatory Rules)

### Zone 1: Root (5 files only)
- **Purpose:** Entry points and high-level navigation
- **Rule:** No technical docs, no binding rules
- **Allowed:** README.md, V2_PORTAL.md, AGENTS.md, ROADMAP.md, CHANGELOG.md

### Zone 2: /contracts/ (Binding Contracts)
- **Purpose:** Normative, binding specifications
- **Rule:** All architectural rules that code MUST follow
- **Files:** 20 contracts (GLOSSARY, LOGGING, MEDIA_NORMALIZATION, etc.)

### Zone 3: /docs/dev/ (Development Setup)
- **Purpose:** How-to guides for setup and CI
- **Rule:** Explanatory only, no new rules
- **Files:** ENV_SETUP.md, LOCAL_SETUP.md, ARCH_GUARDRAILS.md

### Zone 4: /docs/v2/ (Architecture Overviews)
- **Purpose:** Explanatory architecture documentation
- **Rule:** No binding rules, only explanations
- **Files:** Design documents, migration guides, status reports

### Zone 5: /docs/meta/ (Build Input)
- **Purpose:** Source files for generated docs
- **Rule:** Not public-facing, only for builds
- **Files:** AGENT_RULES_CANONICAL.md, diffs/, etc.

### Zone 6: /.github/instructions/ (Path-Scoped Rules)
- **Purpose:** Copilot auto-applied coding rules
- **Rule:** Specific to file paths, auto-applied by VS Code Copilot
- **Files:** 23 instruction files (core-*.md, infra-*.md, etc.)

---

## Drift Prevention System

### The Problem
Previously, AGENTS.md and .github/copilot-instructions.md had different content, causing confusion and inconsistent agent behavior.

### The Solution
1. **Single Canonical Source:** `docs/meta/AGENT_RULES_CANONICAL.md`
2. **Generated Files:** AGENTS.md and copilot-instructions.md are byte-identical, generated from canonical
3. **Sync Script:** `bash scripts/sync-agent-rules.sh` regenerates both files
4. **Verify Script:** `bash scripts/verify-agent-rules.sh` checks for drift
5. **CI Check:** GitHub Actions workflow fails if files diverge

### Usage

**To edit agent rules:**
```bash
# 1. Edit the canonical source
vim docs/meta/AGENT_RULES_CANONICAL.md

# 2. Regenerate both files
bash scripts/sync-agent-rules.sh

# 3. Commit all three files together
git add docs/meta/AGENT_RULES_CANONICAL.md AGENTS.md .github/copilot-instructions.md
git commit -m "Update agent rules"
```

**To check for drift:**
```bash
bash scripts/verify-agent-rules.sh
# Output: ✓ All agent rules files are synchronized
```

**Never edit directly:**
- ❌ AGENTS.md
- ❌ .github/copilot-instructions.md

---

## Metrics

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Root .md files | 136 | 5 | -131 (-96%) |
| Total deleted files | - | 144 | - |
| Contracts in /contracts/ | 17 | 20 | +3 (moved) |
| TMDB contracts | 2 | 1 | Merged |
| Agent rule files | 2 (divergent) | 3 (1 canonical + 2 generated) | Synchronized |
| Duplicate docs | Many | 0 | Eliminated |

---

## Key Benefits

1. **Single Source of Truth** - Each topic has exactly one authoritative document
2. **No More Drift** - AGENTS.md and copilot-instructions.md stay in sync via CI
3. **Clear Zones** - 6 distinct documentation zones with clear purposes
4. **No Archives** - Old docs deleted, not just moved (prevents backward-digging)
5. **No Duplicate Rules** - All binding rules in /contracts/ or .github/instructions/
6. **Better Discovery** - contracts/README.md is comprehensive inventory
7. **CI Protection** - Workflow prevents accidental drift

---

## Migration Guide for Contributors

### Before This Change
- Look in root for docs
- Check /docs/v2/ for contracts
- Duplicate content in multiple places
- AGENTS.md ≠ copilot-instructions.md

### After This Change
- Binding contracts: `/contracts/`
- Architecture docs: `/docs/v2/`
- Dev setup: `/docs/dev/`
- Path-scoped rules: `/.github/instructions/`
- Agent rules: Edit `docs/meta/AGENT_RULES_CANONICAL.md` only

### Finding Documentation
1. **Entry Point:** README.md or V2_PORTAL.md
2. **Binding Rules:** Check `/contracts/` (see contracts/README.md)
3. **Code Rules:** Check `/.github/instructions/` (auto-applied by Copilot)
4. **Architecture:** Check `/docs/v2/` (explanatory)
5. **Setup:** Check `/docs/dev/` (how-to)

---

## Verification Checklist

- [x] Root has only 5 .md files
- [x] All contracts in `/contracts/` (20 files)
- [x] AGENTS.md = .github/copilot-instructions.md (byte-identical)
- [x] Sync script works: `bash scripts/sync-agent-rules.sh`
- [x] Verify script works: `bash scripts/verify-agent-rules.sh`
- [x] CI workflow added: `.github/workflows/verify-agent-rules.yml`
- [x] contracts/README.md updated with new locations
- [x] V2_PORTAL.md references updated
- [x] .github/instructions/* references updated
- [x] No references to deleted docs
- [x] No duplicate/forwarder docs remain

---

## Related Issues

- Original Issue: "Docs Frühjahrsputz"
- PR: copilot/clean-up-docs-structure
- Follows: SSOT consolidation plan (25 topics)

---

## Next Steps (Optional)

The issue mentioned these as parallel/future work:

1. **Code SSOT Hotspots** (Quick Wins):
   - Shared Type Mappers (WorkType/SourceType/MediaType conversions)
   - CategoryFallbackStrategy (Xtream)
   - Unified Headers Profiles (API vs Playback)

2. **CC Refactor** (Complexity Reduction):
   - XtreamCatalogPipelineImpl → Orchestrator + phase/* handlers
   - DefaultXtreamApiClient → client/*, streaming/*, mapper/* delegation

3. **CI/Tooling**:
   - markdownlint-cli2 (style)
   - lychee (link checker)
   - cspell (typos)
   - Detekt CC gate ≤ 15

These are **NOT** part of this docs cleanup and should be separate PRs.

---

## Conclusion

The FishIT-Player v2 documentation is now in a **PLATIN-compliant SSOT structure**:

- ✅ 144 obsolete docs deleted
- ✅ 20 binding contracts centralized in `/contracts/`
- ✅ 6 clear documentation zones
- ✅ Drift prevention via canonical source + CI
- ✅ Zero duplicate rules
- ✅ Clean root directory (5 files)

**The documentation wildgrowth problem is solved.**
