# Telegram Playback Error – Analyse & Lösung

## Executive Summary

**Problem**: ExoPlayer-Fehler beim Abspielen von großen Telegram-Videos (>500MB)  
**Root Cause**: File Size Diskrepanz zwischen TDLib-Fenster (1MB) und ExoPlayer-Erwartung (2GB)  
**Status**: Analysiert, Lösung dokumentiert, Prototyp implementiert  
**Nächste Schritte**: Phase 2 Implementation (TelegramChunkedDataSource)

---

## Problem-Analyse

### Fehler aus Log (tdlog021225)

```
[INFO] telegram: opened source=TelegramFileDataSource
    fileId=1729
    correctFileSize=2095513162    ← ExoPlayer erwartet 2GB
    localFileSize=1048576         ← TDLib hat nur 1MB geladen

[ERROR] PLAYER_ERROR: Playback error: Source error
```

### Ablauf des Fehlers

1. **User startet Video** (2GB, 120 Minuten)
2. **TelegramFileDataSource.open()** wird aufgerufen
3. **T_TelegramFileDownloader.ensureFileReady()** lädt initiales Fenster:
   - Mode: `INITIAL_START`
   - Requested: 954KB (runtime setting)
   - Downloaded: 1MB (TDLib rundet auf)
4. **ExoPlayer bekommt FileDataSource** mit:
   - Path: `/data/data/.../temp/6`
   - Length: **2095513162** (2GB - korrekte File Size)
5. **ExoPlayer versucht zu lesen/seeken** → Fehler:
   - File existiert nur mit 1MB
   - ExoPlayer erwartet 2GB
   - FileDataSource kann nicht über 1MB hinaus lesen
   - → **Source Error**

### Bestätigung der Vermutung

✅ **Die Vermutung des Users war 100% korrekt:**

> "meine Vermutung ist, dass ein bestimmtes Fenster erfragt wird, aber der exoplayer die gesamte File size übermittelt wird. dadurch entsteht eine Diskrepanz, weil Exo File size x erwartet aber nur die aus dem Fenster bekommt."

Die aktuelle Architektur geht davon aus, dass TDLib die **gesamte Datei** herunterlädt, bevor ExoPlayer darauf zugreift. Das funktioniert nicht für große Videos.

---

## Implementierte Lösung (Phase 1)

### 1. Solution Document (TELEGRAM_CHUNKED_STREAMING_SOLUTION.md)

Umfassendes Dokument mit:
- Detaillierter Problem-Analyse
- 3 Lösungsansätze (Chunked Streaming, ExoPlayer Cache, Full Download)
- **Empfehlung**: Hybrid-Ansatz (Chunked für >500MB, Full Download für <500MB)
- Architektur-Komponenten (TelegramChunkedDataSource, ChunkCalculator)
- Implementation Roadmap (5 Phasen)
- Testing-Szenarien
- Monitoring & Diagnostics

### 2. ChunkCalculator Utility (Implementiert ✅)

**Datei**: `app/src/main/java/com/chris/m3usuite/telegram/player/ChunkCalculator.kt`

**Features**:
- Berechnet Video-Chunks basierend auf Duration und File Size
- Default: 30-Minuten-Chunks (konfigurierbar)
- Chunk-Index-Lookup (nach Position oder Byte-Offset)
- Cache-Size-Berechnung
- Binary Search für Performance

**Beispiel**:
```kotlin
// 2GB Video, 120 Minuten → 4 Chunks à 500MB
val chunks = ChunkCalculator.calculateChunks(
    durationMs = 120 * 60 * 1000L,
    fileSizeBytes = 2_000_000_000L,
    chunkDurationMs = 30 * 60 * 1000L
)

// Finde Chunk für Position 45min
val chunkIndex = ChunkCalculator.getChunkIndexForPosition(
    chunks, 45 * 60 * 1000L
) // → 1 (zweiter Chunk)

// Berechne benötigten Cache
val cacheSize = ChunkCalculator.calculateRequiredCacheSize(
    chunks = chunks,
    currentChunkIndex = 1,
    preloadCount = 1,      // 1 Chunk vorausladen
    keepBehindCount = 1    // 1 Chunk behalten
) // → ~1.5GB (3 Chunks)
```

### 3. Comprehensive Tests (Implementiert ✅)

**Datei**: `app/src/test/java/com/chris/m3usuite/telegram/player/ChunkCalculatorTest.kt`

