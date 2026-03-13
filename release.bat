@echo off
:: Quick release script - just provide version number
:: Example: release.bat v1.2.0

if "%~1"=="" (
    echo Usage: release.bat [version]
    echo Example: release.bat v1.2.0
    exit /b 1
)

.\build-and-release.bat %*
