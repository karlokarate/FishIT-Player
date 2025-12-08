# Xtream Pipeline v2: Wiederverwendungs-Analyse

> **Datum:** 2025-12-07 (Updated: 2025-12-07)  
> **Scope:** Review des v1 Xtream-Codes auf Wiederverwendbarkeit für `:pipeline:xtream` unter Einhaltung der v2 Contracts  
> **Referenzen:**
> - `AGENTS_V2.md`
> - `V1_VS_V2_ANALYSIS_REPORT.md`
> - `IMPLEMENTATION_PHASES_V2.md`
> - `ARCHITECTURE_OVERVIEW_V2.md`
> - **`MEDIA_NORMALIZATION_CONTRACT.md`** ⭐ NEU
> - **`MEDIA_NORMALIZER_DESIGN.md`** ⭐ NEU
> - **`LOGGING_CONTRACT_V2.md`** ⭐ NEU

---

## Executive Summary

Die v1 Xtream Pipeline ist **produktionsreif** und als **Tier 1** klassifiziert (~5.400 Zeilen inkl. Repository). Der Code kann nahezu unverändert nach `:pipeline:xtream` portiert werden.

### ⚠️ KRITISCH: Neue Anforderungen aus MEDIA_NORMALIZATION_CONTRACT.md

Die Xtream-Pipeline muss nun zusätzlich:
1. **`toRawMediaMetadata()`** für alle Content-Types implementieren
2. **Keine eigene Titel-Normalisierung** durchführen
3. **Externe IDs (TMDB)** nur durchreichen, wenn vom Xtream-Panel geliefert
4. **Zentrale Normalisierung** via `:core:metadata-normalizer` nutzen

Diese Analyse dokumentiert:

1. **Direktes Porting** – Welche Dateien 1:1 übernommen werden
2. **Anpassungen** – Minimale Änderungen für v2 Interfaces
3. **Neu zu erstellen** – Fehlende v2-spezifische Komponenten
4. **Contract-Compliance** – Einhaltung aller v2-Regeln
5. **⭐ NEU: Metadata Normalization** – Integration mit zentralem Normalizer

---

## 1. v1 Xtream-Komponenten-Inventar

### 1.1 Core Pipeline (~903 Zeilen)

| Datei | Zeilen | v2 Status | Anmerkungen |
|-------|--------|-----------|-------------|
| `XtreamClient.kt` | 903 | ✅ PORT DIREKT | Rate-Limiting, Cache, API-Calls |
| `XtreamConfig.kt` | 400 | ✅ PORT DIREKT | URL-Factory, PathKinds |
| `XtreamModels.kt` | 206 | ✅ PORT DIREKT | Raw + Normalized Models |
| `XtreamCapabilities.kt` | 630 | ✅ PORT DIREKT | Discovery, Port-Resolver |
| `XtreamDetect.kt` | 118 | ✅ PORT DIREKT | URL-Parsing, Cred-Extraktion |
| `XtreamSeeder.kt` | 147 | ⚠️ ADAPTIEREN | v1-spezifische Koordination |
| `XtreamImportCoordinator.kt` | 48 | ⚠️ ADAPTIEREN | Singleton → Hilt-Injectable |
| `ProviderLabelStore.kt` | 106 | ✅ PORT DIREKT | Provider-Label-Mapping |

### 1.2 Repository Layer (~2.829 Zeilen)

| Datei | Zeilen | v2 Status | Anmerkungen |
|-------|--------|-----------|-------------|
| `XtreamObxRepository.kt` | 2829 | ⚠️ ADAPTIEREN | v2 Interface implementieren |

### 1.3 Shared DataSources

| Datei | v2 Target | Status | Anmerkungen |
|-------|-----------|--------|-------------|
| `DelegatingDataSourceFactory.kt` | `:player:internal` | ✅ PORT | URL-Routing |
| `RarDataSource.kt` | `:pipeline:telegram` | ✅ PORT | RAR-Streaming |

---

## 2. Direktes Porting (Tier 1)

### 2.1 XtreamClient.kt → `:pipeline:xtream`

**Produktionsqualität:**

