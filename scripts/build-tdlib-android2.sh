#!/usr/bin/env bash
# -*- coding: utf-8 -*-
# TDLib Android Builder — JNI + Java Bindings (SDK-free, static BoringSSL, NDK r27b/r27c)
# Soft-fail & maximal Caching: baut so viel wie möglich, sammelt Fehler & schreibt Log/Report
# Pfade & Dateinamen strikt gemäß TDLib CMakeLists:
#  - INSTALL {lib,bin,include}
#  - CMake-Package: lib/cmake/Td/TdConfig.cmake
#  - Generator:     bin/td_generate_java_api
#  - Java-Sources:  TdApi.java, Client.java, Log.java

# --- Soft-Fail-Modus ----------------------------------------------------------
# Kein -e, damit wir kontrolliert weiterlaufen können
set -uo pipefail
SOFT_FAIL="${SOFT_FAIL:-1}"      # 1=tolerant weiterbauen, 0=hart abbrechen
FINAL_EXIT="${FINAL_EXIT:-1}"    # 1=roten Exitcode, 0=grün trotz Fehlern

# Fehler-/Status-Sammlung
declare -a BUILD_ERRORS=()
record_err(){ BUILD_ERRORS+=("$1"); }

# Gruppierung für GitHub Actions Logs
group(){ echo "::group::$1"; }
endgroup(){ echo "::endgroup::"; }

# Wrapper für Kommandos (respektiert SOFT_FAIL)
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

# Ninja: bei Fehlern weiter (-k 0)
NINJA_KEEP=( -- -k 0 )

# --- CLI / ENV ----------------------------------------------------------------
NDK="${ANDROID_NDK_ROOT:-}"
BORINGSSL_DIR="${BORINGSSL_DIR:-.third_party/boringssl}"
ABIS="${ABIS:-arm64-v8a,armeabi-v7a}"
API_LEVEL="${API_LEVEL:-21}"
ANDROID_STL="${ANDROID_STL:-c++_static}"
TD_REF="${TD_REF:-}"

# Tools prüfen
need(){ command -v "$1" >/dev/null 2>&1 || { echo "Missing tool: $1"; exit 1; }; }
need cmake; need ninja; need javac; need jar; need gperf

# Host-Toolchain lokalisieren
HOST_OS=$(uname | tr '[:upper:]' '[:lower:]')
HOST_ARCH_RAW=$(uname -m)
case "$HOST_ARCH_RAW" in x86_64|amd64) HOST_ARCH="x86_64" ;; arm64|aarch64) HOST_ARCH="arm64" ;; *) HOST_ARCH="x86_64" ;; esac

# Projektpfade
ROOT="$(pwd)"
TD_DIR="$ROOT/td"
OUT_DIR="$ROOT/out"
JAVA_SRC_DIR="$OUT_DIR/java_src"
JAVA_CLASSES_DIR="$OUT_DIR/classes"
LOG_DIR="$OUT_DIR/logs"
LOG_FILE="$LOG_DIR/build.log"
REPORT_TXT="$LOG_DIR/summary.txt"
REPORT_JSON="$LOG_DIR/summary.json"

# CMake-Install-Layout
INSTALL_LIBDIR="lib"
INSTALL_BINDIR="bin"
INSTALL_INCLUDEDIR="include"
CMAKEDIR_REL="${INSTALL_LIBDIR}/cmake/Td"   # -> TdConfig.cmake liegt hier

# Ausgangslage herstellen
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/libs" "$JAVA_SRC_DIR" "$JAVA_CLASSES_DIR" "$LOG_DIR"

# Globales Logging aktivieren (alles ins Log + Konsole)
# shellcheck disable=SC2094
exec > >(tee -a "$LOG_FILE") 2>&1

# NDK prüfen (erst nach Logging, damit Meldungen ins Log gehen)
[[ -n "$NDK" && -d "$NDK" ]] || { echo "NDK not found at: ${NDK:-<empty>}"; exit 1; }
PREBUILT_DIR="${NDK}/toolchains/llvm/prebuilt/${HOST_OS}-${HOST_ARCH}"
[[ -d "$PREBUILT_DIR" ]] || { echo "NDK prebuilt toolchain not found at $PREBUILT_DIR"; exit 1; }

