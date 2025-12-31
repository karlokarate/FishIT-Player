# Xtream Pipeline & Playback Blocker Analyse

## Zusammenfassung

Komplette Analyse der Xtream-Pipeline und Playback-Architektur zur Identifizierung von Flags, Checks und Bedingungen, die das Abspielen von Xtream-Inhalten verhindern könnten.

---

## ⚠️ KRITISCHER BLOCKER IDENTIFIZIERT

**Ort:** `playback/xtream/XtreamPlaybackSourceFactoryImpl.kt:144-148`

```kotlin
// Verify session is initialized
if (xtreamApiClient.capabilities == null) {
    throw PlaybackSourceException(
        message = "Xtream session not initialized. Please connect to Xtream first.",
        sourceType = SourceType.XTREAM
    )
}
```

**Auswirkung:** ALLE Xtream-Playback-Versuche schlagen fehl, wenn die Session nicht initialisiert ist.

---

## 🎯 Vollständige Liste der Playback-Blocker

### 1. **Session Nicht Initialisiert** (SCHWERE: KRITISCH ⛔)

**Was wird geprüft:**
- `XtreamApiClient.capabilities == null`

**Wann tritt es auf:**
- App startet ohne gespeicherte Zugangsdaten
- Gespeicherte Zugangsdaten sind ungültig/abgelaufen
- `XtreamSessionBootstrap.start()` schlägt fehl
- Benutzer meldet sich manuell ab

**Lösung:**
- `XtreamApiClient.initialize(config)` muss erfolgreich sein
- Initialisierung erfolgt über:
  1. `XtreamSessionBootstrap.start()` (App-Start) ODER
  2. `XtreamAuthRepositoryAdapter.initialize()` (Onboarding/Login)

**Initialisierungs-Ablauf:**
```
Gespeicherte Credentials
    ↓
XtreamCredentialsStore.read()
    ↓
XtreamApiClient.initialize(config)
    ↓
    ├─ Port-Auflösung
    ├─ API-Endpoint-Discovery  
    ├─ Credential-Validierung
    └─ Capability-Caching
    ↓
capabilities gesetzt ✅
    ↓
SourceActivationStore.setXtreamActive()
    ↓
Playback bereit 🎬
```

### 2. **Fehlende PlaybackContext Extras** (SCHWERE: HOCH ⚠️)

**Erforderliche Keys:**
- `contentType`: "live" | "vod" | "series"
- Content-ID: `streamId` ODER `vodId` ODER `seriesId`
- Für Serien: `episodeId` (bevorzugt) ODER `seasonNumber` + `episodeNumber`

**Wann tritt es auf:**
- Navigation von der UI ohne korrekte Extras
- Fehlende `playbackHints` aus der Pipeline
- Fallback-sourceId-Parsing schlägt fehl

**Aktuelle Implementation:**
- `PlayerNavViewModel.buildExtrasForSource()` behandelt dies
- Priorität: `playbackHints` aus Pipeline > Fallback-Parsing

### 3. **Unsichere Prebuilt-URI Ablehnung** (SCHWERE: MITTEL 🔒)

**Sicherheits-Checks:**
- Lehnt URIs mit userinfo ab (`user:pass@host`)
- Lehnt URIs mit `username=` oder `password=` Query-Parametern ab
- Lehnt Xtream-Credential-Pfade ab: `/live/{user}/{pass}/`, `/movie/{user}/{pass}/`
- Konservative False-Positives sind akzeptabel (Sicherheit > Kompatibilität)

**Ort:** `XtreamPlaybackSourceFactoryImpl.kt:192-256 (isSafePrebuiltXtreamUri())`

**Lösung:**
- Verwende session-abgeleiteten Pfad (primär)
- ODER sichere CDN-URLs ohne Credentials

### 4. **Nicht Unterstütztes Ausgabeformat** (SCHWERE: NIEDRIG ⚡)

**Format-Policy:** `m3u8 > ts > mp4`

**Wann tritt es auf:**
- Server `allowedOutputFormats` enthält nicht m3u8/ts/mp4
- `containerExtension` enthält Nicht-Streaming-Format (mkv, avi)

**Format-Auswahl (Priorität):**
1. Policy-basierte Auswahl aus `allowedOutputFormats` (ZUVERLÄSSIGSTE)
2. Explizite `containerExtension` NUR wenn TRUE streaming format (`m3u8`, `ts`)
3. XtreamApiClient-Defaults (m3u8)

**WICHTIG:**
- `containerExtension` aus VOD-Metadaten beschreibt die DATEI, nicht das Streaming-Output
- `mp4` wird NUR aus `allowedOutputFormats` akzeptiert
- Aus `containerExtension` werden nur `m3u8` und `ts` akzeptiert

### 5. **DI Factory Nicht Registriert** (SCHWERE: THEORETISCH ✅)

**Status:** VERIFIZIERT FUNKTIONIEREND ✅

**Verifikation:**
- `XtreamPlaybackModule` korrekt konfiguriert mit `@Module`, `@InstallIn`, `@Binds`, `@IntoSet`
- Modul in `app-v2/build.gradle.kts` enthalten
- Folgt dem kanonischen `@Multibinds`-Pattern aus AGENTS.md

