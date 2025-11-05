#!/usr/bin/env bash
set -Eeuo pipefail

# ============ Styling ============
# Robust gegen fehlendes Terminal (CI): tput nur verwenden, wenn vorhanden & funktionsfähig
if command -v tput >/dev/null 2>&1 && [ -n "${TERM-}" ] && tput colors >/dev/null 2>&1; then
  _c_red=$(tput setaf 1); _c_grn=$(tput setaf 2); _c_yel=$(tput setaf 3)
  _c_blu=$(tput setaf 4); _c_mag=$(tput setaf 5); _c_cyn=$(tput setaf 6)
  _c_bold=$(tput bold); _c_rst=$(tput sgr0)
else
  _c_red=""; _c_grn=""; _c_yel=""; _c_blu=""; _c_mag=""; _c_cyn=""; _c_bold=""; _c_rst=""
  # Stub, damit spätere tput-Aufrufe sicher sind
  tput() { :; }
fi
log() { printf "%s[%s]%s %s\n" "$_c_blu" "${1}" "$_c_rst" "${2}"; }
ok()  { printf "%s[%s]%s %s\n" "$_c_grn" "OK" "$_c_rst" "${1}"; }
warn(){ printf "%s[%s]%s %s\n" "$_c_yel" "WARN" "$_c_rst" "${1}"; }
err() { printf "%s[%s]%s %s\n" "$_c_red" "ERR" "$_c_rst" "${1}" >&2; }

trap 'err "Abgebrochen (Zeile $LINENO). DEBUG=1 für set -x."' ERR
[ "${DEBUG:-0}" = "1" ] && set -x

# ============ Defaults ============
CHANNEL="${CHANNEL:-stable}"                 # stable|edge
TD_REF="${TD_REF:-}"                        # expliziter Tag/Commit/Branch > überschreibt CHANNEL
API_LEVEL="${API_LEVEL:-24}"                # min. 21 für 64-bit (wird unten saniert)
ABIS="${ABIS:-arm64-v8a}"                   # z.B. "arm64-v8a,armeabi-v7a"
BUILD_TYPE="${BUILD_TYPE:-MinSizeRel}"      # MinSizeRel|Release
TD_SSL="${TD_SSL:-boringssl}"               # boringssl|openssl
OUT_DIR="${OUT_DIR:-$PWD/out}"
JOBS="${JOBS:-$( (command -v nproc >/dev/null && nproc) || sysctl -n hw.ncpu 2>/dev/null || echo 4 )}"
USE_CCACHE="${USE_CCACHE:-auto}"            # auto|ccache|sccache|off
NO_STRIP="${NO_STRIP:-0}"                   # 1 = Symbole behalten
BORINGSSL_REF="${BORINGSSL_REF:-}"          # optionaler Commit/Ref
NDK_MIN="${NDK_MIN:-26}"                    # Info/Warnung; nicht strikt
PLAN="${PLAN:-0}"                           # 1 = Dry-Run

# Reproducible builds (kann mit Commitzeit überschrieben werden)
SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-$(date -u +%s)}"

# ============ CLI ============
usage() {
cat <<EOF
${_c_bold}TDLib Android Build Script${_c_rst}

Nutzung:
  $(basename "$0") [Optionen]

Wichtige Optionen:
  --channel stable|edge     Standard: ${CHANNEL}
  --ref <tag|commit|branch> Fixe TDLib-Quelle (überschreibt --channel)
  --api-level <N>           Android API-Level (default ${API_LEVEL})
  --abis <list>             Komma-getrennt, z.B. arm64-v8a,armeabi-v7a (default ${ABIS})
  --build-type <cfg>        MinSizeRel|Release (default ${BUILD_TYPE})
  --ssl <boringssl|openssl> TLS/Krypto-Basis (default ${TD_SSL})
  --out <path>              Output-Basispfad (default ${OUT_DIR})
  --jobs <N>                Parallel-Jobs (default ${JOBS})
  --no-ccache               Deaktiviert ccache/sccache
  --use-ccache              Erzwingt ccache
  --use-sccache             Erzwingt sccache
  --plan                    Dry-Run (nur Schritte zeigen)
  --clean                   Build-Ordner bereinigen
  --deep-clean              Alles (inkl. Quellen/third_party) bereinigen
  --jar                     Zusätzlich tdlib-sources.jar erzeugen
  --help                    Diese Hilfe
EOF
}

