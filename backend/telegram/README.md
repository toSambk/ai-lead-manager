# Telegram module

This module contains the Telegram protocol adapters used by the application:

- signed Mini App `initData` verification using `TELEGRAM_BOT_TOKEN`;
- webhook secret verification and JSON update parsing;
- `/start` and `/help` command recognition;
- outbound `sendMessage` calls with an optional Mini App web app button;
- Bot API failure classification into retryable and permanent errors.

The module does not access PostgreSQL or define Spring controllers. `backend/app` orchestrates webhook handling and workers, `backend/persistence` stores update deduplication and delivery state, and `backend/core` owns their ports and transport-neutral models.

The webhook records every update ID idempotently, binds a private chat to the verified Telegram user, handles bot commands, and forwards ordinary text to an active clarification conversation. Outbound delivery covers command replies, manager notifications, lead confirmations, and manager-approved clarification questions. Conversation state and message persistence remain application and persistence responsibilities.
