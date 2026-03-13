@echo off
chcp 65001 >nul
title CustomItemsK Auto Builder
setlocal enabledelayedexpansion

echo ╔══════════════════════════════════════════════════════════════╗
echo ║            CustomItemsK Auto Builder (hamsterioip)           ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

REM Check if version argument is provided
if "%~1"=="" (
    echo Usage: build-and-release.bat [version]
    echo Example: build-and-release.bat v1.2.0
    echo.
    pause
    exit /b 1
)

set VERSION=%~1

REM Validate version format (should start with v)
if not "%VERSION:~0,1%"=="v" (
    echo ⚠ Warning: Version should start with 'v' (e.g., v1.2.0)
    set /p CONTINUE="Continue anyway? (y/n): "
    if /i not "!CONTINUE!"=="y" exit /b 1
)

echo 📦 Building version: %VERSION%
echo.

REM Check if git is available
where git >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ Error: Git is not installed or not in PATH
    pause
    exit /b 1
)

REM Check if we're in a git repository
git rev-parse --git-dir >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ Error: Not a git repository
    pause
    exit /b 1
)

echo 🔄 Step 1/5: Pulling latest changes from GitHub...
git pull origin main
if %errorlevel% neq 0 (
    echo ⚠ Warning: Could not pull latest changes
    set /p CONTINUE="Continue anyway? (y/n): "
    if /i not "!CONTINUE!"=="y" exit /b 1
)
echo ✅ Done
echo.

echo 🔨 Step 2/5: Building mod with Gradle...
.\gradlew.bat clean build
if %errorlevel% neq 0 (
    echo ❌ Error: Build failed!
    pause
    exit /b 1
)
echo ✅ Build successful
echo.

echo 📋 Step 3/5: Committing changes...
git add .
set COMMIT_MSG=Release %VERSION% - Auto build
git commit -m "%COMMIT_MSG%"
if %errorlevel% neq 0 (
    echo ⚠ No changes to commit or commit failed
)
echo ✅ Committed
echo.

echo 🚀 Step 4/5: Pushing to GitHub...
git push origin main
if %errorlevel% neq 0 (
    echo ❌ Error: Failed to push to GitHub
    pause
    exit /b 1
)
echo ✅ Pushed
echo.

echo 🏷️ Step 5/5: Creating release tag %VERSION%...
git tag %VERSION%
git push origin %VERSION%
if %errorlevel% neq 0 (
    echo ❌ Error: Failed to push tag
    pause
    exit /b 1
)
echo ✅ Tag pushed
echo.

echo ╔══════════════════════════════════════════════════════════════╗
echo ║                    ✅ BUILD COMPLETE!                        ║
echo ╠══════════════════════════════════════════════════════════════╣
echo ║  Version: %VERSION%                                          ║
echo ║  GitHub:  https://github.com/hamsterioip/customitemsk       ║
echo ║  Actions: https://github.com/hamsterioip/customitemsk/actions ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
echo 📌 What's happening now:
echo    1. GitHub Actions is building your mod
echo    2. It will be published to Modrinth
    3. A release will be created on GitHub
echo    4. version.json will be updated automatically
echo.
echo 🎉 Players will get this update automatically when they restart Minecraft!
echo.
pause
