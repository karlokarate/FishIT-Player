#!/usr/bin/env bash
# TDLib Android Builder — JNI + Java Bindings (SDK-free, static BoringSSL)
# Final: API24, Preflight-Dirs, force BoringSSL/Zlib, verbose, JNI Javadoc fix, sccache/ccache auto
set -euo pipefail

SOFT_FAIL="${SOFT_FAIL:-1}"; FINAL_EXIT="${FINAL_EXIT:-1}"
declare -a BUILD_ERRORS=(); record_err(){ BUILD_ERRORS+=("$1"); }
group(){ echo "::group::$1"; }; endgroup(){ echo "::endgroup::"; }
run(){ local what="$1"; shift; group "$what"; echo "> $what"
  if "$@"; then echo "[ok] $what"; else local rc=$?; echo "[fail:$rc] $what"; record_err "$what (exit=$rc)"; [[ "$SOFT_FAIL" != "1" ]] && { endgroup; return $rc; }; fi
  endgroup; }

NINJA_KEEP=( -- -k 0 )

# ---- CLI --------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ndk) NDK="$2"; shift 2 ;;
    --abis) ABIS="$2"; shift 2 ;;
    --api-level) API_LEVEL="$2"; shift 2 ;;
    --boringssl-dir) BORINGSSL_DIR="$2"; shift 2 ;;
    --ref|--td-ref) TD_REF="$2"; shift 2 ;;
    --stl) ANDROID_STL="$2"; shift 2 ;;
    --interpro) CMAKE_INTERPRO="$2"; shift 2 ;;
    *) echo "[warn] unknown arg: $1"; shift ;;
  esac
done

# ---- ENV/Defaults -----------------------------------------------------------
NDK="${NDK:-${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-}}}"
BORINGSSL_DIR="${BORINGSSL_DIR:-.third_party/boringssl}"
ABIS="${ABIS:-arm64-v8a,armeabi-v7a}"
API_LEVEL="${API_LEVEL:-24}"
ANDROID_STL="${ANDROID_STL:-c++_static}"
TD_REF="${TD_REF:-}"; CMAKE_INTERPRO="${CMAKE_INTERPRO:-0}"

need(){ command -v "$1" >/dev/null 2>&1 || { echo "Missing tool: $1"; exit 1; }; }
need cmake; need ninja; need javac; need jar; need gperf

HOST_OS=$(uname | tr '[:upper:]' '[:lower:]'); case "$(uname -m)" in x86_64|amd64) HOST_ARCH="x86_64";; aarch64|arm64) HOST_ARCH="arm64";; *) HOST_ARCH="x86_64";; esac
ROOT="$(pwd)"; TD_DIR="$ROOT/td"; OUT_DIR="$ROOT/out"
JAVA_SRC_DIR="$OUT_DIR/java_src"; JAVA_CLASSES_DIR="$OUT_DIR/classes"; LOG_DIR="$OUT_DIR/logs"
LOG_FILE="$LOG_DIR/build.log"; REPORT_TXT="$LOG_DIR/summary.txt"; REPORT_JSON="$LOG_DIR/summary.json"
INSTALL_LIBDIR="lib"; INSTALL_BINDIR="bin"; INSTALL_INCLUDEDIR="include"; CMAKEDIR_REL="${INSTALL_LIBDIR}/cmake/Td"

# ---- PRE-FLIGHT: ALLES ANLEGEN ----------------------------------------------
rm -rf "$OUT_DIR"; mkdir -p "$OUT_DIR" "$JAVA_SRC_DIR" "$JAVA_CLASSES_DIR" "$LOG_DIR"
[[ -d "$TD_DIR" ]] || { echo "TD source dir not found: $TD_DIR"; exit 1; }
[[ -n "$NDK" && -d "$NDK" ]] || { echo "NDK not found at: ${NDK:-<empty>}"; exit 1; }
PREBUILT_DIR="${NDK}/toolchains/llvm/prebuilt/${HOST_OS}-${HOST_ARCH}"
[[ -d "$PREBUILT_DIR" ]] || { echo "NDK prebuilt toolchain not found at $PREBUILT_DIR"; exit 1; }
[[ -d "$BORINGSSL_DIR" ]] || { echo "BoringSSL dir missing: $BORINGSSL_DIR"; exit 1; }
BORINGSSL_DIR="$(cd "$BORINGSSL_DIR" && pwd)"

