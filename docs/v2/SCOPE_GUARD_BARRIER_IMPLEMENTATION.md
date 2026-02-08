# Scope Guard Barrier Implementation

> **Version:** 1.0  
> **Date:** 2025-01-XX  
> **Status:** Implemented (Levels 1-2), Concept (Level 3)

---

## Overview

Scope Guard now operates as a **true technical barrier** in the Codespace environment,
not just an advisory system. This document describes the three enforcement levels.

---

## Enforcement Levels

### Level 1: Git Pre-Commit Hook ✅ IMPLEMENTED

**Location:** `.git/hooks/pre-commit`  
**Source:** `scripts/hooks/pre-commit`  
**Installer:** `scripts/install-scope-guard-hooks.sh`

**How It Works:**
1. Before each `git commit`, the hook is executed
2. Runs `scope-guard-cli.py check-staged` on all staged files
3. Blocks commit if any file is READ_ONLY or BUNDLE_BLOCKED
4. Shows clear error messages with instructions

**Enforcement:**
- ✅ Blocks commits to `AGENTS.md`, `.github/copilot-instructions.md`
- ✅ Blocks commits to `legacy/**`, `app/**`
- ✅ Shows which file to edit instead (for GENERATED files)
- ✅ Allows `--no-verify` bypass (with explicit acknowledgment)

**Limitation:**
- Can be bypassed with `git commit --no-verify`
- Not enforced for non-git saves

---

### Level 2: File Watcher ✅ IMPLEMENTED

**Python Version (cross-platform):** `tools/scope-guard-watcher.py`  
**Bash Version (Linux only):** `scripts/scope-guard-watcher.sh`

The Python version works on all platforms with a polling fallback when `watchdog` 
is not installed. The bash version requires `inotify-tools` (Linux only).

**How It Works:**
1. Watches workspace for file modifications in real-time
2. On any write to the watched directory, checks the file using CLI
3. Displays warning if file violates scope rules
4. Optional `--aggressive` mode auto-reverts changes
5. `--verbose` mode shows scope/bundle invariants

**Usage:**
```bash
# Python version (recommended - cross-platform)
python3 tools/scope-guard-watcher.py             # Basic watching
python3 tools/scope-guard-watcher.py --verbose   # Show scope/bundle details
python3 tools/scope-guard-watcher.py --aggressive  # Auto-revert (dangerous!)
python3 tools/scope-guard-watcher.py --quiet     # Only show BLOCKED

# Install watchdog for better performance (optional)
pip install watchdog

# Bash version (Linux with inotify-tools)
./scripts/scope-guard-watcher.sh
./scripts/scope-guard-watcher.sh --aggressive
```

**Watched Directories:**
- Root (for `AGENTS.md`, `README.md`, etc.)
- `core/`, `infra/`, `pipeline/`, `playback/`, `player/`
- `feature/`, `app-v2/`, `contracts/`, `docs/`
- `.scope/`, `.github/`, `legacy/`

**Limitation:**
- Must be manually started
- Aggressive mode can lose work
- Polling mode uses more CPU than inotify

---

### Level 3: VS Code Extension 📋 CONCEPT

**Status:** Not implemented - concept only

This would provide the **strongest guarantee** by intercepting saves at the editor level.

**VS Code API Used:**
```typescript
// In extension activate()
vscode.workspace.onWillSaveTextDocument((event) => {
  const filePath = event.document.uri.fsPath;
  const status = checkScopeGuard(filePath);
  
  if (status === 'READ_ONLY') {
    event.waitUntil(
      Promise.reject(new Error('Scope Guard: File is READ_ONLY'))
    );
  }
});
```

**Implementation Requirements:**
1. Create extension in `tools/scope-guard-vscode/`
2. Package as VSIX
3. Auto-install in devcontainer.json

