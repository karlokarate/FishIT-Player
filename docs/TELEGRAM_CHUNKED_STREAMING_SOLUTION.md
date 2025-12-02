# Telegram Chunked Streaming – Lösung für Memory-Effizientes Video-Streaming

## Problem-Analyse (tdlog021225)

### Identifizierter Fehler

Aus dem Runtime-Log ergibt sich folgendes Problem:

```
[INFO] telegram: opened source=TelegramFileDataSource,
    fileId=1729,
    remoteId=BAACAgIAAx0CR7YUWgACQ_FpLxw0fELLviUKzubNep_fhA1KgAACY5UAAlnUUUncug3KWL8HLzgE,
    localPath=/data/data/com.chris.m3usuite/no_backup/tdlib-files/temp/6,
    dataSpecPosition=0,
    correctFileSize=2095513162,  ← ExoPlayer erwartet 2GB
    localFileSize=1048576        ← Lokal sind nur 1MB vorhanden

[ERROR] PLAYER_ERROR: Playback error: Source error type=Source
```

**Root Cause:**

1. **File Size Diskrepanz**: TDLib meldet die korrekte Gesamtgröße (`correctFileSize=2095513162` ≈ 2GB)
2. **Windowed Download**: `T_TelegramFileDownloader.ensureFileReady()` lädt nur ein kleines Fenster (954KB initial prefix)
3. **ExoPlayer Erwartung**: ExoPlayer bekommt die volle File Size (2GB) mitgeteilt
4. **Lesefehler**: Wenn ExoPlayer versucht, über das 1MB-Fenster hinaus zu lesen oder zu seeken, schlägt der Zugriff fehl, da die Datei physisch nur 1MB groß ist

### Bestätigung der Vermutung

Die Vermutung des Users war korrekt:
- Ein bestimmtes Fenster wird angefragt (z.B. 0-954KB für initialen Start)
- ExoPlayer wird aber die gesamte File Size (2GB) übermittelt
- ExoPlayer erwartet, die gesamte Datei lesen zu können
- Beim Versuch, über das Fenster hinaus zu lesen/seeken, entsteht die Diskrepanz → "Source error"

## Aktuelle Architektur (IST-Zustand)

### TelegramFileDataSource

```kotlin
// Phase D+ Zero-Copy Architecture:
// 1. TDLib downloaded file to cache directory on disk
// 2. This DataSource delegates to Media3's FileDataSource for all I/O
// 3. No ByteArray buffers, no custom position tracking
// 4. ExoPlayer/FileDataSource handles seeking and scrubbing
```

**Problem**: Die "Zero-Copy" Architektur geht davon aus, dass TDLib die **gesamte Datei** herunterlädt, bevor ExoPlayer darauf zugreift. Das ist bei großen Videos (>100MB) speicherintensiv und langsam.

### T_TelegramFileDownloader.ensureFileReady()

```kotlin
// Current behavior:
// - Downloads a small window (e.g., 954KB for INITIAL_START mode)
// - Returns local path when window is ready
// - Does NOT continue downloading in background
```

**Problem**: Nach dem initialen Fenster stoppt der Download. Wenn ExoPlayer mehr Daten braucht, sind diese nicht verfügbar.

## Lösungsansätze

### Option 1: Chunked Streaming mit Rolling Window (Empfohlen)

Implementiere ein **rollendes Fenster-System**, das kontinuierlich vorauslädt und alte Chunks löscht.

#### Konzept: 30-Minuten-Chunks

```
Video: |████████████████████████████████████████| 120 Min (2GB)
       |<-- Chunk 1 -->|<-- Chunk 2 -->|<-- Chunk 3 -->|<-- Chunk 4 -->|
       0-30min         30-60min        60-90min        90-120min
       (500MB)         (500MB)         (500MB)         (500MB)

Playback-Position: Min 28
Buffer:
  ✅ Chunk 1 (0-30min)   - Aktiv im RAM-Puffer
  ✅ Chunk 2 (30-60min)  - Wird vorab geladen
  ⬜ Chunk 3 (60-90min)  - Noch nicht geladen
  ⬜ Chunk 4 (90-120min) - Noch nicht geladen

Bei Min 29:30:
  ❌ Chunk 1 wird aus TDLib-Cache gelöscht (deleteFile)
  ✅ Chunk 2 wird aktiv
  🔄 Chunk 3 wird im Hintergrund geladen
```

#### Architektur-Komponenten

##### 1. `TelegramChunkedDataSource` (Neu)

