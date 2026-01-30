# 🎯 Android Studio: Wo ist das Java SDK Setting?

## **LÖSUNG: 2 Orte, wo du das Java SDK setzen musst**

---

## 📍 **ORT 1: Gradle JDK (WICHTIGSTER!)**

### **Weg zum Setting:**

```
File (oben links)
  └─ Settings (oder Ctrl + Alt + S)
      └─ Build, Execution, Deployment
          └─ Build Tools
              └─ Gradle
                  └─ [HIER] Gradle JDK ← Dropdown-Menü auf der rechten Seite
```

### **Was du siehst:**

```
┌─────────────────────────────────────────────────────────────┐
│ Settings                                                  × │
├─────────────────────────────────────────────────────────────┤
│ ┌─ Appearance & Behavior                                  ┐ │
│ ├─ Keymap                                                 │ │
│ ├─ Editor                                                 │ │
│ ├─ Plugins                                                │ │
│ ├─▼ Build, Execution, Deployment                         │ │
│ │  ├─▼ Build Tools                                       │ │
│ │  │  ├─▶ Gradle          ← HIER KLICKEN                │ │
│ │  │  └─  Maven                                          │ │
│ │  ├─  Compiler                                          │ │
│ │  └─  Debugger                                          │ │
│ └─ Languages & Frameworks                                 │ │
└─────────────────────────────────────────────────────────────┘
```

### **Auf der rechten Seite:**

```
┌────────────────────────────────────────┐
│ Gradle                                 │
├────────────────────────────────────────┤
│                                        │
│ Build and run using:                   │
│   ⚪ Gradle (Default)                  │
│   ⚪ IntelliJ IDEA                     │
│                                        │
│ Gradle JDK:                           │
│   ┌──────────────────────────────┐    │
│   │ 21                        ▼  │ ← HIER! Dropdown öffnen
│   └──────────────────────────────┘    │
│                                        │
│   Optionen im Dropdown:               │
│   ┌──────────────────────────────┐    │
│   │ 21                           │    │
│   │ 17                           │    │
│   │ 11                           │    │
│   │ ─────────────────────────    │    │
│   │ Download JDK...          ← Falls 21 fehlt
│   │ Add JDK...                   │    │
│   └──────────────────────────────┘    │
│                                        │
│         [ Apply ]  [ OK ]              │
└────────────────────────────────────────┘
```

### **Aktion:**
1. ✅ **Wähle "21"** aus dem Dropdown
2. ✅ **Falls "21" fehlt:** Klicke **"Download JDK..."**
   - Version: `21`
   - Vendor: `Eclipse Temurin (AdoptOpenJDK HotSpot)`
3. ✅ Klicke **"Apply"** → **"OK"**

---

## 📍 **ORT 2: Project SDK**

### **Weg zum Setting:**

```
File (oben links)
  └─ Project Structure (oder Ctrl + Alt + Shift + S)
      └─ Project (links im Menü)
          └─ [HIER] SDK ← Dropdown-Menü rechts
```

### **Was du siehst:**

```
┌─────────────────────────────────────────────────────────────┐
│ Project Structure                                       × │
├─────────────────────────────────────────────────────────────┤
│ ┌─ Project Settings ────────┐ ┌─ Project ──────────────┐   │
│ │  ├─▶ Project           ← HIER KLICKEN             │   │
│ │  ├─  Modules               │ │                        │   │
│ │  ├─  Libraries             │ │ Name: FishITPlayer     │   │
│ │  └─  Facets                │ │                        │   │
│ ├─ Platform Settings ─────────┤ │ SDK:                   │   │
│ │  ├─  SDKs                  │ │ ┌────────────────────┐ │   │
│ │  └─  Global Libraries      │ │ │ 21              ▼  │ │ ← HIER!
│ └───────────────────────────────┘ └────────────────────┘ │   │
│                                   │                        │   │
│                                   │ Language level:        │   │
│                                   │ ┌────────────────────┐ │   │
│                                   │ │ SDK default (21)▼  │ │   │
│                                   │ └────────────────────┘ │   │
│                                   │                        │   │
│                                   │     [ Apply ] [ OK ]   │   │
│                                   └────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### **Aktion:**
1. ✅ **SDK:** Wähle **"21"**
2. ✅ **Language level:** Wähle **"SDK default (21 - ...)"**
3. ✅ Klicke **"Apply"** → **"OK"**

---

## 🚀 **Nach den Änderungen:**

### **WICHTIG: Gradle Sync ausführen!**

```
File (oben links)
  └─ Sync Project with Gradle Files
