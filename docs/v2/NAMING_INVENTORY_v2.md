# v2 Naming Inventory

**Generated:** 2025-12-11  
**Scope:** `app-v2`, `feature/*`, `core/*`, `pipeline/*`, `infra/*`, `playback/*`, `player/*`  
**Total Files:** 233 Kotlin files

---

## File-to-Vocabulary Mapping

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `app-v2/src/main/java/com/fishit/player/v2/FishItV2Application.kt` | `:app-v2` | `v2` | Hilt Application entry point | AppShell | |
| `app-v2/src/main/java/com/fishit/player/v2/MainActivity.kt` | `:app-v2` | `v2` | Main Activity | AppShell | |
| `app-v2/src/main/java/com/fishit/player/v2/feature/AppFeatureRegistry.kt` | `:app-v2` | `v2.feature` | Central feature registry implementation | AppShell | |
| `app-v2/src/main/java/com/fishit/player/v2/feature/di/FeatureModule.kt` | `:app-v2` | `v2.feature.di` | Hilt module for feature system | AppShell | |
| `app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt` | `:app-v2` | `v2.navigation` | Compose navigation host | AppShell | |
| `app-v2/src/main/java/com/fishit/player/v2/ui/debug/DebugSkeletonScreen.kt` | `:app-v2` | `v2.ui.debug` | Debug placeholder UI | Tooling | |
| `app-v2/src/main/java/com/fishit/player/v2/ui/theme/Theme.kt` | `:app-v2` | `v2.ui.theme` | Material3 theme | AppShell | |
| `app-v2/src/main/java/com/fishit/player/v2/ui/theme/Type.kt` | `:app-v2` | `v2.ui.theme` | Typography definitions | AppShell | |
| `app-v2/src/test/java/com/fishit/player/v2/feature/AppFeatureRegistryTest.kt` | `:app-v2` | `v2.feature` | Unit test for feature registry | AppShell | |

---

