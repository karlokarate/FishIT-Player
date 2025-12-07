#!/bin/bash
# Test Xtream API endpoints with KönigTV credentials

BASE="http://konigtv.com:8080"
USER="Christoph10"
PASS="JQ2rKsQ744"

echo "============================================================"
echo "XTREAM API TEST - KönigTV"
echo "============================================================"
echo "Host: $BASE"
echo "User: $USER"
echo ""

echo ">>> Server Info..."
curl -s "$BASE/player_api.php?username=$USER&password=$PASS" | python3 -m json.tool 2>/dev/null | head -30
echo ""

echo ">>> Live Categories..."
curl -s "$BASE/player_api.php?username=$USER&password=$PASS&action=get_live_categories" | head -c 500
echo ""
echo ""

echo ">>> VOD Categories..."
curl -s "$BASE/player_api.php?username=$USER&password=$PASS&action=get_vod_categories" | head -c 500
echo ""
echo ""

echo ">>> Series Categories..."
curl -s "$BASE/player_api.php?username=$USER&password=$PASS&action=get_series_categories" | head -c 500
echo ""
echo ""

echo ">>> Live Streams (sample)..."
curl -s "$BASE/player_api.php?username=$USER&password=$PASS&action=get_live_streams&category_id=0" | head -c 500
echo ""
echo ""

echo ">>> VOD Streams (sample)..."
curl -s "$BASE/player_api.php?username=$USER&password=$PASS&action=get_vod_streams&category_id=0" | head -c 500
echo ""
echo ""

echo ">>> Series (sample)..."
curl -s "$BASE/player_api.php?username=$USER&password=$PASS&action=get_series&category_id=0" | head -c 500
echo ""
echo ""

echo "============================================================"
echo "PLAYBACK URL EXAMPLES"
echo "============================================================"
echo "Live:   $BASE/live/$USER/$PASS/1.m3u8"
echo "VOD:    $BASE/vod/$USER/$PASS/1.mp4"
echo "Series: $BASE/series/$USER/$PASS/1.mkv"
echo ""
echo "M3U:    $BASE/get.php?username=$USER&password=$PASS&type=m3u_plus&output=ts"
echo ""
echo "TEST COMPLETE"
