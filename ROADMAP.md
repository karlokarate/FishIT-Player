#
# FishIT Player – **Roadmap: Import/Export finalisieren**

Zielbild:
- **Schnell‑Import** ausschließlich im **Erststart‑Screen (`setup`)**.
- **Settings‑Export/Import** in **Settings** (manuell + optional Auto‑Backup).
- **Google‑Drive** (optional): Login + Zielordner wählen + Upload/Download.
- Nach **Import**: automatischer **Refresh** (M3U/Xtream) und Worker‑Scheduling.
  - Update 2025‑09‑11: Xtream‑Worker abgeschaltet (no‑op). Xtream wird on‑demand verwendet (Lazy Loading), M3U nur Fallback.

Hinweis (Build & WSL)
- Für konsistente Linux‑Builds unter WSL siehe `AGENTS.md` → Abschnitt „WSL/Linux Build & Test“ (Repo‑lokale Ordner: `.wsl-android-sdk`, `.wsl-gradle`, `.wsl-java-17`, empfohlene Env‑Variablen).

---

## Kurzfristige Roadmap (konkret, verifiziert)

- Lazy Loading global aktiv (OBX‑Prefetch sichtbar/Favoriten). Periodische Worker entfernt/no‑op (Xtream/EPG). Telegram‑Cleanup/Sync bleiben.
- OBX‑Only (M3U/Xtream/EPG/Telegram): alle Pfade nutzen ObjectBox; Room entfernt.

To‑Dos (umsetzbar):
- [ ] UI‑Details anreichern: VOD/Serien‑Detailscreens um zusätzliche Felder erweitern (director/cast/genre/trailer/images; bei Serien optional Backdrop/Poster Fallbacks).
- [ ] Import‑Feedback: Dezente Fortschrittsanzeige/Toast beim Delta‑Import (on‑demand und Settings‑Aktionen).
  - Note: Settings now correctly detects a running on-demand Xtream delta job (name alignment); follow-up: optional progress surface/snackbar from WorkManager state.
- [ ] EPG: Optional Stale‑Cleanup (OBX) und kleine Diagnoseansicht (aktuelle Now/Next je Sender).
- [ ] Doku‑Hygiene: AGENTS/ARCHITECTURE/CHANGELOG konsistent halten (AGENTS als Single Source).
  - Update: HTTP‑Logs Export in Settings umgesetzt (ZIP + Share Sheet). AGENTS/ARCH kept unchanged (keine Architektur‑Auswirkung).

Erledigt (Discovery/Delta‑Import – 2025‑09‑15)
- [x] Xtream Discovery: Strenger Port‑Resolver – nur 2xx + parsebares JSON zählen als Treffer. Cloudflare 5xx/521 werden verworfen; Kandidatenlauf (z. B. 8080) findet funktionsfähigen Port. Fallback auf 80/443 bleibt letzte Option.
- [x] Port‑Probes senden jetzt explizite Actions (`get_live_streams|get_series|get_vod_streams`) inkl. `category_id=*`, um WAF‑Regeln zu erfüllen. HTTP‑Kandidaten priorisieren `8080`; `2095` entfernt.
- [x] Settings „Import aktualisieren“: Inline‑Delta‑Import wieder aktiviert (kein frühzeitiges `return@launch`), damit Port‑Fallback/Feedback sofort greifen; One‑Time‑Worker läuft parallel.

Erledigt (Stabilität HTTP/Xtream – 2025‑09‑08)
- [x] OkHttp als Singleton mit zusammenführendem Cookie‑Jar (Session/CF‑Cookies bleiben erhalten; Keep‑Alive aktiv).
- [x] Interceptor nutzt nicht‑blockierende Header‑Snapshots (RequestHeadersProvider).
- [x] Xtream‑Enrichment gedrosselt (Batch 75, kurze Delays, Retry bei 429/513).
- [x] Auto‑Scheduling von Enrichment aus `scheduleAll` entfernt (manuell/gezielt triggern).
  - Update 2025‑09‑11: Enrichment‑ und Refresh‑Worker deaktiviert; Xtream wird on‑demand genutzt.

---

Wiederaufnahmepunkt (2025‑09‑11) – ObjectBox + Xtream-first UI

Status (Erledigt)
- core/xtream ist Single Source of Truth (Detect/Config/Capabilities/Client/Models).
- ObjectBox integriert als Primärstore: `ObxCategory`, `ObxLive/Vod/Series(nameLower)`, `ObxEpisode`, `ObxEpgNowNext`, `ObxStore`.
- Import (`XtreamObxRepository.importAllFull`): Full Content inkl. robuster `categoryId` und Details (trailer, images, imdb/tmdb, episodes mit playExt).
- EPG: `EpgRepository` persistiert Now/Next in Room + ObjectBox; `prefetchEpgForVisible` schreibt Short‑EPG direkt in ObjectBox.
- Start/Library/SeriesDetail: ObjectBox‑first (Listen, Kategorien, Episoden). Category‑Sheet zieht aus `ObxCategory` (mit Counts + Suche). Suche voll OBX‑gestützt (nameLower + Kategorienamen).
- LiveDetail: Event‑basierte Updates via ObjectBox‑Subscription (kein Polling).

