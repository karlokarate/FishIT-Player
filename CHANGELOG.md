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
  - Status (vor Änderung): nicht funktionierend (Now/Next unzuverlässig)
  - Status (nach Änderung): zu testen durch Nutzer

2025-08-29
- fix(playback): Einheitliche Header (User-Agent/Referer) auch für VOD/Serie/Resume und Live-Preview gesetzt; Live-Tile-Preview nutzt nun MediaSource mit DefaultHttpDataSource und Default-Request-Properties.
  - Dateien: `ui/screens/VodDetailScreen.kt`, `ui/screens/SeriesDetailScreen.kt`, `ui/components/ResumeCarousel.kt`, `ui/components/rows/HomeRows.kt`
  - Status (vor Änderung): nicht funktionierend (302/Redirect ohne Header)
  - Status (nach Änderung): zu testen durch Nutzer

2025-08-29
- fix(build): Korrigierte Header-Implementierung in ResumeCarousel (Coroutine statt runBlocking; Flow.first korrekt aus Coroutine), fehlende Importe (`collectAsState`, `flow.first`) ergänzt.
  - Dateien: `ui/components/ResumeCarousel.kt`, `ui/components/rows/HomeRows.kt`, `ui/screens/VodDetailScreen.kt`
  - Status (vor Änderung): nicht funktionierend (Build-Fehler)
  - Status (nach Änderung): zu testen durch Nutzer

2025-08-29
- fix(redirect): Erlaube Cross‑Protocol Redirects (http↔https) in allen Player‑HTTP‑Factories; zusätzliche Extra‑Header (JSON aus Settings) werden bei internen Playern und Live‑Preview angewandt.
  - Dateien: `player/InternalPlayerScreen.kt`, `ui/components/rows/HomeRows.kt`
  - Status (vor Änderung): nicht funktionierend (302 bei Redirects)
  - Status (nach Änderung): zu testen durch Nutzer

2025-08-29
- feat(epg): Neuer `EpgRepository` mit kurzem TTL‑Cache (Now/Next via `get_short_epg`), UI‑Integration in Live‑Tiles und Live‑Detail.
  - Dateien: `data/repo/EpgRepository.kt`, `ui/components/rows/HomeRows.kt`, `ui/screens/LiveDetailScreen.kt`
  - Status (vor Änderung): nicht funktionierend (Now/Next inkonsistent, keine Caches)
  - Status (nach Änderung): zu testen durch Nutzer
- feat(epg): XMLTV‑Fallback für Now/Next, falls `get_short_epg` leer/fehlerhaft ist (nutzt `tvg-id` → XMLTV `programme`, streaming Parser, frühzeitiger Abbruch).
  - Dateien: `core/epg/XmlTv.kt`, `data/repo/EpgRepository.kt`
  - Status (nach Änderung): zu testen durch Nutzer
- feat(settings): Sichtbare Schaltfläche „EPG testen (Debug)“ unter Quelle.
  - Datei: `ui/screens/SettingsScreen.kt`
  - Status (nach Änderung): zu testen durch Nutzer
- fix(xtream): Schonendes Port‑Update in `configureFromM3uUrl()` (passt nur Port an, wenn Host übereinstimmt); Output nur setzen, wenn leer.
  - Datei: `data/repo/XtreamRepository.kt`
  - Status (vor Änderung): ggf. falscher Port bei https‑M3U
  - Status (nach Änderung): zu testen durch Nutzer
