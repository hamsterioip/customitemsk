@echo off
chcp 65001 >nul
title Push Enhanced Changeling

echo ╔══════════════════════════════════════════════════════════════╗
echo ║     Push Enhanced Changeling Update                          ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

cd /d "%~dp0"

echo 🔧 Step 1: Pulling latest changes...
git pull origin main

echo.
echo 🔨 Step 2: Committing enhanced Changeling...
git add .
git commit -m "Enhanced Changeling with 10 disguises, cloning, shadow step, taming"

echo.
echo 🚀 Step 3: Pushing to GitHub...
git push origin main

echo.
echo 🏷️ Step 4: Creating release v1.1.0...
git tag v1.1.0
git push origin v1.1.0

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                   ✅ v1.1.0 RELEASED!                        ║
echo ╠══════════════════════════════════════════════════════════════╣
echo ║  Enhanced Changeling Features:                               ║
echo ║  • 10 different disguises (Cow, Pig, Sheep, Villager, etc.)  ║
echo ║  • Mimics ambient sounds of disguised mob                    ║
echo ║  • Clones itself at low health                               ║
echo ║  • Shadow step teleport when damaged                         ║
echo ║  • Can be tamed with golden apples                           ║
echo ║  • Stronger at night                                         ║
echo ║  • True form reveal with shadow tentacles                    ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
pause