HOST_INSTALL_PREFIX="$TD_DIR/example/java/td"
DOCS_DIR="$TD_DIR/example/java/docs"
mkdir -p "$TD_DIR/example/java" \
         "$HOST_INSTALL_PREFIX/$INSTALL_LIBDIR" "$HOST_INSTALL_PREFIX/$INSTALL_BINDIR" "$HOST_INSTALL_PREFIX/$INSTALL_INCLUDEDIR" "$HOST_INSTALL_PREFIX/$CMAKEDIR_REL" \
         "$HOST_INSTALL_PREFIX/$INSTALL_BINDIR/td/generate/scheme" \
         "$DOCS_DIR"

IFS=',' read -ra ABI_ARR <<< "$ABIS"
for _abi in "${ABI_ARR[@]}"; do
  _abi="$(echo "$_abi" | xargs)"
  ANDROID_PREFIX="$TD_DIR/example/java/td-android-$_abi"
  mkdir -p "$ANDROID_PREFIX/$INSTALL_LIBDIR" "$ANDROID_PREFIX/$INSTALL_BINDIR" "$ANDROID_PREFIX/$INSTALL_INCLUDEDIR" "$ANDROID_PREFIX/$CMAKEDIR_REL" "$OUT_DIR/libs/$_abi"
  [[ -d "$BORINGSSL_DIR/$_abi/include" ]] || { echo "BoringSSL include missing: $BORINGSSL_DIR/$_abi/include"; exit 1; }
  [[ -f "$BORINGSSL_DIR/$_abi/lib/libssl.a"    ]] || { echo "BoringSSL libssl.a missing for $_abi"; exit 1; }
  [[ -f "$BORINGSSL_DIR/$_abi/lib/libcrypto.a" ]] || { echo "BoringSSL libcrypto.a missing for $_abi"; exit 1; }
done

# ---- LOG UMLEITEN -----------------------------------------------------------
exec > >(tee -a "$LOG_FILE") 2>&1

# ---- Compiler-Launcher -------------------------------------------------------
if command -v sccache >/dev/null 2>&1; then
  echo "[launcher] using sccache"; CC_LAUNCH=( -DCMAKE_C_COMPILER_LAUNCHER=sccache -DCMAKE_CXX_COMPILER_LAUNCHER=sccache )
elif command -v ccache >/dev/null 2>&1; then
  echo "[launcher] using ccache"; export CCACHE_DIR="${CCACHE_DIR:-$ROOT/.ccache}"; export CCACHE_BASEDIR="${CCACHE_BASEDIR:-$ROOT}"; export CCACHE_MAXSIZE="${CCACHE_MAXSIZE:-2G}"
  CC_LAUNCH=( -DCMAKE_C_COMPILER_LAUNCHER=ccache -DCMAKE_CXX_COMPILER_LAUNCHER=ccache )
else echo "[launcher] none"; CC_LAUNCH=(); fi

# ---- IPO/LTO ----------------------------------------------------------------
if [[ "${CMAKE_INTERPRO:-0}" == "1" ]]; then ANDROID_OPT=(-DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON); TD_LTO=(-DTD_ENABLE_LTO=ON); else ANDROID_OPT=(); TD_LTO=(); fi

echo "== TDLib build: JNI + Java bindings (SDK-free) =="
echo "NDK       : $NDK"; echo "STL       : $ANDROID_STL"; echo "API-Level : android-$API_LEVEL"
echo "ABIs      : $ABIS"; echo "BoringSSL : $BORINGSSL_DIR"; echo "TD_DIR    : $TD_DIR"; echo "OUT_DIR   : $OUT_DIR"; echo "LOG_FILE  : $LOG_FILE"

# Host-OpenSSL via pkg-config hart deaktivieren
export PKG_CONFIG_LIBDIR=""; export PKG_CONFIG_PATH=""; export OPENSSL_CONF="/dev/null"

pushd "$TD_DIR" >/dev/null