- ✅ Per-Host Rate-Limiting (120ms Min-Intervall via `Mutex`)
- ✅ In-Memory Cache (60s TTL, 15s für EPG)
- ✅ Parallele Limits via `Semaphore(4)` für EPG
- ✅ Robustes JSON-Parsing mit Fallbacks
- ✅ Credential-Redaktion im Logging
- ✅ VOD-Alias-Rotation (`vod|movie|movies`)

**Erforderliche Änderungen:**

```kotlin
// ALT (v1):
import com.chris.m3usuite.core.debug.GlobalDebug
import com.chris.m3usuite.core.logging.UnifiedLog
import com.chris.m3usuite.BuildConfig

// NEU (v2):
import com.fishit.player.infra.logging.UnifiedLog
// BuildConfig → injected config
```

**Package-Migration:**

```
com.chris.m3usuite.core.xtream → com.fishit.player.pipeline.xtream
```

### 2.2 XtreamConfig.kt → `:pipeline:xtream`

**Produktionsqualität:**

- ✅ Deterministische URL-Erzeugung
- ✅ Schema/Port-Explizitheit (kein Raten)
- ✅ URL-Encoding für Credentials
- ✅ basePath-Support für Reverse-Proxys
- ✅ Container-Extension-Validierung
- ✅ Live/VOD/Series-Präferenzlisten

**Keine Änderungen erforderlich** – Pure Kotlin, keine externen Abhängigkeiten.

### 2.3 XtreamModels.kt → `:pipeline:xtream`

**Enthält:**

```kotlin
// Raw API Models (direkte Deserialisierung)
@Serializable data class RawCategory(...)
@Serializable data class RawLiveStream(...)
@Serializable data class RawVod(...)
@Serializable data class RawSeries(...)
@Serializable data class RawEpisode(...)
@Serializable data class RawEpisodeInfo(...)

// Normalized Models (UI/DB-freundlich)
@Serializable data class NormalizedVodDetail(...)
@Serializable data class NormalizedSeriesDetail(...)
@Serializable data class NormalizedSeason(...)
@Serializable data class NormalizedEpisode(...)
@Serializable data class XtShortEPGProgramme(...)
```

**v2 Mapping:**

- `RawCategory` → `XtreamCategory` (in `:pipeline:xtream`)
- `NormalizedVodDetail` → `XtreamVodItem` (v2 Domain Model)
- `NormalizedSeriesDetail` → `XtreamSeriesItem` (v2 Domain Model)
- `NormalizedEpisode` → `XtreamEpisode` (v2 Domain Model)
- `RawLiveStream` → `XtreamChannel` (v2 Domain Model)

### 2.4 XtreamCapabilities.kt → `:pipeline:xtream`

**Produktionsqualität:**

- ✅ `CapabilityDiscoverer` mit parallelem Probing
- ✅ `ProviderCapabilityStore` mit TTL-Cache (24h)
- ✅ `EndpointPortStore` mit TTL-Cache (7 Tage)
- ✅ Konfigurierbare Port-Kandidaten (injizierbar)
- ✅ VOD-Alias-Discovery (`vod|movie|movies`)
- ✅ Response-Type-Detection (array/object)

**Erforderliche Änderungen:**

```kotlin
// SharedPreferences → DataStore oder Hilt-Provider
class ProviderCapabilityStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    ...
)
```

### 2.5 XtreamDetect.kt → `:pipeline:xtream`

**Produktionsqualität:**

- ✅ `detectCreds()` – URL → XtreamCreds
- ✅ `parseStreamId()` – Stream-ID aus URL
- ✅ Query-Parameter-Parsing
- ✅ Pfad-basierte Credential-Extraktion

**Keine Änderungen erforderlich** – Pure Kotlin, keine Abhängigkeiten.

---

## 3. Adaptiertes Porting (Tier 2)

### 3.1 XtreamObxRepository.kt → `:pipeline:xtream`

**Was bleibt erhalten:**

