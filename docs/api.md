# Preliminary HTTP API

This document records the proposed first-release API. The paths and responsibilities are provisional; request and response schemas will be finalized with each use case. `GET /api/system`, Telegram session login, `/api/me`, logout, `GET /api/categories`, lead creation and reads, manager status transitions, owner assignment, internal notes, AI analysis state and retry, lead event history, Telegram webhook handling, and the clarification conversation are implemented. The remaining planned routes return `501 Not Implemented`.

All controllers live in `backend/app/src/main/kotlin/dev/aileadmanager/app/controller`. The frontend calls the backend over HTTP. In normal environments, the backend derives user identity and role from verified Telegram data; a request body cannot assign its own customer ID or role. The explicitly enabled `local` profile is the only exception and provides fixed development identities. Customer-scoped reads must enforce ownership, and manager and administrator operations require their respective roles before implementation is considered complete.

## Authentication and catalog

| Method | Path | Intended caller | Planned behavior |
| --- | --- | --- | --- |
| `GET` | `/api/auth/csrf` | Mini App | Return a CSRF token and its header name; call before login and again after login. Implemented. |
| `POST` | `/api/auth/telegram` | Mini App | Verify raw Telegram `initData`, upsert the user without changing the existing role, and establish a JDBC-backed session. Implemented. |
| `GET` | `/api/me` | Authenticated user | Return the current user and role. Implemented. |
| `POST` | `/api/auth/logout` | Authenticated user | Invalidate the session. Implemented. |
| `GET` | `/api/dev/auth/users` | Local development | List fixed test identities. Registered only with the `local` Spring profile. |
| `POST` | `/api/dev/auth/login` | Local development | Establish a normal session for a selected test identity. Registered only with the `local` Spring profile. |
| `GET` | `/api/categories` | Authenticated user | Return active service categories ordered by name. Implemented. |
| `GET` | `/api/managers` | Manager, administrator | Return users eligible to own a lead, ordered by display name. Implemented. |

`GET /api/categories` returns a JSON array such as `[{ "id": 1, "code": "website_development", "name": "Website development" }]`. Inactive categories are omitted. An unauthenticated request returns `401 Unauthorized`.

## Leads

| Method | Path | Intended caller | Planned behavior |
| --- | --- | --- | --- |
| `POST` | `/api/leads` | Customer | Validate and save a lead, queue its AI analysis and Telegram notifications, and return its ID and public reference. Implemented for an authenticated customer. |
| `GET` | `/api/leads` | Customer, manager, administrator | Return a paginated list ordered newest first. Implemented with `page` and `size`; customers see only their leads, while managers and administrators see all leads. Filters remain planned. |
| `GET` | `/api/leads/{id}` | Customer, manager, administrator | Return lead detail. Implemented; customers can retrieve only their own leads, while managers and administrators can retrieve any lead. |
| `PATCH` | `/api/leads/{id}/status` | Manager, administrator | Apply a valid status transition with optimistic version checking and append an audit event in the same transaction. Implemented. |
| `PATCH` | `/api/leads/{id}/assignee` | Manager, administrator | Assign or clear an eligible owner with optimistic version checking and append an audit event in the same transaction. Implemented. |
| `POST` | `/api/leads/{id}/notes` | Manager, administrator | Add an internal note that is never sent to a customer. Implemented. |
| `GET` | `/api/leads/{id}/notes` | Manager, administrator | Return internal notes newest first. Implemented. |
| `GET` | `/api/leads/{id}/events` | Manager, administrator | Return the lead's newest-first status and owner audit history. Implemented. |
| `GET` | `/api/leads/{id}/messages` | Customer, manager, administrator | Return clarification messages in chronological order. Implemented; customers see their own replies and successfully delivered questions, while managers see pending and failed questions as well. |

### Create lead contract

The request body is JSON. `categoryId`, `description`, and `contactDetails` are required. The budget amount and currency must be supplied together; the amount is nonnegative with at most two decimal places and the currency is an ISO 4217 code. `desiredDeadline` is an optional ISO date. `customerId`, role, status, owner, and timestamps are not accepted from the request body.

~~~json
{
  "categoryId": 1,
  "description": "Build a website for a cafe",
  "estimatedBudgetAmount": 1200.50,
  "budgetCurrency": "USD",
  "desiredDeadline": "2026-11-01",
  "contactDetails": "@cafe_owner"
}
~~~

With a verified customer identity, success returns `201 Created` with `Location: /api/leads/{id}` and a body like this:

~~~json
{
  "id": 123,
  "reference": "LM-000123",
  "status": "NEW",
  "createdAt": "2026-09-20T12:00:00Z"
}
~~~

Invalid input or an unavailable category returns `400 Bad Request`; an absent session returns `401 Unauthorized`, and a session without the `CUSTOMER` role returns `403 Forbidden`. The core use case checks that the customer exists with role `CUSTOMER`, the category is active, and the input satisfies domain rules before saving. The controller obtains the customer ID from the Spring Security principal. No HTTP header or JSON field can substitute for it. The lead, its initial AI job, and applicable Telegram delivery jobs are committed in one transaction. A manager notification is queued when `TELEGRAM_MANAGER_CHAT_ID` is configured. A customer confirmation is queued when the customer has previously bound a private chat by sending a command to the bot.

