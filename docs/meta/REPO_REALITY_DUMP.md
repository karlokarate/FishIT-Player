# Repo Reality Dump (v2)

> **Generated:** December 19, 2025  
> **Last Updated:** December 20, 2025  
> **Branch:** `architecture/v2-bootstrap`  
> **Purpose:** Capture exact current state of v2 wiring: modules, DI, UI, sync, workers, pipelines, normalizer, persistence

---

## 🔍 Related Audit Documentation

- **SSOT Catalog Engine Audit:** [`SSOT_CATALOG_ENGINE_AUDIT_SUMMARY.md`](./SSOT_CATALOG_ENGINE_AUDIT_SUMMARY.md)
  - Complete audit of all SSOT work names, TMDB isolation, worker compliance, contract conformance
  - **Status:** ✅ All audits passed (2025-12-20)

---

## 1. Modules & Gradle

### 1.1 settings.gradle.kts Includes

```kotlin
// ========== v2 Modules ==========

// App Entry
include(":app-v2")

// Core
include(":core:model")
include(":core:player-model")
include(":core:feature-api")
include(":core:persistence")
include(":core:metadata-normalizer")
include(":core:catalog-sync")
include(":core:firebase")
include(":core:ui-imaging")
include(":core:ui-theme")
include(":core:ui-layout")
include(":core:app-startup")

// Playback & Player
include(":playback:domain")
include(":playback:telegram")
include(":playback:xtream")
include(":player:ui")
include(":player:ui-api")
include(":player:internal")
include(":player:miniplayer")
include(":player:nextlib-codecs")

// Pipelines (no UI)
include(":pipeline:telegram")
include(":pipeline:xtream")
include(":pipeline:io")
include(":pipeline:audiobook")

// Feature Shells (UI)
include(":feature:home")
include(":feature:library")
include(":feature:live")
include(":feature:detail")
include(":feature:telegram-media")
include(":feature:audiobooks")
include(":feature:settings")
include(":feature:onboarding")

// Infrastructure
include(":infra:logging")
include(":infra:tooling")
include(":infra:transport-telegram")
include(":infra:transport-xtream")
include(":infra:data-telegram")
include(":infra:data-xtream")
include(":infra:data-home")
include(":infra:imaging")
include(":infra:work")

// Tools (JVM CLI, no Android)
include(":tools:pipeline-cli")
```

### 1.2 Root build.gradle.kts Key Plugins

| Plugin | Version |
|--------|---------|
| com.android.application | 8.6.1 |
| com.android.library | 8.6.1 |
| kotlin("android") | 2.1.0 |
| kotlin("plugin.compose") | 2.1.0 |
| com.google.devtools.ksp | 2.1.0-1.0.29 |
| com.google.dagger.hilt.android | 2.56.1 |
| io.gitlab.arturbosch.detekt | 1.23.8 |
| org.jlleitschuh.gradle.ktlint | 12.1.2 |

### 1.3 libs.versions.toml

```toml
[versions]
tmdbApi = "1.6.0"

[libraries]
# ONLY used in :core:metadata-normalizer per TMDB_ENRICHMENT_CONTRACT.md
tmdb-api = { module = "app.moviebase:tmdb-api", version.ref = "tmdbApi" }
```

---

## 2. App Entry

### 2.1 Application Class

**File:** `app-v2/src/main/java/com/fishit/player/v2/FishItV2Application.kt`

