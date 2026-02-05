# Instruction Files Index

**Version:** 1.2  
**Last Updated:** 2026-02-04  
**Status:** SSOT for path-scoped instruction files

> **Purpose:** Central registry of all instruction files, their scope, dependencies, and versioning.
> This file is the **single source of truth** for instruction file metadata and cross-references.

---

## 📋 Instruction Files Overview

### Global (All Files)

| File | Applies To | Status | Version | Dependencies |
|------|------------|--------|---------|--------------|
| `scope-guard.instructions.md` | `**` (all files) | ✅ Active | 1.0 | `.scope/*.scope.json` files |

### Core Layer

| File | Applies To | Status | Version | Dependencies |
|------|------------|--------|---------|--------------|
| `core-model.instructions.md` | `core/model/**` | ✅ Active | 1.0 | - (foundational) |
| `core-player-model.instructions.md` | `core/player-model/**` | ✅ Active | 1.0 | core-model |
| `core-persistence.instructions.md` | `core/persistence/**` | ✅ Active | 1.0 | core-model |
| `core-normalizer.instructions.md` | `core/metadata-normalizer/**` | ✅ Active | 1.0 | core-model |
| `core-imaging.instructions.md` | `core/ui-imaging/**` | ✅ Active | 1.0 | core-model, infra-transport-telegram (interface only) |
| `core-ui.instructions.md` | `core/ui-layout/**`, `core/ui-theme/**` | ✅ Active | 1.0 | core-model |
| `core-catalog-sync.instructions.md` | `core/catalog-sync/**` | ✅ Active | 1.1 | core-model, pipeline, infra-data |
| `core-domain.instructions.md` | `core/*-domain/**` | ✅ Active | 1.0 | core-model, playback-domain |

### Infrastructure Layer

| File | Applies To | Status | Version | Dependencies |
|------|------------|--------|---------|--------------|
| `infra-logging.instructions.md` | `infra/logging/**` | ✅ Active | 1.0 | - (foundational) |
| `infra-work.instructions.md` | `infra/work/**` | ✅ Active | 1.0 | core-model |
| `infra-transport.instructions.md` | `infra/transport-*/**` | ✅ Active | 1.1 | infra-logging |
| `infra-transport-telegram.instructions.md` | `infra/transport-telegram/**` | ✅ Active | 1.0 | infra-transport, infra-logging |
| `infra-transport-xtream.instructions.md` | `infra/transport-xtream/**` | ✅ Active | 1.0 | infra-transport, infra-logging |
| `infra-data.instructions.md` | `infra/data-*/**` (incl. data-nx) | ✅ Active | 1.2 | core-model, core-persistence, infra-logging, NX_SSOT_CONTRACT |

### Pipeline Layer

| File | Applies To | Status | Version | Dependencies |
|------|------------|--------|---------|--------------|
| `pipeline.instructions.md` | `pipeline/*/**` | ✅ Active | 1.1 | core-model, infra-transport, infra-logging |

### Playback Layer

| File | Applies To | Status | Version | Dependencies |
|------|------------|--------|---------|--------------|
| `playback.instructions.md` | `playback/**` | ✅ Active | 1.1 | core-player-model, infra-transport, infra-logging |

### Player Layer

| File | Applies To | Status | Version | Dependencies |
|------|------------|--------|---------|--------------|
| `player.instructions.md` | `player/**` | ✅ Active | 1.0 | core-player-model, playback-domain |

### Feature Layer

| File | Applies To | Status | Version | Dependencies |
|------|------------|--------|---------|--------------|
| `feature-common.instructions.md` | `feature/**` | ✅ Active | 1.0 | core-domain, core-model |
| `feature-detail.instructions.md` | `feature/detail/**` | ✅ Active | 1.0 | feature-common, core-domain |
| `feature-settings.instructions.md` | `feature/settings/**` | ✅ Active | 1.0 | feature-common, core-catalog-sync |

### Application Layer

| File | Applies To | Status | Version | Dependencies |
|------|------------|--------|---------|--------------|
| `app-work.instructions.md` | `app-v2/src/main/java/*/work/**` | ✅ Active | 1.1 | core-catalog-sync, infra-work, infra-logging |
| `catalog-sync.instructions.md` | `**/catalog-sync/**`, `**/work/**CatalogSync*`, `**/bootstrap/*Bootstrap*` | ✅ Active | 1.0 | core-catalog-sync, infra-work |

**Total Files:** 23  
**Last Audit:** 2026-02-04

---

## 🔗 Dependency Graph

