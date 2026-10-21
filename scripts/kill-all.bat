@echo off
setlocal
echo ========================================
echo   Star Stream Reader - Stop Project Services
echo ========================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0kill-all.ps1"
exit /b %errorlevel%