```kotlin
@HiltAndroidApp
class FishItV2Application :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    
    @Inject lateinit var imageLoaderProvider: Provider<ImageLoader>
    @Inject lateinit var workConfiguration: Configuration
    @Inject lateinit var xtreamSessionBootstrap: XtreamSessionBootstrap
    @Inject lateinit var catalogSyncBootstrap: CatalogSyncBootstrap
    @Inject lateinit var telegramActivationObserver: TelegramActivationObserver
    @Inject lateinit var sourceActivationObserver: SourceActivationObserver

    override fun onCreate() {
        super.onCreate()
        // Initialize unified logging system FIRST
        UnifiedLogInitializer.init(isDebug = BuildConfig.DEBUG)
        // Start source activation observers
        sourceActivationObserver.start(appScope)
        telegramActivationObserver.start()
        // Start session bootstraps
        xtreamSessionBootstrap.start()
        catalogSyncBootstrap.start()
    }
    
    override fun newImageLoader(context: PlatformContext): ImageLoader = 
        imageLoaderProvider.get()

    override val workManagerConfiguration: Configuration
        get() = workConfiguration
}
```

### 2.2 MainActivity

**File:** `app-v2/src/main/java/com/fishit/player/v2/MainActivity.kt`

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var catalogSyncBootstrap: CatalogSyncBootstrap
    @Inject lateinit var xtreamSessionBootstrap: XtreamSessionBootstrap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        xtreamSessionBootstrap.start()
        setContent {
            FishItV2Theme {
                Surface(...) {
                    AppNavHost(catalogSyncBootstrap = catalogSyncBootstrap)
                }
            }
        }
    }
}
```

### 2.3 Navigation

**File:** `app-v2/src/main/java/com/fishit/player/v2/navigation/AppNavHost.kt`

- **Start Destination:** `Routes.START` (onboarding)
- **Main Routes:** START → HOME → DETAIL → PLAYER

```kotlin
@Composable
fun AppNavHost(catalogSyncBootstrap: CatalogSyncBootstrap) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.START,
    ) {
        composable(Routes.START) { StartScreen(...) }
        composable(Routes.HOME) { HomeScreen(...) }
        composable(Routes.DETAIL_PATTERN) { DetailScreen(...) }
        composable(Routes.PLAYER_PATTERN) { PlayerNavScreen(...) }
        composable(Routes.DEBUG) { DebugScreen(...) }
        // ...
    }
}
```

---

## 3. UI Overview

### 3.1 FishTile

**File:** `core/ui-layout/src/main/java/com/fishit/player/core/ui/layout/FishTile.kt`

Primary content tile for media items with:

- Poster/thumbnail image via `FishImage` / `FishImageTiered`
- Source frame (colored border for Telegram/Xtream/Local)
- Progress bar (resumeFraction)
- Title overlay
- Focus scaling and glow effects
- DPAD-focusable via `tvClickable`

### 3.2 FishRow

**File:** `core/ui-layout/src/main/java/com/fishit/player/core/ui/layout/FishRow.kt`

Horizontal scrolling row with:

- `FishRowHeader` (title + count)
- `LazyRow` with `focusGroup()`
- `FishRowSimple` variant with direct tile generation
- `FishRowEmpty` for empty states

### 3.3 HomeScreen

**File:** `feature/home/src/main/java/com/fishit/player/feature/home/HomeScreen.kt`

```kotlin
@Composable
fun HomeScreen(
    onItemClick: (HomeMediaItem) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    // Renders MediaRow for each category:
    // - Continue Watching
    // - Recently Added
    // - Telegram Media (FishColors.SourceTelegram)
    // - Xtream VOD (FishColors.SourceXtream)
    // - Xtream Series
    // - Xtream Live
}
```

### 3.4 HomeViewModel

**File:** `feature/home/src/main/java/com/fishit/player/feature/home/HomeViewModel.kt`

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeContentRepository: HomeContentRepository
) : ViewModel() {
    // Aggregates flows from homeContentRepository:
    // - observeTelegramMedia()
    // - observeXtreamLive()
    // - observeXtreamVod()
    // - observeXtreamSeries()
}
```

### 3.5 HomeContentRepository Interface

**File:** `feature/home/src/main/java/com/fishit/player/feature/home/domain/HomeContentRepository.kt`

```kotlin
interface HomeContentRepository {
    fun observeTelegramMedia(): Flow<List<HomeMediaItem>>
    fun observeXtreamLive(): Flow<List<HomeMediaItem>>
    fun observeXtreamVod(): Flow<List<HomeMediaItem>>
    fun observeXtreamSeries(): Flow<List<HomeMediaItem>>
}
```

