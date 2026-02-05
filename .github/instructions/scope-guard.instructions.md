---
applyTo: '**'
---

# Scope Guard System v3.0 – Mandatory Pre-Edit Check

> **⚠️ HARD RULE:** Before ANY file modification, agents MUST check the Scope Guard system.

## Quick Reference

```
.scope/
├── catalog-sync.scope.json    # core/catalog-sync/** 
├── persistence.scope.json     # core/persistence/**
├── scope-guard.config.json    # Server configuration with bundles
└── scope-guard.schema.json    # Scope file schema
```

---

## 🚨 Agent Modes (NEW in v3.0)

### Interactive Mode (Default)
- Requires user confirmation for scope assignments
- Use when: Chat-based interactions, code reviews

### Unattended Mode 
- Auto-assigns files to suggested scopes
- **MUST call `scope_guard_summary` at end of session**
- Use when: Background agents, automated tasks, subagents

**Start every session with:**
```
scope_guard_start_session(agent_mode="interactive")  // or "unattended"
```

---

## Mandatory Workflow

### Step 1: Start Session
```
scope_guard_start_session(agent_mode="interactive")
```

### Step 2: Check Before Edit
```
scope_guard_check(file_path="path/to/file.kt")
```

Possible responses:

| Status | Meaning | Next Action |
|--------|---------|-------------|
| `EXCLUDED` | Build artifact, node_modules, etc. | Proceed freely |
| `READ_ONLY` | Protected path (legacy/, AGENTS.md) | **DO NOT EDIT** |
| `BUNDLE_BLOCKED` | Matches bundle pattern | Read bundle first |
| `BLOCKED` | In scope but not read | Read scope first |
| `UNTRACKED` | Not in any scope | Assign to scope |
| `ALLOWED` | Ready to edit | Proceed with invariants |

### Step 3: Read Scope/Bundle (if blocked)
```
scope_guard_get(scope_id="catalog-sync")      // View details
scope_guard_read(scope_id="catalog-sync")     // Acknowledge
// OR for bundles:
scope_guard_read_bundle(bundle_id="gradle-config")
```

### Step 4: Check Again
```
scope_guard_check(file_path="path/to/file.kt")
→ ALLOWED
```

### Step 5: End Session (MANDATORY for unattended)
```
scope_guard_summary(edited_files=["file1.kt", "file2.kt"])
```

---

## Bundles (Pre-defined File Groups)

Bundles group related files that should be treated atomically:

| Bundle ID | Description | Key Files |
|-----------|-------------|-----------|
| `gradle-config` | Build configuration | build.gradle.kts, libs.versions.toml |
| `agent-governance` | Agent rules | AGENTS.md (GENERATED), instructions/*.md |
| `scope-system` | Scope Guard itself | .scope/**, tools/scope-guard-mcp/** |
| `contracts` | Architecture contracts | contracts/*.md |
| `ci-infra` | CI/CD configuration | .github/workflows/**, .devcontainer/** |

**Bundle workflow:**
```
scope_guard_check(file_path="build.gradle.kts")
→ BUNDLE_BLOCKED, bundle_id="gradle-config"

scope_guard_read_bundle(bundle_id="gradle-config")
→ ACKNOWLEDGED, invariants to follow

scope_guard_check(file_path="build.gradle.kts")
→ ALLOWED
```

---

## Global Excludes (Auto-Allowed)

These paths never need scope checking:
- `**/build/**` - Build outputs
- `**/.gradle/**` - Gradle cache
- `**/node_modules/**` - NPM packages
- `**/.idea/**`, `**/*.iml` - IDE config
- `**/*.log`, `**/*.tmp` - Temp files
- `**/local.properties` - Local config
- `**/generated/**` - Generated code

---

## Read-Only Paths (NEVER Edit)

These paths are protected:
- `legacy/**` - Legacy code (reference only)
- `app/**` - Old app module (use app-v2/)
- `AGENTS.md` - Generated from AGENT_RULES_CANONICAL.md
- `.github/copilot-instructions.md` - Generated

If you try to edit these:
```
scope_guard_check(file_path="legacy/SomeFile.kt")
→ READ_ONLY, reason: "Path matches readOnlyPaths pattern"
```

**For AGENTS.md:** Edit `docs/meta/AGENT_RULES_CANONICAL.md` instead.

---

## Available Tools (v3.0)

| Tool | Purpose | Required |
|------|---------|----------|
| `scope_guard_start_session` | Start session with mode | **First call** |
| `scope_guard_check` | Check file access | **Before every edit** |
| `scope_guard_get` | View scope/bundle details | When blocked |
| `scope_guard_read` | Acknowledge scope | After reviewing |
| `scope_guard_read_bundle` | Acknowledge bundle | After reviewing |
| `scope_guard_list` | List all scopes/bundles | Discovery |
| `scope_guard_assign` | Assign untracked file | When UNTRACKED |
| `scope_guard_confirm` | Confirm assignment | Interactive mode |
| `scope_guard_request_new_scope` | Create new scope | Rare |
| `scope_guard_validate_code` | Check forbidden patterns | Pre-commit |
| `scope_guard_audit_log` | View history | Debugging |
| `scope_guard_summary` | End session report | **Unattended mode** |

---

## Unattended Agent Rules

If you are a **background agent** or **subagent**:

1. **Start with unattended mode:**
   ```
   scope_guard_start_session(agent_mode="unattended")
   ```

2. **Auto-assignments happen automatically** - no user confirmation needed

3. **MUST end with summary:**
   ```
   scope_guard_summary(
     edited_files=["path/to/edited1.kt", "path/to/edited2.kt"],
     notes="Refactored X to use Y pattern"
   )
   ```

4. **Summary MUST be included in your final report** to the user/parent agent

---

## Server Unavailability

If Scope Guard MCP server is not available:

```
⚠️ MUST inform user: 
"Scope Guard server is unavailable. Cannot verify scope requirements.
Please restart VS Code or check .vscode/mcp.json configuration."
```

**DO NOT proceed with edits** without explicit user acknowledgment.

---

## Response Format

Every tool response includes:

```json
{
  "_scopeGuard": {
    "version": "3.0",
    "timestamp": "...",
    "guidance": {
      "status": "BLOCKED",
      "agentMode": "interactive",
      "nextSteps": ["Call scope_guard_get...", "Then scope_guard_read..."],
      "warnings": [],
      "scopeSchema": { ... }  // When relevant
    }
  },
  // ... actual response data
}
```

**Always follow the `nextSteps` guidance!**

---

## Audit Trail

All actions logged to `.scope/audit.log`:
- Session starts/ends
- Check attempts
- Scope reads
- Assignments
- Auto-assignments (unattended mode)

Use `scope_guard_audit_log(limit=20)` to review.

Use `scope_guard_audit_log` to review recent history.
