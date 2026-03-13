@echo off
chcp 65001 >nul
title Fix Node.js Warning & Push

echo 🔧 Fixing Node.js deprecation warning...
git add .github/workflows/publish.yml
git commit -m "Fix Node.js deprecation warning"
git push origin main

echo.
echo ✅ Fix pushed!
echo.
echo Your next release will not show the warning.
echo.
pause
