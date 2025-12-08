> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Telegram Streaming Runtime Integration – Full Implementation Checklist  
### For `docs/internal_player/` – Copilot-Compatible with Checkboxes

This document defines ALL remaining integration steps required to wire the new runtime Telegram/TDLib/ExoPlayer/Thumbnail settings (introduced in PR #410) into the production streaming pipeline.

Every item includes a checkbox so Copilot and developers can track implementation progress.

---

# ✅ PHASE 0 — Requirements Recap

PR #410 delivered UI + persistence for runtime settings.  
**Still missing:** actual wiring into the engine.

- [x] SettingsStore keys (25+)  
- [x] SettingsRepository flows  
- [x] Advanced settings UI  
- [x] TDLib loglevel setter  
- [ ] Downloader concurrency enforcement  
- [ ] ensureFileReady runtime prefix/margin/timeouts  
- [ ] Thumbnail prefetch wiring  
- [ ] ExoPlayer LoadControl wiring  
- [ ] Diagnostic overlays  
- [ ] App log filtering  
- [ ] Jank telemetry sampling  

---

# 🟦 PHASE 1 — Domain-Level Settings Provider

### Goal  
Avoid leaking DataStore into engine code. Provide **one streamed configuration object** for all engine components.

### Tasks
- [ ] Create `TelegramStreamingSettings` data class:

```kotlin
data class TelegramStreamingSettings(
    val tdlibLogLevel: Int,
    val maxGlobalDownloads: Int,
    val maxVideoDownloads: Int,
    val maxThumbDownloads: Int,
    val initialMinPrefixBytes: Long,
    val seekMarginBytes: Long,
    val ensureFileReadyTimeoutMs: Long,
    val thumbPrefetchEnabled: Boolean,
    val thumbPrefetchBatchSize: Int,
    val thumbMaxParallel: Int,
    val thumbPauseWhileVodBuffering: Boolean,
    val thumbFullDownload: Boolean,
    val exoMinBufferMs: Int,
    val exoMaxBufferMs: Int,
    val exoBufferForPlaybackMs: Int,
    val exoBufferForPlaybackAfterRebufferMs: Int,
    val exoExactSeek: Boolean,
    val showEngineOverlay: Boolean,
    val showStreamingOverlay: Boolean,
    val tgAppLogLevel: Int,
    val jankTelemetrySampleRate: Int,
)
```

- [ ] Create `TelegramStreamingSettingsProvider`  
- [ ] Combine SettingsStore flows to a single `StateFlow<TelegramStreamingSettings>`  
- [ ] Convert KB → Bytes / sec → ms in this layer only  
- [ ] Provide `.currentSettings` accessor for synchronous reads  

---

# 🟩 PHASE 2 — Download Concurrency Enforcement

### Goal  
Prevent Telegram thumbnails from starving VOD by applying dynamic concurrency limits.

### Tasks
- [ ] In `T_TelegramFileDownloader`, add counters:
  - [ ] activeGlobalDownloads  
  - [ ] activeVideoDownloads  
  - [ ] activeThumbDownloads  
- [ ] Add job queues (FIFO recommended)
- [ ] Classify downloads:
  - [ ] VIDEO  
  - [ ] THUMB  
- [ ] Before starting a job:
  - [ ] If global >= maxGlobal → enqueue  
  - [ ] If video >= maxVideo → enqueue  
  - [ ] If thumb >= maxThumb → enqueue  
- [ ] Resume queued jobs on completion  
- [ ] Make queue operations coroutine-friendly  

---

# 🟧 PHASE 3 — ensureFileReady() Runtime-driven

### Goal  
Use runtime settings instead of hardcoded constants.

### Tasks
- [ ] Replace TELEGRAM_MIN_PREFIX_BYTES with `settings.initialMinPrefixBytes`
- [ ] Replace SEEK_MARGIN_BYTES with `settings.seekMarginBytes`
- [ ] Implement:

```kotlin
val requiredByMode = when (mode) {
    INITIAL_START -> s.initialMinPrefixBytes
    SEEK -> startPosition + s.seekMarginBytes
}
```

- [ ] `minBytes` override support  
- [ ] Compute:

```kotlin
val required = if (minBytes > 0) max(requiredByMode, minBytes) else requiredByMode
```

- [ ] Wrap wait loop in:

```kotlin
withTimeout(settings.ensureFileReadyTimeoutMs) { ... }
```

- [ ] On timeout:
  - [ ] structured log  
  - [ ] throw TelegramFileReadTimeoutException  

---

# 🟨 PHASE 4 — Thumbnail Prefetch Integration

### Goal  
Prevent thumbnail fetch from slowing down streaming.

### Tasks
- [ ] Skip prefetch entirely when `thumbPrefetchEnabled = false`
- [ ] Limit prefetch batch size to `settings.thumbPrefetchBatchSize`
- [ ] Add parallel-download semaphore with size `settings.thumbMaxParallel`
- [ ] Pause when any Telegram VOD is buffering:
  - [ ] Subscribe to VOD buffering state  
  - [ ] Suspend new thumb downloads while buffering  
- [ ] Full-download behavior:
  - [ ] If `thumbFullDownload = true` → ensureFileReady(minBytes = totalSizeBytes)
  - [ ] Else prefix-only mode  
- [ ] Log:
  - [ ] batch summary  
  - [ ] per-thumb result (prefix/full, prefix size, path)  

---

# 🟫 PHASE 5 — ExoPlayer Dynamic LoadControl

### Goal  
Update player buffering at runtime.

### Tasks
- [ ] Add helper:

```kotlin
fun buildTelegramLoadControl(s: TelegramStreamingSettings): LoadControl
```

- [ ] Apply:
  - [ ] s.exoMinBufferMs  
  - [ ] s.exoMaxBufferMs  
  - [ ] s.exoBufferForPlaybackMs  
  - [ ] s.exoBufferForPlaybackAfterRebufferMs  
- [ ] Hook into player factory  
- [ ] Ensure LoadControl updates either:
  - [ ] recreate player, or  
  - [ ] rebuild load control dynamically  
- [ ] Apply exact seek toggle  

---

# 🟦 PHASE 6 — Diagnostic Overlays

### Goal  
Visual debugging with minimal performance cost.

### Tasks
**Engine Overlay (showEngineOverlay)**  
- [ ] TDLib engine state (started/authReady/authState)  
- [ ] Active downloads overview  
- [ ] Queued jobs  

**Streaming Overlay (showStreamingOverlay)**  
- [ ] windowStart / windowEnd  
- [ ] downloadedPrefixSize / requiredPrefix  
- [ ] ExoPlayer state (BUFFERING / READY / ENDED)  
- [ ] positionMs / durationMs / isPlaying  

- [ ] Hide overlays when toggles = false  

---

# 🟪 PHASE 7 — App-side Telegram Log Filter

### Goal  
Distinguish app-level logs from TDLib logs.

### Tasks
- [ ] Create:

```kotlin
object TelegramLog {
    fun log(level: Int, tag: String, msg: String)
}
```

- [ ] Only emit logs when:

```kotlin
level <= settings.tgAppLogLevel
```

Levels:  
- 0 ERROR  
- 1 WARN  
- 2 INFO  
- 3 DEBUG  

- [ ] Replace all direct Log.d/x calls in Telegram modules  

---

# 🟧 PHASE 8 — Jank Telemetry Sampling

### Goal  
Reduce telemetry spam, lighten logging overhead.

### Tasks
- [ ] Add counter  
- [ ] Only log every `settings.jankTelemetrySampleRate` frames  
- [ ] Ensure non-blocking + no main-thread work  

---

# 🟦 PHASE 9 — Manual & Automated Verification

### Manual Test Matrix
- [ ] Default settings behave identical to pre-settings version  
- [ ] Lower prefix → faster initial playback  
- [ ] Larger seek margin → smoother seeking  
- [ ] Pause-while-buffering → avoids stream starvation  
- [ ] Limits enforce 1 active video download reliably  
- [ ] TDLib loglevel updates without restart  

### Automated Tests
- [ ] Mapper tests: SettingsStore → TelegramStreamingSettings  
- [ ] ensureFileReady prefix/margin/timeout  
- [ ] Downloader concurrency tests  
- [ ] Thumbnail-prefetch logic  
- [ ] LoadControl builder validation  

---

# 🎉 Expected Outcome

After all steps are completed:

- All runtime settings directly influence real-time streaming  
- No rebuild required for tuning  
- Thumbnails no longer conflict with video  
- Full visibility via overlays  
- Clean architecture:  
  **UI → SettingsStore → DomainSettings → Engine**  