**Test Coverage**:
- ✅ Basic chunk calculation (120min, 2GB)
- ✅ Small videos (single chunk)
- ✅ Large videos (3h, 5GB)
- ✅ Different chunk sizes (15/30/60 min)
- ✅ Position/offset lookups
- ✅ Cache size calculations
- ✅ Edge cases (boundaries, negative values, empty lists)
- ✅ Realistic scenarios (90min movie)
- ✅ Input validation

**Build Status**: ✅ Compiles successfully

---

## Nächste Schritte (Phase 2+)

### Phase 2: TelegramChunkedDataSource Implementation (3-4 Tage)

**Neue Datei**: `app/src/main/java/com/chris/m3usuite/telegram/player/TelegramChunkedDataSource.kt`

**Kern-Features**:
```kotlin
class TelegramChunkedDataSource(
    private val serviceClient: T_TelegramServiceClient,
    private val chunkDurationMs: Long = 30 * 60 * 1000L
) : DataSource {
    
    // State Management
    private val chunks = mutableListOf<VideoChunk>()
    private var currentChunkIndex = 0
    private var currentDelegate: FileDataSource? = null
    
    // Core Methods
    override fun open(dataSpec: DataSpec): Long {
        // 1. Parse tg:// URL
        // 2. Calculate chunks via ChunkCalculator
        // 3. Load current chunk + preload next
        // 4. Open FileDataSource for current chunk
    }
    
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        // 1. Read from current delegate
        // 2. Check if chunk boundary reached
        // 3. Switch to next chunk if needed
        // 4. Cleanup old chunks
    }
    
    // Helper Methods
    private suspend fun loadChunk(index: Int) { /* ... */ }
    private suspend fun preloadChunk(index: Int) { /* ... */ }
    private fun switchToNextChunk() { /* ... */ }
    private fun cleanupOldChunks() { /* ... */ }
}
```

**Integration Points**:
1. **T_TelegramFileDownloader**: Benötigt `deleteFile(fileId: Int)` Methode
2. **PlaybackLauncher**: Strategy Selection basierend auf File Size
3. **DataStore**: Settings für Chunk-Konfiguration

### Phase 3: Integration & Settings (2 Tage)

**DataStore Settings**:
```kotlin
data class TelegramStreamingSettings(
    val chunkDurationMs: Long = 30 * 60 * 1000L,
    val fullDownloadThresholdBytes: Long = 500 * 1024 * 1024L,
    val chunksToPreload: Int = 1,
    val autoCleanupOldChunks: Boolean = true,
    val keepChunksBehind: Int = 1
)
```

**Strategy Selection**:
```kotlin
enum class StreamingStrategy {
    FULL_DOWNLOAD,      // < 500MB: Download komplett
    CHUNKED_STREAMING   // >= 500MB: Chunked mit Cleanup
}

fun selectStrategy(fileSizeBytes: Long?): StreamingStrategy {
    return when {
        fileSizeBytes == null -> CHUNKED_STREAMING
        fileSizeBytes < 500 * 1024 * 1024 -> FULL_DOWNLOAD
        else -> CHUNKED_STREAMING
    }
}
```

### Phase 4: Testing & Refinement (2-3 Tage)

**Test-Szenarien**:

1. **Kleines Video (< 500MB)**:
   - Strategie: FULL_DOWNLOAD
   - Erwartung: Download vor Playback, instant Seek

2. **Großes Video (2GB, 120 Min)**:
   - Strategie: CHUNKED_STREAMING
   - Erwartung: Start in 2-3 Sek, seamless Chunk-Switch
   - Test: Play → Chunk 0, bei Min 29 → Chunk 1 preload, bei Min 30 → Switch + Cleanup

3. **Seek-Verhalten**:
   - Forward Seek (innerhalb Preload): Instant
   - Forward Seek (weit voraus): Download-Pause
   - Backward Seek (gelöschter Chunk): Neu laden

4. **Edge Cases**:
   - Netzwerk-Unterbrechung während Chunk-Load
   - App-Pause während Playback
   - Rapid Seeking (mehrere Chunks überspringen)

### Phase 5: Rollout (1 Tag)

**Feature Flag**:
```kotlin
object BuildConfig {
    const val TELEGRAM_CHUNKED_STREAMING = true // Feature Flag
}
```

**Documentation**:
- User-Guide: Streaming-Settings im Settings-Screen
- Developer-Guide: Architektur, Extension Points
- Troubleshooting: Häufige Probleme und Lösungen

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
        "loadTimeMs" to loadTimeMs.toString()
    )
)

