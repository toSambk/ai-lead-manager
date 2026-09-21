param(
    [string]$CloudflaredPath = 'C:\Tools\cloudflared\cloudflared.exe',
    [string]$Url = 'http://localhost:5173'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $CloudflaredPath -PathType Leaf)) {
    throw "cloudflared.exe was not found at $CloudflaredPath. Pass its location with -CloudflaredPath."
}

if (-not [Uri]::IsWellFormedUriString($Url, [UriKind]::Absolute)) {
    throw 'Url must be an absolute URL, for example http://localhost:5173.'
}

Write-Host "Starting a Cloudflare Quick Tunnel for $Url. Press Ctrl+C to stop."
& $CloudflaredPath tunnel --url $Url
if ($LASTEXITCODE -ne 0) {
    throw "cloudflared exited with code $LASTEXITCODE."
}
