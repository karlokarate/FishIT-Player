#!/bin/bash
# Post-create script for Android development in Codespaces

set -e

echo "=== Setting up Android development environment ==="

# SDK location in home directory (persists across rebuilds, not in workspace)
SDK_DIR="/home/codespace/.android-sdk"

# Find correct Java installation
JAVA_DIR="/usr/lib/jvm/msopenjdk-current"
if [ ! -d "$JAVA_DIR" ]; then
    # Fallback: find any Java 21 installation
    JAVA_DIR=$(find /usr/lib/jvm -maxdepth 1 -type d -name "*21*" 2>/dev/null | head -1)
fi
if [ -z "$JAVA_DIR" ] || [ ! -d "$JAVA_DIR" ]; then
    JAVA_DIR=$(dirname $(dirname $(readlink -f $(which java))))
fi

echo "Using JAVA_HOME: $JAVA_DIR"

# Set JAVA_HOME in bashrc for persistence
if ! grep -q "JAVA_HOME=" ~/.bashrc 2>/dev/null; then
    echo "export JAVA_HOME=\"$JAVA_DIR\"" >> ~/.bashrc
    echo "export PATH=\"\$JAVA_HOME/bin:\$PATH\"" >> ~/.bashrc
    echo "✓ JAVA_HOME added to .bashrc"
fi

# Export for current session
export JAVA_HOME="$JAVA_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

# Set SDK location in local.properties
echo "sdk.dir=$SDK_DIR" > /workspaces/FishIT-Player/local.properties
echo "✓ local.properties configured"

# Export environment variables for current session
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

# Install Android SDK cmdline-tools if not present
if [ ! -d "$SDK_DIR/cmdline-tools/latest/bin" ]; then
    echo "=== Installing Android SDK Command Line Tools ==="
    mkdir -p "$SDK_DIR/cmdline-tools"
    
    # Download command line tools
    CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    TEMP_ZIP="/tmp/cmdline-tools.zip"
    
    echo "Downloading Android Command Line Tools..."
    curl -L -o "$TEMP_ZIP" "$CMDLINE_TOOLS_URL"
    
    echo "Extracting..."
    unzip -q "$TEMP_ZIP" -d "$SDK_DIR/cmdline-tools"
    mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rm "$TEMP_ZIP"
    
    echo "✓ Android cmdline-tools installed"
fi

# Install required SDK packages if cmdline-tools are present
if [ -d "$SDK_DIR/cmdline-tools/latest/bin" ]; then
    echo "=== Installing Android SDK packages ==="
    yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses 2>/dev/null || true
    
    # Install packages matching Releasebuildsafe.yml workflow
    "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --install \
        "platform-tools" \
        "platforms;android-35" \
        "build-tools;35.0.0" \
        2>/dev/null || true
    
    echo "✓ SDK packages installed"
else
    echo "⚠️ SDK cmdline-tools installation failed"
fi

# Start memory monitor in background
echo "=== Starting memory monitor ==="
if [ ! -f /tmp/memory-monitor.pid ] || ! ps -p $(cat /tmp/memory-monitor.pid 2>/dev/null) > /dev/null 2>&1; then
    nohup bash /workspaces/FishIT-Player/.devcontainer/monitor-memory.sh > /tmp/memory-monitor.log 2>&1 &
    echo $! > /tmp/memory-monitor.pid
    echo "✓ Memory monitor started (PID: $!)"
else
    echo "✓ Memory monitor already running"
fi

echo "=== Android environment ready ==="