**Implementation:** `infra/data-home`

---

## 4. Feature System

### 4.1 FeatureId Definitions

**File:** `core/feature-api/src/main/kotlin/com/fishit/player/core/feature/Features.kt`

| Object | Feature IDs |
|--------|-------------|
| `CanonicalMediaFeatures` | `CANONICAL_MODEL`, `NORMALIZE`, `RESOLVE_TMDB` |
| `TelegramFeatures` | `FULL_HISTORY_STREAMING`, `LAZY_THUMBNAILS` |
| `XtreamFeatures` | `LIVE_STREAMING`, `VOD_PLAYBACK`, `SERIES_METADATA` |
| `AppFeatures` | `CACHE_MANAGEMENT` |
| `LoggingFeatures` | `UNIFIED_LOGGING` |
| `SettingsFeatures` | `CORE_SINGLE_DATASTORE` |
| `UiFeatures` | `SCREEN_HOME`, `SCREEN_LIBRARY`, `SCREEN_TELEGRAM`, `SCREEN_SETTINGS`, `SCREEN_LIVE`, `SCREEN_AUDIOBOOKS` |

### 4.2 FeatureRegistry

**File:** `app-v2/src/main/java/com/fishit/player/v2/feature/AppFeatureRegistry.kt`

```kotlin
@Singleton
class AppFeatureRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards FeatureProvider>,
) : FeatureRegistry {
    private val providersById = providers.groupBy { it.featureId }
    override fun isSupported(featureId: FeatureId): Boolean = 
        providersById.containsKey(featureId)
}
```

### 4.3 FeatureModule (DI)

**File:** `app-v2/src/main/java/com/fishit/player/v2/feature/di/FeatureModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureModule {
    @Multibinds
    abstract fun featureProviders(): Set<FeatureProvider>
    
    companion object {
        @Provides @Singleton
        fun provideFeatureRegistry(providers: Set<...>): FeatureRegistry = 
            AppFeatureRegistry(providers)
    }
}
```

### 4.4 CapabilityProvider Classes

| File | Class |
|------|-------|
| `pipeline/telegram/...capability/TelegramFullHistoryCapabilityProvider.kt` | `TelegramFullHistoryCapabilityProvider` |
| `pipeline/telegram/...capability/TelegramLazyThumbnailsCapabilityProvider.kt` | `TelegramLazyThumbnailsCapabilityProvider` |

---

## 5. Sync & Workers

### 5.1 All Worker Classes

| Worker | File | Purpose |
|--------|------|---------|
| `CatalogSyncOrchestratorWorker` | `app-v2/.../work/CatalogSyncOrchestratorWorker.kt` | Builds/enqueues deterministic worker chain |
| `XtreamPreflightWorker` | `app-v2/.../work/XtreamPreflightWorker.kt` | Xtream auth validation |
| `XtreamCatalogScanWorker` | `app-v2/.../work/XtreamCatalogScanWorker.kt` | Xtream sync via CatalogSyncService |
| `TelegramAuthPreflightWorker` | `app-v2/.../work/TelegramAuthPreflightWorker.kt` | Telegram auth check |
| `TelegramFullHistoryScanWorker` | `app-v2/.../work/TelegramFullHistoryScanWorker.kt` | Full history sync via CatalogSyncService |
| `TelegramIncrementalScanWorker` | `app-v2/.../work/TelegramIncrementalScanWorker.kt` | Incremental sync via CatalogSyncService |
| `IoQuickScanWorker` | `app-v2/.../work/IoQuickScanWorker.kt` | Local IO scan (stub) |
| `TmdbEnrichmentOrchestratorWorker` | `app-v2/.../work/TmdbEnrichmentOrchestratorWorker.kt` | TMDB enrichment entry point |
| `TmdbEnrichmentBatchWorker` | `app-v2/.../work/TmdbEnrichmentBatchWorker.kt` | Process TMDB batches via TmdbMetadataResolver |
| `TmdbEnrichmentContinuationWorker` | `app-v2/.../work/TmdbEnrichmentContinuationWorker.kt` | Schedule next TMDB batch |

