#!/usr/bin/env bash
set -euo pipefail

# Build TDLib (JNI) for Android arm64-v8a and copy libtdjni.so into :libtd.
# Focus: smallest possible .so without breaking TDLib functionality used in-app.
# Techniques: LTO, MinSizeRel, section GC/ICF, static BoringSSL, aggressive strip.
# Prereqs: git, cmake (>=3.18), Ninja (optional), Android NDK r23+.
# Env: ANDROID_NDK_HOME or ANDROID_NDK_ROOT must point to NDK.

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# Simple args: --only-arm64 | --only-v7a | --skip-v7a | --ref <tag-or-commit>
BUILD_ARM64=1
BUILD_V7A=1
TD_REF_ARG=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --only-arm64) BUILD_V7A=0 ;;
    --only-v7a) BUILD_ARM64=0 ;;
    --skip-v7a|--no-v7a) BUILD_V7A=0 ;;
    --ref|--tag|--commit)
      shift
      TD_REF_ARG="${1:-}"
      ;;
    *)
      echo "Unknown argument: $1" >&2 ;;
  esac
  shift || true
done
# Prefer repo-local toolchains if present
export PATH="$REPO_DIR/.wsl-cmake/bin:$REPO_DIR/.wsl-ninja/bin:$REPO_DIR/.wsl-gperf/usr/bin:$REPO_DIR/.wsl-gperf/bin:$PATH"
OUT_DIR64="$REPO_DIR/libtd/src/main/jniLibs/arm64-v8a"
OUT_DIR32="$REPO_DIR/libtd/src/main/jniLibs/armeabi-v7a"
TD_DIR="$REPO_DIR/.third_party/td"
# Pin to a specific TDLib tag/branch/commit for reproducibility.
# Default: latest validated stable for this project.
TD_DEFAULT_TAG="v1.8.56"
# Priority: CLI --ref > TD_TAG env > TD_COMMIT env > default
TD_PIN_REF="${TD_REF_ARG:-${TD_TAG:-${TD_COMMIT:-$TD_DEFAULT_TAG}}}"

# BoringSSL (static) config
BORING_DIR="$REPO_DIR/.third_party/boringssl"
BORING_BUILD_DIR="$BORING_DIR/build-android-arm64"

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

# Ensure tags are available for selection
(cd "$TD_DIR" && git fetch --tags --force --prune origin >/dev/null 2>&1 || true)

# Try to checkout the requested pin; if missing, fallback to latest v* tag
if [[ -n "$TD_PIN_REF" ]]; then
  echo "Checking out TDLib ref: $TD_PIN_REF (or latest v* tag as fallback)…"
  if (cd "$TD_DIR" && git fetch --depth 1 origin "$TD_PIN_REF" >/dev/null 2>&1); then
    (cd "$TD_DIR" && git checkout --detach FETCH_HEAD)
  else
    echo "Ref $TD_PIN_REF not found upstream; using latest v* tag…" >&2
    LATEST_TAG=$(cd "$TD_DIR" && git ls-remote --tags --refs origin 'v*' | awk -F/ '{print $3}' | sort -V | tail -1)
    if [[ -z "$LATEST_TAG" ]]; then
      echo "ERROR: Could not determine latest TDLib tag." >&2
      exit 7
    fi
    echo "Resolved latest TDLib tag: $LATEST_TAG"
    (cd "$TD_DIR" && git fetch --depth 1 origin "refs/tags/$LATEST_TAG" && git checkout --detach FETCH_HEAD)
  fi
else
  echo "TD_PIN_REF empty; selecting latest v* tag…"
  LATEST_TAG=$(cd "$TD_DIR" && git ls-remote --tags --refs origin 'v*' | awk -F/ '{print $3}' | sort -V | tail -1)
  if [[ -z "$LATEST_TAG" ]]; then
    echo "ERROR: Could not determine latest TDLib tag." >&2
    exit 7
  fi
  echo "Resolved latest TDLib tag: $LATEST_TAG"
  (cd "$TD_DIR" && git fetch --depth 1 origin "refs/tags/$LATEST_TAG" && git checkout --detach FETCH_HEAD)
