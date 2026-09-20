# Preliminary HTTP API

This document records the proposed first-release API. The paths and responsibilities are provisional; request and response schemas will be finalized with each use case. At present, only `GET /api/system` is functional. The remaining routes are registered by Spring controllers but return `501 Not Implemented`. A registered route is not evidence that authentication, authorization, persistence, Telegram delivery, or AI processing is available.

All controllers live in `backend/app/src/main/kotlin/dev/aileadmanager/app/controller`. The frontend calls the backend over HTTP. The backend derives user identity and role from verified Telegram data; a request body cannot assign its own customer ID or role. Customer-scoped reads must enforce ownership, and manager and administrator operations require their respective roles before implementation is considered complete.

## Authentication and catalog

| Method | Path | Intended caller | Planned behavior |
| --- | --- | --- | --- |
| `POST` | `/api/auth/telegram` | Mini App | Verify Telegram `initData` and its age, then establish a short-lived backend session. The session format is not decided yet. |
| `GET` | `/api/me` | Authenticated user | Return the current user and role. |
| `GET` | `/api/categories` | Authenticated user | List active service categories for the lead form. |

## Leads

| Method | Path | Intended caller | Planned behavior |
| --- | --- | --- | --- |
| `POST` | `/api/leads` | Customer | Validate and save a lead; return its ID and public reference. Queue AI analysis separately. |
| `GET` | `/api/leads` | Customer, manager, administrator | Return a paginated list. Customers see only their leads; managers can filter by status, priority, and owner. |
| `GET` | `/api/leads/{id}` | Customer, manager, administrator | Return the lead detail allowed by the caller's role. |
| `PATCH` | `/api/leads/{id}/status` | Manager, administrator | Apply a valid status transition and write an audit event. |
| `PATCH` | `/api/leads/{id}/assignee` | Manager, administrator | Assign an eligible owner and write an audit event. |
| `POST` | `/api/leads/{id}/notes` | Manager, administrator | Add an internal note that is never sent to a customer. |
| `GET` | `/api/leads/{id}/events` | Manager, administrator | Return the lead's audit history. |
| `GET` | `/api/leads/{id}/messages` | Customer, manager, administrator | Return messages visible to the caller. Internal notes are excluded from customer responses. |

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
