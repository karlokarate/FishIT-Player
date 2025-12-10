# FishIT Pipeline CLI

CLI-Tool zum Testen der Telegram- und Xtream-Pipelines ohne Android-UI.

## Schnellstart

### Option A: Neue Telegram-Session erstellen (Empfohlen)

```bash
# 1. API Credentials setzen (von https://my.telegram.org/apps)
export TG_API_ID="12345678"
export TG_API_HASH="your_api_hash_here"

# 2. Interaktive Authentifizierung starten
./fishit-cli tg auth --phone +491234567890

# 3. SMS/Telegram-Code eingeben wenn gefragt
# 4. Ggf. 2FA-Passwort eingeben
# 5. Fertig! Session ist in ~/.tdlib-session gespeichert
```

### Option B: Setup-Wizard (für Xtream oder bestehende Session)

```bash
# Setup starten (interaktive Konfiguration)
source ./tools/fishit-cli-setup.sh
```

Der Wizard fragt nach:

- **Telegram**: API ID, API Hash, Session-Pfad
- **Xtream**: Provider URL, Username, Password

Die Konfiguration wird in `~/.fishit-cli-config` gespeichert.

### 3. CLI verwenden

```bash
# Hilfe anzeigen
./fishit-cli --help

# Telegram-Status prüfen
./fishit-cli tg status

# Telegram-Chats auflisten
./fishit-cli tg list-chats

# Xtream VOD-Katalog anzeigen
./fishit-cli xtream list-vod --limit 10
```

---

## Installation

### Voraussetzungen

- **Java 17+** (JDK)
- **Android SDK** (für Gradle-Build)
- **TDLib Native Libraries** (für Telegram - im Codespace vorinstalliert)

### Variante A: Aus dem Projekt-Repository

```bash
# Repository klonen
git clone https://github.com/karlokarate/FishIT-Player.git
cd FishIT-Player

# Setup ausführen
source ./tools/fishit-cli-setup.sh

# CLI starten
./fishit-cli --help
```

### Variante B: Aus dem ZIP-Bundle

```bash
# ZIP entpacken
unzip fishit-pipeline-cli-*.zip
cd fishit-cli

# Setup ausführen
source ./fishit-cli-setup.sh

# CLI starten
./fishit-cli --help
```

---

## Konfiguration

### Umgebungsvariablen

Die CLI liest Konfiguration aus Umgebungsvariablen:

#### Telegram

| Variable | Beschreibung | Beispiel |
|----------|--------------|----------|
| `TG_API_ID` | Telegram API ID | `12345678` |
| `TG_API_HASH` | Telegram API Hash | `abcdef1234567890...` |
| `TG_SESSION_PATH` | Pfad zur TDLib-Session | `~/.tdlib-session` |

#### Xtream

| Variable | Beschreibung | Beispiel |
|----------|--------------|----------|
| `XTREAM_URL` | Provider Base URL | `http://provider.com:8080` |
| `XTREAM_USERNAME` | Benutzername | `user123` |
| `XTREAM_PASSWORD` | Passwort | `pass456` |

### Manuelle Konfiguration

```bash
# Telegram
export TG_API_ID="12345678"
export TG_API_HASH="your_api_hash_here"
export TG_SESSION_PATH="$HOME/.tdlib-session"

# Xtream
export XTREAM_URL="http://provider.com:8080"
export XTREAM_USERNAME="username"
export XTREAM_PASSWORD="password"
```

### Konfigurationsdatei

Die Setup-Wizard speichert die Konfiguration in `~/.fishit-cli-config`.
Diese kann manuell editiert oder mit `source ~/.fishit-cli-config` geladen werden.

---

## Befehle

### Telegram-Befehle

```bash
# Interaktive TDLib-Authentifizierung (NEU!)
./fishit-cli telegram auth --session-dir ~/.fishit-cli/telegram

# Verbindungsstatus prüfen
./fishit-cli telegram status

# Verfügbare Chats auflisten
./fishit-cli telegram list-chats [--limit N]

# Media-Samples aus einem Chat
./fishit-cli telegram sample-media --chat-id <ID> [--limit N]
```

#### Telegram Auth-Flow

Der `auth`-Befehl führt durch den vollständigen TDLib-Authentifizierungsprozess:

1. **TDLib initialisieren** - Lädt die native Library und erstellt den Client
2. **Telefonnummer eingeben** - Internationale Nummer mit Ländervorwahl
3. **Verifizierungscode eingeben** - SMS oder Telegram-App Code
4. **2FA-Passwort** (falls aktiviert) - Two-Factor Authentication

```bash
# Beispiel Auth-Session
$ ./fishit-cli telegram auth --session-dir ~/.fishit-cli/telegram

🔑 Starte Telegram-Authentifizierung...
📁 Session-Verzeichnis: /home/user/.fishit-cli/telegram

⏳ TDLib initialisiert. Warte auf Auth-State...
📱 Bitte Telefonnummer eingeben (z.B. +491701234567): +49170...
📨 Verifizierungscode wurde gesendet. Bitte eingeben: 12345
✅ Erfolgreich eingeloggt!
📁 Session gespeichert in: /home/user/.fishit-cli/telegram

# Nach erfolgreichem Login bleiben die Session-Daten gespeichert
# Weitere Befehle nutzen die existierende Session automatisch
```

