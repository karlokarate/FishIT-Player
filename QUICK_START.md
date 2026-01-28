# 🚀 Quick Start – FishIT-Player Entwicklung

Schnelleinstieg für neue Entwickler auf Windows-Systemen.

## ⚡ 5-Minuten Setup

### 1. Voraussetzungen prüfen

```powershell
# JDK 21 prüfen
java -version
# Erwartet: openjdk version "21.x.x"

# Android SDK prüfen
echo $env:ANDROID_HOME
# Erwartet: C:\Users\admin\AppData\Local\Android\Sdk
```

**Fehlt etwas?** → Siehe [Vollständiges Setup](docs/dev/LOCAL_SETUP.md)

### 2. Projekt klonen & öffnen

```powershell
cd C:\Users\admin\StudioProjects
git clone https://github.com/karlokarate/FishIT-Player.git
cd FishIT-Player
```

**In Android Studio öffnen:**
1. `File → Open → C:\Users\admin\StudioProjects\FishIT-Player`
2. Warte auf Gradle Sync (2-5 Minuten beim ersten Mal)

### 3. Build & Run

```powershell
# Debug APK bauen
.\gradlew :app-v2:assembleDebug

# APK Location:
# app-v2\build\outputs\apk\debug\app-v2-debug.apk
```

**Auf Gerät installieren:**
```powershell
adb install -r app-v2\build\outputs\apk\debug\app-v2-debug.apk
```

---

## 📋 Häufige Tasks

### Code Quality Check

```powershell
# Alle Checks ausführen (vor Commit)
.\gradlew ktlintCheck detekt lintDebug test

# Nur Formatierung prüfen
.\gradlew ktlintCheck

# Auto-Format
.\gradlew ktlintFormat
```

### Tests ausführen

```powershell
# Alle Unit Tests
.\gradlew test

# Modul-spezifisch
.\gradlew :core:model:test
.\gradlew :pipeline:telegram:test
```

### MCP Server für Copilot Tools

```powershell
# JAR bauen (einmalig, dann nur bei Änderungen)
.\gradlew :tools:mcp-server:fatJar

# Manuell starten (für Debugging)
java -jar tools\mcp-server\build\libs\mcp-server-1.0.0-all.jar
```

---

## 🎯 Entwicklungs-Workflow

### Branch-Strategie

```bash
# Neues Feature
git checkout -b feature/mein-feature architecture/v2-bootstrap

# Bugfix
git checkout -b fix/issue-123 architecture/v2-bootstrap
```

### Vor jedem Commit

```powershell
# 1. Code formatieren
.\gradlew ktlintFormat

# 2. Quality Checks
.\gradlew ktlintCheck detekt lintDebug

# 3. Tests
.\gradlew test

# 4. Commit
git add .
git commit -m "feat: Beschreibung des Features"
```

### Commit Message Format

```
feat: Neue Funktion hinzugefügt
fix: Bug in Player behoben
refactor: Code-Struktur verbessert
docs: Dokumentation aktualisiert
test: Tests hinzugefügt
chore: Build-Konfiguration angepasst
```

---

## 📚 Wichtige Dokumentation

### Architektur & Verträge (MUST READ)

| Dokument | Wann lesen | Priorität |
|----------|------------|-----------|
| [AGENTS.md](AGENTS.md) | **IMMER vor Änderungen** | 🔴 KRITISCH |
| [contracts/](contracts/) | Vor Änderungen im jeweiligen Bereich | 🔴 KRITISCH |
| [copilot-instructions.md](.github/copilot-instructions.md) | Einmal zu Beginn | 🟡 Wichtig |
| [V2_PORTAL.md](V2_PORTAL.md) | Für Architektur-Überblick | 🟡 Wichtig |

### Modul-spezifische Anweisungen

Unter `.github/instructions/` befinden sich **21 path-scoped instruction files**:

- `core-model.instructions.md` → `core/model/**`
- `pipeline.instructions.md` → `pipeline/**`
- `player.instructions.md` → `player/**`
- `infra-transport-telegram.instructions.md` → `infra/transport-telegram/**`
- usw.

