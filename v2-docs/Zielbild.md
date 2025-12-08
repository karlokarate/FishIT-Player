
* Canonical Media & Normalizer
* Pipelines: Telegram, Xtream, Audiobook, IO
* Internal Player / Playback
* Imaging / Coil3
* Logging & Telemetry
* Cache & Storage
* Settings (global & pipeline-spezifisch)
* FFMpegKit / Tools
* UI-Features (Home, Library, Live, Detail, Telegram, Settings, Audiobooks)

Ich baue dir das als **Baum-Übersichten** auf, so dass du:

* Feature-IDs,
* Owner-Module,
* Provider-Standorte,
* Feature-Doku-Slots

auf einen Blick siehst.

---

## 1. Globale Feature-Struktur – „Kategorien-Baum“

```text
FeatureCatalog
├─ canonical-media
│  ├─ media.canonical_model
│  ├─ media.normalize
│  └─ media.resolve.tmdb
├─ pipelines
│  ├─ telegram
│  │  ├─ telegram.full_history_streaming
│  │  ├─ telegram.lazy_thumbnails
│  │  ├─ telegram.vod_playback
│  │  ├─ telegram.clip_playback
│  │  └─ telegram.chat_metadata_export
│  ├─ xtream
│  │  ├─ xtream.live_streaming
│  │  ├─ xtream.vod_playback
│  │  ├─ xtream.series_metadata
│  │  ├─ xtream.epg_integration
│  │  ├─ xtream.catchup_playback
│  │  └─ xtream.server_capabilities_discovery
│  ├─ audiobook
│  │  ├─ audiobook.library_import
│  │  ├─ audiobook.progress_tracking
│  │  └─ audiobook.chapter_metadata
│  └─ io
│     ├─ io.m3u_import
│     ├─ io.local_file_scan
│     └─ io.playlist_normalization
├─ playback / internal-player
│  ├─ playback.vod
│  ├─ playback.live
│  ├─ playback.trickplay
│  ├─ playback.subtitles_cc
│  ├─ playback.tv_input_integration
│  └─ playback.telemetry
├─ imaging
│  ├─ image.source.poster
│  ├─ image.source.backdrop
│  ├─ image.source.thumb
│  ├─ image.pipeline.telegram
│  └─ image.pipeline.xtream
├─ logging & telemetry
│  ├─ infra.logging.unified
│  ├─ infra.logging.log_viewer
│  ├─ infra.telemetry.player_stats
│  ├─ infra.telemetry.pipeline_events
│  └─ infra.telemetry.ui_jank
├─ cache & storage
│  ├─ app.cache_management
│  ├─ cache.logs
│  ├─ cache.telegram
│  ├─ cache.xtream
│  └─ cache.ffmpegkit
├─ settings & config
│  ├─ settings.core_single_datastore
│  ├─ settings.player_defaults
│  ├─ settings.telegram_advanced
│  ├─ settings.xtream_accounts
│  ├─ settings.audiobook_preferences
│  └─ settings.debug_diagnostics
├─ ffmpegkit & media-tools
│  ├─ ffmpegkit.transcode
│  ├─ ffmpegkit.waveform_analysis
│  └─ ffmpegkit.thumbnail_extraction
├─ ui/screens
│  ├─ ui.screen.home
│  ├─ ui.screen.library
│  ├─ ui.screen.live
│  ├─ ui.screen.detail
│  ├─ ui.screen.telegram
│  ├─ ui.screen.settings
│  └─ ui.screen.audiobooks
└─ app shell
   ├─ app.navigation
   ├─ app.feature_registry
   └─ app.startup_diagnostics
```

Das ist der **logische Feature-Katalog** – jetzt mappe ich das auf Module, Provider & Doku.

---

## 2. Modul-Owner-Baum – Wer „besitzt“ welche Features?

### 2.1 Core & Canonical Media

```text
core/
├─ canonical-media/     (Owner: "core:canonical-media")
│  ├─ media.canonical_model
│  └─ media.normalize   (Basis-Schnittstellen)
├─ metadata-normalizer/ (Owner: "core:metadata-normalizer")
│  ├─ media.normalize        (konkrete Implementierungen)
│  └─ media.resolve.tmdb
├─ logging/             (Owner: "core:logging")
│  └─ infra.logging.unified
└─ telemetry/           (Owner: "core:telemetry")
   ├─ infra.telemetry.player_stats
   ├─ infra.telemetry.pipeline_events
   └─ infra.telemetry.ui_jank
```

### 2.2 Infra (Imageloader, Cache, Settings, FFMpegKit)

