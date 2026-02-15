# Layer Boundaries

**Generated on 2026-02-15 — DO NOT EDIT MANUALLY**

> Regenerate with: `./gradlew :tools:doc-generator:run --args="--diagrams"`

## Architecture Layers

```mermaid
graph TD
    subgraph UI["UI Layer"]
        feature_home["feature:home"]
        feature_library["feature:library"]
        feature_detail["feature:detail"]
        feature_settings["feature:settings"]
    end

    subgraph Domain["Domain Layer"]
        core_home_domain["core:home-domain"]
        core_library_domain["core:library-domain"]
        core_detail_domain["core:detail-domain"]
    end

    subgraph Data["Data Layer"]
        infra_data_nx["infra:data-nx"]
        infra_data_xtream["infra:data-xtream"]
        infra_data_telegram["infra:data-telegram"]
    end

    subgraph Pipeline["Pipeline Layer"]
        pipeline_xtream["pipeline:xtream"]
        pipeline_telegram["pipeline:telegram"]
    end

    subgraph Transport["Transport Layer"]
        infra_transport_xtream["infra:transport-xtream"]
        infra_transport_telegram["infra:transport-telegram"]
    end

    subgraph Core["Core Model"]
        core_model["core:model"]
        core_persistence["core:persistence"]
    end

    UI --> Domain
    Domain --> Data
    Data --> Core
    Pipeline --> Transport
    Pipeline --> Core
    Transport --> Core

    style UI fill:#4CAF50,color:#fff
    style Domain fill:#2196F3,color:#fff
    style Data fill:#FF9800,color:#fff
    style Pipeline fill:#9C27B0,color:#fff
    style Transport fill:#F44336,color:#fff
    style Core fill:#607D8B,color:#fff
```

## Forbidden Dependencies

| Layer | MUST NOT Import From |
|-------|---------------------|
| Pipeline | Persistence, Data, Playback, UI |
| Transport | Pipeline, Data, Playback, UI, Persistence |
| Data | Pipeline DTOs (TelegramMediaItem, XtreamVodItem) |
| Playback | Pipeline DTOs |
| UI/Feature | Obx*, Pipeline DTOs, Transport |