# ---- Ref --------------------------------------------------------------------
if [[ -n "$TD_REF" ]]; then run "git-fetch-ref" git fetch --depth=1 origin "$TD_REF"; run "git-checkout-ref" git checkout -qf FETCH_HEAD; fi
COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
DESCRIBE="$(git describe --tags --always --long --dirty=+ 2>/dev/null || echo "$COMMIT")"
echo "$DESCRIBE" > "$OUT_DIR/TDLIB_VERSION.txt"

# ---- Host build & install ---------------------------------------------------
run "host-configure" cmake -S . -B build-host -G Ninja \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_VERBOSE_MAKEFILE=ON \
  -DCMAKE_INSTALL_PREFIX:PATH="$HOST_INSTALL_PREFIX" \
  -DCMAKE_INSTALL_LIBDIR="$INSTALL_LIBDIR" -DCMAKE_INSTALL_BINDIR="$INSTALL_BINDIR" -DCMAKE_INSTALL_INCLUDEDIR="$INSTALL_INCLUDEDIR" \
  -DTD_ENABLE_TESTS=OFF -DTD_ENABLE_JNI=ON \
  "${TD_LTO[@]}" "${CC_LAUNCH[@]}"
run "host-build" cmake --build build-host "${NINJA_KEEP[@]}"
run "host-prepare_cross_compiling" cmake --build build-host --target prepare_cross_compiling "${NINJA_KEEP[@]}"
run "host-install" cmake --build build-host --target install "${NINJA_KEEP[@]}"

TD_HOST_CMAKE="$HOST_INSTALL_PREFIX/$CMAKEDIR_REL/TdConfig.cmake"
[[ -f "$TD_HOST_CMAKE" ]] || record_err "Missing $TD_HOST_CMAKE"

GEN_DIR="$HOST_INSTALL_PREFIX/$INSTALL_BINDIR/td/generate"
SCHEME_DST_DIR="$GEN_DIR/scheme"; mkdir -p "$GEN_DIR" "$SCHEME_DST_DIR"
if [[ ! -x "$GEN_DIR/td_generate_java_api" ]]; then SRC_BIN="$(find build-host -type f -path '*/td/generate/td_generate_java_api' | head -n1 || true)"; [[ -n "$SRC_BIN" ]] && run "fallback-copy-tdgen" cp -f "$SRC_BIN" "$GEN_DIR/td_generate_java_api" && chmod +x "$GEN_DIR/td_generate_java_api" || true; fi
if [[ -x "$GEN_DIR/td_generate_java_api" && ! -x "$HOST_INSTALL_PREFIX/$INSTALL_BINDIR/td_generate_java_api" ]]; then run "compat-copy-tdgen-root" cp -f "$GEN_DIR/td_generate_java_api" "$HOST_INSTALL_PREFIX/$INSTALL_BINDIR/td_generate_java_api"; fi
if [[ ! -f "$GEN_DIR/TlDocumentationGenerator.php" || ! -f "$GEN_DIR/JavadocTlDocumentationGenerator.php" ]]; then [[ -d "td/generate" ]] && run "copy-php-generators" rsync -a --include='*/' --include='*.php' --exclude='*' "td/generate/" "$GEN_DIR/"; fi
for f in td_api.tl td_api.tlo; do if [[ ! -s "$SCHEME_DST_DIR/$f" ]]; then SRC="$(find build-host -type f -path "*/td/generate/scheme/$f" | head -n1 || true)"; [[ -n "$SRC" && -f "$SRC" ]] && run "copy-schema-$f" cp -f "$SRC" "$SCHEME_DST_DIR/$f"; fi; done
export PATH="$HOST_INSTALL_PREFIX/$INSTALL_BINDIR:$PATH"

