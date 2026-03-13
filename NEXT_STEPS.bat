@echo off
chcp 65001 >nul
title Next Steps - Create Your First Release

echo ╔══════════════════════════════════════════════════════════════╗
echo ║         🎉 Files Pushed Successfully! 🎉                     ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
echo ✅ Your files are now on GitHub: hamsterioip/customitemsk
echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                    NEXT STEPS                                ║
echo ╠══════════════════════════════════════════════════════════════╣
echo ║                                                              ║
echo ║  Step 1: ENABLE GITHUB ACTIONS                               ║
echo ║  ─────────────────────────────────────                       ║
echo ║  1. Go to: https://github.com/hamsterioip/customitemsk      ║
echo ║  2. Click the "Actions" tab at the top                       ║
echo ║  3. If you see "I understand my workflows..."                 ║
echo ║     → Click the green "Enable Actions" button                 ║
echo ║                                                              ║
echo ║  Step 2: CREATE FIRST RELEASE                                ║
echo ║  ─────────────────────────────────────                       ║
echo ║  Run this command in VS Code terminal:                       ║
echo ║                                                              ║
echo ║      .\build-and-release.bat v1.0.0                         ║
echo ║                                                              ║
echo ║  This will:                                                   ║
echo ║  • Build your mod                                            ║
echo ║  • Push to GitHub                                            ║
echo ║  • Create release tag v1.0.0                                 ║
echo ║  • Trigger GitHub Actions                                    ║
echo ║                                                              ║
echo ║  Step 3: VERIFY                                               ║
echo ║  ─────────────────────────────────────                       ║
echo ║  • Go to: https://github.com/hamsterioip/customitemsk       ║
echo ║  • Click "Actions" tab                                        ║
echo ║  • You should see a yellow workflow running                   ║
echo ║  • After 2-3 minutes, go to "Releases" tab                    ║
echo ║  • Your v1.0.0 release should be there!                      ║
echo ║                                                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
echo 🔗 Quick Links:
echo    Repo:     https://github.com/hamsterioip/customitemsk
echo    Actions:  https://github.com/hamsterioip/customitemsk/actions
echo    Releases: https://github.com/hamsterioip/customitemsk/releases
echo.
pause