```
                    ┌─────────────────┐
                    │  core-model     │  (foundational)
                    └────────┬────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
        ┌───────────▼─────────┐  ┌───▼──────────────┐
        │ core-player-model   │  │ infra-logging    │  (foundational)
        └──────────┬──────────┘  └──────────────────┘
                   │
    ┌──────────────┼──────────────┬─────────────────┐
    │              │              │                 │
┌───▼──────┐  ┌───▼─────────┐  ┌─▼─────────┐  ┌───▼────────────┐
│ playback │  │ infra-data  │  │ pipeline  │  │ infra-transport│
│ -domain  │  └──────┬──────┘  └─────┬─────┘  └────────┬───────┘
└────┬─────┘         │               │                 │
     │               │               │                 │
     │         ┌─────▼───────────────▼─────────────────▼─────┐
     │         │         core-catalog-sync                    │
     │         └──────────────────────┬──────────────────────┘
     │                                │
     │                    ┌───────────▼────────────┐
     │                    │    app-v2/work         │
     │                    └────────────────────────┘
     │
┌────▼──────────┐
│  player/**    │
└───────────────┘
```

---

## 📚 Binding Contracts Reference

All instruction files MUST comply with these authoritative contracts:

| Contract | Location | Scope |
|----------|----------|-------|
| **AGENTS.md** | `/AGENTS.md` | PRIMARY AUTHORITY - All architecture rules |
| **SCOPE_GUARDS** | `/.scope/*.scope.json` | Bounded contexts, file inventories, invariants |
| **GLOSSARY** | `/contracts/GLOSSARY_v2_naming_and_modules.md` | Naming conventions, module taxonomy |
| **NX_SSOT** | `/contracts/NX_SSOT_CONTRACT.md` | NX entity schema, invariants (INV-01 to INV-13) |
| **LOGGING** | `/contracts/LOGGING_CONTRACT_V2.md` | UnifiedLog usage (v1.1) |
| **NORMALIZATION** | `/contracts/MEDIA_NORMALIZATION_CONTRACT.md` | RawMediaMetadata, pipeline rules (AUTHORITATIVE) |
| **TMDB_ENRICHMENT** | `/contracts/TMDB_ENRICHMENT_CONTRACT.md` | TMDB enrichment, canonical identity (AUTHORITATIVE) |
| **STARTUP_TRIGGER** | `/docs/v2/STARTUP_TRIGGER_CONTRACT.md` | Smart empty states |
| **PLAYER** | `/contracts/INTERNAL_PLAYER_*` | Player behavior, playback contracts |
| **TELEGRAM** | `/contracts/TELEGRAM_*` | Telegram-specific contracts |
| **XTREAM** | `/contracts/XTREAM_SCAN_PREMIUM_CONTRACT_V1.md` | Xtream Premium Contract |
| **WORKERS** | `/contracts/CATALOG_SYNC_WORKERS_CONTRACT_V2.md` | Worker architecture (W-1 to W-22) |

**Note on Contract Locations:**
- **All binding contracts are in `/contracts/`**: GLOSSARY, LOGGING, NORMALIZATION, TMDB_ENRICHMENT, INTERNAL_PLAYER_*, TELEGRAM_*, XTREAM_*, WORKERS, NX_SSOT
- **Only STARTUP_TRIGGER remains in `/docs/v2/`** (implementation guide, not a binding contract)
- When referencing contracts in instruction files, use `/contracts/` for all binding contracts

---

## 🔄 Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.2 | 2026-02-04 | Added version headers to all 19 remaining instruction files (100% coverage achieved) |
| 1.1 | 2026-02-02 | Added `catalog-sync.instructions.md` (sync triggers, optimistic activation, timeline) |
| 1.2 | 2026-01-23 | Added infra/data-nx scope, NX Schema Consistency Tests mandate (MANDATORY), NX_SSOT_CONTRACT reference |
| 1.1 | 2026-01-07 | PLATIN Audit fixes: Dot-space typos, broken refs, extractSeasonEpisode ownership, contract path clarification, imaging exception, TAG length guidance |
| 1.0 | 2026-01-07 | Initial index creation |

---

## 🏗️ Module Naming Conventions

### Core Module Naming (`:core:*`)

Modules in the `core/` directory follow the **`:core:<name>`** naming convention in Gradle:

| Directory Path | Gradle Module Name | Purpose |
|----------------|-------------------|---------|
| `core/model/` | `:core:model` | Foundational data classes |
| `core/player-model/` | `:core:player-model` | Player primitives |
| `core/persistence/` | `:core:persistence` | ObjectBox entities |
| `core/feature-api/` | `:core:feature-api` | Feature system contracts |
| `core/catalog-sync/` | `:core:catalog-sync` | Sync orchestration contracts |
| `core/source-activation-api/` | `:core:source-activation-api` | Source state contracts |
| `core/metadata-normalizer/` | `:core:metadata-normalizer` | Normalization logic |
| `core/*-domain/` | `:core:<name>-domain` | Domain use cases |

