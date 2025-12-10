# Player Migration Status – Phase 4 Complete

**Status:** Phase 4 – Telegram & Xtream PlaybackFactories  
**Last Updated:** 2025-12-10

---

## 1. v2-Player IST-Bestand

### 1.1 Module Overview

| Module | Path | Status | Notes |
|--------|------|--------|-------|
| `core:model` | `/core/model/` | ✅ EXISTS | Contains `RawMediaMetadata`, `PlaybackType` (legacy) |
| `core:player-model` | `/core/player-model/` | ✅ COMPLETE | `PlaybackContext`, `PlaybackState`, `PlaybackError`, `SourceType` |
| `playback:domain` | `/playback/domain/` | ✅ COMPLETE | Interfaces + `PlaybackSourceFactory` + `PlaybackSource` |
| `playback:telegram` | `/playback/telegram/` | ✅ COMPLETE | `TelegramFileDataSource` + `TelegramPlaybackSourceFactoryImpl` |
| `playback:xtream` | `/playback/xtream/` | ✅ COMPLETE | `XtreamPlaybackSourceFactoryImpl` |
| `player:internal` | `/player/internal/` | ✅ COMPLETE | `PlaybackSourceResolver` with factory injection |
| `player:input` | `/player/input/` | ❌ MISSING | Needs creation (Phase 13) |
| `feature:player-ui` | `/feature/player-ui/` | ❌ MISSING | Future phase |

---

## 2. Detailed File Analysis

### 2.1 `core:model` – PlaybackContext (Current)

| File | Status | Notes |
|------|--------|-------|
| `PlaybackContext.kt` | ✅ GOOD | Source-agnostic, well-structured |
| `PlaybackType.kt` | ✅ GOOD | Enum: VOD, SERIES, LIVE, TELEGRAM, AUDIOBOOK, IO |
| `ResumePoint.kt` | ✅ GOOD | Resume tracking model |

**Assessment:** Current `PlaybackContext` is in `core:model`, not `core:player-model`. Migration Plan Phase 2 calls for dedicated `core:player-model` module. **Decision needed: Keep in `core:model` or create new module?**

### 2.2 `playback:domain` – Interfaces

| File | Status | Notes |
|------|--------|-------|
| `ResumeManager.kt` | ✅ GOOD | Clean interface |
| `KidsPlaybackGate.kt` | ✅ GOOD | Clean interface with `GateResult` sealed class |
| `LivePlaybackController.kt` | ✅ GOOD | EPG models included |
| `SubtitleSelectionPolicy.kt` | ✅ GOOD | Interface defined |
| `SubtitleStyleManager.kt` | ✅ GOOD | Style management interface |
| `TvInputController.kt` | ✅ GOOD | TV remote handling |
| `defaults/DefaultResumeManager.kt` | ⚠️ STUB | In-memory only, needs persistence |
| `defaults/DefaultKidsPlaybackGate.kt` | ⚠️ STUB | Minimal implementation |
| `defaults/DefaultLivePlaybackController.kt` | ⚠️ STUB | Minimal implementation |
| `defaults/DefaultSubtitleSelectionPolicy.kt` | ⚠️ STUB | Minimal implementation |
| `defaults/DefaultSubtitleStyleManager.kt` | ⚠️ STUB | Minimal implementation |
| `defaults/DefaultTvInputController.kt` | ⚠️ STUB | Minimal implementation |
| `di/PlaybackDomainModule.kt` | ✅ EXISTS | Hilt bindings |

**Assessment:** All interfaces are clean. Default implementations are stubs for Phase 1.

### 2.3 `playback:telegram` – Telegram Playback

| File | Status | Notes |
|------|--------|-------|
| `TelegramPlaybackSourceFactory.kt` | ⚠️ STUB | Interface only, no implementation |
| `TelegramFileDataSource.kt` | ❌ MISSING | Currently in wrong location (`player:internal`) |

**Assessment:** Needs `TelegramFileDataSource` moved here from `player:internal`.

### 2.4 `playback:xtream` – Xtream Playback

| File | Status | Notes |
|------|--------|-------|
| `XtreamPlaybackSourceFactory.kt` | ⚠️ STUB | Interface + basic `XtreamPlaybackSource` data class |

**Assessment:** Interface defined. Needs real implementation in Phase 4.

### 2.5 `player:internal` – SIP Core (CRITICAL)

| File | Status | Notes |
|------|--------|-------|
| `InternalPlayerEntry.kt` | ✅ GOOD | Composable entry point |
| `session/InternalPlayerSession.kt` | ✅ GOOD | ExoPlayer lifecycle management |
| `state/InternalPlayerState.kt` | ✅ GOOD | StateFlow-based state |
| `source/InternalPlaybackSourceResolver.kt` | ⚠️ PARTIAL | Has test-stream fallback |
| `source/telegram/TelegramFileDataSource.kt` | ❌ VIOLATION | MUST move to `playback:telegram` |
| `ui/InternalPlayerControls.kt` | ✅ GOOD | Compose controls overlay |
| `ui/PlayerSurface.kt` | ✅ GOOD | PlayerView wrapper |

**Layer Violations Found:**

1. **`build.gradle.kts`:**
   ```kotlin
   implementation(project(":pipeline:telegram"))  // ❌ FORBIDDEN
   implementation("dev.g000sha256:tdl-coroutines-android:5.0.0")  // ❌ FORBIDDEN
   ```

2. **`TelegramFileDataSource.kt`:**
   ```kotlin
   import com.fishit.player.pipeline.telegram.tdlib.TelegramClient  // ❌ NON-EXISTENT
   import com.fishit.player.pipeline.telegram.tdlib.TelegramFileException  // ❌ NON-EXISTENT
   ```

