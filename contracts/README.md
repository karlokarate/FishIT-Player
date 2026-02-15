# v2 Contracts – Binding Specifications

**Status:** Authoritative  
**Last Updated:** 2026-02-04

> ⚠️ **AGENTS MUST READ ALL CONTRACTS** before making any code changes.
> 
> Per `AGENTS.md` Section 15: All documents in this folder are **binding contracts**.
> Agents must read and understand all contracts before modifying related code.

---

## Contract Inventory

### Core Contracts

| Contract | Version | Scope | Description |
|----------|---------|-------|-------------|
| [GLOSSARY_v2_naming_and_modules.md](GLOSSARY_v2_naming_and_modules.md) | 2.0 | **Global** | Authoritative vocabulary and naming conventions for all v2 code |
| [MEDIA_NORMALIZATION_CONTRACT.md](MEDIA_NORMALIZATION_CONTRACT.md) | 1.0 | Pipelines, Normalizer | Rules for `RawMediaMetadata` → `NormalizedMediaMetadata` |
| [TMDB_ENRICHMENT_CONTRACT.md](TMDB_ENRICHMENT_CONTRACT.md) | 1.0 | Normalizer, Enrichment | TMDB enrichment rules, canonical identity, imaging |
| [CATALOG_SYNC_WORKERS_CONTRACT_V2.md](CATALOG_SYNC_WORKERS_CONTRACT_V2.md) | 2.0 | WorkManager | Catalog sync worker architecture (W-1 to W-22) |
| [LOGGING_CONTRACT_V2.md](LOGGING_CONTRACT_V2.md) | 1.1 | All Modules | Unified logging rules, lambda-based lazy logging, allowed/forbidden APIs |
| [NX_SSOT_CONTRACT.md](NX_SSOT_CONTRACT.md) | 1.0 | **Persistence Layer** | OBX PLATIN NX_* entities SSOT - deterministic keys, ingest ledger, invariants |

### Player Contract (Consolidated)

| Contract | Scope | Description |
|----------|-------|-------------|
| [INTERNAL_PLAYER_CONTRACT.md](INTERNAL_PLAYER_CONTRACT.md) | All Player Phases | Consolidated: Resume/Kids, Subtitles, Surface, TV Input, Session, Performance |

### Pipeline Contracts

| Contract | Status | Scope | Description |
|----------|--------|-------|-------------|
| [TELEGRAM_PARSER_CONTRACT.md](TELEGRAM_PARSER_CONTRACT.md) | Draft | Telegram Pipeline | Telegram message parsing and domain mapping |
| [TELEGRAM_ID_ARCHITECTURE_CONTRACT.md](TELEGRAM_ID_ARCHITECTURE_CONTRACT.md) | **Binding** | Telegram IDs | remoteId-first design for TDLib file references |
| [TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md](TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md) | **Binding** | Telegram Pipeline | Structured bundle detection and lossless emission rules |
| [TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md](TELEGRAM_LEGACY_MODULE_MIGRATION_CONTRACT.md) | Completed | Telegram Migration | Legacy v1 → v2 migration rules (historical reference) |

### Xtream Contracts

| Contract | Status | Scope | Description |
|----------|--------|-------|-------------|
| [XTREAM_SCAN_PREMIUM_CONTRACT_V1.md](XTREAM_SCAN_PREMIUM_CONTRACT_V1.md) | **Binding** | Xtream Transport | Premium Contract - timeouts, headers, parallelism, rate limiting |

---

## Canonical Contract Locations

All binding contracts are now in `/contracts/`:

- `contracts/MEDIA_NORMALIZATION_CONTRACT.md` – SOLE AUTHORITY for pipeline normalization
- `contracts/TMDB_ENRICHMENT_CONTRACT.md` – SOLE AUTHORITY for TMDB enrichment
- `contracts/CATALOG_SYNC_WORKERS_CONTRACT_V2.md` – SOLE AUTHORITY for WorkManager sync

No forwarders or duplicate definitions exist elsewhere.

---

## Contract Compliance Rules

### 1. Reading Requirements

Before modifying code in any of these areas, agents **MUST** read the relevant contracts:

| Area | Required Contracts |
|------|-------------------|
| Naming / New Classes | `GLOSSARY_v2_naming_and_modules.md` |
| Pipelines | `MEDIA_NORMALIZATION_CONTRACT.md`, `GLOSSARY` |
| Logging | `LOGGING_CONTRACT_V2.md` |
| Player | `INTERNAL_PLAYER_CONTRACT.md` |
| Telegram | `TELEGRAM_PARSER_CONTRACT.md`, `TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`, `TELEGRAM_STRUCTURED_BUNDLES_CONTRACT.md`, `MEDIA_NORMALIZATION_CONTRACT.md` |
| Telegram IDs / Imaging | `TELEGRAM_ID_ARCHITECTURE_CONTRACT.md` |
| Xtream | `XTREAM_SCAN_PREMIUM_CONTRACT_V1.md` |
| Persistence / OBX | `NX_SSOT_CONTRACT.md` |
| WorkManager Sync | `CATALOG_SYNC_WORKERS_CONTRACT_V2.md` |
| TMDB Enrichment | `TMDB_ENRICHMENT_CONTRACT.md` |

### 2. Violation Handling

If a contract violation is detected:

1. **STOP** implementation immediately
2. **DOCUMENT** the conflict
3. **ASK** the user for resolution
4. **DO NOT** proceed with violating changes

### 3. Contract Updates

Contracts may only be updated:
- With explicit user approval
- With a clear changelog entry
- With version number increment

---

## Cross-References

Related documentation (not contracts, but important references):

- [AGENTS.md](/AGENTS.md) – Agent rules for v2 development
- [V2_PORTAL.md](/V2_PORTAL.md) – V2 architecture entry point
- [ROADMAP.md](/ROADMAP.md) – Project roadmap
