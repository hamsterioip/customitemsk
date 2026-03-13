@echo off
chcp 65001 >nul
title Push CustomItemsK to GitHub

echo ╔══════════════════════════════════════════════════════════════╗
echo ║     Push CustomItemsK to GitHub (hamsterioip)               ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

cd /d "%~dp0"

echo 📤 Step 1: Adding all files...
git add .

echo.
echo 💾 Step 2: Committing changes...
git commit -m "Add auto-update system, teleport commands, new entities, and build scripts"
if %errorlevel% neq 0 (
    echo ⚠️  No changes to commit or commit failed
    pause
    exit /b 1
)

echo.
echo 🚀 Step 3: Pushing to GitHub...
git push origin main
if %errorlevel% neq 0 (
    echo ❌ Push failed!
    pause
    exit /b 1
)

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                    ✅ PUSH SUCCESSFUL!                       ║
echo ╠══════════════════════════════════════════════════════════════╣
echo ║  Your files are now on GitHub!                              ║
echo ║                                                              ║
echo ║  Next steps:                                                 ║
echo ║  1. Go to: https://github.com/hamsterioip/customitemsk      ║
echo ║  2. Check Actions tab is now visible                         ║
echo ║  3. Run: build-and-release.bat v1.0.0                       ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
pause
