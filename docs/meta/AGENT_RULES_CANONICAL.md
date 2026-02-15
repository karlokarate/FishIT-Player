# Agent Rules – FishIT-Player v2

> **Branch:** `architecture/v2-bootstrap` | **Namespace:** `com.fishit.player.*`

---

## 🚨 HARD RULES (NON-NEGOTIABLE)

```yaml
MUST_READ_BEFORE_ANY_CHANGE:
  - /contracts/GLOSSARY_v2_naming_and_modules.md
  - Relevant scope file from /.scope/
  - Module README.md if modifying that module

NEVER_DO:
  - Merge to main from v2 branches
  - Edit files under /legacy/** or /app/**
  - Use namespace com.chris.m3usuite outside legacy/
  - Reintroduce TdlibClientProvider (v1 pattern)
  - Add Obx* imports in UI/ViewModel/feature modules
  - Call pipelines directly from Workers (use CatalogSyncWorkScheduler)
  - Import pipeline/** or data/** from player/** modules
  - Create a second implementation when an SSOT already exists for that purpose
  - Keep duplicate/fallback implementations that serve the same purpose as an SSOT
  - Skip duplicate findings — ALWAYS consolidate or document + roadmap
  - Write legacy/migration/backward-compat code for old entity data (DEV PHASE)
  - Add fallback readers for old key formats or field layouts (DEV PHASE)
  - Keep "legacy" codepaths that reconstruct data from denormalized entity fields

ALWAYS_DO:
  - Check /.scope/*.scope.json before editing covered files
  - Read module README.md before modifying files
  - Follow @Multibinds + @IntoSet pattern for playback sources
  - Use NxWorkRepository for UI (not Obx* repositories)
  - Enforce SSOT: ONE implementation per purpose (see ssot-enforcement.instructions.md)
  - When duplicates found: CONSOLIDATE into SSOT → DELETE all others
  - When unused imports/impls found: evaluate need → implement/TODO or DELETE
```

---

## Layer Boundaries

```
UI → Domain → Data → Pipeline → Transport → core:model
```

| Layer | ALLOWED Imports | FORBIDDEN Imports |
|-------|-----------------|-------------------|
| **UI/Feature** | Domain, core:model | Obx*, Pipeline DTOs, Transport |
| **Domain** | Data repos, core:model | Pipeline, Transport, TDLib |
| **Data** | core:model, RawMediaMetadata | Pipeline DTOs (TelegramMediaItem, XtreamVodItem) |
| **Pipeline** | Transport wrappers (TgMessage), core:model | Persistence, Data, Playback, UI |
| **Transport** | core:model, TDLib/HTTP | Pipeline, Data, UI, Persistence |
| **Playback** | Transport interfaces, core:model | Pipeline DTOs |
| **Player** | PlaybackContext, PlaybackSourceFactory | Pipeline, Data, Transport impl |

---

## Path Rules

```yaml
ALLOWED_PATHS:  # May create/edit
  - /app-v2/**
  - /core/**
  - /infra/**
  - /feature/**
  - /player/**
  - /playback/**
  - /pipeline/**
  - /docs/v2/**
  - /docs/meta/**
  - /scripts/**
  - Root: AGENTS.md, V2_PORTAL.md, CHANGELOG.md, ROADMAP.md

READ_ONLY_PATHS:  # Reference only
  - /legacy/**
  - /app/**
  - /docs/legacy/**
  - /docs/archive/**
```

---

## Naming Contract

| Where | Pattern | Example |
|-------|---------|---------|
| Pipeline capability | `*CapabilityProvider` | `TelegramCapabilityProvider` |
| App feature | `*FeatureProvider` | `HomeFeatureProvider` |
| Transport | `*Client`, `*Adapter` | `XtreamApiClient` |
| Data | `*Repository`, `Obx*Entity`, `NX_*` | `NxWorkRepository` |
| Pipeline mapper | `*Mapper`, `*Extensions` | `TelegramMediaMapper` |