```text
infra/
├─ imageloader/         (Owner: "infra:imageloader")
│  ├─ image.source.poster
│  ├─ image.source.backdrop
│  ├─ image.source.thumb
│  ├─ image.pipeline.telegram
│  └─ image.pipeline.xtream
├─ cache/               (Owner: "infra:cache")
│  ├─ app.cache_management
│  ├─ cache.logs
│  ├─ cache.telegram
│  ├─ cache.xtream
│  └─ cache.ffmpegkit
├─ settings/            (Owner: "infra:settings")
│  ├─ settings.core_single_datastore
│  ├─ settings.player_defaults
│  └─ settings.debug_diagnostics
└─ ffmpegkit/           (Owner: "infra:ffmpegkit")
   ├─ ffmpegkit.transcode
   ├─ ffmpegkit.waveform_analysis
   └─ ffmpegkit.thumbnail_extraction
```

### 2.3 Pipelines

```text
pipeline/
├─ telegram/            (Owner: "pipeline-telegram")
│  ├─ telegram.full_history_streaming
│  ├─ telegram.lazy_thumbnails
│  ├─ telegram.vod_playback
│  ├─ telegram.clip_playback
│  └─ telegram.chat_metadata_export
├─ xtream/              (Owner: "pipeline-xtream")
│  ├─ xtream.live_streaming
│  ├─ xtream.vod_playback
│  ├─ xtream.series_metadata
│  ├─ xtream.epg_integration
│  ├─ xtream.catchup_playback
│  └─ xtream.server_capabilities_discovery
├─ audiobook/           (Owner: "pipeline-audiobook")
│  ├─ audiobook.library_import
│  ├─ audiobook.progress_tracking
│  └─ audiobook.chapter_metadata
└─ io/                  (Owner: "pipeline-io")
   ├─ io.m3u_import
   ├─ io.local_file_scan
   └─ io.playlist_normalization
```

### 2.4 Player & Playback

```text
player/
└─ internal/            (Owner: "player:internal")
   ├─ playback.vod
   ├─ playback.live
   ├─ playback.trickplay
   ├─ playback.subtitles_cc
   ├─ playback.tv_input_integration
   └─ playback.telemetry

playback/
└─ domain/              (Owner: "playback:domain")
   ├─ mapping zwischen Features und SIP-API
   └─ Feature-basierte Policies (z.B. wie Resume funktioniert)
```

### 2.5 UI-Feature-Module

```text
feature/
├─ home/                (Owner: "feature:home")
│  ├─ ui.screen.home
│  └─ AppEntryPoints (z.B. Recent, Continue Watching)
├─ library/             (Owner: "feature:library")
│  └─ ui.screen.library
├─ live/                (Owner: "feature:live")
│  └─ ui.screen.live
├─ detail/              (Owner: "feature:detail")
│  └─ ui.screen.detail
├─ telegram-media/      (Owner: "feature:telegram-media")
│  └─ ui.screen.telegram
├─ settings/            (Owner: "feature:settings")
│  ├─ ui.screen.settings
│  ├─ settings.telegram_advanced
│  ├─ settings.xtream_accounts
│  └─ settings.audiobook_preferences
└─ audiobooks/          (Owner: "feature:audiobooks")
   └─ ui.screen.audiobooks
```

### 2.6 App-Shell

```text
app-v2/
└─ src/main/kotlin/com/fishit/player/app/
   ├─ App.kt                 (Application)
   ├─ navigation/            (Owner: "app-v2:navigation")
   │  └─ app.navigation
   ├─ feature/
   │  └─ AppFeatureRegistry.kt   (Owner: "app-v2:feature-registry")
   │     └─ app.feature_registry
   └─ diagnostics/           (Owner: "app-v2:diagnostics")
      └─ app.startup_diagnostics
```

---

## 3. Provider-Bäume – Wo die FeatureProvider-Klassen liegen

### 3.1 Zentrales Feature-API-Modul (für IDs & Basis)

```text
core/feature-api/
└─ src/main/kotlin/com/fishit/player/core/feature/
   ├─ FeatureId.kt
   ├─ FeatureScope.kt
   ├─ FeatureOwner.kt
   ├─ FeatureProvider.kt
   ├─ FeatureRegistry.kt
   ├─ Features.kt          # IDs hier gruppiert nach Kategorien
   └─ catalog/
      ├─ FeatureDescriptor.kt
      ├─ FeatureCatalog.kt
      └─ FeatureCatalogGenerator.kt (optional, für Tools)
```

### 3.2 Beispiel: Telegram-Feature-Provider

