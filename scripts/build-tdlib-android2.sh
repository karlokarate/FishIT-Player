# scripts/build-tdlib-android2.sh
#!/usr/bin/env bash
# -*- coding: utf-8 -*-
# TDLib Android Builder — JNI + Java Bindings (SDK-free, static BoringSSL, NDK r27b/r27c)
# - Baut JNI (libtdjni.so) für konfigurierbare ABIs (default: arm64-v8a, armeabi-v7a)
# - Generiert Java-Bindings (TdApi.java, Client.java, Cache.java) aus TDLib-Quelle
# - Packt tdlib-<describe>.jar (org/drinkless/tdlib/*)
# - Läuft im Repo-Root, nutzt TD-Quellen unter ./td, schreibt Artefakte nach ./out

set -euo pipefail

# ------------------------ Argumente / Flags ------------------------
NDK="${ANDROID_NDK_ROOT:-}"
BORINGSSL_DIR="${BORINGSSL_DIR:-.third_party/boringssl}"
ABIS="arm64-v8a,armeabi-v7a"
API_LEVEL="21"
ANDROID_STL="c++_static"
TD_REF="${TD_REF:-}"  # optional, Workflow checkt TD vorher aus

# Flag-Parsing + Positions-Compatibility
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ndk)             NDK="$2"; shift 2 ;;
    --boringssl-dir)   BORINGSSL_DIR="$2"; shift 2 ;;
    --abis)            ABIS="$2"; shift 2 ;;
    --api-level)       API_LEVEL="$2"; shift 2 ;;
    --stl)             ANDROID_STL="$2"; shift 2 ;;
    --ref)             TD_REF="$2"; shift 2 ;;
    -*)
      echo "Unknown option: $1" >&2; exit 2 ;;
    *)
      if [[ -z "${NDK}" ]]; then
        NDK="$1"
      elif [[ "${BORINGSSL_DIR}" == ".third_party/boringssl" ]]; then
        BORINGSSL_DIR="$1"
      elif [[ "$ANDROID_STL" == "c++_static" ]]; then
        ANDROID_STL="$1"
      else
        echo "Too many positional args. Use flags (see header)." >&2; exit 2
      fi
      shift ;;
  esac
done

# ------------------------ Sanity / Tools --------------------------
need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing tool: $1"; exit 1; }; }
need cmake; need ninja; need javac; need jar; need gperf
command -v php >/dev/null 2>&1 || echo "Note: PHP not found (only needed if AddIntDef.php is present)"

CMAKE_VER=$(cmake --version | head -1 | awk '{print $3}')
NINJA_VER=$(ninja --version)
ver_ge () { [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | head -1)" = "$1" ]; }
ver_ge "3.29" "$CMAKE_VER" || { echo "Require: cmake >= 3.29 (found $CMAKE_VER)"; exit 1; }
ver_ge "1.11" "$NINJA_VER" || { echo "Require: ninja >= 1.11 (found $NINJA_VER)"; exit 1; }

[[ -n "${NDK}" && -d "${NDK}" ]] || { echo "NDK not found at: ${NDK:-<empty>}"; exit 1; }
if [[ "$ANDROID_STL" != "c++_static" && "$ANDROID_STL" != "c++_shared" ]]; then
  echo 'Error: --stl must be "c++_static" or "c++_shared".' >&2
  exit 1
fi

HOST_OS=$(uname | tr '[:upper:]' '[:lower:]')
HOST_ARCH_RAW=$(uname -m)
case "$HOST_ARCH_RAW" in x86_64|amd64) HOST_ARCH="x86_64" ;; arm64|aarch64) HOST_ARCH="arm64" ;; *) HOST_ARCH="x86_64" ;; esac
PREBUILT_DIR="${NDK}/toolchains/llvm/prebuilt/${HOST_OS}-${HOST_ARCH}"
[[ -d "$PREBUILT_DIR" ]] || { echo "NDK prebuilt toolchain not found at $PREBUILT_DIR"; exit 1; }

