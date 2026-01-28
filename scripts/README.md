# Release Management Scripts

This directory contains scripts for managing GitHub releases.

## Delete All Releases

Two scripts are provided to delete all GitHub releases from the repository:

### 1. Interactive Script (Recommended)

**File:** `delete-all-releases.sh`

Features:
- Dry-run mode to preview deletions
- Confirmation prompt before deletion
- Progress reporting during deletion
- Summary of deleted and failed releases

Usage:
```bash
# Preview what would be deleted
./scripts/delete-all-releases.sh --dry-run

# Delete all releases (with confirmation)
./scripts/delete-all-releases.sh
```

### 2. One-Liner Script (Fast)

**File:** `delete-releases-oneliner.sh`

Features:
- Quick deletion without prompts
- Good for automation
- Minimal output

Usage:
```bash
./scripts/delete-releases-oneliner.sh
```

## Prerequisites

Both scripts require:
- GitHub CLI (`gh`) installed: https://cli.github.com/
- Authenticated with GitHub: `gh auth login`
- Write permissions to the repository

## See Also

- `RELEASE_DELETION_GUIDE.md` - Complete documentation with all deletion methods
- `.github/workflows/release-build.yml` - Release build workflow that creates these releases

## Other Scripts

This directory also contains:
- `check-dev-env.ps1` - Check development environment (Windows PowerShell)
- `check_dependencies.sh` - Check project dependencies
- `setup-wsl-*.sh` - WSL environment setup scripts
- `sync-ext4.sh` - File synchronization scripts
- Various Python scripts for M3U processing and validation

### check-dev-env.ps1

**Zweck:** Prüft die Entwicklungsumgebung auf Vollständigkeit (Windows).

**Verwendung:**
```powershell
.\scripts\check-dev-env.ps1
```

**Prüft:**
- JDK 21, Android SDK, Gradle, Node.js, MCP Server, IDE Config

**Siehe:** [docs/dev/LOCAL_SETUP.md](../docs/dev/LOCAL_SETUP.md) für vollständiges Setup

