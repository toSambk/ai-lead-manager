Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-ProjectEnvironment {
    param([string]$ProjectRoot)

    $envFile = Join-Path $ProjectRoot '.env'
    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
        throw 'Missing .env file. Copy .env.example to .env and fill in the Telegram settings.'
    }
    $settings = @{}
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $separator = $line.IndexOf('=')
        if ($separator -lt 1) { continue }
        $settings[$line.Substring(0, $separator).Trim()] = $line.Substring($separator + 1).Trim()
    }
    return $settings
}

function Get-RequiredSetting {
    param(
        [hashtable]$Settings,
        [string]$Name
    )

    if (-not $Settings.ContainsKey($Name) -or [string]::IsNullOrWhiteSpace($Settings[$Name])) {
        throw "$Name is missing or empty in .env."
    }
    return $Settings[$Name]
}

function Invoke-TelegramBotApi {
    param(
        [string]$BotToken,
        [string]$Method,
        [hashtable]$Body = @{}
    )

    try {
        $response = Invoke-RestMethod `
            -Method Post `
            -Uri "https://api.telegram.org/bot$BotToken/$Method" `
            -ContentType 'application/json' `
            -Body ($Body | ConvertTo-Json -Depth 10 -Compress)
    } catch {
        throw "Telegram Bot API method $Method failed. Check the bot token, network, and supplied values."
    }
    if (-not $response.ok) {
        throw "Telegram Bot API method $Method returned an unsuccessful response."
    }
    return $response.result
}
