#!/bin/bash
# KönigTV API Test Script
# Tests all major Xtream API endpoints and saves responses

BASE_URL="http://konigtv.com:8080"
USER="Christoph10"
PASS="JQ2rKsQ744"
OUT_DIR="/workspaces/FishIT-Player/api_test_results"

mkdir -p "$OUT_DIR"

echo "=== KönigTV API Test ==="
echo "Testing: $BASE_URL"
echo ""

# 1. Server Info (no action)
echo "1. Server Info..."
curl -s "$BASE_URL/player_api.php?username=$USER&password=$PASS" > "$OUT_DIR/01_server_info.json"
echo "   -> $(wc -c < "$OUT_DIR/01_server_info.json") bytes"

# 2. Live Categories
echo "2. Live Categories..."
curl -s "$BASE_URL/player_api.php?username=$USER&password=$PASS&action=get_live_categories" > "$OUT_DIR/02_live_categories.json"
echo "   -> $(wc -c < "$OUT_DIR/02_live_categories.json") bytes"

# 3. VOD Categories
echo "3. VOD Categories..."
curl -s "$BASE_URL/player_api.php?username=$USER&password=$PASS&action=get_vod_categories" > "$OUT_DIR/03_vod_categories.json"
echo "   -> $(wc -c < "$OUT_DIR/03_vod_categories.json") bytes"

# 4. Series Categories
echo "4. Series Categories..."
curl -s "$BASE_URL/player_api.php?username=$USER&password=$PASS&action=get_series_categories" > "$OUT_DIR/04_series_categories.json"
echo "   -> $(wc -c < "$OUT_DIR/04_series_categories.json") bytes"

# 5. Live Streams (first 50)
echo "5. Live Streams (sample)..."
curl -s "$BASE_URL/player_api.php?username=$USER&password=$PASS&action=get_live_streams" | head -c 50000 > "$OUT_DIR/05_live_streams_sample.json"
echo "   -> $(wc -c < "$OUT_DIR/05_live_streams_sample.json") bytes (truncated)"

# 6. VOD Streams (first 50)
echo "6. VOD Streams (sample)..."
curl -s "$BASE_URL/player_api.php?username=$USER&password=$PASS&action=get_vod_streams" | head -c 50000 > "$OUT_DIR/06_vod_streams_sample.json"
echo "   -> $(wc -c < "$OUT_DIR/06_vod_streams_sample.json") bytes (truncated)"

# 7. Series List (first 50)
echo "7. Series List (sample)..."
curl -s "$BASE_URL/player_api.php?username=$USER&password=$PASS&action=get_series" > "$OUT_DIR/07_series_sample.json"
echo "   -> $(wc -c < "$OUT_DIR/07_series_sample.json") bytes"

# 8. VOD Info (need a VOD ID first)
echo "8. Extracting VOD ID for detail test..."
VOD_ID=$(cat "$OUT_DIR/06_vod_streams_sample.json" | grep -o '"stream_id":[0-9]*' | head -1 | grep -o '[0-9]*')
if [ -n "$VOD_ID" ]; then
    echo "   Testing VOD ID: $VOD_ID"
    curl -s "$BASE_URL/player_api.php?username=$USER&password=$PASS&action=get_vod_info&vod_id=$VOD_ID" > "$OUT_DIR/08_vod_info.json"
    echo "   -> $(wc -c < "$OUT_DIR/08_vod_info.json") bytes"
else
    echo "   -> No VOD ID found"
fi

# 9. Series Info (need a Series ID first)
echo "9. Extracting Series ID for detail test..."
SERIES_ID=$(cat "$OUT_DIR/07_series_sample.json" | grep -o '"series_id":[0-9]*' | head -1 | grep -o '[0-9]*')
if [ -n "$SERIES_ID" ]; then
    echo "   Testing Series ID: $SERIES_ID"
    curl -s "$BASE_URL/player_api.php?username=$USER&password=$PASS&action=get_series_info&series_id=$SERIES_ID" > "$OUT_DIR/09_series_info.json"
    echo "   -> $(wc -c < "$OUT_DIR/09_series_info.json") bytes"
else
    echo "   -> No Series ID found"
fi

# 10. Short EPG (need a stream ID)
echo "10. Extracting Live Stream ID for EPG test..."
LIVE_ID=$(cat "$OUT_DIR/05_live_streams_sample.json" | grep -o '"stream_id":[0-9]*' | head -1 | grep -o '[0-9]*')
if [ -n "$LIVE_ID" ]; then
    echo "    Testing Live Stream ID: $LIVE_ID"
    curl -s "$BASE_URL/player_api.php?username=$USER&password=$PASS&action=get_short_epg&stream_id=$LIVE_ID" > "$OUT_DIR/10_short_epg.json"
    echo "    -> $(wc -c < "$OUT_DIR/10_short_epg.json") bytes"
