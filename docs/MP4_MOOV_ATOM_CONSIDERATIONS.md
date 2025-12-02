# MP4 Container Format & MOOV Atom Considerations für Telegram Streaming

## Problem: MOOV Atom Location

MP4-Videos können den MOOV-Atom (Metadata) an **zwei verschiedenen Stellen** haben:

### 1. Fast-Start MP4 (MOOV at Beginning)
```
[ftyp][moov][mdat...........................]
 ^---- Metadata am Anfang (optimiert für Streaming)
```

### 2. Traditional MP4 (MOOV at End)
```
[ftyp][mdat...........................][moov]
                                        ^---- Metadata am Ende (nicht streaming-optimiert)
```

## Auswirkung auf Chunked Streaming

### Aktuelles Verhalten (TelegramFileDataSource)

```kotlin
const val MIN_PREFIX_BYTES = 256 * 1024L // 256 KB initial prefix
```

**Problem**:
- Bei Fast-Start MP4: ✅ 256 KB sind ausreichend für FTYP + MOOV
- Bei Traditional MP4: ❌ MOOV am Ende fehlt → ExoPlayer kann Video nicht parsen

## Lösung: Dual-Range Initial Load

### Strategie

Für **INITIAL_START** Mode:
1. **Immer laden**: Erste 256-512 KB (FTYP + MOOV falls am Anfang)
2. **Optional laden**: Letzte 256 KB (MOOV falls am Ende)

### Implementation Options

#### Option A: Zwei separate Downloads (Einfach)

```kotlin
// In T_TelegramFileDownloader.ensureFileReady()
if (mode == EnsureFileReadyMode.INITIAL_START && totalSize > 1_000_000L) {
    // 1. Load header (first 512 KB)
    downloadPrefix(fileId, offset = 0L, limit = 512 * 1024L)
    
    // 2. Load potential MOOV at end (last 256 KB)
    val endOffset = maxOf(0L, totalSize - 256 * 1024L)
    if (endOffset > 512 * 1024L) { // Don't overlap with header
        downloadSuffix(fileId, offset = endOffset, limit = 256 * 1024L)
    }
}
```

**Vorteile**:
- ✅ Funktioniert für beide MOOV-Positionen
- ✅ ExoPlayer kann Container-Format parsen
- ✅ Nur ~768 KB initial statt 1 MB

**Nachteile**:
- ⚠️ TDLib API unterstützt möglicherweise keine Suffix-Downloads
- ⚠️ Zwei separate Downloads (Overhead)

#### Option B: Intelligente Erkennung (Komplex)

```kotlin
// Phase 1: Load first 512 KB
val header = downloadPrefix(fileId, 0L, 512 * 1024L)

// Phase 2: Parse header to detect MOOV location
val moovAtStart = header.contains("moov") || header.hasMoovAtom()

if (!moovAtStart && totalSize > 1_000_000L) {
    // MOOV likely at end, load suffix
    val endOffset = maxOf(0L, totalSize - 256 * 1024L)
    downloadSuffix(fileId, endOffset, 256 * 1024L)
}
```

**Vorteile**:
- ✅ Nur ein zusätzlicher Download wenn nötig
- ✅ Intelligent und effizient

**Nachteile**:
- ⚠️ Benötigt MOOV-Atom-Parsing
- ⚠️ Komplexer Code

## Empfohlene Sofort-Lösung

### Erhöhe Initial Prefix auf 1-2 MB

```kotlin
// In TelegramStreamingSettings oder T_TelegramFileDownloader
const val INITIAL_MIN_PREFIX_BYTES = 2 * 1024 * 1024L // 2 MB statt 256 KB
```

**Rationale**:
- ✅ Deckt die meisten Fast-Start MP4s ab (MOOV meist < 1 MB)
- ✅ Einfache Änderung ohne neue API
- ✅ ExoPlayer kann mit 2 MB gut arbeiten
- ⚠️ Funktioniert NICHT für Traditional MP4 mit MOOV am Ende

### Für Traditional MP4 Support

**Langfristige Lösung**: TDLib unterstützt keine Byte-Range-Requests mit Gaps.

**Workaround**:
1. Bei Playback-Fehler (Source error):
   - Lade die letzten 256 KB separat
   - Versuche erneut
