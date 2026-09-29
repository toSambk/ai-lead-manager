. (Join-Path $PSScriptRoot 'telegram-api-common.ps1')

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$settings = Get-ProjectEnvironment -ProjectRoot $projectRoot
$botToken = Get-RequiredSetting -Settings $settings -Name 'TELEGRAM_BOT_TOKEN'
$result = Invoke-TelegramBotApi -BotToken $botToken -Method 'getWebhookInfo'
$result | ConvertTo-Json -Depth 10
