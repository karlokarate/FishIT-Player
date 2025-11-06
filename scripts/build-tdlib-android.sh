#!/usr/bin/env bash
# TDLib Android builder — SDK-free, JNI + Java bindings, BoringSSL static, NDK r27b
# Outputs:
#   out/libs/arm64-v8a/libtdjni.so
#   out/libs/armeabi-v7a/libtdjni.so
#   out/tdlib-<describe>.jar  (enthält org/drinkless/tdlib/*.class: TdApi, Client, Cache)
#
# Usage:
#   ./build-tdlib-android.sh <ANDROID_NDK_ROOT> <BORINGSSL_DIR> [c++_static|c++_shared]
# Example:
#   ./build-tdlib-android.sh /opt/android-ndk/r27b /opt/boringssl c++_static
#
# Notes:
#   - Kein Android SDK, kein Javadoc, keine Tests.
#   - TDLib-Repo-Struktur erwartet (CMakeLists.txt im Repo-Root; example/java vorhanden).

set -euo pipefail

ANDROID_NDK_ROOT=${1:? "Pass ANDROID_NDK_ROOT (z.B. /opt/android-ndk/r27b)"}
BORINGSSL_DIR=${2:? "Pass BORINGSSL_DIR (prebuilt per ABI, siehe README)"}
ANDROID_STL=${3:-c++_static}
if [[ "$ANDROID_STL" != "c++_static" && "$ANDROID_STL" != "c++_shared" ]]; then
  echo 'Error: ANDROID_STL must be "c++_static" or "c++_shared".' >&2
  exit 1
fi

# --- Tooling checks (neuere/bessere Varianten bevorzugt) ---
need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing tool: $1"; exit 1; }; }
need cmake; need ninja; need javac; need jar; need php

CMAKE_VER=$(cmake --version | head -1 | awk '{print $3}')
NINJA_VER=$(ninja --version)
ver_ge () { [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | head -1)" = "$1" ]; }
ver_ge "3.29" "$CMAKE_VER" || { echo "Require: cmake >= 3.29 (found $CMAKE_VER)"; exit 1; }
ver_ge "1.11" "$NINJA_VER" || { echo "Require: ninja >= 1.11 (found $NINJA_VER)"; exit 1; }

# --- NDK r27x presence ---
[[ -d "$ANDROID_NDK_ROOT" ]] || { echo "NDK not found at $ANDROID_NDK_ROOT"; exit 1; }
HOST_OS=$(uname | tr '[:upper:]' '[:lower:]')
HOST_ARCH_RAW=$(uname -m)
case "$HOST_ARCH_RAW" in
  x86_64|amd64) HOST_ARCH="x86_64" ;;
  arm64|aarch64) HOST_ARCH="arm64" ;;
  *) HOST_ARCH="x86_64" ;;
esac
PREBUILT_DIR="${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/${HOST_OS}-${HOST_ARCH}"
[[ -d "$PREBUILT_DIR" ]] || { echo "NDK prebuilt toolchain not found at $PREBUILT_DIR"; exit 1; }

# --- BoringSSL layout check ---
for abi in arm64-v8a armeabi-v7a; do
  [[ -d "$BORINGSSL_DIR/$abi/include" ]] || { echo "Missing $BORINGSSL_DIR/$abi/include"; exit 1; }
  [[ -f "$BORINGSSL_DIR/$abi/lib/libcrypto.a" && -f "$BORINGSSL_DIR/$abi/lib/libssl.a" ]] \
    || { echo "Missing libcrypto.a/libssl.a in $BORINGSSL_DIR/$abi/lib"; exit 1; }
done

# --- Build config ---
BUILD_TYPE=RelWithDebInfo
ABIS=("arm64-v8a" "armeabi-v7a")
ANDROID_PLATFORM_ALL=android-21   # einheitlich & robust
OUT_DIR="out"
JAVA_SRC_DIR="$OUT_DIR/java"
JAVA_CLASSES_DIR="$OUT_DIR/classes"

# --- Clean ---
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/libs/arm64-v8a" "$OUT_DIR/libs/armeabi-v7a" "$JAVA_SRC_DIR" "$JAVA_CLASSES_DIR"

# --- In Repo-Root wechseln ---
cd "$(dirname "$0")"

echo "== TDLib build: JNI + Java bindings (SDK-free) =="
echo "NDK     : $ANDROID_NDK_ROOT"
echo "CMake   : $CMAKE_VER"
echo "Ninja   : $NINJA_VER"
echo "STL     : $ANDROID_STL"
echo "API     : $ANDROID_PLATFORM_ALL"
echo "BoringSSL: $BORINGSSL_DIR"

# --- 1) Java-Bindings generieren: TdApi.java ---
echo "-- Generating Java sources (TdApi.java) ..."
mkdir -p org/drinkless/tdlib
cmake -S . -B "build-native-java" -DTD_GENERATE_SOURCE_FILES=ON
cmake --build "build-native-java" --target td_generate_java_api

# FIX: Datei befindet sich sicher im Build-Verzeichnis
TDAPI_SRC="build-native-java/org/drinkless/tdlib/TdApi.java"
[[ -f "$TDAPI_SRC" ]] || { echo "❌ $TDAPI_SRC not found"; exit 1; }

