# Data model and persistence boundary

This document defines the PostgreSQL schema. The foundation tables are implemented by Liquibase change set `001-foundation`, JDBC session tables by `002-sessions`, append-only lead audit events by `003-lead-events`, lead messages for internal notes by `004-lead-messages`, and AI job/result storage by `005-ai-analysis` in `backend/persistence/src/main/resources/db/changelog/changes`. Additional business tables remain planned and should be added only when the related workflow is implemented.

The design follows the first release in [README.md](../README.md): one business, Telegram as the only inbound channel, and one Spring Boot service. PostgreSQL is the source of truth for leads and their history. The frontend never accesses it directly.

## Foundation schema (first migration)

The migration creates the quoted PostgreSQL schema `"AI_LEAD_MANAGER"`. Its uppercase spelling is significant: SQL must quote the schema name. Liquibase keeps its tracking tables in the default `public` schema, so it can create the application schema in the first change set.

| Table | Columns | Rules and purpose |
| --- | --- | --- |
| `users` | `id bigint` identity PK, `telegram_user_id bigint` unique not null, `role varchar(16)` not null, `display_name text` not null, `created_at timestamptz` not null, `updated_at timestamptz` not null | `role` is `CUSTOMER`, `MANAGER`, or `ADMIN`. Create or update a user only from a Telegram identity verified by the backend. Role changes require an administrator use case; a client request cannot set its own role. |
| `service_categories` | `id bigint` identity PK, `code varchar(64)` unique not null, `name text` not null, `active boolean` not null default true | Seed at least `website_development` and `automation`. Deactivate categories instead of deleting ones referenced by leads. The administrator workflow can edit this catalog later. |
| `leads` | `id bigint` identity PK, `customer_id bigint` not null, `category_id bigint` not null, `description text` not null, `estimated_budget_amount numeric(14,2)` nullable, `budget_currency char(3)` nullable, `desired_deadline date` nullable, `contact_details text` not null, `status varchar(24)` not null default `NEW`, `owner_id bigint` nullable, `created_at timestamptz` not null, `updated_at timestamptz` not null, `version bigint` not null default 0 | FKs to `users` for customer/owner and to `service_categories` for category. Reject blank description or contact details, negative budget, and budget with no currency (or currency with no budget). Store currency as an uppercase ISO 4217 code. `owner_id` must refer to a manager/admin; enforce that in the core use case because a simple FK cannot check a user's role. `version` supports optimistic concurrency when manager edits arrive later. |

`LeadStatus` in `backend/core` defines the allowed status values: `NEW`, `CLARIFICATION`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`. The first migration adds a database check for these values. A status transition will be validated in core before persistence; the database check only guards the stored value. The application should reject unknown values rather than silently mapping them.

The lead's public reference number can be `LM-` followed by its database ID, padded for display (for example, `LM-000123`). It is stable and unique without a second sequence or mutable column. The internal ID remains a `bigint`; the API can expose the reference separately. Do not use this reference as proof that a caller may view the lead.

The first migration indexes `leads(customer_id, created_at desc)`, `leads(status, created_at desc)`, and `leads(owner_id, created_at desc)`. Add a category filter index when that filter is implemented. Use UTC instants in `timestamptz`; convert to the user's local time only at the API/UI edge.

## Additional first-release tables

These tables are introduced incrementally with their workflows. The table below distinguishes implemented storage from planned additions.

| Table | Key fields and constraints | Added with |
| --- | --- | --- |
| `lead_messages` | PK, `lead_id` FK, `sender_id` nullable FK, `kind` (`CUSTOMER_REPLY`, `MANAGER_REPLY`, `FOLLOW_UP_QUESTION`, `INTERNAL_NOTE`), body, and creation time. Internal notes must never enter the outgoing Telegram path. Telegram identifiers, approval, and delivery state will be added when customer conversations are implemented. | Implemented for internal notes; customer messages remain planned. |
| `lead_events` | PK, `lead_id` FK, `actor_id` nullable FK, event type, old/new status or owner IDs as applicable, creation time. Insert in the same transaction as each status or owner change. Keep history append-only. | Implemented for status and owner changes. |
| `telegram_updates` | `update_id bigint` PK, processing state and timestamps. Claim each update before applying its effects; a repeated update ID must not create another lead/message. Store only the fields needed for processing and diagnostics, not a raw token. | Telegram integration |
| `ai_jobs` | PK, `lead_id` FK, state (`PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`), attempt count and limit, next attempt time, lease fields, bounded error code/message, timestamps. A partial unique index permits one active job per lead. | Implemented. |
| `ai_results` | PK, unique `job_id` FK, `lead_id` FK, summary, JSON extracted facts and missing fields, suggested question, priority and reason, creation time. Invalid provider output is never stored as successful. | Implemented. |

When customer-facing messages are implemented, represent draft, approval, and delivery separately so a send retry cannot bypass manager approval. Use a unique Telegram message/update key where available, and record the send outcome. The exact delivery transaction and retry contract should be finalized with the bot workflow.

## Persistence module responsibility

`backend/core` owns Lead, Customer/Manager concepts, lifecycle rules, authorization decisions expressed as use cases, and storage interfaces such as `LeadRepository` and `UserRepository`. It has no Spring, SQL, PostgreSQL, Telegram, or AI SDK dependency.

`backend/persistence` owns versioned SQL migrations, Spring Data JDBC records and repositories, and adapters for the core storage interfaces. Spring Data generates basic writes and queries; custom SQL belongs here only when needed. The adapters translate database records to core models. They do not decide who may access a lead, which transitions are valid, whether AI output is acceptable, or whether a message may be sent. The lead record uses Spring Data's version field for optimistic concurrency.

`backend/app` supplies the datasource and migration configuration, wires repository implementations into use cases, authenticates the verified Telegram principal, and exposes HTTP endpoints. `backend/telegram` handles Telegram update parsing and outbound delivery; it uses core interfaces/use cases rather than issuing SQL. `backend/ai` invokes the AI provider and returns structured output for core validation.

Repository operations now support saving a lead, finding one by ID or by ID plus customer ID, and listing all leads or a customer's leads with pagination. User and category lookup are also available. The Spring Data records keep foreign keys as IDs, so saving a lead does not cascade into users or categories. Login atomically upserts a Telegram user by `telegram_user_id` while preserving the existing role. Lead creation occurs in a later request using the session's internal user ID. The API derives `customer_id` from that authenticated session, never from an untrusted request field. A customer-scoped read includes the customer predicate in the database query; core authorization checks remain necessary.

## Migration sequence

1. **V1 foundation (implemented):** Liquibase creates `users`, `service_categories`, and `leads`, including FKs, checks, indexes, and safe category seed data. Spring Data JDBC repository adapters provide storage. Lead create, paginated list, and detail operations use the verified session and enforce customer ownership.
2. **V2 sessions (implemented):** Liquibase creates `public.spring_session` and `public.spring_session_attributes`. Spring Session JDBC stores server-side sessions there; Spring Security loads the user's current role from `users` on each request.
3. **V3 lead events (implemented):** add append-only status and owner event storage. Status and owner changes use it now.
4. **V4 lead messages (partially implemented):** add append-only messages and implement manager-only internal notes. Customer messages, approval, and delivery metadata remain planned.
5. **Telegram reliability:** add update deduplication and outbound delivery state with unique keys.
6. **V5 AI analysis (implemented with local stub):** add jobs, leases, bounded persisted retries, validated results, manual retry, and stale-job recovery. A remote provider remains planned.

Each migration is forward-only and versioned. Do not place schema creation in application startup code or expose database models directly as API responses.