## Core Modules

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `core/app-startup/src/main/java/com/fishit/player/core/appstartup/AppStartup.kt` | `:core:app-startup` | `core.appstartup` | App startup interface | CoreModel | |
| `core/app-startup/src/main/java/com/fishit/player/core/appstartup/AppStartupConfig.kt` | `:core:app-startup` | `core.appstartup` | Startup configuration | CoreModel | |
| `core/app-startup/src/main/java/com/fishit/player/core/appstartup/AppStartupImpl.kt` | `:core:app-startup` | `core.appstartup` | Startup implementation | CoreModel | |
| `core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/CatalogSyncContract.kt` | `:core:catalog-sync` | `core.catalogsync` | Catalog sync contracts | CoreModel | |
| `core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/DefaultCatalogSyncService.kt` | `:core:catalog-sync` | `core.catalogsync` | Default sync service | CoreModel | |
| `core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/di/CatalogSyncModule.kt` | `:core:catalog-sync` | `core.catalogsync.di` | Hilt DI module | CoreModel | |
| `core/feature-api/src/main/kotlin/com/fishit/player/core/feature/FeatureId.kt` | `:core:feature-api` | `core.feature` | Feature identifier value class | CoreModel | |
| `core/feature-api/src/main/kotlin/com/fishit/player/core/feature/FeatureOwner.kt` | `:core:feature-api` | `core.feature` | Feature ownership data class | CoreModel | |
| `core/feature-api/src/main/kotlin/com/fishit/player/core/feature/FeatureProvider.kt` | `:core:feature-api` | `core.feature` | Feature provider interface | CoreModel | |
| `core/feature-api/src/main/kotlin/com/fishit/player/core/feature/FeatureRegistry.kt` | `:core:feature-api` | `core.feature` | Feature registry interface | CoreModel | |
| `core/feature-api/src/main/kotlin/com/fishit/player/core/feature/FeatureScope.kt` | `:core:feature-api` | `core.feature` | Feature scope enum | CoreModel | |
| `core/feature-api/src/main/kotlin/com/fishit/player/core/feature/Features.kt` | `:core:feature-api` | `core.feature` | Feature ID constants | CoreModel | |
| `core/firebase/src/main/java/com/fishit/player/core/firebase/package-info.kt` | `:core:firebase` | `core.firebase` | Package placeholder | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/CanonicalMediaId.kt` | `:core:model` | `core.model` | Canonical media identifier | CoreModel | |
| `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/FallbackCanonicalKeyGenerator.kt` | `:core:metadata-normalizer` | `core.metadata-normalizer` | Canonical fallback key generator | MetadataNormalizer | |
| `core/model/src/main/java/com/fishit/player/core/model/ImageRef.kt` | `:core:model` | `core.model` | Image reference sealed class | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/MediaSourceRef.kt` | `:core:model` | `core.model` | Media source reference | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/MediaSourceRefExtensions.kt` | `:core:model` | `core.model` | MediaSourceRef extension functions | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/MediaType.kt` | `:core:model` | `core.model` | Media type enum | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/MediaVariant.kt` | `:core:model` | `core.model` | Media variant data class | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/MimeDecider.kt` | `:core:model` | `core.model` | MIME type decision logic | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/NormalizedMedia.kt` | `:core:model` | `core.model` | Normalized media data class | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/NormalizedMediaMetadata.kt` | `:core:model` | `core.model` | Normalized metadata | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/PipelineIdTag.kt` | `:core:model` | `core.model` | Pipeline identifier enum | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/PlaybackType.kt` | `:core:model` | `core.model` | Playback type enum | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/RawMediaMetadata.kt` | `:core:model` | `core.model` | Raw pipeline metadata | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/ResumePoint.kt` | `:core:model` | `core.model` | Resume position data | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/SourceKey.kt` | `:core:model` | `core.model` | Pipeline source key | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/VariantHealthStore.kt` | `:core:model` | `core.model` | Variant health tracking | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/VariantSelector.kt` | `:core:model` | `core.model` | Variant selection logic | CoreModel | |
| `core/model/src/main/java/com/fishit/player/core/model/repository/*.kt` | `:core:model` | `core.model.repository` | Repository interfaces | CoreModel | |
| `core/player-model/src/main/java/com/fishit/player/core/playermodel/PlaybackContext.kt` | `:core:player-model` | `core.playermodel` | Playback context data | Player | |
| `core/player-model/src/main/java/com/fishit/player/core/playermodel/PlaybackError.kt` | `:core:player-model` | `core.playermodel` | Playback error types | Player | |
| `core/player-model/src/main/java/com/fishit/player/core/playermodel/PlaybackState.kt` | `:core:player-model` | `core.playermodel` | Playback state enum | Player | |
| `core/player-model/src/main/java/com/fishit/player/core/playermodel/SourceType.kt` | `:core:player-model` | `core.playermodel` | Source type enum | Player | |
| `core/persistence/src/main/java/com/fishit/player/core/persistence/**/*.kt` | `:core:persistence` | `core.persistence.*` | ObjectBox persistence layer | CoreModel | |
| `core/ui-imaging/src/main/java/com/fishit/player/core/imaging/**/*.kt` | `:core:ui-imaging` | `core.imaging.*` | Coil image loading | CoreModel | |

---

## Metadata Normalizer

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/MediaMetadataNormalizer.kt` | `:core:metadata-normalizer` | `core.metadata` | Normalizer interface | MetadataNormalizer | |
| `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/DefaultMediaMetadataNormalizer.kt` | `:core:metadata-normalizer` | `core.metadata` | Default implementation | MetadataNormalizer | |
| `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/TmdbMetadataResolver.kt` | `:core:metadata-normalizer` | `core.metadata` | TMDB resolver interface | MetadataNormalizer | |
| `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/DefaultTmdbMetadataResolver.kt` | `:core:metadata-normalizer` | `core.metadata` | Default TMDB resolver | MetadataNormalizer | |
| `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/Normalizer.kt` | `:core:metadata-normalizer` | `core.metadata` | Unified normalizer facade | MetadataNormalizer | |
| `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/parser/SceneNameParser.kt` | `:core:metadata-normalizer` | `core.metadata.parser` | Scene name parser interface | MetadataNormalizer | |
| `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/parser/RegexSceneNameParser.kt` | `:core:metadata-normalizer` | `core.metadata.parser` | Regex-based parser | MetadataNormalizer | |
| `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/parser/ParsedSceneInfo.kt` | `:core:metadata-normalizer` | `core.metadata.parser` | Parsed scene info data | MetadataNormalizer | |

