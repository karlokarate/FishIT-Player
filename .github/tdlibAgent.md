# tdlibAgent.md – Single Source of Truth für die Telegram-Integration

> **Dieses Dokument ist die _Single Source of Truth_ für die Telegram‑Integration im FishIT‑Player.**  
> Alle neuen Arbeiten an TDLib/Telegram müssen sich an diesem Dokument orientieren.  
> Einzige Ausnahmen sind:
> - die offizielle TDLib‑/tdl‑coroutines‑Dokumentation und
> - das lokale Dokument `tools/tdlib_coroutines_doku.md` (Upstream‑API‑Referenz).

Dieses Dokument ist bewusst so aufgebaut, dass es auf eine **erweiterte, hoch-performante und extrem robuste Telegram‑UX** hinarbeitet:
- eine **Unified Telegram Engine** im Hintergrund,
- **Zero‑Copy Streaming** für Telegram‑Videos,
- ein **intelligenter Heuristik‑Layer** für Content‑Erkennung,
- ein **Turbo‑Sync‑Modus** mit Parallelisierung,
- ein **Telegram Activity Feed**,
- plus gezielte Qualitätstools (Leak‑Checks, Profiling, Coverage, Widgets, etc.).

---

## 0. Zielbild & Rahmen

### 0.1 Ziel der Integration

1. **Einmaliger Login im Settings‑Screen**, mit **persistenter TDLib‑Session** über App‑Neustarts hinweg (kein erneutes Eingeben von Telefonnummer/Code, solange TDLib die Session akzeptiert).
2. **Zentrale Telegram‑Schicht („Unified Telegram Engine“)**
   - genau **einen** `TdlClient` pro Prozess,
   - **eine** `T_TelegramSession` für Auth/State,
   - koordinierte Nutzung von `T_ChatBrowser`, `T_TelegramFileDownloader` und `TelegramContentRepository`.
3. **Flow‑basierte Verarbeitung**:
   - Auth‑Status, Sync‑Status, Activity‑Events und Inhalte werden über `Flow` / `StateFlow` bereitgestellt.
4. **Chat‑Picker → Parsing → UI**:
   - User wählt Chats im Settings‑Screen.
   - Worker nutzt `T_ChatBrowser` + `MediaParser` + `TgContentHeuristics`, befüllt `TelegramContentRepository`.
   - StartScreen + Library zeigen Telegram‑Content (VOD/Serien) strukturiert an.
5. **In‑App Live‑Logging & Diagnostik**:
   - Kurze Status‑Overlays („Parsing erfolgreich / fehlgeschlagen“).
   - Ein Log‑Screen für Telegram‑Ereignisse (Phone & TV).
6. **TV & Touch**:
   - Gleiches Backend für Smartphones, Tablets, Android‑TV, FireTV (ARM64 + armeabi‑v7a).
   - TV‑Optimierung nutzt vorhandene Focus‑ und Layout‑Kits (`FocusKit`, `FishRow`, `FishTelegramContent`).
7. **Next‑Level UX:**
   - **Zero‑Copy Telegram Streaming** (möglichst ohne Disk‑Zwischenlagen).
   - **Turbo‑Sync** mit adaptiver Parallelisierung abhängig vom Device‑Profil.
   - **Intelligente Content‑Heuristik** für bessere Film/Serien‑Erkennung.
   - **Telegram Activity Feed**, der neue Inhalte visuell anzeigt.

### 0.2 Technischer Rahmen (IST)

- TDLib wird über **`dev.g000sha256:tdl-coroutines-android:5.0.0`** eingebunden.
- Relevante Telegram‑Module existieren aktuell unter:

```text
app/src/main/java/com/chris/m3usuite/telegram/config/AppConfig.kt
app/src/main/java/com/chris/m3usuite/telegram/config/ConfigLoader.kt
app/src/main/java/com/chris/m3usuite/telegram/session/TelegramSession.kt
app/src/main/java/com/chris/m3usuite/telegram/browser/ChatBrowser.kt
app/src/main/java/com/chris/m3usuite/telegram/parser/MediaParser.kt
app/src/main/java/com/chris/m3usuite/telegram/models/MediaModels.kt
app/src/main/java/com/chris/m3usuite/telegram/downloader/TelegramFileDownloader.kt
app/src/main/java/com/chris/m3usuite/telegram/ui/TelegramViewModel.kt

app/src/main/java/com/chris/m3usuite/data/repo/TelegramContentRepository.kt
app/src/main/java/com/chris/m3usuite/player/datasource/TelegramDataSource.kt

app/src/main/java/com/chris/m3usuite/ui/layout/FishTelegramContent.kt
app/src/main/java/com/chris/m3usuite/ui/screens/TelegramSettingsViewModel.kt
app/src/main/java/com/chris/m3usuite/ui/screens/TelegramServiceClient.kt

app/src/main/java/com/chris/m3usuite/work/TelegramSyncWorker.kt
```

Dieses Dokument beschreibt, wie diese Bausteine **zusammengeführt**, **umstrukturiert** und **erweitert** werden sollen.

---

## 1. Ziel‑Struktur der Telegram‑Module

### 1.1 Package‑Layout (SOLL)

Alle Telegram‑Sachen sollen logisch gebündelt werden. Ziel‑Layout:

```text
com.chris.m3usuite.telegram.core
    - T_TelegramServiceClient.kt   (Unified Telegram Engine)
    - T_TelegramSession.kt         (ehem. TelegramSession, leicht angepasst)
    - T_ChatBrowser.kt             (ehem. ChatBrowser)
    - T_TelegramFileDownloader.kt  (ehem. TelegramFileDownloader)

com.chris.m3usuite.telegram.config
    - AppConfig.kt
    - ConfigLoader.kt

com.chris.m3usuite.telegram.models
    - MediaModels.kt               (MediaKind, ParsedItem, ...)

com.chris.m3usuite.telegram.parser
    - MediaParser.kt
    - TgContentHeuristics.kt       (neu – heuristische Content-Erkennung)

com.chris.m3usuite.telegram.ui
    - TelegramSettingsViewModel.kt (ehem. ui/screens/TelegramSettingsViewModel.kt, Telegram‑Teil)
    - TelegramViewModel.kt         (nur falls weiter genutzt; sonst schrittweise ablösen)
    - TelegramLogViewModel.kt      (neu – für Log‑Screen)
    - TelegramActivityFeedViewModel.kt (neu – für Activity Feed)

com.chris.m3usuite.telegram.logging
    - TelegramLogRepository.kt     (neu)

com.chris.m3usuite.telegram.work
    - TelegramSyncWorker.kt        (ehem. work/TelegramSyncWorker.kt – Turbo‑Sync)

com.chris.m3usuite.telegram.player
    - TelegramDataSource.kt        (ehem. player/datasource/TelegramDataSource.kt; Zero‑Copy‑fähig)

com.chris.m3usuite.telegram.ui.feed
    - TelegramActivityFeedScreen.kt (neu – Activity Feed UI)

com.chris.m3usuite.data.repo
    - TelegramContentRepository.kt (bleibt bei den Repos, bekommt klaren Telegram‑Scope)

com.chris.m3usuite.ui.layout
    - FishTelegramContent.kt
    - FishTelegramRow.kt
```

**Namenskonvention für Telegram‑Core‑Typen:**

- Prefix `T_` oder `Tg` für Core‑Klassen, die direkt mit TDLib arbeiten, z. B.:
  - `T_TelegramServiceClient`, `T_ChatBrowser`, `T_TelegramSession`, `T_TelegramFileDownloader`.
- UI‑Klassen behalten lesbare Namen (`TelegramSettingsViewModel`, `TelegramActivityFeedViewModel`, `FishTelegramRow`).

### 1.2 Migration IST → SOLL

