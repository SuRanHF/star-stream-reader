@echo off
title Star Stream Reader
setlocal
echo ========================================
echo   Star Stream Reader - Starting...
echo ========================================

set ROOT=%~dp0
cd /d "%ROOT%"

:: 1. Kill anything on target ports first
echo [0/4] Cleanup ports...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3174 "') do taskkill /f /pid %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3173 "') do taskkill /f /pid %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3175 "') do taskkill /f /pid %%a >nul 2>&1
timeout /t 2 /nobreak >nul
echo   Ports cleared.

:: 2. MySQL
echo [1/4] MySQL...
net start MySQL97 >nul 2>&1
if %errorlevel% equ 2 (echo   MySQL already running) else if %errorlevel% equ 0 (echo   MySQL started) else (echo   [WARN] MySQL97 not found)

:: 3. Backend
echo [2/4] Backend (3174)...
if not exist "%ROOT%springboot-backend\target\reader-game-0.1.0.jar" (
    echo   Building JAR...
    cd /d "%ROOT%springboot-backend"
    call mvn clean package -DskipTests -q
    cd /d "%ROOT%"
)
start "Star-Backend-3174" cmd /k "cd /d %ROOT%springboot-backend && java -jar target\reader-game-0.1.0.jar --spring.profiles.active=dev --server.port=3174"
echo   Waiting for backend...
timeout /t 15 /nobreak >nul

:: 4. Player
echo [3/4] Player (3173)...
start "Star-Player-3173" cmd /k "cd /d %ROOT%frontend-player && set VITE_API_BASE_URL=http://localhost:3174/api && npx vite --host 0.0.0.0 --port 3173"
timeout /t 4 /nobreak >nul

:: 5. Admin
echo [4/4] Admin (3175)...
start "Star-Admin-3175" cmd /k "cd /d %ROOT%frontend-admin && set VITE_API_BASE_URL=http://localhost:3174/api && npx vite --host 0.0.0.0 --port 3175"
timeout /t 4 /nobreak >nul

echo.
echo ========================================
echo   All services started!
echo ========================================
echo   Player : http://localhost:3173
echo   Admin  : http://localhost:3175
echo   API Doc: http://localhost:3174/doc.html
echo ========================================
echo   Run kill-all.bat to stop all services.
echo ========================================
pause