```kotlin
// ✅ Diese Methoden bleiben:
suspend fun hasAnyContent(): Boolean
suspend fun countTotals(): Triple<Long, Long, Long>
suspend fun seedListsQuick(...)
suspend fun importVodDetailsForIds(...)
suspend fun importSeriesDetailsForIds(...)
fun liveChanges(): Flow<Unit>
fun vodChanges(): Flow<Unit>
fun seriesChanges(): Flow<Unit>
```

**Was angepasst werden muss:**

1. **Interface-Implementierung:**

```kotlin
// NEU: v2 Interface implementieren
class XtreamObxRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore,
    private val client: XtreamClient,
    private val settings: XtreamSettingsProvider,
) : XtreamCatalogRepository, XtreamLiveRepository {
    
    // XtreamCatalogRepository
    override fun getVodItems(filter: Filter): Flow<PagingData<XtreamVodItem>>
    override fun getSeriesItems(filter: Filter): Flow<PagingData<XtreamSeriesItem>>
    override suspend fun getVodDetail(id: Int): XtreamVodItem?
    override suspend fun getSeriesDetail(id: Int): XtreamSeriesItem?
    
    // XtreamLiveRepository
    override fun getChannels(filter: Filter): Flow<PagingData<XtreamChannel>>
    override fun getEpg(channelId: Int): Flow<List<XtreamEpgEntry>>
}
```

2. **Settings-Abstraktion:**

```kotlin
// ALT (v1):
private val settings: SettingsStore

// NEU (v2):
interface XtreamSettingsProvider {
    val host: Flow<String>
    val username: Flow<String>
    val password: Flow<String>
    val port: Flow<Int>
    val workersEnabled: Flow<Boolean>
}
```

3. **ObjectBox-Entitäten in `:core:persistence`:**

```kotlin
// Bereits in v1 vorhanden, nach :core:persistence verschieben:
@Entity data class ObxCategory(...)
@Entity data class ObxLive(...)
@Entity data class ObxVod(...)
@Entity data class ObxSeries(...)
@Entity data class ObxEpisode(...)
@Entity data class ObxEpgNowNext(...)
```

### 3.2 XtreamSeeder.kt → `:pipeline:xtream`

**Was angepasst werden muss:**

```kotlin
// ALT (v1): Object Singleton
object XtreamSeeder {
    suspend fun ensureSeeded(context: Context, store: SettingsStore, ...)
}

// NEU (v2): Hilt-Injectable
class XtreamSeeder @Inject constructor(
    private val repository: XtreamCatalogRepository,
    private val settings: XtreamSettingsProvider,
    private val client: XtreamClient,
) {
    suspend fun ensureSeeded(force: Boolean = false): Result<SeedResult>
}

data class SeedResult(
    val liveCount: Int,
    val vodCount: Int,
    val seriesCount: Int,
)
```

### 3.3 XtreamImportCoordinator.kt → `:pipeline:xtream`

**Was angepasst werden muss:**

```kotlin
// ALT (v1): Object Singleton mit CoroutineScope
object XtreamImportCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val seederInFlight = MutableStateFlow(false)
}

// NEU (v2): Hilt Singleton mit @ApplicationScope
@Singleton
class XtreamImportCoordinator @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _seederInFlight = MutableStateFlow(false)
    val seederInFlight: StateFlow<Boolean> = _seederInFlight.asStateFlow()
    
    suspend fun <T> runSeeding(block: suspend () -> T): T
    fun enqueueWork(block: suspend () -> Unit)
    suspend fun waitUntilIdle()
}
```

---

## 4. Neu zu erstellende v2 Komponenten

### 4.1 v2 Domain Interfaces (`:pipeline:xtream`)

