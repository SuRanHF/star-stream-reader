[CmdletBinding()]
param(
    [string]$ProjectRoot,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Join-Path $PSScriptRoot '..'
}
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path.TrimEnd('\')
$backendDir = Join-Path $ProjectRoot 'springboot-backend'
$playerDir = Join-Path $ProjectRoot 'frontend-player'
$adminDir = Join-Path $ProjectRoot 'frontend-admin'

function Get-ListeningProcessIds([int]$Port) {
    @(
        Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique
    )
}

function Start-ServiceWhenPortIsFree(
    [string]$Name,
    [int]$Port,
    [string]$WorkingDirectory,
    [string]$Command
) {
    $owners = @(Get-ListeningProcessIds $Port)
    if ($owners.Count -gt 0) {
        Write-Host "[$Name] Port $Port is already in use (PID $($owners -join ', ')); leaving it untouched."
        return
    }

    if ($CheckOnly) {
        Write-Host "[$Name] Would start on port $Port from $WorkingDirectory."
        return
    }

    if (-not (Test-Path -LiteralPath $WorkingDirectory -PathType Container)) {
        Write-Warning "[$Name] Directory not found: $WorkingDirectory"
        return
    }
    if (-not (Test-Path -LiteralPath (Join-Path $WorkingDirectory 'node_modules') -PathType Container) -and $Name -ne 'Backend') {
        Write-Warning "[$Name] Dependencies are missing; run npm install in $WorkingDirectory first."
        return
    }

    try {
        $process = Start-Process -FilePath $env:ComSpec `
            -ArgumentList @('/d', '/s', '/c', $Command) `
            -WorkingDirectory $WorkingDirectory `
            -WindowStyle Hidden `
            -PassThru
        Write-Host "[$Name] Started launcher PID $($process.Id); waiting for port $Port."
    }
    catch {
        Write-Warning "[$Name] Could not start: $($_.Exception.Message)"
    }
}

Write-Host "Project: $ProjectRoot"
if ($CheckOnly) {
    Write-Host 'Check-only mode: no services will be started or stopped.'
}
else {
    $mysql = Get-Service -Name 'MySQL97' -ErrorAction SilentlyContinue
    if ($mysql -and $mysql.Status -ne 'Running') {
        try {
            Start-Service -Name 'MySQL97' -ErrorAction Stop
            Write-Host '[MySQL] Started MySQL97.'
        }
        catch {
            Write-Warning "[MySQL] Could not start MySQL97: $($_.Exception.Message)"
        }
    }
    elseif ($mysql) {
        Write-Host '[MySQL] MySQL97 is already running.'
    }
    else {
        Write-Warning '[MySQL] Service MySQL97 was not found; continuing without changing other services.'
    }
}

$maven = Join-Path $env:ProgramFiles 'Apache\apache-maven-3.9.9\bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $maven -PathType Leaf)) {
    $mavenCommand = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($mavenCommand) { $maven = $mavenCommand.Source }
}
if (Test-Path -LiteralPath $maven -PathType Leaf) {
    $backendCommand = 'call "{0}" spring-boot:run -Dspring-boot.run.profiles=dev' -f $maven
    Start-ServiceWhenPortIsFree 'Backend' 3174 $backendDir $backendCommand
}
else {
    Write-Warning '[Backend] Maven was not found; backend was not started.'
}

$npmCommand = Get-Command 'npm.cmd' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $npmCommand) {
    Write-Warning '[Frontend] npm.cmd was not found; player and admin were not started.'
}
else {
    Start-ServiceWhenPortIsFree 'Player' 5173 $playerDir 'call npm.cmd run dev'
    # frontend-admin/package.json defines port 5174; keep this aligned with its own dev script.
    Start-ServiceWhenPortIsFree 'Admin' 5174 $adminDir 'call npm.cmd run dev'
}

Write-Host ''
Write-Host 'Player: http://localhost:5173/game'
Write-Host 'Admin : http://localhost:5174'
Write-Host 'API   : http://localhost:3174'
if (-not $CheckOnly) {
    Write-Host 'Existing listeners were left running; no port owner is force-killed.'
}
