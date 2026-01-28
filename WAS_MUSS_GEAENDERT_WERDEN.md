# ✅ Was muss in der IDE geändert werden?

> **🎯 NEU: Visuelle Anleitung mit Screenshots-Text:**  
> **[WO_IST_JAVA_SDK_SETTING.md](docs/dev/WO_IST_JAVA_SDK_SETTING.md)** ← Zeigt GENAU wo jedes Setting ist!

---

## Schnellantwort:

### 1️⃣ **Terminal öffnen** (Alt + F12)
### 2️⃣ **Gradle JDK auf 21 setzen**
   - `File → Settings → Build Tools → Gradle → Gradle JDK: 21`
### 3️⃣ **Project SDK auf 21 setzen**  
   - `File → Project Structure → Project → SDK: 21`
### 4️⃣ **Gradle Sync** ausführen
   - `File → Sync Project with Gradle Files`
### 5️⃣ **IDE neu starten** (für MCP-Config)

---

## 📖 Detaillierte Anleitung

Siehe: **[docs/dev/IDE_SETUP_GUIDE.md](docs/dev/IDE_SETUP_GUIDE.md)**

Diese Datei enthält:
- Screenshot-ähnliche Schritt-für-Schritt-Anleitung
- Wo jedes Setting zu finden ist
- Was zu erwarten ist
- Troubleshooting für häufige Probleme

---

## 🔍 Schnell-Check im Terminal

Öffne Terminal (Alt + F12) und führe aus:

```powershell
# Quick Test
.\scripts\quick-test.ps1

# Falls Execution Policy blockiert:
powershell -ExecutionPolicy Bypass -File .\scripts\quick-test.ps1

# Oder manuell prüfen:
java -version  # Sollte 21.x.x zeigen
.\gradlew.bat --version  # Sollte Gradle 8.13 zeigen
```

---

## ❓ Warum diese Änderungen?

| Was | Warum |
|-----|-------|
| **JDK 21** | Projekt benötigt Java 21 (siehe `build.gradle.kts`) |
| **Gradle JDK 21** | Gradle muss mit JDK 21 laufen für Kotlin 2.1.0 |
| **Gradle Sync** | IDE muss Projekt-Struktur neu laden nach JDK-Änderung |
| **IDE Neustart** | MCP-Konfiguration wird nur beim Start geladen |

---

## 📁 Weitere Hilfe

| Problem | Lösung |
|---------|--------|
| JDK 21 nicht in Liste | [IDE_SETUP_GUIDE.md](docs/dev/IDE_SETUP_GUIDE.md) → "JDK 21 not found" |
| Gradle Sync schlägt fehl | `.\gradlew.bat --stop` dann neu syncen |
| Terminal zeigt falsches Verzeichnis | `cd C:\Users\admin\StudioProjects\FishIT-Player` |
| MCP Tools nicht verfügbar | IDE neu starten, 30 Sek warten |

---

## ✅ Nach Setup

```powershell
# Ersten Build starten
.\gradlew.bat :app-v2:assembleDebug
```

**Erwartetes Ergebnis:** `BUILD SUCCESSFUL in Xs`

---

**Vollständige Dokumentation:** [SETUP_COMPLETE.md](SETUP_COMPLETE.md)
