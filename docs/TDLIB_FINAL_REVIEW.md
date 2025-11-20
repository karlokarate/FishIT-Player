# TDLib Final Review – Wiring, Cleanup, CI, Quality

Diese Datei definiert den **abschließenden Review- und Cleanup-Prozess** für die TDLib-Integration im Repository `karlokarate/FishIT-Player`.

Zweck:

- Abgleich der tatsächlichen Implementierung (**IST**) mit der Architektur-Spezifikation in `.github/tdlibAgent.md` (**SOLL**).
- Aufräumen von Legacy-Code.
- Schließen aller Wiring-Lücken (Settings ↔ Sync ↔ Repository ↔ UI ↔ Streaming ↔ Logging).
- Vollständiges Aktivieren von Feed- und Log-Screens.
- Durchziehen einer konsistenten Logging-Strategie.
- Sicherstellen, dass CI und Gradle-Konfigurationen vollständig und korrekt sind.
- Aktualisieren der Architektur-Doku, sodass sie den finalen Stand repräsentiert.

**WICHTIG:**  
Checkboxen (`- [ ]`) dürfen erst dann auf `- [x]` gesetzt werden, wenn die jeweilige Aufgabe vollständig, getestet und CI-grün erledigt ist.  
Diese Datei wird solange wiederverwendet, bis **alle** Checkboxes auf `- [x]` stehen.

---

## 0. Branch und Referenzen

**Pflicht:**

- [ ] Alle Arbeiten dieses Reviews erfolgen ausschließlich im Branch  
      `feature/tdlib-final-review-and-polish`.

- [ ] Vor Beginn ist `main` lokal aktualisiert und in den Branch gemergt:
      - `git checkout main`
      - `git pull origin main`
      - `git checkout feature/tdlib-final-review-and-polish`
      - `git merge main`

- [ ] Folgende Dateien wurden zu Beginn des Reviews vollständig gelesen und als verbindliche Spezifikation verstanden:
  - [ ] `.github/tdlibAgent.md`
  - [ ] `docs/TDLIB_TASK_GROUPING.md`

---

## 1. Legacy-TDLib-Code aufräumen

Ziel: Alle alten, überflüssigen TDLib-Integrationen entfernen oder sauber als ungenutztes Legacy kennzeichnen, sodass NUR noch die neue Architektur relevant ist.

### 1.1 Legacy-Klassen identifizieren

Folgende Dateien gelten als Legacy (alte Architektur):

- `app/src/main/java/com/chris/m3usuite/telegram/session/TelegramSession.kt`
- `app/src/main/java/com/chris/m3usuite/telegram/browser/ChatBrowser.kt`
- `app/src/main/java/com/chris/m3usuite/telegram/downloader/TelegramFileDownloader.kt`
- `app/src/main/java/com/chris/m3usuite/telegram/ui/TelegramViewModel.kt`

Tasks:

- [ ] Überprüfen, ob diese Legacy-Klassen **nirgendwo mehr produktiv verwendet** werden
      (keine seriöse Nutzung in UI, Settings, Worker, DataSource, Player).

- [ ] Falls noch irgendwo referenziert:
      - [ ] Referenzen auf die neuen Core-Klassen (`T_TelegramServiceClient`, `T_TelegramSession`, `T_ChatBrowser`, `T_TelegramFileDownloader`) umstellen.

### 1.2 Entweder löschen oder eindeutig als Legacy markieren

- [ ] Für jede Legacy-Datei entscheiden:
  - Entweder:
    - [ ] Klasse löschen (nur wenn sicher komplett ungenutzt).
  - Oder:
    - [ ] Klasse mit `@Deprecated("Legacy TDLib integration – do not use in new code.")` annotieren.
    - [ ] Alle verbleibenden Referenzen in Kommentaren mit „LEGACY/UNUSED“ markieren.
    - [ ] Sicherstellen, dass keine neuen Features mehr auf diese Klassen aufbauen.

---

