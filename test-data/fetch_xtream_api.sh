#!/bin/bash
#
# Xtream API Fetcher - Run this locally to bypass Cloudflare
#
# Usage: ./fetch_xtream_api.sh "http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts"
#

M3U_LINK="${1:-http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts}"

# Extract credentials from M3U link
HOST=$(echo "$M3U_LINK" | sed -n 's|.*://\([^:/]*\).*|\1|p')
PORT=$(echo "$M3U_LINK" | sed -n 's|.*://[^:]*:\([0-9]*\)/.*|\1|p')
PORT=${PORT:-80}
USER=$(echo "$M3U_LINK" | sed -n 's|.*username=\([^&]*\).*|\1|p')
PASS=$(echo "$M3U_LINK" | sed -n 's|.*password=\([^&]*\).*|\1|p')

echo "=== Extracted Credentials ==="
echo "Host: $HOST"
echo "Port: $PORT"
echo "Username: $USER"
echo "Password: $PASS"
echo ""

# Output directory
OUT_DIR="./xtream-responses"
mkdir -p "$OUT_DIR"

# Common headers (IBOPlayer style)
HEADERS=(
  -H "Accept: */*"
  -H "User-Agent: IBOPlayer/1.4 (Android)"
  -H "Host: $HOST:$PORT"
  -H "Connection: Keep-Alive"
  -H "Accept-Encoding: gzip"
)

BASE_URL="http://$HOST:$PORT/player_api.php"

# API Endpoints
declare -A ENDPOINTS=(
  ["account_info"]="username=$USER&password=$PASS"
  ["live_categories"]="username=$USER&password=$PASS&action=get_live_categories"
  ["vod_categories"]="username=$USER&password=$PASS&action=get_vod_categories"
  ["series_categories"]="username=$USER&password=$PASS&action=get_series_categories"
  ["live_streams"]="username=$USER&password=$PASS&action=get_live_streams"
  ["vod_streams"]="username=$USER&password=$PASS&action=get_vod_streams"
  ["series"]="username=$USER&password=$PASS&action=get_series"
)

echo "=== Fetching API Responses ==="
for name in "${!ENDPOINTS[@]}"; do
  url="$BASE_URL?${ENDPOINTS[$name]}"
  outfile="$OUT_DIR/${name}.json"
  
  echo -n "Fetching $name... "
  
  curl -s --max-time 120 "${HEADERS[@]}" --compressed "$url" -o "$outfile"
  
  if [ -f "$outfile" ]; then
    size=$(wc -c < "$outfile")
    # Check if JSON
    if head -c 1 "$outfile" | grep -q '[{\[]'; then
      echo "OK ($size bytes)"
    else
      echo "ERROR - not JSON ($size bytes)"
      head -c 200 "$outfile"
      echo ""
    fi
  else
    echo "FAILED"
  fi
done

# Fetch one VOD category detail as sample
echo ""
echo "=== Fetching Sample VOD Category (384) ==="
curl -s --max-time 60 "${HEADERS[@]}" --compressed \
  "$BASE_URL?username=$USER&password=$PASS&action=get_vod_streams&category_id=384" \
  -o "$OUT_DIR/vod_streams_cat_384.json"
echo "Saved: vod_streams_cat_384.json ($(wc -c < "$OUT_DIR/vod_streams_cat_384.json") bytes)"

# Fetch one series info as sample
echo ""
echo "=== Fetching Sample Series Info ==="
# First get series list to find an ID
SERIES_ID=$(cat "$OUT_DIR/series.json" 2>/dev/null | grep -o '"series_id":[0-9]*' | head -1 | cut -d: -f2)
if [ -n "$SERIES_ID" ]; then
  curl -s --max-time 60 "${HEADERS[@]}" --compressed \
    "$BASE_URL?username=$USER&password=$PASS&action=get_series_info&series_id=$SERIES_ID" \
    -o "$OUT_DIR/series_info_sample.json"
  echo "Saved: series_info_sample.json for series_id=$SERIES_ID"
fi

echo ""
echo "=== Summary ==="
ls -la "$OUT_DIR"/*.json 2>/dev/null | awk '{print $9, $5}'
echo ""
echo "Done! Upload the xtream-responses folder to the codespace."
