#!/usr/bin/env bash
# -*- coding: utf-8 -*-
# TDLib Android Builder — JNI + Java Bindings
# - Builds JNI (libtdjni.so) for arm64-v8a and optionally armeabi-v7a
# - Generates Java bindings (TdApi.java, Client.java) via TDLib example/java (td_generate_java_api)
# - Embeds commit/tag into tdlib_android_commit()
# - Writes libtd/TDLIB_VERSION.txt and libtd/.tdlib_meta
#
# Defaults:
#   * TDLib version: BRANCH HEAD (origin/master), unless TD_REF/--ref is set
#   * ANDROID API: 24  (override with API_LEVEL)
#   * Generator: Ninja if present; ccache if present
#   * Crypto: BoringSSL built per-ABI; provided via OpenSSL CMake shim
#
# Usage:
#   scripts/build_tdlib_android.sh [--only-arm64|--only-v7a|--skip-v7a]
#                                  [--ref <tag|commit|branch>]
#                                  [--api-level=<21|24|...>]
#                                  [--minsize|--release]
#                                  [--no-ccache] [--no-ninja]
#
# Environment (CI-friendly):
#   ANDROID_NDK_HOME / ANDROID_NDK_ROOT / ANDROID_HOME / ANDROID_SDK_ROOT
#   TD_REF / TD_TAG / TD_COMMIT     # explicit ref (tag/commit/branch)
#   BORING_REF / BORING_TAG / BORING_COMMIT
#   API_LEVEL                       # default 24
#   OFFLINE=1                       # disable network fetches
#   LOCKFILE=.third_party/versions.lock   # optional key=value pins (TD_REF=..., BORING_REF=...)
set -Eeuo pipefail
umask 022

# ---------- logging & helpers ----------
LOG_TS() { date -u +'%Y-%m-%dT%H:%M:%SZ'; }
note()  { printf '[%s] \033[1;34mNOTE\033[0m: %s\n'  "$(LOG_TS)" "$*" >&2; }
warn()  { printf '[%s] \033[1;33mWARN\033[0m: %s\n'  "$(LOG_TS)" "$*" >&2; }
die()   { printf '[%s] \033[1;31mERROR\033[0m: %s\n' "$(LOG_TS)" "$*" >&2; exit 1; }
HAS()   { command -v "$1" >/dev/null 2>&1; }
NPROC() { HAS nproc && nproc || (HAS sysctl && sysctl -n hw.ncpu) || echo 4; }

trap 'ec=$?; [[ $ec -ne 0 ]] && warn "Build failed with exit code $ec"; exit $ec' ERR

# ---------- layout ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
THIRD="$REPO_DIR/.third_party"; mkdir -p "$THIRD"
TD_DIR="$THIRD/td"
BORING_DIR="$THIRD/boringssl"
OPENSSL_CMAKE_DIR="$THIRD/openssl-config"
OUT_DIR64="$REPO_DIR/libtd/src/main/jniLibs/arm64-v8a"
OUT_DIR32="$REPO_DIR/libtd/src/main/jniLibs/armeabi-v7a"
JAVA_OUT_DIR="$REPO_DIR/libtd/src/main/java/org/drinkless/tdlib"
META_FILE="$REPO_DIR/libtd/TDLIB_VERSION.txt"
META_BLOB="$REPO_DIR/libtd/.tdlib_meta"

# ---------- defaults & args ----------
BUILD_ARM64=1; BUILD_V7A=1
API_LEVEL="${API_LEVEL:-24}"
BUILD_TYPE="MinSizeRel"
USE_CCACHE=1; USE_NINJA=1
OFFLINE="${OFFLINE:-0}"
LOCKFILE_DEFAULT="$THIRD/versions.lock"; LOCKFILE="${LOCKFILE:-$LOCKFILE_DEFAULT}"

