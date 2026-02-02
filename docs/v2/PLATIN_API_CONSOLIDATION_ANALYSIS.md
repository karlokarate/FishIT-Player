# PLATIN API Call Consolidation Analysis

> **Status:** Phase 1 (Mark Deprecated) ✅ COMPLETE  
> **Last Updated:** 2025-01-31  
> **Build Verification:** ✅ BUILD SUCCESSFUL (deprecation warnings showing correctly)

## Current State: API Calls Verstreut (PROBLEM)

### Wo `getSeriesInfo()` aufgerufen wird:

| Location | Module | Purpose | Layer | Problem |
|----------|--------|---------|-------|---------|
| `DetailEnrichmentServiceImpl.enrichSeriesFromXtream()` | `infra:data-detail` | Metadata only | Data | ❌ Partial, 1 of 3 calls |
| `XtreamSeriesIndexRefresherImpl.refreshSeasons()` | `infra:data-xtream` | Seasons only | Data | ❌ Partial, 2 of 3 calls |
| `XtreamSeriesIndexRefresherImpl.refreshEpisodes()` | `infra:data-xtream` | Episodes only | Data | ❌ Partial, 3 of 3 calls |
| `XtreamCatalogScanWorker.importSeriesDetailOnce()` | `app-v2:work` | Background sync | App | ⚠️ Good but wrong layer |
| **`UnifiedDetailLoaderImpl.loadSeriesDetailInternal()`** | `infra:data-detail` | **ALLES** | Data | ✅ PLATIN |

### Wo `getVodInfo()` aufgerufen wird:

| Location | Module | Purpose | Layer | Problem |
|----------|--------|---------|-------|---------|
| `DetailEnrichmentServiceImpl.enrichVodFromXtream()` | `infra:data-detail` | Metadata + Hints | Data | ⚠️ OK but separate |
| `XtreamCatalogScanWorker.importVodDetailOnce()` | `app-v2:work` | Background sync | App | ⚠️ Good but wrong layer |
| **`UnifiedDetailLoaderImpl.loadVodDetailInternal()`** | `infra:data-detail` | **ALLES** | Data | ✅ PLATIN |

---

## Target State: PLATIN Konsolidierung

### Layer Boundaries (Korrekt)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        UI Layer (feature/*)                                 │
│  - UnifiedDetailViewModel calls UnifiedDetailLoader                         │
│  - NO direct API client access                                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Domain Layer (core/detail-domain)                       │
│  - UnifiedDetailLoader interface (PLATIN entry point)                       │
│  - DetailEnrichmentService interface                                        │
│  - XtreamSeriesIndexRepository interface                                    │
│  - XtreamSeriesIndexRefresher interface (DEPRECATED → use Loader)           │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      Data Layer (infra/data-*)                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  infra/data-detail (PLATIN SINGLE POINT OF API CALLS)              │    │
│  │    - UnifiedDetailLoaderImpl ✅ (ONE call for Series/VOD)          │    │
│  │    - DetailEnrichmentServiceImpl (DEPRECATED → delegates to Loader)│    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  infra/data-xtream (Repository storage only, NO API calls)          │    │
│  │    - XtreamSeriesIndexRepository ✅ (DB operations)                 │    │
│  │    - XtreamSeriesIndexRefresherImpl (DEPRECATED → use Loader)       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                   Transport Layer (infra/transport-xtream)                  │
│  - XtreamApiClient.getSeriesInfo()                                          │
│  - XtreamApiClient.getVodInfo()                                             │
│  - Pure HTTP/API access, NO business logic                                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## PLATIN Konsolidierungs-Strategie

### Phase 1: Mark Deprecated ✅ COMPLETE

All deprecated items now show compiler warnings during build:

1. **`XtreamSeriesIndexRefresherImpl`** → `@Deprecated` ✅
   - Class-level deprecation with full KDoc
   - All 3 methods marked deprecated
   - Compiler warning: "Use UnifiedDetailLoader.loadDetailImmediate()"

2. **`XtreamSeriesIndexRefresher` interface** → `@Deprecated` ✅
   - Interface-level deprecation in `core:detail-domain`
   - Migration code example in KDoc
   - All method signatures marked deprecated

3. **`DetailEnrichmentServiceImpl.enrichSeriesFromXtream()`** → `@Deprecated` ✅
   - KDoc explains problem (only saves metadata)
   - Links to `UnifiedDetailLoaderImpl` as alternative

4. **`DetailEnrichmentServiceImpl.enrichVodFromXtream()`** → `@Deprecated` ✅
   - KDoc explains consolidated approach
   - DeprecationLevel.WARNING for gradual migration

**Build Output (confirms deprecation working):**
```
w: 'class XtreamSeriesIndexRefresherImpl' is deprecated. Use UnifiedDetailLoader...
w: 'interface XtreamSeriesIndexRefresher' is deprecated. Use UnifiedDetailLoader...
w: 'suspend fun enrichSeriesFromXtream(...)' is deprecated. Use UnifiedDetailLoader...
w: 'suspend fun enrichVodFromXtream(...)' is deprecated. Use UnifiedDetailLoader...
BUILD SUCCESSFUL
```

### Phase 2: Delegate to PLATIN Loader (TODO)

1. **`DetailEnrichmentServiceImpl.enrichImmediate()`** should delegate to `UnifiedDetailLoader`
2. **`XtreamSeriesIndexRefresher`** interface stays but implementation delegates to Loader

### Phase 3: Remove Deprecated Code

After validation (2-4 weeks):
- Remove `enrichSeriesFromXtream()` method
- Remove `enrichVodFromXtream()` method  
- Remove `XtreamSeriesIndexRefresherImpl` class
- Keep only `UnifiedDetailLoaderImpl` as single source of API calls

---

## Code Ownership by Module

### `infra:data-detail` (PLATIN Owner)

**Owns ALL Xtream detail API calls:**
- ✅ `UnifiedDetailLoaderImpl` - PLATIN implementation
- ⚠️ `DetailEnrichmentServiceImpl` - delegates to loader (transitional)

### `infra:data-xtream` (Storage Only)

**Owns ONLY storage operations:**
- ✅ `XtreamSeriesIndexRepository` - DB read/write
- ⚠️ `XtreamSeriesIndexRefresherImpl` - DEPRECATED, will be removed

### `app-v2:work` (Background Sync)

**Owns background sync scheduling:**
- ⚠️ `XtreamCatalogScanWorker` - should call `UnifiedDetailLoader` for detail fetching

---

## Benefits of PLATIN Consolidation

1. **ONE API Call** - No more 3x getSeriesInfo() calls
2. **Single Point of Truth** - All detail fetching in `UnifiedDetailLoaderImpl`
3. **Clear Layer Boundaries** - Transport does HTTP, Data does business logic
4. **Deduplication Built-In** - `inflightRequests` map prevents concurrent duplicate calls
5. **Priority System Integration** - HIGH/CRITICAL priority for UI vs background
6. **Atomic Persistence** - Metadata + Seasons + Episodes saved together
