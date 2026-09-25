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
- **backend:telegram:** Mini App `initData` verification; incoming bot updates and outgoing messages remain planned.
- **backend:ai:** AI provider adapters. The deterministic local stub is implemented; a remote provider remains planned.
- **backend:app:** Spring Boot entry point, REST API, authorization, and module configuration.
- **frontend:** Customer and manager views for the Mini App. It accesses data only through the backend API.

The foundation, JDBC session, lead event, lead message, and AI analysis migrations, datasource wiring, and repository adapters are implemented. Lead create, role-scoped read, status transition, owner assignment, assignable-manager listing, internal note, AI analysis, and event history use cases are in `backend/core`; Spring configuration and transactional orchestration are in `backend/app`. `backend/telegram` verifies signed Mini App data; `backend/app/security` creates JDBC-backed sessions and reloads the user's current role from PostgreSQL for each request. A profile-gated local authentication adapter can establish the same kind of session for fixed test identities; its controller and seeder are absent unless the `local` profile is explicitly active. Customer reads add the customer predicate to repository queries, while managers and administrators use the general read methods. Lead creation and AI job enqueue run in one transaction. The application worker claims jobs through PostgreSQL `SKIP LOCKED`, calls the provider outside the claim transaction, validates output in core, and commits the result, job completion, and optional `NEW` to `CLARIFICATION` transition together. Retry timing and leases are persisted. The frontend exposes status, owner, internal note, and AI analysis controls only to managers and administrators. Bot commands, customer conversations, outbound Telegram delivery, and a remote AI provider remain planned.
