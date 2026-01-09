# MCP Server Setup for GitHub Copilot

This guide explains how to configure MCP (Model Context Protocol) servers for GitHub Copilot Coding Agent in the FishIT-Player repository.

## Overview

The repository provides three MCP servers for enhanced Copilot capabilities:

1. **GitHub MCP**: Full GitHub API access (issues, PRs, code search, CI/CD)
2. **Sequential Thinking**: Long-term context for multi-step task chains  
3. **FishIT Pipeline**: Domain-specific tools for Xtream API, Telegram, and pipeline testing

## Configuration Files

- **`.github/copilot-mcp-settings.json`** - Copilot Coding Agent configuration
- **`.vscode/mcp.json`** - Local VS Code development
- **`.devcontainer/devcontainer.json`** - Codespaces environment
- **`.github/workflows/copilot-setup-steps.yml`** - Cloud agents (GitHub Actions)

## For Repository Maintainers

### 1. Set Up GitHub Environment with Secrets

The FishIT Pipeline MCP server requires API credentials to function. These should be configured as GitHub Environment secrets.

**Steps:**

1. Navigate to the repository settings:
   ```
   https://github.com/karlokarate/FishIT-Player/settings/environments
   ```

2. Create a new environment named **`copilot`** (if it doesn't exist)

3. Add the following secrets to the `copilot` environment:

   | Secret Name | Description | Where to Get |
   |-------------|-------------|--------------|
   | `COPILOT_MCP_TELEGRAM_API_ID` | Telegram API ID (numeric) | https://my.telegram.org/apps |
   | `COPILOT_MCP_TELEGRAM_API_HASH` | Telegram API Hash (hexadecimal) | https://my.telegram.org/apps |
   | `COPILOT_MCP_XTREAM_URL` | Xtream server URL | Your Xtream provider |
   | `COPILOT_MCP_XTREAM_USER` | Xtream username | Your Xtream account |
   | `COPILOT_MCP_XTREAM_PASS` | Xtream password | Your Xtream account |

4. For GitHub MCP server authentication, the `COPILOT_MCP_TOKEN` repository secret is automatically used if configured

**Note:** All credentials are now managed through environment variables for security.

### 2. Build the FishIT Pipeline MCP Server

The MCP server is a standalone JAR that needs to be built before use:

```bash
# Build the fat JAR
./gradlew :tools:mcp-server:fatJar

# Verify the JAR was created
ls -la tools/mcp-server/build/libs/mcp-server-1.0.0-all.jar
```

The build process:
- Uses JDK 21
- Bundles all dependencies (OkHttp, TDLib, MCP SDK)
- Creates a ~50MB JAR file
- Takes about 30-60 seconds

**Automatic Build:** The `copilot-setup-steps.yml` workflow caches the JAR and rebuilds if needed.

### 3. Test the MCP Server Locally

You can test the server manually in a terminal:

```bash
java -jar tools/mcp-server/build/libs/mcp-server-1.0.0-all.jar
```

The server communicates via STDIO (standard input/output) using the MCP protocol.

## For Copilot Users

### Using MCP Tools in Copilot Chat

Once configured, you can use MCP tools directly in Copilot Chat:

**Example Queries:**

```
# Xtream API
"Use xtream_vod_categories to list all movie categories"
"Get VOD streams from category 5 and show the first 3 items"
"Generate an Xtream playback URL for VOD ID 12345"

# Telegram Integration
"Show me the TgMessage schema"
"Generate a mock Telegram video message"
"Check Telegram TDLib configuration status"

# Pipeline Testing
"Parse this title: The.Movie.2024.1080p.BluRay.x264-GROUP"
"Normalize this Xtream VOD item to RawMediaMetadata"
"What MediaType would be detected for a 25-minute video?"
```

### Available MCP Tools

Run in Copilot Chat to see all available tools:
```
"What MCP tools are available?"
```

Or check `tools/mcp-server/README.md` for the complete tool list.

## Troubleshooting

### "MCP server not found" error

**Cause:** The JAR hasn't been built yet.

**Solution:**
```bash
./gradlew :tools:mcp-server:fatJar
```

### Telegram tools return errors

**Cause:** Missing or invalid Telegram API credentials.

**Solution:**
1. Verify `COPILOT_MCP_TELEGRAM_API_ID` and `COPILOT_MCP_TELEGRAM_API_HASH` are set
2. Get credentials from https://my.telegram.org/apps
3. Update GitHub Environment secrets

### "Command not found: java"

**Cause:** JDK 21 not installed.

**Solution:** 
- Codespaces: JDK 21 is pre-installed
- Local: Install JDK 21 from https://adoptium.net/

### Xtream API calls fail

**Possible Causes:**
1. Xtream server is down
2. Credentials expired/changed
3. Network connectivity issues

**Solution:**
- Test manually: `curl http://your-server.com:8080/player_api.php?username=YOUR_USER&password=YOUR_PASS`
- Verify environment secrets are configured correctly
- Check if server credentials have expired

### Using Repository Secrets vs Environment Secrets

**Question:** How to use `COPILOT_MCP_TOKEN` (repository secret) as an environment token?

**Answer:** Repository secrets and environment secrets work differently:

1. **Repository Secrets** (like `COPILOT_MCP_TOKEN`):
   - Configured at: https://github.com/karlokarate/FishIT-Player/settings/secrets/actions
   - Available in **all** workflows automatically
   - Used in `.github/workflows/copilot-setup-steps.yml` as `${{ secrets.COPILOT_MCP_TOKEN }}`

2. **Environment Secrets** (like `COPILOT_MCP_TELEGRAM_API_ID`):
   - Configured at: https://github.com/karlokarate/FishIT-Player/settings/environments
   - Only available when workflow specifies `environment: copilot`
   - More restrictive - requires approval rules

**To use COPILOT_MCP_TOKEN as an environment variable:**

Add it to the `copilot` environment secrets (in addition to or instead of repository secrets):
```
1. Go to: https://github.com/karlokarate/FishIT-Player/settings/environments
2. Select the "copilot" environment
3. Add secret: COPILOT_MCP_TOKEN
4. The value will automatically be available as ${env:COPILOT_MCP_TOKEN} in MCP configs
```

This allows the same token to be used both in workflows and MCP configurations.

## Security Notes

### Sensitive Data Handling

- **All credentials** now use environment variable references (`${env:COPILOT_MCP_*}`)
- **No hardcoded credentials** in configuration files
- **GitHub tokens** are automatically injected in Codespaces

### Best Practices

1. **Never commit** API credentials directly to files
2. **Use environment variables** for all sensitive data
3. **Rotate credentials** if accidentally exposed
4. **Limit scope** of GitHub tokens to minimum required permissions

## Architecture

```
GitHub Copilot Coding Agent
      │
      │ reads .github/copilot-mcp-settings.json
      ▼
┌─────────────────────────────────┐
│ MCP Servers (3 instances)       │
│  ├─ GitHub MCP (Docker)         │
│  ├─ Sequential Thinking (NPM)   │
│  └─ FishIT Pipeline (Java JAR)  │
└─────────────────────────────────┘
      │
      ▼
External APIs:
- GitHub API (via GITHUB_TOKEN)
- Xtream API (via HTTP)
- Telegram TDLib (via native libs)
```

## References

- **MCP Protocol Specification:** https://modelcontextprotocol.io/
- **FishIT Pipeline Server README:** `tools/mcp-server/README.md`
- **Copilot MCP Documentation:** `AGENTS.md` Section 16
- **Workflow Configuration:** `.github/workflows/copilot-setup-steps.yml`