CLEAN=0; DEEPCLEAN=0; MAKE_JAR=0; EDGE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --help|-h) usage; exit 0 ;;
    --channel) CHANNEL="$2"; shift;;
    --edge) CHANNEL="edge"; EDGE=1;;
    --ref) TD_REF="$2"; shift;;
    --api-level) API_LEVEL="$2"; shift;;
    --abis) ABIS="$2"; shift;;
    --build-type) BUILD_TYPE="$2"; shift;;
    --ssl) TD_SSL="$2"; shift;;
    --out) OUT_DIR="$2"; shift;;
    --jobs) JOBS="$1"; shift;;
    --no-ccache) USE_CCACHE="off";;
    --use-ccache) USE_CCACHE="ccache";;
    --use-sccache) USE_CCACHE="sccache";;
    --plan) PLAN=1;;
    --clean) CLEAN=1;;
    --deep-clean) DEEPCLEAN=1;;
    --jar) MAKE_JAR=1;;
    *) err "Unbekannte Option: $1"; usage; exit 2 ;;
  esac
  shift
done

# === Sanitize Inputs ===
# ABIS: Whitespace killen
ABIS="$(echo "$ABIS" | tr -d '[:space:]')"
# API_LEVEL: "24.0" -> "24", min 21
API_LEVEL_SAN="$(printf '%s' "$API_LEVEL" | sed -E 's/[^0-9].*$//' )"
[ -z "$API_LEVEL_SAN" ] && API_LEVEL_SAN=24
[ "$API_LEVEL_SAN" -lt 21 ] && API_LEVEL_SAN=21
API_LEVEL="$API_LEVEL_SAN"

# ============ Helpers ============
need() { command -v "$1" >/dev/null 2>&1 || { err "Benötigtes Tool fehlt: $1"; return 1; }; }
check_cmds() {
  log "CHECK" "Host-Abhängigkeiten prüfen…"
  need git; need cmake; need ninja || need ninja-build; need gperf; need php; need python3; need java; need javac; need jar; need unzip; need zip
  ok "Host-Tools vorhanden."
}
sha256() { command -v sha256sum >/dev/null 2>&1 && sha256sum "$1" | awk '{print $1}' || shasum -a256 "$1" | awk '{print $1}'; }

ANDROID_NDK_ROOT_DET=""
find_ndk() {
  # 1) Respektiere definierte Variablen
  for var in ANDROID_NDK_ROOT ANDROID_NDK_HOME; do
    v="${!var:-}"
    [ -n "$v" ] && [ -f "$v/build/cmake/android.toolchain.cmake" ] && ANDROID_NDK_ROOT_DET="$v"
  done
  # 2) Fallback via ANDROID_SDK_ROOT
  if [ -z "$ANDROID_NDK_ROOT_DET" ] && [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT/ndk" ]; then
    ANDROID_NDK_ROOT_DET="$(ls -d "$ANDROID_SDK_ROOT/ndk/"* 2>/dev/null | tail -n1 || true)"
    [ -f "$ANDROID_NDK_ROOT_DET/build/cmake/android.toolchain.cmake" ] || ANDROID_NDK_ROOT_DET=""
  fi
  # 3) Fehlermeldung
  [ -n "$ANDROID_NDK_ROOT_DET" ] || { err "Android NDK nicht gefunden. Setze ANDROID_NDK_ROOT oder ANDROID_NDK_HOME bzw. ANDROID_SDK_ROOT/ndk/<ver>."; exit 1; }
  ok "NDK: $ANDROID_NDK_ROOT_DET"
}

resolve_td_ref() {
  if [ -n "$TD_REF" ]; then echo "$TD_REF"; return 0; fi
  if [ "$CHANNEL" = "edge" ]; then echo "master"; return 0; fi

  # Stable = neuester Tag vX.Y.Z (ohne -rc/-beta)
  log "INFO" "Ermittle neuesten stabilen TDLib-Tag…"
  local latest_tag
  latest_tag="$(git ls-remote --tags --refs https://github.com/tdlib/td.git 'v[0-9]*' | awk '{print $2}' \
     | sed -E 's#refs/tags/##' | grep -Ev -- '-(rc|beta|alpha)' | sort -V | tail -n1 || true)"
  if [ -n "$latest_tag" ]; then
    ok "Neuster stabiler Tag: ${latest_tag}"
    echo "$latest_tag"
  else
    warn "Keine Tags gefunden – falle auf HEAD zurück."
    echo "master"
  fi
}

# Compiler-Launcher konfigurieren
configure_cache_env() {
  case "$USE_CCACHE" in
    off) export CCACHE_DISABLE=1; warn "Compiler-Cache deaktiviert." ;;
    ccache)
      if command -v ccache >/dev/null 2>&1; then
        export CCACHE_DIR="${CCACHE_DIR:-$PWD/.cache/ccache}"
        mkdir -p "$CCACHE_DIR"
        export CMAKE_C_COMPILER_LAUNCHER=ccache
        export CMAKE_CXX_COMPILER_LAUNCHER=ccache
        ok "ccache aktiviert ($CCACHE_DIR)."
      else warn "ccache nicht gefunden – ohne Cache."; fi ;;
    sccache)
      if command -v sccache >/dev/null 2>&1; then
        export SCCACHE_DIR="${SCCACHE_DIR:-$PWD/.cache/sccache}"
        mkdir -p "$SCCACHE_DIR"
        export CMAKE_C_COMPILER_LAUNCHER=sccache
        export CMAKE_CXX_COMPILER_LAUNCHER=sccache
        ok "sccache aktiviert ($SCCACHE_DIR)."
      else warn "sccache nicht gefunden – ohne Cache."; fi ;;
    auto|*)
      if command -v sccache >/dev/null 2>&1; then
        USE_CCACHE="sccache"; configure_cache_env
      elif command -v ccache >/dev/null 2>&1; then
        USE_CCACHE="ccache"; configure_cache_env
      else warn "Kein Compiler-Cache verfügbar."; fi ;;
  esac
}

