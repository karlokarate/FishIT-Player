#!/usr/bin/env bash
#
# fishit-cli-setup.sh
#
# Interactive setup script for FishIT Pipeline CLI.
# Configures Telegram and/or Xtream credentials and exports them as environment variables.
#
# Usage:
#   source ./fishit-cli-setup.sh
#   # or
#   . ./fishit-cli-setup.sh
#
# After sourcing, run the CLI with:
#   ./fishit-cli telegram status
#   ./fishit-cli xtream list-vod
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}"
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║           FishIT Pipeline CLI - Setup Wizard              ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Detect script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$HOME/.fishit-cli-config"

# Function to load existing config
load_config() {
    if [ -f "$CONFIG_FILE" ]; then
        echo -e "${YELLOW}📂 Found existing configuration at $CONFIG_FILE${NC}"
        read -p "Load existing configuration? [Y/n]: " load_existing
        if [[ ! "$load_existing" =~ ^[Nn]$ ]]; then
            source "$CONFIG_FILE"
            echo -e "${GREEN}✅ Configuration loaded${NC}"
            return 0
        fi
    fi
    return 1
}

# Function to save config
save_config() {
    cat > "$CONFIG_FILE" << EOF
# FishIT CLI Configuration
# Generated on $(date)

# Telegram Configuration
export TG_API_ID="$TG_API_ID"
export TG_API_HASH="$TG_API_HASH"
export TG_SESSION_PATH="$TG_SESSION_PATH"

# Xtream Configuration
export XTREAM_URL="$XTREAM_URL"
export XTREAM_USERNAME="$XTREAM_USERNAME"
export XTREAM_PASSWORD="$XTREAM_PASSWORD"
EOF
    chmod 600 "$CONFIG_FILE"
    echo -e "${GREEN}✅ Configuration saved to $CONFIG_FILE${NC}"
}

# Function to setup Telegram
setup_telegram() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}📱 Telegram Configuration${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "To use Telegram features, you need:"
    echo "  1. API credentials from https://my.telegram.org/apps"
    echo "  2. An existing TDLib session directory"
    echo ""

    read -p "Configure Telegram? [y/N]: " setup_tg
    if [[ "$setup_tg" =~ ^[Yy]$ ]]; then
        # API ID
        read -p "Enter API ID [${TG_API_ID:-not set}]: " new_api_id
        if [ -n "$new_api_id" ]; then
            export TG_API_ID="$new_api_id"
        fi

        # API Hash
        read -p "Enter API Hash [${TG_API_HASH:+****}${TG_API_HASH:-not set}]: " new_api_hash
        if [ -n "$new_api_hash" ]; then
            export TG_API_HASH="$new_api_hash"
        fi

        # Session Path
        default_session="${TG_SESSION_PATH:-$HOME/.tdlib-session}"
        read -p "Enter TDLib session path [$default_session]: " new_session_path
        if [ -n "$new_session_path" ]; then
            export TG_SESSION_PATH="$new_session_path"
        else
            export TG_SESSION_PATH="$default_session"
        fi

        # Validate session path
        if [ -d "$TG_SESSION_PATH" ]; then
            echo -e "${GREEN}✅ Session directory found: $TG_SESSION_PATH${NC}"
        else
            echo -e "${YELLOW}⚠️  Session directory not found. Create it or provide a valid path.${NC}"
        fi

        echo -e "${GREEN}✅ Telegram configured${NC}"
    else
        echo -e "${YELLOW}⏭️  Skipping Telegram configuration${NC}"
    fi
}

# Function to setup Xtream
setup_xtream() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}📺 Xtream Configuration${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "To use Xtream features, you need provider credentials:"
    echo "  - Base URL (e.g., http://provider.com:8080)"
    echo "  - Username"
    echo "  - Password"
    echo ""

    read -p "Configure Xtream? [y/N]: " setup_xc
    if [[ "$setup_xc" =~ ^[Yy]$ ]]; then
        # URL
        read -p "Enter Xtream URL [${XTREAM_URL:-not set}]: " new_url
        if [ -n "$new_url" ]; then
            export XTREAM_URL="$new_url"
        fi

        # Username
        read -p "Enter Username [${XTREAM_USERNAME:-not set}]: " new_username
        if [ -n "$new_username" ]; then
            export XTREAM_USERNAME="$new_username"
        fi

        # Password
        read -sp "Enter Password [${XTREAM_PASSWORD:+****}${XTREAM_PASSWORD:-not set}]: " new_password
        echo ""
        if [ -n "$new_password" ]; then
            export XTREAM_PASSWORD="$new_password"
        fi

        echo -e "${GREEN}✅ Xtream configured${NC}"
    else
        echo -e "${YELLOW}⏭️  Skipping Xtream configuration${NC}"
    fi
}

# Function to show summary
show_summary() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}📋 Configuration Summary${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    
    echo -e "${CYAN}Telegram:${NC}"
    if [ -n "$TG_API_ID" ] && [ -n "$TG_API_HASH" ]; then
        echo -e "  API ID:       ${GREEN}$TG_API_ID${NC}"
        echo -e "  API Hash:     ${GREEN}****${NC}"
        echo -e "  Session Path: ${GREEN}${TG_SESSION_PATH:-not set}${NC}"
    else
        echo -e "  ${YELLOW}Not configured${NC}"
    fi

    echo ""
    echo -e "${CYAN}Xtream:${NC}"
    if [ -n "$XTREAM_URL" ] && [ -n "$XTREAM_USERNAME" ]; then
        echo -e "  URL:      ${GREEN}$XTREAM_URL${NC}"
        echo -e "  Username: ${GREEN}$XTREAM_USERNAME${NC}"
        echo -e "  Password: ${GREEN}****${NC}"
    else
        echo -e "  ${YELLOW}Not configured${NC}"
    fi
    echo ""
}

# Function to show quick start
show_quickstart() {
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}🚀 Quick Start${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "Now you can use the CLI:"
    echo ""
    if [ -n "$TG_API_ID" ]; then
        echo -e "  ${GREEN}./fishit-cli telegram status${NC}       # Check Telegram connection"
        echo -e "  ${GREEN}./fishit-cli telegram list-chats${NC}   # List available chats"
    fi
    if [ -n "$XTREAM_URL" ]; then
        echo -e "  ${GREEN}./fishit-cli xtream status${NC}         # Check Xtream connection"
        echo -e "  ${GREEN}./fishit-cli xtream list-vod${NC}       # List VOD catalog"
        echo -e "  ${GREEN}./fishit-cli xtream list-series${NC}    # List Series catalog"
        echo -e "  ${GREEN}./fishit-cli xtream list-live${NC}      # List Live channels"
    fi
    echo ""
    echo -e "  ${GREEN}./fishit-cli --help${NC}                # Show all commands"
    echo ""
}

# Main flow
main() {
    # Try to load existing config
    load_config || true

    # Setup services
    setup_telegram
    setup_xtream

    # Show summary
    show_summary

    # Save configuration
    read -p "Save configuration for future sessions? [Y/n]: " save_cfg
    if [[ ! "$save_cfg" =~ ^[Nn]$ ]]; then
        save_config
    fi

    # Show quick start
    show_quickstart

    echo -e "${GREEN}✅ Setup complete! Environment variables are now set.${NC}"
    echo ""
}

# Run main
main
