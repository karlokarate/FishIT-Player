# Lokales Entwicklungsumgebung Setup

Diese Anleitung beschreibt die vollständige Einrichtung der Entwicklungsumgebung für FishIT-Player auf Windows.

## Systemanforderungen

| Komponente        | Minimum       | Empfohlen     |
| ----------------- | ------------- | ------------- |
| **JDK**           | 21            | 21 (Temurin)  |
| **RAM**           | 16 GB         | 32 GB         |
| **Festplatte**    | 50 GB SSD     | 100 GB SSD    |
| **Android Studio**| Ladybug 2024.3| Ladybug 2024.3|
| **Gradle**        | 8.13          | 8.13 (Wrapper)|
| **Node.js**       | 18.x (für MCP)| 20.x LTS      |

## 1. JDK 21 Installation

### Option A: Android Studio Embedded JDK (Empfohlen)

Android Studio Ladybug enthält ein eingebettetes JDK 21. Für Gradle wird dieses automatisch verwendet.

**Prüfe in Android Studio:**
1. `File → Settings → Build, Execution, Deployment → Build Tools → Gradle`
2. Stelle sicher, dass "Gradle JDK" auf `21` oder `Embedded JDK` gesetzt ist

### Option B: Adoptium Temurin JDK 21

```powershell
# Installation via winget (Windows)
winget install EclipseAdoptium.Temurin.21.JDK

# Oder Download von: https://adoptium.net/temurin/releases/?version=21
```

Nach der Installation:
```powershell
# JAVA_HOME setzen (System-Umgebungsvariablen)
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-21.x.x", "Machine")

# Prüfen
java -version
# Erwartet: openjdk version "21.x.x" ...
```

## 2. Android SDK Konfiguration

Das Android SDK ist bereits in `local.properties` konfiguriert:

```properties
sdk.dir=C:\\Users\\admin\\AppData\\Local\\Android\\Sdk
```

### Erforderliche SDK-Komponenten

Stelle sicher, dass diese Komponenten installiert sind (SDK Manager in Android Studio):

- **SDK Platforms:**
  - Android 15 (API 35) - Ziel-SDK
  - Android 13 (API 33) - Min-SDK für Tests

- **Build Tools:**
  - 35.0.0

- **SDK Tools:**
  - Android SDK Command-line Tools
  - Android Emulator
  - Android SDK Platform-Tools
  - NDK (Side by side) - für TDLib native Libs

## 3. Gradle Konfiguration

Die `gradle.properties` ist bereits für lokale Entwicklung optimiert:

```properties
# Daemon für schnellere Builds
org.gradle.daemon=true

# Heap: 8GB für große Codebasis mit R8 Full Mode
org.gradle.jvmargs=-Xmx8g -XX:+UseG1GC -XX:MaxMetaspaceSize=1g

# Parallele Builds
org.gradle.parallel=true
org.gradle.workers.max=4
```

### Gradle Wrapper Version

Das Projekt verwendet Gradle 8.13 (via Wrapper). Führe immer `./gradlew` aus, nicht `gradle` direkt.

## 4. MCP Server Setup (für Copilot Tools)

### 4.1 Node.js installieren

```powershell
# Installation via winget
winget install OpenJS.NodeJS.LTS

# Prüfen
node -v  # >= 18.x
npm -v
npx -v
```

### 4.2 MCP Server JAR bauen

```powershell
cd C:\Users\admin\StudioProjects\FishIT-Player
.\gradlew :tools:mcp-server:fatJar
```

Das JAR wird erstellt unter:
```
tools/mcp-server/build/libs/mcp-server-1.0.0-all.jar
```

### 4.3 Umgebungsvariablen für MCP (Optional)

Für Xtream und Telegram API Zugriff:

