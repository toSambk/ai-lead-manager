# Preliminary HTTP API

This document records the proposed first-release API. The paths and responsibilities are provisional; request and response schemas will be finalized with each use case. `GET /api/system`, Telegram session login, `/api/me`, logout, `GET /api/categories`, and `POST /api/leads` are implemented. The remaining planned routes return `501 Not Implemented`. A registered route is not evidence that Telegram message delivery or AI processing is available.

All controllers live in `backend/app/src/main/kotlin/dev/aileadmanager/app/controller`. The frontend calls the backend over HTTP. The backend derives user identity and role from verified Telegram data; a request body cannot assign its own customer ID or role. Customer-scoped reads must enforce ownership, and manager and administrator operations require their respective roles before implementation is considered complete.

## Authentication and catalog

| Method | Path | Intended caller | Planned behavior |
| --- | --- | --- | --- |
| `GET` | `/api/auth/csrf` | Mini App | Return a CSRF token and its header name; call before login and again after login. Implemented. |
| `POST` | `/api/auth/telegram` | Mini App | Verify raw Telegram `initData`, upsert the user without changing the existing role, and establish a JDBC-backed session. Implemented. |
| `GET` | `/api/me` | Authenticated user | Return the current user and role. Implemented. |
| `POST` | `/api/auth/logout` | Authenticated user | Invalidate the session. Implemented. |
| `GET` | `/api/categories` | Authenticated user | Return active service categories ordered by name. Implemented. |

`GET /api/categories` returns a JSON array such as `[{ "id": 1, "code": "website_development", "name": "Website development" }]`. Inactive categories are omitted. An unauthenticated request returns `401 Unauthorized`.

## Leads

| Method | Path | Intended caller | Planned behavior |
| --- | --- | --- | --- |
| `POST` | `/api/leads` | Customer | Validate and save a lead; return its ID and public reference. Implemented for an authenticated customer. AI analysis will be queued in a later phase. |
| `GET` | `/api/leads` | Customer, manager, administrator | Return a paginated list. Customers see only their leads; managers can filter by status, priority, and owner. |
| `GET` | `/api/leads/{id}` | Customer, manager, administrator | Return the lead detail allowed by the caller's role. |
| `PATCH` | `/api/leads/{id}/status` | Manager, administrator | Apply a valid status transition and write an audit event. |
| `PATCH` | `/api/leads/{id}/assignee` | Manager, administrator | Assign an eligible owner and write an audit event. |
| `POST` | `/api/leads/{id}/notes` | Manager, administrator | Add an internal note that is never sent to a customer. |
| `GET` | `/api/leads/{id}/events` | Manager, administrator | Return the lead's audit history. |
| `GET` | `/api/leads/{id}/messages` | Customer, manager, administrator | Return messages visible to the caller. Internal notes are excluded from customer responses. |

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

Invalid input or an unavailable category returns `400 Bad Request`; an absent session returns `401 Unauthorized`, and a session without the `CUSTOMER` role returns `403 Forbidden`. The core use case checks that the customer exists with role `CUSTOMER`, the category is active, and the input satisfies domain rules before saving. The controller obtains the customer ID from the Spring Security principal. No HTTP header or JSON field can substitute for it. AI jobs and Telegram notifications are not created yet.

### Mini App session flow

1. `GET /api/auth/csrf` returns `{ "token": "...", "headerName": "X-CSRF-TOKEN" }` and creates a session cookie.
2. `POST /api/auth/telegram` sends `{ "initData": "<raw Telegram.WebApp.initData>" }` with that CSRF header. The backend verifies the Telegram HMAC and an `auth_date` no older than 10 minutes, rotates the session ID, and returns `{ "id": 123, "displayName": "Customer", "role": "CUSTOMER" }`. A missing bot token returns `503`.
3. Fetch a new CSRF token after login. Send the session cookie and CSRF header on state-changing requests. `GET /api/me` reads the current user and role from the database-backed session; `POST /api/auth/logout` invalidates it.

Serve the Mini App and API from the same origin. The session cookie is `HttpOnly`, `SameSite=Lax`, and `Secure` by default; set `SESSION_COOKIE_SECURE=false` only for local HTTP development. Session rows live in PostgreSQL, and the bot token must come from `TELEGRAM_BOT_TOKEN` without being logged or returned.

## AI and customer replies

| Method | Path | Intended caller | Planned behavior |
| --- | --- | --- | --- |
| `GET` | `/api/leads/{id}/ai-analysis` | Manager, administrator | Return analysis and job state: `PENDING`, `RUNNING`, `SUCCEEDED`, or `FAILED`. |
| `POST` | `/api/leads/{id}/ai-analysis/retry` | Manager, administrator | Request another analysis after failure without creating duplicate active jobs. |
| `POST` | `/api/leads/{id}/reply-drafts` | Manager, administrator | Create a customer-facing reply draft; an AI suggestion may supply initial text. |
| `POST` | `/api/leads/{id}/reply-drafts/{draftId}/send` | Manager, administrator | Explicitly approve a draft and queue its delivery. AI output alone never sends a message. |

## Telegram and administration

| Method | Path | Intended caller | Planned behavior |
| --- | --- | --- | --- |
| `POST` | `/api/telegram/webhook` | Telegram | Verify the webhook secret and deduplicate updates by Telegram `update_id`. Store replies against the correct lead. |
| `GET` | `/api/admin/managers` | Administrator | List users eligible for assignment. |
| `PATCH` | `/api/admin/users/{id}/role` | Administrator | Change a user's role. |
| `POST` | `/api/admin/categories` | Administrator | Create a service category. |
| `PATCH` | `/api/admin/categories/{id}` | Administrator | Update or deactivate a service category. |
| `GET` | `/api/admin/ai-settings` | Administrator | Read safe AI processing settings; never return provider secrets. |
| `PATCH` | `/api/admin/ai-settings` | Administrator | Change safe AI processing settings. Provider keys remain environment variables. |

`GET /api/system` is an implemented connectivity endpoint. Spring Boot Actuator also provides `/actuator/health`.

## Planned background jobs

| Job | Trigger | Work and completion |
| --- | --- | --- |
| `AnalyzeLead` | Lead creation, a material customer reply, or an authorized manual retry | Invoke the AI adapter, validate structured output, persist a result, and mark the job `SUCCEEDED` or `FAILED` after bounded retries. Lead creation must succeed independently of AI availability. |
| `SendTelegramMessage` | Lead confirmation, manager notification, or manager-approved customer reply | Deliver through the Telegram adapter and record the outcome. Retries must not bypass approval or create duplicate customer messages. |
| `RecoverStaleJobs` | Scheduled maintenance | Detect jobs abandoned in `RUNNING` after an interruption; retry within the configured limit or mark them `FAILED`. |

These are planned application jobs, not separate deployable services. The initial implementation can use PostgreSQL-backed jobs and a worker inside the existing Spring Boot service. The exact job schema and scheduling mechanism will be finalized when the workflows are implemented.
