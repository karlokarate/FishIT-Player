# Player Migration Status – Phase 5 Complete + Review Fixes

**Status:** Phase 5 – MiniPlayer Migration + Review Fixes  
**Last Updated:** 2025-12-11

---

> ⚠️ **Note on Document Hierarchy:**
> 
> This document tracks the **v2 Player Migration** (Phases 0-14) as defined in `player migrationsplan.md`.
> 
> The following documents are **Legacy v1 Refactoring** documents and describe earlier work on the v1 codebase:
> - `INTERNAL_PLAYER_REFACTOR_ROADMAP.md` (v1 Phases 1-9)
> - `INTERNAL_PLAYER_REFACTOR_STATUS.md` (v1 integration status)
> - `INTERNAL_PLAYER_REFACTOR_SSOT.md` (v1 consolidated knowledge)
> 
> For v2 work, use this document and `PLAYER_ARCHITECTURE_V2.md` as primary references.

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
| `player:miniplayer` | `/player/miniplayer/` | ✅ COMPLETE | MiniPlayer state, manager, overlay (Phase 5) |
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

## 5.5 Phase 5: MiniPlayer ✅ COMPLETE

| Component | Status | Location |
|-----------|--------|----------|
| `MiniPlayerState` | ✅ DONE | `player:miniplayer` |
| `MiniPlayerMode` enum | ✅ DONE | `player:miniplayer` |
| `MiniPlayerAnchor` enum | ✅ DONE | `player:miniplayer` |
| `MiniPlayerManager` interface | ✅ DONE | `player:miniplayer` |
| `DefaultMiniPlayerManager` | ✅ DONE | `player:miniplayer` |
| `MiniPlayerCoordinator` | ✅ DONE | `player:miniplayer` |
| `PlayerWithMiniPlayerState` | ✅ DONE | `player:miniplayer` |
| `MiniPlayerOverlay` | ✅ DONE | `player:miniplayer/ui/` |
| `MiniPlayerModule` (Hilt DI) | ✅ DONE | `player:miniplayer/di/` |
| Unit Tests | ✅ DONE | `player:miniplayer/test/` |

**Key Implementation Details:**
- Battle-tested v1 MiniPlayer logic ported to v2 architecture
- State machine for Fullscreen ↔ MiniPlayer transitions
- Resize mode with DPAD controls (move, resize, confirm, cancel)
- Anchor snapping (corners + center top/bottom)
- Hilt DI integration with `@Singleton` scope
- Combined `PlayerWithMiniPlayerState` for UI layer consumption
- `MiniPlayerCoordinator` for high-level transition orchestration

---

## 5.6 Phase 1-6 Review Fixes (2025-12-11)

Code Review entdeckte und behob folgende Issues:

| Issue | Fix | Status |
|-------|-----|--------|
| Media3 Version 1.9.0 (nicht existent) | Korrigiert auf 1.8.0 | ✅ FIXED |
| `getPlayer()` fehlt in InternalPlayerSession | Methode hinzugefügt für UI-Attachment | ✅ FIXED |
| TelegramFileDataSource nicht in ExoPlayer integriert | DataSource.Factory-Map + DI-Modul erstellt | ✅ FIXED |
| PlayerDataSourceModule.kt | Neues Hilt-Modul für DataSource-Factories | ✅ CREATED |

**Key Changes:**
- `InternalPlayerSession` akzeptiert jetzt `Map<DataSourceType, DataSource.Factory>` via Konstruktor
- ExoPlayer wird mit korrekter `MediaSourceFactory` konfiguriert basierend auf `PlaybackSource.dataSourceType`
- `TelegramFileDataSourceFactory` wird via Hilt injiziert für `tg://` URIs
- Alle Module nutzen jetzt konsistent Media3 1.8.0

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
| `MiniPlayerManager` | Battle-tested | ✅ Ported to player:miniplayer |
| `MiniPlayerState` | Battle-tested | ✅ Ported to player:miniplayer |

---

## 7. Migration Progress

| Phase | Focus | Status |
|-------|-------|--------|
| 0 | Guardrails & Architecture | ✅ COMPLETE |
| 1 | IST-Analyse | ✅ COMPLETE |
| 2 | Player-Modell finalisieren | ✅ COMPLETE |
| 3 | SIP-Kern portieren | ✅ COMPLETE |
| 4 | Telegram & Xtream Factories | ✅ COMPLETE |
| 5 | MiniPlayer | ✅ COMPLETE |
| 6 | Subtitles/CC | ⏳ PENDING |
| 7 | Audio-Spur | ⏳ PENDING |
| 8 | Serienmodus & TMDB | ⏳ PENDING |
| 9 | Kids/Guest Policy | ⏳ PENDING |
| 10 | Fehler-Handling | ⏳ PENDING |
| 11 | Download & Offline | ⏳ PENDING |
| 12 | Live-TV | ⏳ PENDING |
| 13 | Input & Casting | ⏳ PENDING |
| 14 | Tests & Doku | ⏳ PENDING |
