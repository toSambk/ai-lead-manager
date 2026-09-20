interface TelegramWebApp {
  initData: string
  ready(): void
}

interface Window {
  Telegram?: { WebApp: TelegramWebApp }
}
