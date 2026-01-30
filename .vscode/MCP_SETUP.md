# MCP Server Setup für Windows

## Aktuelle Konfiguration

Die MCP-Server sind in `.vscode/mcp.json` konfiguriert:

### ✅ Sequential Thinking Server
- **Status**: Funktioniert sofort
- **Package**: `@modelcontextprotocol/server-sequential-thinking`
- **Verwendung**: Automatisch via `npx` installiert

### ⚠️ FishIT Pipeline Server
- **Status**: Deaktiviert (muss gebaut werden)
- **Typ**: Eigener Java MCP Server

## FishIT Pipeline Server aktivieren

### Voraussetzungen
- Java 21 (bereits installiert: OpenJDK Temurin 21.0.7)
- Gradle (bereits im Projekt vorhanden)

### Build-Anweisungen

```powershell
# 1. Navigiere zum Projekt-Root
cd C:\Users\admin\StudioProjects\FishIT-Player

# 2. Baue den MCP Server
.\gradlew :tools:mcp-server:fatJar

# 3. Prüfe, ob JAR erstellt wurde
Test-Path "tools\mcp-server\build\libs\mcp-server-1.0.0-all.jar"
```

### Aktivierung nach Build

In `.vscode/mcp.json`:
```json
{
  "servers": {
    "fishit-pipeline": {
      "disabled": false  // ← Ändere true zu false
    }
  }
}
```

Oder entferne die Zeile `"disabled": true,` komplett.

### Umgebungsvariablen

Für Telegram-Features (optional):
```powershell
[Environment]::SetEnvironmentVariable("TELEGRAM_API_ID", "DEINE_API_ID", "User")
[Environment]::SetEnvironmentVariable("TELEGRAM_API_HASH", "DEIN_API_HASH", "User")
```

## Codespace vs. Lokale Entwicklung

| Server             | Codespace                          | Windows Lokal                       |
| ------------------ | ---------------------------------- | ----------------------------------- |
| Sequential Thinking| ✅ Aktiv                           | ✅ Aktiv                            |
| FishIT Pipeline    | ✅ Aktiv (auto-built)              | ⚠️ Manueller Build erforderlich    |
| GitHub MCP         | ✅ Aktiv (Docker)                  | ❌ Nicht konfiguriert (optional)   |

## Troubleshooting

### Fehler: "Unable to access jarfile"
→ FishIT Pipeline Server wurde noch nicht gebaut (siehe Build-Anweisungen oben)

### Fehler: "404 Not Found - @anthropics/sequential-thinking-mcp-server"
→ **Falsches Package!** Korrekt ist: `@modelcontextprotocol/server-sequential-thinking`

### Fehler: "EPERM: operation not permitted"
→ Berechtigungsproblem. Starte Android Studio als Administrator oder prüfe Ordnerrechte.

## Weitere Informationen

- **Copilot Instructions**: `.github/copilot-instructions.md` (Abschnitt "MCP Server Integration")
- **FishIT Pipeline Server Doku**: `tools/mcp-server/README.md`
- **Codespace Config**: `.devcontainer/devcontainer.json`