**FORBIDDEN:** `*FeatureProvider` in pipeline/* | `feature/` package in pipeline/* | `com.chris.m3usuite` outside legacy/

---

## SSOT Entities

```yaml
UI_SSOT:  # ONLY these for UI consumption
  entity: NX_Work
  relations: NX_WorkSourceRef, NX_WorkVariant, NX_WorkRelation, NX_WorkUserState
  repository: NxWorkRepository
  FORBIDDEN_IN_UI: Obx*, ObxCanonicalMedia*, ObxXtream*, ObxTelegram*

TRANSPORT_SSOT:
  telegram: DefaultTelegramClient (ONE instance)
  interfaces: TelegramAuthClient, TelegramHistoryClient, TelegramFileClient
  FORBIDDEN: Multiple TdlClient instances, TdlibClientProvider

SYNC_SSOT:
  orchestrator: core/catalog-sync
  scheduler: CatalogSyncWorkScheduler
  FORBIDDEN: Direct pipeline.sync() from workers
```

---

## Contract References

| Area | Contract |
|------|----------|
| Any change | `/contracts/GLOSSARY_v2_naming_and_modules.md` |
| Pipeline | `/contracts/MEDIA_NORMALIZATION_CONTRACT.md` |
| Logging | `/contracts/LOGGING_CONTRACT_V2.md` |
| Player | `/contracts/INTERNAL_PLAYER_CONTRACT.md` |
| Telegram | `/contracts/TELEGRAM_PARSER_CONTRACT.md` |
| NX SSOT | `/contracts/NX_SSOT_CONTRACT.md` |
| Xtream Onboarding | `/contracts/XTREAM_ONBOARDING_CATEGORY_SELECTION_CONTRACT.md` |
| Xtream Sync | `/contracts/CATALOG_SYNC_WORKERS_CONTRACT_V2.md` |

---

## Scope Guard System

Before editing files in these modules, **READ the scope file first**:

| Module Path | Scope File |
|------------|------------|
| `core/catalog-sync/**` | `.scope/catalog-sync.scope.json` |
| `core/persistence/**` | `.scope/persistence.scope.json` |

Each scope file contains: `mandatoryReadBeforeEdit`, `globalInvariants`, `criticalFiles`, `forbiddenPatterns`

---

## Path-Scoped Instructions

Auto-applied rules in `.github/instructions/*.md`:

| Pattern | Instruction File |
|---------|------------------|
| `core/model/**` | `core-model.instructions.md` |
| `core/persistence/**` | `core-persistence.instructions.md` |
| `core/catalog-sync/**` | `core-catalog-sync.instructions.md` |
| `pipeline/**` | `pipeline.instructions.md` |
| `infra/transport-*/**` | `infra-transport.instructions.md` |
| `infra/data-*/**` | `infra-data.instructions.md` |
| `player/**` | `player.instructions.md` |
| `playback/**` | `playback.instructions.md` |
| `feature/**` | `feature-common.instructions.md` |

Full index: `.github/instructions/_index.instructions.md`

### SSOT Enforcement (Global)

> **Full details:** `.github/instructions/ssot-enforcement.instructions.md`

**Core Rule:** For every unique purpose → exactly ONE implementation. No exceptions.

```yaml
SSOT_DECISION_TREE:
  duplicate_found:
    - Superior SSOT exists?  → DELETE duplicate (don't "align" it)
    - No clear SSOT?         → CONSOLIDATE best parts → CREATE one SSOT → DELETE rest
    - Duplicate is fallback?  → DELETE (fallbacks produce incompatible output)
    - Too large for scope?    → TODO + ROADMAP.md (NEVER skip silently)
```

**DEV PHASE — No Migration, No Backward Compatibility:**
```yaml
DEV_PHASE_RULE:  # Until first production release
  - Fix the writer to produce correct data
  - DELETE any fallback/legacy reader for old format
  - DO NOT write migration code or "if old format" fallbacks
  - Next sync writes fresh, correct data — done
  - Reason: No production data exists. Every test = fresh sync.
```

**SSOT Registry — Use these, never create alternatives:**

| Purpose | SSOT Location | Key API |
|---------|---------------|---------|
| Resolution → label | `core/model/util/ResolutionLabel.kt` | `fromHeight()`, `badgeLabel()` |
| File size → string | `core/model/util/FileSizeFormatter.kt` | `format()` |
| MIME → container | `core/model/util/ContainerGuess.kt` | `fromMimeType()` |
| Source priority | `core/model/util/SourcePriority.kt` | `basePriority()`, `totalPriority()` |
| Playback hints codec | `infra/data-nx/mapper/base/PlaybackHintsDecoder.kt` | `decodeFromVariant()`, `encodeToJson()` |
| Hint key constants | `core/model/PlaybackHintKeys.kt` | `Xtream.*`, `Telegram.*` |
| Source labels | `infra/data-nx/mapper/TypeMappers.kt` | `SourceLabelBuilder` |
| Quality tags | `core/model/MediaVariant.kt` | `QualityTags.fromResolutionHeight()` |
| Slug generation | `core/model/util/SlugGenerator.kt` | `toSlug()` |

**SSOT Placement by Layer:**

| Purpose Type | Correct Layer |
|-------------|---------------|
| Pure data mapping | `core/model/util/` |
| Entity logic | `core/model/` or `core/persistence/` |
| Transport encoding | `infra/transport-*/` |
| Cross-source mapping | `infra/data-*/mapper/` |
| Pipeline extraction | `pipeline/*/` |
| UI presentation | `feature/*/ui/` or `core/ui-*/` |
| Sync orchestration | `core/catalog-sync/` |

---

## Quick Checklists

### Pre-Change (MANDATORY)

- [ ] Read GLOSSARY contract
- [ ] Check if file has scope in `.scope/`
- [ ] Read module README.md
- [ ] Verify change is under allowed paths
- [ ] No forbidden imports in plan
- [ ] Search for existing SSOT before implementing any logic

### Post-Change (MANDATORY)

- [ ] No edits in /legacy/** or /app/**
- [ ] No com.chris.m3usuite outside legacy/
- [ ] Code compiles
- [ ] Naming follows Glossary patterns
- [ ] No duplicate implementations introduced (check SSOT Registry)
- [ ] No legacy/migration/fallback code added (DEV PHASE)

---

## Detailed Rules (External Files)

| Topic | Location |
|-------|----------|
| Full checklists | `.scope/agent-checklists.rules.json` |
| Player migration phases | `ROADMAP.md` |
| Pipeline migration | `legacy/gold/EXTRACTION_SUMMARY.md` |
| MCP configuration | `.vscode/mcp.json`, `.devcontainer/devcontainer.json` |

---

> **Canonical Source:** `docs/meta/AGENT_RULES_CANONICAL.md`  
> **Sync:** `scripts/sync-agent-rules.sh`  
> **Full Archive:** `.deprecated/docs-archive-2026-02-15/AGENT_RULES_CANONICAL_FULL_BACKUP.md`  
> **Scope Files:** `.scope/layer-boundaries.rules.json`, `.scope/naming-rules.yaml`, `.scope/agent-checklists.rules.json`
