param(
    [Parameter(Mandatory = $true)]
    [string]$PublicUrl,
    [switch]$DropPendingUpdates
)

. (Join-Path $PSScriptRoot 'telegram-api-common.ps1')

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$settings = Get-ProjectEnvironment -ProjectRoot $projectRoot
$botToken = Get-RequiredSetting -Settings $settings -Name 'TELEGRAM_BOT_TOKEN'
$webhookSecret = Get-RequiredSetting -Settings $settings -Name 'TELEGRAM_WEBHOOK_SECRET'
$baseUrl = $PublicUrl.Trim().TrimEnd('/')
if (-not $baseUrl.StartsWith('https://', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'PublicUrl must use HTTPS.'
}

$webhookUrl = "$baseUrl/api/telegram/webhook"
$null = Invoke-TelegramBotApi -BotToken $botToken -Method 'setWebhook' -Body @{
    url = $webhookUrl
    secret_token = $webhookSecret
    allowed_updates = @('message', 'callback_query')
    drop_pending_updates = [bool]$DropPendingUpdates
}
Write-Host "Telegram webhook configured: $webhookUrl"
