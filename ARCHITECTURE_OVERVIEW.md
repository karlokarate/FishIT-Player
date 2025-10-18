# FishIT Player – Architecture Overview (derived from AGENTS.md)

Deep‑Dive Update: 2025‑09‑23
- Build, networking, images and playback updated to reflect current code.
- Lifecycle/Performance: `collectAsStateWithLifecycle` breit eingesetzt; JankStats in MainActivity aktiv.
- Global Debug: per Settings schaltbar (Reiter „Import & Diagnose“). Loggt Navigationsschritte (NavController‑Listener), DPAD‑Eingaben (UP/DOWN/LEFT/RIGHT/CENTER/BACK inkl. Player‑Tasten), Tile‑Focus (inkl. OBX‑Titel in Klammern) und OBX‑Key‑Updates (Backfill der sort/provider/genre/year Keys) unter Logcat‑Tag `GlobalDebug`. Die Kern‑Row‑Engines (MediaRowCore/Paged) emittieren Tile‑Focus für alle Kacheln.
- Global Debug erweitert: Zusätzlich loggen alle fokussierbaren Widgets (Buttons, Chips, Clickables) `focus:widget component=<type> module=<route> tag=<hint>`, auch direkt beim Start bei der ersten Fokusübernahme.
- FocusKit: Einzige öffentliche Oberfläche für Fokus/DPAD über alle UIs. Primitives (`tvClickable`, `tvFocusFrame`, `tvFocusableItem`, `focusGroup`, `focusBringIntoViewOnFocus`), vereinheitlichte Row‑Wrapper (`TvRowLight`/`TvRowMedia`/`TvRowPaged`) sowie DPAD‑ und Grid‑Helfer (`onDpadAdjustLeftRight/UpDown`, `focusNeighbors`). Buttons (`TvButton*`) werden re‑exportiert.
- UI Layout (Fish*): Tokens (`ui/layout/FishTheme`) steuern Größe/Abstände/Ecken/Fokus-Scale. `FishTile` normiert das Aussehen (ContentScale.Fit, Glow/Frame, Reflection), `FishRow`/`FishRowPaged` kapseln Row-Engines (DPAD/edge-left→Chrome/Paging). Inhaltliche Slots kommen aus `FishVodContent`/`FishSeriesContent`/`FishLiveContent`, geteilt über `FishMeta`/`FishActions`/`FishLogging`. `FishResumeTile` ist die generische Resume-Karte.
- FishHeader overlay: `FishHeaderHost` + `FishHeaderData` (Text/Chip/Provider) versorgen Start- und Library-Sektionen mit schwebenden Beacons; klassische Inline-Header entfallen.
- FishForms: TV-Form-Controls leben in `ui/layout/FishForm.kt` (Section/Switch/Select/Slider/TextField/ButtonRow), bauen auf FocusKit + FishTheme Tokens und ersetzen nach und nach die historischen `ui/forms` Wrapper.

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
- Settings: Der Chat-Picker bietet Multi-Select, speichert eine gemeinsame CSV
  (`tg_selected_chats_csv`) und migriert Alt-Felder (`tg_selected_vod_chats_csv`,
  `tg_selected_series_chats_csv`) einmalig. Bestätigen startet direkt einen
  kombinierten MODE_ALL-Sync; die UI löst Chat-Namen via `CMD_RESOLVE_CHAT_TITLES`
  auf und zeigt einen separaten Sync-Button.
 - ObjectBox: Telegram messages are stored in `ObxTelegramMessage` (chatId/messageId/fileId/uniqueId/supportsStreaming/caption/date/localPath/thumbFileId). Repository/DataSources update OBX directly.
- Login (Alpha): Auth-Flow liegt in `feature-tg-auth` (Domain `TgAuthState/TgAuthAction`, Data `TgAuthOrchestrator`, UI `PhoneScreen`/`CodeScreen`/`PasswordScreen`, DI `TgAuthModule`). Der Orchestrator bindet `TelegramAuthRepository`, startet automatisch Googles SMS User Consent, mapped TDLib-Fehler via `TgErrorMapper` (Flood-Wait/App-Update/Ban) und stößt `ResendAuthenticationCode` an. Der Settings-Dialog verwendet die neuen Composables; QR-Login bleibt ein expliziter Button und öffnet `tg://login` nur bei installierter Telegram-App. Ein „Per Code anmelden“-Fallback bleibt jederzeit aktiv.
- Build hygiene (2025-11-02): Telegram-Settings setzen die Flow-Debounces wieder
  mit `kotlinx.coroutines.flow` um, der Chat-Picker ist als `@Composable`
  gekennzeichnet und `TgSmsConsentManager` kapselt den `SupervisorJob`
  eigenständig, damit Kotlin 2.0 sauber kompiliert.