Todos für die Umstrukturierung:

- [ ] `TelegramServiceClient.kt` aus `ui/screens` nach `telegram/core` verschieben und in `T_TelegramServiceClient` umbenennen.
- [ ] `TelegramSession.kt` nach `telegram/core` verschieben und in `T_TelegramSession` umbenennen (oder intern `T_TelegramSession` und extern `TelegramSession` typealias, falls viel Code existiert).
- [ ] `ChatBrowser.kt` nach `telegram/core` verschieben und in `T_ChatBrowser` umbenennen.
- [ ] `TelegramFileDownloader.kt` nach `telegram/core` verschieben und in `T_TelegramFileDownloader` umbenennen.
- [ ] `TelegramDataSource.kt` nach `telegram/player` verschieben (Package `com.chris.m3usuite.telegram.player`).
- [ ] `TelegramSyncWorker.kt` nach `telegram/work` verschieben.
- [ ] `TelegramSettingsViewModel.kt` aus `ui/screens` nach `telegram/ui` verschieben (Package anpassen).
- [ ] `TelegramViewModel.kt` im Package `telegram/ui` belassen, aber:
  - keine neuen Call‑Sites hinzufügen,
  - Funktionalität schrittweise in spezialisierte VMs (Settings, Feed, Admin/Debug) überführen.
- [ ] Neue Dateien anlegen:
  - `telegram/parser/TgContentHeuristics.kt`
  - `telegram/logging/TelegramLogRepository.kt`
  - `telegram/ui/TelegramLogViewModel.kt`
  - `telegram/ui/feed/TelegramActivityFeedViewModel.kt`
  - `telegram/ui/feed/TelegramActivityFeedScreen.kt`
- [ ] Import‑Pfade und DI/Factory‑Konstrukte im gesamten Projekt auf die neue Struktur aktualisieren.

---

## 2. Begriffe → konkrete Bausteine (IST‑Module, SOLL‑Rolle)

### 2.1 Client

- **Typ:** `dev.g000sha256.tdl.TdlClient`
- **Ziel‑Owner:** `T_TelegramServiceClient`
- **Regel:** Es existiert **genau eine** Instanz von `TdlClient` pro Prozess.  
  Außerhalb von `telegram.core` darf `TdlClient` **nicht direkt** verwendet werden.

### 2.2 Session

- **IST:** `TelegramSession` unter `telegram/session`.
- **Ziel:** `T_TelegramSession` unter `telegram/core`, die:
  - einen `TdlClient` vom ServiceClient injiziert bekommt,
  - `AuthEvent`‑Flows erzeugt und konsumiert,
  - den TDLib‑Auth‑State (`AuthorizationState*`) auf einfache Domain‑Events mappt.

### 2.3 Browser

- **IST:** `ChatBrowser` unter `telegram/browser`.
- **Ziel:** `T_ChatBrowser` unter `telegram/core`, die:
  - über `T_TelegramSession`/`TdlClient` Chats und Messages paginiert,
  - Update‑Flows (`newMessageUpdates`, `chatPositionUpdates`) konsumiert,
  - High‑Level‑APIs bereitstellt:
    - `getTopChats(...)`
    - `getChat(chatId: Long)`
    - `loadMessagesPaged(chatId: Long, ...)`
    - `observeMessages(chatId: Long): Flow<List<Message>>`.

### 2.4 Parser & Intelligence

- **IST:** `MediaParser` unter `telegram/parser`, `MediaKind`/`ParsedItem` unter `telegram/models`.
- **Ziel:**
  - `MediaParser`: stateless Parser‑Logik bleibt, klassifiziert Content (Movie, Series, Episode, RAR, Invite, etc.).
  - **Neu:** `TgContentHeuristics`:
    - zusätzliche Heuristiken für Titel, Episoden, Reihenfolgen, Subchats,
    - kombinierte Auswertung von Chat‑Titel, Dateiname, Caption, Sprache,
    - Hilfsfunktionen wie:
      - `fun classify(parsed: ParsedItem, chatTitle: String): HeuristicResult`
      - `fun guessSeasonEpisode(text: String): SeasonEpisode?`.

