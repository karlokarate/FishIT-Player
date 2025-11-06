#!/usr/bin/env bash
# -*- coding: utf-8 -*-
# TDLib Android Builder — JNI + Java Bindings (SDK-free, static BoringSSL, NDK r27b/r27c)

set -euo pipefail

NDK="${ANDROID_NDK_ROOT:-}"
BORINGSSL_DIR="${BORINGSSL_DIR:-.third_party/boringssl}"
ABIS="arm64-v8a,armeabi-v7a"
API_LEVEL="21"
ANDROID_STL="c++_static"
TD_REF="${TD_REF:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ndk) NDK="$2"; shift 2 ;;
    --boringssl-dir) BORINGSSL_DIR="$2"; shift 2 ;;
    --abis) ABIS="$2"; shift 2 ;;
    --api-level) API_LEVEL="$2"; shift 2 ;;
    --stl) ANDROID_STL="$2"; shift 2 ;;
    --ref) TD_REF="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

need(){ command -v "$1" >/dev/null 2>&1 || { echo "Missing tool: $1"; exit 1; }; }
need cmake; need ninja; need javac; need jar; need gperf

[[ -n "${NDK}" && -d "${NDK}" ]] || { echo "NDK not found at: ${NDK:-<empty>}"; exit 1; }
[[ "$ANDROID_STL" == "c++_static" || "$ANDROID_STL" == "c++_shared" ]] || { echo 'Error: --stl must be c++_static or c++_shared'; exit 1; }

HOST_OS=$(uname | tr '[:upper:]' '[:lower:]')
HOST_ARCH_RAW=$(uname -m)
case "$HOST_ARCH_RAW" in x86_64|amd64) HOST_ARCH="x86_64" ;; arm64|aarch64) HOST_ARCH="arm64" ;; *) HOST_ARCH="x86_64" ;; esac
PREBUILT_DIR="${NDK}/toolchains/llvm/prebuilt/${HOST_OS}-${HOST_ARCH}"
[[ -d "$PREBUILT_DIR" ]] || { echo "NDK prebuilt toolchain not found at $PREBUILT_DIR"; exit 1; }

ROOT="$(pwd)"
TD_DIR="$ROOT/td"
OUT_DIR="$ROOT/out"
JAVA_SRC_DIR="$OUT_DIR/java_src"
JAVA_CLASSES_DIR="$OUT_DIR/classes"

IFS=',' read -ra ABI_ARR <<< "$ABIS"
for abi in "${ABI_ARR[@]}"; do
  abi="$(echo "$abi" | xargs)"
  [[ -d "$BORINGSSL_DIR/$abi/include" ]] || { echo "Missing $BORINGSSL_DIR/$abi/include"; exit 1; }
  [[ -f "$BORINGSSL_DIR/$abi/lib/libcrypto.a" && -f "$BORINGSSL_DIR/$abi/lib/libssl.a" ]] || { echo "Missing BoringSSL libs for $abi"; exit 1; }
done

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/libs" "$JAVA_SRC_DIR" "$JAVA_CLASSES_DIR"

echo "== TDLib build: JNI + Java bindings (SDK-free) =="
echo "NDK       : $NDK"
echo "STL       : $ANDROID_STL"
echo "API-Level : android-$API_LEVEL"
echo "ABIs      : $ABIS"
echo "BoringSSL : $BORINGSSL_DIR"
echo "TD_DIR    : $TD_DIR"
echo "OUT_DIR   : $OUT_DIR"
echo

[[ -d "$TD_DIR" ]] || { echo "TD source dir not found: $TD_DIR"; exit 1; }
pushd "$TD_DIR" >/dev/null

if [[ -n "${TD_REF}" ]]; then
  git fetch --depth=1 origin "$TD_REF"
  git checkout -qf FETCH_HEAD
fi
COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
DESCRIBE="$(git describe --tags --always --long --dirty=+ 2>/dev/null || echo "$COMMIT")"
echo "$DESCRIBE" > "$OUT_DIR/TDLIB_VERSION.txt"

# ---------- (1) TD + JNI "installierbar" bauen ----------
# WICHTIG: absoluter Install-Prefix, damit TdConfig.cmake unter .../lib/cmake/Td landet
JAVA_TD_INSTALL_PREFIX="${TD_DIR}/example/java/td"

cmake -S . -B build-java-install \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX:PATH="${JAVA_TD_INSTALL_PREFIX}" \
  -DTD_ENABLE_JNI=ON \
  -DTD_ENABLE_TESTS=OFF \
  -DTD_ENABLE_TDJSON=ON \
  -DTD_GENERATE_SOURCE_FILES=ON

# Kernbibliotheken VOR dem Install bauen, damit Export/Package erzeugt wird
cmake --build build-java-install --target tdjson tdclient tdtl tdactor tdutils
cmake --build build-java-install --target install

