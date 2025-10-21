#!/usr/bin/env bash
set -euo pipefail

# Mirror the current repository to an ext4-backed directory for builds.
# Usage:
#   scripts/sync-ext4.sh [TARGET_DIR]
# Default TARGET_DIR: "$HOME/dev/FishITplayer"

SRC_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# Default target points to the known ext4 mirror
TARGET_DIR="${1:-/home/chris/dev/FishITplayer-wsl}"

# Only create if parent exists; otherwise fail fast to avoid accidental paths
mkdir -p "$TARGET_DIR"

# Build an rsync exclude list: prefer repo .gitignore + our sync excludes
EXCLUDES_FILE="$SRC_DIR/.sync-excludes.txt"

rsync -avh --delete \
  --checksum \
  --filter=':- .gitignore' \
  --exclude-from="$EXCLUDES_FILE" \
  --exclude='/.wsl-*' \
  --exclude='/**/.gradle/' \
  --exclude='/**/.idea/' \
  --exclude='/app/build/' \
  --exclude='/libtd/build/' \
  "$SRC_DIR/" "$TARGET_DIR/"

echo "Synced to: $TARGET_DIR"