Nächste Schritte (To‑Do)
- Optional: OBX‑Search um `sortTitleLower` erweitern (zusätzlicher Index) für konsistentere Treffer bei normalisierten Titeln.
- [x] Provider/Genre/Year direkt in OBX‑Queries abbilden (normalisierte Felder `providerKey`/`genreKey`/`yearKey` + Indizes).
- [x] Content‑Flows (Start/Library/Details/Suche) ausschließlich über ObjectBox.
- [x] Schritt 2 (Teil A): Profile/Permissions/Kids/Resume/Telegram auf OBX-Repos umgestellt; ProfileGate, Start/Details/Player angepasst. Nächster Schritt: ProfileManagerScreen, restliche UI und komplettes Entfernen der Room‑DAOs.
- UI: Schrittweise Migration verbleibender Room‑Pfade (z. B. globale Paging‑Sichten) auf ObjectBox; Room nur für Profile/Permissions/Resume/FTS belassen.
- Cleanup: Alte Xtream‑Altpfade, ungenutzte Worker‑Trigger endgültig entfernen, sobald alle Screens umgestellt sind.
- [x] Lazy Loading: Live/VOD/Series gruppiert über OBX‑Keys; sichtbare Rows laden paged. Header listen per Distinct‑Keys.
- [x] Start/Home: Series/VOD paged; globale Live‑Row bei fehlenden Favoriten.
- [x] Delta‑Import (OBX Upsert + Orphan‑Cleanup) inkl. Worker (periodisch + on-demand); One‑shot Key‑Backfill Worker.



## Telegram Integration (TDLib)

Status: Opt‑in Alpha live. Login/Picker/Sync/DataSource/Packaging umgesetzt; v7a‑Buildscript vorhanden.

- Erledigt
  - [x] Settings: Global toggle `tg_enabled`, Login (Telefon→Code→Passwort), auto DB‑Key‑Check, Status‑Debug.
  - [x] Chat/Folder Picker: getrennte Quellen für Film/Serien Sync (CSV in Settings).
  - [x] DB: Telegram index via `ObxTelegramMessage`; DataSources/Service updaten `localPath` direkt in OBX.
  - [x] Worker: `TelegramSyncWorker` (Heuristik SxxExx, Dedupe, Mapping auf VOD/Series).
  - [x] Player: `TelegramTdlibDataSource` (Streaming) + `TelegramRoutingDataSource` (lokal) + tg:// URIs in UI/Autoplay.
  - [x] Packaging: `:libtd` mit `arm64-v8a/libtdjni.so`. v7a entfällt.
  - [x] v7a Fix: BoringSSL statisch in `libtdjni.so` gelinkt, um 32‑bit OpenSSL‑Dyn‑Deps zu vermeiden.

- Nächste Schritte
  - [x] Heuristik ausbauen (deutsche Schreibweisen, Episoden‑Titel), Container/Ext ableiten, Laufzeit. (v1: S/E de‑Regex, ext/duration best‑effort)
  - [x] Fortschritt/Status UI für Sync (Sheets/Toasts), Retry‑Strategie, Throttling. (v1: Settings zeigt laufenden Sync + Zähler)
  - [x] Cache‑Policy (GB/TTL) durchsetzen (Cleanup Worker), Limits pro Quelle. (v1: GB‑Limit, täglicher Trim)
  - [x] Thumbs/Chat‑Photos in Poster‑Pipeline (Coil) integrieren. (v1: Nachricht‑Thumb als Poster, Chat‑Photo für Serien)
  - [ ] Optional: CI‑Jobs zum Bauen der TDLib‑.so für arm64.

### Phase 2 — Next‑gen TDLib Integration (State of the Art)
- Auth & Security
  - [x] QR‑Login unterstützen (`requestQrCodeAuthentication`) zusätzlich zu Telefon/Code/Passwort.
  - [x] 2FA: `AuthorizationStateWaitPassword` (Dialog + Service; senden/prüfen Passwort).
  - [ ] E‑Mail‑Flows: `AuthorizationStateWaitEmailAddress`, `AuthorizationStateWaitEmailCode` (später; UI/Service‑Kommandos vorbereiten).
  - [x] DB‑Verschlüsselung: 256‑bit `databaseEncryptionKey` einmalig generieren, im Android Keystore lagern (AES‑GCM), beim Start `checkDatabaseEncryptionKey`.
  - [x] `TdlibParameters` voll befüllen (use*Database, systemLanguageCode, deviceModel, systemVersion, applicationVersion, files/database dirs) und genau einmal setzen.
- Prozess‑/Lebenszyklus
  - [x] TDLib in dediziertem Service in separatem Prozess (`android:process=":tdlib"`) betreiben; Foreground nur bei aktiven Downloads/Login.
  - [x] `setInBackground(true/false)` an App‑Lifecycle koppeln; `setNetworkType` bei Konnektivitätswechsel aktualisieren.
