#!/bin/bash
# =============================================================================
# Scope Guard File Watcher
# =============================================================================
#
# Monitors file changes in real-time and warns about scope violations.
# This is a WARNING system - it doesn't block edits, just notifies.
#
# Usage:
#   ./scripts/scope-guard-watcher.sh [OPTIONS]
#
# Options:
#   --aggressive    Auto-revert READ_ONLY violations (DANGEROUS)
#   --verbose       Show SCOPE and BUNDLE details with invariants
#   --all           Watch entire workspace (not just known directories)
#   --quiet         Only show BLOCKED (no UNTRACKED warnings)
#
# Requirements:
#   - inotify-tools (Linux): apt-get install inotify-tools
#
# =============================================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CLI_TOOL="$WORKSPACE_ROOT/tools/scope-guard-cli.py"

# Defaults
AGGRESSIVE_MODE=false
VERBOSE_MODE=false
WATCH_ALL=false
QUIET_MODE=false

# Parse arguments
for arg in "$@"; do
    case $arg in
        --aggressive)
            AGGRESSIVE_MODE=true
            ;;
        --verbose)
            VERBOSE_MODE=true
            ;;
        --all)
            WATCH_ALL=true
            ;;
        --quiet)
            QUIET_MODE=true
            ;;
    esac
done

# Check dependencies
if ! command -v inotifywait &> /dev/null; then
    echo -e "${RED}ERROR: inotify-tools not installed${NC}"
    echo "Install with: sudo apt-get install inotify-tools"
    exit 1
fi

if [ ! -f "$CLI_TOOL" ]; then
    echo -e "${RED}ERROR: CLI tool not found: $CLI_TOOL${NC}"
    exit 1
fi

# Build watch directories - include root for AGENTS.md etc
if [ "$WATCH_ALL" = true ]; then
    EXISTING_DIRS=("$WORKSPACE_ROOT")
else
    # Include workspace root for AGENTS.md, README.md, etc.
    WATCH_DIRS=(
        "$WORKSPACE_ROOT"  # Root files: AGENTS.md, README.md
        "$WORKSPACE_ROOT/core"
        "$WORKSPACE_ROOT/infra"
        "$WORKSPACE_ROOT/pipeline"
        "$WORKSPACE_ROOT/playback"
        "$WORKSPACE_ROOT/player"
        "$WORKSPACE_ROOT/feature"
        "$WORKSPACE_ROOT/app-v2"
        "$WORKSPACE_ROOT/legacy"
        "$WORKSPACE_ROOT/contracts"
        "$WORKSPACE_ROOT/.scope"
        "$WORKSPACE_ROOT/.github"
        "$WORKSPACE_ROOT/docs"
    )
    
    # Filter to only existing directories
    EXISTING_DIRS=()
    for dir in "${WATCH_DIRS[@]}"; do
        if [ -d "$dir" ] || [ "$dir" = "$WORKSPACE_ROOT" ]; then
            EXISTING_DIRS+=("$dir")
        fi
    done