- TdLibReflection baut die TdlibParameters mit echten App-/Gerätewerten (VERSION_NAME, Hersteller+Modell, `Android <Release> (API <SDK>)`, Sprache aus `Configuration.locales`, `useTestDc=false`) und nutzt pro Installation einen eindeutigen Unterordner (`filesDir/tdlib/<installId>`) für Datenbank/Dateien. TDLib 406 „UPDATE_APP_TO_LOGIN“ führt nicht mehr zum automatischen QR-Trigger; der Service belässt den Nutzer im Code-Flow und meldet nur den Fehler.
- TdLibReflection `sendForResult` kapselt nun Timeouts, Retry/Backoff und optionale
  Trace-Tags pro Aufruf. Service, Datasource und Mapper übergeben sprechende
  Tags („History[…]“, „Chats:…“, „MediaMapper:…“), damit Logcat die
  jeweiligen Requests klar zuordnet und fehlerhafte Antworten automatisch
  retried oder sauber abgebrochen werden.
- Kotlin 2.0 benötigt explizite `Array<Class<*>>`-Parameterlisten für reflektierte
  TDLib-Konstruktoren; TdLibReflection setzt diese nun typisiert zusammen, damit
  Release-Builds nicht an `Comparable & Serializable`-Schnittmengen scheitern.
- Playback DataSource: `TelegramRoutingDataSource` für Media3 routet `tg://message?chatId=&messageId=` auf lokale Pfade und triggert bei Bedarf `DownloadFile(fileId)`; `localPath` wird persistiert.
- Settings: Der Chat-Picker listet Hauptordner/Archiv/Folder, erlaubt Multi-Select und zeigt aufgelöste Chat-Namen (AUTHENTICATED). „Übernehmen & Sync starten“ schreibt `tg_selected_chats_csv` und stößt direkt MODE_ALL an.
- Sync: `TelegramSyncWorker` nutzt `MODE_ALL`, ruft `CMD_PULL_CHAT_HISTORY` pro Chat mit `fetchAll=true` auf, persistiert Ergebnisse via `indexMessageContent(..)` und rebuildet anschließend `TelegramSeriesIndexer.rebuildWithStats`. Ergebnisse (Filme/Serien/Episoden) landen in `SchedulingGateway.telegramSyncState`; HomeChrome blendet Fortschritt/Resultat nicht-blockierend ein.
  - Paging: Für Serien‑Chats wird die gesamte Historie gepaged. Nach der ersten Seite werden weitere Seiten mit `fromMessageId = oldestId - 1` und `offset=0` abgeholt (Duplicate‑Guard), um Duplikate zu vermeiden und „nur letzte Seite“-Fälle auszuschließen.
- Scheduling: Nach erfolgreichem Sync ruft der Worker `SchedulingGateway.onTelegramSyncCompleted(ctx, refreshHome)` auf. Standardmäßig werden `TelegramCacheCleanupWorker.schedule(...)` und `ObxKeyBackfillWorker.scheduleOnce(...)` getriggert; optional (z. B. Settings CTA) kann `scheduleAll()` erneut ausgeführt werden, damit HomeChrome sofort aktualisiert.
- Mapping/Heuristik: SxxExx‑Parser ordnet Nachrichten Episoden (Serie) vs. Filme (VOD) zu; Serien/Filme werden als `MediaItem` mit `source="TG"` projiziert (Titel aus Caption). Thumbnails werden on‑demand via TDLib `GetFile` geladen und als `file://` angezeigt (kein Prefetch).
 - Heuristik erweitert: erkennt Bereiche (z. B. S01E01‑03, 1x02‑05, `S1:E2`), reine Episodenlabels („Episode 4“) und Sprach-Tags (`GER/ENG`, `[VOSTFR]`, `ITA`, `ES`).
 - Metadaten: via TDLib werden zusätzliche Felder persistiert (`durationSecs`, `mimeType`, `sizeBytes`, `width`/`height`, `language`). `MediaItem` übernimmt `durationSecs`/`plot` sowie `containerExt` (aus `mimeType`).