fi

# Fetch BoringSSL (shallow)
if [[ ! -d "$BORING_DIR/.git" ]]; then
  echo "Cloning BoringSSL…"
  git clone --depth 1 https://boringssl.googlesource.com/boringssl "$BORING_DIR"
fi

if (( BUILD_ARM64 == 1 )); then
  # Build BoringSSL static libs for arm64-v8a (PIC)
  mkdir -p "$BORING_BUILD_DIR"
  cd "$BORING_BUILD_DIR"
  GEN=""
  if command -v ninja >/dev/null 2>&1; then GEN="-G Ninja"; fi
  echo "Configuring BoringSSL (arm64-v8a, MinSizeRel, IPO)…"
  cmake \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DCMAKE_BUILD_TYPE=MinSizeRel \
    -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON \
    -DCMAKE_C_FLAGS_MINSIZEREL="-Os -ffunction-sections -fdata-sections" \
    -DCMAKE_CXX_FLAGS_MINSIZEREL="-Os -ffunction-sections -fdata-sections" \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    $GEN \
    "$BORING_DIR"

  echo "Building BoringSSL (ssl, crypto)…"
  cmake --build . --target ssl crypto -j$(nproc || sysctl -n hw.ncpu || echo 4)

  # Resolve produced static libs and include dir
  SSL_A="$BORING_BUILD_DIR/ssl/libssl.a"
  CRYPTO_A="$BORING_BUILD_DIR/crypto/libcrypto.a"
  if [[ ! -f "$SSL_A" ]]; then
    if [[ -f "$BORING_BUILD_DIR/libssl.a" ]]; then SSL_A="$BORING_BUILD_DIR/libssl.a"; fi
  fi
  if [[ ! -f "$CRYPTO_A" ]]; then
    if [[ -f "$BORING_BUILD_DIR/libcrypto.a" ]]; then CRYPTO_A="$BORING_BUILD_DIR/libcrypto.a"; fi
  fi
  if [[ ! -f "$SSL_A" || ! -f "$CRYPTO_A" ]]; then
    echo "ERROR: BoringSSL static libraries not found (ssl: $SSL_A, crypto: $CRYPTO_A)." >&2
    exit 3
  fi
  BORING_INCLUDE="$BORING_DIR/include"
  if [[ ! -d "$BORING_INCLUDE" ]]; then
    echo "ERROR: BoringSSL include directory not found: $BORING_INCLUDE" >&2
    exit 4
  fi
fi

# 1) Generate TDLib source files for cross-compilation (host build)
NATIVE_BUILD_DIR="$TD_DIR/build-native-gen"
mkdir -p "$NATIVE_BUILD_DIR"
cd "$NATIVE_BUILD_DIR"
echo "Preparing TDLib generated sources (host)…"
cmake -DCMAKE_BUILD_TYPE=Release -DTD_GENERATE_SOURCE_FILES=ON -DTD_ENABLE_JNI=ON "$TD_DIR"
cmake --build . --target prepare_cross_compiling -j$(nproc || sysctl -n hw.ncpu || echo 4)

# Ensure TdApi.java is generated into example/java for binding sync
if rg -n "add_custom_target\(td_generate_java_api" "$TD_DIR/example/java/CMakeLists.txt" >/dev/null 2>&1; then
  echo "Generating Java API (TdApi.java) …"
  cmake --build . --target td_generate_java_api -j$(nproc || sysctl -n hw.ncpu || echo 4) || true
fi

