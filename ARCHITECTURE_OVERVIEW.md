# FishIT Player – Architecture Overview (derived from AGENTS.md)

Deep‑Dive Update: 2025‑09‑23
- Build, networking, images and playback updated to reflect current code.
- Lifecycle/Performance: `collectAsStateWithLifecycle` breit eingesetzt; JankStats in MainActivity aktiv.
  - Global Debug: per Settings schaltbar (Reiter „Import & Diagnose“). Loggt Navigationsschritte (NavController‑Listener), DPAD‑Eingaben (UP/DOWN/LEFT/RIGHT/CENTER/BACK inkl. Player‑Tasten), Tile‑Focus (inkl. OBX‑Titel in Klammern) und OBX‑Key‑Updates (Backfill der sort/provider/genre/year Keys) unter Logcat‑Tag `GlobalDebug`.

Dieses Dokument bietet den vollständigen, detaillierten Überblick über Module, Flows und Verantwortlichkeiten der App. Es ist aus `AGENTS.md` abgeleitet und wird hier als zentrale Architektur‑Referenz gepflegt.

> Technologie-Stack
>
> - Android (Kotlin), Jetpack Compose, Navigation‑Compose  
> - DataStore Preferences, WorkManager  
> - OkHttp (HTTP), Coil 3 (Bilder), Media3/ExoPlayer (Video)  
> - Persistenz: ObjectBox (OBX) als Primär‑Store  
> - Module: `app` (Haupt‑App) + `libtd` (TDLib JNI/Java)

---

## Telegram (Login + Scaffold)

- Feature Flag: Global in Settings (`tg_enabled`, default false). Zusatzoptionen: `tg_selected_chats_csv`, `tg_cache_limit_gb`.
 - ObjectBox: Telegram messages are stored in `ObxTelegramMessage` (chatId/messageId/fileId/uniqueId/supportsStreaming/caption/date/localPath/thumbFileId). Repository/DataSources update OBX directly.
- Login (Alpha): Reflection‑Bridge `telegram/TdLibReflection.kt` + `TelegramAuthRepository` (kein direkter TDLib‑Compile‑Dep). Settings: Button „Telegram verbinden“ (Telefon → Code → Passwort), auto DB‑Key‑Check, Status‑Debug.
- Playback DataSource: `TelegramRoutingDataSource` für Media3 routet `tg://message?chatId=&messageId=` auf lokale Pfade und triggert bei Bedarf `DownloadFile(fileId)`; `localPath` wird persistiert.
- Streaming DataSource: `TelegramTdlibDataSource` (gated via `tg_enabled` + AUTH) lädt/streamt Dateien progressiv (Seek) via TDLib; persistiert `localPath`; Fallback auf Routing/HTTP.
- Build: `TG_API_ID`/`TG_API_HASH` als BuildConfig via sichere Lookup‑Kette (ohne Secrets im Repo). Reihenfolge: ENV → `/.tg.secrets.properties` (root, untracked) → `-P` Gradle‑Props → Default 0/leer. Packaging: TDLib (arm64‑v8a), ProGuard‑Keep für `org.drinkless.td.libcore.telegram.**`. Die JNI‑Lib `libtdjni.so` wird durch einen statischen Initializer in `org.drinkless.tdlib.Client` automatisch geladen.
- Packaging: `:libtd` Android‑Library mit `jniLibs` (`arm64-v8a/libtdjni.so`). App hängt an `:libtd`, sodass TDLib zur Laufzeit vorhanden ist; BuildConfig `TG_API_ID`/`TG_API_HASH` kommen aus `gradle.properties`.
- Build TDLib/JNI: Single‑ABI arm64‑v8a wird mit statisch gelinktem BoringSSL gebaut und ins Modul kopiert.
  - arm64: `scripts/tdlib-build-arm64.sh` (Phase‑2: LTO/GC‑sections/strip‑unneeded zur Größenreduktion)
 - Cache: `TelegramCacheCleanupWorker` trimmt lokale TD‑Dateien täglich auf `TG_CACHE_LIMIT_GB` (GB) – best‑effort Datei‑System‑Trim.


## 1) Build, Run & Tests

- JDK 17, Build via Gradle Wrapper
- `./gradlew clean build`
- (Optional) Lint/Static‑Checks, falls konfiguriert: `./gradlew lint detekt ktlintFormat`
- App-Entry: `com.chris.m3usuite.MainActivity`
- MinSDK 21 (Android 5.0+). Split‑APKs: `arm64-v8a` (primär) und `armeabi-v7a` (Legacy/Fire TV 2nd gen); Universal‑APK ist deaktiviert.
- Min. Laufzeit-Voraussetzung: Netzwerkzugriff (für Xtream, Bilder).

Telegram Gating
- `tg_enabled` (Settings) und AUTHENTICATED (TDLib) sind Pflicht, bevor Sync/Picker/DataSources aktiv werden. Ohne diese Gateways sind alle Telegram-Funktionen no-op; Xtream bleibt unbeeinflusst. Phase-2: separater TDLib-Service-Prozess (eigenes `:tdlib`), FCM Push (`registerDevice`/`processPushNotification`) → weniger Polling (WorkManager als Fallback), QR-Login zusätzlich.
 - Start‑Up Verhalten: `TdLibReflection.available()` prüft nur die Klassenpräsenz (Class.forName mit `initialize=false`) und triggert keinen statischen Initializer in `org.drinkless.tdlib.Client`. Dadurch wird `libtdjni.so` erst geladen, wenn die Telegram‑Funktionalität tatsächlich aktiviert und genutzt wird. Bei FCM‑Push startet der Service TDLib lazy mit BuildConfig‑Keys, verarbeitet Push und bleibt ansonsten im Leerlauf.