fi

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  🔒 Scope Guard File Watcher${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "Watching ${#EXISTING_DIRS[@]} locations for changes..."
if [ "$AGGRESSIVE_MODE" = true ]; then
    echo -e "${RED}⚠️  AGGRESSIVE MODE: READ_ONLY violations will be auto-reverted!${NC}"
fi
if [ "$VERBOSE_MODE" = true ]; then
    echo -e "${CYAN}📖 VERBOSE MODE: Showing scope/bundle invariants${NC}"
fi
if [ "$QUIET_MODE" = true ]; then
    echo -e "${YELLOW}🔇 QUIET MODE: Only showing BLOCKED files${NC}"
fi
echo ""
echo "Press Ctrl+C to stop."
echo ""
echo -e "${MAGENTA}───────────────────────────────────────────────────────────────${NC}"

# Watch for file changes
# Note: We watch workspace root non-recursively for root files + recursive for subdirs
inotifywait -m -r \
    --format '%w%f' \
    -e modify,create,moved_to \
    --exclude '(\.git/|/build/|/\.gradle/|node_modules|\.idea|__pycache__|\.pyc|\~$|\.swp$|\.swo$)' \
    "${EXISTING_DIRS[@]}" 2>/dev/null | while read file; do
    
    # Skip if file doesn't exist (might be temp file)
    [ ! -f "$file" ] && continue
    
    # Skip backup/temp files
    [[ "$file" == *~ ]] && continue
    [[ "$file" == *.swp ]] && continue
    [[ "$file" == *.swo ]] && continue
    
    # Get relative path
    rel_path="${file#$WORKSPACE_ROOT/}"
    
    # Skip if path still absolute (outside workspace)
    [[ "$rel_path" == /* ]] && continue
    
    # Use status command for verbose mode, check for others
    if [ "$VERBOSE_MODE" = true ]; then
        output=$(python3 "$CLI_TOOL" status "$rel_path" 2>&1) || true
        status_line=$(echo "$output" | grep "^Status:" | head -1)
        status=$(echo "$status_line" | cut -d: -f2 | xargs)
    else
        output=$(python3 "$CLI_TOOL" check "$rel_path" 2>&1) || true
        # Determine status from output
        if echo "$output" | grep -q "BLOCKED"; then
            status="READ_ONLY"
        elif echo "$output" | grep -q "UNTRACKED"; then
            status="UNTRACKED"
        elif echo "$output" | grep -q "In scope"; then
            status="SCOPE"
        elif echo "$output" | grep -q "In bundle"; then
            status="BUNDLE"
        else
            status="ALLOWED"
        fi
    fi
    
    case "$status" in
        READ_ONLY)
            echo -e "${RED}🚫 BLOCKED: $rel_path${NC}"
            echo "$output" | grep -E "Reason:|Message:|→|Edit source:" | sed 's/^/   /'
            
            if [ "$AGGRESSIVE_MODE" = true ]; then
                echo -e "${YELLOW}   ↩ Auto-reverting...${NC}"
                git -C "$WORKSPACE_ROOT" checkout "$rel_path" 2>/dev/null || echo -e "${RED}   Failed to revert (file may be new)${NC}"
            fi
            echo ""
            ;;
        UNTRACKED)
            if [ "$QUIET_MODE" = false ]; then
                echo -e "${YELLOW}⚠️  UNTRACKED: $rel_path${NC}"
                echo "   Consider adding to a scope with scope-manager.py"
                echo ""
            fi
            ;;
        SCOPE)
            if [ "$VERBOSE_MODE" = true ]; then
                echo -e "${CYAN}📁 SCOPE: $rel_path${NC}"
                # Extract scope info
                scope_name=$(echo "$output" | grep "^Scope:" | cut -d: -f2 | xargs)
                echo "   Scope: $scope_name"
                # Show invariants if any
                if echo "$output" | grep -q "Invariants:"; then
                    echo "   Invariants:"
                    echo "$output" | sed -n '/Invariants:/,/^$/p' | grep "•" | sed 's/^/   /'
                fi
                echo ""
            fi
            ;;
        BUNDLE)
            if [ "$VERBOSE_MODE" = true ]; then
                echo -e "${MAGENTA}📦 BUNDLE: $rel_path${NC}"
                # Extract bundle info
                bundle_name=$(echo "$output" | grep "^Bundle:" | cut -d: -f2 | xargs)
                echo "   Bundle: $bundle_name"
                # Show invariants if any
                if echo "$output" | grep -q "Invariants:"; then
                    echo "   Invariants:"
                    echo "$output" | sed -n '/Invariants:/,/^$/p' | grep "•" | sed 's/^/   /'
                fi
                echo ""
            fi
            ;;
        # ALLOWED/EXCLUDED: silent
    esac
done
