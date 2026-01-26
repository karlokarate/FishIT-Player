# NX Migration Status

**Last Updated:** Jan 2026  
**Migration Phase:** NX-ONLY Active (Legacy OBX Deprecated)

---

## Overview

The NX (Next-generation) data layer is now the **ONLY SSOT** for all UI screens.
Per `NX_SSOT_CONTRACT.md` INV-6, no UI code may import or query legacy `Obx*` entities.

**Key Milestones (Jan 2026):**
- ✅ NX entities are the SSOT for UI reads
- ✅ Legacy OBX repositories marked `@Deprecated`
- ✅ Legacy ContentRepositoryAdapter files deleted
- ✅ CatalogSyncService writes exclusively via NxCatalogWriter
- ✅ feature:detail uses NX via NxCanonicalMediaRepositoryImpl (bound in NxDataModule)
- ✅ Profile/Content/ScreenTime legacy files deleted (7 files, Jan 2026)

---

## Current Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     CatalogSyncService                               │
│  (Receives normalized media from pipelines)                         │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │  NxCatalogWriter              │
              │  (NX-ONLY - no dual-write)    │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │  NX_Work                      │
              │  NX_WorkSourceRef             │
              │  NX_WorkVariant               │
              │  NX_WorkRelation              │
              │  NX_WorkUserState             │
              └───────────────────────────────┘
```

```
┌─────────────────────────────────────────────────────────────────────┐
│                     HomeViewModel (feature:home)                     │
│  observeContinueWatching(), observeRecentlyAdded(), etc.            │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │  HomeContentRepository        │
              │  (Interface in home-domain)   │
              └───────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │  NxHomeContentRepositoryImpl  │  ← ACTIVE (SSOT)
              │  (Reads from NX_* entities)   │
              └───────────────────────────────┘
```

---

## Component Status

### ✅ WRITE-Side (NX-ONLY Active)

| Component | Status | Location | Notes |
|-----------|--------|----------|-------|
| `NxCatalogWriter` | ✅ Active | `infra/data-nx/writer/` | ONLY writer - no dual-write |
| CatalogSyncService | ✅ NX-ONLY | `core/catalog-sync/` | All batches go through NxCatalogWriter |

### ✅ READ-Side (NX-ONLY Active)

| Component | Status | Location | Notes |
|-----------|--------|----------|-------|
| `NxHomeContentRepositoryImpl` | ✅ Active | `infra/data-nx/home/` | SSOT for Home screen |
| `NxLibraryContentRepositoryImpl` | ✅ Active | `infra/data-nx/library/` | SSOT for Library screen |
| `NxLiveContentRepositoryImpl` | ✅ Active | `infra/data-nx/live/` | SSOT for Live TV screen |
| `NxXtreamSeriesIndexRepository` | ✅ Active | `infra/data-nx/xtream/` | SSOT for Series detail |
| Hilt Bindings | ✅ Active | `NxDataModule` | All content repos bound to NX implementations |

### ❌ DELETED (Jan 2026)

| Component | Status | Was In | Notes |
|-----------|--------|--------|-------|
| `HomeContentRepositoryAdapter` | ❌ Deleted | `infra/data-home/` | Replaced by NxHomeContentRepositoryImpl |
| `LibraryContentRepositoryAdapter` | ❌ Deleted | `infra/data-xtream/` | Replaced by NxLibraryContentRepositoryImpl |
| `LiveContentRepositoryAdapter` | ❌ Deleted | `infra/data-xtream/` | Replaced by NxLiveContentRepositoryImpl |

### ⚠️ DEPRECATED (Marked for Removal)

| Component | Status | Location | Notes |
|-----------|--------|----------|-------|
| `ObxXtreamCatalogRepository` | ⚠️ @Deprecated | `infra/data-xtream/` | Legacy, see INV-6 |
| `ObxXtreamLiveRepository` | ⚠️ @Deprecated | `infra/data-xtream/` | Legacy, see INV-6 |
| `ObxXtreamSeriesIndexRepository` | ⚠️ @Deprecated | `infra/data-xtream/` | Use NxXtreamSeriesIndexRepository |
| `ObxCanonicalMediaRepository` | ⚠️ @Deprecated | `core/persistence/` | Not SSOT anymore |

### 🔴 PENDING MIGRATION

| Component | Status | Location | Notes |
|-----------|--------|----------|-------|
| `feature:detail` module | 🔴 Uses legacy | `feature/detail/` | Still uses CanonicalMediaRepository |
| `PlayMediaUseCase` | 🔴 Uses legacy | `core/detail-domain/` | Needs migration to NX |
| `UnifiedDetailViewModel` | 🔴 Uses legacy | `feature/detail/` | Needs migration to NX |

### 📦 NX Repository Implementations

All 22 NX repositories are implemented and bound:

| Repository | Implementation | Status |
|------------|----------------|--------|
| `NxWorkRepository` | `NxWorkRepositoryImpl` | ✅ Active |
| `NxWorkUserStateRepository` | `NxWorkUserStateRepositoryImpl` | ✅ Active |
| `NxWorkSourceRefRepository` | `NxWorkSourceRefRepositoryImpl` | ✅ Active |
| `NxWorkVariantRepository` | `NxWorkVariantRepositoryImpl` | ✅ Active |
| `NxWorkRelationRepository` | `NxWorkRelationRepositoryImpl` | ✅ Active |
| `NxWorkRuntimeStateRepository` | `NxWorkRuntimeStateRepositoryImpl` | ✅ Active |
| `NxIngestLedgerRepository` | `NxIngestLedgerRepositoryImpl` | ✅ Active |
| `NxProfileRepository` | `NxProfileRepositoryImpl` | ✅ Active |
| `NxProfileRuleRepository` | `NxProfileRuleRepositoryImpl` | ✅ Active |
| `NxProfileUsageRepository` | `NxProfileUsageRepositoryImpl` | ✅ Active |
| `NxSourceAccountRepository` | `NxSourceAccountRepositoryImpl` | ✅ Active |
| `NxCloudOutboxRepository` | `NxCloudOutboxRepositoryImpl` | ✅ Active |
| `NxWorkEmbeddingRepository` | `NxWorkEmbeddingRepositoryImpl` | ✅ Active |
| `NxWorkRedirectRepository` | `NxWorkRedirectRepositoryImpl` | ✅ Active |
| `NxWorkAuthorityRepository` | `NxWorkAuthorityRepositoryImpl` | ✅ Active |
| `NxWorkDiagnostics` | `NxWorkDiagnosticsImpl` | ✅ Active |
| `NxWorkUserStateDiagnostics` | `NxWorkUserStateDiagnosticsImpl` | ✅ Active |
| `NxWorkSourceRefDiagnostics` | `NxWorkSourceRefDiagnosticsImpl` | ✅ Active |
| `NxWorkVariantDiagnostics` | `NxWorkVariantDiagnosticsImpl` | ✅ Active |

---

## Data Flow

### 1. Catalog Ingestion (Telegram/Xtream)

```
Pipeline Output (RawMediaMetadata, NormalizedMediaMetadata)
    │
    ▼
