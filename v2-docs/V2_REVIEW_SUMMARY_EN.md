# v2 Rebuild Review Summary (English)

> **Date:** 2025-12-07  
> **Full Review:** See `V2_REBUILD_REVIEW_2025.md` (German, 28.7 KB)  
> **Status:** 🔴 ~17% complete, but architecture is excellent

---

## TL;DR

**Architecture:** ✅ Excellent (clean layers, clear contracts, great docs)  
**Code Implementation:** 🔴 Critical (~5k of ~30k LOC needed)  
**Main Issue:** "Skeleton First" instead of "Port First" strategy  
**Solution:** Aggressive porting from v1 per `V1_VS_V2_ANALYSIS_REPORT.md`

---

## Current Status by Module

| Module | Status | Progress | LOC | Critical Issues |
|--------|--------|----------|-----|-----------------|
| **app-v2** | 🟡 Partial | 10% | 6 files | No AppShell, no FeatureRegistry |
| **core:model** | 🟢 Good | 80% | 48 files | Missing Profile/Entitlement models |
| **core:metadata-normalizer** | 🟡 Skeleton | 30% | Interface only | No scene parser, no TMDB integration |
| **core:persistence** | 🔴 Critical | 20% | Interfaces only | **No ObjectBox entities from v1** |
| **pipeline:telegram** | 🟡 Partial | 60% | 20 files | **Missing TelegramFileDataSource (413 LOC)** |
| **pipeline:xtream** | 🟡 Partial | 50% | 21 files | **Missing XtreamObxRepository (2829 LOC)** |
| **pipeline:io** | 🟢 Skeleton | 100% | 14 files | Complete skeleton with tests |
| **pipeline:audiobook** | 🔴 Empty | 5% | 1 file | Only package-info |
| **playback:domain** | 🔴 Empty | 0% | 0 files | **BLOCKING: No interfaces at all** |
| **player:internal** | 🔴 Empty | 0% | 0 files | **CRITICAL: v1 has 5000 LOC SIP ready** |
| **feature:\*** (6 modules) | 🔴 Empty | 0% | 0 files | **All empty** |
| **infra:logging** | 🔴 Empty | 0% | 0 files | **v1 has UnifiedLog (578 LOC) ready** |
| **infra:tooling** | 🔴 Empty | 0% | 0 files | Empty |

---

## What Went Wrong

### 1. 🔥 Main Problem: "Skeleton First" Instead of "Port First"

**What Happened:**
1. Build module structure ✅
2. Define interfaces ✅
3. Create dummy implementations ✅
4. Port v1 code ❌ **STUCK HERE**

**Result:** After ~4 weeks, we have:
- ✅ Clean architecture
- ✅ Compiling modules
- ❌ **BUT:** No functional app because critical v1 components missing

**Better Approach:**
Follow `V1_VS_V2_ANALYSIS_REPORT.md` "Port First" strategy:
1. Port Tier 1 systems immediately (~12k LOC)
2. Port Tier 2 systems with minor adapt
3. **Result:** Functional app after ~2 weeks instead of skeletons

---

### 2. ❌ Tier 1 Systems Ignored

`V1_VS_V2_ANALYSIS_REPORT.md` lists **6 production-ready Tier 1 systems**:

| System | v1 Quality | v1 LOC | v2 Status | Impact |
|--------|------------|--------|-----------|--------|
| **SIP Player (Phase 1-8)** | ⭐⭐⭐⭐⭐ | 5000 | 🔴 NOT PORTED | No playback possible |
| **UnifiedLog** | ⭐⭐⭐⭐⭐ | 578 | 🔴 NOT PORTED | No diagnostics |
| **FocusKit** | ⭐⭐⭐⭐⭐ | 1353 | 🔴 NOT PORTED | No TV navigation |
| **Fish\* Layout System** | ⭐⭐⭐⭐⭐ | 2000 | 🔴 NOT PORTED | No UI components |
| **Xtream Pipeline** | ⭐⭐⭐⭐⭐ | 3000 | 🟡 PARTIAL | Content queries broken |
| **AppImageLoader** | ⭐⭐⭐⭐⭐ | 153 | 🔴 NOT PORTED | No image loading |

**Status:** 🔴 **0 of 6 Tier 1 systems ported**

**Impact:** v2 is unusable despite v1 having production-ready code

---

### 3. ❌ Phase 1 Skipped

`IMPLEMENTATION_PHASES_V2.md` Phase 1 expects:
- `playback:domain` with interfaces + default implementations
- `player:internal` with SIP ported from v1
- `feature:home` with DebugPlaybackScreen

**Status:** 🔴 **Phase 1 completely skipped**
- playback:domain: EMPTY
- player:internal: EMPTY
- feature:home: EMPTY (only debug screen in app-v2)

**Impact:** All subsequent phases blocked (Phase 2 Telegram, Phase 3 Xtream need Phase 1)

