#!/usr/bin/env bash
#
# postStartImportTdlibSession.sh
#
# Imports an encrypted TDLib session into the Codespace.
# This script runs automatically after the Codespace starts.
#
# Required Codespace Secrets:
# - TDLIB_SESSION_URL: URL to download the encrypted session archive
# - TDLIB_SESSION_PASS: Password to decrypt the session archive
#
# Optional:
# - TG_API_ID: Telegram API ID (for runtime)
# - TG_API_HASH: Telegram API Hash (for runtime)
#

set -e

SESSION_ROOT="/workspace/.cache/tdlib-user"
mkdir -p "$SESSION_ROOT"

echo "[tdlib] Starting TDLib session import..."

# Check for required secrets
if [ -z "$TDLIB_SESSION_URL" ] || [ -z "$TDLIB_SESSION_PASS" ]; then
    echo "[tdlib] ⚠️  TDLIB_SESSION_URL or TDLIB_SESSION_PASS not set."
    echo "[tdlib]    Telegram tests will be disabled."
    echo "[tdlib]    To enable: Set secrets in GitHub Codespaces settings."
    exit 0
fi

# Check for API credentials
if [ -z "$TG_API_ID" ] || [ -z "$TG_API_HASH" ]; then
    echo "[tdlib] ⚠️  TG_API_ID or TG_API_HASH not set."
    echo "[tdlib]    Telegram runtime will fail without these."
fi

# Download encrypted session
echo "[tdlib] Downloading encrypted session..."
if ! curl -sSL "$TDLIB_SESSION_URL" -o /tmp/tdlib-session.enc 2>/dev/null; then
    echo "[tdlib] ❌ Failed to download session from TDLIB_SESSION_URL"
    exit 1
fi

# Verify download
if [ ! -s /tmp/tdlib-session.enc ]; then
    echo "[tdlib] ❌ Downloaded file is empty"
    exit 1
fi

# Decrypt session
echo "[tdlib] Decrypting session..."
if ! openssl enc -d -aes-256-cbc \
    -in /tmp/tdlib-session.enc \
    -out /tmp/tdlib-session.tar.gz \
    -pass env:TDLIB_SESSION_PASS 2>/dev/null; then
    echo "[tdlib] ❌ Failed to decrypt session (wrong password?)"
    rm -f /tmp/tdlib-session.enc
    exit 1
fi

# Extract session
echo "[tdlib] Extracting session..."
if ! tar -xzf /tmp/tdlib-session.tar.gz -C "$SESSION_ROOT" 2>/dev/null; then
    echo "[tdlib] ❌ Failed to extract session archive"
    rm -f /tmp/tdlib-session.enc /tmp/tdlib-session.tar.gz
    exit 1
fi

# Cleanup temp files
rm -f /tmp/tdlib-session.enc /tmp/tdlib-session.tar.gz

# Verify session structure
if [ ! -d "$SESSION_ROOT/tdlib/db" ]; then
    echo "[tdlib] ⚠️  Session extracted but db/ folder not found"
    echo "[tdlib]    Expected structure: $SESSION_ROOT/tdlib/db/"
else
    echo "[tdlib] ✅ Session ready at $SESSION_ROOT/tdlib"
    echo "[tdlib]    Database: $SESSION_ROOT/tdlib/db"
    echo "[tdlib]    Files:    $SESSION_ROOT/tdlib/files"
fi

# Set permissions
chmod -R 700 "$SESSION_ROOT"

echo "[tdlib] Session import complete."