**Diese werden automatisch von VS Code Copilot geladen!**

### Quick References

| Thema | Dokument |
|-------|----------|
| Lokales Setup | [docs/dev/LOCAL_SETUP.md](docs/dev/LOCAL_SETUP.md) |
| Build Guide | [BUILD_GUIDE.md](BUILD_GUIDE.md) |
| UI Components | [docs/fish_layout.md](docs/fish_layout.md) |
| Player Contract | [contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md](contracts/INTERNAL_PLAYER_BEHAVIOR_CONTRACT.md) |
| Telegram | [.github/tdlibAgent.md](.github/tdlibAgent.md) |
| Naming | [contracts/GLOSSARY_v2_naming_and_modules.md](contracts/GLOSSARY_v2_naming_and_modules.md) |

---

## 🔧 Copilot Optimal Nutzen

### 1. Custom Agent aktiviert

Du arbeitest bereits im `v2_codespace_agent` Modus. Dieser Agent:

- ✅ Liest automatisch AGENTS.md und Contracts
- ✅ Prüft Layer-Boundaries vor Änderungen
- ✅ Befolgt path-scoped instructions
- ✅ Führt Pre-/Post-Change Checklists durch

### 2. MCP Tools nutzen

Nach MCP Server Setup kannst du in Copilot Chat fragen:

```
"Zeige mir alle Xtream VOD Kategorien"
→ Nutzt xtream_vod_categories Tool

"Parse diesen Titel: Movie.Title.2024.1080p.WEB-DL"
→ Nutzt normalize_parse_title Tool

"Generiere eine Mock-Telegram-Nachricht"
→ Nutzt telegram_mock_message Tool
```

### 3. Architektur-Fragen

```
"Darf pipeline/* auf infra/data-* zugreifen?"
→ Agent prüft AGENTS.md Layer Boundaries

"Wie mappe ich XtreamVodItem zu RawMediaMetadata?"
→ Agent liest MEDIA_NORMALIZATION_CONTRACT.md
```

---

## 🚨 Häufige Fehler vermeiden

### ❌ NICHT TUN

```kotlin
// ❌ Layer-Violation: Pipeline darf nicht auf Data zugreifen
class TelegramPipeline(
    private val repository: TelegramRepository // WRONG!
)

// ❌ Player darf nicht Telegram kennen
class InternalPlayer(
    private val telegramClient: TelegramClient // WRONG!
)

// ❌ UI darf nicht auf legacy Obx* zugreifen
class HomeViewModel(
    private val obxRepo: ObxCanonicalMediaRepository // WRONG!
)
```

### ✅ RICHTIG

```kotlin
// ✅ Pipeline emittiert nur RawMediaMetadata
class TelegramPipeline : CatalogPipeline {
    override suspend fun sync() {
        val items = fetchItems()
        emit(items.map { it.toRawMediaMetadata() })
    }
}

// ✅ Player ist source-agnostic
class InternalPlayer(
    private val sourceResolver: PlaybackSourceResolver // Abstraction!
)

// ✅ UI liest nur von NX_* Entities
class HomeViewModel(
    private val nxWorkRepository: NxWorkRepository // SSOT!
)
```

---

## 📞 Hilfe bekommen

### Dokumentation durchsuchen

```powershell
# Suche nach Schlüsselwörtern
findstr /s /i "RawMediaMetadata" *.md
findstr /s /i "TelegramPipeline" contracts\*.md
```

### Bei Problemen

1. **Erst Dokumentation prüfen:** AGENTS.md, contracts/, copilot-instructions.md
2. **Copilot fragen:** "Erkläre mir die Layer Boundaries in AGENTS.md"
3. **Issue erstellen:** Mit Details zu Fehler, Logs, Steps to Reproduce

---

**Erstellt:** 2026-01-28  
**Branch:** architecture/v2-bootstrap  
**JDK:** 21 | **Gradle:** 8.13 | **Kotlin:** 2.1.0
