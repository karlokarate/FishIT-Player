# Docs Frühjahrsputz - Completion Summary

**Date:** 2026-02-04  
**Status:** ✅ COMPLETE  
**Issue:** Docs Frühjahrsputz (Spring Cleaning)

---

## Overview

Successfully consolidated FishIT-Player v2 documentation following the SSOT (Single Source of Truth) principle. Removed 144 obsolete/duplicate files and established a clear document zone structure.

---

## What Was Done

### Phase 1: SSOT Root Cleanup ✅

**Goal:** Keep only 5 essential root files, delete all others.

**Result:**
- **Kept (5 files):**
  - `README.md` - Project router
  - `V2_PORTAL.md` - v2 entry point
  - `AGENTS.md` - Architecture rules (generated)
  - `ROADMAP.md` - Project roadmap
  - `CHANGELOG.md` - Version history

- **Deleted (131 files):** All obsolete root docs including:
  - Build guides and checklists
  - Bug fix summaries and analyses
  - Performance reports
  - Logcat analyses
  - Sync implementation summaries
  - All temporary/historical docs

### Phase 2: AGENTS/Copilot Synchronization ✅

**Goal:** Prevent drift between AGENTS.md and .github/copilot-instructions.md.

**Implementation:**
- Created canonical source: `docs/meta/AGENT_RULES_CANONICAL.md`
- Generated both `AGENTS.md` and `.github/copilot-instructions.md` from canonical (byte-identical)
- Added sync script: `scripts/sync-agent-rules.sh`
- Added verify script: `scripts/verify-agent-rules.sh`
- Added CI workflow: `.github/workflows/verify-agent-rules.yml`

**Drift Prevention:**
```bash
# To sync after editing canonical:
bash scripts/sync-agent-rules.sh

# CI check on every PR:
bash scripts/verify-agent-rules.sh
```

### Phase 3: Contracts Consolidation ✅

**Goal:** Move all binding contracts to `/contracts/`, remove duplicates.

**Actions:**
- Moved `MEDIA_NORMALIZATION_CONTRACT.md` from `docs/v2/` to `contracts/` (full content, replaced forwarder)
- Moved `CATALOG_SYNC_WORKERS_CONTRACT_V2.md` from `docs/` to `contracts/`
- Merged 2 TMDB contracts into `contracts/TMDB_ENRICHMENT_CONTRACT.md`:
  - `docs/v2/TMDB_ENRICHMENT_CONTRACT.md`
  - `contracts/TMDB Canonical Identity & Imaging SSOT Contract (v2).md`
- Removed duplicate `docs/v2/NX_SSOT_CONTRACT.md` (kept one in `contracts/`)

### Phase 4: Path-Scoped Instructions Updates ✅

**Goal:** Update all references to contracts in instruction files.

**Files Updated (8):**
- `.github/instructions/_index.instructions.md`
- `.github/instructions/core-domain.instructions.md`
- `.github/instructions/core-model.instructions.md`
- `.github/instructions/core-normalizer.instructions.md`
- `.github/instructions/core-persistence.instructions.md`
- `.github/instructions/infra-data.instructions.md`
- `.github/instructions/infra-transport-telegram.instructions.md`
- `.github/instructions/pipeline.instructions.md`

**Changes:**
- Updated contract paths: `docs/v2/` → `contracts/`
- Removed references to deleted `.github/tdlibAgent.md`
- Updated binding contracts table

### Phase 5: docs/ Cleanup ✅

**Goal:** Remove duplicates and consolidated telegram docs.

**Deleted (13 files):**
- `docs/v2/NAMING_INVENTORY_v2.md` (no unique content)
- `docs/v2/MEDIA_NORMALIZATION_AND_UNIFICATION.md` (duplicate)
- `docs/v2/TMDB_IMPLEMENTATION_PLAN.md` (duplicate)
- `docs/v2/telegram/` (5 files - content merged into contracts)
- `.github/tdlibAgent.md` (content now in contracts/TELEGRAM_*.md)

### Phase 6: Update V2_PORTAL.md ✅

**Goal:** Update references to point to correct SSOT locations.

**Changes:**
- Updated `MEDIA_NORMALIZATION_CONTRACT` reference to `/contracts/`
- Removed reference to deleted `MEDIA_NORMALIZATION_AND_UNIFICATION.md`