# Flags für deterministische Builds
common_flags() {
  local pfx_map="-ffile-prefix-map=${PWD}=./src -fdebug-prefix-map=${PWD}=./src"
  local size_opt=""
  if [ "$BUILD_TYPE" = "MinSizeRel" ]; then size_opt="-Oz"; else size_opt="-O3"; fi
  echo "-DNDEBUG $size_opt -fno-plt -fvisibility=hidden ${pfx_map}"
}

# ============ Start ============
log "CONFIG" "Kanal=${CHANNEL} TD_REF=${TD_REF:-<auto>} API=${API_LEVEL} ABIS=${ABIS} BUILD_TYPE=${BUILD_TYPE} SSL=${TD_SSL}"
log "CONFIG" "OUT_DIR=${OUT_DIR} JOBS=${JOBS} Cache=${USE_CCACHE}"

# Clean
if [ "$DEEPCLEAN" = "1" ]; then
  log "CLEAN" "Deep clean…"; rm -rf "$OUT_DIR" .work .third_party td boringssl
  ok "Deep clean fertig."; exit 0
elif [ "$CLEAN" = "1" ]; then
  log "CLEAN" "Clean…"; rm -rf "$OUT_DIR" .work
  ok "Clean fertig."; exit 0
fi

mkdir -p "$OUT_DIR"/{android,java,meta} .work .third_party

check_cmds
find_ndk
configure_cache_env

TD_REF_RESOLVED="$(resolve_td_ref)"
log "INFO" "Verwendete TDLib-Ref: ${TD_REF_RESOLVED}"

# ============ Quellen holen ============
if [ ! -d td/.git ]; then
  log "GIT" "Clone tdlib/td…"
  git clone --depth 1 --branch "$TD_REF_RESOLVED" https://github.com/tdlib/td.git td
else
  log "GIT" "Fetch/Checkout…"
  (cd td && git fetch --tags --prune && git checkout -f "$TD_REF_RESOLVED" && git pull --ff-only || true)
fi

TD_DESCRIBE="$(cd td && (git describe --tags --always --dirty || git rev-parse --short HEAD))"
TD_HASH="$(cd td && git rev-parse HEAD || echo unknown)"
ok "TDLib @ ${TD_DESCRIBE} (${TD_HASH})"

# Quelle: Git-Zeit als SOURCE_DATE_EPOCH (sofern nicht manuell gesetzt)
if [ -z "${SOURCE_DATE_EPOCH:-}" ] || [ "${SOURCE_DATE_EPOCH}" = "" ]; then
  export SOURCE_DATE_EPOCH="$(cd td && git log -1 --pretty=%ct)"
fi
export SOURCE_DATE_EPOCH

# Optional: BoringSSL holen
if [ "$TD_SSL" = "boringssl" ]; then
  if [ ! -d boringssl/.git ]; then
    log "GIT" "Clone BoringSSL…"
    git clone https://boringssl.googlesource.com/boringssl boringssl
  fi
  if [ -n "$BORINGSSL_REF" ]; then
    (cd boringssl && git fetch && git checkout -f "$BORINGSSL_REF")
    ok "BoringSSL @ $(cd boringssl && git rev-parse --short HEAD)"
  fi
fi

