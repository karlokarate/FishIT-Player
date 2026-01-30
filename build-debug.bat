@echo off
echo ========================================
echo Building FishIT-Player (PERFORMANCE TEST MODE)
echo ========================================
echo.
echo DEBUG TOOLS DISABLED FOR ACCURATE BENCHMARKS:
echo - LeakCanary: OFF
echo - Chucker: OFF
echo.
echo This ensures clean performance measurements without debug overhead.
echo.
cd /d C:\Users\admin\StudioProjects\FishIT-Player
gradlew.bat clean :app-v2:assembleDebug -PincludeLeakCanary=false -PincludeChucker=false --console=plain
echo.
echo Build complete!
echo APK location: app-v2\build\outputs\apk\debug\app-v2-debug.apk
echo.
echo IMPORTANT: This APK has NO debug tools for performance testing!
pause
