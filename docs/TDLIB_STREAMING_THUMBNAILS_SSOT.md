# TDLib Streaming & Thumbnails – Single Source of Truth

> **Created:** 2025-12-02  
> **Purpose:** Konsolidiertes Wissen über TDLib-Coroutines-Integration, Streaming-Architektur, Thumbnail-Mechanismen und Runtime-Konfiguration  
> **Status:** ACTIVE – Single Source of Truth für alle TDLib/Telegram Streaming & Thumbnail-Features  
> **Basis:** `.github/tdlibAgent.md` + PR #410-#412 + Changelog Nov-Dec 2025

---

## Inhaltsverzeichnis

1. [Zeitleiste & Meilensteine](#zeitleiste--meilensteine)
2. [TDLib-Integration: g00sha's tdl-coroutines](#tdlib-integration-g00shas-tdl-coroutines)
3. [Zero-Copy Streaming-Architektur](#zero-copy-streaming-architektur)
4. [Thumbnail-System](#thumbnail-system)
5. [Runtime Settings Framework](#runtime-settings-framework)
6. [Concurrency & Download-Management](#concurrency--download-management)
7. [Player-Integration](#player-integration)
8. [Erkenntnisse & Best Practices](#erkenntnisse--best-practices)
9. [Offene TODOs & Roadmap](#offene-todos--roadmap)

---

## Zeitleiste & Meilensteine

### Phase 1: Infrastruktur-Migration (Nov 2025)

**18. Nov 2025 – Commit d67da684:**

- ✅ **Integration von `dev.g000sha256:tdl-coroutines-android:5.0.0`** aus Maven Central
- ✅ Entfernung eigener TDLib-Builds (6305 Zeilen Code gelöscht)
- ✅ Löschung aller 12 TDLib-Build-Workflows
- ✅ Entfernung `libtd/` Modul
- ✅ Native Binaries für arm64-v8a + armeabi-v7a mit statischem BoringSSL-Linking

**Rationale:**

- Wartbare, reproduzierbare TDLib-Version
- Keine Custom-Builds mehr nötig
- Upstream-Updates über Maven Central

### Phase 2: Streaming-Refaktor (Nov-Dez 2025)

**30. Nov – 01. Dez 2025:**

- ✅ **RemoteId-first Resolution** für Thumbnails (PR #403)
  - FileId ist volatil und wird bei TDLib-Session-Rotation ungültig
  - RemoteId ist stabil über Sessions hinweg
  - Alle Thumbnail-Pfade nutzen jetzt `TelegramImageRef` mit `remoteId`

**01. Dez 2025 – PR #405:**

- ✅ **Windowed Streaming** mit 16 MB Fenstern (statt Full-Download)
- ✅ `ensureFileReady()` mit `INITIAL_START` / `SEEK` Modes
- ✅ Zero-Copy reads via `RandomAccessFile` aus TDLib-Cache
- ✅ Prefetch-Margin (4 MB) für nahtloses Weiterscrollen

**01. Dez 2025 – PR #406:**

- ✅ Telegram Playback Logging & Buffering Watchdog
- ✅ Engine Health State Separation

**01. Dez 2025 – PR #408:**

- ✅ `TelegramPlaybackRequest` erweitert (Duration, FileSize)
- ✅ `INITIAL_START` / `SEEK` Modes für optimiertes Streaming

**01. Dez 2025 – PR #409:**

- ✅ `minBytes` Override für Full-Downloads (z.B. Thumbnails)
- ✅ Backwards-kompatibles URL-Parsing

### Phase 3: Runtime Settings (Dez 2025)

**02. Dez 2025 – PR #410:**

- ✅ **25+ Runtime Settings** für TDLib, Streaming, ExoPlayer, Thumbnails
- ✅ UI in Advanced Settings (Sliders, Switches, Spinners)
- ✅ `SettingsStore` Persistierung via DataStore
- ✅ TDLib Log-Level zur Laufzeit änderbar

**02. Dez 2025 – PR #411:**

- ✅ `TelegramStreamingSettings` Domain-Klasse
- ✅ `TelegramStreamingSettingsProvider` mit `StateFlow`
- ✅ KB→Bytes / sec→ms Konvertierung im Provider

**02. Dez 2025 – PR #412 (WIP):**

- ✅ **Phase 2:** Download Concurrency Enforcement
  - FIFO-Queues für VIDEO/THUMB Downloads
  - Runtime Limits: maxGlobalDownloads, maxVideoDownloads, maxThumbDownloads
- ✅ **Phase 3:** `ensureFileReady()` Runtime-driven
  - Nutzt `settings.initialMinPrefixBytes` / `seekMarginBytes` / `ensureFileReadyTimeoutMs`
- ✅ **Phase 4:** Thumbnail Prefetch Integration
  - Semaphore mit `thumbMaxParallel`
  - Batch Size via `thumbPrefetchBatchSize`
- ✅ **Phase 4b:** Playback-aware Prefetch + Full-Download Toggle
  - Prefetch pausiert automatisch, wenn `PlaybackSession` Telegram VOD Buffering meldet
  - `thumbFullDownload` Schalter zwingt `ensureFileReady()` zu vollständigen Thumbnail-Downloads
- ✅ **Phase 5:** ExoPlayer LoadControl Builder
  - `buildTelegramLoadControl()` mit Runtime Buffer-Sizes
- ✅ **Phase 5b:** Player Wiring
  - `InternalPlayerSession` nutzt Runtime LoadControl + `exoExactSeek` Toggle für Telegram Playback
- ⏳ **Phase 6-9:** Overlays, Logging, Telemetry (pending)

---

## TDLib-Integration: g00sha's tdl-coroutines

### Warum tdl-coroutines?

**Upstream-Library:** `dev.g000sha256:tdl-coroutines-android:5.0.0`

- ✅ Offizielle Kotlin-Coroutine-Bindings für TDLib
- ✅ Flow-basierte Updates (`authorizationStateUpdates`, `fileUpdates`)
- ✅ Suspend-Functions statt Callbacks
- ✅ AAR mit Native Libraries für arm64-v8a + armeabi-v7a
- ✅ Statisches BoringSSL-Linking (keine externen .so-Dependencies)

**Vorteile gegenüber Custom-Builds:**

- Reproduzierbare Versionen über Maven Central
- Keine 6000+ Zeilen Build-Scripts/Workflows
- Automatische Updates via Gradle-Dependencies
- Community-Support & Bugfixes vom Upstream

### Package-Struktur

```
telegram/
├── core/                           # TDLib-Core-Wrappers
│   ├── T_TelegramServiceClient.kt  # Unified Telegram Engine (Singleton)
│   ├── T_TelegramSession.kt        # Auth State Management
│   ├── T_ChatBrowser.kt            # Chat & Message Browsing
│   ├── T_TelegramFileDownloader.kt # File Downloads + Windowing
│   └── TelegramFileLoader.kt       # High-Level File API
├── domain/                         # Domain Models
│   ├── TelegramStreamingSettings.kt        # Runtime Config Data Class
│   └── TelegramStreamingSettingsProvider.kt # Settings Flow Provider
├── player/                         # Media3 Integration
│   ├── TelegramFileDataSource.kt   # Media3 DataSource für tg:// URLs
│   ├── TelegramPlaybackRequest.kt  # Playback-Kontext (Duration, Size)
│   ├── TelegramExceptions.kt       # Custom Exceptions
│   └── TelegramLoadControlBuilder.kt # ExoPlayer LoadControl
├── prefetch/                       # Background Thumbnail Prefetch
│   └── TelegramThumbPrefetcher.kt  # Prefetcher mit Semaphore
└── util/
    └── TelegramPlayUrl.kt          # URL-Schema: tg://file/<fileId>
```

### TDLib Client Lifecycle

```
┌──────────────────────────────────────────────────────────────┐
│ App.onCreate()                                               │
│   └─> T_TelegramServiceClient.getInstance(context)          │
│        └─> Singleton Pattern (companion object)             │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│ T_TelegramServiceClient.ensureStarted(context, settingsStore)│
│   ├─> Check if already started (isStarted.get())            │
│   ├─> Load TDLib config via ConfigLoader                    │
│   ├─> Create TdlClient(config)                              │
│   ├─> Create T_TelegramSession(client)                      │
│   ├─> Create T_ChatBrowser(session)                         │
│   ├─> Create T_TelegramFileDownloader(session, settings)    │
│   └─> Subscribe to Update Flows                             │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│ T_TelegramSession.login()                                    │
│   ├─> If TDLib DB has valid session → AuthReady             │
│   ├─> Else: WaitPhoneNumber → WaitCode → WaitPassword       │
│   └─> Emit AuthEvents via SharedFlow                        │
└──────────────────────────────────────────────────────────────┘
```

**Wichtig:**

- **Singleton** bleibt über App-Lifecycle erhalten
- **TDLib DB** ist persistent (`context.filesDir/tdlib`)
- **Auto-Reconnect** bei Netzwerkwechsel
- **Session Rotation:** FileId wird ungültig, RemoteId bleibt stabil

---

## Zero-Copy Streaming-Architektur

### Grundprinzip

**"Zero-Copy"** bezieht sich auf den **App-Layer**, nicht TDLib:

- TDLib cached Mediendateien weiterhin auf Disk (unvermeidbar)
- App liest **direkt** aus TDLib-Cache via `RandomAccessFile`
- **Keine** zusätzlichen Kopien zwischen TDLib-Cache und Player-Buffer
- **Keine** App-seitigen Ringbuffer oder In-Memory-Kopien

### Windowed Streaming (16 MB Fenster)

**Ziel:** Nur notwendige Teile einer großen Datei herunterladen

**Implementierung:**

```kotlin
// In T_TelegramFileDownloader.kt

data class WindowState(
    val fileId: Int,
    var windowStart: Long,
    var windowSize: Long,
    var localSize: Long = 0,
    var isComplete: Boolean = false
)

suspend fun ensureWindow(
    fileIdInt: Int,
    windowStart: Long,
    windowSize: Long = 16 * 1024 * 1024L  // 16 MB
): Boolean
```

**Flow:**

1. **Position bestimmen:** `windowStart = seekPosition`
2. **Fenster prüfen:** Liegt aktuelle Position im Fenster?
3. **Falls nein:** Altes Fenster canceln, neues Fenster öffnen
4. **TDLib Download:** `downloadFile(offset=windowStart, limit=windowSize)`
5. **Polling:** Warten bis `downloadedPrefixSize >= required`

**Window Transition:**

```
Position: 12 MB ───────────────────────────────────────────────────> 90 MB
           │                                                            │
           ▼                                                            ▼
Window 1:  [0 ──────── 16 MB]
                                     Window 2:  [90 MB ──────── 106 MB]
                                                    │
                                     Prefetch Margin: 4 MB vorher starten
```

### ensureFileReady() Modes

**INITIAL_START Mode:**

- Für ersten Playback-Start (`dataSpecPosition == 0`)
- Benötigt nur kleinen Prefix (Standard: 256 KB, Runtime: `settings.initialMinPrefixBytes`)
- **Rationale:** Schneller Playback-Start, Rest lädt im Hintergrund

**SEEK Mode:**

- Für Seek-Operationen (`dataSpecPosition > 0`)
- Benötigt `startPosition + margin` (Standard: 1 MB, Runtime: `settings.seekMarginBytes`)
- **Rationale:** Nach Seek nur Puffer rund um neue Position, nicht bis EOF

**Beispiel:**

```kotlin
val requiredByMode = when (mode) {
    INITIAL_START -> settings.initialMinPrefixBytes  // 256 KB
    SEEK -> startPosition + settings.seekMarginBytes  // +1 MB
}

val windowSize = minOf(requiredByMode, 50 * 1024 * 1024L) // Max 50 MB
```

### File Handle Caching

**Warum?** Zero-Copy reads direkt aus TDLib-Cache:

```kotlin
// In T_TelegramFileDownloader.kt
private val fileHandleCache = ConcurrentHashMap<Int, RandomAccessFile>()

suspend fun readFileChunk(
    fileId: String,
    position: Long,
    buffer: ByteArray,
    offset: Int,
    length: Int
): Int = withContext(Dispatchers.IO) {
    val raf = fileHandleCache.getOrPut(fileIdInt) {
        RandomAccessFile(localPath, "r")
    }
    raf.seek(position)
    raf.read(buffer, offset, length)
}
```

**Retry-Logik:**

- Bei `IOException` → Handle aus Cache entfernen
- Bis zu 3 Versuche
- Verhindert Stale-Handle-Probleme

---

## Thumbnail-System

### RemoteId-First Architecture

**Problem:** FileId ist volatil

- TDLib kann FileId bei Session-Rotation ändern
- FileId ist TDLib-intern, nicht persistent
- Nach App-Neustart oder Session-Reset werden FileIds neu vergeben

**Lösung:** RemoteId ist stabil

```kotlin
data class TelegramImageRef(
    val fileId: Int?,       // Volatile, kann null sein
    val remoteId: String,   // STABLE, eindeutig
    val uniqueId: String?   // Optional, zusätzliche Stabilität
)
```

**Resolution Flow:**

```
1. UI requests Thumbnail via remoteId
   └─> TelegramFileLoader.ensureThumbDownloaded(ref: TelegramImageRef)

2. Resolve FileId from RemoteId
   └─> client.getRemoteFile(remoteId)
       ├─> Success: FileId erhalten
       └─> Failure: Thumbnail nicht verfügbar

3. Download via FileId
   └─> ensureFileReady(fileId, startPos=0, minBytes=totalSize, mode=INITIAL_START)
       └─> Full-Download für Thumbnails (kein Windowing)

4. Cache local path
   └─> Return local file path für Coil
```

### Prefetch-System

**Ziel:** Thumbnails im Hintergrund laden, bevor User scrollt

**Implementierung:**

```kotlin
// In TelegramThumbPrefetcher.kt

class TelegramThumbPrefetcher(
    private val serviceClient: T_TelegramServiceClient,
    private val repository: TelegramContentRepository,
    private val settingsProvider: TelegramStreamingSettingsProvider,
) {
    private var downloadSemaphore: Semaphore? = null
    private val vodBufferingFlow: Flow<Boolean> =
        combine(
            PlaybackSession.buffering,
            PlaybackSession.currentSourceState,
        ) { isBuffering, source ->
            val src = source ?: return@combine false
            isBuffering && src.isTelegram && src.isVodLike
        }.distinctUntilChanged()

    suspend fun observeAndPrefetch() {
        val settings = settingsProvider.currentSettings

        // Gate: Skip if disabled
        if (!settings.thumbPrefetchEnabled) return

        // Init Semaphore with runtime limit
        downloadSemaphore = Semaphore(settings.thumbMaxParallel)

        combine(
            repository.observeVodItemsByChat(),
            vodBufferingFlow,
        ) { chatMap, vodBuffering -> chatMap to vodBuffering }
            .collectLatest { (chatMap, vodBuffering) ->
            val currentSettings = settingsProvider.currentSettings
            if (!currentSettings.thumbPrefetchEnabled) return@collectLatest
            if (currentSettings.thumbPauseWhileVodBuffering && vodBuffering) {
                TelegramLogRepository.debug(
                    source = "TelegramThumbPrefetcher",
                    message = "Prefetch paused – Telegram VOD buffering",
                )
                return@collectLatest
            }
            val posterRefs = chatMap.values.flatten()
                .mapNotNull { it.posterRef }
                .distinctBy { it.remoteId }
                .filter { it.remoteId !in prefetchedRemoteIds }
                .take(currentSettings.thumbPrefetchBatchSize)

            // Parallel download with Semaphore control
            coroutineScope {
                posterRefs.map { ref ->
                    async {
                        downloadSemaphore?.acquire()
                        try {
                            prefetchThumbnail(ref)
                        } finally {
                            downloadSemaphore?.release()
                        }
                    }
                }.awaitAll()
            }
        }
    }
}
```

**Key Features:**

- ✅ Semaphore-based Concurrency (verhindert Überlast)
- ✅ FIFO via `take(batchSize)`
- ✅ RemoteId-Dedup (keine doppelten Downloads)
- ✅ Runtime konfigurierbar (enable/disable, batch size, parallel limit)
- ✅ Optional Pause während Telegram VOD buffernt (PlaybackSession-Signal)
- ✅ Shared settings provider garantiert identische Limits für Prefetcher und FileLoader

### Thumbnail Download Modi

**Ziel:** Laufzeit-Schalter zwischen Prefix-Download (schneller, kleiner) und vollständigem Download für hochwertige Assets.

```kotlin
val settings = settingsProvider.currentSettings
val shouldDownloadFull = settings.thumbFullDownload && totalSizeBytes > 0L
val minBytes = when {
    shouldDownloadFull -> totalSizeBytes
    totalSizeBytes > 0L -> min(totalSizeBytes, settings.initialMinPrefixBytes)
    else -> settings.initialMinPrefixBytes
}.coerceAtLeast(MIN_THUMB_PREFIX_BYTES)

downloader.ensureFileReady(
    fileId = fileId,
    startPosition = 0,
    minBytes = minBytes,
    mode = INITIAL_START,
    fileSizeBytes = totalSizeBytes,
    timeoutMs = timeoutMs,
)
```

- `thumbFullDownload = true` zwingt vollständige Downloads, sobald TDLib die Dateigröße meldet.
- Fällt automatisch auf Prefix + Mindestgröße (64 KiB) zurück, wenn keine Dateigröße verfügbar ist.
- Telemetrie-Logs kennzeichnen Partial-Downloads, damit Diagnose-Tools erkennen, ob ein Voll-Download fehlte.

---

## Runtime Settings Framework

### Ziel

**Alle** Streaming/Download-Parameter zur Laufzeit änderbar, ohne App-Neustart

### Architektur

```
UI (SettingsScreen)
    │
    ▼
SettingsStore (DataStore Preferences)
    │
    ▼
SettingsRepository (Kotlin Flows)
    │
    ▼
TelegramStreamingSettingsProvider (Domain Layer)
    │   • Kombiniert alle Settings-Flows
    │   • Konvertiert Units (KB→Bytes, sec→ms)
    │   • Liefert StateFlow<TelegramStreamingSettings>
    │
    ▼
Engine Components (inject settingsProvider)
    ├─> T_TelegramFileDownloader
    ├─> TelegramThumbPrefetcher
    └─> TelegramLoadControlBuilder
```

### Settings Data Class

```kotlin
data class TelegramStreamingSettings(
    // TDLib
    val tdlibLogLevel: Int,                          // 0-5, ändert TDLib Verbosity

    // Download Concurrency
    val maxGlobalDownloads: Int,                     // Max parallel downloads (alle Typen)
    val maxVideoDownloads: Int,                      // Max parallel VOD/Episode downloads
    val maxThumbDownloads: Int,                      // Max parallel Thumbnail downloads

    // Streaming Windows
    val initialMinPrefixBytes: Long,                 // INITIAL_START prefix (default 256 KB)
    val seekMarginBytes: Long,                       // SEEK margin (default 1 MB)
    val ensureFileReadyTimeoutMs: Long,              // Timeout für Downloads (default 30s)

    // Thumbnail Prefetch
    val thumbPrefetchEnabled: Boolean,               // Enable/Disable prefetch
    val thumbPrefetchBatchSize: Int,                 // Items pro Batch (default 100)
    val thumbMaxParallel: Int,                       // Parallel Semaphore (default 3)
    val thumbPauseWhileVodBuffering: Boolean,        // Prefetch pausiert während Telegram VOD buffernt
    val thumbFullDownload: Boolean,                  // Vollständiger Thumbnail-Download statt Prefix

    // ExoPlayer LoadControl
    val exoMinBufferMs: Int,                         // Min buffer before playback
    val exoMaxBufferMs: Int,                         // Max buffer size
    val exoBufferForPlaybackMs: Int,                 // Buffer needed to start
    val exoBufferForPlaybackAfterRebufferMs: Int,    // Buffer after rebuffer
    val exoExactSeek: Boolean,                       // Exact vs nearest keyframe

    // Debug Overlays
    val showEngineOverlay: Boolean,                  // TDLib engine diagnostics (TODO Phase 6)
    val showStreamingOverlay: Boolean,               // Streaming window info (TODO Phase 6)

    // Logging
    val tgAppLogLevel: Int,                          // App-side Telegram log level (TODO Phase 7)
    val jankTelemetrySampleRate: Int                 // Sample rate für Telemetry (TODO Phase 8)
)
```

### UI-Integration

**Advanced Settings Screen:**

```kotlin
// In SettingsScreen.kt - TelegramAdvancedSettingsSection

Column {
    // TDLib Log Level
    Text("TDLib Log-Level")
    Slider(
        value = tgLogVerbosity.toFloat(),
        onValueChange = { viewModel.setTgLogVerbosity(it.toInt()) },
        valueRange = 0f..5f,
        steps = 4
    )

    // Download Concurrency
    Text("Max Globale Downloads")
    Spinner(
        value = maxGlobal,
        options = listOf(1, 2, 3, 5, 10),
        onValueChange = { viewModel.setMaxGlobalDownloads(it) }
    )

    // Streaming Prefix
    Text("Initial Prefix (KB)")
    Slider(
        value = initialPrefixKb.toFloat(),
        onValueChange = { viewModel.setInitialPrefixKb(it.toInt()) },
        valueRange = 64f..2048f
    )

    // Thumbnail Prefetch Toggle
    Switch(
        checked = thumbPrefetchEnabled,
        onCheckedChange = { viewModel.setThumbPrefetchEnabled(it) }
    )
}
```

**Persistence Layer:**

```kotlin
// In SettingsStore.kt

val tgLogVerbosity = dataStore.data.map { it[TG_LOG_VERBOSITY] ?: 2 }
val maxGlobalDownloads = dataStore.data.map { it[TG_MAX_GLOBAL_DOWNLOADS] ?: 5 }
val initialMinPrefixKb = dataStore.data.map { it[TG_INITIAL_MIN_PREFIX_KB] ?: 256 }
val thumbPrefetchEnabled = dataStore.data.map { it[TG_THUMB_PREFETCH_ENABLED] ?: true }
// ... 25+ weitere Keys
```

### Provider-Pattern

**TelegramStreamingSettingsProvider.kt:**

```kotlin
class TelegramStreamingSettingsProvider(
    private val settingsRepository: SettingsRepository
) {
    val settingsFlow: StateFlow<TelegramStreamingSettings> = combine(
        settingsRepository.tgLogVerbosity,
        settingsRepository.maxGlobalDownloads,
        settingsRepository.initialMinPrefixKb,
        // ... alle 25+ Flows
    ) { flows ->
        TelegramStreamingSettings(
            tdlibLogLevel = flows[0] as Int,
            maxGlobalDownloads = flows[1] as Int,
            initialMinPrefixBytes = (flows[2] as Int) * 1024L,  // KB → Bytes
            // ... alle Settings
        )
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
        started = SharingStarted.Eagerly,
        initialValue = TelegramStreamingSettings()  // Defaults
    )

    // Synchronous accessor for non-suspending contexts
    val currentSettings: TelegramStreamingSettings
        get() = settingsFlow.value
}
```

---

## Concurrency & Download-Management

### Problem

**Ohne Limits:**

- Thumbnail-Prefetch kann 100+ Downloads starten
- VOD-Playback wird ausgehungert (starved)
- TDLib überlastet mit parallel Requests
- Netzwerk-Timeouts und Fehler

### Lösung: FIFO-Queues + Runtime Limits

**Download-Typen:**

```kotlin
sealed class DownloadKind {
    object VIDEO : DownloadKind()  // Priority >= 32 (VOD, Episode)
    object THUMB : DownloadKind()  // Priority < 32 (Thumbnails)
}
```

**Concurrency-Enforcement:**

```kotlin
// In T_TelegramFileDownloader.kt

@Volatile private var activeGlobalDownloads = 0
@Volatile private var activeVideoDownloads = 0
@Volatile private var activeThumbDownloads = 0

private val globalQueue = ConcurrentLinkedQueue<PendingDownloadJob>()
private val videoQueue = ConcurrentLinkedQueue<PendingDownloadJob>()
private val thumbQueue = ConcurrentLinkedQueue<PendingDownloadJob>()

private val queueLock = Any()

fun canStartDownload(kind: DownloadKind): Boolean {
    val settings = settingsProvider.currentSettings

    // Check global limit
    if (activeGlobalDownloads >= settings.maxGlobalDownloads) {
        return false
    }

    // Check type-specific limits
    return when (kind) {
        is DownloadKind.VIDEO -> activeVideoDownloads < settings.maxVideoDownloads
        is DownloadKind.THUMB -> activeThumbDownloads < settings.maxThumbDownloads
    }
}
```

**Download Flow:**

```
1. Request: downloadFile(fileId, priority)
   │
   ├─> Classify: priority >= 32 → VIDEO, else → THUMB
   │
   ├─> Check Limits: canStartDownload(kind)?
   │   ├─> YES: incrementCounters(), execute download
   │   └─> NO: enqueueDownload(job)
   │
   └─> On Completion:
       ├─> decrementCounters(kind)
       └─> processQueuedDownloads()
           └─> Find next startable job (FIFO)
               └─> executeDownload(job)
```

**Queue Processing:**

```kotlin
private suspend fun processQueuedDownloads() = withContext(Dispatchers.IO) {
    synchronized(queueLock) {
        while (true) {
            val nextJob = findNextStartableJob() ?: break

            // Remove from queues
            globalQueue.remove(nextJob)
            when (nextJob.kind) {
                is DownloadKind.VIDEO -> videoQueue.remove(nextJob)
                is DownloadKind.THUMB -> thumbQueue.remove(nextJob)
            }

            // Execute without recursing into processQueuedDownloads
            executeDownload(nextJob)
        }
    }
}
```

**Wichtig:**

- ✅ FIFO-Order (Fair Queueing)
- ✅ Lock nur um Queue-Operations (nicht um Downloads)
- ✅ Coroutine-friendly (keine blocking Threads)
- ✅ Runtime konfigurierbar
- ✅ Structured Logging für alle Queue-Events

---

## Player-Integration

### TelegramFileDataSource

**Media3 DataSource für `tg://` URLs:**

```kotlin
class TelegramFileDataSource(
    private val serviceClient: T_TelegramServiceClient
) : BaseDataSource(/* isNetwork = */ true) {

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val playbackRequest = TelegramPlayUrl.parse(uri)

        // Determine mode based on position
        val mode = if (dataSpec.position == 0L) {
            INITIAL_START
        } else {
            SEEK
        }

        // Ensure file is ready for streaming
        val localPath = runBlocking {
            downloader.ensureFileReady(
                fileId = playbackRequest.fileId,
                startPosition = dataSpec.position,
                minBytes = 0L,  // Use mode-based prefix
                mode = mode,
                fileSizeBytes = playbackRequest.fileSize
            )
        }

        // Store for read operations
        currentLocalPath = localPath
        currentPosition = dataSpec.position

        return playbackRequest.fileSize ?: C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return runBlocking {
            downloader.readFileChunk(
                fileId = currentFileId,
                position = currentPosition,
                buffer = buffer,
                offset = offset,
                length = length
            ).also { bytesRead ->
                if (bytesRead > 0) {
                    currentPosition += bytesRead
                }
            }
        }
    }

    override fun close() {
        // Cancel downloads, cleanup handles
        runBlocking {
            downloader.cancelDownload(currentFileId)
            downloader.cleanupFileHandle(currentFileId)
        }
    }
}
```

**URL-Schema:**

```
tg://file/<fileId>?chatId=<chatId>&messageId=<messageId>&remoteId=<remoteId>&size=<size>&duration=<duration>
```

**Beispiel:**

```
tg://file/12345?chatId=-1001234567890&messageId=999&remoteId=BQADBAADAgADr...&size=104857600&duration=5400
```

### ExoPlayer LoadControl

**Dynamic Buffer Configuration:**

```kotlin
// In TelegramLoadControlBuilder.kt

fun buildTelegramLoadControl(settings: TelegramStreamingSettings): LoadControl {
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            settings.exoMinBufferMs,                      // Min buffer (default 15s)
            settings.exoMaxBufferMs,                      // Max buffer (default 50s)
            settings.exoBufferForPlaybackMs,              // Start playback (default 2.5s)
            settings.exoBufferForPlaybackAfterRebufferMs  // Resume after rebuffer (default 5s)
        )
        .build()
}
```

**Player Creation:**

```kotlin
// In InternalPlayerSession.kt

val loadControl = buildTelegramLoadControl(settingsProvider.currentSettings)

val player = ExoPlayer.Builder(context)
    .setLoadControl(loadControl)
    .build()
```

**⏳ TODO Phase 5b:** Hot-Swapping LoadControl zur Laufzeit

- Aktuell: LoadControl nur bei Player-Creation
- Geplant: Player-Neuerstellung oder ExoPlayer-API-Erweiterung

---

## Erkenntnisse & Best Practices

### 1. FileId vs RemoteId

**Lesson Learned:** FileId ist volatil

- ❌ Nicht für Persistierung oder Caching verwenden
- ❌ Wird bei TDLib Session-Rotation ungültig
- ✅ RemoteId ist stabil und eindeutig
- ✅ Alle Thumbnail-Refs nutzen RemoteId
- ✅ Resolution über `getRemoteFile(remoteId)` wenn FileId benötigt wird

### 2. Windowed Streaming ist essentiell

**Ohne Windows:** 4 GB Film = 4 GB Download vor Playback
**Mit Windows:** 16 MB Download, Rest im Hintergrund

- ✅ Schnellerer Playback-Start (< 2 Sekunden)
- ✅ Geringerer Storage-Verbrauch
- ✅ Besseres Seek-Verhalten

### 3. Mode-based Prefixes

**INITIAL_START:** Nur 256 KB für schnellen Start
**SEEK:** Nur +1 MB rund um Seek-Position

- ✅ Verhindert unnötige Downloads
- ✅ Optimiert für typisches User-Verhalten
- ✅ Runtime konfigurierbar für Edge-Cases

### 4. Concurrency Enforcement ist Pflicht

**Problem ohne Limits:**

- 100+ Thumbnail-Downloads blockieren VOD-Playback
- TDLib Timeouts und Fehler
- Schlechte User-Experience

**Lösung:**

- ✅ Separate Limits für VIDEO/THUMB
- ✅ FIFO-Queues für Fairness
- ✅ Runtime änderbar je Device (Low-End vs High-End)

### 5. Runtime Settings = Flexibilität

**Vorteil:**

- ✅ A/B-Testing ohne App-Update
- ✅ Device-Profile (Phone/Tablet/TV, Low/High-End)
- ✅ User-Präferenzen (Data-Saver Mode, etc.)
- ✅ Debugging (erhöhe Timeouts bei schlechtem Netz)

### 6. Structured Logging ist Gold wert

**Implementierung:**

```kotlin
TelegramLogRepository.debug(
    source = "T_TelegramFileDownloader",
    message = "Download job enqueued",
    details = mapOf(
        "fileId" to job.fileId.toString(),
        "kind" to job.kind::class.simpleName.orEmpty(),
        "priority" to job.priority.toString(),
        "globalQueueSize" to globalQueue.size.toString()
    )
)
```

**Nutzen:**

- ✅ Filterable Logs (nach Source, Level, Details)
- ✅ In-App Log-Viewer für Enduser-Diagnostik
- ✅ Export für Bug-Reports
- ✅ Performance-Analyse (Queue-Längen, Timeouts)

### 7. Zero-Copy ist nicht Zero-Disk

**Klärung:**

- TDLib cached **immer** auf Disk (unvermeidbar)
- "Zero-Copy" = keine **App-seitigen** Kopien
- App liest direkt aus TDLib-Cache via `RandomAccessFile`
- Kein In-Memory-Ringbuffer nötig

### 8. Prefetch Semaphore vs Batch-Chunking

**Vergleich:**

```kotlin
// ❌ Alt: Batch-Chunking (3 parallel pro Chunk, blockiert zwischen Chunks)
posterRefs.chunked(3).forEach { batch ->
    batch.map { async { download(it) } }.awaitAll()
    delay(1000)  // Pause zwischen Chunks
}

// ✅ Neu: Semaphore (alle starten parallel, Semaphore limitiert)
val semaphore = Semaphore(3)
posterRefs.map { ref ->
    async {
        semaphore.acquire()
        try { download(ref) }
        finally { semaphore.release() }
    }
}.awaitAll()
```

**Vorteil Semaphore:**

- Keine künstlichen Pausen
- FIFO via `acquire()` Order
- Gleichmäßigere Auslastung

---

## Offene TODOs & Roadmap

### Phase 5b: LoadControl Hot-Swapping (TODO)

**Problem:** LoadControl kann nur bei Player-Creation gesetzt werden
**Lösungen:**

1. Player neu erstellen bei Settings-Änderung
2. ExoPlayer API-Erweiterung (Upstream-Feature-Request)
3. Workaround: Settings nur zwischen Playback-Sessions änderbar

### Phase 6: Debug Overlays (TODO)

**Engine Overlay:**

- TDLib State (Started, AuthReady, Connection)
- Active Downloads (Global/Video/Thumb)
- Queue Sizes

**Streaming Overlay:**

- Current Window (Start/End)
- Downloaded Prefix / Required Prefix
- ExoPlayer State (BUFFERING/READY/ENDED)
- Position / Duration / IsPlaying

**Implementierung:**

```kotlin
if (settings.showEngineOverlay) {
    Box(modifier = Modifier.align(Alignment.TopEnd)) {
        Column {
            Text("TDLib: ${serviceClient.authState.value}")
            Text("Downloads: ${downloader.activeGlobalDownloads}")
            Text("Queue: ${downloader.globalQueue.size}")
        }
    }
}
```

### Phase 7: App-side Telegram Log Filter (TODO)

**Ziel:** Nur relevante Telegram-Logs loggen basierend auf Level

```kotlin
object TelegramLog {
    fun log(level: Int, tag: String, msg: String) {
        val settings = settingsProvider.currentSettings
        if (level <= settings.tgAppLogLevel) {
            AppLog.log(category = "telegram", level = level, message = msg)
        }
    }
}
```

**Migration:**

- Alle `Log.d/i/w/e` in Telegram-Modulen → `TelegramLog.log`

### Phase 8: Jank Telemetry Sampling (TODO)

**Ziel:** Reduzieren von Telemetry-Overhead

```kotlin
private var frameCounter = 0

fun onFrameDraw() {
    frameCounter++
    if (frameCounter % settings.jankTelemetrySampleRate == 0) {
        // Log jank metrics
        TelemetryLogger.logJank(...)
    }
}
```

### Phase 9: Automated Tests (TODO)

**Coverage-Ziele:**

- ✅ `TelegramStreamingSettingsProvider` Mapper-Tests
- ⏳ `ensureFileReady()` Prefix/Margin/Timeout-Tests
- ⏳ Downloader Concurrency-Tests (Queue, Counters, FIFO)
- ⏳ Thumbnail Prefetch Logic-Tests (Semaphore, Dedup)
- ⏳ LoadControl Builder Validation

**Test-Framework:**

- JUnit5 für Unit-Tests
- MockK für TDLib-Client-Mocking
- Turbine für Flow-Testing
- Robolectric für Android-Context-Tests

---

## Referenzen

### Primäre Dokumente

- **`.github/tdlibAgent.md`** – Single Source of Truth für Telegram-Integration
- **`CHANGELOG.md`** – Chronologische Historie (Nov-Dez 2025)
- **`ROADMAP.md`** – Geplante Features & Prioritäten

### Implementierungs-Docs

- **`docs/FishIT_Telegram_Runtime_Checklist_Docs.md`** – Phase 0-9 Checkliste
- **`docs/TDLIB_TASK_GROUPING.md`** – Cluster-Analyse & Parallelisierung
- **`docs/TDLIB_FINAL_REVIEW_UPDATED.md`** – Wiring & Cleanup Status
- **`docs/telegram_cleanup_checklist.md`** – Pipeline Migrations-TODOs

### Player-Integration

- **`docs/TELEGRAM_SIP_PLAYER_INTEGRATION.md`** – Telegram Player-Wiring
- **`docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md`** – Resume & Kids-Mode
- **`docs/BUG_ANALYSIS_REPORT_2025-12-01.md`** – 5 Runtime-Bugs (alle gefixed)

### Logging & Diagnostics

- **`docs/LOGGING_SYSTEM_ANALYSIS.md`** – Unified Logging Architecture
- **`docs/LOG_VIEWER.md`** – In-App Log-Viewer

### Upstream-Referenzen

- **g00sha's tdl-coroutines:** https://github.com/G00sha/tdl-coroutines
- **TDLib API:** https://core.telegram.org/tdlib/docs/

---

**Autor:** GitHub Copilot  
**Version:** 1.0  
**Letzte Aktualisierung:** 2025-12-02  
**Status:** ✅ ACTIVE – Single Source of Truth für TDLib Streaming & Thumbnails