### Read lead contract

`GET /api/leads?page=0&size=20` accepts a zero-based page and a size from 1 to 100. It returns `items`, `page`, `size`, `totalElements`, and `totalPages`. Items and `GET /api/leads/{id}` include the stable reference, category, submitted fields, status, optional owner, timestamps, and optimistic `version`. A customer query is scoped by the authenticated user's internal ID at the repository boundary. Requesting a missing or another customer's lead returns `404 Not Found`; invalid pagination returns `400 Bad Request`.

### Change status contract

`PATCH /api/leads/{id}/status` accepts the target status and the version last read by the manager:

~~~json
{
  "status": "IN_PROGRESS",
  "version": 0
}
~~~

Managers and administrators can use this endpoint. Customers receive `403 Forbidden`. The implemented transitions are `NEW` to `CLARIFICATION`, `IN_PROGRESS`, or `REJECTED`; `CLARIFICATION` to `NEW`, `IN_PROGRESS`, or `REJECTED`; and `IN_PROGRESS` to `COMPLETED` or `REJECTED`. `COMPLETED` and `REJECTED` are terminal. A repeated or invalid transition and a stale version return `409 Conflict`; a missing lead returns `404 Not Found`.

The response is the updated lead with an incremented version. The update and its `STATUS_CHANGED` event are committed atomically. `GET /api/leads/{id}/events` returns those events newest first and is restricted to managers and administrators.

### Assign owner contract

`PATCH /api/leads/{id}/assignee` accepts the internal ID of a manager or administrator and the version last read by the caller. Set `ownerId` to `null` to return the lead to the unassigned queue.

~~~json
{
  "ownerId": 42,
  "version": 1
}
~~~

Managers and administrators can assign any eligible owner, replace the current owner, or clear the assignment. This shared-queue rule is the current first-release policy. The response is the updated lead with an incremented version. The lead update and its `OWNER_CHANGED` event are committed atomically. A stale version or unchanged owner returns `409 Conflict`, an ineligible or missing owner returns `400 Bad Request`, and a missing lead returns `404 Not Found`.

### Internal notes contract

`POST /api/leads/{id}/notes` accepts `{ "body": "Confirm the budget before the call." }`. After trimming, the note must contain 1 to 2,000 characters. Success returns `201 Created` and the stored note with its author and creation time. `GET /api/leads/{id}/notes` returns notes newest first.

Only managers and administrators can create or read internal notes. Notes use the `INTERNAL_NOTE` message kind and are excluded from customer APIs and Telegram delivery. A missing lead returns `404 Not Found`; invalid content returns `400 Bad Request`.

### Mini App session flow

1. `GET /api/auth/csrf` returns `{ "token": "...", "headerName": "X-CSRF-TOKEN" }` and creates a session cookie.
2. `POST /api/auth/telegram` sends `{ "initData": "<raw Telegram.WebApp.initData>" }` with that CSRF header. The backend verifies the Telegram HMAC and an `auth_date` no older than 10 minutes, rotates the session ID, and returns `{ "id": 123, "displayName": "Customer", "role": "CUSTOMER" }`. A missing bot token returns `503`.
3. Fetch a new CSRF token after login. Send the session cookie and CSRF header on state-changing requests. `GET /api/me` reads the current user and role from the database-backed session; `POST /api/auth/logout` invalidates it.

Serve the Mini App and API from the same origin. The session cookie is `HttpOnly`, `SameSite=Lax`, and `Secure` by default; set `SESSION_COOKIE_SECURE=false` only for local HTTP development. Session rows live in PostgreSQL, and the bot token must come from `TELEGRAM_BOT_TOKEN` without being logged or returned.

### Local test-user flow

Set `SPRING_PROFILES_ACTIVE=local` only in a local environment. On startup, the application creates or refreshes two customers, two managers, and one administrator under reserved local Telegram IDs. `GET /api/dev/auth/users` returns their safe selector metadata. `POST /api/dev/auth/login` accepts `{ "userKey": "customer-alice" }`, rotates the session ID, stores the selected internal user ID in the same session attribute used by Telegram login, and clears the previous CSRF token. The frontend then fetches a new CSRF token. Switching users therefore exercises the normal authorization filters and repositories rather than bypassing them.

The controller and seeder do not exist unless the `local` profile is active. Production deployments must leave `SPRING_PROFILES_ACTIVE` empty or set it to production-specific profiles that do not include `local`.

## AI and customer replies