TD_REF_ENV="${TD_REF:-${TD_TAG:-${TD_COMMIT:-}}}"
BORING_REF_ENV="${BORING_REF:-${BORING_TAG:-${BORING_COMMIT:-}}}"
TD_REF_ARG=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --only-arm64) BUILD_V7A=0 ;;
    --only-v7a|--skip-v7a) BUILD_ARM64=0 ;;
    --ref|--tag|--commit) shift; TD_REF_ARG="${1:-}" ;;
    --api-level=*) API_LEVEL="${1#*=}" ;;
    --release) BUILD_TYPE="Release" ;;
    --minsize) BUILD_TYPE="MinSizeRel" ;;
    --no-ccache) USE_CCACHE=0 ;;
    --no-ninja)  USE_NINJA=0 ;;
    -h|--help) sed -n '1,180p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) warn "Unbekanntes Argument ignoriert: $1" ;;
  esac; shift || true
done

# Pins aus Lockfile (optional)
if [[ -z "$TD_REF_ARG" && -z "$TD_REF_ENV" && -f "$LOCKFILE" ]]; then
  note "Lade Pins aus $LOCKFILE"
  # shellcheck disable=SC1090
  . "$LOCKFILE" || true
  TD_REF_ENV="${TD_REF_ENV:-${TD_REF:-}}"
  BORING_REF_ENV="${BORING_REF_ENV:-${BORING_REF:-}}"
fi
TD_SELECTED_REF="${TD_REF_ARG:-$TD_REF_ENV}"

# ---------- tools ----------
for t in git cmake awk sed; do HAS "$t" || die "Benötigtes Tool fehlt: $t"; done
SHA256(){
  if HAS sha256sum; then sha256sum "$1"|awk '{print $1}'
  elif HAS shasum; then shasum -a 256 "$1"|awk '{print $1}'
  else die "sha256sum/shasum fehlt"
  fi
}

CMAKE_CCACHE=()
if (( USE_CCACHE==1 )) && HAS ccache; then
  mkdir -p "${CCACHE_DIR:-$HOME/.cache/ccache}"
  CMAKE_CCACHE=(-DCMAKE_C_COMPILER_LAUNCHER=ccache -DCMAKE_CXX_COMPILER_LAUNCHER=ccache)
  note "ccache aktiviert"
fi
GEN_ARGS=(); (( USE_NINJA==1 )) && HAS ninja && GEN_ARGS=(-G Ninja) && note "Ninja aktiviert"

# ---------- NDK detection ----------
detect_ndk(){
  local ndk="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
  if [[ -z "$ndk" ]]; then
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [[ -n "$sdk" && -d "$sdk/ndk" ]]; then
      local newest=""; while IFS= read -r -d '' d; do newest="$d"; done < <(find "$sdk/ndk" -mindepth 1 -maxdepth 1 -type d -print0 | sort -zV)
      ndk="$newest"
    fi
  fi
  [[ -n "$ndk" ]] || die "ANDROID_NDK_HOME/ROOT oder ANDROID_(HOME|SDK_ROOT)/ndk nicht gesetzt"
  [[ -f "$ndk/build/cmake/android.toolchain.cmake" ]] || die "NDK toolchain fehlt: $ndk"
  echo "$ndk"
}
NDK="$(detect_ndk)"; note "NDK: $NDK"; export ANDROID_NDK_HOME="$NDK"

# ---------- TDLib fetch/checkout ----------
if [[ ! -d "$TD_DIR/.git" ]]; then
  [[ "$OFFLINE" == "1" ]] && die "OFFLINE=1 aber TDLib fehlt unter $TD_DIR"
  note "Cloning TDLib…"
  git clone --depth 1 https://github.com/tdlib/td.git "$TD_DIR"
elif [[ "$OFFLINE" != "1" ]]; then
  ( cd "$TD_DIR" && git fetch --tags --force --prune origin >/dev/null 2>&1 || true )
  ( cd "$TD_DIR" && git fetch --force --prune origin >/dev/null 2>&1 || true )
fi

