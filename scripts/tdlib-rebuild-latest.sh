#!/usr/bin/env bash
set -euo pipefail

# Rebuild TDLib JNI for the latest available upstream tag and sync Java bindings.
# - Cleans old build artifacts (safe dirs only)
# - Ensures repo-local toolchains are in PATH
# - Sets ANDROID_* / JAVA_HOME / GRADLE_USER_HOME env vars for WSL
# - Builds both arm64-v8a and armeabi-v7a via scripts/tdlib-build-arm64.sh
# - Verifies no dynamic OpenSSL deps remain in produced .so files
#
# Usage:
#   bash scripts/tdlib-rebuild-latest.sh            # auto-detect latest v* tag
#   bash scripts/tdlib-rebuild-latest.sh --ref v1.8.0  # use specific tag/commit
#

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_DIR"

REF=""
ONLY_ARM64=0
ONLY_V7A=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref|--tag|--commit) shift; REF="${1:-}" ;;
    --only-arm64) ONLY_ARM64=1 ;;
    --only-v7a) ONLY_V7A=1 ;;
    *) echo "Unknown argument: $1" >&2 ;;
  esac; shift || true
done

log() { printf "[tdlib-rebuild] %s\n" "$*"; }
fail() { printf "[tdlib-rebuild][ERROR] %s\n" "$*" >&2; exit 1; }

# Ensure repo-local toolchains preferred
export PATH="$REPO_DIR/.wsl-cmake/bin:$REPO_DIR/.wsl-gperf:$PATH"

# Detect/prepare envs (prefer repo-local WSL layout)
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$REPO_DIR/.wsl-android-sdk}"
export JAVA_HOME="${JAVA_HOME:-$REPO_DIR/.wsl-java-17}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$REPO_DIR/.wsl-gradle}"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# Resolve NDK path; prefer configured default from docs if present, otherwise pick newest in ndk/*
DEFAULT_NDK="$ANDROID_SDK_ROOT/ndk/26.1.10909125"
if [[ -d "$DEFAULT_NDK" ]]; then
  export ANDROID_NDK_HOME="$DEFAULT_NDK"
else
  if [[ -d "$ANDROID_SDK_ROOT/ndk" ]]; then
    newest_ndk=$(ls -1 "$ANDROID_SDK_ROOT/ndk" 2>/dev/null | sort -V | tail -n1 || true)
    if [[ -n "$newest_ndk" && -d "$ANDROID_SDK_ROOT/ndk/$newest_ndk" ]]; then
      export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/$newest_ndk"
    fi
  fi
fi
[[ -n "${ANDROID_NDK_HOME:-}" && -d "$ANDROID_NDK_HOME" ]] || fail "ANDROID_NDK_HOME not found. Ensure $ANDROID_SDK_ROOT/ndk/* exists."

# Optional: ensure portable tools (cmake/gperf) exist
if ! command -v cmake >/dev/null 2>&1; then
  log "cmake not found in PATH; attempting repo-local setup (scripts/setup-wsl-build-tools.sh)…"
  bash "$REPO_DIR/scripts/setup-wsl-build-tools.sh"
fi
if ! command -v gperf >/dev/null 2>&1; then
  log "gperf not found in PATH; attempting repo-local setup (scripts/setup-wsl-gperf.sh)…"
  bash "$REPO_DIR/scripts/setup-wsl-gperf.sh"
fi

# Determine latest TDLib tag if not given
if [[ -z "$REF" ]]; then
  log "Resolving latest TDLib tag from upstream…"
  REF=$(git ls-remote --tags --refs https://github.com/tdlib/td 'v*' \
        | awk -F/ '{print $3}' \
        | sort -V | tail -n1)
  [[ -n "$REF" ]] || fail "Could not resolve latest TDLib tag from upstream."
  log "Latest tag: $REF"
else
  log "Using requested TDLib ref: $REF"
fi

# Clean old build artifacts (safe)
log "Cleaning previous TDLib/BoringSSL build directories…"
rm -rf "$REPO_DIR/.third_party/td/build-"* || true
rm -rf "$REPO_DIR/.third_party/boringssl/build-"* || true
rm -rf "$REPO_DIR/.third_party/td/CMakeCache.txt" "$REPO_DIR/.third_party/td/CMakeFiles" || true

log "Removing previous JNI outputs (will be re-generated)…"
rm -f "$REPO_DIR/libtd/src/main/jniLibs/arm64-v8a/libtdjni.so" || true
rm -f "$REPO_DIR/libtd/src/main/jniLibs/armeabi-v7a/libtdjni.so" || true

# Build both ABIs (or only the requested one)
BUILD_ARGS=(--ref "$REF")
if (( ONLY_ARM64 == 1 )); then BUILD_ARGS=(--only-arm64 --ref "$REF"); fi
if (( ONLY_V7A == 1 )); then BUILD_ARGS=(--only-v7a --ref "$REF"); fi

log "Starting TDLib build via scripts/tdlib-build-arm64.sh ${BUILD_ARGS[*]}…"
bash "$REPO_DIR/scripts/tdlib-build-arm64.sh" "${BUILD_ARGS[@]}"

# Verify .so outputs and no dynamic OpenSSL deps
log "Verifying produced libraries…"
bash "$REPO_DIR/scripts/verify-tdlib-readelf.sh" || fail "Verification failed (OpenSSL dyn deps or missing libs)."

# Summary
log "Build completed. Summary:"
for abi in arm64-v8a armeabi-v7a; do
  so="$REPO_DIR/libtd/src/main/jniLibs/$abi/libtdjni.so"
  if [[ -f "$so" ]]; then
    sz=$(stat -c%s "$so" 2>/dev/null || wc -c < "$so")
    printf "  - %s: %s bytes\n" "$abi" "$sz"
  else
    printf "  - %s: (missing)\n" "$abi"
  fi
done

hash_line=$(sed -n '1,30p' "$REPO_DIR/libtd/src/main/java/org/drinkless/tdlib/TdApi.java" | rg -n "GIT_COMMIT_HASH" -n | awk -F: '{print $1}' | head -n1)
if [[ -n "$hash_line" ]]; then
  sed -n "${hash_line}p" "$REPO_DIR/libtd/src/main/java/org/drinkless/tdlib/TdApi.java"
fi

log "Done. You can now run './gradlew clean build'."