# ============ Dry-Run ============
if [ "$PLAN" = "1" ]; then
  log "PLAN" "Würde TDLib(${TD_DESCRIBE}) + ${TD_SSL} für {${ABIS}} bauen; Output in ${OUT_DIR}."
  exit 0
fi

# ============ Host-Stage: Java-API generieren ============
log "HOST" "Java-API generieren (td_generate_java_api)…"
HOST_BLD=.work/host
mkdir -p "$HOST_BLD"
pushd "$HOST_BLD" >/dev/null
  cmake -G Ninja ../..//td \
    -DCMAKE_BUILD_TYPE=Release \
    -DTD_ENABLE_JNI=ON \
    -DCMAKE_INSTALL_PREFIX:PATH="$PWD/install" \
    -DCMAKE_C_FLAGS="$(common_flags)" \
    -DCMAKE_CXX_FLAGS="$(common_flags)"
  cmake --build . --target install -- -j"$JOBS"
popd >/dev/null

# Java-Dateien einsammeln
JAVA_SRC_DIR="$HOST_BLD/install/share/td/example/java/org/drinkless/tdlib"
if [ ! -f "$JAVA_SRC_DIR/TdApi.java" ]; then
  # Fallback: Einige Versionen legen direkt in example/java ab
  JAVA_SRC_DIR="td/example/java/org/drinkless/tdlib"
fi
[ -f "$JAVA_SRC_DIR/TdApi.java" ] || { err "TdApi.java nicht gefunden – Generator fehlgeschlagen."; exit 2; }

mkdir -p "$OUT_DIR/java/org/drinkless/tdlib"
rsync -a "$JAVA_SRC_DIR/" "$OUT_DIR/java/org/drinkless/tdlib/"

# Optional: JAR (Sources) erzeugen
if [ "$MAKE_JAR" = "1" ]; then
  pushd "$OUT_DIR/java" >/dev/null
    zip -rq tdlib-sources.jar org
  popd >/dev/null
  ok "Sources-JAR erzeugt: $OUT_DIR/java/tdlib-sources.jar"
fi

# ============ TLS: BoringSSL für jede ABI bauen ============
build_boringssl() {
  local abi="$1"
  local bdir=".work/boringssl-${abi}"
  local inst=".work/boringssl-install-${abi}"
  mkdir -p "$bdir" "$inst"
  cmake -G Ninja boringssl -B "$bdir" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT_DET/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="android-${API_LEVEL}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=OFF \
    -DCMAKE_INSTALL_PREFIX="$inst" \
    -DCMAKE_C_FLAGS="$(common_flags) -fPIC" \
    -DCMAKE_CXX_FLAGS="$(common_flags) -fPIC"
  cmake --build "$bdir" -- -j"$JOBS"
  mkdir -p "$inst/include" "$inst/lib"
  rsync -a boringssl/include/ "$inst/include/"
  cp "$bdir/crypto/libcrypto.a" "$inst/lib/" || true
  cp "$bdir/ssl/libssl.a" "$inst/lib/" || true
  [ -f "$inst/lib/libcrypto.a" ] || { err "BoringSSL Build für ${abi} fehlgeschlagen."; exit 3; }
  ok "BoringSSL ${abi} bereit."
}

if [ "$TD_SSL" = "boringssl" ]; then
  IFS=',' read -r -a _abis <<<"$ABIS"
  for abi in "${_abis[@]}"; do
    log "SSL" "BoringSSL für ${abi}…"; build_boringssl "$abi"
  done
fi

