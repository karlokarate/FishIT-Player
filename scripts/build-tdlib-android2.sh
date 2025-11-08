#!/usr/bin/env bash
# -*- coding: utf-8 -*-
# TDLib Android Builder — JNI + Java Bindings (SDK-free, static BoringSSL)
# Validated against upstream CMakeLists requirements:
#   - Host requires OpenSSL + Zlib; early return otherwise (we ensure via apt). 
#   - prepare_cross_compiling must run BEFORE install so td_api.tlo exists.
#   - For Android, we link BoringSSL using OPENSSL_* variables.
#   - LTO: use TD_ENABLE_LTO when requested.
# See upstream CMake for details. 

set -uo pipefail
SOFT_FAIL="${SOFT_FAIL:-1}"      # 1=tolerant weiterbauen, 0=hart abbrechen
FINAL_EXIT="${FINAL_EXIT:-1}"    # 1=roter Exit bei Fehlern, 0=grün trotz Fehlern

declare -a BUILD_ERRORS=()
record_err(){ BUILD_ERRORS+=("$1"); }

group(){ echo "::group::$1"; }
endgroup(){ echo "::endgroup::"; }

run(){
  local what="$1"; shift
  group "$what"
  echo "> $what"
  if "$@"; then
    echo "[ok] $what"
  else
    local rc=$?
    echo "[fail:$rc] $what"
    record_err "$what (exit=$rc)"
    if [[ "$SOFT_FAIL" != "1" ]]; then endgroup; return $rc; fi
  fi
  endgroup
}

NINJA_KEEP=( -- -k 0 )

# --- CLI ---------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ndk)            NDK="$2"; shift 2 ;;
    --abis)           ABIS="$2"; shift 2 ;;
    --api-level)      API_LEVEL="$2"; shift 2 ;;
    --boringssl-dir)  BORINGSSL_DIR="$2"; shift 2 ;;
    --ref|--td-ref)   TD_REF="$2"; shift 2 ;;
    --stl)            ANDROID_STL="$2"; shift 2 ;;
    --interpro)       CMAKE_INTERPRO="$2"; shift 2 ;;
    *) echo "[warn] unknown arg: $1"; shift ;;
  esac
done

# --- Eingaben / ENV ----------------------------------------------------------
NDK="${NDK:-${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-}}}"
BORINGSSL_DIR="${BORINGSSL_DIR:-.third_party/boringssl}"
ABIS="${ABIS:-arm64-v8a,armeabi-v7a}"
API_LEVEL="${API_LEVEL:-21}"
ANDROID_STL="${ANDROID_STL:-c++_static}"
TD_REF="${TD_REF:-}"
CMAKE_INTERPRO="${CMAKE_INTERPRO:-0}"

# Optional: fehlende Tools automatisch installieren
ensure() {
  if command -v apt-get >/dev/null 2>&1 && command -v sudo >/dev/null 2>&1; then
    for pkg in "$@"; do
      local apt_pkg="$pkg"
      [[ "$pkg" == "ninja" ]] && apt_pkg="ninja-build"
      if ! command -v "$pkg" >/dev/null 2>&1; then
        echo "[ensure] installing $apt_pkg ..."
        sudo apt-get update -y && sudo apt-get install -y "$apt_pkg"
      fi
    done
  fi
}
ensure cmake ninja gperf ccache || true

# Tool-Checks
need(){ command -v "$1" >/dev/null 2>&1 || { echo "Missing tool: $1"; exit 1; }; }
need cmake; need ninja; need javac; need jar; need gperf

HOST_OS=$(uname | tr '[:upper:]' '[:lower:]')
case "$(uname -m)" in x86_64|amd64) HOST_ARCH="x86_64" ;; arm64|aarch64) HOST_ARCH="arm64" ;; *) HOST_ARCH="x86_64" ;; esac

ROOT="$(pwd)"
TD_DIR="$ROOT/td"
OUT_DIR="$ROOT/out"
JAVA_SRC_DIR="$OUT_DIR/java_src"
JAVA_CLASSES_DIR="$OUT_DIR/classes"
LOG_DIR="$OUT_DIR/logs"
LOG_FILE="$LOG_DIR/build.log"
REPORT_TXT="$LOG_DIR/summary.txt"
REPORT_JSON="$LOG_DIR/summary.json"