```kotlin
// Gemäß ARCHITECTURE_OVERVIEW_V2.md Section 4.2

/**
 * Repository für VOD/Series Inhalte.
 */
interface XtreamCatalogRepository {
    fun getVodItems(filter: VodFilter = VodFilter.ALL): Flow<PagingData<XtreamVodItem>>
    fun getSeriesItems(filter: SeriesFilter = SeriesFilter.ALL): Flow<PagingData<XtreamSeriesItem>>
    fun getCategories(kind: ContentKind): Flow<List<XtreamCategory>>
    suspend fun getVodDetail(vodId: Int): XtreamVodItem?
    suspend fun getSeriesDetail(seriesId: Int): XtreamSeriesItem?
    suspend fun refreshCatalog(): Result<RefreshStats>
}

/**
 * Repository für Live-Channels und EPG.
 */
interface XtreamLiveRepository {
    fun getChannels(filter: ChannelFilter = ChannelFilter.ALL): Flow<PagingData<XtreamChannel>>
    fun getEpgForChannel(channelId: Int): Flow<List<XtreamEpgEntry>>
    fun getNowNext(channelIds: List<Int>): Flow<Map<Int, NowNextPair>>
    suspend fun refreshEpg(channelIds: List<Int>): Result<Int>
}

/**
 * Factory für Playback-Sources.
 */
interface XtreamPlaybackSourceFactory {
    fun createLiveSource(channel: XtreamChannel): MediaSource
    fun createVodSource(vod: XtreamVodItem): MediaSource
    fun createSeriesEpisodeSource(episode: XtreamEpisode): MediaSource
}
```

### 4.2 v2 Domain Models (`:pipeline:xtream`)

```kotlin
// Gemäß IMPLEMENTATION_PHASES_V2.md Phase 3

data class XtreamVodItem(
    val id: Int,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val year: Int?,
    val rating: Double?,
    val plot: String?,
    val genre: String?,
    val director: String?,
    val cast: String?,
    val duration: Duration?,
    val container: String?,
    val trailer: TrailerInfo?,
    val categoryId: String?,
    val providerKey: String?,
) {
    fun toPlaybackContext(): PlaybackContext = PlaybackContext(
        type = PlaybackType.VOD,
        vodId = id,
        title = name,
        // ...
    )
}

data class XtreamSeriesItem(
    val id: Int,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val year: Int?,
    val rating: Double?,
    val plot: String?,
    val genre: String?,
    val seasons: List<XtreamSeason>,
    val categoryId: String?,
    val providerKey: String?,
)

data class XtreamSeason(
    val number: Int,
    val episodes: List<XtreamEpisode>,
)

data class XtreamEpisode(
    val id: Int,
    val number: Int,
    val title: String?,
    val plot: String?,
    val poster: String?,
    val duration: Duration?,
    val container: String?,
    val airDate: String?,
) {
    fun toPlaybackContext(seriesId: Int, seasonNumber: Int): PlaybackContext = PlaybackContext(
        type = PlaybackType.SERIES,
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeId = id,
        episodeNumber = number,
        title = title,
        // ...
    )
}

data class XtreamChannel(
    val id: Int,
    val name: String,
    val logo: String?,
    val epgId: String?,
    val categoryId: String?,
    val hasArchive: Boolean,
) {
    fun toPlaybackContext(): PlaybackContext = PlaybackContext(
        type = PlaybackType.LIVE,
        channelId = id,
        title = name,
        // ...
    )
}

data class XtreamEpgEntry(
    val channelId: Int,
    val title: String,
    val start: Instant,
    val end: Instant,
    val description: String?,
)

data class NowNextPair(
    val now: XtreamEpgEntry?,
    val next: XtreamEpgEntry?,
)

data class XtreamCategory(
    val id: String,
    val name: String,
    val kind: ContentKind,
)

enum class ContentKind { LIVE, VOD, SERIES }
```

### 4.3 XtreamPlaybackSourceFactory Implementation

```kotlin
class XtreamPlaybackSourceFactoryImpl @Inject constructor(
    private val config: XtreamConfig,
    private val httpDataSourceFactory: OkHttpDataSource.Factory,
) : XtreamPlaybackSourceFactory {
    
    override fun createLiveSource(channel: XtreamChannel): MediaSource {
        val url = config.liveUrl(channel.id)
        return when {
            url.endsWith(".m3u8") -> HlsMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
            else -> ProgressiveMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
        }
    }
    
    override fun createVodSource(vod: XtreamVodItem): MediaSource {
        val url = config.vodUrl(vod.id, vod.container)
        return ProgressiveMediaSource.Factory(httpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))
    }
    
    override fun createSeriesEpisodeSource(episode: XtreamEpisode): MediaSource {
        // URL via config.seriesEpisodeUrl(...)
    }
}
```