# ---- Helpers ----------------------------------------------------------------
triple_for_abi(){ case "$1" in arm64-v8a) echo "aarch64-linux-android";; armeabi-v7a) echo "arm-linux-androideabi";; x86) echo "i686-linux-android";; x86_64) echo "x86_64-linux-android";; *) echo ""; esac; }
detect_jni_hints(){ local JAVA_HOME_IN="${JAVA_HOME:-}"; if [[ -z "$JAVA_HOME_IN" ]] && command -v javac >/dev/null 2>&1; then local JBIN; JBIN="$(readlink -f "$(command -v javac)")"; JAVA_HOME_IN="$(cd "$(dirname "$JBIN")/.." && pwd)"; fi
  local JVM_SO="" INC1="" INC2=""; if [[ -n "$JAVA_HOME_IN" && -d "$JAVA_HOME_IN" ]]; then INC1="$JAVA_HOME_IN/include"; INC2="$JAVA_HOME_IN/include/linux"
    if [[ -f "$JAVA_HOME_IN/lib/server/libjvm.so" ]]; then JVM_SO="$JAVA_HOME_IN/lib/server/libjvm.so"; else JVM_SO="$(find "$JAVA_HOME_IN" -type f -name libjvm.so | head -n1 || true)"; fi; fi
  if [[ -f "${JVM_SO:-/nope}" && -d "${INC1:-/nope}" ]]; then
    JNI_HINTS=(-DANDROID=ON -DJAVA_HOME="$JAVA_HOME_IN" -DJAVA_JVM_LIBRARY="$JVM_SO" -DJAVA_INCLUDE_PATH="$INC1" -DJAVA_INCLUDE_PATH2="$INC2" -DJNI_INCLUDE_DIRS="$INC1;$INC2" -DJNI_LIBRARIES="$JVM_SO")
  else echo "[jni] WARN: Host-JDK unvollständig"; JNI_HINTS=(-DANDROID=ON); fi; }

# ---- Java-Bindings (Host) ---------------------------------------------------
run "java-configure" cmake -S example/java -B example/java/build -G Ninja \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_VERBOSE_MAKEFILE=ON \
  -DCMAKE_PREFIX_PATH="$HOST_INSTALL_PREFIX" -DTd_DIR="$HOST_INSTALL_PREFIX/$CMAKEDIR_REL" \
  -DCMAKE_INSTALL_PREFIX:PATH="$TD_DIR/example/java" "${CC_LAUNCH[@]}"
run "java-install" cmake --build example/java/build --target install "${NINJA_KEEP[@]}"
TDAPI_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/TdApi.java' | head -n1 || true)"
CLIENT_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/Client.java' | head -n1 || true)"
[[ -f "$TDAPI_SRC" ]] || record_err "Could not locate generated TdApi.java"
[[ -f "$CLIENT_SRC" ]] || record_err "Missing Client.java"
run "copy-java-sources" bash -c "mkdir -p '$JAVA_SRC_DIR/org/drinkless/tdlib'; [[ -f '$TDAPI_SRC' ]] && cp -f '$TDAPI_SRC'  '$JAVA_SRC_DIR/org/drinkless/tdlib/TdApi.java' || true; [[ -f '$CLIENT_SRC' ]] && cp -f '$CLIENT_SRC' '$JAVA_SRC_DIR/org/drinkless/tdlib/Client.java' || true"