```text
pipeline/telegram/
└─ src/main/kotlin/com/fishit/player/pipeline/telegram/
   ├─ feature/
   │  ├─ TelegramFullHistoryFeatureProvider.kt
   │  ├─ TelegramLazyThumbnailsFeatureProvider.kt
   │  ├─ TelegramVodPlaybackFeatureProvider.kt
   │  ├─ TelegramClipPlaybackFeatureProvider.kt
   │  └─ TelegramChatMetadataExportFeatureProvider.kt
   ├─ history/
   │  ├─ TelegramHistoryScanner.kt
   │  └─ TelegramHistoryRepository.kt
   └─ thumbs/
      ├─ TelegramThumbKeyFactory.kt
      ├─ TelegramThumbFetcher.kt
      └─ TelegramThumbCachePolicy.kt
```

### 3.3 Beispiel: Xtream-Feature-Provider

```text
pipeline/xtream/
└─ src/main/kotlin/com/fishit/player/pipeline/xtream/
   ├─ feature/
   │  ├─ XtreamLiveStreamingFeatureProvider.kt
   │  ├─ XtreamVodPlaybackFeatureProvider.kt
   │  ├─ XtreamSeriesMetadataFeatureProvider.kt
   │  ├─ XtreamEpgIntegrationFeatureProvider.kt
   │  ├─ XtreamCatchupPlaybackFeatureProvider.kt
   │  └─ XtreamServerCapabilitiesFeatureProvider.kt
   ├─ api/
   │  ├─ XtreamApiClient.kt
   │  └─ XtreamEndpoints.kt
   ├─ repository/
   │  ├─ XtreamChannelRepository.kt
   │  └─ XtreamVodRepository.kt
   └─ mapping/
      └─ XtreamToRawMediaMapper.kt   # erzeugt RawMediaMetadata
```

### 3.4 Beispiel: Logging & Telemetry Provider

```text
core/logging/
└─ src/main/kotlin/com/fishit/player/core/logging/
   ├─ UnifiedLogger.kt
   └─ features/
      └─ UnifiedLoggingFeatureProvider.kt      # infra.logging.unified

core/telemetry/
└─ src/main/kotlin/com/fishit/player/core/telemetry/
   ├─ PlayerStatsTelemetry.kt
   ├─ PipelineEventTelemetry.kt
   ├─ UiJankTelemetry.kt
   └─ features/
      ├─ PlayerStatsFeatureProvider.kt        # infra.telemetry.player_stats
      ├─ PipelineEventsFeatureProvider.kt     # infra.telemetry.pipeline_events
      └─ UiJankFeatureProvider.kt             # infra.telemetry.ui_jank
```

### 3.5 Beispiel: Imaging / Coil3 Provider

```text
infra/imageloader/
└─ src/main/kotlin/com/fishit/player/infra/imageloader/
   ├─ GlobalImageLoader.kt               # Coil3 instance
   ├─ TelegramImageSource.kt             # binds telegram thumb/poster URLs → ImageRequest
   ├─ XtreamImageSource.kt
   └─ features/
      ├─ ImagePosterSourceFeatureProvider.kt      # image.source.poster
      ├─ ImageBackdropSourceFeatureProvider.kt    # image.source.backdrop
      ├─ ImageThumbSourceFeatureProvider.kt       # image.source.thumb
      ├─ TelegramImagePipelineFeatureProvider.kt  # image.pipeline.telegram
      └─ XtreamImagePipelineFeatureProvider.kt    # image.pipeline.xtream
```

### 3.6 Beispiel: Cache-Management Provider

```text
infra/cache/
└─ src/main/kotlin/com/fishit/player/infra/cache/
   ├─ CacheManager.kt
   ├─ LogCache.kt
   ├─ TelegramCache.kt
   ├─ XtreamCache.kt
   └─ features/
      ├─ AppCacheManagementFeatureProvider.kt  # app.cache_management
      ├─ LogCacheFeatureProvider.kt            # cache.logs
      ├─ TelegramCacheFeatureProvider.kt       # cache.telegram
      ├─ XtreamCacheFeatureProvider.kt         # cache.xtream
      └─ FfmpegCacheFeatureProvider.kt         # cache.ffmpegkit
```

---

## 4. Feature-Doku-Baum (`docs/v2/features/**`) – inklusive Xtream, Logging, etc

### 4.1 Canonical Media & Normalizer

```text
docs/v2/features/canonical-media/
├─ FEATURE_media.canonical_model.md
└─ FEATURE_media.normalize.md

docs/v2/features/metadata/
└─ FEATURE_media.resolve.tmdb.md
```

