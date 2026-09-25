# End-to-end happy path

This document describes the target first-release workflow from opening the Telegram bot to completing a lead. Each section states whether the behavior is implemented or planned.

## Flow overview

~~~text
Customer opens the Telegram bot
  -> opens the Mini App
  -> authenticates with signed Telegram initData
  -> submits a lead
  -> receives a public reference
  -> sees the lead in My requests

Backend stores the lead
  -> queues AI analysis
  -> validates and stores the AI result
  -> queues a manager notification

Manager opens the Mini App
  -> sees the lead inbox
  -> opens the lead and AI analysis
  -> assigns an owner
  -> changes status
  -> reviews and approves a customer reply

Telegram delivers the approved reply
  -> customer responds
  -> backend stores the response against the lead
  -> manager completes the lead
~~~

## 1. Open the Mini App

The customer sends `/start` to the bot. The bot responds with a button that opens the public HTTPS URL of the Mini App.

The Mini App can currently be opened through a button configured in BotFather. Server-side `/start` handling and bot-generated buttons are planned.

## 2. Authenticate with Telegram

Telegram supplies signed data through `window.Telegram.WebApp.initData`. The frontend requests a CSRF token and sends the raw value to the backend:

~~~http
GET /api/auth/csrf
POST /api/auth/telegram
~~~

The backend:

1. Parses the query string.
2. Recalculates the Telegram HMAC with `TELEGRAM_BOT_TOKEN`.
3. Compares the calculated and received hashes in constant time.
4. Rejects data with an `auth_date` older than ten minutes.
5. Extracts the Telegram user ID and display name.
6. Creates or updates the local user without overwriting an existing role.
7. Rotates the session ID and stores the authenticated session in PostgreSQL.
8. Returns the local user ID, display name, and role.

This step is implemented.

## 3. Create a lead

The frontend loads active service categories through `GET /api/categories`. The customer submits the form through `POST /api/leads`.

The controller obtains `customerId` from the authenticated principal. The request body cannot set a customer ID, role, status, owner, or timestamps.

The request follows this path:

~~~text
LeadController
  -> CreateLeadUseCase
  -> UserRepository and ServiceCategoryRepository validation
  -> LeadRepository
  -> PostgreSQL
~~~

Core verifies that the caller is a customer, the category is active, required fields are present, and budget fields are consistent. PostgreSQL stores the lead with status `NEW` and no owner. The API returns `201 Created` with a stable reference such as `LM-000123`.

This step is implemented.

## 4. Read leads

The customer loads `GET /api/leads?page=0&size=10` and can open `GET /api/leads/{id}`. Customer queries include the authenticated customer ID at the repository boundary, so another customer's lead is returned as `404 Not Found`.

Managers and administrators use the same endpoints but can list and open all leads. The frontend shows `My requests` for customers and `Lead inbox` for managers and administrators.

This step is implemented. Manager filters remain planned.

## 5. Analyze the lead

After lead storage succeeds, the application creates an `AnalyzeLead` job in PostgreSQL. Lead creation remains successful if the AI provider is unavailable.

The worker claims the job and moves it through:

~~~text
PENDING -> RUNNING -> SUCCEEDED
                   -> FAILED
~~~

The AI provider returns structured output containing:

- a concise summary;
- extracted facts;
- missing information;
- a suggested clarification question;
- a priority and its reason.

The backend validates the structure and allowed values before storing an AI result. Invalid output is retried within a fixed limit and is never treated as trusted application data. Jobs are claimed with a lease and `SKIP LOCKED`; expired leases are recovered after a worker interruption. The local stub requires no provider key. When required information is missing and the lead remains `NEW`, successful completion atomically changes it to `CLARIFICATION` and writes a system status event.

This step is implemented with the local stub. A remote provider adapter remains planned.

## 6. Notify the manager

Lead creation queues a `SendTelegramMessage` job for the configured manager or team chat. The notification contains the public reference, category, and current status. A Telegram delivery failure does not roll back lead creation.

This step is planned.

## 7. Process the lead

The manager opens the lead inbox and selects a lead. The detail view will combine the submitted data, message history, audit events, and AI analysis.

The manager loads eligible owners through `GET /api/managers`, assigns or clears an owner through `PATCH /api/leads/{id}/assignee`, and changes status through `PATCH /api/leads/{id}/status`. Core validates permissions, owner role, optimistic version, and allowed status transitions. Every successful status or owner change updates the lead and appends a `lead_events` record in one transaction. The current shared-queue policy allows any manager or administrator to assign the lead to any manager or administrator.

The intended lifecycle is:

~~~text
NEW
  -> CLARIFICATION
  -> IN_PROGRESS
  -> REJECTED

CLARIFICATION
  -> NEW
  -> IN_PROGRESS
  -> REJECTED

IN_PROGRESS
  -> COMPLETED
  -> REJECTED
~~~

Lead list, detail, status transitions, owner assignment, internal notes, audit events, and manager controls are implemented. Internal notes are stored separately from customer-visible messages and are available only to managers and administrators.

## 8. Request clarification

AI may suggest a clarification question, but it cannot send customer-facing text. A manager reviews or edits the suggestion, creates a reply draft, and explicitly approves it through `POST /api/leads/{id}/reply-drafts/{draftId}/send`.

Approval creates a Telegram delivery job. Retrying delivery cannot bypass approval or create duplicate customer messages.

This step is planned.

## 9. Receive a customer reply

Telegram sends updates to `POST /api/telegram/webhook`. The backend verifies the webhook secret, claims the Telegram `update_id`, ignores duplicates, associates the reply with the correct lead, and stores it as a lead message. A material reply may create another AI analysis job.

This step is planned.

## 10. Complete the lead

The manager changes the lead from `IN_PROGRESS` to `COMPLETED`. The backend updates the lead, appends an audit event, and may queue a completion notification. The customer sees the completed status in `My requests`, while the full history remains available to managers.

This step is planned.

## Module responsibilities

| Module | Responsibility in the flow |
| --- | --- |
| `backend/app` | REST controllers, Spring Security, sessions, error mapping, and dependency wiring. |
| `backend/core` | Use cases, access rules, status transitions, and validation of AI results. |
| `backend/persistence` | PostgreSQL records, repository adapters, jobs, and Liquibase migrations. |
| `backend/telegram` | Mini App identity verification, webhook parsing, and outgoing delivery. |
| `backend/ai` | AI provider adapter, local stub, and structured response parsing. |
| `frontend` | Customer form and history, manager inbox, lead detail, and management controls. |

## Current boundary and next increment

The current implemented path is:

~~~text
Telegram authentication
  -> JDBC-backed session
  -> category loading
  -> lead creation
  -> PostgreSQL storage
  -> customer or manager lead list
  -> role-scoped lead detail
  -> manager or administrator assignment
  -> private internal notes
  -> persisted AI job and bounded retries
  -> validated AI result in the manager workspace
  -> status and owner audit history
~~~

The next increment is Telegram bot command handling and reliable manager notification, followed by customer clarification replies.