---

## Feature Modules (App Features)

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `feature/audiobooks/src/main/java/.../package-info.kt` | `:feature:audiobooks` | `feature.audiobooks` | Placeholder | AppFeature | |
| `feature/detail/src/main/java/com/fishit/player/feature/detail/ManualVariantSelectionStore.kt` | `:feature:detail` | `feature.detail` | Manual variant selection | AppFeature | |
| `feature/detail/src/main/java/com/fishit/player/feature/detail/UnifiedDetailUseCases.kt` | `:feature:detail` | `feature.detail` | Detail use cases | AppFeature | |
| `feature/detail/src/main/java/com/fishit/player/feature/detail/UnifiedDetailViewModel.kt` | `:feature:detail` | `feature.detail` | Detail ViewModel | AppFeature | |
| `feature/detail/src/main/java/com/fishit/player/feature/detail/ui/SourceBadge.kt` | `:feature:detail` | `feature.detail.ui` | Source badge composable | AppFeature | |
| `feature/home/src/main/java/com/fishit/player/feature/home/debug/DebugPlaybackScreen.kt` | `:feature:home` | `feature.home.debug` | Debug playback screen | AppFeature | |
| `feature/library/src/main/java/.../package-info.kt` | `:feature:library` | `feature.library` | Placeholder | AppFeature | |
| `feature/live/src/main/java/.../package-info.kt` | `:feature:live` | `feature.live` | Placeholder | AppFeature | |
| `feature/settings/src/main/java/com/fishit/player/feature/settings/PlaybackSettingsRepository.kt` | `:feature:settings` | `feature.settings` | Settings repository | AppFeature | |
| `feature/telegram-media/src/main/java/com/fishit/player/feature/telegram/TelegramMediaViewModel.kt` | `:feature:telegram-media` | `feature.telegram` | Telegram media ViewModel | AppFeature | |

---

## Pipeline Modules

### Telegram Pipeline

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `pipeline/telegram/.../adapter/TelegramPipelineAdapter.kt` | `:pipeline:telegram` | `pipeline.telegram.adapter` | Pipeline adapter | Pipeline | |
| `pipeline/telegram/.../capability/TelegramFullHistoryCapabilityProvider.kt` | `:pipeline:telegram` | `pipeline.telegram.capability` | Full history capability | PipelineCapability | ✅ Correctly named |
| `pipeline/telegram/.../capability/TelegramLazyThumbnailsCapabilityProvider.kt` | `:pipeline:telegram` | `pipeline.telegram.capability` | Lazy thumbnails capability | PipelineCapability | ✅ Correctly named |
| `pipeline/telegram/.../capability/di/TelegramCapabilityModule.kt` | `:pipeline:telegram` | `pipeline.telegram.capability.di` | Capability DI module | PipelineCapability | ✅ Correctly named |
| `pipeline/telegram/.../catalog/TelegramCatalogContract.kt` | `:pipeline:telegram` | `pipeline.telegram.catalog` | Catalog contract | Pipeline | |
| `pipeline/telegram/.../catalog/TelegramCatalogPipelineImpl.kt` | `:pipeline:telegram` | `pipeline.telegram.catalog` | Catalog pipeline implementation | Pipeline | |
| `pipeline/telegram/.../catalog/TelegramChatMediaClassifier.kt` | `:pipeline:telegram` | `pipeline.telegram.catalog` | Chat media classifier | Pipeline | |
| `pipeline/telegram/.../catalog/TelegramChatMediaProfile.kt` | `:pipeline:telegram` | `pipeline.telegram.catalog` | Chat media profile | Pipeline | |
| `pipeline/telegram/.../catalog/TelegramMessageCursor.kt` | `:pipeline:telegram` | `pipeline.telegram.catalog` | Message cursor | Pipeline | |
| `pipeline/telegram/.../debug/TelegramDebugService.kt` | `:pipeline:telegram` | `pipeline.telegram.debug` | Debug service interface | Pipeline | |
| `pipeline/telegram/.../debug/TelegramDebugServiceImpl.kt` | `:pipeline:telegram` | `pipeline.telegram.debug` | Debug service implementation | Pipeline | |
| `pipeline/telegram/.../mapper/TelegramRawMetadataContract.kt` | `:pipeline:telegram` | `pipeline.telegram.mapper` | Metadata mapping contract | Pipeline | |
| `pipeline/telegram/.../model/TelegramMediaItem.kt` | `:pipeline:telegram` | `pipeline.telegram.model` | Telegram media item DTO | Pipeline | |
| `pipeline/telegram/.../model/TelegramRawMetadataExtensions.kt` | `:pipeline:telegram` | `pipeline.telegram.model` | RawMetadata extensions | Pipeline | |
| `pipeline/telegram/.../model/*.kt` | `:pipeline:telegram` | `pipeline.telegram.model` | DTOs and models | Pipeline | |