# ---------- (2) example/java bauen ⇒ generiert TdApi.java ----------
mkdir -p example/java/build
pushd example/java/build >/dev/null
cmake -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="${JAVA_TD_INSTALL_PREFIX}" \
  -DTd_DIR="${JAVA_TD_INSTALL_PREFIX}/lib/cmake/Td" \
  -DCMAKE_INSTALL_PREFIX:PATH="${TD_DIR}/example/java" \
  ..
cmake --build . --target install
popd >/dev/null

# ---------- (3) Java-Sources einsammeln ----------
TDAPI_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/TdApi.java' | head -n1 || true)"
CLIENT_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/Client.java' | head -n1 || true)"
CACHE_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/Cache.java' | head -n1 || true)"
[[ -n "$TDAPI_SRC" && -f "$TDAPI_SRC" ]] || { echo "❌ Could not locate generated TdApi.java"; exit 1; }
[[ -n "$CLIENT_SRC" && -f "$CLIENT_SRC" ]] || { echo "❌ Missing Client.java"; exit 1; }
[[ -n "$CACHE_SRC"  && -f "$CACHE_SRC"  ]] || { echo "❌ Missing Cache.java"; exit 1; }

mkdir -p "$JAVA_SRC_DIR/org/drinkless/tdlib"
cp -f "$TDAPI_SRC"  "$JAVA_SRC_DIR/org/drinkless/tdlib/TdApi.java"
cp -f "$CLIENT_SRC" "$JAVA_SRC_DIR/org/drinkless/tdlib/Client.java"
cp -f "$CACHE_SRC"  "$JAVA_SRC_DIR/org/drinkless/tdlib/Cache.java"

# ---------- (4) JNI pro ABI ----------
for ABI in "${ABI_ARR[@]}"; do
  ABI="$(echo "$ABI" | xargs)"
  BUILD_DIR="build-${ABI}-jni"
  cmake -S . -B "$BUILD_DIR" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="${NDK}/build/cmake/android.toolchain.cmake" \
    -DCMAKE_BUILD_TYPE=RelWithDebInfo \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DANDROID_ABI="${ABI}" \
    -DANDROID_STL="${ANDROID_STL}" \
    -DANDROID_PLATFORM="android-${API_LEVEL}" \
    -DTD_ENABLE_JNI=ON \
    -DOPENSSL_USE_STATIC_LIBS=ON \
    -DOPENSSL_INCLUDE_DIR="${BORINGSSL_DIR}/${ABI}/include" \
    -DOPENSSL_CRYPTO_LIBRARY="${BORINGSSL_DIR}/${ABI}/lib/libcrypto.a" \
    -DOPENSSL_SSL_LIBRARY="${BORINGSSL_DIR}/${ABI}/lib/libssl.a"
  cmake --build "$BUILD_DIR" --target tdjni

  soPath="${BUILD_DIR}/libtdjni.so"
  [[ -f "$soPath" ]] || soPath="$(find "$BUILD_DIR" -maxdepth 3 -name 'libtdjni.so' | head -n1 || true)"
  [[ -n "${soPath:-}" && -f "${soPath}" ]] || { echo "❌ libtdjni.so not found for $ABI"; exit 1; }

  mkdir -p "$OUT_DIR/libs/${ABI}"
  cp -f "$soPath" "$OUT_DIR/libs/${ABI}/"

  if [[ "$ANDROID_STL" == "c++_shared" ]]; then
    case "$ABI" in
      arm64-v8a)  TRIPLE="aarch64-linux-android" ;;
      armeabi-v7a) TRIPLE="arm-linux-androideabi" ;;
    esac
    SHARED_STL="${PREBUILT_DIR}/sysroot/usr/lib/${TRIPLE}/libc++_shared.so"
    [[ -f "$SHARED_STL" ]] && cp -f "$SHARED_STL" "$OUT_DIR/libs/${ABI}/" && \
      "${PREBUILT_DIR}/bin/llvm-strip" "$OUT_DIR/libs/${ABI}/libc++_shared.so" || true
  fi
  "${PREBUILT_DIR}/bin/llvm-strip" --strip-unneeded "$OUT_DIR/libs/${ABI}/libtdjni.so" || true
done

popd >/dev/null

# ---------- (5) Java → JAR ----------
find "$JAVA_SRC_DIR" -name "*.java" > "$OUT_DIR/java-sources.list"
javac --release 8 -d "$JAVA_CLASSES_DIR" @"$OUT_DIR/java-sources.list"

DESC="local"
if [[ -d "$TD_DIR/.git" ]]; then
  DESC="$(git -C "$TD_DIR" describe --tags --always --long --dirty=+ 2>/dev/null || git -C "$TD_DIR" rev-parse --short HEAD)"
fi
JAR_PATH="$OUT_DIR/tdlib-${DESC}.jar"
jar cf "$JAR_PATH" -C "$JAVA_CLASSES_DIR" .

echo "== Done =="
echo "Artifacts:"
echo "  $OUT_DIR/libs/*/libtdjni.so"
[[ "$ANDROID_STL" == "c++_shared" ]] && echo "  $OUT_DIR/libs/*/libc++_shared.so"
echo "  $JAR_PATH"
echo "  $OUT_DIR/TDLIB_VERSION.txt"