### 2.5 Repository

- **IST:** `TelegramContentRepository` unter `data/repo`.
- **Ziel:** Single‑Entry‑Point für **persistente** Telegram‑Inhalte:
  - speichert `ObxTelegramMessage` in ObjectBox,
  - nutzt `MediaParser` + `TgContentHeuristics` beim Indexieren,
  - bietet `Flow<List<MediaItem>>` für:
    - StartScreen (Telegram‑Rows),
    - Library (Tabs „Telegram Filme“ / „Telegram Serien“),
    - Telegram Activity Feed.
  - erzeugt Play‑URLs im Format `tg://file/<fileId>?chatId=...&messageId=...`.

### 2.6 Downloader & DataSource (Zero‑Copy‑fähig)

- **Downloader (Core):** `T_TelegramFileDownloader` in `telegram/core`.
  - nutzt `TdlClient.fileUpdates` (tdl‑coroutines) für Download‑Progress,
  - stellt API bereit für:
    - Datei‑Download auf Disk,
    - **Stream‑Zugriff** in in‑Memory‑Ringbuffer.

- **DataSource (Player):** `TelegramDataSource` in `telegram/player`.
  - nutzt `T_TelegramFileDownloader` für Zero‑Copy‑Streaming, wo sinnvoll,
  - wird von `DelegatingDataSourceFactory` für `tg://` URLs ausgewählt.

### 2.7 UI‑Schicht

- **Settings:**
  - `TelegramSettingsViewModel` unter `telegram/ui`.
  - Verwaltet:
    - Login‑Flow (Telefonnummer, Code, Passwort),
    - Chat‑Picker‑State (welche Chats sind für VOD/Serien/Feed ausgewählt),
    - Flags in `SettingsStore` (`TG_ENABLED`, `TG_SELECTED_*`).

- **Activity Feed:**
  - `TelegramActivityFeedViewModel` unter `telegram/ui/feed`.
  - Konsumiert:
    - `Flow<TgActivityEvent>` aus Repository/ServiceClient,
    - `TelegramContentRepository` für Detail‑Infos.
  - `TelegramActivityFeedScreen` zeigt neue Inhalte (Film/Serien‑Updates, neue Einträge) an.

- **Allgemeine Telegram‑VM:**
  - `TelegramViewModel` unter `telegram/ui`.
  - Nur für dedizierte Legacy‑Screens/Debug nutzen, keine neuen Features mehr direkt dagegen bauen.

- **Layout/Rows:**
  - `FishTelegramContent` und `FishTelegramRow` unter `ui/layout`.
  - Parallele Struktur zu Xtream‑Rows, inklusive Focus‑Handling auf TV.

### 2.8 Worker & Scheduling (Turbo‑Sync)

- **Worker:** `TelegramSyncWorker` unter `telegram/work`.
  - Liest Chat‑Auswahl aus `SettingsStore`.
  - Nutzt `T_TelegramServiceClient` + `T_ChatBrowser` + `MediaParser` + `TgContentHeuristics` + `TelegramContentRepository`.
  - arbeitet parallel pro Chat‑Gruppe mit adaptiver Parallelität.

- **Scheduling:** Anbindung über bestehenden `SchedulingGateway`:
  - Konstante `NAME_TG_SYNC`.
  - Helper‑Funktionen: `scheduleTelegramSync(mode, refreshHome)`.

### 2.9 Logging

- **Basis‑Logger:** `DiagnosticsLogger` unter `diagnostics`.
- **Ziel:** `TelegramLogRepository` unter `telegram/logging`, das:
  - einen In‑Memory‑Ringbuffer für Telegram‑Logs hält,
  - `StateFlow<List<TgLogEntry>>` + `SharedFlow<TgLogEntry>` für UI bereitstellt,
  - intern `DiagnosticsLogger` nutzt, um Logcat/Datei zu bedienen.