## Telegram Service Process

- Service (`.telegram.service.TelegramTdlibService`) läuft in separatem Prozess `:tdlib` und hostet genau eine TDLib‑Client‑Instanz.
- IPC via `Messenger` (minimal): Start/Params, Auth‑Kommandos (Phone/Code/Passwort/QR), Logout, Abfrage Auth‑State; Push‑Kommandos (`registerDevice`, `processPushNotification`); Lifecycle (`SetInBackground`).
- Events: `REPLY_AUTH_STATE` bei Zustandswechseln; TDLib‑Fehler (`TdApi.Error`) werden als `REPLY_ERROR` an die UI gemeldet (Code + Message), sodass Fehl­eingaben (z. B. Telefonnummernformat, ungültige API‑Keys) nicht mehr stumm hängen.
- Foreground: Vordergrund‑Modus bei interaktivem Login und aktiven Downloads (`UpdateFile`); stoppt im Leerlauf/bei AUTHENTICATED.
- Network: beobachtet Connectivity und setzt `SetNetworkType(WiFi/Mobile/Other/None)` entsprechend.

Push (FCM)
- `FirebasePushService` (optional) registriert Token (opportunistisch) und leitet Daten‑Payload an den TDLib‑Service weiter.
- Service startet TDLib bei erstem Token/Push lazy (BuildConfig TG‑Keys) und ruft `RegisterDevice`/`ProcessPushNotification` per Reflection.
- Keine Foreground‑Nutzung für Push, minimaler RAM/CPU‑Footprint; ohne google‑services.json bleibt es no‑op.

Client‑Wrapper (`.telegram.service.TelegramServiceClient`)
- Bind/Unbind; Befehle (`start`, `requestQr`, `sendPhone`, `sendCode`, `sendPassword`, `getAuth`, `logout`, `registerFcm`, `processPush`, `setInBackground`).
- Pufferung/Race‑Fix: Befehle werden in einer Queue gepuffert, bis die Service‑Verbindung steht. So erreicht `CMD_START` den Service garantiert vor nachfolgenden Auth‑Kommandos (z. B. `sendPhone`), und die UI erhält Zustands‑Events zuverlässig (kein „Warte auf Antwort…“‑Hänger mehr).
- `authStates(): Flow<String>` liefert Zustandswechsel an die UI/Repos.

Repository/Settings Lifecycle
- `TelegramAuthRepository` bevorzugt den Service (Fallback: Reflection). Settings binden den Service bei `ON_START` (senden `SetInBackground(false)`) und lösen bei `ON_STOP` `SetInBackground(true)` + Unbind aus.

WSL/Linux Hinweise
- Siehe `AGENTS.md` → „WSL/Linux Build & Test“ (Repo‑lokale Ordner `.wsl-android-sdk`, `.wsl-gradle`, `.wsl-java-17`, empfohlene Env‑Variablen). Windows‑Builds bleiben kompatibel.

---

## 2) Top‑Level Projektstruktur

