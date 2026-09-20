# Telegram module

This module verifies signed Telegram Mini App `initData` using the bot token supplied through `TELEGRAM_BOT_TOKEN`. It checks the HMAC, timestamp, and user identity without trusting `initDataUnsafe`. Bot API updates, commands, and outgoing messages remain planned.
