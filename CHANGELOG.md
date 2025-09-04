# 
# FishIT Player – Changelog

All notable changes to this project are documented here. Keep entries concise and tied to commits/PRs.

2025-09-04
- chore(tdlib/java): Bundle TDLib Java classes (`Client.java`, `TdApi.java`) in `:libtd` from TGX-Android/tdlib so reflection can find `org.drinkless.tdlib.*` at runtime. Added keep-rules for `org.drinkless.tdlib.**`.
- fix(tdlib/native): Ensure TDLib JNI is loaded by adding `System.loadLibrary("tdjni")` static initializer in `:libtd` `Client.java`. Prevents `UnsatisfiedLinkError` on first TDLib call; works with packaged `arm64-v8a` and `armeabi-v7a` `jniLibs`.
 - chore(build/telegram): Secure secrets sourcing for `TG_API_ID`/`TG_API_HASH`. Precedence: ENV → root `/.tg.secrets.properties` (untracked) → `-P` Gradle props → default. Avoids committing keys while enabling local test builds.

2025-09-04
- fix(player/routing): Default to internal when `playerMode=external` but no preferred external package is set; prevents unwanted Android app chooser and keeps playback in the internal player.

2025-09-04
- chore(tdlib/v7a): Add prebuilt `libtdjni.so` for `armeabi-v7a` (sourced from TGX-Android/tdlib) under `libtd/src/main/jniLibs/armeabi-v7a/`. Ensures v7a runtime parity alongside arm64.

2025-09-04
- feat(telegram/sync+ds): Upsert `telegram_messages` during sync; persist `localPath` from DataSource; add download trigger by fileId; gate TDLib streaming by `tg_enabled`+AUTH.
  - Worker: fixes pagination (offset=-1); records `fileUniqueId`, `supportsStreaming`, `date`, `thumbFileId`; imports thumbs/metadata (duration/container ext); uses chat photo as series poster.
  - Player: `TelegramTdlibDataSource` checks flag/auth and updates `telegram_messages.localPath` once path resolves.
  - DataSource: `TelegramRoutingDataSource` triggers `DownloadFile(fileId)` and persists `localPath` when known.
  - UI: Settings shows minimal sync progress via WorkManager polling.
  - Work: New `TelegramCacheCleanupWorker` trims TD cache by size (GB) daily.

2025-09-04
- fix(ui/library): Collect VOD/Series category collapse/expand state from SettingsStore in `LibraryScreen`; resolves unresolved references `vodCatCollapsedCsv`, `vodCatExpandedOrderCsv`, `seriesCatCollapsedCsv`, `seriesCatExpandedOrderCsv` during `:app:compileDebugKotlin`.
2025-09-04
- feat(telegram/login): Add reflection-based TDLib auth bridge + Settings login flow.
  - New: `telegram/TdLibReflection.kt` (no direct tdlib dependency; uses reflection to drive auth state + send phone/code).
  - New: `data/repo/TelegramAuthRepository.kt` managing client/auth state.
  - UI: Settings → “Telegram verbinden” button opens number/code dialog; “Status (Debug)” shows current auth state.
  - Build: `TG_API_ID` / `TG_API_HASH` BuildConfig fields (read from `gradle.properties`). TDLib libs still required at runtime.