### 5.2 Unique Work Names

| Constant | Value |
|----------|-------|
| `WORK_NAME_CATALOG_SYNC` | `"catalog_sync_global"` |
| `WORK_NAME_TMDB_ENRICHMENT` | `"tmdb_enrichment_global"` |

### 5.3 Worker Tags (W-12)

```kotlin
// Base
const val TAG_CATALOG_SYNC = "catalog_sync"

// Source tags
const val TAG_SOURCE_XTREAM = "source_xtream"
const val TAG_SOURCE_TELEGRAM = "source_telegram"
const val TAG_SOURCE_IO = "source_io"
const val TAG_SOURCE_TMDB = "source_tmdb"

// Mode tags
const val TAG_MODE_AUTO = "mode_auto"
const val TAG_MODE_EXPERT_NOW = "mode_expert_sync_now"
const val TAG_MODE_FORCE_RESCAN = "mode_expert_force_rescan"

// Worker tags
const val TAG_WORKER_ORCHESTRATOR = "worker/CatalogSyncOrchestratorWorker"
const val TAG_WORKER_TMDB_ORCHESTRATOR = "worker/TmdbEnrichmentOrchestratorWorker"
// ... etc
```

### 5.4 InputData Keys (W-13, W-14)

```kotlin
// Common
const val KEY_SYNC_RUN_ID = "sync_run_id"
const val KEY_SYNC_MODE = "sync_mode"
const val KEY_ACTIVE_SOURCES = "active_sources"
const val KEY_WIFI_ONLY = "wifi_only"
const val KEY_MAX_RUNTIME_MS = "max_runtime_ms"
const val KEY_DEVICE_CLASS = "device_class"

// TMDB-specific
const val KEY_TMDB_SCOPE = "tmdb_scope"
const val KEY_TMDB_FORCE_REFRESH = "tmdb_force_refresh"
const val KEY_TMDB_BATCH_SIZE_HINT = "tmdb_batch_size_hint"
const val KEY_TMDB_BATCH_CURSOR = "tmdb_batch_cursor"
```

### 5.5 WorkManager Enqueue Locations

| File | Method |
|------|--------|
| `CatalogSyncWorkSchedulerImpl.kt` | `schedule()` via `WorkManager.enqueueUniqueWork()` |
| `CatalogSyncOrchestratorWorker.kt` | `workManager.beginUniqueWork()` for child chains |
| `TmdbEnrichmentSchedulerImpl.kt` | `WorkManager.enqueueUniqueWork()` |
| `TmdbEnrichmentOrchestratorWorker.kt` | `WorkManager.enqueueUniqueWork()` |
| `TmdbEnrichmentBatchWorker.kt` | `WorkManager.enqueueUniqueWork()` |
| `TmdbEnrichmentContinuationWorker.kt` | `WorkManager.enqueueUniqueWork()` |

---

## 6. CatalogSyncService

### 6.1 Interface

**File:** `core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/CatalogSyncContract.kt`

```kotlin
interface CatalogSyncService {
    fun syncTelegram(
        chatIds: List<Long>? = null,
        syncConfig: SyncConfig = SyncConfig.DEFAULT,
    ): Flow<SyncStatus>

    fun syncXtream(
        includeVod: Boolean = true,
        includeSeries: Boolean = true,
        includeEpisodes: Boolean = true,
        includeLive: Boolean = true,
        syncConfig: SyncConfig = SyncConfig.DEFAULT,
    ): Flow<SyncStatus>

    suspend fun clearSource(source: String)
}
```

### 6.2 Implementation

**File:** `core/catalog-sync/src/main/java/com/fishit/player/core/catalogsync/DefaultCatalogSyncService.kt`