---

## 5. Contract-Compliance Checklist

### 5.1 AGENTS_V2.md Compliance

| Regel | Status | Nachweis |
|-------|--------|----------|
| Keine Abhängigkeit zu `:feature:*` | ✅ | Nur `:core:*`, `:infra:logging` |
| Keine Abhängigkeit zu `:player:internal` | ✅ | Factory-Interface statt Player-Direkt |
| Keine UI/Compose in `:pipeline:*` | ✅ | Pure Domain + Repository |
| ObjectBox in `:core:persistence` | ✅ | Entities dort, nur Zugriff hier |
| Englische Code-Sprache | ✅ | Alle Identifier englisch |

### 5.2 IMPLEMENTATION_PHASES_V2.md Phase 3 Compliance

| Anforderung | Status | Nachweis |
|-------------|--------|----------|
| XtreamVodItem definieren | ⏳ NEU | Siehe 4.2 |
| XtreamSeriesItem definieren | ⏳ NEU | Siehe 4.2 |
| XtreamEpisode definieren | ⏳ NEU | Siehe 4.2 |
| XtreamChannel definieren | ⏳ NEU | Siehe 4.2 |
| XtreamEpgEntry definieren | ⏳ NEU | Siehe 4.2 |
| XtreamCatalogRepository Interface | ⏳ NEU | Siehe 4.1 |
| XtreamLiveRepository Interface | ⏳ NEU | Siehe 4.1 |
| XtreamPlaybackSourceFactory Interface | ⏳ NEU | Siehe 4.1 |
| HTTP/URL-Building portieren | ✅ PORT | XtreamConfig.kt |
| DelegatingDataSourceFactory portieren | ✅ PORT | Nach `:player:internal` |
| **⭐ toRawMediaMetadata() implementieren** | ⏳ NEU | Siehe 10.2 |

### 5.3 MEDIA_NORMALIZATION_CONTRACT.md Compliance ⭐ NEU

| Anforderung | v1 Status | v2 Aktion |
|-------------|-----------|-----------|
| Pipelines liefern nur RawMediaMetadata | ⚠️ Fehlt | `toRawMediaMetadata()` hinzufügen |
| Keine Titel-Normalisierung in Pipeline | ✅ v1 OK | Keine Änderung nötig |
| Keine TMDB-Lookups in Pipeline | ✅ v1 OK | Keine Änderung nötig |
| Externe IDs nur durchreichen | ✅ v1 OK | Keine Änderung nötig |
| SourceType.XTREAM verwenden | ⏳ NEU | In Extensions |
| Stabile sourceId | ⏳ NEU | Format: `xtream:vod:<id>` |

### 5.4 LOGGING_CONTRACT_V2.md Compliance ⭐ NEU

| Anforderung | v1 Status | v2 Aktion |
|-------------|-----------|-----------|
| Nur `UnifiedLog.*` verwenden | ✅ v1 nutzt | Package ändern |
| Kein `android.util.Log` | ✅ | - |
| Kein `Timber` | ✅ | - |
| Secrets redaktieren | ✅ v1 redaktiert | - |
| TAG-Convention | ⚠️ | `XtreamClient` → ok |

### 5.5 V1_VS_V2_ANALYSIS_REPORT.md Compliance

| Vorgabe | Status |
|---------|--------|
| Tier 1 Xtream als "Port Directly" klassifiziert | ✅ |
| ~3000 Zeilen produktionsgetesteter Code | ✅ |
| Appendix A File-Mapping eingehalten | ✅ |

---

## 6. Migrations-Aufwand-Schätzung

### 6.1 Direkte Ports (< 1h pro Datei)

| Datei | Aufwand | Änderungen |
|-------|---------|------------|
| `XtreamClient.kt` | 30 min | Package, Logging-Import |
| `XtreamConfig.kt` | 15 min | Nur Package |
| `XtreamModels.kt` | 15 min | Nur Package |
| `XtreamCapabilities.kt` | 45 min | Package, DataStore statt SharedPrefs |
| `XtreamDetect.kt` | 10 min | Nur Package |
| `ProviderLabelStore.kt` | 20 min | Package, DataStore |

