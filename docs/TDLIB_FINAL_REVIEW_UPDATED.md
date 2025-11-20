# TDLib Final Review – Wiring, Cleanup, CI, Quality (UPDATED – based on actual IST)

Diese Datei wurde **aktualisiert**, basierend auf dem tatsächlichen Stand des Repos aus ZIP (5).  
Alle **bereits erledigten Punkte sind auf [x] gesetzt**, echte Lücken bleiben auf [ ].

Diese MD wird so lange wiederverwendet, bis **alle** Checkboxes gesetzt sind.  
Copilot MUSS jede einzelne Checkbox bewusst setzen, erst wenn die jeweilige Aufgabe vollständig erfüllt ist.

---

## 0. Branch & Referenzen

**Pflicht:**

- [ ] Arbeiten erfolgen ausschließlich in  
      `feature/tdlib-final-review-and-polish`

- [ ] Vor Beginn:  
      ```
      git checkout main
      git pull origin main
      git checkout feature/tdlib-final-review-and-polish
      git merge main
      ```

- [x] `.github/tdlibAgent.md` geprüft  
- [x] `docs/TDLIB_TASK_GROUPING.md` geprüft  

---

## 1. Legacy-TDLib-Code aufräumen

Legacy-Dateien:

- `telegram/session/TelegramSession.kt`
- `telegram/browser/ChatBrowser.kt`
- `telegram/downloader/TelegramFileDownloader.kt`
- `telegram/ui/TelegramViewModel.kt`

### Aufgaben:

- [ ] Sicherstellen, dass diese Klassen **nirgendwo produktiv genutzt werden**
- [ ] Falls sie noch genutzt werden → vollständig ersetzen durch neue Core-Klassen
- [ ] Für jede Datei:
  - [ ] ENTWEDER löschen  
  - [ ] ODER `@Deprecated("Legacy – do not use")` + Kommentar „LEGACY/UNUSED“

**Status IST:** Noch nicht bereinigt.

---

## 2. Settings ↔ Sync Wiring schließen

Ziel laut Spezifikation aus `.github/tdlibAgent.md`:  
Chat-Auswahl und Aktivierung von Telegram müssen **immer** einen passenden Sync auslösen.

### Aufgaben:

### 2.1 Sync-Scheduling aus Settings

- [x] `TelegramSettingsViewModel`:  
      Nach Änderung der Chat-Auswahl →  
      `SchedulingGateway.scheduleTelegramSync(...MODE_SELECTION_CHANGED...)`

- [x] Optional: Bei erster Aktivierung von Telegram →  
      `scheduleTelegramSync(...MODE_ALL...)`

- [x] SettingsStore-Keys werden korrekt gelesen & geschrieben

### 2.2 Worker-Verhalten

- [x] Worker liest Settings
- [x] Worker ruft `ensureStarted()` auf
- [x] Worker nutzt `T_ChatBrowser`
- [x] Worker nutzt Parser + Heuristik
- [x] Worker persistiert Inhalte
- [x] Worker-Modes vollständig implementiert

**Status:** Abgeschlossen. Settings-Seite triggert jetzt korrekt sync bei Chat-Auswahl-Änderung und erster Aktivierung.

---

## 3. UI/Feed: Activity Feed & Start/Library

### 3.1 Activity-Feed

- [x] Screen existiert (`TelegramActivityFeedScreen`)
- [x] ViewModel existiert (`TelegramActivityFeedViewModel`)
- [x] Navigation ist registriert
- [x] Settings-Button verlinkt korrekt
- [x] Feed zeigt echte Daten

### 3.2 StartScreen

- [x] Telegram-Reihe sichtbar
- [x] Datenquelle = `getTelegramVod()`
- [x] Playback via `tg://` funktioniert

### 3.3 LibraryScreen

- [x] Telegram-Content sichtbar
- [ ] Tabs „Filme/Serien“ existieren → **NICHT** exakt wie in Spec  
      → Entscheiden:
      - [ ] Tabs implementieren  
      - [ ] Oder Abweichung in Spezifikation dokumentieren

---

## 4. Logging & Log-Screen

### 4.1 Log-Screen

- [x] `TelegramLogScreen` erreichbar über Settings
- [x] ViewModel korrekt verdrahtet
- [x] Ausgabe-Liste funktioniert

### 4.2 Logging in Modulen

Aktueller Stand:

- Core (Session/Browser/ServiceClient) nutzt Logging **vollständig** (T_TelegramFileDownloader ergänzt)
- Sync nutzt Logging **vollständig**
- Streaming (`TelegramDataSource`) loggt **vollständig**
- UI/Feed loggt **vollständig** (TelegramActivityFeedViewModel ergänzt)
- Snackbars/Overlays aus `TelegramLogRepository.events` fehlen

### Aufgaben:

- [x] Core-Module auf `TelegramLogRepository` umstellen
- [x] Sync-Module vollständig loggen
- [x] Streaming-Module vollständig loggen
- [x] UI/Feed-Module loggen Nutzeraktionen/Ereignisse
- [ ] Snackbar/Overlay-Mechanik für WARN/ERROR implementieren

---

## 5. Gradle & CI: final

### 5.1 Dependencies

- [x] tdl-coroutines korrekt eingebunden
- [x] ProGuard/R8-Regeln für `dev.g000sha256.tdl.dto.*` prüfen & ergänzen (bereits vorhanden)
- [x] keine alten native libs aktiv

### 5.2 Quality Tools

- [x] LeakCanary (debug)
- [x] coroutine-debug (debug)
- [x] profileinstaller
- [x] Kover

### 5.3 CI

- [x] PR-CI-Workflow erstellen:
  - assembleDebug
  - testDebugUnitTest
  - detekt
  - ktlintCheck
- [ ] README/CI-Doku ergänzen (optional)

---

## 6. Testsuite – Abdeckung

Existierende Tests:

- [x] MediaParserTest
- [x] TgContentHeuristicsTest
- [x] DiagnosticsLoggerTest
- [x] PerformanceMonitorTest
- [x] TvKeyDebouncerTest

Fehlt:

- [ ] Repository-Tests (`TelegramContentRepository`)
- [ ] Worker-Tests (`TelegramSyncWorker`)
- [ ] DataSource-Tests (`TelegramDataSource`)
- [ ] Logging-Tests
- [ ] UI-Smoketests (Compose)
- [ ] Fehlerpfad-Tests (Auth, Network, Parsing-Errors)

---

## 7. Dokumentation aktualisieren

- [ ] In `.github/tdlibAgent.md` markieren:
  - implementierte Module
  - vorhandene Tests
  - CI-Jobs
- [ ] Abschnitt „Deviations & Rationale“ hinzufügen
- [ ] Feed/Library-Tab-Entscheidung dokumentieren

---

## 8. Abschlusskriterien

Diese Datei ist erst vollständig, wenn:

- [ ] Legacy-Code entfernt oder markiert wurde
- [ ] Settings ↔ Sync korrekt verdrahtet ist
- [ ] Feed/Library vollständig finalisiert ist
- [ ] Logging überall integriert ist
- [ ] Events zeigen Overlays
- [ ] Gradle final sauber ist
- [ ] CI für PRs existiert und läuft
- [ ] Testsuite vervollständigt wurde
- [ ] Doku aktualisiert wurde
- [ ] Branch `feature/tdlib-final-review-and-polish` erfolgreich in `main` gemerged wurde
