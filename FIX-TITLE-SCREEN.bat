@echo off
chcp 65001 >nul
title Fix Title Screen - Release v1.0.4

echo ╔══════════════════════════════════════════════════════════════╗
echo ║     Fix Title Screen Text (Made by Koon + Version)          ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

cd /d "%~dp0"

echo 🔧 Step 1: Pulling latest changes...
git pull origin main

echo.
echo 🔨 Step 2: Committing fix...
git add .
git commit -m "Fix TitleScreenMixin - simplify injection"

echo.
echo 🚀 Step 3: Pushing to GitHub...
git push origin main

echo.
echo 🗑️ Step 4: Cleaning up old tags...
git push --delete origin v1.0.4 2>nul
git tag -d v1.0.4 2>nul

echo.
echo 🏷️ Step 5: Creating release v1.0.4...
git tag v1.0.4
git push origin v1.0.4

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                   ✅ v1.0.4 RELEASED!                        ║
echo ╠══════════════════════════════════════════════════════════════╣
echo ║                                                              ║
echo ║  When you restart Minecraft, you should see:                ║
echo ║  • "Made by Koon | CustomItemsK" at bottom                  ║
echo ║  • "Version: v1.0.4" at top right                           ║
echo ║                                                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
pause
