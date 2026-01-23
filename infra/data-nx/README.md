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
