#!/bin/bash
# =============================================================================
# Scope Guard Hooks Installer
# =============================================================================
#
# Installs git hooks for Scope Guard enforcement.
# Run automatically by devcontainer post-create, or manually:
#   ./scripts/install-scope-guard-hooks.sh
#
# =============================================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOOKS_SOURCE="$WORKSPACE_ROOT/scripts/hooks"
GIT_HOOKS_DIR="$WORKSPACE_ROOT/.git/hooks"

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Scope Guard Hooks Installer${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

# Check if we're in a git repo
if [ ! -d "$WORKSPACE_ROOT/.git" ]; then
    echo -e "${RED}ERROR: Not a git repository: $WORKSPACE_ROOT${NC}"
    exit 1
fi

# Create hooks directory if needed
mkdir -p "$GIT_HOOKS_DIR"

# Install pre-commit hook
HOOK_FILE="$HOOKS_SOURCE/pre-commit"
TARGET="$GIT_HOOKS_DIR/pre-commit"

if [ -f "$HOOK_FILE" ]; then
    # Backup existing hook if it's different
    if [ -f "$TARGET" ]; then
        if ! cmp -s "$HOOK_FILE" "$TARGET"; then
            echo -e "${YELLOW}Backing up existing pre-commit hook...${NC}"
            mv "$TARGET" "$TARGET.backup.$(date +%Y%m%d%H%M%S)"
        fi
    fi
    
    # Copy hook
    cp "$HOOK_FILE" "$TARGET"
    chmod +x "$TARGET"
    echo -e "${GREEN}✓ Installed: pre-commit hook${NC}"
else
    echo -e "${RED}ERROR: Hook not found: $HOOK_FILE${NC}"
    exit 1
fi

# Verify CLI tool exists
CLI_TOOL="$WORKSPACE_ROOT/tools/scope-guard-cli.py"
if [ -f "$CLI_TOOL" ]; then
    chmod +x "$CLI_TOOL"
    echo -e "${GREEN}✓ Verified: scope-guard-cli.py${NC}"
else
    echo -e "${RED}WARNING: CLI tool not found: $CLI_TOOL${NC}"
fi

# Test the hook
echo ""
echo -e "${BLUE}Testing Scope Guard CLI...${NC}"
if python3 "$CLI_TOOL" check README.md 2>/dev/null; then
    echo -e "${GREEN}✓ Scope Guard CLI working${NC}"
else
    echo -e "${YELLOW}⚠ CLI test returned non-zero (may be expected for some files)${NC}"
fi

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  Scope Guard hooks installed successfully!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo "The pre-commit hook will now check all staged files before each commit."
echo ""
echo "Commands:"
echo "  python3 tools/scope-guard-cli.py check <file>      # Check single file"
echo "  python3 tools/scope-guard-cli.py check-staged      # Check staged files"
echo "  python3 tools/scope-guard-cli.py status <file>     # Show file status"
echo ""
echo "To bypass (USE WITH CAUTION):"
echo "  git commit --no-verify -m \"message\""
echo ""
