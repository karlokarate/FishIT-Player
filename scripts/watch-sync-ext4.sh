#!/usr/bin/env bash
set -euo pipefail

# Watch the repo for changes and continuously sync to ext4.
# Requires: inotifywait (inotify-tools). If missing, prints installation hint.
# Usage:
#   scripts/watch-sync-ext4.sh [TARGET_DIR]

TARGET_DIR="${1:-$HOME/dev/FishITplayer}"
HERE="$(cd "$(dirname "$0")" && pwd)"

if ! command -v inotifywait >/dev/null 2>&1; then
  echo "inotifywait not found. Install with: sudo apt-get install -y inotify-tools" >&2
  exit 1
else
  echo "Watching for changes; syncing to: $TARGET_DIR"
fi

"$HERE/sync-ext4.sh" "$TARGET_DIR"

while inotifywait -r -e modify,create,delete,move --exclude '(^|/)\.(git|gradle|idea)/' "$HERE/.."; do
  "$HERE/sync-ext4.sh" "$TARGET_DIR" || true
done