- Push‑Sync (FCM)
  - [x] FCM integrieren: `registerDevice` mit Token; in Service `processPushNotification` weiterreichen. Optional ohne google‑services.json lauffähig (No‑op wenn nicht konfiguriert).
  - [x] Polling reduzieren: Updates ereignisgetrieben (Basis): Service wertet TDLib‑Updates aus und indiziert Minimal‑Metadaten. WorkManager bleibt Backfill/Fallback.
- Files/Streaming & Index
  - [x] Updates‑First: `UpdateNewMessage`/`UpdateMessageContent` für Index (Minimal‑Metadaten in Room). Folgearbeiten: vollständige Medientyp‑Erkennung/Mapping.
  - [ ] Backfill zielgerichtet: `searchChatMessages`/Filter statt Vollverlauf.
  - [x] Downloads priorisieren: `downloadFile(..., priority, offset, limit, synchronous=false)` + `UpdateFile` auswerten (Pfad/Progress) → Foreground steuern; (DB `localPath` bislang via DataSources, Service‑Pfad folgt).
  - [x] Playback weiter über progressive TDLib‑Downloads (Seek) mit Fallback Routing/HTTP.
- Storage & Cleanup
  - [ ] `getStorageStatistics` nutzen für intelligente Bereinigung (LRU/kaum genutzt) statt reiner GB‑Grenze; Worker bleibt als Guardrail.
- API‑Schicht & Logging
  - [x] Eine Client‑Instanz pro Session im Service; Repository/Client‑Wrapper für IPC.
  - [ ] Logging kontrollieren: `setLogStream` (optional Datei), `setVerbosityLevel(1)` in Prod; Debug gezielt aktivierbar.
- Versionierung & Build‑Hygiene
  - [ ] JNI/Java immer aus demselben TDLib Tag/Commit bauen; `TdApi.java` regenerieren.
  - [x] Start‑Assertion: einfache Binding‑Prüfung (`verifyBindings`) → Logwarnung bei möglichem Mismatch.
- Größenoptimierung (arm64)
  - [ ] `tdlib-build-arm64.sh`: LTO (`-DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON`), `-ffunction-sections -fdata-sections`, Linker `--gc-sections --icf=safe`, `-fvisibility=hidden`, `llvm-strip --strip-unneeded`.

## 0) Architekturentscheidungen

- **Export‑Format**: JSON (`m3usuite-settings-v1.json`)  
  - enthält alle **DataStore‑Keys** (siehe ARCHITECTURE_OVERVIEW.md §8).  
  - optionaler Block `"profiles"` (Profile + Kids‑Whitelist) → aktivierbar per Checkbox.
- **Sicherheit**: Sensible Felder (`XT_PASS`, ggf. Adult‑PIN‑Hash) werden **AES‑verschlüsselt** (Schlüssel via Android Keystore) oder – Minimalversion – Klartext mit deutlichem Hinweis.
- **Speicherorte**:
  - **Lokal** via **SAF** (ACTION_CREATE_DOCUMENT / OPEN_DOCUMENT).
  - **Drive (optional)** via **REST v3** + Google Sign-In (`https://www.googleapis.com/auth/drive.file`).

---

## 1) UI-Änderungen

### 1.1 Erststart („Setup“)
- In `PlaylistSetupScreen.kt` folgenden Block ergänzen (oberhalb der Formularfelder):
  - **„Schnell‑Import von Drive“**: Button „Bei Google anmelden“ → Drive‑Login; Button „Von Drive importieren“ → öffnet Dateipicker (Drive).
  - Hinweis-Text für unterstütztes Format (`m3usuite-settings-v1.json`).
- Beim erfolgreichen Import:
  - Settings in DataStore schreiben → `XtreamRepository.configureFromM3uUrl()` (falls M3U gesetzt).  
  - `XtreamRefreshWorker.schedule(...)` + `XtreamEnrichmentWorker.schedule(...)`.  
    - Update 2025‑09‑11: Beide no‑op; EPG‑PeriodicWorker wurde inzwischen entfernt (Lazy Prefetch übernimmt).
  - Navigation zu `gate`.

### 1.2 Home (`StartScreen.kt`)
– (Erledigt) Schnell‑Import‑Karte entfernt.

### 1.3 Settings (`SettingsScreen.kt`)
Abschnitt **„Daten & Backup“** – offen:
- **Drive‑Einstellungen**  
  - Status (angemeldet/abgemeldet, Konto), „Bei Google anmelden/abmelden“, Zielordner wählen.  
  - Buttons: „Zu Drive exportieren“, „Aus Drive importieren“.
  - Toggle: „Automatisches Backup nach Änderungen“ (optional Phase 2).
Hinweis: Lokaler Export/Import (SAF) ist implementiert.

---

## 2) Implementierungsschritte (Schritt‑für‑Schritt)

