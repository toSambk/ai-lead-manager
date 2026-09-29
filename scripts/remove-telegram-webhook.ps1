param([switch]$DropPendingUpdates)

. (Join-Path $PSScriptRoot 'telegram-api-common.ps1')

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$settings = Get-ProjectEnvironment -ProjectRoot $projectRoot
$botToken = Get-RequiredSetting -Settings $settings -Name 'TELEGRAM_BOT_TOKEN'
$null = Invoke-TelegramBotApi -BotToken $botToken -Method 'deleteWebhook' -Body @{
    drop_pending_updates = [bool]$DropPendingUpdates
}
Write-Host 'Telegram webhook removed.'
