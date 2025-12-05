#!/bin/bash
# Post-create script for Android development in Codespaces

set -e

echo "=== Setting up Android development environment ==="

# SDK location in home directory (persists across rebuilds, not in workspace)
SDK_DIR="/home/codespace/.android-sdk"

# Set SDK location in local.properties
echo "sdk.dir=$SDK_DIR" > /workspaces/FishIT-Player/local.properties
echo "✓ local.properties configured"

# Export environment variables for current session
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

# Install required SDK packages if not present
if [ -d "$SDK_DIR/cmdline-tools/latest/bin" ]; then
    echo "=== Installing Android SDK packages ==="
    yes | sdkmanager --licenses 2>/dev/null || true
    
    # Install packages matching Releasebuildsafe.yml workflow
    sdkmanager --install \
        "platform-tools" \
        "platforms;android-35" \
        "build-tools;35.0.0" \
        2>/dev/null || true
    
    echo "✓ SDK packages installed"
else
    echo "⚠️ SDK cmdline-tools not found - rebuild container to install"
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