```
app/src/main/java/com/chris/m3usuite
├── MainActivity.kt                         # Root-Navigation, Startziel (setup|gate), Worker-Scheduling
├── backup/                                 # Settings Backup/Restore (Datei/Drive), Quick Import UI
│   ├── BackupFormat.kt                     # JSON-Manifeste + Payload-Modelle
│   ├── SettingsBackupManager.kt            # Export/Import Orchestrierung (Settings/DB-Teilmengen)
│   ├── BackupRestoreSection.kt             # Settings-Abschnitt (Export/Import UI)
│   └── QuickImportRow.kt                   # Setup/Home-Kachel (optional) für Schnell-Import
├── core/
│   ├── http/HttpClient.kt                  # OkHttp mit UA/Referer/Extra-Headern aus SettingsStore
│   ├── m3u/M3UExporter.kt                  # Exportiert ObjectBox-Kataloge als M3U (Backup/Sharing)
│   └── xtream/                             # Xtream Codes REST-Client, Seeder & Models
│       ├── XtreamClient.kt                 # canonical client (Discovery + Listen + Details + EPG + Play-URLs)
│       ├── XtreamConfig.kt                 # Konfiguration + Ableitung aus Xtream/M3U-URL (get.php)
│       ├── XtreamSeeder.kt                 # Kopf-Import (Live/VOD/Series) + Discovery-Koordination
│       └── XtreamModels.kt
        
        Xtream Client Notes
        - Discovery→Fetch: On first run, the app forces discovery and immediately fires the six reference list calls (live/vod/series: categories + streams).
        - Wildcard category: when no category is selected, requests include `&category_id=0`; if both wildcard and `0` return empty, the client retries once without the parameter to satisfy stricter panels.
        - VOD alias: client uses the discovered alias (`vod|movie|movies`) for categories/streams and falls back in a defined order.
        - VOD IDs: client falls back to `stream_id` when `vod_id`/`movie_id` are missing, so ObjectBox imports succeed on panels that only expose stream IDs.
        - Series episodes: prefer Xtream `episode_id` when building play URLs; fall back to season/episode numbers for legacy panels.
        - Trailers: normalize bare YouTube IDs to full URLs before playback so ExoPlayer receives a valid URI.
        - Direct URLs: `XtreamUrlFactory` consults the capability cache (vodKind/basePath) before emitting play URLs so direct playback matches the resolved server alias.
        - Telemetry: every `player_api.php?action=...` URL is logged at info level to aid debugging traffic sequences.
        - WAF-friendly probes: discovery pings include explicit `action` and `category_id=0` so Cloudflare/WAF returns JSON instead of 521.
├── data/
│   ├── db/                                 # Room: Entities, DAOs, Database
│   │   ├── AppDatabase.kt                  # Versionierung, Migrations, Seeding (Adult Profile)
│   │   └── Entities.kt                     # MediaItem, MediaItemFts (FTS4), Episode, Category, Profile, KidContent,
│   │                                       # ResumeMark, ScreenTimeEntry, Views + DAOs
│   └── repo/                               # Repositories (IO/Business-Logik)
│       ├── XtreamObxRepository.kt          # Xtream Lists/Details → ObjectBox (Heads, Delta, Details)
│       ├── EpgRepository.kt                # Now/Next: persistenter Room‑Cache (epg_now_next) + XMLTV‑Fallback
│       ├── MediaQueryRepository.kt         # Gefilterte Queries (Kids-Whitelist), Suche (FTS)
│       ├── ProfileRepository.kt            # Profile & aktuelles Profil (Adult/Kid), PIN/Remember
│       ├── KidContentRepository.kt         # Whitelist-Verwaltung (allow/disallow/bulk)
│       ├── ResumeRepository.kt             # Wiedergabe-Fortschritt (vod/episode)
│       └── ScreenTimeRepository.kt         # Tages-Limits, Verbrauch, Reset
├── player/
│   ├── PlayerChooser.kt                    # „Ask | Internal | External“ – zentrale Startlogik
│   ├── InternalPlayerScreen.kt             # Media3 Player; Resume; Subs (Style aus Settings); Audio‑Spurauswahl (Sprache/Kanäle/Bitrate); Tap‑Overlay
│   └── ExternalPlayer.kt                   # ACTION_VIEW mit Headern; bevorzugtes Paket
├── prefs/
│   ├── SettingsStore.kt                    # DataStore (alle App-Settings & Flags)
│   └── SettingsSnapshot.kt                 # Serialisierte Sicht (Backup/Restore)
├── domain/
│   └── selectors/ContentSelectors.kt       # Selektoren/Heuristiken für Content-Listen
├── drive/
│   └── DriveClient.kt                      # Optionaler Drive‑Client (Login/Upload/Download)
├── ui/
│   ├── auth/                               # ProfileGate (PIN, Profile wählen), CreateProfileSheet
│   ├── components/                         # UI-Bausteine (Header, Carousels, Controls)
│   ├── home/HomeChromeScaffold.kt          # Gemeinsamer Chrome (Header + Bottom) mit TV-Only Chrome-State (Visible/Collapsed/Expanded)
│   │   - TV: Header/Bottom anfangs sichtbar, auto-collapse bei Content-Scroll; Menu (Burger) toggelt Expanded (Fokus-Trap Header↔Bottom, Content geblurred). Long‑Press DPAD‑LEFT triggert dieselbe Burger‑Funktion (Expand/Collapse). Panels sliden animiert ein/aus; Content-Padding passt sich an.
│   ├── home/StartScreen.kt                 # „Gate“-Home: Suche, Carousels, Live-Favoriten
│   │   - Live-Favoriten-Picker nutzt Paging über `MediaQueryRepository.pagingSearchFilteredFlow("live", …)`; Suchtreffer entsprechen der Library-Suche.
│   │   - Reihen (Serien/Filme) laden lazy per Paging3 über ObjectBox; horizontale Rows mit Skeletons (fisch.png, Shimmer/Puls)
│   │   - Live: Favoriten-Row bleibt; ohne Favoriten globale paged Live-Row mit EPG-Prefetch
│   ├── screens/                            # Setup/Library/Details/Settings
│   │   ├── PlaylistSetupScreen.kt          # Erststart: Xtream-Creds ableiten/speichern & `XtreamSeeder` anstoßen
│   │   ├── LibraryScreen.kt                # Durchsuchen (Filter, Raster/Listen)
│   │   │   - VOD: Kategorien zuerst; bei ausgewählter Kategorie horizontale, paginierte Row (Paging3 über ObjectBox)
│   │   │     mit Material3 Skeleton‑Placeholders (Shimmer/Puls, fisch.png). EPG‑Prefetch für sichtbare Items bleibt aktiv.
│   │   ├── LiveDetailScreen.kt             # Live-Details, Play/Favorit/Kids-Freigabe (Profil-Lookup toleriert fehlende IDs; kein -1 ObjectBox-Dump)
│   │   ├── VodDetailScreen.kt              # VOD-Details, Poster focus + 50% backdrop overlay, Enrichment-Fetch, Resume
│   │   ├── SeriesDetailScreen.kt           # Serien, Staffel/Episoden-Listing, Resume Next
│   │   └── SettingsScreen.kt               # UI-, Player-, Filter-, Profile-Settings
│   ├── skin/                               # TV-Skin (Focus/Scale, Modifiers), Theme
│   ├── state/ScrollStateRegistry.kt        # Scroll-Positions pro Row/Grid
│   ├── theme/                              # Farben, Typo, Theme
│   └── util/Images.kt, AvatarModel.kt      # Coil-ImageRequests mit Headers aus Settings
└── work/
    ├── Workers.kt                          # XtreamRefreshWorker, XtreamEnrichmentWorker (deaktiviert)
    ├── XtreamDeltaImportWorker.kt          # Periodischer Delta‑Import (unmetered+charging) + one-shot trigger
    ├── ObxKeyBackfillWorker.kt             # One‑shot Key‑Backfill (sort/provider/genre/year)
    ├── (removed)                           # EPG periodic worker removed; OBX prefetch on-demand
    ├── SchedulingGateway.kt                # Zentrales Unique-Work Scheduling (KEEP/REPLACE)
    ├── ScreenTimeResetWorker.kt            # täglicher Reset der ScreenTime
    └── BootCompletedReceiver.kt            # Re-Scheduling nach Boot
```

