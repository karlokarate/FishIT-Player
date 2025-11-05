#!/usr/bin/env bash
# -*- coding: utf-8 -*-
# TDLib Android Builder (WSL2/GitHub Actions/Local)
# - Defaults to building TDLib from 'master' if no ref (tag/branch/commit) is provided
# - Generates Java bindings (TdApi.java, Client.java, Log.java) via example/java -> target td_generate_java_api
# - Builds libtdjni.so for arm64-v8a and optionally armeabi-v7a
# - Copies outputs into ./libtd/src/main/jniLibs/<ABI>/ and ./libtd/src/main/java/org/drinkless/tdlib/
# - Writes ./libtd/TDLIB_VERSION.txt and ./libtd/.tdlib_meta (JSON) for caching/diagnostics

set -euo pipefail

# Better diagnostics on failure
trap 'code=$?; echo "ERROR: failed at ${BASH_SOURCE[0]}:${LINENO} (exit ${code})" >&2; exit "${code}"' ERR

#---------------------------
# Defaults / Configuration
#---------------------------
TD_REMOTE="${TD_REMOTE:-https://github.com/tdlib/td.git}"
TD_REF=""                         # set via --ref; empty => "master"
ANDROID_ABIS="arm64-v8a"          # comma separated; add armeabi-v7a if desired
ANDROID_API="${ANDROID_API:-24}"
BUILD_TYPE="${BUILD_TYPE:-MinSizeRel}"  # MinSizeRel or Release recommended
NDK_PATH="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
USE_NINJA="${USE_NINJA:-1}"
CLEAN="${CLEAN:-0}"

# Project-relative dirs
WORK_ROOT="$(pwd)"
THIRD_PARTY_DIR="${WORK_ROOT}/.third_party"
TD_DIR="${THIRD_PARTY_DIR}/td"
OUT_ROOT="${WORK_ROOT}/libtd"
JAVA_DST="${OUT_ROOT}/src/main/java/org/drinkless/tdlib"
JNI_ROOT="${OUT_ROOT}/src/main/jniLibs"
META_TXT="${OUT_ROOT}/TDLIB_VERSION.txt"
META_JSON="${OUT_ROOT}/.tdlib_meta"

#---------------------------
# Helpers
#---------------------------
msg() { echo -e "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] $*"; }
die() { echo "ERROR: $*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

# Parse args
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref)        TD_REF="${2:-}"; shift 2 ;;
    --abis)       ANDROID_ABIS="${2:-}"; shift 2 ;;
    --api-level)  ANDROID_API="${2:-}"; shift 2 ;;
    --build-type) BUILD_TYPE="${2:-}"; shift 2 ;;
    --ndk)        NDK_PATH="${2:-}"; shift 2 ;;
    --clean)      CLEAN=1; shift ;;
    --help|-h)
cat <<'USAGE'
Usage:
  scripts/build_tdlib_android.sh [options]

Options:
  --ref <tag|branch|commit>          # default: master
  --abis <comma list>                # e.g. arm64-v8a,armeabi-v7a
  --api-level <N>                    # default: 24
  --build-type <MinSizeRel|Release>  # default: MinSizeRel
  --ndk </path/to/ndk>
  --clean

Examples:
  bash scripts/build_tdlib_android.sh
  bash scripts/build_tdlib_android.sh --ref v1.8.56
  bash scripts/build_tdlib_android.sh --abis arm64-v8a,armeabi-v7a --api-level 36 --build-type Release
USAGE
      exit 0 ;;
    *) die "Unknown argument: $1" ;;
  esac
done

# Default ref -> master (explicit)
if [[ -z "${TD_REF}" ]]; then
  TD_REF="master"
fi

# Check NDK
if [[ -z "${NDK_PATH}" ]]; then
  # Try common GitHub Actions path
  if [[ -d "${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}/ndk" ]]; then
    NDK_PATH="$(ls -d ${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}/ndk/* 2>/dev/null | sort -V | tail -n1 || true)"
  fi
fi
[[ -d "${NDK_PATH:-}" ]] || die "Android NDK not found. Set ANDROID_NDK_HOME/ANDROID_NDK_ROOT or pass --ndk."

# Toolchain file
TOOLCHAIN_FILE="${NDK_PATH}/build/cmake/android.toolchain.cmake"
[[ -f "${TOOLCHAIN_FILE}" ]] || die "Toolchain file not found at ${TOOLCHAIN_FILE}"