### 2.1 Settings‑Serialisierung
(Erledigt) Über `prefs/SettingsSnapshot.kt` + `backup/SettingsBackupManager.kt` (Dump/Restore aller Keys, Profile, Resume‑Marks).

### 2.2 Dateiexport/-import (lokal, SAF)
(Erledigt) `backup/BackupRestoreSection.kt` (Export/Import via SAF) + Integration in `SettingsScreen`.

### 2.3 Drive‑Integration (optional Phase 2)
- Gradle:
  - `implementation("com.google.android.gms:play-services-auth:21.x")`
  - `implementation("com.google.api-client:google-api-client-android:2.x")`
  - `implementation("com.google.apis:google-api-services-drive:v3-rev###-1.###.0")`
- Paket `drive/`:
  - `DriveAuth.kt` – Google Sign-In + Token Refresh
  - `DriveClient.kt` – Upload/Download `m3usuite-settings-v1.json` im app‑eigenen Ordner (`drive.file`‑Scope)
- UI‑Status in `SettingsScreen`/`PlaylistSetupScreen` anzeigen und Aktionen anbinden.

### 2.4 Onboarding‑Anpassungen
- `PlaylistSetupScreen`:
  - (Teilweise) „Schnell‑Import“ via Drive vorhanden (`QuickImportRow`). Lokaler Import‑Button noch offen.
  - Nach erfolgreichem Schnell‑Import: Import anstoßen (Xtream/M3U) → Worker planen → Navigieren zu `gate` (offen).
  - `MainActivity` bleibt unverändert (Startziel `setup` wenn `m3uUrl` leer).

### 2.5 Post‑Import Aktionen
- Offen für „Schnell‑Import“ (Setup/Settings): Nach Restore Import anstoßen (Xtream/M3U) + Worker planen + Erfolgs‑Feedback.

### 2.6 Auto‑Backup (optional Phase 3)
- Worker `SettingsAutoBackupWorker` (Unique, Network CONNECTED):
  - Trigger: beim Wechsel relevanter Keys (DataStore `data` Flow → Debounce → Enqueue) oder täglich.
  - Ziel: Drive (falls angemeldet) **und** optional lokaler Export‑Ordner.

### 2.7 Tests
- Unit‑Tests `SettingsBackupTest` (Round‑Trip: export→import→Vergleich).
- Repo‑Smoke‑Tests (Playlist/Xtream mit Fake‑HTTP).
- UI‑Tests: Onboarding‑Pfad, Settings‑Buttons sichtbar/funktional.

---

## 3) Akzeptanzkriterien

- **Setup**: „Schnell‑Import“ vorhanden; Import einer gültigen `m3usuite-settings-v1.json` setzt **alle** relevanten Settings.
- **Home**: keine Schnell‑Import‑Karte mehr.
- **Settings**: Abschnitte *Export*, *Import*, *Drive‑Einstellungen* vorhanden und funktional.
- **Nach Import**: Inhalte werden aktualisiert (Sync gestartet), Worker geplant.
- **Optional**: Drive‑Login, Upload/Download funktionieren zuverlässig.

---

## 4) Migration & Fallbacks

- Wenn keine Drive‑Anmeldung → **nur** Datei‑Export/-Import anbieten.
- Bei unvollständigem Snapshot (z. B. fehlende `EPG_URL`): keine Fehlermeldung, nur setzen was vorhanden ist.
- Versionierung über `v`‑Feld (künftige Felder rückwärtskompatibel).

---

## 5) Risiken & Gegenmaßnahmen

- **Drive REST** ist komplex → Minimal‑Viable zuerst mit **SAF** (Datei) fertigstellen.
- **Sensible Daten** (Xtream‑Passwort) → Verschlüsselung + Hinweistext.
- **Große Kataloge** → Kids‑Filter performant halten (möglichst DAO‑seitig selektieren, nicht nur in Memory).
- **User‑Agent/Referer** müssen überall identisch sein (HttpClient & Coil – bereits zentralisiert).

---

## Aufgabenliste (Checkliste – offen)

- [ ] UI‑Details anreichern (neue Metadaten aus Repos einbinden).
- [x] Room vollständig entfernen (OBX‑Only). M3U/Xtream/EPG/Telegram/Start/Library/Details/Worker nutzen OBX. Room‑Dateien/Deps entfernt; UI/Repos angepasst.
- [ ] SchedulingGateway: tote Pfade/Refs entfernen, Doku angleichen.
- [ ] Drive‑Integration (optional Phase 2): Auth/Upload/Download für Settings‑Backup.
- [ ] Unit‑ & UI‑Tests aktualisieren (OBX‑Pfade, Lazy Prefetch, Details‑Rendering).

Erledigt (Search v1.5 – FTS)
- [x] Room‑FTS4 über `name/sortTitle` mit `unicode61` (`remove_diacritics=2`).
- [x] DAO + Repository auf FTS umgestellt (Prefix‑Suche via `*`, AND über Tokens).
- [x] Migration v6→v7: FTS‑Tabelle, Trigger, Backfill.
- [x] Debounce der Tipp‑Suche (300 ms) mit Abbruch älterer Läufe (`collectLatest`).

