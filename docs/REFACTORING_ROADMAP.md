# Refactoring Roadmap (Post-Integration)

**Version:** 1.0  
**Last Updated:** 2026-02-04  
**Status:** Planning

## Overview

This document outlines the remaining high-complexity files that could benefit from similar handler/builder refactoring in future sprints. All files listed have been audited and prioritized by complexity, maintainability impact, and PLATIN quality standards.

---

## Already Completed ✅

### Sprint 1 & 2: Handler & Builder Creation
- ✅ **Xtream Handlers** (6 classes)
  - XtreamConnectionManager, XtreamCategoryFetcher, XtreamStreamFetcher
  - LiveStreamMapper, VodStreamMapper, SeriesStreamMapper
- ✅ **NX Builders** (3 classes)
  - WorkEntityBuilder, SourceRefBuilder, VariantBuilder
- ✅ **Tests:** 15+ unit tests created and passing
- ✅ **Documentation:** READMEs updated

### Sprint 3: Integration (Current PR #675)
- ⏳ **DefaultXtreamApiClient:** 2312 → ~800 lines (in progress)
- ⏳ **NxCatalogWriter:** 610 → ~300 lines (in progress)

**Total Impact So Far:**
- Lines reduced: ~1,822 (62%)
- Complexity reduced: CC 52/28 → CC ~8/6
- Test coverage: +15 tests

---

## Remaining High-Complexity Files (Audit Results)

### Priority 1: Critical Maintainability Issues

#### 1.1 Telegram Transport Layer

**File:** `infra/transport-telegram/src/main/java/.../DefaultTelegramClient.kt`  
**Current:** ~1,800 lines, CC ~45  
**Priority:** **HIGH** (similar to Xtream)  
**Estimated Effort:** 6-8 hours

**Issues:**
- Monolithic client handling auth, history, files, thumbnails
- Similar pattern to Xtream (should have handlers)
- High coupling, difficult to test in isolation

**Proposed Solution:**
Create 5 handlers:
```
TelegramAuthHandler        → login, logout, auth state
TelegramHistoryHandler     → chats, messages, pagination
TelegramFileHandler        → file downloads, progress
TelegramRemoteResolver     → remoteId resolution
TelegramThumbFetcher       → thumbnail loading (already interface)
```

**Benefits:**
- Reduction: ~1,800 → ~600 lines (67%)
- CC reduction: 45 → ~8
- Better testability
- Consistent with Xtream pattern

