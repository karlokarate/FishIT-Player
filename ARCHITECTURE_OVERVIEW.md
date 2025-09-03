# FishIT Player – Architecture Overview (derived from AGENTS_NEW.md)

Deep‑Dive Update: 2025‑09‑03
- Inventory, dependencies and build notes refreshed per current repo. Non‑breaking optimization guidance added.

Dieses Dokument bietet den vollständigen, detaillierten Überblick über Module, Flows und Verantwortlichkeiten der App. Es ist aus `AGENTS_NEW.md` abgeleitet und wird hier als zentrale Architektur‑Referenz gepflegt.

> Technologie-Stack
>
> - Android (Kotlin), Jetpack Compose, Navigation‑Compose  
> - Room (SQLite), DataStore Preferences, WorkManager  
> - OkHttp (HTTP), Coil 3 (Bilder), Media3/ExoPlayer (Video)  
> - Struktur: Single‑Module‑Projekt `app`

---

## 1) Build, Run & Tests

- JDK 17, Build via Gradle Wrapper
  - `./gradlew clean build`
  - (Optional) Lint/Static‑Checks, falls konfiguriert: `./gradlew lint detekt ktlintFormat`
- App-Entry: `com.chris.m3usuite.MainActivity`
- Min. Laufzeit-Voraussetzung: Netzwerkzugriff (für M3U/Xtream, Bilder).

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
│   ├── m3u/M3UParser.kt                    # Parser für M3U → MediaItem (Type-Heuristik)
│   └── xtream/                             # Xtream Codes REST-Client & Models
│       ├── XtreamClient.kt
│       ├── XtreamConfig.kt                 # Konfiguration + Ableitung aus M3U-URL
│       └── XtreamModels.kt
├── data/
│   ├── db/                                 # Room: Entities, DAOs, Database
│   │   ├── AppDatabase.kt                  # Versionierung, Migrations, Seeding (Adult Profile)
│   │   └── Entities.kt                     # MediaItem, Episode, Category, Profile, KidContent,
│   │                                       # ResumeMark, ScreenTimeEntry, Views + DAOs
│   └── repo/                               # Repositories (IO/Business-Logik)
│       ├── PlaylistRepository.kt           # Download & Import M3U → MediaItem (live/vod/series)
│       ├── XtreamRepository.kt             # Vollimport via Xtream (Kategorien, Streams, Episoden, Details)
│       ├── EpgRepository.kt                # Now/Next: persistenter Room‑Cache (epg_now_next) + XMLTV‑Fallback
│       ├── MediaQueryRepository.kt         # Gefilterte Queries (Kids-Whitelist), Suche
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
│   ├── home/StartScreen.kt                 # „Gate“-Home: Suche, Carousels, Live-Favoriten
│   ├── screens/                            # Setup/Library/Details/Settings
│   │   ├── PlaylistSetupScreen.kt          # Erststart: M3U/EPG/UA/Referer speichern & Import auslösen
│   │   ├── LibraryScreen.kt                # Durchsuchen (Filter, Raster/Listen)
│   │   ├── LiveDetailScreen.kt             # Live-Details, Play/Favorit/Kids-Freigabe
│   │   ├── VodDetailScreen.kt              # VOD-Details, Enrichment-Fetch, Resume
│   │   ├── SeriesDetailScreen.kt           # Serien, Staffel/Episoden-Listing, Resume Next
│   │   └── SettingsScreen.kt               # UI-, Player-, Filter-, Profile-Settings
│   ├── skin/                               # TV-Skin (Focus/Scale, Modifiers), Theme
│   ├── state/ScrollStateRegistry.kt        # Scroll-Positions pro Row/Grid
│   ├── theme/                              # Farben, Typo, Theme
│   └── util/Images.kt, AvatarModel.kt      # Coil-ImageRequests mit Headers aus Settings
└── work/
    ├── Workers.kt                          # XtreamRefreshWorker, XtreamEnrichmentWorker
    ├── EpgRefreshWorker.kt                 # Periodisches Now/Next‑Refresh (15m) + Stale‑Cleanup
    ├── SchedulingGateway.kt                # Zentrales Unique-Work Scheduling (KEEP/REPLACE)
    ├── ScreenTimeResetWorker.kt            # täglicher Reset der ScreenTime
    └── BootCompletedReceiver.kt            # Re-Scheduling nach Boot