# Patch gperf-generated sources for C++17 (remove obsolete 'register' specifier)
if [[ -d "$TD_DIR/tdutils/generate/auto" ]]; then
  echo "Patching gperf-generated files for C++17 compatibility…"
  sed -i -E 's/\bregister\s+//g' "$TD_DIR/tdutils/generate/auto/mime_type_to_extension.gperf" || true
  sed -i -E 's/\bregister\s+//g' "$TD_DIR/tdutils/generate/auto/extension_to_mime_type.gperf" || true
  sed -i -E 's/\bregister\s+//g' "$TD_DIR/tdutils/generate/auto/mime_type_to_extension.cpp" || true
  sed -i -E 's/\bregister\s+//g' "$TD_DIR/tdutils/generate/auto/extension_to_mime_type.cpp" || true
fi

# 2) Build Android JNI with static BoringSSL
JNI_BUILD_DIR="$TD_DIR/build-android-arm64-jni"
EXAMPLE_DIR="$TD_DIR/example/android"
USE_WRAPPER=0
if [[ ! -d "$EXAMPLE_DIR" ]]; then
  EXAMPLE_DIR="$TD_DIR/example/java"
  if [[ -d "$EXAMPLE_DIR" ]]; then
    USE_WRAPPER=1
  fi
fi
if (( BUILD_ARM64 == 1 )); then
  mkdir -p "$JNI_BUILD_DIR"
  # Clear previous CMake cache if switching source layout (example vs wrapper)
  rm -rf "$JNI_BUILD_DIR"/CMakeCache.txt "$JNI_BUILD_DIR"/CMakeFiles
  cd "$JNI_BUILD_DIR"

GEN=""
if command -v ninja >/dev/null 2>&1; then GEN="-G Ninja"; fi

if [[ "$USE_WRAPPER" -eq 1 ]]; then
  echo "Preparing wrapper CMake (example/java) …"
