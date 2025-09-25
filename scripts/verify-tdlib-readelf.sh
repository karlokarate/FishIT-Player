#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
FAILED=0

check_lib() {
  local lib="$1"
  if [[ ! -f "$lib" ]]; then
    echo "[FAIL] Missing: $lib" >&2
    FAILED=1
    return
  fi
  if ! command -v readelf >/dev/null 2>&1; then
    echo "readelf not found in PATH; skipping dependency check" >&2
    return
  fi
  local deps
  deps=$(readelf -d "$lib" | rg -i 'NEEDED.*(ssl|crypto)' || true)
  if [[ -n "$deps" ]]; then
    echo "[FAIL] OpenSSL dependency found in $lib:" >&2
    echo "$deps" >&2
    FAILED=1
  else
    echo "[OK] $lib has no dynamic OpenSSL deps"
  fi
}

check_lib "$ROOT_DIR/libtd/src/main/jniLibs/armeabi-v7a/libtdjni.so"
check_lib "$ROOT_DIR/libtd/src/main/jniLibs/arm64-v8a/libtdjni.so"

exit $FAILED

