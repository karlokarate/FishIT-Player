# Gold Nuggets Extraction Summary

**Date:** 2025-12-08  
**Task:** Extract proven patterns from v1 codebase for v2 porting guidance

---

## Executive Summary

This document summarizes the gold nuggets extracted from the v1 codebase (`/legacy/v1-app/`) and documented for v2 implementation. All extracted patterns are now documented in `/legacy/gold/` with detailed porting guidance.

### What are "Gold Nuggets"?

Gold nuggets are proven, production-tested patterns from v1 that:
- Solve real problems elegantly
- Handle edge cases discovered through production use
- Should be preserved (adapted, not copied) in v2
- Represent architectural wisdom worth carrying forward

---

## Extracted Gold Nuggets

### 1. Telegram Pipeline (`/legacy/gold/telegram-pipeline/`)

**Document:** `GOLD_TELEGRAM_CORE.md` (341 lines)

**Key Patterns Extracted:**
- ✅ **Unified Telegram Engine** - Single TdlClient instance pattern
- ✅ **Auth State Machine** - Clear sealed class hierarchy for auth flows
- ✅ **Zero-Copy Streaming** - Delegate to FileDataSource for playback
- ✅ **RemoteId-First URLs** - Cross-session stable media identification
- ✅ **Chat Browsing** - Cursor-based pagination for TDLib
- ✅ **Lazy Thumbnails** - On-demand thumbnail loading
- ✅ **Priority Downloads** - TDLib priority levels (32=critical, 16=high, 8=normal, 1=low)
- ✅ **MP4 Header Validation** - Structure-based validation before playback

**v2 Target Modules:**
- `pipeline/telegram/tdlib/` - TDLib client abstraction
- `player/internal/source/telegram/` - Streaming DataSource
- `pipeline/telegram/repository/` - Content repository

**Status:** Phase 2 in progress (see TELEGRAM_TDLIB_V2_INTEGRATION.md)

---

### 2. Xtream Pipeline (`/legacy/gold/xtream-pipeline/`)

**Document:** `GOLD_XTREAM_CLIENT.md` (403 lines)

**Key Patterns Extracted:**
- ✅ **Per-Host Rate Limiting** - 120ms minimum interval with Mutex
- ✅ **Dual-TTL Cache** - 60s for catalog, 15s for EPG
- ✅ **VOD Alias Rotation** - Try "vod", "movie", "movies" until one works
- ✅ **Multi-Port Discovery** - Parallel probing of common ports (80, 8080, 25461, etc.)
- ✅ **Capability Detection** - Probe for EPG, series, catchup, XMLTV support
- ✅ **Parallel EPG Prefetch** - Semaphore(4) for controlled concurrency
- ✅ **Category Fallback** - Try category_id=* first, fall back to category_id=0
- ✅ **Graceful Degradation** - Empty results instead of crashes

**v2 Target Modules:**
- `pipeline/xtream/client/` - API client with rate limiting and caching
- `pipeline/xtream/discovery/` - Multi-port and capability detection
- `pipeline/xtream/repository/` - Catalog and EPG repository

**Status:** Near future phase (see XTREAM_PIPELINE_V2_REUSE_ANALYSIS.md)

---

### 3. UI Focus & TV Navigation (`/legacy/gold/ui-patterns/`)

**Document:** `GOLD_FOCUS_KIT.md` (516 lines)

**Key Patterns Extracted:**
- ✅ **FocusKit Entry Point** - Single import surface for all focus APIs
- ✅ **Focus Zone System** - Named zones with automatic registration
- ✅ **Focus Group Pattern** - Scoped containers with initial focus
- ✅ **tvClickable Modifiers** - Combines focus, click, and visual feedback
- ✅ **Focus Indicators** - Custom glow effect with theme colors
- ✅ **Initial Focus Handling** - Automatic focus on screen entry
- ✅ **Focus Memory** - Remember position across navigation
- ✅ **DPAD Key Handling** - Custom key interception with long press
- ✅ **Row Navigation** - FocusRowEngine for horizontal navigation
- ✅ **TV Form Components** - DPAD-first Switch, Slider, TextField, Select

