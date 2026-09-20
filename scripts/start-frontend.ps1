param(
    [string]$AllowedHost
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$frontendDir = Join-Path $projectRoot 'frontend'
if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
    throw 'npm.cmd was not found. Install Node.js and open a new PowerShell terminal.'
}

if ($AllowedHost) {
    if ($AllowedHost -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]*$') {
        throw 'AllowedHost must be a hostname without https://, a port, or a path.'
    }
    $env:__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS = $AllowedHost
}

Push-Location $frontendDir
try {
    if (-not (Test-Path -LiteralPath (Join-Path $frontendDir 'node_modules\vite') -PathType Container)) {
        Write-Host 'Installing frontend dependencies from package-lock.json.'
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) {
            throw "npm ci exited with code $LASTEXITCODE."
        }
    }

    Write-Host 'Starting the frontend. Press Ctrl+C to stop.'
    & npm.cmd run dev
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend exited with code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
