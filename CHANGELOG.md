# 
# m3uSuite – Changelog

All notable changes to this project are documented here. Keep entries concise and tied to commits/PRs.

2025-09-03
- fix(build): Add missing `animateFloat` imports in `ProfileManagerScreen` and `LibraryScreen`; resolves compile errors.
- fix(player): Define `refreshAudioOptions()` in scope before first use; track options refresh on `onTracksChanged`. No behavior change intended.
- docs(architecture): Update EPG design (persistent Now/Next cache + XMLTV fallback + periodic refresh) and permissions repo; minor player notes.

2025-09-02
- feat(profiles): Gast‑Profil eingeführt (konservative Defaults).
- feat(permissions): Pro‑Profil‑Rechte (canOpenSettings, canChangeSources, canUseExternalPlayer, canEditFavorites, canSearch, canSeeResume, canEditWhitelist) + Enforcements (Settings‑Route‑Gating, Externer Player → intern bei Verbot, Favoriten/Assign‑UI‑Gating, Resume‑Sichtbarkeit).
  - Dateien: data/db/Entities.kt, data/db/AppDatabase.kt (v6 + MIGRATION_5_6), data/repo/PermissionRepository.kt, player/PlayerChooser.kt, MainActivity.kt, ui/home/StartScreen.kt, ui/screens/LibraryScreen.kt
- feat(whitelist): Kategorien‑Freigaben + Item‑Ausnahmen; Admin‑UI im ProfileManager (Badges, expandierbare Items mit Kästchen).
  - Dateien: data/db/Entities.kt (kid_category_allow, kid_content_block), data/db/AppDatabase.kt (v5 + MIGRATION_4_5), data/repo/MediaQueryRepository.kt, ui/profile/ProfileManagerScreen.kt
- fix(kid-mode): Home‑Refresh nutzt gefilterte Queries; Favoriten read‑only für eingeschränkte Profile; „Für Kinder freigeben“ nur bei Rechten sichtbar.
  - Dateien: ui/home/StartScreen.kt, ui/components/rows/HomeRows.kt
- chore(db): Schema auf v6; Migrationen idempotent und rückwärtskompatibel.

2025-09-01
- Docs: ROADMAP erweitert um „Performance & Reliability Plan (Top 5)“: Header/HTTP‑Zentralisierung, WorkManager‑Idempotenz, UI‑Lifecycle/Struktur‑Härtung, Streaming‑Exporter + Batch‑I/O, DataStore/EPG‑Hygiene. Siehe Commit 1fbbb49.

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
- feat(epg): Persistenter EPG-Cache (Room `epg_now_next`) mit Now/Next pro `tvg-id`, inkl. In-Memory TTL.
  - Dateien: `data/db/Entities.kt`, `data/db/AppDatabase.kt`, `data/repo/EpgRepository.kt`
- feat(epg): XMLTV Multi-Indexing (`XmlTv.indexNowNext`) für mehrere Kanäle in einem Durchlauf; EPG-Fallback auch ohne Xtream-Creds aktiv.
  - Dateien: `core/epg/XmlTv.kt`, `data/repo/EpgRepository.kt`
- feat(epg): Hintergrund-Refresh (WorkManager) alle 15 Minuten; bereinigt veraltete Cache-Daten (>24h).
  - Dateien: `work/EpgRefreshWorker.kt`, `MainActivity.kt`, `data/repo/PlaylistRepository.kt`
- feat(ui): Live-Tiles zeigen aktuellen Programmtitel + Fortschrittsbalken.
  - Datei: `ui/components/rows/HomeRows.kt`
- feat(xtream): Import fusioniert `epg_channel_id` aus bestehender DB, wenn Provider sie nicht liefert (Match per `streamId`).
  - Datei: `data/repo/XtreamRepository.kt`
- fix(xtream): Auto-Detection ergänzt kompaktes URL-Schema `http(s)://host/<user>/<pass>/<id>` (ohne `/live`/Extension).
  - Datei: `core/xtream/XtreamDetect.kt`
Status: zu testen durch Nutzer

2025-08-29
- feat(ui): Einheitliches Accent-Design (DesignTokens) + „Carded Look“ mit `AccentCard`.
  - Dateien: `ui/theme/DesignTokens.kt`, `ui/common/CardKit.kt`
- feat(ui): Hintergrund-Polish (Gradient + radialer Glow + geblurrtes App‑Icon) für Settings, Start, Library, PlaylistSetup, ProfileGate/Manager, Live/VOD/Series Detail.
  - Dateien: diverse `ui/screens/*`, `ui/home/StartScreen.kt`, `ui/auth/ProfileGate.kt`, `ui/profile/ProfileManagerScreen.kt`
- feat(ui): Kids‑Profile mit kräftigem KidAccent (#FF5E5B) und stärkerem Glow.
  - Dateien: `ui/theme/DesignTokens.kt`, Hintergründe in Screens
- feat(ui): CTAs/Chips: Akzentuierte Buttons und Chips (Assist/Filter) auf Detail‑Screens.
  - Dateien: `ui/screens/VodDetailScreen.kt`, `ui/screens/SeriesDetailScreen.kt`, `ui/screens/LiveDetailScreen.kt`
- fix(ui/touch): Reordering der Live‑Favoriten nur per Long‑Press; Scrollen auf Touch bleibt flüssig.
  - Datei: `ui/components/rows/HomeRows.kt` (detectDragGesturesAfterLongPress)
Status: zu testen durch Nutzer

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
- feat(export): M3U Export in Settings – Teilen als Text oder als Datei speichern (Playlist aus DB generiert inkl. url-tvg, LIVE+VOD URLs).
  - Dateien: `core/m3u/M3UExporter.kt`, `ui/screens/SettingsScreen.kt`
  - Status (nach Änderung): zu testen durch Nutzer
 - fix(export): Teilen als Datei (FileProvider) statt EXTRA_TEXT, um `TransactionTooLargeException` bei großen Playlists zu vermeiden; Provider/paths korrigiert.
  - Dateien: `ui/screens/SettingsScreen.kt`, `app/src/main/res/xml/file_paths.xml`
  - Status (nach Änderung): zu testen durch Nutzer
- fix(xtream): Schonendes Port‑Update in `configureFromM3uUrl()` (passt nur Port an, wenn Host übereinstimmt); Output nur setzen, wenn leer.
  - Datei: `data/repo/XtreamRepository.kt`
  - Status (vor Änderung): ggf. falscher Port bei https‑M3U
  - Status (nach Änderung): zu testen durch Nutzer
