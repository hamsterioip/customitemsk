@echo off
chcp 65001 >nul
title Fix Build & Push v1.0.3

echo 🔧 Step 1: Pulling changes from GitHub...
git pull origin main

echo.
echo 🔨 Step 2: Committing fix...
git add .
git commit -m "Fix version display in title screen"

echo.
echo 🚀 Step 3: Pushing to GitHub...
git push origin main

echo.
echo 🗑️ Step 4: Deleting broken v1.0.3...
git push --delete origin v1.0.3 2>nul
git tag -d v1.0.3 2>nul

echo.
echo 🏷️ Step 5: Recreating v1.0.3...
git tag v1.0.3
git push origin v1.0.3

echo.
echo ✅ Done! Check https://github.com/hamsterioip/customitemsk/actions
pause
