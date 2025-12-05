# FishIT Player v2 – Phase 2 Task: Pipeline Stubs & Core Persistence

**Datum:** 2025-12-05  
**Branch:** `architecture/v2-bootstrap`  
**Vorgänger:** Phase 0-1 ✅ COMPLETE  
**Geschätzte Dauer:** 3-5 Tage  
**Priorität:** HOCH

---

## Executive Summary

Phase 2 baut auf dem erfolgreichen Phase 0-1 Bootstrap auf und implementiert:
1. **Pipeline Stubs** - Grundlegende Abstractions für alle 4 Pipelines
2. **Core Persistence** - ObjectBox Port aus v1 (~17.000 Zeilen)
3. **Playback Domain Impl** - ResumeManager/KidsPlaybackGate mit echten Repositories

**Ziel:** Nach Phase 2 kann die App echten Content aus v1-Datenbank laden und abspielen.

---

## Voraussetzungen

✅ **Phase 0-1 abgeschlossen:**
- Alle 16 Module erstellt
- PlaybackContext & Domain Interfaces vorhanden
- Hilt DI konfiguriert
- DebugPlaybackScreen funktioniert mit Test-Stream

📖 **Pflicht-Lektüre:**
- `v2-docs/V1_VS_V2_ANALYSIS_REPORT.md` - Tier 1/2 Komponenten, Appendix A (File Mapping)
- `v2-docs/IMPLEMENTATION_PHASES_V2.md` - Phase 2 Beschreibung
- `v2-docs/AGENTS_V2.md` - v2 Execution Rules

---

## Phase 2 - Modul-Übersicht

### 2.1 Pipeline Module (Stubs → Minimal funktionsfähig)

| Modul | Status | Deliverables | Aufwand |
|-------|--------|--------------|---------|
| `:pipeline:xtream` | 📦 Stub | XtreamClient Port, XtreamConfig, XtreamContent Mapper | 1-2 Tage |
| `:pipeline:telegram` | 📦 Stub | T_TelegramServiceClient Port, TelegramContent Mapper | 1-2 Tage |
| `:pipeline:io` | 📦 Stub | LocalFileProvider, SAF Integration Stub | 0.5 Tag |
| `:pipeline:audiobook` | 📦 Stub | AudiobookScanner Stub (Phase 5+) | 0.5 Tag |

### 2.2 Core Module (Implementation)

| Modul | Status | Deliverables | Aufwand |
|-------|--------|--------------|---------|
| `:core:persistence` | ⚠️ Leer | ObxStore, Repositories (17k Zeilen v1-Port) | 2-3 Tage |

### 2.3 Playback Domain (Implementation)

| Modul | Status | Deliverables | Aufwand |
|-------|--------|--------------|---------|
| `:playback:domain` | ✅ Interfaces | DefaultResumeManager (echte Persistence), DefaultKidsPlaybackGate (echte Profile) | 1 Tag |

---

## Task 1: `:core:persistence` - ObjectBox Port

**Ziel:** ObxStore und alle Repositories aus v1 portieren.

### 1.1 ObjectBox Setup

**Dateien aus v1 (Tier 1 - Direct Port):**

```
v1: app/src/main/java/com/chris/m3usuite/data/obx/
→ v2: core/persistence/src/main/java/com/fishit/player/core/persistence/obx/

Port-Liste:
- ObxStore.kt (189 Zeilen) → BoxStore Singleton
- ObxEntities.kt (300+ Zeilen) → @Entity Definitionen:
  - ObxCategory
  - ObxLive
  - ObxVod
  - ObxSeries
  - ObxEpisode
  - ObxEpgNowNext
  - ObxProfile
  - ObxProfilePermissions
  - ObxResumeMark
  - ObxTelegramMessage
```

**Neue v2-spezifische Anpassungen:**

```kotlin
// core/persistence/src/main/java/com/fishit/player/core/persistence/ObxStoreProvider.kt

@Module
@InstallIn(SingletonComponent::class)
object ObxStoreModule {
    @Provides
    @Singleton
    fun provideBoxStore(@ApplicationContext context: Context): BoxStore {
        return ObxStore.get(context)
    }
}
```

