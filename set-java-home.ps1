# Quick Fix: Setze JAVA_HOME für bereits installiertes JDK 21
# KEINE Admin-Rechte nötig (User-Level)

Write-Host "`n=== JDK 21 Umgebungsvariablen setzen ===" -ForegroundColor Cyan
Write-Host ""

# Finde JDK 21
Write-Host "[1/3] Suche JDK 21 Installation..." -ForegroundColor Yellow
$jdkPath = $null
$possiblePaths = @(
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java"
)

foreach ($basePath in $possiblePaths) {
    if (Test-Path $basePath) {
        $jdkDir = Get-ChildItem $basePath -Filter "jdk-21*" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($jdkDir) {
            $jdkPath = $jdkDir.FullName
            break
        }
    }
}

if (-not $jdkPath) {
    Write-Host "✗ JDK 21 nicht gefunden!" -ForegroundColor Red
    Write-Host "Führe zuerst aus: winget install EclipseAdoptium.Temurin.21.JDK" -ForegroundColor Yellow
    exit 1
}

Write-Host "✓ Gefunden: $jdkPath" -ForegroundColor Green
Write-Host ""

# Setze JAVA_HOME (User-Level)
Write-Host "[2/3] Setze JAVA_HOME (User-Level, keine Admin-Rechte nötig)..." -ForegroundColor Yellow
try {
    [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkPath, "User")
    Write-Host "✓ JAVA_HOME = $jdkPath" -ForegroundColor Green
} catch {
    Write-Host "✗ Fehler beim Setzen von JAVA_HOME!" -ForegroundColor Red
    Write-Host "Fehler: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Aktualisiere PATH (User-Level)
Write-Host "[3/3] Aktualisiere PATH (User-Level)..." -ForegroundColor Yellow
try {
    $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
    if (-not $currentPath) {
        $currentPath = ""
    }

    $jdkBin = "$jdkPath\bin"

    if ($currentPath -notlike "*$jdkBin*") {
        if ($currentPath -and -not $currentPath.EndsWith(";")) {
            $currentPath += ";"
        }
        $newPath = $currentPath + $jdkBin
        [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
        Write-Host "✓ PATH aktualisiert (JDK bin hinzugefügt)" -ForegroundColor Green
    } else {
        Write-Host "✓ PATH bereits korrekt" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Fehler beim Aktualisieren von PATH!" -ForegroundColor Red
    Write-Host "Fehler: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Erfolgreich! ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "✓ JAVA_HOME gesetzt: $jdkPath" -ForegroundColor Green
Write-Host "✓ PATH aktualisiert" -ForegroundColor Green
Write-Host ""
Write-Host "⚠️  WICHTIG: Schließe ALLE PowerShell-Fenster und öffne ein neues!" -ForegroundColor Yellow
Write-Host ""
Write-Host "Dann teste:" -ForegroundColor Cyan
Write-Host "  java -version" -ForegroundColor White
Write-Host "  echo `$env:JAVA_HOME" -ForegroundColor White
Write-Host ""
Write-Host "Danach in Android Studio:" -ForegroundColor Cyan
Write-Host "  1. IDE neu starten" -ForegroundColor White
Write-Host "  2. File → Settings → Build Tools → Gradle → Gradle JDK: 21" -ForegroundColor White
Write-Host "  3. File → Sync Project with Gradle Files" -ForegroundColor White
Write-Host ""
