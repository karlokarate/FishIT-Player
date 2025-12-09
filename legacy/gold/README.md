# Legacy Gold Nuggets

This folder contains curated "gold nuggets" from the v1 codebase – patterns, implementations, and architectural decisions that are still valuable for v2 development.

## Overview

**Status:** ✅ **COMPLETE** - Gold nuggets extracted and documented (2025-12-08)

See **`EXTRACTION_SUMMARY.md`** for complete extraction report and porting guidance.

## Structure

### 📱 **`telegram-pipeline/`** – Telegram/TDLib Integration
- **`GOLD_TELEGRAM_CORE.md`** (341 lines) - Unified engine, zero-copy streaming, RemoteId URLs, priority downloads
- **Key Patterns:** Auth state machine, chat browsing, lazy thumbnails, MP4 validation

### 📡 **`xtream-pipeline/`** – Xtream Codes API Integration  
- **`GOLD_XTREAM_CLIENT.md`** (403 lines) - Rate limiting, caching, URL generation, discovery
- **Key Patterns:** VOD alias rotation, multi-port discovery, EPG prefetch, graceful degradation

### 🎮 **`ui-patterns/`** – TV/Focus Handling & Navigation
- **`GOLD_FOCUS_KIT.md`** (516 lines) - FocusKit, focus zones, DPAD handling, row navigation
- **Key Patterns:** tvClickable modifiers, focus memory, long press, TV form components

### 📊 **`logging-telemetry/`** – Logging & Diagnostics
- **`GOLD_LOGGING.md`** (506 lines) - UnifiedLog, ring buffer, structured events, log viewer
- **Key Patterns:** Source categories, async processing, performance monitor, Crashlytics

## Usage

### For Developers

When implementing v2 features, **always** consult these gold nuggets:

1. **Read the relevant gold document** for your area (Telegram, Xtream, UI, Logging)
2. **Understand the patterns** - why they work, what problems they solve
3. **Adapt (don't copy)** - Re-implement using v2 architecture
4. **Follow v2 contracts** - MEDIA_NORMALIZATION_CONTRACT.md, LOGGING_CONTRACT_V2.md, etc.
5. **Test thoroughly** - Write unit tests for ported patterns

### For Agents

When asked to implement features related to:
- **Telegram** → Read `telegram-pipeline/GOLD_TELEGRAM_CORE.md`
- **Xtream** → Read `xtream-pipeline/GOLD_XTREAM_CLIENT.md`
- **TV/Focus** → Read `ui-patterns/GOLD_FOCUS_KIT.md`
- **Logging** → Read `logging-telemetry/GOLD_LOGGING.md`

## Key Statistics

| Category | Source Lines (v1) | Gold Doc Lines | Patterns Extracted |
|----------|-------------------|----------------|-------------------|
| Telegram | ~3,800 | 341 | 8 major patterns |
| Xtream | ~5,400 | 403 | 8 major patterns |
| UI/Focus | ~1,650 | 516 | 10 major patterns |
| Logging | ~1,600 | 506 | 10 major patterns |
| **TOTAL** | **~12,450** | **1,766** | **36 patterns** |

## Porting Status

| Category | v2 Status | Priority | Phase |
|----------|-----------|----------|-------|
| **Logging** | ✅ Partial (infra/logging exists) | P0 | Phase 0 (verify) |
| **Focus/TV** | ❌ Not started | P0 | Phase 1 (high priority) |
| **Telegram** | 🔄 In progress | P1 | Phase 2 (current) |
| **Xtream** | ❌ Not started | P2 | Phase 3 (near future) |

## Golden Rules

1. **Never Copy-Paste** - Always re-implement using v2 architecture
2. **Follow Contracts** - Respect v2 contracts (normalization, logging, module boundaries)
3. **Update Imports** - `com.chris.m3usuite.*` → `com.fishit.player.*`
4. **Singleton → Interface** - Replace singletons with injectable interfaces
5. **Test Everything** - Write unit tests for all ported patterns

## References

- **Extraction Summary:** `EXTRACTION_SUMMARY.md` (this folder)
- **v1 Source:** `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/`
- **v2 Portal:** `/V2_PORTAL.md`
- **v2 Agents:** `/AGENTS.md`
- **v2 Docs:** `/docs/v2/`

---

> **Note:** These are reference materials representing 4 weeks of v1 production battle-testing.
> The patterns here solve real problems discovered in the field.
> **Port wisely, test thoroughly, respect the lessons learned.**
