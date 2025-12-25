#!/bin/bash
# Fix JAVA_HOME for current session and permanently

# Find correct Java installation
JAVA_DIR="/usr/lib/jvm/msopenjdk-current"
if [ ! -d "$JAVA_DIR" ]; then
    # Try finding Java 21
    JAVA_DIR=$(find /usr/lib/jvm -maxdepth 1 -type d -name "*21*" 2>/dev/null | head -1)
fi
if [ -z "$JAVA_DIR" ] || [ ! -d "$JAVA_DIR" ]; then
    JAVA_DIR=$(dirname $(dirname $(readlink -f $(which java))))
fi

echo "Found Java at: $JAVA_DIR"
echo ""

# Export for current session
export JAVA_HOME="$JAVA_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

# Verify
echo "JAVA_HOME is now: $JAVA_HOME"
java -version

# Add to bashrc if not present
if ! grep -q "^export JAVA_HOME=" ~/.bashrc 2>/dev/null; then
    echo "" >> ~/.bashrc
    echo "# Java Home (FishIT-Player)" >> ~/.bashrc
    echo "export JAVA_HOME=\"$JAVA_DIR\"" >> ~/.bashrc
    echo "export PATH=\"\$JAVA_HOME/bin:\$PATH\"" >> ~/.bashrc
    echo ""
    echo "✓ Added JAVA_HOME to ~/.bashrc for persistence"
fi

# Unset any invalid SDKMAN settings
if [ -n "$SDKMAN_DIR" ]; then
    unset SDKMAN_DIR
    echo "✓ Cleared invalid SDKMAN_DIR"
fi

echo ""
echo "======================================"
echo "To apply in current terminal, run:"
echo "  source ~/.bashrc"
echo "Or copy and run:"
echo "  export JAVA_HOME=\"$JAVA_DIR\""
echo "======================================"