**Gesamt direkte Ports: ~2,5 Stunden**

### 6.2 Adaptierte Ports (2-4h pro Datei)

| Datei | Aufwand | Änderungen |
|-------|---------|------------|
| `XtreamObxRepository.kt` | 4h | v2 Interfaces, Mapper |
| `XtreamSeeder.kt` | 1h | Hilt-Injectable |
| `XtreamImportCoordinator.kt` | 30 min | Hilt Singleton |

**Gesamt adaptierte Ports: ~5,5 Stunden**

### 6.3 Neue Komponenten (1-3h pro Interface)

| Komponente | Aufwand |
|------------|---------|
| v2 Domain Models | 2h |
| v2 Interfaces | 1h |
| XtreamPlaybackSourceFactory Impl | 2h |
| Hilt DI Module | 1h |

**Gesamt neue Komponenten: ~6 Stunden**

### 6.4 Gesamtaufwand

| Kategorie | Zeit |
|-----------|------|
| Direkte Ports | 2,5h |
| Adaptierte Ports | 5,5h |
| Neue Komponenten | 6h |
| **Total** | **~14 Stunden** |

---

## 7. Empfohlene Migrations-Reihenfolge

### Phase 3.1: Foundation

1. Package-Struktur in `:pipeline:xtream` erstellen
2. v2 Domain Models definieren
3. v2 Interfaces definieren

### Phase 3.2: Core Porting

4. `XtreamConfig.kt` portieren
5. `XtreamModels.kt` portieren (+ Mapper zu v2 Models)
6. `XtreamDetect.kt` portieren
7. `XtreamClient.kt` portieren

### Phase 3.3: Discovery & Caching

8. `XtreamCapabilities.kt` portieren
9. `ProviderLabelStore.kt` portieren

### Phase 3.4: Repository & Coordination

10. `XtreamObxRepository.kt` adaptieren
11. `XtreamSeeder.kt` adaptieren
12. `XtreamImportCoordinator.kt` adaptieren

### Phase 3.5: Playback Integration

13. `XtreamPlaybackSourceFactory` implementieren
14. Hilt DI Module erstellen

---

## 8. Offene Fragen / Entscheidungen

### 8.1 SharedPreferences vs DataStore

**Empfehlung:** DataStore für v2

- `ProviderCapabilityStore` → `ProviderCapabilityDataStore`
- `EndpointPortStore` → `EndpointPortDataStore`

### 8.2 Singleton Pattern vs Hilt

**Empfehlung:** Hilt für v2

- `XtreamImportCoordinator` → `@Singleton class`
- `XtreamSeeder` → `@Singleton class`

### 8.3 CategoryNormalizer Standort

**Empfehlung:** `:core:model` als Utility

- Wird von `:pipeline:xtream` und `:pipeline:telegram` genutzt

---

## 9. Abhängigkeiten für build.gradle.kts

```kotlin
// :pipeline:xtream/build.gradle.kts
dependencies {
    // v2 Module
    implementation(project(":core:model"))
    implementation(project(":core:persistence"))
    implementation(project(":infra:logging"))
    
    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // HTTP
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    
    // ObjectBox (Zugriff, Entities in :core:persistence)
    implementation("io.objectbox:objectbox-kotlin:4.0.3")
    
    // Paging
    implementation("androidx.paging:paging-runtime-ktx:3.3.5")
    implementation("androidx.paging:paging-compose:3.3.5")
    
    // DI
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.2")
}
```

---

## 10. ⭐ NEU: Metadata Normalization Integration

### 10.1 Contract-Anforderungen (MEDIA_NORMALIZATION_CONTRACT.md)

Gemäß dem neuen Contract MUSS die Xtream-Pipeline:

| Anforderung | Status | Implementierung |
|-------------|--------|-----------------|
| `toRawMediaMetadata()` für VOD | ⏳ NEU | Extension Function |
| `toRawMediaMetadata()` für Series | ⏳ NEU | Extension Function |
| `toRawMediaMetadata()` für Episodes | ⏳ NEU | Extension Function |
| `toRawMediaMetadata()` für Live | ⏳ NEU | Extension Function |
| Keine eigene Titel-Normalisierung | ✅ v1 OK | v1 macht keine Normalisierung |
| TMDB-IDs nur durchreichen | ✅ v1 OK | v1 liest tmdb_id aus API |
| Keine TMDB-Lookups in Pipeline | ✅ v1 OK | v1 macht keine Lookups |

### 10.2 Erforderliche Extension Functions

```kotlin
// In :pipeline:xtream

/**
 * Converts Xtream VOD item to RawMediaMetadata.
 * Per MEDIA_NORMALIZATION_CONTRACT.md: NO title cleaning, NO TMDB lookups.
 */
fun XtreamVodItem.toRawMediaMetadata(): RawMediaMetadata = RawMediaMetadata(
    originalTitle = name,  // RAW - no cleaning!
    mediaType = MediaType.MOVIE,
    year = year,
    season = null,
    episode = null,
    durationMinutes = duration?.inWholeMinutes?.toInt(),
    externalIds = ExternalIds(
        tmdbId = tmdbId,  // Only if Xtream panel provides it
        imdbId = imdbId,
    ),
    sourceType = SourceType.XTREAM,
    sourceLabel = "Xtream: $providerKey",
    sourceId = "xtream:vod:$id",
)

/**
 * Converts Xtream Series Episode to RawMediaMetadata.
 */
fun XtreamEpisode.toRawMediaMetadata(
    seriesName: String,
    seriesId: Int,
): RawMediaMetadata = RawMediaMetadata(
    originalTitle = title ?: "$seriesName S${season}E${number}",
    mediaType = MediaType.SERIES_EPISODE,
    year = null,
    season = season,
    episode = number,
    durationMinutes = duration?.inWholeMinutes?.toInt(),
    externalIds = ExternalIds(),  // Episodes rarely have external IDs
    sourceType = SourceType.XTREAM,
    sourceLabel = "Xtream: $seriesName",
    sourceId = "xtream:episode:$seriesId:$id",
)

/**
 * Converts Xtream Live Channel to RawMediaMetadata.
 */
fun XtreamChannel.toRawMediaMetadata(): RawMediaMetadata = RawMediaMetadata(
    originalTitle = name,
    mediaType = MediaType.LIVE,
    year = null,
    season = null,
    episode = null,
    durationMinutes = null,
    externalIds = ExternalIds(),
    sourceType = SourceType.XTREAM,
    sourceLabel = "Xtream Live",
    sourceId = "xtream:live:$id",
)
```

### 10.3 v1 Code-Analyse: Normalisierungs-Freiheit

Der v1 `XtreamClient` führt **keine Titel-Normalisierung** durch:

```kotlin
// v1 XtreamClient.kt - Zeilen 247-253 (GOOD!)
RawVod(
    num = obj["num"]?.asIntOrNull(),
    name = obj["name"]?.asString(),  // ✅ Raw name, no cleaning
    vod_id = resolvedId,
    stream_icon = obj["stream_icon"]?.asString(),
    category_id = obj["category_id"]?.asString(),
    container_extension = obj["container_extension"]?.asString(),
)
```

**Fazit:** Der v1 Code ist bereits contract-compliant bezüglich "keine eigene Normalisierung".

### 10.4 v1 Code-Analyse: TMDB-ID Durchreichung

Der v1 `XtreamClient.getVodDetailFull()` liest TMDB-IDs korrekt durch:

```kotlin
// v1 XtreamClient.kt - Zeilen 351-352 (GOOD!)
imdbId = pickStr("imdb_id"),
tmdbId = pickStr("tmdb_id"),
```

**Fazit:** Der v1 Code reicht externe IDs bereits korrekt durch.

### 10.5 Integration mit Core Normalizer

Der Flow für Xtream-Content wird:

```
XtreamClient.getVodStreams()
    ↓
XtreamVodItem (pipeline-local model)
    ↓
XtreamVodItem.toRawMediaMetadata()
    ↓
RawMediaMetadata (shared :core:model)
    ↓
MediaMetadataNormalizer.normalize()
    ↓
NormalizedMediaMetadata
    ↓
TmdbMetadataResolver.enrich()  (optional)
    ↓
CanonicalMediaId
```

