# infra/data-nx Module

## Overview

This module provides **ObjectBox implementations** for all 22 NX repository interfaces defined in `core/model/repository/`.

## Architecture

```
core/model/repository/           <- Domain interfaces (no ObjectBox)
    NxWorkUserStateRepository.kt
    NxWorkRepository.kt
    ...

infra/data-nx/                   <- This module (ObjectBox implementations)
    repository/
        WorkUserStateRepositoryImpl.kt
        WorkRepositoryImpl.kt
        ...
    mapper/
        WorkUserStateMapper.kt
        WorkMapper.kt
        ...
    di/
        NxDataModule.kt          <- Hilt bindings

core/persistence/obx/            <- Entity definitions
    NxEntities.kt (NX_Work, NX_WorkUserState, etc.)
```

## Responsibilities

1. **Implement** all `Nx*Repository` interfaces from `core/model/repository/`
2. **Map** between domain models and ObjectBox entities
3. **Provide** Hilt bindings via `NxDataModule`

## SSOT Contract Compliance

All implementations MUST follow `contracts/NX_SSOT_CONTRACT.md`:

- Use composite keys (profileKey + workKey) as SSOT identity
- Set `lastUpdatedAtMs`, `lastUpdatedByDeviceId`, `cloudSyncState = DIRTY` on every write
- Never expose ObjectBox IDs as SSOT identifiers

## Implementation Pattern

```kotlin
@Singleton
class WorkUserStateRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore,
) : NxWorkUserStateRepository {
    private val box by lazy { boxStore.boxFor(NX_WorkUserState::class.java) }
    
    override suspend fun get(profileKey: String, workKey: String): WorkUserState? {
        return box.query(
            NX_WorkUserState_.profileKey.equal(profileKey)
                .and(NX_WorkUserState_.workKey.equal(workKey))
        ).build().findFirst()?.toDomain()
    }
}
```

## Dependencies

- `core:model` - Domain interfaces and models
- `core:persistence` - ObjectBox entities and BoxStore
- `infra:logging` - Logging infrastructure

## 🏗️ Builder Pattern Architecture (PLATIN Refactoring)

To reduce Cyclomatic Complexity in `NxCatalogWriter` (CC ~28 → target ≤ 15), entity construction logic has been extracted into specialized builder classes:

### Builder Classes

```
infra/data-nx/writer/
├── NxCatalogWriter.kt                    (Orchestrator - delegates to builders)
└── builder/
    ├── WorkEntityBuilder.kt              (NX_Work construction) - CC ~6
    ├── SourceRefBuilder.kt               (NX_WorkSourceRef construction) - CC ~5
    └── VariantBuilder.kt                 (NX_WorkVariant construction) - CC ~4
```

### Responsibilities

| Builder | Creates | Handles |
|---------|---------|---------|
| `WorkEntityBuilder` | `NX_Work` | Recognition state, external IDs, timestamps |
| `SourceRefBuilder` | `NX_WorkSourceRef` | Source key construction, clean item key extraction, live-specific fields |
| `VariantBuilder` | `NX_WorkVariant` | Variant key construction, container extraction |

### Benefits
1. **Reduced Complexity:** Original CC ~28 → Builder average CC ~5
2. **Eliminates Duplication:** ~220 lines of repeated construction logic removed
3. **Testability:** Each builder can be unit tested independently
4. **Maintainability:** Single responsibility per builder

### Example Usage

```kotlin
@Singleton
class NxCatalogWriter @Inject constructor(
    private val workEntityBuilder: WorkEntityBuilder,
    private val sourceRefBuilder: SourceRefBuilder,
    private val variantBuilder: VariantBuilder,
    // ...
) {
    suspend fun ingest(
        raw: RawMediaMetadata,
        normalized: NormalizedMediaMetadata,
        accountKey: String,
    ): String? {
        val workKey = buildWorkKey(normalized)
        
        // 1. Build and upsert work entity
        val work = workEntityBuilder.build(normalized, workKey)
        workRepository.upsert(work)
        
        // 2. Build and upsert source reference
        val sourceKey = buildSourceKey(...)
        val sourceRef = sourceRefBuilder.build(raw, workKey, accountKey, sourceKey)
        sourceRefRepository.upsert(sourceRef)
        
        // 3. Build and upsert variant (if applicable)
        if (raw.playbackHints.isNotEmpty()) {
            val variantKey = buildVariantKey(sourceKey)
            val variant = variantBuilder.build(
                variantKey, workKey, sourceKey,
                raw.playbackHints, normalized.durationMs
            )
            variantRepository.upsert(variant)
        }
        
        return workKey
    }
}
```

For implementation details, see PR #[issue_number].
