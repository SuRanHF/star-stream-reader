@echo off
setlocal
echo ========================================
echo   Star Stream Reader - Starting...
echo ========================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1"
exit /b %errorlevel%