CatalogSyncService.processBatch()
    │
    ▼
NxCatalogWriter.ingest()                [NX-ONLY - no dual-write]
    │
    ├──► NxWorkRepository.upsert()
    ├──► NxWorkSourceRefRepository.upsert()
    └──► NxWorkVariantRepository.upsert()
```

### 2. Home Screen Content

```
HomeViewModel
    │
    ▼
HomeContentRepository.observeMovies() / observeRecentlyAdded() / ...
    │
    ▼
NxHomeContentRepositoryImpl  [SSOT]
    │
    ├──► NxWorkRepository.observeByType()
    ├──► NxWorkUserStateRepository.observeContinueWatching()
    └──► NxWorkSourceRefRepository.findByWorkKey()
               │
               ▼
          HomeMediaItem (domain model)
```

---

## Profile Handling

**Current:** Uses `DEFAULT_PROFILE_KEY = "default"` for all user states.

**Future:** Will integrate with `ProfileManager` when implemented in v2.

---

## Next Steps

1. ✅ ~~Mark old OBX components with `@Deprecated` annotation~~ (Done Jan 2026)
2. ✅ ~~Create NX implementations for other repositories (Library, Live, Detail)~~ (Done)
3. ✅ ~~Remove dual-write to old OBX layer~~ (Done - NX-ONLY mode active)
4. ✅ ~~Delete unused ContentRepositoryAdapter files~~ (Done Jan 2026)
5. ✅ ~~Migrate `feature:detail` to use NX repositories~~ (Already uses NxCanonicalMediaRepositoryImpl)
6. ✅ ~~Delete Profile/Content/ScreenTime legacy files~~ (7 files deleted Jan 2026)
7. **[TODO]** Implement ProfileManager for proper multi-profile support
8. **[DEFERRED]** Migrate TelegramContentRepository to NX (blocked by CatalogSync refactor)

---

## Files Modified in This Migration

### New Files Created

| File | Purpose |
|------|---------|
| `infra/data-nx/src/.../home/NxHomeContentRepositoryImpl.kt` | NX-based HomeContentRepository implementation |
| `infra/data-nx/src/.../writer/NxCatalogWriter.kt` | Catalog ingestion writer |

### Files Modified

| File | Change |
|------|--------|
| `infra/data-nx/build.gradle.kts` | Added `:core:home-domain` dependency |
| `infra/data-nx/src/.../di/NxDataModule.kt` | Added HomeContentRepository binding |
| `infra/data-home/src/.../di/HomeDataModule.kt` | Removed binding (moved to NxDataModule) |
| `core/catalog-sync/build.gradle.kts` | Added `:infra:data-nx` dependency |
| `core/catalog-sync/src/.../DefaultCatalogSyncService.kt` | Added NxCatalogWriter dual-write |

---

## Verification

```bash
# Compile check
./gradlew :app-v2:compileDebugKotlin --no-daemon

# Full APK build
./gradlew assembleDebug --no-daemon
```

Both commands complete successfully with BUILD SUCCESSFUL.
