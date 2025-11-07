#!/usr/bin/env bash
# -*- coding: utf-8 -*-
# TDLib Android Builder — JNI + Java Bindings (SDK-free, static BoringSSL, NDK r27b/r27c)
# Pfade & Dateinamen strikt gemäß TDLib CMakeLists:
#  - INSTALL {lib,bin,include}
#  - CMake-Package: lib/cmake/Td/TdConfig.cmake
#  - Generator:     bin/td_generate_java_api
#  - Java-Sources:  TdApi.java, Client.java, Log.java

set -euo pipefail

NDK="${ANDROID_NDK_ROOT:-}"
BORINGSSL_DIR="${BORINGSSL_DIR:-.third_party/boringssl}"
ABIS="arm64-v8a,armeabi-v7a"
API_LEVEL="21"
ANDROID_STL="c++_static"
TD_REF="${TD_REF:-}"

# ---- CLI-Parsing -------------------------------------------------------------
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

# CMake-Layout laut TDLib (Install-Defaults + CMake-Package)
INSTALL_LIBDIR="lib"
INSTALL_BINDIR="bin"
INSTALL_INCLUDEDIR="include"
CMAKEDIR_REL="${INSTALL_LIBDIR}/cmake/Td"   # -> TdConfig.cmake liegt hier  2

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

# Optionaler Ref-Wechsel (Repo ist vom Workflow bereits ausgecheckt)
if [[ -n "${TD_REF}" ]]; then
  git fetch --depth=1 origin "$TD_REF"
  git checkout -qf FETCH_HEAD
fi
COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
DESCRIBE="$(git describe --tags --always --long --dirty=+ 2>/dev/null || echo "$COMMIT")"
echo "$DESCRIBE" > "$OUT_DIR/TDLIB_VERSION.txt"

# -----------------------------------------------------------------------------
# (1) HOST-BUILD → vollständige Installation (TdConfig.cmake, Targets, Headers)
#     Kein TD_GENERATE_SOURCE_FILES; wir wollen das volle Paket installiert bekommen.
#     CMakeLists installiert Exports + TdConfig.cmake in ${lib}/cmake/Td.  3
# -----------------------------------------------------------------------------
JAVA_TD_INSTALL_PREFIX="${TD_DIR}/example/java/td"

cmake -S . -B build-host \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX:PATH="${JAVA_TD_INSTALL_PREFIX}" \
  -DCMAKE_INSTALL_LIBDIR="${INSTALL_LIBDIR}" \
  -DCMAKE_INSTALL_BINDIR="${INSTALL_BINDIR}" \
  -DCMAKE_INSTALL_INCLUDEDIR="${INSTALL_INCLUDEDIR}" \
  -DTD_ENABLE_TESTS=OFF

cmake --build build-host
cmake --build build-host --target install

TD_CMAKE_PACKAGE="${JAVA_TD_INSTALL_PREFIX}/${CMAKEDIR_REL}/TdConfig.cmake"
[[ -f "$TD_CMAKE_PACKAGE" ]] || { echo "❌ Missing ${TD_CMAKE_PACKAGE}"; exit 1; }

# -----------------------------------------------------------------------------
# (1b) Generator + Schemas separat erzeugen und an erwartete Orte legen.
#     laut CMake: Schemas/Generator kommen aus td/generate / prepare_cross_compiling. 4
# -----------------------------------------------------------------------------
cmake -S . -B build-gen \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DTD_ENABLE_JNI=OFF \
  -DTD_ENABLE_TESTS=OFF \
  -DTD_GENERATE_SOURCE_FILES=ON

# Schemata erzeugen (td_api.tl/.tlo, JSON/Common/MTProto)
cmake --build build-gen --target prepare_cross_compiling
# Generator bauen
cmake --build build-gen --target td_generate_java_api

# Generator-Bin nach ${prefix}/bin
GEN_SRC="$(find build-gen -type f -path '*/td/generate/td_generate_java_api' -o -name 'td_generate_java_api' | head -n1 || true)"
[[ -n "$GEN_SRC" && -f "$GEN_SRC" ]] || { echo "❌ Could not build td_generate_java_api"; exit 1; }
GEN_DST_DIR="${JAVA_TD_INSTALL_PREFIX}/${INSTALL_BINDIR}"
mkdir -p "$GEN_DST_DIR"
cp -f "$GEN_SRC" "${GEN_DST_DIR}/td_generate_java_api"
chmod +x "${GEN_DST_DIR}/td_generate_java_api"

