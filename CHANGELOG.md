#
# m3uSuite – Changelog

All notable changes to this project are documented here. Keep entries concise and tied to commits/PRs.

2025-08-29
- Docs: Rebuilt `ARCHITECTURE_OVERVIEW.md` from latest `AGENTS_NEW.md` overview.
- Docs: Refreshed `AGENTS.md` (policies, quick build/test, summary).
- Docs: Replaced `ROADMAP.md` with the new Import/Export plan (from `ROADMAP_NEW.md`) and fixed cross-reference to `ARCHITECTURE_OVERVIEW.md §8`.
- Docs: Initialized `CHANGELOG.md`.

2025-08-29
- feat(epg): Auto-Erkennung Xtream-Creds + streamId aus M3U/Stream-URLs; Persistenz in SettingsStore; Worker-Scheduling nach Import; Now/Next via `get_short_epg` ohne manuelle EPG-URL.
  - Dateien: `core/xtream/XtreamDetect.kt`, `core/m3u/M3UParser.kt`, `prefs/SettingsStore.kt`, `data/repo/PlaylistRepository.kt`
  - Status: ungeprüft (Build/Test durch Nutzer ausstehend)
