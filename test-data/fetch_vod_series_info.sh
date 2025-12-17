#!/bin/bash
#
# Fetch VOD_INFO and SERIES_INFO to find TMDB IDs
#
# Usage: ./fetch_vod_series_info.sh
#

HOST="konigtv.com"
PORT="8080"
USER="Christoph10"
PASS="JQ2rKsQ744"

OUT_DIR="./xtream-responses"
BASE_URL="http://$HOST:$PORT/player_api.php"

HEADERS=(
  -H "Accept: */*"
  -H "User-Agent: IBOPlayer/1.4 (Android)"
  -H "Host: $HOST:$PORT"
  -H "Connection: Keep-Alive"
  -H "Accept-Encoding: gzip"
)

echo "=== Fetching VOD Info (first VOD item) ==="
# Get first VOD ID from vod_streams.json
VOD_ID=$(cat "$OUT_DIR/vod_streams.json" 2>/dev/null | grep -o '"stream_id":[0-9]*' | head -1 | cut -d: -f2)
if [ -n "$VOD_ID" ]; then
  echo "Fetching VOD info for stream_id=$VOD_ID..."
  curl -s --max-time 60 "${HEADERS[@]}" --compressed \
    "$BASE_URL?username=$USER&password=$PASS&action=get_vod_info&vod_id=$VOD_ID" \
    -o "$OUT_DIR/vod_info_sample.json"
  echo "Saved: vod_info_sample.json"
  head -c 2000 "$OUT_DIR/vod_info_sample.json"
  echo ""
fi

echo ""
echo "=== Fetching Series Info (first Series item) ==="
# Get first Series ID from series.json  
SERIES_ID=$(cat "$OUT_DIR/series.json" 2>/dev/null | grep -o '"series_id":[0-9-]*' | head -1 | cut -d: -f2)
if [ -n "$SERIES_ID" ]; then
  echo "Fetching Series info for series_id=$SERIES_ID..."
  curl -s --max-time 60 "${HEADERS[@]}" --compressed \
    "$BASE_URL?username=$USER&password=$PASS&action=get_series_info&series_id=$SERIES_ID" \
    -o "$OUT_DIR/series_info_sample.json"
  echo "Saved: series_info_sample.json"
  head -c 2000 "$OUT_DIR/series_info_sample.json"
  echo ""
fi

echo ""
echo "=== Summary ==="
ls -la "$OUT_DIR"/*_sample.json 2>/dev/null
