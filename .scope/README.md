# Scope Guards System

> **Purpose:** Prevent LLM agents from "losing scope" during long conversations.

## Problem Statement

When an LLM agent works through a multi-step task:
- At **Step 1**: Rules established ("Don't duplicate methods", "Read all 27 files first")
- At **Step 5**: Agent "forgets" rules from Step 1 due to context window limitations
- **Result**: Duplicate methods, missed patterns, architectural violations

## Solution: Scope Guard Files

Each `.scope/*.scope.json` file defines a **bounded context** that must be fully considered before any edit:

```
.scope/
├── scope-guard.schema.json       # Schema for *.scope.json files
├── catalog-sync.scope.json       # 27 files, ~5400 LOC
├── persistence.scope.json        # 20 entities, repositories
├── xtream-pipeline.scope.json    # 26 files, ~3500 LOC (TODO)
├── layer-boundaries.rules.json   # Layer hierarchy & import rules
├── agent-checklists.rules.json   # Pre/Post-change checklists
├── naming-rules.yaml             # Naming conventions
└── README.md                     # This file

Note: Only *.scope.json files follow the scope-guard schema.
      *.rules.json and *.yaml are supplementary reference files.
```

## How Agents Should Use This

### Before ANY edit:

1. **Identify the scope** - Which scope.json covers the file being edited?
2. **Read the scope file** - It's small enough to fit in context
3. **Verify invariants** - Check that planned change doesn't violate any
4. **Consider related scopes** - Check `relatedScopes` array

### What each scope file contains:

| Section | Purpose |
|---------|---------|
| `mandatoryReadBeforeEdit` | Documents that MUST be read first |
| `criticalFiles` | Files with their purpose and invariants |
| `globalInvariants` | Rules that apply to ALL files in scope |
| `forbiddenPatterns` | Anti-patterns to avoid |
| `consumers` | Who depends on this code |
| `testCoverage` | Current test status |
| `knownBugs` | Tracked bugs in specific files |

## Example Workflow

**Agent receives task:** "Add a new sync method to DefaultXtreamSyncService.kt"

1. File is in `core/catalog-sync` → Read `.scope/catalog-sync.scope.json`
2. Scope shows this file has invariants:
   - "Must respect SyncStrategy from IncrementalSyncDecider"
   - "Must inject FingerprintRepository for Tier 4"
3. Scope shows knownBugs:
   - "executePhase() ignores SyncStrategy - FIX PENDING"
4. Check `globalInvariants`:
   - "Workers MUST call CatalogSyncWorkScheduler"
5. Check `consumers`:
   - 4 files depend on this service
6. **Now agent has full context** to make a safe change

## Creating New Scope Files

Use the schema:
```bash
# Validate a scope file:
cat .scope/my-scope.scope.json | python3 -m json.tool
```

Key sections to define:
- `scopeId` - Unique identifier
- `criticalFiles` - Most important files with their invariants
- `globalInvariants` - Rules that apply everywhere
- `forbiddenPatterns` - Common mistakes to avoid
- `testCoverage` - Track test gaps

## Limitations

This is a **workaround**, not a solution to the fundamental context window problem:

- Agent must actively read the scope file (not enforced)
- Large scopes may still exceed context
- Requires manual maintenance of scope files

Better solutions would require:
- MCP tools that enforce scope reading
- IDE integration that blocks edits without scope check
- Multi-agent orchestration with scope enforcement

## Maintenance

After significant changes:
1. Update `lastVerified` date
2. Update `fileCount` and `totalLOC` if changed
3. Add new `criticalFiles` if important files added
4. Update `knownBugs` as bugs are found/fixed
5. Update `testCoverage` after writing tests