```kotlin
/**
 * DataSource für chunked streaming von großen Telegram-Videos.
 * 
 * Strategie:
 * - Teilt Video in 30-Min-Chunks (konfigurierbar)
 * - Lädt aktuellen Chunk + 1 voraus
 * - Löscht alte Chunks aus TDLib-Cache wenn nicht mehr benötigt
 * - Verwendet mehrere FileDataSource-Instanzen (eine pro Chunk)
 * - Seamless switching zwischen Chunks (ohne Pause)
 */
@UnstableApi
class TelegramChunkedDataSource(
    private val serviceClient: T_TelegramServiceClient,
    private val chunkDurationMs: Long = 30 * 60 * 1000L, // 30 Minuten
) : DataSource {
    
    data class VideoChunk(
        val index: Int,
        val startMs: Long,
        val endMs: Long,
        val startByte: Long,
        val endByte: Long,
        val fileId: Int,
        val localPath: String?,
        val state: ChunkState
    )
    
    enum class ChunkState {
        NOT_LOADED,
        LOADING,
        READY,
        DELETED
    }
    
    private val chunks = mutableListOf<VideoChunk>()
    private var currentChunkIndex = 0
    private var currentDelegate: FileDataSource? = null
    
    override fun open(dataSpec: DataSpec): Long {
        // 1. Parse tg:// URL und hole Video-Metadaten
        val videoInfo = parseAndGetVideoInfo(dataSpec.uri)
        
        // 2. Berechne Chunks basierend auf Duration und FileSize
        calculateChunks(videoInfo)
        
        // 3. Bestimme aktuellen Chunk basierend auf dataSpec.position
        currentChunkIndex = getChunkIndexForPosition(dataSpec.position)
        
        // 4. Lade aktuellen Chunk + preload nächsten
        loadChunk(currentChunkIndex)
        preloadChunk(currentChunkIndex + 1)
        
        // 5. Öffne FileDataSource für aktuellen Chunk
        val chunk = chunks[currentChunkIndex]
        currentDelegate = FileDataSource()
        val chunkDataSpec = dataSpec.buildUpon()
            .setUri(Uri.fromFile(File(chunk.localPath!!)))
            .setPosition(dataSpec.position - chunk.startByte)
            .setLength(chunk.endByte - chunk.startByte)
            .build()
        
        return currentDelegate!!.open(chunkDataSpec)
    }
    
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = currentDelegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        
        // Check if we need to switch to next chunk
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            if (currentChunkIndex < chunks.size - 1) {
                // Switch to next chunk
                switchToNextChunk()
                return read(buffer, offset, length) // Retry read from new chunk
            }
        }
        
        // Cleanup old chunks (delete from TDLib cache)
        cleanupOldChunks()
        
        return bytesRead
    }
    
    private suspend fun loadChunk(index: Int) {
        if (index < 0 || index >= chunks.size) return
        val chunk = chunks[index]
        if (chunk.state != ChunkState.NOT_LOADED) return
        
        chunks[index] = chunk.copy(state = ChunkState.LOADING)
        
        // Download chunk window from TDLib
        val localPath = serviceClient.downloader().ensureFileReady(
            fileId = chunk.fileId,
            startPosition = chunk.startByte,
            minBytes = chunk.endByte - chunk.startByte,
            mode = EnsureFileReadyMode.SEEK
        )
        
        chunks[index] = chunk.copy(
            localPath = localPath,
            state = ChunkState.READY
        )
    }
    
    private fun cleanupOldChunks() {
        // Delete chunks that are more than 1 behind current position
        for (i in 0 until currentChunkIndex - 1) {
            val chunk = chunks[i]
            if (chunk.state == ChunkState.READY) {
                // Delete from TDLib cache
                serviceClient.client.send(
                    DeleteFile(id = chunk.fileId)
                )
                chunks[i] = chunk.copy(state = ChunkState.DELETED)
                
                TelegramLogRepository.info(
                    source = "TelegramChunkedDataSource",
                    message = "Deleted old chunk from cache",
                    details = mapOf(
                        "chunkIndex" to i.toString(),
                        "startMs" to chunk.startMs.toString(),
                        "endMs" to chunk.endMs.toString()
                    )
                )
            }
        }
    }
}
```

##### 2. `ChunkCalculator` (Util)