**Gradle Setup:**

```kotlin
// core/persistence/build.gradle.kts

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("io.objectbox") version "5.0.1"
}

dependencies {
    implementation(project(":core:model"))
    
    implementation(libs.objectbox.android)
    implementation(libs.objectbox.kotlin)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
```

---

### 1.2 Repository Interfaces & Implementations

**Neue v2 Repository Interfaces:**

```kotlin
// core/persistence/src/main/java/com/fishit/player/core/persistence/repositories/

// 1. ProfileRepository.kt
interface ProfileRepository {
    suspend fun getCurrentProfile(): Profile?
    suspend fun getProfile(id: Long): Profile?
    suspend fun getAllProfiles(): List<Profile>
    suspend fun isKidsProfile(id: Long): Boolean
}

// 2. ResumeRepository.kt
interface ResumeRepository {
    suspend fun getResumePoint(contentId: String): ResumePoint?
    suspend fun saveResumePoint(contentId: String, positionMs: Long, durationMs: Long)
    suspend fun clearResumePoint(contentId: String)
    suspend fun getAllResumePoints(profileId: Long): List<ResumePoint>
}

// 3. ContentRepository.kt
interface ContentRepository {
    // Xtream Content
    suspend fun getVodById(id: Long): Vod?
    suspend fun getSeriesById(id: Long): Series?
    suspend fun getEpisode(seriesId: Long, season: Int, episode: Int): Episode?
    suspend fun getLiveById(id: Long): Live?
    
    // Telegram Content
    suspend fun getTelegramMessageById(id: Long): TelegramMessage?
}
```

**Default Implementations (ObjectBox-basiert):**

```kotlin
// core/persistence/src/main/java/com/fishit/player/core/persistence/repositories/obx/

class ObxProfileRepository @Inject constructor(
    private val boxStore: BoxStore
) : ProfileRepository {
    private val profileBox = boxStore.boxFor(ObxProfile::class.java)
    
    override suspend fun getCurrentProfile(): Profile? = withContext(Dispatchers.IO) {
        // Port v1 logic from ProfileObxRepository
    }
    // ... implementation
}

class ObxResumeRepository @Inject constructor(
    private val boxStore: BoxStore
) : ResumeRepository {
    private val resumeBox = boxStore.boxFor(ObxResumeMark::class.java)
    
    override suspend fun getResumePoint(contentId: String): ResumePoint? = withContext(Dispatchers.IO) {
        // Port v1 logic from ResumeRepository
    }
    // ... implementation
}

class ObxContentRepository @Inject constructor(
    private val boxStore: BoxStore
) : ContentRepository {
    private val vodBox = boxStore.boxFor(ObxVod::class.java)
    private val seriesBox = boxStore.boxFor(ObxSeries::class.java)
    private val episodeBox = boxStore.boxFor(ObxEpisode::class.java)
    private val liveBox = boxStore.boxFor(ObxLive::class.java)
    
    // Port v1 queries from XtreamObxRepository
    // ... implementation
}
```

**Hilt DI Bindings:**

```kotlin
// core/persistence/src/main/java/com/fishit/player/core/persistence/di/

@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceModule {
    
    @Binds
    abstract fun bindProfileRepository(impl: ObxProfileRepository): ProfileRepository
    
    @Binds
    abstract fun bindResumeRepository(impl: ObxResumeRepository): ResumeRepository
    
    @Binds
    abstract fun bindContentRepository(impl: ObxContentRepository): ContentRepository
}
```

---

### 1.3 Data Models Mapping

**v1 → v2 Model Conversion:**

```kotlin
// core/persistence/src/main/java/com/fishit/player/core/persistence/mappers/

// ObxProfile → Profile (core:model)
fun ObxProfile.toCoreProfile(): Profile = Profile(
    id = this.id,
    name = this.name,
    type = this.type,
    // ... mapping
)

// ObxResumeMark → ResumePoint (core:model)
fun ObxResumeMark.toResumePoint(): ResumePoint = ResumePoint(
    contentId = this.key,
    positionMs = this.positionMs,
    durationMs = this.durationMs,
    updatedAtMillis = this.updatedAtMillis
)

// ObxVod → PlaybackContext
fun ObxVod.toPlaybackContext(): PlaybackContext = PlaybackContext(
    type = PlaybackType.VOD,
    uri = this.url,
    title = this.name,
    posterUrl = this.logoUrl,
    contentId = this.id.toString(),
    // ... mapping
)
```

