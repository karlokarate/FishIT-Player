# FishIT-Player

A Kotlin-based Android application for streaming media content with support for Xtream Codes API, Telegram media integration, and Android TV.

---

## Repository State

This repository is in **v2 rebuild mode** on the default branch `architecture/v2-bootstrap`.

### v2 Codebase (Active)

All active development happens in the v2 modules:

| Path | Description |
|------|-------------|
| `/app-v2/` | v2 application module |
| `/core/` | Core libraries (model, persistence, firebase, imaging) |
| `/infra/` | Infrastructure (cache, network, DI) |
| `/feature/` | Feature modules (home, library, detail, settings, etc.) |
| `/player/` | Internal player (SIP) |
| `/playback/` | Playback domain logic |
| `/pipeline/` | Data pipelines (telegram, xtream, audiobook, io) |

### v2 Documentation

| Path | Description |
|------|-------------|
| `/docs/v2/` | Active v2 specs: canonical media, pipelines, internal player, logging |
| `/docs/meta/` | Build, quality, workspace documentation |

### Legacy (Archived)

The v1 codebase and historical documentation are archived under `/legacy/`:

| Path | Description |
|------|-------------|
| `/legacy/v1-app/` | Full v1 app code – **read-only, not in build** |
| `/legacy/docs/` | v1 and historical docs – tagged with LEGACY headers |
| `/legacy/gold/` | Curated "gold nuggets" – valuable patterns from v1 |

---

## Quick Navigation

| Document | Purpose |
|----------|---------|
| **[V2_PORTAL.md](V2_PORTAL.md)** | Main entry point for v2 development |
| **[AGENTS.md](AGENTS.md)** | Agent rules and automation guidelines |
| **[CHANGELOG.md](CHANGELOG.md)** | v2-only changelog (starting at Phase 0) |
| **[ROADMAP.md](ROADMAP.md)** | v2-only roadmap (Phases 0–5) |
| **[ARCHITECTURE_OVERVIEW.md](ARCHITECTURE_OVERVIEW.md)** | v2 architecture overview |

---

## How to Navigate

### For v2 Development

1. Start with **[V2_PORTAL.md](V2_PORTAL.md)** – the main entry point
2. Read **[AGENTS.md](AGENTS.md)** – rules for all agents and automation
3. Browse **[docs/v2/](docs/v2/)** – detailed v2 specifications

### For Legacy Reference

When porting behavior from v1 or researching historical decisions:

1. Browse **[legacy/docs/](legacy/docs/)** – organized by topic:
   - `v1/` – general v1 architecture and implementation
   - `telegram/` – Telegram/TDLib integration docs
   - `ui/` – TV/focus handling and layout patterns
   - `logging/` – logging and telemetry docs
   - `ffmpegkit/` – FFmpegKit integration docs
   - `archive/` – old status and phase reports
   - `agents/` – old agent files

2. Check **[legacy/gold/](legacy/gold/)** – curated valuable patterns (36 patterns from v1 production):
   - **`telegram-pipeline/`** – 8 patterns: unified engine, zero-copy streaming, RemoteId URLs, priority downloads, MP4 validation
   - **`xtream-pipeline/`** – 8 patterns: rate limiting, dual-TTL cache, alias rotation, multi-port discovery, graceful degradation
   - **`ui-patterns/`** – 10 patterns: FocusKit, focus zones, tvClickable, DPAD handling, focus memory, row navigation
   - **`logging-telemetry/`** – 10 patterns: UnifiedLog facade, ring buffer, source categories, structured events, log viewer

   > See **[GOLD_EXTRACTION_FINAL_REPORT.md](GOLD_EXTRACTION_FINAL_REPORT.md)** for overview and porting guidance.

> **Note:** All legacy docs are tagged with a LEGACY banner. Do not use them as authoritative references for v2.

---

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Quality checks
./gradlew ktlintCheck detekt lintDebug test
```

For detailed build instructions, see **[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)**.

### Android build environment (CI & local)

- **CI:** `.github/workflows/android-ci.yml` installs the Android SDK via `tools/env/setup_android.sh` and runs `./gradlew :app-v2:assembleDebug test detekt lintDebug`.
- **Local:** Run `tools/env/setup_android.sh` once (requires `curl` and `unzip`) to install the SDK to `~/.android-sdk`, then run the same Gradle tasks locally.
- **Dev Container (optional):** Open the repo with VS Code Dev Containers to auto-provision Java 17 and the Android SDK via `tools/env/setup_android.sh`.

---

## Technology Stack

- **Language:** Kotlin 2.0+
- **UI:** Jetpack Compose with Material3
- **Database:** ObjectBox (primary), DataStore Preferences
- **Networking:** OkHttp 5.x, Coil 3.x
- **Media:** Media3/ExoPlayer with FFmpeg extension
- **Build:** Gradle 8.13+, AGP 8.5+, JDK 21

---

## License

See [LICENSE](LICENSE) for details.