```kotlin
@Singleton
class DefaultCatalogSyncService @Inject constructor(
    private val telegramPipeline: TelegramCatalogPipeline,
    private val xtreamPipeline: XtreamCatalogPipeline,
    private val telegramRepository: TelegramContentRepository,
    private val xtreamCatalogRepository: XtreamCatalogRepository,
    private val xtreamLiveRepository: XtreamLiveRepository,
    private val normalizer: MediaMetadataNormalizer,
    private val canonicalMediaRepository: CanonicalMediaRepository,
) : CatalogSyncService
```

### 6.3 Injection Points

| File | Injected Into |
|------|---------------|
| `XtreamCatalogScanWorker.kt` | `catalogSyncService.syncXtream()` |
| `TelegramFullHistoryScanWorker.kt` | `catalogSyncService.syncTelegram()` |
| `TelegramIncrementalScanWorker.kt` | `catalogSyncService.syncTelegram()` |

---

## 7. Telegram Pipeline

### 7.1 Pipeline Interface

**File:** `pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/catalog/TelegramCatalogContract.kt`

```kotlin
interface TelegramCatalogPipeline {
    fun scanCatalog(config: TelegramCatalogConfig): Flow<TelegramCatalogEvent>
    fun liveMediaUpdates(config: TelegramLiveUpdatesConfig): Flow<TelegramCatalogEvent>
}
```

### 7.2 Implementation

**File:** `pipeline/telegram/.../TelegramCatalogPipelineImpl.kt`

### 7.3 Event Types

```kotlin
sealed interface TelegramCatalogEvent {
    data class ItemDiscovered(val item: TelegramCatalogItem)
    data class ScanStarted(val chatCount: Int, val estimatedTotalMessages: Long?)
    data class ScanProgress(...)
    data class ScanCompleted(...)
    data class ScanCancelled(...)
    data class ScanError(...)
}
```

### 7.4 toRawMediaMetadata()

**File:** `pipeline/telegram/.../model/TelegramRawMetadataExtensions.kt`

```kotlin
fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata {
    val rawTitle = extractRawTitle()
    val effectiveYear = structuredYear ?: year
    val effectiveDurationMs = structuredLengthMinutes?.toLong() * 60_000L 
        ?: durationSecs?.toLong() * 1000L
    val externalIds = buildExternalIds() // structuredTmdbId → TmdbRef
    
    return RawMediaMetadata(
        originalTitle = rawTitle,
        mediaType = mapTelegramMediaType(),
        year = effectiveYear,
        externalIds = externalIds,
        sourceType = SourceType.TELEGRAM,
        pipelineIdTag = PipelineIdTag.TELEGRAM,
        poster = toPosterImageRef(),
        thumbnail = toThumbnailImageRef(),
        placeholderThumbnail = toMinithumbnailImageRef(),
        // ...
    )
}
```

### 7.5 Structured Bundles

**Files:**

- `pipeline/telegram/.../model/TelegramBundleType.kt` - Bundle type enum
- `pipeline/telegram/.../grouper/TelegramMessageBundler.kt` - Message bundling logic
- `pipeline/telegram/.../mapper/TelegramBundleToMediaItemMapper.kt` - Bundle → MediaItem

Bundle types: `SINGLE`, `PARTIAL_2ER`, `FULL_3ER`

---

## 8. Xtream Pipeline

### 8.1 Pipeline Interface

**File:** `pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/catalog/XtreamCatalogContract.kt`

```kotlin
interface XtreamCatalogPipeline {
    fun scanCatalog(config: XtreamCatalogConfig): Flow<XtreamCatalogEvent>
}
```

### 8.2 Event Types

```kotlin
sealed interface XtreamCatalogEvent {
    data class ItemDiscovered(val item: XtreamCatalogItem, val kind: XtreamItemKind)
    data class ScanStarted(...)
    data class ScanProgress(...)
    data class ScanCompleted(...)
    data class ScanError(...)
}

enum class XtreamItemKind { VOD, SERIES, EPISODE, LIVE }
```

