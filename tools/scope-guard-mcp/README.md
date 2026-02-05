# Scope Guard MCP Server

Enforces reading `.scope/*.scope.json` files before editing covered files.

## Installation

Already configured in `.vscode/mcp.json`. Server starts automatically with VS Code.

## Available Tools

| Tool | Purpose |
|------|---------|
| `scope_guard_check` | **CALL FIRST** - Check if file edit is allowed |
| `scope_guard_read` | Mark a scope as read (after reviewing) |
| `scope_guard_get` | Get full scope file contents |
| `scope_guard_list` | List all scopes and their read status |

## Workflow

```
1. Agent wants to edit: core/catalog-sync/DefaultXtreamSyncService.kt

2. Agent calls: scope_guard_check(file_path="core/catalog-sync/...")
   → Returns: BLOCKED - must read scope 'catalog-sync' first

3. Agent calls: scope_guard_get(scope_id="catalog-sync")
   → Returns: Full scope file with invariants, forbidden patterns, etc.

4. Agent reviews the scope content

5. Agent calls: scope_guard_read(scope_id="catalog-sync")
   → Returns: OK - scope marked as read

6. Agent calls: scope_guard_check(file_path="core/catalog-sync/...")
   → Returns: ALLOWED - with reminder of invariants

7. Agent proceeds with edit
```

## Building

```bash
cd tools/scope-guard-mcp
npm install
npm run build
```

## Testing

```bash
WORKSPACE_ROOT=/workspaces/FishIT-Player node dist/index.js
```

## Session State

- Scope read status is **per-session** (resets when server restarts)
- This is intentional: ensures fresh review each coding session

## Limitations

- Agent must voluntarily call `scope_guard_check` (not enforced by MCP protocol)
- No automatic blocking of `edit_file` calls
- Requires agent instructions to mandate usage

## Future Improvements

- VS Code extension that intercepts file saves
- Pre-commit hook validation
- Integration with Copilot custom instructions