# Checkout-Strategie:
# - expliziter Ref → genau der (Tag/Commit/Branch)
# - sonst: HEAD von origin/master
SOURCE_BRANCH=""
if [[ -n "$TD_SELECTED_REF" ]]; then
  note "TD_REF gesetzt: $TD_SELECTED_REF"
  [[ "$OFFLINE" == "1" ]] || (cd "$TD_DIR" && git fetch --depth 1 origin "$TD_SELECTED_REF") || true
  (cd "$TD_DIR" && git rev-parse --verify -q "$TD_SELECTED_REF" >/dev/null) \
    || (cd "$TD_DIR" && git rev-parse --verify -q "origin/$TD_SELECTED_REF" >/dev/null) \
    || die "TDLib Ref '$TD_SELECTED_REF' nicht vorhanden"
  (cd "$TD_DIR" && git checkout --detach "$TD_SELECTED_REF" 2>/dev/null || git checkout --detach "origin/$TD_SELECTED_REF")
else
  note "Kein TD_REF angegeben — baue origin/master"
  [[ "$OFFLINE" == "1" ]] || (cd "$TD_DIR" && git fetch --depth 1 origin master)
  (cd "$TD_DIR" && git checkout --detach FETCH_HEAD 2>/dev/null || git checkout --detach "origin/master")
  SOURCE_BRANCH="master"
fi

TD_COMMIT_HASH="$(cd "$TD_DIR" && git rev-parse --short=12 HEAD)"
TD_TAG_EXACT="$(cd "$TD_DIR" && (git describe --tags --exact-match 2>/dev/null || true))"
TD_TAG_NEAREST="$(cd "$TD_DIR" && (git describe --tags --abbrev=0 2>/dev/null || true))"
BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
note "TDLib @ ${TD_COMMIT_HASH} ${TD_TAG_EXACT:+(exact $TD_TAG_EXACT)}"

# ---------- Generate Java API ----------
HOST_BUILD_DIR="$TD_DIR/build-host"; rm -rf "$HOST_BUILD_DIR"; mkdir -p "$HOST_BUILD_DIR"
cmake -S "$TD_DIR" -B "$HOST_BUILD_DIR" -DCMAKE_BUILD_TYPE=Release "${GEN_ARGS[@]}"
JAVA_GEN_DIR="$TD_DIR/build-java-gen"; rm -rf "$JAVA_GEN_DIR"; mkdir -p "$JAVA_GEN_DIR"
cmake -S "$TD_DIR/example/java" -B "$JAVA_GEN_DIR" -DTd_DIR="$HOST_BUILD_DIR" -DCMAKE_BUILD_TYPE=Release "${GEN_ARGS[@]}"
cmake --build "$JAVA_GEN_DIR" --target td_generate_java_api -j"$(NPROC)"
SRC_JAVA_DIR_BUILD="$JAVA_GEN_DIR/org/drinkless/tdlib"
SRC_JAVA_DIR_SRC="$TD_DIR/example/java/org/drinkless/tdlib"
SRC_JAVA_DIR=""
[[ -f "$SRC_JAVA_DIR_BUILD/TdApi.java" && -f "$SRC_JAVA_DIR_BUILD/Client.java" ]] && SRC_JAVA_DIR="$SRC_JAVA_DIR_BUILD"
[[ -z "$SRC_JAVA_DIR" && -f "$SRC_JAVA_DIR_SRC/TdApi.java" && -f "$SRC_JAVA_DIR_SRC/Client.java" ]] && SRC_JAVA_DIR="$SRC_JAVA_DIR_SRC"
[[ -n "$SRC_JAVA_DIR" ]] || die "TdApi.java/Client.java nicht gefunden"
mkdir -p "$JAVA_OUT_DIR"; cp -f "$SRC_JAVA_DIR/TdApi.java" "$JAVA_OUT_DIR/"; cp -f "$SRC_JAVA_DIR/Client.java" "$JAVA_OUT_DIR/"
note "Java Bindings -> $JAVA_OUT_DIR"

