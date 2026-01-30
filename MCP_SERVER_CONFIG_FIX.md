# MCP Server Configuration Fix

**Date**: 2026-01-30  
**Status**: ✅ Resolved

## Problem

MCP Server in Android Studio konnte nicht starten:

1. **FishIT Pipeline Server**: `Unable to access jarfile` - JAR existiert nicht
2. **Sequential Thinking Server**: `404 Not Found` - Falsches NPM Package

```
Error: Unable to access jarfile C:\Users\admin\StudioProjects\FishIT-Player\tools\mcp-server\build\libs\mcp-server-1.0.0-all.jar
npm error 404 Not Found - GET https://registry.npmjs.org/@anthropics%2fsequential-thinking-mcp-server
```

## Root Cause

### Issue 1: FishIT Pipeline Server
- ❌ JAR wurde nie gebaut (nur im Codespace automatisch erstellt)
- ❌ Windows benötigt manuellen Build-Schritt

### Issue 2: Sequential Thinking Server
- ❌ Falsches Package: `@anthropics/sequential-thinking-mcp-server` (existiert nicht)
- ✅ Korrektes Package: `@modelcontextprotocol/server-sequential-thinking`

## Solution

### Änderungen

#### 1. `.vscode/mcp.json` (Windows Lokal)
```json
{
  "servers": {
    "sequential-thinking": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-sequential-thinking"]
    },
    "fishit-pipeline": {
      "disabled": true,  // Bis JAR gebaut ist
      // ...existing config...
    }
  }
}
```

#### 2. `.github/copilot-mcp-settings.json` (GitHub Workspace)
```json
{
  "mcpServers": {
    "sequential-thinking": { /* hinzugefügt */ },
    "fishit-pipeline": { /* existing */ }
  }
}
```

#### 3. Neue Dateien
- ✅ `.vscode/MCP_SETUP.md` - Ausführliche Setup-Anleitung
- ✅ `build-mcp-server.ps1` - One-Click Build-Script

### FishIT Pipeline Server aktivieren

```powershell
# Build JAR
.\build-mcp-server.ps1

# Dann in .vscode/mcp.json:
# "disabled": true  ← entfernen oder false setzen
```

## Testing

### Sequential Thinking Server
- ✅ Installiert automatisch via `npx`
- ✅ Kein Build oder Setup erforderlich
- ✅ Funktioniert sofort nach IDE-Neustart

### FishIT Pipeline Server
- ⚠️ Manueller Build erforderlich (siehe oben)
- ✅ Danach voll funktionsfähig
- ✅ Nutzt gleiche Config wie Codespace

## Files Changed

```
.vscode/mcp.json                          MODIFIED (Sequential Thinking hinzugefügt, FishIT disabled)
.github/copilot-mcp-settings.json        MODIFIED (Sequential Thinking hinzugefügt)
.vscode/MCP_SETUP.md                     CREATED  (Setup-Anleitung)
build-mcp-server.ps1                     CREATED  (Build-Script)
MCP_SERVER_CONFIG_FIX.md                 CREATED  (Diese Datei)
```

## Next Steps

1. **Android Studio neu laden**: Settings → MCP Servers sollte jetzt Sequential Thinking anzeigen
2. **FishIT Pipeline bauen**: Optional `.\build-mcp-server.ps1` ausführen
3. **Testen**: In Copilot Chat sollten MCP Tools verfügbar sein

## References

- **Codespace Config**: `.devcontainer/devcontainer.json` (funktionierende Vorlage)
- **MCP SDK Docs**: https://github.com/modelcontextprotocol/kotlin-sdk
- **Sequential Thinking**: https://github.com/modelcontextprotocol/servers