---

## 3) Screens & Navigations‑Flow

Routen (aus `MainActivity`):  
`setup` → `gate` → (`library`/`browse`) → `live/{id}` | `vod/{id}` | `series/{id}` → `settings` → `profiles`

1. **setup = `PlaylistSetupScreen`**
   - Eingaben: Erstauswahl zwischen `get.php`-Link (Credential-Ableitung) oder direktem Xtream-Login (Host/Port/HTTPS/User/Pass/Output). EPG-URL wird automatisch aus den Credentials generiert.
   - Persistiert Quellen (`setSources`) und Xtream-Creds (`setXtHost/Port/User/Pass/Output`) im `SettingsStore`, setzt `XtPortVerified=false` und ruft anschließend `XtreamSeeder.ensureSeeded(force=true, forceDiscovery=true)` auf.
   - Nach Erfolg: `SchedulingGateway.scheduleAll()` (Xtream-Details, Telegram-Cleanup, Key-Backfill) und Navigation zu `gate`.
   - Unterstützt „VIEW“-Deep-Links (Link wird vorbefüllt und sofort validiert).

2. **gate = Start/Home (`StartScreen`)**
   - Suche, Resume‑Carousels (VOD/Episoden), Serien/Filme/TV‑Rows.
   - Live‑Tiles zeigen Now/Next + Fortschrittsbalken (EPG‑Cache).
   - Live‑Favoriten (persistiert via `FAV_LIVE_IDS_CSV`).
   - Kid‑Profile berücksichtigen (Queries über `MediaQueryRepository` filtern per Whitelist).
   - Serien/Filme‑Rows: Bei leerer Suche werden „Zuletzt gesehen“ (links) und „Neu“ (rechts) gemischt. Beim Erststart ohne Resume‑Marks erscheinen nur neue Inhalte; NEU‑Badge markiert nur diese.
   - Optional: „Quick Import“ (Drive/Datei), sofern im Build/UI aktiviert.
   - XtreamSeeder-Fallback: Wenn OBX leer ist und Xtream-Creds vorhanden sind, ruft StartScreen `XtreamSeeder.ensureSeeded(force=false)` auf, um ohne Blocking-Screen Kopf-Daten zu laden. UI bleibt interaktiv; Listen aktualisieren sich via OBX-Subscriptions.

3. **library/browse = `LibraryScreen`**
   - Browsing mit Suchfeld, Grid‑Ansicht und dynamischer Gruppierung/Filter je Typ:
     - VOD: Gruppierung Provider (normalisiert), Jahr, Genre; Textfilter. "Unbekannt" bündelt nicht zuordenbares.
     - Serien: Gruppierung Provider (normalisiert), Jahr, Genre; Textfilter. Episoden on‑demand.
     - Live: Gruppierung Provider (normalisiert) oder Genre (News/Sport/Kids/Music/Doku); kein Jahr; Textfilter.
   - Expand/Collapse je Kategorie mit persistenter Ordnung (CSV in Settings), Rows laden lazy nach.
   - Navigation state preservation: Top‑level switches between Start (`library?q=…`) and Library (`browse`) use Navigation‑Compose state saving (`restoreState=true` and `popUpTo(findStartDestination()){ saveState=true }`). Additionally, a lightweight in‑memory scroll cache in `ScrollStateRegistry` ensures list/grid positions persist even if backstack entries are removed by navigation.

4. **Details: `live/{id}`, `vod/{id}`, `series/{id}`**
   - Aktionen: Play (via `PlayerChooser`), Favorisieren, Kids freigeben/sperren.
   - VOD/Series Details/Poster/Episoden per OBX: delta‑import; Serien on‑demand via `XtreamObxRepository.importSeriesDetailOnce`.

5. **player = `InternalPlayerScreen`**
   - Media3/ExoPlayer, Resume‑Handling (`ResumeRepository`), Untertitel‑Stil, Rotation‑Lock Option.

6. **settings = `SettingsScreen`**
   - Player-Modus (ask/internal/external + bevorzugtes externes Paket), Subtitle‑Stil, Header‑Verhalten, Autoplay Next, TV‑Filter.
   - Daten & Backup: Export/Import (Datei/Drive), Drive‑Einstellungen.
   - Global: Toggle „Kategorie 'For Adults' anzeigen“. Wenn AUS (Default), werden Inhalte mit Kategorie „For Adults“ in Start, Library und Suche ausgeblendet (Filter via `MediaQueryRepository`/UI).

7. **profiles = `ProfileGate` & `ProfileManagerScreen`**
   - Adult-PIN, „Remember last profile“, Anlage Kind‑Profile inkl. Avatar; Kids‑Whitelist‑Listen.

---

## 4) Datenmodell (ObjectBox, OBX)