# ---------- BoringSSL ----------
if [[ ! -d "$BORING_DIR/.git" ]]; then
  [[ "$OFFLINE" == "1" ]] && die "OFFLINE=1 aber BoringSSL fehlt unter $BORING_DIR"
  git clone --depth 1 https://boringssl.googlesource.com/boringssl "$BORING_DIR"
elif [[ "$OFFLINE" != "1" ]]; then
  ( cd "$BORING_DIR" && git fetch --force --prune origin >/dev/null 2>&1 || true )
fi
if [[ -n "$BORING_REF_ENV" ]]; then
  [[ "$OFFLINE" == "1" ]] || (cd "$BORING_DIR" && git fetch --depth 1 origin "$BORING_REF_ENV") || true
  (cd "$BORING_DIR" && git rev-parse --verify -q "$BORING_REF_ENV" >/dev/null) \
    || (cd "$BORING_DIR" && git rev-parse --verify -q "origin/$BORING_REF_ENV" >/dev/null) \
    || die "BoringSSL Ref '$BORING_REF_ENV' nicht vorhanden"
  (cd "$BORING_DIR" && git checkout --detach "$BORING_REF_ENV" 2>/dev/null || git checkout --detach "origin/$BORING_REF_ENV")
fi

build_boringssl () {
  local abi="$1" out="$2" N="${3:-$(NPROC)}"
  mkdir -p "$out"; pushd "$out" >/dev/null
  local GEN=(); HAS ninja && GEN=(-G Ninja)
  cmake -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM="android-${API_LEVEL}" \
        -DCMAKE_BUILD_TYPE="$BUILD_TYPE" -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON \
        "${GEN[@]}" "$BORING_DIR"
  cmake --build . --target ssl crypto -j"$N"
  popd >/dev/null
}

emit_openssl_config_for_boringssl () {
  local dir="$1" inc="$2" ssl="$3" crypto="$4"
  mkdir -p "$dir"
  cat > "$dir/OpenSSLConfig.cmake" <<EOF
# Auto-generated adapter to use BoringSSL with projects calling find_package(OpenSSL)
set(OPENSSL_FOUND TRUE)
set(OPENSSL_INCLUDE_DIR "${inc}")
set(OPENSSL_CRYPTO_LIBRARY "${crypto}")
set(OPENSSL_SSL_LIBRARY "${ssl}")
if(NOT TARGET OpenSSL::Crypto)
  add_library(OpenSSL::Crypto STATIC IMPORTED)
  set_target_properties(OpenSSL::Crypto PROPERTIES IMPORTED_LOCATION "${crypto}")
  target_include_directories(OpenSSL::Crypto INTERFACE "${inc}")
endif()
if(NOT TARGET OpenSSL::SSL)
  add_library(OpenSSL::SSL STATIC IMPORTED)
  set_target_properties(OpenSSL::SSL PROPERTIES IMPORTED_LOCATION "${ssl}")
  target_link_libraries(OpenSSL::SSL INTERFACE OpenSSL::Crypto)
  target_include_directories(OpenSSL::SSL INTERFACE "${inc}")
endif()
set(OPENSSL_VERSION "3.0.0")
EOF
  cat > "$dir/OpenSSLConfigVersion.cmake" <<'EOF'
set(PACKAGE_VERSION "3.0.0")
set(PACKAGE_VERSION_COMPATIBLE TRUE)
EOF
}

find_strip () {
  local STRIP_BIN=""
  for host in linux-x86_64 darwin-x86_64 darwin-aarch64 windows-x86_64; do
    local cand="$NDK/toolchains/llvm/prebuilt/$host/bin/llvm-strip"
    [[ -x "$cand" ]] && { STRIP_BIN="$cand"; break; }
  done
  [[ -z "$STRIP_BIN" ]] && STRIP_BIN="$(command -v llvm-strip || true)"
  [[ -z "$STRIP_BIN" ]] && STRIP_BIN="$(command -v strip || true)"
  echo "$STRIP_BIN"
}