2025-09-04
- feat(telegram/datasource): Add `TelegramRoutingDataSource` for Media3 with `tg://message?chatId=&messageId=` URIs. Internal player auto‑routes tg scheme to local cached file (Room `telegram_messages.localPath`) and falls back if missing. `VodDetailScreen` now emits tg URIs for TG items.
2025-09-04
- feat(telegram/streaming): Add `TelegramTdlibDataSource` (streaming) that uses TDLib (reflection) to download and serve Telegram message files progressively with seek support. Internal player uses this factory; falls back to routing/HTTP when unavailable.
2025-09-04
- feat(series/tg): Add per-episode TG mapping (Episode.tgChatId/tgMessageId/tgFileId + migration v8→v9). Series playback now prefers `tg://message` when episode carries TG refs; PlayerChooser forces internal for tg://. Updated StartScreen and ResumeCarousel to respect TG episodes.
2025-09-04
- feat(telegram/sync): Add `TelegramSyncWorker` with chat/folder picker UI in Settings. Film Sync/Serien Sync let users select chats as sources; worker fetches history via TDLib (reflection), maps media to VOD or Series (SxxExx heuristics), and upserts DB with TG fields.
2025-09-04
- chore(tdlib/packaging): Add `:libtd` module with `jniLibs` (libtdjni.so). App depends on `:libtd` to ensure runtime availability. BuildConfig `TG_API_ID`/`TG_API_HASH` read from `gradle.properties`.

2025-09-03
- feat(telegram/scaffold): Global feature flag in Settings; Room schema v8 adds `MediaItem.source` + TG refs and new `telegram_messages` table + DAO; Gradle packaging prepped for universal ABI (armeabi-v7a, arm64-v8a); ProGuard keep for TDLib. Added `TelegramRepository` to resolve local file paths when present and wired VOD detail playback to use it. Default OFF; no TDLib libs bundled yet.

2025-09-03
- feat(paging): Introduce Paging 3 for large lists. Room DAO now exposes `PagingSource` for type/category and FTS search; repository provides `Flow<PagingData<MediaItem>>`; `LibraryScreen` renders a paged grid with stable keys. Dependencies: `androidx.paging:paging-runtime-ktx`, `androidx.paging:paging-compose`.
- perf/metrics: Tag JankStats frames with `route` and `scroll` via `PerformanceMetricsState` in `LibraryScreen` and `StartScreen`. Log TTFV when first paged items appear in Library.
- ui/perf: Shimmer placeholders in paged grids during refresh/append (Library grid, Start Live picker) for smoother TTFV.
- ui/perf: ShimmerBox now overlays a gently rotating fish icon while loading per item.
- perf/metrics: Per-route JankStats counters via `JankReporter`; logs windowed frames/janks/rate every 5s.
- fix(build): Mark `loadPrevByType` as `suspend` in `PlaylistRepository` to call suspend DAO (`urlsWithExtraByType`) correctly; resolves "Suspension functions can only be called within coroutine body" during `:app:compileDebugKotlin`.

2025-09-03
- feat(search): Add Room FTS4 index (`mediaitem_fts`) over `name` and `sortTitle` with `unicode61` tokenizer (`remove_diacritics=2`). DAO query `globalSearchFts(...)` and repository switch to FTS with prefix `*` and `AND` across tokens. Migration v6→v7 creates table, sync triggers, and backfills.
- perf/ui: Debounce search input in `LibraryScreen` (300 ms) with `snapshotFlow + debounce + collectLatest` for smooth type‑ahead.

 - perf/lifecycle: Migrate key UI state reads to `collectAsStateWithLifecycle` in `InternalPlayerScreen`, `SettingsScreen`, `CollapsibleHeader`, `FishBackground`, and live row EPG observation; reduces leaks and off-screen work.
  - perf/metrics: Wire `JankStats.createAndTrack(window)` in `MainActivity` for lightweight jank logging; basis for start/jank dashboards.
  - perf/lifecycle: Use `repeatOnLifecycle(STARTED)` for side-effectful Flow collections (nav route changes, fish spin triggers, LiveDetail EPG DB observer, global headers snapshot). Ensures collectors stop when app is backgrounded.

2025-09-03
- docs(roadmap): Added "m3uSuite · Roadmap vNext (Q4 2025 – Q2 2026)" section with milestones M0–M3, fixes, performance levers, new implementations, build strategy, risks, metrics, and sequence.

