@echo off
chcp 65001 >nul
title Fix Build & Push v1.0.3

echo 🔨 Fixing version display...
git add .
git commit -m "Fix version display in title screen"
git push origin main

echo.
echo 🗑️ Deleting broken v1.0.3...
git push --delete origin v1.0.3 2>nul
git tag -d v1.0.3 2>nul

echo.
echo 🏷️ Recreating v1.0.3...
git tag v1.0.3
git push origin v1.0.3

echo.
echo ✅ Done! Check https://github.com/hamsterioip/customitemsk/actions
pause
