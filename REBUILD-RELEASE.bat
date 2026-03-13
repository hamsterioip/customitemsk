@echo off
chcp 65001 >nul
title Rebuild Release v1.0.0

echo ╔══════════════════════════════════════════════════════════════╗
echo ║         Rebuild Release with JAR Fix                         ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

cd /d "%~dp0"

echo 🔧 Step 1: Building locally to verify JAR is created...
.\gradlew.bat clean build

if %errorlevel% neq 0 (
    echo ❌ Build failed!
    pause
    exit /b 1
)

echo.
echo 📦 Step 2: Checking for JAR file...
dir build\libs\*.jar /b

if %errorlevel% neq 0 (
    echo ❌ No JAR files found!
    pause
    exit /b 1
)

echo.
echo ✅ JAR file found!
echo.
echo 📤 Step 3: Pushing fixed workflow to GitHub...
git add .github/workflows/publish.yml
git commit -m "Fix JAR upload - rename to customitemsk.jar"
git push origin main
if %errorlevel% neq 0 (
    echo ❌ Push failed!
    pause
    exit /b 1
)

echo.
echo 🗑️ Step 4: Deleting old broken release...
git push --delete origin v1.0.0 2>nul
git tag -d v1.0.0 2>nul
echo ✅ Old release deleted (if it existed)

echo.
echo 🏷️ Step 5: Creating new release tag...
git tag v1.0.0
git push origin v1.0.0
if %errorlevel% neq 0 (
    echo ❌ Failed to push tag!
    pause
    exit /b 1
)

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                   ✅ REBUILD INITIATED!                      ║
echo ╠══════════════════════════════════════════════════════════════╣
echo ║                                                              ║
echo ║  Check progress at:                                          ║
echo ║  https://github.com/hamsterioip/customitemsk/actions        ║
echo ║                                                              ║
echo ║  Wait for the green checkmark, then check:                   ║
echo ║  https://github.com/hamsterioip/customitemsk/releases       ║
echo ║                                                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
start https://github.com/hamsterioip/customitemsk/actions
pause
