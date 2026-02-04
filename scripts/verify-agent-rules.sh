#!/usr/bin/env bash
# Verify agent rules are synchronized

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

AGENTS="$REPO_ROOT/AGENTS.md"
COPILOT="$REPO_ROOT/.github/copilot-instructions.md"
CANONICAL="$REPO_ROOT/docs/meta/AGENT_RULES_CANONICAL.md"

echo "Verifying agent rules synchronization..."

# Check if all files exist
if [[ ! -f "$AGENTS" ]]; then
    echo "Error: AGENTS.md not found"
    exit 1
fi

if [[ ! -f "$COPILOT" ]]; then
    echo "Error: .github/copilot-instructions.md not found"
    exit 1
fi

if [[ ! -f "$CANONICAL" ]]; then
    echo "Error: docs/meta/AGENT_RULES_CANONICAL.md not found"
    exit 1
fi

# Extract content after the generation warning (skip first 8 lines)
AGENTS_CONTENT=$(tail -n +9 "$AGENTS")
COPILOT_CONTENT=$(tail -n +9 "$COPILOT")
CANONICAL_CONTENT=$(tail -n +2 "$CANONICAL")

# Compare AGENTS.md with .github/copilot-instructions.md
if [[ "$AGENTS_CONTENT" != "$COPILOT_CONTENT" ]]; then
    echo "❌ FAIL: AGENTS.md and .github/copilot-instructions.md are not identical"
    echo ""
    echo "The two files have diverged. Run ./scripts/sync-agent-rules.sh to fix."
    exit 1
fi

# Compare with canonical source
if [[ "$AGENTS_CONTENT" != "$CANONICAL_CONTENT" ]]; then
    echo "❌ FAIL: Generated files differ from canonical source"
    echo ""
    echo "Run ./scripts/sync-agent-rules.sh to regenerate from canonical source."
    exit 1
fi

echo "✓ All agent rules files are synchronized"
exit 0
