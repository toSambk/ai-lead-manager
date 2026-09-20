# Data model and persistence boundary

This document defines the PostgreSQL schema. The foundation tables are implemented by Liquibase change set `001-foundation` in `backend/persistence/src/main/resources/db/changelog/changes/001-foundation.sql`. Later tables remain planned and should be added only when the related workflow is implemented.

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

## Later first-release tables

These are schema decisions for planned workflows, not a request to create all tables in the first migration.

| Table | Key fields and constraints | Added with |
| --- | --- | --- |
| `lead_messages` | PK, `lead_id` FK, `sender_id` nullable FK, `kind` (`CUSTOMER_REPLY`, `MANAGER_REPLY`, `FOLLOW_UP_QUESTION`, `INTERNAL_NOTE`), body, creation time, optional Telegram chat/message IDs, approval and delivery state for customer-facing manager/AI text. Internal notes must never enter the outgoing Telegram path. | Conversation and manager workspace |
| `lead_events` | PK, `lead_id` FK, `actor_id` nullable FK, event type, old/new status or owner IDs as applicable, creation time. Insert in the same transaction as each status or owner change. Keep history append-only. | Manager workspace and audit |
| `telegram_updates` | `update_id bigint` PK, processing state and timestamps. Claim each update before applying its effects; a repeated update ID must not create another lead/message. Store only the fields needed for processing and diagnostics, not a raw token. | Telegram integration |
| `ai_jobs` | PK, `lead_id` FK, state (`PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`), attempt count, next attempt time, bounded error code, timestamps. Failed jobs remain visible for manual handling. | AI processing |
| `ai_results` | PK, `lead_id` and `job_id` FKs, summary, validated extracted facts, missing field names, suggested question, priority and reason, creation time. Results are versioned by job; invalid provider output is never treated as a successful result. | AI processing |

When customer-facing messages are implemented, represent draft, approval, and delivery separately so a send retry cannot bypass manager approval. Use a unique Telegram message/update key where available, and record the send outcome. The exact delivery transaction and retry contract should be finalized with the bot workflow.

## Persistence module responsibility

`backend/core` owns Lead, Customer/Manager concepts, lifecycle rules, authorization decisions expressed as use cases, and storage interfaces such as `LeadRepository` and `UserRepository`. It has no Spring, SQL, PostgreSQL, Telegram, or AI SDK dependency.

`backend/persistence` owns versioned SQL migrations, Spring Data JDBC records and repositories, and adapters for the core storage interfaces. Spring Data generates basic writes and queries; custom SQL belongs here only when needed. The adapters translate database records to core models. They do not decide who may access a lead, which transitions are valid, whether AI output is acceptable, or whether a message may be sent. The lead record uses Spring Data's version field for optimistic concurrency.

`backend/app` supplies the datasource and migration configuration, wires repository implementations into use cases, authenticates the verified Telegram principal, and exposes HTTP endpoints. `backend/telegram` handles Telegram update parsing and outbound delivery; it uses core interfaces/use cases rather than issuing SQL. `backend/ai` invokes the AI provider and returns structured output for core validation.

Repository operations now support saving a lead, finding one by ID or by ID plus customer ID, and listing all leads or a customer's leads with pagination. User and category lookup are also available. The Spring Data records keep foreign keys as IDs, so saving a lead does not cascade into users or categories. When the intake use case is built, persist user lookup/upsert and lead creation atomically. The API must derive `customer_id` from verified Telegram identity, never from an untrusted request field. A customer-scoped read includes the customer predicate in the database query; core authorization checks remain necessary.

## Migration sequence

1. **V1 foundation (schema and repositories implemented):** Liquibase creates `users`, `service_categories`, and `leads`, including FKs, checks, indexes, and safe category seed data. The app configures the datasource and runs migrations at startup. Spring Data JDBC repository adapters provide storage. POST and GET lead API operations remain planned.
2. **Conversation and manager work:** add messages and events with explicit transaction boundaries for lead changes.
3. **Telegram reliability:** add update deduplication and outbound delivery state with unique keys.
4. **AI:** add jobs and validated results with bounded retry/failure states.

Each migration is forward-only and versioned. Do not place schema creation in application startup code or expose database models directly as API responses.
