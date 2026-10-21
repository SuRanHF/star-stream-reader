[CmdletBinding(SupportsShouldProcess = $true)]
param([string]$ProjectRoot)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Join-Path $PSScriptRoot '..'
}
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path.TrimEnd('\')
$backendDir = (Join-Path $ProjectRoot 'springboot-backend').TrimEnd('\')
$playerDir = (Join-Path $ProjectRoot 'frontend-player').TrimEnd('\')
$adminDir = (Join-Path $ProjectRoot 'frontend-admin').TrimEnd('\')

function Get-ProcessById([int]$Id) {
    Get-CimInstance Win32_Process -Filter "ProcessId=$Id" -ErrorAction SilentlyContinue
}

function Test-PathInCommandLine([string]$CommandLine, [string]$Path) {
    if ([string]::IsNullOrWhiteSpace($CommandLine)) { return $false }
    $normalizedPath = $Path.TrimEnd('\')
    return $CommandLine.IndexOf($normalizedPath, [StringComparison]::OrdinalIgnoreCase) -ge 0
}

function Test-ProjectService([int]$Id, [int]$Port) {
    $process = Get-ProcessById $Id
    if (-not $process) { return $false }

    if ($Port -eq 5173 -and $process.Name -ieq 'node.exe') {
        return (Test-PathInCommandLine $process.CommandLine $playerDir) -and
            ($process.CommandLine -match '(?i)vite(\.js|\\bin\\vite)')
    }
    if ($Port -eq 5174 -and $process.Name -ieq 'node.exe') {
        return (Test-PathInCommandLine $process.CommandLine $adminDir) -and
            ($process.CommandLine -match '(?i)vite(\.js|\\bin\\vite)')
    }
    if ($Port -eq 3174 -and $process.Name -ieq 'java.exe' -and
        $process.CommandLine -match 'com\.huazhenghai\.readergame\.ReaderGameApplication') {
        $parentId = [int]$process.ParentProcessId
        for ($depth = 0; $depth -lt 5 -and $parentId -gt 0; $depth++) {
            $parent = Get-ProcessById $parentId
            if (-not $parent) { break }
            if ($parent.Name -ieq 'java.exe' -and
                (Test-PathInCommandLine $parent.CommandLine $backendDir) -and
                $parent.CommandLine -match 'org\.codehaus\.plexus\.classworlds\.launcher\.Launcher' -and
                $parent.CommandLine -match 'spring-boot:run') {
                return $true
            }
            $parentId = [int]$parent.ParentProcessId
        }
    }
    return $false
}

$targets = @(
    @{ Name = 'Backend'; Port = 3174 },
    @{ Name = 'Player'; Port = 5173 },
    @{ Name = 'Admin'; Port = 5174 }
)

foreach ($target in $targets) {
    $owners = @(
        Get-NetTCPConnection -State Listen -LocalPort $target.Port -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique
    )
    foreach ($ownerId in $owners) {
        if (-not (Test-ProjectService ([int]$ownerId) $target.Port)) {
            Write-Warning "[$($target.Name)] PID $ownerId owns port $($target.Port), but project ownership could not be verified; leaving it untouched."
            continue
        }

        $process = Get-ProcessById ([int]$ownerId)
        if ($PSCmdlet.ShouldProcess("$($process.Name) PID $ownerId", "Stop this project's $($target.Name) service on port $($target.Port)")) {
            Stop-Process -Id ([int]$ownerId) -Force -ErrorAction Stop
            Write-Host "[$($target.Name)] Stopped project process PID $ownerId on port $($target.Port)."
        }
    }
}

Write-Host 'No unrelated processes or general java.exe instances were targeted.'