**v2 Target Modules:**
- `core/ui-focus/` (to be created) or `feature/*/ui/` - Focus management
- `core/ui-common/` - TV modifiers and utilities
- `core/ui-layout/` - FishRow and layout components (already exists)

**Status:** Priority for Phase 1 (essential for TV UX)

---

### 4. Logging & Telemetry (`/legacy/gold/logging-telemetry/`)

**Document:** `GOLD_LOGGING.md` (506 lines)

**Key Patterns Extracted:**
- ✅ **UnifiedLog Facade** - Single entry point for all logging
- ✅ **Ring Buffer** - 1000-entry circular buffer for in-memory logs
- ✅ **SourceCategory System** - Predefined log categories for filtering
- ✅ **Persistent Filters** - DataStore-backed filter settings
- ✅ **Optional File Export** - Session log file for bug reports
- ✅ **In-App Log Viewer** - Real-time log UI with DPAD navigation
- ✅ **Structured Events** - JSON-serializable diagnostics events
- ✅ **Async Processing** - Non-blocking event logging
- ✅ **Performance Monitor** - Automatic slow operation detection
- ✅ **Crashlytics Integration** - ERROR logs sent to Firebase

**v2 Target Modules:**
- `infra/logging/` - **Already exists!** Verify implementation
- `infra/telemetry/` - DiagnosticsLogger and PerformanceMonitor
- `feature/settings/ui/` - Log viewer screen

**Status:** Phase 0 - Verify existing infra/logging matches v1

---

## Porting Strategy

### Golden Rules

1. **Never Copy-Paste** - Always re-implement using v2 architecture
2. **Follow Contracts** - Respect MEDIA_NORMALIZATION_CONTRACT.md, LOGGING_CONTRACT_V2.md, etc.
3. **Update Imports** - `com.chris.m3usuite` → `com.fishit.player`
4. **Singleton → Interface** - Replace singletons with injectable interfaces
5. **Test Everything** - Write unit tests for ported patterns

### Phased Approach

**Phase 0: Validation** (NOW)
- [ ] Verify existing `infra/logging/UnifiedLog.kt` matches v1 API
- [ ] Check for missing features (ring buffer, filters, etc.)
- [ ] Document any gaps

**Phase 1: Core Focus** (HIGH PRIORITY)
- [ ] Port FocusKit entry point to `core/ui-focus/`
- [ ] Port tvClickable/tvFocusableItem modifiers
- [ ] Port focus zone system
- [ ] Add unit tests

**Phase 2: Telegram Streaming** (IN PROGRESS)
- [ ] Complete TelegramTdlibClient interface
- [ ] Port TelegramFileDataSource to player/internal
- [ ] Port RemoteId resolution logic
- [ ] Integrate with InternalPlaybackSourceResolver

**Phase 3: Xtream Client** (NEAR FUTURE)
- [ ] Port rate limiting and caching
- [ ] Port URL generation and alias rotation
- [ ] Port multi-port discovery
- [ ] Implement XtreamApiClient interface

**Phase 4: Structured Logging** (ONGOING)
- [ ] Port DiagnosticsLogger to infra/telemetry
- [ ] Port SourceCategory system
- [ ] Port log viewer UI to feature/settings
- [ ] Add performance monitoring

---

## Key Differences: v1 → v2

### Architecture Changes

| Aspect | v1 Approach | v2 Approach | Reason |
|--------|-------------|-------------|--------|
| **Singletons** | `object T_TelegramServiceClient` | `interface TelegramTdlibClient + impl` | Testability, DI |
| **Metadata** | Pipeline normalizes titles | Pipeline emits RawMediaMetadata only | MEDIA_NORMALIZATION_CONTRACT |
| **Logging** | `com.chris.m3usuite.core.logging` | `com.fishit.player.infra.logging` | Module boundaries |
| **UI Location** | Telegram UI in telegram package | UI in `feature/telegram-media` | Module boundaries |
| **ObjectBox** | Used everywhere | Prefer interfaces, abstract persistence | Flexibility |

### Package Migrations