```

---

## 3) Screens & Navigations‑Flow

Routen (aus `MainActivity`):  
`setup` → `gate` → (`library`/`browse`) → `live/{id}` | `vod/{id}` | `series/{id}` → `settings` → `profiles`

1. **setup = `PlaylistSetupScreen`**
   - Eingaben: M3U‑URL, optional EPG‑URL, User‑Agent, Referer.
   - Speichert in `SettingsStore`, triggert M3U‑Import (*PlaylistRepository.refreshFromM3U*) oder Xtream‑Konfiguration+Import (*XtreamRepository*).
   - Nach Erfolg: Scheduling `XtreamRefreshWorker` + `XtreamEnrichmentWorker`, Navigation zu `gate`.
   - Unterstützt „VIEW“-Deep‑Links (URL vorbefüllt).

2. **gate = Start/Home (`StartScreen`)**
   - Suche, Resume‑Carousels (VOD/Episoden), Serien/Filme/TV‑Rows.
   - Live‑Tiles zeigen Now/Next + Fortschrittsbalken (EPG‑Cache).
   - Live‑Favoriten (persistiert via `FAV_LIVE_IDS_CSV`).
   - Kid‑Profile berücksichtigen (Queries über `MediaQueryRepository` filtern per Whitelist).
   - Optional: „Quick Import“ (Drive/Datei), sofern im Build/UI aktiviert.

3. **library/browse = `LibraryScreen`**
   - Browsing mit Suchfeld, Grid‑Ansicht, Filter/Sortier‑Heuristiken.

4. **Details: `live/{id}`, `vod/{id}`, `series/{id}`**
   - Aktionen: Play (via `PlayerChooser`), Favorisieren, Kids freigeben/sperren.
   - VOD lädt ggf. Details/Poster/Backdrop (Enrichment) über `XtreamRepository.enrichVodDetailsOnce`.

5. **player = `InternalPlayerScreen`**
   - Media3/ExoPlayer, Resume‑Handling (`ResumeRepository`), Untertitel‑Stil, Rotation‑Lock Option.

6. **settings = `SettingsScreen`**
   - Player-Modus (ask/internal/external + bevorzugtes externes Paket), Subtitle‑Stil, Header‑Verhalten, Autoplay Next, TV‑Filter.
   - Daten & Backup: Export/Import (Datei/Drive), Drive‑Einstellungen.

7. **profiles = `ProfileGate` & `ProfileManagerScreen`**
   - Adult-PIN, „Remember last profile“, Anlage Kind‑Profile inkl. Avatar; Kids‑Whitelist‑Listen.

---

## 4) Datenmodell (Room)

Zentrale Entities (Auszug aus `Entities.kt`)
- MediaItem (type: `live` | `vod` | `series`) – Felder u. a.: `id`, `type`, `streamId?`, `name`, `sortTitle`, `categoryId`, `categoryName`, `poster`, `backdrop`, `plot`, `url`, `extraJson`, `durationSecs?`, `rating?`.
- Episode (für Serien): `seriesStreamId`, `episodeId`, `seasonNumber`, `episodeNumber`, `name`, `airDate`, `poster`, `plot`, `url` …
- Category (kind: live/vod/series).
- Profile: `type` (`adult`|`kid`), `name`, `avatarId` …
- KidContentItem: Whitelist‑Tabelle (Kind weist Content `type+id` frei).
- KidCategoryAllow: Kategorien‑Freigaben pro Profil (Whitelist auf Kategorie‑Ebene).
- KidContentBlock: Item‑Ausnahmen (Block auf Item‑Ebene trotz Kategorien‑Freigabe).
- ProfilePermissions: Rechtematrix pro Profil (Settings, Quellen, Externer Player, Favoriten, Suche, Resume, Whitelist).
- ResumeMark: VOD/Episode Fortschritt (ms/sek, Zeitstempel).
- ScreenTimeEntry: Limit und Verbrauch pro Tag+Kind.
- EpgNowNext (Tabelle `epg_now_next`): Persistenter Now/Next‑Cache pro Kanal (`tvg-id`), inkl. Titel/Zeiten und `updatedAt`.

Wichtige DAOs & Constraints
- Indizes auf `type+streamId`, `categoryId` etc. für schnelle Queries.
- `mediaDao.clearType(type)` + `upsertAll()` werden beim Import genutzt.
- Views: `ResumeVodView`, `ResumeEpisodeView` für Carousels.

---

## 5) Repositories (Domänenlogik)

- PlaylistRepository
  - `refreshFromM3U()` lädt M3U über OkHttp, parst via `M3UParser`, ersetzt DB‑Inhalte der Typen live/vod/series.
  - `extraJson` enthält `addedAt` (Zeitstempel bleibt bei Re‑Import erhalten; Matching per URL).

- XtreamRepository
  - `configureFromM3uUrl()` leitet Host/Port/User/Pass/Output aus der M3U ab und speichert sie in `SettingsStore` (inkl. EPG‑Fallback).
  - `importAll()`: Vollabgleich Kategorien, Live, VOD, Serien, Episoden; `addedAt` konserviert (Matching per `streamId`).  
  - `enrichVodDetailsOnce(mediaId)` lädt Poster/Backdrop/Plot/Rating/Duration nach und aktualisiert `MediaItem`.
  - `loadSeriesInfo(seriesStreamId)` lädt Episoden‑Listen und ersetzt sie in der DB.

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
- DriveClient (optional)
  - Upload/Download der Backup‑Datei; Konto/Ordner‑Verwaltung für Drive‑Ziel.

---

## 6) Core/HTTP & Media

- HttpClientFactory: OkHttp‑Client, setzt User‑Agent, Referer und optionale Extra‑Header aus DataStore (einheitlich für Streams & Bilder).
- Live‑Preview (HomeRows): nutzt ExoPlayer mit `DefaultHttpDataSource.Factory` und setzt Default‑Request‑Properties (User‑Agent/Referer) für Provider, die Header erzwingen. Status: zu testen durch Nutzer.
- Images.kt: Liefert Coil ImageRequest mit denselben Headern.
- M3UParser: Attribute‑Parser, `inferType()` (Heuristik: Group‑Title/Dateiendung).
- EpgRepository: Liefert Now/Next mit persistentem Room‑Cache (`epg_now_next`), XMLTV‑Fallback bei leeren/fehlenden Xtream‑Antworten und kurzer In‑Memory‑TTL. Integrationen in Live‑Tiles und Live‑Detail.
  - EPG‑Refresh läuft periodisch via `EpgRefreshWorker` (Standard 15 Minuten) mit Bereinigung veralteter Einträge (>24h).
- XtreamDetect: Ableitung von Xtream‑Creds aus `get.php`/Stream‑URLs; `parseStreamId` (unterstützt `<id>.<ext>`, Query `stream_id|stream`, HLS `.../<id>/index.m3u8`). Status: zu testen durch Nutzer.

---

## 7) Hintergrund‑Jobs (WorkManager)

- XtreamRefreshWorker: Regelmäßiger Sync (Xtream.importAll oder Playlist.refreshFromM3U – je nach Konfiguration).
- XtreamEnrichmentWorker: Lädt VOD‑Details nach (Poster, Plot, Dauer, Rating).
  (über `SchedulingGateway` mit Unique‑Work geplant)
- EpgRefreshWorker: Aktualisiert periodisch den Now/Next‑Cache; bereinigt veraltete Cache‑Einträge (>24h).
- ScreenTimeResetWorker: Setzt täglich den Verbrauch der Kinder‑Profile zurück.
- BootCompletedReceiver: Plant Worker nach Reboot neu.

> Scheduling wird in `MainActivity` aktiviert, sobald `m3uUrl` gesetzt ist.

---

## 8) Settings (DataStore)

Wichtige Keys (Auszug):
- Quellen: `M3U_URL`, `EPG_URL`, `USER_AGENT`, `REFERER`, `EXTRA_HEADERS`
- Player: `PLAYER_MODE` (ask|internal|external), `PREF_PLAYER_PACKAGE`
- Xtream: `XT_HOST`, `XT_PORT`, `XT_USER`, `XT_PASS`, `XT_OUTPUT`
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
- `ui/screens/SettingsScreen.kt` → Einstellungen; zu ergänzen um Settings‑Export, Settings‑Import, Drive‑Einstellungen.
- `ui/screens/*DetailScreen.kt` → Anzeigen + Aktionen (Play/Favorit/Whitelist).  
- `player/PlayerChooser.kt` → zentrale Startlogik für Wiedergabe (Ask/Internal/External).  
- `player/InternalPlayerScreen.kt` → interner Player inkl. Resume/Subs/Rotation.  
- `core/xtream/*` → REST‑Client und Modelle für Xtream, plus Konfig‑Ableitung aus M3U.  
- `data/repo/*` → sämtliche IO/Business‑Operationen (Import, Query, Profile, Resume, ScreenTime).  
- `work/*` → periodische Tasks.  
- `prefs/SettingsStore.kt` → sämtliche Preferences und Typed Getter/Setter.  
- `ui/util/Images.kt` → einheitliche Header für Coil/Streams.
 - `backup/*` → Backup/Restore Flow (UI + Manager + Formate).  
 - `drive/DriveClient.kt` → Drive‑Integration (optional).  
 - `work/SchedulingGateway.kt` → Einheitliche Unique‑Work Steuerung.

---

## 10) Bekannte Punkte & potentielle Probleme

- Drive‑Integration fehlt vollständig (kein Auth/Drive‑Client im Projekt).  
- Schnell‑Import liegt aktuell auf dem Home‑Screen; soll in `setup` (Onboarding).  
- Import/Export für Settings existiert nicht – UI‑Hooks in `SettingsScreen` fehlen.  
- Heuristik `M3UParser.inferType` kann bei exotischen Gruppen/Titeln fehlklassifizieren.  
- Kids‑Filter: `MediaQueryRepository` berechnet erlaubt‑Sets in Memory; bei großen Katalogen ggf. optimieren (Join im DAO).  
- Worker starten abhängig von `m3uUrl`; bei Import‑Fehlern ist kein automatischer Retry geplant (nur WorkManager‑Retry).  
- Headers: UA/Referer müssen für Streams und Bilder konsistent gesetzt werden (bereits implementiert; bei Anpassungen beachten).

---

## 11) Wo implementiere ich was? (Cheat‑Sheet)

- Onboarding (Schnell‑Import verlegen): `ui/screens/PlaylistSetupScreen.kt`, `MainActivity.kt`.  
- Settings‑Export/Import: neue Util `settings/SettingsBackup.kt` + UI in `SettingsScreen.kt`; optional Worker `SettingsAutoBackupWorker` in `work/`.  
- Drive‑Login & Upload/Download: neues Paket `drive/` (Auth, REST v3), Manifest‑Scopes & Gradle‑Deps.  
- Manueller Dateiexport lokal: SAF‑Intents in `SettingsScreen.kt` (ACTION_CREATE_DOCUMENT/ACTION_OPEN_DOCUMENT).  
- Nach Import: `XtreamRefreshWorker.schedule(...)` + `XtreamEnrichmentWorker.schedule(...)` triggern.

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
- Import: `PlaylistRepository` nutzt `core/m3u` (Parser/Exporter), `core/xtream/XtreamDetect`, `HttpClientFactory`, DB, `SchedulingGateway`.
- Xtream: `XtreamRepository` nutzt `core/xtream` (Client/Config/Models), DB und `SettingsStore`.
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