```

Oder klicke auf das 🔄 Elephant-Icon in der Toolbar.

**Warte 2-5 Minuten** bis du siehst:
```
BUILD SUCCESSFUL in 45s
```

---

## ❓ **Ich sehe "21" nicht im Dropdown!**

### **Lösung: JDK 21 herunterladen**

Im **Gradle JDK** Dropdown:
1. Ganz nach unten scrollen
2. Klicke **"Download JDK..."**
3. Im Dialog:
   - **Version:** `21`
   - **Vendor:** `Eclipse Temurin (AdoptOpenJDK HotSpot)`
   - **Location:** Standard belassen
4. Klicke **"Download"**
5. Warten (~2-5 Minuten)
6. Danach erscheint "21" in der Liste

### **Alternative: Via winget (PowerShell als Admin)**

**Einzeiler - Installiert und konfiguriert JDK 21 permanent:**

```powershell
winget install EclipseAdoptium.Temurin.21.JDK --accept-package-agreements --accept-source-agreements; $jdkPath = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Filter "jdk-21*" -Directory | Select-Object -First 1).FullName; [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkPath, "Machine"); [Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path", "Machine") + ";$jdkPath\bin", "Machine"); Write-Host "✓ JDK 21 installiert und JAVA_HOME gesetzt: $jdkPath" -ForegroundColor Green
```

**WICHTIG:** PowerShell **als Administrator** ausführen!

**Prüfen nach Installation:**
```powershell
# Neue PowerShell öffnen (nicht Admin nötig)
java -version  # Sollte 21.x.x zeigen
echo $env:JAVA_HOME  # Sollte JDK-Pfad zeigen
```

Dann IDE neu starten.

---

## 🧪 **Testen ob es funktioniert:**

Öffne Terminal in Android Studio (`Alt + F12`):

```powershell
# Dieses Script prüft alles
.\fix-java-home-windows.ps1

# Oder manuell:
java -version  # Sollte 21.x.x zeigen
.\gradlew.bat --version  # Sollte mit JDK 21 laufen
```

---

## 📸 **Screenshot-Referenz (Text)**

```
┌────────────────────────────────────────────────────────────┐
│ Android Studio 2024.x                                      │
├────────────────────────────────────────────────────────────┤
│ [File] [Edit] [View] [Navigate] [Code] [Analyze] [Build]  │
│   │                                                         │
│   ├─ Settings... (Ctrl+Alt+S)       ← HIER FÜR GRADLE JDK │
│   ├─ Project Structure... (Ctrl+Alt+Shift+S) ← FÜR PROJECT SDK
│   ├─ ...                                                    │
│   └─ Sync Project with Gradle Files ← NACH ÄNDERUNGEN     │
└────────────────────────────────────────────────────────────┘
```

---

## ✅ **Checkliste:**

- [ ] Gradle JDK auf 21 gesetzt (Settings → Build Tools → Gradle)
- [ ] Project SDK auf 21 gesetzt (Project Structure → Project)
- [ ] Gradle Sync durchgeführt (File → Sync Project...)
- [ ] BUILD SUCCESSFUL erschienen
- [ ] `java -version` zeigt 21.x.x
- [ ] `.\gradlew.bat --version` läuft ohne Fehler

---

**Bei weiteren Problemen:** Führe `.\fix-java-home-windows.ps1` aus (Script prüft alles automatisch)