---

### Task 1 Checkliste

**Phase 2.1 - Core Persistence Setup:**

- [ ] `core/persistence/build.gradle.kts` mit ObjectBox Plugin
- [ ] ObxStore.kt portieren aus v1
- [ ] ObxEntities.kt portieren aus v1 (alle 10 Entities)
- [ ] ObxStoreModule für Hilt DI

**Phase 2.2 - Repository Interfaces:**

- [ ] ProfileRepository Interface
- [ ] ResumeRepository Interface
- [ ] ContentRepository Interface
- [ ] Mapper Extensions (ObxEntity → CoreModel)

**Phase 2.3 - Repository Implementations:**

- [ ] ObxProfileRepository (v1: ProfileObxRepository.kt)
- [ ] ObxResumeRepository (v1: ResumeRepository.kt)
- [ ] ObxContentRepository (v1: XtreamObxRepository.kt queries)
- [ ] PersistenceModule für Hilt Bindings

**Phase 2.4 - Testing:**

- [ ] ObxProfileRepositoryTest
- [ ] ObxResumeRepositoryTest
- [ ] ObxContentRepositoryTest
- [ ] Integration Test mit echten v1-Daten

---

## Task 2: `:pipeline:xtream` - Xtream Pipeline Stub

**Ziel:** Minimal funktionsfähiger Xtream Content Provider.

### 2.1 Xtream Client Port (Tier 1 - Direct Port)

**Dateien aus v1:**

```
v1: app/src/main/java/com/chris/m3usuite/core/xtream/
→ v2: pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/

Port-Liste:
- XtreamClient.kt (450 Zeilen) - HTTP Client für Xtream API
- XtreamConfig.kt (60 Zeilen) - Host/Port/User/Pass
- XtreamModels.kt (200 Zeilen) - Data Classes für API Responses
```

**v2-spezifische Abstractions:**

```kotlin
// pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/XtreamContentProvider.kt

interface XtreamContentProvider {
    suspend fun getVodStream(vodId: Long): PlaybackContext?
    suspend fun getSeriesStream(seriesId: Long, season: Int, episode: Int): PlaybackContext?
    suspend fun getLiveStream(liveId: Long): PlaybackContext?
    suspend fun isAvailable(): Boolean
}

class DefaultXtreamContentProvider @Inject constructor(
    private val client: XtreamClient,
    private val contentRepository: ContentRepository
) : XtreamContentProvider {
    
    override suspend fun getVodStream(vodId: Long): PlaybackContext? {
        val vod = contentRepository.getVodById(vodId) ?: return null
        // Build PlaybackContext from ObxVod
        return vod.toPlaybackContext()
    }
    
    // ... implementation für Series & Live
}
```

**Hilt Module:**

```kotlin
// pipeline/xtream/src/main/java/com/fishit/player/pipeline/xtream/di/

@Module
@InstallIn(SingletonComponent::class)
object XtreamModule {
    
    @Provides
    @Singleton
    fun provideXtreamClient(): XtreamClient {
        // Port v1 XtreamClient mit OkHttp
        return XtreamClient()
    }
    
    @Binds
    abstract fun bindXtreamContentProvider(
        impl: DefaultXtreamContentProvider
    ): XtreamContentProvider
}
```

---

### 2.2 Xtream Content Loading Integration

**Feature Home Integration:**

```kotlin
// feature/home/src/main/java/com/fishit/player/feature/home/HomeViewModel.kt

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val xtreamProvider: XtreamContentProvider,
    private val contentRepository: ContentRepository
) : ViewModel() {
    
    val recentVods: StateFlow<List<Vod>> = flow {
        // Load from contentRepository
        emit(contentRepository.getRecentVods())
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    fun playVod(vodId: Long) {
        viewModelScope.launch {
            val context = xtreamProvider.getVodStream(vodId)
            if (context != null) {
                // Navigate to InternalPlayerEntry
            }
        }
    }
}
```