Primäre Entities (siehe `data/obx/ObxEntities.kt`)
- `ObxLive`/`ObxVod`/`ObxSeries` mit indizierten Schlüsseln (`nameLower`, `sortTitleLower`, `providerKey`, `genreKey`, `yearKey`) und optionalen Metadaten (Poster/Plot/Trailer/Duration…)
- `ObxEpisode` für Serien (seriesId/season/episodeNum + episodeId für direkte Play‑URLs)
- `ObxEpgNowNext` als Now/Next‑Cache (on‑demand Prefetch; UI liest OBX)
- Profile & Rechte: `ObxProfile`, `ObxProfilePermissions`
- Kids: `ObxKidCategoryAllow`, `ObxKidContentAllow`, `ObxKidContentBlock`
- Resume: `ObxResumeMark` (VOD/Episode Position)
- Telegram: `ObxTelegramMessage` (Minimal‑Index + `localPath`)

Aggregierte Indextabellen (Heads‑Import, keine Vollscans)
- `ObxIndexProvider`, `ObxIndexYear`, `ObxIndexGenre`, `ObxIndexLang`, `ObxIndexQuality`

OBX‑ID‑Bridging
- Stabil in `MediaItem.id` kodiert: live=`1e12+streamId`, vod=`2e12+vodId`, series=`3e12+seriesId`. Detail‑Screens lösen in OBX auf und bauen Play‑URLs via `XtreamClient`.

---

## 5) Repositories (Domänenlogik)

Hinweis (OBX-first für Xtream):
- Xtream-Pfade persistieren ausschließlich in ObjectBox. Room wird in App‑Flows nicht mehr verwendet; UI darf nicht auf Room‑Queries zurückfallen.

- XtreamSeeder
  - `ensureSeeded(context, store, reason, force, forceDiscovery)` orchestriert Discovery (falls nötig) und importiert Kopf-Listen (Live/VOD/Series) via `XtreamObxRepository.importHeadsOnly`. Aktualisiert `setLastSeedCounts`, `setLastImportAtMs`, `setXtPortVerified`.
  - Wird von Setup, Settings ("Import aktualisieren"), StartScreen (Fallback) und `XtreamDeltaImportWorker` genutzt.

- XtreamObxRepository
  - ObjectBox-first Repository für sämtliche Xtream-Operationen: Kopf-Import (`importHeadsOnly`), Quick-Seed (`seedListsQuick`), Detail-Refresh (`refreshDetailsChunk`), per-Provider/Genre/Year Queries, Search (nameLower + category match) sowie EPG-Helper.
  - `newClient(forceRefreshDiscovery)` kapselt Discovery (Port/Alias) via `XtreamClient`.
  - `importHeadsOnly(deleteOrphans=false)` füllt Live/VOD/Series mit Kopf-Daten, aktualisiert Provider-/Genre-/Year-Indizes.
  - `refreshDetailsChunk(vodLimit, seriesLimit)` lädt fehlende Details/Posterdaten in kleinen Batches (bounded concurrency).

- XtreamConfig / XtreamDetect
  - `XtreamConfig.fromM3uUrl(...)` dient weiterhin zur Ableitung der Zugangsdaten aus `get.php`-Links (Setup / Settings).
  - `XtreamDetect.detectCreds` unterstützt das Parsen kompakter live/movie/series-URLs, falls Nutzer Links aus Playern teilen.

- MediaQueryRepository
  - Liefert Listen & Suchergebnisse – bei Kid‑Profil gefiltert nach Whitelist (Tabellen‑Join/Filter via In‑Memory‑Set).

- ProfileRepository
  - Aktuelles Profil (Adult/Kid), „remember last profile“, Adult‑PIN‑Gate.

- PermissionRepository
  - Liefert effektive Rechtematrix je aktuellem Profil; seeden von Defaults je Profiltyp (Adult/Kid/Gast); UI‑Gating (Settings, Quellen, Externer Player, Favoriten, Suche, Resume, Whitelist).

- KidContentRepository
  - allow/disallow (einzeln/bulk) von Content‑IDs pro Kind und Typ.

- ResumeRepository
  - Set/Clear + Abfragen der jüngsten VOD/Episoden‑Marks für Resume‑Carousels.

- ScreenTimeRepository
  - Tages‑Key (yyyy‑MM‑dd), ensure‑Entry, addUsage(delta), resetToday; verwendet `ScreenTimeResetWorker` täglich.

Backup/Restore
- SettingsBackupManager
  - Export/Import von Settings (DataStore) und optionalen Teilmengen (Profile/Resume‑Marks) gemäß BackupFormat.
- BackupFormat
  - JSON‑Struktur: Manifest + Payload (inkl. Versionierung); kompatibel mit zukünftigen Erweiterungen.
- DriveClient (optional, Shim)
  - Platzhalter‑Implementierung ohne echte Sign‑In/REST‑Anbindung. Upload/Download sind als Stubs vorhanden; echte Integration optional.

---

## 6) Core/HTTP & Media