## 2. Settings ↔ Sync Wiring schließen

Ziel: Änderungen in den Telegram-Einstellungen (aktivieren, Chat-Auswahl) müssen zuverlässig einen Sync auslösen, wie in `.github/tdlibAgent.md` spezifiziert.

Betroffene Dateien:

- `app/src/main/java/com/chris/m3usuite/telegram/ui/TelegramSettingsViewModel.kt`
- `app/src/main/java/com/chris/m3usuite/work/TelegramSyncWorker.kt`
- `app/src/main/java/com/chris/m3usuite/work/SchedulingGateway.kt`
- `app/src/main/java/com/chris/m3usuite/prefs/SettingsStore.kt`

### 2.1 Sync-Scheduling aus Settings

- [ ] In `TelegramSettingsViewModel`:
  - [ ] Nach Änderungen an der Chat-Auswahl wird `SchedulingGateway.scheduleTelegramSync(...)` aufgerufen.
  - [ ] Der Modus entspricht der Spezifikation aus `tdlibAgent.md`:
        z. B. `MODE_SELECTION_CHANGED` bei neuer/änderter Auswahl.
  - [ ] Optionaler initialer Vollsync (`MODE_ALL`) nach erfolgreicher erstmaliger Aktivierung von Telegram.

- [ ] `SettingsStore` Keys (`TG_ENABLED`, `TG_SELECTED_*_CHATS_CSV` etc.) werden korrekt gelesen/geschrieben.

### 2.2 Worker-Logik prüfen

- [ ] `TelegramSyncWorker`:
  - [ ] Liest alle relevanten Settings, bevor er arbeitet.
  - [ ] Ruft `T_TelegramServiceClient.ensureStarted(...)` korrekt auf.
  - [ ] Lädt Nachrichten ausschließlich über `T_ChatBrowser`.
  - [ ] Wendet `MediaParser` (+ Heuristiken) auf alle relevanten Nachrichten an.
  - [ ] Persistiert sauber in `TelegramContentRepository`.
  - [ ] Aktualisiert `syncState` in `T_TelegramServiceClient`.

- [ ] Die verschiedenen Modes (`MODE_ALL`, `MODE_SELECTION_CHANGED`, `MODE_BACKFILL_SERIES`) sind implementiert und werden an den gewünschten Stellen verwendet.

---

## 3. UI/Feed: Activity Feed und Library/StartScreen final einhängen

Ziel: Der Activity Feed und alle Telegram-UI-Teile sind nicht nur vorhanden, sondern auch **wirklich erreichbar und voll verdrahtet**.

Betroffene Dateien:

- `telegram/ui/TelegramSettingsViewModel.kt`
- `telegram/ui/feed/TelegramActivityFeedViewModel.kt`
- `telegram/ui/feed/TelegramActivityFeedScreen.kt`
- `ui/layout/FishTelegramContent.kt`
- `ui/home/StartScreen.kt`
- `ui/screens/LibraryScreen.kt`
- ggf. Navigation / Menü / Settings-UI

### 3.1 Activity-Feed sichtbar machen

- [ ] `TelegramActivityFeedScreen` ist im Navigationssystem registriert (NavGraph / Screen-Routing).
- [ ] Es existiert mindestens ein Einstiegspunkt:
  - [ ] z. B. ein Menüpunkt in den Settings namens „Telegram-Feed“ oder
  - [ ] ein Button/Tile im Home/Library-Bereich.
- [ ] `TelegramActivityFeedViewModel`:
  - [ ] Konsumiert `T_TelegramServiceClient.activityEvents`.
  - [ ] Verknüpft Feed-Einträge mit `TelegramContentRepository`-Daten (`MediaItem` etc.).
  - [ ] Liefert eine nicht-leere Liste, sobald Sync/Parsing gelaufen ist.

### 3.2 StartScreen