cat > CMakeLists.txt <<'EOF'
cmake_minimum_required(VERSION 3.10)
project(tdjni_wrap LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if (NOT DEFINED TD_DIR)
  message(FATAL_ERROR "TD_DIR must be provided via -DTD_DIR=... pointing to TDLib source directory")
endif()
add_subdirectory(${TD_DIR} td)
add_library(tdjni SHARED "${TD_DIR}/example/java/td_jni.cpp")
target_link_libraries(tdjni PRIVATE Td::TdStatic)
if (ANDROID_ABI STREQUAL "armeabi-v7a")
  target_link_libraries(tdjni PRIVATE atomic)
endif()
if (CMAKE_SYSROOT)
  target_include_directories(tdjni SYSTEM PRIVATE ${CMAKE_SYSROOT}/usr/include)
endif()
target_compile_definitions(tdjni PRIVATE PACKAGE_NAME="org/drinkless/tdlib")
EOF
  SRC_DIR="$(pwd)"
else
  SRC_DIR="$EXAMPLE_DIR"
fi

  echo "Configuring TDLib example ($(basename "$EXAMPLE_DIR")) (arm64-v8a, static BoringSSL, LTO, MinSizeRel)…"
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
    -DOPENSSL_INCLUDE_DIR="$BORING_INCLUDE" \
    -DOPENSSL_SSL_LIBRARY="$SSL_A" \
    -DOPENSSL_CRYPTO_LIBRARY="$CRYPTO_A" \
    -DTD_DIR="$TD_DIR" \
    $GEN \
    "$SRC_DIR"

  echo "Building tdjni…"
  cmake --build . --target tdjni -j$(nproc || sysctl -n hw.ncpu || echo 4)

  # Locate libtdjni.so produced by example/android
  LIB_PATH="$(pwd)/libtdjni.so"
  if [[ ! -f "$LIB_PATH" ]]; then
    if [[ -f "./lib/libtdjni.so" ]]; then LIB_PATH="./lib/libtdjni.so"; fi
    if [[ -f "./jni/libtdjni.so" ]]; then LIB_PATH="./jni/libtdjni.so"; fi
  fi

  if [[ ! -f "$LIB_PATH" ]]; then
    echo "ERROR: libtdjni.so not found after build." >&2
    exit 2
  fi

  mkdir -p "$OUT_DIR64"
  cp -f "$LIB_PATH" "$OUT_DIR64/"
  echo "OK: Copied $(basename "$LIB_PATH") -> $OUT_DIR64"

  # Always align Java bindings (TdApi.java, Client.java) to the same TDLib commit
  echo "Copying Java bindings (TdApi.java, Client.java) from TDLib example/java…"
  SRC_JAVA_DIR="$TD_DIR/example/java/org/drinkless/tdlib"
  if [[ -f "$SRC_JAVA_DIR/TdApi.java" && -f "$SRC_JAVA_DIR/Client.java" ]]; then
    DEST_DIR="$REPO_DIR/libtd/src/main/java/org/drinkless/tdlib"
    mkdir -p "$DEST_DIR"
    cp -f "$SRC_JAVA_DIR/TdApi.java" "$DEST_DIR/"
    cp -f "$SRC_JAVA_DIR/Client.java" "$DEST_DIR/"
    echo "OK: Java bindings synchronized. (Note: Log.java is kept local to match JNI signatures)"
  else
    echo "WARNING: TDLib example/java sources not found; skipping Java binding copy" >&2
  fi

  # Robust strip discovery across platforms
  STRIP_BIN=""
  for host in linux-x86_64 windows-x86_64 darwin-x86_64 darwin-aarch64; do
    cand="$NDK/toolchains/llvm/prebuilt/$host/bin/llvm-strip"
    if [[ -x "$cand" ]]; then STRIP_BIN="$cand"; break; fi
  done
  if [[ -z "${STRIP_BIN:-}" ]]; then STRIP_BIN="$(command -v llvm-strip || true)"; fi
  if [[ -z "${STRIP_BIN:-}" ]]; then STRIP_BIN="$(command -v strip || true)"; fi
  if [[ -n "${STRIP_BIN:-}" ]]; then
    echo "Stripping unneeded symbols (arm64-v8a) using $(basename "$STRIP_BIN")…"
    "$STRIP_BIN" --strip-unneeded -x "$OUT_DIR64/libtdjni.so" || true
  fi

  # Optional sanity check: ensure no dynamic OpenSSL deps leaked
  if command -v readelf >/dev/null 2>&1; then
    echo "Verifying no dynamic OpenSSL deps…"
    if readelf -d "$OUT_DIR64/libtdjni.so" | rg -i 'NEEDED.*(ssl|crypto)' >/dev/null; then
      echo "WARNING: OpenSSL dyn deps detected in libtdjni.so (unexpected)" >&2
    else
      echo "OK: no dynamic OpenSSL deps"
    fi
  fi
fi

# =====================
# Build for armeabi-v7a
# =====================
if (( BUILD_V7A == 1 )); then
echo "\n=== Building TDLib for armeabi-v7a (size-optimized) ==="

# Reconfigure BoringSSL for v7a
BORING_BUILD_DIR_32="$BORING_DIR/build-android-armv7"
mkdir -p "$BORING_BUILD_DIR_32"
cd "$BORING_BUILD_DIR_32"
GEN=""; if command -v ninja >/dev/null 2>&1; then GEN="-G Ninja"; fi
echo "Configuring BoringSSL (armeabi-v7a, MinSizeRel, IPO)…"
cmake \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=armeabi-v7a \
  -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=MinSizeRel \
  -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON \
  -DCMAKE_C_FLAGS_MINSIZEREL="-Os -ffunction-sections -fdata-sections" \
  -DCMAKE_CXX_FLAGS_MINSIZEREL="-Os -ffunction-sections -fdata-sections" \
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  $GEN \
  "$BORING_DIR"
cmake --build . --target ssl crypto -j$(nproc || sysctl -n hw.ncpu || echo 4)
SSL_A_32="$BORING_BUILD_DIR_32/ssl/libssl.a"; [[ -f "$SSL_A_32" ]] || SSL_A_32="$BORING_BUILD_DIR_32/libssl.a"
CRYPTO_A_32="$BORING_BUILD_DIR_32/crypto/libcrypto.a"; [[ -f "$CRYPTO_A_32" ]] || CRYPTO_A_32="$BORING_BUILD_DIR_32/libcrypto.a"
if [[ ! -f "$SSL_A_32" || ! -f "$CRYPTO_A_32" ]]; then
  echo "ERROR: BoringSSL (v7a) static libraries not found" >&2; exit 5
fi

# TDLib JNI for v7a
JNI_BUILD_DIR_32="$TD_DIR/build-android-armv7-jni"
mkdir -p "$JNI_BUILD_DIR_32" && rm -rf "$JNI_BUILD_DIR_32/CMakeCache.txt" "$JNI_BUILD_DIR_32/CMakeFiles"
cd "$JNI_BUILD_DIR_32"
if [[ "$USE_WRAPPER" -eq 1 ]]; then
  cat > CMakeLists.txt <<'EOF'
cmake_minimum_required(VERSION 3.10)
project(tdjni_wrap_v7 LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if (NOT DEFINED TD_DIR)
  message(FATAL_ERROR "TD_DIR must be provided via -DTD_DIR=...")
endif()
add_subdirectory(${TD_DIR} td)
add_library(tdjni SHARED "${TD_DIR}/example/java/td_jni.cpp")
target_link_libraries(tdjni PRIVATE Td::TdStatic)
if (ANDROID_ABI STREQUAL "armeabi-v7a")
  target_link_libraries(tdjni PRIVATE atomic)
endif()
if (CMAKE_SYSROOT)
  target_include_directories(tdjni SYSTEM PRIVATE ${CMAKE_SYSROOT}/usr/include)
endif()
target_compile_definitions(tdjni PRIVATE PACKAGE_NAME="org/drinkless/tdlib")
EOF
  SRC_DIR_32="$(pwd)"
else
  SRC_DIR_32="$EXAMPLE_DIR"
fi

echo "Configuring TDLib (armeabi-v7a, static BoringSSL, LTO, MinSizeRel)…"
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
  -DOPENSSL_INCLUDE_DIR="$BORING_INCLUDE" \
  -DOPENSSL_SSL_LIBRARY="$SSL_A_32" \
  -DOPENSSL_CRYPTO_LIBRARY="$CRYPTO_A_32" \
  -DTD_DIR="$TD_DIR" \
  $GEN \
  "$SRC_DIR_32"

cmake --build . --target tdjni -j$(nproc || sysctl -n hw.ncpu || echo 4)

LIB32_PATH="$(pwd)/libtdjni.so"; [[ -f "$LIB32_PATH" ]] || LIB32_PATH="./lib/libtdjni.so"; [[ -f "$LIB32_PATH" ]] || LIB32_PATH="./jni/libtdjni.so"
if [[ ! -f "$LIB32_PATH" ]]; then echo "ERROR: libtdjni.so (v7a) not found" >&2; exit 6; fi

mkdir -p "$OUT_DIR32"
cp -f "$LIB32_PATH" "$OUT_DIR32/"
echo "OK: Copied $(basename "$LIB32_PATH") -> $OUT_DIR32"

if [[ -n "${STRIP_BIN:-}" ]]; then
  echo "Stripping unneeded symbols (armeabi-v7a)…"
  "$STRIP_BIN" --strip-unneeded -x "$OUT_DIR32/libtdjni.so" || true
fi

if command -v readelf >/dev/null 2>&1; then
  echo "Verifying no dynamic OpenSSL deps (v7a)…"
  if readelf -d "$OUT_DIR32/libtdjni.so" | rg -i 'NEEDED.*(ssl|crypto)' >/dev/null; then
    echo "WARNING: OpenSSL dyn deps detected in v7a libtdjni.so" >&2
  else
    echo "OK: no dynamic OpenSSL deps (v7a)"
  fi
fi
fi
