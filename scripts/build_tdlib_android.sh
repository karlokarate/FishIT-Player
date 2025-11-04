#!/usr/bin/env bash
set -euo pipefail
trap 'c=$?; echo "ERROR at ${BASH_SOURCE[0]}:${LINENO} (exit $c)" >&2; exit $c' ERR

# Defaults
TD_REMOTE="${TD_REMOTE:-https://github.com/tdlib/td.git}"
TD_REF="master"
ABIS="arm64-v8a,armeabi-v7a"
API="${ANDROID_API:-24}"
BUILD_TYPE="MinSizeRel"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

usage(){ cat <<'U'
TDLib Android Builder (minimal)
Builds libtdjni.so (arm64-v8a, armeabi-v7a) + Java bindings from TDLib master.
Options (accepts "--k v" and "--k=v"):
  --ref <tag|branch|commit>     --abis <comma,list>   --api-level <N>
  --build-type <MinSizeRel|Release>   --release   --ndk </path/to/ndk>
U
}

# Parse args (supports --k=v)
while [[ $# -gt 0 ]]; do
  case "$1" in
    --help|-h) usage; exit 0 ;;
    --release|--release=*) BUILD_TYPE="Release"; shift ;;
    --ref|--ref=*)         v="${1#*=}"; [[ "$v" == "$1" ]] && { v="${2:-}"; shift; }; TD_REF="${v:-master}"; shift ;;
    --abis|--abis=*)       v="${1#*=}"; [[ "$v" == "$1" ]] && { v="${2:-}"; shift; }; ABIS="${v:-$ABIS}"; shift ;;
    --api-level|--api-level=*) v="${1#*=}"; [[ "$v" == "$1" ]] && { v="${2:-}"; shift; }; API="${v:-$API}"; shift ;;
    --build-type|--build-type=*) v="${1#*=}"; [[ "$v" == "$1" ]] && { v="${2:-}"; shift; }; BUILD_TYPE="${v:-$BUILD_TYPE}"; shift ;;
    --ndk|--ndk=*)         v="${1#*=}"; [[ "$v" == "$1" ]] && { v="${2:-}"; shift; }; NDK="${v:-}"; shift ;;
    *) echo "ERROR: Unknown argument: $1" >&2; usage; exit 1 ;;
  esac
done

# Detect NDK
if [[ -z "${NDK}" ]]; then
  SDK="${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}"
  [[ -d "$SDK/ndk" ]] && NDK="$(ls -d "$SDK/ndk"/* 2>/dev/null | sort -V | tail -n1 || true)"
fi
[[ -d "${NDK:-}" ]] || { echo "NDK not found (set ANDROID_NDK_HOME/ANDROID_NDK_ROOT or --ndk)"; exit 1; }
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"
[[ -f "$TOOLCHAIN" ]] || { echo "Toolchain not found at $TOOLCHAIN"; exit 1; }

# ccache
if command -v ccache >/dev/null 2>&1; then
  export CC="ccache clang"; export CXX="ccache clang++"
  export CMAKE_C_COMPILER_LAUNCHER=ccache
  export CMAKE_CXX_COMPILER_LAUNCHER=ccache
fi

# Paths
ROOT="$(pwd)"
TP="$ROOT/.third_party"; TD="$TP/td"
JAVA_DST="$ROOT/libtd/src/main/java/org/drinkless/tdlib"
JNI_DST="$ROOT/libtd/src/main/jniLibs"
META_TXT="$ROOT/libtd/TDLIB_VERSION.txt"; META_JSON="$ROOT/libtd/.tdlib_meta"
mkdir -p "$TP" "$JAVA_DST" "$JNI_DST"

# Clone/checkout
if [[ ! -d "$TD/.git" ]]; then
  git clone --filter=blob:none --recurse-submodules "$TD_REMOTE" "$TD"
else
  (cd "$TD" && git fetch --tags --prune)
fi
(
  cd "$TD"
  git checkout -f "$TD_REF"
  git submodule update --init --recursive
)