2. Oder: Konvertiere Videos zu Fast-Start MP4 (MOOV at front)

## Integration in ChunkedDataSource

### Angepasste Chunk-Berechnung

```kotlin
data class ChunkInfo(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val startByte: Long,
    val endByte: Long,
    val sizeBytes: Long,
    val requiresSuffix: Boolean = false  // Neu: Für ersten Chunk
) {
    /**
     * Für ersten Chunk: Zusätzlich letzte 256 KB laden
     * falls Video > 10 MB (potentiell MOOV am Ende)
     */
    val suffixBytes: Long
        get() = if (requiresSuffix && index == 0) 256 * 1024L else 0L
}
```

### In TelegramChunkedDataSource

```kotlin
private suspend fun loadChunk(index: Int) {
    val chunk = chunks[index]
    
    // Standard chunk download
    val localPath = serviceClient.downloader().ensureFileReady(
        fileId = fileId,
        startPosition = chunk.startByte,
        minBytes = chunk.sizeBytes,
        mode = if (index == 0) INITIAL_START else SEEK
    )
    
    // Für ersten Chunk: Optional Suffix für MOOV
    if (chunk.requiresSuffix && totalSize > 10 * 1024 * 1024) {
        try {
            val suffixOffset = maxOf(0L, totalSize - 256 * 1024L)
            if (suffixOffset > chunk.endByte) {
                serviceClient.downloader().ensureFileReady(
                    fileId = fileId,
                    startPosition = suffixOffset,
                    minBytes = 256 * 1024L,
                    mode = SEEK
                )
            }
        } catch (e: Exception) {
            // Suffix optional, continue if fails
            TelegramLogRepository.warn(
                source = "TelegramChunkedDataSource",
                message = "Failed to load suffix for MOOV, continuing anyway",
                exception = e
            )
        }
    }
}
```

## TDLib DTO Metadaten

### Frage: Sind Metadaten in DTOs ausreichend?

**TDLib Video DTO** enthält:
```kotlin
data class Video(
    val duration: Int,          // ✅ Ja, vorhanden
    val width: Int,             // ✅ Ja
    val height: Int,            // ✅ Ja
    val fileName: String,       // ✅ Ja
    val mimeType: String,       // ✅ Ja
    val thumbnail: Thumbnail?,  // ✅ Ja
    val video: File             // ✅ File mit size
)
```

**Antwort**: **NEIN, nicht ausreichend für Playback**

TDLib DTOs enthalten **grundlegende Metadaten**, aber **NICHT**:
- ❌ Codec-Details (H.264 Profile, Level)
- ❌ Bitrate, Frame-Rate
- ❌ Audio-Codec, Sample-Rate
- ❌ Subtitle-Tracks
- ❌ Keyframe-Index
- ❌ Chapter-Markers

**ExoPlayer benötigt diese Informationen aus dem MOOV-Atom**, um:
- Container-Format zu parsen
- Codec-Parameter zu extrahieren
- Sample-Tables zu lesen
- Seeking zu ermöglichen

## Fazit & Empfehlung

### Für Phase 2 Implementation

1. **Sofort**: Erhöhe `initialMinPrefixBytes` auf 2 MB
   ```kotlin
   val settings = TelegramStreamingSettings(
       initialMinPrefixBytes = 2 * 1024 * 1024L, // 2 MB
       seekMarginBytes = 1 * 1024 * 1024L        // 1 MB
   )
   ```

2. **Short-term**: Implementiere MOOV-at-End Detection
   - Parse erste 512 KB für MOOV-Atom
   - Falls nicht gefunden: Lade letzte 256 KB

3. **Long-term**: Server-seitige Konvertierung
   - Empfehle Fast-Start MP4 Format
   - Oder: Re-mux beim ersten Playback (Background-Task)

### Dokumentation Update

Aktualisiere `TELEGRAM_CHUNKED_STREAMING_SOLUTION.md`:
- Neue Sektion: "MP4 Container Format Considerations"
- Initial Prefix Size: 2 MB (statt 256 KB)
- Suffix Loading für Traditional MP4 Support

---

**Erstellt**: 2025-12-02  
**Status**: Technical Note / Implementation Guide  
**Nächster Schritt**: Code-Änderungen für erhöhten Initial Prefix
