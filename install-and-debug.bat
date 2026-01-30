@echo off
echo Installing and launching FishIT-Player...
echo.

echo [1/3] Installing APK...
adb install -r app-v2\build\outputs\apk\debug\app-v2-debug.apk
if errorlevel 1 (
    echo ERROR: Installation failed!
    pause
    exit /b 1
)

echo.
echo [2/3] Starting app...
adb shell am start -n com.fishit.player.v2/.MainActivity

echo.
echo [3/3] Starting Logcat capture...
echo Logcat will be saved to: logcat_24_debug.txt
echo Press Ctrl+C when done (after navigating to HomeScreen and waiting 2-3 minutes)
echo.

adb logcat -c
adb logcat -v time > logcat_24_debug.txt

echo.
echo Logcat saved! Check logcat_24_debug.txt
pause