```
v1: com.chris.m3usuite.telegram.core.*
v2: com.fishit.player.pipeline.telegram.tdlib.*

v1: com.chris.m3usuite.core.xtream.*
v2: com.fishit.player.pipeline.xtream.client.*

v1: com.chris.m3usuite.ui.focus.*
v2: com.fishit.player.core.ui.focus.*

v1: com.chris.m3usuite.core.logging.*
v2: com.fishit.player.infra.logging.*
```

---

## What NOT to Port

### Patterns to Discard

1. **Global Mutable Singletons** - Replace with DI
2. **v1 Navigation** - Use v2 navigation system
3. **Legacy Theme** - Use v2 Fish* theme
4. **v1 ObjectBox Entities** - Define new v2 entities
5. **Blocking I/O on Main Thread** - Use coroutines properly
6. **Mixed Concerns** - Respect v2 module boundaries

### Anti-Patterns Found

- ❌ Normalization logic in pipelines (violates contract)
- ❌ UI components in data modules (violates boundaries)
- ❌ Direct TDLib access outside core (violates abstraction)
- ❌ Hardcoded build flags (use feature system)

---

## Validation Checklist

### For Each Ported Pattern

- [ ] **Contract Compliance** - Follows all v2 contracts (MEDIA_NORMALIZATION_CONTRACT, LOGGING_CONTRACT_V2, etc.)
- [ ] **Module Boundaries** - Respects v2 module structure (pipeline/core/infra/feature separation)
- [ ] **Package Names** - Uses `com.fishit.player.*` namespace
- [ ] **No Legacy Refs** - Zero `com.chris.m3usuite` imports outside legacy/
- [ ] **Testable** - Has unit tests (or testability plan)
- [ ] **Documented** - Has kdoc explaining pattern origin and purpose
- [ ] **Detekt Clean** - Passes detekt rules (especially logging restrictions)
- [ ] **Build Verified** - Compiles and doesn't break existing code

---

## Success Metrics

### How to Know if Porting Was Successful

1. **Behavioral Parity** - v2 implementation handles same edge cases as v1
2. **Architecture Compliance** - Follows v2 contracts and boundaries
3. **Test Coverage** - Unit tests cover key scenarios
4. **Performance** - No regressions vs v1
5. **Maintainability** - Code is clearer and more modular than v1

---

## References

### v1 Source Locations
- **Telegram:** `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/telegram/`
- **Xtream:** `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/core/xtream/`
- **Focus:** `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/ui/focus/`
- **Logging:** `/legacy/v1-app/app/src/main/java/com/chris/m3usuite/core/logging/`

### v2 Documentation
- **Portal:** `/V2_PORTAL.md`
- **Agents:** `/AGENTS.md`
- **Telegram:** `/docs/v2/TELEGRAM_TDLIB_V2_INTEGRATION.md`
- **Xtream:** `/docs/v2/XTREAM_PIPELINE_V2_REUSE_ANALYSIS.md`
- **Logging:** `/docs/v2/LOGGING_CONTRACT_V2.md`
- **Normalization:** `/docs/v2/MEDIA_NORMALIZATION_CONTRACT.md`

### Gold Nugget Docs
- **Telegram:** `/legacy/gold/telegram-pipeline/GOLD_TELEGRAM_CORE.md`
- **Xtream:** `/legacy/gold/xtream-pipeline/GOLD_XTREAM_CLIENT.md`
- **Focus:** `/legacy/gold/ui-patterns/GOLD_FOCUS_KIT.md`
- **Logging:** `/legacy/gold/logging-telemetry/GOLD_LOGGING.md`

---

## Next Steps

1. **Review this summary** with the team
2. **Prioritize porting phases** based on v2 roadmap
3. **Assign ownership** for each category
4. **Create tracking issues** for each phase
5. **Start with Phase 0** (verify existing infra/logging)
6. **Move to Phase 1** (core focus for TV UX)

---

**Note:** This extraction represents ~4 weeks of v1 production battle-testing. The patterns documented here solve real problems discovered in the field. Port wisely, test thoroughly, and respect the lessons learned.
