---
applyTo: '**'
---

# Scope Guard System v3.2 – Mandatory Pre-Edit Check

> **⚠️ HARD RULE:** Before ANY file modification, agents MUST check the Scope Guard system.

---

## 🚨 MANDATORY: Request Classification (EVERY User Message)

Before responding to ANY user request, the agent MUST:

### Step 1: Classify the Request

| Type | Description | Scope Guard Required? |
|------|-------------|----------------------|
| **QUESTION** | User asks for information, explanation, analysis | ❌ No - respond directly |
| **CODE_CHANGE** | User wants to create, edit, delete, refactor code | ✅ **YES - MUST call scope_guard_start_session FIRST** |
| **CODE_REVIEW** | User wants code analyzed for issues | ⚠️ Optional - helps understand context |
| **PLANNING** | User wants to discuss approach before coding | ❌ No - but mention which scopes will be affected |

### Step 2: For CODE_CHANGE Requests

```
1. Call scope_guard_start_session(agent_mode="interactive")
2. Identify which files will be modified
3. For EACH file: call scope_guard_check(file_path="...")
4. If ANY check returns BLOCKED/READ_ONLY → STOP and inform user
5. Only then proceed with code changes
```

### Step 3: Quick Classification Examples

| User Says | Classification | Action |
|-----------|----------------|--------|
| "Was macht diese Funktion?" | QUESTION | Respond directly |
| "Füge einen Logger hinzu" | CODE_CHANGE | → scope_guard_start_session |
| "Refactore XYZ" | CODE_CHANGE | → scope_guard_start_session |
| "Prüfe den Code auf Bugs" | CODE_REVIEW | Optional scope check |
| "Wie sollten wir das implementieren?" | PLANNING | Discuss, mention scopes |
| "Erstelle eine neue Klasse" | CODE_CHANGE | → scope_guard_start_session |

---

## Quick Reference

```
.scope/
├── catalog-sync.scope.json    # core/catalog-sync/** 
├── persistence.scope.json     # core/persistence/**
├── xtream-*.scope.json        # Xtream source scopes (7 total)
├── scope-guard.config.json    # Server configuration with bundles
└── scope-guard.schema.json    # Scope file schema

tools/
└── scope-manager.py           # MANDATORY for scope creation/modification
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

## 🆕 Working Scope Boundaries (v3.1)

When working on a focused task, agents can declare a **working scope** to prevent scope creep:

```
scope_guard_start_session(
  agent_mode="interactive",
  working_scope="xtream-transport-core",    // Focus on this scope
  allow_related_scopes=true                  // Allow edits in related scopes
)
```

### Boundary Enforcement

When `working_scope` is set:
- Edits **inside** working scope: `ALLOWED`
- Edits in **related scopes**: `IN_RELATED` (if `allow_related_scopes=true`)
- Edits **outside** boundaries: `BOUNDARY_VIOLATION` → **HARD BLOCK**

### When to Use

| Scenario | Use working_scope? |
|----------|-------------------|
| Focused refactoring task | ✅ Yes |
| Cross-cutting changes | ❌ No |
| Bug fix in specific module | ✅ Yes |
| General exploration | ❌ No |

---

## 🆕 File Ownership Model (v3.1)

Files can exist in **multiple scopes** with different ownership roles:

| Ownership | Meaning | Allowed Changes |
|-----------|---------|-----------------|
| `OWNER` | Primary scope, full control | Any structural changes |
| `CONSUMER` | Uses file, limited changes | Call sites only, no signatures |
| `SHARED` | Co-owned by multiple scopes | Coordinate with other owners |

### Multi-Scope Resolution

When a file is in multiple scopes:
1. If `working_scope` is set → use that scope's rules
2. If exactly one scope has `OWNER` → use that scope
3. Otherwise → `MULTI_SCOPE` status, agent must choose

### Setting Ownership

```bash
# Add file with ownership
python3 tools/scope-manager.py add-files xtream-transport-core \
  path/to/file.kt --ownership CONSUMER --shared-with xtream-data,xtream-pipeline-catalog

