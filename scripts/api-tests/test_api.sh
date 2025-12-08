#!/bin/bash

BASE_URL="http://konigtv.com:8080"
USERNAME="Christoph10"
PASSWORD="JQ2rKsQ744"

echo "=========================================="
echo "TEST 1: Server Info"
echo "=========================================="
curl -s "${BASE_URL}/player_api.php?username=${USERNAME}&password=${PASSWORD}"
echo ""
echo ""

echo "=========================================="
echo "TEST 2: Live Categories"
echo "=========================================="
curl -s "${BASE_URL}/player_api.php?username=${USERNAME}&password=${PASSWORD}&action=get_live_categories"
echo ""
echo ""

echo "=========================================="
echo "TEST 3: VOD Categories"
echo "=========================================="
curl -s "${BASE_URL}/player_api.php?username=${USERNAME}&password=${PASSWORD}&action=get_vod_categories"
echo ""
echo ""

echo "=========================================="
echo "TEST 4: Series Categories"
echo "=========================================="
curl -s "${BASE_URL}/player_api.php?username=${USERNAME}&password=${PASSWORD}&action=get_series_categories"
echo ""
echo ""

echo "=========================================="
echo "TEST 5: Live Streams (first 3000 chars)"
echo "=========================================="
curl -s "${BASE_URL}/player_api.php?username=${USERNAME}&password=${PASSWORD}&action=get_live_streams" | head -c 3000
echo ""
echo ""

echo "=========================================="
echo "TEST 6: VOD Streams (first 3000 chars)"
echo "=========================================="
curl -s "${BASE_URL}/player_api.php?username=${USERNAME}&password=${PASSWORD}&action=get_vod_streams" | head -c 3000
echo ""
echo ""

echo "=========================================="
echo "TEST 7: Series List (first 3000 chars)"
echo "=========================================="
curl -s "${BASE_URL}/player_api.php?username=${USERNAME}&password=${PASSWORD}&action=get_series" | head -c 3000
echo ""
echo ""

echo "=========================================="
echo "TESTS COMPLETE"
echo "=========================================="
