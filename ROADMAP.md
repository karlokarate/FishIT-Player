#
# m3uSuite – **Roadmap: Import/Export finalisieren**

Zielbild:
- **Schnell‑Import** ausschließlich im **Erststart‑Screen (`setup`)**.
- **Settings‑Export/Import** in **Settings** (manuell + optional Auto‑Backup).
- **Google‑Drive** (optional): Login + Zielordner wählen + Upload/Download.
- Nach **Import**: automatischer **Refresh** (M3U/Xtream) und Worker‑Scheduling.

---

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
  - Navigation zu `gate`.

### 1.2 Home (`StartScreen.kt`)
- **Schnell‑Import‑Karte entfernen** (nicht mehr angezeigt).

### 1.3 Settings (`SettingsScreen.kt`)
Abschnitt **„Daten & Backup“** ergänzen:
- **Settings‑Export**  
  - Buttons: „Als Datei exportieren …“ (SAF), „Zu Drive exportieren“ (sofern angemeldet).
- **Settings‑Import**  
  - Buttons: „Aus Datei importieren …“, „Aus Drive importieren“.
- **Drive‑Einstellungen**  
  - Status (angemeldet/abgemeldet, Konto), „Bei Google anmelden/abmelden“, Zielordner wählen.  
  - Toggle: „Automatisches Backup nach Änderungen“ (optional Phase 2).

---

## 2) Implementierungsschritte (Schritt‑für‑Schritt)

### 2.1 Settings‑Serialisierung
- Neues Paket `settings/` mit `SettingsBackup.kt`:
  - `suspend fun export(context): SettingsSnapshot` (liest alle Keys aus `SettingsStore`)
  - `suspend fun import(context, snapshot: SettingsSnapshot)` (schreibt Werte zurück; migriert alte Keys).
  - `data class SettingsSnapshot(v: Int = 1, prefs: Map<String, Any?>, profiles?: List<Profile>, kidAllow?: Map<Long, Map<String, List<Long>>>>)`
  - Verschlüsselungs‑Helper (optional): `SecretHelper` (AES/GCM mit Keystore).

### 2.2 Dateiexport/-import (lokal, SAF)
- Utility `FilePickers.kt`: `createDocument()`, `openDocument()` Intents mit `application/json`.
- In `SettingsScreen` die Buttons verdrahten; Progress/Fehler‑Snackbars; Neustart‑Hinweis wenn nötig.

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
  - „Schnell‑Import“ Abschnitt (Drive + Lokal) einbauen.
  - Nach erfolgreichem Import: **M3U/EPG/UA/Referer** gesetzt? → **Import anstoßen** (Xtream/M3U) → Worker planen → `onDone()`.
- `MainActivity` bleibt unverändert (Startziel `setup` wenn `m3uUrl` leer).

### 2.5 Post‑Import Aktionen
- `XtreamRepository.configureFromM3uUrl()` aufrufen, wenn nur M3U im Snapshot steht (Host/Port/User/Pass setzen).
- `XtreamRefreshWorker.schedule(...)` + `XtreamEnrichmentWorker.schedule(...)` immer auslösen.
- Optional: **Snackbar/Toast** „Import erfolgreich – Inhalte werden aktualisiert“.

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

## 6) Aufgabenliste (Checkliste)

- [ ] `StartScreen`: Schnell‑Import entfernen.
- [ ] `PlaylistSetupScreen`: Drive/Lokal **Import** einbauen (Erststart).
- [ ] `SettingsScreen`: **Export**, **Import**, **Drive‑Einstellungen** ergänzen.
- [ ] `settings/SettingsBackup.kt` + `FilePickers.kt` implementieren.
- [ ] (Optional) `drive/DriveAuth.kt` + `drive/DriveClient.kt` + Gradle‑Deps.
- [ ] (Optional) `work/SettingsAutoBackupWorker.kt`.
- [ ] Unit‑ & UI‑Tests ergänzen.
- [ ] AGENTS.md & ROADMAP.md im Repo aktualisieren.
 
---

## Status‑Notizen (ungeprüft)

- EPG/Now‑Next
  - Vor Änderung: nicht funktionierend (keine/instabile Now/Next‑Daten bei M3U‑Only Setups).
  - Nach Änderung: Auto‑Erkennung & Speicherung von Xtream‑Daten + `streamId` bei M3U‑Import (Helfer `XtreamDetect`, `M3UParser` setzt `streamId` für Live) – zu testen durch Nutzer.
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
