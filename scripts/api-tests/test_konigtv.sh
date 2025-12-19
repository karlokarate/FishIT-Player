#!/bin/bash
# Test ALL Xtream API endpoints with KönigTV credentials
# Runs requests in parallel (non-blocking) for speed

set -e

BASE="http://konigtv.com:8080"
USER="Christoph10"
PASS="JQ2rKsQ744"
API="$BASE/player_api.php?username=$USER&password=$PASS"
OUTPUT_DIR="/tmp/xtream_test_$(date +%s)"
TIMEOUT=30
MAX_PARALLEL=10

mkdir -p "$OUTPUT_DIR"

echo "============================================================"
echo "XTREAM API FULL TEST - KönigTV"
echo "============================================================"
echo "Host: $BASE"
echo "User: $USER"
echo "Output: $OUTPUT_DIR"
echo "Started: $(date)"
echo ""

# Helper function: fetch and save
fetch() {
    local name="$1"
    local url="$2"
    local file="$OUTPUT_DIR/${name}.json"
    curl -s --max-time $TIMEOUT "$url" > "$file" 2>/dev/null
    local size=$(wc -c < "$file")
    echo "  ✓ $name ($size bytes)"
}

# Helper function: fetch with limit
fetch_limited() {
    local name="$1"
    local url="$2"
    local limit="${3:-5000}"
    local file="$OUTPUT_DIR/${name}.json"
    curl -s --max-time $TIMEOUT "$url" | head -c "$limit" > "$file" 2>/dev/null
    local size=$(wc -c < "$file")
    echo "  ✓ $name ($size bytes, limited)"
}

echo "============================================================"
echo "PHASE 1: Authentication & Server Info"
echo "============================================================"

# Server Info (contains auth status, user info, server info)
fetch "01_server_info" "$API" &

# Panel API (additional server details)
fetch "02_panel_api" "$BASE/panel_api.php?username=$USER&password=$PASS" &

wait
echo ""