### 10.6 Aufwand für Normalization Integration

| Task | Aufwand |
|------|---------|
| Extension Functions schreiben | 1h |
| Tests für toRawMediaMetadata() | 1h |
| Integration mit Repository | 30 min |
| **Gesamt** | **2,5h** |

---

## 11. ⭐ NEU: Logging Contract Integration

### 11.1 Contract-Anforderungen (LOGGING_CONTRACT_V2.md)

| Anforderung | v1 Status | v2 Änderung |
|-------------|-----------|-------------|
| Nur `UnifiedLog.*` verwenden | ✅ v1 nutzt UnifiedLog | Package-Import ändern |
| Kein `android.util.Log` | ✅ v1 nutzt keins | - |
| Kein `Timber` | ✅ v1 nutzt keins | - |
| TAG-Convention | ⚠️ Teilweise | Vereinheitlichen |
| Keine Secrets loggen | ✅ v1 redaktiert creds | - |

### 11.2 Erforderliche Änderungen

```kotlin
// ALT (v1):
import com.chris.m3usuite.core.logging.UnifiedLog

// NEU (v2):
import com.fishit.player.infra.logging.UnifiedLog
```

Die v1 `XtreamClient` redaktiert bereits Credentials im Logging:

```kotlin
// v1 XtreamClient.kt - Zeilen 82-85 (GOOD!)
private fun redact(url: String): String =
    url
        .replace(Regex("(?i)(password)=([^&]*)"), "${'$'}1=***")
        .replace(Regex("(?i)(username)=([^&]*)"), "${'$'}1=***")
```

**Fazit:** v1 ist bereits contract-compliant bezüglich Logging-Security.

---

## 12. Aktualisierte Aufwand-Schätzung

### 12.1 Gesamt-Aufwand (inkl. neue Contracts)

| Kategorie | Zeit |
|-----------|------|
| Direkte Ports | 2,5h |
| Adaptierte Ports | 5,5h |
| Neue Komponenten (Domain Models, Interfaces) | 6h |
| **⭐ Metadata Normalization Integration** | **2,5h** |
| **⭐ Logging Contract Migration** | **30 min** |
| **Total** | **~17 Stunden** |

### 12.2 Delta zur ursprünglichen Schätzung

| Ursprünglich | Neu | Delta |
|--------------|-----|-------|
| 14h | 17h | +3h |

Der Mehraufwand entsteht durch:
- `toRawMediaMetadata()` Extensions + Tests
- Logging-Package-Migration

---

## 13. Fazit (Aktualisiert)

Die v1 Xtream Pipeline ist **produktionsbereit** und kann mit **moderatem Aufwand** (~17 Stunden) nach v2 portiert werden:

- **~85%** des Codes kann 1:1 übernommen werden
- **~10%** erfordert Interface-Anpassungen für v2 Architektur
- **~5%** NEU: Metadata Normalization Integration

### Contract-Compliance Status

| Contract | Status |
|----------|--------|
| AGENTS_V2.md | ✅ Vollständig compliant |
| MEDIA_NORMALIZATION_CONTRACT.md | ✅ v1 bereits compliant (keine Normalisierung) |
| LOGGING_CONTRACT_V2.md | ⚠️ Package-Import anpassen |
| IMPLEMENTATION_PHASES_V2.md | ✅ Phase 3 ready |

**Keine Neuimplementierung erforderlich** – der v1 Code hat sich in der Produktion bewährt und ist bereits mit den neuen Normalization-Contracts kompatibel.

---

## Changelog

| Datum | Änderung |
|-------|----------|
| 2025-12-07 | Initial Analysis erstellt |
| 2025-12-07 | **Update:** Metadata Normalization Contract Integration hinzugefügt |
| 2025-12-07 | **Update:** Logging Contract v2 Integration hinzugefügt |
| 2025-12-07 | **Update:** Aufwand-Schätzung aktualisiert (14h → 17h) |
