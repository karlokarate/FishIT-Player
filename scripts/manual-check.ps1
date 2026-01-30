# =============================================================================
# FishIT-Player - Manuelle Umgebungsprüfung
# =============================================================================
# Dieses Script kann Zeile für Zeile im Terminal ausgeführt werden
# Öffne Terminal in Android Studio: View → Tool Windows → Terminal (Alt+F12)
# =============================================================================

Write-Host "`n=== FishIT-Player Environment Check ===" -ForegroundColor Cyan
Write-Host "Führe jeden Befehl einzeln aus und prüfe die Ausgabe`n" -ForegroundColor Yellow

# =============================================================================
# 1. JAVA VERSION
# =============================================================================
Write-Host "[1/6] Java Version prüfen:" -ForegroundColor Cyan
Write-Host "Befehl: java -version" -ForegroundColor Gray
Write-Host "Erwartetes Ergebnis: openjdk version `"21.x.x`"`n" -ForegroundColor Yellow

# Zum Ausführen diese Zeile kopieren:
# java -version

# =============================================================================
# 2. GRADLE VERSION
# =============================================================================
Write-Host "`n[2/6] Gradle Version prüfen:" -ForegroundColor Cyan
Write-Host "Befehl: .\gradlew.bat --version" -ForegroundColor Gray
Write-Host "Erwartetes Ergebnis: Gradle 8.13`n" -ForegroundColor Yellow

# Zum Ausführen diese Zeile kopieren:
# .\gradlew.bat --version

# =============================================================================
# 3. ANDROID SDK
# =============================================================================
Write-Host "`n[3/6] Android SDK Pfad prüfen:" -ForegroundColor Cyan
Write-Host "Befehl: Get-Content local.properties | Select-String 'sdk.dir'" -ForegroundColor Gray
Write-Host "Erwartetes Ergebnis: sdk.dir=C:\\Users\\admin\\AppData\\Local\\Android\\Sdk`n" -ForegroundColor Yellow

# Zum Ausführen diese Zeile kopieren:
# Get-Content local.properties | Select-String "sdk.dir"

# =============================================================================
# 4. IDE JDK KONFIGURATION
# =============================================================================
Write-Host "`n[4/6] IDE JDK Konfiguration prüfen:" -ForegroundColor Cyan
Write-Host "Befehl: Get-Content .idea\misc.xml | Select-String 'JDK'" -ForegroundColor Gray
Write-Host "Erwartetes Ergebnis: languageLevel=`"JDK_21`" project-jdk-name=`"21`"`n" -ForegroundColor Yellow

# Zum Ausführen diese Zeile kopieren:
# Get-Content .idea\misc.xml | Select-String "JDK"

# =============================================================================
# 5. GRADLE JDK KONFIGURATION
# =============================================================================
Write-Host "`n[5/6] Gradle JDK Konfiguration prüfen:" -ForegroundColor Cyan
Write-Host "Befehl: Get-Content .idea\gradle.xml | Select-String 'gradleJvm'" -ForegroundColor Gray
Write-Host "Erwartetes Ergebnis: gradleJvm`" value=`"21`"`n" -ForegroundColor Yellow

# Zum Ausführen diese Zeile kopieren:
# Get-Content .idea\gradle.xml | Select-String "gradleJvm"

# =============================================================================
# 6. MCP KONFIGURATION
# =============================================================================
Write-Host "`n[6/6] MCP Konfiguration prüfen:" -ForegroundColor Cyan
Write-Host "Befehl: Test-Path `$env:LOCALAPPDATA\github-copilot\intellij\mcp.json" -ForegroundColor Gray
Write-Host "Erwartetes Ergebnis: True`n" -ForegroundColor Yellow

# Zum Ausführen diese Zeile kopieren:
# Test-Path "$env:LOCALAPPDATA\github-copilot\intellij\mcp.json"

# Falls True, dann Inhalt anzeigen:
# Get-Content "$env:LOCALAPPDATA\github-copilot\intellij\mcp.json"

Write-Host "`n==============================================================================" -ForegroundColor Cyan
Write-Host "NÄCHSTE SCHRITTE:" -ForegroundColor Yellow
Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "1. Führe jeden Befehl einzeln aus (markiere und drücke Enter)" -ForegroundColor White
Write-Host "2. Prüfe ob die Ergebnisse mit den Erwartungen übereinstimmen" -ForegroundColor White
Write-Host "3. Falls JDK nicht 21 ist:" -ForegroundColor White
Write-Host "   → File → Settings → Build Tools → Gradle → Gradle JDK: 21 auswählen" -ForegroundColor Gray
Write-Host "   → File → Project Structure → Project → SDK: 21 auswählen" -ForegroundColor Gray
Write-Host "4. Nach Änderungen: File → Sync Project with Gradle Files" -ForegroundColor White
Write-Host "5. IDE neu starten für MCP-Konfiguration" -ForegroundColor White
Write-Host "==============================================================================" -ForegroundColor Cyan