find_libcxx_shared () {
  local abi="$1" triple=""
  case "$abi" in
    arm64-v8a)    triple="aarch64-linux-android" ;;
    armeabi-v7a)  triple="arm-linux-androideabi" ;;
    x86)          triple="i686-linux-android" ;;
    x86_64)       triple="x86_64-linux-android" ;;
    *) return 1 ;;
  esac
  local p1="$NDK/sources/cxx-stl/llvm-libc++/libs/$abi/libc++_shared.so"; [[ -f "$p1" ]] && { echo "$p1"; return 0; }
  for host in linux-x86_64 darwin-x86_64 darwin-aarch64 windows-x86_64; do
    for api in "$API_LEVEL" 34 33 31 30 29 28 26 24 21; do
      local p2="$NDK/toolchains/llvm/prebuilt/$host/sysroot/usr/lib/$triple/$api/libc++_shared.so"
      [[ -f "$p2" ]] && { echo "$p2"; return 0; }
    done
    local p3="$NDK/toolchains/llvm/prebuilt/$host/sysroot/usr/lib/$triple/libc++_shared.so"
    [[ -f "$p3" ]] && { echo "$p3"; return 0; }
  done
  return 1
}

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
add_subdirectory(${TD_DIR} td EXCLUDE_FROM_ALL)
add_library(tdjni SHARED "${TD_DIR}/example/java/td_jni.cpp" "tdlib_version_stub.cpp")
if (CMAKE_CXX_COMPILER_ID MATCHES "Clang|GNU")
  target_compile_options(tdjni PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
endif()
target_link_libraries(tdjni PRIVATE Td::TdStatic OpenSSL::SSL OpenSSL::Crypto)
if (ANDROID AND ANDROID_ABI STREQUAL "armeabi-v7a")
  target_link_libraries(tdjni PRIVATE atomic)
endif()
target_compile_definitions(tdjni PRIVATE PACKAGE_NAME="org/drinkless/tdlib")
EOF
}

emit_version_stub () {
  local path="$1" commit="$2" exact_tag="$3"
  cat > "$path" <<EOF
// Generated — do not edit.
extern "C" __attribute__((visibility("default"))) const char* tdlib_android_commit() {
  return "$commit${exact_tag:+ ($exact_tag)}";
}
EOF
}

