@echo off
chcp 65001 >nul
title Fix Release & Rebuild

echo ╔══════════════════════════════════════════════════════════════╗
echo ║         Fix Release & Rebuild (hamsterioip)                  ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

echo 🔧 Step 1: Pushing workflow fix...
git add .github/workflows/publish.yml
git commit -m "Fix JAR upload in release workflow"
git push origin main

echo.
echo 🗑️ Step 2: Deleting broken release v1.0.0...
git push --delete origin v1.0.0 2>nul
git tag -d v1.0.0 2>nul
echo ✅ Old release deleted

echo.
echo 🔄 Step 3: Rebuilding and releasing v1.0.0...
echo.
echo Running: .\build-and-release.bat v1.0.0
echo.

.\build-and-release.bat v1.0.0