---

## 3. Konkrete Todos (SOLL‑Umsetzung – Core & Architektur)

### 3.1 T_TelegramServiceClient (Unified Telegram Engine)

**Neue Datei:** `telegram/core/T_TelegramServiceClient.kt`  
(ersetzt den Placeholder in `ui/screens/TelegramServiceClient.kt`, der gelöscht wird)

- [ ] Klasse `T_TelegramServiceClient` implementieren mit:
  - internem `CoroutineScope(SupervisorJob() + Dispatchers.IO)`,
  - genau einer Instanz von `TdlClient`,
  - einer Instanz von `T_TelegramSession`,
  - einer Instanz von `T_ChatBrowser`,
  - einer Instanz von `T_TelegramFileDownloader`.
- [ ] API für andere Schichten:
  - `val authState: StateFlow<TelegramAuthState>`
  - `val connectionState: StateFlow<TgConnectionState>`
  - `val syncState: StateFlow<TgSyncState>`
  - `val activityEvents: SharedFlow<TgActivityEvent>` (für Activity Feed)
  - `suspend fun ensureStarted(context: Context, settings: SettingsStore)`
  - `suspend fun login(phone: String?, code: String?, password: String?)`
  - `suspend fun listChats(context: Context, limit: Int): List<Chat>`
  - `suspend fun resolveChatTitle(chatId: Long): String`
  - `fun downloader(): T_TelegramFileDownloader`
- [ ] Sicherstellen, dass:
  - `TdlClient` mithilfe von `ConfigLoader.loadConfig(context, ...)` konfiguriert wird,
  - TDLib‑Update‑Flows (`authorizationStateUpdates`, `updates`, `fileUpdates`) im Scope des ServiceClients gesammelt und auf Session/Browser/Downloader/Activity‑Feed verteilt werden.
- [ ] Einen „Engine‑Supervisor“ implementieren:
  - Wiederanlauf bei kritischen Fehlern,
  - Reconnect bei Netzwerkwechseln,
  - Hooks für Lifecycle (`ProcessLifecycleOwner`) verwenden, um die Engine definiert zu starten/stoppfen.

### 3.2 T_TelegramSession überarbeiten

**Datei:** `telegram/session/TelegramSession.kt` → `telegram/core/T_TelegramSession.kt`

- [ ] Konstruktor anpassen:
  - kein eigener `TdlClient` mehr anlegen,
  - `TdlClient` wird vom ServiceClient injiziert.
- [ ] `authEvents: SharedFlow<AuthEvent>` beibehalten, aber:
  - Abruf von `authorizationStateUpdates` direkt über tdl‑coroutines API,
  - Funktionen:
    - `startAuthLoop()`
    - `sendPhoneNumber(...)`
    - `sendCode(...)`
    - `sendPassword(...)`
  - laufen ausschließlich im Scope des ServiceClients.
- [ ] Mapping von `AuthorizationState*` auf `AuthEvent` beibehalten, aber:
  - Logging nicht über `println`, sondern via `TelegramLogRepository`.

### 3.3 T_ChatBrowser migrieren

**Datei:** `telegram/browser/ChatBrowser.kt` → `telegram/core/T_ChatBrowser.kt`

- [ ] `ChatBrowser` in `T_ChatBrowser` umbenennen, Package `telegram.core`.
- [ ] Konstruktor:
  - nimmt `TdlClient` oder `T_TelegramSession` (je nach Bedarf).
- [ ] Öffentliche API konsolidieren:
  - `suspend fun getTopChats(limit: Int): List<Chat>`
  - `suspend fun getChat(chatId: Long): Chat>`
  - `suspend fun loadMessagesPaged(chatId: Long, ...): List<Message>`
  - `fun observeMessages(chatId: Long): Flow<List<Message>>`