# Try to install host deps if possible (Ubuntu/GitHub runner)
if have sudo && have apt-get; then
  msg "::group::Installing host dependencies (if missing)"
  sudo apt-get update -y || true
  sudo apt-get install -y --no-install-recommends     git cmake ninja-build gperf pkg-config python3 python3-distutils     zlib1g-dev libssl-dev ca-certificates unzip >/dev/null || true
  msg "::endgroup::"
fi

have git     || die "git is required"
have cmake   || die "cmake is required"
have gperf   || die "gperf (host) is required"
have python3 || die "python3 is required"

# Prefer Ninja generator if requested
CMAKE_GENERATOR=()
if [[ "${USE_NINJA}" == "1" ]] && have ninja; then
  CMAKE_GENERATOR+=("-G" "Ninja")
fi

# Enable ccache if present
if have ccache; then
  export CC="ccache clang"
  export CXX="ccache clang++"
  export CMAKE_C_COMPILER_LAUNCHER=ccache
  export CMAKE_CXX_COMPILER_LAUNCHER=ccache
  msg "::notice::ccache enabled"
fi

# Ensure output dirs
mkdir -p "${THIRD_PARTY_DIR}" "${JNI_ROOT}" "${JAVA_DST}"

# Clone / update TDLib
if [[ ! -d "${TD_DIR}/.git" ]]; then
  msg "Cloning TDLib: ${TD_REMOTE} -> ${TD_DIR}"
  git clone --filter=blob:none --recurse-submodules "${TD_REMOTE}" "${TD_DIR}"
else
  msg "Updating TDLib repo"
  (cd "${TD_DIR}" && git fetch --tags --prune)
fi

pushd "${TD_DIR}" >/dev/null
  msg "Checking out ref: ${TD_REF}"
  git checkout -f "${TD_REF}"
  git submodule update --init --recursive
  TD_COMMIT="$(git rev-parse HEAD)"
  TD_DESCRIBE="$(git describe --always --long --tags || echo unknown)"
popd >/dev/null

msg "Build configuration:"
echo "  Remote   : ${TD_REMOTE}"
echo "  REF      : ${TD_REF}"
echo "  Commit   : ${TD_COMMIT}"
echo "  Describe : ${TD_DESCRIBE}"
echo "  NDK      : ${NDK_PATH}"
echo "  API      : ${ANDROID_API}"
echo "  ABIs     : ${ANDROID_ABIS}"
echo "  Type     : ${BUILD_TYPE}"

# Optional cleanup
if [[ "${CLEAN}" == "1" ]]; then
  msg "Cleaning previous TDLib builds"
  rm -rf "${TD_DIR}/build-host" "${TD_DIR}/build-java-gen" "${TD_DIR}/build-android"* || true
fi

#------------------------------------------------------------------
# 1) Generate Java API (td_generate_java_api) in example/java
#------------------------------------------------------------------
msg "::group::Generating Java API (td_generate_java_api)"
JAVA_GEN_BUILD_DIR="${TD_DIR}/build-java-gen"
mkdir -p "${JAVA_GEN_BUILD_DIR}"
pushd "${JAVA_GEN_BUILD_DIR}" >/dev/null
  cmake "${CMAKE_GENERATOR[@]}" -DCMAKE_BUILD_TYPE=Release -DTD_ENABLE_JNI=ON "${TD_DIR}/example/java"
  cmake --build . --target td_generate_java_api -j"$(getconf _NPROCESSORS_ONLN)"
popd >/dev/null

# Locate generated Java files
JAVA_SOURCE_BASE="${TD_DIR}/example/java/td/src/main/java/org/drinkless/tdlib"
if [[ -f "${JAVA_SOURCE_BASE}/TdApi.java" && -f "${JAVA_SOURCE_BASE}/Client.java" && -f "${JAVA_SOURCE_BASE}/Log.java" ]]; then
  msg "Copying generated Java bindings to ${JAVA_DST}"
  mkdir -p "${JAVA_DST}"
  cp -f "${JAVA_SOURCE_BASE}/TdApi.java" "${JAVA_SOURCE_BASE}/Client.java" "${JAVA_SOURCE_BASE}/Log.java" "${JAVA_DST}/"
