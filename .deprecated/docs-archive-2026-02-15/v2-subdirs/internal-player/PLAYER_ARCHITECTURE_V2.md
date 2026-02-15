# Player Architecture v2 – Guardrails & Layer Definitions

**Status:** Phase 0 – Grundprinzipien & Guardrails  
**Migration Plan:** `docs/v2/player migrationsplan.md`  
**Last Updated:** 2025-12-09

---

## 1. Overview

This document defines the authoritative architecture for the FishIT Player v2 (SIP – Simplified Internal Player). All player-related changes MUST respect this structure.

**Key Principles:**
- Maximize reuse of battle-tested v1 SIP code
- Strict layer separation (no cross-layer imports)
- Player is source-agnostic; specifics live in `playback/*`
- Domain handles policies/preferences; Player only applies them

---

## 2. Module Structure

### 2.1 Layer Hierarchy

```text
┌─────────────────────────────────────────────────────────────┐
│  feature/player-ui                                          │
│    - Player chrome (controls overlay)                       │
│    - Dialogs (CC, audio, error)                             │
│    - Snackbars                                              │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  domain/*                                                   │
│    - PlayItemUseCase                                        │
│    - KidsGate, SeriesModeSettings                           │
│    - Audio/Subtitle preferences per profile                 │
│    - Builds PlaybackContext from DomainMediaItem            │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  player/internal                                            │
│    - InternalPlayerEntry (Composable entry point)           │
│    - InternalPlayerSession (ExoPlayer wrapper)              │
│    - InternalPlayerState (StateFlow)                        │
│    - PlaybackSourceResolver (factory injection)             │
│    - Subtitle engine (SubtitleSelectionPolicy, StyleMgr)    │
│    - Live engine (LivePlaybackController)                   │
│    - System integration (MediaSession, SystemUi)            │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  player/miniplayer                                          │
│    - MiniPlayerState, MiniPlayerManager                     │
│    - MiniPlayerCoordinator, MiniPlayerOverlay               │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  player/input                                               │
│    - PlayerInputContext (TOUCH, REMOTE)                     │
│    - PlayerInputHandler                                     │
│    - Maps input events → PlayerCommands                     │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  playback/telegram                                          │
│    - TelegramPlaybackSourceFactory                          │
│    - TelegramFileDataSource                                 │
│    - Zero-copy streaming, MP4 validation                    │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  playback/xtream                                            │
│    - XtreamPlaybackSourceFactory                            │
│    - HLS/TS DataSource configurations                       │
│    - URL building with authentication                       │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  playback/domain                                            │
│    - ResumeManager (interface + default impl)               │
│    - KidsPlaybackGate (interface + default impl)            │
│    - LivePlaybackController (interface)                     │
│    - SubtitleSelectionPolicy, SubtitleStyleManager          │
│    - TvInputController                                      │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  core/player-model                                          │
│    - PlaybackContext                                        │
│    - PlaybackState, PlaybackError                           │
│    - SourceType enum                                        │
│    - NO source-specific classes                             │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  infra/transport-*                                          │
│    - TelegramTransportClient (TDLib operations)             │
│    - XtreamApiClient (HTTP operations)                      │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Module Paths

| Module | Path | Purpose |
|--------|------|---------|
| `core:player-model` | `/core/player-model/` | Primitive types only |
| `player:internal` | `/player/internal/` | SIP core engine |
| `player:input` | `/player/input/` | Input handling (Touch/DPAD) |
| `playback:domain` | `/playback/domain/` | Abstract interfaces |
| `playback:telegram` | `/playback/telegram/` | Telegram-specific sources |
| `playback:xtream` | `/playback/xtream/` | Xtream-specific sources |
| `feature:player-ui` | `/feature/player-ui/` | UI chrome (future) |

---

## 3. Hard Rules

### 3.1 Import Restrictions

| Module | MAY Import | MUST NOT Import |
|--------|-----------|-----------------|
| `core:player-model` | Kotlin stdlib only | Everything else |
| `player:internal` | `core:player-model`, `playback:domain`, `infra:logging` | `pipeline/**`, `data/**`, TDLib directly |
| `player:input` | `core:player-model` | UI frameworks, Transport |
| `playback:telegram` | `core:player-model`, `transport-telegram`, `playback:domain` | `pipeline/**`, `data/**` |
| `playback:xtream` | `core:player-model`, `transport-xtream`, `playback:domain` | `pipeline/**`, `data/**` |
| `playback:domain` | `core:player-model`, `core:persistence` | `pipeline/**`, Transport directly |

### 3.2 Player Statelessness

The player (`player/internal`) is **stateless** regarding:
- User profiles
- Preferences (audio, subtitles)
- Kids/Guest restrictions
- Resume positions

**All of these are provided by Domain via `PlaybackContext` or injected interfaces.**

### 3.3 DataSource Placement

| DataSource | Location |
|------------|----------|
| `TelegramFileDataSource` | `playback/telegram/` |
| `XtreamHlsDataSource` | `playback/xtream/` |
| Generic HTTP DataSource | Built-in Media3 |

**Never place source-specific DataSources in `player/internal`.**

---

## 4. PlaybackContext Design (Phase 2)

```kotlin
// core/player-model/PlaybackContext.kt
data class PlaybackContext(
    val canonicalId: String,        // e.g., "telegram:123:456" or "xtream:vod:789"
    val sourceType: SourceType,     // TELEGRAM, XTREAM, FILE, etc.
    val uri: String?,               // Direct URI if known
    val sourceKey: String?,         // Key for SourceFactory resolution
    val headers: Map<String, String> = emptyMap(),
    val startPositionMs: Long = 0L,
    val isLive: Boolean = false,
    val isSeekable: Boolean = true,
    val extras: Map<String, String> = emptyMap(),
)

enum class SourceType {
    TELEGRAM,
    XTREAM,
    FILE,
    HTTP,
}
```

**Note:** No series/kids/TMDB info here – that's Domain responsibility.

---

## 5. PlaybackSourceFactory Pattern

```kotlin
// playback/domain/PlaybackSourceFactory.kt
interface PlaybackSourceFactory {
    fun supports(sourceType: SourceType): Boolean
    fun createSource(context: PlaybackContext): PlaybackSource
}

data class PlaybackSource(
    val mediaItem: MediaItem,       // Media3 MediaItem
    val extraHeaders: Map<String, String> = emptyMap(),
)
```

`PlaybackSourceResolver` holds a list of `PlaybackSourceFactory` implementations (injected via Hilt `@IntoSet`) and selects based on `sourceType`.

---

## 6. Migration Phase Overview

| Phase | Focus | Status |
|-------|-------|--------|
| 0 | Guardrails & Architecture | ✅ COMPLETE |
| 1 | IST-Analyse v2-Player & SIP-Bestand | ✅ COMPLETE |
| 2 | Player-Modell finalisieren (`core/player-model`) | ✅ COMPLETE |
| 3 | SIP-Kern portieren (`player/internal`) | ✅ COMPLETE |
| 4 | Telegram & Xtream Factories portieren | ✅ COMPLETE |
| 5 | MiniPlayer portieren (`player/miniplayer`) | ✅ COMPLETE |
| 6 | Subtitles/CC portieren | ⏳ PENDING |
| 7 | Audio-Spur & Profilpräferenzen | ⏳ PENDING |
| 8 | Serienmodus & TMDB | ⏳ PENDING |
| 9 | Kids/Guest Policy | ⏳ PENDING |
| 10 | Fehler-Handling & Retry | ⏳ PENDING |
| 11 | Download & Offline | ⏳ PENDING |
| 12 | Live-TV | ⏳ PENDING |
| 13 | Input & Casting | ⏳ PENDING |
| 14 | Tests & Doku | ⏳ PENDING |

---

## 7. Conflict Resolution

If any conflict arises between:
- This architecture document
- `AGENTS.md` Section 13
- `docs/v2/player migrationsplan.md`
- Other contracts (`INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md`, etc.)

**The agent MUST:**
1. Stop implementation
2. Document the conflict with file/line references
3. Ask the user for resolution
4. Wait for explicit confirmation before proceeding

---

## 8. References

- **Migration Plan:** `docs/v2/player migrationsplan.md`
- **AGENTS.md Section 13:** Player Migration Rules
- **Behavior Contract:** `docs/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md`
- **Legacy SIP Code:** `/legacy/v1-app/.../player/internal/`
- **Gold Patterns:** `/legacy/gold/`
