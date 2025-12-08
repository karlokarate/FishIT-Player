#!/bin/bash
# =============================================================================
# FishIT-Player Codespace Post-Start Script
# =============================================================================
# Runs on every Codespace start (not just creation).
# Safely syncs with v2-bootstrap branch while preserving uncommitted work.
# =============================================================================

set -e

BRANCH="architecture/v2-bootstrap"
REPO_DIR="/workspaces/FishIT-Player"

cd "$REPO_DIR"

echo "🔄 FishIT-Player Codespace Sync Starting..."

# -----------------------------------------------------------------------------
# 1. Check for uncommitted changes and stash them safely
# -----------------------------------------------------------------------------
STASH_CREATED=false
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "📦 Uncommitted changes detected - stashing..."
    git stash push -m "codespace-auto-stash-$(date +%Y%m%d-%H%M%S)"
    STASH_CREATED=true
else
    echo "✅ No uncommitted changes"
fi

# -----------------------------------------------------------------------------
# 2. Fetch and sync with remote branch
# -----------------------------------------------------------------------------
echo "⬇️  Fetching from origin..."
git fetch origin "$BRANCH"

# Check if we're on the right branch
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "$BRANCH" ]; then
    echo "🔀 Switching to $BRANCH..."
    git checkout "$BRANCH"
fi

# Reset to remote state
echo "🔄 Syncing with origin/$BRANCH..."
git reset --hard "origin/$BRANCH"

# -----------------------------------------------------------------------------
# 3. Restore stashed changes if any
# -----------------------------------------------------------------------------
if [ "$STASH_CREATED" = true ]; then
    echo "📤 Restoring your uncommitted changes..."
    if git stash pop; then
        echo "✅ Changes restored successfully"
    else
        echo "⚠️  Conflict during stash pop - your changes are still in 'git stash list'"
        echo "   Run 'git stash show -p' to see them, 'git stash pop' to retry"
    fi
fi

# -----------------------------------------------------------------------------
# 4. Start memory monitor (if not running)
# -----------------------------------------------------------------------------
echo "🧠 Checking memory monitor..."
MONITOR_SCRIPT="$REPO_DIR/.devcontainer/monitor-memory.sh"
if [ -f "$MONITOR_SCRIPT" ]; then
    if [ -f /tmp/memory-monitor.pid ] && ps -p $(cat /tmp/memory-monitor.pid 2>/dev/null) > /dev/null 2>&1; then
        echo "   - Memory monitor already running (PID: $(cat /tmp/memory-monitor.pid))"
    else
        nohup bash "$MONITOR_SCRIPT" > /tmp/memory-monitor.log 2>&1 &
        echo $! > /tmp/memory-monitor.pid
        echo "   - Memory monitor started (PID: $!)"
    fi
fi

# -----------------------------------------------------------------------------
# 5. Clear caches for fresh state
# -----------------------------------------------------------------------------
echo "🧹 Clearing caches..."

# Clear GitLens cache (if exists)
GITLENS_CACHE="$HOME/.config/Code/User/globalStorage/eamodio.gitlens"
if [ -d "$GITLENS_CACHE" ]; then
    rm -rf "$GITLENS_CACHE/cache" 2>/dev/null || true
    echo "   - GitLens cache cleared"
fi

# Clear Gradle daemon (frees memory, forces fresh state)
if [ -f "./gradlew" ]; then
    ./gradlew --stop 2>/dev/null || true
    echo "   - Gradle daemons stopped"
fi

# Clear build intermediates (optional, comment out if too slow)
# rm -rf app/build/intermediates app/build/tmp 2>/dev/null || true

# -----------------------------------------------------------------------------
# 6. Setup git alias if not present
# -----------------------------------------------------------------------------
if ! git config --get alias.sync-v2 >/dev/null 2>&1; then
    git config alias.sync-v2 '!git fetch origin && git reset --hard origin/architecture/v2-bootstrap'
    echo "   - git sync-v2 alias configured"
fi

# -----------------------------------------------------------------------------
# Done
# -----------------------------------------------------------------------------
echo ""
echo "✅ Codespace ready!"
echo "   Branch: $(git branch --show-current)"
echo "   Commit: $(git log -1 --format='%h %s')"
echo ""
echo "💡 Tip: Use 'git sync-v2' anytime to quickly sync with remote"