INSTALL_LIBDIR="lib"
INSTALL_BINDIR="bin"
INSTALL_INCLUDEDIR="include"
CMAKEDIR_REL="${INSTALL_LIBDIR}/cmake/Td"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/libs" "$JAVA_SRC_DIR" "$JAVA_CLASSES_DIR" "$LOG_DIR"

# Globales Logging
exec > >(tee -a "$LOG_FILE") 2>&1

[[ -n "$NDK" && -d "$NDK" ]] || { echo "NDK not found at: ${NDK:-<empty>}"; exit 1; }
PREBUILT_DIR="${NDK}/toolchains/llvm/prebuilt/${HOST_OS}-${HOST_ARCH}"
[[ -d "$PREBUILT_DIR" ]] || { echo "NDK prebuilt toolchain not found at $PREBUILT_DIR"; exit 1; }

# BoringSSL absolut machen
if [[ -d "$BORINGSSL_DIR" ]]; then BORINGSSL_DIR="$(cd "$BORINGSSL_DIR" && pwd)"; fi

IFS=',' read -ra ABI_ARR <<< "$ABIS"

# ccache (optional)
if command -v ccache >/dev/null 2>&1; then
  export CCACHE_DIR="${CCACHE_DIR:-$ROOT/.ccache}"
  export CCACHE_BASEDIR="${CCACHE_BASEDIR:-$ROOT}"
  export CCACHE_MAXSIZE="${CCACHE_MAXSIZE:-2G}"
  echo "[ccache] dir=$CCACHE_DIR basedir=$CCACHE_BASEDIR max=$CCACHE_MAXSIZE"
  CC_LAUNCH=( -DCMAKE_C_COMPILER_LAUNCHER=ccache -DCMAKE_CXX_COMPILER_LAUNCHER=ccache )
else
  CC_LAUNCH=(); echo "[ccache] not found (ok)"
fi

# IPO/LTO knobs
if [[ "$CMAKE_INTERPRO" == "1" ]]; then
  ANDROID_OPT=(-DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON)
  TD_LTO=(-DTD_ENABLE_LTO=ON)   # upstream option
else
  ANDROID_OPT=(); TD_LTO=()
fi

cat <<EOF
== TDLib build: JNI + Java bindings (SDK-free) ==
NDK       : $NDK
STL       : $ANDROID_STL
API-Level : android-$API_LEVEL
ABIs      : $ABIS
BoringSSL : $BORINGSSL_DIR
TD_DIR    : $TD_DIR
OUT_DIR   : $OUT_DIR
LOG_FILE  : $LOG_FILE
EOF

[[ -d "$TD_DIR" ]] || { echo "TD source dir not found: $TD_DIR"; exit 1; }
pushd "$TD_DIR" >/dev/null

# Optional: bestimmter TD-Ref
if [[ -n "$TD_REF" ]]; then
  run "git-fetch-ref" git fetch --depth=1 origin "$TD_REF"
  run "git-checkout-ref" git checkout -qf FETCH_HEAD
fi
COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
DESCRIBE="$(git describe --tags --always --long --dirty=+ 2>/dev/null || echo "$COMMIT")"
echo "$DESCRIBE" > "$OUT_DIR/TDLIB_VERSION.txt"

# Host: kleiner Header-Check
echo '#include <openssl/ssl.h>' | (cc -E - >/dev/null 2>&1 || echo "[warn] Host OpenSSL headers not visible; ensure libssl-dev is installed")

# ---------------------------------------------------------------------------
# (1) HOST-BUILD & INSTALL   (liefert tl-parser, td_generate_java_api, TdConfig)
#     prepare_cross_compiling (Upstream-Target) VOR install → .tlo sicher
# ---------------------------------------------------------------------------
HOST_INSTALL_PREFIX="$TD_DIR/example/java/td"
run "host-clean-install-prefix" bash -lc "rm -rf '$HOST_INSTALL_PREFIX'"

run "host-configure" cmake -S . -B build-host -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX:PATH="$HOST_INSTALL_PREFIX" \
  -DCMAKE_INSTALL_LIBDIR="$INSTALL_LIBDIR" \
  -DCMAKE_INSTALL_BINDIR="$INSTALL_BINDIR" \
  -DCMAKE_INSTALL_INCLUDEDIR="$INSTALL_INCLUDEDIR" \
  -DTD_ENABLE_TESTS=OFF \
  -DTD_ENABLE_JNI=ON \
  "${TD_LTO[@]}" \
  "${CC_LAUNCH[@]}"