```powershell
# System-Umgebungsvariablen setzen
[Environment]::SetEnvironmentVariable("COPILOT_MCP_XTREAM_URL", "http://dein-server:8080", "User")
[Environment]::SetEnvironmentVariable("COPILOT_MCP_XTREAM_USER", "dein_user", "User")
[Environment]::SetEnvironmentVariable("COPILOT_MCP_XTREAM_PASS", "dein_pass", "User")
[Environment]::SetEnvironmentVariable("COPILOT_MCP_TELEGRAM_API_ID", "12345678", "User")
[Environment]::SetEnvironmentVariable("COPILOT_MCP_TELEGRAM_API_HASH", "abc123...", "User")
```

> **Hinweis:** Telegram API Credentials von https://my.telegram.org/apps

### 4.4 MCP Konfiguration prüfen

Die MCP-Konfiguration befindet sich in:
```
C:\Users\admin\AppData\Local\github-copilot\intellij\mcp.json
```

Sie enthält:
- **fishit-pipeline**: Domain-spezifische Tools (Xtream, Telegram, Pipeline)
- **sequential-thinking**: Langzeit-Kontext für mehrstufige Aufgaben

## 5. IDE Konfiguration

### 5.1 Android Studio / IntelliJ Einstellungen

Die IDE-Konfiguration wird automatisch von `.idea/` geladen:

- **Gradle JDK:** JDK 21
- **Projekt SDK:** JDK 21
- **Code Style:** ktlint 1.5.0

### 5.2 Copilot Einstellungen

Für optimale Copilot-Nutzung:

1. **GitHub Copilot Plugin** installiert und angemeldet
2. **Custom Agent aktiviert:** `v2_codespace_agent` Modus
3. **MCP Tools verfügbar** nach IDE-Neustart

## 6. Build-Befehle

### Häufige Tasks

```powershell
# Debug APK bauen
.\gradlew :app-v2:assembleDebug

# Tests ausführen
.\gradlew test

# Code-Qualität prüfen
.\gradlew ktlintCheck detekt lintDebug

# Code formatieren
.\gradlew ktlintFormat

# Vollständiger CI-Check
.\gradlew ktlintCheck detekt lintDebug test

# Clean Build
.\gradlew clean assembleDebug
```

### MCP Server Tasks

```powershell
# MCP Server JAR bauen
.\gradlew :tools:mcp-server:fatJar

# MCP Server direkt starten (für Debugging)
java -jar tools/mcp-server/build/libs/mcp-server-1.0.0-all.jar
```

## 7. Verifizierung

### Checklist nach Setup

- [ ] `java -version` zeigt JDK 21
- [ ] `.\gradlew --version` zeigt Gradle 8.13
- [ ] `node -v` zeigt Node.js 18+
- [ ] Android Studio → Gradle Sync erfolgreich
- [ ] `.\gradlew :app-v2:assembleDebug` kompiliert fehlerfrei
- [ ] MCP JAR existiert: `tools/mcp-server/build/libs/mcp-server-1.0.0-all.jar`
- [ ] Copilot zeigt MCP Tools nach IDE-Neustart

## 8. Troubleshooting

### Gradle Sync schlägt fehl

```powershell
# Gradle Cache löschen
.\gradlew --stop
Remove-Item -Recurse -Force ~\.gradle\caches
.\gradlew clean
```

### Out of Memory

Prüfe `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx8g
```

### MCP Server startet nicht

1. Prüfe ob JAR existiert: `ls tools/mcp-server/build/libs/`
2. Baue neu: `.\gradlew :tools:mcp-server:fatJar`
3. Prüfe Java: `java -version` (muss JDK 21 sein)

### Telegram/Xtream Tools funktionieren nicht

Prüfe Umgebungsvariablen:
```powershell
echo $env:COPILOT_MCP_XTREAM_URL
echo $env:COPILOT_MCP_TELEGRAM_API_ID
```

---

**Erstellt:** 2026-01-28  
**JDK:** 21 | **Gradle:** 8.13 | **AGP:** 8.8.2 | **Kotlin:** 2.1.0
