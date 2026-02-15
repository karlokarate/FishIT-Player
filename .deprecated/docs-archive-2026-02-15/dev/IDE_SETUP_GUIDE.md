# 🔧 IDE Konfiguration - Schritt für Schritt

## Problem: Terminal-Zugriff / JDK-Konfiguration

Wenn das `quick-test.ps1` Script nicht läuft oder Build-Fehler auftreten, folge diesen Schritten:

---

## ✅ Schritt 1: Terminal in IDE öffnen

### Option A: Tastenkombination
- Drücke: **`Alt + F12`**

### Option B: Menü
1. Klicke auf **`View`** (oben)
2. → **`Tool Windows`**
3. → **`Terminal`**

### Option C: Unterer Toolbar
- Klicke auf den **`Terminal`** Tab unten in der IDE

**Erwartetes Ergebnis:**
- Ein PowerShell-Fenster öffnet sich unten in der IDE
- Prompt zeigt: `PS C:\Users\admin\StudioProjects\FishIT-Player>`

---

## ✅ Schritt 2: Gradle JDK auf 21 setzen

### Weg zum Setting:
1. **`File`** → **`Settings`** (oder `Ctrl + Alt + S`)
2. Navigiere zu: **`Build, Execution, Deployment`**
3. → **`Build Tools`**
4. → **`Gradle`**

### Was ändern:
- **Gradle JDK:** Dropdown öffnen und **`21`** auswählen
  - Falls "21" nicht verfügbar:
    - Klicke auf **`Download JDK...`**
    - Wähle: **Version:** `21`, **Vendor:** `Eclipse Temurin (Adoptium)`
    - Klicke **`Download`**

### Weitere Gradle-Einstellungen (optional):
- **Build and run using:** `Gradle (Default)`
- **Run tests using:** `Gradle (Default)`
- **Gradle user home:** Standard belassen

**Klicke `Apply` → `OK`**

---

## ✅ Schritt 3: Project SDK auf 21 setzen

### Weg zum Setting:
1. **`File`** → **`Project Structure...`** (oder `Ctrl + Alt + Shift + S`)
2. Linke Seite: **`Project`** auswählen

### Was ändern:
- **SDK:** Dropdown öffnen und **`21`** auswählen
  - Falls nicht verfügbar: **`Add SDK`** → **`Download JDK...`**
  - Version `21`, Vendor `Eclipse Temurin (Adoptium)`
- **Language level:** **`SDK default (21 - ...)`**

**Klicke `Apply` → `OK`**

---

## ✅ Schritt 4: Gradle Sync durchführen

Nach den Änderungen **muss** ein Gradle Sync erfolgen:

### Option A: Menü
1. **`File`** → **`Sync Project with Gradle Files`**

### Option B: Banner
- Wenn ein gelber Banner erscheint: **`Sync Now`** klicken

### Option C: Gradle Tool Window
1. Öffne **`Gradle`** Tab (rechts in der IDE)
2. Klicke auf das **Refresh-Icon** (🔄)

**Warte 2-5 Minuten** bis Sync abgeschlossen ist.

**Erwartetes Ergebnis:**
- Build-Tab zeigt: **`BUILD SUCCESSFUL`**
- Keine roten Fehler im Gradle-Output

---

## ✅ Schritt 5: MCP Konfiguration aktivieren (für Copilot Tools)

### MCP Config wurde bereits erstellt unter:
```
C:\Users\admin\AppData\Local\github-copilot\intellij\mcp.json
```

### Aktivierung:
1. **IDE komplett neu starten** (nicht nur Projekt schließen!)
   - **`File`** → **`Exit`**
   - Android Studio neu öffnen

2. Nach Neustart: Warte ~30 Sekunden
   - Copilot lädt die MCP-Konfiguration

3. **Prüfen ob MCP aktiv ist:**
   - Öffne GitHub Copilot Chat
   - Frage: "What MCP tools are available?"
   - Erwartete Antwort: Liste mit `fishit-pipeline` und `sequential-thinking` Tools

---

## ✅ Schritt 6: Umgebung testen

Im Terminal (Alt + F12):

```powershell
# Wechsel ins Projekt-Verzeichnis (falls nicht schon dort)
cd C:\Users\admin\StudioProjects\FishIT-Player

# Führe Quick-Test aus
.\scripts\quick-test.ps1
```

### Falls "Execution Policy" Fehler:
```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\quick-test.ps1
```

### Oder direkt mit Bypass:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\quick-test.ps1
```

---

## ✅ Schritt 7: Ersten Build starten

Im Terminal:

```powershell
# Debug APK bauen
.\gradlew.bat :app-v2:assembleDebug
```

**Erwartetes Ergebnis:**
- Gradle lädt Dependencies (~1-2 Minuten beim ersten Mal)
- Build läuft durch alle Module
- Endet mit: **`BUILD SUCCESSFUL in Xs`**
- APK Location wird angezeigt

---

## 🔴 Troubleshooting

### Problem: "JDK 21 not found in list"

**Lösung:**
1. Settings → Build Tools → Gradle → Gradle JDK
2. Klicke **`Download JDK...`**
3. **Version:** `21`
4. **Vendor:** `Eclipse Temurin (Adoptium)`
5. Klicke **`Download`** und warte

### Problem: Gradle Sync schlägt fehl

**Lösung:**
```powershell
# Gradle Daemon stoppen
.\gradlew.bat --stop

# Cache löschen (optional, wenn Probleme bestehen)
Remove-Item -Recurse -Force $env:USERPROFILE\.gradle\caches

# Neu syncen
# Dann in IDE: File → Sync Project with Gradle Files
```

### Problem: Terminal zeigt falsches Verzeichnis

**Lösung:**
```powershell
cd C:\Users\admin\StudioProjects\FishIT-Player
pwd  # Prüfe aktuelles Verzeichnis
```

### Problem: MCP Tools erscheinen nicht

**Lösung:**
1. Prüfe Datei existiert:
   ```powershell
   Test-Path "$env:LOCALAPPDATA\github-copilot\intellij\mcp.json"
   ```
   Sollte `True` ausgeben

2. IDE **komplett neu starten** (wichtig!)

3. Warte ~30 Sekunden nach Neustart

4. In Copilot Chat fragen: "What MCP tools are available?"

---

## 📋 Checkliste - Alles korrekt konfiguriert?

- [ ] Terminal öffnet in IDE (Alt + F12)
- [ ] Gradle JDK = 21 (Settings → Build Tools → Gradle)
- [ ] Project SDK = 21 (Project Structure → Project)
- [ ] Gradle Sync erfolgreich (keine roten Fehler)
- [ ] `java -version` zeigt 21.x.x
- [ ] `.\gradlew.bat --version` zeigt Gradle 8.13
- [ ] MCP config existiert (Test-Path zeigt True)
- [ ] IDE wurde neu gestartet (für MCP)
- [ ] Quick-Test Script läuft durch
- [ ] `.\gradlew.bat :app-v2:assembleDebug` kompiliert erfolgreich

---

## 🚀 Nach erfolgreicher Konfiguration

Siehe:
- [QUICK_START.md](../QUICK_START.md) - Entwicklungs-Workflow
- [docs/dev/LOCAL_SETUP.md](../docs/dev/LOCAL_SETUP.md) - Vollständige Dokumentation
- [AGENTS.md](../AGENTS.md) - Architektur-Regeln (vor Änderungen lesen!)

**Viel Erfolg! 🎯**