| Method | Path | Intended caller | Planned behavior |
| --- | --- | --- | --- |
| `GET` | `/api/leads/{id}/ai-analysis` | Manager, administrator | Return analysis and job state: `PENDING`, `RUNNING`, `SUCCEEDED`, or `FAILED`. Implemented. |
| `POST` | `/api/leads/{id}/ai-analysis/retry` | Manager, administrator | Create a new analysis job after failure without creating duplicate active jobs. Implemented. |
| `GET` | `/api/leads/{id}/reply-drafts` | Manager, administrator | List reply drafts newest first. Implemented. |
| `POST` | `/api/leads/{id}/reply-drafts` | Manager, administrator | Create a customer-facing reply draft while the lead is in `CLARIFICATION`. Implemented. |
| `PATCH` | `/api/leads/{id}/reply-drafts/{draftId}` | Manager, administrator | Edit a `DRAFT` using optimistic version checking. Implemented. |
| `POST` | `/api/leads/{id}/reply-drafts/{draftId}/send` | Manager, administrator | Explicitly approve a draft, reserve the active conversation, and queue delivery. Implemented; AI output alone never sends a message. |

## Telegram and administration

| Method | Path | Intended caller | Planned behavior |
| --- | --- | --- | --- |
| `POST` | `/api/telegram/webhook` | Telegram | Verify `X-Telegram-Bot-Api-Secret-Token`, deduplicate by `update_id`, bind private chats, handle commands, and associate ordinary text with an active clarification conversation. Implemented. |
| `PATCH` | `/api/admin/users/{id}/role` | Administrator | Change a user's role. |
| `POST` | `/api/admin/categories` | Administrator | Create a service category. |
| `PATCH` | `/api/admin/categories/{id}` | Administrator | Update or deactivate a service category. |
| `GET` | `/api/admin/ai-settings` | Administrator | Read safe AI processing settings; never return provider secrets. |
| `PATCH` | `/api/admin/ai-settings` | Administrator | Change safe AI processing settings. Provider keys remain environment variables. |

`GET /api/system` is an implemented connectivity endpoint. Spring Boot Actuator also provides `/actuator/health`.

### Telegram webhook contract

`POST /api/telegram/webhook` accepts raw Telegram update JSON and requires the `X-Telegram-Bot-Api-Secret-Token` header configured through `TELEGRAM_WEBHOOK_SECRET`. An invalid secret returns `403 Forbidden`, a missing server-side secret returns `503 Service Unavailable`, and malformed input returns `400 Bad Request`. Valid and duplicate updates return `200 OK` so Telegram does not keep retrying them.

The handler inserts `update_id` before applying effects. A repeated ID cannot enqueue another response. A private message upserts the Telegram user without changing an existing role and binds the private chat for later delivery. `/start` and `/help` queue responses; unknown bot commands receive the help response. Ordinary customer text is stored only when that customer has one conversation in `AWAITING_REPLY`; the reply closes the conversation and enqueues a new AI analysis in the same transaction.

### Clarification contract

Draft bodies contain 1 to 2,000 characters. Create and update operations require a lead in `CLARIFICATION`; editing or approving a non-`DRAFT` returns `409 Conflict`. Updates and approval include the last-read `version`. Approval returns `202 Accepted`. It returns `422 Unprocessable Entity` when the customer has not bound a private bot chat and `409 Conflict` when that customer already has an active clarification conversation.

Approval persists the approved draft, `FOLLOW_UP_QUESTION`, conversation reservation, and unique Telegram delivery job atomically. Successful delivery changes the message and draft to sent and opens the conversation for a reply. Terminal delivery failure changes them to failed and cancels the reservation. AI suggestions are copied into the editor by the frontend and have no direct send path.

### AI analysis contract

Every new lead queues an AI job in the same database transaction. The worker claims due jobs with PostgreSQL row locking, increments `attemptCount`, and applies a two-minute lease. A successful response is structurally validated before `ai_results` is written and the job becomes `SUCCEEDED`. If required information is missing and the lead is still `NEW`, the same completion transaction changes it to `CLARIFICATION` and appends a system audit event.

Retryable failures are returned to `PENDING` with exponential backoff and jitter. The fifth failed attempt becomes `FAILED`; permanent provider errors fail immediately. A recovery task returns expired `RUNNING` leases to the queue or marks them failed when their attempt limit is exhausted. `POST /api/leads/{id}/ai-analysis/retry` accepts only a latest job in `FAILED` state and returns `202 Accepted` with the new job.

## Background jobs

| Job | Trigger | Work and completion |
| --- | --- | --- |
| `AnalyzeLead` | Lead creation, customer clarification reply, or an authorized manual retry | Invoke the AI adapter with the lead and conversation history, validate structured output, persist a result, and mark the job `SUCCEEDED` or `FAILED` after bounded persisted retries. Implemented with the local stub. |
| `SendTelegramMessage` | Bot command, lead confirmation, manager notification, or manager-approved clarification question | Deliver through the Telegram adapter and record the outcome after bounded persisted retries. Implemented. |
| `RecoverStaleJobs` | Scheduled maintenance | Detect AI or Telegram jobs abandoned in `RUNNING` after an interruption; retry within the configured limit or mark them `FAILED`. Implemented. |

These are application jobs inside the existing Spring Boot service, not separate deployable services. Both workers use PostgreSQL storage, short claim transactions, `SKIP LOCKED`, leases, bounded retries, and explicit terminal failures. External AI and Telegram calls run outside claim transactions. A unique message key prevents duplicate enqueueing, but a Bot API timeout after Telegram accepted a message is ambiguous and can still result in a duplicate on retry.