- [ ] Intern keine eigenen Scopes erzeugen, sondern den ServiceClient‑Scope nutzen.

### 3.4 TelegramSettingsViewModel an ServiceClient anbinden

**Datei:** `ui/screens/TelegramSettingsViewModel.kt` → `telegram/ui/TelegramSettingsViewModel.kt`

- [ ] Konstruktor so umbauen, dass:
  - `T_TelegramServiceClient` + `SettingsStore` injiziert werden,
  - kein direkter `TdlClient` mehr erstellt wird.
- [ ] `TelegramSettingsState` so bauen, dass:
  - `authState: TelegramAuthState` über `T_TelegramServiceClient.authState` gespeist wird,
  - `isLoadingChats`, `availableChats`, `selectedChats` auf Browser/Settings‑Daten beruhen.
- [ ] Login‑Aktionen (`onPhoneEntered`, `onCodeEntered`, `onPasswordEntered`) delegieren:
  - `viewModelScope.launch { serviceClient.login(phone, code, password) }`.
- [ ] Chat‑Picker:
  - `viewModelScope.launch { serviceClient.listChats(context, limit) }` verwenden,
  - Auswahl in `SettingsStore` schreiben (`TG_SELECTED_*_CHATS_CSV` für VOD/Serien/Feed).

---

## 4. Konkrete Todos – Sync, Streaming & Content‑Pipelines

### 4.1 TelegramSyncWorker (Turbo‑Sync)

**Datei:** `work/TelegramSyncWorker.kt` → `telegram/work/TelegramSyncWorker.kt`

- [ ] Worker so implementieren, dass im `doWork()`:
  - `SettingsStore` geladen wird,
  - `T_TelegramServiceClient.ensureStarted(context, settingsStore)` aufgerufen wird,
  - relevante Chat‑IDs ermittelt werden (VOD/Serien/Feed),
  - pro Chat/Gruppe **parallele** Sync‑Tasks gestartet werden:
    - `Dispatchers.IO.limitedParallelism(n)`, wobei `n` aus Device‑Profil (CPU‑Kerne, Device‑Klasse Phone/TV) abgeleitet wird,
    - jeweils:
      - Messages via `T_ChatBrowser.loadMessagesPaged(...)` laden,
      - `MediaParser.parseMessage(...)` anwenden,
      - `TgContentHeuristics` nutzen, um Film/Serie/Episode/Adult/Subchat zu schärfen,
      - `TelegramContentRepository` aktualisieren.
- [ ] Modes:
  - `MODE_ALL`
  - `MODE_SELECTION_CHANGED`
  - `MODE_BACKFILL_SERIES`
- [ ] Fortschrittslogging:
  - `TelegramLogRepository.logSyncEvent(...)` nutzen,
  - `T_TelegramServiceClient.syncState` aktualisieren (z. B. `Running`, `Completed`, `Failed`).

### 4.2 TelegramDataSource (Zero‑Copy‑fähiges Streaming)

**Datei:** `player/datasource/TelegramDataSource.kt` → `telegram/player/TelegramDataSource.kt`

- [ ] Konstruktor so anpassen, dass:
  - ein `T_TelegramServiceClient` und/oder `T_TelegramFileDownloader` injiziert werden.
- [ ] `open(dataSpec: DataSpec)`:
  - `tg://file/<fileId>?chatId=...&messageId=...` parsen,
  - `T_TelegramFileDownloader` bitten, einen in‑Memory‑Stream / Ringbuffer für die Datei bereitzustellen,
  - `TransferListener` korrekt informieren (`onTransferStart`).
- [ ] `read(...)`:
  - Bytes direkt aus in‑Memory‑Buffer (z. B. Okio `Buffer` oder eigener ByteArray‑Ringbuffer) lesen,
  - Download bei Bedarf im Hintergrund fortsetzen.
- [ ] `close()`:
  - laufende Streams abbrechen/freigeben,
  - `TransferListener.onTransferEnd` aufrufen.