mkdir -p "$JAVA_SRC_DIR/org/drinkless/tdlib"
cp -f "$TDAPI_SRC" "$JAVA_SRC_DIR/org/drinkless/tdlib/TdApi.java"

# Optionales Post-Processing (Enum-IntDefs)
if [[ -f "AddIntDef.php" ]]; then
  php AddIntDef.php "$JAVA_SRC_DIR/org/drinkless/tdlib/TdApi.java"
fi

# Pflicht-Quellen aus example/java aufnehmen: Client.java + Cache.java
CLIENT_SRC="example/java/org/drinkless/tdlib/Client.java"
CACHE_SRC="example/java/org/drinkless/tdlib/Cache.java"
[[ -f "$CLIENT_SRC" ]] || { echo "Missing $CLIENT_SRC (TDLib repo expected)"; exit 1; }
[[ -f "$CACHE_SRC"  ]] || { echo "Missing $CACHE_SRC (TDLib repo expected)"; exit 1; }

cp -f "$CLIENT_SRC" "$JAVA_SRC_DIR/org/drinkless/tdlib/Client.java"
cp -f "$CACHE_SRC"  "$JAVA_SRC_DIR/org/drinkless/tdlib/Cache.java"

# --- 2) JNI bauen (libtdjni.so) pro ABI ---
for ABI in "${ABIS[@]}"; do
  BUILD_DIR="build-${ABI}-jni"
  echo "-- Configuring $ABI ..."
  cmake -G Ninja \
    -S . -B "$BUILD_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="${ANDROID_NDK_ROOT}/build/cmake/android.toolchain.cmake" \
    -DCMAKE_BUILD_TYPE="${BUILD_TYPE}" \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DANDROID_ABI="${ABI}" \
    -DANDROID_STL="${ANDROID_STL}" \
    -DANDROID_PLATFORM="${ANDROID_PLATFORM_ALL}" \
    -DTD_ENABLE_JNI=ON \
    -DOPENSSL_USE_STATIC_LIBS=ON \
    -DOPENSSL_INCLUDE_DIR="${BORINGSSL_DIR}/${ABI}/include" \
    -DOPENSSL_CRYPTO_LIBRARY="${BORINGSSL_DIR}/${ABI}/lib/libcrypto.a" \
    -DOPENSSL_SSL_LIBRARY="${BORINGSSL_DIR}/${ABI}/lib/libssl.a"

  echo "-- Building $ABI tdjni ..."
  cmake --build "$BUILD_DIR" --target tdjni

  soPath="${BUILD_DIR}/libtdjni.so"
  [[ -f "$soPath" ]] || soPath=$(find "$BUILD_DIR" -maxdepth 2 -name "libtdjni.so" | head -n1 || true)
  [[ -n "${soPath:-}" && -f "$soPath" ]] || { echo "libtdjni.so not found for $ABI"; exit 1; }

  cp -f "$soPath" "$OUT_DIR/libs/${ABI}/"

  # Bei dynamischer STL: libc++_shared.so beilegen
  if [[ "$ANDROID_STL" == "c++_shared" ]]; then
    case "$ABI" in
      arm64-v8a)  TRIPLE="aarch64-linux-android" ;;
      armeabi-v7a) TRIPLE="arm-linux-androideabi" ;;
    esac
    SHARED_STL="${PREBUILT_DIR}/sysroot/usr/lib/${TRIPLE}/libc++_shared.so"
    [[ -f "$SHARED_STL" ]] && cp -f "$SHARED_STL" "$OUT_DIR/libs/${ABI}/" && \
      "${PREBUILT_DIR}/bin/llvm-strip" "$OUT_DIR/libs/${ABI}/libc++_shared.so" || true
  fi

  # Strip (kleineres APK); Symbole behalten? -> Zeile auskommentieren.
  "${PREBUILT_DIR}/bin/llvm-strip" --strip-unneeded "$OUT_DIR/libs/${ABI}/libtdjni.so" || true
done

# --- 3) Java kompiliert → .jar ---
echo "-- Compiling Java bindings to JAR ..."
find "$JAVA_SRC_DIR" -name "*.java" > "$OUT_DIR/java-sources.list"
# TDLib-Java layer ist framework-frei -> reines JDK reicht
javac --release 8 -d "$JAVA_CLASSES_DIR" @"$OUT_DIR/java-sources.list"

DESC="local"
if command -v git >/dev/null 2>&1 && git rev-parse --git-dir >/dev/null 2>&1; then
  DESC=$(git describe --tags --always --dirty=+ | tr -d '\n' || echo local)
fi
JAR_PATH="$OUT_DIR/tdlib-${DESC}.jar"
jar cf "$JAR_PATH" -C "$JAVA_CLASSES_DIR" .

echo "== Done =="
echo "Artifacts:"
echo "  $OUT_DIR/libs/arm64-v8a/libtdjni.so"
echo "  $OUT_DIR/libs/armeabi-v7a/libtdjni.so"
echo "  $JAR_PATH"
[[ "$ANDROID_STL" == "c++_shared" ]] && echo "  (+ libc++_shared.so jeweils)"