# Display server info summary
echo "--- Server Info Summary ---"
python3 -c "
import json
try:
    with open('$OUTPUT_DIR/01_server_info.json') as f:
        data = json.load(f)
    ui = data.get('user_info', {})
    si = data.get('server_info', {})
    print(f\"  Status: {ui.get('status', 'N/A')}\")
    print(f\"  Auth: {ui.get('auth', 'N/A')}\")
    print(f\"  Expiry: {ui.get('exp_date', 'N/A')}\")
    print(f\"  Active Cons: {ui.get('active_cons', 'N/A')}/{ui.get('max_connections', 'N/A')}\")
    print(f\"  Server URL: {si.get('url', 'N/A')}:{si.get('port', 'N/A')}\")
    print(f\"  Timezone: {si.get('timezone', 'N/A')}\")
    print(f\"  HTTPS Port: {si.get('https_port', 'N/A')}\")
    print(f\"  RTC Server: {si.get('rtmp_port', 'N/A')}\")
except Exception as e:
    print(f'  Error parsing: {e}')
" 2>/dev/null || echo "  (Could not parse server info)"
echo ""

echo "============================================================"
echo "PHASE 2: Categories (parallel)"
echo "============================================================"

fetch "03_live_categories" "$API&action=get_live_categories" &
fetch "04_vod_categories" "$API&action=get_vod_categories" &
fetch "05_series_categories" "$API&action=get_series_categories" &

wait
echo ""

# Count categories
echo "--- Category Counts ---"
for cat in live vod series; do
    num=$(cat "$OUTPUT_DIR"/*_${cat}_categories.json 2>/dev/null | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
    echo "  $cat: $num categories"
done
echo ""

echo "============================================================"
echo "PHASE 3: Full Content Lists (parallel, large data)"
echo "============================================================"

# Full streams without category filter (can be large!)
fetch "06_live_streams_all" "$API&action=get_live_streams" &
fetch "07_vod_streams_all" "$API&action=get_vod_streams" &
fetch "08_series_all" "$API&action=get_series" &

wait
echo ""

# Count items
echo "--- Content Counts ---"
live_count=$(cat "$OUTPUT_DIR/06_live_streams_all.json" 2>/dev/null | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
vod_count=$(cat "$OUTPUT_DIR/07_vod_streams_all.json" 2>/dev/null | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
series_count=$(cat "$OUTPUT_DIR/08_series_all.json" 2>/dev/null | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
echo "  Live Channels: $live_count"
echo "  VOD Items: $vod_count"
echo "  Series: $series_count"
echo ""

echo "============================================================"
echo "PHASE 4: Sample Detail Requests (parallel)"
echo "============================================================"

# Get first stream/vod/series IDs for detail requests
FIRST_LIVE=$(cat "$OUTPUT_DIR/06_live_streams_all.json" 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); print(d[0]['stream_id'] if d else '')" 2>/dev/null || echo "")
FIRST_VOD=$(cat "$OUTPUT_DIR/07_vod_streams_all.json" 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); print(d[0]['stream_id'] if d else '')" 2>/dev/null || echo "")
FIRST_SERIES=$(cat "$OUTPUT_DIR/08_series_all.json" 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); print(d[0]['series_id'] if d else '')" 2>/dev/null || echo "")

echo "  Using sample IDs: Live=$FIRST_LIVE, VOD=$FIRST_VOD, Series=$FIRST_SERIES"

# VOD Info (detailed info about a single VOD)
if [ -n "$FIRST_VOD" ]; then
    fetch "09_vod_info_sample" "$API&action=get_vod_info&vod_id=$FIRST_VOD" &
fi

# Series Info (detailed info including episodes)
if [ -n "$FIRST_SERIES" ]; then
    fetch "10_series_info_sample" "$API&action=get_series_info&series_id=$FIRST_SERIES" &
fi

# Short EPG for a live channel
if [ -n "$FIRST_LIVE" ]; then
    fetch "11_short_epg_sample" "$API&action=get_short_epg&stream_id=$FIRST_LIVE" &
    fetch "12_short_epg_limit" "$API&action=get_short_epg&stream_id=$FIRST_LIVE&limit=10" &
fi

wait
echo ""

echo "============================================================"
echo "PHASE 5: EPG Data (parallel)"
echo "============================================================"

# Simple Data Table (EPG)
fetch "13_epg_simple_table" "$API&action=get_simple_data_table&stream_id=$FIRST_LIVE" &

# XMLTV EPG (can be very large, limit it)
fetch_limited "14_xmltv_epg" "$BASE/xmltv.php?username=$USER&password=$PASS" 50000 &

# EPG by stream (alternative format)
if [ -n "$FIRST_LIVE" ]; then
    fetch "15_epg_by_stream" "$API&action=get_epg&stream_id=$FIRST_LIVE" &
fi

wait
echo ""

echo "============================================================"
echo "PHASE 6: Category-filtered Streams (parallel samples)"
echo "============================================================"

# Get first category IDs
FIRST_LIVE_CAT=$(cat "$OUTPUT_DIR/03_live_categories.json" 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); print(d[0]['category_id'] if d else '')" 2>/dev/null || echo "")
FIRST_VOD_CAT=$(cat "$OUTPUT_DIR/04_vod_categories.json" 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); print(d[0]['category_id'] if d else '')" 2>/dev/null || echo "")
FIRST_SERIES_CAT=$(cat "$OUTPUT_DIR/05_series_categories.json" 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); print(d[0]['category_id'] if d else '')" 2>/dev/null || echo "")

echo "  Using category IDs: Live=$FIRST_LIVE_CAT, VOD=$FIRST_VOD_CAT, Series=$FIRST_SERIES_CAT"

if [ -n "$FIRST_LIVE_CAT" ]; then
    fetch "16_live_by_category" "$API&action=get_live_streams&category_id=$FIRST_LIVE_CAT" &
fi
if [ -n "$FIRST_VOD_CAT" ]; then
    fetch "17_vod_by_category" "$API&action=get_vod_streams&category_id=$FIRST_VOD_CAT" &
fi
if [ -n "$FIRST_SERIES_CAT" ]; then
    fetch "18_series_by_category" "$API&action=get_series&category_id=$FIRST_SERIES_CAT" &
fi

wait
echo ""

echo "============================================================"
echo "PHASE 7: M3U Playlist Formats (parallel)"
echo "============================================================"

# Different M3U output formats
fetch_limited "19_m3u_ts" "$BASE/get.php?username=$USER&password=$PASS&type=m3u_plus&output=ts" 10000 &
fetch_limited "20_m3u_m3u8" "$BASE/get.php?username=$USER&password=$PASS&type=m3u_plus&output=m3u8" 10000 &
fetch_limited "21_m3u_hls" "$BASE/get.php?username=$USER&password=$PASS&type=m3u_plus&output=hls" 10000 &

# M3U with specific content types
fetch_limited "22_m3u_live_only" "$BASE/get.php?username=$USER&password=$PASS&type=m3u_plus&output=ts&type=live" 10000 &
fetch_limited "23_m3u_vod_only" "$BASE/get.php?username=$USER&password=$PASS&type=m3u_plus&output=ts&type=vod" 10000 &

wait
echo ""

echo "============================================================"
echo "PHASE 8: Additional Endpoints (parallel)"
echo "============================================================"

# Some providers support these additional endpoints
fetch "24_user_info" "$API&action=get_user_info" &
fetch "25_server_info_action" "$API&action=get_server_info" &

# Catchup/Timeshift (if supported)
if [ -n "$FIRST_LIVE" ]; then
    fetch "26_catchup_days" "$API&action=get_catchup_days&stream_id=$FIRST_LIVE" &
    # Catchup table for specific date
    fetch "27_catchup_table" "$API&action=get_simple_data_table&stream_id=$FIRST_LIVE" &
fi

wait
echo ""

echo "============================================================"
echo "PHASE 9: Stream URL Generation Examples"
echo "============================================================"

echo "--- Live Stream URLs ---"
if [ -n "$FIRST_LIVE" ]; then
    echo "  TS:    $BASE/live/$USER/$PASS/$FIRST_LIVE.ts"
    echo "  M3U8:  $BASE/live/$USER/$PASS/$FIRST_LIVE.m3u8"
    echo "  RTMP:  rtmp://$BASE/live/$USER/$PASS/$FIRST_LIVE"
fi
echo ""

echo "--- VOD Stream URLs ---"
if [ -n "$FIRST_VOD" ]; then
    echo "  MP4:   $BASE/movie/$USER/$PASS/$FIRST_VOD.mp4"
    echo "  MKV:   $BASE/movie/$USER/$PASS/$FIRST_VOD.mkv"
    echo "  M3U8:  $BASE/movie/$USER/$PASS/$FIRST_VOD.m3u8"
fi
echo ""

echo "--- Series Episode URLs ---"
if [ -n "$FIRST_SERIES" ]; then
    # Get first episode ID from series info
    FIRST_EPISODE=$(cat "$OUTPUT_DIR/10_series_info_sample.json" 2>/dev/null | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    eps=d.get('episodes',{})
    for season in eps.values():
        if season:
            print(season[0].get('id',''))
            break
except: pass
" 2>/dev/null || echo "")
    if [ -n "$FIRST_EPISODE" ]; then
        echo "  Episode: $BASE/series/$USER/$PASS/$FIRST_EPISODE.mkv"
    fi
fi
echo ""

echo "============================================================"
echo "SUMMARY"
echo "============================================================"

# File statistics
total_files=$(ls -1 "$OUTPUT_DIR"/*.json 2>/dev/null | wc -l)
total_size=$(du -sh "$OUTPUT_DIR" 2>/dev/null | cut -f1)

echo "Files saved: $total_files"
echo "Total size: $total_size"
echo "Output dir: $OUTPUT_DIR"
echo ""

echo "--- Content Summary ---"
echo "  Live Categories: $(cat "$OUTPUT_DIR/03_live_categories.json" 2>/dev/null | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "N/A")"
echo "  VOD Categories: $(cat "$OUTPUT_DIR/04_vod_categories.json" 2>/dev/null | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "N/A")"
echo "  Series Categories: $(cat "$OUTPUT_DIR/05_series_categories.json" 2>/dev/null | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "N/A")"
echo "  Live Channels: $live_count"
echo "  VOD Items: $vod_count"
echo "  Series: $series_count"
echo ""

echo "--- Sample Data Preview ---"
echo ""
echo "First Live Channel:"
cat "$OUTPUT_DIR/06_live_streams_all.json" 2>/dev/null | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    if d:
        item=d[0]
        print(f\"  Name: {item.get('name','N/A')}\")
        print(f\"  Stream ID: {item.get('stream_id','N/A')}\")
        print(f\"  Category: {item.get('category_id','N/A')}\")
        print(f\"  EPG ID: {item.get('epg_channel_id','N/A')}\")
        print(f\"  Icon: {item.get('stream_icon','N/A')[:60]}...\")
except: print('  N/A')
" 2>/dev/null || echo "  N/A"
echo ""

echo "First VOD Item:"
cat "$OUTPUT_DIR/07_vod_streams_all.json" 2>/dev/null | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    if d:
        item=d[0]
        print(f\"  Name: {item.get('name','N/A')}\")
        print(f\"  Stream ID: {item.get('stream_id','N/A')}\")
        print(f\"  Rating: {item.get('rating','N/A')}\")
        print(f\"  Year: {item.get('year','N/A')}\")
        print(f\"  Genre: {item.get('genre','N/A')}\")
except: print('  N/A')
" 2>/dev/null || echo "  N/A"
echo ""

echo "First Series:"
cat "$OUTPUT_DIR/08_series_all.json" 2>/dev/null | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    if d:
        item=d[0]
        print(f\"  Name: {item.get('name','N/A')}\")
        print(f\"  Series ID: {item.get('series_id','N/A')}\")
        print(f\"  Rating: {item.get('rating','N/A')}\")
        print(f\"  Year: {item.get('year','N/A')}\")
        print(f\"  Genre: {item.get('genre','N/A')}\")
        print(f\"  Seasons: {item.get('num','N/A')}\")
except: print('  N/A')
" 2>/dev/null || echo "  N/A"
echo ""

echo "Series Info Detail (if available):"
cat "$OUTPUT_DIR/10_series_info_sample.json" 2>/dev/null | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    info=d.get('info',{})
    eps=d.get('episodes',{})
    print(f\"  Title: {info.get('name','N/A')}\")
    print(f\"  Plot: {info.get('plot','N/A')[:100]}...\")
    print(f\"  Cast: {info.get('cast','N/A')[:60]}...\")
    print(f\"  Director: {info.get('director','N/A')}\")
    print(f\"  Seasons: {len(eps)}\")
    total_eps=sum(len(v) for v in eps.values())
    print(f\"  Total Episodes: {total_eps}\")
except: print('  N/A')
" 2>/dev/null || echo "  N/A"
echo ""

echo "============================================================"
echo "API ENDPOINT REFERENCE"
echo "============================================================"
echo "Authentication:"
echo "  GET $BASE/player_api.php?username=X&password=Y"
echo ""
echo "Categories:"
echo "  &action=get_live_categories"
echo "  &action=get_vod_categories"
echo "  &action=get_series_categories"
echo ""
echo "Content Lists:"
echo "  &action=get_live_streams[&category_id=X]"
echo "  &action=get_vod_streams[&category_id=X]"
echo "  &action=get_series[&category_id=X]"
echo ""
echo "Detail Info:"
echo "  &action=get_vod_info&vod_id=X"
echo "  &action=get_series_info&series_id=X"
echo ""
echo "EPG:"
echo "  &action=get_short_epg&stream_id=X[&limit=N]"
echo "  &action=get_simple_data_table&stream_id=X"
echo "  GET $BASE/xmltv.php?username=X&password=Y"
echo ""
echo "Catchup/Timeshift:"
echo "  &action=get_catchup_days&stream_id=X"
echo ""
echo "M3U Playlists:"
echo "  GET $BASE/get.php?username=X&password=Y&type=m3u_plus&output=[ts|m3u8|hls]"
echo ""
echo "Stream URLs:"
echo "  Live:   $BASE/live/USER/PASS/STREAM_ID.[ts|m3u8]"
echo "  VOD:    $BASE/movie/USER/PASS/VOD_ID.[mp4|mkv|m3u8]"
echo "  Series: $BASE/series/USER/PASS/EPISODE_ID.[mp4|mkv]"
echo ""
echo "============================================================"
echo "Completed: $(date)"
echo "Duration: $SECONDS seconds"
echo "============================================================"