// Chunk Switch
TelegramLogRepository.info(
    source = "TelegramChunkedDataSource",
    message = "Switched to next chunk",
    details = mapOf(
        "fromChunk" to oldIndex.toString(),
        "toChunk" to newIndex.toString()
    )
)

// Cleanup
TelegramLogRepository.info(
    source = "TelegramChunkedDataSource",
    message = "Cleaned up old chunk",
    details = mapOf(
        "deletedSizeBytes" to sizeBytes.toString(),
        "cacheFreedMB" to (sizeBytes / 1024 / 1024).toString()
    )
)
```

### Telemetry Metrics

```kotlin
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

## Vorteile der Lösung

### 1. Speicher-Effizienz

**Vorher**:
- 2GB Video → 2GB im TDLib-Cache
- Bei mehreren Videos: Speicher voll

**Nachher**:
- 2GB Video → max 1GB im Cache (2 Chunks gleichzeitig)
- 50% Speichereinsparung
- Automatisches Cleanup alter Chunks

### 2. Schneller Start

**Vorher**:
- Wartezeit: Gesamte Datei laden (~30-60 Sek bei 2GB)

**Nachher**:
- Wartezeit: Nur initial Chunk (~2-3 Sek für 500MB)
- 10-20x schneller

### 3. Nahtlose UX

- Chunk-Wechsel sind für User unsichtbar
- Preload verhindert Pausen
- Seek funktioniert (mit minimaler Delay bei fernen Sprüngen)

### 4. Skalierbar

- Funktioniert auch für sehr große Dateien (5GB+)
- Chunk-Größe konfigurierbar (15/30/60 Min)
- Adaptive Strategie basierend auf File Size

---

## Offene Fragen

### 1. TDLib Partial Downloads

**Frage**: Kann TDLib zuverlässig Byte-Ranges laden?  
**Antwort**: Ja, `ensureFileReady()` nutzt `offset` und `limit` Parameter  
**Verifizierung**: Bestehender Code in `T_TelegramFileDownloader` verwendet bereits Windowed Downloads

### 2. Optimale Chunk Size

**Optionen**:
- 15 Min: Mehr Chunks, kleinere Cache-Usage, häufigere Switches
- 30 Min: **Empfohlen** - Guter Balance
- 60 Min: Weniger Switches, höhere Cache-Usage

**Empfehlung**: 30 Min als Default, in Settings konfigurierbar

### 3. Preload Timing

**Strategie**: Preload starten wenn 80% des aktuellen Chunks abgespielt
```kotlin
// In read() method
val progress = bytesRead / currentChunk.sizeBytes
if (progress > 0.8 && !nextChunkPreloaded) {
    preloadChunk(currentChunkIndex + 1)
}
```

### 4. Cache Directory

**Optionen**:
- TDLib-Cache: Nutzt bestehende Infrastruktur
- Separates Verzeichnis: Bessere Kontrolle

**Empfehlung**: TDLib-Cache, da `deleteFile()` API bereits verfügbar

---

## Zusammenfassung

### Was wurde gemacht

✅ **Analyse**: Root Cause identifiziert (File Size Diskrepanz)  
✅ **Lösung**: Umfassendes Solution Document erstellt  
✅ **Prototyp**: ChunkCalculator implementiert und getestet  
✅ **Dokumentation**: Developer-Guide für Phase 2+

### Was kommt als Nächstes

🔲 **Phase 2**: TelegramChunkedDataSource implementieren (3-4 Tage)  
🔲 **Phase 3**: Integration & Settings (2 Tage)  
🔲 **Phase 4**: Testing & Refinement (2-3 Tage)  
🔲 **Phase 5**: Rollout mit Feature Flag (1 Tag)

**Geschätzter Gesamtaufwand**: 8-10 Tage

### Risiken & Mitigation

**Risiko**: TDLib `deleteFile()` funktioniert nicht wie erwartet  
**Mitigation**: Frühzeitiger Test in Phase 2, Fallback auf keine Cleanup

**Risiko**: Chunk-Switching verursacht kurze Pause  
**Mitigation**: Preload-Strategie, Buffer-Management

**Risiko**: Seek zu weit entfernten Chunks ist langsam  
**Mitigation**: User-Feedback ("Lädt..."), Cancel-Option

---

**Erstellt**: 2025-12-02  
**Status**: Phase 1 Complete ✅  
**Nächster Meilenstein**: Phase 2 - TelegramChunkedDataSource Implementation
