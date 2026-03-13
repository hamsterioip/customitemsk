@echo off
chcp 65001 >nul
title Push Credit Update

echo ╔══════════════════════════════════════════════════════════════╗
echo ║         Push "Made by Koon" Credit Update                    ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

cd /d "%~dp0"

echo 📤 Step 1: Committing credit update...
git add .
git commit -m "Add 'Made by Koon' credit to title screen"

echo.
echo 🚀 Step 2: Pushing to GitHub...
git push origin main

echo.
echo 🏷️ Step 3: Creating release v1.0.2...
git tag v1.0.2
git push origin v1.0.2

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                   ✅ UPDATE PUSHED!                          ║
echo ╠══════════════════════════════════════════════════════════════╣
echo ║                                                              ║
echo ║  Check Actions: https://github.com/hamsterioip/customitemsk ║
echo ║                                                              ║
echo ║  When you restart Minecraft, you'll see:                    ║
echo ║  "§e§lMade by §c§lKoon §7| §b§lCustomItemsK"                  ║
echo ║  at the bottom of the title screen!                         ║
echo ║                                                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
pause