# ---- Core + JNI je ABI ------------------------------------------------------
for ABI in "${ABI_ARR[@]}"; do
  ABI="$(echo "$ABI" | xargs)"; ANDROID_PREFIX="$TD_DIR/example/java/td-android-$ABI"; TRIPLE="$(triple_for_abi "$ABI")"
  ZLIB_INC="$PREBUILT_DIR/sysroot/usr/include"
  Z1="$PREBUILT_DIR/sysroot/usr/lib/$TRIPLE/$API_LEVEL/libz.a"
  Z2="$PREBUILT_DIR/sysroot/usr/lib/$TRIPLE/libz.a"
  Z3="$PREBUILT_DIR/sysroot/usr/lib/armv7a-linux-androideabi/$API_LEVEL/libz.a"   # extra Fallback für v7a
  if   [[ -f "$Z1" ]]; then ZLIB_LIB="$Z1"
  elif [[ -f "$Z2" ]]; then ZLIB_LIB="$Z2"
  elif [[ -f "$Z3" ]]; then ZLIB_LIB="$Z3"
  else echo "zlib not found under NDK sysroot"; exit 1; fi

  rm -rf "build-td-android-$ABI"

  # --- Core (tdcore etc.) ---
  run "core-$ABI-configure" cmake -S . -B "build-td-android-$ABI" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DCMAKE_BUILD_TYPE=RelWithDebInfo -DCMAKE_VERBOSE_MAKEFILE=ON \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DANDROID=ON -DANDROID_ABI="$ABI" \
    -DANDROID_STL="$ANDROID_STL" -DANDROID_PLATFORM="android-$API_LEVEL" \
    -DCMAKE_FIND_USE_SYSTEM_ENVIRONMENT_PATH=OFF \
    -DOPENSSL_FOUND=ON \
    -DOPENSSL_ROOT_DIR="$BORINGSSL_DIR/$ABI" \
    -DOPENSSL_INCLUDE_DIR="$BORINGSSL_DIR/$ABI/include" \
    -DOPENSSL_CRYPTO_LIBRARY="$BORINGSSL_DIR/$ABI/lib/libcrypto.a" \
    -DOPENSSL_SSL_LIBRARY="$BORINGSSL_DIR/$ABI/lib/libssl.a" \
    -DOPENSSL_LIBRARIES="$BORINGSSL_DIR/$ABI/lib/libssl.a;$BORINGSSL_DIR/$ABI/lib/libcrypto.a" \
    -DZLIB_FOUND=ON \
    -DZLIB_INCLUDE_DIR="$ZLIB_INC" -DZLIB_LIBRARY="$ZLIB_LIB" -DZLIB_LIBRARIES="$ZLIB_LIB" \
    -DBUILD_TESTING=OFF -DTD_INSTALL_SHARED_LIBRARIES=OFF -DTD_INSTALL_STATIC_LIBRARIES=ON \
    -DCMAKE_INSTALL_PREFIX:PATH="$ANDROID_PREFIX" \
    "${ANDROID_OPT[@]}" "${TD_LTO[@]}" "${CC_LAUNCH[@]}" \
  || { echo ">>> CMakeError.log (core-$ABI)"; tail -n +1 "build-td-android-$ABI/CMakeFiles/CMakeError.log" 2>/dev/null || true; false; }

  run "core-$ABI-build"   cmake --build "build-td-android-$ABI" "${NINJA_KEEP[@]}" \
  || { echo ">>> tail build log (core-$ABI)"; (find "build-td-android-$ABI" -name '*.log' -print -quit | xargs -r tail -n 200) || true; false; }

  run "core-$ABI-install" cmake --build "build-td-android-$ABI" --target install "${NINJA_KEEP[@]}"

  ANDROID_TD_CMAKE_DIR="$ANDROID_PREFIX/$CMAKEDIR_REL"
  [[ -f "$ANDROID_TD_CMAKE_DIR/TdConfig.cmake" ]] || record_err "Missing Android TdConfig for $ABI"  # CMake export laut CMakeLists erwartet 1

  # --- JNI (tdjni) ---
  detect_jni_hints
  pushd "$TD_DIR/example/java" >/dev/null; BDIR="build-android-$ABI"; rm -rf "$BDIR"
  mkdir -p "$DOCS_DIR" "$TD_DIR/example/java/$INSTALL_BINDIR"

  run "jni-$ABI-configure" cmake -S . -B "$BDIR" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DCMAKE_BUILD_TYPE=RelWithDebInfo -DCMAKE_VERBOSE_MAKEFILE=ON \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DANDROID=ON \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$API_LEVEL" -DANDROID_STL="$ANDROID_STL" \
    -DCMAKE_FIND_USE_SYSTEM_ENVIRONMENT_PATH=OFF \
    -DTd_DIR="$ANDROID_TD_CMAKE_DIR" -DCMAKE_PREFIX_PATH="$ANDROID_PREFIX" \
    -DCMAKE_INSTALL_PREFIX:PATH="$TD_DIR/example/java" \
    -DTD_ENABLE_JNI=ON \
    -DOPENSSL_FOUND=ON \
    -DOPENSSL_ROOT_DIR="$BORINGSSL_DIR/$ABI" \
    -DOPENSSL_INCLUDE_DIR="$BORINGSSL_DIR/$ABI/include" \
    -DOPENSSL_SSL_LIBRARY="$BORINGSSL_DIR/$ABI/lib/libssl.a" \
    -DOPENSSL_CRYPTO_LIBRARY="$BORINGSSL_DIR/$ABI/lib/libcrypto.a" \
    -DOPENSSL_LIBRARIES="$BORINGSSL_DIR/$ABI/lib/libssl.a;$BORINGSSL_DIR/$ABI/lib/libcrypto.a" \
    -DZLIB_FOUND=ON \
    -DZLIB_INCLUDE_DIR="$ZLIB_INC" -DZLIB_LIBRARY="$ZLIB_LIB" -DZLIB_LIBRARIES="$ZLIB_LIB" \
    "${JNI_HINTS[@]}" "${ANDROID_OPT[@]}" "${TD_LTO[@]}" "${CC_LAUNCH[@]}" \
  || { echo ">>> CMakeError.log (jni-$ABI)"; tail -n +1 "$BDIR/CMakeFiles/CMakeError.log" 2>/dev/null || true; false; }

  run "jni-$ABI-build-tdjni" cmake --build "$BDIR" --target tdjni "${NINJA_KEEP[@]}" \
  || { echo ">>> tail build log (jni-$ABI)"; (find "$BDIR" -name '*.log' -print -quit | xargs -r tail -n 200) || true; false; }

  soPath="$(find "$BDIR" -name 'libtdjni.so' -print -quit || true)"
  if [[ -f "$soPath" ]]; then run "jni-$ABI-copy-so" bash -c "cp -f '$soPath' '$OUT_DIR/libs/$ABI/libtdjni.so' && '$PREBUILT_DIR/bin/llvm-strip' --strip-unneeded '$OUT_DIR/libs/$ABI/libtdjni.so' || true"
  else record_err "libtdjni.so not found for $ABI"; fi
  popd >/dev/null