else
  FOUND_TDAPI="$(grep -Rsl --include='TdApi.java' '^package org\.drinkless\.tdlib;' "${TD_DIR}/example/java" || true)"
  [[ -n "${FOUND_TDAPI}" ]] || die "Failed to locate generated TdApi.java. td_generate_java_api did not produce expected outputs."
  msg "Found TdApi.java at: ${FOUND_TDAPI} (fallback copy)"
  mkdir -p "${JAVA_DST}"
  cp -f "${FOUND_TDAPI}" "${JAVA_DST}/"
  CLIENT_CANDIDATE="$(dirname "${FOUND_TDAPI}")/Client.java"
  LOG_CANDIDATE="$(dirname "${FOUND_TDAPI}")/Log.java"
  [[ -f "${CLIENT_CANDIDATE}" ]] && cp -f "${CLIENT_CANDIDATE}" "${JAVA_DST}/" || true
  [[ -f "${LOG_CANDIDATE}"   ]] && cp -f "${LOG_CANDIDATE}"   "${JAVA_DST}/" || true
fi
msg "::endgroup::"

#------------------------------------------------------------------
# 2) Build libtdjni.so for requested ABIs
#------------------------------------------------------------------
IFS=',' read -r -a ABI_LIST <<< "${ANDROID_ABIS}"
for ABI in "${ABI_LIST[@]}"; do
  ABI_TRIM="$(echo "${ABI}" | xargs)"
  [[ -n "${ABI_TRIM}" ]] || continue
  BUILD_DIR="${TD_DIR}/build-android-${ABI_TRIM}"
  msg "::group::Building libtdjni.so for ${ABI_TRIM}"
  mkdir -p "${BUILD_DIR}"
  pushd "${BUILD_DIR}" >/dev/null
    cmake "${CMAKE_GENERATOR[@]}"       -DCMAKE_BUILD_TYPE="${BUILD_TYPE}"       -DCMAKE_INSTALL_PREFIX="${BUILD_DIR}/install"       -DCMAKE_TOOLCHAIN_FILE="${TOOLCHAIN_FILE}"       -DANDROID_ABI="${ABI_TRIM}"       -DANDROID_PLATFORM="android-${ANDROID_API}"       -DANDROID_STL=c++_shared       -DTD_ENABLE_JNI=ON       -DOPENSSL_USE_STATIC_LIBS=ON       "${TD_DIR}"
    cmake --build . -j"$(getconf _NPROCESSORS_ONLN)"
  popd >/dev/null

  JNI_LIB_SRC="$(find "${BUILD_DIR}" -name 'libtdjni.so' -type f | head -n1 || true)"
  [[ -f "${JNI_LIB_SRC}" ]] || die "libtdjni.so not found for ABI ${ABI_TRIM}"
  DEST_DIR="${JNI_ROOT}/${ABI_TRIM}"
  mkdir -p "${DEST_DIR}"
  cp -f "${JNI_LIB_SRC}" "${DEST_DIR}/libtdjni.so"
  msg "::notice::Copied ${ABI_TRIM}/libtdjni.so -> ${DEST_DIR}"
  msg "::endgroup::"
done

#------------------------------------------------------------------
# 3) Metadata
#------------------------------------------------------------------
mkdir -p "${OUT_ROOT}"
{
  echo "TDLib Remote : ${TD_REMOTE}"
  echo "TDLib Ref    : ${TD_REF}"
  echo "TDLib Commit : ${TD_COMMIT}"
  echo "TDLib Describe: ${TD_DESCRIBE}"
  echo "NDK          : ${NDK_PATH}"
  echo "API Level    : ${ANDROID_API}"
  echo "ABIs         : ${ANDROID_ABIS}"
  echo "Build Type   : ${BUILD_TYPE}"
  echo "Timestamp UTC: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "${META_TXT}"

cat > "${META_JSON}" <<EOF
{
  "remote": "${TD_REMOTE}",
  "ref": "${TD_REF}",
  "commit": "${TD_COMMIT}",
  "describe": "${TD_DESCRIBE}",
  "ndk": "${NDK_PATH}",
  "api_level": "${ANDROID_API}",
  "abis": "${ANDROID_ABIS}",
  "build_type": "${BUILD_TYPE}",
  "timestamp_utc": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

msg "Build completed successfully."
msg "Outputs:"
echo "  - Java: ${JAVA_DST}/(TdApi.java, Client.java, Log.java)"
echo "  - JNI : ${JNI_ROOT}/<ABI>/libtdjni.so"
echo "  - Meta: ${META_TXT}, ${META_JSON}"
