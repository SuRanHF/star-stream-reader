@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\kill-all.ps1"
exit /b %errorlevel%
