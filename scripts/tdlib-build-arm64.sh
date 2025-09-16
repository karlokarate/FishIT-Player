#!/usr/bin/env bash
set -euo pipefail

# Build TDLib (JNI) for Android arm64-v8a and copy libtdjni.so into :libtd.
# Prereqs: git, cmake (>=3.18), Ninja (optional), Android NDK r23+.
# Env: ANDROID_NDK_HOME or ANDROID_NDK_ROOT must point to NDK.

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# Prefer repo-local toolchains if present
export PATH="$REPO_DIR/.wsl-cmake/bin:$REPO_DIR/.wsl-ninja/bin:$PATH"
OUT_DIR="$REPO_DIR/libtd/src/main/jniLibs/arm64-v8a"
TD_DIR="$REPO_DIR/.third_party/td"

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

# Fetch BoringSSL (shallow)
if [[ ! -d "$BORING_DIR/.git" ]]; then
  echo "Cloning BoringSSL…"
  git clone --depth 1 https://boringssl.googlesource.com/boringssl "$BORING_DIR"
fi

# Build BoringSSL static libs for arm64-v8a (PIC)
mkdir -p "$BORING_BUILD_DIR"
cd "$BORING_BUILD_DIR"
GEN=""
if command -v ninja >/dev/null 2>&1; then GEN="-G Ninja"; fi
echo "Configuring BoringSSL (arm64-v8a)…"
cmake \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=Release \
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

# 1) Generate TDLib source files for cross-compilation (host build)
NATIVE_BUILD_DIR="$TD_DIR/build-native-gen"
mkdir -p "$NATIVE_BUILD_DIR"
cd "$NATIVE_BUILD_DIR"
echo "Preparing TDLib generated sources (host)…"
cmake -DCMAKE_BUILD_TYPE=Release -DTD_GENERATE_SOURCE_FILES=ON -DTD_ENABLE_JNI=ON "$TD_DIR"
cmake --build . --target prepare_cross_compiling -j$(nproc || sysctl -n hw.ncpu || echo 4)

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

echo "Configuring TDLib example ($(basename "$EXAMPLE_DIR")) (arm64-v8a, static BoringSSL)…"
cmake \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_STANDARD=17 \
  -DCMAKE_CXX_STANDARD_REQUIRED=ON \
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

mkdir -p "$OUT_DIR"
cp -f "$LIB_PATH" "$OUT_DIR/"
echo "OK: Copied $(basename "$LIB_PATH") -> $OUT_DIR"
 
# Strip debug info to keep APK size small
STRIP_BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
if [[ -x "$STRIP_BIN" ]]; then
  echo "Stripping debug symbols (arm64-v8a)…"
  "$STRIP_BIN" --strip-debug "$OUT_DIR/libtdjni.so" || true
fi
