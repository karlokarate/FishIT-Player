#!/bin/bash
# FishIT-Player Development Environment Setup

set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

# Export environment variables
export ANDROID_SDK_ROOT="$REPO_ROOT/.wsl-android-sdk"
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/26.1.10909125"
export JAVA_HOME="/usr/local/sdkman/candidates/java/21.0.9-ms"
export GRADLE_USER_HOME="$REPO_ROOT/.wsl-gradle"
export PATH="$REPO_ROOT/.wsl-cmake/bin:$REPO_ROOT/.wsl-gperf:$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"

echo "🚀 FishIT-Player Development Environment"
echo "========================================"
echo ""
echo "ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
echo "JAVA_HOME: $JAVA_HOME"
echo "GRADLE_USER_HOME: $GRADLE_USER_HOME"
echo ""

# Check if command is provided
if [ $# -eq 0 ]; then
    echo "Usage: ./dev.sh [command]"
    echo ""
    echo "Available commands:"
    echo "  build           - Build debug APK"
    echo "  build-release   - Build release APK"
    echo "  test            - Run unit tests"
    echo "  format          - Format code with ktlint"
    echo "  lint            - Run linting checks"
    echo "  quality         - Run all quality checks"
    echo "  clean           - Clean build"
    echo "  install         - Install debug APK on device"
    echo "  emulator-tv     - Start Android TV emulator"
    echo "  emulator-phone  - Start Pixel 5 emulator"
    echo "  devices         - List connected devices"
    echo "  logcat          - Show logcat output"
    echo "  avds            - List available AVDs"
    echo "  shell           - Start interactive shell with env vars set"
    echo ""
    exit 0
fi

COMMAND=$1

case $COMMAND in
    build)
        echo "🏗️  Building debug APK..."
        ./gradlew assembleDebug
        ;;
    build-release)
        echo "🚀 Building release APK..."
        ./gradlew assembleRelease
        ;;
    test)
        echo "🧪 Running tests..."
        ./gradlew test
        ;;
    format)
        echo "✨ Formatting code..."
        ./gradlew ktlintFormat
        ;;
    lint)
        echo "🔍 Running lint checks..."
        ./gradlew ktlintCheck detekt lintDebug
        ;;
    quality)
        echo "✅ Running full quality check..."
        ./gradlew ktlintCheck detekt lintDebug test
        ;;
    clean)
        echo "🧹 Cleaning build..."
        ./gradlew clean
        ;;
    install)
        echo "📱 Installing debug APK..."
        ./gradlew installDebug
        ;;
    emulator-tv)
        echo "📺 Starting Android TV emulator..."
        "$ANDROID_SDK_ROOT/emulator/emulator" -avd Android_TV_1080p_API_31 -no-snapshot-load &
        ;;
    emulator-phone)
        echo "📱 Starting Pixel 5 emulator..."
        "$ANDROID_SDK_ROOT/emulator/emulator" -avd Pixel_5_API_31 -no-snapshot-load &
        ;;
    devices)
        echo "🔌 Connected devices:"
        adb devices -l
        ;;
    logcat)
        echo "📊 Showing logcat (Ctrl+C to stop)..."
        adb logcat | grep --color=auto -E "FishIT|m3usuite"
        ;;
    avds)
        echo "📋 Available AVDs:"
        "$ANDROID_SDK_ROOT/emulator/emulator" -list-avds
        ;;
    shell)
        echo "🐚 Starting interactive shell with environment configured..."
        echo "Type 'exit' to return"
        bash
        ;;
    *)
        echo "❌ Unknown command: $COMMAND"
        echo "Run './dev.sh' without arguments to see available commands"
        exit 1
        ;;
esac