- UI: Library zeigt bei VOD Telegram-Rows pro ausgewähltem Chat (Tiles mit blauem „T“-Badge) und im Serien-Tab eine aggregierte Row „Telegram Serien“. Auf Start existiert zusätzlich eine globale Row „Telegram Serien“ (Aggregat) sowie Film-Rows je ausgewähltem Chat; die Suche rendert weiterhin eine Telegram-Row. Keine M3U-Pfadgenerierung; Play startet `tg://file/<fileId>` bzw. `rar://msg/<msg>/<entry>` über den Media3 `DelegatingDataSourceFactory` (siehe unten).
- Telegram Serien Aggregation: Nach dem Sync werden Nachrichten aus ausgewählten Serien‑Chats zu `ObxSeries` + `ObxEpisode` aggregiert (SxxEyy‑Heuristik). ProviderKey=`telegram`. Library (Tab Serien) zeigt zusätzlich eine Row „Telegram Serien“. Episoden tragen `tgChatId/tgMessageId/tgFileId`; Playback bevorzugt `tg://` in `SeriesDetailScreen`. `TelegramSeriesIndexer.rebuildWithStats` liefert neben der Gesamtanzahl auch Neu-Zählungen, die der Worker an die UI weiterreicht.
  - Build Guard: Kotlin 2.0 verlangt strikt `Int`-basierte Set-Typen für OBX IDs und einen `IndexedMessageOutcome`-Return der TDLib-Schreibkoroutine. Diese Fixes sind aktiv; keine Rückkehr zu `Set<Long>`/`Result`-Rückgaben.
 - Playback: Für `tg://` nutzt der Player reduzierte RAM‑Puffer (`LoadControl`), während der Download über TDLib (IO) fortschreitet.
- Streaming DataSource: `DelegatingDataSourceFactory` erkennt `tg://file/<id>`
  und `rar://msg/<msg>/<entry>` URIs. Für Telegram greift
  `TdlibRandomAccessSource` (Auth + `tg_enabled` vorausgesetzt):
  - TDLib `downloadFile` mit 512-KiB-Readahead, Backoff und Range-Merging über
    `updateFile`-Events; Downloads werden in ObjectBox (`localPath`, `sizeBytes`)
    gespiegelt.
  - Mehrere TDLib-Listener werden via `TdLibReflection.addUpdateListener`
    unterstützt; Player und Service hören parallel.
  Für `rar://` wird `RarEntryRandomAccessSource` genutzt (RAR5-fähiger
  Junrar-Fork). Der MP3-Eintrag wird deterministisch vorwärts dekomprimiert,
  die extrahierten Chunks landen in einem 1–4-MiB-LRU-Memory-Cache plus
  50–200-MiB-Ringbuffer im Cache-Verzeichnis.
- Build: `TG_API_ID`/`TG_API_HASH` als BuildConfig via sichere Lookup‑Kette (ohne Secrets im Repo). Reihenfolge: ENV → `/.tg.secrets.properties` (root, untracked) → `-P` Gradle‑Props → Default 0/leer. Runtime‑Fallback: Ist BuildConfig leer, liest der app‑seitige TDLib‑Client (Player‑DataSource) die Keys aus den Settings (`tg_api_id`, `tg_api_hash`). Packaging: TDLib (arm64‑v8a), ProGuard‑Keep für `org.drinkless.td.libcore.telegram.**`. Die JNI‑Lib `libtdjni.so` wird durch einen statischen Initializer in `org.drinkless.tdlib.Client` automatisch geladen.
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
- IPC via `Messenger` (minimal): Start/Params, Auth‑Kommandos (Phone/Code/Passwort/QR), Logout, Abfrage Auth‑State; Push‑Kommandos (`registerDevice`, `processPushNotification`); Lifecycle (`SetInBackground`) sowie Chat‑Kommandos (`CMD_LIST_CHATS`, `CMD_RESOLVE_CHAT_TITLES`, `CMD_PULL_CHAT_HISTORY`). Der Service ruft `loadChats` (Main/Archive/Folder) auf, pflegt einen Chat-Cache mittels `updateNewChat`/`updateChat*` (inkl. `chatPosition.order`) und beantwortet Anfragen ohne zusätzliche `getChat`-Runden (Fallback nur bei leerem Cache).
- Events: `REPLY_AUTH_STATE` bei Zustandswechseln; TDLib‑Fehler (`TdApi.Error`) werden als `REPLY_ERROR` an die UI gemeldet (Code + Message), sodass Fehl­eingaben (z. B. Telefonnummernformat, ungültige API‑Keys) nicht mehr stumm hängen.
- Foreground: Vordergrund‑Modus bei interaktivem Login und aktiven Downloads (`UpdateFile`); stoppt im Leerlauf/bei AUTHENTICATED.
- Network: beobachtet Connectivity und setzt `SetNetworkType(WiFi/Mobile/Other/None)` entsprechend.