Erledigt (Paging 3 – große Listen)
- [x] DAO‑Paging‑Queries (`PagingSource` für Typ/Kategorie und FTS‑Suche).
- [x] Repository: `Flow<PagingData<MediaItem>>` + Kids‑Filter auf Paging‑Ebene.
- [x] UI: Paged Grid in `LibraryScreen` mit stabilen Keys.

--- 

Erledigt (Profiles/Permissions) – 2025‑09‑02
- Gast‑Profil (konservative Defaults).
- Pro‑Profil Rechte inkl. Enforcements (Settings, Externer Player, Favoriten, Weiter‑schauen, Whitelist).
- Kid‑Mode Refresh fix (Start/Home → gefilterte Queries).
- Favoriten read‑only für eingeschränkte Profile.
- Whitelist v1: Kategorien erlauben + Item‑Ausnahmen; Admin‑Sheet (Badges, expandierbare Items mit Kästchen).

Nächste Schritte (Kurzfristig)
- Rechte‑Defaults je Rolle: Admin‑Templates für Kid/Gast, anwenden beim Neuanlegen.
- Resume v2: Alle Resume‑Oberflächen strikt nach effektiver Freigabe filtern.
- Suche‑Gating: Suchfeld/Interaktionen verbergen, falls `canSearch=false`.
- Whitelist‑Sheet Performance: Paginierung/Batch‑Loading bei sehr großen Kategorien.
- Migration‑QA: Upgrade v4/v5→v6 mit realen Daten testen; Idempotenz sicherstellen.

Akzeptanz (Ergänzungen)
- Wechsel auf Kid/Gast zeigt niemals ungefilterte Rows, auch nicht nach manuellem Refresh.
- Favoriten‑Zeile ohne Hinzufügen/Sortieren/Entfernen, wenn `canEditFavorites=false`.
- Routen: „Einstellungen“ nur bei `canOpenSettings`; „Profiles“ nur für Adult‑Profil.


## Status‑Notizen (ungeprüft)

- EPG/Now‑Next
  - Vor Änderung: nicht funktionierend (keine/instabile Now/Next‑Daten bei M3U‑Only Setups).
  - Nach Änderung: Auto‑Erkennung & Speicherung von Xtream‑Daten + `streamId` bei M3U‑Import (Helfer `XtreamDetect`, `M3UParser` setzt `streamId` für Live) – zu testen durch Nutzer.
  - Neu: `EpgRepository` mit kurzem TTL‑Cache; UI (Live‑Tiles/Live‑Detail) nutzt Repository statt Direktaufrufen (Performanz, weniger Flaps).
  - Neu: Settings enthält „EPG testen (Debug)“ zur schnellen Diagnose.
  - Playlist‑Import speichert erkannte Xtream‑Creds (überschreibt bestehende nicht) und extrahiert `url-tvg` als EPG‑Fallback; plant `XtreamRefreshWorker`/`XtreamEnrichmentWorker` nach Import.
  - Now/Next via `XtreamClient.get_short_epg` wenn `streamId` vorhanden und Xtream‑Creds gesetzt sind.

- Playback‑Header
  - Vor Änderung: nicht funktionierend/302 bei VOD/Serie/Resume und Live‑Preview (fehlende UA/Referer).
  - Nach Änderung: zu testen durch Nutzer – Header (User‑Agent/Referer) werden für VOD/Serie/Resume sowie Live‑Preview gesetzt.

- Build‑Korrekturen
  - Vor Änderung: nicht funktionierend (Build brach ab wegen fehlender Imports und runBlocking in Composable Buttons).
  - Nach Änderung: zu testen durch Nutzer – Coroutine‑basierte Umsetzung der Header‑Ermittlung in ResumeCarousel, Importe ergänzt.

- Redirect‑Handling
  - Vor Änderung: nicht funktionierend (302 bei Cross‑Protocol Redirects).
  - Nach Änderung: zu testen durch Nutzer – `DefaultHttpDataSource.Factory.setAllowCrossProtocolRedirects(true)` im internen Player und Live‑Preview aktiv.

- XMLTV‑Fallback
  - Nach Änderung: zu testen durch Nutzer – Fallback in `EpgRepository` nutzt `tvg-id` und streamt XMLTV nur bis aktuellem/nächstem `programme`.

---

## Spätere Anreicherung (vorbereitet)

 - Live: Besseres Mapping `tvg-id` ↔ XMLTV‑Sendernamen (Normalisierung/Alias‑Listen).
  - VOD: Heuristiken aus M3U‑Namen (Jahr, Staffel/Episode), De‑Duping nach `sortTitle`, Backdrop/Poster‑Fallbacks.
  - Series: Staffel/Episode aus M3U‑Episoden‑Titeln ableiten und mit Xtream `series_info` verifizieren.

---