- HttpClientFactory: Singleton‑OkHttpClient mit persistentem Cookie‑Jar (Disk‑backed) und dynamischer Disk‑HTTP‑Cache‑Größe (ABI/Low‑RAM/Platz): 32‑bit ≈32 MiB (Cap 64 MiB), 64‑bit ≈96 MiB (Cap 128 MiB), mind. 1% frei. Headers via `RequestHeadersProvider` (UA/Referer/Extras) – konsistent für alle HTTP‑Aufrufe.
- Live‑Preview (HomeRows): ExoPlayer + `DefaultHttpDataSource.Factory` (UA/Referer gesetzt). Preview standardmäßig AUS; Fokus‑EPG hat 120 ms Cooldown. Sichtbare Items für Prefetch (EPG/Details) laufen gedrosselt (`distinctUntilChanged().debounce(100ms)`).
- Images/Coil (v3): Globaler ImageLoader mit Hardware‑Bitmaps, ~25% RAM‑Cache und dynamischem Disk‑Cache (32‑bit ≈256 MiB, 64‑bit ≈512 MiB; Caps 384/768 MiB; mind. 2% frei). Requests lesen ihre Slot‑Größe via `onSizeChanged`, nutzen RGB_565 global, vergeben WxH‑sensitive Cache‑Keys und überschreiben TMDb‑URLs passend (`AppPosterImage`/`AppHeroImage`). ContentScale: Logos=Fit, Avatare=Crop, Karten=Crop; Standard‑Fallbacks stammen aus den neuen Fish‑Assets.
- Player (Media3): `DefaultRenderersFactory` (Decoder‑Fallback an, Extensions aus) bevorzugt Hardware‑Decoder. `PlayerView` verwendet `surface_view` (TV performanter). `DefaultLoadControl` konservativ.
- UI‑State: Scrollpositionen von LazyColumn/LazyRow/LazyVerticalGrid werden pro Route/Gruppe gespeichert und beim Wiederbetreten wiederhergestellt. Zentrale Helper in `ui/state/ScrollStateRegistry.kt` (`rememberRouteListState`, `rememberRouteGridState`).
- XtreamSeeder / XtreamObxRepository: Kopf-Import (Live/VOD/Series) erstellt Provider-/Genre-/Year-Indizes (`ObxIndex*`) direkt aus den Xtream-Listen.
- XtreamImportCoordinator: trackt `seederInFlight`, kapselt `runSeeding`, reiht `XtreamDeltaImportWorker.triggerOnce*` während Seeds ein und spült sie danach. Worker blockieren via `waitUntilIdle()`; `HomeChromeScaffold` zeigt den Status als dezente "Import läuft…"-Kapsel.
- Seeding-Whitelist: Globales Prefix-Set (DE/US/UK/VOD als Default) begrenzt die initialen Quick-Seed-Requests auf ausgewählte Regionen; weitere Prefixe können in Settings aktiviert werden.
- Kategorie-Buckets: `CategoryNormalizer.normalizeBucket(kind, groupTitle, tvgName, url)` bleibt aktiv und wird während des Xtream-Kopfimports angewandt (≤10 stabile Buckets je Kind; Qualitätstoken werden ignoriert).
- Aggregatindizes: `XtreamSeeder` aktualisiert `ObxIndexProvider/Year/Genre` nach jedem Kopf-Import; UI-Gruppierungen lesen ausschließlich daraus (keine Vollscans).
- Adults toggle greift global: `showAdults=false` filtert alle Kategorien/Items, deren Id oder Label mit `For Adult`/`adult_` beginnt – für Start, Library, Search und Paging-Flows.
- EpgRepository: Liefert Now/Next mit Persistenz in ObjectBox `ObxEpgNowNext`, XMLTV‑Fallback bei leeren/fehlenden Xtream‑Antworten und kurzer In‑Memory‑TTL. Integrationen in Live‑Tiles und Live‑Detail. Kein periodischer Worker; Aktualisierung erfolgt on‑demand und beim App‑Start für Favoriten.
- XtreamDetect: Ableitung von Xtream‑Creds aus `get.php`/Stream‑URLs; `parseStreamId` (unterstützt `<id>.<ext>`, Query `stream_id|stream`, HLS `.../<id>/index.m3u8`). Status: zu testen durch Nutzer.
- CapabilityDiscoverer: Strenger Port‑Resolver. Ein Port gilt nur dann als „erreichbar“, wenn eine `player_api.php`‑Probe mit HTTP 2xx beantwortet wird und die Antwort parsebares JSON enthält. Probes setzen jetzt explizit `action` (`get_live_streams|get_series|get_vod_streams`) und `category_id=0` (Wildcard), um WAF/Cloudflare 521 zu vermeiden. HTTP‑Kandidaten priorisieren `8080`; `2095` wurde entfernt. Cache‑Treffer werden einmal revalidiert; bei Fehlschlag wird der Cache‑Eintrag verworfen und ein neuer Kandidatenlauf durchgeführt. Falls kein Kandidat gewinnt, greift als letzte Option der schema‑abhängige Standardport (80/443), den die folgenden Action‑Probes validieren. Wenn der Benutzer explizit einen Port angibt, wird der Resolver übersprungen und der Port unverändert verwendet.

---

## 7) Hintergrund‑Jobs (WorkManager)

- XtreamRefreshWorker: deaktiviert (no‑op). On‑demand Lazy Loading statt Periodic Sync.
- XtreamEnrichmentWorker: deaktiviert (no‑op). Details werden on‑demand geladen.
- XtreamDeltaImportWorker: Periodischer Delta-Import (12h; unmetered + charging) + on-demand One-Shot Trigger – lädt ausschließlich fehlende Details (VOD/Series) und triggert EPG-Favoriten-Refresh.
- ObxKeyBackfillWorker: One-shot Backfill der neuen OBX-Keys.
- Globaler Schalter: `M3U_WORKERS_ENABLED` (DataStore). Wenn `false`, werden Xtream-bezogene Worker und `XtreamSeeder`-Trigger übersprungen; App-Start löst keinen Kopf-Import aus und Settings-Import bleibt passiv.

ObjectBox Store (Primary)
- Entities: `ObxCategory`, `ObxLive(nameLower)`, `ObxVod(nameLower)`, `ObxSeries(nameLower)`, `ObxEpisode`, `ObxEpgNowNext`.
- Store bootstrap in `ObxStore` (singleton `BoxStore`).
- Import: `XtreamObxRepository.importAllFull()` schreibt Full Content inkl. robust `categoryId`.
- Search: Queries on `nameLower.contains(q)` + category-name matches; sort by `nameLower`.
- EPG: `prefetchEpgForVisible` persists short EPG to `ObxEpgNowNext`. `LiveDetailScreen` subscribes to query results (event-based updates).