**Extension `package.json`:**
```json
{
  "name": "scope-guard-vscode",
  "displayName": "Scope Guard",
  "version": "1.0.0",
  "engines": { "vscode": "^1.80.0" },
  "activationEvents": ["onStartupFinished"],
  "main": "./dist/extension.js",
  "contributes": {
    "commands": [
      {
        "command": "scopeGuard.checkFile",
        "title": "Scope Guard: Check Current File"
      },
      {
        "command": "scopeGuard.showStatus",
        "title": "Scope Guard: Show File Status"
      }
    ]
  }
}
```

**Extension `src/extension.ts`:**
```typescript
import * as vscode from 'vscode';
import * as childProcess from 'child_process';
import * as path from 'path';

export function activate(context: vscode.ExtensionContext) {
  const cliPath = path.join(
    vscode.workspace.workspaceFolders?.[0].uri.fsPath || '',
    'tools/scope-guard-cli.py'
  );

  // Block saves to READ_ONLY files
  const saveListener = vscode.workspace.onWillSaveTextDocument(async (event) => {
    const filePath = event.document.uri.fsPath;
    const workspaceRoot = vscode.workspace.workspaceFolders?.[0].uri.fsPath;
    
    if (!workspaceRoot || !filePath.startsWith(workspaceRoot)) {
      return; // Not in workspace
    }

    const relativePath = path.relative(workspaceRoot, filePath);
    
    try {
      const result = childProcess.execSync(
        `python3 "${cliPath}" status "${relativePath}"`,
        { cwd: workspaceRoot, encoding: 'utf-8' }
      );
      
      if (result.includes('READ_ONLY')) {
        const match = result.match(/Edit source: (.+)/);
        const editInstead = match ? match[1] : 'the canonical source';
        
        vscode.window.showErrorMessage(
          `🔒 Scope Guard: ${relativePath} is READ_ONLY. Edit ${editInstead} instead.`,
          'Open Source File'
        ).then(choice => {
          if (choice === 'Open Source File' && match) {
            vscode.workspace.openTextDocument(
              path.join(workspaceRoot, match[1])
            ).then(doc => vscode.window.showTextDocument(doc));
          }
        });
        
        event.waitUntil(
          Promise.reject(new Error('Scope Guard blocked save'))
        );
      }
    } catch (error) {
      console.error('Scope Guard check failed:', error);
    }
  });

  context.subscriptions.push(saveListener);
  
  // Status bar item
  const statusBarItem = vscode.window.createStatusBarItem(
    vscode.StatusBarAlignment.Right, 100
  );
  statusBarItem.text = '🛡️ Scope Guard';
  statusBarItem.tooltip = 'Scope Guard is active';
  statusBarItem.show();
  context.subscriptions.push(statusBarItem);
}

export function deactivate() {}
```

**Devcontainer Integration:**
```json
{
  "customizations": {
    "vscode": {
      "extensions": [
        "${workspaceFolder}/tools/scope-guard-vscode/scope-guard-vscode-1.0.0.vsix"
      ]
    }
  }
}
```

---

## Automatic Installation

The devcontainer `post-create.sh` automatically installs Level 1 (Git hooks):

```bash
# In .devcontainer/post-create.sh
if [ -f "$WORKSPACE_ROOT/scripts/install-scope-guard-hooks.sh" ]; then
    chmod +x "$WORKSPACE_ROOT/scripts/install-scope-guard-hooks.sh"
    "$WORKSPACE_ROOT/scripts/install-scope-guard-hooks.sh"
fi
```

---

## CLI Tool Reference

**Location:** `tools/scope-guard-cli.py`

**Commands:**
```bash
# Check a single file
python3 tools/scope-guard-cli.py check <file>

# Check multiple files
python3 tools/scope-guard-cli.py check-batch <file1> <file2> ...

# Check all staged files (for git hooks)
python3 tools/scope-guard-cli.py check-staged

# Show detailed file status
python3 tools/scope-guard-cli.py status <file>
```

**Exit Codes:**
- `0` - ALLOWED (or all files allowed)
- `1` - BLOCKED (at least one file blocked)
- `2` - ERROR (config not found, invalid args)