### 4.2 Pipelines

```text
docs/v2/features/telegram/
├─ FEATURE_telegram.full_history_streaming.md
├─ FEATURE_telegram.lazy_thumbnails.md
├─ FEATURE_telegram.vod_playback.md
├─ FEATURE_telegram.clip_playback.md
└─ FEATURE_telegram.chat_metadata_export.md

docs/v2/features/xtream/
├─ FEATURE_xtream.live_streaming.md
├─ FEATURE_xtream.vod_playback.md
├─ FEATURE_xtream.series_metadata.md
├─ FEATURE_xtream.epg_integration.md
├─ FEATURE_xtream.catchup_playback.md
└─ FEATURE_xtream.server_capabilities_discovery.md

docs/v2/features/audiobook/
├─ FEATURE_audiobook.library_import.md
├─ FEATURE_audiobook.progress_tracking.md
└─ FEATURE_audiobook.chapter_metadata.md

docs/v2/features/io/
├─ FEATURE_io.m3u_import.md
├─ FEATURE_io.local_file_scan.md
└─ FEATURE_io.playlist_normalization.md
```

### 4.3 Playback / Player

```text
docs/v2/features/playback/
├─ FEATURE_playback.vod.md
├─ FEATURE_playback.live.md
├─ FEATURE_playback.trickplay.md
├─ FEATURE_playback.subtitles_cc.md
├─ FEATURE_playback.tv_input_integration.md
└─ FEATURE_playback.telemetry.md
```

### 4.4 Imaging

```text
docs/v2/features/imaging/
├─ FEATURE_image.source.poster.md
├─ FEATURE_image.source.backdrop.md
├─ FEATURE_image.source.thumb.md
├─ FEATURE_image.pipeline.telegram.md
└─ FEATURE_image.pipeline.xtream.md
```

### 4.5 Logging & Telemetry

```text
docs/v2/features/logging/
├─ FEATURE_infra.logging.unified.md
└─ FEATURE_infra.logging.log_viewer.md

docs/v2/features/telemetry/
├─ FEATURE_infra.telemetry.player_stats.md
├─ FEATURE_infra.telemetry.pipeline_events.md
└─ FEATURE_infra.telemetry.ui_jank.md
```

### 4.6 Cache & Storage

```text
docs/v2/features/cache/
├─ FEATURE_app.cache_management.md
├─ FEATURE_cache.logs.md
├─ FEATURE_cache.telegram.md
├─ FEATURE_cache.xtream.md
└─ FEATURE_cache.ffmpegkit.md
```

### 4.7 Settings & Config

```text
docs/v2/features/settings/
├─ FEATURE_settings.core_single_datastore.md
├─ FEATURE_settings.player_defaults.md
├─ FEATURE_settings.telegram_advanced.md
├─ FEATURE_settings.xtream_accounts.md
├─ FEATURE_settings.audiobook_preferences.md
└─ FEATURE_settings.debug_diagnostics.md
```

### 4.8 FFMpegKit / Tools

```text
docs/v2/features/ffmpegkit/
├─ FEATURE_ffmpegkit.transcode.md
├─ FEATURE_ffmpegkit.waveform_analysis.md
└─ FEATURE_ffmpegkit.thumbnail_extraction.md
```

### 4.9 UI Screens

```text
docs/v2/features/ui/
├─ FEATURE_ui.screen.home.md
├─ FEATURE_ui.screen.library.md
├─ FEATURE_ui.screen.live.md
├─ FEATURE_ui.screen.detail.md
├─ FEATURE_ui.screen.telegram.md
├─ FEATURE_ui.screen.settings.md
└─ FEATURE_ui.screen.audiobooks.md
```

---

## 5. Wie alles zusammenklickt (kurze Zusammenfassung)

* **FeatureId & Features.kt**
  – alle IDs zentral, keine freien Strings; gruppiert nach Kategorien.

* **Owner pro Modul**
  – Core, Infra, Pipelines, Player, UI, App-Shell – jeder besitzt klar definierte Features.

* **FeatureProvider pro Modul**
  – liegen in `.../features/`-Packages innerhalb der jeweiligen Module.

* **FeatureRegistry in `app-v2`**
  – sammelt alle Provider, beantwortet `isSupported()`, `providersFor()`, `ownerOf()`.

* **Docs unter `docs/v2/features/**`**
  – 1:1 Spiegelung der Feature-IDs; alles, was wichtig ist (Xtream, Logging, Cache, FFMpegKit, Settings etc.), hat ein eigenes Contract-File.