else
    echo "    -> No Live ID found"
fi

# 11. XMLTV EPG URL
echo "11. Testing XMLTV EPG URL..."
curl -s -I "$BASE_URL/xmltv.php?username=$USER&password=$PASS" 2>/dev/null | head -5 > "$OUT_DIR/11_xmltv_headers.txt"
echo "    -> Headers saved"

echo ""
echo "=== Summary ==="
echo "Results saved to: $OUT_DIR"
ls -la "$OUT_DIR"

echo ""
echo "=== Quick Analysis ==="

# Analyze server info
echo ""
echo "--- Server Info ---"
cat "$OUT_DIR/01_server_info.json" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    ui = d.get('user_info', {})
    si = d.get('server_info', {})
    print(f'  Status: {ui.get(\"status\", \"N/A\")}')
    print(f'  Username: {ui.get(\"username\", \"N/A\")}')
    print(f'  Exp Date: {ui.get(\"exp_date\", \"N/A\")}')
    print(f'  Max Connections: {ui.get(\"max_connections\", \"N/A\")}')
    print(f'  Active Connections: {ui.get(\"active_cons\", \"N/A\")}')
    print(f'  Server URL: {si.get(\"url\", \"N/A\")}')
    print(f'  Server Port: {si.get(\"port\", \"N/A\")}')
    print(f'  HTTPS Port: {si.get(\"https_port\", \"N/A\")}')
    print(f'  Timezone: {si.get(\"timezone\", \"N/A\")}')
except Exception as e:
    print(f'  Parse error: {e}')
" 2>/dev/null || echo "  (Python parsing failed)"

# Count categories
echo ""
echo "--- Category Counts ---"
for f in live_categories vod_categories series_categories; do
    count=$(cat "$OUT_DIR"/*"$f"*.json 2>/dev/null | grep -o '"category_id"' | wc -l)
    echo "  $f: $count"
done

# Sample structure analysis
echo ""
echo "--- Live Stream Sample Fields ---"
cat "$OUT_DIR/05_live_streams_sample.json" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    if isinstance(d, list) and len(d) > 0:
        print(f'  Total in sample: {len(d)}')
        first = d[0]
        print(f'  Fields: {list(first.keys())}')
except: pass
" 2>/dev/null || head -c 500 "$OUT_DIR/05_live_streams_sample.json"

echo ""
echo "--- VOD Stream Sample Fields ---"
cat "$OUT_DIR/06_vod_streams_sample.json" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    if isinstance(d, list) and len(d) > 0:
        print(f'  Total in sample: {len(d)}')
        first = d[0]
        print(f'  Fields: {list(first.keys())}')
except: pass
" 2>/dev/null || head -c 500 "$OUT_DIR/06_vod_streams_sample.json"

echo ""
echo "--- Series Sample Fields ---"
cat "$OUT_DIR/07_series_sample.json" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    if isinstance(d, list) and len(d) > 0:
        print(f'  Total in sample: {len(d)}')
        first = d[0]
        print(f'  Fields: {list(first.keys())}')
except: pass
" 2>/dev/null || head -c 500 "$OUT_DIR/07_series_sample.json"

echo ""
echo "--- VOD Info Structure ---"
cat "$OUT_DIR/08_vod_info.json" 2>/dev/null | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(f'  Top-level keys: {list(d.keys())}')
    if 'info' in d:
        print(f'  info fields: {list(d[\"info\"].keys())}')
    if 'movie_data' in d:
        print(f'  movie_data fields: {list(d[\"movie_data\"].keys())}')
except Exception as e:
    print(f'  Parse error: {e}')
" 2>/dev/null || echo "  (No VOD info)"

echo ""
echo "--- Series Info Structure ---"
cat "$OUT_DIR/09_series_info.json" 2>/dev/null | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(f'  Top-level keys: {list(d.keys())}')
    if 'info' in d:
        print(f'  info fields: {list(d[\"info\"].keys())}')
    if 'seasons' in d:
        print(f'  seasons count: {len(d[\"seasons\"])}')
    if 'episodes' in d:
        eps = d['episodes']
        print(f'  episodes keys (seasons): {list(eps.keys())}')
        for s, ep_list in eps.items():
            if isinstance(ep_list, list) and len(ep_list) > 0:
                print(f'    Season {s} episode fields: {list(ep_list[0].keys())}')
                break
except Exception as e:
    print(f'  Parse error: {e}')
" 2>/dev/null || echo "  (No Series info)"

echo ""
echo "=== Done ==="