done

popd >/dev/null

# ---- Java → JAR -------------------------------------------------------------
run "java-jar-sources-list" bash -c "find '$JAVA_SRC_DIR' -name '*.java' > '$OUT_DIR/java-sources.list' || true"
if [[ -s "$OUT_DIR/java-sources.list" ]]; then
  run "java-javac" javac --release 8 -d "$JAVA_CLASSES_DIR" @"$OUT_DIR/java-sources.list"
  DESC="local"; [[ -d "$TD_DIR/.git" ]] && DESC="$(git -C "$TD_DIR" describe --tags --always --long --dirty=+ 2>/dev/null || git -C "$TD_DIR" rev-parse --short HEAD)"
  JAR_PATH="$OUT_DIR/tdlib-${DESC}.jar"; run "java-jar-pack" jar cf "$JAR_PATH" -C "$JAVA_CLASSES_DIR" .
else record_err "TdApi.java not generated; skip javac/jar"; fi

# ---- Summary ----------------------------------------------------------------
ERR_COUNT=${#BUILD_ERRORS[@]}
{ echo "==== Build Summary ===="; echo "Date: $(date -Iseconds)"; echo "TD Commit: $DESCRIBE"; echo "NDK: $NDK"; echo "ABIs: $ABIS"; echo "Errors: $ERR_COUNT"
  if ((ERR_COUNT)); then printf ' - %s\n' "${BUILD_ERRORS[@]}"; else echo "No errors recorded."; fi; } | tee "$REPORT_TXT"
{ echo '{'; printf '  "date": "%s",\n' "$(date -Iseconds)"; printf '  "td_commit": "%s",\n' "$DESCRIBE"; printf '  "ndk": "%s",\n' "$NDK"; printf '  "abis": "%s",\n' "$ABIS"; printf '  "errors": ['; if ((ERR_COUNT)); then for i in "${!BUILD_ERRORS[@]}"; do printf '%s{"msg": %q}' "$([[ $i -gt 0 ]] && echo ',')" "${BUILD_ERRORS[$i]}"; done; fi; echo ']'; echo '}'; } > "$REPORT_JSON"

cat <<EOF
== Artifacts ==
$OUT_DIR/TDLIB_VERSION.txt
$OUT_DIR/libs/*/libtdjni.so
$OUT_DIR/tdlib-*.jar
$OUT_DIR/logs/build.log
$OUT_DIR/logs/summary.txt
$OUT_DIR/logs/summary.json
EOF

if ((ERR_COUNT)) && [[ "$FINAL_EXIT" != "0" ]]; then exit 1; else exit 0; fi