### 8.3 toRawMediaMetadata() Extensions

**File:** `pipeline/xtream/.../mapper/XtreamRawMetadataExtensions.kt`

```kotlin
fun XtreamVodItem.toRawMediaMetadata(authHeaders: Map<String, String>): RawMediaMetadata
fun XtreamSeriesItem.toRawMediaMetadata(authHeaders: Map<String, String>): RawMediaMetadata
fun XtreamEpisode.toRawMediaMetadata(seriesName: String, authHeaders: Map<String, String>): RawMediaMetadata
fun XtreamChannel.toRawMediaMetadata(authHeaders: Map<String, String>): RawMediaMetadata
```

Gold Decision (Dec 2025): VOD → `TmdbRef(MOVIE, tmdbId)`, Series → `TmdbRef(TV, tmdbId)`

---

## 9. Normalizer / TMDB

### 9.1 MediaMetadataNormalizer

**File:** `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/MediaMetadataNormalizer.kt`

```kotlin
interface MediaMetadataNormalizer {
    suspend fun normalize(raw: RawMediaMetadata): NormalizedMediaMetadata
}
```

**Implementation:** `RegexMediaMetadataNormalizer` (title cleaning, year extraction, scene-naming parser)

### 9.2 TmdbMetadataResolver

**File:** `core/metadata-normalizer/src/main/java/com/fishit/player/core/metadata/TmdbMetadataResolver.kt`

```kotlin
interface TmdbMetadataResolver {
    suspend fun enrich(normalized: NormalizedMediaMetadata): NormalizedMediaMetadata
}
```

**Implementation:** `DefaultTmdbMetadataResolver` uses `app.moviebase:tmdb-api`

### 9.3 TMDB Dependency

- **Library:** `app.moviebase:tmdb-api:1.6.0`
- **Location:** ONLY in `:core:metadata-normalizer`
- **Contract:** Workers call `TmdbMetadataResolver`, never TMDB client directly (W-4)

---

## 10. ObjectBox Persistence

### 10.1 BoxStore Initialization

**File:** `core/persistence/src/main/java/com/fishit/player/core/persistence/obx/ObxStore.kt`

```kotlin
object ObxStore {
    private val ref = AtomicReference<BoxStore?>()
    
    fun get(context: Context): BoxStore {
        // Double-check locking initialization
        // Uses MyObjectBox.builder().androidContext(...).build()
    }
}
```

### 10.2 Entity Modules

**Files in `core/persistence/.../obx/`:**

- `ObxEntities.kt` - Core entities
- `ObxCanonicalEntities.kt` - Canonical media entities

### 10.3 Repositories

| Interface | Implementation | File |
|-----------|---------------|------|
| `ContentRepository` | `ObxContentRepository` | `core/persistence/.../repository/ObxContentRepository.kt` |
| `ProfileRepository` | `ObxProfileRepository` | `core/persistence/.../repository/ObxProfileRepository.kt` |
| `ResumeRepository` | `ObxResumeRepository` | `core/persistence/.../repository/ObxResumeRepository.kt` |
| `ScreenTimeRepository` | `ObxScreenTimeRepository` | `core/persistence/.../repository/ObxScreenTimeRepository.kt` |
| `CanonicalMediaRepository` | `ObxCanonicalMediaRepository` | `core/persistence/.../repository/ObxCanonicalMediaRepository.kt` |
| `TelegramContentRepository` | `ObxTelegramContentRepository` | `infra/data-telegram/.../ObxTelegramContentRepository.kt` |
| `XtreamCatalogRepository` | `ObxXtreamCatalogRepository` | `infra/data-xtream/.../ObxXtreamCatalogRepository.kt` |
| `XtreamLiveRepository` | `ObxXtreamLiveRepository` | `infra/data-xtream/.../ObxXtreamLiveRepository.kt` |

### 10.4 PersistenceModule (DI)

