#!/usr/bin/env bash
set -euo pipefail

# Downloads portable CMake for Linux x86_64 into .wsl-cmake (repo-local),
# without touching system packages. Skips download if already present unless --force.

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="$REPO_DIR/.wsl-cmake"
URL_CMAKE="https://github.com/Kitware/CMake/releases/download/v3.28.6/cmake-3.28.6-linux-x86_64.tar.gz"
EXPECTED_VER="cmake version 3.28.6"
FORCE=0
if [[ "${1:-}" == "--force" ]]; then FORCE=1; fi

if [[ $FORCE -eq 0 && -x "$TOOLS_DIR/bin/cmake" ]]; then
  INSTALLED_VER="$("$TOOLS_DIR/bin/cmake" --version | head -n1 || true)"
  if [[ "$INSTALLED_VER" == "$EXPECTED_VER" ]]; then
    echo "CMake already present in $TOOLS_DIR ($INSTALLED_VER)"
    exit 0
  fi
fi

mkdir -p "$TOOLS_DIR"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Downloading CMake to $TMP_DIR …"
curl -fsSL "$URL_CMAKE" -o "$TMP_DIR/cmake.tgz"
echo "Extracting CMake …"
tar -xzf "$TMP_DIR/cmake.tgz" -C "$TMP_DIR"

# Move/overlay into .wsl-cmake (flatten one level)
SRC_DIR="$(find "$TMP_DIR" -maxdepth 1 -type d -name "cmake-*" | head -n1)"
if [[ -z "$SRC_DIR" ]]; then
  echo "ERROR: Extracted CMake dir not found" >&2
  exit 2
fi

mkdir -p "$TOOLS_DIR"
# Copy contents to .wsl-cmake
rsync -a --delete "$SRC_DIR/" "$TOOLS_DIR/"

echo "CMake installed to $TOOLS_DIR"
echo
echo "Add to your PATH for this shell:"
echo "  export PATH=\"$TOOLS_DIR/bin:\$PATH\""
echo "Verify: $("$TOOLS_DIR/bin/cmake" --version | head -n1)"