---

### 4. ❌ Too Many Parallel Fronts

Instead of sequential phases, multiple fronts opened:
- Phase 0: Module Skeleton ✅
- Phase 2: Telegram Pipeline (Partial) 🟡
- Phase 3A: Metadata Normalizer (Skeleton) ✅
- Phase 3B: Xtream Pipeline (Partial) 🟡

**But:** Phase 1 (Player) was skipped!

**Problem:** No testable app between phases, no validation if architecture works

---

## Critical Blockers (Priority Order)

### P0 – ABSOLUTE BLOCKERS (v2 unusable without these)

| # | Blocker | Reason | Effort | v1 LOC |
|---|---------|--------|--------|--------|
| 1 | **SIP Player not ported** | No playback functionality | 2-3 days | 5000 |
| 2 | **playback:domain empty** | player:internal can't be built | 1 day | 500 |
| 3 | **UnifiedLog not ported** | No diagnostics/logging | 4 hours | 578 |
| 4 | **ObjectBox Entities missing** | No content persistence | 1 day | 1500 |
| 5 | **TelegramFileDataSource missing** | Telegram videos can't play | 1 day | 413 |
| 6 | **XtreamObxRepository missing** | Xtream content not queryable | 1 day | 2829 |

**Total P0:** ~7 days, ~10,820 LOC to port from v1

---

### P1 – CRITICAL (App works but rudimentary)

| # | Blocker | Reason | Effort | v1 LOC |
|---|---------|--------|--------|--------|
| 7 | **Feature Screens missing** | No navigation, only skeleton | 3 days | 5000 |
| 8 | **FocusKit not ported** | No TV navigation | 1 day | 1353 |
| 9 | **Fish\* Layout not ported** | No UI components | 1 day | 2000 |
| 10 | **AppShell incomplete** | No FeatureRegistry, no startup | 1 day | 500 |

**Total P1:** ~6 days, ~8,853 LOC

---

## Roadmap to Functional v2 Alpha

### Week 1: Player Foundation (P0 Blockers)

**Goal:** v2 can play Telegram + Xtream content

**Day 1-2: playback:domain + player:internal Base**
- [ ] playback:domain interfaces (ResumeManager, KidsPlaybackGate, etc.)
- [ ] playback:domain default implementations (no-op)
- [ ] Port: InternalPlayerState.kt
- [ ] Port: InternalPlayerSession.kt (1393 LOC)
- [ ] Port: InternalPlayerControls.kt, PlayerSurface.kt
- [ ] Build + Manual Test: HTTP stream playback ✅

**Day 3: Logging + Entities**
- [ ] Port: UnifiedLog.kt → infra:logging (578 LOC)
- [ ] Port: LogViewerScreen + ViewModel
- [ ] Port: ObxEntities.kt → core:persistence (10 entities)
- [ ] Port: ObxStore.kt → core:persistence

**Day 4-5: Pipeline Completion**
- [ ] Port: TelegramFileDataSource.kt (413 LOC)
- [ ] Port: T_TelegramFileDownloader.kt (1621 LOC)
- [ ] Port: XtreamObxRepository.kt (2829 LOC)
- [ ] Port: DelegatingDataSourceFactory.kt
- [ ] Build + Test: Telegram + Xtream playback ✅

**Week 1 Output:**
- ✅ v2 can play videos (Telegram + Xtream + HTTP)
- ✅ Logging works
- ✅ Content stored in ObjectBox
- ✅ ~10,000 LOC ported

---

### Week 2: UI Foundation (P1 Features)

**Goal:** v2 is usable with real navigation

**Day 6-7: FocusKit + Fish\* Layout**
- [ ] Port: FocusKit.kt → ui:focus (1353 LOC)
- [ ] Port: TvButtons.kt → ui:common (145 LOC)
- [ ] Port: Fish* Layout System (FishTheme, FishTile, FishRow, etc.)
- [ ] Build + Test: Sample tiles render on TV ✅

**Day 8-10: Feature Screens**
- [ ] feature:home → HomeScreen + ViewModel
- [ ] feature:library → LibraryScreen + ViewModel
- [ ] feature:live → LiveScreen + EPG UI
- [ ] feature:telegram-media → TelegramScreen + Chat Browser
- [ ] feature:settings → SettingsScreen + Profile UI
- [ ] app-v2: AppShell with FeatureRegistry
- [ ] app-v2: Startup Sequence (DeviceProfile, Profiles)

**Week 2 Output:**
- ✅ v2 has functional UI screens
- ✅ Navigation works
- ✅ TV focus works
- ✅ ~9,000 LOC ported

---

### Timeline Overview

| Week | Focus | Output | LOC |
|------|-------|--------|-----|
| **Week 1** | P0 Blockers | Playable v2 | +10,000 |
| **Week 2** | P1 Features | Usable v2 | +9,000 |
| **Week 3** | P2 Polish | Polished v2 | +3,000 |
| **Week 4** | Testing + Docs | Alpha Release | +1,000 |

