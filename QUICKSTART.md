# FishIT-Player Quick Start 🚀

Dein Workspace ist jetzt vollständig eingerichtet!

## ⚡ Schnellstart

### 1. Ersten Build starten

```bash
./dev.sh build
```

### 2. Emulator starten

```bash
# Android TV
./dev.sh emulator-tv

# Phone
./dev.sh emulator-phone
```

### 3. App installieren & testen

```bash
./dev.sh install
```

## 🛠️ Verfügbare Befehle

Das `dev.sh` Script macht alles einfacher:

```bash
./dev.sh build           # Debug APK bauen
./dev.sh build-release   # Release APK bauen
./dev.sh test            # Tests ausführen
./dev.sh format          # Code formatieren
./dev.sh lint            # Lint-Checks
./dev.sh quality         # Alle Quality-Checks
./dev.sh install         # APK installieren
./dev.sh emulator-tv     # TV Emulator starten
./dev.sh emulator-phone  # Phone Emulator starten
./dev.sh devices         # Verbundene Geräte anzeigen
./dev.sh logcat          # Live-Logs anzeigen
./dev.sh avds            # Verfügbare Emulatoren
```

## ⌨️ VS Code Tasks & Shortcuts

- **Ctrl+Shift+B** - Build Debug
- **Ctrl+Shift+T** - Tests ausführen
- **Ctrl+Shift+F** - Code formatieren
- **Ctrl+Shift+Q** - Quality Check

Oder: `Ctrl+Shift+P` → "Tasks: Run Task" → Task auswählen

## 📱 Emulatoren

Zwei Emulatoren sind vorkonfiguriert:

1. **Android_TV_1080p_API_31** - Für TV-Layout Testing
2. **Pixel_5_API_31** - Für Phone/Tablet Testing

Starten via:

- VS Code Task: "🚀 Start Emulator (TV)" oder "📱 Start Emulator (Phone)"
- Terminal: `./dev.sh emulator-tv` oder `./dev.sh emulator-phone`

## 🐛 Debugging

1. Emulator starten
2. App installieren: `./dev.sh install`
3. In VS Code: F5 drücken oder Debug-View → "🐛 Debug Android App"

## 📦 Projekt-Struktur

```
FishIT-Player/
├── .vscode/              # VS Code Konfiguration
│   ├── settings.json     # Workspace-Settings
│   ├── tasks.json        # Build/Test Tasks
│   ├── launch.json       # Debug-Konfiguration
│   └── snippets.code-snippets
├── app/src/main/java/    # Kotlin Source Code
├── app/src/main/res/     # Android Resources
├── docs/                 # Projekt-Dokumentation
├── scripts/              # Build & Setup Scripts
├── tools/                # Development Tools
├── dev.sh               # 🎯 Quick Command Script
└── WORKSPACE_GUIDE.md   # Detaillierte Anleitung
```

## 🎨 Code-Snippets

Tippe in Kotlin-Dateien:

- `composescreen` → Neue Compose Screen
- `composevm` → ViewModel mit UiState
- `fishrow` → FishRow Component
- `tvbutton` → TV Button
- `obxquery` → ObjectBox Query
- `colaunch` → Coroutine Launch
- `remsave` → Remember Saveable State

## ✅ Quality Tools

Vor jedem Commit empfohlen:

```bash
./dev.sh quality
```

Das führt aus:

- ktlint (Code-Style)
- detekt (Statische Analyse)
- Android Lint
- Unit Tests

## 🔧 Environment Setup

Alle Tools sind lokal im Projekt installiert:

- `.wsl-android-sdk/` - Android SDK
- `.wsl-java-17/` - Java 17 JDK
- `.wsl-gradle/` - Gradle Cache
- `.wsl-cmake/` - CMake (für native builds)

Das `dev.sh` Script setzt automatisch alle Environment-Variablen!

## 📚 Weitere Hilfe

- **WORKSPACE_GUIDE.md** - Ausführliche Dokumentation
- **AGENTS.md** - Architektur & Workflow
- **DEVELOPER_GUIDE.md** - Build-Details
- **docs/** - Feature-spezifische Docs

## 💡 Tipps

1. **Hot Reload**: Nutze Compose Live Literals für schnelle UI-Änderungen
2. **Incremental Builds**: Gradle cached automatisch
3. **Parallel Testing**: `./gradlew test --parallel`
4. **Offline-Modus**: `./gradlew --offline` bei langsamem Internet
5. **TV-Testing**: Android TV AVD nutzen für realistische TV-UX

## 🆘 Häufige Probleme

### Build schlägt fehl

```bash
./dev.sh clean
./dev.sh build
```

### Emulator startet nicht

```bash
# Check verfügbare AVDs
./dev.sh avds

# Kein AVD? Erstelle einen:
export ANDROID_SDK_ROOT="$(pwd)/.wsl-android-sdk"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"
avdmanager list
```

### Gradle Issues

```bash
./gradlew --refresh-dependencies
```

### Device nicht erkannt

```bash
./dev.sh devices
# Oder direkt:
adb devices
adb kill-server
adb start-server
```

---

## 🎯 Next Steps

1. ✅ Environment ist eingerichtet
2. ▶️ Starte deinen ersten Build: `./dev.sh build`
3. 📱 Teste im Emulator: `./dev.sh emulator-tv`
4. 🚀 Happy Coding!

Bei Fragen: Siehe **WORKSPACE_GUIDE.md** für Details!
