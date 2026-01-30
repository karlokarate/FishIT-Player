 ⚡ JDK 21 Installation - Quick Reference

## 🚀 **Einzeiler**

### ✅ **Falls JDK 21 bereits installiert ist (nur Umgebungsvariablen setzen):**

**User-Level (KEINE Admin-Rechte nötig):**
```powershell
$jdkPath = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Filter "jdk-21*" -Directory | Select-Object -First 1).FullName; [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkPath, "User"); [Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path", "User") + ";$jdkPath\bin", "User"); Write-Host "✓ JAVA_HOME gesetzt: $jdkPath" -ForegroundColor Green; Write-Host "⚠ WICHTIG: Schließe ALLE PowerShell-Fenster und öffne ein neues!" -ForegroundColor Yellow
```

**System-Level (Admin-Rechte erforderlich):**
```powershell
$jdkPath = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Filter "jdk-21*" -Directory | Select-Object -First 1).FullName; [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkPath, "Machine"); [Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path", "Machine") + ";$jdkPath\bin", "Machine"); Write-Host "✓ JAVA_HOME gesetzt (System-weit): $jdkPath" -ForegroundColor Green
```

### 🆕 **Komplett-Installation (JDK + Konfiguration):**

**NUR als Administrator ausführen:**
```powershell
winget install EclipseAdoptium.Temurin.21.JDK --accept-package-agreements --accept-source-agreements; $jdkPath = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Filter "jdk-21*" -Directory | Select-Object -First 1).FullName; [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkPath, "Machine"); [Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path", "Machine") + ";$jdkPath\bin", "Machine"); Write-Host "✓ JDK 21 installiert: $jdkPath" -ForegroundColor Green
```

**⚠️ Wenn Fehler "Registrierungszugriff unzulässig" → PowerShell nicht als Admin! Nutze User-Level Befehl oben.**

---

## 📋 **Schritt-für-Schritt**

### 1️⃣ PowerShell als Admin öffnen
- **Windows-Taste** drücken
- Tippe: `PowerShell`
- **Rechtsklick** → "Als Administrator ausführen"

### 2️⃣ Einzeiler ausführen
- Obigen Befehl kopieren
- In PowerShell einfügen (Rechtsklick)
- Enter drücken
- Warten (~2-5 Min)

### 3️⃣ Prüfen (neue PowerShell)
```powershell
java -version
echo $env:JAVA_HOME
```

### 4️⃣ Android Studio konfigurieren
- **IDE neu starten**
- `File → Settings → Build Tools → Gradle → Gradle JDK: 21`
- `File → Sync Project with Gradle Files`

---

## 🛠️ **Alternative: Interaktives Script**

```powershell
# Als Administrator ausführen
.\install-jdk21.ps1
```

Dieses Script:
- ✅ Prüft Admin-Rechte
- ✅ Prüft vorhandene Installation
- ✅ Installiert JDK 21
- ✅ Setzt JAVA_HOME + PATH
- ✅ Zeigt Next Steps

---

## ✅ **Was wird gemacht?**

| Aktion | Beschreibung |
|--------|--------------|
| `winget install` | Installiert JDK 21 (Eclipse Temurin) |
| `JAVA_HOME` | Setzt System-Variable auf JDK-Pfad |
| `PATH` | Fügt `bin`-Verzeichnis zu PATH hinzu |
| `Machine` | **Permanent** für alle User |

---

## 🧪 **Test nach Installation**

```powershell
# Neue PowerShell öffnen (NICHT als Admin)
java -version
# Erwarte: openjdk version "21.x.x"

javac -version  
# Erwarte: javac 21.x.x

echo $env:JAVA_HOME
# Erwarte: C:\Program Files\Eclipse Adoptium\jdk-21...

.\gradlew.bat --version
# Sollte ohne Fehler durchlaufen
```

---

## 📁 **Dateien**

| Datei | Zweck |
|-------|-------|
| `install-jdk21.ps1` | Interaktives Installations-Script |
| `fix-java-home-windows.ps1` | Prüft Installation |
| `docs/dev/WO_IST_JAVA_SDK_SETTING.md` | IDE-Anleitung |

---

## 🆘 **Troubleshooting**

### "winget: command not found"
→ Windows zu alt. Installiere **App Installer** aus Microsoft Store

### "Access denied"
→ PowerShell **als Administrator** ausführen

### "JDK 21 not found after install"
→ Neue PowerShell öffnen (alte schließen)

### IDE findet JDK nicht
→ IDE neu starten, dann Settings → Gradle → Gradle JDK → Refresh

---

**Vollständige Dokumentation:** [docs/dev/LOCAL_SETUP.md](docs/dev/LOCAL_SETUP.md)
