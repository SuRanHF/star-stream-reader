# ==================== Star Stream Reader Windows 部署脚本 ====================
# 用法: 以管理员身份运行 PowerShell，执行 .\deploy.ps1
# 前置:
#   - JDK 17 已安装 (JAVA_HOME 已设置)
#   - MySQL 8 已安装并运行
#   - Nginx for Windows 已解压到 C:\nginx
#   - Maven 已安装

$ErrorActionPreference = "Stop"
$DeployRoot = "C:\reader-game"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Star Stream Reader 部署脚本" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# 1. 创建目录结构
Write-Host "`n[1/6] 创建目录..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "$DeployRoot\logs" | Out-Null
New-Item -ItemType Directory -Force -Path "$DeployRoot\nginx\logs" | Out-Null

# 2. 构建后端
Write-Host "`n[2/6] 构建后端..." -ForegroundColor Yellow
Push-Location ..\springboot-backend
mvn clean package -DskipTests -q
Copy-Item "target\reader-game-0.1.0.jar" "$DeployRoot\reader-game.jar" -Force
Pop-Location

# 3. 构建前端
Write-Host "`n[3/6] 构建玩家前端..." -ForegroundColor Yellow
Push-Location ..\frontend-player
npm install --silent
$env:VITE_API_BASE_URL = "/api"
npx vite build --outDir "$DeployRoot\frontend-player"
Pop-Location

Write-Host "`n[4/6] 构建管理后台..." -ForegroundColor Yellow
Push-Location ..\frontend-admin
npm install --silent
$env:VITE_API_BASE_URL = "/api"
npx vite build --outDir "$DeployRoot\frontend-admin"
Pop-Location

# 5. 安装 Windows 服务
Write-Host "`n[5/6] 安装 Windows 服务..." -ForegroundColor Yellow
$WinSW = "C:\reader-game\winsw.exe"
if (Test-Path $WinSW) {
    & $WinSW stop 2>$null
    & $WinSW uninstall 2>$null
}
# 下载 WinSW (如果不存在)
if (-not (Test-Path $WinSW)) {
    Write-Host "  下载 WinSW..." -ForegroundColor Gray
    Invoke-WebRequest -Uri "https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW.NET4.exe" -OutFile $WinSW
}
Copy-Item "deploy\reader-game-winsw.xml" "$DeployRoot\reader-game.xml" -Force
& $WinSW install
& $WinSW start

# 6. 配置 Nginx
Write-Host "`n[6/6] 配置 Nginx..." -ForegroundColor Yellow
Copy-Item "deploy\nginx.conf" "C:\nginx\conf\conf.d\reader-game.conf" -Force -ErrorAction SilentlyContinue
# 如果 nginx 存在则重载
if (Test-Path "C:\nginx\nginx.exe") {
    & C:\nginx\nginx.exe -s reload 2>$null
    if ($LASTEXITCODE -ne 0) {
        & C:\nginx\nginx.exe
    }
}

Write-Host "`n============================================" -ForegroundColor Green
Write-Host "  部署完成！" -ForegroundColor Green
Write-Host "  玩家端:  http://localhost" -ForegroundColor Green
Write-Host "  管理端:  http://localhost/admin" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "`n后续步骤:"
Write-Host "1. 修改 C:\reader-game\reader-game.xml 中的 DB_PASSWORD 和 JWT_SECRET"
Write-Host "2. winsw.exe restart  重启服务使配置生效"
Write-Host "3. winsw.exe status   查看服务状态"
Write-Host "4. 查看日志: Get-Content C:\reader-game\logs\*.log -Tail 50"
