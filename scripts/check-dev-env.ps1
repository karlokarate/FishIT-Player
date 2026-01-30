# FishIT-Player Development Environment Check
# Run: .\scripts\check-dev-env.ps1

Write-Host "=== FishIT-Player Development Environment Check ===" -ForegroundColor Cyan
Write-Host ""

$allOk = $true

# 1. JDK Check
Write-Host "[1/7] Checking JDK..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-Object -First 1
    if ($javaVersion -match "21\.") {
        Write-Host "  ✓ JDK 21 found: $javaVersion" -ForegroundColor Green
    } else {
        Write-Host "  ✗ JDK 21 not found. Current: $javaVersion" -ForegroundColor Red
        Write-Host "    Install from: https://adoptium.net/temurin/releases/?version=21" -ForegroundColor Yellow
        $allOk = $false
    }
} catch {
    Write-Host "  ✗ Java not found in PATH" -ForegroundColor Red
    $allOk = $false
}

# 2. Android SDK Check
Write-Host "[2/7] Checking Android SDK..." -ForegroundColor Yellow
if (Test-Path "local.properties") {
    $sdkPath = Get-Content local.properties | Where-Object { $_ -match "sdk.dir" }
    if ($sdkPath) {
        $sdkDir = ($sdkPath -split "=")[1].Trim().Replace("\\", "\")
        if (Test-Path $sdkDir) {
            Write-Host "  ✓ Android SDK found: $sdkDir" -ForegroundColor Green
        } else {
            Write-Host "  ✗ Android SDK path not found: $sdkDir" -ForegroundColor Red
            $allOk = $false
        }
    }
} else {
    Write-Host "  ✗ local.properties not found" -ForegroundColor Red
    $allOk = $false
}

# 3. Gradle Check
Write-Host "[3/7] Checking Gradle..." -ForegroundColor Yellow
if (Test-Path "gradlew.bat") {
    try {
        $gradleVersion = .\gradlew.bat --version 2>&1 | Select-String "Gradle" | Select-Object -First 1
        Write-Host "  ✓ Gradle Wrapper: $gradleVersion" -ForegroundColor Green
    } catch {
        Write-Host "  ✗ Gradle Wrapper failed to execute" -ForegroundColor Red
        $allOk = $false
    }
} else {
    Write-Host "  ✗ gradlew.bat not found" -ForegroundColor Red
    $allOk = $false
}

# 4. Node.js Check (for MCP)
Write-Host "[4/7] Checking Node.js (for MCP)..." -ForegroundColor Yellow
try {
    $nodeVersion = node -v 2>&1
    if ($nodeVersion -match "v\d+\.") {
        Write-Host "  ✓ Node.js found: $nodeVersion" -ForegroundColor Green
    } else {
        Write-Host "  ! Node.js not found (optional for MCP tools)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ! Node.js not found (optional for MCP tools)" -ForegroundColor Yellow
}

# 5. MCP Server JAR Check
Write-Host "[5/7] Checking MCP Server JAR..." -ForegroundColor Yellow
$mcpJar = "tools\mcp-server\build\libs\mcp-server-1.0.0-all.jar"
if (Test-Path $mcpJar) {
    $jarSize = (Get-Item $mcpJar).Length / 1MB
    Write-Host "  ✓ MCP Server JAR found: $([math]::Round($jarSize, 2)) MB" -ForegroundColor Green
} else {
    Write-Host "  ! MCP Server JAR not built yet" -ForegroundColor Yellow
    Write-Host "    Build with: .\gradlew :tools:mcp-server:fatJar" -ForegroundColor Cyan
}

# 6. MCP Configuration Check
Write-Host "[6/7] Checking MCP Configuration..." -ForegroundColor Yellow
$mcpConfigPath = "$env:LOCALAPPDATA\github-copilot\intellij\mcp.json"
if (Test-Path $mcpConfigPath) {
    $mcpConfig = Get-Content $mcpConfigPath -Raw | ConvertFrom-Json
    if ($mcpConfig.servers.PSObject.Properties.Count -gt 0) {
        Write-Host "  ✓ MCP config found with $($mcpConfig.servers.PSObject.Properties.Count) server(s)" -ForegroundColor Green
        foreach ($server in $mcpConfig.servers.PSObject.Properties) {
            Write-Host "    - $($server.Name)" -ForegroundColor Gray
        }
    } else {
        Write-Host "  ! MCP config exists but no servers configured" -ForegroundColor Yellow
    }
} else {
    Write-Host "  ! MCP config not found at: $mcpConfigPath" -ForegroundColor Yellow
}

# 7. IDE Configuration Check
Write-Host "[7/7] Checking IDE Configuration..." -ForegroundColor Yellow
if (Test-Path ".idea\misc.xml") {
    $miscXml = Get-Content ".idea\misc.xml" -Raw
    if ($miscXml -match 'languageLevel="JDK_21"') {
        Write-Host "  ✓ Project JDK set to 21" -ForegroundColor Green
    } else {
        Write-Host "  ! Project JDK not set to 21" -ForegroundColor Yellow
        Write-Host "    Set in Android Studio: File → Project Structure → SDK Location" -ForegroundColor Cyan
    }
} else {
    Write-Host "  ! .idea\misc.xml not found (IDE not configured)" -ForegroundColor Yellow
}

# Summary
Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Cyan
if ($allOk) {
    Write-Host "✓ All critical components ready!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "  1. Open project in Android Studio" -ForegroundColor White
    Write-Host "  2. Wait for Gradle sync" -ForegroundColor White
    Write-Host "  3. Run: .\gradlew :app-v2:assembleDebug" -ForegroundColor White
} else {
    Write-Host "✗ Some components are missing. See errors above." -ForegroundColor Red
    Write-Host "Full setup guide: docs\dev\LOCAL_SETUP.md" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Optional MCP Server setup:" -ForegroundColor Cyan
Write-Host "  .\gradlew :tools:mcp-server:fatJar" -ForegroundColor White
Write-Host ""

# Environment Variables Check (Optional)
$mcpEnvVars = @(
    "COPILOT_MCP_XTREAM_URL",
    "COPILOT_MCP_XTREAM_USER",
    "COPILOT_MCP_XTREAM_PASS",
    "COPILOT_MCP_TELEGRAM_API_ID",
    "COPILOT_MCP_TELEGRAM_API_HASH"
)

$hasAnyMcpVar = $false
foreach ($varName in $mcpEnvVars) {
    if ([Environment]::GetEnvironmentVariable($varName, "User") -or [Environment]::GetEnvironmentVariable($varName, "Machine")) {
        $hasAnyMcpVar = $true
        break
    }
}

if ($hasAnyMcpVar) {
    Write-Host "MCP Environment Variables:" -ForegroundColor Cyan
    foreach ($varName in $mcpEnvVars) {
        $value = [Environment]::GetEnvironmentVariable($varName, "User")
        if (-not $value) { $value = [Environment]::GetEnvironmentVariable($varName, "Machine") }
        if ($value) {
            $masked = if ($value.Length -gt 8) { $value.Substring(0, 4) + "****" } else { "****" }
            Write-Host "  ✓ $varName = $masked" -ForegroundColor Green
        }
    }
} else {
    Write-Host "MCP Environment Variables: Not configured (optional)" -ForegroundColor Gray
}

Write-Host ""