# BoringSSL Präsenz (für Android-Builds) prüfen
IFS=',' read -ra ABI_ARR <<< "$ABIS"
for abi in "${ABI_ARR[@]}"; do
  abi="$(echo "$abi" | xargs)"
  [[ -d "$BORINGSSL_DIR/$abi/include" ]] || record_err "BoringSSL include missing for $abi"
  [[ -f "$BORINGSSL_DIR/$abi/lib/libcrypto.a" && -f "$BORINGSSL_DIR/$abi/lib/libssl.a" ]] || record_err "BoringSSL libs missing for $abi"
done

# CCache (lokal) einrichten – maximiert Reuse auch bei abgebrochenen Läufen
if command -v ccache >/dev/null 2>&1; then
  export CCACHE_DIR="${CCACHE_DIR:-$ROOT/.ccache}"
  export CCACHE_BASEDIR="${CCACHE_BASEDIR:-$ROOT}"
  export CCACHE_MAXSIZE="${CCACHE_MAXSIZE:-2G}"
  echo "[ccache] dir=$CCACHE_DIR basedir=$CCACHE_BASEDIR max=$CCACHE_MAXSIZE"
  CC_LAUNCH=( -DCMAKE_C_COMPILER_LAUNCHER=ccache -DCMAKE_CXX_COMPILER_LAUNCHER=ccache )
else
  CC_LAUNCH=()
  echo "[ccache] not found (ok)"
fi

# Übersicht
cat <<EOF
== TDLib build: JNI + Java bindings (SDK-free, soft-fail) ==
NDK       : $NDK
STL       : $ANDROID_STL
API-Level : android-$API_LEVEL
ABIs      : $ABIS
BoringSSL : $BORINGSSL_DIR
TD_DIR    : $TD_DIR
OUT_DIR   : $OUT_DIR
LOG_FILE  : $LOG_FILE
EOF

# Wechsle in TD-Quelle
if [[ ! -d "$TD_DIR" ]]; then echo "TD source dir not found: $TD_DIR"; exit 1; fi
pushd "$TD_DIR" >/dev/null

# Optionaler Ref-Wechsel
if [[ -n "$TD_REF" ]]; then
  run "git fetch $TD_REF" git fetch --depth=1 origin "$TD_REF"
  run "git checkout FETCH_HEAD" git checkout -qf FETCH_HEAD
fi
COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
DESCRIBE="$(git describe --tags --always --long --dirty=+ 2>/dev/null || echo "$COMMIT")"
echo "$DESCRIBE" > "$OUT_DIR/TDLIB_VERSION.txt"

# (1) Host-Build & Install (ohne TD_GENERATE_SOURCE_FILES)
JAVA_TD_INSTALL_PREFIX="$TD_DIR/example/java/td"
run "host-configure" cmake -S . -B build-host -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX:PATH="$JAVA_TD_INSTALL_PREFIX" \
  -DCMAKE_INSTALL_LIBDIR="$INSTALL_LIBDIR" \
  -DCMAKE_INSTALL_BINDIR="$INSTALL_BINDIR" \
  -DCMAKE_INSTALL_INCLUDEDIR="$INSTALL_INCLUDEDIR" \
  -DTD_ENABLE_TESTS=OFF \
  "${CC_LAUNCH[@]}"
run "host-build"   cmake --build build-host "${NINJA_KEEP[@]}"
run "host-install" cmake --build build-host --target install "${NINJA_KEEP[@]}"

TD_CMAKE_PACKAGE="$JAVA_TD_INSTALL_PREFIX/$CMAKEDIR_REL/TdConfig.cmake"
[[ -f "$TD_CMAKE_PACKAGE" ]] || record_err "Missing $TD_CMAKE_PACKAGE"

# (1b) Generator & Schemata (TD_GENERATE_SOURCE_FILES=ON)
run "gen-configure" cmake -S . -B build-gen -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DTD_ENABLE_JNI=OFF -DTD_ENABLE_TESTS=OFF -DTD_GENERATE_SOURCE_FILES=ON \
  "${CC_LAUNCH[@]}"
run "gen-prepare_cross_compiling" cmake --build build-gen --target prepare_cross_compiling "${NINJA_KEEP[@]}"
run "gen-td_generate_java_api"   cmake --build build-gen --target td_generate_java_api "${NINJA_KEEP[@]}"

# Artefakte aus build-gen platzieren
GEN_SRC="$(find build-gen -type f -path '*/td/generate/td_generate_java_api' -o -name 'td_generate_java_api' | head -n1 || true)"
if [[ -f "$GEN_SRC" ]]; then
  GEN_DST_DIR="$JAVA_TD_INSTALL_PREFIX/$INSTALL_BINDIR"
  run "copy-generator" bash -c "mkdir -p '$GEN_DST_DIR' && cp -f '$GEN_SRC' '$GEN_DST_DIR/td_generate_java_api' && chmod +x '$GEN_DST_DIR/td_generate_java_api'"
