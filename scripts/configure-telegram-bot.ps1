param([string]$MiniAppUrl)

. (Join-Path $PSScriptRoot 'telegram-api-common.ps1')

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$settings = Get-ProjectEnvironment -ProjectRoot $projectRoot
$botToken = Get-RequiredSetting -Settings $settings -Name 'TELEGRAM_BOT_TOKEN'
if ([string]::IsNullOrWhiteSpace($MiniAppUrl)) {
    $MiniAppUrl = Get-RequiredSetting -Settings $settings -Name 'TELEGRAM_MINI_APP_URL'
}
if (-not $MiniAppUrl.StartsWith('https://', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'MiniAppUrl must use HTTPS.'
}

$null = Invoke-TelegramBotApi -BotToken $botToken -Method 'setMyCommands' -Body @{
    commands = @(
        @{ command = 'start'; description = 'Open the Mini App' },
        @{ command = 'help'; description = 'Show help' }
    )
}
$null = Invoke-TelegramBotApi -BotToken $botToken -Method 'setChatMenuButton' -Body @{
    menu_button = @{
        type = 'web_app'
        text = 'Submit a request'
        web_app = @{ url = $MiniAppUrl.Trim() }
    }
}
Write-Host 'Telegram bot commands and Mini App menu button configured.'
