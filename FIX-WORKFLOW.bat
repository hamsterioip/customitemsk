@echo off
chcp 65001 >nul
title Fix Workflow & Rebuild v1.0.2

echo ╔══════════════════════════════════════════════════════════════╗
echo ║         Fix Workflow & Rebuild v1.0.2                        ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

cd /d "%~dp0"

echo 🔧 Step 1: Pushing fixed workflow...
git add .github/workflows/publish.yml
git commit -m "Fix workflow to checkout main branch"
git push origin main

echo.
echo 🗑️ Step 2: Deleting broken v1.0.2 tag...
git push --delete origin v1.0.2 2>nul
git tag -d v1.0.2 2>nul

echo.
echo 🏷️ Step 3: Recreating v1.0.2 tag...
git tag v1.0.2
git push origin v1.0.2

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                   ✅ REBUILD STARTED!                        ║
echo ╠══════════════════════════════════════════════════════════════╣
echo ║  Check: https://github.com/hamsterioip/customitemsk/actions ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
pause