**Dependencies:**
- None (can be done immediately after PR #675)

---

#### 1.2 Catalog Sync Service

**File:** `core/catalog-sync/src/main/java/.../DefaultCatalogSyncService.kt`  
**Current:** ~950 lines, CC ~35  
**Priority:** **HIGH** (orchestration complexity)  
**Estimated Effort:** 5-7 hours

**Issues:**
- Orchestrates multiple pipelines (Telegram, Xtream, IO)
- Complex state management
- Performance-critical (batch processing)
- Difficult to test scenarios in isolation

**Proposed Solution:**
Create specialized synchronizers:
```
TelegramSynchronizer   → Telegram-specific sync logic
XtreamSynchronizer     → Xtream-specific sync logic
IoSynchronizer         → Local file sync logic
SyncOrchestrator       → Coordinates synchronizers
```

**Benefits:**
- Reduction: ~950 → ~400 lines (58%)
- CC reduction: 35 → ~10
- Easier to add new sources
- Better error handling per source

**Dependencies:**
- Should be done AFTER Telegram handlers (1.1)
- Depends on pipeline interfaces being stable

---

#### 1.3 NxCatalogRepository

**File:** `infra/data-nx/src/main/java/.../ObxNxCatalogRepository.kt`  
**Current:** ~720 lines, CC ~25  
**Priority:** **MEDIUM** (data layer cleanup)  
**Estimated Effort:** 4-5 hours

**Issues:**
- Complex ObjectBox queries
- Mixing query construction with business logic
- Hard to test specific query patterns

**Proposed Solution:**
Create query builders:
```
NxWorkQueryBuilder        → Work queries (by key, type, etc.)
NxSourceRefQueryBuilder   → SourceRef queries (by account, source)
NxVariantQueryBuilder     → Variant queries (by quality, language)
```

**Benefits:**
- Reduction: ~720 → ~400 lines (44%)
- CC reduction: 25 → ~8
- Reusable query logic
- Better type safety

**Dependencies:**
- Should be done AFTER NxCatalogWriter integration (Sprint 3)
- Requires stable NX schema (already done)

---

### Priority 2: Moderate Complexity

#### 2.1 Pipeline Adapters

**Files:**
- `pipeline/telegram/TelegramPipelineAdapter.kt` (~450 lines, CC ~18)
- `pipeline/xtream/XtreamPipelineAdapter.kt` (~380 lines, CC ~15)

**Priority:** **MEDIUM** (moderate complexity, lower impact)  
**Estimated Effort:** 3-4 hours each

**Issues:**
- Transform transport DTOs → RawMediaMetadata
- Some complex mapping logic
- Could benefit from specialized mappers

**Proposed Solution:**
Create domain mappers (similar to Xtream stream mappers):
```
TelegramMessageMapper     → TgMessage → RawMediaMetadata
TelegramBundleMapper      → Message bundles → grouped metadata
XtreamStreamMapper        → XtreamVodStream → RawMediaMetadata
XtreamSeriesMapper        → XtreamSeriesInfo → RawMediaMetadata
```

**Benefits:**
- Reduction: ~830 → ~400 lines total (52%)
- CC reduction: ~33 → ~12
- Consistent mapper pattern across codebase
- Easier to add new fields

**Dependencies:**
- Low priority (adapters work fine as-is)
- Could wait for future pipeline refactoring

---

#### 2.2 Feature ViewModels

**Files:**
- `feature/home/HomeViewModel.kt` (~420 lines, CC ~22)
- `feature/detail/UnifiedDetailViewModel.kt` (~380 lines, CC ~19)
- `feature/library/LibraryViewModel.kt` (~360 lines, CC ~17)

**Priority:** **MEDIUM** (UI layer, lower risk)  
**Estimated Effort:** 2-3 hours each

**Issues:**
- ViewModels doing too much (state + orchestration)
- Some business logic that should be in use cases
- State transformations could be extracted

**Proposed Solution:**
Create use cases and state machines:
```
// For HomeViewModel:
GetHomeContentUseCase      → Fetch home rows
GetContinueWatchingUseCase → Resume position logic
HomeStateMapper            → Domain → UI state

// For DetailViewModel:
GetMediaDetailUseCase      → Fetch detail data
PlayMediaUseCase           → Playback orchestration
DetailStateMapper          → Domain → UI state
```

**Benefits:**
- Reduction: ~1,160 → ~600 lines total (48%)
- CC reduction: ~58 → ~20
- Better testability (use cases are pure)
- Follows Clean Architecture

**Dependencies:**
- Low priority (UI layer is stable)
- Could be done incrementally per feature

---

### Priority 3: Optional Improvements

#### 3.1 Player Layer

**File:** `player/internal/InternalPlayerSession.kt`  
**Current:** ~650 lines, CC ~28  
**Priority:** **LOW** (working well, high risk)  
**Estimated Effort:** 5-6 hours

**Issues:**
- Complex state machine
- Lifecycle management is tricky
- High risk of breaking playback

**Proposed Solution:**
**DEFER** - Player is complex but working well. Only refactor if:
- Bugs emerge requiring fixes
- New playback features needed
- Integration tests can fully validate behavior

**Potential Handlers:**
```
PlaybackStateManager     → State transitions
SourceResolver           → Playback source resolution
SubtitleManager          → Subtitle track selection
AudioTrackManager        → Audio track selection
```

**Risk:** HIGH - Player bugs affect all users immediately

---

#### 3.2 Imaging System

**File:** `core/ui-imaging/GlobalImageLoader.kt`  
**Current:** ~420 lines, CC ~15  
**Priority:** **LOW** (Coil integration, stable)  
**Estimated Effort:** 2-3 hours

**Issues:**
- Coil setup + custom fetchers
- Some complexity in TelegramThumbFetcher integration
- Works well, low maintainability burden

**Proposed Solution:**
**DEFER** - Only refactor if:
- Adding new ImageRef types
- Coil upgrade requires changes
- Performance issues emerge

---

## Implementation Timeline

### Sprint 3 (Current)
- ✅ Handler/Builder Integration
- ✅ Testing & Validation
- ✅ Documentation Updates

### Sprint 4 (Optional)
**Focus:** Telegram Transport  
**Effort:** 1 week  
**Impact:** HIGH

- [ ] Create Telegram handlers (5 classes)
- [ ] Integrate into DefaultTelegramClient
- [ ] Test with real TDLib data
- [ ] Update documentation

**Prerequisites:**
- Sprint 3 complete (Xtream pattern proven)
- Team comfortable with handler pattern

### Sprint 5 (Optional)
**Focus:** Catalog Sync  
**Effort:** 1 week  
**Impact:** HIGH

- [ ] Create source synchronizers (3 classes)
- [ ] Integrate into DefaultCatalogSyncService
- [ ] Performance testing
- [ ] Update documentation

**Prerequisites:**
- Sprint 4 complete (Telegram handlers exist)
- Sync service is stable

### Sprint 6 (Optional)
**Focus:** NX Repository  
**Effort:** 3-4 days  
**Impact:** MEDIUM

- [ ] Create query builders (3 classes)
- [ ] Integrate into ObxNxCatalogRepository
- [ ] Test query performance
- [ ] Update documentation

**Prerequisites:**
- NX schema stable (already true)
- No major schema changes planned

---

## Decision Criteria

### When to Refactor a File

**Refactor if:**
- ✅ Lines > 500 AND CC > 20
- ✅ Hard to add new features
- ✅ Difficult to test
- ✅ High bug rate
- ✅ Similar pattern proven successful (Xtream handlers)

**Defer if:**
- ❌ File is stable and bug-free
- ❌ High risk of breaking critical features
- ❌ No similar pattern exists
- ❌ Testing is difficult/incomplete

### Complexity Thresholds (per PLATIN)

| Metric | Threshold | Action |
|--------|-----------|--------|
| Lines | < 400 | ✅ OK |
| Lines | 400-800 | ⚠️ Consider refactoring |
| Lines | > 800 | ❌ Must refactor |
| CC | < 10 | ✅ OK |
| CC | 10-20 | ⚠️ Monitor |
| CC | > 20 | ❌ Refactor required |

---

## Metrics Tracking

### Current State (Post-Sprint 3)

| Module | Files | Total Lines | Avg CC | Status |
|--------|-------|-------------|--------|--------|
| transport-xtream | 2 | ~1,200 | ~10 | ✅ Refactored |
| data-nx | 2 | ~600 | ~8 | ✅ Refactored |
| transport-telegram | 1 | ~1,800 | ~45 | ⏳ Next |
| catalog-sync | 1 | ~950 | ~35 | ⏳ Next |
| Other | 15+ | ~4,000 | ~18 | 📋 Planned |

### Target State (Post-All Sprints)

| Module | Files | Total Lines | Avg CC | Reduction |
|--------|-------|-------------|--------|-----------|
| transport-xtream | 7 | ~1,200 | ~5 | ✅ 59% |
| data-nx | 5 | ~600 | ~4 | ✅ 51% |
| transport-telegram | 6 | ~1,000 | ~8 | 🎯 45% |
| catalog-sync | 4 | ~600 | ~10 | 🎯 37% |
| Other | 30+ | ~2,500 | ~8 | 🎯 38% |

**Total Improvement:**
- Lines: ~8,800 → ~6,200 (30% reduction)
- CC Avg: ~25 → ~7 (72% complexity reduction)
- Testability: ⬆️ Significant improvement
- Maintainability: ⬆️ PLATIN standards met

---

## Risk Assessment

### Low Risk Refactorings
- ✅ NX Builders (already done)
- ✅ Xtream Handlers (already done)
- ⚠️ NX Query Builders (data layer, well-tested)
- ⚠️ Pipeline Mappers (pure functions)

### Medium Risk Refactorings
- ⚠️ Telegram Handlers (transport layer, complex)
- ⚠️ Catalog Sync (orchestration, performance-critical)
- ⚠️ Feature ViewModels (UI layer, many consumers)

### High Risk Refactorings
- ❌ Player Session (critical feature, complex state)
- ❌ Imaging System (Coil integration, complex caching)

**Mitigation:**
- Start with low-risk refactorings
- Comprehensive testing before medium-risk
- Defer high-risk until absolutely necessary
- Always have rollback plan

---

## Success Criteria (Overall)

### Code Quality
- ✅ All files < 800 lines
- ✅ All methods CC ≤ 10
- ✅ PLATIN quality standards met
- ✅ Test coverage ≥ 80%

### Maintainability
- ✅ New features easy to add
- ✅ Bugs easy to isolate and fix
- ✅ Code reviews faster
- ✅ Onboarding easier

### Performance
- ✅ No regressions
- ✅ Memory usage stable or improved
- ✅ Sync times maintained or improved

### Team
- ✅ Team comfortable with patterns
- ✅ Documentation clear
- ✅ CI/CD stable

---

## Next Steps

1. **Complete Sprint 3** (PR #675)
   - Integrate handlers/builders
   - Test thoroughly
   - Update documentation

2. **Retrospective**
   - What worked well?
   - What was difficult?
   - Adjust approach for Sprint 4

3. **Plan Sprint 4** (Optional)
   - Decide on Telegram handlers
   - Estimate effort
   - Get team buy-in

4. **Monitor Metrics**
   - Track complexity over time
   - Measure bug rates
   - Gather team feedback

---

## Conclusion

The handler/builder pattern has proven successful with Xtream and NX modules:
- ✅ **59% code reduction**
- ✅ **85% complexity reduction**
- ✅ **Better testability**
- ✅ **Improved maintainability**

Applying this pattern to Telegram and Sync modules would yield similar benefits. However, these are **optional** improvements that should be prioritized based on:
- Team capacity
- Bug rates
- Feature roadmap
- Risk tolerance

**Recommendation:** Complete Sprint 3, then reassess priorities based on real-world usage and team feedback.

---

**Document Status:** Ready for Review  
**Next Review:** After Sprint 3 completion