---

### Task 2 Checkliste

**Phase 2.5 - Xtream Pipeline:**

- [ ] `pipeline/xtream/build.gradle.kts` Setup
- [ ] XtreamClient.kt portieren (v1: 450 Zeilen)
- [ ] XtreamConfig.kt portieren
- [ ] XtreamModels.kt portieren
- [ ] XtreamContentProvider Interface & Implementation
- [ ] XtreamModule für Hilt DI
- [ ] Integration mit ContentRepository

**Phase 2.6 - Testing:**

- [ ] XtreamClientTest (Mock HTTP)
- [ ] XtreamContentProviderTest
- [ ] Integration Test mit echten Xtream-Daten

---

## Task 3: `:pipeline:telegram` - Telegram Pipeline Stub

**Ziel:** Minimal funktionsfähiger Telegram Content Provider.

### 3.1 TelegramServiceClient Port (Tier 1 - Direct Port)

**Dateien aus v1:**

```
v1: app/src/main/java/com/chris/m3usuite/telegram/core/
→ v2: pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/

Port-Liste:
- T_TelegramServiceClient.kt (1621 Zeilen) - TDLib Wrapper
- T_TelegramSession.kt (350 Zeilen) - Auth State Management
- T_TelegramFileDownloader.kt (800 Zeilen) - Download Queue
- TelegramFileDataSource.kt (413 Zeilen) - Media3 DataSource
```

**v2-spezifische Abstractions:**

```kotlin
// pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/TelegramContentProvider.kt

interface TelegramContentProvider {
    suspend fun getTelegramStream(messageId: Long): PlaybackContext?
    suspend fun isAuthenticated(): Boolean
    suspend fun getAuthState(): TelegramAuthState
}

class DefaultTelegramContentProvider @Inject constructor(
    private val serviceClient: T_TelegramServiceClient,
    private val contentRepository: ContentRepository
) : TelegramContentProvider {
    
    override suspend fun getTelegramStream(messageId: Long): PlaybackContext? {
        val message = contentRepository.getTelegramMessageById(messageId) ?: return null
        // Build PlaybackContext mit tg:// URL
        return message.toPlaybackContext()
    }
    
    // ... implementation
}
```

---

### 3.2 Telegram File DataSource Integration

**DelegatingDataSourceFactory Port:**

```kotlin
// pipeline/telegram/src/main/java/com/fishit/player/pipeline/telegram/TelegramDataSourceFactory.kt

class TelegramDataSourceFactory @Inject constructor(
    private val serviceClient: T_TelegramServiceClient
) : DataSource.Factory {
    
    override fun createDataSource(): DataSource {
        return TelegramFileDataSource(serviceClient)
    }
}
```

**Player Internal Integration:**

```kotlin
// player/internal/src/main/java/com/fishit/player/internal/source/InternalPlaybackSourceResolver.kt

class InternalPlaybackSourceResolver @Inject constructor(
    private val telegramDataSourceFactory: TelegramDataSourceFactory
) {
    
    fun resolve(playbackContext: PlaybackContext): MediaItem {
        val dataSourceFactory = when {
            playbackContext.uri.startsWith("tg://") -> telegramDataSourceFactory
            else -> DefaultHttpDataSource.Factory()
        }
        
        return MediaItem.Builder()
            .setUri(playbackContext.uri)
            .build()
    }
}
```

---

### Task 3 Checkliste

**Phase 2.7 - Telegram Pipeline:**

- [ ] `pipeline/telegram/build.gradle.kts` Setup
- [ ] T_TelegramServiceClient.kt portieren (v1: 1621 Zeilen)
- [ ] T_TelegramSession.kt portieren
- [ ] T_TelegramFileDownloader.kt portieren
- [ ] TelegramFileDataSource.kt portieren
- [ ] TelegramContentProvider Interface & Implementation
- [ ] TelegramDataSourceFactory
- [ ] TelegramModule für Hilt DI

**Phase 2.8 - Testing:**

- [ ] T_TelegramServiceClientTest
- [ ] TelegramFileDataSourceTest
- [ ] TelegramContentProviderTest
- [ ] Integration Test mit echtem TDLib (Mock)

