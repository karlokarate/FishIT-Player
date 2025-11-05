#!/usr/bin/env bash

# build TDLib for Android using BORINGSSL (static), leaving artifacts layout identical to example/android
# Usage (identisch zur Example-Logik – nur Param 3 = BoringSSL):
#   ./build-tdlib-boringssl.sh <ANDROID_SDK_ROOT|SDK> <NDK_VERSION|23.2.8568313> <BORINGSSL_INSTALL_DIR|third-party/boringssl> <c++_static|c++_shared> <Java|JSON|JSONJava>

ANDROID_SDK_ROOT=${1:-SDK}
ANDROID_NDK_VERSION=${2:-23.2.8568313}
BORINGSSL_INSTALL_DIR=${3:-third-party/boringssl}   # <— statt OPENSSL_INSTALL_DIR
ANDROID_STL=${4:-c++_static}
TDLIB_INTERFACE=${5:-Java}

if [ "$ANDROID_STL" != "c++_static" ] && [ "$ANDROID_STL" != "c++_shared" ] ; then
  echo 'Error: ANDROID_STL must be either "c++_static" or "c++_shared".'
  exit 1
fi

if [ "$TDLIB_INTERFACE" != "Java" ] && [ "$TDLIB_INTERFACE" != "JSON" ] && [ "$TDLIB_INTERFACE" != "JSONJava" ] ; then
  echo 'Error: TDLIB_INTERFACE must be either "Java", "JSON", or "JSONJava".'
  exit 1
fi

# host arch detection (wie im example)
OS_NAME="linux"
HOST_ARCH="linux-x86_64"
UNAME_S="$(uname -s 2>/dev/null || echo Linux)"
case "$UNAME_S" in
  Linux*)   OS_NAME="linux";  HOST_ARCH="linux-x86_64" ;;
  Darwin*)  OS_NAME="mac";    HOST_ARCH="darwin-x86_64" ;;
  CYGWIN*|MINGW*|MSYS*) OS_NAME="win"; HOST_ARCH="windows-x86_64" ;;
esac

source ./check-environment.sh || exit 1

if [ ! -d "$ANDROID_SDK_ROOT" ] ; then
  echo "Error: directory \"$ANDROID_SDK_ROOT\" doesn't exist. Run ./fetch-sdk.sh first, or provide a valid path to Android SDK."
  exit 1
fi

if [ ! -d "$BORINGSSL_INSTALL_DIR" ] ; then
  echo "Error: directory \"$BORINGSSL_INSTALL_DIR\" doesn't exists. Provide prebuilt BoringSSL (per ABI) or run your boringssl build step first."
  exit 1
fi

ANDROID_SDK_ROOT="$(cd "$(dirname -- "$ANDROID_SDK_ROOT")" >/dev/null; pwd -P)/$(basename -- "$ANDROID_SDK_ROOT")"
ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION"
BORINGSSL_INSTALL_DIR="$(cd "$(dirname -- "$BORINGSSL_INSTALL_DIR")" >/dev/null; pwd -P)/$(basename -- "$BORINGSSL_INSTALL_DIR")"
PATH=$ANDROID_SDK_ROOT/cmake/3.22.1/bin:$PATH
TDLIB_INTERFACE_OPTION=$([ "$TDLIB_INTERFACE" == "JSON" ] && echo "-DTD_ANDROID_JSON=ON" || [ "$TDLIB_INTERFACE" == "JSONJava" ] && echo "-DTD_ANDROID_JSON_JAVA=ON" || echo "")

cd $(dirname $0) || exit 1

echo "Generating TDLib source files..."
mkdir -p build-native-$TDLIB_INTERFACE || exit 1
cd build-native-$TDLIB_INTERFACE || exit 1
cmake $TDLIB_INTERFACE_OPTION -DTD_GENERATE_SOURCE_FILES=ON .. || exit 1
cmake --build . || exit 1
cd .. || exit 1

rm -rf tdlib || exit 1