# Schemas (.tl/.tlo) an exakt den Ort, den example/java erwartet:
SCHEME_SRC_TL="$(find build-gen -type f -path '*/td/generate/scheme/td_api.tl'  | head -n1 || true)"
SCHEME_SRC_TLO="$(find build-gen -type f -path '*/td/generate/scheme/td_api.tlo' | head -n1 || true)"
[[ -f "$SCHEME_SRC_TL"  ]] || { echo "❌ Missing generated td_api.tl"; exit 1; }
[[ -f "$SCHEME_SRC_TLO" ]] || { echo "❌ Missing generated td_api.tlo"; exit 1; }
SCHEME_DST_DIR="${JAVA_TD_INSTALL_PREFIX}/${INSTALL_BINDIR}/td/generate/scheme"
mkdir -p "$SCHEME_DST_DIR"
cp -f "$SCHEME_SRC_TL"  "${SCHEME_DST_DIR}/td_api.tl"
cp -f "$SCHEME_SRC_TLO" "${SCHEME_DST_DIR}/td_api.tlo"

# PHP-Helper (Dokugen) in ${prefix}/bin/td/generate/
GEN_HELPER_DST="${JAVA_TD_INSTALL_PREFIX}/${INSTALL_BINDIR}/td/generate"
mkdir -p "$GEN_HELPER_DST"
for f in JavadocTlDocumentationGenerator.php TlDocumentationGenerator.php; do
  # übliches Upstream-Layout: td/generate/<file>
  SRC_A="td/generate/${f}"
  SRC_B="$(find td -type f -name "$f" | head -n1 || true)"
  if [[ -f "$SRC_A" ]]; then cp -f "$SRC_A" "$GEN_HELPER_DST/"; \
  elif [[ -f "$SRC_B" ]]; then cp -f "$SRC_B" "$GEN_HELPER_DST/"; \
  else echo "❌ Missing PHP helper: $f"; exit 1; fi
done

# -----------------------------------------------------------------------------
# (2) example/java bauen → generiert TdApi.java, Client.java, Log.java
#     find_package(Td) via ${prefix}/lib/cmake/Td wie im Bauplan. 5
# -----------------------------------------------------------------------------
mkdir -p example/java/build
pushd example/java/build >/dev/null
cmake -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="${JAVA_TD_INSTALL_PREFIX}" \
  -DTd_DIR="${JAVA_TD_INSTALL_PREFIX}/${CMAKEDIR_REL}" \
  -DCMAKE_INSTALL_PREFIX:PATH="${TD_DIR}/example/java" \
  ..
cmake --build . --target install
popd >/dev/null

# -----------------------------------------------------------------------------
# (3) Java-Sources einsammeln (offiziell: TdApi.java, Client.java, Log.java)
# -----------------------------------------------------------------------------
TDAPI_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/TdApi.java' | head -n1 || true)"
CLIENT_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/Client.java' | head -n1 || true)"
LOG_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/Log.java' | head -n1 || true)"

[[ -n "$TDAPI_SRC" && -f "$TDAPI_SRC" ]] || { echo "❌ Could not locate generated TdApi.java"; exit 1; }
[[ -n "$CLIENT_SRC" && -f "$CLIENT_SRC" ]] || { echo "❌ Missing Client.java"; exit 1; }
[[ -n "$LOG_SRC"    && -f "$LOG_SRC"    ]] || { echo "❌ Missing Log.java"; exit 1; }

mkdir -p "$JAVA_SRC_DIR/org/drinkless/tdlib"
cp -f "$TDAPI_SRC"  "$JAVA_SRC_DIR/org/drinkless/tdlib/TdApi.java"
cp -f "$CLIENT_SRC" "$JAVA_SRC_DIR/org/drinkless/tdlib/Client.java"
cp -f "$LOG_SRC"    "$JAVA_SRC_DIR/org/drinkless/tdlib/Log.java"

# -----------------------------------------------------------------------------
# (4) JNI je ABI (Android) — Ziel 'tdjni' (OPENSSL = BoringSSL statisch)
# -----------------------------------------------------------------------------
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

# -----------------------------------------------------------------------------
# (5) Java → JAR
# -----------------------------------------------------------------------------
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