---

## Task 4: `:pipeline:io` - IO Pipeline Stub

**Ziel:** Grundlegende lokale File-Unterstützung.

### 4.1 LocalFileProvider

```kotlin
// pipeline/io/src/main/java/com/fishit/player/pipeline/io/LocalFileProvider.kt

interface LocalFileProvider {
    suspend fun getLocalMedia(uri: Uri): PlaybackContext?
    suspend fun scanDirectory(directory: File): List<LocalMedia>
}

class DefaultLocalFileProvider @Inject constructor() : LocalFileProvider {
    
    override suspend fun getLocalMedia(uri: Uri): PlaybackContext? {
        // Basic file:// and content:// support
        return PlaybackContext(
            type = PlaybackType.VOD,
            uri = uri.toString(),
            title = uri.lastPathSegment ?: "Unknown",
            contentId = uri.toString()
        )
    }
}
```

---

### Task 4 Checkliste

**Phase 2.9 - IO Pipeline Stub:**

- [ ] `pipeline/io/build.gradle.kts` Setup
- [ ] LocalFileProvider Interface & Implementation
- [ ] SAF (Storage Access Framework) Stub
- [ ] IOModule für Hilt DI
- [ ] Basic Testing

---

## Task 5: `:pipeline:audiobook` - Audiobook Pipeline Stub

**Ziel:** Minimal-Stub für zukünftige Audiobook-Unterstützung.

### 5.1 Audiobook Provider Stub

```kotlin
// pipeline/audiobook/src/main/java/com/fishit/player/pipeline/audiobook/AudiobookProvider.kt

interface AudiobookProvider {
    suspend fun getAudiobook(id: String): PlaybackContext?
    // Phase 5+ implementation
}

class DefaultAudiobookProvider @Inject constructor() : AudiobookProvider {
    override suspend fun getAudiobook(id: String): PlaybackContext? {
        // TODO(Phase 5): Implement audiobook support
        return null
    }
}
```

---

### Task 5 Checkliste

**Phase 2.10 - Audiobook Pipeline Stub:**

- [ ] `pipeline/audiobook/build.gradle.kts` Setup
- [ ] AudiobookProvider Interface (Stub)
- [ ] AudiobookModule für Hilt DI
- [ ] Platzhalter für Phase 5

---

## Task 6: Playback Domain Implementation mit echten Repositories

**Ziel:** DefaultResumeManager & DefaultKidsPlaybackGate mit echten Repositories.

### 6.1 DefaultResumeManager Implementation

```kotlin
// playback/domain/src/main/java/com/fishit/player/playback/domain/defaults/DefaultResumeManager.kt

class DefaultResumeManager @Inject constructor(
    private val resumeRepository: ResumeRepository
) : ResumeManager {
    
    override suspend fun getResumePoint(contentId: String): ResumePoint? {
        return resumeRepository.getResumePoint(contentId)
    }
    
    override suspend fun saveResumePoint(
        context: PlaybackContext,
        positionMs: Long,
        durationMs: Long
    ) {
        val contentId = context.contentId ?: return
        
        // v1 Resume Rules:
        // - Only save if position > 10s
        // - Clear if remaining < 10s
        if (positionMs < 10_000) return
        val remaining = durationMs - positionMs
        if (remaining < 10_000) {
            resumeRepository.clearResumePoint(contentId)
        } else {
            resumeRepository.saveResumePoint(contentId, positionMs, durationMs)
        }
    }
    
    override suspend fun clearResumePoint(contentId: String) {
        resumeRepository.clearResumePoint(contentId)
    }
    
    override suspend fun getAllResumePoints(): List<ResumePoint> {
        // TODO: Get current profile ID
        return resumeRepository.getAllResumePoints(profileId = 0L)
    }
}
```

---

### 6.2 DefaultKidsPlaybackGate Implementation

