# Pipeline Flow

**Generated on 2026-02-15 — DO NOT EDIT MANUALLY**

> Regenerate with: `./gradlew :tools:doc-generator:run --args="--diagrams"`

## Xtream Catalog Sync Flow

```mermaid
sequenceDiagram
    participant W as CatalogSyncWorker
    participant P as XtreamCatalogPipeline
    participant T as XtreamApiClient
    participant M as XtreamMapper
    participant N as MetadataNormalizer
    participant DB as NxCatalogWriter

    Note over W,DB: Scan Phase
    W->>P: startSync(account, categories)
    P->>T: fetchVodStreams(category)
    T-->>P: XtreamVodApiDto[]
    P->>M: mapToRaw(dto)
    M-->>P: RawMediaMetadata[]

    Note over W,DB: Normalization Phase
    P->>N: normalize(raw)
    N-->>P: NormalizedMediaMetadata

    Note over W,DB: Persistence Phase
    P->>DB: ingest(normalized)
    DB-->>P: IngestResult
    P-->>W: SyncResult(added, updated, errors)
```

## Telegram Pipeline Flow

```mermaid
sequenceDiagram
    participant W as CatalogSyncWorker
    participant P as TelegramCatalogPipeline
    participant T as TelegramClient
    participant M as TelegramMediaMapper
    participant N as MetadataNormalizer
    participant DB as NxCatalogWriter

    W->>P: startSync(account, channels)
    P->>T: getHistory(channelId)
    T-->>P: TgMessage[]
    P->>M: mapToRaw(message)
    M-->>P: RawMediaMetadata[]
    P->>N: normalize(raw)
    N-->>P: NormalizedMediaMetadata
    P->>DB: ingest(normalized)
    DB-->>P: IngestResult
```