### Xtream Pipeline

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `pipeline/xtream/.../adapter/XtreamPipelineAdapter.kt` | `:pipeline:xtream` | `pipeline.xtream.adapter` | Pipeline adapter | Pipeline | |
| `pipeline/xtream/.../catalog/DefaultXtreamCatalogSource.kt` | `:pipeline:xtream` | `pipeline.xtream.catalog` | Default catalog source | Pipeline | |
| `pipeline/xtream/.../catalog/XtreamCatalogContract.kt` | `:pipeline:xtream` | `pipeline.xtream.catalog` | Catalog contract | Pipeline | |
| `pipeline/xtream/.../catalog/XtreamCatalogPipelineImpl.kt` | `:pipeline:xtream` | `pipeline.xtream.catalog` | Catalog pipeline implementation | Pipeline | |
| `pipeline/xtream/.../catalog/XtreamCatalogSource.kt` | `:pipeline:xtream` | `pipeline.xtream.catalog` | Catalog source interface | Pipeline | |
| `pipeline/xtream/.../debug/XtreamDebugService.kt` | `:pipeline:xtream` | `pipeline.xtream.debug` | Debug service interface | Pipeline | |
| `pipeline/xtream/.../debug/XtreamDebugServiceImpl.kt` | `:pipeline:xtream` | `pipeline.xtream.debug` | Debug service implementation | Pipeline | |
| `pipeline/xtream/.../mapper/XtreamCatalogMapper.kt` | `:pipeline:xtream` | `pipeline.xtream.mapper` | Catalog to RawMetadata mapper | Pipeline | ✅ Moved |
| `pipeline/xtream/.../mapper/XtreamRawMetadataExtensions.kt` | `:pipeline:xtream` | `pipeline.xtream.mapper` | RawMetadata extensions | Pipeline | ✅ Moved |
| `pipeline/xtream/.../mapper/XtreamImageRefExtensions.kt` | `:pipeline:xtream` | `pipeline.xtream.mapper` | ImageRef extensions | Pipeline | ✅ Moved |
| `pipeline/xtream/.../model/*.kt` | `:pipeline:xtream` | `pipeline.xtream.model` | DTOs and models | Pipeline | |

### IO Pipeline

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `pipeline/io/src/main/java/com/fishit/player/pipeline/io/IoContentRepository.kt` | `:pipeline:io` | `pipeline.io` | Content repository interface | Pipeline | |
| `pipeline/io/src/main/java/com/fishit/player/pipeline/io/IoMediaItem.kt` | `:pipeline:io` | `pipeline.io` | IO media item | Pipeline | |
| `pipeline/io/src/main/java/com/fishit/player/pipeline/io/IoMediaItemExtensions.kt` | `:pipeline:io` | `pipeline.io` | Media item extensions | Pipeline | |
| `pipeline/io/src/main/java/com/fishit/player/pipeline/io/IoPlaybackSourceFactory.kt` | `:pipeline:io` | `pipeline.io` | Playback source factory | Pipeline | |
| `pipeline/io/src/main/java/com/fishit/player/pipeline/io/IoSource.kt` | `:pipeline:io` | `pipeline.io` | IO source definition | Pipeline | |
| `pipeline/io/src/main/java/com/fishit/player/pipeline/io/StubIoContentRepository.kt` | `:pipeline:io` | `pipeline.io` | Stub repository | Pipeline | |
| `pipeline/io/src/main/java/com/fishit/player/pipeline/io/StubIoPlaybackSourceFactory.kt` | `:pipeline:io` | `pipeline.io` | Stub factory | Pipeline | |