- [ ] `DelegatingDataSourceFactory` sicherstellen:
  - für Schema `tg`, Host `file` → `TelegramDataSource` erzeugen.

### 4.3 TelegramContentRepository schärfen

**Datei:** `data/repo/TelegramContentRepository.kt`

- [ ] Sicherstellen, dass:
  - `encodeTelegramId(...)` und `isTelegramItem(...)` stabil und eindeutig sind,
  - Play‑URL immer `tg://file/<fileId>?chatId=...&messageId=...` ist.
- [ ] Funktionen bereitstellen:
  - `fun getTelegramVod(): Flow<List<MediaItem>>`
  - `fun getTelegramSeries(): Flow<List<MediaItem>>`
  - `fun getTelegramFeedItems(): Flow<List<MediaItem>>`
  - `fun searchTelegramContent(query: String): Flow<List<MediaItem>>`
- [ ] `SettingsStore`‑Integration:
  - beim Indexieren berücksichtigen, aus welchen Chats Inhalte kommen (VOD‑ vs. Serien‑ vs. Feed‑Listen).

---

## 5. Konkrete Todos – Logging, Activity Feed & UI

### 5.1 TelegramLogRepository & Log‑Screen

**Neue Datei:** `telegram/logging/TelegramLogRepository.kt`  
**Neue Datei:** `telegram/ui/TelegramLogViewModel.kt`  
**Neue UI:** Telegram‑Log‑Screen (Compose)

- [ ] `TelegramLogRepository`:
  - Datentyp `data class TgLogEntry(timestamp, level, source, message, details?)`,
  - In‑Memory‑Ringbuffer (z. B. 500 Einträge),
  - `val entries: StateFlow<List<TgLogEntry>>`,
  - `val events: SharedFlow<TgLogEntry>`,
  - interne Nutzung von `DiagnosticsLogger` für Logcat/Datei.
- [ ] Instrumentierung:
  - `T_TelegramServiceClient`, `T_TelegramSession`, `T_ChatBrowser`, `T_TelegramFileDownloader`, `TelegramSyncWorker`, `TelegramDataSource` rufen `TelegramLogRepository.log(...)` auf.
- [ ] Log‑Screen:
  - eigener Eintrag im Settings‑Bereich („Telegram‑Log“),
  - Liste der Log‑Einträge (filterbar nach Level/Source, DPAD‑tauglich),
  - einfache Export‑Option (z. B. Log per Share‑Intent).
- [ ] Short Overlays:
  - UI‑Shell (StartScreen, Library, Settings) lauschen auf `events`,
  - bei `level >= WARN` Snackbar/Toast mit kompaktem Text anzeigen.

### 5.2 Telegram Activity Feed

**Neue Dateien:**  
`telegram/ui/feed/TelegramActivityFeedViewModel.kt`  
`telegram/ui/feed/TelegramActivityFeedScreen.kt`

- [ ] `TgActivityEvent` definieren:
  - z. B. `NewMovie`, `NewEpisode`, `NewArchive`, `ChatUpdated`.
- [ ] `T_TelegramServiceClient`:
  - bei relevanten Updates (`NewMessage`, fertiger Download, erfolgreiche Parse‑Ergebnisse) `TgActivityEvent` in `activityEvents` emittieren.
- [ ] `TelegramActivityFeedViewModel`:
  - auf `activityEvents` hören,
  - `TelegramContentRepository` nutzen, um zugehörige `MediaItem`s zu laden,
  - einen UI‑freundlichen Feed‑State liefern (`StateFlow<FeedState>`).
- [ ] `TelegramActivityFeedScreen`:
  - Feed‑Liste anzeigen (TV‑Focusfähig und Touch),
  - direktes Abspielen/Aufrufen von Items ermöglichen,
  - von Start/Library/Settings aus erreichbar (z. B. als eigener Punkt „Telegram‑Feed“).

---

## 6. Qualität, Tools & Bonus‑Module