Erledigt (EPG) – 2025‑08‑29
- Persistenter EPG‑Cache (Room `epg_now_next`) inkl. TTL und Bereinigung.
- XMLTV Multi‑Indexing und Fallback (auch ohne Xtream).
  - Hintergrund‑Worker entfernt: EPG wird on‑demand (sichtbare Tiles/Favoriten beim App‑Start) nach OBX vorgepflegt.
- UI: Live‑Tiles mit Programmtitel + Fortschrittsbalken.
- Xtream‑Import: Merge `epg_channel_id` aus DB, falls fehlend.
- Xtream‑Detection: kompaktes URL‑Schema unterstützt.

Erledigt (UI‑Polish) – 2025‑08‑29
- Einheitliches Accent‑Design (DesignTokens.Accent/KidAccent).
- Hintergrund‑Polish (Gradient + Glow + geblurrtes App‑Icon) in Settings, Start, Library, PlaylistSetup, ProfileGate/Manager, Live/VOD/Series.
- „Carded Look“ mit `AccentCard` für Start‑Rows, Library‑Rails und Detail‑Screens.
- Buttons/Chips akzentuiert (Detail‑CTAs, FilterChips, AssistChips).
- Touch: Live‑Favoriten nur per Long‑Press verschiebbar; Scroll bleibt smooth.
#
# FishIT Player – **Roadmap: Import/Export finalisieren**

## m3uSuite · Roadmap vNext (Q4 2025 – Q2 2026)

**Ziel**  
m3uSuite stabilisieren und beschleunigen (Parsing/EPG/DB/Netzwerk), UI straffen (Compose), Wartung vereinfachen (CI/Diagnostik), und gezielte neue Funktionen ergänzen (Suche v2, PiP, Backup/Restore).

**Grundsätze**
- „Single Source of Truth“ pro Schicht (Settings/Headers, EPG, DB).
- Keine Blocker im UI‑Thread; Streaming‑/Batch‑I/O in Worker.
- Messbar: Jede Maßnahme bekommt Metriken (Zeit, Jank, Cache‑Hit).

---

## 0) Status-Snapshot (aus dem Repo)
- **Projekt**: Android‑App in **Kotlin**, Gradle‑Build, Ordner `app/`, `analysis_epg/`, `epg_inputs/`. :contentReference[oaicite:1]{index=1}
- **Domänenmodule (implizit)**: M3U‑Parser, EPG‑Handling, Netzwerk (OkHttp/Coil), Playback (Media3/Exo), Room‑DB, WorkManager, Compose UI.
 - **Workers aktiv**: XtreamDeltaImport (periodisch + on‑demand), TelegramCacheCleanup (periodisch), ScreenTimeReset (täglich), TelegramSync (on‑demand), ObxKeyBackfill (one‑shot).  
   **Workers entfernt/no‑op**: EPG Refresh (entfernt), Xtream Refresh/Enrichment (no‑op).

> Hinweis: Inhalte der alten `ROADMAP*.md` sind im Browser‑Plugin nicht lesbar; erledigte Punkte wurden daher **aus Erfahrungsstand und Code‑Struktur** bereinigt und die erfahrungsgemäß noch offenen Themen (Settings‑Hygiene, Scheduling/Lifecycle, Suche/Performance, EPG‑Caching) übernommen.

---

## 1) Meilensteine & Deliverables

### M0 · Stabilisierung & schnelle Gewinne (4–6 Wochen)
**Ziele:** Header‑Latenz rausnehmen, Import‑Peaks senken, EPG‑Doppler vermeiden, Parser robuster machen.

- [x] **RequestHeadersProvider (Snapshot)**  
  _Deliverable:_ `core/http` erhält Provider mit `StateFlow` + atomic Snapshot. OkHttp‑Interceptor liest **nur** aus Snapshot (kein `runBlocking`/DataStore im Interceptor).
  _Akzeptanz:_ Bilder/EPG laden ≥15 % schneller; 0 Interceptor‑Waits >5 ms in Trace.

- [x] **DAO‑Projektionen + Batch‑Reads**  
  _Deliverable:_ Schlanke Queries (z. B. `SELECT url, extraJson`, `SELECT streamId, epgChannelId`) + Import in Batches (5–10k).  
  _Akzeptanz:_ Peak‑RAM bei großen Imports −30 %; GC‑Pauses im Import‑Trace halbiert.

- [x] **EPG‑Refresh entkoppeln**  
  _Deliverable:_ Entfernt UI‑Loops; stattdessen `enqueueUnique` **OneTimeWork** bei App‑Start + PeriodicWork (Policies dokumentiert).  
  _Akzeptanz:_ Kein Doppel‑Refresh, Akku‑Last sinkt messbar (Android Battery Historian).

- [ ] **M3U‑Parser „Provider‑Pattern“-Tabelle**  
  _Status:_ Erste Ausbaustufe umgesetzt (URL‑Heuristiken für `series`/`movie(s)`/`vod`, kompakte Xtream‑Pfade).  
  _Deliverable:_ Erweiterbare Regex‑Tabelle (HLS‑Pfade, `channel/…`, `index.m3u8`, Query‑Flags) + optionale JSON‑Overrides (offen) und Provider‑Spezifische Lang/Group‑Mapper.  
  _Akzeptanz:_ Fehlklassifikationen bei Test‑Playlists <1 %.