2025-09-03
- perf(http): Remove DataStore access from OkHttp interceptor; headers now read from an in-memory snapshot (`RequestHeadersProvider` with `StateFlow` + atomic snapshot). `HttpClientFactory` seeds snapshot once and reuses it.
- perf(db/import): Add DAO projections for `url, extraJson` and switch M3U import to batch-read minimal columns in 10k chunks; minimal `addedAt` extraction via regex.
- reliability(epg): Remove UI-owned 5m loop in `MainActivity`; rely on WorkManager (`EpgRefreshWorker` periodic + on-demand once at app start) for refresh.
- ui(lifecycle): Migrate key state reads in `MainActivity` to `collectAsStateWithLifecycle`; start header snapshot updates from `MainActivity` lifecycle scope.
- i18n/sort: Normalize `sortTitle` with NFKD + combining mark removal in `M3UParser`; Room queries now `ORDER BY sortTitle COLLATE NOCASE`.

2025-09-03
- docs(agents): Make AGENTS.md canonical; remove diff-approval; doc updates immediate post-patch.
- chore(gitignore): Ignore SSH/private keys and Android signing keystores to avoid accidental commits.
- docs(consolidate): Set AGENTS.md as sole source; removed AGENTS_NEW.md. Set ROADMAP.md as sole source; removed ROADMAP_NEW.md. Updated references and ARCHITECTURE_OVERVIEW header.
- docs(agents): Add Codex operating rules (override) and WSL build guidance; document repo-local WSL folders `.wsl-android-sdk`, `.wsl-gradle`, `.wsl-java-17` and env setup.
- docs(roadmap): Add cross-reference to AGENTS.md WSL/Linux Build & Test section.
- fix(build): Add missing `animateFloat` imports in `ProfileManagerScreen` and `LibraryScreen`; resolves compile errors.
- fix(player): Define `refreshAudioOptions()` in scope before first use; track options refresh on `onTracksChanged`. No behavior change intended.
- docs(architecture): Update EPG design (persistent Now/Next cache + XMLTV fallback + periodic refresh) and permissions repo; minor player notes.

2025-09-03
- feat(http): Unified headers via `RequestHeadersProvider` + `HttpClientFactory`; applied across Internal Player, Live preview, Coil image requests; external player intent adds VLC `headers` array.
- feat(work): Introduced `SchedulingGateway`; wired Setup and Settings triggers to schedule Xtream refresh + enrichment consistently.
- feat(export): `M3UExporter` now streams to a `Writer` and reads DB in batches to reduce memory usage.
- feat(backup): Local settings backup/restore via SAF (`BackupRestoreSection`) and `SettingsBackupManager`; Quick Import row integrated in Setup and Settings.
- ui(home): Removed Quick‑Import card from Home; Quick Import exists in Setup/Settings only.
- docs(roadmap): Moved completed items from roadmap; roadmap now lists only open tasks.

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
- fix(player/chooser): Default playback mode set to "internal" and settings UI already allows override. Prevents unwanted ask-dialog on details Play.
- fix(library/rows): Show all items per category (removed hard caps of 200 in VOD/Series category rows). Keeps top rows limited but category rows complete.
- fix(kids/assign): After assigning to kid profiles from tiles, show a toast for feedback. No permissions change.
- fix(settings/profile/whitelist): Avoid state updates from background threads in ManageWhitelist; update Compose state on main thread to prevent crashes when opening/editing category contents.
- fix(player/pip): Do not pause playback on Activity onPause when entering Picture-in-Picture; continues stream instead of freezing the last frame.
- fix(vod/plot): Show plot in VOD detail page and add a short 2‑line plot snippet on focused VOD/Series tiles.
- fix(tiles/new): "NEU" badge rendering preserved for new rows; no caps.
- fix(tiles/open): Switch from double‑click to single‑click to open tiles across screens; removes leftover zoom/armed state issues after closing.
- fix(vod/title): Strip category prefixes like "4k-A+ - " from displayed titles in details and tiles.
- feat(header/logo): Fish button in header is now clickable from all major screens (Settings, Details, Profiles) to return to Home without losing in‑progress state.