**Aktueller Status:** KEIN BLOCKER

### 6. **Player Source Resolver Fallback** (SCHWERE: INFO ℹ️)

**Verhalten bei Factory-Fehler:**
1. Fängt Exception von Factory ab
2. Versucht Fallback-Auflösung
3. Nutzt URI direkt wenn HTTP(S)
4. Fällt zurück auf Test-Stream (Big Buck Bunny) wenn keine gültige URI

**Auswirkung:** Kann Fehler maskieren durch Anzeige von Test-Content statt Fehler

---

## 📊 Session-Initialisierungs-Status

### Initialisierungs-Pfade

**Pfad 1: App-Start (XtreamSessionBootstrap)**
```
App-Start
    ↓
XtreamSessionBootstrap.start()
    ↓
XtreamCredentialsStore.read()
    ↓
    ┌─────────────────────┐
    │ Credentials vorhanden? │
    └────┬──────────┬──────┘
         │ JA       │ NEIN
         ↓          ↓
    initialize()  Inactive
         │
         ↓
    ┌──────────┐
    │ Erfolg?  │
    └─┬──────┬─┘
      │ JA   │ NEIN
      ↓      ↓
    Active  Inactive (INVALID_CREDENTIALS)
```

**Pfad 2: Benutzer-Login (XtreamAuthRepositoryAdapter)**
```
Onboarding / Login
    ↓
Benutzer gibt Credentials ein
    ↓
XtreamAuthRepository.initialize(config)
    ↓
XtreamApiClient.initialize()
    ↓
    ┌──────────┐
    │ Erfolg?  │
    └─┬──────┬─┘
      │ JA   │ NEIN
      ↓      ↓
  Credentials    Fehler-UI
  speichern          ↓
      ↓          Retry/Cancel
    Active
```

---

## 🎯 Wahrscheinlichster Real-World-Blocker

### Session-Ablauf oder ungültige Credentials nach App-Neustart

**Szenario:**
1. Benutzer meldet sich erfolgreich an
2. Credentials gespeichert, Session initialisiert
3. App wird beendet oder Gerät startet neu
4. App startet → `XtreamSessionBootstrap` läuft
5. Server lehnt gespeicherte Credentials ab (abgelaufen/Passwort geändert)
6. `capabilities` bleibt null
7. Benutzer versucht Content abzuspielen → "session not initialized" Fehler

**Lösung:**
- Fehlgeschlagene Auto-Init erkennen und Re-Login-Prompt zeigen
- Xtream-Content nicht in UI anzeigen wenn Session inaktiv
- Manuellen "Neu verbinden"-Button in Einstellungen/Account-Screen hinzufügen

---

## 💡 Empfehlungen

### 1. **Pre-Flight Check in UI hinzufügen** (HOHE PRIORITÄT)

```kotlin
// In PlayerNavViewModel.load() oder DetailScreen
if (sourceType == SourceType.XTREAM && xtreamApiClient.capabilities == null) {
    // Zeige: "Verbinde mit Xtream neu..."
    // Trigger: XtreamSessionBootstrap Re-Initialisierung
    // ODER: Weiterleitung zu Login
}
```

### 2. **Verbesserte Fehlermeldungen** (MITTLERE PRIORITÄT)

**Aktuell:** "Xtream session not initialized. Please connect to Xtream first."

**Besser:**
- "Ihre Xtream-Session ist abgelaufen. Bitte melden Sie sich erneut an."
- "Xtream-Server nicht erreichbar. Prüfen Sie Ihre Verbindung."
- "Keine Xtream-Session gefunden. Bitte konfigurieren Sie Ihr Konto."

### 3. **Diagnostics-Screen hinzufügen** (NIEDRIGE PRIORITÄT)

**Anzeige:**
- Session-Status (capabilities != null)
- Auth-Status (Authenticated, Failed, Expired)
- Connection-Status (Connected, Disconnected, Error)
- Factory-Registrierungen (Anzahl)
- Source-Aktivierungs-Status

### 4. **Lazy Re-Initialisierung erwägen** (ZUKUNFT)

Wenn Playback mit "session not initialized" fehlschlägt:
1. Prüfe gespeicherte Credentials
2. Falls vorhanden → versuche stille Re-Initialisierung
3. Zeige Loading-Status während Init
4. Wiederhole Playback bei Erfolg
5. Zeige Login-Prompt bei Fehler

---

## 🔧 Diagnose-Befehle

**Session-Status prüfen:**
```kotlin
val isReady = xtreamApiClient.capabilities != null
val authState = xtreamApiClient.authState.value
val connectionState = xtreamApiClient.connectionState.value
```

**Factory-Registrierung prüfen:**
```kotlin
// In PlaybackSourceResolver
val factoryCount = factoryCount() // Sollte >= 1 sein
val canHandleXtream = canResolve(SourceType.XTREAM)
```

**Source-Aktivierung prüfen:**
```kotlin
val snapshot = sourceActivationStore.snapshot.value
val xtreamActive = snapshot.xtream is SourceActivationState.Active
```