- [ ] **EPG‑Cache auf monotone Zeitquelle**  
  _Deliverable:_ TTL/Delays via `elapsedRealtime()`, optionale LRU‑Größe konfigurierbar.  
  _Akzeptanz:_ „Now/Next“‑Latenz stabil, weniger Flaps bei Zeitwechsel.

- [ ] **Settings‑Hygiene (Batch‑Edits + Encryption)**  
  _Deliverable:_ Zusammengefasste DataStore‑Writes; Passwörter/Keys via Keystore AES/GCM verschlüsselt.  
  _Akzeptanz:_ ≤1 I/O‑Write pro Nutzeraktion; Secrets ruhen verschlüsselt.

---

### M1 · Performance-Welle (6–8 Wochen)
**Ziele:** Listen & Suche beschleunigen, UI‑Jank senken, I/O optimieren.

- [ ] **Room‑FTS (Suche v1.5)**  
  _Deliverable:_ FTS‑Tabelle für `name/sortTitle` (mit Normalisierung: NFKD + combining marks strip), `COLLATE NOCASE`‑Indizes.  
  _Akzeptanz:_ Globale Suche ≤100 ms bei 100k Einträgen; Tipp‑Suche ruckelfrei.

- [ ] **Paging 3 in allen großen Listen**  
  _Deliverable:_ DAO‑Paging‑Queries, `Lazy*` mit stabilen Keys.  
  _Akzeptanz:_ Scroll‑Jank (JankStats) −50 %, Time‑to‑First‑Viewport <200 ms.

- [ ] **Streaming‑I/O & Batch‑Writes (DB)**  
  _Deliverable:_ Große Updates über `INSERT OR REPLACE` in Batches + WAL‑Tuning.  
  _Akzeptanz:_ Import‑Zeit −30 % ggü. vorher.

- [ ] **JankStats + Startup‑Metriken**  
  _Deliverable:_ JankStats aktiv, Cold/Warm Start Messung + einfache In‑App Diagnose.

---

### M2 · UX‑Welle (8–10 Wochen)
**Ziele:** Bedienung verbessern, EPG sichtbarer, Playback‑Komfort.

- [ ] **EPG‑Overlay & Erinnerungen**  
  _Deliverable:_ Now/Next‑BottomSheet, „Erinnern“ (Alarm/Notif + WM), Favoriten‑Priorität.  
  _Akzeptanz:_ Reminder zuverlässig; Overlay <1 s Datenzeit.

- [ ] **PiP‑Modus (Media3)**  
  _Deliverable:_ Player mit PiP, Auto‑Resize, Resume.  
  _Akzeptanz:_ Kein Audio‑Drop beim Wechsel; „Zurück in App“ führt korrekt in Detail.

- [ ] **Favoriten 2.0**  
  _Deliverable:_ Mehrere Favoriten‑Listen („Sport“, „News“) mit eigenem Sortierzustand; DAO‑Filter statt In‑Memory.  
  _Akzeptanz:_ Wechseln/Filtern <50 ms.

- [ ] **Suche v2 (Suggest + Ranking)**  
  _Deliverable:_ Tipp‑Vorschläge (FTS Prefix), Ranking nach Favorit/Resume‑Score; Synonym‑CSV als Option.  
  _Akzeptanz:_ Erste Vorschläge <80 ms, Trefferqualität spürbar besser.

- [ ] **Settings‑Backup/Restore**  
  _Deliverable:_ SAF‑Datei + optional Google‑Drive‑Sync; `m3usuite-settings-v1.json` (verschlüsselte Secrets).  
  _Akzeptanz:_ Vollständiger Roundtrip auf Testgeräten.

---

### M3 · Ops & Qualität (laufend, harte Gates vor Release)
- [ ] **CI‑Checks**: detekt, ktlint, Android Lint (fatal im Release), Gradle Doctor.  
- [ ] **Baseline Profiles & Macrobenchmark**: Start/Scroll‑Benchmarks, Gate in CI.  
- [ ] **Crash‑/ANR‑Monitoring**: Crashlytics/Sentry (Opt‑In), Privacy‑Hinweise.  
- [ ] **Version Catalog + BOMs**: zentrale Pflege (Compose/Media3/OkHttp/Coil/Room).  
- [ ] **Security**: Network‑Security‑Config, StrictMode in Debug, LeakCanary im Debug.

---

## 2) Konkrete Fix‑Liste (umsetzbar ab M0)