# Common CMake hints so find_package(Td) works
CMAKE_HINTS=(-DCMAKE_MODULE_PATH="$TD/cmake" -DCMAKE_PREFIX_PATH="$TD")

# Java API
JBLD="$TD/build-java"; mkdir -p "$JBLD"
cmake -S "$TD/example/java" -B "$JBLD" -DCMAKE_BUILD_TYPE=Release -DTD_ENABLE_JNI=ON "${CMAKE_HINTS[@]}" -G Ninja \
  || cmake -S "$TD/example/java" -B "$JBLD" -DCMAKE_BUILD_TYPE=Release -DTD_ENABLE_JNI=ON "${CMAKE_HINTS[@]}"
cmake --build "$JBLD" --target td_generate_java_api -j"$(getconf _NPROCESSORS_ONLN)"

SRC="$TD/example/java/td/src/main/java/org/drinkless/tdlib"
if [[ -f "$SRC/TdApi.java" ]]; then
  install -D "$SRC/TdApi.java"  "$JAVA_DST/TdApi.java"
  [[ -f "$SRC/Client.java" ]] && install -D "$SRC/Client.java" "$JAVA_DST/Client.java"
  [[ -f "$SRC/Log.java"    ]] && install -D "$SRC/Log.java"    "$JAVA_DST/Log.java"
else
  F="$(grep -Rsl --include='TdApi.java' '^package org\.drinkless\.tdlib;' "$TD/example/java" || true)"
  [[ -n "$F" ]] && install -D "$F" "$JAVA_DST/TdApi.java" || { echo "TdApi.java not found"; exit 1; }
fi

# JNI per ABI
IFS=',' read -r -a L <<< "$ABIS"
for ABI in "${L[@]}"; do
  ABI="$(echo "$ABI" | xargs)"; [[ -n "$ABI" ]] || continue
  B="$TD/build-android-$ABI"; mkdir -p "$B"
  cmake -S "$TD" -B "$B" \
    -DCMAKE_BUILD_TYPE="$BUILD_TYPE" -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$API" -DANDROID_STL=c++_shared \
    -DTD_ENABLE_JNI=ON -DOPENSSL_USE_STATIC_LIBS=ON "${CMAKE_HINTS[@]}" -G Ninja \
    || cmake -S "$TD" -B "$B" \
    -DCMAKE_BUILD_TYPE="$BUILD_TYPE" -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$API" -DANDROID_STL=c++_shared \
    -DTD_ENABLE_JNI=ON -DOPENSSL_USE_STATIC_LIBS=ON "${CMAKE_HINTS[@]}"
  cmake --build "$B" -j"$(getconf _NPROCESSORS_ONLN)"
  SO="$(find "$B" -name libtdjni.so -type f | head -n1 || true)"; [[ -f "$SO" ]] || { echo "no libtdjni.so for $ABI"; exit 1; }
  install -D "$SO" "$JNI_DST/$ABI/libtdjni.so"
done

# Meta
TD_COMMIT="$(git -C "$TD" rev-parse HEAD)"
TD_DESCRIBE="$(git -C "$TD" describe --always --long --tags || echo unknown)"
mkdir -p "$(dirname "$META_TXT")"
{
  echo "TDLib Ref    : $TD_REF"
  echo "TDLib Commit : $TD_COMMIT"
  echo "TDLib Describe: $TD_DESCRIBE"
  echo "NDK          : $NDK"
  echo "API          : $API"
  echo "ABIs         : $ABIS"
  echo "Build Type   : $BUILD_TYPE"
  echo "UTC          : $(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$META_TXT"
printf '{"ref":"%s","commit":"%s","describe":"%s","ndk":"%s","api":"%s","abis":"%s","build_type":"%s","utc":"%s"}\n' \
  "$TD_REF" "$TD_COMMIT" "$TD_DESCRIBE" "$NDK" "$API" "$ABIS" "$BUILD_TYPE" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$META_JSON"

echo "OK: JNI -> $JNI_DST/<ABI>/libtdjni.so  |  Java -> $JAVA_DST/"