build_abi () {
  local abi="$1" out_dir="$2" jni_build_dir="$TD_DIR/build-android-${abi}-jni"
  local boring_out="$BORING_DIR/build-android-${abi}" N="${3:-$(NPROC)}"
  build_boringssl "$abi" "$boring_out" "$N"
  local SSL_A="$boring_out/ssl/libssl.a"; [[ -f "$SSL_A" ]] || SSL_A="$boring_out/libssl.a"
  local CRYPTO_A="$boring_out/crypto/libcrypto.a"; [[ -f "$CRYPTO_A" ]] || CRYPTO_A="$boring_out/libcrypto.a"
  [[ -f "$SSL_A" && -f "$CRYPTO_A" ]] || die "BoringSSL libs fehlen ($abi)"
  rm -rf "$jni_build_dir"; mkdir -p "$jni_build_dir"
  emit_wrapper_cmake "$jni_build_dir/CMakeLists.txt"
  emit_version_stub  "$jni_build_dir/tdlib_version_stub.cpp" "$TD_COMMIT_HASH" "$TD_TAG_EXACT"
  emit_openssl_config_for_boringssl "$OPENSSL_CMAKE_DIR" "$BORING_DIR/include" "$SSL_A" "$CRYPTO_A"
  pushd "$jni_build_dir" >/dev/null
  local CMAKE_FLAGS=(
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake"
    -DANDROID_ABI="$abi"
    -DANDROID_PLATFORM="android-${API_LEVEL}"
    -DCMAKE_BUILD_TYPE="$BUILD_TYPE"
    -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON
    -DANDROID_STL=c++_shared
    -DTD_ENABLE_JNI=ON
    -DTD_ENABLE_LTO=ON
    -DTD_DIR="$TD_DIR"
    -DOPENSSL_USE_STATIC_LIBS=TRUE
    -DCMAKE_PREFIX_PATH="$OPENSSL_CMAKE_DIR"
  )
  cmake "${GEN_ARGS[@]}" "${CMAKE_CCACHE[@]}" "${CMAKE_FLAGS[@]}" "$jni_build_dir"
  cmake --build . --target tdjni -j"$(NPROC)"
  local LIB_PATH=""
  for cand in "libtdjni.so" "./lib/libtdjni.so" "./jni/libtdjni.so"; do
    [[ -f "$cand" ]] && { LIB_PATH="$cand"; break; }
  done
  [[ -n "$LIB_PATH" ]] || die "libtdjni.so ($abi) nicht gefunden"
  mkdir -p "$out_dir"; cp -f "$LIB_PATH" "$out_dir/"
  local STRIP_BIN; STRIP_BIN="$(find_strip)"
  [[ -n "$STRIP_BIN" && -x "$STRIP_BIN" ]] && "$STRIP_BIN" --strip-unneeded -x "$out_dir/libtdjni.so" || true
  local LIBCXX
  if LIBCXX="$(find_libcxx_shared "$abi")"; then cp -f "$LIBCXX" "$out_dir/"; else warn "libc++_shared.so nicht gefunden"; fi
  popd >/dev/null
}

# ---------- build ----------
(( BUILD_ARM64 == 1 )) && build_abi "arm64-v8a" "$OUT_DIR64"
(( BUILD_V7A   == 1 )) && build_abi "armeabi-v7a" "$OUT_DIR32"

# ---------- metadata ----------
mkdir -p "$(dirname "$META_FILE")"
{
  echo "tdlib_commit=$TD_COMMIT_HASH"
  [[ -n "$TD_TAG_EXACT"   ]] && echo "tdlib_tag_exact=$TD_TAG_EXACT" || true
  [[ -n "$TD_TAG_NEAREST" ]] && echo "tdlib_tag_nearest=$TD_TAG_NEAREST" || true
  echo "source_branch=${SOURCE_BRANCH:-}"
  echo "built_utc=$BUILD_TIME"
} > "$META_FILE"

SHA_ARM64=""; [[ -f "$OUT_DIR64/libtdjni.so" ]] && SHA_ARM64="$(SHA256 "$OUT_DIR64/libtdjni.so")"
SHA_V7A="";   [[ -f "$OUT_DIR32/libtdjni.so" ]] && SHA_V7A="$(SHA256 "$OUT_DIR32/libtdjni.so")"
{
  [[ -n "$TD_TAG_EXACT"   ]] && echo "REF=$TD_TAG_EXACT" || true
  [[ -n "$TD_COMMIT_HASH" ]] && echo "COMMIT=$TD_COMMIT_HASH" || true
  echo "SHA_ARM64=${SHA_ARM64}"
  echo "SHA_V7A=${SHA_V7A}"
  echo "NDK=${NDK}"
  echo "BUILT_AT=$BUILD_TIME"
  echo "CMAKE=$(cmake --version | head -1)"
} > "$META_BLOB"

# Optional: reproducible pins
if [[ -n "${TD_SELECTED_REF:-}" || -n "${BORING_REF_ENV:-}" ]]; then
  {
    [[ -n "${TD_SELECTED_REF:-}" ]] && echo "TD_REF=$TD_SELECTED_REF" || true
    [[ -n "${BORING_REF_ENV:-}"  ]] && echo "BORING_REF=$BORING_REF_ENV" || true
  } > "$LOCKFILE"
  note "Pins geschrieben -> $LOCKFILE"
fi

note "Done."