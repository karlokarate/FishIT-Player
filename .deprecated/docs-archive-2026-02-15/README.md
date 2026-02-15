# Meta Documentation

This directory contains canonical sources for generated documentation.

## AGENT_RULES_CANONICAL.md

**Purpose:** Single source of truth for agent rules that generates both `AGENTS.md` and `.github/copilot-instructions.md`.

**Why:** Copilot reads `.github/copilot-instructions.md` automatically, but humans reference `AGENTS.md`. Both must be identical to prevent drift.

**How to Update:**

1. Edit `docs/meta/AGENT_RULES_CANONICAL.md`
2. Run the sync script:
   ```bash
   ./scripts/sync-agent-rules.sh
   ```
3. CI will verify both files match

**CI Check:** The `verify-agent-rules` job ensures:
- `AGENTS.md` == `.github/copilot-instructions.md`
- Both match the canonical source

## Drift Prevention

The canonical source is the ONLY file that should be edited manually. The other two files are generated from it with appropriate headers indicating they're auto-generated.