ID Bridging for UI
- OBX-backed lists adapt to the existing `MediaItem` view-model by encoding stable IDs into `MediaItem.id`:
  - live: `1_000_000_000_000L + streamId`
  - vod: `2_000_000_000_000L + vodId`
  - series: `3_000_000_000_000L + seriesId`
- Detail screens resolve these IDs to OBX rows and construct play URLs via `XtreamClient`. Legacy Room IDs remain supported for backwards compatibility during transition (favorites/resume).

UI Consumption
- Start: lists from ObjectBox; Live favorites prefer OBX; LiveRow EPG prefetch.
- Library: full load path ObjectBox-first; category sheet from `ObxCategory` with counts + search; Room only if OBX empty.
- SeriesDetail: episodes from OBX (playExt) when available; robust series episode URLs; Room fallback remains.
- LiveDetail: initial repo Now/Next; subsequent updates via ObjectBox subscription.
  (über `SchedulingGateway` mit Unique‑Work geplant)
- EPG periodic worker removed: No background WorkManager job. Freshness via on‑demand prefetch (visible rows/favorites at app start) writing to ObjectBox; Room remains as secondary cache for compatibility.
- ScreenTimeResetWorker: Setzt täglich den Verbrauch der Kinder‑Profile zurück.
- BootCompletedReceiver: Plant Worker nach Reboot neu.

Profiles/Kids/Resume/Telegram (OBX)
- Profiles: `ObxProfile` replaces Room `profiles`; `PermissionRepository` seeds/reads `ObxProfilePermissions`.
- Kids whitelist: `ObxKidContentAllow`/`ObxKidCategoryAllow`/`ObxKidContentBlock` query keys referenced by `MediaQueryRepository` gating.
- Screen Time: `ObxScreenTimeEntry` stores per‑day usage/limit; `ScreenTimeRepository` reads/writes ObjectBox.
- Resume: `ObxResumeMark` stores VOD resume by encoded mediaId; series resume migrates later when stable episode keys are available.
- Telegram: `ObxTelegramMessage` stores metadata/localPath; DataSources and Sync worker update this store.

> Scheduling wird in `MainActivity` aktiviert, sobald `m3uUrl` gesetzt ist.

---

## 8) Settings (DataStore)

Wichtige Keys (Auszug):
- Quellen: `M3U_URL`, `EPG_URL`, `USER_AGENT`, `REFERER`, `EXTRA_HEADERS`
- Player: `PLAYER_MODE` (ask|internal|external), `PREF_PLAYER_PACKAGE`
- Xtream: `XT_HOST`, `XT_PORT`, `XT_USER`, `XT_PASS`, `XT_OUTPUT`
- Feature-Gates: `M3U_WORKERS_ENABLED` (globaler Schalter für Xtream-Worker/Scheduling inkl. `XtreamSeeder`), `ROOM_ENABLED`
- UI/UX: `HEADER_COLLAPSED_LAND`, `HEADER_COLLAPSED`, `ROTATION_LOCKED`, Autoplay etc.
- Live‑Filter: `LIVE_FILTER_*` (Deutsch, Kids, Provider, Genres), `FAV_LIVE_IDS_CSV`
- Profile/PIN: `CURRENT_PROFILE_ID`, `ADULT_PIN_SET`, `ADULT_PIN_HASH`
- „Neu seit App‑Start“ Tracking: `FIRST_*_SNAPSHOT`, `NEW_*_IDS_CSV`, `EPISODE_SNAPSHOT_IDS_CSV`, `LAST_APP_START_MS`

Export/Import‑Scope siehe Roadmap – Standard: alle DataStore‑Keys + optionale Profile/Kids‑Whitelist.

---

## 9) Verantwortlichkeiten je Datei (Kurzreferenz)

- `MainActivity.kt` → NavGraph, Startziel, Worker-Scheduling, System UI (Bars).
- `ui/screens/PlaylistSetupScreen.kt` → Erststart/Onboarding, Speichern der Quellen, Import auslösen.
- `ui/home/StartScreen.kt` → Home/Gate (Suche, Carousels, Live‑Favoriten).  
  Änderung: Schnell‑Import‑Karte entfernen (siehe Roadmap).
- `ui/screens/SettingsScreen.kt` → Einstellungen; Export/Import‑UI ergänzen, Drive‑Einstellungen optional.
- `ui/screens/*DetailScreen.kt` → Anzeigen + Aktionen (Play/Favorit/Whitelist).  
- `player/PlayerChooser.kt` → zentrale Startlogik für Wiedergabe (Ask/Internal/External).  
- `player/InternalPlayerScreen.kt` → interner Player inkl. Resume/Subs/Rotation.  
- `core/xtream/*` → REST-Client, Seeder und Modelle für Xtream; Konfiguration wird aus Xtream-Formular oder `get.php`-Links abgeleitet.  
- `data/repo/*` → sämtliche IO/Business‑Operationen (Import, Query, Profile, Resume, ScreenTime).  
- `work/*` → on‑demand/maintenance Tasks (periodic jobs überwiegend entfernt).  
- `prefs/SettingsStore.kt` → sämtliche Preferences und Typed Getter/Setter.  
- `ui/util/Images.kt` → einheitliche Header für Coil/Streams.
 - `backup/*` → Backup/Restore Flow (UI + Manager + Formate).  
 - `drive/DriveClient.kt` → Drive‑Integration (optional).  
 - `work/SchedulingGateway.kt` → Einheitliche Unique‑Work Steuerung.