else
  record_err "Could not build td_generate_java_api"
fi

SCHEME_SRC_TL="$(find build-gen -type f -path '*/td/generate/scheme/td_api.tl'  | head -n1 || true)"
SCHEME_SRC_TLO="$(find build-gen -type f -path '*/td/generate/scheme/td_api.tlo' | head -n1 || true)"
SCHEME_DST_DIR="$JAVA_TD_INSTALL_PREFIX/$INSTALL_BINDIR/td/generate/scheme"
if [[ -f "$SCHEME_SRC_TL" && -f "$SCHEME_SRC_TLO" ]]; then
  run "copy-schemes" bash -c "mkdir -p '$SCHEME_DST_DIR' && cp -f '$SCHEME_SRC_TL' '$SCHEME_DST_DIR/td_api.tl' && cp -f '$SCHEME_SRC_TLO' '$SCHEME_DST_DIR/td_api.tlo'"
else
  [[ -f "$SCHEME_SRC_TL"  ]] || record_err "Missing generated td_api.tl"
  [[ -f "$SCHEME_SRC_TLO" ]] || record_err "Missing generated td_api.tlo"
fi

GEN_HELPER_DST="$JAVA_TD_INSTALL_PREFIX/$INSTALL_BINDIR/td/generate"
run "copy-php-helpers" bash -c '
  mkdir -p "'$GEN_HELPER_DST'"
  ok=0
  for f in JavadocTlDocumentationGenerator.php TlDocumentationGenerator.php; do
    if [[ -f td/generate/$f ]]; then cp -f td/generate/$f "'$GEN_HELPER_DST'/" && ok=1; fi
  done
  if [[ $ok -eq 0 ]]; then
    # Fallback-Suche
    for f in JavadocTlDocumentationGenerator.php TlDocumentationGenerator.php; do
      SRC=$(find td -type f -name "$f" | head -n1 || true)
      if [[ -f "$SRC" ]]; then cp -f "$SRC" "'$GEN_HELPER_DST'/" && ok=1; fi
    done
  fi
  [[ $ok -eq 1 ]]
'

# Vorab-Checks (report-only)
for must in \
  "$JAVA_TD_INSTALL_PREFIX/$CMAKEDIR_REL/TdConfig.cmake" \
  "$JAVA_TD_INSTALL_PREFIX/$INSTALL_BINDIR/td_generate_java_api" \
  "$JAVA_TD_INSTALL_PREFIX/$INSTALL_BINDIR/td/generate/scheme/td_api.tl" \
  "$JAVA_TD_INSTALL_PREFIX/$INSTALL_BINDIR/td/generate/scheme/td_api.tlo"; do
  [[ -e "$must" ]] || record_err "missing $must"
 done
ls -l "$JAVA_TD_INSTALL_PREFIX/$INSTALL_BINDIR" || true
ls -l "$JAVA_TD_INSTALL_PREFIX/$INSTALL_BINDIR/td/generate" || true
ls -l "$JAVA_TD_INSTALL_PREFIX/$INSTALL_BINDIR/td/generate/scheme" || true

# (2) example/java: erzeugt TdApi.java, Client.java, Log.java
run "java-configure" cmake -S example/java -B example/java/build -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="$JAVA_TD_INSTALL_PREFIX" \
  -DTd_DIR="$JAVA_TD_INSTALL_PREFIX/$CMAKEDIR_REL" \
  -DCMAKE_INSTALL_PREFIX:PATH="$TD_DIR/example/java" \
  "${CC_LAUNCH[@]}"
run "java-install" cmake --build example/java/build --target install "${NINJA_KEEP[@]}"

# (3) Java-Sources einsammeln
TDAPI_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/TdApi.java' | head -n1 || true)"
CLIENT_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/Client.java' | head -n1 || true)"
LOG_SRC="$(find example/java -type f -path '*/org/drinkless/tdlib/Log.java' | head -n1 || true)"
[[ -f "$TDAPI_SRC"  ]] || record_err "Could not locate generated TdApi.java"
[[ -f "$CLIENT_SRC" ]] || record_err "Missing Client.java"
[[ -f "$LOG_SRC"    ]] || record_err "Missing Log.java"