run "host-build" cmake --build build-host "${NINJA_KEEP[@]}"
run "host-prepare_cross_compiling" cmake --build build-host --target prepare_cross_compiling "${NINJA_KEEP[@]}"
run "host-install" cmake --build build-host --target install "${NINJA_KEEP[@]}"

TD_HOST_CMAKE="$HOST_INSTALL_PREFIX/$CMAKEDIR_REL/TdConfig.cmake"
[[ -f "$TD_HOST_CMAKE" ]] || record_err "Missing $TD_HOST_CMAKE"

GEN_DIR="$HOST_INSTALL_PREFIX/$INSTALL_BINDIR/td/generate"
SCHEME_DST_DIR="$GEN_DIR/scheme"
mkdir -p "$GEN_DIR" "$SCHEME_DST_DIR"

# Fallbacks: falls Install bestimmte Files nicht gelegt hat
if [[ ! -x "$GEN_DIR/td_generate_java_api" ]]; then
  SRC_BIN="$(find build-host -type f -path '*/td/generate/td_generate_java_api' | head -n1 || true)"
  if [[ -n "$SRC_BIN" && -f "$SRC_BIN" ]]; then
    run "fallback-copy-tdgen" cp -f "$SRC_BIN" "$GEN_DIR/td_generate_java_api"
    chmod +x "$GEN_DIR/td_generate_java_api" || true
  fi
fi
if [[ -x "$GEN_DIR/td_generate_java_api" && ! -x "$HOST_INSTALL_PREFIX/$INSTALL_BINDIR/td_generate_java_api" ]]; then
  run "compat-copy-tdgen-root" cp -f "$GEN_DIR/td_generate_java_api" "$HOST_INSTALL_PREFIX/$INSTALL_BINDIR/td_generate_java_api"
fi

# PHP-Generatoren sicherstellen
if [[ ! -f "$GEN_DIR/TlDocumentationGenerator.php" || ! -f "$GEN_DIR/JavadocTlDocumentationGenerator.php" ]]; then
  if [[ -d "td/generate" ]]; then
    run "copy-php-generators" rsync -a --include='*/' --include='*.php' --exclude='*' "td/generate/" "$GEN_DIR/"
  fi
fi

# Schema (tl/tlo) aus Host-Build übernehmen
for f in td_api.tl td_api.tlo; do
  if [[ ! -s "$SCHEME_DST_DIR/$f" ]]; then
    SRC="$(find build-host -type f -path "*/td/generate/scheme/$f" | head -n1 || true)"
    [[ -n "$SRC" && -f "$SRC" ]] && run "copy-schema-$f" cp -f "$SRC" "$SCHEME_DST_DIR/$f"
  fi
done

# PATH für Generator
export PATH="$HOST_INSTALL_PREFIX/$INSTALL_BINDIR:$PATH"

# Sanity-Listing
for must in \
  "$HOST_INSTALL_PREFIX/$CMAKEDIR_REL/TdConfig.cmake" \
  "$GEN_DIR/td_generate_java_api" \
  "$SCHEME_DST_DIR/td_api.tl" \
  "$SCHEME_DST_DIR/td_api.tlo"; do
  [[ -e "$must" ]] || record_err "missing $must"
done
ls -l "$HOST_INSTALL_PREFIX/$INSTALL_BINDIR" || true
ls -l "$GEN_DIR" || true
ls -l "$SCHEME_DST_DIR" || true

# ---------------------------------------------------------------------------
# (2) example/java: Java-Install → erzeugt TdApi.java, Client.java
# ---------------------------------------------------------------------------
run "java-configure" cmake -S example/java -B example/java/build -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="$HOST_INSTALL_PREFIX" \
  -DTd_DIR="$HOST_INSTALL_PREFIX/$CMAKEDIR_REL" \
  -DCMAKE_INSTALL_PREFIX:PATH="$TD_DIR/example/java" \
  "${CC_LAUNCH[@]}"
run "java-install" cmake --build example/java/build --target install "${NINJA_KEEP[@]}"

TDAPI_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/TdApi.java' | head -n1 || true)"
CLIENT_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/Client.java' | head -n1 || true)"
[[ -f "$TDAPI_SRC"  ]] || record_err "Could not locate generated TdApi.java"
[[ -f "$CLIENT_SRC" ]] || record_err "Missing Client.java"

