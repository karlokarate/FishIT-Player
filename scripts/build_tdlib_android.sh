#!/usr/bin/env bash
set -euo pipefail

# TDLib Android Builder — ALWAYS build latest upstream commit by default
# - Default: latest commit from origin/main (fallback to origin/master)
# - Optional: --ref <tag-or-commit> to pin exactly, or --latest-tag to build newest v* tag
# - Injects commit/tag into libtdjni.so (tdlib_android_commit) and writes libtd/TDLIB_VERSION.txt
#
# Outputs:
#   libtd/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libtdjni.so
#   libtd/src/main/java/org/drinkless/tdlib/{TdApi.java,Client.java}
#   libtd/TDLIB_VERSION.txt  (tdlib_commit, tdlib_tag_exact, tdlib_tag_nearest, source_branch, built_utc)

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR64="$REPO_DIR/libtd/src/main/jniLibs/arm64-v8a"
OUT_DIR32="$REPO_DIR/libtd/src/main/jniLibs/armeabi-v7a"
JAVA_OUT_DIR="$REPO_DIR/libtd/src/main/java/org/drinkless/tdlib"
META_FILE="$REPO_DIR/libtd/TDLIB_VERSION.txt"

TD_DIR="$REPO_DIR/.third_party/td"
BORING_DIR="$REPO_DIR/.third_party/boringssl"

BUILD_ARM64=1
BUILD_V7A=1
TD_REF_ARG=""
USE_LATEST_TAG=0
MAIN_BRANCH="main"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --only-arm64) BUILD_V7A=0 ;;
    --only-v7a) BUILD_ARM64=0 ;;
    --skip-v7a|--no-v7a) BUILD_V7A=0 ;;
    --ref|--tag|--commit) shift; TD_REF_ARG="${1:-}" ;;
    --latest-tag) USE_LATEST_TAG=1 ;;
    --main=*|--branch=*) MAIN_BRANCH="${1#*=}" ;;
    *) echo "Unknown argument: $1" >&2 ;;
  esac
  shift || true
done

# Helpers
NPROC() { command -v nproc >/dev/null 2>&1 && nproc || (command -v sysctl >/dev/null 2>&1 && sysctl -n hw.ncpu) || echo 4; }
HAS()   { command -v "$1" >/dev/null 2>&1; }
RG()    { if HAS rg; then rg "$@"; else grep -R "$@"; fi; }

# NDK detection
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK" ]]; then
  echo "ERROR: ANDROID_NDK_HOME or ANDROID_NDK_ROOT must be set (NDK r23+)." >&2
  exit 1
fi

mkdir -p "$REPO_DIR/.third_party"

# Clone TDLib if missing
if [[ ! -d "$TD_DIR/.git" ]]; then
  echo "Cloning TDLib…"
  git clone --depth 1 https://github.com/tdlib/td.git "$TD_DIR"
fi

# Ensure tags exist
(cd "$TD_DIR" && git fetch --tags --force --prune origin >/dev/null 2>&1 || true)

# === Resolve the source to build ===
if [[ -n "$TD_REF_ARG" ]]; then
  echo "Checking out TDLib ref exactly: $TD_REF_ARG"
  (cd "$TD_DIR" && git fetch --depth 1 origin "$TD_REF_ARG") || { echo "ERROR: Ref '$TD_REF_ARG' not found." >&2; exit 7; }
  (cd "$TD_DIR" && git checkout --detach FETCH_HEAD)
elif (( USE_LATEST_TAG == 1 )); then
  echo "Resolving latest v* tag…"
  LATEST_TAG=$(cd "$TD_DIR" && git ls-remote --tags --refs origin 'v*' | awk -F/ '{print $3}' | sort -V | tail -1)
  [[ -n "$LATEST_TAG" ]] || { echo "ERROR: Could not determine latest TDLib tag." >&2; exit 7; }
  echo "Latest tag: $LATEST_TAG"
  (cd "$TD_DIR" && git fetch --depth 1 origin "refs/tags/$LATEST_TAG" && git checkout --detach FETCH_HEAD)
else
  echo "Building LATEST COMMIT on origin/$MAIN_BRANCH (fallback: origin/master)…"
  if (cd "$TD_DIR" && git ls-remote --heads origin "$MAIN_BRANCH" | grep -q .); then
    (cd "$TD_DIR" && git fetch --depth 1 origin "$MAIN_BRANCH" && git checkout --detach FETCH_HEAD)
    SOURCE_BRANCH="$MAIN_BRANCH"
  elif (cd "$TD_DIR" && git ls-remote --heads origin master | grep -q .); then
    (cd "$TD_DIR" && git fetch --depth 1 origin master && git checkout --detach FETCH_HEAD)
    SOURCE_BRANCH="master"
  else
    echo "ERROR: Neither origin/$MAIN_BRANCH nor origin/master exists." >&2; exit 7;
  fi
fi

# Commit / Tag info
TD_COMMIT_HASH="$(cd "$TD_DIR" && git rev-parse --short=12 HEAD)"
TD_TAG_EXACT="$(cd "$TD_DIR" && (git describe --tags --exact-match 2>/dev/null || true))"
TD_TAG_NEAREST="$(cd "$TD_DIR" && (git describe --tags --abbrev=0 2>/dev/null || true))"
BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "TDLib commit: ${TD_COMMIT_HASH} ${TD_TAG_EXACT:+(exact tag: $TD_TAG_EXACT)}"