### Audiobook Pipeline

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `pipeline/audiobook/src/main/java/.../package-info.kt` | `:pipeline:audiobook` | `pipeline.audiobook` | Placeholder | Pipeline | |

---

## Infrastructure Modules

### Transport Layer

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `infra/transport-telegram/.../TelegramTransportClient.kt` | `:infra:transport-telegram` | `infra.transport.telegram` | Transport client interface | InfraTransport | |
| `infra/transport-telegram/.../DefaultTelegramTransportClient.kt` | `:infra:transport-telegram` | `infra.transport.telegram` | Default transport implementation | InfraTransport | |
| `infra/transport-telegram/.../TdlibClientProvider.kt` | `:infra:transport-telegram` | `infra.transport.telegram` | TDLib client provider (⚠️ internal only, v1 legacy) | InfraTransport | |
| `infra/transport-telegram/.../TelegramClientFactory.kt` | `:infra:transport-telegram` | `infra.transport.telegram` | Client factory | InfraTransport | |
| `infra/transport-telegram/.../TelegramLoggingConfig.kt` | `:infra:transport-telegram` | `infra.transport.telegram` | Logging configuration | InfraTransport | |
| `infra/transport-telegram/.../TelegramSessionConfig.kt` | `:infra:transport-telegram` | `infra.transport.telegram` | Session configuration | InfraTransport | |
| `infra/transport-telegram/.../di/TelegramTransportModule.kt` | `:infra:transport-telegram` | `infra.transport.telegram.di` | Hilt DI module | InfraTransport | |
| `infra/transport-telegram/.../internal/TdLibLogInstaller.kt` | `:infra:transport-telegram` | `infra.transport.telegram.internal` | TDLib log installer | InfraTransport | |
| `infra/transport-xtream/.../XtreamApiClient.kt` | `:infra:transport-xtream` | `infra.transport.xtream` | API client interface | InfraTransport | |
| `infra/transport-xtream/.../DefaultXtreamApiClient.kt` | `:infra:transport-xtream` | `infra.transport.xtream` | Default API client | InfraTransport | |
| `infra/transport-xtream/.../XtreamApiModels.kt` | `:infra:transport-xtream` | `infra.transport.xtream` | API response models | InfraTransport | |
| `infra/transport-xtream/.../XtreamDiscovery.kt` | `:infra:transport-xtream` | `infra.transport.xtream` | Server discovery | InfraTransport | |
| `infra/transport-xtream/.../XtreamUrlBuilder.kt` | `:infra:transport-xtream` | `infra.transport.xtream` | URL builder | InfraTransport | |
| `infra/transport-xtream/.../di/XtreamTransportModule.kt` | `:infra:transport-xtream` | `infra.transport.xtream.di` | Hilt DI module | InfraTransport | |