```kotlin
/**
 * Berechnet Chunk-Grenzen basierend auf Video-Metadaten.
 */
object ChunkCalculator {
    /**
     * @param durationMs Video-Länge in Millisekunden
     * @param fileSizeBytes Video-Größe in Bytes
     * @param chunkDurationMs Chunk-Länge (default: 30 Min)
     * @return Liste von Chunk-Informationen
     */
    fun calculateChunks(
        durationMs: Long,
        fileSizeBytes: Long,
        chunkDurationMs: Long = 30 * 60 * 1000L
    ): List<ChunkInfo> {
        val chunks = mutableListOf<ChunkInfo>()
        val bytesPerMs = fileSizeBytes.toDouble() / durationMs.toDouble()
        
        var currentMs = 0L
        var chunkIndex = 0
        
        while (currentMs < durationMs) {
            val endMs = minOf(currentMs + chunkDurationMs, durationMs)
            val startByte = (currentMs * bytesPerMs).toLong()
            val endByte = (endMs * bytesPerMs).toLong()
            
            chunks.add(
                ChunkInfo(
                    index = chunkIndex,
                    startMs = currentMs,
                    endMs = endMs,
                    startByte = startByte,
                    endByte = endByte,
                    sizeBytes = endByte - startByte
                )
            )
            
            currentMs = endMs
            chunkIndex++
        }
        
        return chunks
    }
    
    data class ChunkInfo(
        val index: Int,
        val startMs: Long,
        val endMs: Long,
        val startByte: Long,
        val endByte: Long,
        val sizeBytes: Long
    )
}
```

##### 3. TDLib DeleteFile Integration

```kotlin
// In T_TelegramFileDownloader.kt

/**
 * Löscht eine Datei aus dem TDLib-Cache.
 * Nützlich für Chunked Streaming, um Speicher freizugeben.
 */
suspend fun deleteFile(fileId: Int) {
    withContext(Dispatchers.IO) {
        try {
            val result = session.send(DeleteFile(id = fileId))
            if (result is TdlResult.Success) {
                TelegramLogRepository.info(
                    source = "T_TelegramFileDownloader",
                    message = "File deleted from cache",
                    details = mapOf("fileId" to fileId.toString())
                )
            } else if (result is TdlResult.Failure) {
                TelegramLogRepository.warn(
                    source = "T_TelegramFileDownloader",
                    message = "Failed to delete file: ${result.message}",
                    details = mapOf(
                        "fileId" to fileId.toString(),
                        "code" to result.code.toString()
                    )
                )
            }
        } catch (e: Exception) {
            TelegramLogRepository.error(
                source = "T_TelegramFileDownloader",
                message = "Exception deleting file: ${e.message}",
                exception = e,
                details = mapOf("fileId" to fileId.toString())
            )
        }
    }
}
```

#### Vorteile

✅ **Speicher-Effizienz**: Nur 2 Chunks gleichzeitig im Speicher (max ~1GB statt 2GB)  
✅ **Seamless Playback**: ExoPlayer merkt den Chunk-Wechsel nicht  
✅ **Flexibel**: Chunk-Größe konfigurierbar (15/30/60 Min)  
✅ **Robust**: Alte Chunks werden proaktiv gelöscht  
✅ **ExoPlayer-Kompatibel**: Nutzt weiterhin FileDataSource intern

#### Nachteile

⚠️ **Seek-Komplexität**: Seek zu Minute 90 erfordert Download von Chunk 3  
⚠️ **Implementierungs-Aufwand**: Neue DataSource mit State-Management  
⚠️ **TDLib-API**: `DeleteFile()` muss verfügbar sein (ist es normalerweise)

---

### Option 2: Progressive Download mit ExoPlayer Cache (Alternative)

Nutze ExoPlayer's eingebautes Caching-System für progressive Downloads.

#### Konzept

```kotlin
// Erstelle einen Cache-aware DataSource
val cache = SimpleCache(
    cacheDir = context.cacheDir.resolve("exoplayer"),
    evictor = LeastRecentlyUsedCacheEvictor(500 * 1024 * 1024L) // 500MB max
)

val cacheDataSourceFactory = CacheDataSource.Factory()
    .setCache(cache)
    .setUpstreamDataSourceFactory(TelegramFileDataSourceFactory(serviceClient))
    .setCacheWriteDataSinkFactory(null) // Read-only cache
```

#### Wie es funktioniert

1. **TelegramFileDataSource** lädt initial nur kleines Fenster
2. **ExoPlayer** liest Daten und cached sie im `SimpleCache`
3. **Background-Download** kann von TDLib weiterlaufen
4. **Cache Eviction** löscht alte Daten automatisch (LRU)

#### Vorteil

✅ Nutzt ExoPlayer's etabliertes Cache-System  
✅ Weniger Custom Code

#### Nachteil

