#!/bin/bash
# Test script to validate Xtream URL parsing and API endpoint construction
# Based on user's reported issue with konigtv.com

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "============================================================"
echo "Xtream URL Parsing & API Test"
echo "============================================================"
echo ""

# User's input URL (M3U format)
USER_URL="http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts"

echo -e "${YELLOW}Input URL:${NC}"
echo "$USER_URL"
echo ""

# Parse the URL
if [[ $USER_URL =~ ^(https?)://([^:]+):([0-9]+)/get\.php\?username=([^&]+)\&password=([^&]+) ]]; then
    SCHEME="${BASH_REMATCH[1]}"
    HOST="${BASH_REMATCH[2]}"
    PORT="${BASH_REMATCH[3]}"
    USERNAME="${BASH_REMATCH[4]}"
    PASSWORD="${BASH_REMATCH[5]}"
    
    echo -e "${GREEN}✓ URL parsed successfully${NC}"
    echo "  Scheme: $SCHEME"
    echo "  Host: $HOST"
    echo "  Port: $PORT"
    echo "  Username: $USERNAME"
    echo "  Password: ***"
    echo ""
else
    echo -e "${RED}✗ Failed to parse URL${NC}"
    exit 1
fi

# Construct expected player_api.php URLs
BASE_URL="$SCHEME://$HOST:$PORT"
API_URL_NO_ACTION="$BASE_URL/player_api.php?username=$USERNAME&password=$PASSWORD"
API_URL_WITH_ACTION="$BASE_URL/player_api.php?action=get_live_categories&username=$USERNAME&password=$PASSWORD"

echo -e "${YELLOW}Expected API Endpoints:${NC}"
echo "  Base URL: $BASE_URL"
echo "  Server Info (no action): ${API_URL_NO_ACTION//$PASSWORD/***}"
echo "  With action: ${API_URL_WITH_ACTION//$PASSWORD/***}"
echo ""

# Test server info endpoint (no action)
echo "============================================================"
echo "Testing Server Info Endpoint (no action parameter)"
echo "============================================================"
echo ""

echo "Calling: ${API_URL_NO_ACTION//$PASSWORD/***}"
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" "$API_URL_NO_ACTION" || echo "HTTP_CODE:000")

HTTP_CODE=$(echo "$RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)
BODY=$(echo "$RESPONSE" | sed '/HTTP_CODE:/d')

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ HTTP 200 OK${NC}"
    
    if [ -z "$BODY" ]; then
        echo -e "${RED}✗ Empty response body${NC}"
        echo ""
        echo -e "${YELLOW}This is the root cause of '(empty response)' error!${NC}"
        echo "Server returned HTTP 200 but with empty body."
        echo ""
    else
        BODY_LENGTH=${#BODY}
        echo -e "${GREEN}✓ Response received (${BODY_LENGTH} bytes)${NC}"
        
        # Check if it's valid JSON
        if echo "$BODY" | jq . > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Valid JSON response${NC}"
            echo ""
            echo "Response preview:"
            echo "$BODY" | jq . 2>/dev/null | head -20
        else
            echo -e "${RED}✗ Invalid JSON response${NC}"
            echo ""
            echo "Response (first 200 chars):"
            echo "${BODY:0:200}"
        fi
    fi
elif [ "$HTTP_CODE" = "000" ]; then
    echo -e "${RED}✗ Connection failed${NC}"
    echo "Could not connect to server. Check network/firewall."
else
    echo -e "${RED}✗ HTTP $HTTP_CODE${NC}"
    echo "Response body (if any):"
    echo "$BODY"
fi

echo ""
echo "============================================================"
echo "Testing Action Endpoint (with action=get_live_categories)"
echo "============================================================"
echo ""

echo "Calling: ${API_URL_WITH_ACTION//$PASSWORD/***}"
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" "$API_URL_WITH_ACTION" || echo "HTTP_CODE:000")

HTTP_CODE=$(echo "$RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)
BODY=$(echo "$RESPONSE" | sed '/HTTP_CODE:/d')

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ HTTP 200 OK${NC}"
    
    if [ -z "$BODY" ]; then
        echo -e "${RED}✗ Empty response body${NC}"
    else
        BODY_LENGTH=${#BODY}
        echo -e "${GREEN}✓ Response received (${BODY_LENGTH} bytes)${NC}"
        
        # Check if it's valid JSON
        if echo "$BODY" | jq . > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Valid JSON response${NC}"
            echo ""
            echo "Categories count:"
            echo "$BODY" | jq '. | length' 2>/dev/null || echo "N/A"
        else
            echo -e "${RED}✗ Invalid JSON response${NC}"
        fi
    fi
elif [ "$HTTP_CODE" = "000" ]; then
    echo -e "${RED}✗ Connection failed${NC}"
else
    echo -e "${RED}✗ HTTP $HTTP_CODE${NC}"
fi

echo ""
echo "============================================================"
echo "Summary & Recommendations"
echo "============================================================"
echo ""
echo "If server info endpoint returns empty body:"
echo "  1. Try using action-based endpoint instead"
echo "  2. Some panels require action parameter for all requests"
echo "  3. Consider using 'get_live_categories' as health check"
echo ""
echo "If connection fails:"
echo "  1. Check network connectivity"
echo "  2. Verify firewall allows outbound connections"
echo "  3. Try different port (80, 8000, 8080, etc.)"
echo ""