### Phase 7: Update contracts/README.md ✅

**Goal:** Reflect new SSOT structure in contracts inventory.

**Changes:**
- Updated contract locations table
- Added `DTO_PLAYBOOK.md` to core contracts
- Updated canonical locations section
- Updated reading requirements table

---

## Final Structure

### Root Directory (5 files only)
```
/
├── README.md (router)
├── V2_PORTAL.md (entry point)
├── AGENTS.md (generated - DO NOT EDIT)
├── ROADMAP.md
└── CHANGELOG.md
```

### Document Zones

#### `/contracts/` - Binding Contracts (Normative)
**20 contracts** - "Gesetzestext" (legal text):
- `GLOSSARY_v2_naming_and_modules.md`
- `DTO_PLAYBOOK.md`
- `MEDIA_NORMALIZATION_CONTRACT.md`
- `TMDB_ENRICHMENT_CONTRACT.md`
- `CATALOG_SYNC_WORKERS_CONTRACT_V2.md`
- `LOGGING_CONTRACT_V2.md`
- `NX_SSOT_CONTRACT.md`
- `INTERNAL_PLAYER_*.md` (8 contracts)
- `TELEGRAM_*.md` (4 contracts)
- `XTREAM_SCAN_PREMIUM_CONTRACT_V1.md`

#### `/docs/dev/` - Dev Setup & CI Guardrails
How-to guides:
- `ENV_SETUP.md`
- `LOCAL_SETUP.md`
- `IDE_SETUP_GUIDE.md`
- `ARCH_GUARDRAILS.md`
- `ARCH_GUARDRAILS_IMPLEMENTATION.md`

#### `/docs/v2/` - Architecture Overviews
Explanatory docs (non-normative):
- `architecture/`
- `internal-player/`
- `logging/`
- Feature-specific guides
- Design documents

#### `/docs/meta/` - Build Input (Not Public Docs)
```
docs/meta/
├── AGENT_RULES_CANONICAL.md ← EDIT THIS
└── README.md
```

#### `/.github/instructions/` - Path-Scoped Coding Rules
Auto-applied by Copilot:
- 22 instruction files (e.g., `core-model.instructions.md`)
- `_index.instructions.md` (inventory)
- `copilot-instructions.md` (generated - DO NOT EDIT)

#### `/scripts/` - Automation
```
scripts/
├── sync-agent-rules.sh (generate AGENTS.md + copilot-instructions.md)
└── verify-agent-rules.sh (CI check for drift)
```

---

## Metrics

### Files Deleted: 144 Total
- 131 root markdown files
- 13 docs/ duplicates/forwarders

### Files Moved: 3
- `MEDIA_NORMALIZATION_CONTRACT.md`: docs/v2 → contracts
- `CATALOG_SYNC_WORKERS_CONTRACT_V2.md`: docs → contracts
- `TMDB_ENRICHMENT_CONTRACT.md`: merged from 2 sources

### Files Merged: 2 → 1
- Old TMDB contracts → `contracts/TMDB_ENRICHMENT_CONTRACT.md`

### Root Files: 5 (down from 136)
- 96% reduction in root clutter

---

## Drift Prevention Mechanism

### How It Works

1. **Single Canonical Source**
   - `docs/meta/AGENT_RULES_CANONICAL.md` contains the master content

2. **Generation**
   - `AGENTS.md` generated for humans
   - `.github/copilot-instructions.md` generated for Copilot
   - Both are byte-identical (except header warning)

3. **Sync Script**
   ```bash
   bash scripts/sync-agent-rules.sh
   ```
   - Reads canonical source
   - Writes to both targets
   - Adds "DO NOT EDIT" warnings

4. **Verify Script**
   ```bash
   bash scripts/verify-agent-rules.sh
   ```
   - Compares all 3 files (ignoring headers)
   - Returns exit code 0 if synchronized
   - Returns exit code 1 if drift detected

5. **CI Check**
   - GitHub Actions workflow: `.github/workflows/verify-agent-rules.yml`
   - Runs on every PR
   - Fails if files diverge

### Editing Workflow

```bash
# 1. Edit the canonical source
vim docs/meta/AGENT_RULES_CANONICAL.md

# 2. Sync to targets
bash scripts/sync-agent-rules.sh

# 3. Verify
bash scripts/verify-agent-rules.sh

# 4. Commit all 3 files
git add docs/meta/AGENT_RULES_CANONICAL.md AGENTS.md .github/copilot-instructions.md
git commit -m "Update agent rules"
```