---

## 10) Bekannte Punkte & potentielle Probleme

- Drive‑Integration: Shim vorhanden (`drive/DriveClient.kt`), echte Auth/REST‑Flows optional.  
- Schnell‑Import liegt aktuell auf dem Home‑Screen; soll in `setup` (Onboarding).  
- Import/Export für Settings existiert nicht – UI‑Hooks in `SettingsScreen` fehlen.  
- Provider-Mapping: `CategoryNormalizer` deckt die gängigen Panels ab; für exotische Anbieter sind Provider-Profile/Overrides (vNext) vorgesehen.
- Kids‑Filter: `MediaQueryRepository` berechnet erlaubt‑Sets in Memory; bei großen Katalogen ggf. optimieren (Join im DAO).  
- Worker starten abhängig von gültigen Xtream-Credentials (`xtHost/Port/User/Pass`); bei Import-Fehlern greift nur der WorkManager-Retry.  
- Headers: UA/Referer müssen für Streams und Bilder konsistent gesetzt werden (bereits implementiert; bei Anpassungen beachten).

---

## 11) Wo implementiere ich was? (Cheat‑Sheet)

- Onboarding (Schnell‑Import verlegen): `ui/screens/PlaylistSetupScreen.kt`, `MainActivity.kt`.  
- Settings‑Export/Import: neue Util `settings/SettingsBackup.kt` + UI in `SettingsScreen.kt`; optional Worker `SettingsAutoBackupWorker` in `work/`.  
- Drive‑Login & Upload/Download (optional): Paket `drive/` (Auth, REST v3) vervollständigen; Manifest‑Scopes & Gradle‑Deps ergänzen.  
- Manueller Dateiexport lokal: SAF‑Intents in `SettingsScreen.kt` (ACTION_CREATE_DOCUMENT/ACTION_OPEN_DOCUMENT).  
- Nach Import: `XtreamSeeder.ensureSeeded(...)` läuft direkt; danach `SchedulingGateway.scheduleAll()` (Delta-Details, Telegram-Cleanup, Key-Backfill).

---

## 12) Acceptance‑Checks (manuell)

- Erststart → Setup erscheint; nach Speichern Import ok; Home zeigt Inhalte.  
- Home zeigt keinen Schnell‑Import‑Block mehr.  
- Settings enthält Export, Import, Drive‑Einstellungen; Export erzeugt Datei; Import setzt Settings und stößt Refresh an.  
- (Optional) Drive‑Login funktioniert; Up/Download der Settings‑Datei ok.

---

## 13) Abhängigkeitskarte (Deep Dependency Map)

- UI → Repositories → DB/HTTP: Screens und ViewModels konsumieren Repositories; direkte DAO‑Zugriffe vermeiden (Kid/Gast stets via `MediaQueryRepository`).
- Player → core/http + prefs: `InternalPlayerScreen`/`ExternalPlayer` nutzen `RequestHeadersProvider`/`HttpClientFactory` und `SettingsStore` (Header/Player‑Modus).
- EPG: `EpgRepository` nutzt `core/epg/XmlTv`, `SettingsStore` (EPG URL), DB‑Tabelle `epg_now_next`; UI‑Tiles/Details konsumieren Repository.
- Import: `XtreamSeeder` + `XtreamObxRepository` nutzen `XtreamClient`/`XtreamDetect`, `HttpClientFactory`, ObjectBox und `SettingsStore`; es gibt keine aktive M3U-Parsing-Pipeline mehr.
- Xtream: `XtreamClient` + `XtreamObxRepository` decken Discovery, Listen, Details, Episoden und EPG ab; `SettingsStore` hält Host/Port/User/Pass/Output sowie Import-Historie.
- Backup: `SettingsBackupManager` nutzt `SettingsStore`, `DbProvider` (optionale Daten), `DriveClient` (optional) und `BackupFormat`.
- Work: `SchedulingGateway` orchestriert XtreamRefresh/XtreamEnrichment/EpgRefresh; `BootCompletedReceiver` re‑schedules.

Querschnitt
- `prefs/SettingsStore` ist zentrale Quelle für Header/Quellen/Filter/Player.
- `core/http/RequestHeadersProvider` wird in Player, Preview (HomeRows), Images (Coil) und ExternalPlayer angewandt.

---

## 14) Optimierungsempfehlungen (nicht‑brechend)

- Scheduling konsolidieren: Alle Jobs ausschließlich über `SchedulingGateway` (UniqueName + klare KEEP/REPLACE Policies). Bei neuen Jobs verpflichtend.
- Lifecycle Hygiene: In UI durchgängig `collectAsStateWithLifecycle`; lange Flows in `repeatOnLifecycle` kapseln; große Composables weiter entflechten.
- Settings‑Snapshot: Bei mehrfachen `first()`‑Zugriffen `SettingsSnapshot` einsetzen; Writes batched via `DataStore.edit {}`.
- HTTP/Headers: `RequestHeadersProvider` flächendeckend sichern (Internal/External/Preview/Coil); Extra‑Header JSON konsistent mergen.
- DB‑Filter: Optional DAO‑seitige Pfade für Kid‑Whitelist bei sehr großen Katalogen (statt rein in‑memory).
- Tests: Unit‑Tests für Backup‑Roundtrip, EPG‑TTL/LRU, SchedulingGateway‑Policies ergänzen.