# BoringSSL clone if missing
if [[ ! -d "$BORING_DIR/.git" ]]; then
  echo "Cloning BoringSSL…"
  git clone --depth 1 https://boringssl.googlesource.com/boringssl "$BORING_DIR"
fi

# Host build to generate sources
NATIVE_BUILD_DIR="$TD_DIR/build-native-gen"
mkdir -p "$NATIVE_BUILD_DIR"
cd "$NATIVE_BUILD_DIR"
echo "Preparing TDLib generated sources (host)…"
cmake -DCMAKE_BUILD_TYPE=Release -DTD_GENERATE_SOURCE_FILES=ON -DTD_ENABLE_JNI=ON "$TD_DIR"
cmake --build . --target prepare_cross_compiling -j"$(NPROC)"

# Generate Java API (best-effort)
if RG -n "add_custom_target\\(td_generate_java_api" "$TD_DIR/example/java/CMakeLists.txt" >/dev/null 2>&1; then
  echo "Generating Java API (TdApi.java)…"
  cmake --build . --target td_generate_java_api -j"$(NPROC)" || true
fi

# Build BoringSSL for ABI
build_boringssl () {
  local abi="$1" out="$2"
  mkdir -p "$out"
  cd "$out"
  local GEN=""; HAS ninja && GEN="-G Ninja"
  echo "Configuring BoringSSL ($abi, MinSizeRel, IPO)…"
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
  echo "Building BoringSSL libs (ssl, crypto)…"
  cmake --build . --target ssl crypto -j"$(NPROC)"
}

# Wrapper CMake (commit symbol injection)
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
add_subdirectory(${TD_DIR} td)
add_library(tdjni SHARED
  "${TD_DIR}/example/java/td_jni.cpp"
  "tdlib_version_stub.cpp"
)
target_link_libraries(tdjni PRIVATE Td::TdStatic)
if (ANDROID_ABI STREQUAL "armeabi-v7a")
  target_link_libraries(tdjni PRIVATE atomic)
endif()
if (CMAKE_SYSROOT)
  target_include_directories(tdjni SYSTEM PRIVATE ${CMAKE_SYSROOT}/usr/include)
endif()
target_compile_definitions(tdjni PRIVATE PACKAGE_NAME="org/drinkless/tdlib")
EOF
}

# Version stub (exported symbol)
emit_version_stub () {
  local path="$1" commit="$2" exact_tag="$3"
  cat > "$path" <<EOF
// Generated — do not edit.
#include <cstdint>
extern "C" __attribute__((visibility("default")))
const char* tdlib_android_commit() {
  return "$commit${exact_tag:+ ($exact_tag)}";
}
EOF
}

# Find strip
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
  emit_version_stub "$JNI_BUILD_DIR/tdlib_version_stub.cpp" "$TD_COMMIT_HASH" "$TD_TAG_EXACT"

  cd "$JNI_BUILD_DIR"
  GEN=""; HAS ninja && GEN="-G Ninja"
  echo "Configuring TDLib JNI (arm64-v8a)…"
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

  echo "Building tdjni (arm64-v8a)…"
  cmake --build . --target tdjni -j"$(NPROC)"

  LIB_PATH="$(pwd)/libtdjni.so"
  [[ -f "$LIB_PATH" ]] || LIB_PATH="./lib/libtdjni.so"
  [[ -f "$LIB_PATH" ]] || LIB_PATH="./jni/libtdjni.so"
  [[ -f "$LIB_PATH" ]] || { echo "ERROR: libtdjni.so (arm64) not found." >&2; exit 2; }

  mkdir -p "$OUT_DIR64"
  cp -f "$LIB_PATH" "$OUT_DIR64/"
  echo "OK: Copied $(basename "$LIB_PATH") -> $OUT_DIR64"

  STRIP_BIN="$(find_strip)"
  if [[ -n "$STRIP_BIN" ]]; then
    echo "Stripping unneeded symbols (arm64)…"
    "$STRIP_BIN" --strip-unneeded -x "$OUT_DIR64/libtdjni.so" || true
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
  emit_version_stub "$JNI_BUILD_DIR_32/tdlib_version_stub.cpp" "$TD_COMMIT_HASH" "$TD_TAG_EXACT"

  cd "$JNI_BUILD_DIR_32"
  GEN=""; HAS ninja && GEN="-G Ninja"
  echo "Configuring TDLib JNI (armeabi-v7a)…"
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

  echo "Building tdjni (armeabi-v7a)…"
  cmake --build . --target tdjni -j"$(NPROC)"

  LIB32_PATH="$(pwd)/libtdjni.so"
  [[ -f "$LIB32_PATH" ]] || LIB32_PATH="./lib/libtdjni.so"
  [[ -f "$LIB32_PATH" ]] || LIB32_PATH="./jni/libtdjni.so"
  [[ -f "$LIB32_PATH" ]] || { echo "ERROR: libtdjni.so (v7a) not found." >&2; exit 6; }

  mkdir -p "$OUT_DIR32"
  cp -f "$LIB32_PATH" "$OUT_DIR32/"
  echo "OK: Cop