1. (Erledigt) **Headers‑Snapshot + Provider**: Kein `runBlocking` im OkHttp‑Interceptor.
2. (Erledigt) **DAO‑Projektionen** statt massiver Entity‑Hydrierung; `addedAt` aus `extraJson` minimal lesen.
3. (Erledigt) **M1 Loop → WorkManager**: In der UI keine 5‑Minuten‑Loops; OneTimeWork beim Start + PeriodicWork.
4. (Teilweise) **collectAsStateWithLifecycle** überall; Side‑Effects via `repeatOnLifecycle`.
5. (Erledigt) **Sprachrobuste Sort‑Keys**: NFKD + Entfernen combining marks; `COLLATE NOCASE`‑Index.
6. **EPG‑Cache → monotone Zeit**; **adaptive TTL** (Favoriten kurz, Rest länger); optional **LRU**.
7. **M3U‑Provider‑Patterns** erweitern + JSON‑Overrides für Edge‑Provider.
8. **DataStore Batch‑Edits** bündeln; weniger Writes.
9. **FTS für Suche** + passende Indizes; `globalSearch` auf FTS umstellen.
10. **Expedited OneTimeWork** (wo erlaubt) für schnelle On‑Demand‑Refreshes.
11. **Secrets verschlüsseln** (Keystore AES/GCM) vor DataStore‑Persist.
12. **DAO‑Views für UI‑Rows** (schlank), Paging‑fähig.

---

## 3) Performance‑Hebel (Top‑5, messbar)

- **P1: Paging 3 + stabile Keys** → massiv weniger Memory/GC in Listen.
- **P2: Streaming‑I/O & Batch‑DB‑Writes** → schnellerer Import, weniger WAL‑Contention.
- **P3: JankStats + Split‑Composables** → gezielte Jank‑Reduktion an Hot‑Paths.
- **P4: Header‑Snapshot** → niedrigere Netz‑Latenz für Thumbnails/EPG.
- **P5: EPG‑Cache‑Strategie** → weniger Requests, konstantere UI‑Zeit.

---

## 4) Neue Implementierungen (10 Vorschläge)

1. **Settings‑Backup/Restore** (SAF + optional Drive; Secrets verschlüsselt).  
2. **EPG‑Overlay & Reminder** (Now/Next, Erinnern mit Notification/Alarm + WM).  
3. **Suche v2** (FTS‑Suggest, Ranking nach Favorit/Resume/Sichtungen, Synonyme als CSV).  
4. **TMDb/TvMaze‑Fallback** für Poster/Backdrop/Plot (Opt‑In).  
5. **PiP‑Modus** im internen Player (Media3).  
6. **Rollen‑Vorlagen für Profile** (Admin/Kid/Gast Defaults; schneller Setup‑Flow).  
7. **Crash‑/ANR‑Monitoring** (Crashlytics oder Sentry; Opt‑In in Settings).  
8. **Baseline Profiles & Macrobenchmarks** (Start/Scroll Gate vor Release).  
9. **Externer Cast‑Pfad (optional)**: Chromecast/DLNA hinter Feature‑Flag.  
10. **Favoriten 2.0**: Mehrere Listen + schnelle Kategorie‑Filterung via DAO.

---

## 5) Abhängigkeits- & Build-Strategie

- **Version Catalog (`libs.versions.toml`)** + **BOMs** (Compose, Media3, OkHttp, Coil, Room) → einheitliche Updates ohne Versionsdrift.  
- **CI (GitHub Actions)**: Lint/Detekt/Ktlint, Unit‑ und Instrumented‑Tests, Macrobenchmarks auf Referenzgerät.  
- **Release‑Gates**: Zero‑crash‑Regression, Startzeit ≤ definiertem Schwellwert, JankRate unter Ziel.

---

## 6) Risiken & Gegenmaßnahmen

- **Doppel‑Scheduling EPG** → _Fix_: Nur WM (OneTime + Periodic).  
- **Import‑RAM‑Peaks** → _Fix_: Projektionen + Batching.  
- **Netzwerk‑Latenz durch Interceptor‑Blocking** → _Fix_: Header‑Snapshot.  
- **Parser‑Fehlklassifikation** → _Fix_: Muster‑Tabelle + Provider‑Overrides + Testsuiten mit realen Playlists.

---

## 7) Messgrößen (Definition of Done)

- **TTFP (Time‑to‑first‑poster)**: −15 % ggü. Basis.
- **Cold Start**: −20 % ggü. Basis (Baseline Profile aktiv).
- **Scroll‑JankRate**: −50 % in großen Listen.
- **Import‑Zeit**: −30 % bei 100k Items.
- **EPG‑Trefferquote (Cache)**: +25 % (Hits / Gesamt).

---

## 8) Umsetzungsreihenfolge (Kurzform)

1) **M0**: Headers‑Provider, DAO‑Projektionen, WM‑Scheduling, Parser‑Pattern, monotone EPG‑TTL, Settings‑Batch+Encrypt.  
2) **M1**: Room‑FTS, Paging 3, Streaming‑I/O, JankStats/Startup‑Metriken.  
3) **M2**: EPG‑Overlay/Reminder, PiP, Favoriten 2.0, Suche v2, Backup/Restore.  
4) **M3**: CI‑Härtung, Baseline Profiles, Macrobench, Crash‑Monitoring, Version‑Catalog/BOMs.

---