# Update existing file ownership
python3 tools/scope-manager.py set-ownership xtream-transport-core \
  path/to/file.kt SHARED --shared-with xtream-data
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
| `BOUNDARY_VIOLATION` | Outside working_scope | **HARD BLOCK** - cannot edit |
| `MULTI_SCOPE` | File in multiple scopes | Choose scope context |
| `IN_RELATED` | In related scope (allowed) | Proceed with caution |

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

## Available Tools (v3.2)

| Tool | Purpose | Required |
|------|---------|----------|
| `scope_guard_start_session` | Start session with mode & working scope | **First call** |
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
| `scope_guard_reload` | Reload scopes from disk | After scope-manager.py changes |
| `scope_guard_set_context` | Change working scope mid-session | Resolve MULTI_SCOPE |

---

## 🆕 Session Health Monitoring (v3.2)

Every response includes **session health metrics** that help agents maintain focus:

```json
{
  "_scopeGuard": {
    "version": "3.2",
    "sessionHealth": {
      "durationMinutes": 15,
      "filesChecked": 12,
      "scopesTouched": 3,
      "healthy": true
    },
    "guidance": {
      "warnings": []
    }
  }
}
```

### Focus Drift Detection

If you touch **more than 5 different scopes** in one session, you'll see:

```
⚠️ Focus drift: Touched 6 different scopes. Consider narrowing focus with working_scope.
```

**Action:** Call `scope_guard_summary` to consolidate, then start new session with `working_scope`.

### Session Duration Warnings

After **30+ minutes**, you'll see:

```
⏰ Session running 35 minutes. Consider periodic scope_guard_summary.
```

**Action:** Summarize progress with `scope_guard_summary`, then continue with fresh session.

### Why This Matters

- **Prevents scope creep:** Long sessions without boundaries lead to sprawling changes
- **Improves visibility:** User/parent agent sees what scopes were touched
- **Catches drift early:** Before too many files are modified outside focus area

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

## 🛠️ Scope Manager Script (MANDATORY for scope changes)

When creating new scopes or adding files to scopes, agents **MUST use the scope-manager.py script** instead of manually editing JSON files.

### Location
```
tools/scope-manager.py
```

### Commands

**List all scopes:**
```bash
python3 tools/scope-manager.py list
```

**Show scope info:**
```bash
python3 tools/scope-manager.py info <scope-id>
```

**Validate all scopes:**
```bash
python3 tools/scope-manager.py validate
```

**Create new scope:**
```bash
# Non-interactive (specify all params)
python3 tools/scope-manager.py create <scope-id> "<description>" \
  --module <module-path> --files <file1> <file2>

# Interactive wizard
python3 tools/scope-manager.py create --interactive
```

**Add files to existing scope:**
```bash
# Single files
python3 tools/scope-manager.py add-files <scope-id> <file1> <file2>

# From file list
python3 tools/scope-manager.py add-files <scope-id> --from-file files.txt

# Add as handlers (not criticalFiles)
python3 tools/scope-manager.py add-files <scope-id> --as-handlers <files>

# Add with ownership (v3.1)
python3 tools/scope-manager.py add-files <scope-id> <files> \
  --ownership CONSUMER --shared-with scope1,scope2
```

**Set file ownership (v3.1):**
```bash
python3 tools/scope-manager.py set-ownership <scope-id> <file-path> <OWNER|CONSUMER|SHARED> \
  --shared-with scope1,scope2
```

**Add new module to scope:**
```bash
python3 tools/scope-manager.py add-module <scope-id> <module-path> <files>
```

### Why Use the Script?

| Manual Edit Risk | Script Benefit |
|------------------|----------------|
| Missing required fields | Auto-includes all schema fields |
| Invalid JSON syntax | Validates before saving |
| Duplicate files | Checks and skips duplicates |
| Overwriting existing data | Merges safely |
| Inconsistent format | Consistent indentation |
| Wrong timestamps | Auto-updates lastVerified |

### HARD RULE

> **⚠️ Agents MUST use `scope-manager.py` for ALL scope modifications.**
> 
> Do NOT manually edit `.scope/*.scope.json` files directly unless:
> 1. Script cannot handle the specific change
> 2. User explicitly requests manual edit
> 3. Debugging script-generated output

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
    "version": "3.1",
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