run "copy-java-sources" bash -c "mkdir -p '$JAVA_SRC_DIR/org/drinkless/tdlib' && \
  cp -f '$TDAPI_SRC'  '$JAVA_SRC_DIR/org/drinkless/tdlib/TdApi.java' 2>/dev/null || true; \
  cp -f '$CLIENT_SRC' '$JAVA_SRC_DIR/org/drinkless/tdlib/Client.java' 2>/dev/null || true; \
  cp -f '$LOG_SRC'    '$JAVA_SRC_DIR/org/drinkless/tdlib/Log.java'    2>/dev/null || true"

# (4) JNI je ABI (Android) — tdjni
for ABI in "${ABI_ARR[@]}"; do
  ABI="$(echo "$ABI" | xargs)"
  BUILD_DIR="build-${ABI}-jni"
  run "jni-$ABI-configure" cmake -S . -B "$BUILD_DIR" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DCMAKE_BUILD_TYPE=RelWithDebInfo \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DANDROID_ABI="$ABI" -DANDROID_STL="$ANDROID_STL" -DANDROID_PLATFORM="android-$API_LEVEL" \
    -DTD_ENABLE_JNI=ON -DOPENSSL_USE_STATIC_LIBS=ON \
    -DOPENSSL_INCLUDE_DIR="$BORINGSSL_DIR/$ABI/include" \
    -DOPENSSL_CRYPTO_LIBRARY="$BORINGSSL_DIR/$ABI/lib/libcrypto.a" \
    -DOPENSSL_SSL_LIBRARY="$BORINGSSL_DIR/$ABI/lib/libssl.a" \
    "${CC_LAUNCH[@]}"
  run "jni-$ABI-build-tdjni" cmake --build "$BUILD_DIR" --target tdjni "${NINJA_KEEP[@]}"

  soPath="$BUILD_DIR/libtdjni.so"
  if [[ ! -f "$soPath" ]]; then soPath="$(find "$BUILD_DIR" -maxdepth 3 -name 'libtdjni.so' | head -n1 || true)"; fi
  if [[ -f "$soPath" ]]; then
    run "jni-$ABI-copy-so" bash -c "mkdir -p '$OUT_DIR/libs/$ABI' && cp -f '$soPath' '$OUT_DIR/libs/$ABI/' && \
      '$PREBUILT_DIR/bin/llvm-strip' --strip-unneeded '$OUT_DIR/libs/$ABI/libtdjni.so' || true"
    if [[ "$ANDROID_STL" == "c++_shared" ]]; then
      case "$ABI" in
        arm64-v8a)  TRIPLE="aarch64-linux-android" ;;
        armeabi-v7a) TRIPLE="arm-linux-androideabi" ;;
      esac
      SHARED_STL="$PREBUILT_DIR/sysroot/usr/lib/$TRIPLE/libc++_shared.so"
      [[ -f "$SHARED_STL" ]] && run "jni-$ABI-copy-stl" cp -f "$SHARED_STL" "$OUT_DIR/libs/$ABI/"
    fi
  else
    record_err "libtdjni.so not found for $ABI"
  fi
done

popd >/dev/null

# (5) Java → JAR
run "java-jar-sources-list" bash -c "find '$JAVA_SRC_DIR' -name '*.java' > '$OUT_DIR/java-sources.list' || true"
run "java-javac" javac --release 8 -d "$JAVA_CLASSES_DIR" @"$OUT_DIR/java-sources.list"
DESC="local"; if [[ -d "$TD_DIR/.git" ]]; then DESC="$(git -C "$TD_DIR" describe --tags --always --long --dirty=+ 2>/dev/null || git -C "$TD_DIR" rev-parse --short HEAD)"; fi
JAR_PATH="$OUT_DIR/tdlib-${DESC}.jar"
run "java-jar-pack" jar cf "$JAR_PATH" -C "$JAVA_CLASSES_DIR" .

# --- Zusammenfassung ----------------------------------------------------------
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

# JSON-Report
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

# Abschließende Artefaktliste (für Upload im Workflow)
cat <<EOF
== Artifacts ==
$OUT_DIR/TDLIB_VERSION.txt
$OUT_DIR/libs/*/libtdjni.so
$OUT_DIR/tdlib-*.jar
$OUT_DIR/logs/build.log
$OUT_DIR/logs/summary.txt
$OUT_DIR/logs/summary.json
EOF

# Exitcode je nach Wunsch
if ((ERR_COUNT)) && [[ "$FINAL_EXIT" != "0" ]]; then
  exit 1
else
  exit 0
fi