if [ "$TDLIB_INTERFACE" == "Java" ] ; then
  echo "Downloading annotation Java package..."
  rm -f android.jar annotation-1.4.0.jar || exit 1
  $WGET https://maven.google.com/androidx/annotation/annotation/1.4.0/annotation-1.4.0.jar || exit 1

  echo "Generating Java source files..."
  cmake --build build-native-$TDLIB_INTERFACE --target tl_generate_java || exit 1
  php AddIntDef.php org/drinkless/tdlib/TdApi.java || exit 1
  mkdir -p tdlib/java/org/drinkless/tdlib || exit 1
  cp -p {..,tdlib}/java/org/drinkless/tdlib/Client.java || exit 1
  mv {,tdlib/java/}org/drinkless/tdlib/TdApi.java || exit 1
  rm -rf org || exit 1

  echo "Generating Javadoc documentation..."
  cp "$ANDROID_SDK_ROOT/platforms/android-34/android.jar" . || exit 1
  JAVADOC_SEPARATOR=$([ "$OS_NAME" == "win" ] && echo ";" || echo ":")
  javadoc -d tdlib/javadoc -encoding UTF-8 -charset UTF-8 -classpath "android.jar${JAVADOC_SEPARATOR}annotation-1.4.0.jar" -quiet -sourcepath tdlib/java org.drinkless.tdlib || exit 1
  rm android.jar annotation-1.4.0.jar || exit 1
fi
if [ "$TDLIB_INTERFACE" == "JSONJava" ] ; then
  mkdir -p tdlib/java/org/drinkless/tdlib || exit 1
  cp -p {..,tdlib}/java/org/drinkless/tdlib/JsonClient.java || exit 1
fi

echo "Building TDLib..."
for ABI in arm64-v8a armeabi-v7a x86_64 x86 ; do
  mkdir -p tdlib/libs/$ABI/ || exit 1

  # Android 64-bit benötigt min API 21
  if [[ "$ABI" == "arm64-v8a" || "$ABI" == "x86_64" ]]; then
    ANDROID_PLATFORM=android-21
  else
    ANDROID_PLATFORM=android-16
  fi

  mkdir -p build-$ABI-$TDLIB_INTERFACE || exit 1
  cd build-$ABI-$TDLIB_INTERFACE || exit 1

  # BoringSSL: statisch linken (include + libcrypto.a + libssl.a)
  cmake -GNinja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
    -DCMAKE_BUILD_TYPE=RelWithDebInfo \
    -DANDROID_ABI=$ABI \
    -DANDROID_STL=$ANDROID_STL \
    -DANDROID_PLATFORM=$ANDROID_PLATFORM \
    $TDLIB_INTERFACE_OPTION \
    -DOPENSSL_USE_STATIC_LIBS=ON \
    -DOPENSSL_INCLUDE_DIR="$BORINGSSL_INSTALL_DIR/$ABI/include" \
    -DOPENSSL_CRYPTO_LIBRARY="$BORINGSSL_INSTALL_DIR/$ABI/lib/libcrypto.a" \
    -DOPENSSL_SSL_LIBRARY="$BORINGSSL_INSTALL_DIR/$ABI/lib/libssl.a" \
    .. || exit 1

  if [ "$TDLIB_INTERFACE" == "Java" ] || [ "$TDLIB_INTERFACE" == "JSONJava" ] ; then
    cmake --build . --target tdjni || exit 1
    cp -p libtd*.so* ../tdlib/libs/$ABI/ || exit 1
  fi
  if [ "$TDLIB_INTERFACE" == "JSON" ] ; then
    cmake --build . --target tdjson || exit 1
    cp -p td/libtdjson.so ../tdlib/libs/$ABI/libtdjson.so.debug || exit 1
    "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$HOST_ARCH/bin/llvm-strip" --strip-debug --strip-unneeded ../tdlib/libs/$ABI/libtdjson.so.debug -o ../tdlib/libs/$ABI/libtdjson.so || exit 1
  fi
  cd .. || exit 1

  if [[ "$ANDROID_STL" == "c++_shared" ]] ; then
    if [[ "$ABI" == "arm64-v8a" ]] ; then
      FULL_ABI="aarch64-linux-android"
    elif [[ "$ABI" == "armeabi-v7a" ]] ; then
      FULL_ABI="arm-linux-androideabi"
    elif [[ "$ABI" == "x86_64" ]] ; then
      FULL_ABI="x86_64-linux-android"
    elif [[ "$ABI" == "x86" ]] ; then
      FULL_ABI="i686-linux-android"
    fi
    cp "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$HOST_ARCH/sysroot/usr/lib/$FULL_ABI/libc++_shared.so" tdlib/libs/$ABI/ || exit 1
    "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$HOST_ARCH/bin/llvm-strip" tdlib/libs/$ABI/libc++_shared.so || exit 1
  fi

  # OpenSSL .so Kopieren entfällt bei BoringSSL (statisch gelinkt)
done

echo "Compressing..."
rm -f tdlib.zip tdlib-debug.zip || exit 1
jar -cMf tdlib-debug.zip tdlib || exit 1
rm tdlib/libs/*/*.debug 2>/dev/null || true
jar -cMf tdlib.zip tdlib || exit 1
mv tdlib.zip tdlib-debug.zip tdlib || exit 1

echo "Done."