```kotlin
// playback/domain/src/main/java/com/fishit/player/playback/domain/defaults/DefaultKidsPlaybackGate.kt

class DefaultKidsPlaybackGate @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val screenTimeRepository: ScreenTimeRepository
) : KidsPlaybackGate {
    
    override suspend fun evaluateStart(): KidsGateState {
        // TODO: Get current profile ID from SettingsStore
        val currentProfileId = 0L // Placeholder
        
        val isKids = profileRepository.isKidsProfile(currentProfileId)
        if (!isKids) {
            return KidsGateState(
                kidActive = false,
                kidBlocked = false,
                kidProfileId = null,
                remainingMinutes = null
            )
        }
        
        val remainingMinutes = screenTimeRepository.remainingMinutes(currentProfileId)
        return KidsGateState(
            kidActive = true,
            kidBlocked = remainingMinutes <= 0,
            kidProfileId = currentProfileId,
            remainingMinutes = remainingMinutes
        )
    }
    
    override suspend fun onPlaybackTick(
        currentState: KidsGateState,
        deltaSecs: Int
    ): KidsGateState {
        if (!currentState.kidActive) return currentState
        
        val profileId = currentState.kidProfileId ?: return currentState
        screenTimeRepository.tickUsageIfPlaying(profileId, deltaSecs)
        
        val newRemaining = screenTimeRepository.remainingMinutes(profileId)
        return currentState.copy(
            kidBlocked = newRemaining <= 0,
            remainingMinutes = newRemaining
        )
    }
}
```

---

### Task 6 Checkliste

**Phase 2.11 - Playback Domain Implementation:**

- [ ] DefaultResumeManager mit ResumeRepository
- [ ] DefaultKidsPlaybackGate mit ProfileRepository + ScreenTimeRepository
- [ ] Update PlaybackDomainModule Hilt Bindings
- [ ] Testing mit echten Repositories (Mock)

---

## Task 7: End-to-End Integration Test

**Ziel:** Testen dass der komplette Flow funktioniert.

### 7.1 Feature Home → Xtream Content → Player

**Test-Scenario:**

```kotlin
// feature/home/src/test/java/com/fishit/player/feature/home/

@HiltAndroidTest
class Phase2IntegrationTest {
    
    @Test
    fun `Load Xtream VOD from DB and play`() = runTest {
        // Given: VOD in ObxStore
        val vod = createTestVod(id = 1L, url = "http://test.m3u8")
        contentRepository.insertVod(vod)
        
        // When: Request playback
        val context = xtreamProvider.getVodStream(vodId = 1L)
        
        // Then: Valid PlaybackContext
        assertThat(context).isNotNull()
        assertThat(context?.type).isEqualTo(PlaybackType.VOD)
        assertThat(context?.uri).isEqualTo("http://test.m3u8")
    }
    
    @Test
    fun `Resume point is saved during playback`() = runTest {
        // Given: Playback at 30 seconds
        val context = PlaybackContext.testVod("http://test.m3u8")
        
        // When: Save resume
        resumeManager.saveResumePoint(context, positionMs = 30_000, durationMs = 120_000)
        
        // Then: Resume point exists
        val resume = resumeManager.getResumePoint(context.contentId!!)
        assertThat(resume?.positionMs).isEqualTo(30_000)
    }
    
    @Test
    fun `Kids profile blocks playback when quota exhausted`() = runTest {
        // Given: Kids profile with 0 minutes
        setupKidsProfile(id = 1L, remainingMinutes = 0)
        
        // When: Evaluate gate
        val state = kidsPlaybackGate.evaluateStart()
        
        // Then: Blocked
        assertThat(state.kidBlocked).isTrue()
    }
}
```

---

### Task 7 Checkliste

**Phase 2.12 - Integration Testing:**

- [ ] Xtream Content Loading E2E Test
- [ ] Telegram Content Loading E2E Test (Mock TDLib)
- [ ] Resume Point Persistence Test
- [ ] Kids Gate Integration Test
- [ ] Player → Pipeline → Persistence Round-Trip Test

---

## Acceptance Criteria

Phase 2 ist **abgeschlossen** wenn:

### Funktional:

✅ **Core Persistence:**
- [ ] ObxStore aus v1 portiert und läuft
- [ ] Alle 10 ObjectBox Entities funktionieren
- [ ] Repositories liefern echte Daten aus DB