### Data Layer

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `infra/data-telegram/.../TelegramContentRepository.kt` | `:infra:data-telegram` | `infra.data.telegram` | Content repository interface | InfraData | |
| `infra/data-telegram/.../TdlibTelegramContentRepository.kt` | `:infra:data-telegram` | `infra.data.telegram` | TDLib-based repository | InfraData | |
| `infra/data-telegram/.../ObxTelegramContentRepository.kt` | `:infra:data-telegram` | `infra.data.telegram` | ObjectBox-based repository | InfraData | |
| `infra/data-telegram/.../di/TelegramDataModule.kt` | `:infra:data-telegram` | `infra.data.telegram.di` | Hilt DI module | InfraData | |
| `infra/data-xtream/.../XtreamCatalogRepository.kt` | `:infra:data-xtream` | `infra.data.xtream` | Catalog repository interface | InfraData | |
| `infra/data-xtream/.../XtreamLiveRepository.kt` | `:infra:data-xtream` | `infra.data.xtream` | Live repository interface | InfraData | |
| `infra/data-xtream/.../ObxXtreamCatalogRepository.kt` | `:infra:data-xtream` | `infra.data.xtream` | ObjectBox catalog repository | InfraData | |
| `infra/data-xtream/.../ObxXtreamLiveRepository.kt` | `:infra:data-xtream` | `infra.data.xtream` | ObjectBox live repository | InfraData | |
| `infra/data-xtream/.../di/XtreamDataModule.kt` | `:infra:data-xtream` | `infra.data.xtream.di` | Hilt DI module | InfraData | |

### Logging

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `infra/logging/.../UnifiedLog.kt` | `:infra:logging` | `infra.logging` | Unified logging facade | Logging | |
| `infra/logging/.../UnifiedLogInitializer.kt` | `:infra:logging` | `infra.logging` | Log initializer | Logging | |

### Tooling

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `infra/tooling/src/main/java/.../package-info.kt` | `:infra:tooling` | `infra.tooling` | Placeholder | Tooling | |

---

## Playback Modules

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `playback/domain/.../PlaybackSource.kt` | `:playback:domain` | `playback.domain` | Playback source data class | Playback | |
| `playback/domain/.../PlaybackSourceFactory.kt` | `:playback:domain` | `playback.domain` | Source factory interface | Playback | |
| `playback/domain/.../ResumeManager.kt` | `:playback:domain` | `playback.domain` | Resume manager interface | Playback | |
| `playback/domain/.../KidsPlaybackGate.kt` | `:playback:domain` | `playback.domain` | Kids gate interface | Playback | |
| `playback/domain/.../LivePlaybackController.kt` | `:playback:domain` | `playback.domain` | Live controller interface | Playback | |
| `playback/domain/.../SubtitleSelectionPolicy.kt` | `:playback:domain` | `playback.domain` | Subtitle policy interface | Playback | |
| `playback/domain/.../SubtitleStyleManager.kt` | `:playback:domain` | `playback.domain` | Subtitle style interface | Playback | |
| `playback/domain/.../TvInputController.kt` | `:playback:domain` | `playback.domain` | TV input interface | Playback | |
| `playback/domain/.../TelegramMp4Validator.kt` | `:playback:domain` | `playback.domain` | MP4 validation | Playback | |
| `playback/domain/.../defaults/Default*.kt` | `:playback:domain` | `playback.domain.defaults` | Default implementations | Playback | |
| `playback/domain/.../di/PlaybackDomainModule.kt` | `:playback:domain` | `playback.domain.di` | Hilt DI module | Playback | |
| `playback/telegram/.../TelegramFileDataSource.kt` | `:playback:telegram` | `playback.telegram` | Telegram DataSource | Playback | |
| `playback/telegram/.../TelegramPlaybackSourceFactoryImpl.kt` | `:playback:telegram` | `playback.telegram` | Telegram source factory | Playback | |
| `playback/telegram/.../di/TelegramPlaybackModule.kt` | `:playback:telegram` | `playback.telegram.di` | Hilt DI module | Playback | |
| `playback/xtream/.../XtreamPlaybackSourceFactoryImpl.kt` | `:playback:xtream` | `playback.xtream` | Xtream source factory | Playback | |
| `playback/xtream/.../di/XtreamPlaybackModule.kt` | `:playback:xtream` | `playback.xtream.di` | Hilt DI module | Playback | |

---

## Player Modules