**Status Values:**
| Status | Meaning |
|--------|---------|
| `EXCLUDED` | Build artifact, node_modules (no rules) |
| `READ_ONLY` | Protected path, cannot modify |
| `GENERATED` | Must edit canonical source instead |
| `BUNDLE` | Part of bundle, must read bundle first |
| `SCOPE` | In scope, must acknowledge scope |
| `UNTRACKED` | Not in any scope (warning only) |
| `ALLOWED` | File can be modified |

---

## What Gets Blocked

### READ_ONLY Paths
- `AGENTS.md` → Edit `docs/meta/AGENT_RULES_CANONICAL.md`
- `.github/copilot-instructions.md` → Edit `docs/meta/AGENT_RULES_CANONICAL.md`
- `legacy/**` → DO NOT EDIT (frozen code)
- `app/**` → DO NOT EDIT (use app-v2/)

### Bundle-Protected Files
- `build.gradle.kts`, `settings.gradle.kts` → Bundle: `gradle-config`
- `.github/workflows/**` → Bundle: `ci-infra`
- `.scope/**` → Bundle: `scope-system`

---

## Bypass Mechanisms

### Git Bypass
```bash
git commit --no-verify -m "[SCOPE-BYPASS] reason: emergency hotfix for X"
```
⚠️ Must include `[SCOPE-BYPASS]` in message to document override.

### File Watcher Bypass
Just don't run the watcher. It's opt-in additional protection.

### Extension Bypass (when implemented)
Disable extension in VS Code settings.

---

## Testing the Barrier

```bash
# Test 1: Try to commit READ_ONLY file
echo "test" >> AGENTS.md
git add AGENTS.md
git commit -m "test"
# Expected: BLOCKED

# Test 2: Check single file
python3 tools/scope-guard-cli.py check AGENTS.md
# Expected: Exit 1, shows READ_ONLY

# Test 3: Check allowed file
python3 tools/scope-guard-cli.py check README.md
# Expected: Exit 0, shows UNTRACKED (warning only)

# Test 4: Start real-time watcher
./scripts/scope-guard-watcher.sh
# Then modify AGENTS.md in another terminal
# Expected: Warning displayed
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    SCOPE GUARD BARRIER                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Level 3: VS Code Extension (PLANNED)                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  onWillSaveTextDocument → Block READ_ONLY saves       │  │
│  └───────────────────────────────────────────────────────┘  │
│                         ↓                                   │
│  Level 2: File Watcher (IMPLEMENTED)                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  inotifywait → Warn/revert on violation               │  │
│  └───────────────────────────────────────────────────────┘  │
│                         ↓                                   │
│  Level 1: Git Pre-Commit Hook (IMPLEMENTED)                 │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  pre-commit → Block commits with violations           │  │
│  └───────────────────────────────────────────────────────┘  │
│                         ↓                                   │
│  Level 0: MCP Server (ADVISORY)                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  scope_guard_check() → Agent voluntarily calls        │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Files Created

| File | Purpose |
|------|---------|
| `tools/scope-guard-cli.py` | Standalone CLI for enforcement |
| `tools/scope-guard-watcher.py` | Cross-platform file watcher (Python) |
| `scripts/hooks/pre-commit` | Git hook that calls CLI |
| `scripts/install-scope-guard-hooks.sh` | Installs hooks to .git/hooks/ |
| `scripts/scope-guard-watcher.sh` | Real-time file watcher (bash/inotify) |

---

## Summary

| Level | Type | Blocks | Bypass |
|-------|------|--------|--------|
| **0** | MCP Server | ❌ Advisory only | Agent ignores |
| **1** | Git Hook | ✅ Commits | `--no-verify` |
| **2** | File Watcher | ⚠️ Warns/Reverts | Don't run |
| **3** | VS Code Extension | ✅ Saves | Disable extension |

**Recommendation:** Always have Level 1 active. Use Level 2 during critical work.
Implement Level 3 for maximum protection.
