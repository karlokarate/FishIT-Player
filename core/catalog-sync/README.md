# core/catalog-sync Module

## Purpose

The `catalog-sync` module is the orchestration layer between Pipeline and Data layers.
It consumes catalog events from pipelines and persists them to the data repositories.

## Architecture Position

```
Transport → Pipeline → **CatalogSync** → Data → Domain → UI
```

## Responsibilities

### Allowed
- Consume TelegramCatalogEvent and XtreamCatalogEvent from pipelines
- Extract RawMediaMetadata from catalog items
- Call repository.upsertAll() to persist items
- Track sync progress and emit status events
- Optionally pass items through MediaMetadataNormalizer before persisting

### Forbidden
- Direct network calls (use Pipeline)
- Direct TDLib/Xtream API calls (use Transport via Pipeline)
- UI updates (emit status events, UI consumes them)
- ObjectBox/DB access (use Data layer repositories)

## Key Components

### CatalogSyncService
Main orchestrator that:
- Connects to pipeline event streams
- Batches items for efficient persistence
- Tracks progress and emits status

### SyncConfig
Configuration for sync behavior:
- Batch size for upserts
- Normalization enabled/disabled
- Progress emission frequency

## Usage

```kotlin
@Inject lateinit var catalogSyncService: CatalogSyncService

// Start sync for Telegram
catalogSyncService.syncTelegram(config = TelegramCatalogConfig.DEFAULT)
    .collect { status ->
        when (status) {
            is SyncStatus.InProgress -> showProgress(status.progress)
            is SyncStatus.Completed -> showSuccess()
            is SyncStatus.Error -> showError(status.message)
        }
    }
```

## Dependencies

- `core:model` - RawMediaMetadata, NormalizedMediaMetadata
- `core:metadata-normalizer` - Optional normalization before persist
- `pipeline:telegram` - TelegramCatalogPipeline, TelegramCatalogEvent
- `pipeline:xtream` - XtreamCatalogPipeline, XtreamCatalogEvent
- `infra:data-telegram` - TelegramContentRepository
- `infra:data-xtream` - XtreamCatalogRepository, XtreamLiveRepository
