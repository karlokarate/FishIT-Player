# RAM-Optimierung & Crash-Prevention für Codespaces

Dieses Workspace ist für stabile Builds in 8GB Codespaces optimiert und verhindert OOM-Crashes.

## 🛡️ Automatische Schutzmaßnahmen

### Memory Monitor (Automatisch aktiv)

- **Läuft im Hintergrund** und überwacht Speicherverbrauch alle 30 Sekunden
- **Automatische Bereinigung** bei >85% RAM-Auslastung
- **Logs:** `/tmp/memory-monitor.log`
- **Status prüfen:** `ps aux | grep monitor-memory`

**Manuelle Steuerung:**

```bash
# Monitor starten
nohup bash .devcontainer/monitor-memory.sh > /tmp/memory-monitor.log 2>&1 &

# Monitor stoppen
kill $(cat /tmp/memory-monitor.pid)

# Logs anzeigen
tail -f /tmp/memory-monitor.log
```

### Safe Build Wrapper

Nutze `./scripts/build/safe-build.sh` statt `./gradlew` für garantiert stabile Builds:

```bash
# Debug Build
./scripts/build/safe-build.sh assembleDebug

# Release Build
./scripts/build/safe-build.sh assembleRelease

# Clean Build
./scripts/build/safe-build.sh clean assembleDebug

# Mit zusätzlichen Argumenten
./scripts/build/safe-build.sh assembleDebug --stacktrace
```

**Features:**

- ✅ Pre-Build Memory Check & Cleanup
- ✅ Konservative RAM-Limits (max 1.3GB)
- ✅ Max 2 parallele Worker
- ✅ Post-Build Cleanup bei >75% RAM
- ✅ Kein Daemon (vermeidet RAM-Leak)

## 📊 VS Code Tasks (Ctrl+Shift+B)

Optimierte Build-Tasks verfügbar:

1. **Safe Build Debug** (Standard) - Sicherer Debug-Build
2. **Safe Build Release** - Sicherer Release-Build
3. **Clean Build** - Clean + Debug Build
4. **Stop All Daemons** - Alle Gradle Daemons beenden
5. **Memory Cleanup** - Sofortige RAM-Bereinigung
6. **Show Memory Status** - Aktuellen RAM-Status anzeigen

## ⚙️ Konfiguration

### Gradle Memory Limits

`gradle.properties` bleibt bewusst auf den Default-Werten (Ø 2 GB Heap, parallele Builds), damit GitHub Actions
und externe Builds genau die gleiche Konfiguration sehen wie vorher.

Die lokale RAM-Restriktion wird ausschließlich über das Workspace-Setup geregelt:

- `./scripts/build/safe-build.sh` setzt `GRADLE_OPTS`, `--max-workers=2`, `--no-daemon` sowie JVM- und Kotlin-Daemon-Flags.
- VS Code-Terminals exportieren dieselben Limits (`terminal.integrated.env.linux.GRADLE_OPTS`).
- Optional kann man eine persönliche `~/.gradle/gradle.properties` mit den Limits anlegen (ergibt nur lokalen Effekt).

Wenn du ohne Wrapper bauen musst, verwende trotzdem die zuvor genannten Env-Variablen oder ruf den Wrapper aus dem Terminal.

### Language Server Limits

`.vscode/settings.json` limitiert alle Language Servers:

- **Java LS:** 1024 MB (war 2048 MB)
- **Kotlin LS:** 768 MB
- **TypeScript:** 1024 MB
- **Gradle Auto-Build:** Deaktiviert

### Terminal Environment

Alle Terminal-Sessions haben automatisch:

```bash
GRADLE_OPTS="-Xmx1536m ..."
_JAVA_OPTIONS="-Xmx1536m ..."
```

## 🔧 Manuelle RAM-Bereinigung

Wenn Builds dennoch fehlschlagen:

```bash
# Volle Bereinigung
./gradlew --stop
pkill -f "org.javacs.kt"
pkill -f "redhat.java"
rm -rf app/build/intermediates
rm -rf .gradle/*/fileChanges

# Oder nutze VS Code Task: "Memory Cleanup"
```

## 📈 Monitoring

```bash
# RAM-Status
free -h

# Top Memory-Fresser
ps aux --sort=-%mem | head -10

# Gradle Daemons
ps aux | grep gradle

# Monitor Logs
tail -f /tmp/memory-monitor.log
```

## 🚨 Troubleshooting

### Build stirbt mit "daemon disappeared"

```bash
# Reduziere Workers weiter
./gradlew assembleDebug --no-daemon --max-workers=1
```

### Kotlin LS crasht ständig

Deaktiviere temporär in VS Code:

```json
"kotlin.languageServer.enabled": false
```

### Extrem langsame Builds

```bash
# Deaktiviere parallele Builds
./gradlew assembleDebug --no-parallel --no-daemon
```

## 📋 Best Practices

1. ✅ **Immer** `scripts/build/safe-build.sh` für große Builds nutzen
2. ✅ **Regelmäßig** Tasks "Stop All Daemons" ausführen
3. ✅ **Vermeiden:** Mehrere simultane Builds
4. ✅ **Prüfen:** Memory Monitor läuft (`ps aux | grep monitor`)
5. ✅ **Bereinigen:** Nach Release-Builds `app/build/` löschen

## 📊 Speicher-Budget (8GB Codespaces)

| Komponente   | RAM       | Status        |
| ------------ | --------- | ------------- |
| System       | ~1.5 GB   | Fix           |
| VS Code      | ~900 MB   | Fix           |
| Java LS      | ~200 MB   | Optimiert     |
| Kotlin LS    | ~150 MB   | Optimiert     |
| Gradle Build | ~1.3 GB   | Limitiert     |
| **Reserve**  | **~4 GB** | **✅ Sicher** |

## 🔄 Auto-Start beim Codespace-Start

Der Memory Monitor startet automatisch via `.devcontainer/post-create.sh`.
Keine manuelle Konfiguration nötig!