Push (FCM)
- `FirebasePushService` (optional) registriert Token (opportunistisch) und leitet Daten‑Payload an den TDLib‑Service weiter.
- Service startet TDLib bei erstem Token/Push lazy (BuildConfig TG‑Keys) und ruft `RegisterDevice`/`ProcessPushNotification` per Reflection.
- Keine Foreground‑Nutzung für Push, minimaler RAM/CPU‑Footprint; ohne google‑services.json bleibt es no‑op.

Client‑Wrapper (`.telegram.service.TelegramServiceClient`)
- Bind/Unbind; Befehle (`start`, `requestQr`, `sendPhone`, `sendCode`, `sendPassword`, `getAuth`, `logout`, `registerFcm`, `processPush`, `setInBackground`, `listChats`, `resolveChatTitles`, `pullChatHistory`).
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
│   ├── forms/                              # TV-Form-Kit (v1): TvFormSection/Switch/Slider/TextField/Select/Button + Validation
│   ├── home/HomeChromeScaffold.kt          # Gemeinsamer Chrome (Header + Bottom) mit TV-Only Chrome-State (Visible/Collapsed/Expanded); ESC/BACK kollabiert Chrome zuerst (TV), bevor Back weitergereicht wird
│   │   - TV (Empty Start): Wenn der Start-Screen leer ist (keine Rows), wird der Chrome auf TV automatisch expandiert und der Fokus landet im Header auf dem Einstellungs-Button (oben rechts). DPAD-LEFT expandiert Chrome auch aus diesem leeren Zustand.
│   │   - TV: Header/Bottom anfangs sichtbar, auto-collapse bei Content-Scroll; Menu (Burger) toggelt Expanded (Fokus-Trap Header↔Bottom, Content geblurred). Long‑Press DPAD‑LEFT triggert dieselbe Burger‑Funktion (Expand/Collapse). Panels sliden animiert ein/aus; Content-Padding passt sich an.
│   ├── home/StartScreen.kt                 # „Gate“-Home: Suche, Carousels, Live-Favoriten
│   │   - Live-Favoriten-Picker nutzt Paging über `MediaQueryRepository.pagingSearchFilteredFlow("live", …)`; Suchtreffer entsprechen der Library-Suche.
│   │   - Reihen (Serien/Filme) laden lazy per Paging3 über ObjectBox; horizontale Rows mit Skeletons (fisch.png, Shimmer/Puls)
│   │   - Live: Favoriten-Row bleibt; ohne Favoriten globale paged Live-Row mit EPG-Prefetch
│   │   - TV initialer Fokus: Auf Start ist ausschließlich die Serien-Row initial focus‑eligible; VOD/Live unterdrücken initiale Fokus‑Anforderungen. Der Fokus landet deterministisch auf der ersten Kachel der obersten Karte (Serie).
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
   - Aktionen: Play (via `MediaActionBar`), Favorisieren (Live), Kids freigeben/sperren.
   - VOD/Series: Köpfe/Episoden per OBX; Serien on‑demand via `XtreamObxRepository.importSeriesDetailOnce`. VOD/Series laden Zusatz‑Metadaten on‑demand beim Öffnen (kein Prefetch auf Start/Home).
   - UI‑Schichtung (VOD/Series):
     - Layer 1: Vollbild‑Hero (Backdrop/Poster) über gesamte Fläche, `ContentScale.Crop`, gemeinsame Alpha `HERO_SCRIM_IMAGE_ALPHA`.
     - Layer 2–3: Lesbarkeits‑Overlays (vertikaler Scrim + radialer Glow) innerhalb Content‑Padding.
    - Layer 4: Content‑Karte mit zentralem `DetailHeader` (ohne Scrim; `showHeroScrim=false`), darunter fokusierbare Plot/Info-Karten mit Expand/Collapse; bei Serie folgen Staffeln/Episoden.
    - Zentraler Header: `ui/detail/DetailHeader` nutzt `MetaChips` (Jahr, Laufzeit, Qualität, Audio, Provider, Kategorie, Genres) und `MediaActionBar`. Header‑Extras (MPAA/Age/kompakte Audio/Video) kommen über `ui/detail/DetailHeaderExtras`. Plot- und Fakten-Karte (TV-fokussierbar) zeigen alle Xtream‑Metadaten (Bewertung, Laufzeit, Format, MPAA/Age, Provider/Kategorie, Genres/Länder, Regie/Cast, Release, Audio/Video/Bitrate, IMDb/TMDb inkl. Link).
     - Faktenblock: `ui/detail/DetailFacts` zeigt ★Rating, Container/Qualität, MPAA/Age, Provider/Kategorie, Genres/Länder, Regie/Cast, Release und IMDB/TMDB‑IDs + TMDb Link sowie Technik (Audio/Video/Bitrate).
     - Hinweis: Legacy‑Header ist entfernt/deaktiviert. FishBackground ist auf Detailseiten entfernt.
   - DPAD/BACK Verhalten (Details): DPAD‑LEFT öffnet HomeChrome nicht (Scaffold‑Schalter `enableDpadLeftChrome=false`). BACK funktioniert per `BackHandler` in VOD/Serie.

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
  - Serienposter: Bei der Serien‑Aggregation verwendet der Indexer das Chat‑Foto (größte Größe) als kanonisches Poster und legt es als erstes Bild in `ObxSeries.imagesJson` ab. Episodenposter stammen aus Video‑Thumbnails und werden nach Download via UpdateFile gesetzt.

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
- Player (Media3): `PlayerComponents.renderersFactory` liefert eine gemeinsam genutzte
  `DefaultRenderersFactory`, die Decoder‑Fallback aktiviert und die FFmpeg-
  Extension bevorzugt. Die FFmpeg-Decoder stammen aus Jellyfins vorgebautem
  `media3-ffmpeg-decoder` 1.8.0+1 AAR (GPL-3.0), daher bleiben alle Media3-Module
  bei 1.8.0 und die ABI-Splits sind auf arm64-v8a/armeabi-v7a festgelegt.
  `PlayerView` verwendet weiterhin `surface_view` (TV performanter).
  `DefaultLoadControl` bleibt konservativ.