---

## ✅ Was korrekt funktioniert

- [x] Xtream Playback-Modul korrekt via Hilt DI registriert
- [x] Session-Bootstrap bei App-Start implementiert
- [x] Credential-Persistierung funktioniert
- [x] Source-Aktivierungs-Tracking vorhanden
- [x] PlaybackContext-Konstruktion mit korrekten Extras
- [x] Factory-Auswahl via PlaybackSourceResolver
- [x] Sicherheits-Checks für Credential-URIs
- [x] Format-Auswahl mit Policy-Priorität
- [x] Fallback-Resolution für Edge-Cases

---

## 📝 Zusammenfassung

**Primärer Blocker:**
- `XtreamApiClient.capabilities == null` Check in Playback-Factory

**Grundursachen:**
1. Keine gespeicherten Credentials (erste Ausführung)
2. Gespeicherte Credentials ungültig/abgelaufen
3. Netzwerk-Fehler während Auto-Initialisierung
4. Manuelle Abmeldung durch Benutzer

**Fix-Strategie:**
- UI-Level Session-Check vor Navigation zum Player hinzufügen
- Passende Fehler-/Reconnect-UI anzeigen wenn Session inaktiv
- Lazy Re-Initialisierung für abgelaufene Sessions erwägen

---

## 🏗️ Architektur-Übersicht

### Playback-Flow

```
UI (PlayerNavViewModel)
    │
    ├─ buildPlaybackContext(sourceType: SourceType.XTREAM)
    │   ├─ canonicalId: "xtream:vod:12345"
    │   ├─ extras: Map<String, String>
    │   │   ├─ contentType: "vod"
    │   │   ├─ vodId: "12345"
    │   │   └─ containerExtension: "mkv"
    │   └─ uri: null (Factory builds URL)
    │
    ↓
Player (PlaybackSourceResolver)
    │
    ├─ factories.find { it.supports(SourceType.XTREAM) }
    │   └─ XtreamPlaybackSourceFactoryImpl via @IntoSet DI
    │
    ↓
XtreamPlaybackSourceFactoryImpl.createSource(context)
    │
    ├─ ⚠️ CHECK: xtreamApiClient.capabilities != null
    │   └─ FAIL → throw PlaybackSourceException
    │
    ├─ URL-Building Path Selection:
    │   ├─ Secondary: Safe prebuilt URI (if context.uri != null)
    │   │   └─ isSafePrebuiltXtreamUri() validation
    │   └─ Primary: Session-derived URL
    │       ├─ Extract contentType, vodId/streamId/seriesId
    │       ├─ Resolve output extension:
    │       │   ├─ Priority 1: allowedOutputFormats → selectXtreamOutputExt()
    │       │   ├─ Priority 2: containerExtension (only m3u8/ts)
    │       │   └─ Priority 3: XtreamApiClient defaults
    │       └─ Build URL:
    │           ├─ buildLiveUrl(streamId, ext)
    │           ├─ buildVodUrl(vodId, ext)
    │           └─ buildSeriesEpisodeUrl(seriesId, season, episode, ext)
    │
    ↓
PlaybackSource
    ├─ uri: "http://server:port/movie/user/pass/12345.m3u8"
    ├─ headers: Map<String, String> (with Referer, User-Agent)
    ├─ mimeType: "application/x-mpegURL"
    └─ dataSourceType: DEFAULT
    │
    ↓
Media3/ExoPlayer
    └─ Playback 🎬
```

### Session-Initialisierung

```
┌─────────────────────────────────────────────┐
│ App-Start                                   │
└────────────┬────────────────────────────────┘
             ↓
┌─────────────────────────────────────────────┐
│ XtreamSessionBootstrap.start()             │
│   - Singleton, runs once per app process   │
│   - Launched in APP_LIFECYCLE_SCOPE        │
└────────────┬────────────────────────────────┘
             ↓
┌─────────────────────────────────────────────┐
│ XtreamCredentialsStore.read()              │
│   - Encrypted storage via DataStore        │
└────────────┬────────────────────────────────┘
             │
         ┌───┴────┐
         │ null?  │
         └┬──────┬┘
      YES │      │ NO
          │      │
          ↓      ↓
     Inactive    XtreamApiClient.initialize(config)
                 │
                 ├─ XtreamDiscovery.discoverCapabilities()
                 │   ├─ Port resolution (if needed)
                 │   ├─ VOD endpoint alias discovery
                 │   └─ Feature detection
                 │
                 ├─ getServerInfo() validation
                 │   └─ Credential check
                 │
                 └─ Cache capabilities
                 │
             ┌───┴────┐
             │Success?│
             └┬──────┬┘
          YES │      │ NO
              │      │
              ↓      ↓
         capabilities    Error
            != null      │
              │          ↓
              ↓      SourceActivationStore
     SourceActivationStore    .setXtreamInactive(
      .setXtreamActive()       INVALID_CREDENTIALS)
              │
              ↓
         🎬 Playback Ready
```

---

**Erstellt:** 2025-12-31  
**Version:** 1.0  
**Status:** Vollständige Analyse abgeschlossen
