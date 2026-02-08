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
├── scope-guard.schema.json         # Schema for *.scope.json files
├── scope-guard.config.json         # Server configuration with bundles
│
│── # Core Scopes (2)
├── catalog-sync.scope.json         # core/catalog-sync - sync orchestration
├── persistence.scope.json          # core/persistence - ObjectBox entities
│
│── # Telegram Scopes (3)
├── telegram-transport.scope.json   # infra/transport-telegram - TDLib
├── telegram-pipeline.scope.json    # pipeline/telegram - message bundling
├── telegram-playback.scope.json    # playback/telegram - streaming
│
│── # Xtream Scopes (7)
├── xtream-transport-core.scope.json      # API client, DTOs
├── xtream-transport-auth.scope.json      # Credentials, authentication
├── xtream-transport-streaming.scope.json # Jackson streaming parser
├── xtream-data.scope.json                # NX repositories
├── xtream-pipeline-catalog.scope.json    # Scan phases, orchestration
├── xtream-pipeline-mapping.scope.json    # DTO → RawMediaMetadata
├── xtream-playback.scope.json            # PlaybackSourceFactory
│
│── # Other Pipelines (2)
├── io-pipeline.scope.json          # pipeline/io - local files (stub)
├── audiobook-pipeline.scope.json   # pipeline/audiobook (stub)
│
│── # Supplementary Files
├── layer-boundaries.rules.json     # Layer hierarchy & import rules
├── agent-checklists.rules.json     # Pre/Post-change checklists
├── naming-rules.yaml               # Naming conventions
├── audit.log                       # Scope Guard audit trail
└── README.md                       # This file

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