**Why `:core:`?**
- Indicates shared/foundational module
- Low in dependency hierarchy
- No source-specific code
- Pure Kotlin/Android (no external APIs)

### Infrastructure Module Naming (`:infra:*`)

| Directory Path | Gradle Module Name | Purpose |
|----------------|-------------------|---------|
| `infra/logging/` | `:infra:logging` | UnifiedLog system |
| `infra/work/` | `:infra:work` | WorkManager infrastructure |
| `infra/transport-telegram/` | `:infra:transport-telegram` | TDLib integration |
| `infra/transport-xtream/` | `:infra:transport-xtream` | Xtream API client |
| `infra/data-telegram/` | `:infra:data-telegram` | Telegram repositories |
| `infra/data-xtream/` | `:infra:data-xtream` | Xtream repositories |

### Other Module Naming

| Directory Path | Gradle Module Name |
|----------------|-------------------|
| `pipeline/telegram/` | `:pipeline:telegram` |
| `playback/telegram/` | `:playback:telegram` |
| `player/internal/` | `:player:internal` |
| `feature/home/` | `:feature:home` |
| `app-v2/` | `:app-v2` |

**Naming Rules:**
1. Top-level directory = module group prefix
2. Subdirectory = module name
3. Use hyphen for multi-word names: `player-model`, `catalog-sync`
4. Domain modules end with `-domain`: `home-domain`, `library-domain`
5. API-only modules end with `-api`: `feature-api`, `source-activation-api`

---

## 📝 Usage Guidelines

### For Copilot Agents

1. **Read this index first** to understand instruction file scope and dependencies
2. **Check version** before applying instructions (some may be outdated)
3. **Follow dependency chain** when working across modules
4. **Cross-reference contracts** for authoritative rules

### For Maintainers

1. **Update version** when making substantial changes to any instruction file
2. **Update this index** when adding/removing instruction files
3. **Document breaking changes** in version history
4. **Verify dependencies** when refactoring module structure

### Path-Scoped Auto-Application

VS Code Copilot **automatically applies** these instructions when editing files in matching paths.
This index helps understand which instructions are active for a given file path.

**Example:**
- Editing `core/model/RawMediaMetadata.kt` → `core-model.instructions.md` auto-applied
- Editing `playback/telegram/TelegramPlaybackSourceFactory.kt` → `playback.instructions.md` auto-applied
- Editing `app-v2/work/XtreamSyncWorker.kt` → `app-work.instructions.md` auto-applied

---

## 🚨 Missing Instructions (Documented Gaps)

The following modules do NOT have dedicated instruction files:

| Module | Status | Reason |
|--------|--------|--------|
| `infra/transport-io/**` | 📋 Planned | Reserved for local file transport (low priority) |
| `playback/local/**` | 📋 Planned | Reserved for local file playback (future) |
| `playback/audiobook/**` | 📋 Planned | Reserved for audiobook playback (future) |
| `pipeline/audiobook/**` | 📋 Planned | Reserved for audiobook pipeline (future) |
| `pipeline/io/**` | 📋 Planned | Reserved for local file pipeline (future) |

**Guidance:** For modules without dedicated instructions, follow:
1. Closest analogous instruction file (e.g., `infra-transport.instructions.md` for `infra-transport-io`)
2. General layer rules from `AGENTS.md`
3. Binding contracts relevant to the layer

---

## 🔍 Quick Reference: Which File to Read?

| Working On | Primary Instructions | Also Read |
|------------|---------------------|-----------|
| Core models | `core-model.instructions.md` | `GLOSSARY` |
| Pipeline modules | `pipeline.instructions.md` | `MEDIA_NORMALIZATION_CONTRACT.md` |
| Transport layer | `infra-transport-*.instructions.md` | `LOGGING_CONTRACT_V2.md` |
| Data repositories | `infra-data.instructions.md` | `core-persistence.instructions.md` |
| Player internals | `player.instructions.md` | `INTERNAL_PLAYER_*` contracts |
| Playback sources | `playback.instructions.md` | `player.instructions.md` |
| Feature screens | `feature-*.instructions.md` | `core-domain.instructions.md` |
| WorkManager workers | `app-work.instructions.md` | `CATALOG_SYNC_WORKERS_CONTRACT_V2.md` |
| Logging code | `infra-logging.instructions.md` | `LOGGING_CONTRACT_V2.md` |

---

## 📧 Maintenance

**Last Reviewed:** 2026-01-07  
**Next Review:** Q1 2026 or after major architecture changes  
**Owner:** Architecture team

**Update Triggers:**
- New module created
- Instruction file added/removed
- Breaking changes to contracts
- Major version increments