run "copy-java-sources" bash -c "mkdir -p '$JAVA_SRC_DIR/org/drinkless/tdlib' && \
  { [[ -f '$TDAPI_SRC'  ]] && cp -f '$TDAPI_SRC'  '$JAVA_SRC_DIR/org/drinkless/tdlib/TdApi.java'  || true; } && \
  { [[ -f '$CLIENT_SRC' ]] && cp -f '$CLIENT_SRC' '$JAVA_SRC_DIR/org/drinkless/tdlib/Client.java' || true; }"

# ---------------------------------------------------------------------------
# (3) PRO-ABI: Android-TDLib (Core) bauen & installieren → tdjni je ABI
# ---------------------------------------------------------------------------
for ABI in "${ABI_ARR[@]}"; do
  ABI="$(echo "$ABI" | xargs)"
  [[ -d "$BORINGSSL_DIR/$ABI/include" ]] || record_err "BoringSSL include missing for $ABI"
  [[ -f "$BORINGSSL_DIR/$ABI/lib/libcrypto.a" ]] || record_err "BoringSSL libcrypto.a missing for $ABI"
  [[ -f "$BORINGSSL_DIR/$ABI/lib/libssl.a"    ]] || record_err "BoringSSL libssl.a missing for $ABI"

  ANDROID_PREFIX="$TD_DIR/example/java/td-android-$ABI"

  run "core-$ABI-configure" cmake -S . -B "build-td-android-$ABI" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DCMAKE_BUILD_TYPE=RelWithDebInfo \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="android-$API_LEVEL" \
    -DOPENSSL_USE_STATIC_LIBS=ON \
    -DOPENSSL_INCLUDE_DIR="$BORINGSSL_DIR/$ABI/include" \
    -DOPENSSL_SSL_LIBRARY="$BORINGSSL_DIR/$ABI/lib/libssl.a" \
    -DOPENSSL_CRYPTO_LIBRARY="$BORINGSSL_DIR/$ABI/lib/libcrypto.a" \
    -DOPENSSL_LIBRARIES="$BORINGSSL_DIR/$ABI/lib/libssl.a;$BORINGSSL_DIR/$ABI/lib/libcrypto.a" \
    -DCMAKE_INSTALL_PREFIX:PATH="$ANDROID_PREFIX" \
    "${ANDROID_OPT[@]}" \
    "${TD_LTO[@]}" \
    "${CC_LAUNCH[@]}"

  run "core-$ABI-build"   cmake --build "build-td-android-$ABI" "${NINJA_KEEP[@]}"
  run "core-$ABI-install" cmake --build "build-td-android-$ABI" --target install "${NINJA_KEEP[@]}"

  ANDROID_TD_CMAKE_DIR="$ANDROID_PREFIX/$CMAKEDIR_REL"
  [[ -f "$ANDROID_TD_CMAKE_DIR/TdConfig.cmake" ]] || record_err "Missing Android TdConfig for $ABI"

  pushd "$TD_DIR/example/java" >/dev/null
  BDIR="build-android-$ABI"

  run "jni-$ABI-configure" cmake -S . -B "$BDIR" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DCMAKE_BUILD_TYPE=RelWithDebInfo \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="android-$API_LEVEL" \
    -DANDROID_STL="$ANDROID_STL" \
    -DTd_DIR="$ANDROID_TD_CMAKE_DIR" \
    -DCMAKE_PREFIX_PATH="$ANDROID_PREFIX" \
    -DTD_ENABLE_JNI=ON \
    -DOPENSSL_USE_STATIC_LIBRARIES=ON \
    -DOPENSSL_INCLUDE_DIR="$BORINGSSL_DIR/$ABI/include" \
    -DOPENSSL_SSL_LIBRARY="$BORINGSSL_DIR/$ABI/lib/libssl.a" \
    -DOPENSSL_CRYPTO_LIBRARY="$BORINGSSL_DIR/$ABI/lib/libcrypto.a" \
    -DOPENSSL_LIBRARIES="$BORINGSSL_DIR/$ABI/lib/libssl.a;$BORINGSSL_DIR/$ABI/lib/libcrypto.a" \
    "${ANDROID_OPT[@]}" \
    "${TD_LTO[@]}" \
    "${CC_LAUNCH[@]}"

  run "jni-$ABI-build-tdjni" cmake --build "$BDIR" --target tdjni "${NINJA_KEEP[@]}"

  soPath="$(find "$BDIR" -name 'libtdjni.so' -print -quit || true)"
  if [[ -f "$soPath" ]]; then
    run "jni-$ABI-copy-so" bash -c "mkdir -p '$OUT_DIR/libs/$ABI' && cp -f '$soPath' '$OUT_DIR/libs/$ABI/' && \
      '$PREBUILT_DIR/bin/llvm-strip' --strip-unneeded '$OUT_DIR/libs/$ABI/libtdjni.so' || true"
    if [[ "$ANDROID_STL" == "c++_shared" ]]; then
      case "$ABI" in
        arm64-v8a)  TRIPLE="aarch64-linux-android" ;;
        armeabi-v7a) TRIPLE="arm-linux-androideabi" ;;
        x86)         TRIPLE="i686-linux-android" ;;
        x86_64)      TRIPLE="x86_64-linux-android" ;;
      esac
      SHARED_STL="$PREBUILT_DIR/sysroot/usr/lib/$TRIPLE/libc++_shared.so"
      [[ -f "$SHARED_STL" ]] && run "jni-$ABI-copy-stl" cp -f "$SHARED_STL" "$OUT_DIR/libs/$ABI/"
    fi
  else
    record_err "libtdjni.so not found for $ABI"
  fi
  popd >/dev/null
