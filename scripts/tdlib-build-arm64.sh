#!/usr/bin/env bash
set -euo pipefail

# TDLib Android Builder â€” arm64-v8a + (optional) armeabi-v7a
#
# Goals
# - Reproducible, size-optimized libtdjni.so for Android
# - Static BoringSSL, MinSizeRel, LTO, GC-sections/ICF, aggressive strip
# - Always sync Java bindings (TdApi.java/Client.java) to the same TD commit
# - Inject the exact TDLib commit/tag into the .so as an exported symbol for runtime checks
#
# Requirements
# - ANDROID_NDK_HOME or ANDROID_NDK_ROOT set (NDK r23+ recommended)
# - git, cmake (>=3.18), Ninja (optional, detected automatically)
# - bash, coreutils; optional: readelf for dep check
#
# Usage
#   ./build_tdlib_android.sh [--only-arm64 | --only-v7a | --skip-v7a] [--ref <tag-or-commit>]
#
# Version pin priority: --ref CLI > TD_TAG env > TD_COMMIT env > TD_DEFAULT_TAG
#
# Outputs
# - libtd/src/main/jniLibs/arm64-v8a/libtdjni.so
# - libtd/src/main/jniLibs/armeabi-v7a/libtdjni.so (unless disabled)
# - libtd/src/main/java/org/drinkless/tdlib/{TdApi.java,Client.java} synced to the same commit
# - libtd/TDLIB_VERSION.txt : commit, tag (if any), build time
# - Exported symbol in the .so: tdlib_android_commit() -> const char* with the git hash
#
# Notes
# - We build a tiny wrapper CMake project around TDLib's example/java td_jni.cpp.
#   This lets us inject a small C++ file that exports the commit string as a symbol.
# - If ripgrep (rg) is absent, we gracefully fall back to grep.

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR64="$REPO_DIR/libtd/src/main/jniLibs/arm64-v8a"
OUT_DIR32="$REPO_DIR/libtd/src/main/jniLibs/armeabi-v7a"
JAVA_OUT_DIR="$REPO_DIR/libtd/src/main/java/org/drinkless/tdlib"
META_FILE="$REPO_DIR/libtd/TDLIB_VERSION.txt"

TD_DIR="$REPO_DIR/.third_party/td"
BORING_DIR="$REPO_DIR/.third_party/boringssl"

# Default validated tag for this project (override via --ref/TD_TAG/TD_COMMIT)
TD_DEFAULT_TAG="v1.8.56"

BUILD_ARM64=1
BUILD_V7A=1
TD_REF_ARG=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --only-arm64) BUILD_V7A=0 ;;
    --only-v7a) BUILD_ARM64=0 ;;
    --skip-v7a|--no-v7a) BUILD_V7A=0 ;;
    --ref|--tag|--commit) shift; TD_REF_ARG="${1:-}";;
    *) echo "Unknown argument: $1" >&2 ;;
  esac
  shift || true
done

# Prefer repo-local toolchains if present
export PATH="$REPO_DIR/.wsl-cmake/bin:$REPO_DIR/.wsl-ninja/bin:$REPO_DIR/.wsl-gperf/usr/bin:$REPO_DIR/.wsl-gperf/bin:$PATH"

# Tool helpers
NPROC() { command -v nproc >/dev/null 2>&1 && nproc || (command -v sysctl >/dev/null 2>&1 && sysctl -n hw.ncpu) || echo 4; }
HAS() { command -v "$1" >/dev/null 2>&1; }
RG()  { if HAS rg; then rg "$@"; else grep -R "$@"; fi; }

# NDK detection
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK" ]]; then
  echo "ERROR: ANDROID_NDK_HOME or ANDROID_NDK_ROOT must be set (NDK r23+)." >&2
  exit 1
fi

mkdir -p "$REPO_DIR/.third_party"

# Clone TDLib shallowly if missing
if [[ ! -d "$TD_DIR/.git" ]]; then
  echo "Cloning TDLibâ€¦"
  git clone --depth 1 https://github.com/tdlib/td.git "$TD_DIR"
fi

# Ensure tags are available
(cd "$TD_DIR" && git fetch --tags --force --prune origin >/dev/null 2>&1 || true)

