#!/usr/bin/env bash
set -euo pipefail

# Set up a portable gperf into .wsl-gperf (repo-local), without sudo.
# Strategy:
# 1) Try apt-get download of Debian/Ubuntu gperf .deb and extract with dpkg-deb (no root needed).
# 2) Fallback to xpack portable tarball if available.

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="$REPO_DIR/.wsl-gperf"
mkdir -p "$TOOLS_DIR"

if [[ -x "$TOOLS_DIR/usr/bin/gperf" || -x "$TOOLS_DIR/bin/gperf" ]]; then
  echo "gperf already present in $TOOLS_DIR"
  exit 0
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Attempting apt-get download of gperf…"
if command -v apt-get >/dev/null 2>&1; then
  set +e
  (cd "$TMP_DIR" && apt-get download -y gperf) >/dev/null 2>&1
  APT_STATUS=$?
  set -e
  if [[ $APT_STATUS -eq 0 ]]; then
    DEB_FILE="$(ls -1 "$TMP_DIR"/gperf_*_amd64.deb 2>/dev/null | head -n1 || true)"
    if [[ -n "$DEB_FILE" ]]; then
      echo "Extracting $DEB_FILE …"
      dpkg-deb -x "$DEB_FILE" "$TOOLS_DIR"
      echo "gperf installed to $TOOLS_DIR/usr/bin"
      exit 0
    fi
  fi
fi

echo "Falling back to xpack portable gperf…"
URL_GPERF="https://github.com/xpack-dev-tools/gperf-xpack/releases/download/v3.1.1-1/xpack-gperf-3.1.1-1-linux-x64.tar.gz"
set +e
curl -fsSL "$URL_GPERF" -o "$TMP_DIR/gperf.tgz"
CURL_STATUS=$?
set -e
if [[ $CURL_STATUS -ne 0 ]]; then
  echo "ERROR: Could not download gperf via apt or xpack. Install gperf manually or run 'sudo apt-get install -y gperf'." >&2
  exit 3
fi

echo "Extracting gperf (xpack) …"
tar -xzf "$TMP_DIR/gperf.tgz" -C "$TMP_DIR"
SRC_DIR="$(find "$TMP_DIR" -maxdepth 1 -type d -name "xpack-gperf-*" | head -n1)"
if [[ -z "$SRC_DIR" ]]; then
  echo "ERROR: Extracted gperf dir not found" >&2
  exit 4
fi
rsync -a --delete "$SRC_DIR/" "$TOOLS_DIR/"
echo "gperf installed to $TOOLS_DIR (xpack)"
echo "Add to PATH: export PATH=\"$TOOLS_DIR/bin:\$PATH\""