# ------------------------ Pfade / Layout --------------------------
ROOT="$(pwd)"
TD_DIR="$ROOT/td"          # TD-Quellen liegen hier (Workflow klont zuvor)
OUT_DIR="$ROOT/out"        # Artefakte IM REPO-ROOT (passt zum Workflow)
JAVA_SRC_DIR="$OUT_DIR/java_src"
JAVA_CLASSES_DIR="$OUT_DIR/classes"

# BoringSSL-Check
IFS=',' read -ra ABI_ARR <<< "$ABIS"
for abi in "${ABI_ARR[@]}"; do
  abi="$(echo "$abi" | xargs)"
  [[ -d "$BORINGSSL_DIR/$abi/include" ]] || { echo "Missing $BORINGSSL_DIR/$abi/include"; exit 1; }
  [[ -f "$BORINGSSL_DIR/$abi/lib/libcrypto.a" && -f "$BORINGSSL_DIR/$abi/lib/libssl.a" ]] \
    || { echo "Missing libcrypto.a/libssl.a in $BORINGSSL_DIR/$abi/lib"; exit 1; }
done

# Clean & prepare out
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/libs" "$JAVA_SRC_DIR" "$JAVA_CLASSES_DIR"

echo "== TDLib build: JNI + Java bindings (SDK-free) =="
echo "NDK       : $NDK"
echo "CMake     : $CMAKE_VER"
echo "Ninja     : $NINJA_VER"
echo "STL       : $ANDROID_STL"
echo "API-Level : android-$API_LEVEL"
echo "ABIs      : $ABIS"
echo "BoringSSL : $BORINGSSL_DIR"
echo "TD_DIR    : $TD_DIR"
echo "OUT_DIR   : $OUT_DIR"
echo

# ------------------------ TDLib-Quelle prüfen ---------------------
[[ -d "$TD_DIR" ]] || { echo "TD source dir not found: $TD_DIR"; exit 1; }

pushd "$TD_DIR" >/dev/null

# Optional: falls du hier noch ref wechseln willst (Workflow checkt idR vorher aus)
if [[ -n "${TD_REF}" ]]; then
  git fetch --depth=1 origin "$TD_REF"
  git checkout -qf FETCH_HEAD
fi

COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
DESCRIBE="$(git describe --tags --always --long --dirty=+ 2>/dev/null || echo "$COMMIT")"
echo "$DESCRIBE" > "$OUT_DIR/TDLIB_VERSION.txt"

# ------------------------ 1) Java-Bindings ------------------------
echo "-- Generating Java sources (TdApi.java) ..."

# (A) Offizieller Weg: install-Target triggert die Generierung
cmake -S . -B build-java-install \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX:PATH=example/java/td \
  -DTD_ENABLE_JNI=ON
cmake --build build-java-install --target install

# Optional zusätzlich: Generator-Binary bauen (nicht direkt ausführen)
cmake -S . -B build-native-java \
  -G Ninja \
  -DTD_ENABLE_JNI=OFF \
  -DTD_ENABLE_TESTS=OFF \
  -DTD_ENABLE_TDJSON=OFF \
  -DTD_GENERATE_SOURCE_FILES=ON \
  -DGPERF_EXECUTABLE:FILEPATH="$(command -v gperf)" \
  -DCMAKE_BUILD_TYPE=Release
cmake --build build-native-java --target td_generate_java_api -- -v

echo "-- Searching for generated TdApi.java ..."
TDAPI_SRC=""
for candidate in \
  "example/java/td/src/main/java/org/drinkless/tdlib/TdApi.java" \
  "example/java/src/main/java/org/drinkless/tdlib/TdApi.java" \
  "build-native-java/td/generate/java/org/drinkless/tdlib/TdApi.java" \
  "td/generate/java/org/drinkless/tdlib/TdApi.java" \
  "generate/java/org/drinkless/tdlib/TdApi.java" \
  "tdlib/generate/java/org/drinkless/tdlib/TdApi.java"
do
  [[ -f "$candidate" ]] && { TDAPI_SRC="$candidate"; break; }
