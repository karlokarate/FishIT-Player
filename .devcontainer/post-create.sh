#!/bin/bash
# Post-create script for Android development in Codespaces

set -e

echo "=== Setting up Android development environment ==="

# Install required packages
echo "=== Installing required packages ==="
if ! command -v inotifywait &> /dev/null; then
    sudo apt-get install -y --no-install-recommends inotify-tools 2>/dev/null || echo "inotify-tools install skipped"
    echo "✓ inotify-tools installed"
fi

# SDK location in home directory (persists across rebuilds, not in workspace)
# Use $HOME to support both 'codespace' and 'vscode' users
SDK_DIR="$HOME/.android-sdk"

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

# Set SDK location in local.properties (uses $HOME which resolves correctly)
echo "sdk.dir=$SDK_DIR" > /workspaces/FishIT-Player/local.properties
echo "✓ local.properties configured (SDK_DIR=$SDK_DIR)"

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

# Install latest Jupyter for data analysis and debugging
echo "=== Installing Jupyter ==="
if ! command -v jupyter &> /dev/null; then
    echo "Installing Jupyter and JupyterLab..."
    pip3 install --user --upgrade pip setuptools wheel
    pip3 install --user --upgrade jupyter jupyterlab notebook ipywidgets
    
    # Add to PATH
    export PATH="$HOME/.local/bin:$PATH"
    if ! grep -q ".local/bin" ~/.bashrc 2>/dev/null; then
        echo "export PATH=\"\$HOME/.local/bin:\$PATH\"" >> ~/.bashrc
    fi
    
    echo "✓ Jupyter installed (version: $(jupyter --version | head -1))"
else
    echo "✓ Jupyter already installed (version: $(jupyter --version | head -1))"
    # Upgrade to latest version
    pip3 install --user --upgrade jupyter jupyterlab notebook ipywidgets
    echo "✓ Jupyter upgraded to latest version"
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

# Install Scope Guard hooks (mandatory for architecture enforcement)
echo "=== Installing Scope Guard enforcement hooks ==="
if [ -f "$WORKSPACE_ROOT/scripts/install-scope-guard-hooks.sh" ]; then
    chmod +x "$WORKSPACE_ROOT/scripts/install-scope-guard-hooks.sh"
    chmod +x "$WORKSPACE_ROOT/tools/scope-guard-cli.py" 2>/dev/null || true
    chmod +x "$WORKSPACE_ROOT/scripts/hooks/"* 2>/dev/null || true
    "$WORKSPACE_ROOT/scripts/install-scope-guard-hooks.sh"
    echo "✓ Scope Guard pre-commit hook installed"
else
    echo "⚠ Scope Guard installer not found - hooks not installed"
fi

echo "=== Android environment ready ==="
echo "=== Jupyter ready - use 'jupyter notebook' or 'jupyter lab' to start ==="
echo "=== Scope Guard: Git commits are now enforced against READ_ONLY paths ==="
