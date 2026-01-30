# Fix Java Home - Windows Version
# Prüft und konfiguriert JDK 21 für FishIT-Player auf Windows

Write-Host "`n=== FishIT-Player Java Setup (Windows) ===" -ForegroundColor Cyan
Write-Host ""

# 1. Prüfe aktuelle Java Version
Write-Host "[1/4] Prüfe installierte Java Version..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-Object -First 1 | Out-String
    Write-Host "Gefunden: $javaVersion" -ForegroundColor Gray

    if ($javaVersion -match "21\.") {
        Write-Host "✓ JDK 21 ist bereits installiert!" -ForegroundColor Green
    } else {
        Write-Host "! JDK ist nicht Version 21" -ForegroundColor Yellow
        Write-Host "Installiere JDK 21 mit einer der folgenden Methoden:" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Methode 1 (Empfohlen): Android Studio" -ForegroundColor White
        Write-Host "  → File → Settings → Build Tools → Gradle" -ForegroundColor Gray
        Write-Host "  → Gradle JDK → Download JDK..." -ForegroundColor Gray
        Write-Host "  → Version: 21, Vendor: Eclipse Temurin" -ForegroundColor Gray
        Write-Host ""
        Write-Host "Methode 2 (winget):" -ForegroundColor White
        Write-Host "  winget install EclipseAdoptium.Temurin.21.JDK" -ForegroundColor Gray
        Write-Host ""
        Write-Host "Methode 3 (Manuell):" -ForegroundColor White
        Write-Host "  https://adoptium.net/temurin/releases/?version=21" -ForegroundColor Gray
    }
} catch {
    Write-Host "✗ Java nicht im PATH gefunden" -ForegroundColor Red
    Write-Host "Bitte installiere JDK 21 über Android Studio oder winget" -ForegroundColor Yellow
}

Write-Host ""

# 2. Prüfe JAVA_HOME
Write-Host "[2/4] Prüfe JAVA_HOME Umgebungsvariable..." -ForegroundColor Yellow
$javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
if (-not $javaHome) {
    $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine")
}

if ($javaHome) {
    Write-Host "JAVA_HOME = $javaHome" -ForegroundColor Gray
    if (Test-Path $javaHome) {
        Write-Host "✓ Pfad existiert" -ForegroundColor Green
    } else {
        Write-Host "✗ Pfad existiert NICHT" -ForegroundColor Red
    }
} else {
    Write-Host "! JAVA_HOME ist nicht gesetzt (optional für Android Studio)" -ForegroundColor Yellow
}

Write-Host ""

# 3. Finde mögliche JDK-Installationen
Write-Host "[3/4] Suche nach JDK-Installationen..." -ForegroundColor Yellow

$possiblePaths = @(
    "$env:ProgramFiles\Eclipse Adoptium",
    "$env:ProgramFiles\Java",
    "$env:ProgramFiles\Android\Android Studio\jbr",
    "$env:LOCALAPPDATA\Programs\Eclipse Adoptium",
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java",
    "C:\Program Files\JetBrains"
)

$foundJdks = @()
foreach ($path in $possiblePaths) {
    if (Test-Path $path) {
        $jdkDirs = Get-ChildItem -Path $path -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match "jdk|java|jbr" }
        foreach ($dir in $jdkDirs) {
            if (Test-Path "$($dir.FullName)\bin\java.exe") {
                $foundJdks += $dir.FullName
                Write-Host "  Gefunden: $($dir.FullName)" -ForegroundColor Gray
            }
        }
    }
}

if ($foundJdks.Count -eq 0) {
    Write-Host "  Keine JDK-Installationen gefunden" -ForegroundColor Yellow
} else {
    Write-Host "✓ $($foundJdks.Count) JDK-Installation(en) gefunden" -ForegroundColor Green
}

Write-Host ""

# 4. Android Studio Gradle JDK Konfiguration prüfen
Write-Host "[4/4] Prüfe Android Studio Konfiguration..." -ForegroundColor Yellow

if (Test-Path ".idea\gradle.xml") {
    $gradleXml = Get-Content ".idea\gradle.xml" -Raw
    if ($gradleXml -match 'gradleJvm" value="([^"]+)"') {
        $currentGradleJdk = $matches[1]
        Write-Host "Aktuelles Gradle JDK: $currentGradleJdk" -ForegroundColor Gray

        if ($currentGradleJdk -eq "21" -or $currentGradleJdk -match "21") {
            Write-Host "✓ Gradle JDK ist auf 21 gesetzt" -ForegroundColor Green
        } else {
            Write-Host "! Gradle JDK ist NICHT auf 21 gesetzt" -ForegroundColor Yellow
            Write-Host "  → Ändere in: File → Settings → Build Tools → Gradle → Gradle JDK: 21" -ForegroundColor Cyan
        }
    }
} else {
    Write-Host "! .idea\gradle.xml nicht gefunden (Projekt noch nicht geöffnet?)" -ForegroundColor Yellow
}

Write-Host ""

# Zusammenfassung
Write-Host "=== Zusammenfassung ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Nächste Schritte in Android Studio:" -ForegroundColor White
Write-Host ""
Write-Host "1. Öffne Settings (Ctrl + Alt + S)" -ForegroundColor Yellow
Write-Host "2. Gehe zu: Build, Execution, Deployment → Build Tools → Gradle" -ForegroundColor Yellow
Write-Host "3. Bei 'Gradle JDK': Wähle '21' aus" -ForegroundColor Yellow
Write-Host "   → Falls nicht vorhanden: Klicke 'Download JDK...' → Version 21" -ForegroundColor Gray
Write-Host "4. Klicke Apply → OK" -ForegroundColor Yellow
Write-Host "5. File → Sync Project with Gradle Files" -ForegroundColor Yellow
Write-Host ""
Write-Host "Falls JDK 21 fehlt, installiere über winget:" -ForegroundColor White
Write-Host "  winget install EclipseAdoptium.Temurin.21.JDK" -ForegroundColor Cyan
Write-Host ""
Write-Host "Dann IDE neu starten und erneut syncen." -ForegroundColor White
Write-Host ""
