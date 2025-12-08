# FishIT-Player VS Code Workspace Guide

## 🚀 Schnellstart

Dieser Workspace ist vollständig konfiguriert für Android/Kotlin-Entwicklung mit allen Quality-Tools und Debugging-Features.

## ⌨️ Tastenkombinationen

| Tastenkombination | Aktion                      |
| ----------------- | --------------------------- |
| `Ctrl+Shift+B`    | Build Debug APK             |
| `Ctrl+Shift+T`    | Tests ausführen             |
| `Ctrl+Shift+F`    | Code formatieren (ktlint)   |
| `Ctrl+Shift+L`    | Code-Style prüfen           |
| `Ctrl+Shift+D`    | Detekt ausführen            |
| `Ctrl+Shift+Q`    | Vollständiger Quality Check |

## 📋 Wichtige Tasks

Öffne die Command Palette (`Ctrl+Shift+P`) und wähle "Tasks: Run Task":

### Build & Deploy

- **🏗️ Build Debug APK** - Debug-Version bauen
- **🚀 Build Release APK** - Release-Version bauen
- **📦 Bundle Release** - Release-Bundle erstellen
- **📱 Install Debug on Device** - APK auf Gerät installieren

### Tests & Quality

- **🧪 Run Tests** - Unit-Tests ausführen
- **✨ Format Code (ktlint)** - Code automatisch formatieren
- **🔍 Check Code Style (ktlint)** - Style-Probleme finden
- **🔎 Run Detekt** - Statische Code-Analyse
- **🧹 Lint Debug** - Android Lint ausführen
- **✅ Full Quality Check** - Alle Checks auf einmal

### Emulator & Devices

- **🚀 Start Emulator (TV)** - Android TV Emulator starten
- **📱 Start Emulator (Phone)** - Phone Emulator starten
- **📋 List AVDs** - Verfügbare AVDs anzeigen
- **🔌 List Connected Devices** - Verbundene Geräte auflisten
- **📊 Show Logcat** - Live-Logs anzeigen
- **🗑️ Uninstall App** - App vom Gerät entfernen

### Build-Tools

- **🔧 Setup WSL Build Tools** - WSL-Tools einrichten
- **🛠️ TDLib Build (arm64)** - TDLib native libs bauen
- **🔍 Audit TV Focus** - TV-Fokus-Regeln prüfen

## 🔧 Emulator einrichten

Falls noch kein AVD existiert:

```bash
# Android TV AVD erstellen
${workspaceFolder}/.wsl-android-sdk/cmdline-tools/latest/bin/avdmanager create avd \
  -n Android_TV_1080p_API_31 \
  -k "system-images;android-31;google_apis;x86_64" \
  -d "tv_1080p"

# Phone AVD erstellen
${workspaceFolder}/.wsl-android-sdk/cmdline-tools/latest/bin/avdmanager create avd \
  -n Pixel_5_API_31 \
  -k "system-images;android-31;google_apis_playstore;x86_64" \
  -d "pixel_5"
```

## 🐛 Debugging

1. **APK auf Emulator installieren**: Task "📱 Install Debug on Device"
2. **App starten** auf dem Emulator
3. **Debugger anhängen**: F5 oder "🐛 Attach Debugger" in der Debug-Ansicht

## 📦 Empfohlene Extensions

Die wichtigsten Extensions werden automatisch vorgeschlagen:

- **Kotlin Language** - Syntax-Highlighting & IntelliSense
- **Java Extension Pack** - Java/Gradle-Support
- **GitLens** - Git-Integration verbessert
- **XML Tools** - XML/Android Resource Editor
- **Error Lens** - Inline-Fehleranzeige

Installiere sie über: View → Extensions → Tab "Recommended"

## 🎨 Code-Formatierung

Das Projekt nutzt ktlint für einheitlichen Code-Style:

```bash
# Automatisch formatieren
./gradlew ktlintFormat

# Nur prüfen
./gradlew ktlintCheck
```

Format-on-Save ist aktiviert! Code wird beim Speichern automatisch formatiert.

## 🔍 Quality Checks

Vor jedem Commit/Push empfohlen:

```bash
# Alles auf einmal
./gradlew ktlintCheck detekt lintDebug test

# Oder über Task: "✅ Full Quality Check"
```

## 📱 Direktes Testing auf Device

```bash
# APK bauen und installieren
./gradlew installDebug

# Logs live verfolgen
adb logcat | grep "FishIT"

# App starten
adb shell am start -n com.chris.m3usuite/.MainActivity
```

## 🌐 Nützliche Pfade

- **Source Code**: `app/src/main/java/com/chris/m3usuite/`
- **Resources**: `app/src/main/res/`
- **Build Output**: `app/build/outputs/apk/`
- **Test Reports**: `app/build/reports/`
- **Docs**: `docs/`

## 💡 Tipps

1. **Schneller Build**: Nutze `./gradlew --daemon` für Build-Daemon
2. **Incremental Builds**: Gradle cached automatisch, nur Änderungen werden neu gebaut
3. **Offline-Modus**: `./gradlew --offline` wenn Internet langsam ist
4. **Parallel Builds**: Bereits aktiviert in `gradle.properties`
5. **TV-Testing**: Nutze Android TV AVD für realistisches TV-Layout

## 🆘 Probleme lösen

### Build schlägt fehl

```bash
# Clean & Rebuild
./gradlew clean assembleDebug
```

### Emulator startet nicht

```bash
# Check AVDs
.wsl-android-sdk/emulator/emulator -list-avds

# Cold boot
.wsl-android-sdk/emulator/emulator -avd <name> -no-snapshot-load
```

### Gradle Sync Probleme

```bash
# Dependencies neu laden
./gradlew --refresh-dependencies
```

## 📚 Weitere Dokumentation

- [AGENTS.md](./AGENTS.md) - Architektur & Workflow-Regeln
- [DEVELOPER_GUIDE.md](./DEVELOPER_GUIDE.md) - Detaillierte Build-Anleitung
- [ARCHITECTURE_OVERVIEW.md](./ARCHITECTURE_OVERVIEW.md) - Modul-Übersicht
- [docs/](./docs/) - Feature-spezifische Dokumentation

## 🎯 Nächste Schritte

1. **Extensions installieren**: Command Palette → "Extensions: Show Recommended Extensions"
2. **WSL-Tools verifizieren**: Task "🔧 Setup WSL Build Tools"
3. **Ersten Build starten**: `Ctrl+Shift+B`
4. **Emulator erstellen**: Siehe Emulator-Sektion oben
5. **App installieren & testen**: Task "📱 Install Debug on Device"

Viel Erfolg! 🚀
