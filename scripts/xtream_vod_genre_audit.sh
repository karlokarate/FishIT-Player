#!/usr/bin/env bash
set -euo pipefail

# Xtream VOD genre audit (KönigTV or any Xtream panel)
#
# Fetches VOD categories and a sample of VOD infos, then maps JSON genres
# to our current buckets (derived strictly from category name).
#
# Usage:
#   BASE="http://host:port" USER="username" PASS="password" \
#   scripts/xtream_vod_genre_audit.sh [sample_count]
#
# Example (KönigTV):
#   BASE="http://konigtv.com:8080" USER="..." PASS="..." scripts/xtream_vod_genre_audit.sh 400
#
# Requirements: curl, jq, awk, sort, uniq

BASE=${BASE:-}
USER=${USER:-}
PASS=${PASS:-}
SAMPLE=${1:-400}

if [[ -z "$BASE" || -z "$USER" || -z "$PASS" ]]; then
  echo "ERROR: set BASE, USER, PASS env vars" >&2
  exit 1
fi

workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

ua="Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Mobile Safari/537.36"
hdrs=( -H "User-Agent: $ua" -H "Accept: application/json, */*" )

echo "# Fetch VOD categories..." >&2
curl -s ${hdrs[@]} "$BASE/player_api.php?action=get_vod_categories&username=$USER&password=$PASS" > "$workdir/vod_cats.json"
if ! jq -e 'type=="array"' "$workdir/vod_cats.json" >/dev/null 2>&1; then
  echo "ERROR: categories call failed; got:" >&2
  head -c 200 "$workdir/vod_cats.json" >&2 || true
  exit 2
fi

echo "# Fetch VOD list (wildcard)..." >&2
curl -s ${hdrs[@]} "$BASE/player_api.php?action=get_vod_streams&category_id=*&username=$USER&password=$PASS" > "$workdir/vod_list.json" || true
if ! jq -e 'type=="array" and length>0' "$workdir/vod_list.json" >/dev/null 2>&1; then
  echo "# Wildcard empty; aggregating per category (capped) ..." >&2
  : > "$workdir/vod_list.jsonl"
  # Cap categories to 50 to keep runtime reasonable
  jq -r '.[].category_id' "$workdir/vod_cats.json" | head -n 50 | while read -r CID; do
    [[ -n "$CID" ]] || CID="*"
    curl -s ${hdrs[@]} "$BASE/player_api.php?action=get_vod_streams&category_id=$CID&username=$USER&password=$PASS" | jq -c '.[]' || true
  done >> "$workdir/vod_list.jsonl"
  printf '[\n' > "$workdir/vod_list.json"
  sed 's/$/,/' "$workdir/vod_list.jsonl" >> "$workdir/vod_list.json"
  printf 'null]\n' >> "$workdir/vod_list.json"
  jq 'map(select(.!=null))' "$workdir/vod_list.json" > "$workdir/_tmp.json" && mv "$workdir/_tmp.json" "$workdir/vod_list.json"
fi

echo "# Sampling $SAMPLE VOD ids for detailed genres..." >&2
jq -r '.[].vod_id' "$workdir/vod_list.json" | head -n "$SAMPLE" > "$workdir/vod_ids.txt"

echo "# Fetching movie_info for sample..." >&2
: > "$workdir/vod_details.tsv"
i=0
while read -r VID; do
  i=$((i+1))
  curl -s ${hdrs[@]} "$BASE/player_api.php?action=get_vod_info&vod_id=$VID&username=$USER&password=$PASS" \
    | jq -r --arg id "$VID" '{id:$id, g:(.movie_data.genre//""), cat:(.movie_data.category_id//""), name:(.movie_data.name//""), cat_name:""}' > "$workdir/_one.json"
  CID=$(jq -r '.cat' "$workdir/_one.json")
  if [[ -n "$CID" && "$CID" != "null" ]]; then
    CATN=$(jq -r --arg c "$CID" 'map(select(.category_id==$c))|.[0].category_name' "$workdir/vod_cats.json")
  else
    CATN=""
  fi
  jq -r --arg cn "$CATN" '.cat_name=$cn | [.id,.g,.cat,.cat_name,.name]|@tsv' "$workdir/_one.json" >> "$workdir/vod_details.tsv"
  # light throttle
  if (( i % 40 == 0 )); then sleep 0.4; fi
done < "$workdir/vod_ids.txt"

echo "# Building mapping JSON-Genre -> Our bucket (category-only)..." >&2
awk -F '\t' 'BEGIN{OFS="\t"}
{
  id=$1; g=$2; cn=tolower($4);
  key="other";
  if (cn ~ /4k|uhd/) key="4k";
  else if (cn ~ /filmreihe|saga|collection|kollektion|collectie/) key="collection";
  else if (cn ~ /show/) key="show";
  else if (cn ~ /abenteuer|avventur|avontuur/) key="adventure";
  else if (cn ~ /drama/) key="drama";
  else if (cn ~ /action|azione/) key="action";
  else if (cn ~ /komö|komed|comedy|commedia/) key="comedy";
  else if (cn ~ /kids|kinder|animation|animazione/) key="kids";
  else if (cn ~ /horror/) key="horror";
  else if (cn ~ /thriller|krimi|crime/) key="thriller";
  else if (cn ~ /doku|docu|dokument|documentary|documentaire/) key="documentary";
  else if (cn ~ /romance|romant|liebesfilm/) key="romance";
  else if (cn ~ /family|familie/) key="family";
  else if (cn ~ /weihnacht|noel|christmas/) key="christmas";
  else if (cn ~ /sci-fi|science fiction|scifi/) key="sci_fi";
  else if (cn ~ /western/) key="western";
  else if (cn ~ /krieg|\bwar\b|guerre/) key="war";
  else if (cn ~ /bollywood/) key="bollywood";
  else if (cn ~ /anime|manga/) key="anime";
  else if (cn ~ /fantasy|fantastico|fantastique/) key="fantasy";
  else if (cn ~ /martial arts|arts martiaux|martial|kung fu/) key="martial_arts";
  else if (cn ~ /classic|classique|klassik|vieux films|klassiker/) key="classic";
  n=split(tolower(g), arr, /[\/,]+/)
  if (n==0 || g=="") { print "(leer)", key; }
  else { for (j=1;j<=n;j++){ t=arr[j]; gsub(/^\s+|\s+$/, "", t); if (length(t)>0) print t, key; } }
}' "$workdir/vod_details.tsv" | sed 's/\t/|/g' | sort | uniq -c | sort -nr > "$workdir/genre_to_bucket.txt"

echo "\nJSON-Genre → Unser Bucket (sample $SAMPLE)"
sed -n '1,120p' "$workdir/genre_to_bucket.txt"

empty=$(awk -F '\t' 'length($2)==0{c++} END{print c+0}' "$workdir/vod_details.tsv")
echo "\nEinträge ohne JSON-Genre im Sample: $empty"