# Resolve pin
TD_PIN_REF="${TD_REF_ARG:-${TD_TAG:-${TD_COMMIT:-$TD_DEFAULT_TAG}}}"
if [[ -n "$TD_PIN_REF" ]]; then
  echo "Checking out TDLib ref: $TD_PIN_REF (fallback to latest v* tag if missing)â€¦"
  if (cd "$TD_DIR" && git fetch --depth 1 origin "$TD_PIN_REF" >/dev/null 2>&1); then
    (cd "$TD_DIR" && git checkout --detach FETCH_HEAD)
  else
    echo "Ref $TD_PIN_REF not found upstream; using latest v* tagâ€¦" >&2
    LATEST_TAG=$(cd "$TD_DIR" && git ls-remote --tags --refs origin 'v*' | awk -F/ '{print $3}' | sort -V | tail -1)
    [[ -n "$LATEST_TAG" ]] || { echo "ERROR: Could not determine latest TDLib tag." >&2; exit 7; }
    echo "Resolved latest TDLib tag: $LATEST_TAG"
    (cd "$TD_DIR" && git fetch --depth 1 origin "refs/tags/$LATEST_TAG" && git checkout --detach FETCH_HEAD)
  fi
else
  echo "TD_PIN_REF empty; selecting latest v* tagâ€¦"
  LATEST_TAG=$(cd "$TD_DIR" && git ls-remote --tags --refs origin 'v*' | awk -F/ '{print $3}' | sort -V | tail -1)
  [[ -n "$LATEST_TAG" ]] || { echo "ERROR: Could not determine latest TDLib tag." >&2; exit 7; }
  echo "Resolved latest TDLib tag: $LATEST_TAG"
  (cd "$TD_DIR" && git fetch --depth 1 origin "refs/tags/$LATEST_TAG" && git checkout --detach FETCH_HEAD)
fi

# Extract commit info
TD_COMMIT_HASH="$(cd "$TD_DIR" && git rev-parse --short=12 HEAD)"
TD_COMMIT_TAG="$(cd "$TD_DIR" && (git describe --tags --exact-match 2>/dev/null || true))"
BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "TDLib commit: ${TD_COMMIT_HASH} ${TD_COMMIT_TAG:+(tag: $TD_COMMIT_TAG)}"

# Clone BoringSSL if missing
if [[ ! -d "$BORING_DIR/.git" ]]; then
  echo "Cloning BoringSSLâ€¦"
  git clone --depth 1 https://boringssl.googlesource.com/boringssl "$BORING_DIR"
fi

# Host build to generate sources for cross compilation
NATIVE_BUILD_DIR="$TD_DIR/build-native-gen"
mkdir -p "$NATIVE_BUILD_DIR"
cd "$NATIVE_BUILD_DIR"
echo "Preparing TDLib generated sources (host)â€¦"
cmake -DCMAKE_BUILD_TYPE=Release -DTD_GENERATE_SOURCE_FILES=ON -DTD_ENABLE_JNI=ON "$TD_DIR"
cmake --build . --target prepare_cross_compiling -j"$(NPROC)"

# Also generate Java API (best-effort)
if RG -n "add_custom_target\\(td_generate_java_api" "$TD_DIR/example/java/CMakeLists.txt" >/dev/null 2>&1; then
  echo "Generating Java API (TdApi.java)â€¦"
  cmake --build . --target td_generate_java_api -j"$(NPROC)" || true
fi

# Helper: build BoringSSL for a given ABI
build_boringssl () {
  local abi="$1" out="$2"
  mkdir -p "$out"
  cd "$out"
  local GEN=""; HAS ninja && GEN="-G Ninja"
  echo "Configuring BoringSSL ($abi, MinSizeRel, IPO)â€¦"
  cmake \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM=android-24 \
    -DCMAKE_BUILD_TYPE=MinSizeRel \
    -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON \
    -DCMAKE_C_FLAGS_MINSIZEREL="-Os -ffunction-sections -fdata-sections" \
    -DCMAKE_CXX_FLAGS_MINSIZEREL="-Os -ffunction-sections -fdata-sections" \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    $GEN \
    "$BORING_DIR"
  echo "Building BoringSSL libs (ssl, crypto)â€¦"
  cmake --build . --target ssl crypto -j"$(NPROC)"
}

