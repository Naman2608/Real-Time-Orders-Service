@echo off
if "%1"=="" (
    echo Usage: release.bat YOUR_GITHUB_TOKEN
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release.ps1" -Token %1
