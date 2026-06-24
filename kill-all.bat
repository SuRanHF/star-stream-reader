@echo off
echo ========================================
echo   Star Stream Reader - Stop All
echo ========================================

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3174 "') do taskkill /f /pid %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3173 "') do taskkill /f /pid %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3175 "') do taskkill /f /pid %%a >nul 2>&1

for /f "tokens=2" %%a in ('tasklist /fi "imagename eq java.exe" /fo csv /nh 2^>nul') do (
    for /f "tokens=1 delims=," %%b in ("%%a") do taskkill /f /pid %%~b >nul 2>&1
)

echo   All services stopped.
echo ========================================
pause
