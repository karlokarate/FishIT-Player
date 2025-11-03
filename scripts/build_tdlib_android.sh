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

# Simple args
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref)        TD_REF="${2:-master}"; shift 2 ;;
    --abis)       ABIS="${2:-$ABIS}"; shift 2 ;;
    --api-level)  API="${2:-$API}"; shift 2 ;;
    --build-type) BUILD_TYPE="${2:-$BUILD_TYPE}"; shift 2 ;;
    --release)    BUILD_TYPE="Release"; shift ;;
    --ndk)        NDK="${2:-}"; shift 2 ;;
    --help|-h)
      cat <<'USAGE'
TDLib Android Builder (minimal)
Builds TDLib JNI (libtdjni.so) for arm64-v8a + armeabi-v7a and generates Java bindings.
Defaults: ref=master, api=24, build_type=MinSizeRel

Options:
  --ref <tag|branch|commit>   (default: master)
  --abis <comma list>         (default: arm64-v8a,armeabi-v7a)
  --api-level <N>             (default: 24)
  --build-type <T>            (MinSizeRel|Release)
  --release                   (shorthand for --build-type Release)
  --ndk </path/to/ndk>
USAGE
      exit 0 ;;
    *) echo "ERROR: Unknown argument: $1" >&2; exit 1 ;;
  esac
done

# Detect NDK if not provided
if [[ -z "${NDK}" ]]; then
  SDK="${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}"
  [[ -d "$SDK/ndk" ]] && NDK="$(ls -d "$SDK/ndk"/* 2>/dev/null | sort -V | tail -n1 || true)"
fi
[[ -d "${NDK:-}" ]] || { echo "NDK not found. Set ANDROID_NDK_HOME/ANDROID_NDK_ROOT or use --ndk" >&2; exit 1; }
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"
[[ -f "$TOOLCHAIN" ]] || { echo "Toolchain not found at $TOOLCHAIN" >&2; exit 1; }

# Enable ccache if available
if command -v ccache >/dev/null 2>&1; then
  export CC="ccache clang"; export CXX="ccache clang++"
  export CMAKE_C_COMPILER_LAUNCHER=ccache
  export CMAKE_CXX_COMPILER_LAUNCHER=ccache
fi

# Paths
ROOT="$(pwd)"
TP="$ROOT/.third_party"
TD="$TP/td"
JAVA_DST="$ROOT/libtd/src/main/java/org/drinkless/tdlib"
JNI_DST="$ROOT/libtd/src/main/jniLibs"
META_TXT="$ROOT/libtd/TDLIB_VERSION.txt"
META_JSON="$ROOT/libtd/.tdlib_meta"

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
  TD_COMMIT="$(git rev-parse HEAD)"
  TD_DESCRIBE="$(git describe --always --long --tags || echo unknown)"
  echo "Using TDLib $TD_DESCRIBE ($TD_COMMIT)"
)

# Generate Java API
BUILD_JAVA="$TD/build-java"
mkdir -p "$BUILD_JAVA"
cmake -S "$TD/example/java" -B "$BUILD_JAVA" -DCMAKE_BUILD_TYPE=Release -DTD_ENABLE_JNI=ON -G Ninja || cmake -S "$TD/example/java" -B "$BUILD_JAVA" -DCMAKE_BUILD_TYPE=Release -DTD_ENABLE_JNI=ON
cmake --build "$BUILD_JAVA" --target td_generate_java_api -j"$(getconf _NPROCESSORS_ONLN)"

# Copy Java bindings
SRC_BASE="$TD/example/java/td/src/main/java/org/drinkless/tdlib"
if [[ -f "$SRC_BASE/TdApi.java" ]]; then
  install -D "$SRC_BASE/TdApi.java" "$JAVA_DST/TdApi.java"
  [[ -f "$SRC_BASE/Client.java" ]] && install -D "$SRC_BASE/Client.java" "$JAVA_DST/Client.java"
  [[ -f "$SRC_BASE/Log.java"    ]] && install -D "$SRC_BASE/Log.java"    "$JAVA_DST/Log.java"
else
  FOUND="$(grep -Rsl --include='TdApi.java' '^package org\.drinkless\.tdlib;' "$TD/example/java" || true)"
  [[ -n "$FOUND" ]] && install -D "$FOUND" "$JAVA_DST/TdApi.java" || { echo "TdApi.java not found" >&2; exit 1; }
fi

# Build JNI per ABI
IFS=',' read -r -a LIST <<< "$ABIS"
for ABI in "${LIST[@]}"; do
  ABI="$(echo "$ABI" | xargs)"; [[ -n "$ABI" ]] || continue
  BDIR="$TD/build-android-$ABI"; mkdir -p "$BDIR"
  cmake -S "$TD" -B "$BDIR" \
    -DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="android-$API" \
    -DANDROID_STL=c++_shared \
    -DTD_ENABLE_JNI=ON \
    -DOPENSSL_USE_STATIC_LIBS=ON \
    -G Ninja || cmake -S "$TD" -B "$BDIR" \
    -DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="android-$API" \
    -DANDROID_STL=c++_shared \
    -DTD_ENABLE_JNI=ON \
    -DOPENSSL_USE_STATIC_LIBS=ON
  cmake --build "$BDIR" -j"$(getconf _NPROCESSORS_ONLN)"
  SO="$(find "$BDIR" -name libtdjni.so -type f | head -n1 || true)"
  [[ -f "$SO" ]] || { echo "libtdjni.so not found for $ABI" >&2; exit 1; }
  install -D "$SO" "$JNI_DST/$ABI/libtdjni.so"
done

# Metadata
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

cat > "$META_JSON" <<EOF
{"ref":"$TD_REF","commit":"$TD_COMMIT","describe":"$TD_DESCRIBE","ndk":"$NDK","api":"$API","abis":"$ABIS","build_type":"$BUILD_TYPE","utc":"$(date -u +%Y-%m-%dT%H:%M:%SZ)"}
EOF

echo "Done. JNI in $JNI_DST/<ABI>/libtdjni.so, Java in $JAVA_DST/"