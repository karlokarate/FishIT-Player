# VS Code Workspace Configuration

Diese Dateien konfigurieren VS Code optimal für die FishIT-Player Entwicklung.

## 📁 Dateien

### `settings.json`

- **Kotlin & Android**: Konfiguriert Kotlin Language Server, Java 17, Android SDK
- **Formatierung**: Auto-Format beim Speichern (ktlint)
- **Terminal**: Automatische Umgebungsvariablen (ANDROID_SDK_ROOT, JAVA_HOME, etc.)
- **Performance**: File-Watcher-Excludes für .gradle, build/, .wsl-\*
- **Editor**: Tab-Größen, Rulers, Bracket Colorization

### `tasks.json`

25+ vorkonfigurierte Tasks mit Emojis:

- **Build**: Debug/Release APK & Bundle
- **Tests**: Unit Tests
- **Quality**: ktlint, detekt, lint
- **Emulator**: Start TV/Phone AVDs
- **Device**: Install, Uninstall, Logcat, Device List
- **Tools**: Setup, TDLib Build, Dependency Reports

**Shortcuts**: Siehe `keybindings.json`

### `launch.json`

Debug-Konfigurationen:

- **Kotlin/Android Debugger** - Für App-Debugging
- **Attach Debugger** - Für laufende Prozesse (Port 5005)

### `extensions.json`

Empfohlene Extensions (werden automatisch vorgeschlagen):

- **Kotlin** (fwcd.kotlin) - Language Support
- **Java Pack** - Java/Gradle Tools
- **GitLens** - Advanced Git
- **XML Tools** - Resource Editor
- **Error Lens** - Inline Errors
- **Todo Highlights** - TODO/FIXME Marker

### `keybindings.json`

Tastenkombinationen für häufige Tasks:

- `Ctrl+Shift+B` → Build Debug APK
- `Ctrl+Shift+T` → Tests
- `Ctrl+Shift+F` → Code formatieren
- `Ctrl+Shift+L` → Code-Style Check
- `Ctrl+Shift+D` → Detekt
- `Ctrl+Shift+Q` → Full Quality Check

### `snippets.code-snippets`

Code-Snippets für schnellere Entwicklung:

- `composescreen` → Compose Screen Template
- `composevm` → ViewModel mit UiState
- `fishrow` → FishRow Component
- `tvbutton` → TV Button
- `obxquery` → ObjectBox Query
- `colaunch` → Coroutine Launch
- `remsave` → Remember Saveable
- `focusreq` → Focus Requester

### `fishit-player.code-workspace`

Multi-Root Workspace File (optional):

- Öffne mit: File → Open Workspace from File
- Nutzt relative Pfade für bessere Portabilität

## 🚀 Erste Schritte

1. **Extensions installieren**:

   - Öffne Extensions-View (`Ctrl+Shift+X`)
   - Tab "Recommended" zeigt alle empfohlenen Extensions
   - Klicke "Install All"

2. **Ersten Build starten**:

   - `Ctrl+Shift+B` oder
   - Terminal: `./dev.sh build`

3. **Task ausführen**:

   - `Ctrl+Shift+P` → "Tasks: Run Task"
   - Wähle z.B. "🧪 Run Tests"

4. **Emulator starten**:
   - Task: "🚀 Start Emulator (TV)"
   - Oder: `./dev.sh emulator-tv`

## 🎯 Tipps

### Auto-Format aktivieren

Bereits aktiviert! Code wird beim Speichern automatisch mit ktlint formatiert.

### Terminal mit Environment

Alle integrierten Terminals erhalten automatisch:

- `ANDROID_SDK_ROOT`
- `JAVA_HOME`
- `GRADLE_USER_HOME`
- Erweiterte `PATH` mit SDK Tools

Alternativ: `./dev.sh shell` für interaktive Shell

### Tasks schnell finden

- `Ctrl+Shift+P` → Type "task"
- Oder Terminal → Rechtsklick → "Run Task"

### Code-Snippets nutzen

In `.kt` Dateien Snippet-Prefix tippen (z.B. `composescreen`) und Tab drücken.

### Error Lens

Die Extension "Error Lens" zeigt Fehler direkt inline im Editor - super praktisch!

## 📝 Anpassungen

### Eigene Tasks hinzufügen

Bearbeite `tasks.json` und füge neue Tasks nach diesem Schema hinzu:

```json
{
  "label": "🎯 Mein Task",
  "type": "shell",
  "command": "./gradlew myCommand",
  "problemMatcher": [],
  "presentation": {
    "reveal": "always",
    "panel": "dedicated"
  }
}
```

### Shortcuts ändern

Bearbeite `keybindings.json`:

```json
{
  "key": "ctrl+alt+t",
  "command": "workbench.action.tasks.runTask",
  "args": "Mein Task Name"
}
```

### Snippets erweitern

Füge in `snippets.code-snippets` hinzu:

```json
"Snippet Name": {
  "prefix": "trigger",
  "body": [
    "// Code hier"
  ],
  "description": "Beschreibung"
}
```

## 🔧 Troubleshooting

### Tasks funktionieren nicht

- Prüfe ob `./gradlew` existiert und ausführbar ist
- Check Terminal-Output für Fehler
- Stelle sicher dass WSL-Tools installiert sind: `./dev.sh`

### Kotlin Language Server startet nicht

- Java 17 muss vorhanden sein
- Prüfe: `./dev.sh shell` dann `java -version`
- Falls fehlt: Führe WSL Setup aus

### Format-on-Save funktioniert nicht

- Extension "Kotlin" installiert?
- Prüfe `settings.json`: `"editor.formatOnSave": true`
- Alternativ: `Ctrl+Shift+F` für manuelles Format

### Emulator startet nicht

- AVD vorhanden? `./dev.sh avds`
- Genug RAM/CPU? Emulator braucht mind. 2GB RAM
- Falls WSL: X11/Wayland Display konfiguriert?

## 📚 Weitere Infos

- **QUICKSTART.md** - Schnelleinstieg
- **WORKSPACE_GUIDE.md** - Ausführliche Anleitung
- **DEVELOPER_GUIDE.md** - Build-Details

## ✨ Features im Überblick

✅ **Auto-Formatierung** - Code wird beim Speichern formatiert  
✅ **Quality Checks** - ktlint, detekt, lint integriert  
✅ **Emulator Control** - TV & Phone direkt aus VS Code  
✅ **Smart Debugging** - Kotlin/Android Debugger ready  
✅ **Code Snippets** - Compose, ObjectBox, Focus-Helpers  
✅ **Git Integration** - GitLens für bessere Übersicht  
✅ **Inline Errors** - Sofortige Fehleranzeige im Code  
✅ **Todo Tracking** - TODO/FIXME automatisch hervorgehoben  
✅ **Fast Tasks** - Keyboard Shortcuts für alles

Happy Coding! 🚀
