# MCP Server Setup for Cloud Agents

This document describes the MCP (Model Context Protocol) server configuration for cloud-based Copilot agents in GitHub Actions workflows.

## Overview

Cloud agents (GitHub Actions, Copilot Workspace) are automatically configured with MCP servers to enable:
- **GitHub MCP Server**: Full GitHub API integration (issues, PRs, code search, CI/CD)
- **Sequential Thinking MCP**: Long-term context maintenance across task chains
- **Custom FishIT Pipeline MCP**: Domain-specific Xtream/Telegram pipeline access

## Required Secret

### `COPILOT_MCP_TOKEN`

A GitHub Personal Access Token (PAT) with the following permissions:
- `repo` - Full control of private repositories
- `workflow` - Update GitHub Action workflows
- `read:org` - Read org and team membership

**Setup:**
1. Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token with required permissions
3. Add to repository secrets as `COPILOT_MCP_TOKEN`

## Workflow Integration

### `copilot-setup-steps.yml`

The main setup workflow for Copilot agents now includes MCP server configuration:

```yaml
env:
  COPILOT_MCP_TOKEN: ${{ secrets.COPILOT_MCP_TOKEN }}
```

**MCP Setup Steps:**
1. **Docker Setup**: Installs Docker Buildx and caches images
2. **GitHub MCP Server**: Pulls and caches `ghcr.io/github/github-mcp-server:latest`
3. **Node.js Setup**: Configures Node.js 20 with NPM cache
4. **Sequential Thinking MCP**: Pre-installs `@modelcontextprotocol/server-sequential-thinking`
5. **Custom JAR Cache**: Caches `tools/mcp-server/build/libs/*.jar`
6. **MCP Configuration**: Creates `~/.config/copilot/mcp.json` with server definitions

### Cache Keys

All MCP dependencies are cached for performance:

- **NPM packages**: `npm-mcp-${{ runner.os }}-v1`
- **Custom JAR**: `mcp-jar-${{ runner.os }}-${{ hashFiles('tools/mcp-server/**/*.kt', 'tools/mcp-server/**/*.gradle*') }}`

Cache keys use stable version numbers (v1) to maintain cache across workflow updates. Docker images are pulled directly without caching as they're efficiently cached by Docker itself.

## MCP Configuration Structure

Cloud agents receive the following MCP configuration at `~/.config/copilot/mcp.json`:

```json
{
  "mcpServers": {
    "github": {
      "command": "docker",
      "args": ["run", "-i", "--rm", "-e", "GITHUB_PERSONAL_ACCESS_TOKEN", 
               "ghcr.io/github/github-mcp-server"],
      "env": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "${COPILOT_MCP_TOKEN}"
      }
    },
    "sequential-thinking": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-sequential-thinking@0.1.0"]
    }
  }
}
```

## Usage in Task Chains

Cloud agents using MCP servers can:

1. **Create and manage issues/PRs** via GitHub MCP
2. **Search code** across the repository
3. **Access CI/CD logs** for debugging
4. **Maintain long-term context** across multiple PRs using Sequential Thinking
5. **Access custom pipeline operations** via FishIT Pipeline MCP (if JAR is built)

## Verification

The workflow includes a verification step that outputs:

```
=== MCP Server Setup Verification ===
Docker: Docker version 24.x.x
Node.js: v20.x.x
NPM: 10.x.x
MCP Config: ✓ exists

✓ MCP servers ready for cloud Copilot agents
  - GitHub MCP: Full GitHub integration
  - Sequential Thinking: Long-term context
  - Custom Pipeline: Domain-specific access (if built)
```

## Troubleshooting

### MCP Server Not Working

1. **Check Secret**: Ensure `COPILOT_MCP_TOKEN` is set in repository secrets
2. **Verify Permissions**: Token must have `repo`, `workflow`, and `read:org` permissions
3. **Check Logs**: Review workflow logs for MCP setup step failures
4. **Cache Issues**: Clear cache by updating cache keys (increment version: `v1` → `v2`)

### Docker Pull Failures

The workflow includes fallback: `|| echo "Image pull failed, will retry on demand"`

If Docker pull fails, the MCP server will attempt to pull on first use.

### Sequential Thinking Installation

Pre-installation uses pinned version for supply chain security:
```bash
npm install -g @modelcontextprotocol/server-sequential-thinking@0.1.0
```

This ensures a verified, immutable version is used instead of downloading arbitrary code on each run.

## Related Files

- `.github/workflows/copilot-setup-steps.yml` - Main setup workflow
- `.github/workflows/setup-mcp-servers.yml` - Reusable MCP setup workflow (for future use)
- `AGENTS.md` Section 16 - Complete MCP documentation
- `.github/copilot-instructions.md` - MCP integration guide

## Reference

- GitHub MCP Server: https://github.blog/ai-and-ml/generative-ai/a-practical-guide-on-how-to-use-the-github-mcp-server/
- MCP Specification: https://modelcontextprotocol.io/docs/learn/server-concepts
- Sequential Thinking: https://github.com/modelcontextprotocol/servers/tree/main/src/sequential-thinking
