# Build FishIT Pipeline MCP Server
# Creates: tools/mcp-server/build/libs/mcp-server-1.0.0-all.jar

Write-Host "🔨 Building FishIT Pipeline MCP Server..." -ForegroundColor Cyan

# Build the fat JAR
& .\gradlew :tools:mcp-server:fatJar

if ($LASTEXITCODE -eq 0) {
    $jarPath = "tools\mcp-server\build\libs\mcp-server-1.0.0-all.jar"
    if (Test-Path $jarPath) {
        Write-Host "✅ Build erfolgreich: $jarPath" -ForegroundColor Green
        Write-Host ""
        Write-Host "📝 Nächste Schritte:" -ForegroundColor Yellow
        Write-Host "   1. In .vscode\mcp.json die Zeile 'disabled: true' bei 'fishit-pipeline' entfernen"
        Write-Host "   2. Android Studio / VS Code neu laden"
        Write-Host "   3. GitHub Copilot Chat sollte jetzt FishIT Pipeline Tools anzeigen"
    } else {
        Write-Host "❌ JAR wurde nicht erstellt: $jarPath" -ForegroundColor Red
    }
} else {
    Write-Host "❌ Build fehlgeschlagen (Exit Code: $LASTEXITCODE)" -ForegroundColor Red
}
