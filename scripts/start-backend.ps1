param(
    [switch]$LocalHttp
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw 'Missing .env file. Copy .env.example to .env and fill in POSTGRES_PASSWORD and TELEGRAM_BOT_TOKEN.'
}

$settings = @{}
$lineNumber = 0
foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
    $lineNumber++
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
        continue
    }

    $separator = $line.IndexOf('=')
    if ($separator -lt 1) {
        throw "Invalid .env entry on line $lineNumber. Use NAME=value."
    }

    $name = $line.Substring(0, $separator).Trim()
    if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        throw "Invalid .env variable name on line $lineNumber."
    }
    $settings[$name] = $line.Substring($separator + 1)
}

foreach ($requiredName in @('POSTGRES_PASSWORD', 'TELEGRAM_BOT_TOKEN')) {
    if (-not $settings.ContainsKey($requiredName) -or [string]::IsNullOrWhiteSpace($settings[$requiredName])) {
        throw "$requiredName is missing or empty in .env."
    }
}

foreach ($name in @('POSTGRES_HOST', 'POSTGRES_PORT', 'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'TELEGRAM_BOT_TOKEN')) {
    if ($settings.ContainsKey($name) -and -not [string]::IsNullOrWhiteSpace($settings[$name])) {
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
}

$env:SESSION_COOKIE_SECURE = if ($LocalHttp) { 'false' } else { 'true' }

Push-Location $projectRoot
try {
    Write-Host 'Starting the backend. Press Ctrl+C to stop.'
    & (Join-Path $projectRoot 'gradlew.bat') ':backend:app:bootRun'
    if ($LASTEXITCODE -ne 0) {
        throw "Backend exited with code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