---

## 3. Layer Violation Summary

| Violation | Location | Status |
|-----------|----------|--------|
| `pipeline:telegram` dependency | `player:internal/build.gradle.kts` | ✅ FIXED |
| TDLib direct dependency | `player:internal/build.gradle.kts` | ✅ FIXED |
| `TelegramFileDataSource` in wrong module | `player:internal/source/telegram/` | ✅ MOVED to `playback:telegram` |
| Non-existent imports | `TelegramFileDataSource.kt` | ✅ FIXED (uses `TelegramTransportClient`) |

---

## 4. Phase 2 Completion Summary

### 4.1 New Module: `core:player-model`

| Component | Status | File |
|-----------|--------|------|
| `SourceType` enum | ✅ CREATED | `SourceType.kt` |
| `PlaybackContext` | ✅ CREATED | `PlaybackContext.kt` |
| `PlaybackState` enum | ✅ CREATED | `PlaybackState.kt` |
| `PlaybackError` | ✅ CREATED | `PlaybackError.kt` |

### 4.2 New in `playback:domain`

| Component | Status | File |
|-----------|--------|------|
| `PlaybackSourceFactory` interface | ✅ CREATED | `PlaybackSourceFactory.kt` |
| `PlaybackSource` data class | ✅ CREATED | `PlaybackSource.kt` |
| `DataSourceType` enum | ✅ CREATED | `PlaybackSource.kt` |
| `PlaybackSourceException` | ✅ CREATED | `PlaybackSourceFactory.kt` |

---

## 5. Next Phases

### Phase 3: SIP-Kern portieren (player/internal) ✅ COMPLETE

| Task | Status | Notes |
|------|--------|-------|
| Update `InternalPlayerSession` to use new `PlaybackContext` | ✅ DONE | Uses `core:player-model` |
| Update `InternalPlayerState` to use new `PlaybackState` | ✅ DONE | Uses `core:player-model` |
| Create `PlaybackSourceResolver` with factory pattern | ✅ DONE | Injects `PlaybackSourceFactory` set via Hilt |
| Update `InternalPlayerEntry` to use new resolver | ✅ DONE | Accepts `PlaybackSourceResolver` parameter |
| Remove old `InternalPlaybackSourceResolver` | ✅ DONE | Deleted legacy file |
| Update `ResumeManager`/`KidsPlaybackGate` to use new types | ✅ DONE | Uses `core:player-model.PlaybackContext` |
| Clean up v2 logging integration | ✅ DONE | Uses `UnifiedLog` |

### Phase 4: Telegram & Xtream Factories ✅ COMPLETE

| Component | Status | Location |
|-----------|--------|----------|
| `TelegramPlaybackSourceFactoryImpl` | ✅ DONE | `playback:telegram` |
| `TelegramFileDataSource` | ✅ DONE | `playback:telegram` |
| `TelegramPlaybackModule` (Hilt DI) | ✅ DONE | `playback:telegram/di/` |
| `XtreamPlaybackSourceFactoryImpl` | ✅ DONE | `playback:xtream` |
| `XtreamPlaybackModule` (Hilt DI) | ✅ DONE | `playback:xtream/di/` |

**Key Implementation Details:**
- Both factories implement `PlaybackSourceFactory` from `playback:domain`
- Factories are bound via `@IntoSet` for injection into `PlaybackSourceResolver`
- `TelegramPlaybackSourceFactoryImpl` builds `tg://` URIs for `TelegramFileDataSource`
- `XtreamPlaybackSourceFactoryImpl` uses `XtreamUrlBuilder` for authenticated URLs

---

## 6. v1 SIP Components for Porting

From `/legacy/v1-app/.../player/internal/`:

| Component | v1 Status | v2 Port Priority |
|-----------|-----------|------------------|
| `InternalPlayerEntry` | Battle-tested | ✅ Ported |
| `InternalPlayerSession` | Battle-tested | ✅ Ported with new factory pattern |
| `InternalPlayerState` | Battle-tested | ✅ Ported to `core:player-model` types |
| `PlaybackSourceResolver` | NEW | ✅ Created (replaces v1 `InternalPlaybackSourceResolver`) |
| `TelegramFileDataSource` | Battle-tested | ✅ Moved to playback:telegram |
| `SubtitleSelectionPolicy` | Battle-tested | Interface exists, impl stub |
| `LivePlaybackController` | Battle-tested | Interface exists, impl stub |
| `MiniPlayerManager` | Battle-tested | ⏳ Phase 5 |
| `MiniPlayerState` | Battle-tested | ⏳ Phase 5 |

---

## 7. Migration Progress

| Phase | Focus | Status |
|-------|-------|--------|
| 0 | Guardrails & Architecture | ✅ COMPLETE |
| 1 | IST-Analyse | ✅ COMPLETE |
| 2 | Player-Modell finalisieren | ✅ COMPLETE |
| 3 | SIP-Kern portieren | ✅ COMPLETE |
| 4 | Telegram & Xtream Factories | ✅ COMPLETE |
| 5 | MiniPlayer | ⏳ PENDING |
| 6 | Subtitles/CC | ⏳ PENDING |
| 7 | Audio-Spur | ⏳ PENDING |
| 8 | Serienmodus & TMDB | ⏳ PENDING |
| 9 | Kids/Guest Policy | ⏳ PENDING |
| 10 | Fehler-Handling | ⏳ PENDING |
| 11 | Download & Offline | ⏳ PENDING |
| 12 | Live-TV | ⏳ PENDING |
| 13 | Input & Casting | ⏳ PENDING |
| 14 | Tests & Doku | ⏳ PENDING |