---

## Key Benefits

### 1. Single Source of Truth
Each topic has exactly one authoritative document. No more "which one is current?"

### 2. No More Drift
AGENTS.md and copilot-instructions.md stay in sync via automation + CI.

### 3. Clear Zones
- Root: 5 router files only
- `/contracts/`: Binding rules
- `/docs/dev/`: Setup guides
- `/docs/v2/`: Architecture explanations
- `/.github/instructions/`: Path-scoped rules

### 4. No Archives
Old docs deleted, not just moved. Prevents agents from citing outdated info.

### 5. No Duplicate Rules
All binding rules in `/contracts/` or `/.github/instructions/`. No second "Gesetzestexte" hiding in `/docs/`.

### 6. Provider-Agnostic
Xtream code remains provider-agnostic (no testanbieter hardcoding).

---

## What Agents Should Know

### Where to Find Rules

| Need | Location |
|------|----------|
| Architecture rules | `AGENTS.md` or `.github/copilot-instructions.md` (identical) |
| Binding contracts | `/contracts/*.md` |
| Path-scoped rules | `.github/instructions/*.instructions.md` (auto-applied) |
| Dev setup | `/docs/dev/*.md` |
| Architecture design | `/docs/v2/*.md` |

### What NOT to Edit

**Never manually edit:**
- `AGENTS.md` (generated)
- `.github/copilot-instructions.md` (generated)

**Always edit:**
- `docs/meta/AGENT_RULES_CANONICAL.md` (source)
- Then run: `bash scripts/sync-agent-rules.sh`

### Where to Add New Docs

| Type | Location |
|------|----------|
| Binding contract | `/contracts/` |
| Dev setup/CI | `/docs/dev/` |
| Architecture overview | `/docs/v2/` |
| Path-scoped coding rule | `.github/instructions/` |

**Never add to root** (except the 5 keepers).

---

## CI Integration

### Workflow: `.github/workflows/verify-agent-rules.yml`

```yaml
name: Verify Agent Rules
on: [pull_request]
jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Verify agent rules sync
        run: bash scripts/verify-agent-rules.sh
```

**Fails if:**
- `AGENTS.md` ≠ `.github/copilot-instructions.md`
- Either differs from `docs/meta/AGENT_RULES_CANONICAL.md`

---

## Next Steps (Optional Future Work)

### Additional CI Gates (from issue)

1. **markdownlint-cli2** - Style linting
2. **lychee** - Link checker
3. **cspell** - Typo detection
4. **vale** (optional) - Terminology enforcement

### Code SSOT Hotspots (from issue)

1. **Shared Type Mappers** - Single file for WorkType/SourceType/MediaType conversions
2. **CategoryFallbackStrategy** - Centralized Xtream fallback logic
3. **Unified Headers Profiles** - API vs Playback headers (Premium Contract)

### CC-Refactor (from issue)

- `XtreamCatalogPipelineImpl.scanCatalog()` → Orchestrator + phase handlers
- `DefaultXtreamApiClient.kt` → Delegation pattern

---

## Questions?

**Where's the contract for X?**  
→ Check `/contracts/README.md` for inventory

**How do I add a new binding rule?**  
→ Add to relevant contract in `/contracts/` or instruction in `.github/instructions/`

**How do I update AGENTS.md?**  
→ Edit `docs/meta/AGENT_RULES_CANONICAL.md`, then run `bash scripts/sync-agent-rules.sh`

**Why are AGENTS.md and copilot-instructions.md identical?**  
→ Copilot doesn't reliably read AGENTS.md, so we maintain byte-identical twins for reliability

**What if I accidentally edit AGENTS.md directly?**  
→ CI will fail. Re-sync from canonical: `bash scripts/sync-agent-rules.sh`

---

## Conclusion

Documentation cleanup complete. The repository now follows a strict SSOT principle with:
- 5 root files only
- Clear document zones
- Drift prevention via automation
- No duplicate or archived docs
- 96% reduction in root clutter

All binding contracts are now in `/contracts/`, all path-scoped rules in `.github/instructions/`, and AGENTS.md is kept in sync with copilot-instructions.md via CI.

**Mission accomplished.** 🎯
