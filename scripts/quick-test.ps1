# Quick Environment Test
# Simplified version for manual execution

Write-Host "=== Quick Environment Test ===" -ForegroundColor Cyan
Write-Host ""

# Test 1: Java Version
Write-Host "Testing Java..." -ForegroundColor Yellow
java -version

Write-Host ""
Write-Host "---" -ForegroundColor Gray
Write-Host ""

# Test 2: Gradle Version
Write-Host "Testing Gradle..." -ForegroundColor Yellow
.\gradlew.bat --version

Write-Host ""
Write-Host "---" -ForegroundColor Gray
Write-Host ""

# Test 3: Android SDK
Write-Host "Testing Android SDK..." -ForegroundColor Yellow
if (Test-Path "local.properties") {
    Write-Host "local.properties found:" -ForegroundColor Green
    Get-Content local.properties | Where-Object { $_ -match "sdk.dir" }
} else {
    Write-Host "local.properties NOT found!" -ForegroundColor Red
}

Write-Host ""
Write-Host "---" -ForegroundColor Gray
Write-Host ""

# Test 4: MCP Configuration
Write-Host "Testing MCP Configuration..." -ForegroundColor Yellow
$mcpPath = "$env:LOCALAPPDATA\github-copilot\intellij\mcp.json"
if (Test-Path $mcpPath) {
    Write-Host "MCP config found:" -ForegroundColor Green
    Write-Host $mcpPath -ForegroundColor Gray
    $mcpContent = Get-Content $mcpPath -Raw | ConvertFrom-Json
    Write-Host "Configured servers: $($mcpContent.servers.PSObject.Properties.Count)" -ForegroundColor Cyan
    foreach ($server in $mcpContent.servers.PSObject.Properties) {
        Write-Host "  - $($server.Name)" -ForegroundColor White
    }
} else {
    Write-Host "MCP config NOT found at: $mcpPath" -ForegroundColor Red
}

Write-Host ""
Write-Host "---" -ForegroundColor Gray
Write-Host ""

# Test 5: IDE Configuration
Write-Host "Testing IDE Configuration..." -ForegroundColor Yellow
if (Test-Path ".idea\misc.xml") {
    $miscXml = Get-Content ".idea\misc.xml" -Raw
    if ($miscXml -match 'languageLevel="JDK_21"') {
        Write-Host "IDE JDK: 21 (Correct)" -ForegroundColor Green
    } else {
        Write-Host "IDE JDK: Not set to 21" -ForegroundColor Yellow
    }
} else {
    Write-Host ".idea\misc.xml NOT found" -ForegroundColor Red
}

if (Test-Path ".idea\gradle.xml") {
    $gradleXml = Get-Content ".idea\gradle.xml" -Raw
    if ($gradleXml -match 'gradleJvm" value="21"') {
        Write-Host "Gradle JDK: 21 (Correct)" -ForegroundColor Green
    } else {
        Write-Host "Gradle JDK: Not set to 21" -ForegroundColor Yellow
    }
} else {
    Write-Host ".idea\gradle.xml NOT found" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan
