# v2 Contracts – Binding Specifications

**Status:** Authoritative  
**Last Updated:** 2025-12-11

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
| [MEDIA_NORMALIZATION_CONTRACT.md](../docs/v2/MEDIA_NORMALIZATION_CONTRACT.md) | 1.0 | Pipelines, Normalizer | Rules for `RawMediaMetadata` → `NormalizedMediaMetadata` (canonical location: docs/v2) |
| [LOGGING_CONTRACT_V2.md](LOGGING_CONTRACT_V2.md) | 1.1 | All Modules | Unified logging rules, lambda-based lazy logging, allowed/forbidden APIs |

### Player Contracts

| Contract | Phase | Scope | Description |
|----------|-------|-------|-------------|
| [INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md](INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md) | All | Resume, Kids Mode | Resume behavior rules, kids/screen-time enforcement |
| [INTERNAL_PLAYER_BEHAVIOR_CONTRACT_FULL.md](INTERNAL_PLAYER_BEHAVIOR_CONTRACT_FULL.md) | All | Reference | Extended behavior contract (placeholder) |
| [INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md](INTERNAL_PLAYER_SUBTITLE_CC_CONTRACT_PHASE4.md) | 4 | Subtitles | Subtitle selection and styling contract |
| [INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md](INTERNAL_PLAYER_PLAYER_SURFACE_CONTRACT_PHASE5.md) | 5 | Player Surface | PlayerView wrapper and surface management |
| [INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md](INTERNAL_PLAYER_TV_INPUT_CONTRACT_PHASE6.md) | 6 | TV Input | DPAD/remote control handling for TV |
| [INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md](INTERNAL_PLAYER_PLAYBACK_SESSION_CONTRACT_PHASE7.md) | 7 | Session | Playback session lifecycle management |
| [INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md](INTERNAL_PLAYER_PHASE8_PERFORMANCE_LIFECYCLE_CONTRACT.md) | 8 | Performance | Player performance and lifecycle optimization |

### Pipeline Contracts

| Contract | Status | Scope | Description |
|----------|--------|-------|-------------|
| [TELEGRAM_PARSER_CONTRACT.md](TELEGRAM_PARSER_CONTRACT.md) | Draft | Telegram Pipeline | Telegram message parsing and domain mapping |
| [TELEGRAM_ID_ARCHITECTURE_CONTRACT.md](TELEGRAM_ID_ARCHITECTURE_CONTRACT.md) | **Binding** | Telegram IDs | remoteId-first design for TDLib file references |

---

## Canonical Contract Locations

- Media Normalization Contract: docs/v2/MEDIA_NORMALIZATION_CONTRACT.md (SOLE AUTHORITY)
- Files in /contracts may only forward or index, never define normative rules.

---

## Contract Compliance Rules

### 1. Reading Requirements

Before modifying code in any of these areas, agents **MUST** read the relevant contracts:

| Area | Required Contracts |
|------|-------------------|
| Naming / New Classes | `GLOSSARY_v2_naming_and_modules.md` |
| Pipelines | `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md`, `GLOSSARY` |
| Logging | `LOGGING_CONTRACT_V2.md` |
| Player | All `INTERNAL_PLAYER_*` contracts |
| Telegram | `TELEGRAM_PARSER_CONTRACT.md`, `TELEGRAM_ID_ARCHITECTURE_CONTRACT.md`, `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md` |
| Telegram IDs / Imaging | `TELEGRAM_ID_ARCHITECTURE_CONTRACT.md` |

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
- [docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md](/docs/v2/internal-player/PLAYER_MIGRATION_STATUS.md) – Player migration progress