- TV Mini‑Player: Der Mini‑Player wird global im `HomeChromeScaffold` als Overlay (unten rechts) gehostet. Auf TV aktiviert der PiP‑Button den Mini statt System‑PiP; die Menü‑Taste (MENU) fokussiert den Mini, sofern sichtbar. Bei expandiertem Chrome bleibt der Mini sichtbar, ist aber nicht fokusierbar. Der ExoPlayer wird über `PlaybackSession` geteilt, sodass der Mini beim Navigieren weiter spielt. `MiniPlayerHost` rendert das PlayerView-Preview plus Titel/Unterzeile/Progress und nutzt `MiniPlayerState` + `MiniPlayerDescriptor`; `LocalMiniPlayerResume` in `MainActivity` navigiert zurück zur Player-Route ohne das ExoPlayer-Objekt zu ersetzen.
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

## Performance

- Baseline Profiles: `assets/dexopt/baseline.prof/.profm` shipped for both ABIs.
- TV low-spec runtime profile
  - Detection: UiMode=Television or `android.software.leanback`/Fire TV features.
  - Focus visuals: reduced scale (~1.03) and no elevation shadow to reduce GPU cost.
  - Paging: smaller windows on TV (`pageSize=16`, `prefetchDistance=1`, `initialLoad=32`).
  - HTTP: OkHttp dispatcher throttled on TV (`maxRequests=16`, `maxRequestsPerHost=4`).
  - Coil: crossfades disabled on TV; RGB_565 + measured sizing retained.
- Playback-aware background work
  - While the internal player is active, Xtream workers are paused (`m3u_workers_enabled=false`) and in-flight unique works canceled; the flag is restored on exit if previously enabled.