done
[[ -z "${TDAPI_SRC}" ]] && TDAPI_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/TdApi.java' | head -n1 || true)"
[[ -z "${TDAPI_SRC}" ]] && TDAPI_SRC="$(find . -type f -path '*/org/drinkless/tdlib/TdApi.java' | head -n1 || true)"
[[ -n "$TDAPI_SRC" && -f "$TDAPI_SRC" ]] || { echo "❌ Could not locate generated TdApi.java"; exit 1; }
echo "✅ Found TdApi.java at $TDAPI_SRC"

CLIENT_SRC="example/java/org/drinkless/tdlib/Client.java"
CACHE_SRC="example/java/org/drinkless/tdlib/Cache.java"
if [[ ! -f "$CLIENT_SRC" || ! -f "$CACHE_SRC" ]]; then
  echo "⚠️  Searching for Client.java / Cache.java ..."
  CLIENT_SRC=$(find . -type f -name "Client.java" | grep "/org/drinkless/tdlib/" | head -n1 || true)
  CACHE_SRC=$(find . -type f -name "Cache.java"  | grep "/org/drinkless/tdlib/" | head -n1 || true)
fi
[[ -f "$CLIENT_SRC" ]] || { echo "❌ Missing Client.java"; exit 1; }
[[ -f "$CACHE_SRC"  ]] || { echo "❌ Missing Cache.java"; exit 1; }

mkdir -p "$JAVA_SRC_DIR/org/drinkless/tdlib"
cp -f "$TDAPI_SRC"  "$JAVA_SRC_DIR/org/drinkless/tdlib/TdApi.java"
cp -f "$CLIENT_SRC" "$JAVA_SRC_DIR/org/drinkless/tdlib/Client.java"
cp -f "$CACHE_SRC"  "$JAVA_SRC_DIR/org/drinkless/tdlib/Cache.java"

# ------------------------ 2) JNI je ABI ---------------------------
for ABI in "${ABI_ARR[@]}"; do
  ABI="$(echo "$ABI" | xargs)"
  BUILD_DIR="build-${ABI}-jni"

  echo "-- Configuring $ABI ..."
  cmake -G Ninja \
    -S . -B "$BUILD_DIR" \
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

  echo "-- Building $ABI tdjni ..."
  cmake --build "$BUILD_DIR" --target tdjni

  soPath="${BUILD_DIR}/libtdjni.so"
  [[ -f "$soPath" ]] || soPath="$(find "$BUILD_DIR" -maxdepth 3 -name 'libtdjni.so' | head -n1 || true)"
  [[ -n "${soPath:-}" && -f "${soPath}" ]] || { echo "❌ libtdjni.so not found for $ABI"; exit 1; }

  mkdir -p "$OUT_DIR/libs/${ABI}"
  cp -f "$soPath" "$OUT_DIR/libs/${ABI}/"

  # ggf. libc++_shared.so
  if [[ "$ANDROID_STL" == "c++_shared" ]]; then
    case "$ABI" in
      arm64-v8a)  TRIPLE="aarch64-linux-android" ;;
      armeabi-v7a) TRIPLE="arm-linux-androideabi" ;;
      *) TRIPLE="" ;;
    esac
    if [[ -n "$TRIPLE" ]]; then
      SHARED_STL="${PREBUILT_DIR}/sysroot/usr/lib/${TRIPLE}/libc++_shared.so"
      [[ -f "$SHARED_STL" ]] && cp -f "$SHARED_STL" "$OUT_DIR/libs/${ABI}/" && \
        "${PREBUILT_DIR}/bin/llvm-strip" "$OUT_DIR/libs/${ABI}/libc++_shared.so" || true
    fi
  fi

  "${PREBUILT_DIR}/bin/llvm-strip" --strip-unneeded "$OUT_DIR/libs/${ABI}/libtdjni.so" || true
done

popd >/dev/null

# ------------------------ 3) Java -> JAR --------------------------
echo "-- Compiling Java bindings to JAR ..."
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
```0