### Xtream-Befehle

```bash
# Verbindungsstatus prüfen
./fishit-cli xtream status

# VOD-Katalog anzeigen
./fishit-cli xtream list-vod [--limit N]

# Serien-Katalog anzeigen
./fishit-cli xtream list-series [--limit N]

# Live-Kanäle anzeigen
./fishit-cli xtream list-live [--limit N]
```

### Meta-Befehle

```bash
# Alle Pipelines testen
./fishit-cli meta test-all

# Pipeline-Flow anzeigen
./fishit-cli meta flow

# Watch-Mode (kontinuierliche Updates)
./fishit-cli meta watch
```

---

## Telegram-Session erstellen

Um Telegram zu nutzen, benötigst du eine authentifizierte TDLib-Session.

### Option 1: Aus bestehender Telegram-App exportieren

Falls du bereits eine Desktop-App mit TDLib nutzt:

```bash
# Session-Verzeichnis kopieren
cp -r /path/to/existing/tdata ~/.tdlib-session
```

### Option 2: Session über CLI erstellen (erfordert Interaktion)

```bash
# 1. TDLib-Tools installieren (Linux)
sudo apt install telegram-cli

# 2. Session erstellen
telegram-cli --phone +49123456789
# Code eingeben, ggf. 2FA-Passwort

# 3. Session-Verzeichnis verwenden
export TG_SESSION_PATH="$HOME/.telegram-cli"
```

### Option 3: Codespaces (GitHub Secret)

Im Codespace kann die Session als Base64-Secret importiert werden:

1. Session auf lokalem Rechner erstellen und als Base64 kodieren:
   ```bash
   tar -czf - ~/.tdlib-session | base64 -w0 > session.b64
   ```

2. Als GitHub Codespaces Secret `TDLIB_SESSION_B64` einfügen

3. Beim Codespace-Start wird die Session automatisch importiert

---

## Beispiel-Workflows

### Xtream: VOD-Katalog durchsuchen

```bash
# 1. Setup
source ./tools/fishit-cli-setup.sh

# 2. Status prüfen
./fishit-cli xtream status

# 3. VOD anzeigen (erste 20)
./fishit-cli xtream list-vod --limit 20

# 4. Serien anzeigen
./fishit-cli xtream list-series --limit 10
```

### Telegram: Medien aus Channel laden

```bash
# 1. Setup
source ./tools/fishit-cli-setup.sh

# 2. Status prüfen
./fishit-cli telegram status

# 3. Chats auflisten
./fishit-cli telegram list-chats

# 4. Medien aus Channel samplen
./fishit-cli telegram sample-media --chat-id -1001234567890 --limit 5
```

### Vollständiger Pipeline-Test

```bash
# Alle Pipelines testen
./fishit-cli meta test-all
```

---

## Architektur

```
tools/pipeline-cli/
├── Main.kt                 # Clikt CLI Entry Point
├── CliConfigLoader.kt      # Konfiguration aus Env/JSON laden
└── commands/
    ├── TelegramCommand.kt  # Telegram-Subcommands
    ├── XtreamCommand.kt    # Xtream-Subcommands
    └── MetaCommand.kt      # Meta-/Test-Commands

core/app-startup/
├── AppStartupConfig.kt     # Pipeline-Konfiguration
├── AppStartup.kt           # Interface
└── AppStartupImpl.kt       # Implementation (Pipeline-Wiring)

pipeline/telegram/debug/
├── TelegramDebugService.kt     # Interface
└── TelegramDebugServiceImpl.kt # Implementation

pipeline/xtream/debug/
├── XtreamDebugService.kt       # Interface
└── XtreamDebugServiceImpl.kt   # Implementation
```

---

## Fehlerbehebung

### "Java not found"

Java 17+ muss installiert sein:
```bash
# Ubuntu/Debian
sudo apt install openjdk-17-jdk

# macOS
brew install openjdk@17
```

### "ANDROID_HOME not set"

Android SDK muss verfügbar sein:
```bash
export ANDROID_HOME=/path/to/android/sdk
# oder in local.properties:
echo "sdk.dir=/path/to/android/sdk" > local.properties
```

### Telegram: "Session invalid"

Die TDLib-Session ist abgelaufen oder ungültig:
1. Neue Session erstellen (siehe oben)
2. `TG_SESSION_PATH` aktualisieren
3. Setup erneut ausführen

### Xtream: "Authentication failed"

Credentials prüfen:
1. URL korrekt? (inkl. Port)
2. Username/Password korrekt?
3. Provider aktiv?

---

## Bekannte Einschränkungen

- **TDLib Native Libraries**: Erfordern passende Plattform (Linux x64 im Codespace)
- **Telegram-Session**: An TDLib-Version gebunden
- **Xtream-Tests**: Erfordern aktive Provider-Zugangsdaten

---

## Lizenz

Dieses Tool ist Teil des FishIT-Player Projekts.
