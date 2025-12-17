#
# Xtream API Fetcher - PowerShell Version for Windows
#
# Usage: .\fetch_xtream_api.ps1
#

$M3U_LINK = "http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts"

# Extract credentials
$uri = [System.Uri]$M3U_LINK
$query = [System.Web.HttpUtility]::ParseQueryString($uri.Query)

$HOST_NAME = $uri.Host
$PORT = if ($uri.Port -gt 0) { $uri.Port } else { 80 }
$USER = $query["username"]
$PASS = $query["password"]

Write-Host "=== Extracted Credentials ===" -ForegroundColor Cyan
Write-Host "Host: $HOST_NAME"
Write-Host "Port: $PORT"
Write-Host "Username: $USER"
Write-Host "Password: $PASS"
Write-Host ""

# Output directory
$OUT_DIR = ".\xtream-responses"
New-Item -ItemType Directory -Force -Path $OUT_DIR | Out-Null

$BASE_URL = "http://${HOST_NAME}:${PORT}/player_api.php"

# Headers (IBOPlayer style)
$headers = @{
    "Accept" = "*/*"
    "User-Agent" = "IBOPlayer/1.4 (Android)"
    "Connection" = "Keep-Alive"
}

# API Endpoints
$endpoints = @{
    "account_info" = "username=$USER&password=$PASS"
    "live_categories" = "username=$USER&password=$PASS&action=get_live_categories"
    "vod_categories" = "username=$USER&password=$PASS&action=get_vod_categories"
    "series_categories" = "username=$USER&password=$PASS&action=get_series_categories"
    "live_streams" = "username=$USER&password=$PASS&action=get_live_streams"
    "vod_streams" = "username=$USER&password=$PASS&action=get_vod_streams"
    "series" = "username=$USER&password=$PASS&action=get_series"
}

Write-Host "=== Fetching API Responses ===" -ForegroundColor Cyan
foreach ($name in $endpoints.Keys) {
    $url = "$BASE_URL`?$($endpoints[$name])"
    $outfile = "$OUT_DIR\$name.json"
    
    Write-Host "Fetching $name... " -NoNewline
    
    try {
        $response = Invoke-WebRequest -Uri $url -Headers $headers -TimeoutSec 120 -UseBasicParsing
        $response.Content | Out-File -FilePath $outfile -Encoding UTF8
        $size = (Get-Item $outfile).Length
        Write-Host "OK ($size bytes)" -ForegroundColor Green
    }
    catch {
        Write-Host "ERROR: $_" -ForegroundColor Red
    }
}

# Fetch sample VOD category
Write-Host ""
Write-Host "=== Fetching Sample VOD Category (384) ===" -ForegroundColor Cyan
try {
    $url = "$BASE_URL`?username=$USER&password=$PASS&action=get_vod_streams&category_id=384"
    $response = Invoke-WebRequest -Uri $url -Headers $headers -TimeoutSec 60 -UseBasicParsing
    $response.Content | Out-File -FilePath "$OUT_DIR\vod_streams_cat_384.json" -Encoding UTF8
    Write-Host "Saved: vod_streams_cat_384.json" -ForegroundColor Green
}
catch {
    Write-Host "ERROR: $_" -ForegroundColor Red
}

# Fetch sample series info
Write-Host ""
Write-Host "=== Fetching Sample Series Info ===" -ForegroundColor Cyan
$seriesFile = "$OUT_DIR\series.json"
if (Test-Path $seriesFile) {
    $seriesData = Get-Content $seriesFile | ConvertFrom-Json
    if ($seriesData.Count -gt 0) {
        $seriesId = $seriesData[0].series_id
        try {
            $url = "$BASE_URL`?username=$USER&password=$PASS&action=get_series_info&series_id=$seriesId"
            $response = Invoke-WebRequest -Uri $url -Headers $headers -TimeoutSec 60 -UseBasicParsing
            $response.Content | Out-File -FilePath "$OUT_DIR\series_info_sample.json" -Encoding UTF8
            Write-Host "Saved: series_info_sample.json (series_id=$seriesId)" -ForegroundColor Green
        }
        catch {
            Write-Host "ERROR: $_" -ForegroundColor Red
        }
    }
}

Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Cyan
Get-ChildItem "$OUT_DIR\*.json" | ForEach-Object { Write-Host "$($_.Name) - $($_.Length) bytes" }
Write-Host ""
Write-Host "Done! Upload the xtream-responses folder to the codespace." -ForegroundColor Green
