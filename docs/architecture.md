# Project architecture

AI Lead Manager is a modular monolith. Gradle builds the Kotlin modules into one Spring Boot service. The frontend is a separate npm project in the same repository.

For local container runs, `infra/compose.yaml` builds that one backend service from `backend/app/Dockerfile` and starts it alongside PostgreSQL. The frontend still runs through Vite on the host, which proxies `/api` to the backend's localhost port. No Kotlin module runs as a separate container.

## Dependencies

~~~text
frontend  --HTTP-->  backend:app
                     ├── backend:core
                     ├── backend:persistence ──> backend:core
                     ├── backend:telegram ────> backend:core
                     └── backend:ai ──────────> backend:core
~~~

- **backend:core:** Entities, business rules, use cases, and interfaces for external dependencies. It does not know about Spring, Telegram, SQL, or AI APIs.
- **backend:persistence:** Liquibase migrations and Spring Data JDBC repository adapters for the foundation tables. See the [data model and module boundary](data-model.md).
- **backend:telegram:** Mini App `initData` verification, webhook update parsing, Bot API transport, and delivery error classification.
- **backend:ai:** AI provider adapters. The deterministic local stub is implemented; a remote provider remains planned.
- **backend:app:** Spring Boot entry point, REST API, authorization, and module configuration.
- **frontend:** Customer and manager views for the Mini App. It accesses data only through the backend API.

The foundation, JDBC session, lead event, lead message, AI analysis, and Telegram delivery migrations, datasource wiring, and repository adapters are implemented. Lead create, role-scoped read, status transition, owner assignment, assignable-manager listing, internal note, AI analysis, and event history use cases are in `backend/core`; Spring configuration and transactional orchestration are in `backend/app`. `backend/telegram` verifies signed Mini App data, parses updates, and calls the Bot API. `backend/app/security` creates JDBC-backed sessions and reloads the user's current role from PostgreSQL for each request. A profile-gated local authentication adapter can establish the same kind of session for fixed test identities; its controller and seeder are absent unless the `local` profile is explicitly active. Customer reads add the customer predicate to repository queries, while managers and administrators use the general read methods.

Inside `backend/app`, HTTP controllers and DTOs live in `controller` and `dto`, security adapters live in `security`, and Spring bean wiring lives in `config`. Transactional orchestration services are grouped by integration or business area under `service.ai`, `service.lead`, and `service.telegram`. Periodic polling components live in `scheduler`; they delegate job claiming and processing to the corresponding service package.

Lead creation, AI job enqueue, and Telegram notification enqueue run in one transaction. AI and Telegram workers claim jobs through PostgreSQL `SKIP LOCKED`, release the claim transaction before an external call, and persist completion or bounded retry state afterward. Leases allow stale `RUNNING` jobs to return to the queue after an interrupted worker. Telegram update IDs and outbound message keys are unique in PostgreSQL. This prevents repeated webhook processing and repeated enqueueing. A Bot API network timeout after Telegram accepted a message is inherently ambiguous, so a later retry may still produce a duplicate Telegram message.

Reply draft approval atomically creates a pending customer-visible message, reserves the customer's single active conversation, and enqueues its Telegram delivery. Successful delivery exposes the conversation to incoming replies. A terminal delivery failure marks the message and draft failed and cancels the reservation. The webhook closes the active conversation when the customer replies, stores the reply, and enqueues a new AI analysis. AI output can suggest text but only a manager or administrator can approve delivery. The AI module provides a deterministic stub and an OpenAI adapter implemented with Spring AI. Provider selection is environment based, while validation, persisted retries, and failure visibility remain provider independent.