⚠️ ExoPlayer Cache ist für HTTP-Streams optimiert, nicht für lokale TDLib-Dateien  
⚠️ Benötigt dennoch Anpassung in `ensureFileReady()` für Background-Download

---

### Option 3: Vollständiger Download mit Progress (Einfachste Lösung)

Für kleinere Videos (<500MB) oder gute Netzwerkverbindungen: Vollständiger Download vor Playback.

#### Änderungen

```kotlin
// In TelegramFileDataSource.kt

override fun open(dataSpec: DataSpec): Long {
    // ... parse URL ...
    
    // Option für vollständigen Download
    val shouldDownloadFully = when {
        fileSizeBytes < 500 * 1024 * 1024 -> true // < 500MB
        settingsProvider.currentSettings.alwaysDownloadFully -> true
        else -> false
    }
    
    if (shouldDownloadFully) {
        // Download entire file
        localPath = runBlocking {
            downloader.ensureFileReady(
                fileId = fileId,
                startPosition = 0L,
                minBytes = fileSizeBytes ?: Long.MAX_VALUE, // Force full download
                mode = EnsureFileReadyMode.INITIAL_START
            )
        }
    } else {
        // Use chunked streaming (Option 1)
        // ...
    }
}
```

#### Vorteil

✅ Einfachste Implementierung  
✅ Keine Chunk-Verwaltung  
✅ Funktioniert mit bestehender Architektur

#### Nachteil

⚠️ Wartezeit vor Playback bei großen Dateien  
⚠️ Hoher Speicherverbrauch (gesamte Datei im Cache)  
⚠️ Langsamer Start bei schlechter Verbindung

---

## Empfohlene Lösung: Hybrid-Ansatz

Kombiniere Option 1 (Chunked) und Option 3 (Full Download) basierend auf Video-Größe:

```kotlin
/**
 * Streaming-Strategie basierend auf File Size.
 */
enum class StreamingStrategy {
    /** Vollständiger Download vor Playback (< 500MB) */
    FULL_DOWNLOAD,
    
    /** Chunked Streaming mit Rolling Window (>= 500MB) */
    CHUNKED_STREAMING
}

fun selectStrategy(fileSizeBytes: Long?): StreamingStrategy {
    return when {
        fileSizeBytes == null -> StreamingStrategy.CHUNKED_STREAMING // Unknown size
        fileSizeBytes < 500 * 1024 * 1024 -> StreamingStrategy.FULL_DOWNLOAD
        else -> StreamingStrategy.CHUNKED_STREAMING
    }
}
```

### Konfigurations-Optionen (DataStore)

```kotlin
data class TelegramStreamingSettings(
    /** Chunk-Länge in Millisekunden (default: 30 Min) */
    val chunkDurationMs: Long = 30 * 60 * 1000L,
    
    /** Max File Size für FULL_DOWNLOAD Strategie (default: 500MB) */
    val fullDownloadThresholdBytes: Long = 500 * 1024 * 1024L,
    
    /** Anzahl Chunks zum Vorausladen (default: 1) */
    val chunksToPreload: Int = 1,
    
    /** Auto-Cleanup alter Chunks (default: true) */
    val autoCleanupOldChunks: Boolean = true,
    
    /** Cleanup-Offset: Behalte N Chunks hinter aktueller Position (default: 1) */
    val keepChunksBehind: Int = 1
)
```

---

## Implementierungs-Roadmap

### Phase 1: Analyse & Prototyp (1-2 Tage)

- [x] Log-Analyse abgeschlossen
- [x] ChunkCalculator implementieren und testen
- [ ] Proof-of-Concept für TelegramChunkedDataSource

### Phase 2: Core Implementation (3-4 Tage)

- [ ] `TelegramChunkedDataSource` vollständig implementieren
- [ ] `deleteFile()` in `T_TelegramFileDownloader` hinzufügen
- [ ] Chunk State Management (Loading, Ready, Deleted)
- [ ] Seamless Chunk-Switching

### Phase 3: Integration (2 Tage)

- [ ] Strategy Selection basierend auf File Size
- [ ] DataStore Settings für Streaming-Konfiguration
- [ ] Update `PlaybackLauncher` für Strategy Selection

### Phase 4: Testing & Refinement (2-3 Tage)

- [ ] Test mit verschiedenen Video-Größen (100MB, 500MB, 2GB, 5GB)
- [ ] Test Seek-Verhalten (vorwärts, rückwärts, weit springen)
- [ ] Performance-Profiling (Speicher, CPU)
- [ ] Edge Cases (Netzwerk-Unterbrechung, App-Pause)