**File:** `core/persistence/src/main/java/com/fishit/player/core/persistence/di/PersistenceModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceModule {
    @Binds @Singleton
    abstract fun bindProfileRepository(impl: ObxProfileRepository): ProfileRepository
    @Binds @Singleton
    abstract fun bindResumeRepository(impl: ObxResumeRepository): ResumeRepository
    @Binds @Singleton
    abstract fun bindContentRepository(impl: ObxContentRepository): ContentRepository
    @Binds @Singleton
    abstract fun bindCanonicalMediaRepository(impl: ObxCanonicalMediaRepository): CanonicalMediaRepository
    // ...
}
```

---

## 11. Source Activation / Login Wiring

### 11.1 SourceActivationStore

**Interface:** `core/catalog-sync/.../SourceActivationStore.kt`  
**Implementation:** `infra/work/.../DefaultSourceActivationStore.kt` (DataStore-backed)

```kotlin
interface SourceActivationStore {
    fun observeStates(): Flow<SourceActivationSnapshot>
    fun getCurrentSnapshot(): SourceActivationSnapshot
    suspend fun markXtreamActive()
    suspend fun markXtreamInactive(reason: SourceErrorReason?)
    suspend fun markTelegramActive()
    suspend fun markTelegramInactive(reason: SourceErrorReason?)
    suspend fun markIoActive()
    suspend fun markIoInactive(reason: SourceErrorReason?)
}
```

### 11.2 Bootstrap Classes

| Class | File | Purpose |
|-------|------|---------|
| `XtreamSessionBootstrap` | `app-v2/.../XtreamSessionBootstrap.kt` | Validates Xtream credentials, updates SourceActivationStore |
| `CatalogSyncBootstrap` | `app-v2/.../CatalogSyncBootstrap.kt` | Triggers initial catalog sync |
| `TelegramActivationObserver` | `app-v2/.../TelegramActivationObserver.kt` | Observes Telegram auth state |
| `SourceActivationObserver` | `infra/work/.../SourceActivationObserver.kt` | Reacts to source state changes |

---

## 12. Violations / Parallel Paths Found

### 12.1 Direct CatalogSyncService Calls from UI

**Scan Results:** ✅ **NO VIOLATIONS FOUND**

Searched `app-v2/**/*.kt` and `feature/**/*.kt` for:

- `CatalogSyncService.sync*()`
- `CatalogSyncService.clear*()`

All CatalogSyncService calls are properly isolated to Workers:

- `XtreamCatalogScanWorker.kt` → `catalogSyncService.syncXtream()`
- `TelegramFullHistoryScanWorker.kt` → `catalogSyncService.syncTelegram()`
- `TelegramIncrementalScanWorker.kt` → `catalogSyncService.syncTelegram()`

### 12.2 Forbidden Import Patterns

**Contract Rule (AGENTS.md Section 3.2):**
> Any occurrence of `com.chris.m3usuite` outside of `/legacy/**` is a bug

**Status:** Should be verified via detekt rule `ForbiddenImport`

### 12.3 Compliance Summary

| Rule | Status |
|------|--------|
| W-2: All scanning via CatalogSyncService | ✅ Compliant |
| W-4: TMDB via TmdbMetadataResolver only | ✅ Compliant |
| W-7: Source Order (Xtream→Telegram→IO) | ✅ Implemented in Orchestrator |
| No UI/ViewModel direct CatalogSyncService | ✅ Compliant |
| No v1 namespace in v2 modules | ⚠️ Verify via detekt |

---

## Summary Statistics

| Category | Count |
|----------|-------|
| Total v2 Modules | 39 |
| Core Modules | 11 |
| Feature Modules | 8 |
| Infrastructure Modules | 9 |
| Pipeline Modules | 4 |
| Player/Playback Modules | 7 |
| Worker Classes | 10 |
| Repository Interfaces | 8 |
| FeatureId Definitions | 17 |

---

*End of Repo Reality Dump*
