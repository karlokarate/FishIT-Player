# ⚡ JDK 21 Quick Install für FishIT-Player
# WICHTIG: Als Administrator ausführen!

Write-Host "`n=== JDK 21 Quick Install ===" -ForegroundColor Cyan
Write-Host ""

# Prüfe Admin-Rechte
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "✗ FEHLER: Dieses Script muss als Administrator ausgeführt werden!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Rechtsklick auf PowerShell → 'Als Administrator ausführen'" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

Write-Host "✓ Administrator-Rechte bestätigt" -ForegroundColor Green
Write-Host ""

# Prüfe ob JDK 21 bereits installiert ist
Write-Host "[1/4] Prüfe vorhandene JDK-Installation..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-Object -First 1 | Out-String
    if ($javaVersion -match "21\.") {
        Write-Host "✓ JDK 21 ist bereits installiert!" -ForegroundColor Green
        Write-Host $javaVersion -ForegroundColor Gray
        Write-Host ""
        Write-Host "Keine weitere Aktion nötig." -ForegroundColor Green
        exit 0
    } else {
        Write-Host "! Java gefunden, aber nicht Version 21:" -ForegroundColor Yellow
        Write-Host $javaVersion -ForegroundColor Gray
    }
} catch {
    Write-Host "! Kein Java im PATH gefunden" -ForegroundColor Yellow
}

Write-Host ""

# Installiere JDK 21 via winget
Write-Host "[2/4] Installiere JDK 21 (Eclipse Temurin) via winget..." -ForegroundColor Yellow
Write-Host "Dies kann 2-5 Minuten dauern..." -ForegroundColor Gray
Write-Host ""

try {
    winget install EclipseAdoptium.Temurin.21.JDK --accept-package-agreements --accept-source-agreements
    Write-Host ""
    Write-Host "✓ JDK 21 Installation abgeschlossen" -ForegroundColor Green
} catch {
    Write-Host "✗ Installation fehlgeschlagen!" -ForegroundColor Red
    Write-Host "Fehler: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Finde installierten JDK-Pfad
Write-Host "[3/4] Suche JDK 21 Installation..." -ForegroundColor Yellow
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
    Write-Host "✗ JDK 21 Installation nicht gefunden!" -ForegroundColor Red
    Write-Host "Bitte manuell installieren: https://adoptium.net/temurin/releases/?version=21" -ForegroundColor Yellow
    exit 1
}

Write-Host "✓ Gefunden: $jdkPath" -ForegroundColor Green
Write-Host ""

# Setze JAVA_HOME und PATH
Write-Host "[4/4] Setze JAVA_HOME und PATH (permanent)..." -ForegroundColor Yellow

try {
    # JAVA_HOME setzen
    [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkPath, "Machine")
    Write-Host "✓ JAVA_HOME = $jdkPath" -ForegroundColor Green

    # PATH aktualisieren (nur wenn noch nicht vorhanden)
    $currentPath = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $jdkBin = "$jdkPath\bin"

    if ($currentPath -notlike "*$jdkBin*") {
        $newPath = $currentPath + ";$jdkBin"
        [Environment]::SetEnvironmentVariable("Path", $newPath, "Machine")
        Write-Host "✓ PATH aktualisiert (JDK bin hinzugefügt)" -ForegroundColor Green
    } else {
        Write-Host "✓ PATH bereits korrekt" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Fehler beim Setzen der Umgebungsvariablen!" -ForegroundColor Red
    Write-Host "Fehler: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Installation Abgeschlossen ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Nächste Schritte:" -ForegroundColor Yellow
Write-Host "1. Öffne eine NEUE PowerShell (nicht Admin nötig)" -ForegroundColor White
Write-Host "2. Teste: java -version" -ForegroundColor White
Write-Host "3. Starte Android Studio neu" -ForegroundColor White
Write-Host "4. In Android Studio:" -ForegroundColor White
Write-Host "   → File → Settings → Build Tools → Gradle → Gradle JDK: 21" -ForegroundColor Gray
Write-Host "   → File → Sync Project with Gradle Files" -ForegroundColor Gray
Write-Host ""
Write-Host "✓ JDK 21 ist jetzt permanent installiert und konfiguriert!" -ForegroundColor Green
Write-Host ""