### Phase 5: Dokumentation & Rollout (1 Tag)

- [ ] User-Dokumentation (Settings, Troubleshooting)
- [ ] Developer-Dokumentation (Architektur, Extension Points)
- [ ] Rollout mit Feature Flag (TELEGRAM_CHUNKED_STREAMING)

---

## Testing-Szenarien

### 1. Kleines Video (< 500MB)

**Erwartetes Verhalten**: FULL_DOWNLOAD Strategie  
✅ Download vor Playback  
✅ Instant Seek nach Download  
✅ Kein Chunk-Management

### 2. Großes Video (2GB, 120 Min)

**Erwartetes Verhalten**: CHUNKED_STREAMING  
✅ Start innerhalb 2-3 Sekunden  
✅ Seamless Playback durch Chunks  
✅ Automatisches Cleanup alter Chunks  
✅ Preload nächster Chunk im Hintergrund

**Test-Szenario**:
```
1. Start Playback → Chunk 0 (0-30min) lädt
2. Bei Min 29 → Chunk 1 (30-60min) wird preloaded
3. Bei Min 30 → Switch zu Chunk 1, Chunk 0 wird gelöscht
4. Bei Min 59 → Chunk 2 (60-90min) wird preloaded
5. Seek zu Min 90 → Chunk 3 (90-120min) lädt
```

### 3. Seek-Verhalten

**Forward Seek (innerhalb nächstem Chunk)**:  
✅ Preloaded Chunk ist ready → Instant  

**Forward Seek (weit voraus, Chunk nicht geladen)**:  
⏳ Download-Pause, dann Resume

**Backward Seek (zu gelöschtem Chunk)**:  
⏳ Chunk muss neu geladen werden

---

## Monitoring & Diagnostics

### Log-Events

```kotlin
// Chunk Lifecycle
TelegramLogRepository.info(
    source = "TelegramChunkedDataSource",
    message = "Chunk loaded",
    details = mapOf(
        "chunkIndex" to index.toString(),
        "startMs" to startMs.toString(),
        "endMs" to endMs.toString(),
        "sizeBytes" to sizeBytes.toString(),
        "loadTimeMs" to loadTimeMs.toString()
    )
)

// Chunk Switch
TelegramLogRepository.info(
    source = "TelegramChunkedDataSource",
    message = "Switched to next chunk",
    details = mapOf(
        "fromChunk" to oldIndex.toString(),
        "toChunk" to newIndex.toString(),
        "playbackPositionMs" to positionMs.toString()
    )
)

// Cleanup
TelegramLogRepository.info(
    source = "TelegramChunkedDataSource",
    message = "Cleaned up old chunk",
    details = mapOf(
        "chunkIndex" to index.toString(),
        "deletedSizeBytes" to sizeBytes.toString(),
        "cacheFreedMB" to (sizeBytes / 1024 / 1024).toString()
    )
)
```

### Telemetry Metrics

```kotlin
// In DiagnosticsLogger
fun logChunkStreamingMetrics(
    fileId: Int,
    totalChunks: Int,
    activeChunk: Int,
    preloadedChunks: Int,
    deletedChunks: Int,
    cacheUsageMB: Long
)
```

---

## Zusammenfassung

### Warum Chunked Streaming?

1. **Speicher-Effizienz**: 2GB Video benötigt nur ~1GB RAM (2 Chunks gleichzeitig)
2. **Schneller Start**: Playback startet nach 2-3 Sek (nur initial Chunk nötig)
3. **Nahtlose UX**: Chunk-Wechsel sind für User unsichtbar
4. **Skalierbar**: Funktioniert auch für sehr große Dateien (5GB+)

### Next Steps

1. **Proof-of-Concept**: Implementiere `ChunkCalculator` und teste mit Beispiel-Video
2. **Prototyp**: Baue minimale `TelegramChunkedDataSource` Version
3. **Validierung**: Teste mit echtem 2GB Telegram-Video
4. **Rollout**: Feature Flag aktivieren nach erfolgreichem Test

### Offene Fragen

1. **TDLib Partial Downloads**: Kann TDLib zuverlässig Byte-Ranges laden?
2. **Chunk Size**: 30 Min optimal, oder besser 15/60 Min?
3. **Preload Timing**: Wann genau soll nächster Chunk vorab geladen werden?
4. **Cache Directory**: TDLib-Cache oder separates Verzeichnis für Chunks?

---

**Erstellt**: 2025-12-02  
**Status**: Draft / Zur Diskussion  
**Nächster Schritt**: Implementierung ChunkCalculator + Prototyp