# ============ TDLib JNI für jede ABI bauen ============
build_td_android() {
  local abi="$1"
  local bdir=".work/td-android-${abi}"
  local ssl_inst=".work/boringssl-install-${abi}"
  mkdir -p "$bdir"

  local openssl_args=()
  if [ "$TD_SSL" = "boringssl" ]; then
    openssl_args+=(
      "-DOPENSSL_USE_STATIC_LIBS=ON"
      "-DOPENSSL_INCLUDE_DIR=${ssl_inst}/include"
      "-DOPENSSL_CRYPTO_LIBRARY=${ssl_inst}/lib/libcrypto.a"
      "-DOPENSSL_SSL_LIBRARY=${ssl_inst}/lib/libssl.a"
    )
  fi

  cmake -G Ninja td -B "$bdir" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT_DET/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="android-${API_LEVEL}" \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
    -DTD_ENABLE_JNI=ON \
    -DTD_ENABLE_LTO=OFF \
    -DCMAKE_C_FLAGS="$(common_flags)" \
    -DCMAKE_CXX_FLAGS="$(common_flags)" \
    "${openssl_args[@]}"

  cmake --build "$bdir" -- -j"$JOBS"

  # Output lokalisieren (libtdjni.so)
  local so_path
  so_path="$(find "$bdir" -name 'libtdjni.so' -type f | head -n1 || true)"
  [ -f "$so_path" ] || { err "libtdjni.so nicht gefunden für ${abi}."; exit 4; }

  # Strip wenn erlaubt
  if [ "$NO_STRIP" != "1" ]; then
    "$ANDROID_NDK_ROOT_DET/toolchains/llvm/prebuilt"/*/bin/llvm-strip "$so_path" || true
  fi

  mkdir -p "$OUT_DIR/android/${abi}"
  cp "$so_path" "$OUT_DIR/android/${abi}/libtdjni.so"
  ok "JNI ${abi}: $(basename "$so_path") → $OUT_DIR/android/${abi}/libtdjni.so"

  # Checks
  if command -v file >/dev/null 2>&1; then file "$OUT_DIR/android/${abi}/libtdjni.so" || true; fi
  if command -v nm >/dev/null 2>&1; then
    if ! nm -D "$OUT_DIR/android/${abi}/libtdjni.so" | grep -q 'Java_org_drinkless_tdlib_Client_'; then
      warn "JNI-Symbole nicht gefunden? Prüfe Build/Flags."
    fi
  fi
}

IFS=',' read -r -a _abis <<<"$ABIS"
for abi in "${_abis[@]}"; do
  log "BUILD" "TDLib JNI für ${abi}…"
  build_td_android "$abi"
done

# ============ Metadaten & Prüfsummen ============
log "META" "Artefakte und Metadaten schreiben…"
{
  echo "tdlib_ref=${TD_REF_RESOLVED}"
  echo "tdlib_describe=${TD_DESCRIBE}"
  echo "tdlib_commit=${TD_HASH}"
  echo "build_time_utc=$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo "api_level=${API_LEVEL}"
  echo "abis=${ABIS}"
  echo "build_type=${BUILD_TYPE}"
  echo "ssl=${TD_SSL}"
  echo "ndk=${ANDROID_NDK_ROOT_DET##*/}"
  echo "use_cache=${USE_CCACHE}"
  echo "source_date_epoch=${SOURCE_DATE_EPOCH}"
} >"$OUT_DIR/meta/.tdlib_meta"

echo "${TD_DESCRIBE} (${TD_HASH})" >"$OUT_DIR/meta/TDLIB_VERSION.txt"

# Checksums
{
  for abi in "${_abis[@]}"; do
    f="$OUT_DIR/android/${abi}/libtdjni.so"
    [ -f "$f" ] && echo "$(sha256 "$f")  android/${abi}/libtdjni.so"
  done
  if [ -f "$OUT_DIR/java/tdlib-sources.jar" ]; then
    echo "$(sha256 "$OUT_DIR/java/tdlib-sources.jar")  java/tdlib-sources.jar"
  fi
} >"$OUT_DIR/meta/checksums.sha256"

# Lizenzen bündeln (TDLib + BoringSSL + zlib)
{
  echo "TDLib: Boost Software License (siehe td/LICENSE_1_0.txt)"
  echo "BoringSSL: ISC/BSD-style (siehe boringssl/LICENSE)"
  echo "zlib: zlib-Lizenz"
} >"$OUT_DIR/meta/NOTICE.txt"

# ============ Summary ============
ok "Build erfolgreich."
log "OUT" "Bibliotheken:"
for abi in "${_abis[@]}"; do
  echo "  - $OUT_DIR/android/${abi}/libtdjni.so"
done
log "OUT" "Java-Sourcen: $OUT_DIR/java/org/drinkless/tdlib/ (optional Sources-JAR: $OUT_DIR/java/tdlib-sources.jar)"
log "OUT" "Metadaten: $OUT_DIR/meta/{.tdlib_meta,TDLIB_VERSION.txt,checksums.sha256,NOTICE.txt}"

# Maschinenlesbarer Mini-Report
cat > "$OUT_DIR/meta/report.json" <<JSON
{
  "td_ref": "$(printf %s "$TD_REF_RESOLVED")",
  "td_describe": "$(printf %s "$TD_DESCRIBE")",
  "td_commit": "$(printf %s "$TD_HASH")",
  "abis": "$(printf %s "$ABIS")",
  "api": ${API_LEVEL},
  "build_type": "$(printf %s "$BUILD_TYPE")",
  "ssl": "$(printf %s "$TD_SSL")",
  "out": "$(printf %s "$OUT_DIR")"
}
JSON