| File Path | Module | Package | Role | Category | Rename Suggestion |
|-----------|--------|---------|------|----------|-------------------|
| `player/internal/.../InternalPlayerEntry.kt` | `:player:internal` | `internal` | Composable player entry | Player | |
| `player/internal/.../session/InternalPlayerSession.kt` | `:player:internal` | `internal.session` | ExoPlayer session manager | Player | |
| `player/internal/.../state/InternalPlayerState.kt` | `:player:internal` | `internal.state` | Player state data class | Player | |
| `player/internal/.../source/PlaybackSourceResolver.kt` | `:player:internal` | `internal.source` | Source resolver with factory injection | Player | |
| `player/internal/.../source/VariantPlaybackOrchestrator.kt` | `:player:internal` | `internal.source` | Variant orchestration | Player | |
| `player/internal/.../ui/InternalPlayerControls.kt` | `:player:internal` | `internal.ui` | Player controls composable | Player | |
| `player/internal/.../ui/PlayerSurface.kt` | `:player:internal` | `internal.ui` | Player surface wrapper | Player | |
| `player/internal/.../di/PlayerDataSourceModule.kt` | `:player:internal` | `internal.di` | DataSource DI module | Player | |
| `player/miniplayer/.../MiniPlayerState.kt` | `:player:miniplayer` | `miniplayer` | MiniPlayer state | Player | |
| `player/miniplayer/.../MiniPlayerManager.kt` | `:player:miniplayer` | `miniplayer` | Manager interface | Player | |
| `player/miniplayer/.../DefaultMiniPlayerManager.kt` | `:player:miniplayer` | `miniplayer` | Default implementation | Player | |
| `player/miniplayer/.../MiniPlayerCoordinator.kt` | `:player:miniplayer` | `miniplayer` | Coordinator | Player | |
| `player/miniplayer/.../PlayerWithMiniPlayerState.kt` | `:player:miniplayer` | `miniplayer` | Combined state | Player | |
| `player/miniplayer/.../ui/MiniPlayerOverlay.kt` | `:player:miniplayer` | `miniplayer.ui` | Overlay composable | Player | |
| `player/miniplayer/.../di/MiniPlayerModule.kt` | `:player:miniplayer` | `miniplayer.di` | Hilt DI module | Player | |

---

## Naming Issues Found

### ✅ Fixed Issues

| Location | Issue | Status |
|----------|-------|--------|
| `pipeline/telegram/feature/` | Package named `feature` in pipeline module | ✅ **FIXED** → `capability/` |
| `TelegramFullHistoryFeatureProvider` | Class named `*FeatureProvider` in pipeline | ✅ **FIXED** → `*CapabilityProvider` |
| `TelegramLazyThumbnailsFeatureProvider` | Class named `*FeatureProvider` in pipeline | ✅ **FIXED** → `*CapabilityProvider` |
| `TelegramFeatureModule` | Hilt module named `*FeatureModule` in pipeline | ✅ **FIXED** → `TelegramCapabilityModule` |

### Completed Alignments

| Location | Previous | New | Status |
|----------|----------|-----|--------|
| `pipeline/xtream/catalog/XtreamCatalogMapper.kt` | In `catalog/` package | Moved to `mapper/` | ✅ **DONE** |
| `pipeline/xtream/model/XtreamRawMetadataExtensions.kt` | In `model/` package | Moved to `mapper/` | ✅ **DONE** |
| `pipeline/xtream/model/XtreamImageRefExtensions.kt` | In `model/` package | Moved to `mapper/` | ✅ **DONE** |

---

## Summary Statistics

| Category | Count | Description |
|----------|-------|-------------|
| AppShell | 8 | Application entry point, navigation, theme (`app-v2`) |
| AppFeature | 10 | User-facing features (`feature/*` modules only) |
| CoreModel | 52 | Core data models and contracts (`core/*`) |
| MetadataNormalizer | 12 | Metadata normalization (`core/metadata-normalizer`) |
| Pipeline | 45 | Pipeline catalog, adapter, model (`pipeline/*`) |
| PipelineCapability | 4 | Pipeline capability providers (`pipeline/*/capability/`) |
| Playback | 20 | Playback sources and domain (`playback/*`) |
| Player | 17 | Internal player and MiniPlayer (`player/*`) |
| InfraTransport | 16 | Transport layer (`infra/transport-*`) |
| InfraData | 10 | Data/repository layer (`infra/data-*`) |
| Logging | 4 | Logging infrastructure (`infra/logging`) |
| Tooling | 3 | Debug and development tools |
| **Total** | **~233** | |