**Total:** ~4 weeks, ~23,000 LOC (of ~30,000 needed)

---

## Recommendations

### 🔥 IMMEDIATE ACTIONS

1. **STOP Skeleton-Building**
   - No new modules
   - No new interfaces without implementation
   - Focus: **Porting**

2. **START Tier 1 Porting**
   - Day 1: SIP Player (player:internal)
   - Day 2: UnifiedLog (infra:logging)
   - Day 3: ObjectBox Entities (core:persistence)
   - Day 4-5: Pipeline Completion

3. **Strict Phase Order**
   - Complete Phase 1 before Phase 2
   - Manual Testing after each phase
   - No more parallel fronts

---

### 📋 PROCESS CHANGES

1. **Port-First Strategy**
   ```
   BEFORE:
   1. Define interface
   2. Create dummy impl
   3. Write tests
   4. Real impl later
   
   AFTER:
   1. Identify v1 code (V1_VS_V2_ANALYSIS_REPORT.md)
   2. Port directly to v2
   3. Migrate tests
   4. Build + Test
   ```

2. **Daily Smoke Tests**
   - Every day: Build + start app-v2
   - Every day: 1 Manual Test (e.g., "Play HTTP stream")
   - Fix blockers immediately, don't stack

3. **Phase Gates**
   - Start Phase N+1 only after:
     - ✅ Phase N compiles
     - ✅ Phase N passed Manual Test
     - ✅ Phase N PR merged

---

## Success Metrics

### Code Coverage

| Category | EOW1 Goal | EOW2 Goal | Alpha Goal |
|----------|-----------|-----------|------------|
| Player | 80% | 80% | 90% |
| Pipelines | 60% | 80% | 90% |
| UI Components | 0% | 80% | 90% |
| Feature Screens | 0% | 60% | 80% |
| **Total** | **40%** | **75%** | **90%** |

### Functional Tests

**End of Week 1:**
- [ ] ✅ app-v2 starts
- [ ] ✅ HTTP test stream plays
- [ ] ✅ Telegram video plays
- [ ] ✅ Xtream VOD plays
- [ ] ✅ Logs visible in LogViewer

**End of Week 2:**
- [ ] ✅ Navigation between screens
- [ ] ✅ TV focus works
- [ ] ✅ Home screen shows content
- [ ] ✅ Settings can create profiles
- [ ] ✅ Resume works

---

## Conclusion

### 🟢 Strengths

1. **Architecture Design:** Excellent (layer separation, dependencies, contracts)
2. **Documentation:** Outstanding (18 docs, clear, actionable)
3. **Module Structure:** Clean (17 modules, compile, correct versions)
4. **Pipeline Interfaces:** Good (Telegram + Xtream ~60-80% core)

### 🔴 Weaknesses

1. **Code Implementation:** Only ~17% (5k of 30k LOC)
2. **Tier 1 Porting:** 0% (none of 6 systems ported)
3. **Player:** 0% (player:internal empty, despite v1 having 5000 LOC)
4. **Feature Screens:** 0% (all 6 modules empty)
5. **Infrastructure:** 0% (logging, tooling empty)
6. **Testing:** No end-to-end tests, no manual tests

### 🎯 Critical Path

**Problem:** "Skeleton First" instead of "Port First"  
**Solution:** Aggressive porting per `V1_VS_V2_ANALYSIS_REPORT.md`

**Timeline:**
- Week 1: P0 Blockers (Player, Logging, Entities, DataSources)
- Week 2: P1 Features (UI, FocusKit, Fish*, Feature Screens)
- Week 3-4: Polish + Testing

**Result:** Functional v2 Alpha in ~4 weeks

---

## Next Steps (Actionable)

### This Week

1. **PR:** "Port SIP Player to player:internal"
   - Copy v1 `player/internal/` → v2 `player/internal/`
   - Adjust imports (com.chris → com.fishit)
   - Build + Test

2. **PR:** "Port UnifiedLog to infra:logging"
   - Copy v1 `core/logging/UnifiedLog.kt` → v2
   - Build + Test

3. **PR:** "Port ObjectBox Entities to core:persistence"
   - Copy v1 `data/obx/ObxEntities.kt` → v2
   - Copy v1 `data/obx/ObxStore.kt` → v2
   - Build + Test

### Next Week

4. **PR:** "Port Telegram DataSources"
5. **PR:** "Port Xtream ObxRepository"
6. **PR:** "Implement feature:home Screen"
7. **PR:** "Port FocusKit + Fish Layout"

---

**End of Review** – Date: 2025-12-07  
**Status:** 🔴 v2 is ~17% complete, but architecture is excellent  
**Recommendation:** Port-First strategy for next 4 weeks