# Wrapper CMake (lets us inject commit symbol)
emit_wrapper_cmake () {
  local path="$1"
  cat > "$path" <<'EOF'
cmake_minimum_required(VERSION 3.10)
project(tdjni_wrap LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (NOT DEFINED TD_DIR)
  message(FATAL_ERROR "TD_DIR must be provided via -DTD_DIR=... pointing to TDLib source directory")
endif()

# TDLib sources
add_subdirectory(${TD_DIR} td)

# JNI shared lib
add_library(tdjni SHARED
  "${TD_DIR}/example/java/td_jni.cpp"
  "tdlib_version_stub.cpp" # injected at configure time
)

target_link_libraries(tdjni PRIVATE Td::TdStatic)
if (ANDROID_ABI STREQUAL "armeabi-v7a")
  target_link_libraries(tdjni PRIVATE atomic)
endif()

if (CMAKE_SYSROOT)
  target_include_directories(tdjni SYSTEM PRIVATE ${CMAKE_SYSROOT}/usr/include)
endif()

# Reduce size further
target_compile_definitions(tdjni PRIVATE PACKAGE_NAME="org/drinkless/tdlib")
EOF
}

# Emit version stub that exports the commit as a default-visible symbol
emit_version_stub () {
  local path="$1" commit="$2" tag="$3"
  cat > "$path" <<EOF
// Generated by build_tdlib_android.sh â€” do not edit.
#include <cstdint>
extern "C" __attribute__((visibility("default")))
const char* tdlib_android_commit() {
  return "$commit${tag:+ ($tag)}";
}
EOF
}

# Find a valid llvm-strip
find_strip () {
  local STRIP_BIN=""
  for host in linux-x86_64 windows-x86_64 darwin-x86_64 darwin-aarch64; do
    local cand="$NDK/toolchains/llvm/prebuilt/$host/bin/llvm-strip"
    if [[ -x "$cand" ]]; then STRIP_BIN="$cand"; break; fi
  done
  [[ -z "$STRIP_BIN" ]] && STRIP_BIN="$(command -v llvm-strip || true)"
  [[ -z "$STRIP_BIN" ]] && STRIP_BIN="$(command -v strip || true)"
  echo "$STRIP_BIN"
}

# ===== arm64-v8a =====
if (( BUILD_ARM64 == 1 )); then
  BORING_BUILD_DIR="$BORING_DIR/build-android-arm64"
  build_boringssl "arm64-v8a" "$BORING_BUILD_DIR"
  SSL_A="$BORING_BUILD_DIR/ssl/libssl.a"; [[ -f "$SSL_A" ]] || SSL_A="$BORING_BUILD_DIR/libssl.a"
  CRYPTO_A="$BORING_BUILD_DIR/crypto/libcrypto.a"; [[ -f "$CRYPTO_A" ]] || CRYPTO_A="$BORING_BUILD_DIR/libcrypto.a"
  [[ -f "$SSL_A" && -f "$CRYPTO_A" ]] || { echo "ERROR: BoringSSL static libraries (arm64) not found." >&2; exit 3; }

  JNI_BUILD_DIR="$TD_DIR/build-android-arm64-jni"
  rm -rf "$JNI_BUILD_DIR"
  mkdir -p "$JNI_BUILD_DIR"
  emit_wrapper_cmake "$JNI_BUILD_DIR/CMakeLists.txt"
  emit_version_stub "$JNI_BUILD_DIR/tdlib_version_stub.cpp" "$TD_COMMIT_HASH" "$TD_COMMIT_TAG"

  cd "$JNI_BUILD_DIR"
  GEN=""; HAS ninja && GEN="-G Ninja"
  echo "Configuring TDLib JNI (arm64-v8a)â€¦"
  cmake \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DCMAKE_BUILD_TYPE=MinSizeRel \
    -DCMAKE_CXX_STANDARD=17 \
    -DCMAKE_CXX_STANDARD_REQUIRED=ON \
    -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON \
    -DTD_ENABLE_LTO=ON \
    -DCMAKE_C_FLAGS_MINSIZEREL="-Os -ffunction-sections -fdata-sections" \
    -DCMAKE_CXX_FLAGS_MINSIZEREL="-Os -ffunction-sections -fdata-sections" \
    -DCMAKE_SHARED_LINKER_FLAGS_MINSIZEREL="-Wl,--gc-sections -Wl,--icf=all -Wl,--exclude-libs,ALL" \
    -DCMAKE_MODULE_LINKER_FLAGS_MINSIZEREL="-Wl,--gc-sections -Wl,--icf=all -Wl,--exclude-libs,ALL" \
    -DTD_ENABLE_JNI=ON \
    -DOPENSSL_USE_STATIC_LIBS=TRUE \
    -DOPENSSL_INCLUDE_DIR="$BORING_DIR/include" \
    -DOPENSSL_SSL_LIBRARY="$SSL_A" \
    -DOPENSSL_CRYPTO_LIBRARY="$CRYPTO_A" \
    -DTD_DIR="$TD_DIR" \
    $GEN \
    "$JNI_BUILD_DIR"

  echo "Building tdjni (arm64-v8a)â€¦"
  cmake --build . --target tdjni -j"$(NPROC)"

  # Locate
  LIB_PATH="$(pwd)/libtdjni.so"
  [[ -f "$LIB_PATH" ]] || LIB_PATH="./lib/libtdjni.so"
  [[ -f "$LIB_PATH" ]] || LIB_PATH="./jni/libtdjni.so"
  [[ -f "$LIB_PATH" ]] || { echo "ERROR: libtdjni.so (arm64) not found." >&2; exit 2; }

  mkdir -p "$OUT_DIR64"
  cp -f "$LIB_PATH" "$OUT_DIR64/"
  echo "OK: Copied $(basename "$LIB_PATH") -> $OUT_DIR64"

  STRIP_BIN="$(find_strip)"
  if [[ -n "$STRIP_BIN" ]]; then
    echo "Stripping unneeded symbols (arm64)â€¦"
    "$STRIP_BIN" --strip-unneeded -x "$OUT_DIR64/libtdjni.so" || true
  fi

  if HAS readelf; then
    echo "Verifying no dynamic OpenSSL deps (arm64)â€¦"
    if readelf -d "$OUT_DIR64/libtdjni.so" | RG -i 'NEEDED.*(ssl|crypto)' >/dev/null; then
      echo "WARNING: OpenSSL dyn deps detected in arm64 libtdjni.so" >&2
    else
      echo "OK: no dynamic OpenSSL deps (arm64)"
    fi
  fi
fi

# ===== armeabi-v7a =====
if (( BUILD_V7A == 1 )); then
  echo -e "\n=== Building TDLib for armeabi-v7a (size-optimized) ==="
  BORING_BUILD_DIR_32="$BORING_DIR/build-android-armv7"
  build_boringssl "armeabi-v7a" "$BORING_BUILD_DIR_32"
  SSL_A_32="$BORING_BUILD_DIR_32/ssl/libssl.a"; [[ -f "$SSL_A_32" ]] || SSL_A_32="$BORING_BUILD_DIR_32/libssl.a"
  CRYPTO_A_32="$BORING_BUILD_DIR_32/crypto/libcrypto.a"; [[ -f "$CRYPTO_A_32" ]] || CRYPTO_A_32="$BORING_BUILD_DIR_32/libcrypto.a"
  [[ -f "$SSL_A_32" && -f "$CRYPTO_A_32" ]] || { echo "ERROR: BoringSSL (v7a) static libraries not found." >&2; exit 5; }

  JNI_BUILD_DIR_32="$TD_DIR/build-android-armv7-jni"
  rm -rf "$JNI_BUILD_DIR_32"
  mkdir -p "$JNI_BUILD_DIR_32"
  emit_wrapper_cmake "$JNI_BUILD_DIR_32/CMakeLists.txt"
  emit_version_stub "$JNI_BUILD_DIR_32/tdlib_version_stub.cpp" "$TD_COMMIT_HASH" "$TD_COMMIT_TAG"

  cd "$JNI_BUILD_DIR_32"
  GEN=""; HAS ninja && GEN="-G Ninja"
  echo "Configuring TDLib JNI (armeabi-v7a)â€¦"
  cmake \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=armeabi-v7a \
    -DANDROID_PLATFORM=android-24 \
    -DCMAKE_BUILD_TYPE=MinSizeRel \
    -DCMAKE_CXX_STANDARD=17 \
    -DCMAKE_CXX_STANDARD_REQUIRED=ON \
    -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON \
    -DTD_ENABLE_LTO=ON \
    -DCMAKE_C_FLAGS_MINSIZEREL="-Os -ffunction-sections -fdata-sections" \
    -DCMAKE_CXX_FLAGS_MINSIZEREL="-Os -ffunction-sections -fdata-sections" \
    -DCMAKE_SHARED_LINKER_FLAGS_MINSIZEREL="-Wl,--gc-sections -Wl,--icf=all -Wl,--exclude-libs,ALL" \
    -DCMAKE_MODULE_LINKER_FLAGS_MINSIZEREL="-Wl,--gc-sections -Wl,--icf=all -Wl,--exclude-libs,ALL" \
    -DTD_ENABLE_JNI=ON \
    -DOPENSSL_USE_STATIC_LIBS=TRUE \
    -DOPENSSL_INCLUDE_DIR="$BORING_DIR/include" \
    -DOPENSSL_SSL_LIBRARY="$SSL_A_32" \
    -DOPENSSL_CRYPTO_LIBRARY="$CRYPTO_A_32" \
    -DTD_DIR="$TD_DIR" \
    $GEN \
    "$JNI_BUILD_DIR_32"

  echo "Building tdjni (armeabi-v7a)â€¦"
  cmake --build . --target tdjni -j"$(NPROC)"

  LIB32_PATH="$(pwd)/libtdjni.so"
  [[ -f "$LIB32_PATH" ]] || LIB32_PATH="./lib/libtdjni.so"
  [[ -f "$LIB32_PATH" ]] || LIB32_PATH="./jni/libtdjni.so"
  [[ -f "$LIB32_PATH" ]] || { echo "ERROR: libtdjni.so (v7a) not found." >&2; exit 6; }

  mkdir -p "$OUT_DIR32"
  cp -f "$LIB32_PATH" "$OUT_DIR32/"
  echo "OK: Copied $(basename "$LIB32_PATH") -> $OUT_DIR32"

  STRIP_BIN="$(find_strip)"
  if [[ -n "$STRIP_BIN" ]]; then
    echo "Stripping unneeded symbols (v7a)â€¦"
    "$STRIP_BIN" --strip-unneeded -x "$OUT_DIR32/libtdjni.so" || true
  fi

  if HAS readelf; then
    echo "Verifying no dynamic OpenSSL deps (v7a)â€¦"
    if readelf -d "$OUT_DIR32/libtdjni.so" | RG -i 'NEEDED.*(ssl|crypto)' >/dev/null; then
      echo "WARNING: OpenSSL dyn deps detected in v7a libtdjni.so" >&2
    else
      echo "OK: no dynamic OpenSSL deps (v7a)"
    fi
  fi
fi

# ===== Sync Java bindings to this exact commit =====
echo "Copying Java bindings (TdApi.java, Client.java) from TDLib example/javaâ€¦"
SRC_JAVA_DIR="$TD_DIR/example/java/org/drinkless/tdlib"
if [[ -f "$SRC_JAVA_DIR/TdApi.java" && -f "$SRC_JAVA_DIR/Client.java" ]]; then
  mkdir -p "$JAVA_OUT_DIR"
  cp -f "$SRC_JAVA_DIR/TdApi.java" "$JAVA_OUT_DIR/"
  cp -f "$SRC_JAVA_DIR/Client.java" "$JAVA_OUT_DIR/"
  echo "OK: Java bindings synchronized."
else
  echo "WARNING: TDLib example/java sources not found; skipping Java binding copy" >&2
fi

# ===== Write metadata file =====
mkdir -p "$(dirname "$META_FILE")"
{
  echo "tdlib_commit=$TD_COMMIT_HASH"
  [[ -n "$TD_COMMIT_TAG" ]] && echo "tdlib_tag=$TD_COMMIT_TAG" || true
  echo "built_utc=$BUILD_TIME"
} > "$META_FILE"
echo "Wrote metadata: $META_FILE"
echo "Done."