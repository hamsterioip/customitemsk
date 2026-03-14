@echo off
chcp 65001 >nul
title Fix Auto-Update Cleanup

echo 🔧 Fixing auto-update to delete old JARs...
git add .
git commit -m "Fix auto-updater to clean up old JAR versions"
git push origin main

echo.
echo 🏷️ Creating release v1.1.1...
git tag v1.1.1
git push origin v1.1.1

echo.
echo ✅ Done! Old versions will now be deleted when updating.
pause