✅ **Pipeline Xtream:**
- [ ] XtreamClient kann Xtream-Content laden
- [ ] PlaybackContext wird korrekt aus ObxVod gebaut
- [ ] URL-Resolution funktioniert

✅ **Pipeline Telegram:**
- [ ] T_TelegramServiceClient ist portiert
- [ ] TelegramFileDataSource funktioniert
- [ ] tg:// URLs können abgespielt werden

✅ **Pipeline IO:**
- [ ] Lokale file:// URLs funktionieren
- [ ] Basis SAF-Unterstützung

✅ **Playback Domain:**
- [ ] ResumeManager speichert/lädt aus DB
- [ ] KidsPlaybackGate prüft echte Profile
- [ ] Screen-Time Enforcement funktioniert

### Qualität:

✅ **Tests:**
- [ ] Mindestens 80% Code Coverage für neue Klassen
- [ ] Alle Repository-Tests grün
- [ ] Integration Tests funktionieren
- [ ] E2E Test: VOD aus DB → Player funktioniert

✅ **Build:**
- [ ] `./gradlew :core:persistence:build` erfolgreich
- [ ] `./gradlew :pipeline:xtream:build` erfolgreich
- [ ] `./gradlew :pipeline:telegram:build` erfolgreich
- [ ] Keine Hilt DI-Fehler

✅ **Dokumentation:**
- [ ] Alle neuen Klassen haben KDoc
- [ ] README in jedem Modul aktualisiert
- [ ] CHANGELOG.md für Phase 2 Update

---

## Abhängigkeiten & Risiken

### Externe Dependencies:

**ObjectBox:**
- Version: 5.0.1 (wie v1)
- Risiko: NIEDRIG (bewährt in v1)

**TDLib (tdlib-coroutines):**
- Version: g00sha (wie v1)
- Risiko: NIEDRIG (bewährt in v1)

**Media3:**
- Version: 1.8.0+ (wie v1)
- Risiko: NIEDRIG (Standard)

### Technische Risiken:

⚠️ **MITTEL:** ObjectBox Migration
- **Problem:** ObjectBox Entities müssen binary-kompatibel bleiben
- **Mitigation:** Namespace beibehalten, schrittweise Migration

⚠️ **NIEDRIG:** Hilt DI Scope
- **Problem:** Repository Lifecycles korrekt definieren
- **Mitigation:** Singleton für alle Repositories

⚠️ **NIEDRIG:** TDLib Native Libs
- **Problem:** Native .so Dateien korrekt linken
- **Mitigation:** Gradle Plugin aus v1 übernehmen

---

## Next Steps nach Phase 2

**Phase 3: Feature Shells** (3-5 Tage)
- `:feature:library` - Content Browser
- `:feature:live` - Live TV UI
- `:feature:settings` - Settings Screen
- `:feature:telegram-media` - Telegram Browser

**Phase 4: UI Polish** (2-3 Tage)
- Fish* Layout System Port
- FocusKit Integration
- DetailScaffold Port

**Phase 5: Firebase Integration** (1-2 Tage)
- `:core:firebase` - Feature Flags, Remote Config
- Optional/Offline-First

---

## Referenzen

**v1 Dateien (Port-Quellen):**
- `app/src/main/java/com/chris/m3usuite/data/obx/` → Core Persistence
- `app/src/main/java/com/chris/m3usuite/core/xtream/` → Xtream Pipeline
- `app/src/main/java/com/chris/m3usuite/telegram/core/` → Telegram Pipeline

**v2 Dokumentation:**
- `v2-docs/V1_VS_V2_ANALYSIS_REPORT.md` - Appendix A (File Mapping)
- `v2-docs/IMPLEMENTATION_PHASES_V2.md` - Phase 2 Details
- `docs/V2_BOOTSTRAP_REVIEW_2025-12-05.md` - Phase 0-1 Review

**Contracts:**
- `docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md` - Resume Rules
- `docs/INTERNAL_PLAYER_REFACTOR_SSOT.md` - SIP Architecture

---

**Erstellt:** 2025-12-05  
**Autor:** GitHub Copilot Agent  
**Basis:** Phase 0-1 Review + v2 Documentation  
**Status:** ✅ READY FOR IMPLEMENTATION