done

popd >/dev/null  # aus $TD_DIR

# ---------------------------------------------------------------------------
# (4) Optional: Java → JAR packen
# ---------------------------------------------------------------------------
run "java-jar-sources-list" bash -c "find '$JAVA_SRC_DIR' -name '*.java' > '$OUT_DIR/java-sources.list' || true"
if [[ -s "$OUT_DIR/java-sources.list" ]]; then
  run "java-javac" javac --release 8 -d "$JAVA_CLASSES_DIR" @"$OUT_DIR/java-sources.list"
  DESC="local"; if [[ -d "$TD_DIR/.git" ]]; then DESC="$(git -C "$TD_DIR" describe --tags --always --long --dirty=+ 2>/dev/null || git -C "$TD_DIR" rev-parse --short HEAD)"; fi
  JAR_PATH="$OUT_DIR/tdlib-${DESC}.jar"
  run "java-jar-pack" jar cf "$JAR_PATH" -C "$JAVA_CLASSES_DIR" .
else
  record_err "TdApi.java not generated; skip javac/jar"
fi

# ---------------------------------------------------------------------------
# (5) Zusammenfassung
# ---------------------------------------------------------------------------
ERR_COUNT=${#BUILD_ERRORS[@]}
{
  echo "==== Build Summary ===="
  echo "Date: $(date -Iseconds)"
  echo "TD Commit: $DESCRIBE"
  echo "NDK: $NDK"
  echo "ABIs: $ABIS"
  echo "Errors: $ERR_COUNT"
  if ((ERR_COUNT)); then
    printf ' - %s\n' "${BUILD_ERRORS[@]}"
  else
    echo "No errors recorded."
  fi
} | tee "$REPORT_TXT"

{
  echo '{'
  printf '  "date": "%s",\n' "$(date -Iseconds)"
  printf '  "td_commit": "%s",\n' "$DESCRIBE"
  printf '  "ndk": "%s",\n' "$NDK"
  printf '  "abis": "%s",\n' "$ABIS"
  printf '  "errors": ['
  if ((ERR_COUNT)); then
    for i in "${!BUILD_ERRORS[@]}"; do
      printf '%s{"msg": %q}' "$([[ $i -gt 0 ]] && echo ',')" "${BUILD_ERRORS[$i]}"
    done
  fi
  echo ']'
  echo '}'
} > "$REPORT_JSON"

cat <<EOF
== Artifacts ==
$OUT_DIR/TDLIB_VERSION.txt
$OUT_DIR/libs/*/libtdjni.so
$OUT_DIR/tdlib-*.jar
$OUT_DIR/logs/build.log
$OUT_DIR/logs/summary.txt
$OUT_DIR/logs/summary.json
EOF

if ((ERR_COUNT)) && [[ "$FINAL_EXIT" != "0" ]]; then
  exit 1
else
  exit 0
fi