### 6.1 Qualität & Debug

- [ ] Sicherstellen, dass `tdl-coroutines-android` weiterhin nativen Code für `arm64-v8a` und `armeabi-v7a` enthält.
- [ ] Keine eigenen `libtdjni.so` mehr einbinden (Ordner `b/libtd` nicht ins Classpath zurückholen).
- [ ] R8/ProGuard‑Regeln für `dev.g000sha256.tdl.dto.*` ergänzen, falls noch nicht vorhanden.
- [ ] Debug‑Tools:
  - `leakcanary-android` (für Telegram‑Scopes),
  - `kotlinx-coroutines-debug` (Debug‑Builds, `System.setProperty("kotlinx.coroutines.debug", "on")`),
  - `androidx.profileinstaller` (Startzeit, insbesondere v7a‑TV‑Sticks),
  - **JetBrains Kover** für Test‑Coverage von Parser, Repository, Worker (Gradle‑Plugin `kover`).

### 6.2 UX & Visuals

- [ ] **Coil 3.x** integrieren:
  - Thumbnails für Telegram‑Media generieren (lokale Frames, z. B. über `MediaMetadataRetriever` oder Media3),
  - `FishTelegramContent` so erweitern, dass Telegram‑Items Coverbilder anzeigen können.
- [ ] **Compose Drag‑/Reorder‑Listen** (oder eigenes Reorder‑Pattern) für Chat‑Picker:
  - Reihenfolge der Telegram‑Chats (z. B. wichtigere Chats nach oben) anpassbar machen,
  - DPAD‑fähig auf TV, Touch auf Phone/Tablet.
- [ ] **Jetpack Glance Widgets**:
  - einfacher Homescreen‑Widget „Neue Telegram‑Filme“ / „Neue Telegram‑Serien“,
  - bezieht Daten aus `TelegramContentRepository.getTelegramFeedItems()`.

### 6.3 Tests & Lint

- [ ] Unit‑Tests:
  - `MediaParser`: Episoden‑Heuristiken (SxxEyy, „Episode 4“, Sprach‑Tags),
  - `TgContentHeuristics`: Klassifikation/Score‑Tests,
  - `TelegramContentRepository`: ID‑Mapping und URL‑Generierung.
- [ ] Instrumented‑Tests:
  - TV‑Navigation: StartScreen → Telegram‑Row → Playback,
  - Library: Telegram‑Tab „Filme“/„Serien“,
  - Activity Feed‑Navigation + Playback.
- [ ] Lint/Detekt:
  - Regel: „Kein direkter `TdlClient`‑Zugriff außerhalb `telegram.core`“,
  - Telegram‑Dateien: keine Monster‑Methoden (> 100 LOC), klare Flow‑Namen (`*State`, `*Events`).

---

## 7. Nutzung dieses Dokuments

1. **Dieses Dokument ist die maßgebliche Referenz** für alle Arbeiten an der Telegram‑Integration im FishIT‑Player.
2. Vor Änderungen an TDLib/Telegram:
   - dieses Dokument lesen und prüfen, ob die geplante Änderung dazu passt.
3. Bei Implementierung neuer Features:
   - zuerst passende Stelle in der beschriebenen Struktur bestimmen (Core, UI, Work, Player, Repo, Logging, Feed, Intelligence),
   - Klassen/Files entsprechend in den genannten Packages anlegen.
4. Bei Abweichungen vom Ist‑Code:
   - Code an dieses Dokument anpassen, nicht umgekehrt – außer wenn die tdl‑coroutines‑Doku eine technische Änderung erzwingt.
5. Nach größeren Änderungen:
   - dieses Dokument anpassen, sodass es **weiterhin Single Source of Truth** bleibt – inklusive der hier beschriebenen „Next‑Level“‑Features (Unified Telegram Engine, Zero‑Copy Streaming, Turbo‑Sync, Content‑Heuristik, Activity Feed).