- [ ] `StartScreen` rendert eine Telegram-Row mit:
  - [ ] Daten aus `TelegramContentRepository.getTelegramVod()`.
  - [ ] `FishTelegramRow` + `FishTelegramContent`.
  - [ ] korrektem Focus-Verhalten auf TV (DPAD).
- [ ] Ein Click auf ein Telegram-MediaItem führt zu:
  - [ ] dem korrekten Detail-/Play-Flow (tg:// URL), nicht in einen toten Pfad.

### 3.3 LibraryScreen

- [ ] `LibraryScreen` hat einen „Telegram“-Bereich oder Tab gemäß `tdlibAgent.md`.
- [ ] Unterteilung:
  - [ ] „Filme“ → `getTelegramVod()`.
  - [ ] „Serien“ → `getTelegramSeries()`.
- [ ] Darstellung über `FishTelegramRow`.
- [ ] DPAD-Fokus und Scrollen funktionieren wie bei anderen Rows (Live/VOD/Xtream).

---

## 4. Logging & Log-Screen vollständig durchziehen

Ziel: Alle relevanten TDLib-Aktionen werden über `TelegramLogRepository` erfasst und der Log-Screen ist erreichbar.

Betroffene Dateien:

- `telegram/logging/TelegramLogRepository.kt`
- `telegram/ui/TelegramLogViewModel.kt`
- `telegram/ui/TelegramLogScreen.kt`
- `diagnostics/DiagnosticsLogger.kt`
- Core/Sync/Streaming/UI-Module (Hooks einbauen)
- Settings/Navigation (Einstiegspunkt)

### 4.1 Log-Screen verlinken

- [ ] In den Settings existiert ein klarer Menüeintrag „Telegram Log“.
- [ ] Dieser öffnet `TelegramLogScreen`.
- [ ] `TelegramLogViewModel` verwendet `TelegramLogRepository.entries`.
- [ ] Filter nach Level/Source funktionieren (falls vorhanden).

### 4.2 Logging aus allen Modulen

Für jeden Cluster:

- [ ] Core (`T_TelegramServiceClient`, `T_TelegramSession`, `T_ChatBrowser`, `T_TelegramFileDownloader`):
  - [ ] Wichtige Events (Auth-Wechsel, Verbindungsstatus, kritische Fehler, Downloadstart/-ende) loggen über `TelegramLogRepository`.

- [ ] Sync (`TelegramSyncWorker`, `TelegramContentRepository`):
  - [ ] Sync-Start, -Ende, Anzahl neuer Items, Fehler loggen.

- [ ] Streaming (`TelegramDataSource`):
  - [ ] Stream-Start, -Ende, IO-Fehler loggen.

- [ ] UI/Feed (`TelegramSettingsViewModel`, `TelegramActivityFeedViewModel`, StartScreen/Library-Screen):
  - [ ] wichtige Nutzeraktionen (z. B. „Telegram aktiviert“, „Chats ausgewählt“, „Feed geöffnet“) loggen.

Log-Struktur:

- [ ] alle Log-Einträge enthalten sinnvoll:
  - Timestamp
  - Level
  - Source/Tag
  - Message
  - optional Details/Throwable

---

## 5. Gradle & CI: finaler Zustand

Ziel: Der Build ist minimal, sauber, reproduzierbar und durch CI gut abgesichert.

### 5.1 Gradle-Dependencies & TDLib

- [ ] `dev.g000sha256:tdl-coroutines-android` ist genau einmal und mit gewünschter Version eingebunden.
- [ ] Es gibt keine aktiven Reste von alten libtd/`libtdjni.so` in `b/libtd` o. ä.
- [ ] ProGuard/R8 schützt die relevanten TDLib-Typen (`dev.g000sha256.tdl.dto.*` etc.), sodass Minify die App nicht bricht.

### 5.2 Quality-Tools

- [ ] LeakCanary ist in `debug`-Build eingebunden, nicht in `release`.
- [ ] `kotlinx-coroutines-debug` wird nur in Debug aktiviert (z. B. in `App.kt` mit `System.setProperty("kotlinx.coroutines.debug","on")` im Debug-Build).
- [ ] `androidx.profileinstaller` ist eingebunden, damit TV-Startzeiten optimiert werden.
- [ ] `kover` ist aktiv und kann Coverage für TDLib-Module erzeugen.

### 5.3 CI-Workflow

- [ ] Es existiert mindestens ein CI-Workflow (z. B. `.github/workflows/android-ci.yml`), der bei `pull_request` auf `main` läuft und:
  - [ ] `./gradlew assembleDebug`
  - [ ] `./gradlew testDebugUnitTest`
  - [ ] `./gradlew detekt`
  - [ ] `./gradlew ktlintCheck`
  ausführt.

- [ ] Der CI-Workflow ist im README oder einem CI-Dokument kurz erklärt.

---

## 6. Testsuite – Abdeckung prüfen und ergänzen

Ziel: Zentrale TDLib-Flows sind durch Tests abgedeckt.

- [ ] Es existieren Unit-/Integrationstests für:
  - [ ] Parsing von Telegram-Messages (MediaParser + Heuristik).
  - [ ] Repository-Schicht (`TelegramContentRepository` – Einfügen, Duplikate, Queries).
  - [ ] Sync-Verhalten (Worker-Modus, Auswahl vs. Vollsync).
  - [ ] DataSource (`TelegramDataSource` – open/read/close, Fehlerfall).
  - [ ] UI-States (mindestens Smoke-Tests oder Screenshot-/Compose-Tests für Settings/Feed/Start/Library, falls sinnvoll).
  - [ ] Logging (z. B. tests, die prüfen, dass bestimmte Aktionen einen Log-Eintrag erzeugen – falls Logger injizierbar).

- [ ] Fehlen diese Tests, wurden sie im Rahmen dieses Tasks ergänzt.

---

## 7. Doku-Update: tdlibAgent.md auf den finalen Stand bringen

Ziel: `.github/tdlibAgent.md` muss den finalen Implementierungszustand korrekt widerspiegeln.

- [ ] In `.github/tdlibAgent.md` wurde pro Cluster kenntlich gemacht:
  - [ ] welche Teile **vollständig implementiert** sind,
  - [ ] welche Tests sie absichern (Test-Dateinamen, Pakete),
  - [ ] welche CI-Jobs sie prüfen.

- [ ] Es wurde ein Abschnitt `Deviations & Rationale` ergänzt:
  - [ ] Alle kleinen Abweichungen von der ursprünglichen Architektur/SOLL sind dort kurz und verständlich begründet.

- [ ] Verweise auf:
  - [ ] `docs/TDLIB_TASK_GROUPING.md`
  - [ ] `docs/TDLIB_FINAL_REVIEW.md`
  - [ ] relevante CI-Workflows
  - [ ] Logging-/Feed-/Logscreen-Doku

---

## 8. Abschlusscheck

Diese Checkliste darf erst komplett umgestellt werden, wenn ALLE obenstehenden Punkte erfüllt sind:

- [ ] Alle Legacy-TDLib-Klassen sind entweder gelöscht oder klar als ungenutztes Legacy markiert.
- [ ] Settings ↔ Sync ↔ Repository ↔ Feed/Rows sind vollständig verdrahtet und getestet.
- [ ] Activity Feed ist sichtbar und funktionsfähig.
- [ ] Log-Screen ist sichtbar und zeigt echte Log-Einträge.
- [ ] Logging durchzieht alle TDLib-Relevanten Module.
- [ ] Gradle und TDLib-Dependencies sind sauber und frei von alten Leichen.
- [ ] CI-Workflow existiert, läuft und schlägt bei Fehlern an.
- [ ] Tests existieren für alle kritischen Flows.
- [ ] `.github/tdlibAgent.md` ist aktualisiert und korrekt.
- [ ] `feature/tdlib-final-review-and-polish` wurde erfolgreich in `main` gemerged (mit Merge-Commit) und alle Checks sind grün.