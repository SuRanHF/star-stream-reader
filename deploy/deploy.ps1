# ==================== Star Stream Reader Windows 部署脚本 ====================
# 域名: reader-scenario-game.bbroot.com
# 用法: 以管理员身份运行 PowerShell
#   powershell -ExecutionPolicy Bypass -File deploy\deploy.ps1
# ====================

$ErrorActionPreference = "Stop"
$DeployRoot = "C:\reader-game"
$Domain = "reader-scenario-game.bbroot.com"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Star Stream Reader 部署" -ForegroundColor Cyan
Write-Host "  域名: $Domain" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# 1. 创建目录
Write-Host "`n[1/7] 创建目录..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "$DeployRoot\logs" | Out-Null
New-Item -ItemType Directory -Force -Path "$DeployRoot\ssl" | Out-Null
New-Item -ItemType Directory -Force -Path "C:\nginx\conf\conf.d" | Out-Null

# 2. Windows 防火墙开放端口
Write-Host "`n[2/7] 开放防火墙 80/443 端口..." -ForegroundColor Yellow
netsh advfirewall firewall add rule name="Reader Game HTTP" dir=in action=allow protocol=tcp localport=80 | Out-Null
netsh advfirewall firewall add rule name="Reader Game HTTPS" dir=in action=allow protocol=tcp localport=443 | Out-Null

# 3. 阿里云安全组提醒
Write-Host "  ⚠ 还需在阿里云控制台 → ECS → 安全组 → 入方向，放行 80 和 443 端口" -ForegroundColor Magenta

# 4. 构建后端
Write-Host "`n[3/7] 构建后端..." -ForegroundColor Yellow
Push-Location ..\springboot-backend
mvn clean package -DskipTests -q
Copy-Item "target\reader-game-0.1.0.jar" "$DeployRoot\reader-game.jar" -Force
Pop-Location

# 5. 构建前端
Write-Host "`n[4/7] 构建玩家前端..." -ForegroundColor Yellow
Push-Location ..\frontend-player
npm install --silent
$env:VITE_API_BASE_URL = "/api"
npx vite build --outDir "$DeployRoot\frontend-player" 2>&1 | Out-Null
Pop-Location

Write-Host "`n[5/7] 构建管理后台..." -ForegroundColor Yellow
Push-Location ..\frontend-admin
npm install --silent
$env:VITE_API_BASE_URL = "/api"
$env:VITE_BASE = "/admin/"
npx vite build --outDir "$DeployRoot\frontend-admin" 2>&1 | Out-Null
Pop-Location

# 6. 安装 Windows 服务
Write-Host "`n[6/7] 安装 Windows 服务..." -ForegroundColor Yellow
$WinSW = "$DeployRoot\winsw.exe"
if (Test-Path $WinSW) {
    & $WinSW stop 2>$null
    & $WinSW uninstall 2>$null
    Start-Sleep -Seconds 2
}
if (-not (Test-Path $WinSW)) {
    Write-Host "  下载 WinSW..." -ForegroundColor Gray
    Invoke-WebRequest -Uri "https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW.NET4.exe" -OutFile $WinSW
}
Copy-Item "deploy\reader-game-winsw.xml" "$DeployRoot\reader-game.xml" -Force
& $WinSW install
& $WinSW start

# 7. 配置 Nginx
Write-Host "`n[7/7] 配置 Nginx..." -ForegroundColor Yellow
# 确保 include conf.d 在 nginx.conf 主配置中
$nginxConf = "C:\nginx\conf\nginx.conf"
if (Test-Path $nginxConf) {
    $content = Get-Content $nginxConf -Raw
    if ($content -notmatch "conf\.d") {
        (Get-Content $nginxConf) -replace "http \{", "http {`r`n    include conf.d/*.conf;" | Set-Content $nginxConf
    }
}
Copy-Item "deploy\nginx.conf" "C:\nginx\conf\conf.d\reader-game.conf" -Force

# 重载 Nginx
if (Test-Path "C:\nginx\nginx.exe") {
    & C:\nginx\nginx.exe -s reload 2>$null
    if ($LASTEXITCODE -ne 0) { & C:\nginx\nginx.exe }
}

# ==================== 完成 ====================
Write-Host "`n============================================" -ForegroundColor Green
Write-Host "  部署完成！" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "`n  ⚠ 重要：完成以下步骤后才能通过域名访问：" -ForegroundColor Yellow
Write-Host "  1. DNS 解析: reader-scenario-game.bbroot.com → A → 121.43.136.226"
Write-Host "  2. 阿里云安全组: 入方向放行 80, 443 端口"
Write-Host "  3. 安装 SSL 证书 (win-acme):"
Write-Host "     winget install win-acme  (或 https://www.win-acme.com)"
Write-Host "     wacs.exe --target manual --host $Domain --installation-script nginx --siteid 1"
Write-Host "  4. 修改 C:\reader-game\reader-game.xml 中的 DB_PASSWORD 和 JWT_SECRET"
Write-Host "  5. 重启服务: C:\reader-game\winsw.exe restart"
Write-Host "`n  查看状态: C:\reader-game\winsw.exe status"
Write-Host "  查看日志: Get-Content C:\reader-game\logs\*.log -Tail 50"
