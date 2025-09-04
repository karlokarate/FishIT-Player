#!/usr/bin/env bash
set -euo pipefail

# Build TDLib (JNI) for Android armeabi-v7a and copy libtdjni.so into :libtd.
# Prereqs: git, cmake (>=3.18), Ninja (optional), Android NDK r23+.
# Env: ANDROID_NDK_HOME or ANDROID_NDK_ROOT must point to NDK.

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$REPO_DIR/libtd/src/main/jniLibs/armeabi-v7a"
TD_DIR="$REPO_DIR/.third_party/td"
BUILD_DIR="$TD_DIR/build-android-v7a"

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK" ]]; then
  echo "ERROR: ANDROID_NDK_HOME or ANDROID_NDK_ROOT must be set (NDK r23+)." >&2
  exit 1
fi

mkdir -p "$REPO_DIR/.third_party"
if [[ ! -d "$TD_DIR/.git" ]]; then
  echo "Cloning TDLib…"
  git clone --depth 1 https://github.com/tdlib/td.git "$TD_DIR"
fi

mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

GEN=""
if command -v ninja >/dev/null 2>&1; then GEN="-G Ninja"; fi

echo "Configuring TDLib (armeabi-v7a)…"
cmake \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=armeabi-v7a \
  -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=Release \
  -DTD_ENABLE_JNI=ON \
  $GEN \
  ..

echo "Building tdjni…"
cmake --build . --target tdjni -j$(nproc || sysctl -n hw.ncpu || echo 4)

LIB_PATH="$(pwd)/libtdjni.so"
if [[ ! -f "$LIB_PATH" ]]; then
  # Some TDLib versions place it under ./lib/ or ./jni/
  if [[ -f "./lib/libtdjni.so" ]]; then LIB_PATH="./lib/libtdjni.so"; fi
  if [[ -f "./jni/libtdjni.so" ]]; then LIB_PATH="./jni/libtdjni.so"; fi
fi

if [[ ! -f "$LIB_PATH" ]]; then
  echo "ERROR: libtdjni.so not found after build." >&2
  exit 2
fi

mkdir -p "$OUT_DIR"
cp -f "$LIB_PATH" "$OUT_DIR/"
echo "OK: Copied $(basename "$LIB_PATH") -> $OUT_DIR"

